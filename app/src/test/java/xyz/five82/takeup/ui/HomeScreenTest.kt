package xyz.five82.takeup.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.five82.takeup.data.LoomItem

class HomeScreenTest {
    @Test
    fun `holds the first load behind placeholders`() {
        assertTrue(showsPlaceholders(home(isLoading = true)))
    }

    @Test
    fun `keeps the offline home on screen while reconnecting`() {
        // Without this a cold start with downloads renders the offline layout for
        // the length of the first request, and a reconnect blanks the downloads.
        assertFalse(showsPlaceholders(home(isLoading = true, isOffline = true)))
    }

    @Test
    fun `refreshes a loaded library in place`() {
        assertFalse(showsPlaceholders(home(isLoading = true, movies = listOf(movie))))
    }

    @Test
    fun `shows no placeholders once the load settles`() {
        assertFalse(showsPlaceholders(home()))
    }

    private val movie = LoomItem(
        id = 42,
        kind = "movie",
        title = "Arrival",
        year = 2016,
        overview = "",
    )

    private fun home(
        isLoading: Boolean = false,
        isOffline: Boolean = false,
        movies: List<LoomItem> = emptyList(),
    ) = MainUiState.Home(
        serverUrl = "http://loom.local:8080",
        content = EMPTY_HOME_CONTENT.copy(movies = movies),
        isLoading = isLoading,
        isOffline = isOffline,
    )
}
