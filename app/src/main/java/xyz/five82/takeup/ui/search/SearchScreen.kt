package xyz.five82.takeup.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.data.Reach
import xyz.five82.takeup.data.isOfflineError
import xyz.five82.takeup.ui.NavState
import xyz.five82.takeup.ui.Screen
import xyz.five82.takeup.ui.components.EmptyState
import xyz.five82.takeup.ui.components.houseLights
import xyz.five82.takeup.ui.episodeLabel
import xyz.five82.takeup.ui.posterFor
import xyz.five82.takeup.ui.posterUrl
import xyz.five82.takeup.ui.takeupViewModel
import xyz.five82.takeup.ui.theme.Amber
import xyz.five82.takeup.ui.theme.Ember
import xyz.five82.takeup.ui.theme.Ink
import xyz.five82.takeup.ui.theme.Muted
import xyz.five82.takeup.ui.theme.Surface1
import xyz.five82.takeup.ui.theme.Teal

class SearchViewModel(private val repository: LoomRepository, initialQuery: String) : ViewModel() {
    val query = MutableStateFlow(initialQuery)
    var results by mutableStateOf<List<Item>>(emptyList())
        private set

    /** Hits from the offline catalog: shows, films and episodes on this device. */
    var offlineResults by mutableStateOf<List<Item>>(emptyList())
        private set
    var offline by mutableStateOf(false)
        private set
    var searched by mutableStateOf(false)
        private set
    var closestMatches by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            query.debounce(300).distinctUntilChanged().collect { text ->
                val trimmed = text.trim()
                if (trimmed.isEmpty()) {
                    results = emptyList()
                    offlineResults = emptyList()
                    searched = false
                    closestMatches = false
                    offline = repository.network.reach.value == Reach.Offline
                } else if (repository.network.reach.value == Reach.Offline) {
                    searchDownloads(trimmed)
                } else {
                    runCatching { repository.api.search(trimmed) }
                        .onSuccess { response ->
                            offline = false
                            results = response.items
                            offlineResults = emptyList()
                            searched = true
                            closestMatches = response.fuzzy && response.items.isNotEmpty()
                        }
                        .onFailure { error ->
                            if (isOfflineError(error)) {
                                repository.network.markUnreachable()
                                searchDownloads(trimmed)
                            }
                        }
                }
            }
        }
    }

    private fun searchDownloads(query: String) {
        offline = true
        results = emptyList()
        offlineResults = repository.offlineCatalog.value.search(query)
        searched = true
        closestMatches = false
    }
}

/**
 * One field over everything Loom indexes: titles and the people credited on
 * them. Loom matches on word starts and ranks exact and prefix matches first.
 * When those find nothing, it returns one-edit fuzzy matches marked as closest
 * matches. Results are always shown in server order.
 */
@Composable
fun SearchScreen(repository: LoomRepository, nav: NavState, initialQuery: String) {
    val model = takeupViewModel { SearchViewModel(repository, initialQuery) }
    val query by model.query.collectAsStateWithLifecycle()
    val catalog by repository.offlineCatalog.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (initialQuery.isEmpty()) focusRequester.requestFocus()
    }

    Column(
        Modifier
            .fillMaxSize()
            .houseLights(Ember)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, end = 12.dp)) {
            IconButton(onClick = { nav.pop() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { model.query.value = it },
                placeholder = { Text("Titles and people") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { model.query.value = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Muted)
                        }
                    }
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }

        if (model.offline) {
            Text(
                "Offline · searching what is downloaded on this device",
                style = MaterialTheme.typography.labelLarge,
                color = Muted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        if (model.searched && model.results.isEmpty() && model.offlineResults.isEmpty()) {
            EmptyState(
                if (model.offline) {
                    "Nothing downloaded matches \"${query.trim()}\"."
                } else {
                    "Nothing in the library matches \"${query.trim()}\"."
                },
            )
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            if (model.closestMatches) {
                item {
                    Text(
                        "Closest matches",
                        style = MaterialTheme.typography.labelLarge,
                        color = Muted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            items(model.results, key = { it.id }) { item ->
                SearchResultRow(item, repository.api.posterUrl(item, 240)) {
                    nav.push(Screen.Detail(item.id))
                }
            }
            // The same row and the same destination as an online hit; only the
            // poster differs, because offline it is a file on disk.
            items(model.offlineResults, key = { "dl-${it.id}" }) { item ->
                SearchResultRow(
                    item = item,
                    poster = repository.posterFor(item, offline = true, widthPx = 240),
                    seriesTitle = catalog.showFor(item.id)?.title,
                ) {
                    nav.push(Screen.Detail(item.id))
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    item: Item,
    poster: String?,
    seriesTitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(52.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .background(Surface1),
        ) {
            if (poster != null) {
                AsyncImage(
                    model = poster,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val context = when (item.kind) {
                "episode" -> listOfNotNull(
                    seriesTitle ?: item.seriesTitle,
                    episodeLabel(item),
                ).joinToString(" · ")
                else -> item.year.takeIf { it > 0 }?.toString()
            }
            if (!context.isNullOrEmpty()) {
                Text(
                    context,
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        KindTag(item.kind)
    }
}

@Composable
private fun KindTag(kind: String) {
    val (label, color) = when (kind) {
        "movie" -> "Movie" to Ember
        "show" -> "Show" to Teal
        "episode" -> "Episode" to Amber
        else -> kind.replaceFirstChar { it.uppercase() } to Muted
    }
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .padding(start = 10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}
