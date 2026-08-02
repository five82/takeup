package xyz.five82.takeup.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.five82.takeup.data.LoomItem

class MainViewModelTest {
    private val episodes = listOf(episode(1), episode(2), episode(3))

    @Test
    fun `finds the episode after the current episode`() {
        assertEquals(2L, nextEpisodeAfter(episodes, 1)?.id)
    }

    @Test
    fun `returns no next episode at the end or for an unknown item`() {
        assertNull(nextEpisodeAfter(episodes, 3))
        assertNull(nextEpisodeAfter(episodes, 99))
    }

    private fun episode(id: Long) = LoomItem(
        id = id,
        kind = "episode",
        title = "Episode $id",
        year = 0,
        overview = "",
        seasonNumber = 1,
        episodeNumber = id.toInt(),
    )
}
