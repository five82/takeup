package xyz.five82.takeup.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import xyz.five82.takeup.api.Genre
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.ui.components.BiasCutBackdrop
import xyz.five82.takeup.ui.home.FoldHomeHero
import xyz.five82.takeup.ui.theme.TakeupTheme
import xyz.five82.takeup.ui.theme.Surface1

class FoldHomeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun backdropKeepsPhoneDefaultAndFoldAspectRatio() {
        val aspect = mutableStateOf(4f / 3f)
        compose.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                BiasCutBackdrop(
                    imageUrl = null,
                    solidLeft = 16.dp,
                    artAspectRatio = aspect.value,
                    modifier = Modifier.requiredWidth(400.dp).testTag("art"),
                )
            }
        }
        fun heightDp(): Float = with(compose.density) {
            compose.onNodeWithTag("art").fetchSemanticsNode().boundsInRoot.height.toDp().value
        }
        assertEquals(300f, heightDp(), 1f)
        compose.runOnIdle { aspect.value = 16f / 9f }
        compose.waitForIdle()
        assertEquals(225f, heightDp(), 1f)
    }

    @Test
    fun longTitleWithoutLogoAndLargeTextGrowBelowThePhoto() {
        val title = "A very long movie title without a logo or a cached backdrop"
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                TakeupTheme {
                    Column(Modifier.requiredWidth(400.dp).verticalScroll(rememberScrollState())) {
                        Box(Modifier.testTag("hero")) {
                            FoldHomeHero(
                                Item(title = title, year = 2017, genres = listOf(Genre(name = "Science fiction"))),
                                backdrop = null,
                                logo = null,
                                label = "Today's Pick",
                                layout = FoldLayout.CoverLandscape,
                                onOpen = {},
                            )
                        }
                        Text("Shelf follows hero")
                    }
                }
            }
        }
        val hero = compose.onNodeWithTag("hero").fetchSemanticsNode().boundsInRoot
        val heading = compose.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val metadata = compose.onNodeWithText("Today's Pick", substring = true, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val photoHeight = with(compose.density) { 225.dp.toPx() }
        assertTrue(heading.top >= hero.top + photoHeight - 1f)
        assertTrue(metadata.top >= heading.bottom)
        assertTrue(hero.bottom >= metadata.bottom)
    }

    @Test
    fun landscapeIdentityIsInlineAndTheWholeHeroOpensDetails() {
        var opened = false
        compose.setContent {
            TakeupTheme {
                Column(Modifier.requiredWidth(591.dp).verticalScroll(rememberScrollState())) {
                    FoldHomeHero(
                        Item(title = "Up", year = 2009),
                        backdrop = null,
                        logo = null,
                        label = "Today's Pick",
                        layout = FoldLayout.CoverLandscape,
                        onOpen = { opened = true },
                    )
                }
            }
        }
        val title = compose.onNodeWithText("Up", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithText("Today's Pick", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(label.left > title.right)
        compose.onNodeWithText("Today's Pick", substring = true).performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun sidebarKeepsItsWidthAndMenuSpacingRegardlessOfCameraInset() {
        compose.setContent {
            TakeupTheme {
                Box(Modifier.requiredSize(160.dp, 340.dp).testTag("sidebar")) {
                    FoldSidebar(NavState(), FoldLayout.CoverLandscape, safeInsets = WindowInsets(left = 40.dp))
                }
            }
        }
        val sidebar = compose.onNodeWithTag("sidebar")
        val bounds = sidebar.fetchSemanticsNode().boundsInRoot
        val home = compose.onNodeWithText("Home").fetchSemanticsNode().boundsInRoot
        with(compose.density) {
            assertEquals(160.dp.toPx(), bounds.width, 1f)
            assertEquals(8.dp.toPx(), home.left - bounds.left, 1f)
        }
        val pixels = sidebar.captureToImage().toPixelMap()
        assertEquals(Surface1.toArgb(), pixels[1, pixels.height / 2].toArgb())
        compose.onNodeWithContentDescription("Search").assertIsDisplayed()
        compose.onNodeWithContentDescription("Downloads").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun cameraInsetsProtectIdentityWithoutInsettingThePhoto() {
        compose.setContent {
            TakeupTheme {
                Column(Modifier.requiredWidth(400.dp).verticalScroll(rememberScrollState())) {
                    Box(Modifier.testTag("hero")) {
                        FoldHomeHero(
                            Item(title = "Up", year = 2009), backdrop = null, logo = null,
                            label = "Today's Pick", layout = FoldLayout.CoverPortrait,
                            onOpen = {}, safeInsets = WindowInsets(left = 40.dp, top = 30.dp),
                        )
                    }
                }
            }
        }
        val hero = compose.onNodeWithTag("hero").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText("Up", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        with(compose.density) {
            // Full-width 16:9 photo, with no status-bar band above it. Only the
            // identity gets the 40dp camera clearance plus its normal 20dp inset.
            assertEquals(225.dp.toPx(), title.top - hero.top, 1f)
            assertEquals(60.dp.toPx(), title.left - hero.left, 1f)
        }
    }

    @Test
    fun inlineIdentityUsesTheCameraSafeWidthNotThePhotoWidth() {
        compose.setContent {
            TakeupTheme {
                Column(Modifier.requiredWidth(591.dp).verticalScroll(rememberScrollState())) {
                    FoldHomeHero(
                        Item(title = "Up"), backdrop = null, logo = null,
                        label = "Today's Pick", layout = FoldLayout.CoverLandscape,
                        onOpen = {}, safeInsets = WindowInsets(left = 140.dp),
                    )
                }
            }
        }
        val title = compose.onNodeWithText("Up", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithText("Today's Pick", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(label.top >= title.bottom)
    }

    @Test
    fun compactSidebarUtilitiesStayReachableWithLargeTextAndShortHeight() {
        val nav = NavState()
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                TakeupTheme {
                    Box(Modifier.requiredSize(160.dp, 340.dp)) {
                        FoldSidebar(nav, FoldLayout.CoverLandscape, safeInsets = WindowInsets(0, 0, 0, 0))
                    }
                }
            }
        }
        compose.onNodeWithText("Browse").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(Tab.Browse, nav.tab) }
        compose.onNodeWithContentDescription("Search").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(Screen.Search(), nav.stack.last()) }
        compose.onNodeWithContentDescription("Downloads").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(Screen.Downloads, nav.stack.last()) }
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(Screen.Settings, nav.stack.last()) }
    }
}
