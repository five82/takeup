package xyz.five82.takeup.ui.player

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleOverlayTest {
    @Test
    fun `fits a wide picture inside the surface`() {
        val rect = subtitleVideoRect(Size(2400f, 1080f), 2.4f)

        assertEquals(2400f, rect.width, 0.01f)
        assertEquals(1000f, rect.height, 0.01f)
        assertEquals(0f, rect.left, 0.01f)
        assertEquals(40f, rect.top, 0.01f)
    }

    @Test
    fun `fits a narrow picture with side bars`() {
        val rect = subtitleVideoRect(Size(2400f, 1080f), 4f / 3f)

        assertEquals(1440f, rect.width, 0.01f)
        assertEquals(1080f, rect.height, 0.01f)
        assertEquals(480f, rect.left, 0.01f)
        assertEquals(0f, rect.top, 0.01f)
    }

    @Test
    fun `unknown aspect uses the whole surface`() {
        val surface = Size(2400f, 1080f)

        for (aspect in listOf(null, 0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val rect = subtitleVideoRect(surface, aspect)
            assertEquals(0f, rect.left, 0f)
            assertEquals(0f, rect.top, 0f)
            assertEquals(surface.width, rect.width, 0f)
            assertEquals(surface.height, rect.height, 0f)
        }
    }
}
