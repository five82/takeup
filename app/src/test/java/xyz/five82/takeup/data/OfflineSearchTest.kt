package xyz.five82.takeup.data

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.five82.takeup.api.Item

class OfflineSearchTest {

    @Test
    fun `matches titles case insensitively and sorts them by title`() {
        val entries = listOf(
            entry(1, "Wings of Desire"),
            entry(2, "The Wind Rises"),
            entry(3, "Wind River"),
        )
        assertEquals(listOf(2L, 3L), matchDownloads(entries, "wind").map { it.item.id })
    }

    @Test
    fun `an episode is findable by the show it belongs to`() {
        val entries = listOf(entry(1, "Pine Barrens", seriesTitle = "The Sopranos"))
        assertEquals(1, matchDownloads(entries, "sopranos").size)
    }

    @Test
    fun `only finished downloads are searchable`() {
        // A half-transferred file cannot play, so offering it would be a lie.
        val entries = listOf(entry(1, "Heat", state = DownloadState.Downloading))
        assertEquals(emptyList<DownloadEntry>(), matchDownloads(entries, "heat"))
    }

    @Test
    fun `an empty query matches nothing rather than everything`() {
        assertEquals(emptyList<DownloadEntry>(), matchDownloads(listOf(entry(1, "Heat")), "   "))
    }

    private fun entry(
        id: Long,
        title: String,
        seriesTitle: String? = null,
        state: DownloadState = DownloadState.Completed,
    ) = DownloadEntry(
        item = Item(id = id, title = title, seriesTitle = seriesTitle),
        state = state,
        uri = "http://loom/stream/$id?tag=a",
        bytesDownloaded = 1,
        totalBytes = 1,
    )
}
