package xyz.five82.takeup.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.data.DownloadEntry

/**
 * What is playable with no Loom: the downloads themselves, under whatever the
 * screen wants to say about being offline.
 *
 * These open the player directly rather than a details screen. Details are a
 * server document - cast, seasons, artwork - and offline there is nothing left
 * on one but the play button this grid already is.
 */
@Composable
fun DownloadedGrid(
    entries: List<DownloadEntry>,
    accent: Color,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 16.dp,
    header: (@Composable () -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 106.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        if (header != null) {
            item(key = "offline-header", span = { GridItemSpan(maxLineSpan) }) { header() }
        }
        if (entries.isNotEmpty()) {
            item(key = "downloaded-label", span = { GridItemSpan(maxLineSpan) }) {
                RowLabel("Downloaded", color = accent, modifier = Modifier.padding(top = 12.dp))
            }
        }
        items(entries, key = { it.item.id }) { entry ->
            PosterCard(
                title = entry.item.title,
                imageUrl = entry.posterPath,
                fallbackTint = accent,
                onClick = { onOpen(entry.item.id) },
            )
        }
    }
}
