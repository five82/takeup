package xyz.five82.takeup.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.MediaFile
import xyz.five82.takeup.api.Stream
import xyz.five82.takeup.ui.detail.BadgeStrip
import xyz.five82.takeup.ui.detail.DetailArtwork
import xyz.five82.takeup.ui.theme.TakeupTheme

class FoldDetailTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun everyDetailKindUsesTheSameNativePhotoAndMeasuredIdentity() {
        val kind = mutableStateOf("movie")
        compose.setContent {
            CompositionLocalProvider(LocalFoldLayout provides FoldLayout.CoverPortrait) {
                TakeupTheme {
                    Column(Modifier.requiredWidth(400.dp).verticalScroll(rememberScrollState())) {
                        Box(Modifier.testTag("hero")) {
                            DetailArtwork(Item(title = "Title", kind = kind.value, year = 2026), null, null, false)
                        }
                    }
                }
            }
        }
        for (value in listOf("movie", "short", "show", "episode")) {
            compose.runOnIdle { kind.value = value }
            val hero = compose.onNodeWithTag("hero").fetchSemanticsNode().boundsInRoot
            val title = compose.onNodeWithText("Title").fetchSemanticsNode().boundsInRoot
            val metadata = compose.onNodeWithText("2026").fetchSemanticsNode().boundsInRoot
            assertEquals(with(compose.density) { 225.dp.toPx() }, title.top - hero.top, 1f)
            assertTrue(metadata.top >= title.bottom)
            assertTrue(hero.bottom >= metadata.bottom)
        }
    }

    @Test
    fun landscapeMetadataStacksAtLargeTextWithoutChangingPhotoGeometry() {
        val fontScale = mutableStateOf(1f)
        val titleText = "A long title without a logo"
        compose.setContent {
            CompositionLocalProvider(
                LocalFoldLayout provides FoldLayout.CoverLandscape,
                LocalDensity provides Density(LocalDensity.current.density, fontScale.value),
            ) {
                TakeupTheme {
                    Column(Modifier.requiredWidth(591.dp).verticalScroll(rememberScrollState())) {
                        Box(Modifier.testTag("hero")) {
                            DetailArtwork(Item(title = titleText, year = 2026), null, null, true)
                        }
                    }
                }
            }
        }
        val title = compose.onNodeWithText(titleText).fetchSemanticsNode().boundsInRoot
        val metadata = compose.onNodeWithText("2026").fetchSemanticsNode().boundsInRoot
        assertTrue(metadata.left > title.right)
        compose.runOnIdle { fontScale.value = 2f }
        val largeTitle = compose.onNodeWithText(titleText).fetchSemanticsNode().boundsInRoot
        val largeMetadata = compose.onNodeWithText("2026").fetchSemanticsNode().boundsInRoot
        val hero = compose.onNodeWithTag("hero").fetchSemanticsNode().boundsInRoot
        assertEquals(with(compose.density) { (591.dp * 9f / 16f).toPx() }, largeTitle.top - hero.top, 1f)
        assertTrue(largeMetadata.top >= largeTitle.bottom)
    }

    @Test
    fun technicalBadgesWrapWholeLabelsAtLargeTextInsteadOfClipping() {
        val width = mutableStateOf(320)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                TakeupTheme {
                    Column(Modifier.requiredWidth(width.value.dp).testTag("badges")) {
                        BadgeStrip(
                            Item(media = MediaFile(
                                size = 65_000_000_000,
                                streams = listOf(
                                    Stream(kind = "video", resolution = "4k", dynamicRange = "dolby_vision", codec = "hevc"),
                                    Stream(kind = "audio", codec = "truehd", channels = 8),
                                ),
                            )),
                        )
                    }
                }
            }
        }
        for (paneWidth in listOf(320, 387, 435, 484, 551, 672)) {
            compose.runOnIdle { width.value = paneWidth }
            val outer = compose.onNodeWithTag("badges").fetchSemanticsNode().boundsInRoot
            for (label in listOf("4K", "DOLBY VISION", "HEVC", "TRUEHD 7.1", "65.0 GB")) {
                val badge = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
                assertTrue("$label must fit in $paneWidth dp", badge.left >= outer.left && badge.right <= outer.right)
                assertTrue("$label must remain a single line", badge.height < 40f)
                assertTrue(badge.bottom <= outer.bottom)
            }
        }
    }

    @Test
    fun phoneDetailKeepsItsOriginalFourByThreePhotoAndLogoLane() {
        compose.setContent {
            TakeupTheme {
                Column(Modifier.requiredWidth(400.dp).verticalScroll(rememberScrollState())) {
                    Box(Modifier.testTag("hero")) {
                        DetailArtwork(Item(title = "Title"), null, null, false)
                    }
                }
            }
        }
        val hero = compose.onNodeWithTag("hero").fetchSemanticsNode().boundsInRoot
        // 300dp photo - 16dp overlap + original 116dp no-logo lane.
        assertEquals(with(compose.density) { 400.dp.toPx() }, hero.height, 1f)
    }
}
