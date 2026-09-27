package xyz.five82.takeup.data

import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.Library
import xyz.five82.takeup.api.LoomApi
import xyz.five82.takeup.api.LoomException
import xyz.five82.takeup.api.MediaFile
import xyz.five82.takeup.api.PlaybackInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LoomRepositoryTest {
    private val settings = Mockito.mock(Settings::class.java)
    private val api = Mockito.mock(LoomApi::class.java)
    private val downloads = Mockito.mock(DownloadStore::class.java)
    private val progress = Mockito.mock(OfflineProgressStore::class.java)
    private val network = Mockito.mock(NetworkPolicy::class.java)
    private val ancestors = Mockito.mock(OfflineItemStore::class.java)
    private val artwork = Mockito.mock(OfflineArtwork::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val server = MutableStateFlow<String?>(null)
    private val entries = MutableStateFlow<List<DownloadEntry>>(emptyList())
    private val captured = MutableStateFlow<List<Item>>(emptyList())
    private val blocked = MutableStateFlow(false)

    @Before fun setup() {
        Mockito.doReturn(server).`when`(settings).serverAddress
        Mockito.doReturn(MutableStateFlow(false)).`when`(settings).dialogueBoost
        Mockito.doReturn(MutableStateFlow(emptyMap<Long, String>())).`when`(settings).libraryKinds
        Mockito.doReturn(entries).`when`(downloads).downloads
        Mockito.doReturn(MutableStateFlow(true)).`when`(downloads).loaded
        Mockito.doReturn(artwork).`when`(downloads).artwork
        Mockito.doReturn(captured).`when`(ancestors).items
        Mockito.doReturn(MutableStateFlow(emptyMap<Long, PendingProgress>())).`when`(progress).pending
        Mockito.doReturn(blocked).`when`(network).blocked
    }

    @After fun tearDown() { scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }

    private fun repository(): LoomRepository = LoomRepository(settings, api, scope, downloads, progress, network, ancestors).also {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun serverAddressIsNormalizedAndTransfersFollowTheNetworkGate() {
        val repo = repository()
        assertTrue(repo.server.value.loaded)
        server.value = "192.168.1.20:8097"
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals("192.168.1.20:8097", repo.server.value.address)
        Mockito.verify(api).baseUrl = LoomApi.normalizeAddress(server.value!!)
        blocked.value = true
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        Mockito.verify(downloads).setTransfersPaused(true)
        repo.resumeDownloads()
        Mockito.verify(downloads, Mockito.never()).resumeQueued()
        blocked.value = false
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        repo.resumeDownloads()
        Mockito.verify(downloads).resumeQueued()
    }

    @Test fun libraryKindCachesTheLookupAndPersistsTheKinds() = runBlocking {
        Mockito.`when`(api.libraries()).thenReturn(listOf(Library(id = 3, kind = "shorts")))
        val repo = repository()
        assertEquals("shorts", repo.libraryKind(3))
        assertEquals("shorts", repo.libraryKind(3))
        Mockito.verify(api, Mockito.times(1)).libraries()
        Mockito.verify(settings).setLibraryKinds(mapOf(3L to "shorts"))
    }

    @Test fun downloadChecksSpaceBeforeQueueingAndUsesTheTaggedPlaybackUrl() = runBlocking {
        val item = Item(id = 7, libraryId = 3, kind = "movie", title = "Film")
        val json = xyz.five82.takeup.api.loomGson.toJson(item)
        Mockito.`when`(api.itemJson(7)).thenReturn(json)
        Mockito.`when`(api.playback(7)).thenReturn(PlaybackInfo(media = MediaFile(size = 200), streamUrl = "/stream/7?tag=new"))
        Mockito.`when`(api.absoluteUrl("/stream/7?tag=new")).thenReturn("http://loom/stream/7?tag=new")
        Mockito.`when`(api.libraries()).thenReturn(listOf(Library(id = 3, kind = "movies")))
        val repo = repository()
        Mockito.`when`(downloads.usableSpaceBytes()).thenReturn(0)
        assertEquals(DownloadResult.NotEnoughSpace, repo.startDownload(7))
        Mockito.verify(downloads, Mockito.never()).enqueue(Mockito.anyLong(), Mockito.anyString(), Mockito.anyString())
        Mockito.`when`(downloads.usableSpaceBytes()).thenReturn(1_000_000_000_000)
        assertEquals(DownloadResult.Started, repo.startDownload(7))
        Mockito.verify(downloads).enqueue(7, "http://loom/stream/7?tag=new", json)
        Mockito.verify(artwork).save(item)
    }

    @Test fun downloadingAnEpisodeCapturesBothParentsEvenWhenArtworkFails() = runBlocking {
        val episode = Item(id = 7, parentId = 2, libraryId = 3, kind = "episode")
        val season = Item(id = 2, parentId = 1, kind = "season")
        val show = Item(id = 1, kind = "show")
        Mockito.`when`(api.itemJson(7)).thenReturn(xyz.five82.takeup.api.loomGson.toJson(episode))
        Mockito.`when`(api.itemJson(2)).thenReturn(xyz.five82.takeup.api.loomGson.toJson(season))
        Mockito.`when`(api.itemJson(1)).thenReturn(xyz.five82.takeup.api.loomGson.toJson(show))
        Mockito.`when`(api.playback(7)).thenReturn(PlaybackInfo(media = MediaFile(size = 20)))
        Mockito.`when`(downloads.usableSpaceBytes()).thenReturn(1_000_000_000_000)
        Mockito.`when`(artwork.save(season)).thenThrow(IllegalStateException("Artwork offline"))
        assertEquals(DownloadResult.Started, repository().startDownload(7))
        Mockito.verify(ancestors).save(season, xyz.five82.takeup.api.loomGson.toJson(season))
        Mockito.verify(ancestors).save(show, xyz.five82.takeup.api.loomGson.toJson(show))
    }

    @Test fun pendingProgressClearsSuccessAndServerErrorsButRetainsNetworkFailures() = runBlocking {
        val pending = mapOf(1L to PendingProgress(10, 100), 2L to PendingProgress(20, 100), 3L to PendingProgress(30, 100))
        Mockito.`when`(progress.all()).thenReturn(pending)
        Mockito.doAnswer { throw LoomException(404, "Gone") }.`when`(api).saveProgress(2, 20, 100)
        Mockito.doAnswer { throw java.io.IOException("Unreachable") }.`when`(api).saveProgress(3, 30, 100)
        repository().flushPendingProgress()
        Mockito.verify(progress).clear(1)
        Mockito.verify(progress).clear(2)
        Mockito.verify(progress, Mockito.never()).clear(3)
    }

    @Test fun capturedAncestorsArePrunedWhenTheLastEpisodeIsRemoved() = runBlocking {
        val season = Item(id = 2, parentId = 1, kind = "season")
        val show = Item(id = 1, kind = "show")
        captured.value = listOf(show, season)
        val repo = repository()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        Mockito.verify(ancestors).delete(setOf(1L, 2L))
        Mockito.verify(artwork).delete(1L)
        Mockito.verify(artwork).delete(2L)
    }
}
