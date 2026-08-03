@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package xyz.five82.takeup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.R
import xyz.five82.takeup.data.LoomItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeScreen(
    state: MainUiState.Home,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onShowMovies: () -> Unit,
    onShowShows: () -> Unit,
    onItemSelected: (LoomItem) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    FilledTonalIconButton(
                        onClick = onOpenSearch,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = stringResource(R.string.search),
                        )
                    }
                    FilledTonalIconButton(
                        onClick = onOpenSettings,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.settings),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        val content = state.content
        val isEmpty = content.continueWatching.isEmpty() &&
            content.recentlyAdded.isEmpty() && content.movies.isEmpty() && content.shows.isEmpty()
        when {
            state.isLoading && isEmpty -> LoadingHome(contentPadding)
            state.error != null && isEmpty -> HomeError(
                contentPadding = contentPadding,
                message = state.error,
                onRetry = onRetry,
                onOpenSettings = onOpenSettings,
            )
            else -> HomeList(
                contentPadding = contentPadding,
                state = state,
                onRetry = onRetry,
                onShowMovies = onShowMovies,
                onShowShows = onShowShows,
                onItemSelected = onItemSelected,
            )
        }
    }
}

@Composable
private fun LoadingHome(contentPadding: PaddingValues) {
    val description = stringResource(R.string.loading_home)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .semantics { contentDescription = description },
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item {
            Box(
                Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
        items(2) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(4) {
                    PosterCardPlaceholder(modifier = Modifier.width(140.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeError(
    contentPadding: PaddingValues,
    message: String,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
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
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRetry,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.retry))
                }
                TextButton(
                    onClick = onOpenSettings,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.settings))
                }
            }
        }
    }
}

@Composable
private fun HomeList(
    contentPadding: PaddingValues,
    state: MainUiState.Home,
    onRetry: () -> Unit,
    onShowMovies: () -> Unit,
    onShowShows: () -> Unit,
    onItemSelected: (LoomItem) -> Unit,
) {
    val content = state.content
    val featured = content.continueWatching.firstOrNull()
        ?: content.recentlyAdded.firstOrNull()
        ?: content.movies.firstOrNull()
        ?: content.shows.firstOrNull()
    val featuredIsContinueWatching = featured != null &&
        content.continueWatching.firstOrNull()?.id == featured.id
    val featuredIsRecentlyAdded = featured != null && !featuredIsContinueWatching &&
        content.recentlyAdded.firstOrNull()?.id == featured.id
    val continueWatching = if (featuredIsContinueWatching) {
        content.continueWatching.drop(1)
    } else {
        content.continueWatching
    }
    val recentlyAdded = if (featuredIsRecentlyAdded) {
        content.recentlyAdded.drop(1)
    } else {
        content.recentlyAdded
    }

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = onRetry,
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
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
                            TextButton(
                                onClick = onRetry,
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            }
            featured?.let { item ->
                item {
                    FeaturedMedia(
                        serverUrl = state.serverUrl,
                        item = item,
                        label = stringResource(
                            when {
                                featuredIsContinueWatching -> R.string.continue_watching
                                featuredIsRecentlyAdded -> R.string.recently_added
                                item.kind == "show" -> R.string.shows
                                else -> R.string.movies
                            },
                        ),
                        onClick = { onItemSelected(item) },
                    )
                }
            }
            if (continueWatching.isNotEmpty()) {
                item {
                    MediaRow(
                        title = stringResource(R.string.continue_watching),
                        serverUrl = state.serverUrl,
                        items = continueWatching,
                        landscape = true,
                        onItemSelected = onItemSelected,
                    )
                }
            }
            if (recentlyAdded.isNotEmpty()) {
                item {
                    MediaRow(
                        title = stringResource(R.string.recently_added),
                        serverUrl = state.serverUrl,
                        items = recentlyAdded,
                        onItemSelected = onItemSelected,
                    )
                }
            }
            if (content.movies.isNotEmpty()) {
                item {
                    MediaRow(
                        title = stringResource(R.string.movies),
                        serverUrl = state.serverUrl,
                        items = content.movies.take(12),
                        actionText = stringResource(R.string.see_all),
                        onAction = onShowMovies,
                        onItemSelected = onItemSelected,
                    )
                }
            }
            if (content.shows.isNotEmpty()) {
                item {
                    MediaRow(
                        title = stringResource(R.string.shows),
                        serverUrl = state.serverUrl,
                        items = content.shows.take(12),
                        actionText = stringResource(R.string.see_all),
                        onAction = onShowShows,
                        onItemSelected = onItemSelected,
                    )
                }
            }
            if (featured == null) {
                item {
                    Text(
                        text = stringResource(R.string.no_home_items),
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturedMedia(
    serverUrl: String,
    item: LoomItem,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f),
        ) {
            MediaArtwork(
                url = item.backdropUrl(serverUrl) ?: item.posterUrl(serverUrl),
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.35f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.9f),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMediumEmphasized,
                    )
                }
                val logoUrl = item.logoUrl(serverUrl)
                if (logoUrl != null) {
                    TitleLogo(
                        url = logoUrl,
                        title = item.title,
                        modifier = Modifier.fillMaxWidth(0.72f),
                    )
                } else {
                    Text(
                        text = item.title,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                    )
                }
                val metadata = listOfNotNull(
                    item.cardSubtitle(),
                    item.mediaDurationMs.takeIf { it > 0 }?.let(::formatRuntime),
                ).joinToString(" \u00B7 ")
                if (metadata.isNotBlank()) {
                    Text(
                        text = metadata,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item.progress?.let { PlaybackStatus(it) }
                Button(
                    onClick = onClick,
                    shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                    modifier = Modifier.height(ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(
                        ButtonDefaults.MediumContainerHeight,
                    ),
                ) {
                    Text(stringResource(R.string.view_details))
                }
            }
        }
    }
}

@Composable
private fun MediaRow(
    title: String,
    serverUrl: String,
    items: List<LoomItem>,
    onItemSelected: (LoomItem) -> Unit,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    landscape: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            if (actionText != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(actionText)
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { item ->
                if (landscape) {
                    LandscapeMediaCard(
                        serverUrl = serverUrl,
                        item = item,
                        onClick = { onItemSelected(item) },
                        modifier = Modifier.width(240.dp),
                    )
                } else {
                    MediaCard(
                        serverUrl = serverUrl,
                        item = item,
                        onClick = { onItemSelected(item) },
                        modifier = Modifier.width(140.dp),
                    )
                }
            }
        }
    }
}
