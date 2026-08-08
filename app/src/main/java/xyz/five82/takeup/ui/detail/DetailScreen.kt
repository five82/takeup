package xyz.five82.takeup.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.ui.NavState
import xyz.five82.takeup.ui.Screen
import xyz.five82.takeup.ui.backdropUrl
import xyz.five82.takeup.ui.components.ErrorState
import xyz.five82.takeup.ui.components.LoadingState
import xyz.five82.takeup.ui.components.PersonAvatar
import xyz.five82.takeup.ui.components.RowLabel
import xyz.five82.takeup.ui.components.ThreadProgress
import xyz.five82.takeup.ui.episodeLabel
import xyz.five82.takeup.ui.formatRuntime
import xyz.five82.takeup.ui.logoUrl
import xyz.five82.takeup.ui.posterUrl
import xyz.five82.takeup.ui.progressFraction
import xyz.five82.takeup.ui.remainingLabel
import xyz.five82.takeup.ui.takeupViewModel
import xyz.five82.takeup.ui.techBadges
import xyz.five82.takeup.ui.theme.Ink
import xyz.five82.takeup.ui.theme.Line
import xyz.five82.takeup.ui.theme.Muted
import xyz.five82.takeup.ui.theme.Stage
import xyz.five82.takeup.ui.theme.Surface1
import xyz.five82.takeup.ui.theme.WovenTheme
import xyz.five82.takeup.ui.theme.rememberWovenSeed
import xyz.five82.takeup.ui.thumbUrl

data class DetailState(
    val loading: Boolean = true,
    val error: String? = null,
    val item: Item? = null,
    val seasons: List<Item> = emptyList(),
    val episodesBySeason: Map<Long, List<Item>> = emptyMap(),
    val selectedSeason: Long? = null,
)

class DetailViewModel(
    private val repository: LoomRepository,
    private val itemId: Long,
) : ViewModel() {
    var state by mutableStateOf(DetailState())
        private set

    fun refresh() {
        viewModelScope.launch {
            try {
                val item = repository.api.item(itemId)
                if (item.kind == "show") {
                    val seasons = repository.api.children(itemId)
                        .filter { it.kind == "season" }
                        .sortedBy { it.seasonNumber }
                    val episodes = coroutineScope {
                        seasons.map { season ->
                            async { season.id to repository.api.children(season.id).sortedBy { it.episodeNumber } }
                        }.awaitAll()
                    }.toMap()
                    // Land on the season holding the next unwatched episode;
                    // specials never stand in front of a pilot.
                    val selected = state.selectedSeason
                        ?: seasons.firstOrNull { it.seasonNumber > 0 && it.unwatchedCount > 0 }?.id
                        ?: seasons.firstOrNull { it.unwatchedCount > 0 }?.id
                        ?: seasons.lastOrNull()?.id
                    state = DetailState(
                        loading = false,
                        item = item,
                        seasons = seasons,
                        episodesBySeason = episodes,
                        selectedSeason = selected,
                    )
                } else {
                    state = DetailState(loading = false, item = item)
                }
            } catch (e: Exception) {
                state = state.copy(
                    loading = false,
                    error = if (state.item == null) e.message ?: "Loom isn't answering" else null,
                )
            }
        }
    }

    fun selectSeason(seasonId: Long) {
        state = state.copy(selectedSeason = seasonId)
    }

    fun setWatched(target: Item, watched: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (watched) repository.api.markPlayed(target.id) else repository.api.clearPlayed(target.id)
            }
            refresh()
        }
    }

    /** The episode a show's play button should offer: first not fully watched. */
    fun nextToWatch(): Item? {
        for (season in state.seasons.filter { it.seasonNumber > 0 }) {
            val episode = state.episodesBySeason[season.id]
                ?.firstOrNull { it.progress?.played != true }
            if (episode != null) return episode
        }
        return null
    }
}

@Composable
fun DetailScreen(repository: LoomRepository, nav: NavState, itemId: Long, topmost: Boolean) {
    val model = takeupViewModel("detail-$itemId") { DetailViewModel(repository, itemId) }
    // Refresh whenever this screen surfaces, including the trip back from the
    // player or the artwork picker.
    LaunchedEffect(topmost) {
        if (topmost) model.refresh()
    }

    val state = model.state
    val item = state.item
    when {
        item == null && state.loading -> LoadingState()
        item == null -> ErrorState(state.error ?: "Loom isn't answering", onRetry = { model.refresh() })
        else -> {
            val seed = rememberWovenSeed(item.id, repository.api.posterUrl(item, 240))
            WovenTheme(seed) {
                if (item.kind == "show") {
                    ShowDetail(repository, nav, model, item, state)
                } else {
                    MovieDetail(repository, nav, model, item)
                }
            }
        }
    }
}

// -- movie / short ------------------------------------------------------------

@Composable
private fun MovieDetail(
    repository: LoomRepository,
    nav: NavState,
    model: DetailViewModel,
    item: Item,
) {
    LazyColumn(Modifier.fillMaxSize().background(Stage), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { DetailHead(repository, nav, model, item) }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                MetaLine(item)
                if (!item.tagline.isNullOrEmpty()) {
                    Text(
                        item.tagline,
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = Muted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                PlayControls(nav, item)
                BadgeStrip(item)
                if (!item.overview.isNullOrEmpty()) {
                    Text(
                        item.overview,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Ink.copy(alpha = 0.92f),
                        modifier = Modifier.padding(top = 18.dp),
                    )
                }
                ChapterLine(item)
            }
        }
        creditsSection(nav, item)
    }
}

// -- show ---------------------------------------------------------------------

@Composable
private fun ShowDetail(
    repository: LoomRepository,
    nav: NavState,
    model: DetailViewModel,
    item: Item,
    state: DetailState,
) {
    LazyColumn(Modifier.fillMaxSize().background(Stage), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { DetailHead(repository, nav, model, item) }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                MetaLine(item)
                val next = model.nextToWatch()
                if (next != null) {
                    val started = next.progress != null && !next.progress.played
                    PlayButton(
                        label = "${if (started) "Resume" else "Play"} · ${episodeLabel(next)} · ${next.title}",
                        onClick = { nav.push(Screen.Player(next.id)) },
                    )
                    val fraction = progressFraction(next)
                    if (fraction != null) {
                        ThreadProgress(
                            fraction,
                            MaterialTheme.colorScheme.primary,
                            Modifier.padding(top = 10.dp).fillMaxWidth(0.55f),
                        )
                    }
                }
                if (!item.overview.isNullOrEmpty()) {
                    Text(
                        item.overview,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Ink.copy(alpha = 0.92f),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
        item {
            SeasonChips(state) { model.selectSeason(it) }
        }
        val episodes = state.selectedSeason?.let { state.episodesBySeason[it] }.orEmpty()
        items(episodes, key = { it.id }) { episode ->
            EpisodeRow(repository, nav, model, episode)
        }
        creditsSection(nav, item)
    }
}

@Composable
private fun SeasonChips(state: DetailState, onSelect: (Long) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    ) {
        items(state.seasons, key = { it.id }) { season ->
            val selected = state.selectedSeason == season.id
            val accent = MaterialTheme.colorScheme.primary
            Column(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
                    .border(1.dp, if (selected) accent else Line, RoundedCornerShape(10.dp))
                    .clickable { onSelect(season.id) }
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    season.title.ifEmpty { "Season ${season.seasonNumber}" },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Ink else Muted,
                )
                if (season.unwatchedCount > 0) {
                    Text(
                        "${season.unwatchedCount} left",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) accent else Muted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRow(
    repository: LoomRepository,
    nav: NavState,
    model: DetailViewModel,
    episode: Item,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { nav.push(Screen.Player(episode.id)) },
                    onLongClick = { menuOpen = true },
                )
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Box(
                Modifier
                    .width(136.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface1),
            ) {
                val thumb = repository.api.thumbUrl(episode, 480)
                if (thumb != null) {
                    AsyncImage(
                        model = thumb,
                        contentDescription = episode.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                val fraction = progressFraction(episode)
                if (fraction != null) {
                    ThreadProgress(
                        fraction,
                        MaterialTheme.colorScheme.primary,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${episode.episodeNumber} · ${episode.title}",
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (episode.progress?.played == true) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Watched",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 6.dp).height(16.dp),
                        )
                    }
                }
                val runtime = episode.media?.durationMs?.takeIf { it > 0 }?.let { formatRuntime(it) }
                if (runtime != null) {
                    Text(
                        listOfNotNull(runtime, remainingLabel(episode)).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (!episode.overview.isNullOrEmpty()) {
                    Text(
                        episode.overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            val watched = episode.progress?.played == true
            DropdownMenuItem(
                text = { Text(if (watched) "Mark unwatched" else "Mark watched") },
                onClick = {
                    menuOpen = false
                    model.setWatched(episode, !watched)
                },
            )
        }
    }
}

// -- shared pieces ------------------------------------------------------------

/** Backdrop melting into the stage, logo art over it, back and overflow above. */
@Composable
private fun DetailHead(
    repository: LoomRepository,
    nav: NavState,
    model: DetailViewModel,
    item: Item,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().height(300.dp)) {
        val backdrop = repository.api.backdropUrl(item, 960)
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Stage.copy(alpha = 0.35f),
                        0.5f to Color.Transparent,
                        1f to Stage,
                    ),
                ),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 4.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HeadIconButton(onClick = { nav.pop() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            Box {
                HeadIconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Ink)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    val watched = item.progress?.played == true ||
                        (item.kind == "show" && item.episodeCount > 0 && item.unwatchedCount == 0)
                    DropdownMenuItem(
                        text = { Text(if (watched) "Mark unwatched" else "Mark watched") },
                        onClick = {
                            menuOpen = false
                            model.setWatched(item, !watched)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Artwork...") },
                        onClick = {
                            menuOpen = false
                            nav.push(Screen.Artwork(item.id, item.title))
                        },
                    )
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
        ) {
            val logo = repository.api.logoUrl(item)
            if (logo != null) {
                AsyncImage(
                    model = logo,
                    contentDescription = item.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomStart,
                    modifier = Modifier
                        .fillMaxWidth(0.66f)
                        .height(76.dp),
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
        }
    }
}

@Composable
private fun HeadIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Stage.copy(alpha = 0.55f)),
        content = content,
    )
}

@Composable
private fun MetaLine(item: Item) {
    val parts = mutableListOf<String>()
    if (item.year > 0) parts += item.year.toString()
    item.media?.durationMs?.takeIf { it > 0 }?.let { parts += formatRuntime(it) }
    item.contentRating?.takeIf { it.isNotEmpty() }?.let { parts += it }
    if (item.voteAverage > 0) parts += "★ %.1f".format(item.voteAverage)
    if (item.kind == "show") {
        item.status?.takeIf { it.isNotEmpty() }?.let { parts += it }
        if (item.totalSeasons > 0) {
            parts += "${item.totalSeasons} season" + if (item.totalSeasons > 1) "s" else ""
        }
        if (item.unwatchedCount > 0) parts += "${item.unwatchedCount} unwatched"
    }
    item.genres?.take(3)?.map { it.name }?.let { parts += it }
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = Muted,
        modifier = Modifier.padding(top = 14.dp),
    )
}

@Composable
private fun PlayControls(nav: NavState, item: Item) {
    val progress = item.progress
    val resumable = progress != null && !progress.played && progress.resumePositionMs > 0
    PlayButton(
        label = when {
            resumable -> listOfNotNull("Resume", remainingLabel(item)).joinToString(" · ")
            progress?.played == true -> "Play again"
            else -> "Play"
        },
        onClick = { nav.push(Screen.Player(item.id)) },
    )
    val fraction = progressFraction(item)
    if (fraction != null) {
        ThreadProgress(
            fraction,
            MaterialTheme.colorScheme.primary,
            Modifier.padding(top = 10.dp).fillMaxWidth(0.55f),
        )
    }
}

@Composable
private fun PlayButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier.padding(top = 16.dp).height(48.dp),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BadgeStrip(item: Item) {
    val badges = techBadges(item.media)
    if (badges.isEmpty()) return
    Row(
        Modifier.padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (badge in badges) {
            Text(
                badge.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .border(1.dp, Line, RoundedCornerShape(5.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun ChapterLine(item: Item) {
    val chapters = item.media?.chapters ?: return
    if (chapters.isEmpty()) return
    Text(
        "${chapters.size} chapters",
        style = MaterialTheme.typography.labelSmall,
        color = Muted,
        modifier = Modifier.padding(top = 14.dp),
    )
}

/** Director line plus a tappable cast row; a tap searches the person. */
private fun androidx.compose.foundation.lazy.LazyListScope.creditsSection(nav: NavState, item: Item) {
    val credits = item.credits.orEmpty()
    if (credits.isEmpty()) return
    val director = credits.firstOrNull { it.role == "director" }
    val cast = credits.filter { it.role != "director" }
    item(key = "credits") {
        Column(Modifier.padding(top = 24.dp)) {
            RowLabel("Cast", modifier = Modifier.padding(start = 20.dp, bottom = 2.dp))
            if (director != null) {
                Text(
                    "Directed by ${director.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.9f),
                    // Outer/inner padding split keeps the text at the usual
                    // 20dp inset while the tap area reaches 48dp tall.
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { nav.push(Screen.Search(director.name)) }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                )
            }
            if (cast.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    items(cast, key = { "${it.personId}-${it.character}" }) { credit ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { nav.push(Screen.Search(credit.name)) }
                                .padding(vertical = 4.dp),
                        ) {
                            PersonAvatar(credit.name)
                            Text(
                                credit.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = Ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            if (!credit.character.isNullOrEmpty()) {
                                Text(
                                    credit.character,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
