package xyz.five82.takeup.ui

import android.os.Looper
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import xyz.five82.takeup.ui.detail.DetailScreen
import xyz.five82.takeup.ui.detail.DetailViewModel
import xyz.five82.takeup.ui.theme.TakeupTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DetailScreenUnitTest {
    @get:Rule val compose = createComposeRule()

    private val repo = Mockito.mock(LoomRepository::class.java)
    private val api = Mockito.mock(LoomApi::class.java)

    private fun showDetail(itemId: Long) {
        val network = Mockito.mock(NetworkPolicy::class.java)
        val downloads = Mockito.mock(DownloadStore::class.java)
        Mockito.doReturn(api).`when`(repo).api
        Mockito.doReturn(network).`when`(repo).network
        Mockito.doReturn(downloads).`when`(repo).downloads
        Mockito.doReturn(MutableStateFlow(Reach.Home)).`when`(network).reach
        Mockito.doReturn(MutableStateFlow("")).`when`(network).reason
        Mockito.doReturn(MutableStateFlow(emptyList<DownloadEntry>())).`when`(downloads).downloads
        compose.setContent { TakeupTheme { DetailScreen(repo, NavState(), itemId, topmost = true) } }
        compose.mainClock.advanceTimeBy(500)
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
