package xyz.five82.takeup.ui.player

import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.media3.datasource.DefaultDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import xyz.five82.takeup.TakeupApplication
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.LoomApi
import xyz.five82.takeup.api.MediaFile
import xyz.five82.takeup.api.PlaybackInfo
import xyz.five82.takeup.api.Progress
import xyz.five82.takeup.data.DownloadEntry
import xyz.five82.takeup.data.DownloadState
import xyz.five82.takeup.data.DownloadStore
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.data.OfflineCatalog
import xyz.five82.takeup.data.OfflineProgressStore
import xyz.five82.takeup.data.PendingProgress
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TakeupApplication::class)
class PlayerViewModelTest {
    private val repo = Mockito.mock(LoomRepository::class.java)
    private val api = Mockito.mock(LoomApi::class.java)
    private val downloads = Mockito.mock(DownloadStore::class.java)
    private val progress = Mockito.mock(OfflineProgressStore::class.java)
    private val app: TakeupApplication get() = RuntimeEnvironment.getApplication() as TakeupApplication
    private fun idle() = Shadows.shadowOf(Looper.getMainLooper()).idle()

    private fun model(id: Long, entry: DownloadEntry? = null, catalog: OfflineCatalog = OfflineCatalog()): PlayerViewModel {
        Mockito.doReturn(api).`when`(repo).api
        Mockito.doReturn(downloads).`when`(repo).downloads
        Mockito.doReturn(progress).`when`(repo).offlineProgress
        Mockito.doReturn(MutableStateFlow(false)).`when`(repo).dialogueBoost
        Mockito.doReturn(MutableStateFlow(catalog)).`when`(repo).offlineCatalog
        Mockito.doReturn(DefaultDataSource.Factory(app)).`when`(downloads).playbackDataSourceFactory
        Mockito.`when`(downloads.entry(id)).thenReturn(entry)
        return PlayerViewModel(app, repo, id).also { idle() }
    }

    @Test fun onlinePlaybackPrefersFreshResumeAndMatchingCachedUri() {
        val item = Item(id = 41, kind = "movie", media = MediaFile(tag = "v2", durationMs = 100_000),
            progress = Progress(resumePositionMs = 38_000))
        val cached = DownloadEntry(item, DownloadState.Completed, "http://old-host/movie?tag=v2", 1, 1)
        runBlocking {
            Mockito.`when`(api.item(41)).thenReturn(item)
            Mockito.`when`(api.playback(41)).thenReturn(PlaybackInfo(media = item.media!!, streamUrl = "http://new-host/movie?tag=v2"))
        }
        Mockito.`when`(api.absoluteUrl("http://new-host/movie?tag=v2"))
            .thenReturn("http://new-host/movie?tag=v2")
        val model = model(41, cached)
        assertEquals(item, model.item)
        assertEquals("http://old-host/movie?tag=v2", model.player.currentMediaItem?.localConfiguration?.uri.toString())
        assertEquals(38_000L, model.player.currentPosition)
        assertNull(model.error)
    }

    @Test fun offlinePlaybackUsesPendingProgressAndDownloadedEpisodeOrder() {
        val first = Item(id = 51, kind = "episode", parentId = 10, seasonNumber = 1, episodeNumber = 1,
            media = MediaFile(tag = "v1", durationMs = 100_000), progress = Progress(resumePositionMs = 5_000))
        val second = first.copy(id = 52, episodeNumber = 2)
        val entries = listOf(first, second).map {
            DownloadEntry(it, DownloadState.Completed, "http://loom/${it.id}?tag=v1", 1, 1)
        }
        runBlocking { Mockito.doAnswer { throw IOException("Loom offline") }.`when`(api).item(51) }
        Mockito.`when`(progress.pending(51)).thenReturn(PendingProgress(21_000, 100_000))
        val model = model(51, entries.first(), OfflineCatalog(entries = entries))
        assertEquals(first, model.item)
        assertEquals("http://loom/51?tag=v1", model.player.currentMediaItem?.localConfiguration?.uri.toString())
        assertEquals(21_000L, model.player.currentPosition)
        assertEquals(second, model.nextEpisode)
        assertNull(model.error)
    }

    @Test fun finalPositionIsQueuedWhenLoomGoesOffline() {
        val item = Item(id = 63, kind = "movie", media = MediaFile(tag = "v1", durationMs = 100_000))
        runBlocking {
            Mockito.`when`(api.item(63)).thenReturn(item)
            Mockito.`when`(api.playback(63)).thenReturn(PlaybackInfo(media = item.media!!, streamUrl = "http://loom/63"))
            Mockito.doAnswer { throw IOException("Loom offline") }.`when`(api).saveProgress(63, 20_000, 100_000)
        }
        Mockito.`when`(api.absoluteUrl("http://loom/63")).thenReturn("http://loom/63")
        val model = model(63)
        model.player.seekTo(20_000)
        val owner = ViewModelStore()
        owner.put("player", model)
        owner.clear()
        runBlocking {
            Mockito.verify(progress, Mockito.timeout(2_000)).enqueue(63, 20_000, 100_000)
        }
    }

    @Test fun serverFailureDoesNotMistakeAnIncompleteDownloadForOfflinePlayback() {
        val item = Item(id = 61, kind = "movie", media = MediaFile(tag = "v1"))
        runBlocking { Mockito.doAnswer { throw IOException("Loom offline") }.`when`(api).item(61) }
        val model = model(61, DownloadEntry(item, DownloadState.Downloading, "http://loom/61?tag=v1", 1, 10))
        assertEquals("Loom offline", model.error)
        assertNull(model.item)
        assertNull(model.player.currentMediaItem)
    }

    @Test fun serverErrorsAreShownEvenWithACompletedDownload() {
        val item = Item(id = 62, kind = "movie", media = MediaFile(tag = "v1"))
        runBlocking { Mockito.`when`(api.item(62)).thenThrow(IllegalStateException("Bad response")) }
        val model = model(62, DownloadEntry(item, DownloadState.Completed, "http://loom/62?tag=v1", 1, 1))
        assertEquals("Bad response", model.error)
        assertNull(model.player.currentMediaItem)
    }
}
