@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package xyz.five82.takeup.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import xyz.five82.takeup.ui.theme.OverlayPillColor
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
            containerColor = OverlayPillColor,
            contentColor = Color.White,
        ),
    ) {
        Icon(
            painter = painterResource(iconResource),
            contentDescription = contentDescription,
        )
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
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            PulsingPlaceholder(Modifier.fillMaxSize())
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
                .data(url)
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

/** Soft breathing surface shown while artwork loads. */
@Composable
internal fun PulsingPlaceholder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "placeholderPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "placeholderAlpha",
    )
    Box(
        modifier = modifier.background(
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha),
        ),
    )
}

/**
 * Screen-wide backdrop for detail screens: a wash of the seeded primary fading
 * into surface, so bodies visibly carry the artwork's palette instead of
 * collapsing to near-black.
 */
@Composable
internal fun DetailBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                // Strong enough to read as the artwork's color, not black: the
                // hero dissolves around the top third, so the tint holds there
                // and even the bottom keeps a visible trace.
                Brush.verticalGradient(
                    0f to lerp(scheme.surface, scheme.primaryContainer, 0.8f),
                    0.35f to lerp(scheme.surface, scheme.primaryContainer, 0.45f),
                    1f to lerp(scheme.surface, scheme.primaryContainer, 0.12f),
                ),
            ),
    ) {
        content()
    }
}

@Composable
internal fun FadingBackdropArtwork(
    url: String?,
    modifier: Modifier = Modifier,
    // Only heroes with text overlaying their lower edge (Season) need extra
    // darkening; elsewhere it would drag the dissolve back toward black.
    darkenBottomForText: Boolean = false,
) {
    Box(modifier = modifier) {
        MediaArtwork(
            url = url,
            // Dissolve the image itself to transparent so whatever sits behind
            // (the tinted DetailBackground) shows through, instead of fading
            // the artwork into an opaque block of surface color.
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.5f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(topScrim()),
        )
        if (darkenBottomForText) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.4f),
                        ),
                    ),
            )
        }
    }
}

@Composable
internal fun TitleLogo(
    url: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = title,
        contentScale = ContentScale.Fit,
        alignment = Alignment.CenterStart,
        modifier = modifier.height(64.dp),
    )
}

@Composable
internal fun WatchedBadge(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.watched)
    Surface(
        modifier = modifier.semantics { contentDescription = description },
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
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
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
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
