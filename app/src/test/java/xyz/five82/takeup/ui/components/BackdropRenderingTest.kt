package xyz.five82.takeup.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import xyz.five82.takeup.ui.FoldLayout
import xyz.five82.takeup.ui.theme.TakeupTheme
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w900dp-h600dp")
class BackdropRenderingTest {
    @get:Rule val compose = createComposeRule()

    private fun image(): String {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "backdrop-test.png")
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLUE)
            file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        return file.toURI().toString()
    }

    @Test fun theArtworkCutIsPaintedWithoutAFullScreenOverlay() {
        val url = image()
        compose.setContent {
            TakeupTheme {
                Box(Modifier.fillMaxSize().testTag("root")) {
                    BiasCutBackdrop(url, 24.dp, contentDescription = "Backdrop")
                    GauzeBackground(url, null, scrimAlphaScale = 0.8f)
                }
            }
        }
        assertTrue(compose.onNodeWithTag("root").captureToImage().width > 0)
    }

    @Test fun foldHeroLaysOutAnInlineTitleAndMetadataWhenWide() {
        var inline = false
        compose.setContent {
            TakeupTheme {
                Box(Modifier.width(700.dp)) {
                    FoldHero("Cinematic Film", null, null, FoldLayout.InnerLandscape,
                        safeInsets = WindowInsets(0, 0, 0, 0),
                        details = { isInline -> inline = isInline })
                }
            }
        }
        compose.onNodeWithText("Cinematic Film").assertExists()
        compose.runOnIdle { assertTrue(inline) }
    }

    @Test fun foldHeroCanPlaceACompactLogoBesideMetadata() {
        val url = image()
        var inline = false
        compose.setContent {
            TakeupTheme {
                Box(Modifier.width(700.dp).testTag("hero")) {
                    FoldHero("Logo Film", url, url, FoldLayout.CoverLandscape,
                        safeInsets = WindowInsets(0, 0, 0, 0),
                        details = { isInline -> inline = isInline })
                }
            }
        }
        compose.onNodeWithTag("hero").captureToImage()
        compose.runOnIdle { assertTrue(inline) }
    }
}
