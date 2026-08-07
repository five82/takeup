@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package xyz.five82.takeup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.R
import xyz.five82.takeup.data.DownloadEntry
import xyz.five82.takeup.data.DownloadState
import xyz.five82.takeup.data.Genre
import xyz.five82.takeup.data.PlaybackProgress
import xyz.five82.takeup.data.downloadProgressFraction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DetailsScreen(
    state: MainUiState.Details,
    downloadEntry: DownloadEntry?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onEditArtwork: () -> Unit,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onGenreSelected: (Genre) -> Unit,
) {
    BackHandler(onBack = onBack)
    UseLightStatusBarIcons()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var confirmRemoval by remember(state.item.id) { mutableStateOf(false) }
    // Downloaded artwork comes off local storage so the screen renders unchanged
    // when Loom is unreachable.
    val backdropUrl = downloadEntry?.backdropPath
        ?: state.item.backdropUrl(state.serverUrl)
        ?: downloadEntry?.posterPath
        ?: state.item.posterUrl(state.serverUrl)
    Box(Modifier.fillMaxSize()) {
    AmbientGlow(url = backdropUrl)
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    MediaOverlayIconButton(
                        iconResource = R.drawable.ic_arrow_back,
                        contentDescription = stringResource(R.string.navigate_back),
                        onClick = onBack,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = contentPadding.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                FadingBackdropArtwork(
                    url = state.item.backdropUrl(state.serverUrl),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .itemArtworkSharedBounds(state.item.id),
                )
            }
            if (state.isLoading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            state.error?.let { error ->
                item {
                    ErrorCard(
                        message = error,
                        onRetry = onRetry,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            item {
                Surface(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        MediaArtwork(
                            url = state.item.posterUrl(state.serverUrl),
                            modifier = Modifier
                                .width(112.dp)
                                .aspectRatio(2f / 3f)
                                .clip(MaterialTheme.shapes.medium),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val logoUrl = if (state.item.kind == "movie") {
                                state.item.logoUrl(state.serverUrl)
                            } else {
                                null
                            }
                            if (logoUrl != null) {
                                TitleLogo(
                                    url = logoUrl,
                                    title = state.item.title,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                Text(
                                    text = state.item.title,
                                    style = MaterialTheme.typography.headlineSmallEmphasized,
                                )
                            }
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
                            DetailActionRow(
                                playLabel = stringResource(
                                    if ((state.item.progress?.resumePositionMs ?: 0L) > 0) {
                                        R.string.resume
                                    } else {
                                        R.string.play
                                    },
                                ),
                                enabled = !state.isLoading,
                                onPlay = onPlay,
                                downloadEntry = downloadEntry,
                                // Loom's current version for this item, which only a
                                // single-item response carries. Comparing against the
                                // download's own snapshot would always match.
                                itemTag = state.item.mediaTag,
                                onDownload = {
                                    when (downloadAction(downloadEntry, state.item.mediaTag)) {
                                        // Deleting finished bytes is worth a prompt;
                                        // abandoning a partial transfer is not.
                                        DownloadAction.Remove -> confirmRemoval = true
                                        DownloadAction.Cancel -> onRemoveDownload()
                                        else -> onDownload()
                                    }
                                },
                                onEditArtwork = onEditArtwork.takeIf {
                                    state.item.kind == "movie" && state.item.tmdbId > 0
                                },
                            )
                        }
                    }
                }
            }
            if (state.item.genres.isNotEmpty()) {
                item {
                    GenreChipRow(
                        genres = state.item.genres,
                        onGenreSelected = onGenreSelected,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            val mediaBadges = state.item.mediaBadges()
            if (mediaBadges.isNotEmpty()) {
                item {
                    MediaBadges(
                        labels = mediaBadges,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
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
    if (confirmRemoval) {
        AlertDialog(
            onDismissRequest = { confirmRemoval = false },
            title = { Text(stringResource(R.string.remove_download_title)) },
            text = {
                Text(stringResource(R.string.remove_download_message, state.item.title))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemoval = false
                        onRemoveDownload()
                    },
                ) {
                    Text(stringResource(R.string.remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoval = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    }
}

/**
 * Connected action group: Play carries the outer pill corners and the trailing
 * actions complete the pill, reading as one control split into segments.
 */
@Composable
private fun DetailActionRow(
    playLabel: String,
    enabled: Boolean,
    onPlay: () -> Unit,
    downloadEntry: DownloadEntry?,
    itemTag: String,
    onDownload: (() -> Unit)?,
    onEditArtwork: (() -> Unit)?,
) {
    val height = ButtonDefaults.MediumContainerHeight
    val outer = 28.dp
    val inner = 8.dp
    val segments = 1 + (if (onDownload != null) 1 else 0) + (if (onEditArtwork != null) 1 else 0)
    var segment = 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val (playStart, playEnd) = segmentCorners(segment++, segments, outer, inner)
        Button(
            onClick = onPlay,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            enabled = enabled,
            shape = RoundedCornerShape(
                topStart = playStart,
                bottomStart = playStart,
                topEnd = playEnd,
                bottomEnd = playEnd,
            ),
            contentPadding = ButtonDefaults.contentPaddingFor(height),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_play),
                contentDescription = null,
            )
            Text(playLabel)
        }
        if (onDownload != null) {
            val (start, end) = segmentCorners(segment++, segments, outer, inner)
            DownloadActionButton(
                entry = downloadEntry,
                itemTag = itemTag,
                enabled = enabled,
                shape = RoundedCornerShape(
                    topStart = start,
                    bottomStart = start,
                    topEnd = end,
                    bottomEnd = end,
                ),
                onClick = onDownload,
            )
        }
        if (onEditArtwork != null) {
            val (start, end) = segmentCorners(segment, segments, outer, inner)
            FilledTonalButton(
                onClick = onEditArtwork,
                modifier = Modifier.fillMaxHeight(),
                shape = RoundedCornerShape(
                    topStart = start,
                    bottomStart = start,
                    topEnd = end,
                    bottomEnd = end,
                ),
                contentPadding = PaddingValues(horizontal = 18.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_artwork),
                    contentDescription = stringResource(R.string.artwork),
                )
            }
        }
    }
}

/**
 * One button covering every download state. A transfer in progress shows its
 * fraction in place of the icon so the pill never changes width mid-download.
 */
@Composable
private fun DownloadActionButton(
    entry: DownloadEntry?,
    itemTag: String,
    enabled: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    val action = downloadAction(entry, itemTag)
    val downloading = entry != null &&
        (entry.state == DownloadState.Downloading || entry.state == DownloadState.Queued)
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxHeight(),
        enabled = enabled,
        shape = shape,
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) {
        when {
            downloading -> CircularProgressIndicator(
                progress = { downloadProgressFraction(entry) },
                modifier = Modifier.size(20.dp),
            )
            action == DownloadAction.Remove -> Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = stringResource(R.string.remove_download),
            )
            else -> Icon(
                painter = painterResource(R.drawable.ic_download),
                contentDescription = stringResource(R.string.download),
                tint = if (action == DownloadAction.Retry) {
                    MaterialTheme.colorScheme.error
                } else {
                    LocalContentColor.current
                },
            )
        }
    }
}

/** Tappable genre pills linking into the genre landing. */
@Composable
internal fun GenreChipRow(
    genres: List<Genre>,
    onGenreSelected: (Genre) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        genres.forEach { genre ->
            Surface(
                onClick = { onGenreSelected(genre) },
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    text = genre.name,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
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
