@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package xyz.five82.takeup.data

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.util.NotificationUtil
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.five82.takeup.R
import xyz.five82.takeup.api.OFFLINE_MESSAGE
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.loomGson
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Owns the single process-wide Media3 download cache. [SimpleCache] refuses a second
 * instance over the same directory, so this must stay a singleton in the application.
 */
class DownloadStore(
    context: Context,
    private val scope: CoroutineScope,
    val artwork: OfflineArtwork,
    network: NetworkPolicy,
) {
    private val appContext = context.applicationContext

    // Internal storage: a Pixel has no removable volume to gain capacity from, and
    // keeping the cache out of user-visible storage stops a file manager from
    // deleting spans behind SimpleCache's index. allowBackup="false" already keeps
    // multi-gigabyte media out of cloud backup.
    private val downloadDirectory = File(appContext.filesDir, "downloads")
    private val databaseProvider = StandaloneDatabaseProvider(appContext)
    private val cache = SimpleCache(downloadDirectory, NoOpCacheEvictor(), databaseProvider)
    private val upstreamFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(false)

    val downloadManager = DownloadManager(
        appContext,
        databaseProvider,
        cache,
        upstreamFactory,
        Executors.newSingleThreadExecutor(),
    ).apply { maxParallelDownloads = 1 }

    /**
     * Reads cached bytes but never writes them. Without the null sink, ordinary
     * streaming would fill a cache whose NoOpCacheEvictor never reclaims anything.
     *
     * The upstream is gated: a downloaded title plays untouched because every read
     * hits the cache, while streaming one that is not downloaded stops at the gate,
     * including when the phone leaves Wi-Fi mid-film. Downloads use the ungated
     * factory - [setTransfersPaused] holds those back instead, so they can resume.
     */
    val playbackDataSourceFactory: DataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory {
            GatedDataSource(upstreamFactory.createDataSource()) { network.blocked.value }
        }
        .setCacheWriteDataSinkFactory(null)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    private val notificationHelper =
        DownloadNotificationHelper(appContext, MediaDownloadService.CHANNEL_ID)

    private val _downloads = MutableStateFlow<List<DownloadEntry>>(emptyList())
    val downloads: StateFlow<List<DownloadEntry>> = _downloads.asStateFlow()

    // A replacement download must not be added until the superseded one is gone, or
    // Media3 merges the requests and the removal frees the new cache key instead.
    private val pendingReplacements = mutableMapOf<String, DownloadRequest>()
    private var progressTicker: Job? = null

    init {
        downloadManager.addListener(
            object : DownloadManager.Listener {
                override fun onInitialized(manager: DownloadManager) = refresh()

                override fun onDownloadChanged(
                    manager: DownloadManager,
                    download: Download,
                    finalException: Exception?,
                ) {
                    notifyTerminalState(download)
                    refresh()
                }

                override fun onDownloadRemoved(manager: DownloadManager, download: Download) {
                    pendingReplacements.remove(download.request.id)?.let { request ->
                        DownloadService.sendAddDownload(
                            appContext,
                            MediaDownloadService::class.java,
                            request,
                            false,
                        )
                    }
                    refresh()
                }
            },
        )
        refresh()
    }

    fun entry(itemId: Long): DownloadEntry? =
        _downloads.value.firstOrNull { it.item.id == itemId }

    fun usableSpaceBytes(): Long = downloadDirectory.parentFile?.usableSpace ?: 0L

    fun enqueue(itemId: Long, streamUrl: String, itemJson: String) {
        val id = itemId.toString()
        // No custom cache key: playback builds its MediaItem from the URI alone, so
        // any key set here would silently miss the cache and re-stream the file.
        val request = DownloadRequest.Builder(id, streamUrl.toUri())
            .setData(itemJson.toByteArray(Charsets.UTF_8))
            .build()
        val existing = entry(itemId)
        if (existing != null && existing.uri != streamUrl) {
            pendingReplacements[id] = request
            DownloadService.sendRemoveDownload(appContext, MediaDownloadService::class.java, id, false)
        } else {
            DownloadService.sendAddDownload(appContext, MediaDownloadService::class.java, request, false)
        }
    }

    fun remove(itemId: Long) {
        pendingReplacements.remove(itemId.toString())
        DownloadService.sendRemoveDownload(
            appContext,
            MediaDownloadService::class.java,
            itemId.toString(),
            false,
        )
        scope.launch { artwork.delete(itemId) }
    }

    fun removeAll() {
        pendingReplacements.clear()
        val ids = _downloads.value.map { it.item.id }
        DownloadService.sendRemoveAllDownloads(appContext, MediaDownloadService::class.java, false)
        scope.launch { ids.forEach { artwork.delete(it) } }
    }

    /**
     * The cellular gate holds transfers rather than failing them: a paused download
     * keeps the bytes it has and carries on once Wi-Fi is back, where a failed one
     * would wait for the user to press Retry.
     */
    fun setTransfersPaused(paused: Boolean) {
        if (paused) downloadManager.pauseDownloads() else downloadManager.resumeDownloads()
    }

    /** Resumes work interrupted by a process death. No scheduler runs while dead. */
    fun resumeQueued() {
        runCatching {
            DownloadService.sendResumeDownloads(appContext, MediaDownloadService::class.java, false)
        }
    }

    /**
     * The foreground notification only lives as long as the transfer, which for a
     * short film on a LAN can be seconds. Post a lasting one on the terminal state
     * so finishing and failing are both visible after the fact.
     */
    private fun notifyTerminalState(download: Download) {
        val title = runCatching {
            snapshotItem(download).title
        }.getOrNull() ?: return
        val notification = when (download.state) {
            Download.STATE_COMPLETED -> notificationHelper.buildDownloadCompletedNotification(
                appContext,
                R.drawable.ic_download,
                null,
                title,
            )
            Download.STATE_FAILED -> notificationHelper.buildDownloadFailedNotification(
                appContext,
                R.drawable.ic_download,
                null,
                title,
            )
            else -> return
        }
        // A stable id per item so a replaced download updates its own notification
        // rather than stacking a new one each time.
        NotificationUtil.setNotification(
            appContext,
            TERMINAL_NOTIFICATION_ID_BASE + download.request.id.hashCode().and(0xFFFF),
            notification,
        )
    }

    private fun refresh() {
        scope.launch {
            val entries = withContext(Dispatchers.IO) { readIndex() }
            _downloads.value = entries
            if (entries.any { it.state == DownloadState.Downloading }) startProgressTicker()
        }
    }

    private fun readIndex(): List<DownloadEntry> = runCatching {
        downloadManager.downloadIndex.getDownloads().use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    toEntry(cursor.download)?.let { add(it) }
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun toEntry(download: Download): DownloadEntry? {
        val item = runCatching { snapshotItem(download) }.getOrNull() ?: return null
        return DownloadEntry(
            item = item,
            state = when (download.state) {
                Download.STATE_COMPLETED -> DownloadState.Completed
                Download.STATE_FAILED -> DownloadState.Failed
                Download.STATE_REMOVING -> DownloadState.Removing
                Download.STATE_DOWNLOADING -> DownloadState.Downloading
                else -> DownloadState.Queued
            },
            uri = download.request.uri.toString(),
            bytesDownloaded = download.bytesDownloaded,
            totalBytes = download.contentLength,
            posterPath = artwork.posterPath(item.id),
            backdropPath = artwork.backdropPath(item.id),
            thumbPath = artwork.thumbPath(item.id),
            logoPath = artwork.logoPath(item.id),
        )
    }

    private fun snapshotItem(download: Download): Item =
        loomGson.fromJson(String(download.request.data, Charsets.UTF_8), Item::class.java)

    /**
     * Media3's listener only fires on state transitions, so live byte counts have to
     * be polled the way its own foreground notification does.
     */
    private fun startProgressTicker() {
        if (progressTicker?.isActive == true) return
        progressTicker = scope.launch {
            while (true) {
                val active = downloadManager.currentDownloads
                if (active.isEmpty()) break
                delay(PROGRESS_INTERVAL_MS)
                val byId = active.associateBy { it.request.id }
                _downloads.value = _downloads.value.map { entry ->
                    val live = byId[entry.item.id.toString()] ?: return@map entry
                    entry.copy(
                        bytesDownloaded = live.bytesDownloaded,
                        totalBytes = live.contentLength,
                    )
                }
            }
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 1_000L

        // Above the download service's own foreground notification id.
        const val TERMINAL_NOTIFICATION_ID_BASE = 100
    }
}

/** Refuses to reach the network while [blocked], and delegates everything else. */
private class GatedDataSource(
    private val delegate: DataSource,
    private val blocked: () -> Boolean,
) : DataSource by delegate {
    override fun open(dataSpec: DataSpec): Long {
        if (blocked()) throw IOException(OFFLINE_MESSAGE)
        return delegate.open(dataSpec)
    }
}
