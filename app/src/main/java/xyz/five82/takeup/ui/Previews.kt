package xyz.five82.takeup.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.data.LoomItem
import xyz.five82.takeup.data.PlaybackProgress
import xyz.five82.takeup.ui.theme.TakeupTheme

// Artwork never loads in previews (no server), so cards render their pulse
// placeholder; layout, typography, and color roles are still representative.

private val previewItem = LoomItem(
    id = 1,
    kind = "movie",
    title = "The Long Voyage Home",
    year = 2024,
    overview = "A crew sails into the unknown.",
    mediaDurationMs = 118 * 60 * 1000L,
    progress = PlaybackProgress(
        positionMs = 30 * 60 * 1000L,
        durationMs = 118 * 60 * 1000L,
        played = false,
        resumePositionMs = 30 * 60 * 1000L,
    ),
)

@Preview
@Composable
private fun MediaCardPreview() {
    TakeupTheme {
        MediaCard(
            serverUrl = "http://loom.local:8080",
            item = previewItem,
            onClick = {},
            modifier = Modifier.width(140.dp),
        )
    }
}

@Preview
@Composable
private fun LandscapeMediaCardPreview() {
    TakeupTheme {
        LandscapeMediaCard(
            serverUrl = "http://loom.local:8080",
            item = previewItem,
            onClick = {},
            modifier = Modifier.width(240.dp),
        )
    }
}

@Preview
@Composable
private fun GenreCardPreview() {
    TakeupTheme {
        GenreCard(
            name = "Science Fiction",
            itemCount = 42,
            onClick = {},
            modifier = Modifier
                .width(156.dp)
                .height(88.dp),
        )
    }
}

@Preview
@Composable
private fun ErrorCardPreview() {
    TakeupTheme {
        ErrorCard(
            message = "Takeup could not connect to Loom. Check the address and server.",
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun FullScreenErrorPreview() {
    TakeupTheme {
        FullScreenError(
            message = "The Loom server did not respond in time.",
            onRetry = {},
            secondaryLabel = "Settings",
            onSecondary = {},
        )
    }
}
