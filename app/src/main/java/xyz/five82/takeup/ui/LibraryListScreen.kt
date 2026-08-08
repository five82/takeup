@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package xyz.five82.takeup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.R
import xyz.five82.takeup.data.GenreSummary
import xyz.five82.takeup.data.LoomItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LibraryListScreen(
    state: MainUiState.Library,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onGenreSelected: (Long) -> Unit,
    onItemSelected: (LoomItem) -> Unit,
) {
    BackHandler(onBack = onBack)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // Transparent so the app-level ambient glow reads behind the grid.
        containerColor = Color.Transparent,
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            when (state.kind) {
                                LibraryKind.Movies -> R.string.movies
                                LibraryKind.Shorts -> R.string.shorts
                                LibraryKind.Shows -> R.string.shows
                            },
                        ),
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
        when {
            state.isLoading && state.items.isEmpty() -> LoadingLibrary(
                contentPadding,
                state.kind,
            )
            state.error != null && state.items.isEmpty() -> FullScreenError(
                message = state.error,
                onRetry = onRetry,
                modifier = Modifier.padding(contentPadding),
            )
            state.items.isEmpty() && state.genres.isEmpty() -> EmptyLibraryList(
                contentPadding,
                state.kind,
            )
            else -> LibraryGrid(
                contentPadding = contentPadding,
                state = state,
                onRetry = onRetry,
                onGenreSelected = onGenreSelected,
                onItemSelected = onItemSelected,
            )
        }
    }
}

@Composable
private fun LoadingLibrary(
    contentPadding: PaddingValues,
    kind: LibraryKind,
) {
    val description = stringResource(
        when (kind) {
            LibraryKind.Movies -> R.string.loading_movies
            LibraryKind.Shorts -> R.string.loading_shorts
            LibraryKind.Shows -> R.string.loading_shows
        },
    )
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 132.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .semantics { contentDescription = description },
        contentPadding = PaddingValues(
            start = 12.dp,
            top = 12.dp,
            end = 12.dp,
            bottom = BottomToolbarInset,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(6) {
            PosterCardPlaceholder()
        }
    }
}

@Composable
private fun EmptyLibraryList(
    contentPadding: PaddingValues,
    kind: LibraryKind,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(
                when (kind) {
                    LibraryKind.Movies -> R.string.no_movies
                    LibraryKind.Shorts -> R.string.no_shorts
                    LibraryKind.Shows -> R.string.no_shows
                },
            ),
        )
    }
}

@Composable
private fun LibraryGrid(
    contentPadding: PaddingValues,
    state: MainUiState.Library,
    onRetry: () -> Unit,
    onGenreSelected: (Long) -> Unit,
    onItemSelected: (LoomItem) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = onRetry,
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 132.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
            start = 12.dp,
            top = 12.dp,
            end = 12.dp,
            bottom = BottomToolbarInset,
        ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.genres.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    GenreFilterRow(
                        genres = state.genres,
                        selectedGenreId = state.selectedGenreId,
                        onGenreSelected = onGenreSelected,
                    )
                }
            }
            if (state.items.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.no_movies_for_genre),
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.error?.let { error ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ErrorCard(message = error, onRetry = onRetry)
                }
            }
            items(state.items, key = { it.id }) { item ->
                MediaCard(
                    serverUrl = state.serverUrl,
                    item = item,
                    onClick = { onItemSelected(item) },
                    modifier = Modifier.fillMaxWidth(),
                    sharedArtwork = true,
                )
            }
        }
    }
}

@Composable
private fun GenreFilterRow(
    genres: List<GenreSummary>,
    selectedGenreId: Long,
    onGenreSelected: (Long) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = 0L) {
            FilterChip(
                selected = selectedGenreId == 0L,
                onClick = { onGenreSelected(0L) },
                label = { Text(stringResource(R.string.all_genres)) },
            )
        }
        rowItems(genres, key = { it.id }) { genre ->
            FilterChip(
                selected = selectedGenreId == genre.id,
                onClick = { onGenreSelected(genre.id) },
                label = { Text(genre.name) },
            )
        }
    }
}
