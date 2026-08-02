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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.R
import xyz.five82.takeup.data.LoomItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MovieListScreen(
    state: MainUiState.Movies,
    onRetry: () -> Unit,
    onChangeServer: () -> Unit,
    onMovieSelected: (LoomItem) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.movies)) },
                actions = {
                    TextButton(onClick = onChangeServer) {
                        Text(stringResource(R.string.change_server))
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
                onChangeServer = onChangeServer,
            )
            state.items.isEmpty() -> EmptyMovieList(contentPadding)
            else -> MovieList(
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
    onChangeServer: () -> Unit,
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
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
                TextButton(onClick = onChangeServer) {
                    Text(stringResource(R.string.change_server))
                }
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
private fun MovieList(
    contentPadding: PaddingValues,
    state: MainUiState.Movies,
    onRetry: () -> Unit,
    onMovieSelected: (LoomItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.server_label, state.serverUrl),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
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
        items(state.items, key = { it.id }) { movie ->
            MovieCard(movie = movie, onClick = { onMovieSelected(movie) })
        }
    }
}

@Composable
private fun MovieCard(movie: LoomItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = if (movie.year > 0) {
                    stringResource(R.string.year_title, movie.title, movie.year)
                } else {
                    movie.title
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (movie.overview.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = movie.overview,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
