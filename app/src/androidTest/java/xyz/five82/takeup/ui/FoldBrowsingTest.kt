package xyz.five82.takeup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import xyz.five82.takeup.ui.artwork.ArtworkKindTabs
import xyz.five82.takeup.ui.theme.TakeupTheme
import xyz.five82.takeup.ui.theme.Teal
import xyz.five82.takeup.ui.theme.Violet

class FoldBrowsingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun backgroundReachesEdgesWhileContentAvoidsEitherCameraEdgeAndStatusBar() {
        val layout = mutableStateOf(FoldLayout.CoverPortrait)
        val cameraOnLeft = mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalFoldLayout provides layout.value) {
                val insets = if (cameraOnLeft.value) WindowInsets(left = 40.dp, top = 30.dp)
                else WindowInsets(right = 40.dp, top = 30.dp)
                Box(
                    Modifier.requiredSize(400.dp, 300.dp).testTag("background")
                        .background(Teal).browsingContentInsets(insets),
                ) {
                    Box(Modifier.fillMaxSize().background(Violet).testTag("content"))
                }
            }
        }
        for (panel in FoldLayout.entries.filter { it != FoldLayout.Phone }) {
            for (left in listOf(true, false)) {
                compose.runOnIdle { layout.value = panel; cameraOnLeft.value = left }
                val background = compose.onNodeWithTag("background")
                val outer = background.fetchSemanticsNode().boundsInRoot
                val inner = compose.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot
                with(compose.density) {
                    assertEquals(400.dp.toPx(), outer.width, 1f)
                    assertEquals(300.dp.toPx(), outer.height, 1f)
                    assertEquals(30.dp.toPx(), inner.top - outer.top, 1f)
                    assertEquals((if (left) 40.dp else 0.dp).toPx(), inner.left - outer.left, 1f)
                    assertEquals((if (left) 0.dp else 40.dp).toPx(), outer.right - inner.right, 1f)
                }
                val pixels = background.captureToImage().toPixelMap()
                assertEquals(Teal.toArgb(), pixels[pixels.width / 2, 1].toArgb())
                val cameraX = if (left) 1 else pixels.width - 2
                assertEquals(Teal.toArgb(), pixels[cameraX, pixels.height / 2].toArgb())
                assertEquals(Violet.toArgb(), pixels[pixels.width / 2, pixels.height - 2].toArgb())
            }
        }
    }

    @Test
    fun utilityScrollClearanceIncludesSystemBarOnlyOnFold() {
        val layout = mutableStateOf(FoldLayout.Phone)
        var bottom = 0.dp
        compose.setContent {
            CompositionLocalProvider(LocalFoldLayout provides layout.value) {
                bottom = foldBottomPadding(16.dp, WindowInsets(bottom = 24.dp))
            }
        }
        compose.runOnIdle { assertEquals(16.dp, bottom) }
        for (panel in FoldLayout.entries.filter { it != FoldLayout.Phone }) {
            compose.runOnIdle { layout.value = panel }
            compose.runOnIdle { assertEquals(40.dp, bottom) }
        }
    }

    @Test
    fun phoneKeepsItsExistingHorizontalInsets() {
        compose.setContent {
            CompositionLocalProvider(LocalFoldLayout provides FoldLayout.Phone) {
                Box(
                    Modifier.requiredSize(400.dp, 300.dp).testTag("screen")
                        .browsingContentInsets(WindowInsets(left = 40.dp, right = 40.dp)),
                ) {
                    Box(Modifier.fillMaxSize().testTag("content"))
                }
            }
        }
        val outer = compose.onNodeWithTag("screen").fetchSemanticsNode().boundsInRoot
        val inner = compose.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot
        assertEquals(outer.left, inner.left, 1f)
        assertEquals(outer.right, inner.right, 1f)
    }

    @Test
    fun artworkKindsWrapWholeTargetsWithLargeTextInsteadOfStackingLetters() {
        var selected = "poster"
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, 2f),
                LocalFoldLayout provides FoldLayout.CoverPortrait,
            ) {
                TakeupTheme {
                    Box(Modifier.requiredSize(400.dp, 240.dp).testTag("tabs")) {
                        ArtworkKindTabs(selected) { selected = it }
                    }
                }
            }
        }
        val outer = compose.onNodeWithTag("tabs").fetchSemanticsNode().boundsInRoot
        for (kind in listOf("Poster", "Backdrop", "Logo", "Thumb")) {
            val tab = compose.onNodeWithText(kind).assertIsDisplayed()
            val bounds = tab.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= outer.left && bounds.right <= outer.right)
            assertTrue(bounds.height <= with(compose.density) { 64.dp.toPx() })
            tab.performClick()
            compose.runOnIdle { assertEquals(kind.lowercase(), selected) }
        }
    }
}
