package xyz.five82.takeup.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.hasScrollAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.five82.takeup.api.Collection
import xyz.five82.takeup.api.Genre
import xyz.five82.takeup.api.Home
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.LoomApi
import xyz.five82.takeup.api.SearchResponse
import xyz.five82.takeup.api.Shelf
import xyz.five82.takeup.api.Progress
import xyz.five82.takeup.data.DownloadEntry
import xyz.five82.takeup.data.DownloadState
import xyz.five82.takeup.data.OfflineArtwork
import xyz.five82.takeup.data.DownloadStore
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.data.NetworkPolicy
import xyz.five82.takeup.data.OfflineCatalog
import xyz.five82.takeup.data.Reach
import xyz.five82.takeup.data.ServerConfig
import xyz.five82.takeup.ui.browse.BrowseScreen
import xyz.five82.takeup.ui.home.HomeScreen
import xyz.five82.takeup.ui.library.LibraryScreen
import xyz.five82.takeup.ui.search.SearchScreen
import xyz.five82.takeup.ui.settings.SettingsScreen
import xyz.five82.takeup.ui.theme.TakeupTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class LowCoverageScreensTest {
    @get:Rule val compose = createComposeRule()

    private val repo = Mockito.mock(LoomRepository::class.java)
    private val api = Mockito.mock(LoomApi::class.java)
    private val network = Mockito.mock(NetworkPolicy::class.java)
    private val downloads = Mockito.mock(DownloadStore::class.java)
    private val reach = MutableStateFlow(Reach.Home)
    private val catalog = MutableStateFlow(OfflineCatalog())
    private val nav = NavState()

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        Mockito.doReturn(api).`when`(repo).api
        Mockito.doReturn(network).`when`(repo).network
        Mockito.doReturn(downloads).`when`(repo).downloads
        Mockito.doReturn(Mockito.mock(OfflineArtwork::class.java)).`when`(downloads).artwork
        Mockito.doReturn(reach).`when`(network).reach
        Mockito.doReturn(MutableStateFlow("No connection.")).`when`(network).reason
        Mockito.doReturn(MutableStateFlow(false)).`when`(network).allowCellular
        Mockito.doReturn(MutableStateFlow(emptyList<DownloadEntry>())).`when`(downloads).downloads
        Mockito.doReturn(catalog).`when`(repo).offlineCatalog
        Mockito.doReturn(MutableStateFlow(ServerConfig(true, "192.168.1.20:8097"))).`when`(repo).server
        compose.setContent { TakeupTheme { content() } }
        compose.mainClock.advanceTimeBy(500)
    }

    @Test fun emptyLibraryExplainsHowToPopulateIt() {
        runBlocking {
            Mockito.`when`(api.allItems("movies")).thenReturn(emptyList())
        }
        show { LibraryScreen(repo, nav, "movies", active = true) }
        compose.onNodeWithText("Nothing here yet. Scan the library from Settings.").assertExists()
    }

    @Test fun offlineLibraryOffersRetryAndSettingsInsteadOfStalePosters() {
        reach.value = Reach.Offline
        show { LibraryScreen(repo, nav, "tv", active = true) }
        compose.onNodeWithText("TV").assertExists()
        compose.onNodeWithText("Settings").assertExists()
        Mockito.verifyNoInteractions(api)
    }

    @Test fun libraryDisplaysServerTitlesAndOpensDetail() {
        runBlocking {
            Mockito.`when`(api.allItems("movies")).thenReturn(listOf(Item(id = 42, title = "A Small Film", kind = "movie")))
        }
        show { LibraryScreen(repo, nav, "movies", active = true) }
        compose.onNodeWithText("A SMALL FILM").performClick()
        org.junit.Assert.assertEquals(Screen.Detail(42), nav.stack.last())
    }

    @Test fun browseDisplaysCollectionsAndGenres() {
        runBlocking {
            Mockito.`when`(api.collections()).thenReturn(listOf(Collection("classics", "Classics", listOf(Item(id = 3, title = "Classic Film")))))
            Mockito.`when`(api.genres()).thenReturn(listOf(Genre(id = 7, name = "Drama", itemCount = 1)))
        }
        show { BrowseScreen(repo, nav, active = true) }
        compose.onNodeWithText("Classics").assertExists()
        compose.onNodeWithText("Genres").performClick()
        compose.onNodeWithText("Drama").assertExists()
    }

    @Test fun browseOfflineExplainsWhyCollectionsAreUnavailable() {
        reach.value = Reach.Offline
        show { BrowseScreen(repo, nav, active = true) }
        compose.onNodeWithText("Browse").assertExists()
        compose.onNodeWithText("Settings").assertExists()
        Mockito.verifyNoInteractions(api)
    }

    @Test fun searchShowsClosestMatchesAndClearsThemWithTheQuery() {
        runBlocking {
            Mockito.`when`(api.search("movey")).thenReturn(SearchResponse(listOf(Item(id = 12, title = "Movie", kind = "movie")), fuzzy = true))
        }
        show { SearchScreen(repo, nav, "movey", active = false) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Closest matches").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Closest matches").assertExists()
        compose.onNodeWithText("Movie").performClick()
        org.junit.Assert.assertEquals(Screen.Detail(12), nav.stack.last())
        compose.onNodeWithContentDescription("Clear").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Closest matches").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Movie").assertDoesNotExist()
    }

    @Test fun searchOfflineFindsNothingWithoutCallingServer() {
        reach.value = Reach.Offline
        show { SearchScreen(repo, nav, "missing", active = false) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Nothing downloaded matches \"missing\".").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Nothing downloaded matches \"missing\".").assertExists()
        Mockito.verifyNoInteractions(api)
    }

    @Test fun settingsValidatesAddressBeforeContactingLoom() {
        reach.value = Reach.Offline
        show { SettingsScreen(repo, nav) }
        compose.onNodeWithText("Loom address").performTextClearance()
        compose.onNodeWithText("Loom address").performTextInput("http://")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Enter an address like 192.168.1.20:8097").assertExists()
        Mockito.verifyNoInteractions(api)
    }

    @Test fun homeShowsFeaturedFilm() {
        runBlocking {
            Mockito.`when`(api.home()).thenReturn(Home(
                featured = Item(id = 1, title = "Featured Film", kind = "movie"),
                continueWatching = listOf(Item(id = 2, title = "In Progress", kind = "movie")),
            ))
        }
        show { HomeScreen(repo, nav, active = true) }
        compose.onNodeWithText("Featured Film").assertExists()
    }

    @Test fun offlineHomeLeadsWithAStartedDownloadNotTheNewestTitle() {
        reach.value = Reach.Offline
        val started = Item(id = 10, title = "Watching Now", kind = "movie",
            progress = Progress(positionMs = 500, durationMs = 1000))
        val recent = Item(id = 11, title = "Fresh Download", kind = "movie")
        catalog.value = OfflineCatalog(entries = listOf(
            DownloadEntry(started, DownloadState.Completed, "http://loom/10", 100, 100, 10),
            DownloadEntry(recent, DownloadState.Completed, "http://loom/11", 100, 100, 20),
        ))
        show { HomeScreen(repo, nav, active = true) }
        org.junit.Assert.assertTrue(compose.onAllNodesWithText("Watching Now").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("Continue watching", substring = true).assertExists()
        Mockito.verifyNoInteractions(api)
    }

    @Test fun homeRendersNextUpRecentlyAddedAndRotatingShelf() {
        runBlocking { Mockito.`when`(api.home()).thenReturn(Home(
            nextUp = listOf(Item(id = 2, title = "Next Episode", kind = "episode")),
            recentlyAdded = listOf(Item(id = 3, title = "New Arrival", kind = "movie")),
            shelves = listOf(Shelf(key = "classics", title = "Classics", items = listOf(
                Item(id = 4, title = "Old Favourite", kind = "movie")))),
        )) }
        show { HomeScreen(repo, nav, active = true) }
        compose.onAllNodes(hasScrollAction())[0].performScrollToIndex(1)
        compose.onNodeWithText("NEXT UP").assertExists()
        compose.onAllNodes(hasScrollAction())[0].performScrollToIndex(2)
        compose.onNodeWithText("RECENTLY ADDED").assertExists()
        compose.onAllNodes(hasScrollAction())[0].performScrollToIndex(3)
        compose.onNodeWithText("CLASSICS").assertExists()
        compose.onNodeWithText("OLD FAVOURITE").performClick()
        org.junit.Assert.assertEquals(Screen.Detail(4), nav.stack.last())
    }

    @Test fun homeServerErrorOffersSettings() {
        runBlocking { Mockito.`when`(api.home()).thenThrow(IllegalStateException("Server is busy")) }
        show { HomeScreen(repo, nav, active = true) }
        compose.onNodeWithText("Server is busy").assertExists()
        compose.onNodeWithText("Server settings").performClick()
        org.junit.Assert.assertEquals(Screen.Settings, nav.stack.last())
    }
}
