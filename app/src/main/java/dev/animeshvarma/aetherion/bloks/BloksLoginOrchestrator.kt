package dev.animeshvarma.aetherion.bloks

import dev.animeshvarma.aetherion.auth.LoginObserver
import dev.animeshvarma.aetherion.auth.LoginStep
import dev.animeshvarma.aetherion.auth.NoopLoginObserver
import dev.animeshvarma.aetherion.auth.StepStatus
import dev.animeshvarma.aetherion.auth.TerminalLevel
import dev.animeshvarma.aetherion.network.InstagramApiClient
import dev.animeshvarma.aetherion.network.interceptors.LoggingInterceptor
import dev.animeshvarma.aetherion.network.models.HighlightReelItem
import dev.animeshvarma.aetherion.network.models.MediaFeedItem
import dev.animeshvarma.aetherion.network.models.OwnProfileData
import dev.animeshvarma.aetherion.network.models.ProfileUserDetail
import dev.animeshvarma.aetherion.util.Constants
import dev.animeshvarma.aetherion.util.DeviceFingerprint
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ResponseBody
import retrofit2.Response

class TwoFactorRequiredException(
    val twoFactorIdentifier: String,
    message: String = "Two-factor authentication is enabled on this account"
) : Exception(message)

class AuthFailedException(message: String) : Exception(message)

data class LoginResult(
    val success: Boolean,
    val sessionId: String,
    val userId: String,
    val username: String,
    val was2faFlow: Boolean = false
)

class BloksLoginOrchestrator(
    private val apiClient: InstagramApiClient,
    private val deviceFingerprint: DeviceFingerprint
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val executedSteps = mutableListOf<String>()

    // ---------------------------------------------------------------- login

    suspend fun login(
        username: String,
        password: String,
        observer: LoginObserver = NoopLoginObserver
    ): LoginResult {
        executedSteps.clear()
        deviceFingerprint.clearSession()
        val user = username.trim()

        // ── 1. DEVICE ───────────────────────────────────────────────
        observer.step(LoginStep.DEVICE, StepStatus.RUNNING, "POST launcher/sync")
        observer.log(TerminalLevel.INFO, "POST /api/v1/launcher/sync/")
        val keyReady = runCatching { apiClient.bootstrapPasswordKey() }.getOrDefault(false)
        val keyId = deviceFingerprint.pwdKeyId

        if (keyReady) {
            observer.log(TerminalLevel.GOOD, "password key received · key_id=$keyId")
            observer.step(LoginStep.DEVICE, StepStatus.DONE, "key id $keyId")
            executedSteps += "1. launcher/sync (key=$keyId)"
        } else {
            observer.log(
                TerminalLevel.WARN,
                "no password key in response — using #PWD_INSTAGRAM:0 plaintext"
            )
            observer.step(LoginStep.DEVICE, StepStatus.DONE, "no key · plaintext fallback")
            executedSteps += "1. launcher/sync (no key)"
        }
        delay(300)

        // ── 2. ATTEST ───────────────────────────────────────────────
        observer.step(LoginStep.ATTEST, StepStatus.RUNNING, "requesting challenge nonce")
        observer.log(TerminalLevel.INFO, "POST /api/v1/attestation/create_android_keystore/")

        var attestHeader: String? = null
        val keystore = runCatching { apiClient.createAndroidKeystore(true) }.getOrNull()
        val nonce = keystore?.body()?.challenge_nonce.orEmpty()

        if (nonce.isNotBlank()) {
            deviceFingerprint.challengeNonce = nonce
            attestHeader = runCatching { apiClient.buildAttestHeader(nonce) }.getOrNull()
        }

        if (attestHeader != null) {
            observer.log(
                TerminalLevel.GOOD,
                "attestation signed · nonce ${nonce.take(18)}… · ${attestHeader.length}C"
            )
            observer.step(
                LoginStep.ATTEST, StepStatus.DONE,
                "HTTP ${keystore?.code() ?: 0} · signed"
            )
            executedSteps += "2. keystore (signed nonce=${nonce.take(8)})"
        } else {
            observer.log(TerminalLevel.WARN, "attestation unavailable — continuing unattested")
            observer.step(LoginStep.ATTEST, StepStatus.DONE, "unattested")
            executedSteps += "2. keystore (unattested)"
        }
        delay(300)

        // ── 3. SUBMIT ───────────────────────────────────────────────
        observer.step(LoginStep.SUBMIT, StepStatus.RUNNING, "encrypting password")
        observer.log(TerminalLevel.INFO, "#PWD_INSTAGRAM:4 · AES-256-GCM key + RSA wrap")
        observer.log(TerminalLevel.INFO, "POST /api/v1/accounts/login/")

        val response = apiClient.legacyLogin(user, password, attestHeader)
        val body = readRawResponse(response)
        executedSteps += "3. accounts/login (HTTP ${response.code()})"

        observer.log(
            TerminalLevel.INFO,
            "← HTTP ${response.code()} · ${body.length} bytes"
        )

        if (response.code() == 0) {
            observer.step(LoginStep.SUBMIT, StepStatus.FAILED, "network error")
            throw AuthFailedException("Network unreachable: $body")
        }

        if (body.isNotBlank()) {
            observer.log(
                if (response.isSuccessful) TerminalLevel.GOOD else TerminalLevel.ERROR,
                body.take(400)
            )
        }

        extractTwoFactorIdentifier(body)?.let { id ->
            observer.step(LoginStep.SUBMIT, StepStatus.FAILED, "2FA required")
            throw TwoFactorRequiredException(id)
        }

        if (deviceFingerprint.sessionId.isBlank()) {
            val msg = describeFailure(response.code(), body)
            observer.step(LoginStep.SUBMIT, StepStatus.FAILED, "HTTP ${response.code()}")
            throw AuthFailedException(msg)
        }

        val (pk, name) = extractLoggedInUser(body) ?: (deviceFingerprint.dsUserId to user)
        deviceFingerprint.selfUserId = pk
        deviceFingerprint.selfUsername = name
        deviceFingerprint.selfUsername = name

        observer.log(TerminalLevel.GOOD, "sessionid acquired · ds_user_id=$pk")
        observer.step(LoginStep.SUBMIT, StepStatus.DONE, "HTTP ${response.code()} · session minted")
        delay(280)

        // ── 4. SESSION ──────────────────────────────────────────────
        observer.step(LoginStep.SESSION, StepStatus.RUNNING, "accounts/current_user")
        observer.log(TerminalLevel.INFO, "GET /api/v1/accounts/current_user/")

        val probe = runCatching { apiClient.currentUserRaw() }.getOrNull()
        val probeCode = probe?.code() ?: 0

        if (probeCode == 401 || probeCode == 403) {
            observer.step(LoginStep.SESSION, StepStatus.FAILED, "HTTP $probeCode")
            throw AuthFailedException("Session was rejected by Instagram (HTTP $probeCode)")
        }

        observer.log(
            if (probe?.isSuccessful == true) TerminalLevel.GOOD else TerminalLevel.WARN,
            "current_user → HTTP $probeCode"
        )
        observer.step(
            LoginStep.SESSION, StepStatus.DONE,
            if (probe?.isSuccessful == true) "verified" else "HTTP $probeCode"
        )
        executedSteps += "4. current_user (HTTP $probeCode)"

        return LoginResult(
            success = true,
            sessionId = deviceFingerprint.sessionId,
            userId = pk.ifBlank { deviceFingerprint.dsUserId },
            username = name,
            was2faFlow = false
        )
    }

    // ---------------------------------------------------------------- profile

    suspend fun fetchProfile(
        observer: LoginObserver = NoopLoginObserver
    ): OwnProfileData {
        val steps = executedSteps.toMutableList()

        // ── 5. PROFILE ──────────────────────────────────────────────
        observer.step(LoginStep.PROFILE, StepStatus.RUNNING, "resolving account")
        observer.log(TerminalLevel.INFO, "GET /api/v1/multiple_accounts/get_account_family/")

        val family = runCatching { apiClient.getAccountFamily() }.getOrNull()?.body()
        val account = family?.current_account
            ?: family?.main_accounts?.firstOrNull()
            ?: family?.child_accounts?.firstOrNull()

        var userId = account?.user_id
            ?: account?.pk
            ?: deviceFingerprint.selfUserId.ifBlank { deviceFingerprint.dsUserId }

        if (userId.isBlank()) {
            observer.step(LoginStep.PROFILE, StepStatus.FAILED, "user id unresolved")
            throw AuthFailedException("Could not resolve your user id — session may be partial")
        }
        deviceFingerprint.selfUserId = userId
        account?.username?.takeIf { it.isNotBlank() }?.let { deviceFingerprint.selfUsername = it }
        val avatarFallback = account?.profile_pic_url.orEmpty()
        steps += "5. account_family (pk=$userId)"

        delay(200)
        observer.log(TerminalLevel.INFO, "GET /api/v1/users/$userId/info/")
        val info = runCatching { apiClient.getUserInfo(userId) }.getOrNull()
        val detail: ProfileUserDetail? = info?.body()?.user
        val resolvedUsername = detail?.username?.takeIf { it.isNotBlank() }
            ?: deviceFingerprint.selfUsername
        deviceFingerprint.selfUsername = resolvedUsername
        steps += "6. users/$userId/info (HTTP ${info?.code() ?: 0})"

        observer.log(
            if (info?.isSuccessful == true) TerminalLevel.GOOD else TerminalLevel.WARN,
            "@$resolvedUsername · ${detail?.follower_count ?: 0} followers"
        )
        observer.step(LoginStep.PROFILE, StepStatus.DONE, "@$resolvedUsername")
        delay(200)

        // ── 6. MEDIA ────────────────────────────────────────────────
        observer.step(LoginStep.MEDIA, StepStatus.RUNNING, "loading posts")
        observer.log(TerminalLevel.INFO, "GET /api/v1/feed/user/$userId/")

        val feed = runCatching { apiClient.getUserFeed(userId) }.getOrNull()
        val posts: List<MediaFeedItem> = feed?.body()?.items.orEmpty()
        steps += "7. feed/user (HTTP ${feed?.code() ?: 0}, ${posts.size} posts)"

        delay(180)
        observer.log(TerminalLevel.INFO, "GET /api/v1/highlights/$userId/highlights_tray/")
        val highlightsResp = runCatching { apiClient.getHighlightsTray(userId) }.getOrNull()
        val highlights: List<HighlightReelItem> = highlightsResp?.body()?.tray.orEmpty()
        steps += "8. highlights_tray (HTTP ${highlightsResp?.code() ?: 0}, ${highlights.size})"

        val b64 = deviceFingerprint.selfUserIdB64
        val textFeed = runCatching { apiClient.getTextFeed(b64) }.getOrNull()
        val textFeedCount = textFeed?.body()?.count ?: 0

        observer.log(
            TerminalLevel.GOOD,
            "${posts.size} posts · ${highlights.size} highlights · $textFeedCount text posts"
        )
        observer.step(
            LoginStep.MEDIA, StepStatus.DONE,
            "${posts.size} posts · ${highlights.size} highlights"
        )

        return OwnProfileData(
            userId = userId,
            userIdB64 = b64,
            username = resolvedUsername,
            fullName = detail?.full_name.orEmpty(),
            biography = detail?.biography.orEmpty(),
            avatarUrl = detail?.bestAvatarUrl?.takeIf { it.isNotBlank() } ?: avatarFallback,
            mediaCount = detail?.media_count ?: posts.size,
            followerCount = detail?.follower_count ?: 0,
            followingCount = detail?.following_count ?: 0,
            isPrivate = detail?.is_private ?: false,
            isVerified = detail?.is_verified ?: false,
            is2faEnabled = deviceFingerprint.is2faEnabled,
            category = detail?.category,
            pronouns = detail?.pronouns.orEmpty(),
            externalUrl = detail?.external_url,
            bioLinks = detail?.bio_links.orEmpty(),
            highlights = highlights,
            posts = posts,
            textFeedCount = textFeedCount,
            executedFlowName = "Password login → profile",
            executedSteps = steps
        )
    }

    // ---------------------------------------------------------------- helpers

    private fun readRawResponse(resp: Response<ResponseBody>?): String {
        if (resp == null) return ""
        return try {
            resp.body()?.string() ?: resp.errorBody()?.string() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractLoggedInUser(raw: String): Pair<String, String>? {
        if (raw.isBlank()) return null
        return runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            val user = obj["logged_in_user"]?.jsonObject ?: return@runCatching null
            val pk = user["pk"]?.jsonPrimitive?.content
                ?: user["pk_id"]?.jsonPrimitive?.content
                ?: return@runCatching null
            val name = user["username"]?.jsonPrimitive?.content ?: ""
            if (pk.isBlank()) null else (pk to name)
        }.getOrNull()
    }

    private fun extractTwoFactorIdentifier(raw: String): String? {
        if (raw.isBlank()) return null
        return runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            if (obj["two_factor_required"]?.jsonPrimitive?.content != "true" &&
                obj["two_factor_info"] == null
            ) return@runCatching null
            obj["two_factor_info"]?.jsonObject
                ?.get("two_factor_identifier")?.jsonPrimitive?.content
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun describeFailure(code: Int, raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("needs_upgrade") ->
                "Instagram requires a newer client build for this account/network. " +
                        "Client presents ${Constants.IG_VERSION} (${Constants.IG_VERSION_CODE})."

            lower.contains("bad_password") || lower.contains("incorrect password") ->
                "Incorrect password — or this device is not yet trusted for password auth."

            lower.contains("invalid_user") ->
                "That username doesn't exist, or Instagram is silently blocking this network."

            lower.contains("challenge_required") ->
                "Instagram wants to verify this login. Approve it in the official app, then retry."

            lower.contains("please wait a few minutes") || code == 429 ->
                "Rate limited. Wait several minutes before trying again — do not repeat."

            code == 400 -> "Instagram rejected the request: ${raw.take(200)}"
            code == 401 || code == 403 -> "Instagram refused the login (HTTP $code)."
            else -> "Login failed (HTTP $code): ${raw.take(200)}"
        }
    }
}