package xyz.five82.takeup.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.LoomApi

class ImageUrlsTest {
    @Test fun `each artwork kind uses its own image id tag and default width`() {
        val api = LoomApi("http://loom:8097")
        val item = Item(posterImageId = 1, posterImageTag = "p", backdropImageId = 2,
            backdropImageTag = "b", logoImageId = 3, logoImageTag = "l",
            thumbImageId = 4, thumbImageTag = "t")

        assertEquals("http://loom:8097/api/v1/images/1?width=480&tag=p", api.posterUrl(item))
        assertEquals("http://loom:8097/api/v1/images/2?width=1440&tag=b", api.backdropUrl(item))
        assertEquals("http://loom:8097/api/v1/images/3?width=480&tag=l", api.logoUrl(item))
        assertEquals("http://loom:8097/api/v1/images/4?width=480&tag=t", api.thumbUrl(item))
        assertEquals("http://loom:8097/api/v1/images/4?width=240&tag=t", api.thumbUrl(item, 100))
        assertNull(api.logoUrl(Item()))
    }
}
