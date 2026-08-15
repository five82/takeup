package xyz.five82.takeup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.five82.takeup.api.Collection
import xyz.five82.takeup.api.Genre
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.Progress
import xyz.five82.takeup.ui.home.dailyPick
import xyz.five82.takeup.ui.home.dailyPickLabel
import xyz.five82.takeup.ui.home.dailyPickSlot
import xyz.five82.takeup.ui.home.discoveryRows

class DiscoveryTest {

    private val sciFi = Genre(id = 1, name = "Sci-Fi")
    private val drama = Genre(id = 2, name = "Drama")

    private fun movie(
        id: Long,
        genre: Genre = sciFi,
        rating: Double = 8.0,
        durationMs: Long = 2 * 60 * 60 * 1000L,
        started: Boolean = false,
        backdropImageId: Long = 0,
    ) = Item(
        id = id,
        kind = "movie",
        title = "Movie $id",
        genres = listOf(genre),
        voteAverage = rating,
        durationMs = durationMs,
        progress = if (started) Progress(positionMs = 1) else null,
        backdropImageId = backdropImageId,
    )

    private fun show(id: Long, unwatched: Int = 8) = Item(
        id = id,
        kind = "show",
        title = "Show $id",
        genres = listOf(drama),
        voteAverage = 8.0,
        episodeCount = 8,
        unwatchedCount = unwatched,
    )

    private val movies = (1L..20L).map {
        movie(
            it,
            genre = if (it % 2 == 0L) sciFi else drama,
            durationMs = if (it % 5 == 0L) 80 * 60 * 1000L else 2 * 60 * 60 * 1000L,
        )
    }
    private val shows = (21L..30L).map { show(it) }
    private val collections = listOf(
        Collection("first", "First Collection", listOf(movie(1), movie(3))),
        Collection("second", "Second Collection", listOf(movie(2), movie(4))),
    )
    private val recentlyPlayed = listOf(movie(5, started = true), movie(7, started = true), show(21), show(22))

    private fun rowsFor(day: Long) = discoveryRows(movies, shows, collections, recentlyPlayed, day)

    @Test
    fun dailyPickIsStableAndPrefersUnstartedHighlyRatedBackdropArt() {
        val preferred = movie(99, backdropImageId = 99)
        val lowRatedArt = movie(100, rating = 5.0, backdropImageId = 100)
        val startedArt = movie(101, started = true, backdropImageId = 101)

        val first = dailyPick(listOf(preferred, lowRatedArt, startedArt), 100, 12)
        val second = dailyPick(listOf(startedArt, preferred, lowRatedArt), 100, 12)

        assertEquals(preferred, first)
        assertEquals(first, second)
    }

    @Test
    fun dailyPickOnlyChoosesMovies() {
        val preferredMovie = movie(99, backdropImageId = 99)
        val preferredShow = show(100).copy(backdropImageId = 100)

        assertEquals(preferredMovie, dailyPick(listOf(preferredShow, preferredMovie), 100, 12))
    }

    @Test
    fun dailyPickDoesNotChangeWhenLibraryChangesWithinSlot() {
        val candidates = (1L..4L).map { movie(it, backdropImageId = it) }
        val today = dailyPick(candidates, 100, 12)
        val added = candidates + movie(99, backdropImageId = 99)

        assertEquals(
            today,
            dailyPick(
                added,
                100,
                12,
                previousSlot = dailyPickSlot(100, 12),
                previousPick = today,
            ),
        )
    }

    @Test
    fun dailyPickChangesAtSixAmAndSixPm() {
        val candidates = (1L..4L).map { movie(it, backdropImageId = it) }
        val today = dailyPick(candidates, 100, 6)
        val tonight = dailyPick(candidates, 100, 18)
        val nextToday = dailyPick(candidates, 101, 6)

        assertEquals(today, dailyPick(candidates, 100, 17))
        assertNotEquals(today, tonight)
        assertEquals(tonight, dailyPick(candidates, 101, 0))
        assertEquals(tonight, dailyPick(candidates, 101, 5))
        assertNotEquals(tonight, nextToday)
    }

    @Test
    fun dailyPickWordingChangesAtSixAmAndSixPm() {
        assertEquals("Tonight's Pick", dailyPickLabel(0))
        assertEquals("Tonight's Pick", dailyPickLabel(5))
        assertEquals("Today's Pick", dailyPickLabel(6))
        assertEquals("Today's Pick", dailyPickLabel(17))
        assertEquals("Tonight's Pick", dailyPickLabel(18))
        assertEquals("Tonight's Pick", dailyPickLabel(23))
    }

    @Test
    fun sameDayIsStableAndCapped() {
        val first = rowsFor(100)
        val second = rowsFor(100)
        assertEquals(first, second)
        assertTrue(first.size == 3)
    }

    @Test
    fun rowsRotateAcrossDays() {
        val keysByDay = (0L..14L).map { day -> rowsFor(day).map { it.key } }
        assertTrue(keysByDay.distinct().size > 1)
    }

    @Test
    fun shelvesHonorTheirFilters() {
        for (day in 0L..30L) {
            for (row in rowsFor(day)) {
                when {
                    row.title.startsWith("Tonight: ") -> {
                        val genre = row.title.removePrefix("Tonight: ")
                        assertTrue(row.items.all { item -> item.genres.orEmpty().any { it.name == genre } })
                        assertTrue(row.items.none { it.progress != null })
                    }
                    row.key == "quick" -> assertTrue(
                        row.items.all { it.kind == "movie" && it.durationMs < 90 * 60 * 1000L },
                    )
                    row.key == "again" ->
                        assertEquals(recentlyPlayed.map { it.id }, row.items.map { it.id })
                    row.key == "unstarted" -> {
                        assertEquals("New to You", row.title)
                        assertTrue(
                            row.items.none {
                                it.progress != null || (it.kind == "show" && it.unwatchedCount < it.episodeCount)
                            },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun thinShelvesAreSkipped() {
        // Two finished movies cannot fill the Watch It Again shelf.
        val sparse = listOf(movie(5, started = true), movie(7, started = true))
        for (day in 0L..30L) {
            val rows = discoveryRows(movies, shows, collections, sparse, day)
            assertTrue(rows.none { it.key == "again" })
        }
    }

    @Test
    fun collectionShelfMayRunThin() {
        // Collections are exempt from the four-item floor; Loom already
        // guarantees at least two owned members.
        val rows = (0L..30L).flatMap { rowsFor(it) }.filter { it.key.startsWith("col-") }
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { it.items.size == 2 })
    }
}
