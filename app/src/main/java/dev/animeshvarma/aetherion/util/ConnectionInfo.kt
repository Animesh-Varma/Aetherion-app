package dev.animeshvarma.aetherion.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager

class ConnectionInfo(context: Context) {
    private val cm =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?

    /** WIFI / CELLULAR / ETHERNET / UNKNOWN — falls back to WIFI. */
    fun type(): String {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "WIFI"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "UNKNOWN"
        }
    }

    /** MCC+MNC numeric, e.g. "405840" — sent as X-FB-Net-HNI. */
    fun networkOperator(): String = tm?.networkOperator ?: ""

    /** Operator name (for internal logging). */
    fun operatorName(): String = tm?.networkOperatorName ?: ""

    fun bandwidthKbps(): Int = 0

    fun connectionQuality(): String = "EXCELLENT"
}