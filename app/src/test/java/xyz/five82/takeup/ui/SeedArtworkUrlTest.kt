package xyz.five82.takeup.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.five82.takeup.data.Genre
import xyz.five82.takeup.data.HomeContent
import xyz.five82.takeup.data.LoomItem

class SeedArtworkUrlTest {
    private val serverUrl = "http://loom.local:8080"

    @Test
    fun `hero pool prefers continue watching then recently added without duplicates`() {
        val state = home(
            continueWatching = listOf(item(1), item(2), item(3), item(4)),
            recentlyAdded = listOf(item(3), item(5), item(6), item(7), item(8)),
        )
        assertEquals(listOf(1L, 2L, 3L, 5L, 6L, 7L), state.heroItems().map { it.id })
    }

    @Test
    fun `hero pool falls back to the first movie or show`() {
        val movies = home(movies = listOf(item(10), item(11)))
        assertEquals(listOf(10L), movies.heroItems().map { it.id })

        val shows = home(shows = listOf(item(20)))
        assertEquals(listOf(20L), shows.heroItems().map { it.id })

        assertTrue(home().heroItems().isEmpty())
    }

    @Test
    fun `home seeds from the first hero item backdrop`() {
        val state = home(continueWatching = listOf(item(1, backdropId = 41)))
        assertEquals(imageUrl(41), seedArtworkUrl(state))
    }

    @Test
    fun `details and show details seed from backdrop with poster fallback`() {
        val withBackdrop = item(1, backdropId = 41, posterId = 51)
        val posterOnly = item(2, posterId = 52)

        assertEquals(
            imageUrl(41),
            seedArtworkUrl(MainUiState.Details(serverUrl, withBackdrop, BrowseOrigin.Home)),
        )
        assertEquals(
            imageUrl(52),
            seedArtworkUrl(MainUiState.Details(serverUrl, posterOnly, BrowseOrigin.Home)),
        )
        assertEquals(
            imageUrl(41),
            seedArtworkUrl(MainUiState.ShowDetails(serverUrl, withBackdrop, origin = BrowseOrigin.Home)),
        )
    }

    @Test
    fun `season seeds from the show backdrop with season poster fallback`() {
        val show = item(1, backdropId = 41)
        val season = item(2, posterId = 52)
        assertEquals(
            imageUrl(41),
            seedArtworkUrl(MainUiState.Season(serverUrl, show, season)),
        )
        assertEquals(
            imageUrl(52),
            seedArtworkUrl(MainUiState.Season(serverUrl, item(1), season)),
        )
    }

    @Test
    fun `playback seeds from the item being watched`() {
        assertEquals(
            imageUrl(41),
            seedArtworkUrl(
                MainUiState.Playback(serverUrl, item(1, backdropId = 41), BrowseOrigin.Home),
            ),
        )
    }

    @Test
    fun `genre landing seeds from its first item`() {
        val state = MainUiState.GenreLanding(
            serverUrl = serverUrl,
            genre = Genre(id = 7, name = "Sci-Fi"),
            items = listOf(item(1, backdropId = 41), item(2, backdropId = 42)),
        )
        assertEquals(imageUrl(41), seedArtworkUrl(state))
        assertNull(
            seedArtworkUrl(
                MainUiState.GenreLanding(serverUrl, Genre(id = 7, name = "Sci-Fi")),
            ),
        )
    }

    @Test
    fun `states without dominant artwork use the brand seed`() {
        assertNull(seedArtworkUrl(MainUiState.Starting))
        assertNull(seedArtworkUrl(MainUiState.Connect()))
        assertNull(seedArtworkUrl(MainUiState.Library(serverUrl, LibraryKind.Movies)))
        assertNull(seedArtworkUrl(MainUiState.Search(serverUrl)))
        assertNull(seedArtworkUrl(MainUiState.GenreHub(serverUrl)))
        assertNull(seedArtworkUrl(home()))
    }

    private fun home(
        continueWatching: List<LoomItem> = emptyList(),
        recentlyAdded: List<LoomItem> = emptyList(),
        movies: List<LoomItem> = emptyList(),
        shows: List<LoomItem> = emptyList(),
    ) = MainUiState.Home(
        serverUrl = serverUrl,
        content = HomeContent(
            continueWatching = continueWatching,
            recentlyAdded = recentlyAdded,
            movies = movies,
            shorts = emptyList(),
            shows = shows,
        ),
    )

    private fun item(id: Long, backdropId: Long = 0, posterId: Long = 0) = LoomItem(
        id = id,
        kind = "movie",
        title = "Item $id",
        year = 2024,
        overview = "",
        backdropImageId = backdropId,
        posterImageId = posterId,
    )

    private fun imageUrl(imageId: Long) = "$serverUrl/api/v1/images/$imageId"
}
