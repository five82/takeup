package xyz.five82.takeup.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.five82.takeup.api.CELLULAR_BLOCKED_MESSAGE
import java.io.IOException

class CellularPolicyTest {

    @Test
    fun `cellular is blocked only while the setting is off`() {
        assertTrue(cellularBlocked(onCellular = true, allowCellular = false))
        assertFalse(cellularBlocked(onCellular = true, allowCellular = true))
    }

    @Test
    fun `wifi is never blocked`() {
        assertFalse(cellularBlocked(onCellular = false, allowCellular = false))
        assertFalse(cellularBlocked(onCellular = false, allowCellular = true))
    }

    @Test
    fun `a blocked request reads as being offline`() {
        // What makes the app fall back to downloads rather than show an error.
        assertTrue(isOfflineError(IOException(CELLULAR_BLOCKED_MESSAGE)))
    }
}
