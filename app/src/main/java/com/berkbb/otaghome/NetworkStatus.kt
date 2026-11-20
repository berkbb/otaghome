package com.berkbb.otaghome

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.DrawableRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

        /** Picks the non-unspecified signal strength (`NetworkCapabilities` on API 29+, else legacy RSSI). */
        fun resolveWifiBars(capabilitiesSignal: Int?, legacyRssi: Int?): Int {
            val signal = capabilitiesSignal?.takeIf { it != Int.MIN_VALUE } ?: legacyRssi
            return if (signal == null) 4 else wifiBars(signal)
        }

        fun read(context: Context): NetworkStatus {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return Offline
            val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
            return fromCapabilities(context, caps)
        }

        fun fromCapabilities(context: Context, caps: NetworkCapabilities?): NetworkStatus {
            if (caps == null) return Offline
            return when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Ethernet
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    val capSignal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) caps.signalStrength else null
                    val legacyRssi = if (capSignal == null || capSignal == Int.MIN_VALUE) {
                        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
                        @Suppress("DEPRECATION")
                        runCatching { wifi?.connectionInfo?.rssi }.getOrNull()
                    } else null
                    Wifi(resolveWifiBars(capSignal, legacyRssi))
                }
                else -> Offline
            }
        }
    }
}

/** Observes the TV's active network via `ConnectivityManager.NetworkCallback` while the screen is visible. */
internal class NetworkMonitor(context: Context) {

    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)

    private val _status = MutableStateFlow(NetworkStatus.read(appContext))
    val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    private var active = false
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = refresh()
    }

    fun setActive(active: Boolean) {
        if (this.active == active) return
        this.active = active
        if (active) {
            refresh()
            if (!registered) {
                runCatching {
                    connectivityManager?.registerDefaultNetworkCallback(callback)
                    registered = connectivityManager != null
                }
            }
        } else if (registered) {
            runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
            registered = false
        }
    }

    fun refresh() {
        _status.value = NetworkStatus.read(appContext)
    }

    fun clear() = setActive(false)
}
