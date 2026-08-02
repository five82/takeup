package xyz.five82.takeup.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LoomRepositoryTest {
    @Test
    fun `adds series context to episodes and reuses parent lookups`() = runBlocking {
        val requestedIds = mutableListOf<Long>()
        val items = listOf(
            episode(id = 1, parentId = 10),
            episode(id = 2, parentId = 10),
            LoomItem(id = 3, kind = "movie", title = "Movie", year = 2026, overview = ""),
        )

        val contextualItems = addEpisodeContext(items) { id ->
            requestedIds += id
            when (id) {
                10L -> LoomItem(
                    id = 10,
                    parentId = 20,
                    kind = "season",
                    title = "Season 1",
                    year = 0,
                    overview = "",
                )
                20L -> LoomItem(
                    id = 20,
                    kind = "show",
                    title = "Test Show",
                    year = 2026,
                    overview = "",
                )
                else -> null
            }
        }

        assertEquals(listOf(10L, 20L), requestedIds)
        assertEquals("Test Show", contextualItems[0].seriesTitle)
        assertEquals("Season 1", contextualItems[1].seasonTitle)
        assertEquals("", contextualItems[2].seriesTitle)
    }

    private fun episode(id: Long, parentId: Long) = LoomItem(
        id = id,
        parentId = parentId,
        kind = "episode",
        title = "Episode $id",
        year = 0,
        overview = "",
        seasonNumber = 1,
        episodeNumber = id.toInt(),
    )
}
