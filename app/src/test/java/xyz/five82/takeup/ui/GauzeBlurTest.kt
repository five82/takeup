package xyz.five82.takeup.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.five82.takeup.ui.components.boxBlur

class GauzeBlurTest {

    @Test
    fun uniformFieldIsUntouched() {
        val color = 0xFF3FA7D1.toInt()
        val pixels = IntArray(8 * 6) { color }
        boxBlur(pixels, width = 8, height = 6, radius = 2)
        assertTrue(pixels.all { it == color })
    }

    @Test
    fun opaqueAlphaSurvivesTheBlur() {
        val pixels = IntArray(10 * 10) { if (it % 3 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        boxBlur(pixels, width = 10, height = 10, radius = 3)
        assertTrue(pixels.all { (it ushr 24) == 0xFF })
    }

    @Test
    fun impulseSpreadsSymmetrically() {
        // A single bright pixel mid-line must bleed the same amount to both
        // sides, and blurring must not shift the image (the transposed
        // two-pass trick restores orientation).
        val width = 11
        val pixels = IntArray(width * width)
        pixels[5 * width + 5] = 0xFFFFFFFF.toInt()
        boxBlur(pixels, width = width, height = width, radius = 2)
        val center = pixels[5 * width + 5]
        assertTrue((center shr 16 and 0xFF) > 0)
        assertEquals(pixels[5 * width + 3], pixels[5 * width + 7])
        assertEquals(pixels[3 * width + 5], pixels[7 * width + 5])
    }
}
