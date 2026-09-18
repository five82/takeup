package xyz.five82.takeup.ui

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
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
import xyz.five82.takeup.ui.downloads.DownloadsScreen
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
    val context = LocalContext.current
    val hasHinge = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)
    }
    // Fold panels may use different densities, which can recreate the activity
    // even when orientation and screen-size changes are handled in place.
    // Keep the existing phone navigation lifetime unchanged.
    val nav = if (hasHinge) {
        takeupViewModel("fold-navigation") { FoldNavigationModel() }.nav
    } else {
        remember { NavState() }
    }
    val navHaze = rememberHazeState()
    BackHandler(enabled = nav.stack.isNotEmpty()) { nav.pop() }

    BoxWithConstraints(Modifier.fillMaxSize().background(Stage)) {
        val layout = foldLayout(hasHinge, maxWidth.value, maxHeight.value)
        val density = LocalDensity.current
        val cutout = WindowInsets.displayCutout
        val direction = LocalLayoutDirection.current
        val sidebarOnRight = foldSidebarOnRight(
            layout, cutout.getLeft(density, direction), cutout.getRight(density, direction),
        )
        val contentPadding = PaddingValues.Absolute(
            left = if (sidebarOnRight) 0.dp else layout.sidebarWidth.dp,
            right = if (sidebarOnRight) layout.sidebarWidth.dp else 0.dp,
        )
        // Keep both children at stable call sites when the camera changes sides.
        // Only their placement changes, preserving the tab's remembered scroll.
        Box(
            Modifier.fillMaxSize().then(
                if (hasHinge) Modifier.windowInsetsPadding(
                    WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal),
                ) else Modifier,
            ),
        ) {
            if (layout.hasSidebar) {
                FoldSidebar(
                    nav, layout,
                    modifier = Modifier.align(
                        if (sidebarOnRight) AbsoluteAlignment.TopRight else AbsoluteAlignment.TopLeft,
                    ),
                )
            }
            // Home paints its art edge-to-edge and protects text locally. Other
            // tab roots retain their safe viewport.
            Box(
                Modifier.fillMaxSize()
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding)
                    .then(if (hasHinge) Modifier.clipToBounds() else Modifier)
                    .then(
                        if (hasHinge && nav.tab != Tab.Home) Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                        ) else Modifier,
                    )
                    .then(
                        if (!hasHinge || nav.stack.isEmpty()) Modifier.hazeSource(navHaze) else Modifier,
                    ),
            ) {
                CompositionLocalProvider(LocalFoldLayout provides layout) {
                    when (nav.tab) {
                        Tab.Home -> HomeScreen(repository, nav, active = nav.stack.isEmpty())
                        Tab.Movies -> LibraryScreen(repository, nav, "movies", active = nav.stack.isEmpty())
                        Tab.Tv -> LibraryScreen(repository, nav, "tv", active = nav.stack.isEmpty())
                        Tab.Shorts -> LibraryScreen(repository, nav, "shorts", active = nav.stack.isEmpty())
                        Tab.Browse -> BrowseScreen(repository, nav, active = nav.stack.isEmpty())
                    }
                }
            }
        }
        // Phones retain their root-only pill. Fold navigation is drawn above
        // the browsing stack below, so details do not cover it.
        if (layout == FoldLayout.Phone) TakeupNavPill(nav, navHaze, Modifier.align(Alignment.BottomCenter))
        nav.stack.forEachIndexed { index, screen ->
            val topmost = index == nav.stack.lastIndex
            key(index, screen) {
                // Each stacked screen owns its ViewModels. Without this,
                // viewModel() scopes to the activity and popping a screen
                // never clears them - the player would keep playing after
                // back. Clearing on dispose runs onCleared, which releases
                // the ExoPlayer and reports final progress.
                ScreenScoped {
                    val browsingOnFold = hasHinge && screen !is Screen.Player
                    // Keep the same content pane and camera-opposite sidebar
                    // throughout browsing. Playback alone covers the whole shell.
                    Box(
                        Modifier.fillMaxSize()
                            .then(
                                if (browsingOnFold) Modifier
                                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                                    .padding(contentPadding)
                                    .consumeWindowInsets(contentPadding)
                                    .clipToBounds()
                                else Modifier,
                            )
                            .background(Stage)
                            .then(
                                if (browsingOnFold && screen !is Screen.Detail) Modifier.windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                                ) else Modifier,
                            )
                            .then(
                                if (browsingOnFold && topmost) Modifier.hazeSource(navHaze) else Modifier,
                            ),
                    ) {
                        CompositionLocalProvider(
                            LocalFoldLayout provides if (browsingOnFold) layout else FoldLayout.Phone,
                        ) {
                            when (screen) {
                                is Screen.Detail -> DetailScreen(repository, nav, screen.itemId, topmost)
                                is Screen.Player -> PlayerScreen(repository, nav, screen.itemId)
                                is Screen.Search -> SearchScreen(
                                    repository, nav, screen.initialQuery, active = !hasHinge || topmost,
                                )
                                is Screen.Settings -> SettingsScreen(repository, nav)
                                is Screen.Downloads -> DownloadsScreen(repository, nav)
                                is Screen.Artwork -> ArtworkScreen(repository, nav, screen.itemId, screen.title)
                                is Screen.GenreGrid -> GenreGridScreen(repository, nav, screen)
                                is Screen.CollectionGrid -> CollectionGridScreen(repository, nav, screen)
                            }
                        }
                    }
                }
            }
        }
        if (showFoldNavPill(layout, nav.stack.lastOrNull(), WindowInsets.ime.getBottom(density) > 0)) {
            TakeupNavPill(nav, navHaze, Modifier.align(Alignment.BottomCenter))
        }
    }
}

private class FoldNavigationModel : ViewModel() {
    val nav = NavState()
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
internal fun TakeupNavPill(
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
