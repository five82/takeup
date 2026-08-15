package xyz.five82.takeup.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Cellular without the user's say-so is treated as no network at all. */
fun cellularBlocked(onCellular: Boolean, allowCellular: Boolean): Boolean =
    onCellular && !allowCellular

/**
 * The cellular gate. Loom lives on a LAN, so Wi-Fi is the ordinary way to reach
 * it and a cellular connection means a data plan is paying for whatever moves.
 * Unless the user allows it, cellular counts as being offline: requests fail the
 * way an unreachable server does, which drops the app onto the offline paths it
 * already has, where downloaded titles are all that remain.
 */
class CellularPolicy(
    context: Context,
    private val settings: Settings,
    scope: CoroutineScope,
) {
    private val connectivity = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    private val onCellular = MutableStateFlow(activeNetworkIsCellular())

    val allowed: StateFlow<Boolean> = settings.allowCellular
        .stateIn(scope, SharingStarted.Eagerly, false)

    // Seeded from the transport alone, before DataStore has answered: for the
    // moment the setting is unknown, a cellular connection stays blocked rather
    // than letting the first requests after launch out onto the data plan.
    val blocked: StateFlow<Boolean> =
        combine(onCellular, settings.allowCellular, ::cellularBlocked)
            .stateIn(scope, SharingStarted.Eagerly, onCellular.value)

    init {
        connectivity.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    // A VPN carried over cellular keeps the cellular transport,
                    // so tunnelling to Loom does not slip past the gate.
                    onCellular.value =
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                }

                override fun onLost(network: Network) {
                    onCellular.value = false
                }
            },
        )
    }

    suspend fun setAllowed(value: Boolean) = settings.setAllowCellular(value)

    private fun activeNetworkIsCellular(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
}
