package xyz.five82.takeup.data

import android.os.Looper
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
