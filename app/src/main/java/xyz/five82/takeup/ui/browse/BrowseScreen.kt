package xyz.five82.takeup.ui.browse

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import xyz.five82.takeup.api.Collection
import xyz.five82.takeup.api.Genre
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.ui.NavState
import xyz.five82.takeup.ui.Screen
import xyz.five82.takeup.ui.components.EmptyState
import xyz.five82.takeup.ui.components.ErrorState
import xyz.five82.takeup.ui.components.LoadingState
import xyz.five82.takeup.ui.components.PosterCard
import xyz.five82.takeup.ui.components.RowLabel
import xyz.five82.takeup.ui.components.navPillClearance
import xyz.five82.takeup.ui.components.shadowWeave
import xyz.five82.takeup.ui.posterUrl
import xyz.five82.takeup.ui.takeupViewModel
import xyz.five82.takeup.ui.theme.Ink
import xyz.five82.takeup.ui.theme.Muted
import xyz.five82.takeup.ui.theme.Violet
import xyz.five82.takeup.ui.theme.genreThread
import xyz.five82.takeup.ui.theme.rememberWovenThreads

data class BrowseState(
    val loading: Boolean = true,
    val error: String? = null,
    val collections: List<Collection> = emptyList(),
    val genres: List<Genre> = emptyList(),
)

class BrowseViewModel(private val repository: LoomRepository) : ViewModel() {
    var state by mutableStateOf(BrowseState())
        private set

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            val empty = state.collections.isEmpty() && state.genres.isEmpty()
            if (!silent && empty) state = state.copy(loading = true)
            try {
                coroutineScope {
                    val collections = async { repository.api.collections() }
                    val genres = async { repository.api.genres() }
                    state = BrowseState(
                        loading = false,
                        collections = collections.await(),
                        genres = genres.await(),
                    )
                }
            } catch (e: Exception) {
                state = state.copy(
                    loading = false,
                    error = if (empty) e.message ?: "Loom isn't answering" else null,
                )
            }
        }
    }
}

/**
 * The two ways to slice the movie library, side by side: every collection as
 * a cover card, every genre as a chip. Both open a filtered grid.
 */
@Composable
fun BrowseScreen(repository: LoomRepository, nav: NavState, active: Boolean) {
    val model = takeupViewModel("browse") { BrowseViewModel(repository) }
    LaunchedEffect(active) {
        if (active) {
            model.refresh(silent = model.state.collections.isNotEmpty() || model.state.genres.isNotEmpty())
        }
    }

    val state = model.state
    // Shadow weave: the first collection's cover casts its colors into the
    // top of the screen; violet holds the room until it decodes.
    val lead = state.collections.firstOrNull()?.items?.firstOrNull()
    val swatches = rememberWovenThreads(lead?.let { repository.api.posterUrl(it, 240) }).orEmpty()
    Column(Modifier.fillMaxSize().shadowWeave(swatches, fallback = Violet).statusBarsPadding()) {
        Text(
            "Browse",
            style = MaterialTheme.typography.displaySmall,
            color = Ink,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp),
        )
        when {
            state.loading -> LoadingState()
            state.error != null -> ErrorState(state.error, onRetry = { model.refresh() })
            state.collections.isEmpty() && state.genres.isEmpty() ->
                EmptyState("Nothing to browse yet. Scan the library from Settings.")
            else -> BrowseContent(state, repository, nav)
        }
    }
}

@Composable
private fun BrowseContent(state: BrowseState, repository: LoomRepository, nav: NavState) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 106.dp),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 8.dp,
            bottom = navPillClearance(),
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.collections.isNotEmpty()) {
            item(key = "collections-label", span = { GridItemSpan(maxLineSpan) }) {
                RowLabel("Collections", color = Violet, modifier = Modifier.padding(top = 4.dp))
            }
            items(state.collections, key = { "col-${it.slug}" }) { collection ->
                // The first member's poster fronts the collection; Loom
                // guarantees at least two owned members.
                PosterCard(
                    title = collection.title,
                    imageUrl = collection.items.firstOrNull()?.let { repository.api.posterUrl(it) },
                    badgeCount = collection.items.size,
                    badgeColor = Violet,
                    fallbackTint = Violet,
                    onClick = { nav.push(Screen.CollectionGrid(collection.slug, collection.title)) },
                )
            }
        }
        if (state.genres.isNotEmpty()) {
            item(key = "genres-label", span = { GridItemSpan(maxLineSpan) }) {
                RowLabel("Genres", modifier = Modifier.padding(top = 18.dp))
            }
            item(key = "genres", span = { GridItemSpan(maxLineSpan) }) {
                GenreCloud(state.genres) { genre ->
                    nav.push(Screen.GenreGrid(genre.id, genre.name))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreCloud(genres: List<Genre>, onSelect: (Genre) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (genre in genres) {
            GenreChip(genre, onClick = { onSelect(genre) })
        }
    }
}

@Composable
private fun GenreChip(genre: Genre, onClick: () -> Unit) {
    // Each chip keeps a hint of its thread, so the cloud reads as a band of
    // colored thread rather than a gray fence.
    val color = genreThread(genre.id)
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            "${genre.name} · ${genre.itemCount}",
            style = MaterialTheme.typography.labelLarge,
            color = Muted,
        )
    }
}
