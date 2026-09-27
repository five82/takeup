package xyz.five82.takeup.data

import android.os.Looper
import androidx.media3.datasource.DataSpec
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import xyz.five82.takeup.api.OFFLINE_MESSAGE
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.loomGson
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadStoreRuntimeTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val artwork = Mockito.mock(OfflineArtwork::class.java)
    private val network = Mockito.mock(NetworkPolicy::class.java)
    private val blocked = MutableStateFlow(true)

    @After fun close() { scope.cancel() }

    private fun store(): DownloadStore {
        Mockito.doReturn(blocked).`when`(network).blocked
        val store = DownloadStore(RuntimeEnvironment.getApplication(), scope, artwork, network)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        return store
    }

    @Test fun cacheUpstreamRefusesNetworkReadsWhileBlocked() {
        val store = store()
        val source = store.playbackDataSourceFactory.createDataSource()
        try {
            source.open(DataSpec(android.net.Uri.parse("http://127.0.0.1:1/stream")))
            org.junit.Assert.fail("A cache miss must not reach the network")
        } catch (e: IOException) {
            assertEquals(OFFLINE_MESSAGE, e.message)
        } finally {
            source.close()
        }
        assertNull(store.entry(9))
    }

    @Test fun enqueueAndRemoveAllLeaveNoDownloadedEntry() {
        val store = store()
        val item = Item(id = 42, kind = "movie", title = "Saved Film")
        store.enqueue(42, "http://127.0.0.1:1/stream?tag=v1", loomGson.toJson(item))
        store.removeAll()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(store.downloads.value.none { it.item.id == 42L })
    }

    @Test fun completedSnapshotIsLoadedWithItsArtworkAndCanBeRemoved() {
        val context = RuntimeEnvironment.getApplication()
        val index = DefaultDownloadIndex(StandaloneDatabaseProvider(context))
        val item = Item(id = 777, kind = "movie", title = "Offline Feature")
        val request = DownloadRequest.Builder("777", android.net.Uri.parse("http://loom/film"))
            .setData(loomGson.toJson(item).toByteArray()).build()
        index.putDownload(Download(request, Download.STATE_COMPLETED, 123L, 456L, 4096L, 0, 0))
        try {
            val store = store()
            // The index is read on Dispatchers.IO, then published on the main looper.
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
            while (store.entry(777) == null && System.nanoTime() < deadline) {
                Thread.sleep(10)
                Shadows.shadowOf(Looper.getMainLooper()).idle()
            }
            val entry = store.entry(777)
            assertNotNull(entry)
            assertEquals("Offline Feature", entry!!.item.title)
            assertEquals(DownloadState.Completed, entry.state)
            assertEquals("http://loom/film", entry.uri)
            assertEquals(4096L, entry.totalBytes)
            assertEquals(123L, entry.startTimeMs)
            Mockito.verify(artwork, Mockito.atLeastOnce()).posterPath(777)
            Mockito.verify(artwork, Mockito.atLeastOnce()).backdropPath(777)
            // Replacing a URI must remove the old cache key before Media3 adds the new one.
            store.enqueue(777, "http://loom/film?tag=new", loomGson.toJson(item))
            assertEquals("http://loom/film", store.entry(777)?.uri)
            store.removeAll()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runBlocking { Mockito.verify(artwork).delete(777) }
        } finally {
            index.removeDownload("777")
        }
    }

    @Test fun queuedSnapshotSurvivesAnUnreadableIndexEntry() {
        val context = RuntimeEnvironment.getApplication()
        val index = DefaultDownloadIndex(StandaloneDatabaseProvider(context))
        val good = DownloadRequest.Builder("778", android.net.Uri.parse("http://loom/next"))
            .setData(loomGson.toJson(Item(id = 778, kind = "episode", title = "Next Up")).toByteArray()).build()
        val bad = DownloadRequest.Builder("779", android.net.Uri.parse("http://loom/bad"))
            .setData("not json".toByteArray()).build()
        index.putDownload(Download(good, Download.STATE_STOPPED, 123L, 456L, -1L, 1, 0))
        index.putDownload(Download(bad, Download.STATE_COMPLETED, 123L, 456L, 100L, 0, 0))
        val failed = DownloadRequest.Builder("780", android.net.Uri.parse("http://loom/failed"))
            .setData(loomGson.toJson(Item(id = 780, kind = "movie", title = "Interrupted")).toByteArray()).build()
        index.putDownload(Download(failed, Download.STATE_FAILED, 123L, 456L, 100L, 0,
            Download.FAILURE_REASON_UNKNOWN))
        try {
            val store = store()
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
            while (store.entry(778) == null && System.nanoTime() < deadline) {
                Thread.sleep(10)
                Shadows.shadowOf(Looper.getMainLooper()).idle()
            }
            assertEquals(DownloadState.Queued, store.entry(778)?.state)
            assertNull(store.entry(779))
            assertEquals(DownloadState.Failed, store.entry(780)?.state)
        } finally {
            index.removeDownload("778")
            index.removeDownload("779")
            index.removeDownload("780")
        }
    }

    @Test fun emptyIndexLoadsAndDownloadQueueCanBePausedAndResumed() {
        val store = store()
        assertTrue(store.usableSpaceBytes() > 0)
        store.setTransfersPaused(true)
        assertTrue(store.downloadManager.downloadsPaused)
        store.setTransfersPaused(false)
        assertFalse(store.downloadManager.downloadsPaused)
        store.resumeQueued()
        store.remove(99)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        runBlocking { Mockito.verify(artwork).delete(99) }
    }
}
