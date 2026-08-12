package dev.animeshvarma.aetherion.util

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import java.util.Locale

/**
 * Builds the Instagram device profile dynamically from native Android sources,
 * so every install presents its own genuine device profile (like the real app).
 */
class DeviceProfile(private val context: Context) {

    val androidVersion: String = Build.VERSION.RELEASE
    val sdkInt: Int = Build.VERSION.SDK_INT
    val manufacturer: String = Build.MANUFACTURER
    val model: String = Build.MODEL
    val device: String = Build.DEVICE
    val hardware: String = Build.HARDWARE
    val cpuAbi: String = Build.SUPPORTED_ABIS.firstOrNull() ?: ""

    private val currentLocale: Locale
        get() = context.resources.configuration.locales[0] ?: Locale.getDefault()

    val locale: String by lazy {
        val l = currentLocale
        listOfNotNull(l.language, l.country).takeIf { it.isNotEmpty() }?.joinToString("_")
            ?: "en_US"
    }

    val countryCode: String by lazy {
        val l = currentLocale
        l.country.ifBlank { "US" }
    }

    private val displayMetrics: DisplayMetrics by lazy {
        DisplayMetrics().also {
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay.getRealMetrics(it)
        }
    }

    val densityDpi: Int get() = displayMetrics.densityDpi
    val screenWidth: Int get() = displayMetrics.widthPixels
    val screenHeight: Int get() = displayMetrics.heightPixels

    /**
     * Instagram UA format:
     * Instagram <ver> Android (<sdk>/<os>; <density>; <w>x<h>; <mfr>; <model>; <device>; <abi>; <locale>; <app_id>)
     */
    val userAgent: String
        get() = buildString {
            append("Instagram ${Constants.APP_VERSION} Android (")
            append("$sdkInt/$androidVersion; ")
            append("${densityDpi}dpi; ")
            append("${screenWidth}x${screenHeight}; ")
            append("$manufacturer; ")
            append("$model; ")
            append("$device; ")
            append("$cpuAbi; ")
            append("$locale; ")
            append("${Constants.FB_ANALYTICS_APP_ID})")
        }

    val acceptLanguage: String get() = locale.replace('_', '-')

    val timeZoneOffsetSeconds: Int
        get() = java.util.TimeZone.getDefault()
            .getOffset(System.currentTimeMillis()) / 1000

    val timeZoneName: String get() = java.util.TimeZone.getDefault().id
}