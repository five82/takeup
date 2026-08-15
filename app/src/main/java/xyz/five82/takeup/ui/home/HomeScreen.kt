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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.animation.core.animateDpAsState
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.data.OfflineCatalog
import xyz.five82.takeup.data.Reach
import xyz.five82.takeup.data.isOfflineError
import xyz.five82.takeup.ui.NavState
import xyz.five82.takeup.ui.Screen
import xyz.five82.takeup.ui.components.BiasCutBackdrop
import xyz.five82.takeup.ui.components.CardAction
import xyz.five82.takeup.ui.components.logoLaneHeight
import xyz.five82.takeup.ui.components.ErrorState
import xyz.five82.takeup.ui.components.LoadingState
import xyz.five82.takeup.ui.components.OfflineBanner
import xyz.five82.takeup.ui.components.OfflineNotice
import xyz.five82.takeup.ui.components.PosterCard
import xyz.five82.takeup.ui.components.ThreadProgress
import xyz.five82.takeup.ui.components.navPillClearance
import xyz.five82.takeup.ui.components.dyeBath
import xyz.five82.takeup.ui.components.houseLights
import xyz.five82.takeup.ui.components.threeThreads
import xyz.five82.takeup.ui.components.ThumbCard
import xyz.five82.takeup.ui.backdropFor
import xyz.five82.takeup.ui.backdropUrl
import xyz.five82.takeup.ui.episodeLabel
import xyz.five82.takeup.ui.formatRuntime
import xyz.five82.takeup.ui.logoFor
import xyz.five82.takeup.ui.logoUrl
import xyz.five82.takeup.ui.posterFor
import xyz.five82.takeup.ui.posterUrl
import xyz.five82.takeup.ui.progressFraction
import xyz.five82.takeup.ui.remainingLabel
import xyz.five82.takeup.ui.thumbFor
import xyz.five82.takeup.ui.thumbUrl
import xyz.five82.takeup.ui.theme.Ember
import xyz.five82.takeup.ui.theme.Ink
import xyz.five82.takeup.ui.theme.Muted
import xyz.five82.takeup.ui.theme.Stage
import xyz.five82.takeup.ui.theme.Violet
import xyz.five82.takeup.ui.theme.rememberWovenThreads

data class HomeState(
    val loading: Boolean = true,
    val error: String? = null,
    val offline: Boolean = false,
    val continueWatching: List<Item> = emptyList(),
    val nextUp: List<Item> = emptyList(),
    val recentlyAdded: List<Item> = emptyList(),
    val dailyPick: Item? = null,
    val discovery: List<DiscoveryRow> = emptyList(),
)

class HomeViewModel(private val repository: LoomRepository) : ViewModel() {
    var state by mutableStateOf(HomeState())
        private set

    // A scan refreshes the library, but must not redraw the current slot's
    // pick. The next scheduled slot is the only time this cache is replaced.
    private var cachedPickSlot: Long? = null

    /**
     * [force] is what the Try again button presses: the user is asking for the
     * attempt itself, so a stale offline verdict must not answer for the server.
     */
    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            // Ask the policy before the network. Seven parallel calls that can
            // only time out is what made the app feel like a streaming service
            // that had simply gone down.
            if (!force && repository.network.reach.value == Reach.Offline) {
                state = HomeState(loading = false, offline = true)
                return@launch
            }
            if (state.dailyPick == null && state.continueWatching.isEmpty() && state.recentlyAdded.isEmpty()) {
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
                    val movieItems = movies.await()
                    val showItems = shows.await()
                    val now = LocalDateTime.now()
                    val epochDay = now.toLocalDate().toEpochDay()
                    val slot = dailyPickSlot(epochDay, now.hour)
                    val currentPick = state.dailyPick?.let { previous ->
                        // Keep the current slot's item up to date when a scan
                        // returns it, while retaining it if the scan omits it.
                        movieItems.firstOrNull { it.id == previous.id } ?: previous
                    }
                    val pick = dailyPick(
                        movies = movieItems,
                        epochDay = epochDay,
                        hour = now.hour,
                        previousSlot = cachedPickSlot,
                        previousPick = currentPick,
                    )
                    state = HomeState(
                        loading = false,
                        continueWatching = continueWatching.await(),
                        nextUp = nextUp.await(),
                        recentlyAdded = recentlyAdded.await(),
                        dailyPick = pick,
                        discovery = discoveryRows(
                            movies = movieItems,
                            shows = showItems,
                            collections = collections.await(),
                            recentlyPlayed = recentlyPlayed.await(),
                            epochDay = epochDay,
                        ),
                    )
                    cachedPickSlot = slot
                }
                // The server answered, so anything queued while offline can land.
                repository.flushPendingProgress()
            } catch (e: Exception) {
                if (isOfflineError(e)) {
                    // Drop the cached library rather than keep it: every stale
                    // poster opens a screen that cannot play. Only a connection
                    // failure counts - a Loom bug must not masquerade as being
                    // offline.
                    repository.network.markUnreachable()
                    state = HomeState(loading = false, offline = true)
                } else {
                    state = state.copy(
                        loading = false,
                        offline = false,
                        error = if (
                            state.dailyPick == null &&
                            state.continueWatching.isEmpty() &&
                            state.recentlyAdded.isEmpty()
                        ) {
                            e.message ?: "Loom isn't answering"
                        } else {
                            null
                        },
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
            refresh()
        }
    }
}

@Composable
fun HomeScreen(repository: LoomRepository, nav: NavState, active: Boolean) {
    val model = takeupHomeViewModel(repository)
    val reach by repository.network.reach.collectAsStateWithLifecycle()

    // Refresh whenever home surfaces, then at 6 am and 6 pm for the next pick.
    // A finished episode also leaves Continue Watching without a manual reload.
    // Keying on reach means walking back onto the home network reconnects on
    // its own, rather than leaving the offline screen up until something asks.
    LaunchedEffect(active, reach) {
        if (active) {
            while (true) {
                model.refresh()
                val now = LocalDateTime.now()
                val nextChange = when {
                    now.hour < 6 -> now.toLocalDate().atTime(6, 0)
                    now.hour < 18 -> now.toLocalDate().atTime(18, 0)
                    else -> now.toLocalDate().plusDays(1).atTime(6, 0)
                }
                delay(Duration.between(now, nextChange).toMillis().coerceAtLeast(1_000))
            }
        }
    }

    val state = model.state
    when {
        state.loading -> LoadingState()
        state.offline -> OfflineHome(
            repository,
            nav,
            onRetry = {
                repository.network.recheck()
                model.refresh(force = true)
            },
        )
        state.error != null -> ErrorState(
            state.error,
            onRetry = { model.refresh() },
            secondaryAction = "Server settings",
            onSecondaryAction = { nav.push(Screen.Settings) },
        )
        else -> HomeContent(repository, nav, model, state)
    }
}

/**
 * Home with no Loom: the same room over what is genuinely on this device. The
 * stale library stays absent - cached posters would open screens that cannot
 * play - but a download is a real title, so it gets the hero, the rows and the
 * dye bath that home gives anything else.
 */
@Composable
private fun OfflineHome(repository: LoomRepository, nav: NavState, onRetry: () -> Unit) {
    val catalog by repository.offlineCatalog.collectAsStateWithLifecycle()
    val reason by repository.network.reason.collectAsStateWithLifecycle()
    val continueWatching = catalog.continueWatching()
    val downloaded = catalog.recent()
    // Lead with what is half-watched, else with what landed most recently.
    val hero = continueWatching.firstOrNull() ?: downloaded.firstOrNull()
    val heroThreads = rememberWovenThreads(
        hero?.let { repository.posterFor(it, offline = true, widthPx = 240) },
    ).orEmpty()
    val room = if (heroThreads.isNotEmpty()) {
        Modifier.dyeBath(heroThreads.first()).threeThreads(heroThreads)
    } else {
        Modifier.houseLights(Ember)
    }
    LazyColumn(
        Modifier.fillMaxSize().then(room),
        contentPadding = PaddingValues(bottom = navPillClearance()),
    ) {
        item(key = "offline-head") {
            // The same two controls the hero carries when there is a hero, so
            // settings and searching what is downloaded stay reachable offline.
            Box(Modifier.fillMaxWidth()) {
                if (hero != null) {
                    Hero(
                        item = hero,
                        backdrop = repository.backdropFor(hero, offline = true, widthPx = 960),
                        logo = repository.logoFor(hero, offline = true),
                        label = if (hero == continueWatching.firstOrNull()) {
                            "Continue watching"
                        } else {
                            "Downloaded"
                        },
                        onOpen = { nav.push(Screen.Detail(hero.id)) },
                    )
                } else {
                    OfflineNotice(
                        reason = "$reason Nothing is downloaded to this device yet.",
                        onRetry = onRetry,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(start = 20.dp, end = 20.dp, top = 44.dp),
                    )
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
        if (hero == null) return@LazyColumn
        item(key = "offline-banner") {
            OfflineBanner(reason, onRetry, Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp))
        }
        if (continueWatching.isNotEmpty()) {
            item(key = "offline-cw") {
                HomeRow("Continue Watching") {
                    items(continueWatching, key = { "ocw-${it.id}" }) { item ->
                        ThumbCard(
                            title = item.title,
                            imageUrl = repository.thumbFor(item, offline = true),
                            width = 200,
                            line = offlineLine(item, catalog),
                            lineStyle = MaterialTheme.typography.bodyMedium,
                            progress = progressFraction(item),
                            progressColor = Ember,
                            onClick = { nav.push(Screen.Detail(item.id)) },
                        )
                    }
                }
            }
        }
        item(key = "offline-downloads") {
            HomeRow("Downloaded") {
                items(downloaded, key = { "dl-${it.id}" }) { item ->
                    PosterCard(
                        title = item.title,
                        imageUrl = repository.posterFor(item, offline = true),
                        width = 128,
                        badgeCount = if (item.kind == "show") {
                            catalog.episodes(item.id).count { it.progress?.played != true }
                        } else {
                            0
                        },
                        progress = progressFraction(item),
                        onClick = { nav.push(Screen.Detail(item.id)) },
                    )
                }
            }
        }
    }
}

/** Like [continueLine], but naming the show as well: offline nothing else does. */
private fun offlineLine(item: Item, catalog: OfflineCatalog): String {
    if (item.kind != "episode") {
        return remainingLabel(item) ?: formatRuntime(item.media?.durationMs ?: 0)
    }
    return listOfNotNull(
        catalog.showFor(item.id)?.title,
        "${episodeLabel(item)} · ${item.title}",
        remainingLabel(item),
    ).joinToString(" · ")
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
    // Only the scheduled pick gets the hero. Falling back to the newest movie
    // put whatever landed last under a "Tonight's Pick" label - documentaries
    // and half-watched titles included - and changed it off schedule.
    val hero = state.dailyPick
    val heroLabel = dailyPickLabel(LocalTime.now().hour)
    val continueWatching = state.continueWatching.filterNot { it.id == hero?.id }
    val nextUp = state.nextUp.filterNot { it.id == hero?.id }
    val recentlyAdded = state.recentlyAdded.filterNot { it.id == hero?.id }
    val discovery = state.discovery.mapNotNull { row ->
        row.copy(items = row.items.filterNot { it.id == hero?.id }).takeIf { it.items.isNotEmpty() }
    }
    // The daily pick colors the whole room: its seed dyes the stage, and its
    // swatches sit as still fields of light down the screen. Ember house
    // lights hold the room until the art resolves (and with no hero at all).
    val heroThreads = hero?.let { rememberWovenThreads(api.posterUrl(it, 240)) }.orEmpty()
    val room = if (heroThreads.isNotEmpty()) {
        Modifier.dyeBath(heroThreads.first()).threeThreads(heroThreads)
    } else {
        Modifier.houseLights(Ember)
    }
    LazyColumn(
        Modifier.fillMaxSize().then(room),
        contentPadding = PaddingValues(bottom = navPillClearance()),
    ) {
        item(key = "hero") {
            Box {
                if (hero != null) {
                    Hero(
                        item = hero,
                        backdrop = api.backdropUrl(hero, 960) ?: api.thumbUrl(hero, 960),
                        logo = api.logoUrl(hero),
                        label = heroLabel,
                        onOpen = { nav.push(Screen.Detail(hero.id)) },
                    )
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

        if (continueWatching.isNotEmpty()) {
            item(key = "cw") {
                HomeRow("Continue Watching") {
                    items(continueWatching, key = { "cw-${it.id}" }) { item ->
                        ThumbCard(
                            title = item.title,
                            imageUrl = api.thumbUrl(item),
                            width = 200,
                            line = continueLine(item),
                            lineStyle = MaterialTheme.typography.bodyMedium,
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

        if (nextUp.isNotEmpty()) {
            item(key = "next") {
                HomeRow("Next Up") {
                    items(nextUp, key = { "nu-${it.id}" }) { item ->
                        ThumbCard(
                            title = item.title,
                            imageUrl = api.thumbUrl(item),
                            width = 200,
                            line = "${episodeLabel(item)} · ${item.title}",
                            lineStyle = MaterialTheme.typography.bodyMedium,
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

        if (recentlyAdded.isNotEmpty()) {
            item(key = "recent") {
                HomeRow("Recently Added") {
                    items(recentlyAdded, key = { "ra-${it.id}" }) { item ->
                        PosterCard(
                            title = item.title,
                            imageUrl = api.posterUrl(item),
                            width = 128,
                            onClick = { nav.push(Screen.Detail(item.id)) },
                        )
                    }
                }
            }
        }

        // The rotating shelves: a different slice of the library every day.
        for (discoveryRow in discovery) {
            item(key = "d-${discoveryRow.key}") {
                HomeRow(discoveryRow.title, labelColor = Violet) {
                    items(discoveryRow.items, key = { "${discoveryRow.key}-${it.id}" }) { item ->
                        PosterCard(
                            title = item.title,
                            imageUrl = api.posterUrl(item),
                            width = 128,
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
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.titleMedium.copy(
                letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing,
            ),
            color = labelColor,
            modifier = Modifier.padding(start = 20.dp, bottom = 10.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun Hero(
    item: Item,
    backdrop: String?,
    logo: String?,
    label: String,
    onOpen: () -> Unit,
) {
    // No fixed height: the backdrop sizes itself to 4:3 art plus the logo
    // and resume band, so a tall logo grows the hero instead of squeezing
    // the photo.
    Box(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        // Same tailoring as the detail head: area-normalized logo lane, line
        // riding above it, 76dp of resume line and progress stacked below.
        var logoAspect by remember(item.id) { mutableStateOf<Float?>(null) }
        val lane by animateDpAsState(logoLaneHeight(logoAspect), label = "logoLane")
        val solid by animateDpAsState(
            if (logo != null) logoLaneHeight(logoAspect) + 76.dp else 160.dp,
            label = "biasSolid",
        )
        BiasCutBackdrop(
            imageUrl = backdrop,
            solidLeft = solid,
            modifier = Modifier.fillMaxWidth(),
            contentDescription = item.title,
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp)
                .fillMaxWidth(),
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
            val line = buildList {
                add(label)
                if (item.year > 0) add(item.year.toString())
                item.genres?.firstOrNull()?.name?.let(::add)
            }.joinToString(" · ")
            Text(
                line,
                style = MaterialTheme.typography.bodyMedium,
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
