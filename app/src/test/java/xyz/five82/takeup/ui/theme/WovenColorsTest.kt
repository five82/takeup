package xyz.five82.takeup.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WovenColorsTest {
    private fun bitmap(vararg pixels: Int): Bitmap =
        Bitmap.createBitmap(pixels, pixels.size, 1, Bitmap.Config.ARGB_8888)

    @Test fun saturatedColorsBeatNeutralBackgroundAndKeepSeparateHues() {
        val art = bitmap(*IntArray(12) { android.graphics.Color.rgb(230, 225, 215) },
            *IntArray(5) { android.graphics.Color.RED },
            *IntArray(4) { android.graphics.Color.GREEN },
            *IntArray(3) { android.graphics.Color.BLUE })
        val threads = WovenColors.threadColors(art)
        assertEquals(3, threads.size)
        assertEquals(Color.Red, threads[0])
        assertEquals(Color.Green, threads[1])
        assertEquals(Color.Blue, threads[2])
        assertEquals(Color.Red, WovenColors.dominantColor(art))
        assertEquals(listOf(Color.Red), WovenColors.threadColors(art, max = 1))
    }

    @Test fun shadesOfOneHueDoNotCrowdOutASecondThread() {
        val art = bitmap(android.graphics.Color.RED, android.graphics.Color.rgb(180, 0, 0),
            android.graphics.Color.BLUE, android.graphics.Color.BLACK, android.graphics.Color.WHITE)
        assertEquals(2, WovenColors.threadColors(art).size)
        assertTrue(WovenColors.threadColors(art).contains(Color.Blue))
        assertTrue(WovenColors.threadColors(bitmap(android.graphics.Color.BLACK, android.graphics.Color.WHITE)).isEmpty())
        assertNull(WovenColors.dominantColor(bitmap(android.graphics.Color.BLACK)))
    }

    @Test fun unreadableArtworkDoesNotCacheAnEmptyPalette() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val url = File(context.cacheDir, "missing-woven-art.png").toURI().toString()
        assertEquals(emptyList<Color>(), WovenColors.threadsFor(context, url))
        assertNull(WovenColors.cached(url))
    }

    @Test fun missingArtworkProducesNoThreadsAndFieldToneStaysLegible() = runBlocking {
        assertEquals(emptyList<Color>(), WovenColors.cached(null))
        assertEquals(emptyList<Color>(), WovenColors.threadsFor(RuntimeEnvironment.getApplication(), null))
        assertNull(WovenColors.cached("unused-artwork"))
        val dark = Color(0xFF201A1A).fieldTone()
        val bright = Color(0xFFFF0000).fieldTone()
        assertTrue(dark.red > 0.1f)
        assertTrue(bright.red < 0.8f)
        assertFalse(dark == bright)
    }
}
