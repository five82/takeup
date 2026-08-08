package xyz.five82.takeup.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.five82.takeup.data.HomeContent
import xyz.five82.takeup.data.LoomItem
import xyz.five82.takeup.data.PlaybackProgress

class SpotlightTest {
    @Test
    fun `same day picks the same title`() {
        val state = home(movies = listOf(movie(1), movie(2), movie(3)))
        assertEquals(state.spotlightItem(epochDay = 20500), state.spotlightItem(epochDay = 20500))
    }

    @Test
    fun `consecutive days rotate through the pool`() {
        val state = home(movies = listOf(movie(1), movie(2), movie(3)))
        val picks = (20500L..20502L).map { state.spotlightItem(it)?.id }
        assertEquals(listOf(1L, 2L, 3L).toSet(), picks.toSet())
    }

    @Test
    fun `prefers untouched movies with backdrops`() {
        val state = home(
            movies = listOf(
                movie(1, watched = true),
                movie(2, backdropId = 0),
                movie(3),
            ),
        )
        (20500L..20510L).forEach { day ->
            assertEquals(3L, state.spotlightItem(day)?.id)
        }
    }

    @Test
    fun `a movie in progress never spotlights - it is already in Continue Watching`() {
        val state = home(movies = listOf(movie(1, resumeMs = 60_000), movie(2)))
        (20500L..20510L).forEach { day ->
            assertEquals(2L, state.spotlightItem(day)?.id)
        }
    }

    @Test
    fun `falls back to watched movies with backdrops before bare movies`() {
        val watched = home(movies = listOf(movie(1, watched = true), movie(2, backdropId = 0)))
        assertEquals(1L, watched.spotlightItem(20500)?.id)

        val bare = home(movies = listOf(movie(1, watched = true, backdropId = 0)))
        assertEquals(1L, bare.spotlightItem(20500)?.id)
    }

    @Test
    fun `falls back to shows then shorts without movies`() {
        val shows = home(shows = listOf(movie(7)))
        assertEquals(7L, shows.spotlightItem(20500)?.id)

        val shorts = home(shorts = listOf(movie(8)))
        assertEquals(8L, shorts.spotlightItem(20500)?.id)
    }

    @Test
    fun `empty library has no spotlight`() {
        assertNull(home().spotlightItem(20500))
    }

    private fun home(
        movies: List<LoomItem> = emptyList(),
        shows: List<LoomItem> = emptyList(),
        shorts: List<LoomItem> = emptyList(),
    ) = MainUiState.Home(
        serverUrl = "http://loom.local:8080",
        content = HomeContent(
            continueWatching = emptyList(),
            nextUp = emptyList(),
            recentlyAdded = emptyList(),
            movies = movies,
            shorts = shorts,
            shows = shows,
            collections = emptyList(),
        ),
    )

    private fun movie(
        id: Long,
        watched: Boolean = false,
        resumeMs: Long = 0,
        backdropId: Long = id + 100,
    ) = LoomItem(
        id = id,
        kind = "movie",
        title = "Movie $id",
        year = 2024,
        overview = "",
        backdropImageId = backdropId,
        progress = if (watched || resumeMs > 0) {
            PlaybackProgress(
                positionMs = resumeMs,
                durationMs = 7_200_000,
                played = watched,
                resumePositionMs = resumeMs,
            )
        } else {
            null
        },
    )
}
