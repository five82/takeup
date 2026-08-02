package xyz.five82.takeup.ui

import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import xyz.five82.takeup.R
import xyz.five82.takeup.data.LoomItem
import xyz.five82.takeup.data.PreparedPlayback
import java.util.Locale

@Composable
internal fun PlaybackScreen(
    state: MainUiState.Playback,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onPlayNext: () -> Unit,
    onBackToSeason: () -> Unit,
    onSaveProgress: (itemId: Long, positionMs: Long, durationMs: Long) -> Unit,
) {
    BackHandler(onBack = onBack)
    ImmersiveSystemBars()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            state.isLoading -> PlaybackLoading()
            state.error != null -> PlaybackError(
                message = state.error,
                onRetry = onRetry,
            )
            state.prepared != null -> VideoPlayer(
                playback = state.prepared,
                nextEpisode = state.nextEpisode,
                canReturnToSeason = state.origin == BrowseOrigin.Season,
                onBack = onBack,
                onPlayNext = onPlayNext,
                onBackToSeason = onBackToSeason,
                onSaveProgress = onSaveProgress,
            )
        }
        if (state.prepared == null) {
            PlaybackHeader(title = state.item.title, onBack = onBack)
        }
    }
}

@Composable
private fun ImmersiveSystemBars() {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val window = activity?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            onDispose {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

@Composable
private fun PlaybackLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
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
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
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
    playback: PreparedPlayback,
    nextEpisode: LoomItem?,
    canReturnToSeason: Boolean,
    onBack: () -> Unit,
    onPlayNext: () -> Unit,
    onBackToSeason: () -> Unit,
    onSaveProgress: (itemId: Long, positionMs: Long, durationMs: Long) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnSaveProgress by rememberUpdatedState(onSaveProgress)
    var playerError by remember(playback.streamUrl) { mutableStateOf<String?>(null) }
    var cropToFill by rememberSaveable(playback.streamUrl) { mutableStateOf(false) }
    var controlsVisible by remember(playback.streamUrl) { mutableStateOf(true) }
    var hdr10Detected by remember(playback.streamUrl) { mutableStateOf(false) }
    var firstFrameRendered by remember(playback.streamUrl) { mutableStateOf(false) }
    var showHdrBadge by remember(playback.streamUrl) { mutableStateOf(false) }
    var playbackEnded by remember(playback.streamUrl) { mutableStateOf(false) }
    var selectedAudioLabel by remember(playback.streamUrl) { mutableStateOf<String?>(null) }
    var selectedSubtitleLabel by remember(playback.streamUrl) { mutableStateOf<String?>(null) }
    val resizeMode = if (cropToFill) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    } else {
        AspectRatioFrameLayout.RESIZE_MODE_FIT
    }
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

    LaunchedEffect(hdr10Detected, firstFrameRendered) {
        if (hdr10Detected && firstFrameRendered) {
            showHdrBadge = true
            delay(4_000)
            showHdrBadge = false
        }
    }

    DisposableEffect(player, lifecycleOwner) {
        val playerListener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    saveProgress()
                    playbackEnded = true
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) playbackEnded = false
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) saveProgress()
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackEnded = false
                playerError = buildString {
                    append(error.errorCodeName)
                    error.cause?.message?.takeIf { it.isNotBlank() }?.let {
                        append(": ")
                        append(it)
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                if (!hdr10Detected && tracks.hasSelectedHdr10Track()) {
                    hdr10Detected = true
                }
                selectedAudioLabel = tracks.selectedTrackLabel(C.TRACK_TYPE_AUDIO)
                selectedSubtitleLabel = tracks.selectedTrackLabel(C.TRACK_TYPE_TEXT)
            }

            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
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

    val audioText = selectedAudioLabel?.let { stringResource(R.string.audio_track, it) }
    val subtitleText = selectedSubtitleLabel?.let { stringResource(R.string.subtitle_track, it) }
    val supportingText = listOfNotNull(
        playback.contextTitle.takeIf { it.isNotBlank() },
        listOfNotNull(audioText, subtitleText).joinToString(" \u00B7 ").ifBlank { null },
    ).joinToString("  |  ")

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                    this.resizeMode = resizeMode
                    keepScreenOn = true
                    useController = true
                    controllerAutoShow = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setShowSubtitleButton(true)
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == View.VISIBLE
                        },
                    )
                }
            },
            update = {
                it.player = player
                it.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize(),
        )
        playerError?.let { error ->
            PlaybackFailureOverlay(
                message = error,
                onRetry = {
                    playerError = null
                    player.prepare()
                    player.play()
                },
                onBack = onBack,
            )
        }
        if (controlsVisible || playbackEnded || playerError != null) {
            PlaybackHeader(
                title = playback.title,
                supportingText = supportingText,
                onBack = onBack,
                actionText = stringResource(
                    if (cropToFill) R.string.fit_video else R.string.crop_video,
                ),
                onAction = { cropToFill = !cropToFill },
            )
        }
        if (playbackEnded) {
            EndOfPlaybackOverlay(
                itemTitle = playback.title,
                nextEpisode = nextEpisode,
                canReturnToSeason = canReturnToSeason,
                onPlayNext = onPlayNext,
                onReplay = {
                    playbackEnded = false
                    playerError = null
                    player.seekTo(0)
                    player.play()
                },
                onBack = if (canReturnToSeason) onBackToSeason else onBack,
            )
        }
        if (showHdrBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                        ),
                    )
                    .padding(top = 56.dp, end = 16.dp),
            ) {
                Surface(
                    color = Color(0xCC000000),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = stringResource(R.string.hdr_badge),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackFailureOverlay(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Color(0xF21A181C),
            shape = MaterialTheme.shapes.large,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.playback_failed_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = message,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.retry))
                    }
                    TextButton(
                        onClick = onBack,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) {
                        Text(stringResource(R.string.back_to_details))
                    }
                }
            }
        }
    }
}

@Composable
private fun EndOfPlaybackOverlay(
    itemTitle: String,
    nextEpisode: LoomItem?,
    canReturnToSeason: Boolean,
    onPlayNext: () -> Unit,
    onReplay: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Color(0xF21A181C),
            shape = MaterialTheme.shapes.large,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.finished),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = itemTitle,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (nextEpisode != null) {
                    Text(
                        text = listOfNotNull(
                            nextEpisode.episodeLabel(),
                            nextEpisode.title,
                        ).joinToString(" \u00B7 "),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Button(
                        onClick = onPlayNext,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.play_next))
                    }
                }
                OutlinedButton(
                    onClick = onReplay,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.replay))
                }
                TextButton(
                    onClick = onBack,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                ) {
                    Text(
                        stringResource(
                            if (canReturnToSeason) {
                                R.string.back_to_season
                            } else {
                                R.string.back_to_details
                            },
                        ),
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal fun isHdr10Track(format: Format): Boolean =
    format.sampleMimeType != MimeTypes.VIDEO_DOLBY_VISION &&
        format.colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084

private fun Tracks.hasSelectedHdr10Track(): Boolean = groups.any { group ->
    group.type == C.TRACK_TYPE_VIDEO &&
        (0 until group.length).any { trackIndex ->
            group.isTrackSelected(trackIndex) && isHdr10Track(group.getTrackFormat(trackIndex))
        }
}

private fun Tracks.selectedTrackLabel(trackType: Int): String? {
    groups.forEach { group ->
        if (group.type != trackType) return@forEach
        for (trackIndex in 0 until group.length) {
            if (group.isTrackSelected(trackIndex)) {
                return trackLabel(group.getTrackFormat(trackIndex))
            }
        }
    }
    return null
}

private fun trackLabel(format: Format): String =
    trackLabel(format.label, format.language)

internal fun trackLabel(
    label: String?,
    language: String?,
    locale: Locale = Locale.getDefault(),
): String {
    label?.takeIf { it.isNotBlank() }?.let { return it }
    language
        ?.takeIf { it.isNotBlank() && it != "und" }
        ?.let { languageTag ->
            Locale.forLanguageTag(languageTag).getDisplayLanguage(locale)
                .takeIf { it.isNotBlank() }
                ?.let { return it }
        }
    return "Default"
}

@Composable
private fun PlaybackHeader(
    title: String,
    onBack: () -> Unit,
    supportingText: String = "",
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x99000000))
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            )
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavigationBackButton(onClick = onBack, tint = Color.White)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            if (supportingText.isNotBlank()) {
                Text(
                    text = supportingText,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        if (actionText != null && onAction != null) {
            TextButton(
                onClick = onAction,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            ) {
                Text(actionText)
            }
        }
    }
}
