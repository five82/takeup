package xyz.five82.takeup.ui.player

import android.text.SpannableString
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubtitleOverlayUiTest {
    @get:Rule val compose = createComposeRule()
    private val player = Mockito.mock(Player::class.java)

    @Test fun cuesUpdateWithPlayerAndListenerIsRemovedOnDisposal() {
        val emphasized = SpannableString("Hello there").apply {
            setSpan(StyleSpan(Typeface.ITALIC), 0, 5, 0)
        }
        val first = Cue.Builder().setText(emphasized).build()
        Mockito.`when`(player.currentCues).thenReturn(CueGroup(listOf(first), 0))
        Mockito.`when`(player.videoSize).thenReturn(VideoSize(1920, 1080))
        val listener = ArgumentCaptor.forClass(Player.Listener::class.java)
        val visible = mutableStateOf(true)
        compose.setContent { if (visible.value) SubtitleOverlay(player, 80.dp, false, Modifier.fillMaxSize()) }
        Mockito.verify(player).addListener(listener.capture())
        compose.onNodeWithText("Hello there").assertExists()

        compose.runOnIdle {
            listener.value.onVideoSizeChanged(VideoSize(720, 576))
            listener.value.onCues(CueGroup(listOf(
                Cue.Builder().setText("Top left").setLine(0.1f, Cue.LINE_TYPE_FRACTION).setPosition(0.1f).build(),
                Cue.Builder().setText("Center").setLine(0.5f, Cue.LINE_TYPE_FRACTION).setPosition(0.5f).build(),
                Cue.Builder().setText("Bottom right").setLine(0.9f, Cue.LINE_TYPE_FRACTION).setPosition(0.9f).build(),
            ), 0))
        }
        compose.onNodeWithText("Hello there").assertDoesNotExist()
        compose.onNodeWithText("Top left").assertExists()
        compose.onNodeWithText("Center").assertExists()
        compose.onNodeWithText("Bottom right").assertExists()
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        Mockito.verify(player).removeListener(listener.value)
    }

    @Test fun bitmapOnlyCuesDoNotRenderText() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        Mockito.`when`(player.currentCues).thenReturn(CueGroup(listOf(Cue.Builder().setBitmap(bitmap).build()), 0))
        Mockito.`when`(player.videoSize).thenReturn(VideoSize(0, 0))
        compose.setContent { SubtitleOverlay(player, 0.dp, true, Modifier.fillMaxSize()) }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).assertCountEquals(0)
    }
}
