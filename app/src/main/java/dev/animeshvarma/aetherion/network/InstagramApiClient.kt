package dev.animeshvarma.aetherion.network

import dev.animeshvarma.aetherion.network.interceptors.LoggingInterceptor
import dev.animeshvarma.aetherion.network.interceptors.SignatureInterceptor
import dev.animeshvarma.aetherion.network.models.LoginResponse
import dev.animeshvarma.aetherion.network.models.MsisdnResponse
import dev.animeshvarma.aetherion.network.models.QeSyncResponse
import dev.animeshvarma.aetherion.crypto.PasswordEncryptor
import dev.animeshvarma.aetherion.util.ConnectionInfo
import dev.animeshvarma.aetherion.util.Constants
import dev.animeshvarma.aetherion.util.DeviceFingerprint
import dev.animeshvarma.aetherion.util.DeviceProfile
import dev.animeshvarma.aetherion.util.ServerClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class InstagramApiClient(
    private val deviceFingerprint: DeviceFingerprint,
    private val deviceProfile: DeviceProfile,
    private val connectionInfo: ConnectionInfo,
    private val scope: CoroutineScope
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor { message ->
            LoggingInterceptor.logNetwork("OKHTTP", message)
        }.apply { level = HttpLoggingInterceptor.Level.BODY }

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

    // ---- Bootstrap ----
    suspend fun readMsisdnHeader(): Response<MsisdnResponse> =
        api.readMsisdnHeader(deviceFingerprint.deviceId)

    suspend fun qeSync(): Response<QeSyncResponse> {
        val resp = api.qeSync(deviceFingerprint.uuid)
        ServerClock.sync(resp.headers()["Date"])
        PasswordEncryptor.syncFromHeaders(
            resp.headers()["ig-set-password-encryption-key-id"],
            resp.headers()["ig-set-password-encryption-pub-key"]
        )
        return resp
    }

    suspend fun launcherSync(): Response<QeSyncResponse> =
        api.launcherSync(deviceFingerprint.uuid)

    // ---- Login ----
    suspend fun login(username: String, password: String): Response<LoginResponse> {
        val csrf = deviceFingerprint.csrfToken.ifBlank { DeviceFingerprint.genToken() }
        val timestamp = ServerClock.epochSeconds().toString()

        var encPassword = PasswordEncryptor.plaintextPassword(password, timestamp)
        if (PasswordEncryptor.isAvailable()) {
            val built = try {
                PasswordEncryptor.encryptPassword(password, timestamp)
            } catch (e: Exception) {
                LoggingInterceptor.logNetwork(
                    "PWD", "encryption failed, falling back to plaintext: ${e.message}")
                null
            }
            if (built != null) encPassword = built
        }

        val data = linkedMapOf<String, String>(
            "jazoest" to DeviceFingerprint.generateJazoest(deviceFingerprint.phoneId),
            "country_codes" to "[{\"country_code\":\"${deviceProfile.countryCode}\",\"source\":[\"default\"]}]",
            "phone_id" to deviceFingerprint.phoneId,
            "enc_password" to encPassword,
            "username" to username,
            "adid" to deviceFingerprint.adid,
            "guid" to deviceFingerprint.uuid,
            "device_id" to deviceFingerprint.deviceId,
            "google_tokens" to "[]",
            "login_attempt_count" to "0",
            "_csrftoken" to csrf
        )
        return api.login(data)
    }

    suspend fun twoFactorLogin(
        username: String,
        twoFactorIdentifier: String,
        code: String
    ): Response<LoginResponse> {
        val data = linkedMapOf<String, String>(
            "verification_code" to code,
            "phone_id" to deviceFingerprint.phoneId,
            "_csrftoken" to deviceFingerprint.csrfToken.ifBlank { DeviceFingerprint.genToken() },
            "two_factor_identifier" to twoFactorIdentifier,
            "username" to username,
            "trust_this_device" to "0",
            "guid" to deviceFingerprint.uuid,
            "device_id" to deviceFingerprint.deviceId,
            "waterfall_id" to deviceFingerprint.waterfallId,
            "verification_method" to "3"
        )
        return api.twoFactorLogin(data)
    }
}