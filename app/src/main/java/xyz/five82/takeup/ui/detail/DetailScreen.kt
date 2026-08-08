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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.animation.core.animateDpAsState
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.ui.NavState
import xyz.five82.takeup.ui.Screen
import xyz.five82.takeup.ui.backdropUrl
import xyz.five82.takeup.ui.components.BiasCutBackdrop
import xyz.five82.takeup.ui.components.ErrorState
import xyz.five82.takeup.ui.components.logoLaneHeight
import xyz.five82.takeup.ui.components.LoadingState
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
import xyz.five82.takeup.ui.theme.Ember
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

/** Backdrop ending on the bias cut, logo art below it, back and overflow above. */
@Composable
private fun DetailHead(
    repository: LoomRepository,
    nav: NavState,
    model: DetailViewModel,
    item: Item,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // No fixed height: the backdrop sizes itself to 4:3 art plus the logo
    // band, so a tall logo grows the head instead of squeezing the photo.
    Box(Modifier.fillMaxWidth()) {
        val backdrop = repository.api.backdropUrl(item, 960)
        val logo = repository.api.logoUrl(item)
        // Raw seed, not colorScheme.primary: the toned primary washes out to
        // pastel, and the head should match the home hero for the same title.
        val seed = rememberWovenSeed(item.id, repository.api.posterUrl(item, 240))
        // Cut tailored to the logo: the lane is area-normalized once the art
        // decodes, and the line rides just above it. Animated so the first
        // load settles instead of popping.
        var logoAspect by remember(item.id) { mutableStateOf<Float?>(null) }
        val lane by animateDpAsState(logoLaneHeight(logoAspect), label = "logoLane")
        val solid by animateDpAsState(
            if (logo != null) logoLaneHeight(logoAspect) + 22.dp else 116.dp,
            label = "biasSolid",
        )
        BiasCutBackdrop(
            imageUrl = backdrop,
            tint = seed ?: Ember,
            solidLeft = solid,
            modifier = Modifier.fillMaxWidth(),
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
                        text = { Text("Artwork") },
                        onClick = {
                            menuOpen = false
                            nav.push(Screen.Artwork(item.id, item.title))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (watched) "Mark unwatched" else "Mark watched") },
                        onClick = {
                            menuOpen = false
                            model.setWatched(item, !watched)
                        },
                    )
                }
            }
        }
        // Logo lane under the low start of the cut; the line climbs away to
        // the right, so clearance only grows across the lane.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        ) {
            if (logo != null) {
                AsyncImage(
                    model = logo,
                    contentDescription = item.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomStart,
                    onState = { state ->
                        val size = (state as? AsyncImagePainter.State.Success)
                            ?.painter?.intrinsicSize
                        if (size != null && size.width > 0f && size.height > 0f) {
                            logoAspect = size.width / size.height
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(lane),
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

// Loom stores no people photos, so the credits are billing, not headshots:
// a vertical list of name-over-role cards. Full width means names wrap
// instead of truncating.
private const val COLLAPSED_CAST_COUNT = 6

/** Film-style billing, director first; tapping a card searches the person. */
private fun androidx.compose.foundation.lazy.LazyListScope.creditsSection(nav: NavState, item: Item) {
    val credits = item.credits.orEmpty()
    if (credits.isEmpty()) return
    val director = credits.firstOrNull { it.role == "director" }
    val cast = credits.filter { it.role != "director" }
    item(key = "credits") {
        var expanded by rememberSaveable { mutableStateOf(false) }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 24.dp, start = 20.dp, end = 20.dp),
        ) {
            RowLabel("Cast", modifier = Modifier.padding(bottom = 4.dp))
            if (director != null) {
                CreditCard(director.name, "Director", nav)
            }
            val visible = if (expanded) cast else cast.take(COLLAPSED_CAST_COUNT)
            visible.forEach { credit ->
                CreditCard(credit.name, credit.character, nav)
            }
            if (!expanded && cast.size > COLLAPSED_CAST_COUNT) {
                Text(
                    "All ${cast.size} cast members",
                    style = MaterialTheme.typography.titleSmall,
                    color = Muted,
                    // Outer/inner padding split keeps the text at the card
                    // inset while the tap area reaches 48dp tall.
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { expanded = true }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** One person on the bill: name over role, on a card shaped to look tappable. */
@Composable
private fun CreditCard(name: String, role: String?, nav: NavState) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .clickable { nav.push(Screen.Search(name)) }
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        // The scale jumps from 16sp body to 20sp title, so the bill sizes
        // its own type: one step up from body, role a step up from label.
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 25.sp),
            color = Ink,
        )
        if (!role.isNullOrEmpty()) {
            Text(
                role,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = Muted,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}
