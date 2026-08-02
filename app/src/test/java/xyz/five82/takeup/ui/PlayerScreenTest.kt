package xyz.five82.takeup.ui

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
