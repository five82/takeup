package xyz.five82.takeup.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import xyz.five82.takeup.ui.components.FoldHero
import xyz.five82.takeup.ui.theme.TakeupTheme

class FoldHeroLogoTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun landscapeUsesVisibleLogoBoundsForBackdropGapAndMetadataAlignment() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Decode real images: a null logo/title-only test cannot catch the
        // letterboxing inside AsyncImage that caused the landscape regression.
        val images = listOf(480 to 124, 600 to 100, 120 to 160).map { (width, height) ->
            val file = File(context.cacheDir, "hero-logo-$width-$height.png")
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.Magenta.toArgb())
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            file to width.toFloat() / height
        }
        val layout = mutableStateOf(FoldLayout.CoverLandscape)
        val logo = mutableStateOf(images.first().first.absolutePath)
        val metadataLines = mutableStateOf(2)
        val cameraSide = mutableStateOf(0)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                TakeupTheme {
                    val paneWidth = if (layout.value == FoldLayout.CoverLandscape) 591.dp else 712.dp
                    Column(Modifier.requiredWidth(paneWidth).verticalScroll(rememberScrollState())) {
                        Box(Modifier.testTag("hero")) {
                            FoldHero(
                                title = "Logo", backdrop = null, logo = logo.value, layout = layout.value,
                                safeInsets = WindowInsets(
                                    left = if (cameraSide.value == 1) 40.dp else 0.dp,
                                    right = if (cameraSide.value == 2) 40.dp else 0.dp,
                                ),
                            ) { inline ->
                                check(inline)
                                Column(Modifier.testTag("metadata")) {
                                    repeat(metadataLines.value) { Text("Metadata line $it") }
                                }
                            }
                        }
                    }
                }
            }
        }
        try {
            for (panel in listOf(FoldLayout.CoverLandscape, FoldLayout.InnerLandscape)) {
                for ((file, aspect) in images) {
                    for (lines in listOf(1, 2, 3)) {
                        for (camera in 0..2) {
                            compose.runOnIdle {
                                layout.value = panel
                                logo.value = file.absolutePath
                                metadataLines.value = lines
                                cameraSide.value = camera
                            }
                            val maxWidth = if (panel == FoldLayout.CoverLandscape) 152f else 176f
                            val maxHeight = if (panel == FoldLayout.CoverLandscape) 56f else 64f
                            val expectedWidth = minOf(maxWidth, maxHeight * aspect)
                            val expectedHeight = expectedWidth / aspect
                            compose.waitUntil(5_000) {
                                val bounds = compose.onNodeWithContentDescription("Logo").fetchSemanticsNode().boundsInRoot
                                kotlin.math.abs(bounds.height - expectedHeight) <= 1f &&
                                    kotlin.math.abs(bounds.width - expectedWidth) <= 1f
                            }
                            compose.waitForIdle()
                            val image = compose.onNodeWithContentDescription("Logo")
                            val bounds = image.fetchSemanticsNode().boundsInRoot
                            val details = compose.onNodeWithTag("metadata").fetchSemanticsNode().boundsInRoot
                            val hero = compose.onNodeWithTag("hero").fetchSemanticsNode().boundsInRoot
                            val photoHeight = (if (panel == FoldLayout.CoverLandscape) 591f else 712f) * 9f / 16f
                            assertEquals("No extra band below the photo", photoHeight, minOf(bounds.top, details.top) - hero.top, 1f)
                            assertEquals("Center the text on the visible logo", bounds.center.y, details.center.y, 1f)
                            assertEquals("No empty logo lane before text", 24f, details.left - bounds.right, 1f)
                            // Bounds alone would miss a fitted, bottom-aligned
                            // image in a larger box. The pixels must fill it too.
                            val pixels = image.captureToImage().toPixelMap()
                            assertEquals(Color.Magenta.toArgb(), pixels[1, 1].toArgb())
                            assertEquals(Color.Magenta.toArgb(), pixels[pixels.width - 2, pixels.height - 2].toArgb())
                        }
                    }
                }
            }
        } finally {
            images.forEach { (file, _) -> file.delete() }
        }
    }
}
