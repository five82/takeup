package xyz.five82.takeup.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.five82.takeup.data.HomeContent
import xyz.five82.takeup.data.LoomItem

class OfflineHomeTest {
    private val serverUrl = "http://loom.local:8080"

    @Test
    fun `network loss flips a loaded home to the downloads-only offline home`() {
        val offline = offlineHomeAfterLoss(home(), hasDownloads = true)
        assertEquals(serverUrl, offline?.serverUrl)
        assertTrue(offline!!.isOffline)
        assertTrue(offline.content.isEmpty())
    }

    @Test
    fun `without downloads there is no offline home to show`() {
        assertNull(offlineHomeAfterLoss(home(), hasDownloads = false))
    }

    @Test
    fun `an already offline home is left alone`() {
        assertNull(offlineHomeAfterLoss(home(isOffline = true), hasDownloads = true))
    }

    @Test
    fun `a loading home is left for the failing request to settle`() {
        assertNull(offlineHomeAfterLoss(home(isLoading = true), hasDownloads = true))
    }

    @Test
    fun `only home reacts to network loss`() {
        assertNull(offlineHomeAfterLoss(MainUiState.Starting, hasDownloads = true))
        assertNull(
            offlineHomeAfterLoss(
                MainUiState.Search(serverUrl),
                hasDownloads = true,
            ),
        )
    }

    private fun home(
        isLoading: Boolean = false,
        isOffline: Boolean = false,
    ) = MainUiState.Home(
        serverUrl = serverUrl,
        content = HomeContent(
            continueWatching = emptyList(),
            nextUp = emptyList(),
            recentlyAdded = emptyList(),
            movies = listOf(
                LoomItem(id = 1, kind = "movie", title = "Arrival", year = 2016, overview = ""),
            ),
            shorts = emptyList(),
            shows = emptyList(),
            collections = emptyList(),
        ),
        isLoading = isLoading,
        isOffline = isOffline,
    )
}
