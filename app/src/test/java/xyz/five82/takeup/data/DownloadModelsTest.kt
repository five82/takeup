package xyz.five82.takeup.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.LoomException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class DownloadModelsTest {
    @Test
    fun `reads the media version tag from a stream URL`() {
        assertEquals("abc123", tagFromStreamUrl("/api/v1/media/7?tag=abc123"))
        assertEquals(
            "abc123",
            tagFromStreamUrl("http://192.168.1.20:8097/api/v1/media/7?width=480&tag=abc123"),
        )
    }

    @Test
    fun `reports no tag for an untagged stream URL`() {
        assertEquals("", tagFromStreamUrl("/api/v1/media/7"))
        assertEquals("", tagFromStreamUrl("/api/v1/media/7?width=480"))
    }

    @Test
    fun `plays cached bytes when the downloaded version still matches`() {
        val entry = entry(uri = "http://old.host/api/v1/media/7?tag=abc123")

        assertEquals(
            "http://old.host/api/v1/media/7?tag=abc123",
            resolveStreamUrl(entry, "abc123", "http://new.host/api/v1/media/7?tag=abc123"),
        )
    }

    @Test
    fun `streams the current version when the download is superseded`() {
        val entry = entry(uri = "http://loom/api/v1/media/7?tag=old")

        assertEquals(
            "http://loom/api/v1/media/7?tag=new",
            resolveStreamUrl(entry, "new", "http://loom/api/v1/media/7?tag=new"),
        )
    }

    @Test
    fun `streams when nothing is downloaded or the download is incomplete`() {
        val resolved = "http://loom/api/v1/media/7?tag=abc123"

        assertEquals(resolved, resolveStreamUrl(null, "abc123", resolved))
        assertEquals(
            resolved,
            resolveStreamUrl(entry(state = DownloadState.Downloading), "abc123", resolved),
        )
    }

    @Test
    fun `detects a superseded download only when Loom reports a version`() {
        val entry = entry(uri = "/api/v1/media/7?tag=old")

        assertTrue(isStaleDownload(entry, "new"))
        assertFalse(isStaleDownload(entry, "old"))
        // List endpoints omit media, so an unknown tag must not look stale.
        assertFalse(isStaleDownload(entry, ""))
        assertFalse(isStaleDownload(entry.copy(state = DownloadState.Downloading), "new"))
    }

    @Test
    fun `keeps storage headroom when checking free space`() {
        val gigabyte = 1_000_000_000L

        assertTrue(hasRoomFor(sizeBytes = gigabyte, usableBytes = 2 * gigabyte))
        assertFalse(hasRoomFor(sizeBytes = 2 * gigabyte, usableBytes = 2 * gigabyte))
        // Fits on raw bytes but eats into the reserve.
        assertFalse(hasRoomFor(sizeBytes = gigabyte, usableBytes = gigabyte + 1))
        assertFalse(hasRoomFor(sizeBytes = 0, usableBytes = 100 * gigabyte))
    }

    @Test
    fun `treats only connection-level failures as offline`() {
        assertTrue(isOfflineError(UnknownHostException("loom")))
        assertTrue(isOfflineError(ConnectException("refused")))
        assertTrue(isOfflineError(SocketTimeoutException("timeout")))
        assertTrue(isOfflineError(IOException("dropped")))
        assertFalse(isOfflineError(LoomException(404, "item not found")))
        assertFalse(isOfflineError(IllegalStateException("bug")))
    }

    @Test
    fun `reports download progress without dividing by an unset length`() {
        val downloading = DownloadState.Downloading

        assertEquals(
            0f,
            downloadProgressFraction(entry(state = downloading, total = -1, downloaded = 512)),
            0f,
        )
        assertEquals(
            0f,
            downloadProgressFraction(entry(state = downloading, total = 0, downloaded = 0)),
            0f,
        )
        assertEquals(
            0.5f,
            downloadProgressFraction(entry(state = downloading, total = 1000, downloaded = 500)),
            0.001f,
        )
        assertEquals(
            1f,
            downloadProgressFraction(entry(state = DownloadState.Completed, total = -1)),
            0f,
        )
    }

    @Test
    fun `formats download sizes`() {
        assertEquals("0 MB", formatBytes(0))
        assertEquals("500 MB", formatBytes(500_000_000))
        assertEquals("1.5 GB", formatBytes(1_500_000_000))
        assertEquals("42.9 GB", formatBytes(42_949_672_960))
    }

    @Test
    fun `lists active downloads before completed ones`() {
        val downloading = entry(title = "Zodiac", state = DownloadState.Downloading)
        val completedA = entry(title = "Arrival", state = DownloadState.Completed)
        val completedB = entry(title = "Blade Runner", state = DownloadState.Completed)

        assertEquals(
            listOf("Zodiac", "Arrival", "Blade Runner"),
            downloadedRowItems(listOf(completedB, completedA, downloading)).map { it.item.title },
        )
    }

    @Test
    fun `summarizes active downloads and completed bytes`() {
        val entries = listOf(
            entry(state = DownloadState.Queued),
            entry(state = DownloadState.Failed),
            entry(state = DownloadState.Removing),
            entry(title = "Done", total = 1_500_000_000),
            entry(title = "Unknown size", total = -1, downloaded = 500_000_000),
        )

        assertEquals(
            DownloadSummary(activeCount = 3, completedCount = 2, completedBytes = 2_000_000_000),
            downloadSummary(entries),
        )
    }

    @Test
    fun `offers the action that matches the download's state`() {
        assertEquals(DownloadAction.Start, downloadAction(null, "abc"))
        assertEquals(
            DownloadAction.Cancel,
            downloadAction(entry(state = DownloadState.Downloading), "abc123"),
        )
        assertEquals(
            DownloadAction.Cancel,
            downloadAction(entry(state = DownloadState.Queued), "abc123"),
        )
        assertEquals(
            DownloadAction.Retry,
            downloadAction(entry(state = DownloadState.Failed), "abc123"),
        )
        assertEquals(DownloadAction.Remove, downloadAction(entry(), "abc123"))
        // Completed but Loom now reports a different version.
        assertEquals(DownloadAction.Update, downloadAction(entry(), "newtag"))
        // No version from a list endpoint must not look like an update.
        assertEquals(DownloadAction.Remove, downloadAction(entry(), ""))
    }

    @Test
    fun `states the download's condition in words`() {
        assertEquals(
            "Downloaded · 1.5 GB",
            downloadStatusLabel(entry(total = 1_500_000_000)),
        )
        assertEquals(
            "Downloading · 50%",
            downloadStatusLabel(entry(state = DownloadState.Downloading, total = 1000, downloaded = 500)),
        )
        assertEquals("Download failed", downloadStatusLabel(entry(state = DownloadState.Failed)))
        assertEquals("Download queued", downloadStatusLabel(entry(state = DownloadState.Queued)))
    }

    private fun entry(
        title: String = "Arrival",
        state: DownloadState = DownloadState.Completed,
        uri: String = "/api/v1/media/7?tag=abc123",
        downloaded: Long = 0,
        total: Long = 0,
    ) = DownloadEntry(
        item = Item(id = 42, kind = "movie", title = title, year = 2016),
        state = state,
        uri = uri,
        bytesDownloaded = downloaded,
        totalBytes = total,
    )
}
