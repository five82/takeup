package xyz.five82.takeup.ui.player

import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import xyz.five82.takeup.TakeupApplication
import xyz.five82.takeup.api.Chapter
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.ui.NavState
import xyz.five82.takeup.ui.Screen
import xyz.five82.takeup.ui.components.ErrorState
import xyz.five82.takeup.ui.components.threeThreads
import xyz.five82.takeup.ui.episodeLabel
import xyz.five82.takeup.ui.formatClock
import xyz.five82.takeup.ui.takeupViewModel
import xyz.five82.takeup.ui.theme.Ink
import xyz.five82.takeup.ui.theme.Muted
import xyz.five82.takeup.ui.theme.Surface1
import xyz.five82.takeup.ui.theme.WovenTheme
import xyz.five82.takeup.ui.theme.rememberWovenSeed
import xyz.five82.takeup.ui.theme.rememberWovenThreads
import xyz.five82.takeup.ui.posterUrl
import xyz.five82.takeup.ui.thumbUrl

// Shading for the control chips. Video renders on a SurfaceView, which Compose
// cannot blur behind an overlay, so a deep translucent fill does the work that
// a blur would otherwise share.
private val ChipFill = Color(0xFF0A0E17).copy(alpha = 0.62f)
private val ChipStroke = Ink.copy(alpha = 0.14f)
private val ConsoleFill = Color(0xFF0B0F1A).copy(alpha = 0.75f)

@Composable
fun PlayerScreen(repository: LoomRepository, nav: NavState, itemId: Long) {
    val application = LocalContext.current.applicationContext as TakeupApplication
    val model = takeupViewModel("player-$itemId") { PlayerViewModel(application, repository, itemId) }

    // The player owns the device while it is on screen: landscape, immersive,
    // and awake. All of it is handed back on dispose.
    val activity = LocalActivity.current
    DisposableEffect(Unit) {
        val window = activity?.window
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val error = model.error
    if (error != null) {
        ErrorState(error, onRetry = { model.load() })
        return
    }

    val item = model.item
    val seed = rememberWovenSeed(itemId, item?.let { repository.api.posterUrl(it, 240) })
    WovenTheme(seed) {
        PlayerContent(repository, nav, model)
    }
}

// PlayerView.subtitleView is flagged unstable, but it is the only way to move
// rendered cues clear of the control console.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun PlayerContent(repository: LoomRepository, nav: NavState, model: PlayerViewModel) {
    val player = model.player
    var controlsVisible by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var buffering by remember { mutableStateOf(true) }
    var tracks by remember { mutableStateOf(player.currentTracks) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var scrubPreview by remember { mutableStateOf<Long?>(null) }
    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }
    var interactionTick by remember { mutableIntStateOf(0) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }
        }
        player.addListener(listener)
        // Sync anything that changed before the listener attached: a fully
        // cached download reaches READY before first composition, and a
        // listener only reports transitions, so the initial spinner would
        // otherwise never clear.
        playing = player.isPlaying
        tracks = player.currentTracks
        if (player.playbackState == Player.STATE_READY ||
            player.playbackState == Player.STATE_ENDED
        ) {
            buffering = false
        }
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.takeIf { it > 0 } ?: model.item?.media?.durationMs ?: 0
            delay(250)
        }
    }

    // Controls fade while playing; any interaction brings them back, and
    // interactionTick restarts the countdown so they cannot vanish mid-use.
    LaunchedEffect(controlsVisible, playing, sheet, interactionTick) {
        if (controlsVisible && playing && sheet == null) {
            delay(3500)
            controlsVisible = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { controlsVisible = !controlsVisible }
            },
    ) {
        // Lift rendered subtitle cues clear of the console while it is up; the
        // value tracks the console's approximate height.
        val subtitleLiftPx = with(LocalDensity.current) { 168.dp.roundToPx() }
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    this.player = player
                }
            },
            update = { view ->
                view.player = player
                view.subtitleView?.setPadding(
                    0, 0, 0,
                    if (controlsVisible && !model.ended) subtitleLiftPx else 0,
                )
            },
            onRelease = { view -> view.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && !model.ended) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        val chromeVisible = controlsVisible && !model.ended
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 4 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 4 },
        ) {
            PlayerTopBar(nav, model, position)
        }
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 4 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 4 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerControls(
                model = model,
                playing = playing,
                position = scrubPreview ?: position,
                duration = duration,
                tracks = tracks,
                onInteraction = { interactionTick++ },
                onScrubPreview = { preview ->
                    if (preview != null && scrubPreview == null) interactionTick++
                    scrubPreview = preview
                },
                onSeek = { target ->
                    player.seekTo(target)
                    scrubPreview = null
                    interactionTick++
                },
                onOpenSheet = { sheet = it },
            )
        }

        if (model.ended) {
            EndOverlay(repository, nav, model)
        }
    }

    when (val open = sheet) {
        is PlayerSheet.Chapters -> ChaptersSheet(
            chapters = model.item?.media?.chapters.orEmpty(),
            positionMs = position,
            onSelect = { chapter ->
                player.seekTo(chapter.startMs)
                sheet = null
            },
            onDismiss = { sheet = null },
        )
        is PlayerSheet.Tracks -> TrackSheet(
            player = player,
            tracks = tracks,
            trackType = open.trackType,
            onDismiss = { sheet = null },
        )
        null -> Unit
    }
}

@Composable
private fun PlayerTopBar(nav: NavState, model: PlayerViewModel, positionMs: Long) {
    val item = model.item
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(ChipFill)
                .border(1.dp, ChipStroke, CircleShape)
                .clickable { nav.pop() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Ink,
                modifier = Modifier.size(28.dp),
            )
        }
        if (item != null) {
            val pill = RoundedCornerShape(percent = 50)
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .clip(pill)
                    .background(ChipFill)
                    .border(1.dp, ChipStroke, pill)
                    .padding(horizontal = 26.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    if (item.kind == "episode") "${episodeLabel(item)} · ${item.title}" else item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val chapter = currentChapter(item.media?.chapters, positionMs)
                if (chapter != null) {
                    Text(
                        chapterName(chapter),
                        style = MaterialTheme.typography.labelMedium,
                        color = Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerControls(
    model: PlayerViewModel,
    playing: Boolean,
    position: Long,
    duration: Long,
    tracks: Tracks,
    onInteraction: () -> Unit,
    onScrubPreview: (Long?) -> Unit,
    onSeek: (Long) -> Unit,
    onOpenSheet: (PlayerSheet) -> Unit,
    modifier: Modifier = Modifier,
) {
    val player = model.player
    val chapters = model.item?.media?.chapters.orEmpty()
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .clip(shape)
            .background(ConsoleFill)
            .border(1.dp, ChipStroke, shape)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                formatClock(position),
                style = MaterialTheme.typography.labelLarge,
                color = Ink,
            )
            ChapterScrubBar(
                positionMs = position,
                durationMs = duration,
                chapters = chapters,
                accent = accent,
                onPreview = onScrubPreview,
                onSeek = onSeek,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatClock(duration),
                style = MaterialTheme.typography.labelLarge,
                color = Muted,
            )
        }
        // Three zones - utility pills on the wings, transport dead center - so
        // the play button stays horizontally centered no matter what is shown.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f)) {
                if (chapters.isNotEmpty()) {
                    ConsolePill("Chapters") {
                        onInteraction()
                        onOpenSheet(PlayerSheet.Chapters)
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                SkipChip(forward = false) {
                    onInteraction()
                    player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                }
                Box(
                    Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Ink.copy(alpha = 0.12f))
                        .clickable { onInteraction(); if (playing) player.pause() else player.play() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (playing) {
                        PauseGlyph(accent = Ink)
                    } else {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = Ink,
                            modifier = Modifier.size(46.dp),
                        )
                    }
                }
                SkipChip(forward = true) {
                    onInteraction()
                    player.seekTo(player.currentPosition + 10_000)
                }
            }
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                if (trackCount(tracks, androidx.media3.common.C.TRACK_TYPE_AUDIO) > 1) {
                    ConsolePill("Audio") {
                        onInteraction()
                        onOpenSheet(PlayerSheet.Tracks(androidx.media3.common.C.TRACK_TYPE_AUDIO))
                    }
                }
                if (trackCount(tracks, androidx.media3.common.C.TRACK_TYPE_TEXT) > 0) {
                    ConsolePill("CC") {
                        onInteraction()
                        onOpenSheet(PlayerSheet.Tracks(androidx.media3.common.C.TRACK_TYPE_TEXT))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsolePill(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(Ink.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .defaultMinSize(minWidth = 64.dp, minHeight = 44.dp)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Ink)
    }
}

@Composable
private fun SkipChip(forward: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Ink.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SkipGlyph(forward)
    }
}

/**
 * Circular skip arrow with the seconds inside; the material glyph lives in the
 * extended icon set, which is not worth pulling in for two icons.
 */
@Composable
private fun SkipGlyph(forward: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(34.dp)
                // The back glyph is the forward glyph mirrored.
                .scale(scaleX = if (forward) 1f else -1f, scaleY = 1f),
        ) {
            val stroke = 2.dp.toPx()
            val r = size.minDimension / 2f - 5.dp.toPx()
            // Arc with a gap at the top; the arrowhead sits on the leading edge.
            drawArc(
                color = Ink,
                startAngle = -75f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(center.x - r, center.y - r),
                size = Size(2 * r, 2 * r),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            val angle = Math.toRadians(-75.0)
            val tip = Offset(
                center.x + r * cos(angle).toFloat(),
                center.y + r * sin(angle).toFloat(),
            )
            val tangent = Offset(-sin(angle).toFloat(), cos(angle).toFloat())
            val radial = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            val head = 5.dp.toPx()
            drawPath(
                Path().apply {
                    moveTo(tip.x + tangent.x * head, tip.y + tangent.y * head)
                    lineTo(tip.x + radial.x * head * 0.8f, tip.y + radial.y * head * 0.8f)
                    lineTo(tip.x - radial.x * head * 0.8f, tip.y - radial.y * head * 0.8f)
                    close()
                },
                Ink,
            )
        }
        Text("10", style = MaterialTheme.typography.labelMedium, color = Ink)
    }
}

/** Two bars; the core icon set has no pause glyph and one is not worth a library. */
@Composable
private fun PauseGlyph(accent: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(width = 9.dp, height = 32.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        Box(Modifier.size(width = 9.dp, height = 32.dp).clip(RoundedCornerShape(2.dp)).background(accent))
    }
}

@Composable
private fun EndOverlay(repository: LoomRepository, nav: NavState, model: PlayerViewModel) {
    val next = model.nextEpisode
    // The finished title's colors linger while up-next appears: drifting
    // thread fields over the dark scrim, woven from its poster.
    val finished = model.item
    val threads = rememberWovenThreads(
        finished?.id ?: 0L,
        finished?.let { repository.api.posterUrl(it, 240) },
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .threeThreads(threads, drifting = true),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (next != null) {
                Text("Up next", style = MaterialTheme.typography.labelMedium, color = Muted)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { nav.replaceTop(Screen.Player(next.id)) }
                        .padding(8.dp),
                ) {
                    Box(
                        Modifier
                            .width(240.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Surface1),
                    ) {
                        val thumb = repository.api.thumbUrl(next, 480)
                        if (thumb != null) {
                            AsyncImage(
                                model = thumb,
                                contentDescription = next.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Text(
                        "${episodeLabel(next)} · ${next.title}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                TextButton(onClick = { nav.pop() }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Done", color = Muted)
                }
            } else {
                TextButton(onClick = {
                    model.player.seekTo(0)
                    model.player.play()
                    model.ended = false
                }) {
                    Text("Play again", color = Ink)
                }
                TextButton(onClick = { nav.pop() }) {
                    Text("Done", color = Muted)
                }
            }
        }
    }
}

// -- sheets -------------------------------------------------------------------

sealed interface PlayerSheet {
    data object Chapters : PlayerSheet
    data class Tracks(val trackType: Int) : PlayerSheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChaptersSheet(
    chapters: List<Chapter>,
    positionMs: Long,
    onSelect: (Chapter) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            for (chapter in chapters) {
                val active = currentChapter(chapters, positionMs) == chapter
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(chapter) }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        chapterName(chapter),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (active) MaterialTheme.colorScheme.primary else Ink,
                    )
                    Text(
                        formatClock(chapter.startMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackSheet(player: Player, tracks: Tracks, trackType: Int, onDismiss: () -> Unit) {
    val groups = tracks.groups.filter { it.type == trackType && it.length > 0 }
    val isText = trackType == androidx.media3.common.C.TRACK_TYPE_TEXT
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                if (isText) "Subtitles" else "Audio",
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (isText) {
                val disabled = player.trackSelectionParameters.disabledTrackTypes.contains(trackType)
                TrackRow("Off", selected = disabled || groups.none { it.isSelected }) {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(trackType, true)
                        .build()
                    onDismiss()
                }
            }
            for (group in groups) {
                for (trackIndex in 0 until group.length) {
                    if (!group.isTrackSupported(trackIndex)) continue
                    val format = group.getTrackFormat(trackIndex)
                    TrackRow(
                        trackLabel(format),
                        selected = group.isTrackSelected(trackIndex),
                    ) {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(trackType, false)
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
                            .build()
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else Ink,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}

private fun trackLabel(format: androidx.media3.common.Format): String {
    val language = format.language
        ?.takeIf { it.isNotEmpty() && it != "und" }
        ?.let { java.util.Locale.forLanguageTag(it).displayLanguage.ifEmpty { it } }
    val parts = mutableListOf<String>()
    format.label?.takeIf { it.isNotEmpty() }?.let { parts += it }
    if (language != null && parts.none { it.contains(language, ignoreCase = true) }) {
        parts.add(0, language)
    }
    if (parts.isEmpty()) parts += "Track"
    return parts.joinToString(" · ")
}

private fun trackCount(tracks: Tracks, trackType: Int): Int =
    tracks.groups.filter { it.type == trackType }.sumOf { it.length }

private fun currentChapter(chapters: List<Chapter>?, positionMs: Long): Chapter? =
    chapters?.lastOrNull { it.startMs <= positionMs }

/** Disc rips routinely leave chapter marks unnamed; fall back to the number. */
fun chapterName(chapter: Chapter): String =
    chapter.title?.takeIf { it.isNotEmpty() } ?: "Chapter ${chapter.index + 1}"
