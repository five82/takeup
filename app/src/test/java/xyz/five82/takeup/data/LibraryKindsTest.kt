package xyz.five82.takeup.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The library id-to-kind map outlives the connection that learned it, so it has
 * to survive a round trip through DataStore intact.
 */
class LibraryKindsTest {

    @Test
    fun `survives a round trip`() {
        val kinds = mapOf(1L to "movies", 2L to "tv", 3L to "shorts")

        assertEquals(kinds, decodeLibraryKinds(encodeLibraryKinds(kinds)))
    }

    @Test
    fun `malformed or absent state decodes to empty rather than throwing`() {
        assertEquals(emptyMap<Long, String>(), decodeLibraryKinds(""))
        assertEquals(emptyMap<Long, String>(), decodeLibraryKinds("{\"oops\""))
    }
}
