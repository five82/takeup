package xyz.five82.takeup

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.five82.takeup.ui.ConnectScreen
import xyz.five82.takeup.ui.DetailsScreen
import xyz.five82.takeup.ui.HomeScreen
import xyz.five82.takeup.ui.LibraryListScreen
import xyz.five82.takeup.ui.LocalNetworkPermissionScreen
import xyz.five82.takeup.ui.MainUiState
import xyz.five82.takeup.ui.MainViewModel
import xyz.five82.takeup.ui.PlaybackScreen
import xyz.five82.takeup.ui.SeasonScreen
import xyz.five82.takeup.ui.ShowDetailsScreen
import xyz.five82.takeup.ui.theme.TakeupTheme

private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        val application = application as TakeupApplication
        MainViewModel.factory(application.container.loomRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TakeupTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PermissionAwareApp(viewModel)
                }
            }
        }
    }
}

@Composable
private fun PermissionAwareApp(viewModel: MainViewModel) {
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
        TakeupApp(viewModel)
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
private fun TakeupApp(viewModel: MainViewModel) {
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

    when (val current = state) {
        MainUiState.Starting -> StartingScreen()
        is MainUiState.Connect -> ConnectScreen(
            state = current,
            onServerUrlChanged = viewModel::updateServerUrl,
            onConnect = viewModel::connect,
        )
        is MainUiState.Home -> saveableStateHolder.SaveableStateProvider(
            key = "home:${current.serverUrl}",
        ) {
            HomeScreen(
                state = current,
                onRetry = viewModel::retryHome,
                onChangeServer = viewModel::changeServer,
                onShowMovies = viewModel::showMovies,
                onShowShows = viewModel::showShows,
                onItemSelected = viewModel::selectHomeItem,
            )
        }
        is MainUiState.Library -> saveableStateHolder.SaveableStateProvider(
            key = "library:${current.kind}:${current.serverUrl}",
        ) {
            LibraryListScreen(
                state = current,
                onRetry = viewModel::retryLibrary,
                onBack = viewModel::backToHome,
                onItemSelected = viewModel::selectLibraryItem,
            )
        }
        is MainUiState.ShowDetails -> saveableStateHolder.SaveableStateProvider(
            key = "show:${current.show.id}",
        ) {
            ShowDetailsScreen(
                state = current,
                onBack = viewModel::backFromShowDetails,
                onRetry = viewModel::retryShowDetails,
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
                onEpisodeSelected = viewModel::selectEpisode,
            )
        }
        is MainUiState.Details -> DetailsScreen(
            state = current,
            onBack = viewModel::backFromDetails,
            onRetry = viewModel::retryDetails,
            onPlay = viewModel::playDetails,
        )
        is MainUiState.Playback -> PlaybackScreen(
            state = current,
            onRetry = viewModel::retryPlayback,
            onBack = viewModel::backFromPlayback,
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

@Composable
private fun StartingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
