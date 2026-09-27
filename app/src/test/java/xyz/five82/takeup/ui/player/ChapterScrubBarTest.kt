package xyz.five82.takeup.ui.player

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.five82.takeup.api.Chapter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChapterScrubBarTest {
    @get:Rule val compose = createComposeRule()
    private val seeks = mutableListOf<Long>()
    private val previews = mutableListOf<Long?>()

    private fun show(duration: Long) {
        compose.setContent {
            ChapterScrubBar(
                positionMs = 25_000,
                durationMs = duration,
                chapters = listOf(Chapter(0, 0), Chapter(1, 50_000)),
                accent = Color.Cyan,
                onPreview = previews::add,
                onSeek = seeks::add,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    @Test fun tapSeeksToRelativePosition() {
        show(100_000)
        compose.onRoot().performTouchInput { click(center) }
        assertTrue(seeks.single() in 49_000..51_000)
    }

    @Test fun zeroDurationDoesNotSeek() {
        show(0)
        compose.onRoot().performTouchInput { click(center) }
        assertTrue(seeks.isEmpty())
    }

    @Test fun draggingPreviewsAndSeeksOnRelease() {
        show(100_000)
        compose.onRoot().performTouchInput { swipe(Offset(width * 0.1f, center.y), Offset(width * 0.9f, center.y), durationMillis = 500) }
        assertTrue(previews.any { it != null })
        assertEquals(null, previews.last())
        assertTrue(seeks.single() > 80_000)
    }
}
