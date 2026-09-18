package xyz.five82.takeup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import xyz.five82.takeup.ui.theme.TakeupTheme
import xyz.five82.takeup.ui.theme.Teal

class FoldNavPillTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun floatingRootPillLeavesBackgroundVisibleAndReturnsAfterDetail() {
        val nav = NavState()
        compose.setContent {
            TakeupTheme {
                val haze = rememberHazeState()
                Box(Modifier.requiredSize(400.dp, 600.dp).testTag("screen")) {
                    Box(Modifier.fillMaxSize().background(Teal).hazeSource(haze))
                    if (showNavPill(FoldLayout.CoverPortrait, nav.stack.lastOrNull())) {
                        TakeupNavPill(nav, haze, Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
        for (tab in Tab.entries) compose.onNodeWithContentDescription(tab.label).assertIsDisplayed()
        val pixels = compose.onNodeWithTag("screen").captureToImage().toPixelMap()
        val pillY = pixels.height - with(compose.density) { 64.dp.roundToPx() }
        assertEquals(Teal.toArgb(), pixels[1, pillY].toArgb())
        assertEquals(Teal.toArgb(), pixels[pixels.width - 2, pillY].toArgb())
        compose.runOnIdle { nav.push(Screen.Detail(34)) }
        for (tab in Tab.entries) compose.onNodeWithContentDescription(tab.label).assertDoesNotExist()
        compose.runOnIdle { nav.pop() }
        for (tab in Tab.entries) compose.onNodeWithContentDescription(tab.label).assertIsDisplayed()
        compose.onNodeWithContentDescription("Movies").performClick()
        compose.runOnIdle {
            assertEquals(Tab.Movies, nav.tab)
            assertTrue(nav.stack.isEmpty())
        }
    }
}
