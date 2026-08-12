package dev.animeshvarma.aetherion.util

import android.content.Context
import android.provider.Settings
import java.security.SecureRandom
import java.util.UUID
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class DeviceFingerprint(context: Context) {
    private val prefs =
        context.getSharedPreferences("aetherion_identity", Context.MODE_PRIVATE)

    // ---- Native Android id (16 hex) → device_id = "android-" + android_id ----
    val androidId: String = getOrCreate("android_id") {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.length >= 16 }
            ?: randomHex(16)
    }
    val deviceId: String get() = "android-$androidId"

    // ---- Generated once per install, persisted forever after ----
    val uuid: String get() = getOrCreate("uuid") { UUID.randomUUID().toString().lowercase() }
    val phoneId: String get() = getOrCreate("phone_id") { UUID.randomUUID().toString().lowercase() }
    val familyDeviceId: String
        get() = getOrCreate("family_device_id") { UUID.randomUUID().toString().lowercase() }
    val qeDeviceId: String
        get() = getOrCreate("qe_device_id") { UUID.randomUUID().toString().lowercase() }
    val waterfallId: String
        get() = getOrCreate("waterfall_id") { UUID.randomUUID().toString().lowercase() }
    val adid: String get() = getOrCreate("adid") { UUID.randomUUID().toString().lowercase() }

    // ---- Session state (set dynamically by responses) ----
    var mid: String by prefsString("mid", "")
    var csrfToken: String by prefsString("csrf", "")
    var igDid: String by prefsString("ig_did", "")
    var sessionId: String by prefsString("sessionid", "")
    var dsUserId: String by prefsString("ds_user_id", "")
    var rur: String by prefsString("rur", "")
    var igAuth: String by prefsString("ig_auth", "")

    val xMid: String get() = mid

    private fun getOrCreate(key: String, gen: () -> String): String {
        prefs.getString(key, null)?.let { return it }
        val v = gen()
        prefs.edit().putString(key, v).apply()
        return v
    }

    private fun randomHex(n: Int): String {
        val sr = SecureRandom()
        return (1..n).joinToString("") { "%02x".format(sr.nextInt(256)) }
    }

    private fun prefsString(key: String, default: String) =
        object : ReadWriteProperty<Any?, String> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): String =
                prefs.getString(key, default) ?: default
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
                prefs.edit().putString(key, value).apply()
            }
        }

    companion object {
        fun genToken(size: Int = 64): String {
            val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            return buildString { repeat(size) { append(chars[SecureRandom().nextInt(chars.length)]) } }
        }

        fun generateJazoest(phoneId: String): String =
            "2" + phoneId.fold(0) { acc, c -> acc + c.code }.toString()
    }
}