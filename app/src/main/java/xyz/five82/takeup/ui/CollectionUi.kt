@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package xyz.five82.takeup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.R
import xyz.five82.takeup.data.LoomCollection
import xyz.five82.takeup.ui.theme.heroBottomScrim

/**
 * Artwork-driven shelf card. Unlike a genre, a collection is a franchise or a
 * studio the user recognizes on sight, so a member's backdrop identifies it far
 * faster than a color block would.
 */
@Composable
internal fun CollectionCard(
    serverUrl: String,
    collection: LoomCollection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
    ) {
        Box(Modifier.fillMaxSize()) {
            MediaArtwork(
                url = collection.artworkUrl(serverUrl),
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(heroBottomScrim(MaterialTheme.colorScheme.surface)),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = collection.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.title_count,
                        collection.items.size,
                        collection.items.size,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
