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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material3.TopAppBarDefaults
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
import xyz.five82.takeup.data.Genre
import xyz.five82.takeup.data.GenreSummary
import xyz.five82.takeup.data.LoomItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SearchScreen(
    state: MainUiState.Search,
    modifier: Modifier = Modifier,
    onQueryChanged: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onItemSelected: (LoomItem) -> Unit,
    onGenreSelected: (Genre) -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = modifier,
        // Transparent so the app-level ambient glow reads behind the results.
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { SearchField(query = state.query, onQueryChanged = onQueryChanged) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
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
                state.error != null && state.results.isEmpty() -> FullScreenError(
                    message = state.error,
                    onRetry = onRetry,
                )
                state.query.isBlank() || !state.searched && !state.isLoading ->
                    SearchBrowse(
                        genres = state.genres,
                        onGenreSelected = onGenreSelected,
                    )
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
    // Autofocus only for a fresh search; returning to an existing query via the
    // toolbar should not pop the keyboard.
    LaunchedEffect(Unit) {
        if (query.isEmpty()) focusRequester.requestFocus()
    }
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

/**
 * Empty-query state: browse-by-genre cards (a la streaming search pages),
 * falling back to a plain hint until genres are known.
 */
@Composable
private fun SearchBrowse(
    genres: List<GenreSummary>,
    onGenreSelected: (Genre) -> Unit,
) {
    if (genres.isEmpty()) {
        SearchIdleHint()
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = 8.dp,
            end = 12.dp,
            bottom = BottomToolbarInset,
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = stringResource(R.string.browse_by_genre),
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
        }
        items(genres, key = { it.id }) { genre ->
            GenreCard(
                name = genre.name,
                itemCount = genre.itemCount,
                onClick = { onGenreSelected(Genre(id = genre.id, name = genre.name)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.8f),
            )
        }
    }
}

@Composable
private fun SearchIdleHint() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.search_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
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
        contentPadding = PaddingValues(bottom = BottomToolbarInset),
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
                            .itemArtworkSharedBounds(item.id)
                            .clip(MaterialTheme.shapes.medium),
                    )
                } else {
                    MediaArtwork(
                        url = item.posterUrl(serverUrl),
                        modifier = Modifier
                            .width(56.dp)
                            .aspectRatio(2f / 3f)
                            .itemArtworkSharedBounds(item.id)
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
