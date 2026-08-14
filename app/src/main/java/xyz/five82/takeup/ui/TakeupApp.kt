package xyz.five82.takeup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.ui.artwork.ArtworkScreen
import xyz.five82.takeup.ui.browse.BrowseScreen
import xyz.five82.takeup.ui.browse.CollectionGridScreen
import xyz.five82.takeup.ui.browse.GenreGridScreen
import xyz.five82.takeup.ui.components.Selvedge
import xyz.five82.takeup.ui.detail.DetailScreen
import xyz.five82.takeup.ui.home.HomeScreen
import xyz.five82.takeup.ui.library.LibraryScreen
import xyz.five82.takeup.ui.onboarding.OnboardingScreen
import xyz.five82.takeup.ui.player.PlayerScreen
import xyz.five82.takeup.ui.search.SearchScreen
import xyz.five82.takeup.ui.settings.SettingsScreen
import xyz.five82.takeup.ui.theme.Amber
import xyz.five82.takeup.ui.theme.Ember
import xyz.five82.takeup.ui.theme.Faint
import xyz.five82.takeup.ui.theme.Ink
import xyz.five82.takeup.ui.theme.Stage
import xyz.five82.takeup.ui.theme.Surface1
import xyz.five82.takeup.ui.theme.Teal
import xyz.five82.takeup.ui.theme.Violet

@Composable
fun TakeupApp(repository: LoomRepository) {
    val server by repository.server.collectAsStateWithLifecycle()
    when {
        !server.loaded -> Box(Modifier.fillMaxSize().background(Stage))
        server.address.isNullOrBlank() -> OnboardingScreen(repository)
        else -> MainScaffold(repository)
    }
}

@Composable
private fun MainScaffold(repository: LoomRepository) {
    val nav = remember { NavState() }
    val navHaze = rememberHazeState()
    BackHandler(enabled = nav.stack.isNotEmpty()) { nav.pop() }

    Box(Modifier.fillMaxSize().background(Stage)) {
        // Tab roots stay in composition beneath the overlay stack so their
        // scroll positions survive a trip into detail or the player. The nav
        // pill floats over them; scrollables clear it with navPillClearance.
        Box(Modifier.fillMaxSize().hazeSource(navHaze)) {
            when (nav.tab) {
                Tab.Home -> HomeScreen(repository, nav, active = nav.stack.isEmpty())
                Tab.Movies -> LibraryScreen(repository, nav, "movies", active = nav.stack.isEmpty())
                Tab.Tv -> LibraryScreen(repository, nav, "tv", active = nav.stack.isEmpty())
                Tab.Shorts -> LibraryScreen(repository, nav, "shorts", active = nav.stack.isEmpty())
                Tab.Browse -> BrowseScreen(repository, nav, active = nav.stack.isEmpty())
            }
        }
        TakeupNavPill(nav, navHaze, Modifier.align(Alignment.BottomCenter))
        nav.stack.forEachIndexed { index, screen ->
            val topmost = index == nav.stack.lastIndex
            key(index, screen) {
                // Each stacked screen owns its ViewModels. Without this,
                // viewModel() scopes to the activity and popping a screen
                // never clears them - the player would keep playing after
                // back. Clearing on dispose runs onCleared, which releases
                // the ExoPlayer and reports final progress.
                ScreenScoped {
                    Box(Modifier.fillMaxSize().background(Stage)) {
                        when (screen) {
                            is Screen.Detail -> DetailScreen(repository, nav, screen.itemId, topmost)
                            is Screen.Player -> PlayerScreen(repository, nav, screen.itemId)
                            is Screen.Search -> SearchScreen(repository, nav, screen.initialQuery)
                            is Screen.Settings -> SettingsScreen(repository, nav)
                            is Screen.Artwork -> ArtworkScreen(repository, nav, screen.itemId, screen.title)
                            is Screen.GenreGrid -> GenreGridScreen(repository, nav, screen)
                            is Screen.CollectionGrid -> CollectionGridScreen(repository, nav, screen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenScoped(content: @Composable () -> Unit) {
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(Unit) {
        onDispose { owner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

/** Floating frosted pill; the active tab shows its thread beneath the icon. */
@Composable
private fun TakeupNavPill(
    nav: NavState,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(50))
            .hazeEffect(hazeState) {
                backgroundColor = Surface1
                blurRadius = 28.dp
                tints = listOf(HazeTint(Surface1.copy(alpha = 0.78f)))
                noiseFactor = 0.06f
            }
            .border(1.dp, Ink.copy(alpha = 0.20f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (tab in Tab.entries) {
            val selected = nav.tab == tab
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { nav.selectTab(tab) }
                    .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
                    .padding(horizontal = 10.dp),
            ) {
                Icon(
                    when (tab) {
                        Tab.Home -> Icons.Filled.Home
                        Tab.Movies -> MovieIcon
                        Tab.Tv -> TvIcon
                        Tab.Shorts -> TheatersIcon
                        Tab.Browse -> ExploreIcon
                    },
                    contentDescription = tab.label,
                    tint = if (selected) Ink else Faint,
                    modifier = Modifier.size(28.dp),
                )
                // The active marker is the tab's thread; Home gets the whole selvedge.
                Box(
                    Modifier.padding(top = 4.dp).size(width = 18.dp, height = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        when (tab) {
                            Tab.Home -> Selvedge(Modifier.width(18.dp), height = 3f)
                            Tab.Movies -> Dot(Ember)
                            Tab.Tv -> Dot(Teal)
                            Tab.Shorts -> Dot(Amber)
                            Tab.Browse -> Dot(Violet)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dot(color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(5.dp).clip(CircleShape).background(color))
}
