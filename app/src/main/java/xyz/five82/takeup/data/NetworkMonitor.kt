package xyz.five82.takeup.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Tracks whether any local network (wifi/ethernet) is up.
 *
 * `true` never decides whether Takeup is online: Loom lives on one LAN, so the
 * phone can sit on some other wifi with Loom unreachable. Reaching Loom is the
 * only real test, and joining a network is merely the moment worth retrying.
 * `false` is different - with no local network at all, Loom is definitely
 * unreachable, which is what lets Home switch to downloads-only the instant
 * airplane mode flips on rather than after a failed request.
 */
internal class NetworkMonitor(context: Context) {
    private val connectivity = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    val wifiAvailable: Flow<Boolean> = callbackFlow {
        val manager = connectivity
        if (manager == null) {
            close()
            return@callbackFlow
        }
        // registerNetworkCallback replays onAvailable for already-connected
        // matching networks, so the flow settles on the correct initial state.
        val networks = mutableSetOf<Network>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                networks.add(network)
                trySend(true)
            }

            override fun onLost(network: Network) {
                networks.remove(network)
                trySend(networks.isNotEmpty())
            }
        }
        // Wifi rather than "has internet": Loom sits on the LAN, so a wifi network
        // with no internet at all still reaches it, and cellular never does.
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        manager.registerNetworkCallback(request, callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().conflate()
}
