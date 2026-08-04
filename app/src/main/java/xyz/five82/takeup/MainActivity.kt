@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
)

package xyz.five82.takeup

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.five82.takeup.ui.ArtworkScreen
import xyz.five82.takeup.ui.SettingsScreen
import xyz.five82.takeup.ui.DetailsScreen
import xyz.five82.takeup.ui.GenreHubScreen
import xyz.five82.takeup.ui.GenreLandingScreen
import xyz.five82.takeup.ui.HomeScreen
import xyz.five82.takeup.ui.LibraryListScreen
import xyz.five82.takeup.ui.LocalNetworkPermissionScreen
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import xyz.five82.takeup.ui.LocalNavAnimatedContentScope
import xyz.five82.takeup.ui.LocalSharedTransitionScope
import xyz.five82.takeup.ui.MainUiState
import xyz.five82.takeup.ui.MainViewModel
import xyz.five82.takeup.ui.NavigationToolbar
import xyz.five82.takeup.ui.PlaybackScreen
import xyz.five82.takeup.ui.topDestination
import xyz.five82.takeup.ui.SearchScreen
import xyz.five82.takeup.ui.SeasonScreen
import xyz.five82.takeup.ui.ShowDetailsScreen
import xyz.five82.takeup.ui.seedArtworkUrl
import xyz.five82.takeup.ui.theme.TakeupTheme
import xyz.five82.takeup.ui.theme.rememberSeedColor

private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        val application = application as TakeupApplication
        MainViewModel.factory(application.container.loomRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            // The Home hero carousel reports its focused item so the theme can
            // follow swipes; every other screen derives its seed from state.
            var heroSeedUrl by remember { mutableStateOf<String?>(null) }
            val seedUrl = if (state is MainUiState.Home) {
                heroSeedUrl ?: seedArtworkUrl(state)
            } else {
                seedArtworkUrl(state)
            }
            TakeupTheme(seedColor = rememberSeedColor(seedUrl)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PermissionAwareApp(
                        viewModel = viewModel,
                        onHeroSeedUrlChanged = { heroSeedUrl = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionAwareApp(
    viewModel: MainViewModel,
    onHeroSeedUrlChanged: (String?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val requiresLocalNetworkPermission = Build.VERSION.SDK_INT >= 37
    var permissionGranted by remember {
        mutableStateOf(
            !requiresLocalNetworkPermission ||
                ContextCompat.checkSelfPermission(
                    context,
                    LOCAL_NETWORK_PERMISSION,
                ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        permissionDenied = !granted
    }

    DisposableEffect(context, lifecycleOwner, requiresLocalNetworkPermission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && requiresLocalNetworkPermission) {
                permissionGranted = ContextCompat.checkSelfPermission(
                    context,
                    LOCAL_NETWORK_PERMISSION,
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (permissionGranted) {
        LaunchedEffect(viewModel) { viewModel.start() }
        TakeupApp(viewModel, onHeroSeedUrlChanged)
    } else {
        LocalNetworkPermissionScreen(
            wasDenied = permissionDenied,
            onGrantAccess = {
                if (permissionDenied) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        },
                    )
                } else {
                    permissionLauncher.launch(LOCAL_NETWORK_PERMISSION)
                }
            },
        )
    }
}

@Composable
private fun TakeupApp(
    viewModel: MainViewModel,
    onHeroSeedUrlChanged: (String?) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val isPlaying = state is MainUiState.Playback
    val saveableStateHolder = rememberSaveableStateHolder()

    LaunchedEffect(activity, isPlaying) {
        activity?.requestedOrientation = if (isPlaying) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val motionScheme = MaterialTheme.motionScheme
    val hazeState = rememberHazeState()
    // Hides the toolbar on scroll-down and springs it back on scroll-up; the
    // top-level screens feed it through their nestedScroll modifiers.
    val toolbarScrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
        exitDirection = FloatingToolbarExitDirection.Bottom,
    )
    val topScreenModifier = Modifier.nestedScroll(toolbarScrollBehavior)
    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
    Box(modifier = Modifier.fillMaxSize()) {
    AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = {
            (
                fadeIn(motionScheme.defaultEffectsSpec()) +
                    scaleIn(motionScheme.defaultSpatialSpec(), initialScale = 0.98f)
                ) togetherWith (
                fadeOut(motionScheme.fastEffectsSpec()) +
                    scaleOut(motionScheme.fastSpatialSpec(), targetScale = 1.01f)
                )
        },
        modifier = Modifier.hazeSource(hazeState),
        label = "screen",
    ) { animatedState ->
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
        when (val current = animatedState) {
        MainUiState.Starting -> StartingScreen()
        is MainUiState.Connect -> SettingsScreen(
            state = current,
            onServerUrlChanged = viewModel::updateServerUrl,
            onConnect = viewModel::connect,
            onBack = viewModel::backFromSettings,
        )
        is MainUiState.Home -> saveableStateHolder.SaveableStateProvider(
            key = "home:${current.serverUrl}",
        ) {
            HomeScreen(
                state = current,
                modifier = topScreenModifier,
                onRetry = viewModel::retryHome,
                onOpenSettings = viewModel::openSettings,
                onShowMovies = viewModel::showMovies,
                onShowShows = viewModel::showShows,
                onItemSelected = viewModel::selectHomeItem,
                onPlayItem = viewModel::playHomeItem,
                onGenreSelected = viewModel::openGenre,
                onOpenGenreHub = viewModel::openGenreHub,
                onHeroSeedUrlChanged = onHeroSeedUrlChanged,
            )
        }
        is MainUiState.Library -> saveableStateHolder.SaveableStateProvider(
            key = "library:${current.kind}:${current.serverUrl}",
        ) {
            LibraryListScreen(
                state = current,
                modifier = topScreenModifier,
                onRetry = viewModel::retryLibrary,
                onBack = viewModel::backToHome,
                onGenreSelected = viewModel::selectGenre,
                onItemSelected = viewModel::selectLibraryItem,
            )
        }
        is MainUiState.Search -> SearchScreen(
            state = current,
            modifier = topScreenModifier,
            onQueryChanged = viewModel::updateSearchQuery,
            onRetry = viewModel::retrySearch,
            onBack = viewModel::backFromSearch,
            onItemSelected = viewModel::selectSearchItem,
            onGenreSelected = viewModel::openGenre,
        )
        is MainUiState.GenreHub -> GenreHubScreen(
            state = current,
            onBack = viewModel::backFromGenreHub,
            onRetry = viewModel::retryGenreHub,
            onGenreSelected = viewModel::openGenre,
        )
        is MainUiState.GenreLanding -> saveableStateHolder.SaveableStateProvider(
            key = "genre:${current.genre.id}",
        ) {
            GenreLandingScreen(
                state = current,
                onBack = viewModel::backFromGenreLanding,
                onRetry = viewModel::retryGenreLanding,
                onItemSelected = viewModel::selectGenreItem,
                onPlayItem = viewModel::playGenreItem,
            )
        }
        is MainUiState.ShowDetails -> saveableStateHolder.SaveableStateProvider(
            key = "show:${current.show.id}",
        ) {
            ShowDetailsScreen(
                state = current,
                onBack = viewModel::backFromShowDetails,
                onRetry = viewModel::retryShowDetails,
                onEditArtwork = viewModel::editArtwork,
                onSeasonSelected = viewModel::selectSeason,
            )
        }
        is MainUiState.Season -> saveableStateHolder.SaveableStateProvider(
            key = "season:${current.season.id}",
        ) {
            SeasonScreen(
                state = current,
                onBack = viewModel::backFromSeason,
                onRetry = viewModel::retrySeason,
                onEditArtwork = viewModel::editArtwork,
                onEpisodeSelected = viewModel::selectEpisode,
            )
        }
        is MainUiState.Details -> DetailsScreen(
            state = current,
            onBack = viewModel::backFromDetails,
            onRetry = viewModel::retryDetails,
            onEditArtwork = viewModel::editArtwork,
            onPlay = viewModel::playDetails,
            onGenreSelected = viewModel::openGenre,
        )
        is MainUiState.Artwork -> ArtworkScreen(
            state = current,
            onBack = viewModel::backFromArtwork,
            onKindSelected = viewModel::selectArtworkKind,
            onOptionSelected = viewModel::selectArtwork,
            onReset = viewModel::resetArtwork,
            onRetry = viewModel::retryArtwork,
        )
        is MainUiState.Playback -> PlaybackScreen(
            state = current,
            onRetry = viewModel::retryPlayback,
            onBack = viewModel::backFromPlayback,
            onPlayNext = viewModel::playNextEpisode,
            onBackToSeason = viewModel::backToSeasonFromPlayback,
            onSaveProgress = { itemId, positionMs, durationMs ->
                viewModel.saveProgress(
                    serverUrl = current.serverUrl,
                    itemId = itemId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                )
            },
        )
        }
        }
    }
    AnimatedVisibility(
        visible = state.topDestination() != null,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        enter = fadeIn(motionScheme.defaultEffectsSpec()) +
            slideInVertically(motionScheme.defaultSpatialSpec()) { it * 2 },
        exit = fadeOut(motionScheme.fastEffectsSpec()) +
            slideOutVertically(motionScheme.fastSpatialSpec()) { it * 2 },
        label = "toolbar",
    ) {
        NavigationToolbar(
            current = state.topDestination(),
            onSelect = viewModel::selectTopDestination,
            hazeState = hazeState,
            scrollBehavior = toolbarScrollBehavior,
        )
    }
    }
    }
    }
}

@Composable
private fun StartingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.app_name),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.displaySmallEmphasized,
            )
            Spacer(Modifier.height(24.dp))
            LoadingIndicator()
        }
    }
}
