package xyz.five82.takeup.ui

import android.os.Looper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import xyz.five82.takeup.api.LoomApi
import xyz.five82.takeup.data.LoomDiscovery
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.ui.onboarding.OnboardingViewModel

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class OnboardingViewModelTest {
    private val repository = Mockito.mock(LoomRepository::class.java)
    private val discovery = Mockito.mock(LoomDiscovery::class.java)
    private val api = Mockito.mock(LoomApi::class.java)

    private fun model(): OnboardingViewModel {
        Mockito.doReturn(api).`when`(repository).api
        return OnboardingViewModel(repository, discovery)
    }

    @Test fun invalidAddressDoesNotAttemptConnection() {
        val model = model()
        model.connect("http://")
        assertEquals("Enter an address like 192.168.1.20:8097", model.error)
        Mockito.verifyNoInteractions(api)
        Mockito.verify(discovery, Mockito.never()).stop()
    }

    @Test fun successfulConnectionPersistsAddressAndStopsDiscovery() {
        val model = model()
        model.connect(" 192.168.1.20:8097 ")
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals("192.168.1.20:8097", model.address)
        assertNull(model.error)
        assertFalse(model.checking)
        runBlocking { Mockito.verify(api).health() }
        runBlocking { Mockito.verify(repository).setServerAddress("192.168.1.20:8097") }
        Mockito.verify(discovery).stop()
    }

    @Test fun failedHealthCheckClearsCandidateAndReportsError() {
        runBlocking { Mockito.`when`(api.health()).thenThrow(IllegalStateException("unreachable")) }
        val model = model()
        model.connect("192.168.1.20:8097")
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Loom isn't answering at 192.168.1.20:8097", model.error)
        assertNull(api.baseUrl)
        assertFalse(model.checking)
        Mockito.verify(discovery, Mockito.never()).stop()
    }
}
