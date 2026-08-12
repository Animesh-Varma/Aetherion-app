package dev.animeshvarma.aetherion.network.interceptors

import dev.animeshvarma.aetherion.util.ConnectionInfo
import dev.animeshvarma.aetherion.util.Constants
import dev.animeshvarma.aetherion.util.DeviceFingerprint
import dev.animeshvarma.aetherion.util.DeviceProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SignatureInterceptor(
    private val deviceFingerprint: DeviceFingerprint,
    private val deviceProfile: DeviceProfile,
    private val connectionInfo: ConnectionInfo
) : Interceptor {

    companion object {
        const val FORM = "application/x-www-form-urlencoded"

        fun buildSignedBody(jsonPayload: String): String =
            "ig_sig_key_version=${Constants.IG_SIGNATURE_KEY_VERSION}" +
                    "&signed_body=${Constants.SIGNATURE_VERSION_LABEL}." +
                    URLEncoder.encode(jsonPayload, "UTF-8")

        fun buildSignedBodyStrict(jsonPayload: String): String {
            val key = Constants.IG_SIGNATURE_KEY_HEX.chunked(2)
                .map { it.toInt(16).toByte() }.toByteArray()
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            val hex = mac.doFinal(jsonPayload.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return "ig_sig_key_version=${Constants.IG_SIGNATURE_KEY_VERSION}" +
                    "&signed_body=${Constants.SIGNATURE_VERSION_LABEL}.$hex." +
                    URLEncoder.encode(jsonPayload, "UTF-8")
        }
    }

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val original = chain.request()
        val builder = original.newBuilder()
            .header("User-Agent", deviceProfile.userAgent)
            .header("X-IG-App-ID", Constants.FB_ANALYTICS_APP_ID)
            .header("X-IG-Device-ID", deviceFingerprint.deviceId)
            .header("X-IG-Android-ID", deviceFingerprint.androidId)
            .header("X-IG-Family-Device-ID", deviceFingerprint.familyDeviceId)
            .header("X-IG-App-Locale", deviceProfile.locale)
            .header("X-IG-Device-Locale", deviceProfile.locale)
            .header("X-IG-TimeZone-Offset", deviceProfile.timeZoneOffsetSeconds.toString())
            .header("X-IG-TimeZone", deviceProfile.timeZoneName)
            .header("Accept-Language", deviceProfile.acceptLanguage)
            .header("X-Bloks-Version-Id", Constants.BLOKS_VERSION)
            .header("X-IG-Capabilities", Constants.X_IG_CAPABILITIES)
            .header("X-IG-Connection-Type", connectionInfo.type())
            .header("X-FB-Net-HNI", connectionInfo.networkOperator())
            .header("X-FB-Connection-Quality", connectionInfo.connectionQuality())
            .header("X-FB-Bandwidth-KBPS", connectionInfo.bandwidthKbps().toString())
            .header("X-IG-App-Startup", "false")

        if (deviceFingerprint.mid.isNotBlank()) builder.header("X-MID", deviceFingerprint.mid)
        if (deviceFingerprint.igAuth.isNotBlank()) {
            builder.header("Authorization", deviceFingerprint.igAuth)
            builder.header("IG-U-IG-Authorization", deviceFingerprint.igAuth)
        }
        val withHeaders = builder.build()

        val body = original.body
        if (original.method != "POST" || body?.contentType()?.toString() != FORM) {
            return chain.proceed(withHeaders)
        }
        val raw = okio.Buffer().apply { body.writeTo(this) }.readUtf8()
        if (raw.contains("signed_body=")) return chain.proceed(withHeaders)

        val params = parseFormBody(raw)
        val jsonPayload = Json.encodeToString(params)
        val signed = buildSignedBody(jsonPayload).toRequestBody(FORM.toMediaType())
        return chain.proceed(withHeaders.newBuilder().post(signed).build())
    }

    private fun parseFormBody(body: String): LinkedHashMap<String, String> {
        val map = LinkedHashMap<String, String>()
        body.split("&").forEach { pair ->
            val i = pair.indexOf('=')
            if (i >= 0) {
                map[URLDecoder.decode(pair.substring(0, i), "UTF-8")] =
                    URLDecoder.decode(pair.substring(i + 1), "UTF-8")
            } else if (pair.isNotEmpty()) {
                map[URLDecoder.decode(pair, "UTF-8")] = ""
            }
        }
        return map
    }
}