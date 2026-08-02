package xyz.five82.takeup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.R
import xyz.five82.takeup.data.PlaybackProgress
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailsScreen(
    state: MainUiState.Details,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlay: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.item.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { NavigationBackButton(onClick = onBack) },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (state.isLoading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            item {
                MediaArtwork(
                    url = state.item.backdropUrl(state.serverUrl),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                )
            }
            state.error?.let { error ->
                item {
                    Card(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = onRetry) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    MediaArtwork(
                        url = state.item.posterUrl(state.serverUrl),
                        modifier = Modifier
                            .width(112.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = state.item.title,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        val seriesContext = listOf(
                            state.item.seriesTitle,
                            state.item.seasonTitle,
                        ).filter { it.isNotBlank() }.joinToString(" \u00B7 ")
                        if (seriesContext.isNotBlank()) {
                            Text(
                                text = seriesContext,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        val metadata = listOfNotNull(
                            state.item.subtitle(),
                            state.item.mediaDurationMs.takeIf { it > 0 }?.let(::formatRuntime),
                            state.item.releaseDate
                                .takeIf { state.item.kind == "episode" && it.isNotBlank() }
                                ?.let(::formatReleaseDate),
                        ).joinToString(" \u00B7 ")
                        if (metadata.isNotBlank()) {
                            Text(
                                text = metadata,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        state.item.progress?.let { progress ->
                            PlaybackStatus(progress)
                        }
                        Button(
                            onClick = onPlay,
                            enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(
                                    if ((state.item.progress?.resumePositionMs ?: 0L) > 0) {
                                        R.string.resume
                                    } else {
                                        R.string.play
                                    },
                                ),
                            )
                        }
                    }
                }
            }
            if (state.item.overview.isNotBlank()) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.overview),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = state.item.overview,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            item { Box(Modifier.padding(bottom = 4.dp)) }
        }
    }
}

@Composable
internal fun PlaybackStatus(progress: PlaybackProgress) {
    if (progress.played) {
        Text(
            text = stringResource(R.string.watched),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        return
    }
    if (progress.resumePositionMs <= 0 || progress.durationMs <= 0) return

    val percent = (progress.positionMs.toDouble() / progress.durationMs * 100)
        .roundToInt()
        .coerceIn(0, 100)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(
                R.string.resume_progress,
                formatPosition(progress.resumePositionMs),
                percent,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        val fraction = percent / 100f
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .progressSemantics(fraction),
        )
    }
}

private fun formatPosition(durationMs: Long): String {
    val totalMinutes = durationMs / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}"
    } else {
        "$minutes min"
    }
}

internal fun formatReleaseDate(
    value: String,
    locale: Locale = Locale.getDefault(),
): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("MMM d, uuuu", locale))
}.getOrDefault(value)

internal fun formatRuntime(durationMs: Long): String {
    val totalMinutes = durationMs / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}
