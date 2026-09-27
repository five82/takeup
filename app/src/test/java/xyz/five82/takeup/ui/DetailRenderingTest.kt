package xyz.five82.takeup.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.MediaFile
import xyz.five82.takeup.api.Stream
import xyz.five82.takeup.ui.detail.BadgeStrip
import xyz.five82.takeup.ui.detail.DetailArtwork
import xyz.five82.takeup.ui.theme.TakeupTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DetailRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun portraitDetailWithoutArtworkShowsTitleAndMetadata() {
        compose.setContent {
            CompositionLocalProvider(LocalFoldLayout provides FoldLayout.CoverPortrait) {
                TakeupTheme { DetailArtwork(Item(title = "The Film", year = 2024), null, null, false) }
            }
        }
        compose.onNodeWithText("The Film").assertExists()
        compose.onNodeWithText("2024").assertExists()
    }

    @Test fun technicalBadgesShowActualMediaProperties() {
        compose.setContent {
            TakeupTheme {
                Column {
                    BadgeStrip(Item(media = MediaFile(
                        size = 65_000_000_000,
                        streams = listOf(
                            Stream(kind = "video", resolution = "4k", dynamicRange = "dolby_vision", codec = "hevc"),
                            Stream(kind = "audio", codec = "truehd", channels = 8),
                        ),
                    )))
                }
            }
        }
        for (label in listOf("4K", "DOLBY VISION", "HEVC", "TRUEHD 7.1", "65.0 GB")) {
            compose.onNodeWithText(label).assertExists()
        }
    }
}
