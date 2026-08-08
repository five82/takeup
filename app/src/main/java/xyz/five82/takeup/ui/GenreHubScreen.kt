@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package xyz.five82.takeup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.R
import xyz.five82.takeup.data.Genre
import xyz.five82.takeup.data.GenreSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GenreHubScreen(
    state: MainUiState.GenreHub,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onGenreSelected: (Genre) -> Unit,
) {
    BackHandler(onBack = onBack)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // Transparent so the app-level ambient glow reads behind the grid.
        containerColor = Color.Transparent,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.genres)) },
                navigationIcon = { NavigationBackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        when {
            state.isLoading && state.genres.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
            state.error != null && state.genres.isEmpty() -> FullScreenError(
                message = state.error,
                onRetry = onRetry,
                modifier = Modifier.padding(contentPadding),
            )
            state.genres.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.no_genres))
            }
            else -> GenreGrid(
                contentPadding = contentPadding,
                genres = state.genres,
                error = state.error,
                onRetry = onRetry,
                onGenreSelected = onGenreSelected,
            )
        }
    }
}

@Composable
private fun GenreGrid(
    contentPadding: PaddingValues,
    genres: List<GenreSummary>,
    error: String?,
    onRetry: () -> Unit,
    onGenreSelected: (Genre) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = 4.dp,
            end = 12.dp,
            bottom = 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        error?.let { message ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                ErrorCard(message = message, onRetry = onRetry)
            }
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
