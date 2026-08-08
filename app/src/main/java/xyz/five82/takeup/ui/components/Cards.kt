package xyz.five82.takeup.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import xyz.five82.takeup.ui.theme.Ink
import xyz.five82.takeup.ui.theme.Muted
import xyz.five82.takeup.ui.theme.Stage
import xyz.five82.takeup.ui.theme.Surface1

/** A named action for a card's long-press menu. */
data class CardAction(val label: String, val run: () -> Unit)

/**
 * Poster (2:3) card. When artwork is missing the card carries the title in
 * the display face on a surface tinted by [fallbackTint].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    title: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    width: Int = 108,
    badgeCount: Int = 0,
    badgeColor: Color = MaterialTheme.colorScheme.secondary,
    progress: Float? = null,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    fallbackTint: Color = MaterialTheme.colorScheme.primary,
    actions: List<CardAction> = emptyList(),
    onClick: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier.width(width.dp)) {
        Column(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (actions.isEmpty()) null else ({ menuOpen = true }),
                ),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(Surface1),
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    MissingArt(title, fallbackTint)
                }
                if (badgeCount > 0) {
                    BobbinBadge(badgeCount, badgeColor, Modifier.align(Alignment.TopEnd))
                }
                if (progress != null) {
                    ThreadProgress(
                        progress,
                        progressColor,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                    )
                }
            }
        }
        CardMenu(menuOpen, actions) { menuOpen = false }
    }
}

/**
 * 16:9 thumb card for Continue Watching and Next Up. Loom's thumb art has
 * the title baked in, so the lines beneath carry context, not identity.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThumbCard(
    title: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    width: Int = 172,
    line: String? = null,
    progress: Float? = null,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    fallbackTint: Color = MaterialTheme.colorScheme.primary,
    actions: List<CardAction> = emptyList(),
    onClick: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier.width(width.dp)) {
        Column(
            Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = if (actions.isEmpty()) null else ({ menuOpen = true }),
            ),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface1),
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    MissingArt(title, fallbackTint)
                }
                if (progress != null) {
                    ThreadProgress(
                        progress,
                        progressColor,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
            if (line != null) {
                Text(
                    line,
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                )
            }
        }
        CardMenu(menuOpen, actions) { menuOpen = false }
    }
}

/** Unwatched-episode count spooled onto a poster corner. */
@Composable
private fun BobbinBadge(count: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(6.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Stage.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun MissingArt(title: String, tint: Color) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Surface1),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(10.dp)) {
            Box(
                Modifier
                    .width(22.dp)
                    .background(tint.copy(alpha = 0.85f))
                    .padding(top = 3.dp),
            )
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize),
                color = Ink,
                textAlign = TextAlign.Center,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun CardMenu(open: Boolean, actions: List<CardAction>, onDismiss: () -> Unit) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        for (action in actions) {
            DropdownMenuItem(
                text = { Text(action.label) },
                onClick = {
                    onDismiss()
                    action.run()
                },
            )
        }
    }
}
