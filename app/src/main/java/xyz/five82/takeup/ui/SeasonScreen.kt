package xyz.five82.takeup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.R
import xyz.five82.takeup.data.LoomItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeasonScreen(
    state: MainUiState.Season,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onEpisodeSelected: (LoomItem) -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.show.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { NavigationBackButton(onClick = onBack) },
            )
        },
    ) { contentPadding ->
        when {
            state.isLoading && state.episodes.isEmpty() -> LoadingSeason(contentPadding)
            state.error != null && state.episodes.isEmpty() -> SeasonError(
                contentPadding = contentPadding,
                message = state.error,
                onRetry = onRetry,
            )
            else -> EpisodeList(
                contentPadding = contentPadding,
                state = state,
                onRetry = onRetry,
                onEpisodeSelected = onEpisodeSelected,
            )
        }
    }
}

@Composable
private fun LoadingSeason(contentPadding: PaddingValues) {
    val description = stringResource(R.string.loading_episodes)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .semantics { contentDescription = description },
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(3) {
            Card {
                EpisodeCardPlaceholder()
            }
        }
    }
}

@Composable
private fun SeasonError(
    contentPadding: PaddingValues,
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun EpisodeList(
    contentPadding: PaddingValues,
    state: MainUiState.Season,
    onRetry: () -> Unit,
    onEpisodeSelected: (LoomItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.isLoading) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        item {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaArtwork(
                    url = state.season.posterUrl(state.serverUrl),
                    modifier = Modifier
                        .width(96.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(10.dp)),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = state.season.title,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(R.string.episodes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        state.error?.let { error ->
            item {
                Card(
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
        if (state.episodes.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.no_episodes),
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.episodes, key = { it.id }) { episode ->
            EpisodeCard(
                serverUrl = state.serverUrl,
                episode = episode,
                onClick = { onEpisodeSelected(episode) },
            )
        }
    }
}

@Composable
private fun EpisodeCard(
    serverUrl: String,
    episode: LoomItem,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box {
                MediaArtwork(
                    url = episode.backdropUrl(serverUrl),
                    modifier = Modifier
                        .width(128.dp)
                        .aspectRatio(16f / 9f),
                )
                if (episode.progress?.played == true) {
                    WatchedBadge(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                episode.subtitle()?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    text = episode.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                val metadata = listOfNotNull(
                    episode.mediaDurationMs.takeIf { it > 0 }?.let(::formatRuntime),
                    episode.releaseDate.takeIf { it.isNotBlank() }?.let(::formatReleaseDate),
                ).joinToString(" \u00B7 ")
                if (metadata.isNotBlank()) {
                    Text(
                        text = metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                episode.progress?.let { PlaybackStatus(it) }
            }
        }
        val mediaBadges = episode.mediaBadges()
        if (mediaBadges.isNotEmpty()) {
            MediaBadges(
                labels = mediaBadges,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            )
        }
        if (episode.overview.isNotBlank()) {
            Text(
                text = episode.overview,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
