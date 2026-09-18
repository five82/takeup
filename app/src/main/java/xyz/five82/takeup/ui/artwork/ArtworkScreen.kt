package xyz.five82.takeup.ui.artwork

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import xyz.five82.takeup.api.ImageOption
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.ui.NavState
import xyz.five82.takeup.ui.components.EmptyState
import xyz.five82.takeup.ui.components.LoadingState
import xyz.five82.takeup.ui.takeupViewModel
import xyz.five82.takeup.ui.theme.Ink
import xyz.five82.takeup.ui.theme.Line
import xyz.five82.takeup.ui.theme.Muted
import xyz.five82.takeup.ui.theme.Stage
import xyz.five82.takeup.ui.theme.Surface1

private val KINDS = listOf("poster", "backdrop", "logo", "thumb")

class ArtworkViewModel(
    private val repository: LoomRepository,
    private val itemId: Long,
) : ViewModel() {
    var kind by mutableStateOf("poster")
    val options = mutableStateMapOf<String, List<ImageOption>>()
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)

    fun load() {
        viewModelScope.launch {
            loading = true
            runCatching { repository.api.imageOptions(itemId, kind) }
                .onSuccess {
                    options[kind] = sortArtworkOptions(it)
                    error = null
                }
                .onFailure { error = it.message }
            loading = false
        }
    }

    fun selectKind(newKind: String) {
        kind = newKind
        if (options[newKind] == null) load()
    }

    // Optimistic: Loom downloads the full-size original before answering, which
    // takes seconds on a slow path to TMDB, so the grid updates immediately and
    // the request runs behind it. On failure the previous selection comes back.
    fun choose(option: ImageOption) {
        val chosenKind = kind
        val previous = options[chosenKind] ?: return
        options[chosenKind] = applySelection(previous, option)
        error = null
        viewModelScope.launch {
            runCatching { repository.api.selectImage(itemId, chosenKind, option.provider, option.providerPath) }
                .onFailure {
                    options[chosenKind] = previous
                    error = it.message
                }
        }
    }

    fun reset() {
        viewModelScope.launch {
            busy = true
            runCatching { repository.api.resetImage(itemId, kind) }
                .onFailure { error = it.message }
            busy = false
            load()
        }
    }
}

internal fun applySelection(options: List<ImageOption>, chosen: ImageOption): List<ImageOption> =
    options.map {
        it.copy(selected = it.provider == chosen.provider && it.providerPath == chosen.providerPath)
    }

internal fun sortArtworkOptions(options: List<ImageOption>): List<ImageOption> =
    options.sortedWith(
        compareByDescending<ImageOption> { it.width.toLong() * it.height }
            .thenByDescending { it.voteAverage }
            .thenByDescending { it.voteCount },
    )

/**
 * Loom's curation surface: browse TMDB's options for each artwork kind,
 * pick one, or hand the choice back to the default. Thumbnails load from
 * TMDB directly, so this screen needs the internet, not just the LAN.
 */
@Composable
fun ArtworkScreen(repository: LoomRepository, nav: NavState, itemId: Long, title: String) {
    val model = takeupViewModel("artwork-$itemId") { ArtworkViewModel(repository, itemId) }
    LaunchedEffect(Unit) {
        if (model.options[model.kind] == null) model.load()
    }

    Column(Modifier.fillMaxSize().background(Stage).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, end = 20.dp)) {
            IconButton(onClick = { nav.pop() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            Text(
                "Artwork · $title",
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (kind in KINDS) {
                val selected = model.kind == kind
                val accent = MaterialTheme.colorScheme.primary
                Text(
                    kind.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Ink else Muted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) accent.copy(alpha = 0.2f) else Color.Transparent)
                        .border(1.dp, if (selected) accent else Line, RoundedCornerShape(50))
                        .clickable { model.selectKind(kind) }
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(horizontal = 16.dp)
                        .wrapContentHeight(),
                )
            }
        }

        val current = model.options[model.kind]
        when {
            model.loading && current == null -> LoadingState()
            model.error != null && current == null ->
                EmptyState(model.error ?: "Artwork options are unavailable")
            current.isNullOrEmpty() ->
                EmptyState("TMDB has no ${model.kind} options for this title.")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if (model.kind == "poster") 104.dp else 156.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().weight(1f),
            ) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Column {
                        OutlinedButton(onClick = { model.reset() }, enabled = !model.busy) {
                            Text("Reset to default")
                        }
                        model.error?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                items(current, key = { it.provider + it.providerPath }) { option ->
                    OptionCell(option, model.kind, enabled = !model.busy) { model.choose(option) }
                }
            }
        }
    }
}

@Composable
private fun OptionCell(option: ImageOption, kind: String, enabled: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val aspect = if (kind == "poster") 2f / 3f else 16f / 9f
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface1)
            .border(
                width = if (option.selected) 2.dp else 1.dp,
                color = if (option.selected) accent else Line,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        AsyncImage(
            model = option.thumbnailUrl,
            contentDescription = null,
            // Logos have arbitrary shapes; never crop them.
            contentScale = if (kind == "logo") ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier.fillMaxSize().padding(if (kind == "logo") 12.dp else 0.dp),
        )
        if (option.selected) {
            Text(
                "SELECTED",
                style = MaterialTheme.typography.labelSmall,
                color = Ink,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(accent.copy(alpha = 0.85f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
