package xyz.five82.takeup.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.ui.NavState
import xyz.five82.takeup.ui.Screen
import xyz.five82.takeup.ui.components.CardAction
import xyz.five82.takeup.ui.components.ErrorState
import xyz.five82.takeup.ui.components.LoadingState
import xyz.five82.takeup.ui.components.PosterCard
import xyz.five82.takeup.ui.components.RowLabel
import xyz.five82.takeup.ui.components.ThreadProgress
import xyz.five82.takeup.ui.components.navPillClearance
import xyz.five82.takeup.ui.components.ThumbCard
import xyz.five82.takeup.ui.backdropUrl
import xyz.five82.takeup.ui.episodeLabel
import xyz.five82.takeup.ui.formatRuntime
import xyz.five82.takeup.ui.logoUrl
import xyz.five82.takeup.ui.posterUrl
import xyz.five82.takeup.ui.progressFraction
import xyz.five82.takeup.ui.remainingLabel
import xyz.five82.takeup.ui.thumbUrl
import xyz.five82.takeup.ui.theme.Ember
import xyz.five82.takeup.ui.theme.Ink
import xyz.five82.takeup.ui.theme.Muted
import xyz.five82.takeup.ui.theme.Stage
import xyz.five82.takeup.ui.theme.Violet

data class HomeState(
    val loading: Boolean = true,
    val error: String? = null,
    val continueWatching: List<Item> = emptyList(),
    val nextUp: List<Item> = emptyList(),
    val recentlyAdded: List<Item> = emptyList(),
    val discovery: List<DiscoveryRow> = emptyList(),
)

class HomeViewModel(private val repository: LoomRepository) : ViewModel() {
    var state by mutableStateOf(HomeState())
        private set

    fun refresh() {
        viewModelScope.launch {
            if (state.continueWatching.isEmpty() && state.recentlyAdded.isEmpty()) {
                state = state.copy(loading = true)
            }
            try {
                coroutineScope {
                    val continueWatching = async { repository.api.continueWatching() }
                    val nextUp = async { repository.api.nextUp() }
                    val recentlyAdded = async { repository.api.recentlyAdded() }
                    val movies = async { repository.api.allItems("movies") }
                    val shows = async { repository.api.allItems("tv") }
                    val collections = async { repository.api.collections() }
                    val recentlyPlayed = async { repository.api.recentlyPlayed() }
                    state = HomeState(
                        loading = false,
                        continueWatching = continueWatching.await(),
                        nextUp = nextUp.await(),
                        recentlyAdded = recentlyAdded.await(),
                        discovery = discoveryRows(
                            movies = movies.await(),
                            shows = shows.await(),
                            collections = collections.await(),
                            recentlyPlayed = recentlyPlayed.await(),
                            epochDay = LocalDate.now().toEpochDay(),
                        ),
                    )
                }
            } catch (e: Exception) {
                state = state.copy(
                    loading = false,
                    error = if (state.continueWatching.isEmpty() && state.recentlyAdded.isEmpty()) {
                        e.message ?: "Loom isn't answering"
                    } else {
                        null
                    },
                )
            }
        }
    }

    fun setWatched(item: Item, watched: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (watched) repository.api.markPlayed(item.id) else repository.api.clearPlayed(item.id)
            }
            refresh()
        }
    }
}

@Composable
fun HomeScreen(repository: LoomRepository, nav: NavState, active: Boolean) {
    val model = takeupHomeViewModel(repository)

    // Refresh whenever the home screen surfaces again, so a finished episode
    // leaves Continue Watching without a manual reload.
    LaunchedEffect(active) {
        if (active) model.refresh()
    }

    val state = model.state
    when {
        state.loading -> LoadingState()
        state.error != null -> ErrorState(state.error, onRetry = { model.refresh() })
        else -> HomeContent(repository, nav, model, state)
    }
}

@Composable
private fun takeupHomeViewModel(repository: LoomRepository): HomeViewModel =
    xyz.five82.takeup.ui.takeupViewModel("home") { HomeViewModel(repository) }

@Composable
private fun HomeContent(
    repository: LoomRepository,
    nav: NavState,
    model: HomeViewModel,
    state: HomeState,
) {
    val api = repository.api
    val hero = state.continueWatching.firstOrNull() ?: state.recentlyAdded.firstOrNull()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = navPillClearance())) {
        item(key = "hero") {
            Box {
                if (hero != null) {
                    Hero(hero, api, onOpen = {
                        nav.push(if (hero.isPlayable) Screen.Detail(hero.id) else Screen.Detail(hero.id))
                    })
                } else {
                    Box(Modifier.fillMaxWidth().height(220.dp))
                }
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 4.dp, end = 8.dp),
                ) {
                    RoundIconButton(Icons.Filled.Search, "Search") { nav.push(Screen.Search()) }
                    RoundIconButton(Icons.Filled.Settings, "Settings") { nav.push(Screen.Settings) }
                }
            }
        }

        if (state.continueWatching.isNotEmpty()) {
            item(key = "cw") {
                HomeRow("Continue Watching") {
                    items(state.continueWatching, key = { "cw-${it.id}" }) { item ->
                        ThumbCard(
                            title = item.title,
                            imageUrl = api.thumbUrl(item),
                            line = continueLine(item),
                            progress = progressFraction(item),
                            progressColor = Ember,
                            actions = listOf(
                                CardAction("Play") { nav.push(Screen.Player(item.id)) },
                                CardAction("Mark watched") { model.setWatched(item, true) },
                                CardAction("Remove from Continue Watching") { model.setWatched(item, false) },
                            ),
                            onClick = { nav.push(Screen.Player(item.id)) },
                        )
                    }
                }
            }
        }

        if (state.nextUp.isNotEmpty()) {
            item(key = "next") {
                HomeRow("Next Up") {
                    items(state.nextUp, key = { "nu-${it.id}" }) { item ->
                        ThumbCard(
                            title = item.title,
                            imageUrl = api.thumbUrl(item),
                            line = "${episodeLabel(item)} · ${item.title}",
                            actions = listOf(
                                CardAction("Play") { nav.push(Screen.Player(item.id)) },
                                CardAction("Mark watched") { model.setWatched(item, true) },
                            ),
                            onClick = { nav.push(Screen.Player(item.id)) },
                        )
                    }
                }
            }
        }

        if (state.recentlyAdded.isNotEmpty()) {
            item(key = "recent") {
                HomeRow("Recently Added") {
                    items(state.recentlyAdded, key = { "ra-${it.id}" }) { item ->
                        PosterCard(
                            title = item.title,
                            imageUrl = api.posterUrl(item),
                            onClick = { nav.push(Screen.Detail(item.id)) },
                        )
                    }
                }
            }
        }

        // The rotating shelves: a different slice of the library every day.
        for (discoveryRow in state.discovery) {
            item(key = "d-${discoveryRow.key}") {
                HomeRow(discoveryRow.title, labelColor = Violet) {
                    items(discoveryRow.items, key = { "${discoveryRow.key}-${it.id}" }) { item ->
                        PosterCard(
                            title = item.title,
                            imageUrl = api.posterUrl(item),
                            progress = progressFraction(item),
                            onClick = { nav.push(Screen.Detail(item.id)) },
                        )
                    }
                }
            }
        }
    }
}

private fun continueLine(item: Item): String {
    val remaining = remainingLabel(item)
    return if (item.kind == "episode") {
        listOfNotNull("${episodeLabel(item)} · ${item.title}", remaining).joinToString(" · ")
    } else {
        remaining ?: formatRuntime(item.media?.durationMs ?: 0)
    }
}

@Composable
private fun HomeRow(
    label: String,
    labelColor: Color = Muted,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(Modifier.padding(top = 22.dp)) {
        RowLabel(label, color = labelColor, modifier = Modifier.padding(start = 20.dp, bottom = 10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun Hero(item: Item, api: xyz.five82.takeup.api.LoomApi, onOpen: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(400.dp)
            .clickable(onClick = onOpen),
    ) {
        val backdrop = api.backdropUrl(item, 960) ?: api.thumbUrl(item, 960)
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // The melt: artwork is never hard-cropped against the stage.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Stage.copy(alpha = 0.30f),
                        0.45f to Color.Transparent,
                        1f to Stage,
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp)
                .fillMaxWidth(),
        ) {
            val logo = api.logoUrl(item)
            if (logo != null) {
                AsyncImage(
                    model = logo,
                    contentDescription = item.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .height(84.dp)
                        .wrapContentWidth(Alignment.Start),
                    alignment = Alignment.BottomStart,
                )
            } else {
                Text(
                    item.title,
                    style = MaterialTheme.typography.displayMedium,
                    color = Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val line = when {
                item.progress != null && !item.progress.played ->
                    listOfNotNull("Resume", remainingLabel(item)).joinToString(" · ")
                item.year > 0 -> "New · ${item.year}"
                else -> "New"
            }
            Text(
                line,
                style = MaterialTheme.typography.labelSmall,
                color = Ink.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
            )
            val fraction = progressFraction(item)
            if (fraction != null) {
                ThreadProgress(fraction, Ember, Modifier.fillMaxWidth(0.6f))
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .padding(start = 4.dp)
            .size(48.dp)
            .clip(CircleShape)
            .background(Stage.copy(alpha = 0.55f)),
    ) {
        Icon(icon, contentDescription = description, tint = Ink)
    }
}
