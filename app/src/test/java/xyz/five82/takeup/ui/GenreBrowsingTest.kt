package xyz.five82.takeup.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.five82.takeup.data.Genre
import xyz.five82.takeup.data.HomeContent
import xyz.five82.takeup.data.LoomItem

class GenreBrowsingTest {
    private val action = Genre(id = 1, name = "Action")
    private val drama = Genre(id = 2, name = "Drama")
    private val western = Genre(id = 3, name = "Western")

    @Test
    fun `spotlights need enough titles per genre`() {
        val state = home(
            movies(action, count = 4) + movies(drama, count = 3, startId = 100),
        )
        val spotlights = state.genreSpotlights(dayOfYear = 0, minItems = 4)
        assertEquals(listOf(action), spotlights.map { it.first })
        assertEquals(4, spotlights.single().second.size)
    }

    @Test
    fun `spotlights rotate with the day`() {
        val state = home(
            movies(action, count = 6) +
                movies(drama, count = 5, startId = 100) +
                movies(western, count = 4, startId = 200),
        )
        val dayZero = state.genreSpotlights(dayOfYear = 0, rowCount = 2)
        val dayOne = state.genreSpotlights(dayOfYear = 1, rowCount = 2)
        assertEquals(listOf(action, drama), dayZero.map { it.first })
        assertEquals(listOf(drama, western), dayOne.map { it.first })
    }

    @Test
    fun `spotlight rows cap at twelve items`() {
        val state = home(movies(action, count = 20))
        val spotlights = state.genreSpotlights(dayOfYear = 0)
        assertEquals(12, spotlights.single().second.size)
    }

    @Test
    fun `browse entries count titles largest first`() {
        val state = home(
            movies(action, count = 2) + movies(drama, count = 5, startId = 100),
        )
        val entries = state.genreBrowseEntries()
        assertEquals(listOf("Drama", "Action"), entries.map { it.name })
        assertEquals(listOf(5, 2), entries.map { it.itemCount })
    }

    @Test
    fun `no genres yields no rows`() {
        val state = home(listOf(item(1, emptyList())))
        assertTrue(state.genreSpotlights(dayOfYear = 0).isEmpty())
        assertTrue(state.genreBrowseEntries().isEmpty())
    }

    private fun movies(genre: Genre, count: Int, startId: Long = 0) =
        (1..count).map { item(startId + it, listOf(genre)) }

    private fun item(id: Long, genres: List<Genre>) = LoomItem(
        id = id,
        kind = "movie",
        title = "Item $id",
        year = 2024,
        overview = "",
        genres = genres,
    )

    private fun home(movies: List<LoomItem>) = MainUiState.Home(
        serverUrl = "http://loom.local:8080",
        content = HomeContent(
            continueWatching = emptyList(),
            recentlyAdded = emptyList(),
            movies = movies,
            shows = emptyList(),
        ),
    )
}
