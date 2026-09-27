package xyz.five82.takeup.ui.player

import android.media.audiofx.DynamicsProcessing
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DialogueBoostTest {
    @Test fun changingSessionOrChannelCountRecreatesTheEffect() {
        Mockito.mockConstruction(DynamicsProcessing::class.java).use { effects ->
            val boost = DialogueBoost()
            boost.setEnabled(true, 11, 2)
            boost.setEnabled(true, 11, 2)
            assertEquals(1, effects.constructed().size)
            Mockito.verify(effects.constructed()[0], Mockito.times(2)).setEnabled(true)

            boost.setEnabled(false, 11, 2)
            Mockito.verify(effects.constructed()[0]).setEnabled(false)
            boost.setEnabled(true, 12, 2)
            Mockito.verify(effects.constructed()[0]).release()
            boost.setEnabled(true, 12, 6)
            Mockito.verify(effects.constructed()[1]).release()
            assertEquals(3, effects.constructed().size)
            boost.release()
            Mockito.verify(effects.constructed()[2]).release()
            boost.release()
            Mockito.verify(effects.constructed()[2], Mockito.times(1)).release()
        }
    }

    @Test fun unavailableEffectDoesNotCrashPlaybackAndCanBeRetried() {
        Mockito.mockConstruction(DynamicsProcessing::class.java) { _, context ->
            if (context.count == 1) throw IllegalStateException("effect unavailable")
        }.use { effects ->
            val boost = DialogueBoost()
            boost.setEnabled(true, 11, 2)
            boost.setEnabled(false, 11, 2)
            boost.setEnabled(true, 11, 2)
            assertEquals(1, effects.constructed().size)
            boost.release()
        }
    }
}
