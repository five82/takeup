@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)
@file:androidx.annotation.OptIn(
    markerClass = [
        androidx.media3.common.util.ExperimentalApi::class,
        androidx.media3.common.util.UnstableApi::class,
    ],
)

package xyz.five82.takeup.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
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
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.compose.material3.indicator.PositionAndDurationText
import androidx.media3.ui.compose.material3.indicator.ProgressSlider
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberSeekBackButtonState
import androidx.media3.ui.compose.state.rememberSeekForwardButtonState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import xyz.five82.takeup.R
import xyz.five82.takeup.data.LoomItem
import xyz.five82.takeup.data.PreparedPlayback
import xyz.five82.takeup.ui.theme.OverlayPillColor
import xyz.five82.takeup.ui.theme.playerBottomScrim
import xyz.five82.takeup.ui.theme.topScrim
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
            LoadingIndicator()
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
            Button(
                onClick = onRetry,
                shapes = ButtonDefaults.shapes(),
            ) {
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
    var controlsInteraction by remember(playback.streamUrl) { mutableIntStateOf(0) }
    var isPlaying by remember(playback.streamUrl) { mutableStateOf(false) }
    var isBuffering by remember(playback.streamUrl) { mutableStateOf(true) }
    var hdr10Detected by remember(playback.streamUrl) { mutableStateOf(false) }
    var firstFrameRendered by remember(playback.streamUrl) { mutableStateOf(false) }
    var showHdrBadge by remember(playback.streamUrl) { mutableStateOf(false) }
    var playbackEnded by remember(playback.streamUrl) { mutableStateOf(false) }
    var selectedAudioLabel by remember(playback.streamUrl) { mutableStateOf<String?>(null) }
    var selectedSubtitleLabel by remember(playback.streamUrl) { mutableStateOf<String?>(null) }
    var currentTracks by remember(playback.streamUrl) { mutableStateOf(Tracks.EMPTY) }
    var showPlaybackOptions by remember(playback.streamUrl) { mutableStateOf(false) }
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
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED) {
                    saveProgress()
                    playbackEnded = true
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) playbackEnded = false
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
                currentTracks = tracks
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
    val onControlsInteraction: () -> Unit = {
        controlsVisible = true
        controlsInteraction = controlsInteraction + 1
    }

    LaunchedEffect(
        controlsVisible,
        controlsInteraction,
        isPlaying,
        playbackEnded,
        playerError,
        showPlaybackOptions,
    ) {
        if (
            controlsVisible && isPlaying && !playbackEnded &&
            playerError == null && !showPlaybackOptions
        ) {
            delay(4_000)
            controlsVisible = false
        }
    }

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
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            update = {
                it.player = player
                it.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
                .pointerInput(playback.streamUrl, playbackEnded, playerError) {
                    detectTapGestures {
                        if (!playbackEnded && playerError == null) {
                            controlsVisible = !controlsVisible
                            controlsInteraction++
                        }
                    }
                },
        )
        if (isBuffering && !controlsVisible && playerError == null) {
            ContainedLoadingIndicator(modifier = Modifier.align(Alignment.Center))
        }
        AnimatedVisibility(
            visible = controlsVisible && !playbackEnded && playerError == null,
            modifier = Modifier.zIndex(1f),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            PlaybackControls(
                player = player,
                title = playback.title,
                supportingText = supportingText,
                cropToFill = cropToFill,
                isBuffering = isBuffering,
                onBack = onBack,
                onToggleCrop = {
                    cropToFill = !cropToFill
                    onControlsInteraction()
                },
                onShowOptions = {
                    showPlaybackOptions = true
                    onControlsInteraction()
                },
                onInteraction = onControlsInteraction,
            )
        }
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
        if (showPlaybackOptions) {
            PlaybackOptionsSheet(
                tracks = currentTracks,
                onDismiss = { showPlaybackOptions = false },
                onSelectTrack = { group, trackIndex ->
                    showPlaybackOptions = false
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .clearOverridesOfType(group.type)
                        .setTrackTypeDisabled(group.type, false)
                        .setOverrideForType(
                            TrackSelectionOverride(group.mediaTrackGroup, trackIndex),
                        )
                        .build()
                },
                onDisableSubtitles = {
                    showPlaybackOptions = false
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                },
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
                    color = OverlayPillColor,
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
private fun PlaybackControls(
    player: Player,
    title: String,
    supportingText: String,
    cropToFill: Boolean,
    isBuffering: Boolean,
    onBack: () -> Unit,
    onToggleCrop: () -> Unit,
    onShowOptions: () -> Unit,
    onInteraction: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        PlaybackHeader(
            title = title,
            supportingText = supportingText,
            onBack = onBack,
            cropToFill = cropToFill,
            onToggleCrop = onToggleCrop,
            onOptions = onShowOptions,
        )
        if (isBuffering) {
            ContainedLoadingIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            PlaybackButtonGroup(
                player = player,
                onInteraction = onInteraction,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(playerBottomScrim())
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                    ),
                )
                .padding(start = 20.dp, top = 32.dp, end = 20.dp, bottom = 12.dp),
        ) {
            ProgressSlider(
                player = player,
                modifier = Modifier.fillMaxWidth(),
                onValueChange = { onInteraction() },
                onValueChangeFinished = onInteraction,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                ProvideTextStyle(MaterialTheme.typography.labelLargeEmphasized) {
                    PositionAndDurationText(
                        player = player,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackButtonGroup(
    player: Player,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playPauseState = rememberPlayPauseButtonState(player)
    val seekBackState = rememberSeekBackButtonState(player)
    val seekForwardState = rememberSeekForwardButtonState(player)
    val mediumShapes = IconButtonDefaults.shapes(
        shape = IconButtonDefaults.mediumRoundShape,
        pressedShape = IconButtonDefaults.mediumPressedShape,
    )
    val extraLargeShapes = IconButtonDefaults.shapes(
        shape = IconButtonDefaults.extraLargeRoundShape,
        pressedShape = IconButtonDefaults.extraLargePressedShape,
    )

    ButtonGroup(
        overflowIndicator = {},
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        customItem(
            buttonGroupContent = {
                FilledTonalIconButton(
                    onClick = {
                        seekBackState.onClick()
                        onInteraction()
                    },
                    shapes = mediumShapes,
                    modifier = Modifier.size(64.dp),
                    enabled = seekBackState.isEnabled,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_seek_back_10),
                        contentDescription = stringResource(R.string.seek_back_10),
                        modifier = Modifier.size(28.dp),
                    )
                }
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                FilledIconButton(
                    onClick = {
                        playPauseState.onClick()
                        onInteraction()
                    },
                    shapes = extraLargeShapes,
                    modifier = Modifier.size(96.dp),
                    enabled = playPauseState.isEnabled,
                ) {
                    Icon(
                        painter = painterResource(
                            if (playPauseState.showPlay) R.drawable.ic_play else R.drawable.ic_pause,
                        ),
                        contentDescription = stringResource(
                            if (playPauseState.showPlay) R.string.play else R.string.pause,
                        ),
                        modifier = Modifier.size(40.dp),
                    )
                }
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                FilledTonalIconButton(
                    onClick = {
                        seekForwardState.onClick()
                        onInteraction()
                    },
                    shapes = mediumShapes,
                    modifier = Modifier.size(64.dp),
                    enabled = seekForwardState.isEnabled,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_seek_forward_10),
                        contentDescription = stringResource(R.string.seek_forward_10),
                        modifier = Modifier.size(28.dp),
                    )
                }
            },
            menuContent = {},
        )
    }
}

private data class PlaybackTrackOption(
    val group: Tracks.Group,
    val trackIndex: Int,
    val label: String,
    val selected: Boolean,
)

private fun Tracks.optionsFor(trackType: Int): List<PlaybackTrackOption> = groups
    .asSequence()
    .filter { it.type == trackType }
    .flatMap { group ->
        (0 until group.length).asSequence()
            .filter(group::isTrackSupported)
            .map { trackIndex ->
                PlaybackTrackOption(
                    group = group,
                    trackIndex = trackIndex,
                    label = trackLabel(group.getTrackFormat(trackIndex)),
                    selected = group.isTrackSelected(trackIndex),
                )
            }
    }
    .toList()

@Composable
private fun PlaybackOptionsSheet(
    tracks: Tracks,
    onDismiss: () -> Unit,
    onSelectTrack: (Tracks.Group, Int) -> Unit,
    onDisableSubtitles: () -> Unit,
) {
    val audioOptions = tracks.optionsFor(C.TRACK_TYPE_AUDIO)
    val subtitleOptions = tracks.optionsFor(C.TRACK_TYPE_TEXT)
    val sheetState = rememberBottomSheetState(
        SheetValue.Hidden,
        setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.zIndex(2f),
        sheetState = sheetState,
        sheetGesturesEnabled = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.playback_options),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            if (audioOptions.isNotEmpty()) {
                PlaybackOptionsHeading(stringResource(R.string.audio))
                PlaybackTrackGroup(
                    audioOptions.map { option ->
                        PlaybackTrackChoice(
                            label = option.label,
                            selected = option.selected,
                            onClick = { onSelectTrack(option.group, option.trackIndex) },
                        )
                    },
                )
            }
            PlaybackOptionsHeading(stringResource(R.string.subtitles))
            PlaybackTrackGroup(
                listOf(
                    PlaybackTrackChoice(
                        label = stringResource(R.string.off),
                        selected = !tracks.isTypeSelected(C.TRACK_TYPE_TEXT),
                        onClick = onDisableSubtitles,
                    ),
                ) + subtitleOptions.map { option ->
                    PlaybackTrackChoice(
                        label = option.label,
                        selected = option.selected,
                        onClick = { onSelectTrack(option.group, option.trackIndex) },
                    )
                },
            )
        }
    }
}

@Composable
private fun PlaybackOptionsHeading(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleMediumEmphasized,
    )
}

private data class PlaybackTrackChoice(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun PlaybackTrackGroup(choices: List<PlaybackTrackChoice>) {
    choices.forEachIndexed { index, choice ->
        SegmentedListItem(
            onClick = choice.onClick,
            shapes = ListItemDefaults.segmentedShapes(index = index, count = choices.size),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = if (index == choices.size - 1) 0.dp else ListItemDefaults.SegmentedGap,
                ),
            trailingContent = {
                RadioButton(
                    selected = choice.selected,
                    onClick = choice.onClick,
                )
            },
        ) {
            Text(choice.label)
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
            .zIndex(2f)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
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
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onRetry,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                    TextButton(
                        onClick = onBack,
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.textButtonColors(),
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
            .zIndex(2f)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
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
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                Text(
                    text = itemTitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    Button(
                        onClick = onPlayNext,
                        shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ButtonDefaults.MediumContainerHeight),
                        contentPadding = ButtonDefaults.contentPaddingFor(
                            ButtonDefaults.MediumContainerHeight,
                        ),
                    ) {
                        Text(stringResource(R.string.play_next))
                    }
                }
                OutlinedButton(
                    onClick = onReplay,
                    shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(
                        ButtonDefaults.MediumContainerHeight,
                    ),
                ) {
                    Text(stringResource(R.string.replay))
                }
                TextButton(
                    onClick = onBack,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.textButtonColors(),
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
    cropToFill: Boolean? = null,
    onToggleCrop: (() -> Unit)? = null,
    onOptions: (() -> Unit)? = null,
) {
    val mediumContainerSize = IconButtonDefaults.mediumContainerSize()
    val mediumIconSize = IconButtonDefaults.mediumIconSize
    val mediumShapes = IconButtonDefaults.shapes(
        shape = IconButtonDefaults.mediumRoundShape,
        pressedShape = IconButtonDefaults.mediumPressedShape,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(topScrim())
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            )
            .heightIn(min = 72.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
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
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            if (supportingText.isNotBlank()) {
                Text(
                    text = supportingText,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        if (cropToFill != null && onToggleCrop != null) {
            FilledTonalIconToggleButton(
                checked = cropToFill,
                onCheckedChange = { onToggleCrop() },
                shapes = IconButtonDefaults.toggleableShapes(),
                modifier = Modifier.size(mediumContainerSize),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_crop),
                    contentDescription = stringResource(
                        if (cropToFill) R.string.fit_video else R.string.crop_video,
                    ),
                    modifier = Modifier.size(mediumIconSize),
                )
            }
        }
        if (onOptions != null) {
            FilledTonalIconButton(
                onClick = onOptions,
                shapes = mediumShapes,
                modifier = Modifier.size(mediumContainerSize),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.playback_options),
                    modifier = Modifier.size(mediumIconSize),
                )
            }
        }
    }
}
