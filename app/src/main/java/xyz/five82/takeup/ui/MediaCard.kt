@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package xyz.five82.takeup.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.data.LoomItem

// Cards are intentionally containerless: the artwork carries the visual weight
// and text sits directly on the screen surface, keeping browse rows light.

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
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large),
            ) {
                MediaArtwork(
                    url = item.posterUrl(serverUrl),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                )
                MediaCardProgressOverlay(item, Modifier.matchParentSize())
            }
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.dp)) {
                Text(
                    text = item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
internal fun LandscapeMediaCard(
    serverUrl: String,
    item: LoomItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle = item.cardSubtitle()
    Card(
        onClick = onClick,
        modifier = modifier.semantics(mergeDescendants = true) {},
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge),
            ) {
                MediaArtwork(
                    url = item.backdropUrl(serverUrl) ?: item.posterUrl(serverUrl),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                )
                MediaCardProgressOverlay(item, Modifier.matchParentSize())
            }
            Column(modifier = Modifier.padding(horizontal = 6.dp)) {
                Text(
                    text = item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaCardProgressOverlay(item: LoomItem, modifier: Modifier = Modifier) {
    val progress = item.progress
    Box(modifier = modifier) {
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
