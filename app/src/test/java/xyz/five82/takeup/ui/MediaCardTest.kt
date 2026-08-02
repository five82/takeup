package xyz.five82.takeup.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
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
    fun `includes the series title on episode cards`() {
        assertEquals(
            "Test Show \u00B7 S01E02",
            episode(season = 1, episode = 2)
                .copy(seriesTitle = "Test Show")
                .cardSubtitle(),
        )
    }

    @Test
    fun `formats full episode context for details and playback`() {
        assertEquals(
            "Test Show \u00B7 Season 1 \u00B7 S01E02",
            episode(season = 1, episode = 2)
                .copy(seriesTitle = "Test Show", seasonTitle = "Season 1")
                .episodeContext(),
        )
    }

    @Test
    fun `formats release dates and preserves invalid values`() {
        assertEquals("Aug 2, 2026", formatReleaseDate("2026-08-02", Locale.US))
        assertEquals("not-a-date", formatReleaseDate("not-a-date", Locale.US))
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
