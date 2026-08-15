package xyz.five82.takeup.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Screens that stack above the tab roots. */
sealed interface Screen {
    data class Detail(val itemId: Long) : Screen
    data class Player(val itemId: Long) : Screen
    data class Search(val initialQuery: String = "") : Screen
    data object Settings : Screen
    data class Artwork(val itemId: Long, val title: String) : Screen
    data class GenreGrid(val genreId: Long, val title: String, val thread: Int) : Screen
    data class CollectionGrid(val slug: String, val title: String) : Screen
}

enum class Tab(val label: String, val library: String?) {
    Home("Home", null),
    Movies("Movies", "movies"),
    Tv("TV", "tv"),
    Shorts("Shorts", "shorts"),
    Browse("Browse", null),
}

/**
 * Hand-rolled navigation: five tab roots and one overlay stack. A handful of
 * destinations does not justify a navigation library. State does not survive
 * process death; on a LAN client that restarts to its home screen, that is
 * an acceptable trade.
 */
class NavState {
    var tab by mutableStateOf(Tab.Home)
    val stack = mutableStateListOf<Screen>()

    fun push(screen: Screen) {
        stack += screen
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    fun replaceTop(screen: Screen) {
        if (stack.isNotEmpty()) stack[stack.lastIndex] = screen else stack += screen
    }

    fun selectTab(target: Tab) {
        tab = target
        stack.clear()
    }
}
