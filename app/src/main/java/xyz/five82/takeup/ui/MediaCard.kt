package xyz.five82.takeup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import xyz.five82.takeup.data.LoomItem

@Composable
internal fun MediaCard(
    serverUrl: String,
    item: LoomItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle = item.subtitle()
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                AsyncImage(
                    model = item.posterUrl(serverUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                )
                val progress = item.progress
                if (progress != null && !progress.played && progress.durationMs > 0) {
                    LinearProgressIndicator(
                        progress = {
                            (progress.positionMs.toFloat() / progress.durationMs)
                                .coerceIn(0f, 1f)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(4.dp),
                    )
                }
            }
            Text(
                text = item.title,
                modifier = Modifier.padding(
                    start = 10.dp,
                    top = 10.dp,
                    end = 10.dp,
                    bottom = if (subtitle == null) 10.dp else 0.dp,
                ),
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
            )
            subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

internal fun LoomItem.subtitle(): String? = when {
    kind == "episode" && seasonNumber > 0 && episodeNumber > 0 -> buildString {
        append("S")
        append(seasonNumber.toString().padStart(2, '0'))
        append("E")
        append(episodeNumber.toString().padStart(2, '0'))
        if (episodeEndNumber > episodeNumber) {
            append("-")
            append(episodeEndNumber.toString().padStart(2, '0'))
        }
    }
    year > 0 -> year.toString()
    releaseDate.isNotBlank() -> releaseDate.take(4)
    else -> null
}
