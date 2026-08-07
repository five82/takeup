@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package xyz.five82.takeup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.R
import xyz.five82.takeup.data.DownloadEntry
import xyz.five82.takeup.data.DownloadState
import xyz.five82.takeup.data.downloadProgressFraction
import xyz.five82.takeup.data.formatBytes
import xyz.five82.takeup.data.isStaleDownload

/** What the download control on a details screen should currently offer. */
internal enum class DownloadAction { Start, Cancel, Remove, Retry, Update }

internal fun downloadAction(entry: DownloadEntry?, itemTag: String): DownloadAction = when {
    entry == null -> DownloadAction.Start
    entry.state == DownloadState.Failed -> DownloadAction.Retry
    entry.state == DownloadState.Completed && isStaleDownload(entry, itemTag) -> DownloadAction.Update
    entry.state == DownloadState.Completed -> DownloadAction.Remove
    else -> DownloadAction.Cancel
}

/**
 * Corner radii for one segment of a connected button group, so the row reads as a
 * single control split into [count] parts regardless of how many are shown.
 */
internal fun segmentCorners(index: Int, count: Int, outer: Dp, inner: Dp): Pair<Dp, Dp> {
    val start = if (index == 0) outer else inner
    val end = if (index == count - 1) outer else inner
    return start to end
}

@Composable
internal fun downloadStatusLabel(entry: DownloadEntry): String = when (entry.state) {
    // Name the state rather than only showing a size, so "ready to watch offline"
    // never has to be inferred from a colour or a number.
    DownloadState.Completed -> stringResource(
        R.string.downloaded_size,
        formatBytes(entry.totalBytes.coerceAtLeast(0)),
    )
    DownloadState.Failed -> stringResource(R.string.download_failed)
    DownloadState.Queued -> stringResource(R.string.download_queued)
    DownloadState.Removing -> stringResource(R.string.remove_download)
    DownloadState.Downloading -> stringResource(
        R.string.downloading,
        (downloadProgressFraction(entry) * 100).toInt(),
    )
}

/**
 * Poster card for the Home row. Unlike [MediaCard] it renders the locally stored
 * artwork and reports transfer state, so the row stays meaningful with no server.
 */
@Composable
internal fun DownloadCard(
    entry: DownloadEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    url = entry.posterPath,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                )
                if (entry.state != DownloadState.Completed) {
                    LinearProgressIndicator(
                        progress = { downloadProgressFraction(entry) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(
                    text = entry.item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = downloadStatusLabel(entry),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/**
 * Leading state indicator for the Settings list. Distinguished by icon rather than
 * colour: this theme derives `primary` from the item's own artwork, so a red poster
 * would otherwise paint "completed" in the same red that means "failed".
 */
@Composable
internal fun DownloadStateIcon(entry: DownloadEntry, modifier: Modifier = Modifier) {
    val icon = if (entry.state == DownloadState.Completed) {
        R.drawable.ic_check
    } else {
        R.drawable.ic_download
    }
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = modifier.size(20.dp),
        tint = if (entry.state == DownloadState.Failed) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}
