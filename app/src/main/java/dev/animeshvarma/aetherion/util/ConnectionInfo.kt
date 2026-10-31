package dev.animeshvarma.aetherion.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager

class ConnectionInfo(private val context: Context) {

    private val connectivityManager: ConnectivityManager?
        get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val telephonyManager: TelephonyManager?
        get() = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private fun activeCapabilities(): NetworkCapabilities? {
        val cm = connectivityManager ?: return null
        val net = cm.activeNetwork ?: return null
        return cm.getNetworkCapabilities(net)
    }

    fun type(): String {
        val caps = activeCapabilities() ?: return "UNKNOWN"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "MOBILE(LTE)"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "WIFI"
        }
    }

    fun networkOperator(): String {
        val tm = telephonyManager ?: return ""
        return tm.networkOperator?.takeIf { it.isNotBlank() }
            ?: tm.simOperator?.takeIf { it.isNotBlank() }
            ?: ""
    }

    /**
     * Dynamically classifies link bandwidth from Android NetworkCapabilities.linkDownstreamBandwidthKbps
     */
    fun connectionQuality(): String {
        val kbps = bandwidthKbps()
        return when {
            kbps <= 0 -> "UNKNOWN"
            kbps < 150 -> "POOR"
            kbps < 550 -> "MODERATE"
            kbps < 2000 -> "GOOD"
            else -> "EXCELLENT"
        }
    }

    /**
     * Reads real downstream bandwidth from the Android kernel network capabilities.
     */
    fun bandwidthKbps(): Int {
        return activeCapabilities()?.linkDownstreamBandwidthKbps ?: 0
    }
}