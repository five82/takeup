package xyz.five82.takeup.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldNavigationTest {
    @Test
    fun phoneAndCoverPillsAreRootOnlyAndSidebarLayoutsNeverShowAPill() {
        val stacked = listOf(
            Screen.Detail(34), Screen.Search(), Screen.Settings, Screen.Downloads,
            Screen.Artwork(34, "Title"), Screen.GenreGrid(1, "Drama"), Screen.CollectionGrid("films", "Films"),
            Screen.Player(34),
        )
        for (layout in FoldLayout.entries) {
            assertEquals(!layout.hasSidebar, showNavPill(layout, null))
            for (screen in stacked) assertFalse(showNavPill(layout, screen))
        }
    }

    @Test
    fun coverPillReturnsAfterPoppingDetail() {
        val nav = NavState()
        assertTrue(showNavPill(FoldLayout.CoverPortrait, nav.stack.lastOrNull()))
        nav.push(Screen.Detail(34))
        assertFalse(showNavPill(FoldLayout.CoverPortrait, nav.stack.lastOrNull()))
        nav.pop()
        assertTrue(showNavPill(FoldLayout.CoverPortrait, nav.stack.lastOrNull()))
    }

    @Test
    fun persistentSidebarDoesNotStackTheCurrentUtilityAgain() {
        val nav = NavState()
        val detail = Screen.Detail(34)
        nav.push(detail)
        for (utility in listOf(Screen.Search(), Screen.Downloads, Screen.Settings)) {
            nav.openSidebarScreen(utility)
            nav.openSidebarScreen(utility)
            assertEquals(listOf(detail, utility), nav.stack.toList())
            nav.pop()
            assertEquals(listOf(detail), nav.stack.toList())
        }
    }

    @Test
    fun sidebarTabLeavesTheDetailStackAndSearchCanClearAPersonQuery() {
        val nav = NavState()
        nav.push(Screen.Detail(34))
        nav.push(Screen.Search("Director"))
        nav.openSidebarScreen(Screen.Search())
        assertEquals(Screen.Search(), nav.stack.last())
        nav.selectTab(Tab.Movies)
        assertEquals(Tab.Movies, nav.tab)
        assertTrue(nav.stack.isEmpty())
    }
}
