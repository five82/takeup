package xyz.five82.takeup.ui

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlayerScreenTest {
    @Test
    fun `detects HDR10 video from PQ color transfer`() {
        assertTrue(
            isHdr10Track(
                videoFormat(MimeTypes.VIDEO_H265, C.COLOR_TRANSFER_ST2084),
            ),
        )
    }

    @Test
    fun `uses track labels before human readable languages`() {
        assertEquals(
            "Director commentary",
            trackLabel("Director commentary", "en", Locale.US),
        )
        assertEquals(
            "Spanish",
            trackLabel(null, "es", Locale.US),
        )
        assertEquals(
            "Default",
            trackLabel(null, "und", Locale.US),
        )
    }

    @Test
    fun `skips to the chapter marks around the current position`() {
        val chapters = listOf(0L, 300_000L, 500_000L)

        assertEquals(300_000L, nextChapterPositionMs(chapters, 299_999L))
        assertEquals(500_000L, nextChapterPositionMs(chapters, 300_000L))
        assertNull(nextChapterPositionMs(chapters, 500_000L))
        assertEquals(300_000L, previousChapterPositionMs(chapters, 400_000L))
        assertEquals(0L, previousChapterPositionMs(chapters, 299_999L))
    }

    // Pressing previous just after a mark should step back past it rather than
    // restarting the handful of seconds you have already watched.
    @Test
    fun `steps past the mark just entered when going back`() {
        val chapters = listOf(0L, 300_000L, 500_000L)

        assertEquals(300_000L, previousChapterPositionMs(chapters, 304_000L))
        assertEquals(0L, previousChapterPositionMs(chapters, 302_000L))
        assertEquals(0L, previousChapterPositionMs(chapters, 0L))
    }

    // A file whose first mark is not at zero has nowhere to go back to from the
    // opening seconds, so the button stays disabled rather than seeking forward.
    @Test
    fun `has no chapter behind a position before the first mark`() {
        assertNull(previousChapterPositionMs(listOf(5_000L, 300_000L), 1_000L))
        assertNull(previousChapterPositionMs(emptyList(), 1_000L))
        assertNull(nextChapterPositionMs(emptyList(), 1_000L))
    }

    @Test
    fun `does not badge HLG or Dolby Vision as HDR10`() {
        assertFalse(isHdr10Track(videoFormat(MimeTypes.VIDEO_H265, C.COLOR_TRANSFER_HLG)))
        assertFalse(
            isHdr10Track(
                videoFormat(MimeTypes.VIDEO_DOLBY_VISION, C.COLOR_TRANSFER_ST2084),
            ),
        )
    }

    private fun videoFormat(mimeType: String, colorTransfer: Int): Format =
        Format.Builder()
            .setSampleMimeType(mimeType)
            .setColorInfo(
                ColorInfo.Builder()
                    .setColorTransfer(colorTransfer)
                    .build(),
            )
            .build()
}
