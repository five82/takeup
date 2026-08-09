package xyz.five82.takeup.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.LoomApi
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.ui.NavState
import xyz.five82.takeup.ui.Screen
import xyz.five82.takeup.ui.components.CardAction
import xyz.five82.takeup.ui.components.EmptyState
import xyz.five82.takeup.ui.components.ErrorState
import xyz.five82.takeup.ui.components.LoadingState
import xyz.five82.takeup.ui.components.PosterCard
import xyz.five82.takeup.ui.components.shadowWeave
import xyz.five82.takeup.ui.posterUrl
import xyz.five82.takeup.ui.progressFraction
import xyz.five82.takeup.ui.takeupViewModel
import xyz.five82.takeup.ui.theme.Ink
import xyz.five82.takeup.ui.theme.Violet
import xyz.five82.takeup.ui.theme.genreThread
import xyz.five82.takeup.ui.theme.rememberWovenThreads

/** All movies in one genre, pushed from a Browse chip. */
@Composable
fun GenreGridScreen(repository: LoomRepository, nav: NavState, screen: Screen.GenreGrid) {
    ItemGridScreen(repository, nav, screen.title, genreThread(screen.genreId)) {
        it.allItems("movies", screen.genreId)
    }
}

/** One collection's members, pushed from a Browse cover card. */
@Composable
fun CollectionGridScreen(repository: LoomRepository, nav: NavState, screen: Screen.CollectionGrid) {
    ItemGridScreen(repository, nav, screen.title, Violet) { api ->
        api.collections().firstOrNull { it.slug == screen.slug }?.items.orEmpty()
    }
}

data class ItemGridState(
    val loading: Boolean = true,
    val error: String? = null,
    val items: List<Item> = emptyList(),
)

class ItemGridViewModel(
    private val repository: LoomRepository,
    private val load: suspend (LoomApi) -> List<Item>,
) : ViewModel() {
    var state by mutableStateOf(ItemGridState())
        private set

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent && state.items.isEmpty()) state = state.copy(loading = true)
            try {
                state = state.copy(loading = false, error = null, items = load(repository.api))
            } catch (e: Exception) {
                state = state.copy(
                    loading = false,
                    error = if (state.items.isEmpty()) e.message ?: "Loom isn't answering" else null,
                )
            }
        }
    }

    fun setWatched(item: Item, watched: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (watched) repository.api.markPlayed(item.id) else repository.api.clearPlayed(item.id)
            }
            refresh(silent = true)
        }
    }
}

@Composable
private fun ItemGridScreen(
    repository: LoomRepository,
    nav: NavState,
    title: String,
    accent: Color,
    load: suspend (LoomApi) -> List<Item>,
) {
    val model = takeupViewModel("grid-$title") { ItemGridViewModel(repository, load) }
    LaunchedEffect(Unit) { model.refresh() }

    val state = model.state
    // Shadow weave: the grid's lead poster casts its colors into the top of
    // the screen; the grid's accent holds the room until it decodes.
    val lead = state.items.firstOrNull()
    val swatches = rememberWovenThreads(lead?.let { repository.api.posterUrl(it, 240) }).orEmpty()
    Column(Modifier.fillMaxSize().shadowWeave(swatches, fallback = accent).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, end = 20.dp)) {
            IconButton(onClick = { nav.pop() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            Text(
                title,
                style = MaterialTheme.typography.displaySmall,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            state.loading -> LoadingState()
            state.error != null -> ErrorState(state.error, onRetry = { model.refresh() })
            state.items.isEmpty() -> EmptyState("Nothing here.")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 106.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                items(state.items, key = { it.id }) { item ->
                    PosterCard(
                        title = item.title,
                        imageUrl = repository.api.posterUrl(item),
                        badgeCount = item.unwatchedCount,
                        progress = progressFraction(item),
                        progressColor = accent,
                        fallbackTint = accent,
                        actions = watchedActions(item, model),
                        onClick = { nav.push(Screen.Detail(item.id)) },
                    )
                }
            }
        }
    }
}

private fun watchedActions(item: Item, model: ItemGridViewModel): List<CardAction> {
    val watched = item.progress?.played == true ||
        (item.kind == "show" && item.episodeCount > 0 && item.unwatchedCount == 0)
    return if (watched) {
        listOf(CardAction("Mark unwatched") { model.setWatched(item, false) })
    } else {
        listOf(CardAction("Mark watched") { model.setWatched(item, true) })
    }
}
