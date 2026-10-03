package xyz.five82.takeup.ui

import android.view.ViewGroup
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.five82.takeup.TakeupApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = TakeupApplication::class)
class MainActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun startsTheAppAndReattachesAfterRecreation() {
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { activity ->
            assertTrue(activity.application is TakeupApplication)
            assertEquals(Lifecycle.State.RESUMED, activity.lifecycle.currentState)
            assertTrue((activity.window.decorView as ViewGroup).childCount > 0)
        }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { activity ->
            assertEquals(Lifecycle.State.RESUMED, activity.lifecycle.currentState)
        }
    }
}
