package xyz.five82.takeup.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
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
import xyz.five82.takeup.api.Genre
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.Progress
import xyz.five82.takeup.ui.components.PosterCard
import xyz.five82.takeup.ui.components.ThumbCard
import xyz.five82.takeup.ui.home.Hero
import xyz.five82.takeup.ui.home.HomeFeed
import xyz.five82.takeup.ui.home.HomeRow
import xyz.five82.takeup.ui.theme.TakeupTheme

class HomeSpacingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun heroAndEveryShelfShareTheSameGapAcrossPhoneAndFoldViews() {
        val layout = mutableStateOf(FoldLayout.Phone)
        val width = mutableStateOf(427)
        val fontScale = mutableStateOf(1f)
        val progress = mutableStateOf<Progress?>(null)
        compose.setContent {
            // Fit full feeds on the Pixel test host while measuring actual dp,
            // including both stacked and inline Fold identity arrangements.
            CompositionLocalProvider(
                LocalDensity provides Density(1f, fontScale.value),
                LocalFoldLayout provides layout.value,
            ) {
                TakeupTheme {
                    Box(Modifier.requiredSize(width.value.dp, 2400.dp)) {
                        HomeFeed {
                            item {
                                Box(Modifier.testTag("hero")) {
                                    Hero(
                                        Item(
                                            title = "A long featured movie title without logo artwork",
                                            year = 2026,
                                            genres = listOf(Genre(name = "Science fiction")),
                                            progress = progress.value,
                                        ),
                                        backdrop = null, logo = null, label = "Today's Pick", onOpen = {},
                                    )
                                }
                            }
                            for (index in 0..3) {
                                item {
                                    Box(Modifier.testTag("row-$index")) {
                                        HomeRow("Shelf $index") {
                                            item {
                                                if (index < 2) {
                                                    ThumbCard(
                                                        title = "Episode", imageUrl = null,
                                                        heading = "Show", line = "S1E1 - Episode",
                                                        width = layout.value.thumbWidth,
                                                        modifier = Modifier.testTag("card-$index"), onClick = {},
                                                    )
                                                } else {
                                                    PosterCard(
                                                        title = "Movie", imageUrl = null, width = 128,
                                                        modifier = Modifier.testTag("card-$index"), onClick = {},
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val views = listOf(
            FoldLayout.Phone to 427,
            FoldLayout.Phone to 952,
            FoldLayout.CoverPortrait to 475,
            FoldLayout.CoverLandscape to 591,
            FoldLayout.InnerPortrait to 524,
            FoldLayout.InnerLandscape to 712,
        )
        for ((view, paneWidth) in views) {
            for (scale in listOf(1f, 2f)) {
                for (resuming in listOf(false, true)) {
                    compose.runOnIdle {
                        layout.value = view
                        width.value = paneWidth
                        fontScale.value = scale
                        progress.value = if (resuming) Progress(positionMs = 100, durationMs = 1000) else null
                    }
                    var previous = compose.onNodeWithTag("hero").fetchSemanticsNode().boundsInRoot
                    for (index in 0..3) {
                        val label = compose.onNodeWithText("SHELF $index").fetchSemanticsNode().boundsInRoot
                        val card = compose.onNodeWithTag("card-$index").fetchSemanticsNode().boundsInRoot
                        assertEquals("$view / $scale / $resuming: section $index", 24f, label.top - previous.bottom, 1f)
                        assertEquals("$view: heading to card", 12f, card.top - label.bottom, 1f)
                        previous = compose.onNodeWithTag("row-$index").fetchSemanticsNode().boundsInRoot
                    }
                    val title = compose.onNodeWithText(
                        "A long featured movie title without logo artwork", useUnmergedTree = true,
                    ).fetchSemanticsNode().boundsInRoot
                    // Large titles stay below the art rather than growing up
                    // through it in the phone's old estimated overlay band.
                    val photoHeight = paneWidth * if (view == FoldLayout.Phone) 3f / 4f else 9f / 16f
                    val hero = compose.onNodeWithTag("hero").fetchSemanticsNode().boundsInRoot
                    assertTrue(title.top >= hero.top + photoHeight - 1f)
                    if (!view.inlineHeroIdentity || scale == 2f) {
                        val metadata = compose.onNodeWithText(
                            "Today's Pick", substring = true, useUnmergedTree = true,
                        ).fetchSemanticsNode().boundsInRoot
                        assertEquals("$view: title to metadata", 12f, metadata.top - title.bottom, 1f)
                    }
                }
            }
        }
    }
}
