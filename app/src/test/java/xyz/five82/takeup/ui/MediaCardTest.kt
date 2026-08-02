package xyz.five82.takeup.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.five82.takeup.data.LoomItem

class MediaCardTest {
    @Test
    fun `formats regular and combined episode numbers`() {
        assertEquals(
            "S01E02-03",
            episode(season = 1, episode = 2, episodeEnd = 3).subtitle(),
        )
    }

    @Test
    fun `formats season zero specials`() {
        assertEquals(
            "S00E01",
            episode(season = 0, episode = 1).subtitle(),
        )
    }

    private fun episode(
        season: Int,
        episode: Int,
        episodeEnd: Int = episode,
    ): LoomItem = LoomItem(
        id = 1,
        kind = "episode",
        title = "Episode",
        year = 0,
        overview = "",
        seasonNumber = season,
        episodeNumber = episode,
        episodeEndNumber = episodeEnd,
    )
}
