package xyz.five82.takeup.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.data.LoomItem

@Composable
internal fun MediaCard(
    serverUrl: String,
    item: LoomItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle = item.cardSubtitle()
    Card(
        onClick = onClick,
        modifier = modifier.semantics(mergeDescendants = true) {},
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth()) {
                MediaArtwork(
                    url = item.posterUrl(serverUrl),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                )
                val progress = item.progress
                if (progress?.played == true) {
                    WatchedBadge(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    )
                } else if (
                    progress != null &&
                    progress.resumePositionMs > 0 &&
                    progress.durationMs > 0
                ) {
                    val fraction = (progress.positionMs.toFloat() / progress.durationMs)
                        .coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(4.dp)
                            .progressSemantics(fraction),
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
            subtitle?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

internal fun LoomItem.subtitle(): String? = when {
    episodeLabel() != null -> episodeLabel()
    year > 0 -> year.toString()
    releaseDate.isNotBlank() -> releaseDate.take(4)
    else -> null
}

internal fun LoomItem.cardSubtitle(): String? = if (kind == "episode") {
    listOfNotNull(
        seriesTitle.takeIf { it.isNotBlank() },
        episodeLabel(),
    ).joinToString(" \u00B7 ").ifBlank { null }
} else {
    subtitle()
}
