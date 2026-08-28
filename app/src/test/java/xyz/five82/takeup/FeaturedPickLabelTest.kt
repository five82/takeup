package xyz.five82.takeup

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.five82.takeup.ui.home.featuredPickLabel

class FeaturedPickLabelTest {
    @Test
    fun featuredPickLabelChangesAtSixAmAndSixPm() {
        assertEquals("Tonight's Pick", featuredPickLabel(0))
        assertEquals("Tonight's Pick", featuredPickLabel(5))
        assertEquals("Today's Pick", featuredPickLabel(6))
        assertEquals("Today's Pick", featuredPickLabel(17))
        assertEquals("Tonight's Pick", featuredPickLabel(18))
        assertEquals("Tonight's Pick", featuredPickLabel(23))
    }
}
