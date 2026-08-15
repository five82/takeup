package xyz.five82.takeup.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.data.DownloadState
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.data.Reach
import xyz.five82.takeup.data.downloadedRowItems
import xyz.five82.takeup.data.isOfflineError
import xyz.five82.takeup.ui.NavState
import xyz.five82.takeup.ui.Screen
import xyz.five82.takeup.ui.components.CardAction
import xyz.five82.takeup.ui.components.DownloadedGrid
import xyz.five82.takeup.ui.components.EmptyState
import xyz.five82.takeup.ui.components.ErrorState
import xyz.five82.takeup.ui.components.LoadingState
import xyz.five82.takeup.ui.components.OfflineNotice
import xyz.five82.takeup.ui.components.PosterCard
import xyz.five82.takeup.ui.components.houseLights
import xyz.five82.takeup.ui.components.navPillClearance
import xyz.five82.takeup.ui.posterUrl
import xyz.five82.takeup.ui.progressFraction
import xyz.five82.takeup.ui.takeupViewModel
import xyz.five82.takeup.ui.theme.Ink
import xyz.five82.takeup.ui.theme.libraryThread

data class LibraryState(
    val loading: Boolean = true,
    val error: String? = null,
    val offline: Boolean = false,
    val items: List<Item> = emptyList(),
)

class LibraryViewModel(
    private val repository: LoomRepository,
    private val library: String,
) : ViewModel() {
    var state by mutableStateOf(LibraryState())
        private set

    fun refresh(silent: Boolean = false, force: Boolean = false) {
        viewModelScope.launch {
            if (!force && repository.network.reach.value == Reach.Offline) {
                state = LibraryState(loading = false, offline = true)
                return@launch
            }
            if (!silent && state.items.isEmpty()) state = state.copy(loading = true)
            try {
                state = state.copy(
                    loading = false,
                    error = null,
                    offline = false,
                    items = repository.api.allItems(library),
                )
            } catch (e: Exception) {
                if (isOfflineError(e)) {
                    // Stale posters open screens that cannot play, so the grid
                    // goes with the connection rather than lingering.
                    repository.network.markUnreachable()
                    state = LibraryState(loading = false, offline = true)
                } else {
                    state = state.copy(
                        loading = false,
                        error = if (state.items.isEmpty()) e.message ?: "Loom isn't answering" else null,
                    )
                }
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

/** A library tab is nothing but the full A-Z grid; browsing by genre or collection lives on the Browse tab. */
@Composable
fun LibraryScreen(repository: LoomRepository, nav: NavState, library: String, active: Boolean) {
    val model = takeupViewModel("library-$library") { LibraryViewModel(repository, library) }
    val reach by repository.network.reach.collectAsStateWithLifecycle()
    LaunchedEffect(active, reach) {
        if (active) model.refresh(silent = model.state.items.isNotEmpty())
    }

    val state = model.state
    val thread = libraryThread(library)
    // House lights: the wing's thread color pours from the corner, so you
    // know where you are before reading the title.
    Column(Modifier.fillMaxSize().houseLights(thread).statusBarsPadding()) {
        Text(
            when (library) {
                "movies" -> "Movies"
                "tv" -> "TV"
                else -> "Short Films"
            },
            style = MaterialTheme.typography.displaySmall,
            color = Ink,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp),
        )
        when {
            state.loading -> LoadingState()
            state.offline -> OfflineLibrary(repository, nav, thread) {
                repository.network.recheck()
                model.refresh(force = true)
            }
            state.error != null -> ErrorState(state.error, onRetry = { model.refresh() })
            state.items.isEmpty() -> EmptyState("Nothing here yet. Scan the library from Settings.")
            else -> LazyVerticalGrid(
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
                items(state.items, key = { it.id }) { item ->
                    PosterCard(
                        title = item.title,
                        imageUrl = repository.api.posterUrl(item),
                        badgeCount = item.unwatchedCount,
                        progress = progressFraction(item),
                        progressColor = thread,
                        fallbackTint = thread,
                        actions = watchedActions(item, model),
                        onClick = { nav.push(Screen.Detail(item.id)) },
                    )
                }
            }
        }
    }
}

/**
 * A library tab with no Loom. Which library a download came from is a server
 * fact this device does not keep, so every tab offers the same downloads rather
 * than guessing a split that would file the same film under two tabs.
 */
@Composable
private fun OfflineLibrary(
    repository: LoomRepository,
    nav: NavState,
    accent: Color,
    onRetry: () -> Unit,
) {
    val downloads by repository.downloads.downloads.collectAsStateWithLifecycle()
    val reason by repository.network.reason.collectAsStateWithLifecycle()
    val ready = downloadedRowItems(downloads.filter { it.state == DownloadState.Completed })
    DownloadedGrid(
        entries = ready,
        accent = accent,
        onOpen = { nav.push(Screen.Player(it)) },
        bottomPadding = navPillClearance(),
        header = {
            OfflineNotice(
                reason = reason + if (ready.isEmpty()) {
                    " Nothing is downloaded to this device yet."
                } else {
                    " These titles are downloaded on this device."
                },
                onRetry = onRetry,
                onSettings = { nav.push(Screen.Settings) },
            )
        },
    )
}

private fun watchedActions(item: Item, model: LibraryViewModel): List<CardAction> {
    val watched = item.progress?.played == true ||
        (item.kind == "show" && item.episodeCount > 0 && item.unwatchedCount == 0)
    return if (watched) {
        listOf(CardAction("Mark unwatched") { model.setWatched(item, false) })
    } else {
        listOf(CardAction("Mark watched") { model.setWatched(item, true) })
    }
}
