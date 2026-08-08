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
import xyz.five82.takeup.ui.NavState
import xyz.five82.takeup.ui.Screen
import xyz.five82.takeup.ui.components.EmptyState
import xyz.five82.takeup.ui.components.houseLights
import xyz.five82.takeup.ui.episodeLabel
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
    var searched by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            query.debounce(300).distinctUntilChanged().collect { text ->
                val trimmed = text.trim()
                if (trimmed.isEmpty()) {
                    results = emptyList()
                    searched = false
                } else {
                    runCatching { repository.api.search(trimmed) }
                        .onSuccess {
                            results = it
                            searched = true
                        }
                }
            }
        }
    }
}

/**
 * One field over everything Loom indexes: titles and the people credited on
 * them. Loom ranks exact and prefix matches first, so results are shown in
 * server order.
 */
@Composable
fun SearchScreen(repository: LoomRepository, nav: NavState, initialQuery: String) {
    val model = takeupViewModel { SearchViewModel(repository, initialQuery) }
    val query by model.query.collectAsStateWithLifecycle()
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

        if (model.searched && model.results.isEmpty()) {
            EmptyState("Nothing in the library matches \"${query.trim()}\".")
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(model.results, key = { it.id }) { item ->
                SearchResultRow(repository, item) {
                    when (item.kind) {
                        // An episode found by search plays directly; its art
                        // and context line already say where it belongs.
                        "episode" -> nav.push(Screen.Player(item.id))
                        else -> nav.push(Screen.Detail(item.id))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(repository: LoomRepository, item: Item, onClick: () -> Unit) {
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
            val poster = repository.api.posterUrl(item, 240)
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
                    item.seriesTitle,
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
