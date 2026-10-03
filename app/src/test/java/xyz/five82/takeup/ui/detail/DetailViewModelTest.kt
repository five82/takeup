package xyz.five82.takeup.ui.detail

import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.LoomApi
import xyz.five82.takeup.api.Progress
import xyz.five82.takeup.data.DownloadEntry
import xyz.five82.takeup.data.DownloadResult
import xyz.five82.takeup.data.DownloadState
import xyz.five82.takeup.data.DownloadStore
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.data.NetworkPolicy
import xyz.five82.takeup.data.OfflineCatalog
import xyz.five82.takeup.data.Reach
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class DetailViewModelTest {
    private val repo = Mockito.mock(LoomRepository::class.java)
    private val api = Mockito.mock(LoomApi::class.java)
    private val network = Mockito.mock(NetworkPolicy::class.java)
    private val downloads = Mockito.mock(DownloadStore::class.java)
    private val reach = MutableStateFlow(Reach.Home)

    private fun model(itemId: Long, catalog: OfflineCatalog = OfflineCatalog()): DetailViewModel {
        Mockito.doReturn(api).`when`(repo).api
        Mockito.doReturn(network).`when`(repo).network
        Mockito.doReturn(downloads).`when`(repo).downloads
        Mockito.doReturn(MutableStateFlow(catalog)).`when`(repo).offlineCatalog
        Mockito.doReturn(reach).`when`(network).reach
        return DetailViewModel(repo, itemId)
    }

    private fun idle() = Shadows.shadowOf(Looper.getMainLooper()).idle()

    @Test fun offlineShowUsesDownloadedEpisodesRatherThanServerUnwatchedCounts() {
        val show = Item(id = 1, title = "Show", kind = "show")
        val special = Item(id = 2, title = "Specials", kind = "season", parentId = 1, seasonNumber = 0)
        val season = Item(id = 3, title = "First", kind = "season", parentId = 1, seasonNumber = 1, unwatchedCount = 0)
        val watched = Item(id = 4, title = "Watched", kind = "episode", parentId = 2,
            progress = Progress(played = true))
        val next = Item(id = 5, title = "Next", kind = "episode", parentId = 3)
        val catalog = OfflineCatalog(
            entries = listOf(watched, next).map { DownloadEntry(it, DownloadState.Completed, "http://loom/${it.id}", 1, 1, 0) },
            ancestors = listOf(show, special, season),
        )
        reach.value = Reach.Offline
        val model = model(1, catalog)
        model.refresh()
        idle()
        assertTrue(model.state.offline)
        assertEquals(3L, model.state.selectedSeason)
        assertEquals(next, model.nextToWatch())
        model.selectSeason(2)
        model.refresh()
        idle()
        assertEquals(2L, model.state.selectedSeason)
        Mockito.verifyNoInteractions(api)
    }

    @Test fun offlineMissingTitleAndMovieHaveNoSeasonList() {
        reach.value = Reach.Offline
        val movie = Item(id = 7, title = "Film", kind = "movie")
        val catalog = OfflineCatalog(entries = listOf(DownloadEntry(movie, DownloadState.Completed,
            "http://loom/7", 1, 1, 0)))
        val model = model(7, catalog)
        model.refresh()
        idle()
        assertEquals(movie, model.state.item)
        assertTrue(model.state.seasons.isEmpty())
        val missing = model(8, catalog)
        missing.refresh()
        idle()
        assertNull(missing.state.item)
        assertFalse(missing.state.loading)
    }

    @Test fun onlineShowLoadsAndSortsSeasonsAndEpisodesAndKeepsSelection() {
        reach.value = Reach.Home
        val show = Item(id = 1, title = "Show", kind = "show")
        val first = Item(id = 2, title = "First", kind = "season", seasonNumber = 1)
        val second = Item(id = 3, title = "Second", kind = "season", seasonNumber = 2, unwatchedCount = 2)
        val late = Item(id = 4, title = "Late", kind = "episode", episodeNumber = 2)
        val early = Item(id = 5, title = "Early", kind = "episode", episodeNumber = 1)
        runBlocking {
            Mockito.`when`(api.item(1)).thenReturn(show)
            Mockito.`when`(api.children(1)).thenReturn(listOf(second, Item(id = 9, kind = "movie"), first))
            Mockito.`when`(api.children(2)).thenReturn(emptyList())
            Mockito.`when`(api.children(3)).thenReturn(listOf(late, early))
        }
        val model = model(1)
        model.refresh()
        idle()
        assertEquals(listOf(2L, 3L), model.state.seasons.map { it.id })
        assertEquals(listOf(early, late), model.state.episodesBySeason[3])
        assertEquals(3L, model.state.selectedSeason)
        assertEquals(early, model.nextToWatch())
        model.selectSeason(2)
        model.refresh()
        idle()
        assertEquals(2L, model.state.selectedSeason)
    }

    @Test fun networkFailureFallsBackOfflineAndOtherFailurePreservesExistingItem() {
        reach.value = Reach.Home
        val movie = Item(id = 7, title = "Film", kind = "movie")
        var calls = 0
        runBlocking { Mockito.doAnswer {
            when (calls++) {
                0 -> movie
                1 -> throw IllegalStateException("server error")
                else -> throw IOException("offline")
            }
        }.`when`(api).item(7) }
        val model = model(7)
        model.refresh()
        idle()
        assertEquals(movie, model.state.item)
        model.refresh()
        idle()
        assertEquals(movie, model.state.item)
        assertNull(model.state.error)
        model.refresh()
        idle()
        assertTrue(model.state.offline)
        Mockito.verify(network).markUnreachable()
    }

    @Test fun downloadReportsLowSpaceAndFailuresAndRemovalClearsMessage() {
        reach.value = Reach.Home
        val model = model(7)
        runBlocking { Mockito.`when`(repo.startDownload(7)).thenReturn(DownloadResult.NotEnoughSpace)
            .thenThrow(IllegalStateException("transfer failed")) }
        model.download()
        idle()
        assertEquals("Not enough free space for this file", model.downloadMessage)
        model.download()
        idle()
        assertEquals("transfer failed", model.downloadMessage)
        model.removeDownload()
        assertNull(model.downloadMessage)
        Mockito.verify(downloads).remove(7)
    }

    @Test fun markingWatchedCallsTheAppropriateEndpointAndReloads() {
        reach.value = Reach.Home
        val item = Item(id = 7, title = "Film", kind = "movie")
        runBlocking { Mockito.`when`(api.item(7)).thenReturn(item) }
        val model = model(7)
        model.setWatched(item, true)
        idle()
        model.setWatched(item, false)
        idle()
        runBlocking {
            Mockito.verify(api).markPlayed(7)
            Mockito.verify(api).clearPlayed(7)
        }
        assertEquals(item, model.state.item)
    }
}
