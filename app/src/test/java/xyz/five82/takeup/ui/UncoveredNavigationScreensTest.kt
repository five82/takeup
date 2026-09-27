package xyz.five82.takeup.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.five82.takeup.api.Collection
import xyz.five82.takeup.api.ImageOption
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.LoomApi
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.data.NetworkPolicy
import xyz.five82.takeup.data.Reach
import xyz.five82.takeup.ui.artwork.ArtworkScreen
import xyz.five82.takeup.ui.browse.CollectionGridScreen
import xyz.five82.takeup.ui.browse.GenreGridScreen
import xyz.five82.takeup.ui.theme.TakeupTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UncoveredNavigationScreensTest {
    @get:Rule val compose = createComposeRule()
    private val repo = Mockito.mock(LoomRepository::class.java)
    private val api = Mockito.mock(LoomApi::class.java)
    private val network = Mockito.mock(NetworkPolicy::class.java)
    private val reach = MutableStateFlow(Reach.Home)
    private val nav = NavState()

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        Mockito.doReturn(api).`when`(repo).api
        Mockito.doReturn(network).`when`(repo).network
        Mockito.doReturn(reach).`when`(network).reach
        Mockito.doReturn(MutableStateFlow("No route to Loom.")).`when`(network).reason
        compose.setContent { TakeupTheme { content() } }
        compose.mainClock.advanceTimeBy(500)
    }

    @Test fun genreGridOpensAnItemAndCanGoBack() {
        runBlocking { Mockito.`when`(api.allItems("movies", 8)).thenReturn(listOf(Item(id = 42, title = "A Film", kind = "movie"))) }
        show { GenreGridScreen(repo, nav, Screen.GenreGrid(8, "Drama")) }
        compose.onNodeWithText("Drama").assertExists()
        compose.onNodeWithText("A FILM", substring = true).performClick()
        assertEquals(Screen.Detail(42), nav.stack.last())
    }

    @Test fun collectionGridUsesTheSelectedCollectionNotTheFirst() {
        runBlocking { Mockito.`when`(api.collections()).thenReturn(listOf(
            Collection("other", "Other", listOf(Item(id = 1, title = "Wrong"))),
            Collection("chosen", "Chosen", listOf(Item(id = 2, title = "Right"))),
        )) }
        show { CollectionGridScreen(repo, nav, Screen.CollectionGrid("chosen", "Chosen")) }
        compose.onNodeWithText("RIGHT", substring = true).assertExists()
        compose.onNodeWithText("WRONG", substring = true).assertDoesNotExist()
    }

    @Test fun offlineGridDoesNotRequestACollection() {
        reach.value = Reach.Offline
        show { CollectionGridScreen(repo, nav, Screen.CollectionGrid("chosen", "Chosen")) }
        compose.onNodeWithText("No route to Loom.", substring = true).assertExists()
        Mockito.verifyNoInteractions(api)
    }

    @Test fun failedGridRequestOffersRetry() {
        runBlocking { Mockito.`when`(api.allItems("movies", 8)).thenThrow(IllegalStateException("Bad response")) }
        show { GenreGridScreen(repo, nav, Screen.GenreGrid(8, "Drama")) }
        compose.onNodeWithText("Bad response").assertExists()
    }

    @Test fun artworkTabsLoadEachKindAndSelectionCanBeReset() {
        val poster = ImageOption(provider = "tmdb", providerPath = "/poster", width = 300, height = 450, thumbnailUrl = "")
        runBlocking {
            Mockito.`when`(api.imageOptions(42, "poster")).thenReturn(listOf(poster))
            Mockito.`when`(api.imageOptions(42, "backdrop")).thenReturn(emptyList())
        }
        show { ArtworkScreen(repo, nav, 42, "Film") }
        compose.onNodeWithText("Reset to default").assertExists()
        compose.onNodeWithText("Backdrop").performClick()
        compose.onNodeWithText("TMDB has no backdrop options for this title.").assertExists()
        compose.onNodeWithText("Poster").performClick()
        compose.onNodeWithText("Reset to default").performClick()
        runBlocking { Mockito.verify(api).resetImage(42, "poster") }
    }

    @Test fun artworkErrorDoesNotPretendOptionsExist() {
        runBlocking { Mockito.`when`(api.imageOptions(42, "poster")).thenThrow(IllegalStateException("Images unavailable")) }
        show { ArtworkScreen(repo, nav, 42, "Film") }
        compose.onNodeWithText("Images unavailable").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
    }

    @Test fun sidebarTabsAndUtilitiesNavigateWithoutStackingDuplicateUtilities() {
        show { FoldSidebar(nav, FoldLayout.InnerLandscape) }
        compose.onNodeWithText("Movies").performClick()
        assertEquals(Tab.Movies, nav.tab)
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        assertEquals(Screen.Settings, nav.stack.last())
        compose.onNodeWithText("Settings").performClick()
        assertEquals(1, nav.stack.size)
    }

    @Test fun compactSidebarKeepsUtilityButtons() {
        show { FoldSidebar(nav, FoldLayout.CoverLandscape) }
        compose.onNodeWithContentDescription("Downloads").performClick()
        assertEquals(Screen.Downloads, nav.stack.last())
    }
}
