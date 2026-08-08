@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package xyz.five82.takeup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.R
import xyz.five82.takeup.data.LoomItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SeasonScreen(
    state: MainUiState.Season,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onEditArtwork: () -> Unit,
    onSetWatched: (Boolean) -> Unit,
    onEpisodeSelected: (LoomItem) -> Unit,
) {
    BackHandler(onBack = onBack)
    UseLightStatusBarIcons()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(
                        visible = scrollBehavior.state.overlappedFraction > 0.01f,
                        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                    ) {
                        Column {
                            Text(
                                text = state.show.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMediumEmphasized,
                            )
                            Text(
                                text = state.season.title,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
                navigationIcon = {
                    MediaOverlayIconButton(
                        iconResource = R.drawable.ic_arrow_back,
                        contentDescription = stringResource(R.string.navigate_back),
                        onClick = onBack,
                    )
                },
                actions = {
                    WatchedStateMenu(onSetWatched = onSetWatched)
                    if (state.show.tmdbId > 0) {
                        MediaOverlayIconButton(
                            iconResource = R.drawable.ic_artwork,
                            contentDescription = stringResource(R.string.artwork),
                            onClick = onEditArtwork,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        EpisodeList(
            contentPadding = contentPadding,
            state = state,
            onRetry = onRetry,
            onEpisodeSelected = onEpisodeSelected,
        )
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
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item {
            SeasonHero(state)
        }
        if (state.isLoading) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.episodes),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                if (state.episodes.isNotEmpty()) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.episode_count,
                            state.episodes.size,
                            state.episodes.size,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        state.error?.let { error ->
            item {
                ErrorCard(
                    message = error,
                    onRetry = onRetry,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                )
            }
        }
        if (state.isLoading && state.episodes.isEmpty()) {
            items(3) { index ->
                LoadingEpisodeListItem(index = index, count = 3)
            }
        } else if (state.episodes.isEmpty() && state.error == null) {
            item {
                Text(
                    text = stringResource(R.string.no_episodes),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(state.episodes, key = { _, episode -> episode.id }) { index, episode ->
            EpisodeListItem(
                serverUrl = state.serverUrl,
                episode = episode,
                index = index,
                count = state.episodes.size,
                onClick = { onEpisodeSelected(episode) },
            )
        }
    }
}

@Composable
private fun SeasonHero(state: MainUiState.Season) {
    // The title block sits below the artwork on the stage rather than over the
    // fade zone: text over the dissolve needed an extra darkening gradient
    // that dragged the whole fade back toward black.
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FadingBackdropArtwork(
            url = state.show.backdropUrl(state.serverUrl)
                ?: state.season.backdropUrl(state.serverUrl),
            // Shares the show's key so ShowDetails -> Season morphs the hero.
            modifier = Modifier
                .fillMaxWidth()
                .itemArtworkSharedBounds(state.show.id),
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            MediaArtwork(
                url = state.season.posterUrl(state.serverUrl),
                modifier = Modifier
                    .width(88.dp)
                    .aspectRatio(2f / 3f)
                    .clip(MaterialTheme.shapes.large),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val logoUrl = state.show.logoUrl(state.serverUrl)
                if (logoUrl != null) {
                    TitleLogo(
                        url = logoUrl,
                        title = state.show.title,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = state.show.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                    )
                }
                Text(
                    text = state.season.title,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                if (state.episodes.isNotEmpty()) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.episode_count,
                            state.episodes.size,
                            state.episodes.size,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingEpisodeListItem(index: Int, count: Int) {
    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 12.dp,
                end = 12.dp,
                bottom = if (index == count - 1) 0.dp else ListItemDefaults.SegmentedGap,
            ),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = shapes.shape,
    ) {
        EpisodeCardPlaceholder()
    }
}

@Composable
private fun EpisodeListItem(
    serverUrl: String,
    episode: LoomItem,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    // The listing this row comes from carries no media duration, so the runtime
    // lives on the episode's details screen rather than costing a request each.
    val metadata = episode.releaseDate.takeIf { it.isNotBlank() }
        ?.let(::formatReleaseDate)
        .orEmpty()
    val hasSupportingContent = metadata.isNotBlank() ||
        episode.overview.isNotBlank() || episode.progress != null

    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 12.dp,
                end = 12.dp,
                bottom = if (index == count - 1) 0.dp else ListItemDefaults.SegmentedGap,
            ),
        leadingContent = {
            Box {
                MediaArtwork(
                    url = episode.backdropUrl(serverUrl),
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(16f / 9f)
                        .clip(MaterialTheme.shapes.medium),
                )
                if (episode.progress?.played == true) {
                    WatchedBadge(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                    )
                }
            }
        },
        overlineContent = episode.subtitle()?.let { subtitle ->
            {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
        supportingContent = if (hasSupportingContent) {
            {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (metadata.isNotBlank()) {
                        Text(
                            text = metadata,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (episode.overview.isNotBlank()) {
                        Text(
                            text = episode.overview,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    episode.progress?.let { PlaybackStatus(it) }
                }
            }
        } else {
            null
        },
        verticalAlignment = Alignment.Top,
        contentPadding = PaddingValues(12.dp),
    ) {
        Text(
            text = episode.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
    }
}
