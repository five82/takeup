package xyz.five82.takeup.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.five82.takeup.data.DownloadEntry
import xyz.five82.takeup.data.DownloadState
import xyz.five82.takeup.data.LoomItem

class DownloadsUiTest {
    @Test
    fun `offers a download when nothing is stored`() {
        assertEquals(DownloadAction.Start, downloadAction(null, "abc123"))
    }

    @Test
    fun `offers to cancel while a download is in flight`() {
        assertEquals(
            DownloadAction.Cancel,
            downloadAction(entry(state = DownloadState.Downloading), "abc123"),
        )
        assertEquals(
            DownloadAction.Cancel,
            downloadAction(entry(state = DownloadState.Queued), "abc123"),
        )
    }

    @Test
    fun `offers to remove a completed download`() {
        assertEquals(
            DownloadAction.Remove,
            downloadAction(entry(uri = "/api/v1/media/7?tag=abc123"), "abc123"),
        )
    }

    @Test
    fun `offers an update once Loom reports a newer version`() {
        assertEquals(
            DownloadAction.Update,
            downloadAction(entry(uri = "/api/v1/media/7?tag=old"), "new"),
        )
    }

    @Test
    fun `offers a retry after a failure`() {
        assertEquals(
            DownloadAction.Retry,
            downloadAction(entry(state = DownloadState.Failed), "abc123"),
        )
    }

    @Test
    fun `keeps a completed download when the item tag is unknown`() {
        // List endpoints omit media, so a blank tag must not read as superseded.
        assertEquals(DownloadAction.Remove, downloadAction(entry(), ""))
    }

    @Test
    fun `rounds the outer corners of a single segment`() {
        assertEquals(28.dp to 28.dp, segmentCorners(0, 1, 28.dp, 8.dp))
    }

    @Test
    fun `splits a two segment pill`() {
        assertEquals(28.dp to 8.dp, segmentCorners(0, 2, 28.dp, 8.dp))
        assertEquals(8.dp to 28.dp, segmentCorners(1, 2, 28.dp, 8.dp))
    }

    @Test
    fun `keeps the middle of a three segment pill square`() {
        assertEquals(28.dp to 8.dp, segmentCorners(0, 3, 28.dp, 8.dp))
        assertEquals(8.dp to 8.dp, segmentCorners(1, 3, 28.dp, 8.dp))
        assertEquals(8.dp to 28.dp, segmentCorners(2, 3, 28.dp, 8.dp))
    }

    private fun entry(
        state: DownloadState = DownloadState.Completed,
        uri: String = "/api/v1/media/7?tag=abc123",
    ) = DownloadEntry(
        item = LoomItem(id = 42, kind = "movie", title = "Arrival", year = 2016, overview = ""),
        state = state,
        uri = uri,
        bytesDownloaded = 0,
        totalBytes = 0,
    )
}
