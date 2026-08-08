package xyz.five82.takeup.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoomCollectionTest {
    private val serverUrl = "http://loom.test:8097"

    // The card is landscape, so a later member's backdrop beats an earlier
    // member's poster rather than the first artwork of any kind winning.
    @Test
    fun `card artwork prefers a backdrop from any member`() {
        val collection = collection(
            movie(id = 1, posterImageId = 7),
            movie(id = 2, backdropImageId = 8),
        )

        assertEquals(
            "http://loom.test:8097/api/v1/images/8",
            collection.artworkUrl(serverUrl),
        )
    }

    @Test
    fun `card artwork falls back to a poster`() {
        val collection = collection(movie(id = 1), movie(id = 2, posterImageId = 7))

        assertEquals(
            "http://loom.test:8097/api/v1/images/7",
            collection.artworkUrl(serverUrl),
        )
    }

    @Test
    fun `a shelf without artwork has no card image`() {
        assertNull(collection(movie(id = 1), movie(id = 2)).artworkUrl(serverUrl))
    }

    private fun collection(vararg items: LoomItem) = LoomCollection(
        slug = "star-wars",
        title = "Star Wars",
        items = items.toList(),
    )

    private fun movie(
        id: Long,
        posterImageId: Long = 0,
        backdropImageId: Long = 0,
    ) = LoomItem(
        id = id,
        kind = "movie",
        title = "Movie $id",
        year = 1977,
        overview = "",
        posterImageId = posterImageId,
        backdropImageId = backdropImageId,
    )
}
