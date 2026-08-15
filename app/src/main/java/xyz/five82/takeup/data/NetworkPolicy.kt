package xyz.five82.takeup.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.InetAddresses
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.five82.takeup.api.LoomApi
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

/**
 * Where Loom is, as far as this device can tell. [Unknown] only lasts until the
 * first probe answers; every screen treats it as "still looking".
 */
enum class Reach { Unknown, Home, Remote, Offline }

/**
 * Everything the gates are decided from. These travel as one named value rather
 * than four loose booleans: they are all the same type, and wiring four flows
 * into a four-argument function positionally is a silent mistake waiting to be
 * made. The defaults are the safe reading before DataStore has answered.
 */
data class NetworkFacts(
    val onCellular: Boolean = false,
    val allowCellular: Boolean = false,
    val onHomeSubnet: Boolean = true,
    val allowRemote: Boolean = false,
)

/**
 * Policy alone, before anything is asked of the network. Cellular spends a data
 * plan, and a subnet that is not Loom's means any answer would arrive through a
 * tunnel; both are refusals the user has to opt out of.
 */
fun networkBlocked(facts: NetworkFacts): Boolean = with(facts) {
    (onCellular && !allowCellular) || (!onHomeSubnet && !allowRemote)
}

/**
 * What a probe that was actually made adds up to. Answering from Loom's own
 * subnet is the only thing that counts as being home; answering from anywhere
 * else got there over the tunnel.
 */
fun reachFrom(answered: Boolean, onHomeSubnet: Boolean): Reach = when {
    !answered -> Reach.Offline
    onHomeSubnet -> Reach.Home
    else -> Reach.Remote
}

/** Why the app is offline, in the app's own voice rather than an exception's. */
fun offlineReason(facts: NetworkFacts): String = with(facts) {
    when {
        onCellular && !allowCellular -> "Cellular data is off in Takeup."
        !onHomeSubnet && !allowRemote -> "You are away from your home network."
        else -> "Loom isn't answering."
    }
}

/**
 * True when [server] and [local] agree on the first [prefixLength] bits, which
 * is what puts two addresses on one subnet.
 */
fun inSameSubnet(server: ByteArray, local: ByteArray, prefixLength: Int): Boolean {
    if (server.size != local.size) return false
    if (prefixLength <= 0 || prefixLength > local.size * 8) return false
    var bits = prefixLength
    for (i in local.indices) {
        if (bits <= 0) break
        val mask = if (bits >= 8) 0xFF else (0xFF shl (8 - bits)) and 0xFF
        if ((server[i].toInt() and mask) != (local[i].toInt() and mask)) return false
        bits -= 8
    }
    return true
}

/**
 * Whether Loom is reachable, decided before the app asks anything of it.
 *
 * Loom is a LAN server, so "online" is not a property of the phone having
 * internet - it is whether this particular network can hand us Loom. Two gates
 * answer that without a single packet: cellular spends a data plan, and being
 * off Loom's subnet means only the tunnel could carry the answer. Either one,
 * unless allowed, makes [blocked] true, which is the single flag the API
 * client, the playback upstream, and the transfer queue all read.
 *
 * Past those, one short probe settles it. It deliberately does not go through
 * [LoomApi]'s client: that client refuses calls while [blocked], and a probe
 * that can be refused would make offline a state the app could never leave.
 */
class NetworkPolicy(
    context: Context,
    private val settings: Settings,
    private val scope: CoroutineScope,
) {
    private val connectivity = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    private val onCellular = MutableStateFlow(activeNetworkIsCellular())

    // No server configured yet counts as home: onboarding has to be able to
    // reach a Loom it has not been told about yet.
    private val onHomeSubnet = MutableStateFlow(true)

    private val serverUrl: StateFlow<String?> = settings.serverAddress
        .map { it?.let(LoomApi::normalizeAddress) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val allowCellular: StateFlow<Boolean> = settings.allowCellular
        .stateIn(scope, SharingStarted.Eagerly, false)

    val allowRemote: StateFlow<Boolean> = settings.allowRemote
        .stateIn(scope, SharingStarted.Eagerly, false)

    // Named construction, so the four booleans cannot be wired up in the wrong
    // order. Seeded before DataStore has answered: for the moment the settings
    // are unknown, a cellular connection stays blocked rather than letting the
    // first requests out onto the data plan, and anything else is allowed.
    private val facts: StateFlow<NetworkFacts> =
        combine(
            onCellular,
            onHomeSubnet,
            settings.allowCellular,
            settings.allowRemote,
        ) { cellular, homeSubnet, allowCellular, allowRemote ->
            NetworkFacts(
                onCellular = cellular,
                allowCellular = allowCellular,
                onHomeSubnet = homeSubnet,
                allowRemote = allowRemote,
            )
        }.stateIn(scope, SharingStarted.Eagerly, NetworkFacts(onCellular = onCellular.value))

    val blocked: StateFlow<Boolean> = facts
        .map(::networkBlocked)
        .stateIn(scope, SharingStarted.Eagerly, networkBlocked(facts.value))

    private val _reach = MutableStateFlow(Reach.Unknown)
    val reach: StateFlow<Reach> = _reach.asStateFlow()

    /** Current wording for why there is no Loom, for whichever screen asks. */
    val reason: StateFlow<String> = facts
        .map(::offlineReason)
        .stateIn(scope, SharingStarted.Eagerly, offlineReason(facts.value))

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    private var probeJob: Job? = null

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
                    onNetworkChanged()
                }

                override fun onLost(network: Network) {
                    onCellular.value = false
                    onNetworkChanged()
                }
            },
        )
        // Also covers the first load: the address arrives from DataStore after
        // construction, and the subnet cannot be judged until it does.
        scope.launch { serverUrl.collect { onNetworkChanged() } }
    }

    /** Re-decides from scratch. Safe to call as often as a screen likes. */
    fun recheck() {
        probeJob?.cancel()
        probeJob = scope.launch {
            // Network callbacks arrive in bursts as an interface settles, and a
            // manual retry is not worth telling apart from one of those.
            delay(PROBE_DEBOUNCE_MS)
            val url = serverUrl.value
            if (blocked.value || url == null) {
                _reach.value = Reach.Offline
                return@launch
            }
            _reach.value = reachFrom(probe(url), onHomeSubnet.value)
        }
    }

    /**
     * Drops straight to offline on a failed request, so a screen that has just
     * watched a call time out does not wait on a probe to agree with it.
     */
    fun markUnreachable() {
        probeJob?.cancel()
        _reach.value = Reach.Offline
    }

    suspend fun setAllowCellular(value: Boolean) {
        settings.setAllowCellular(value)
        recheck()
    }

    suspend fun setAllowRemote(value: Boolean) {
        settings.setAllowRemote(value)
        recheck()
    }

    private fun onNetworkChanged() {
        onHomeSubnet.value = serverHostOnLocalSubnet()
        recheck()
    }

    private suspend fun probe(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("$baseUrl/api/v1/health").build()
            probeClient.newCall(request).execute().use { response ->
                // A stranger's device sitting at the same address on a network
                // that happens to share Loom's subnet must not read as Loom, so
                // the body has to look like Loom's JSON, not merely be a 200.
                response.isSuccessful && response.body.string().trimStart().startsWith("{")
            }
        }.getOrDefault(false)
    }

    /** Whether any local interface shares a subnet with the configured server. */
    private fun serverHostOnLocalSubnet(): Boolean {
        val host = serverUrl.value?.toHttpUrlOrNull()?.host ?: return true
        // A hostname would need DNS to compare, and a LAN name only resolves at
        // home anyway; treat it as home rather than block the app on a lookup.
        if (!InetAddresses.isNumericAddress(host)) return true
        val server = runCatching { InetAddress.getByName(host).address }.getOrNull() ?: return true
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces().toList() }
            .getOrNull() ?: return true
        return interfaces.any { nic ->
            runCatching {
                // Tailscale carries a route to the home subnet but holds no
                // address on it, so the tunnel cannot read as being home. The
                // name check is belt and braces for a tunnel that assigns one.
                nic.isUp && !nic.isLoopback && !nic.name.startsWith("tun") &&
                    nic.interfaceAddresses.any {
                        inSameSubnet(server, it.address.address, it.networkPrefixLength.toInt())
                    }
            }.getOrDefault(false)
        }
    }

    private fun activeNetworkIsCellular(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private companion object {
        // Short on purpose: this decides how long a screen waits before it can
        // honestly say it is offline, so it must not sit on the app's timeout.
        const val PROBE_TIMEOUT_MS = 1_500L
        const val PROBE_DEBOUNCE_MS = 300L
    }
}
