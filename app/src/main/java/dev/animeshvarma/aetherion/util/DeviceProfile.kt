package dev.animeshvarma.aetherion.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.telephony.TelephonyManager
import java.util.Locale
import java.util.TimeZone

class DeviceProfile(private val context: Context) {

    val igVersionName: String = Constants.IG_VERSION
    val igVersionCode: String = Constants.IG_VERSION_CODE

    val locale: String
        get() {
            val loc = currentLocale()
            val lang = loc.language.ifBlank { "en" }.lowercase(Locale.ROOT)
            val country = loc.country.ifBlank { "US" }.uppercase(Locale.ROOT)
            return "${lang}_${country}"
        }

    val countryIso: String
        get() {
            // 1. SIM
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            tm?.simCountryIso?.takeIf { it.length == 2 }?.let { return it.uppercase(Locale.ROOT) }
            tm?.networkCountryIso?.takeIf { it.length == 2 }?.let { return it.uppercase(Locale.ROOT) }
            // 2. Timezone region (Asia/Calcutta -> IN, Asia/Kolkata -> IN)
            val tz = TimeZone.getDefault()
            if (tz.id.contains("/")) {
                val region = tz.id.substringAfter("/").substringBefore("/")
                TIMEZONE_REGIONS[region]?.let { return it }
            }
            // 3. Locale
            return currentLocale().country.ifBlank { "US" }.uppercase(Locale.ROOT)
        }

    private val TIMEZONE_REGIONS = mapOf(
        "Calcutta" to "IN", "Kolkata" to "IN", "Karachi" to "PK", "Dhaka" to "BD",
        "Colombo" to "LK", "Kathmandu" to "NP", "Dubai" to "AE", "Riyadh" to "SA",
        "London" to "GB", "Berlin" to "DE", "Paris" to "FR", "New_York" to "US",
        "Los_Angeles" to "US", "Chicago" to "US", "Toronto" to "CA", "Sao_Paulo" to "BR",
        "Singapore" to "SG", "Jakarta" to "ID", "Manila" to "PH", "Bangkok" to "TH",
        "Tokyo" to "JP", "Seoul" to "KR", "Sydney" to "AU"
    )

    /** Only used for the `country_codes` field of /accounts/login/. */
    val countryCallingCode: Int
        get() = COUNTRY_CODES[countryIso] ?: 1

    val acceptLanguage: String
        get() {
            val loc = currentLocale()
            val tag = "${loc.language.ifBlank { "en" }.lowercase(Locale.ROOT)}-" +
                    "${loc.country.ifBlank { "US" }.uppercase(Locale.ROOT)}"
            return if (tag.equals("en-US", ignoreCase = true)) "en-US" else "$tag, en-US"
        }

    val deviceLanguagesJson: String
        get() {
            val loc = currentLocale()
            val tag = "${loc.language.ifBlank { "en" }.lowercase(Locale.ROOT)}-" +
                    "${loc.country.ifBlank { "US" }.uppercase(Locale.ROOT)}"
            return "{\"system_languages\":\"$tag\"}"
        }

    val timeZoneName: String
        get() = TimeZone.getDefault().id

    val timeZoneOffsetSeconds: Int
        get() = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000

    val isLayoutRtl: Boolean
        get() = context.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL

    val userAgent: String
        get() {
            val rawSdk = Build.VERSION.SDK_INT
            val sdk = if (rawSdk > 35) 35 else rawSdk
            val release = if (rawSdk > 35) "15" else Build.VERSION.RELEASE.orEmpty().ifBlank { "15" }
            val dm = context.resources.displayMetrics
            val dpi = "${dm.densityDpi}dpi"
            val res = "${dm.widthPixels}x${dm.heightPixels}"
            val manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { "Google" }
            val model = Build.MODEL.orEmpty().ifBlank { "Pixel 9" }
            val device = Build.DEVICE.orEmpty().ifBlank { "tokay" }
            val board = Build.BOARD.ifBlank { Build.HARDWARE.orEmpty() }.ifBlank { "tokay" }
            return "Instagram $igVersionName Android " +
                    "($sdk/$release; $dpi; $res; $manufacturer; $model; $device; $board; $locale; $igVersionCode)"
        }

    fun batteryLevel(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 50
    }

    fun isCharging(): Boolean {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return bm?.isCharging ?: false
    }

    fun isDarkMode(): Boolean {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    fun hasCameraPermission(): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun currentLocale(): Locale {
        val locales = context.resources.configuration.locales
        return if (!locales.isEmpty) locales[0] else Locale.getDefault()
    }

    companion object {
        private val COUNTRY_CODES = mapOf(
            "US" to 1, "CA" to 1, "IN" to 91, "GB" to 44, "AU" to 61, "DE" to 49,
            "FR" to 33, "IT" to 39, "ES" to 34, "NL" to 31, "BE" to 32, "BR" to 55,
            "MX" to 52, "AR" to 54, "CL" to 56, "CO" to 57, "AE" to 971, "SA" to 966,
            "QA" to 974, "KW" to 965, "IL" to 972, "EG" to 20, "ZA" to 27, "NG" to 234,
            "KE" to 254, "PK" to 92, "BD" to 880, "LK" to 94, "NP" to 977, "CN" to 86,
            "HK" to 852, "TW" to 886, "JP" to 81, "KR" to 82, "SG" to 65, "MY" to 60,
            "TH" to 66, "VN" to 84, "PH" to 63, "ID" to 62, "NZ" to 64, "IE" to 353,
            "PT" to 351, "CH" to 41, "AT" to 43, "SE" to 46, "NO" to 47, "DK" to 45,
            "FI" to 358, "PL" to 48, "CZ" to 420, "RO" to 40, "GR" to 30, "TR" to 90,
            "RU" to 7, "UA" to 380
        )
    }
}