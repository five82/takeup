package xyz.five82.takeup.ui.player

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.datasource.DefaultDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import xyz.five82.takeup.TakeupApplication
import xyz.five82.takeup.api.Chapter
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.LoomApi
import xyz.five82.takeup.api.MediaFile
import xyz.five82.takeup.api.PlaybackInfo
import xyz.five82.takeup.data.DownloadStore
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.ui.NavState
import xyz.five82.takeup.ui.Screen
import xyz.five82.takeup.ui.takeupViewModel
import xyz.five82.takeup.ui.theme.TakeupTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = TakeupApplication::class)
class PlayerScreenUnitTest {
    @get:Rule val compose = createComposeRule()
    private val repo = Mockito.mock(LoomRepository::class.java)
    private val api = Mockito.mock(LoomApi::class.java)
    private val downloads = Mockito.mock(DownloadStore::class.java)
    private lateinit var model: PlayerViewModel
    private val nav = NavState()

    private fun show(item: Item, failOnce: Boolean = false, following: Item? = null) {
        Mockito.doReturn(api).`when`(repo).api
        Mockito.doReturn(downloads).`when`(repo).downloads
        Mockito.doReturn(MutableStateFlow(false)).`when`(repo).dialogueBoost
        Mockito.doReturn(DefaultDataSource.Factory(RuntimeEnvironment.getApplication()))
            .`when`(downloads).playbackDataSourceFactory
        runBlocking {
            if (following != null) {
                val season = Item(id = item.parentId!!, kind = "season", parentId = 5)
                Mockito.`when`(api.item(season.id)).thenReturn(season)
                Mockito.`when`(api.children(5)).thenReturn(listOf(season))
                Mockito.`when`(api.children(item.parentId)).thenReturn(listOf(item, following))
            }
            if (failOnce) {
                Mockito.`when`(api.item(item.id)).thenThrow(IllegalStateException("Loom is unavailable")).thenReturn(item)
            } else {
                Mockito.`when`(api.item(item.id)).thenReturn(item)
            }
            Mockito.`when`(api.playback(item.id)).thenReturn(
                PlaybackInfo(media = item.media!!, streamUrl = "http://127.0.0.1:1/movie"),
            )
        }
        Mockito.`when`(api.absoluteUrl("http://127.0.0.1:1/movie")).thenReturn("http://127.0.0.1:1/movie")
        compose.setContent {
            TakeupTheme {
                model = takeupViewModel("player-${item.id}") {
                    PlayerViewModel(RuntimeEnvironment.getApplication() as TakeupApplication, repo, item.id)
                }
                PlayerScreen(repo, nav, item.id)
            }
        }
    }

    @Test fun titleAndChapterControlsAppearBeforeVideoIsReady() {
        show(Item(
            id = 17, title = "Sample Film", kind = "movie",
            media = MediaFile(tag = "a", durationMs = 90_000, chapters = listOf(
                Chapter(0, 0, "Opening"), Chapter(1, 45_000, "Middle"),
            )),
        ))
        compose.onNodeWithText("Sample Film").assertExists()
        compose.onNodeWithText("Opening").assertExists()
        compose.onNodeWithContentDescription("Crop video").performClick()
        compose.onNodeWithContentDescription("Fit video").assertExists()
        compose.onNodeWithText("Chapters").assertExists()
        compose.onNodeWithContentDescription("Play").assertExists()
        compose.runOnIdle { nav.push(Screen.Player(17)) }
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(emptyList<Screen>(), nav.stack.toList())
    }

    @Test fun finishedEpisodeOffersTheNextEpisode() {
        val next = Item(id = 21, title = "Second", kind = "episode", parentId = 10, seasonNumber = 1, episodeNumber = 2)
        show(Item(
            id = 20, title = "First", kind = "episode", parentId = 10,
            seasonNumber = 1, episodeNumber = 1,
            media = MediaFile(tag = "episode", durationMs = 90_000),
        ), following = next)
        compose.onNodeWithText("S1E1 · First").assertExists()
        compose.runOnIdle {
            assertEquals(next, model.nextEpisode)
            model.ended = true
        }
        compose.onNodeWithText("Up next").assertExists()
        compose.onNodeWithText("S1E2 · Second").performClick()
        assertEquals(Screen.Player(21), nav.stack.last())
    }

    @Test fun playbackFailureCanBeRetried() {
        show(Item(id = 19, title = "Retry Film", kind = "movie", media = MediaFile(tag = "c", durationMs = 90_000)), failOnce = true)
        compose.onNodeWithText("Loom is unavailable").assertExists()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Retry Film").assertExists()
    }

    @Test fun finishedFilmOffersReplayAndExit() {
        show(Item(id = 18, title = "Finished Film", kind = "movie", media = MediaFile(tag = "b", durationMs = 90_000)))
        compose.runOnIdle {
            nav.push(Screen.Player(18))
            model.ended = true
        }
        compose.onNodeWithText("Play again").performClick()
        compose.runOnIdle { assertFalse(model.ended) }
        compose.runOnIdle { model.ended = true }
        compose.onNodeWithText("Done").performClick()
        assertEquals(emptyList<Screen>(), nav.stack.toList())
    }
}
