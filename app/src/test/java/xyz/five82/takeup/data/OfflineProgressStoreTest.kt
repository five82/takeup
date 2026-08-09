package xyz.five82.takeup.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineProgressStoreTest {
    @Test
    fun `round trips pending progress`() {
        val pending = mapOf(
            42L to PendingProgress(915_000, 6_960_000),
            57L to PendingProgress(120_000, 1_440_000),
        )

        assertEquals(pending, decodePendingProgress(encodePendingProgress(pending)))
    }

    @Test
    fun `encodes an empty map as an empty object`() {
        assertEquals("{}", encodePendingProgress(emptyMap()))
        assertTrue(decodePendingProgress("{}").isEmpty())
    }

    @Test
    fun `treats missing state as nothing pending`() {
        assertTrue(decodePendingProgress("").isEmpty())
        assertTrue(decodePendingProgress("   ").isEmpty())
    }

    @Test
    fun `degrades to empty rather than throwing on malformed state`() {
        // This is only a retry queue, so corrupt state must never break startup.
        assertTrue(decodePendingProgress("not json").isEmpty())
        assertTrue(decodePendingProgress("[1,2,3]").isEmpty())
    }

    @Test
    fun `skips entries that are not a position and duration pair`() {
        val decoded = decodePendingProgress("""{"42":[915000,6960000],"57":[1],"bad":[1,2]}""")

        assertEquals(mapOf(42L to PendingProgress(915_000, 6_960_000)), decoded)
    }
}
