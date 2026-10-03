package xyz.five82.takeup.data

import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class NetworkPolicyRuntimeTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val settings = Mockito.mock(Settings::class.java)
    private val address = MutableStateFlow<String?>(null)
    private val cellular = MutableStateFlow(false)

    @After fun close() {
        scope.cancel()
    }

    private fun policy(): NetworkPolicy {
        Mockito.doReturn(address).`when`(settings).serverAddress
        Mockito.doReturn(cellular).`when`(settings).allowCellular
        return NetworkPolicy(RuntimeEnvironment.getApplication(), settings, scope)
    }

    private fun advance() {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    @Test fun noConfiguredServerSettlesOfflineWithoutSendingAProbe() {
        val policy = policy()
        policy.recheck()
        advance()
        assertEquals(Reach.Offline, policy.reach.value)
        policy.markUnreachable()
        assertEquals(Reach.Offline, policy.reach.value)
    }

    @Test fun cellularSettingIsForwardedToPersistence() = runBlocking {
        val policy = policy()
        policy.setAllowCellular(true)
        Mockito.verify(settings).setAllowCellular(true)
        Unit
    }

}
