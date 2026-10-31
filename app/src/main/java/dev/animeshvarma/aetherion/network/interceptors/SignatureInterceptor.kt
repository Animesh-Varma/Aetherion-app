package dev.animeshvarma.aetherion.network.interceptors

import dev.animeshvarma.aetherion.crypto.PasswordEncryptor
import dev.animeshvarma.aetherion.util.ConnectionInfo
import dev.animeshvarma.aetherion.util.Constants
import dev.animeshvarma.aetherion.util.CookieParser
import dev.animeshvarma.aetherion.util.DeviceFingerprint
import dev.animeshvarma.aetherion.util.DeviceProfile
import dev.animeshvarma.aetherion.util.ServerClock
import okhttp3.Interceptor
import okio.Buffer

class SignatureInterceptor(
    private val deviceFingerprint: DeviceFingerprint,
    private val deviceProfile: DeviceProfile,
    private val connectionInfo: ConnectionInfo
) : Interceptor {

    companion object {
        const val FORM = "application/x-www-form-urlencoded; charset=UTF-8"
    }

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val original = chain.request()
        val path = original.url.encodedPath

        val isPreSessionEndpoint =
            path.contains("/attestation/") ||
                    path.contains("/accounts/login/") ||
                    path.contains("/accounts/two_factor_login/") ||
                    path.contains("/bloks/apps/com.bloks.www.caa.login") ||
                    path.contains("/bloks/async_action/com.bloks.www.caa.") ||
                    path.contains("/bloks/async_action/com.bloks.www.bloks.caa.") ||
                    path.contains("/bloks/apps/com.bloks.www.two_") ||
                    path.contains("/bloks/async_action/com.bloks.www.two_step_")

        val rawBodyBytes: ByteArray = original.body?.let { body ->
            val buf = Buffer()
            body.writeTo(buf)
            buf.readByteArray()
        } ?: ByteArray(0)

        val locale = deviceProfile.locale
        val isPostSession = !isPreSessionEndpoint && deviceFingerprint.igAuth.isNotBlank()

        val builder = original.newBuilder()
            .header("accept-language", deviceProfile.acceptLanguage)
            .header("user-agent", deviceProfile.userAgent)
            .header("x-ig-app-id", Constants.FB_ANALYTICS_APP_ID)
            .header("x-ig-device-id", deviceFingerprint.deviceId)
            .header("x-ig-family-device-id", deviceFingerprint.familyDeviceId)
            .header("x-ig-android-id", deviceFingerprint.androidId)
            .header("x-ig-app-locale", locale)
            .header("x-ig-device-locale", locale)
            .header("x-ig-mapped-locale", locale)
            .header("x-ig-timezone-offset", deviceProfile.timeZoneOffsetSeconds.toString())
            .header("x-ig-capabilities", Constants.X_IG_CAPABILITIES)
            .header("x-bloks-version-id", Constants.BLOKS_VERSION)
            .header("x-bloks-is-layout-rtl", deviceProfile.isLayoutRtl.toString())
            .header(
                "x-bloks-prism-button-version",
                if (isPostSession) "INDIGO_PRIMARY_BORDERED_SECONDARY" else "CONTROL"
            )
            .header("x-bloks-prism-colors-enabled", isPostSession.toString())
            .header("x-bloks-prism-extended-palette-gray", isPostSession.toString())
            .header("x-bloks-prism-extended-palette-indigo", "true")
            .header("x-bloks-prism-extended-palette-polish-enabled", isPostSession.toString())
            .header("x-bloks-prism-extended-palette-red", "true")
            .header("x-bloks-prism-extended-palette-rest-of-colors", "true")
            .header("x-bloks-prism-font-enabled", "true")
            .header("x-bloks-prism-indigo-link-version", "1")
            .header("x-ig-bloks-serialize-payload", "true")
            .header("x-ig-is-adaptive-ui", "false")
            .header("x-ig-is-foldable", "false")
            .header("x-mid", deviceFingerprint.mid)
            .header("x-pigeon-session-id", deviceFingerprint.pigeonSessionId)
            .header("x-tigon-is-retry", "False")
            .header("x-fb-conn-uuid-client", DeviceFingerprint.randomHex(32))
            .header("x-fb-http-engine", Constants.HTTP_ENGINE)
            .header("priority", "u=3")

        if (original.header("x-fb-friendly-name") == null) {
            val friendlyName = when {
                path.contains("/bloks/async_action/") -> {
                    val act = path.substringAfter("/bloks/async_action/").trimEnd('/')
                    "IgApi: bloks/async_action/$act/"
                }
                path.contains("/bloks/apps/") -> {
                    val app = path.substringAfter("/bloks/apps/").trimEnd('/')
                    "BloksScreenQuery: $app"
                }
                else -> {
                    val clean = path.removePrefix("/api/v1/")
                    "IgApi: $clean"
                }
            }
            builder.header("x-fb-friendly-name", friendlyName)
        }

        // Only send a client-endpoint when we actually know the real value.
        if (original.header("x-ig-client-endpoint") == null && !path.contains("/bloks/async_action/")) {
            when {
                path.contains("login_homepage") ->
                    builder.header("x-ig-client-endpoint", "com.bloks.www.caa.login.login_homepage")
                isPostSession && !path.contains("/accounts/") ->
                    builder.header("x-ig-client-endpoint", Constants.FEED_CLIENT_ENDPOINT)
            }
        }

        if (isPreSessionEndpoint) {
            builder.header("ig-intended-user-id", "0")
            builder.header("x-ig-www-claim", "0")
        } else {
            builder.header(
                "x-ig-www-claim",
                deviceFingerprint.computeWwwClaim(payloadBytes = rawBodyBytes, isPreSession = false)
            )
            builder.header("x-ig-device-timezone", deviceProfile.timeZoneName)
            builder.header("x-ig-device-languages", deviceProfile.deviceLanguagesJson)

            if (deviceFingerprint.igAuth.isNotBlank()) {
                builder.header("authorization", deviceFingerprint.igAuth)
            }
            if (deviceFingerprint.dsUserId.isNotBlank()) {
                builder.header("ig-intended-user-id", deviceFingerprint.dsUserId)
                builder.header("ig-u-ds-user-id", deviceFingerprint.dsUserId)
            }
            if (deviceFingerprint.rur.isNotBlank()) {
                builder.header("ig-u-rur", deviceFingerprint.rur)
            }
        }

        if (original.method == "POST") {
            if (original.header("content-type") == null) builder.header("content-type", FORM)
            // Was missing entirely; Instagram cross-checks body _csrftoken against this.
            builder.header("x-csrftoken", deviceFingerprint.csrfToken)
        }

        val response = chain.proceed(builder.build())

        ServerClock.sync(response.headers["Date"])
        PasswordEncryptor.syncFromHeaders(
            response.headers["ig-set-password-encryption-key-id"],
            response.headers["ig-set-password-encryption-pub-key"],
            deviceFingerprint
        )

        response.headers["ig-set-x-mid"]?.takeIf { it.isNotBlank() }?.let { deviceFingerprint.mid = it }
        response.headers["ig-set-ig-u-rur"]?.takeIf { it.isNotBlank() }?.let { deviceFingerprint.rur = it.trim('"') }
        response.headers["ig-set-ig-u-ds-user-id"]?.takeIf { it.isNotBlank() }?.let { deviceFingerprint.dsUserId = it }
        response.headers["x-ig-set-www-claim"]?.takeIf { it.isNotBlank() }?.let { deviceFingerprint.serverWwwClaim = it }
        response.headers["ig-set-authorization"]?.takeIf { it.isNotBlank() }?.let { deviceFingerprint.igAuth = it }

        CookieParser.extractSession(response.headers)?.let { s ->
            if (s.sessionId.isNotBlank()) deviceFingerprint.sessionId = s.sessionId
            if (s.dsUserId.isNotBlank()) deviceFingerprint.dsUserId = s.dsUserId
            if (s.rur.isNotBlank()) deviceFingerprint.rur = s.rur
        }

        return response
    }
}