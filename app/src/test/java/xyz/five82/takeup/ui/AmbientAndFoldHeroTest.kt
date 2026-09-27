package xyz.five82.takeup.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.five82.takeup.api.Genre
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.Progress
import xyz.five82.takeup.ui.components.GauzeBackground
import xyz.five82.takeup.ui.components.dyeBath
import xyz.five82.takeup.ui.components.houseLights
import xyz.five82.takeup.ui.components.shadowWeave
import xyz.five82.takeup.ui.components.threeThreads
import xyz.five82.takeup.ui.home.FoldHomeHero
import xyz.five82.takeup.ui.theme.TakeupTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AmbientAndFoldHeroTest {
    @get:Rule val compose = createComposeRule()

    @Test fun foldHeroOpensTheFeaturedTitle() {
        var opened = 0
        val item = Item(id = 42, title = "Moonrise", year = 2024,
            genres = listOf(Genre(id = 1, name = "Drama")),
            progress = Progress(positionMs = 500, durationMs = 1000))
        compose.setContent {
            TakeupTheme {
                FoldHomeHero(item, null, null, "Continue watching", FoldLayout.CoverPortrait,
                    onOpen = { opened++ }, safeInsets = WindowInsets(0, 0, 0, 0))
            }
        }
        compose.onNodeWithText("Moonrise").performClick()
        assertEquals(1, opened)
    }

    @Test fun ambientTreatmentsRenderWithEmptyAndMultipleArtSwatches() {
        compose.setContent {
            TakeupTheme {
                Box(Modifier.fillMaxSize().dyeBath(Color.Blue).houseLights(Color.Red)
                    .shadowWeave(listOf(Color.Green, Color.Blue), Color.Red)
                    .threeThreads(listOf(Color.Blue, Color.Red, Color.Green))) {
                    GauzeBackground(null, Color.Blue)
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun singleSwatchAndNoSeedRemainValidFallbacks() {
        compose.setContent {
            TakeupTheme {
                Box(Modifier.fillMaxSize().dyeBath(null)
                    .shadowWeave(listOf(Color.Red), Color.Blue)
                    .threeThreads(emptyList())) {
                    GauzeBackground(null, null)
                }
            }
        }
        compose.waitForIdle()
    }
}
