package com.berkbb.otaghome

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.annotation.DrawableRes

/** What the top bar's network icon shows. */
sealed interface NetworkStatus {
    data object Offline : NetworkStatus
    data object Ethernet : NetworkStatus
    /** [bars] is 0–4, like the system status icons. */
    data class Wifi(val bars: Int) : NetworkStatus

    @get:DrawableRes
    val iconRes: Int
        get() = when (this) {
            Offline -> R.drawable.ic_wifi_off
            Ethernet -> R.drawable.ic_ethernet
            is Wifi -> when (bars) {
                0 -> R.drawable.ic_wifi_0
                1 -> R.drawable.ic_wifi_1
                2 -> R.drawable.ic_wifi_2
                3 -> R.drawable.ic_wifi_3
                else -> R.drawable.ic_wifi_4
            }
        }

    companion object {
        /** RSSI (dBm) to 0–4 bars, with the thresholds AOSP uses for five signal levels. */
        fun wifiBars(rssi: Int): Int = when {
            rssi >= -55 -> 4
            rssi >= -66 -> 3
            rssi >= -77 -> 2
            rssi >= -88 -> 1
            else -> 0
        }

        fun read(context: Context): NetworkStatus {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return Offline
            val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull() ?: return Offline
            return when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Ethernet
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
                    // RSSI is not location-sensitive, so the deprecated getter still reports it
                    @Suppress("DEPRECATION")
                    val rssi = runCatching { wifi?.connectionInfo?.rssi }.getOrNull()
                    Wifi(if (rssi == null) 4 else wifiBars(rssi))
                }
                else -> Offline
            }
        }
    }
}
