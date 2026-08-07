package xyz.five82.takeup.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
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
    fun `queues progress only for failures a retry could fix`() {
        assertTrue(isOfflineError(UnknownHostException("loom")))
        assertTrue(isOfflineError(ConnectException("refused")))
        assertTrue(isOfflineError(SocketTimeoutException("timeout")))
        assertTrue(isOfflineError(IOException("dropped")))
        assertFalse(isOfflineError(LoomHttpException(404, "item not found")))
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

    private fun entry(
        title: String = "Arrival",
        state: DownloadState = DownloadState.Completed,
        uri: String = "/api/v1/media/7?tag=abc123",
        downloaded: Long = 0,
        total: Long = 0,
    ) = DownloadEntry(
        item = LoomItem(id = 42, kind = "movie", title = title, year = 2016, overview = ""),
        state = state,
        uri = uri,
        bytesDownloaded = downloaded,
        totalBytes = total,
    )
}
