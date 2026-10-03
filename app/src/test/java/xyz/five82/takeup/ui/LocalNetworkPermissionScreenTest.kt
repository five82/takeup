package xyz.five82.takeup.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.five82.takeup.ui.theme.TakeupTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class LocalNetworkPermissionScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun initialPermissionExplainsTheNeedAndRequestsAccess() {
        var grants = 0
        compose.setContent { TakeupTheme { LocalNetworkPermissionScreen(false) { grants++ } } }
        compose.onNodeWithText("Local network access").assertExists()
        compose.onNodeWithText("Takeup needs access to your local network to connect to Loom.").assertExists()
        compose.onNodeWithText("Allow access").performClick()
        assertEquals(1, grants)
    }

    @Test fun deniedPermissionOffersSettingsInsteadOfRepeatingTheRequest() {
        var opens = 0
        compose.setContent { TakeupTheme { LocalNetworkPermissionScreen(true) { opens++ } } }
        compose.onNodeWithText("Access is off.", substring = true).assertExists()
        compose.onNodeWithText("Open settings").performClick()
        assertEquals(1, opens)
    }
}
