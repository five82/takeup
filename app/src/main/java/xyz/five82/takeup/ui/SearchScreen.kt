@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package xyz.five82.takeup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.R
import xyz.five82.takeup.data.LoomItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SearchScreen(
    state: MainUiState.Search,
    onQueryChanged: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onItemSelected: (LoomItem) -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { NavigationBackButton(onClick = onBack) },
                title = { SearchField(query = state.query, onQueryChanged = onQueryChanged) },
            )
        },
    ) { contentPadding ->
        val loadingDescription = stringResource(R.string.loading_search)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .imePadding(),
        ) {
            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = loadingDescription },
                )
            }
            when {
                state.error != null && state.results.isEmpty() -> SearchError(
                    message = state.error,
                    onRetry = onRetry,
                )
                state.query.isBlank() || !state.searched && !state.isLoading ->
                    Box(modifier = Modifier.fillMaxSize())
                state.results.isEmpty() && state.searched && !state.isLoading -> NoSearchResults(
                    query = state.query,
                )
                else -> SearchResults(
                    serverUrl = state.serverUrl,
                    results = state.results,
                    onItemSelected = onItemSelected,
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChanged("") },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.clear_search),
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Search,
        ),
        shape = MaterialTheme.shapes.extraLarge,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SearchError(
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
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
            Button(
                onClick = onRetry,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun NoSearchResults(query: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.no_search_results, query),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun SearchResults(
    serverUrl: String,
    results: List<LoomItem>,
    onItemSelected: (LoomItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        itemsIndexed(results, key = { _, item -> item.id }) { index, item ->
            SearchResultItem(
                serverUrl = serverUrl,
                item = item,
                index = index,
                count = results.size,
                onClick = { onItemSelected(item) },
            )
        }
    }
}

@Composable
private fun SearchResultItem(
    serverUrl: String,
    item: LoomItem,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    val isEpisode = item.kind == "episode"
    val kindLabel = stringResource(
        when (item.kind) {
            "movie" -> R.string.movie
            "show" -> R.string.tv_show
            else -> R.string.episode
        },
    )
    val context = listOfNotNull(
        kindLabel,
        if (isEpisode) item.episodeContext() else item.subtitle(),
    ).filter { it.isNotBlank() }.joinToString(" \u00B7 ")
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
                if (isEpisode) {
                    MediaArtwork(
                        url = item.backdropUrl(serverUrl) ?: item.posterUrl(serverUrl),
                        modifier = Modifier
                            .width(120.dp)
                            .aspectRatio(16f / 9f)
                            .clip(MaterialTheme.shapes.medium),
                    )
                } else {
                    MediaArtwork(
                        url = item.posterUrl(serverUrl),
                        modifier = Modifier
                            .width(56.dp)
                            .aspectRatio(2f / 3f)
                            .clip(MaterialTheme.shapes.medium),
                    )
                }
                if (item.progress?.played == true) {
                    WatchedBadge(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                    )
                }
            }
        },
        overlineContent = {
            Text(
                text = context,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        },
        supportingContent = item.overview.takeIf { it.isNotBlank() }?.let { overview ->
            {
                Text(
                    text = overview,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        verticalAlignment = Alignment.Top,
        contentPadding = PaddingValues(12.dp),
    ) {
        Text(
            text = item.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
    }
}
