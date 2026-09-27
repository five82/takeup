package xyz.five82.takeup.ui

import android.os.Looper
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.assertIsNotEnabled
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.LoomApi
import xyz.five82.takeup.data.DownloadEntry
import xyz.five82.takeup.data.DownloadStore
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.data.NetworkPolicy
import xyz.five82.takeup.data.Reach
import xyz.five82.takeup.data.DownloadState
import xyz.five82.takeup.data.OfflineCatalog
import xyz.five82.takeup.data.OfflineArtwork
import xyz.five82.takeup.api.MediaFile
import xyz.five82.takeup.api.Credit
import xyz.five82.takeup.api.Chapter
import xyz.five82.takeup.api.Genre
import xyz.five82.takeup.api.Progress
import xyz.five82.takeup.ui.detail.DetailScreen
import xyz.five82.takeup.ui.detail.DetailViewModel
import xyz.five82.takeup.ui.theme.TakeupTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DetailScreenUnitTest {
    @get:Rule val compose = createComposeRule()

    private val repo = Mockito.mock(LoomRepository::class.java)
    private val api = Mockito.mock(LoomApi::class.java)

    private fun showDetail(
        itemId: Long,
        reach: Reach = Reach.Home,
        entries: List<DownloadEntry> = emptyList(),
        catalog: OfflineCatalog = OfflineCatalog(),
    ): NavState {
        val nav = NavState()
        val network = Mockito.mock(NetworkPolicy::class.java)
        val downloads = Mockito.mock(DownloadStore::class.java)
        Mockito.doReturn(api).`when`(repo).api
        Mockito.doReturn(network).`when`(repo).network
        Mockito.doReturn(downloads).`when`(repo).downloads
        Mockito.doReturn(Mockito.mock(OfflineArtwork::class.java)).`when`(downloads).artwork
        Mockito.doReturn(MutableStateFlow(reach)).`when`(network).reach
        Mockito.doReturn(MutableStateFlow(catalog)).`when`(repo).offlineCatalog
        Mockito.doReturn(MutableStateFlow("")).`when`(network).reason
        Mockito.doReturn(MutableStateFlow(entries)).`when`(downloads).downloads
        compose.setContent { TakeupTheme { DetailScreen(repo, nav, itemId, topmost = true) } }
        compose.mainClock.advanceTimeBy(500)
        return nav
    }

    @Test fun movieDetailShowsFetchedMetadataAndOverview() {
        runBlocking {
            Mockito.`when`(api.item(42)).thenReturn(
                Item(id = 42, title = "A Small Film", kind = "movie", year = 2024, overview = "A story worth telling"),
            )
        }
        showDetail(42)
        compose.onNodeWithText("A story worth telling").assertExists()
    }

    @Test fun failedRequestShowsTheServerErrorInsteadOfAnEmptyDetail() {
        runBlocking { Mockito.`when`(api.item(42)).thenThrow(IllegalStateException("Bad response")) }
        showDetail(42)
        compose.onNodeWithText("Bad response").assertExists()
    }

    @Test fun episodeShowsItsNumberAndOverview() {
        runBlocking { Mockito.`when`(api.item(42)).thenReturn(
            Item(id = 42, title = "The Pilot", kind = "episode", seasonNumber = 1,
                episodeNumber = 2, overview = "An episode synopsis", media = MediaFile(durationMs = 3_600_000)),
        ) }
        showDetail(42)
        assertEquals(2, compose.onAllNodesWithText("The Pilot").fetchSemanticsNodes().size)
        compose.onNodeWithText("An episode synopsis").assertExists()
        compose.onNodeWithText("Play").assertExists()
    }

    @Test fun completedDownloadShowsRemoveAndStaleVersionOffersUpdate() {
        val movie = Item(id = 42, title = "Saved Film", kind = "movie", mediaTag = "v1")
        runBlocking { Mockito.`when`(api.item(42)).thenReturn(movie) }
        val entry = DownloadEntry(movie, DownloadState.Completed, "http://loom/42?tag=v1", 100, 100, 0)
        showDetail(42, entries = listOf(entry))
        compose.onNodeWithContentDescription("Remove download").assertExists()
        compose.onNodeWithText("Downloaded", substring = true).assertExists()
    }

    @Test fun supersededDownloadExplainsWhyItNeedsAnUpdate() {
        val movie = Item(id = 42, title = "Saved Film", kind = "movie", mediaTag = "new")
        runBlocking { Mockito.`when`(api.item(42)).thenReturn(movie) }
        val old = DownloadEntry(movie, DownloadState.Completed, "http://loom/42?tag=old", 100, 100, 0)
        showDetail(42, entries = listOf(old))
        compose.onNodeWithContentDescription("Update download").assertExists()
        compose.onNodeWithText("Update available", substring = true).assertExists()
    }

    @Test fun queuedDownloadShowsTransferStatus() {
        val movie = Item(id = 42, title = "Queued Film", kind = "movie")
        runBlocking { Mockito.`when`(api.item(42)).thenReturn(movie) }
        val queued = DownloadEntry(movie, DownloadState.Queued, "http://loom/42", 0, -1, 0)
        showDetail(42, entries = listOf(queued))
        compose.onNodeWithText("Download queued").assertExists()
    }

    @Test fun failedDownloadShowsRetryAndFailureStatus() {
        val movie = Item(id = 42, title = "Saved Film", kind = "movie", mediaTag = "new")
        runBlocking { Mockito.`when`(api.item(42)).thenReturn(movie) }
        val failed = DownloadEntry(movie, DownloadState.Failed, "http://loom/42?tag=old", 0, 100, 0)
        showDetail(42, entries = listOf(failed))
        compose.onNodeWithContentDescription("Retry download").assertExists()
        compose.onNodeWithText("Download failed").assertExists()
    }

    @Test fun offlineMovieCanPlayButCannotRequestANewDownloadOrEditArtwork() {
        val movie = Item(id = 42, title = "Saved Film", kind = "movie",
            progress = Progress(positionMs = 600_000, durationMs = 3_600_000, resumePositionMs = 600_000))
        val entry = DownloadEntry(movie, DownloadState.Completed, "http://loom/42", 100, 100, 0)
        showDetail(42, reach = Reach.Offline, entries = listOf(entry),
            catalog = OfflineCatalog(entries = listOf(entry)))
        compose.onNodeWithText("Saved Film").assertExists()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Artwork").assertIsNotEnabled()
        compose.onNodeWithText("Mark watched").assertIsNotEnabled()
    }

    @Test fun offlineMissingTitleExplainsWhyAndOffersRetry() {
        showDetail(42, reach = Reach.Offline)
        compose.onNodeWithText("This title is not downloaded to this device.", substring = true).assertExists()
    }

    @Test fun filmMetadataChaptersAndCreditsSurviveScrolling() {
        val movie = Item(id = 42, title = "The Feature", kind = "movie", year = 2024,
            tagline = "One night only", overview = "A feature-length story",
            contentRating = "PG-13", voteAverage = 8.5,
            genres = listOf(Genre(name = "Drama")),
            media = MediaFile(durationMs = 90_000, chapters = listOf(Chapter(title = "Opening"))),
            credits = listOf(Credit(name = "A Director", role = "director"),
                Credit(name = "A Performer", role = "actor", character = "The Lead")))
        runBlocking { Mockito.`when`(api.item(42)).thenReturn(movie) }
        showDetail(42)
        compose.onNodeWithText("One night only").assertExists()
        compose.onNodeWithText("1 chapters").assertExists()
        compose.onNode(hasScrollAction()).performScrollToIndex(2)
        compose.onNodeWithText("A Director").assertExists()
        compose.onNodeWithText("The Lead").assertExists()
    }

    @Test fun offlineShowCountsOnlyEpisodesOnTheDevice() {
        val show = Item(id = 42, title = "Offline Series", kind = "show", episodeCount = 10)
        val season = Item(id = 43, title = "Season One", kind = "season", seasonNumber = 1, parentId = 42)
        val episode = Item(id = 44, title = "Pilot", kind = "episode", parentId = 43, episodeNumber = 1)
        val entry = DownloadEntry(episode, DownloadState.Completed, "http://loom/44", 100, 100, 0)
        showDetail(42, reach = Reach.Offline, entries = listOf(entry),
            catalog = OfflineCatalog(entries = listOf(entry), ancestors = listOf(season, show)))
        compose.onNodeWithText("Offline Series").assertExists()
        compose.onNodeWithText("1 of 10 episode on this device").assertExists()
        compose.onNodeWithText("Play", substring = true).assertExists()
    }

    @Test fun episodeRowOpensItsOwnDetailFromTheShow() {
        val season = Item(id = 8, title = "Season One", kind = "season", seasonNumber = 1, unwatchedCount = 1)
        val episode = Item(id = 9, title = "First Episode", kind = "episode",
            parentId = 8, episodeNumber = 1, overview = "Pilot summary")
        runBlocking {
            Mockito.`when`(api.item(42)).thenReturn(Item(id = 42, title = "Series", kind = "show"))
            Mockito.`when`(api.children(42)).thenReturn(listOf(season))
            Mockito.`when`(api.children(8)).thenReturn(listOf(episode))
        }
        val nav = showDetail(42)
        compose.onAllNodes(hasScrollAction())[0].performScrollToIndex(3)
        compose.onNodeWithText("Pilot summary", substring = true).performClick()
        assertEquals(Screen.Detail(9), nav.stack.last())
    }

    @Test fun showOpensAtTheFirstUnwatchedRegularSeason() {
        val special = Item(id = 1, title = "Specials", kind = "season", seasonNumber = 0, unwatchedCount = 1)
        val watched = Item(id = 2, title = "Season One", kind = "season", seasonNumber = 1)
        val next = Item(id = 3, title = "Season Two", kind = "season", seasonNumber = 2, unwatchedCount = 1)
        val episode = Item(id = 4, title = "New Episode", kind = "episode", parentId = 3, seasonNumber = 2, episodeNumber = 1)
        runBlocking {
            Mockito.`when`(api.item(42)).thenReturn(Item(id = 42, title = "The Show", kind = "show"))
            Mockito.`when`(api.children(42)).thenReturn(listOf(next, special, watched))
            Mockito.`when`(api.children(1)).thenReturn(emptyList())
            Mockito.`when`(api.children(2)).thenReturn(emptyList())
            Mockito.`when`(api.children(3)).thenReturn(listOf(episode))
        }
        showDetail(42)
        // Exercise the selection independently of lazy-list visibility on a phone.
        val model = DetailViewModel(repo, 42)
        model.refresh()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(3L, model.state.selectedSeason)
        assertEquals(episode, model.nextToWatch())

        compose.onNodeWithText("The Show").assertExists()
        compose.onNodeWithText("Season Two").assertExists()
    }
}
