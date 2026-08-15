package xyz.five82.takeup.ui.player

import android.media.audiofx.DynamicsProcessing
import android.util.Log

private const val TAG = "DialogueBoost"

/**
 * Night-mode dynamic range compression: a single full-range multiband
 * compressor plus limiter riding on the player's audio session, so quiet
 * dialogue lifts without loud passages clipping. DynamicsProcessing needs
 * API 28+ (app minSdk is 31, so no version guard is needed), and effect
 * creation can still fail on some devices/routes - failures are logged and
 * degrade to a no-op rather than crashing playback.
 */
class DialogueBoost {
    private var effect: DynamicsProcessing? = null
    private var sessionId: Int = -1
    private var channelCount: Int = 0

    fun setEnabled(enabled: Boolean, sessionId: Int, channelCount: Int) {
        if (!enabled) {
            effect?.setEnabled(false)
            return
        }
        if (effect == null || this.sessionId != sessionId || this.channelCount != channelCount) {
            recreate(sessionId, channelCount)
        }
        effect?.setEnabled(true)
    }

    private fun recreate(sessionId: Int, channelCount: Int) {
        effect?.release()
        effect = null
        try {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                channelCount,
                /* preEqInUse = */ false, 0,
                /* mbcInUse = */ true, 1,
                /* postEqInUse = */ false, 0,
                /* limiterInUse = */ true,
            ).build()
            val dp = DynamicsProcessing(/* priority = */ 0, sessionId, config)

            val band = DynamicsProcessing.MbcBand(
                /* enabled = */ true,
                /* cutoffFrequency = */ 20000f,
                /* attackTime = */ 20f,
                /* releaseTime = */ 250f,
                /* ratio = */ 3f,
                /* threshold = */ -32f,
                /* kneeWidth = */ 6f,
                /* noiseGateThreshold = */ -90f,
                /* expanderRatio = */ 1f,
                /* preGain = */ 0f,
                /* postGain = */ 8f,
            )
            dp.setMbcBandAllChannelsTo(0, band)

            val limiter = DynamicsProcessing.Limiter(
                /* inUse = */ true, /* enabled = */ true, /* linkGroup = */ 0,
                /* attackTime = */ 1f, /* releaseTime = */ 60f,
                /* ratio = */ 10f, /* threshold = */ -1.5f, /* postGain = */ 0f,
            )
            dp.setLimiterAllChannelsTo(limiter)

            effect = dp
            this.sessionId = sessionId
            this.channelCount = channelCount
        } catch (e: RuntimeException) {
            // Includes UnsupportedOperationException: effect creation is not
            // guaranteed on every device/route, and this feature is a nicety.
            Log.w(TAG, "Dialogue boost effect unavailable", e)
            effect = null
        }
    }

    fun release() {
        effect?.release()
        effect = null
    }
}
