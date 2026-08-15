package xyz.five82.takeup

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.LoomApi
import xyz.five82.takeup.ui.thumbUrl

class ImageUrlsTest {
    private val api = LoomApi(baseUrl = "http://loom.local")

    @Test
    fun `thumb url uses backdrop when dedicated thumb is missing`() {
        val item = Item(
            backdropImageId = 42,
            backdropImageTag = "backdrop-tag",
        )

        assertEquals(
            "http://loom.local/api/v1/images/42?width=480&tag=backdrop-tag",
            api.thumbUrl(item),
        )
    }

    @Test
    fun `thumb url prefers dedicated thumb`() {
        val item = Item(
            backdropImageId = 42,
            thumbImageId = 99,
            thumbImageTag = "thumb-tag",
        )

        assertEquals(
            "http://loom.local/api/v1/images/99?width=480&tag=thumb-tag",
            api.thumbUrl(item),
        )
    }
}
