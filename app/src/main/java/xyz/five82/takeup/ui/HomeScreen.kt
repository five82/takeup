@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package xyz.five82.takeup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
    onShowMovies: () -> Unit,
    onShowShows: () -> Unit,
    onItemSelected: (LoomItem) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                    )
                },
                actions = {
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
            .padding(contentPadding),
        contentPadding = PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Text(
                text = description,
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleLarge,
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
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = onRetry,
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
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
            if (state.content.continueWatching.isNotEmpty()) {
                item {
                    MediaRow(
                        title = stringResource(R.string.continue_watching),
                        serverUrl = state.serverUrl,
                        items = state.content.continueWatching,
                        onItemSelected = onItemSelected,
                    )
                }
            }
            if (state.content.recentlyAdded.isNotEmpty()) {
                item {
                    MediaRow(
                        title = stringResource(R.string.recently_added),
                        serverUrl = state.serverUrl,
                        items = state.content.recentlyAdded,
                        onItemSelected = onItemSelected,
                    )
                }
            }
            if (state.content.movies.isNotEmpty()) {
                item {
                    MediaRow(
                        title = stringResource(R.string.movies),
                        serverUrl = state.serverUrl,
                        items = state.content.movies.take(12),
                        actionText = stringResource(R.string.see_all),
                        onAction = onShowMovies,
                        onItemSelected = onItemSelected,
                    )
                }
            }
            if (state.content.shows.isNotEmpty()) {
                item {
                    MediaRow(
                        title = stringResource(R.string.shows),
                        serverUrl = state.serverUrl,
                        items = state.content.shows.take(12),
                        actionText = stringResource(R.string.see_all),
                        onAction = onShowShows,
                        onItemSelected = onItemSelected,
                    )
                }
            }
            if (
                state.content.continueWatching.isEmpty() &&
                state.content.recentlyAdded.isEmpty() &&
                state.content.movies.isEmpty() &&
                state.content.shows.isEmpty()
            ) {
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
private fun MediaRow(
    title: String,
    serverUrl: String,
    items: List<LoomItem>,
    onItemSelected: (LoomItem) -> Unit,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
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
