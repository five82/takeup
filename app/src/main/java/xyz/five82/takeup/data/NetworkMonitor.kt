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

/**
 * Emits when the device joins a network.
 *
 * This never decides whether Takeup is offline: Loom lives on one LAN, so a phone
 * can be fully online over cellular with Loom still unreachable, and can sit on
 * home wifi while Loom itself is down. Reaching Loom is the only real test. What
 * connectivity is good for is knowing when it is worth trying again - rejoining
 * home wifi is exactly that moment.
 */
internal class NetworkMonitor(context: Context) {
    private val connectivity = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    val networksAvailable: Flow<Unit> = callbackFlow {
        val manager = connectivity
        if (manager == null) {
            close()
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(Unit)
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
    }.conflate()
}
