package xyz.five82.takeup.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldLayoutTest {
    @Test
    fun regularPhonesNeverOptInRegardlessOfWindowSize() {
        for ((width, height) in listOf(427f to 952f, 952f to 427f, 704f to 933f, 1600f to 1000f)) {
            val layout = foldLayout(false, width, height)
            assertEquals(FoldLayout.Phone, layout)
            assertEquals(200, layout.thumbWidth)
            assertFalse(layout.hasSidebar)
            assertFalse(layout.inlineHeroIdentity)
        }
    }

    @Test
    fun fold8PanelsAtEmulatorDensityUseTheFourApprovedCompositions() {
        val scale = 420f / 160f
        assertEquals(FoldLayout.CoverPortrait, foldLayout(true, 1248 / scale, 1972 / scale))
        assertEquals(FoldLayout.CoverLandscape, foldLayout(true, 1972 / scale, 1248 / scale))
        assertEquals(FoldLayout.InnerPortrait, foldLayout(true, 1848 / scale, 2448 / scale))
        assertEquals(FoldLayout.InnerLandscape, foldLayout(true, 2448 / scale, 1848 / scale))
        assertEquals(160, FoldLayout.CoverLandscape.sidebarWidth)
        assertEquals(180, FoldLayout.InnerPortrait.sidebarWidth)
        assertEquals(220, FoldLayout.InnerLandscape.sidebarWidth)
        assertEquals(180, FoldLayout.InnerLandscape.thumbWidth)
    }

    @Test
    fun coverLandscapeSidebarTracksThePhysicalCameraEdge() {
        assertTrue(foldSidebarOnRight(FoldLayout.CoverLandscape, 104, 0))
        assertFalse(foldSidebarOnRight(FoldLayout.CoverLandscape, 0, 104))
        assertFalse(foldSidebarOnRight(FoldLayout.CoverLandscape, 0, 0))
        for (layout in FoldLayout.entries.filter { it != FoldLayout.CoverLandscape }) {
            assertFalse(foldSidebarOnRight(layout, 104, 0))
        }
    }

    @Test
    fun narrowMultiWindowDoesNotSqueezeInASidebar() {
        assertEquals(FoldLayout.CoverPortrait, foldLayout(true, 360f, 800f))
        assertEquals(FoldLayout.CoverPortrait, foldLayout(true, 599f, 350f))
        assertEquals(FoldLayout.CoverLandscape, foldLayout(true, 600f, 350f))
    }

    @Test
    fun squareAndResizedWindowsHaveADeterministicLayout() {
        assertEquals(FoldLayout.InnerPortrait, foldLayout(true, 600f, 600f))
        assertEquals(FoldLayout.InnerLandscape, foldLayout(true, 800f, 600f))
        assertEquals(FoldLayout.CoverLandscape, foldLayout(true, 800f, 599f))
    }
}
