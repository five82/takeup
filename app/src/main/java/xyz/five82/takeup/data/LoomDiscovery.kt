package xyz.five82.takeup.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.InetAddress

private const val LOOM_SERVICE_TYPE = "_loom._tcp"

data class DiscoveredLoom(val name: String, val address: String)

/** Discovers Loom's DNS-SD advertisements while the first-run screen is visible. */
class LoomDiscovery(context: Context) {
    private val nsdManager = context.getSystemService(NsdManager::class.java)
    private val multicastLock = context.getSystemService(WifiManager::class.java)
        .createMulticastLock("takeup-loom-discovery")
        .apply { setReferenceCounted(false) }
    private val executor = context.mainExecutor
    private val availableNames = mutableSetOf<String>()
    private val services = mutableMapOf<String, DiscoveredLoom>()
    private val callbacks = mutableMapOf<String, NsdManager.ServiceInfoCallback>()

    private var running = false
    private var discoveryActive = false
    private var onUpdate: (List<DiscoveredLoom>) -> Unit = {}
    private var onFailure: () -> Unit = {}

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            discoveryActive = true
        }

        override fun onServiceFound(info: NsdServiceInfo) {
            if (!running || info.serviceType.trimEnd('.') != LOOM_SERVICE_TYPE) return
            val name = info.serviceName
            availableNames += name
            runCatching { track(info) }
                .onFailure { callbacks.remove(name) }
        }

        override fun onServiceLost(info: NsdServiceInfo) {
            val name = info.serviceName
            availableNames -= name
            services.remove(name)
            callbacks.remove(name)?.let { callback ->
                runCatching { nsdManager.unregisterServiceInfoCallback(callback) }
            }
            emit()
        }

        override fun onDiscoveryStopped(serviceType: String) {
            discoveryActive = false
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            discoveryActive = false
            if (running) {
                running = false
                if (multicastLock.isHeld) multicastLock.release()
                onFailure()
            }
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            discoveryActive = false
        }
    }

    fun start(onUpdate: (List<DiscoveredLoom>) -> Unit, onFailure: () -> Unit) {
        if (running) return
        running = true
        this.onUpdate = onUpdate
        this.onFailure = onFailure
        multicastLock.acquire()
        try {
            nsdManager.discoverServices(
                LOOM_SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener,
            )
        } catch (_: RuntimeException) {
            running = false
            multicastLock.release()
            onFailure()
        }
    }

    fun stop() {
        if (!running) return
        running = false
        if (discoveryActive) {
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
        }
        callbacks.values.forEach { callback ->
            runCatching { nsdManager.unregisterServiceInfoCallback(callback) }
        }
        callbacks.clear()
        if (multicastLock.isHeld) multicastLock.release()
    }

    private fun track(info: NsdServiceInfo) {
        val name = info.serviceName
        if (callbacks.containsKey(name)) return
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                update(serviceInfo)
            }

            override fun onServiceLost() {
                services.remove(name)
                emit()
            }

            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                callbacks.remove(name)
            }

            override fun onServiceInfoCallbackUnregistered() = Unit
        }
        callbacks[name] = callback
        nsdManager.registerServiceInfoCallback(info, executor, callback)
    }

    private fun update(info: NsdServiceInfo) {
        if (!running || info.serviceName !in availableNames) return
        loomAddress(info.hostAddresses, info.port)?.let { address ->
            services[info.serviceName] = DiscoveredLoom(info.serviceName, address)
            emit()
        }
    }

    private fun emit() {
        if (running) onUpdate(services.values.sortedBy { it.name.lowercase() })
    }
}

internal fun loomAddress(addresses: List<InetAddress>, port: Int): String? {
    if (port !in 1..65535) return null
    val address = addresses.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
        ?: addresses.firstOrNull { !it.isLoopbackAddress }
        ?: return null
    val literal = address.hostAddress?.replace("%", "%25") ?: return null
    return if (literal.contains(':')) "[$literal]:$port" else "$literal:$port"
}
