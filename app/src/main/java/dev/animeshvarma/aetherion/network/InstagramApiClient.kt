package dev.animeshvarma.aetherion.network

import dev.animeshvarma.aetherion.crypto.KeystoreManager
import dev.animeshvarma.aetherion.crypto.PasswordEncryptor
import dev.animeshvarma.aetherion.network.interceptors.LoggingInterceptor
import dev.animeshvarma.aetherion.network.interceptors.SignatureInterceptor
import dev.animeshvarma.aetherion.network.models.AccountFamilyResponse
import dev.animeshvarma.aetherion.network.models.AttestationResponse
import dev.animeshvarma.aetherion.network.models.HighlightsTrayResponse
import dev.animeshvarma.aetherion.network.models.ReelsTrayResponse
import dev.animeshvarma.aetherion.network.models.TextFeedResponse
import dev.animeshvarma.aetherion.network.models.UserFeedResponse
import dev.animeshvarma.aetherion.network.models.UserProfileResponse
import dev.animeshvarma.aetherion.util.ConnectionInfo
import dev.animeshvarma.aetherion.util.Constants
import dev.animeshvarma.aetherion.util.DeviceFingerprint
import dev.animeshvarma.aetherion.util.DeviceProfile
import dev.animeshvarma.aetherion.util.ServerClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

data class HomepageLoginSpec(
    val waterfallId: String,
    val usernameInputId: String,
    val passwordInputId: String,
    val caaCoreDataEncrypted1: String,
    val caaCoreDataEncrypted2: String,
    val wwwRequestId: String,
    val qplInstanceId: Long,
    val preLoginActions: List<String> = emptyList()
)

class InstagramApiClient(
    private val deviceFingerprint: DeviceFingerprint,
    private val deviceProfile: DeviceProfile,
    private val connectionInfo: ConnectionInfo,
    @Suppress("unused") private val keystoreManager: KeystoreManager,
    private val scope: CoroutineScope
) {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    init {
        PasswordEncryptor.restoreFromDeviceFingerprint(deviceFingerprint)
    }

    val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor { message ->
            LoggingInterceptor.logNetwork("OKHTTP", message)
        }.apply { level = HttpLoggingInterceptor.Level.HEADERS }

        OkHttpClient.Builder()
            .cookieJar(InstagramCookieJar(deviceFingerprint))
            .addInterceptor(SignatureInterceptor(deviceFingerprint, deviceProfile, connectionInfo))
            .addInterceptor(logging)
            .addInterceptor(LoggingInterceptor())
            .connectTimeout(Constants.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(Constants.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(Constants.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    private val api = retrofit.create(InstagramApiService::class.java)

    private val bkClientContext: String
        get() = buildJsonObject {
            put("bloks_version", Constants.BLOKS_VERSION)
            put("styles_id", "instagram")
        }.toString()

    // ================================================================
    // NEW — pre-login bootstrap. This is what mints the password RSA key.
    // ================================================================

    suspend fun bootstrapPasswordKey(): Boolean {
        runCatching { api.launcherSync(id = deviceFingerprint.deviceId) }
            .onSuccess { LoggingInterceptor.logInfo("API", "launcher/sync -> HTTP ${it.code()}") }
        return PasswordEncryptor.isAvailable()
    }

    // ================================================================
    // NEW — legacy signed_body login. Stable across app builds.
    // ================================================================

    fun buildAttestHeader(challengeNonce: String): String {
        keystoreManager.regenerateWithChallenge(challengeNonce)
        return keystoreManager.buildAttestParamsHeader(challengeNonce)
    }

    suspend fun legacyLogin(
        username: String,
        password: String,
        attestHeader: String? = null
    ): Response<ResponseBody> {
        val timestamp = ServerClock.epochSeconds().toString()
        val encPassword = buildEncryptedPassword(password, timestamp)

        val bodyJson = buildJsonObject {
            put("jazoest", jazoest(deviceFingerprint.phoneId))
            put("country_codes",
                "[{\"country_code\":\"${deviceProfile.countryCallingCode}\",\"source\":[\"default\"]}]")
            put("phone_id", deviceFingerprint.phoneId)
            put("enc_password", encPassword)
            put("username", username)
            put("_csrftoken", deviceFingerprint.csrfToken)
            put("adid", deviceFingerprint.adid)
            put("guid", deviceFingerprint.guid)
            put("device_id", deviceFingerprint.androidId)
            put("google_tokens", "[]")
            put("login_attempt_count", "0")
        }.toString()

        val headers = if (!attestHeader.isNullOrBlank())
            mapOf("x-ig-attest-params" to attestHeader) else emptyMap()

        LoggingInterceptor.logInfo(
            "AUTH", "accounts/login attest=${!attestHeader.isNullOrBlank()} payload=${bodyJson.take(280)}"
        )
        return api.loginSigned(headers, mapOf("signed_body" to "SIGNATURE.$bodyJson"))
    }

    suspend fun twoFactorLogin(
        username: String,
        twoFactorIdentifier: String,
        code: String
    ): Response<ResponseBody> {
        val bodyJson = buildJsonObject {
            put("verification_code", code)
            put("phone_id", deviceFingerprint.phoneId)
            put("_csrftoken", deviceFingerprint.csrfToken)
            put("two_factor_identifier", twoFactorIdentifier)
            put("username", username)
            put("trust_this_device", "1")
            put("guid", deviceFingerprint.guid)
            put("device_id", deviceFingerprint.androidId)
            put("waterfall_id", deviceFingerprint.waterfallId)
            put("verification_method", "3")
        }.toString()

        return api.twoFactorSigned(mapOf("signed_body" to "SIGNATURE.$bodyJson"))
    }

    suspend fun currentUserRaw(): Response<ResponseBody> = api.currentUserRaw()

    /** jazoest = "2" + sum of char codes. Cheap anti-tamper token. */
    private fun jazoest(value: String): String = "2" + value.sumOf { it.code }

    // ================================================================
    // Bloks path (unchanged, used as fallback)
    // ================================================================

    suspend fun loadLoginHomepage(): Response<ResponseBody> {
        val paramsJson = buildJsonObject {
            put("device_id", deviceFingerprint.deviceId)
            put("family_device_id", deviceFingerprint.familyDeviceId)
            put("waterfall_id", deviceFingerprint.waterfallId)
        }.toString()

        return api.getBloksApp(
            appId = "com.bloks.www.caa.login.login_homepage",
            extraHeaders = emptyMap(),
            params = paramsJson,
            bkClientContext = bkClientContext,
            bloksVersioningId = Constants.BLOKS_VERSION
        )
    }

    suspend fun callPreLoginAsyncAction(
        actionName: String,
        spec: HomepageLoginSpec
    ): Response<ResponseBody> {
        val serverParams = buildJsonObject {
            put("INTERNAL__latency_qpl_marker_id", 36707139)
            put("INTERNAL__latency_qpl_instance_id", spec.qplInstanceId)
            put("device_id", deviceFingerprint.deviceId)
            put("family_device_id", deviceFingerprint.familyDeviceId)
            put("waterfall_id", spec.waterfallId)
            put("is_platform_login", false)
            put("is_from_logged_out", false)
            put("access_flow_version", "pre_mt_behavior")
            put("login_surface", "login_home")
            put("login_entry_point", "logged_out")
            put("INTERNAL_INFRA_screen_id", "bk.caa.login.language_selector")
        }
        val clientInputParams = buildJsonObject {
            put("lois_settings", buildJsonObject { put("lois_token", "") })
            put("aac", deviceFingerprint.caaAccessContext)
        }
        val paramsJson = buildJsonObject {
            put("server_params", serverParams)
            put("client_input_params", clientInputParams)
        }.toString()

        return api.postBloksAsyncAction(
            actionName = actionName,
            extraHeaders = emptyMap(),
            params = paramsJson,
            bkClientContext = bkClientContext,
            bloksVersioningId = Constants.BLOKS_VERSION
        )
    }

    suspend fun createAndroidKeystore(includeConnUuid: Boolean = true): Response<AttestationResponse> {
        val keyHash = keystoreManager.getKeyHash()
        return api.createAndroidKeystore(
            extraHeaders = emptyMap(),
            appScopedDeviceId = deviceFingerprint.deviceId,
            keyHash = keyHash
        )
    }

    suspend fun sendBloksLoginRequest(
        username: String,
        password: String,
        challengeNonce: String,
        spec: HomepageLoginSpec,
        includeAttestHeader: Boolean = true
    ): Response<ResponseBody> {
        val encPassword = buildEncryptedPassword(password, ServerClock.epochSeconds().toString())
        val attestHeader = if (includeAttestHeader) {
            keystoreManager.buildAttestParamsHeader(challengeNonce)
        } else null

        val serverParams = buildJsonObject {
            put("INTERNAL__latency_qpl_marker_id", 36707139)
            put("INTERNAL__latency_qpl_instance_id", spec.qplInstanceId)
            put("credential_type", "password")
            put("username_text_input_id", spec.usernameInputId)
            put("password_text_input_id", spec.passwordInputId)
            put("login_source", "Login")
            put("login_credential_type", "none")
            put("server_login_source", "login")
            put("ar_event_source", "login_home_page")
            put("should_trigger_override_login_success_action", 0)
            put("should_trigger_override_login_2fa_action", 0)
            put("is_caa_perf_enabled", 0)
            put("reg_flow_source", "login_home_native_integration_point")
            put("caller", "gslr")
            put("is_from_landing_page", 0)
            put("is_from_empty_password", 0)
            put("is_from_aymh", 0)
            put("is_from_password_entry_page", 0)
            put("is_from_assistive_id", 0)
            put("is_from_msplit_fallback", 0)
            put("two_step_login_type", "one_step_login")
            put("left_nav_button_action", "NONE")
            put("device_id", deviceFingerprint.deviceId)
            put("family_device_id", deviceFingerprint.familyDeviceId)
            put("waterfall_id", spec.waterfallId)
            put("offline_experiment_group", JsonNull)
            put("layered_homepage_experiment_group", JsonNull)
            put("is_platform_login", 0)
            put("is_from_logged_in_switcher", 0)
            put("is_from_logged_out", 0)
            put("access_flow_version", "pre_mt_behavior")
            put("login_surface", "login_home")
            put("login_entry_point", "logged_out")
        }

        val clientInputParams = buildJsonObject {
            put("device_id", deviceFingerprint.deviceId)
            put("family_device_id", deviceFingerprint.familyDeviceId)
            put("machine_id", deviceFingerprint.mid)
            put("cloud_trust_token", JsonNull)
            put("block_store_machine_id", "")
            put("zero_balance_state", "")
            put("contact_point", username)
            put("password", encPassword)
            put("accounts_list", buildJsonArray {})
            put("fb_ig_device_id", buildJsonArray {})
            put("secure_family_device_id", "")
            put("sim_phones", buildJsonArray {})
            put("device_emails", buildJsonArray {})
            put("encrypted_msisdn", "")
            put("headers_infra_flow_id", "")
            put("try_num", 1)
            put("login_attempt_count", 1)
            put("event_flow", "login_manual")
            put("event_step", "home_page")
            put("openid_tokens", buildJsonObject {})
            put("auth_secure_device_id", "")
            put("client_known_key_hash", if (attestHeader != null) keystoreManager.getKeyHash() else "")
            put("has_whatsapp_installed", 0)
            put("sso_token_map_json_string", "")
            put("should_show_nested_nta_from_aymh", 0)
            put(
                "flash_call_permission_status",
                buildJsonObject {
                    put("READ_PHONE_STATE", "DENIED")
                    put("READ_CALL_LOG", "DENIED")
                    put("ANSWER_PHONE_CALLS", "DENIED")
                }
            )
            put("gms_incoming_call_retriever_eligibility", "not_eligible")
            put("password_contains_non_ascii", "false")
            put("has_granted_read_contacts_permissions", 0)
            put("has_granted_read_phone_permissions", 0)
            put("app_manager_id", "")
            put("ig_vetted_device_nonces", "")
            put("aymh_accounts", buildJsonArray {})
            put("sso_accounts_auth_data", buildJsonArray {})
            put("blocked_uids", buildJsonArray {})
            put("si_device_param_network_info", buildJsonObject {})
            put("network_bssid", JsonNull)
            put("lois_settings", buildJsonObject { put("lois_token", "") })
            put("aac", deviceFingerprint.caaAccessContext)
        }

        val paramsJson = buildJsonObject {
            put("server_params", serverParams)
            put("client_input_params", clientInputParams)
        }.toString()

        val headers = if (attestHeader != null) {
            mapOf("x-ig-attest-params" to attestHeader)
        } else emptyMap()

        val primary = api.postBloksAsyncAction(
            actionName = "com.bloks.www.bloks.caa.login.async.send_login_request",
            extraHeaders = headers,
            params = paramsJson,
            bkClientContext = bkClientContext,
            bloksVersioningId = Constants.BLOKS_VERSION
        )

        if (primary.code() == 429) {
            LoggingInterceptor.logInfo("FLOW", "429 from i.instagram.com; retrying on b.i.instagram.com")
            val altUrl = "${Constants.LOGIN_HOST_URL}" +
                    "bloks/async_action/com.bloks.www.bloks.caa.login.async.send_login_request/"
            return api.postBloksAsyncActionByUrl(
                fullUrl = altUrl,
                extraHeaders = headers,
                params = paramsJson,
                bkClientContext = bkClientContext,
                bloksVersioningId = Constants.BLOKS_VERSION
            )
        }
        return primary
    }

    // 2FA Bloks screens — kept for the fallback path
    suspend fun load2faEntrypoint(challengeContext: String): Response<ResponseBody> {
        val paramsJson = buildJsonObject {
            put("two_step_verification_context", challengeContext)
            put("device_id", deviceFingerprint.deviceId)
            put("family_device_id", deviceFingerprint.familyDeviceId)
        }.toString()
        return api.getBloksApp(
            appId = "com.bloks.www.two_step_verification.entrypoint",
            extraHeaders = emptyMap(), params = paramsJson,
            bkClientContext = bkClientContext,
            bloksVersioningId = Constants.BLOKS_VERSION
        )
    }

    suspend fun load2faMethodPicker(stateToken: String): Response<ResponseBody> {
        val paramsJson = buildJsonObject {
            put("two_step_verification_context", stateToken)
            put("device_id", deviceFingerprint.deviceId)
        }.toString()
        return api.getBloksApp(
            appId = "com.bloks.www.two_step_verification.method_picker",
            extraHeaders = emptyMap(), params = paramsJson,
            bkClientContext = bkClientContext,
            bloksVersioningId = Constants.BLOKS_VERSION
        )
    }

    suspend fun navigate2faMethodToTotp(stateToken: String): Response<ResponseBody> {
        val paramsJson = buildJsonObject {
            put(
                "server_params",
                buildJsonObject {
                    put("two_step_verification_context", stateToken)
                    put("selected_method", "totp")
                    put("device_id", deviceFingerprint.deviceId)
                }
            )
            put(
                "client_input_params",
                buildJsonObject {
                    put("selected_method", "totp")
                    put("two_step_verification_context", stateToken)
                    put("device_id", deviceFingerprint.deviceId)
                }
            )
        }.toString()
        return api.postBloksAsyncAction(
            actionName = "com.bloks.www.two_step_verification.method_picker.navigation.async",
            extraHeaders = emptyMap(), params = paramsJson,
            bkClientContext = bkClientContext,
            bloksVersioningId = Constants.BLOKS_VERSION
        )
    }

    suspend fun load2faTotpScreen(navState: String): Response<ResponseBody> {
        val paramsJson = buildJsonObject {
            put("two_step_verification_context", navState)
            put("device_id", deviceFingerprint.deviceId)
            put("family_device_id", deviceFingerprint.familyDeviceId)
        }.toString()
        return api.getBloksApp(
            appId = "com.bloks.www.two_factor_login.enter_totp_code",
            extraHeaders = emptyMap(), params = paramsJson,
            bkClientContext = bkClientContext,
            bloksVersioningId = Constants.BLOKS_VERSION
        )
    }

    suspend fun verify2faCodeBloks(code: String, totpFormState: String): Response<ResponseBody> {
        val paramsJson = buildJsonObject {
            put(
                "server_params",
                buildJsonObject {
                    put("INTERNAL__latency_qpl_marker_id", 36707139)
                    put("INTERNAL__latency_qpl_instance_id", System.currentTimeMillis())
                    put("two_step_verification_context", totpFormState)
                    put("device_id", deviceFingerprint.deviceId)
                    put("family_device_id", deviceFingerprint.familyDeviceId)
                }
            )
            put(
                "client_input_params",
                buildJsonObject {
                    put("code", code)
                    put("verification_code", code)
                    put("security_code", code)
                    put("trust_this_device", 1)
                    put("two_step_verification_context", totpFormState)
                    put("device_id", deviceFingerprint.deviceId)
                    put("family_device_id", deviceFingerprint.familyDeviceId)
                    put("android_id", deviceFingerprint.androidId)
                }
            )
        }.toString()
        return api.postBloksAsyncAction(
            actionName = "com.bloks.www.two_step_verification.verify_code.async",
            extraHeaders = emptyMap(), params = paramsJson,
            bkClientContext = bkClientContext,
            bloksVersioningId = Constants.BLOKS_VERSION
        )
    }

    suspend fun poll2faHasBeenAllowed(pollState: String): Response<ResponseBody> {
        val paramsJson = buildJsonObject {
            put("server_params", buildJsonObject {
                put("two_step_verification_context", pollState)
                put("device_id", deviceFingerprint.deviceId)
            })
            put("client_input_params", buildJsonObject {
                put("two_step_verification_context", pollState)
                put("device_id", deviceFingerprint.deviceId)
            })
        }.toString()
        return api.postBloksAsyncAction(
            actionName = "com.bloks.www.two_step_verification.has_been_allowed.async",
            extraHeaders = emptyMap(), params = paramsJson,
            bkClientContext = bkClientContext,
            bloksVersioningId = Constants.BLOKS_VERSION
        )
    }

    // ---- Feed / GraphQL preludes ----
    suspend fun postTranslationsPrelude(): Response<JsonElement> {
        val headers = mapOf(
            "ig-u-ds-user-id" to deviceFingerprint.dsUserId,
            "x-client-doc-id" to Constants.TRANSLATIONS_DOC_ID,
            "x-root-field-name" to Constants.TRANSLATIONS_ROOT_FIELD,
            "x-graphql-client-library" to Constants.GRAPHQL_CLIENT_LIBRARY,
            "x-fb-friendly-name" to Constants.TRANSLATIONS_FRIENDLY_NAME
        )
        val variables = buildJsonObject {
            put("user_id", deviceFingerprint.dsUserId)
            put("device_locale", deviceProfile.locale)
        }.toString()
        val fields = linkedMapOf(
            "method" to "post",
            "format" to "json",
            "server_timestamps" to "true",
            "locale" to "user",
            "fb_api_req_friendly_name" to Constants.TRANSLATIONS_FRIENDLY_NAME,
            "client_doc_id" to Constants.TRANSLATIONS_DOC_ID,
            "variables" to variables
        )
        return api.postGraphQlQuery(Constants.GRAPHQL_URL, headers, fields)
    }

    suspend fun postFeedTimelineColdStart(): Response<JsonElement> {
        val nowMs = ServerClock.nowMillis().toString()
        val headers = mapOf(
            "x-ig-app-start-request" to "1",
            "x-ig-accept-hint" to "feed",
            "x-ig-tail-load-request" to "false",
            "x-ig-client-endpoint" to Constants.FEED_CLIENT_ENDPOINT,
            "x-fb-friendly-name" to "IgApi: feed/timeline/"
        )
        val fields = linkedMapOf(
            "has_camera_permission" to if (deviceProfile.hasCameraPermission()) "1" else "0",
            "feed_view_info" to "[]",
            "phone_id" to deviceFingerprint.familyDeviceId,
            "client_view_state_media_list" to "[]",
            "reason" to "cold_start_fetch",
            "battery_level" to deviceProfile.batteryLevel().toString(),
            "include_personalization_features" to "true",
            "request_build_time" to nowMs,
            "timezone_offset" to deviceProfile.timeZoneOffsetSeconds.toString(),
            "client_recorded_request_time_ms" to nowMs,
            "device_timezone_name" to deviceProfile.timeZoneName,
            "device_id" to deviceFingerprint.deviceId,
            "request_id" to UUID.randomUUID().toString(),
            "is_pull_to_refresh" to "0",
            "_uuid" to deviceFingerprint.deviceId,
            "include_attribution_ui_data" to "true",
            "is_charging" to if (deviceProfile.isCharging()) "1" else "0",
            "app_start_time" to nowMs,
            "is_non_feed_surface" to "0",
            "is_dark_mode" to if (deviceProfile.isDarkMode()) "1" else "0",
            "session_level_signals" to "{\"cold_start\":true}",
            "will_sound_on" to "0",
            "session_id" to UUID.randomUUID().toString(),
            "bloks_versioning_id" to Constants.BLOKS_VERSION
        )
        return api.postFeedTimeline(headers, fields)
    }

    suspend fun postReelsTray(): Response<ReelsTrayResponse> =
        api.postReelsTray(deviceFingerprint.deviceId)

    suspend fun postReelsMediaStream(reelIds: List<String>): Response<JsonElement> {
        val encodedIds = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()),
            reelIds
        )
        return api.postReelsMediaStream(encodedIds)
    }

    // ---- Profile ----
    suspend fun getAccountFamily(): Response<AccountFamilyResponse> = api.getAccountFamily()

    suspend fun getUserInfo(userId: String): Response<UserProfileResponse> = api.getUserInfo(userId)

    suspend fun postUserInfoStream(userId: String): Response<ResponseBody> =
        api.postUserInfoStream(userId = userId, uuid = deviceFingerprint.deviceId)

    suspend fun getUserByBase64(userIdB64: String): Response<ResponseBody> =
        api.getUserByBase64(userIdB64)

    suspend fun getHighlightsTray(userId: String): Response<HighlightsTrayResponse> =
        api.getHighlightsTray(userId)

    suspend fun getTextFeed(userIdB64: String): Response<TextFeedResponse> =
        api.getTextFeed(userIdB64)

    suspend fun getUserFeed(userId: String): Response<UserFeedResponse> = api.getUserFeed(userId)

    fun fetchImageBytes(url: String): ByteArray? {
        if (url.isBlank()) return null
        return try {
            val req = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", deviceProfile.userAgent)
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildEncryptedPassword(password: String, timestamp: String): String {
        if (PasswordEncryptor.isAvailable()) {
            runCatching { PasswordEncryptor.encryptPassword(password, timestamp) }
                .onSuccess {
                    LoggingInterceptor.logNetwork("PWD", "#PWD_INSTAGRAM:4 built (${it.length}c)")
                    return it
                }
                .onFailure {
                    LoggingInterceptor.logNetwork("PWD", "v4 encryption failed: ${it.message}")
                }
        }
        LoggingInterceptor.logNetwork("PWD", "falling back to #PWD_INSTAGRAM:0 (plaintext)")
        return PasswordEncryptor.plaintextPassword(password, timestamp)
    }
}