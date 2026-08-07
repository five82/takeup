package xyz.five82.takeup.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageUrlAtWidthTest {
    private val tagged = "http://loom.test:8097/api/v1/images/7?tag=poster-tag"

    @Test
    fun `snaps a requested width up to the covering bucket`() {
        assertEquals("$tagged&width=240", imageUrlAtWidth(tagged, 128))
        assertEquals("$tagged&width=240", imageUrlAtWidth(tagged, 240))
        assertEquals("$tagged&width=480", imageUrlAtWidth(tagged, 241))
        assertEquals("$tagged&width=960", imageUrlAtWidth(tagged, 700))
        assertEquals("$tagged&width=1440", imageUrlAtWidth(tagged, 1236))
    }

    @Test
    fun `clamps oversized and unbounded slots to the widest bucket`() {
        assertEquals("$tagged&width=1440", imageUrlAtWidth(tagged, 4096))
        assertEquals("$tagged&width=1440", imageUrlAtWidth(tagged, Int.MAX_VALUE))
    }

    @Test
    fun `starts the query when the image has no tag`() {
        val untagged = "http://loom.test:8097/api/v1/images/7"
        assertEquals("$untagged?width=480", imageUrlAtWidth(untagged, 480))
    }

    @Test
    fun `leaves non-Loom artwork urls untouched`() {
        val thumbnail = "https://image.tmdb.org/t/p/w342/poster.jpg"
        assertEquals(thumbnail, imageUrlAtWidth(thumbnail, 480))
    }
}
