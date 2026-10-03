package xyz.five82.takeup.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
class LoomDiscoveryRuntimeTest {
    private val context = Mockito.mock(Context::class.java)
    private val nsd = Mockito.mock(NsdManager::class.java)
    private val wifi = Mockito.mock(WifiManager::class.java)
    private val lock = Mockito.mock(WifiManager.MulticastLock::class.java)
    private val listener = ArgumentCaptor.forClass(NsdManager.DiscoveryListener::class.java)
    private val callback = ArgumentCaptor.forClass(NsdManager.ServiceInfoCallback::class.java)
    private val updates = mutableListOf<List<DiscoveredLoom>>()
    private var failures = 0

    private fun discovery(): LoomDiscovery {
        Mockito.`when`(context.getSystemService(NsdManager::class.java)).thenReturn(nsd)
        Mockito.`when`(context.getSystemService(WifiManager::class.java)).thenReturn(wifi)
        Mockito.`when`(wifi.createMulticastLock("takeup-loom-discovery")).thenReturn(lock)
        Mockito.`when`(lock.isHeld).thenReturn(true)
        Mockito.`when`(context.mainExecutor).thenReturn(Executor { it.run() })
        return LoomDiscovery(context).also { it.start(updates::add) { failures++ } }
    }

    private fun service(name: String, type: String = "_loom._tcp.", port: Int = 8097): NsdServiceInfo =
        NsdServiceInfo().apply {
            serviceName = name
            serviceType = type
            setPort(port)
        }

    @Test @Config(sdk = [37]) fun modernDiscoveryTracksUpdatesLossAndStop() {
        val discovery = discovery()
        Mockito.verify(nsd).discoverServices(Mockito.eq("_loom._tcp"), Mockito.eq(NsdManager.PROTOCOL_DNS_SD), listener.capture())
        discovery.start(updates::add) { failures++ } // idempotent
        Mockito.verify(nsd, Mockito.times(1)).discoverServices(Mockito.anyString(), Mockito.anyInt(), Mockito.any())
        listener.value.onDiscoveryStarted("_loom._tcp")
        listener.value.onServiceFound(service("other", "_other._tcp."))
        listener.value.onServiceFound(service("Zeta"))
        listener.value.onServiceFound(service("Zeta")) // only one callback per name
        listener.value.onServiceFound(service("alpha"))
        Mockito.verify(nsd, Mockito.times(2)).registerServiceInfoCallback(Mockito.any(NsdServiceInfo::class.java), Mockito.any(Executor::class.java), callback.capture())
        callback.allValues[0].onServiceUpdated(service("Zeta").apply {
            hostAddresses = listOf(InetAddress.getByName("192.168.1.20"))
        })
        callback.allValues[1].onServiceUpdated(service("alpha").apply {
            hostAddresses = listOf(InetAddress.getByName("192.168.1.21"))
        })
        assertEquals(listOf("alpha", "Zeta"), updates.last().map { it.name })
        callback.allValues[0].onServiceLost()
        assertEquals(listOf("alpha"), updates.last().map { it.name })
        listener.value.onServiceLost(service("alpha"))
        assertTrue(updates.last().isEmpty())
        Mockito.verify(nsd).unregisterServiceInfoCallback(callback.allValues[1])
        discovery.stop()
        Mockito.verify(nsd).stopServiceDiscovery(listener.value)
        Mockito.verify(lock).release()
        discovery.stop()
    }

    @Test @Config(sdk = [37]) fun failureAndLateUpdatesDoNotResurrectServices() {
        val discovery = discovery()
        Mockito.verify(nsd).discoverServices(Mockito.anyString(), Mockito.anyInt(), listener.capture())
        listener.value.onServiceFound(service("Loom"))
        Mockito.verify(nsd).registerServiceInfoCallback(Mockito.any(NsdServiceInfo::class.java), Mockito.any(Executor::class.java), callback.capture())
        callback.value.onServiceInfoCallbackRegistrationFailed(1)
        listener.value.onStartDiscoveryFailed("_loom._tcp", 1)
        assertEquals(1, failures)
        callback.value.onServiceUpdated(service("Loom"))
        assertTrue(updates.isEmpty())
        discovery.stop()
        listener.value.onDiscoveryStopped("_loom._tcp")
        listener.value.onStopDiscoveryFailed("_loom._tcp", 1)
    }

    @Test @Config(sdk = [37]) fun serviceLossStillEmitsWhenUnregistrationThrows() {
        val discovery = discovery()
        Mockito.verify(nsd).discoverServices(Mockito.anyString(), Mockito.anyInt(), listener.capture())
        listener.value.onServiceFound(service("Loom"))
        Mockito.verify(nsd).registerServiceInfoCallback(Mockito.any(NsdServiceInfo::class.java),
            Mockito.any(Executor::class.java), callback.capture())
        callback.value.onServiceUpdated(service("Loom").apply {
            hostAddresses = listOf(InetAddress.getByName("192.168.1.10"))
        })
        Mockito.doThrow(IllegalStateException("already removed")).`when`(nsd)
            .unregisterServiceInfoCallback(callback.value)
        listener.value.onServiceLost(service("Loom"))
        assertTrue(updates.last().isEmpty())
        listener.value.onServiceFound(service("Loom"))
        Mockito.verify(nsd, Mockito.times(2)).registerServiceInfoCallback(Mockito.any(NsdServiceInfo::class.java),
            Mockito.any(Executor::class.java), Mockito.any(NsdManager.ServiceInfoCallback::class.java))
        discovery.stop()
    }

    @Test @Config(sdk = [37]) fun stopReleasesLockEvenWhenNsdThrows() {
        val discovery = discovery()
        Mockito.verify(nsd).discoverServices(Mockito.anyString(), Mockito.anyInt(), listener.capture())
        listener.value.onDiscoveryStarted("_loom._tcp")
        Mockito.doThrow(IllegalStateException("already stopped")).`when`(nsd)
            .stopServiceDiscovery(listener.value)
        discovery.stop()
        Mockito.verify(lock).release()
        listener.value.onServiceFound(service("late"))
        Mockito.verify(nsd, Mockito.never()).registerServiceInfoCallback(Mockito.any(NsdServiceInfo::class.java),
            Mockito.any(Executor::class.java), Mockito.any(NsdManager.ServiceInfoCallback::class.java))
    }

    @Test @Config(sdk = [37]) fun synchronousStartFailureReleasesLock() {
        Mockito.`when`(context.getSystemService(NsdManager::class.java)).thenReturn(nsd)
        Mockito.`when`(context.getSystemService(WifiManager::class.java)).thenReturn(wifi)
        Mockito.`when`(wifi.createMulticastLock(Mockito.anyString())).thenReturn(lock)
        Mockito.doThrow(IllegalStateException("NSD unavailable")).`when`(nsd)
            .discoverServices(Mockito.anyString(), Mockito.anyInt(), Mockito.any())
        LoomDiscovery(context).start(updates::add) { failures++ }
        assertEquals(1, failures)
        Mockito.verify(lock).release()
    }


    @Test @Config(sdk = [37]) fun registrationFailureCanBeRetriedOnNextAdvertisement() {
        val discovery = discovery()
        Mockito.verify(nsd).discoverServices(Mockito.anyString(), Mockito.anyInt(), listener.capture())
        Mockito.doThrow(IllegalStateException("registration failed")).`when`(nsd)
            .registerServiceInfoCallback(Mockito.any(NsdServiceInfo::class.java),
                Mockito.any(Executor::class.java), Mockito.any(NsdManager.ServiceInfoCallback::class.java))
        listener.value.onServiceFound(service("Loom"))
        Mockito.clearInvocations(nsd)
        Mockito.doNothing().`when`(nsd).registerServiceInfoCallback(
            Mockito.any(NsdServiceInfo::class.java), Mockito.any(Executor::class.java),
            Mockito.any(NsdManager.ServiceInfoCallback::class.java))
        listener.value.onServiceFound(service("Loom"))
        Mockito.verify(nsd).registerServiceInfoCallback(Mockito.any(NsdServiceInfo::class.java),
            Mockito.any(Executor::class.java), callback.capture())
        callback.value.onServiceUpdated(service("Loom", port = 0))
        assertTrue(updates.isEmpty())
        discovery.stop()
        listener.value.onServiceFound(service("Late"))
        assertTrue(updates.isEmpty())
    }
}
