package dev.animeshvarma.aetherion.bloks

import dev.animeshvarma.aetherion.network.InstagramApiClient
import dev.animeshvarma.aetherion.network.interceptors.LoggingInterceptor
import dev.animeshvarma.aetherion.network.models.LoginResponse
import dev.animeshvarma.aetherion.util.CookieParser
import dev.animeshvarma.aetherion.util.DeviceFingerprint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response

class TwoFactorRequiredException(val twoFactorIdentifier: String) :
    Exception("Two-factor authentication required")

class BloksLoginOrchestrator(
    private val apiClient: InstagramApiClient,
    private val deviceFingerprint: DeviceFingerprint
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Bootstrap requests that seed mid/csrftoken/ig_did cookies. */
    suspend fun bootstrap() {
        coerceResponse(apiClient.readMsisdnHeader())
        coerceResponse(apiClient.qeSync())
        coerceResponse(apiClient.launcherSync())
    }

    suspend fun login(username: String, password: String): LoginResult {
        bootstrap()
        val resp = apiClient.login(username, password)
        return resolveLoginResponse(resp, username)
    }

    suspend fun submitTwoFactor(
        username: String,
        twoFactorIdentifier: String,
        code: String
    ): LoginResult {
        val resp = apiClient.twoFactorLogin(username, twoFactorIdentifier, code)
        return resolveLoginResponse(resp, username)
    }

    private fun resolveLoginResponse(
        resp: Response<LoginResponse>,
        username: String
    ): LoginResult {
        if (!resp.isSuccessful) {
            val err = resp.errorBody()?.string() ?: "HTTP ${resp.code()}"
            throw Exception("Instagram login failed (${resp.code()}): $err")
        }
        val body = resp.body() ?: throw Exception("Empty login response")

        // 2FA / challenge (unchanged)
        body.two_factor_required?.takeIf { it }?.let {
            val id = body.two_factor_info?.two_factor_identifier
                ?: body.two_step_verification_context
                ?: throw Exception("Two-factor required but no identifier returned")
            throw TwoFactorRequiredException(id)
        }
        if (body.status != "ok") {
            throw Exception("Instagram login rejected: ${body.message ?: body.error_type ?: "unknown"}")
        }
        body.logged_in_user ?: throw Exception("Login did not return a user")

        // ---- Session: modern IG returns it in ig-set-authorization, not Set-Cookie ----
        val auth = resp.headers()["ig-set-authorization"]
        val authSession = auth?.let(::parseAuthToken)

        val sessionId = authSession?.sessionId
            ?: CookieParser.extractSession(resp.headers())?.sessionId
            ?: deviceFingerprint.sessionId.ifBlank {
                logCookies(resp)
                throw Exception(
                    "Login succeeded but no sessionid was present in " +
                            "ig-set-authorization or Set-Cookie headers."
                )
            }

        // Persist for authenticated calls
        if (auth != null) deviceFingerprint.igAuth = auth
        authSession?.let {
            deviceFingerprint.sessionId = it.sessionId
            deviceFingerprint.dsUserId = it.dsUserId
        }

        val userId = body.logged_in_user?.pk?.toString()
            ?: authSession?.dsUserId
            ?: deviceFingerprint.dsUserId

        return LoginResult(
            success = true,
            sessionId = sessionId,
            userId = userId,
            username = body.logged_in_user?.username ?: username
        )
    }

    /** Decodes "Bearer IGT:2:<base64url json>" -> sessionid / ds_user_id / rur. */
    private fun parseAuthToken(auth: String): CookieParser.IgSession? {
        val marker = "IGT:2:"
        val idx = auth.indexOf(marker)
        if (idx < 0) return null
        val b64 = auth.substring(idx + marker.length)
        return try {
            val raw = android.util.Base64.decode(b64, android.util.Base64.URL_SAFE)
            val jsonStr = String(raw, Charsets.UTF_8)
            val obj = json.parseToJsonElement(jsonStr).jsonObject
            val ds = obj["ds_user_id"]?.jsonPrimitive?.content ?: ""
            val ses = obj["sessionid"]?.jsonPrimitive?.content ?: return null
            CookieParser.IgSession(
                sessionId = java.net.URLDecoder.decode(ses, "UTF-8"),
                dsUserId = ds,
                rur = ""
            )
        } catch (e: Exception) {
            LoggingInterceptor.logNetwork("LOGIN", "parseAuthToken failed: ${e.message}")
            null
        }
    }

    private fun logCookies(resp: Response<LoginResponse>) {
        val all = resp.headers().values("Set-Cookie")
        LoggingInterceptor.logNetwork("LOGIN", "Set-Cookie headers (${all.size}): $all")
        val auth = resp.headers()["ig-set-authorization"]
        LoggingInterceptor.logNetwork("LOGIN", "ig-set-authorization: ${auth ?: "(none)"}")
    }

    private fun <T> coerceResponse(response: Response<T>): T {
        if (response.isSuccessful) return response.body()!!
        throw Exception("HTTP ${response.code()}: ${response.errorBody()?.string()}")
    }
}

enum class LoginStep { BOOTSTRAP, AUTHENTICATE, TWO_FACTOR, COMPLETE, ERROR }

data class LoginResult(
    val success: Boolean,
    val sessionId: String,
    val userId: String,
    val username: String
)