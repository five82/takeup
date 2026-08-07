package xyz.five82.takeup.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.five82.takeup.data.LoomItem

class MainViewModelTest {
    private val episodes = listOf(episode(1), episode(2), episode(3))

    // A show of 20 episodes with 8 left, split across two seasons.
    private val show = rollup(id = 10, kind = "show", episodes = 20, unwatched = 8)
    private val seasonOne =
        rollup(id = 11, kind = "season", episodes = 10, unwatched = 3, parentId = 10)
    private val seasonTwo =
        rollup(id = 12, kind = "season", episodes = 10, unwatched = 5, parentId = 10)

    @Test
    fun `finds the episode after the current episode`() {
        assertEquals(2L, nextEpisodeAfter(episodes, 1)?.id)
    }

    @Test
    fun `returns no next episode at the end or for an unknown item`() {
        assertNull(nextEpisodeAfter(episodes, 3))
        assertNull(nextEpisodeAfter(episodes, 99))
    }

    @Test
    fun `watching an episode leaves one fewer under its season and show`() {
        assertEquals(2, seasonOne.shiftUnwatched(-1).unwatchedCount)
        assertEquals(7, show.shiftUnwatched(-1).unwatchedCount)
    }

    @Test
    fun `a rollup never leaves the range Loom counted`() {
        assertEquals(0, seasonOne.shiftUnwatched(-9).unwatchedCount)
        assertEquals(10, seasonOne.shiftUnwatched(9).unwatchedCount)
    }

    @Test
    fun `rows without a rollup ignore the shift`() {
        assertEquals(0, episode(1).shiftUnwatched(1).unwatchedCount)
    }

    @Test
    fun `marking a show watched empties it and every season below`() {
        assertEquals(0, show.afterWatchedCascade(show, watched = true).unwatchedCount)
        assertEquals(0, seasonOne.afterWatchedCascade(show, watched = true).unwatchedCount)
        assertEquals(0, seasonTwo.afterWatchedCascade(show, watched = true).unwatchedCount)
    }

    @Test
    fun `clearing a show restores every episode below it`() {
        assertEquals(20, show.afterWatchedCascade(show, watched = false).unwatchedCount)
        assertEquals(10, seasonOne.afterWatchedCascade(show, watched = false).unwatchedCount)
    }

    @Test
    fun `a season cascade moves its show by only its own episodes`() {
        // Season one's 3 unwatched leave the show's 8, and its other season stays put.
        assertEquals(0, seasonOne.afterWatchedCascade(seasonOne, watched = true).unwatchedCount)
        assertEquals(5, show.afterWatchedCascade(seasonOne, watched = true).unwatchedCount)
        assertEquals(5, seasonTwo.afterWatchedCascade(seasonOne, watched = true).unwatchedCount)
    }

    @Test
    fun `clearing a season returns its episodes to the show`() {
        // Season one goes from 3 unwatched back to all 10, so the show gains 7.
        assertEquals(10, seasonOne.afterWatchedCascade(seasonOne, watched = false).unwatchedCount)
        assertEquals(15, show.afterWatchedCascade(seasonOne, watched = false).unwatchedCount)
    }

    @Test
    fun `an unrelated row is untouched by a cascade`() {
        val other = rollup(id = 99, kind = "show", episodes = 6, unwatched = 6)
        assertEquals(6, other.afterWatchedCascade(show, watched = true).unwatchedCount)
        assertEquals(6, other.afterWatchedCascade(seasonOne, watched = true).unwatchedCount)
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

    private fun rollup(
        id: Long,
        kind: String,
        episodes: Int,
        unwatched: Int,
        parentId: Long = 0,
    ) = LoomItem(
        id = id,
        kind = kind,
        title = "Item $id",
        year = 0,
        overview = "",
        parentId = parentId,
        episodeCount = episodes,
        unwatchedCount = unwatched,
    )
}
