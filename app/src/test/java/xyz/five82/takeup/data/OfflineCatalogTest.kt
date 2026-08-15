package xyz.five82.takeup.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.Progress

/**
 * The offline library, which is the download list read as a hierarchy. These
 * pin the grouping rules a screen with no Loom depends on.
 */
class OfflineCatalogTest {

    @Test
    fun `a downloaded episode is filed under its show rather than listed loose`() {
        val catalog = catalog(
            entries = listOf(episode(101, "The Constant", seasonId = 20, episodeNumber = 5)),
            ancestors = listOf(SEASON_FOUR, SHOW),
        )

        assertEquals(listOf(SHOW.id), catalog.library("tv").map { it.id })
        assertEquals(listOf(SEASON_FOUR.id), catalog.children(SHOW.id).map { it.id })
        assertEquals(listOf(101L), catalog.children(SEASON_FOUR.id).map { it.id })
        assertEquals(SHOW.id, catalog.showFor(101)?.id)
    }

    @Test
    fun `seasons and episodes are ordered by number, not by download order`() {
        val catalog = catalog(
            entries = listOf(
                episode(103, "Ji Yeon", seasonId = 20, episodeNumber = 7, startTimeMs = 30),
                episode(101, "The Constant", seasonId = 20, episodeNumber = 5, startTimeMs = 10),
                episode(201, "Because You Left", seasonId = 21, episodeNumber = 1, startTimeMs = 20),
            ),
            ancestors = listOf(SEASON_FIVE, SEASON_FOUR, SHOW),
        )

        assertEquals(listOf(20L, 21L), catalog.children(SHOW.id).map { it.id })
        assertEquals(listOf(101L, 103L), catalog.children(SEASON_FOUR.id).map { it.id })
        assertEquals(listOf(101L, 103L, 201L), catalog.episodes(SHOW.id).map { it.id })
    }

    @Test
    fun `only completed downloads are browsable`() {
        val catalog = catalog(
            entries = listOf(
                movie(1, "Heat"),
                movie(2, "Thief", state = DownloadState.Downloading),
            ),
        )

        assertEquals(listOf(1L), catalog.library("movies").map { it.id })
        // The half-transferred one still answers, so its detail screen can say so.
        assertEquals("Thief", catalog.item(2)?.title)
    }

    @Test
    fun `an episode whose show was never captured is still offered`() {
        // Downloaded before shows were captured: the file plays either way, so it
        // stands on its own rather than disappearing from the tab.
        val catalog = catalog(entries = listOf(episode(101, "The Constant", seasonId = 20)))

        assertEquals(listOf(101L), catalog.library("tv").map { it.id })
        assertNull(catalog.showFor(101))
    }

    @Test
    fun `library kinds split movies from short films`() {
        val catalog = catalog(
            entries = listOf(movie(1, "Heat", libraryId = 7), movie(2, "La Jetee", libraryId = 8)),
            libraryKinds = mapOf(7L to "movies", 8L to "shorts"),
        )

        assertEquals(listOf(1L), catalog.library("movies").map { it.id })
        assertEquals(listOf(2L), catalog.library("shorts").map { it.id })
    }

    @Test
    fun `a download from an unknown library lands under movies rather than nowhere`() {
        val catalog = catalog(entries = listOf(movie(1, "Heat", libraryId = 7)))

        assertEquals(listOf(1L), catalog.library("movies").map { it.id })
        assertEquals(emptyList<Long>(), catalog.library("shorts").map { it.id })
    }

    @Test
    fun `the newest download leads, with a show standing in for its episodes`() {
        val catalog = catalog(
            entries = listOf(
                movie(1, "Heat", startTimeMs = 10),
                episode(101, "The Constant", seasonId = 20, startTimeMs = 30),
                episode(103, "Ji Yeon", seasonId = 20, startTimeMs = 20),
            ),
            ancestors = listOf(SEASON_FOUR, SHOW),
        )

        assertEquals(listOf(SHOW.id, 1L), catalog.recent().map { it.id })
    }

    @Test
    fun `continue watching holds what was started and not finished`() {
        val catalog = catalog(
            entries = listOf(
                movie(1, "Heat", progress = Progress(positionMs = 600_000, durationMs = 6_000_000)),
                movie(2, "Thief", progress = Progress(positionMs = 0, durationMs = 6_000_000)),
                movie(
                    3,
                    "Collateral",
                    progress = Progress(positionMs = 5_900_000, durationMs = 6_000_000, played = true),
                ),
            ),
        )

        assertEquals(listOf(1L), catalog.continueWatching().map { it.id })
    }

    @Test
    fun `a position queued offline stands in for the snapshot it is ahead of`() {
        val catalog = catalog(
            entries = listOf(
                movie(1, "Heat", progress = Progress(positionMs = 60_000, durationMs = 6_000_000)),
            ),
            pending = mapOf(1L to PendingProgress(positionMs = 3_000_000, durationMs = 6_000_000)),
        )

        assertEquals(3_000_000L, catalog.item(1)?.progress?.positionMs)
        assertEquals(3_000_000L, catalog.item(1)?.progress?.resumePositionMs)
    }

    @Test
    fun `a title finished offline leaves continue watching without waiting for Loom`() {
        val catalog = catalog(
            entries = listOf(
                movie(1, "Heat", progress = Progress(positionMs = 60_000, durationMs = 6_000_000)),
            ),
            pending = mapOf(1L to PendingProgress(positionMs = 5_800_000, durationMs = 6_000_000)),
        )

        assertTrue(catalog.item(1)?.progress?.played == true)
        assertEquals(emptyList<Item>(), catalog.continueWatching())
    }

    @Test
    fun `search matches a title, the show above it, and nothing on an empty query`() {
        val catalog = catalog(
            entries = listOf(
                movie(1, "Heat"),
                episode(101, "The Constant", seasonId = 20),
            ),
            ancestors = listOf(SEASON_FOUR, SHOW),
        )

        assertEquals(listOf(1L), catalog.search("hea").map { it.id })
        // The show itself and the episode beneath it both answer to its name.
        assertEquals(listOf(SHOW.id, 101L), catalog.search("lost").map { it.id })
        assertEquals(emptyList<Item>(), catalog.search("   "))
    }

    @Test
    fun `the player chains through a show's downloaded episodes`() {
        val catalog = catalog(
            entries = listOf(
                episode(101, "The Constant", seasonId = 20, episodeNumber = 5),
                episode(201, "Because You Left", seasonId = 21, episodeNumber = 1),
            ),
            ancestors = listOf(SEASON_FOUR, SEASON_FIVE, SHOW),
        )

        assertEquals(listOf(101L, 201L), catalog.siblingEpisodes(101).map { it.id })
    }

    private fun catalog(
        entries: List<DownloadEntry> = emptyList(),
        ancestors: List<Item> = emptyList(),
        libraryKinds: Map<Long, String> = emptyMap(),
        pending: Map<Long, PendingProgress> = emptyMap(),
    ) = OfflineCatalog(entries, ancestors, libraryKinds, pending)

    private fun movie(
        id: Long,
        title: String,
        libraryId: Long = 7,
        state: DownloadState = DownloadState.Completed,
        progress: Progress? = null,
        startTimeMs: Long = 0,
    ) = entry(
        Item(id = id, kind = "movie", title = title, libraryId = libraryId, progress = progress),
        state,
        startTimeMs,
    )

    private fun episode(
        id: Long,
        title: String,
        seasonId: Long,
        episodeNumber: Int = 1,
        state: DownloadState = DownloadState.Completed,
        startTimeMs: Long = 0,
    ) = entry(
        Item(
            id = id,
            kind = "episode",
            title = title,
            libraryId = 9,
            parentId = seasonId,
            seasonNumber = if (seasonId == 20L) 4 else 5,
            episodeNumber = episodeNumber,
        ),
        state,
        startTimeMs,
    )

    private fun entry(item: Item, state: DownloadState, startTimeMs: Long) = DownloadEntry(
        item = item,
        state = state,
        uri = "http://loom/stream/${item.id}?tag=a",
        bytesDownloaded = 1,
        totalBytes = 1,
        startTimeMs = startTimeMs,
    )

    private companion object {
        val SHOW = Item(id = 9, kind = "show", title = "Lost", libraryId = 9, episodeCount = 121)
        val SEASON_FOUR =
            Item(id = 20, kind = "season", title = "Season 4", libraryId = 9, parentId = 9, seasonNumber = 4)
        val SEASON_FIVE =
            Item(id = 21, kind = "season", title = "Season 5", libraryId = 9, parentId = 9, seasonNumber = 5)
    }
}
