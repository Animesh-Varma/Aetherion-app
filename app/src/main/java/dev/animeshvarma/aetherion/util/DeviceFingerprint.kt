package dev.animeshvarma.aetherion.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class DeviceFingerprint(private val context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    // ---------------- Identity ----------------

    val deviceId: String
        get() = getOrCreate("x_ig_device_id") { UUID.randomUUID().toString() }

    val uuid: String
        get() = deviceId

    /** `guid` in the login payload. */
    val guid: String
        get() = deviceId

    val familyDeviceId: String
        get() = getOrCreate("x_ig_family_device_id") { UUID.randomUUID().toString() }

    val phoneId: String
        get() = familyDeviceId

    val androidId: String
        @SuppressLint("HardwareIds")
        get() = getOrCreate("x_ig_android_id") {
            val sysId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            } catch (_: Exception) {
                null
            }
            val hex = if (!sysId.isNullOrBlank() && sysId.length == 16) sysId.lowercase()
            else randomHex(16)
            "android-$hex"
        }

    /** `adid` in the login payload. */
    val adid: String
        get() = getOrCreate("adid") { UUID.randomUUID().toString() }

    /** X-Pigeon-Session-Id — one per install, not per request. */
    val pigeonSessionId: String
        get() = getOrCreate("pigeon_session_id") { "UFS-${UUID.randomUUID()}-1" }

    var waterfallId: String
        get() = getOrCreate("waterfall_id") { UUID.randomUUID().toString() }
        set(value) = prefs.edit().putString("waterfall_id", value).apply()

    var mid: String
        get() = getOrCreate("x_mid") { generate28CharMid() }
        set(value) {
            if (value.isNotBlank() && value != "null") {
                prefs.edit().putString("x_mid", value).apply()
            }
        }

    val metaUsdid: String
        get() = getOrCreate("x_meta_usdid_266") {
            val raw = ByteArray(199)
            secureRandom.nextBytes(raw)
            Base64.encodeToString(raw, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                .take(266).padEnd(266, 'A')
        }

    val metaZca: String
        get() = getOrCreate("x_meta_zca_691") {
            val raw = ByteArray(518)
            secureRandom.nextBytes(raw)
            Base64.encodeToString(raw, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                .take(691).padEnd(691, 'A')
        }

    var metaZca2: String
        get() = getOrCreate("x_meta_zca2_130") {
            val raw = ByteArray(97)
            secureRandom.nextBytes(raw)
            Base64.encodeToString(raw, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                .take(130).padEnd(130, 'A')
        }
        set(value) {
            if (value.length >= 100 && value != "null") {
                prefs.edit().putString("x_meta_zca2_130", value).apply()
            }
        }

    var caaAccessContext: String
        get() = prefs.getString("caa_access_context", "") ?: ""
        set(value) {
            if (value.isNotBlank() && value != "null") {
                prefs.edit().putString("caa_access_context", value).apply()
            }
        }

    val deviceHmacKeyHex: String
        get() = getOrCreate("device_hmac_key_hex") { randomHex(64) }

    var keyHash: String
        get() = prefs.getString("keystore_key_hash", "") ?: ""
        set(value) = prefs.edit().putString("keystore_key_hash", value).apply()

    var challengeNonce: String
        get() = prefs.getString("challenge_nonce", "") ?: ""
        set(value) = prefs.edit().putString("challenge_nonce", value).apply()

    var preSessionState: String
        get() = prefs.getString("pre_session_state", "") ?: ""
        set(value) = prefs.edit().putString("pre_session_state", value).apply()

    var twoFactorState: String
        get() = prefs.getString("two_factor_state", "") ?: ""
        set(value) = prefs.edit().putString("two_factor_state", value).apply()

    var twoFactorNavState: String
        get() = prefs.getString("two_factor_nav_state", "") ?: ""
        set(value) = prefs.edit().putString("two_factor_nav_state", value).apply()

    var totpFormState: String
        get() = prefs.getString("totp_form_state", "") ?: ""
        set(value) = prefs.edit().putString("totp_form_state", value).apply()

    var allowState: String
        get() = prefs.getString("allow_state", "") ?: ""
        set(value) = prefs.edit().putString("allow_state", value).apply()

    // ---------------- Cookies / session ----------------

    /**
     * Was `prefs.getString("csrftoken", "") ?: ""` — returned "" until the server
     * set the cookie, so `_csrftoken` was always empty. Now generates on first use.
     */
    var csrfToken: String
        get() {
            val existing = prefs.getString("csrftoken", null)
            if (!existing.isNullOrBlank() && existing != "null") return existing
            val created = genToken(32)
            prefs.edit().putString("csrftoken", created).apply()
            return created
        }
        set(value) {
            if (value.isNotBlank()) prefs.edit().putString("csrftoken", value).apply()
        }

    var igDid: String
        get() = getOrCreate("ig_did") { UUID.randomUUID().toString().uppercase() }
        set(value) = prefs.edit().putString("ig_did", value).apply()

    var sessionId: String
        get() = prefs.getString("sessionid", "") ?: ""
        set(value) = prefs.edit().putString("sessionid", value).apply()

    var dsUserId: String
        get() = prefs.getString("ds_user_id", "") ?: ""
        set(value) = prefs.edit().putString("ds_user_id", value).apply()

    var rur: String
        get() = prefs.getString("ig_u_rur", "") ?: ""
        set(value) {
            if (value.isNotBlank()) prefs.edit().putString("ig_u_rur", value).apply()
        }

    var serverWwwClaim: String
        get() = prefs.getString("server_www_claim", "0") ?: "0"
        set(value) {
            if (value.isNotBlank()) prefs.edit().putString("server_www_claim", value).apply()
        }

    var selfUserId: String
        get() = prefs.getString("self_user_id", "")?.takeIf { it.isNotBlank() } ?: dsUserId
        set(value) {
            if (value.isNotBlank()) prefs.edit().putString("self_user_id", value).apply()
        }

    val selfUserIdB64: String
        get() {
            val uid = selfUserId.ifBlank { dsUserId }
            if (uid.isBlank()) return ""
            return Base64.encodeToString(uid.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }

    var selfUsername: String
        get() = prefs.getString("self_username", "") ?: ""
        set(value) {
            if (value.isNotBlank()) prefs.edit().putString("self_username", value).apply()
        }

    var is2faEnabled: Boolean
        get() = prefs.getBoolean("self_is_2fa_enabled", false)
        set(value) = prefs.edit().putBoolean("self_is_2fa_enabled", value).apply()

    var pwdKeyId: String
        get() = prefs.getString("pwd_key_id", "") ?: ""
        set(value) = prefs.edit().putString("pwd_key_id", value).apply()

    var pwdPubKey: String
        get() = prefs.getString("pwd_pub_key", "") ?: ""
        set(value) = prefs.edit().putString("pwd_pub_key", value).apply()

    var igAuth: String
        get() {
            val stored = prefs.getString("ig_auth", "") ?: ""
            if (stored.startsWith("Bearer IGT:2:")) return stored
            if (sessionId.isNotBlank() && dsUserId.isNotBlank()) {
                return buildBearerToken(dsUserId, sessionId).also {
                    prefs.edit().putString("ig_auth", it).apply()
                }
            }
            return stored
        }
        set(value) {
            prefs.edit().putString("ig_auth", value).apply()
            parseAndPersistAuthHeader(value)
        }

    // ---------------- Helpers ----------------

    fun hasActiveSession(): Boolean =
        sessionId.isNotBlank() && (dsUserId.isNotBlank() || selfUserId.isNotBlank())

    fun buildBearerToken(dsUser: String, session: String): String {
        val jsonPayload = buildJsonObject {
            put("ds_user_id", dsUser)
            put("sessionid", session)
        }.toString()
        val b64 = Base64.encodeToString(jsonPayload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "Bearer IGT:2:$b64"
    }

    fun parseAndPersistAuthHeader(authHeader: String) {
        val marker = "IGT:2:"
        val idx = authHeader.indexOf(marker)
        if (idx < 0) return
        val b64 = authHeader.substring(idx + marker.length).trim()
        try {
            val raw = try {
                Base64.decode(b64, Base64.URL_SAFE)
            } catch (_: Exception) {
                Base64.decode(b64, Base64.DEFAULT)
            }
            val obj = json.parseToJsonElement(String(raw, Charsets.UTF_8)).jsonObject
            val ds = obj["ds_user_id"]?.jsonPrimitive?.content ?: ""
            val ses = obj["sessionid"]?.jsonPrimitive?.content ?: ""
            if (ds.isNotBlank()) {
                dsUserId = ds
                if (selfUserId.isBlank()) selfUserId = ds
            }
            if (ses.isNotBlank()) sessionId = URLDecoder.decode(ses, "UTF-8")
        } catch (_: Exception) {
        }
    }

    fun computeWwwClaim(payloadBytes: ByteArray, isPreSession: Boolean): String {
        if (isPreSession || sessionId.isBlank()) return "0"
        if (serverWwwClaim.startsWith("hmac.")) return serverWwwClaim
        return try {
            val keyBytes = deviceHmacKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
            mac.update(sessionId.toByteArray(Charsets.UTF_8))
            mac.update(payloadBytes)
            val b64 = Base64.encodeToString(
                mac.doFinal(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            "hmac.AR0${b64.take(45)}"
        } catch (_: Exception) {
            serverWwwClaim.ifBlank { "0" }
        }
    }

    fun rotateDeviceIdentity() {
        prefs.edit()
            .putString("x_ig_device_id", UUID.randomUUID().toString())
            .putString("x_ig_family_device_id", UUID.randomUUID().toString())
            .putString("waterfall_id", UUID.randomUUID().toString())
            .putString("x_ig_android_id", "android-${randomHex(16)}")
            .remove("csrftoken").remove("ig_did").remove("x_meta_usdid_266")
            .remove("x_meta_zca_691").remove("x_meta_zca2_130")
            .remove("challenge_nonce").remove("caa_access_context")
            .apply()
    }

    fun getAllSavedSecretsSummary(): Map<String, String> = linkedMapOf(
        "x-ig-device-id" to deviceId,
        "x-ig-family-device-id" to familyDeviceId,
        "x-ig-android-id" to androidId,
        "adid" to adid,
        "x-mid" to mid,
        "csrftoken" to "${csrfToken.take(10)}... (${csrfToken.length}c)",
        "pwd_key_id" to pwdKeyId.ifBlank { "(none)" },
        "key_hash (SHA-256)" to keyHash.ifBlank { "(pending)" },
        "challenge_nonce" to challengeNonce.ifBlank { "(none)" },
        "ds_user_id" to dsUserId.ifBlank { "(none)" },
        "self.user_id" to selfUserId.ifBlank { "(none)" },
        "b64(self.user_id)" to selfUserIdB64.ifBlank { "(none)" },
        "sessionid" to if (sessionId.isNotBlank()) "${sessionId.take(12)}..." else "(none)",
        "authorization" to if (igAuth.isNotBlank()) "${igAuth.take(28)}..." else "(none)",
        "ig-u-rur" to rur.ifBlank { "(awaiting server)" },
        "x-ig-www-claim" to serverWwwClaim,
        "x-meta-usdid" to "${metaUsdid.take(18)}... (${metaUsdid.length}c)",
        "x-meta-zca" to "${metaZca.take(18)}... (${metaZca.length}c)",
        "x-meta-zca2" to "${metaZca2.take(18)}... (${metaZca2.length}c)"
    )

    fun clearSession() {
        prefs.edit()
            .remove("sessionid").remove("ds_user_id").remove("ig_auth")
            .remove("self_user_id").remove("self_username")
            .remove("pre_session_state").remove("two_factor_state")
            .remove("two_factor_nav_state").remove("totp_form_state")
            .remove("allow_state").remove("challenge_nonce").remove("ig_u_rur")
            .putString("server_www_claim", "0")
            .apply()
    }

    private fun getOrCreate(key: String, generator: () -> String): String {
        val existing = prefs.getString(key, null)
        if (!existing.isNullOrBlank() && existing != "null") return existing
        val created = generator()
        prefs.edit().putString(key, created).apply()
        return created
    }

    companion object {
        private const val PREFS_NAME = "aetherion_dynamic_secrets"
        private val secureRandom = SecureRandom()

        fun randomHex(len: Int): String {
            val bytes = ByteArray((len + 1) / 2)
            secureRandom.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }.take(len)
        }

        fun genToken(length: Int = 32): String {
            val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            return (1..length).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
        }

        fun generate28CharMid(): String {
            val buf = ByteBuffer.allocate(21)
            buf.putInt((System.currentTimeMillis() / 1000L).toInt())
            buf.put(byteArrayOf(0x00, 0x01, 0x00, 0x01))
            val rand = ByteArray(13)
            secureRandom.nextBytes(rand)
            buf.put(rand)
            return Base64.encodeToString(
                buf.array(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
        }
    }
}