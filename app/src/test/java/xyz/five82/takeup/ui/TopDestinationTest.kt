package xyz.five82.takeup.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.five82.takeup.data.LoomItem

class TopDestinationTest {
    private val serverUrl = "http://loom.local:8080"

    @Test
    fun `top level states map to their toolbar destination`() {
        assertEquals(
            TopDestination.Home,
            MainUiState.Home(serverUrl).topDestination(),
        )
        assertEquals(
            TopDestination.Movies,
            MainUiState.Library(serverUrl, LibraryKind.Movies).topDestination(),
        )
        assertEquals(
            TopDestination.Shorts,
            MainUiState.Library(serverUrl, LibraryKind.Shorts).topDestination(),
        )
        assertEquals(
            TopDestination.Shows,
            MainUiState.Library(serverUrl, LibraryKind.Shows).topDestination(),
        )
        assertEquals(
            TopDestination.Search,
            MainUiState.Search(serverUrl).topDestination(),
        )
    }

    @Test
    fun `offline home hides the toolbar`() {
        // Every remaining destination needs Loom, so offering them would only lead
        // to screens that cannot load.
        assertNull(MainUiState.Home(serverUrl, isOffline = true).topDestination())
    }

    @Test
    fun `detail and playback states hide the toolbar`() {
        val item = LoomItem(id = 1, kind = "movie", title = "Item", year = 2024, overview = "")
        assertNull(MainUiState.Starting.topDestination())
        assertNull(MainUiState.Connect().topDestination())
        assertNull(MainUiState.Details(serverUrl, item, BrowseOrigin.Home).topDestination())
        assertNull(MainUiState.ShowDetails(serverUrl, item, origin = BrowseOrigin.Home).topDestination())
        assertNull(MainUiState.Season(serverUrl, item, item).topDestination())
        assertNull(MainUiState.Artwork(serverUrl, item).topDestination())
        assertNull(MainUiState.Playback(serverUrl, item, BrowseOrigin.Home).topDestination())
    }
}
