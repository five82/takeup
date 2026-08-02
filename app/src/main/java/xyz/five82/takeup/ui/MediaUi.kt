package xyz.five82.takeup.ui

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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.util.Locale
import xyz.five82.takeup.R
import xyz.five82.takeup.data.LoomItem
import xyz.five82.takeup.data.MediaStream

@Composable
internal fun NavigationBackButton(
    onClick: () -> Unit,
    tint: Color = LocalContentColor.current,
) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = stringResource(R.string.navigate_back),
            tint = tint,
        )
    }
}

@Composable
internal fun MediaArtwork(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_image_placeholder),
            contentDescription = null,
            modifier = Modifier.size(42.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        )
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        )
    }
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
                shape = RoundedCornerShape(6.dp),
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
    "dolby_vision" -> "Dolby Vision"
    "hdr" -> "HDR"
    "sdr" -> "SDR"
    else -> null
}

private fun MediaStream.channelLayoutLabel(): String? {
    if (channelLayout.isNotBlank()) {
        val layout = channelLayout.substringBefore('(')
        return when (layout.lowercase(Locale.ROOT)) {
            "mono" -> "Mono"
            "stereo" -> "Stereo"
            else -> layout
        }
    }
    return when (channels) {
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        in 3..Int.MAX_VALUE -> "$channels ch"
        else -> null
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
                .clip(RoundedCornerShape(12.dp))
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
                .clip(RoundedCornerShape(8.dp))
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
