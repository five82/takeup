package xyz.five82.takeup.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import xyz.five82.takeup.R
import xyz.five82.takeup.data.PreparedPlayback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackScreen(
    state: MainUiState.Playback,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onSaveProgress: (itemId: Long, positionMs: Long, durationMs: Long) -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(state.item.title, maxLines = 1) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { contentPadding ->
        when {
            state.isLoading -> PlaybackLoading(contentPadding)
            state.error != null -> PlaybackError(
                contentPadding = contentPadding,
                message = state.error,
                onRetry = onRetry,
            )
            state.prepared != null -> VideoPlayer(
                contentPadding = contentPadding,
                playback = state.prepared,
                onSaveProgress = onSaveProgress,
            )
        }
    }
}

@Composable
private fun PlaybackLoading(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.loading_playback), color = Color.White)
        }
    }
}

@Composable
private fun PlaybackError(
    contentPadding: PaddingValues,
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.playback_failed, message),
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun VideoPlayer(
    contentPadding: PaddingValues,
    playback: PreparedPlayback,
    onSaveProgress: (itemId: Long, positionMs: Long, durationMs: Long) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnSaveProgress by rememberUpdatedState(onSaveProgress)
    var playerError by remember(playback.streamUrl) { mutableStateOf<String?>(null) }
    val player = remember(playback.streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            setHandleAudioBecomingNoisy(true)
        }
    }

    fun saveProgress() {
        val duration = player.duration.takeIf { it > 0 } ?: playback.durationMs
        if (duration > 0) {
            latestOnSaveProgress(
                playback.itemId,
                player.currentPosition.coerceAtLeast(0),
                duration,
            )
        }
    }

    LaunchedEffect(player, playback.streamUrl) {
        val mediaItem = MediaItem.Builder()
            .setUri(playback.streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(playback.title)
                    .build(),
            )
            .build()
        player.setMediaItem(mediaItem)
        if (playback.resumePositionMs > 0) {
            player.seekTo(playback.resumePositionMs)
        }
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(player) {
        while (isActive) {
            delay(15_000)
            if (player.isPlaying) saveProgress()
        }
    }

    DisposableEffect(player, lifecycleOwner) {
        val playerListener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) saveProgress()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) saveProgress()
            }

            override fun onPlayerError(error: PlaybackException) {
                playerError = buildString {
                    append(error.errorCodeName)
                    error.cause?.message?.takeIf { it.isNotBlank() }?.let {
                        append(": ")
                        append(it)
                    }
                }
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                saveProgress()
                player.pause()
            }
        }
        player.addListener(playerListener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            saveProgress()
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            player.removeListener(playerListener)
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    this.player = player
                    keepScreenOn = true
                    useController = true
                    controllerAutoShow = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )
        playerError?.let { error ->
            Text(
                text = stringResource(R.string.playback_failed, error),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Color(0xCC000000))
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
