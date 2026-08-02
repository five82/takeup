package xyz.five82.takeup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.R
import xyz.five82.takeup.data.LoomItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MovieListScreen(
    state: MainUiState.Movies,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onMovieSelected: (LoomItem) -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.movies)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.back))
                    }
                },
            )
        },
    ) { contentPadding ->
        when {
            state.isLoading && state.items.isEmpty() -> LoadingMovies(contentPadding)
            state.error != null && state.items.isEmpty() -> MovieListError(
                contentPadding = contentPadding,
                message = state.error,
                onRetry = onRetry,
            )
            state.items.isEmpty() -> EmptyMovieList(contentPadding)
            else -> MovieGrid(
                contentPadding = contentPadding,
                state = state,
                onRetry = onRetry,
                onMovieSelected = onMovieSelected,
            )
        }
    }
}

@Composable
private fun LoadingMovies(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.loading_movies))
        }
    }
}

@Composable
private fun MovieListError(
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
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun EmptyMovieList(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.no_movies))
    }
}

@Composable
private fun MovieGrid(
    contentPadding: PaddingValues,
    state: MainUiState.Movies,
    onRetry: () -> Unit,
    onMovieSelected: (LoomItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 132.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.isLoading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        state.error?.let { error ->
            item(span = { GridItemSpan(maxLineSpan) }) {
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
        items(state.items, key = { it.id }) { movie ->
            MediaCard(
                serverUrl = state.serverUrl,
                item = movie,
                onClick = { onMovieSelected(movie) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
