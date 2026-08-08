@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package xyz.five82.takeup.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.util.Locale
import xyz.five82.takeup.R
import xyz.five82.takeup.data.LoomItem
import xyz.five82.takeup.data.MediaDynamicRange
import xyz.five82.takeup.data.MediaStream
import xyz.five82.takeup.data.imageUrlAtWidth
import xyz.five82.takeup.ui.theme.overlayPillColor
import xyz.five82.takeup.ui.theme.topScrim

@Composable
internal fun NavigationBackButton(
    onClick: () -> Unit,
    tint: Color = LocalContentColor.current,
) {
    IconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = stringResource(R.string.navigate_back),
            tint = tint,
        )
    }
}

@Composable
internal fun MediaOverlayIconButton(
    iconResource: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = overlayPillColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(
            painter = painterResource(iconResource),
            contentDescription = contentDescription,
        )
    }
}

/**
 * Watched-state actions for a show or a season. Loom attaches playback state only
 * to playable items, so there is no season- or show-level flag to toggle against
 * and both directions are offered outright.
 */
@Composable
internal fun WatchedStateMenu(onSetWatched: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        MediaOverlayIconButton(
            iconResource = R.drawable.ic_more_vert,
            contentDescription = stringResource(R.string.watched_state),
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.mark_watched)) },
                onClick = {
                    expanded = false
                    onSetWatched(true)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.clear_watched_history)) },
                onClick = {
                    expanded = false
                    onSetWatched(false)
                },
            )
        }
    }
}

@Composable
internal fun UseLightStatusBarIcons() {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val window = activity?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            val previousAppearance = controller.isAppearanceLightStatusBars
            controller.isAppearanceLightStatusBars = false
            onDispose {
                controller.isAppearanceLightStatusBars = previousAppearance
            }
        }
    }
}

@Composable
internal fun MediaArtwork(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var isLoading by remember(url) { mutableStateOf(url != null) }
    var isError by remember(url) { mutableStateOf(url == null) }
    // Sizing the request from the laid-out width keeps every call site honest:
    // Loom resizes to the bucket that covers this slot rather than serving a
    // full-size original. An unbounded slot lands on the widest bucket.
    BoxWithConstraints(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            ArtworkPlaceholder(Modifier.fillMaxSize())
        }
        if (isError) {
            Icon(
                painter = painterResource(R.drawable.ic_image_placeholder),
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            )
        }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url?.let { imageUrlAtWidth(it, constraints.maxWidth) })
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            onState = { state ->
                isLoading = state is AsyncImagePainter.State.Loading
                isError = state is AsyncImagePainter.State.Error
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Calm static surface shown while artwork loads. Deliberately not animated:
 * a screen full of loading cards used to run one infinite pulse animation
 * per image, which cost frames exactly when image decoding needed them.
 */
@Composable
internal fun ArtworkPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

/**
 * The one ambient background: an iris wash glowing from the top edge and
 * dissolving into the indigo stage. It lives once behind the whole app (in
 * MainActivity) rather than per screen, so every surface - search, settings,
 * hubs - sits in the same light. The eased stops keep the falloff from
 * reading as a hard band, and there is no artwork blur to bleed or band.
 */
@Composable
internal fun AmbientGlow(modifier: Modifier = Modifier) {
    val glow = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(520.dp)
            .background(
                Brush.verticalGradient(
                    0f to glow.copy(alpha = 0.26f),
                    0.35f to glow.copy(alpha = 0.11f),
                    0.7f to glow.copy(alpha = 0.04f),
                    1f to Color.Transparent,
                ),
            ),
    )
}

// Backdrop geometry and fade tuning. The sharp image is cropped taller than
// its 16:9 source so it arrives slightly zoomed, and the container extends
// below it for the blurred continuation layer.
private const val BackdropSharpFadeStart = 0.30f
private const val BackdropSharpAspect = 16f / 10f
private const val BackdropTotalAspect = 16f / 12f
private const val BackdropBlurredUnderlayer = true

// An abrupt-looking fade is rarely the gradient's length; it is the visible
// corner where a linear ramp begins. Sampling a smoothstep curve into stops
// removes that corner at both ends of the dissolve.
private fun easedFadeStops(start: Float, maxAlpha: Float = 1f): Array<Pair<Float, Color>> {
    val steps = 8
    return Array(steps + 1) { i ->
        val x = i / steps.toFloat()
        val eased = x * x * (3f - 2f * x)
        (start + x * (1f - start)) to Color.Black.copy(alpha = maxAlpha * (1f - eased))
    }
}

private val SharpFadeMask = Brush.verticalGradient(*easedFadeStops(BackdropSharpFadeStart))
private val BlurFadeMask = Brush.verticalGradient(*easedFadeStops(start = 0.45f, maxAlpha = 0.8f))

// The blurred layer only carries color, so the smallest resize bucket is
// plenty and skips a second full-size fetch of the backdrop.
private const val BlurRequestWidth = 240

@Composable
internal fun FadingBackdropArtwork(
    url: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.aspectRatio(BackdropTotalAspect)) {
        if (BackdropBlurredUnderlayer && url != null) {
            // A heavily blurred copy of the same artwork continues past the
            // sharp image, so the dissolve lands on the picture's own light
            // instead of dropping straight onto the stage.
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrlAtWidth(url, BlurRequestWidth))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(brush = BlurFadeMask, blendMode = BlendMode.DstIn)
                    }
                    .blur(64.dp),
            )
        }
        MediaArtwork(
            url = url,
            // The sharp image's own alpha dissolves out over its lower half,
            // handing off to the blurred continuation behind it.
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(BackdropSharpAspect)
                .align(Alignment.TopCenter)
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(brush = SharpFadeMask, blendMode = BlendMode.DstIn)
                },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(topScrim()),
        )
    }
}

// Logos draw 64dp tall and Fit-scaled, so their drawn width follows the
// artwork's aspect ratio rather than the slot. This covers even a very wide
// wordmark while skipping the multi-thousand-pixel original.
private const val LogoRequestWidth = 960

@Composable
internal fun TitleLogo(
    url: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(imageUrlAtWidth(url, LogoRequestWidth))
            .crossfade(true)
            .build(),
        contentDescription = title,
        contentScale = ContentScale.Fit,
        alignment = Alignment.CenterStart,
        modifier = modifier.height(64.dp),
    )
}

// Watched and unwatched badges wear Mint (`secondary`), the palette's state
// color: never used for actions, so it always reads as "done".
@Composable
internal fun WatchedBadge(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.watched)
    Surface(
        modifier = modifier.semantics { contentDescription = description },
        color = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
        shape = CircleShape,
        shadowElevation = 2.dp,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            modifier = Modifier
                .padding(4.dp)
                .size(16.dp),
        )
    }
}

/** The watched badge's counterpart for a show or season with episodes left. */
@Composable
internal fun UnwatchedBadge(count: Int, modifier: Modifier = Modifier) {
    val description = pluralStringResource(R.plurals.unwatched_count, count, count)
    Surface(
        modifier = modifier.semantics { contentDescription = description },
        color = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
        shape = CircleShape,
        shadowElevation = 2.dp,
    ) {
        Text(
            text = count.toString(),
            // Matches WatchedBadge's 16dp icon inside 4dp padding, so a row of
            // shows badges at one size whichever way each one went.
            modifier = Modifier
                .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .wrapContentSize(Alignment.Center),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
internal fun MediaBadges(
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    if (labels.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEach { label ->
            // Neutral on purpose: these are facts about the file, not states
            // or actions, so they take no accent at all.
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

internal fun LoomItem.mediaBadges(): List<String> {
    val video = mediaStreams.primary("video")
    val audio = mediaStreams.primary("audio")
    return listOfNotNull(
        video?.codecLabel(),
        video?.resolutionLabel(),
        video?.dynamicRangeLabel(),
        audio?.codecLabel(),
        audio?.channelLayoutLabel(),
    )
}

private fun List<MediaStream>.primary(kind: String): MediaStream? {
    val matching = filter { it.kind == kind }
    return matching.firstOrNull { it.isDefault } ?: matching.firstOrNull()
}

private fun MediaStream.codecLabel(): String? {
    if (codec.isBlank()) return null
    return when (codec.lowercase(Locale.ROOT)) {
        "av1" -> "AV1"
        "h264" -> "H.264"
        "hevc" -> "HEVC"
        "mpeg2video" -> "MPEG-2"
        "mpeg4" -> "MPEG-4"
        "prores" -> "ProRes"
        "vc1" -> "VC-1"
        "vp8" -> "VP8"
        "vp9" -> "VP9"
        "aac" -> "AAC"
        "ac3" -> "AC-3"
        "dts" -> when {
            profile.contains("DTS-HD MA", ignoreCase = true) -> "DTS-HD MA"
            profile.contains("DTS-HD HRA", ignoreCase = true) -> "DTS-HD HRA"
            profile.contains("DTS-HD", ignoreCase = true) -> "DTS-HD"
            else -> "DTS"
        }
        "eac3" -> "E-AC-3"
        "flac" -> "FLAC"
        "mp3" -> "MP3"
        "opus" -> "Opus"
        "truehd" -> "TrueHD"
        "vorbis" -> "Vorbis"
        else -> if (codec.startsWith("pcm_", ignoreCase = true)) {
            "PCM"
        } else {
            codec.uppercase(Locale.ROOT)
        }
    }
}

private fun MediaStream.resolutionLabel(): String? {
    if (width <= 0 || height <= 0) return null
    return when {
        width >= 3000 || height >= 2000 -> "4K"
        width >= 1200 || height >= 700 -> "HD"
        else -> "SD"
    }
}

private fun MediaStream.dynamicRangeLabel(): String? = when (dynamicRange) {
    MediaDynamicRange.DOLBY_VISION -> "Dolby Vision"
    MediaDynamicRange.HDR -> "HDR"
    MediaDynamicRange.SDR -> "SDR"
    null -> null
}

private fun MediaStream.channelLayoutLabel(): String? {
    if (channelLayout.isBlank()) return null
    val layout = channelLayout.substringBefore('(')
    return when (layout.lowercase(Locale.ROOT)) {
        "mono" -> "Mono"
        "stereo" -> "Stereo"
        else -> layout
    }
}

@Composable
internal fun PosterCardPlaceholder(modifier: Modifier = Modifier) {
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.large)
                .background(placeholder),
        )
        Box(
            Modifier
                .fillMaxWidth(0.82f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(placeholder),
        )
        Box(
            Modifier
                .fillMaxWidth(0.48f)
                .height(11.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(placeholder.copy(alpha = 0.75f)),
        )
    }
}

@Composable
internal fun EpisodeCardPlaceholder(modifier: Modifier = Modifier) {
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .width(128.dp)
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.medium)
                .background(placeholder),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.35f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(placeholder),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(placeholder),
            )
            Box(
                Modifier
                    .fillMaxWidth(0.72f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(placeholder.copy(alpha = 0.75f)),
            )
        }
    }
}
