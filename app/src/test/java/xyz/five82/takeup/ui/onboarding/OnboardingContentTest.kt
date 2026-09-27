package xyz.five82.takeup.ui.onboarding

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.five82.takeup.api.LoomApi
import xyz.five82.takeup.data.DiscoveredLoom
import xyz.five82.takeup.data.LoomDiscovery
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.ui.theme.TakeupTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingContentTest {
    @get:Rule val compose = createComposeRule()
    private val repo = Mockito.mock(LoomRepository::class.java)
    private val discovery = Mockito.mock(LoomDiscovery::class.java)
    private val api = Mockito.mock(LoomApi::class.java)
    private lateinit var model: OnboardingViewModel

    private fun show() {
        Mockito.doReturn(api).`when`(repo).api
        model = OnboardingViewModel(repo, discovery)
        compose.setContent { TakeupTheme { OnboardingContent(model) } }
    }

    @Test fun manualEntryValidatesBeforeTryingServer() {
        show()
        compose.onNodeWithText("Looking for Loom on your network...").assertExists()
        compose.onNodeWithText("Server address").performTextInput("http://")
        compose.onNodeWithText("Connect").performClick()
        compose.onNodeWithText("Enter an address like 192.168.1.20:8097").assertExists()
        Mockito.verifyNoInteractions(api)
    }

    @Test fun discoveredServerCanBeSelected() {
        show()
        compose.runOnIdle { model.discovered = listOf(DiscoveredLoom("Living Room", "192.168.1.20:8097")) }
        compose.onNodeWithText("Available on your network").assertExists()
        compose.onNodeWithText("Living Room").performClick()
        compose.runOnIdle { assertEquals("192.168.1.20:8097", model.address) }
        Mockito.verify(discovery).stop()
    }

    @Test fun discoveryFailureShowsManualFallback() {
        show()
        compose.runOnIdle { model.discoveryFailed = true }
        compose.onNodeWithText("Automatic discovery unavailable").assertExists()
        compose.onNodeWithText("Server address").assertExists()
    }
}
