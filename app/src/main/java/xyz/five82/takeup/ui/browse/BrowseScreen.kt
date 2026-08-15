package xyz.five82.takeup.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cabin
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import xyz.five82.takeup.api.Collection
import xyz.five82.takeup.api.Genre
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.data.Reach
import xyz.five82.takeup.data.isOfflineError
import xyz.five82.takeup.ui.NavState
import xyz.five82.takeup.ui.Screen
import xyz.five82.takeup.ui.backdropUrl
import xyz.five82.takeup.ui.components.EmptyState
import xyz.five82.takeup.ui.components.ErrorState
import xyz.five82.takeup.ui.components.LoadingState
import xyz.five82.takeup.ui.components.MissingArt
import xyz.five82.takeup.ui.components.OfflineBanner
import xyz.five82.takeup.ui.components.OfflineNotice
import xyz.five82.takeup.ui.components.PosterCard
import xyz.five82.takeup.ui.components.navPillClearance
import xyz.five82.takeup.ui.components.shadowWeave
import xyz.five82.takeup.ui.posterFor
import xyz.five82.takeup.ui.posterUrl
import xyz.five82.takeup.ui.progressFraction
import xyz.five82.takeup.ui.takeupViewModel
import xyz.five82.takeup.ui.theme.Ink
import xyz.five82.takeup.ui.theme.Line
import xyz.five82.takeup.ui.theme.Muted
import xyz.five82.takeup.ui.theme.Stage
import xyz.five82.takeup.ui.theme.Violet
import xyz.five82.takeup.ui.theme.genreDarken
import xyz.five82.takeup.ui.theme.genreField
import xyz.five82.takeup.ui.theme.rememberWovenThreads

data class BrowseState(
    val loading: Boolean = true,
    val error: String? = null,
    val offline: Boolean = false,
    val collections: List<Collection> = emptyList(),
    val genres: List<Genre> = emptyList(),
)

class BrowseViewModel(private val repository: LoomRepository) : ViewModel() {
    var state by mutableStateOf(BrowseState())
        private set

    fun refresh(silent: Boolean = false, force: Boolean = false) {
        viewModelScope.launch {
            if (!force && repository.network.reach.value == Reach.Offline) {
                state = BrowseState(loading = false, offline = true)
                return@launch
            }
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
                if (isOfflineError(e)) {
                    repository.network.markUnreachable()
                    state = BrowseState(loading = false, offline = true)
                } else {
                    state = state.copy(
                        loading = false,
                        error = if (empty) e.message ?: "Loom isn't answering" else null,
                    )
                }
            }
        }
    }
}

/**
 * Two rooms for the two ways to slice the movie library: a segmented switch
 * flips between every collection as a backdrop card and every genre as a
 * flat color tile. Both open a filtered grid.
 */
@Composable
fun BrowseScreen(repository: LoomRepository, nav: NavState, active: Boolean) {
    val model = takeupViewModel("browse") { BrowseViewModel(repository) }
    val reach by repository.network.reach.collectAsStateWithLifecycle()
    LaunchedEffect(active, reach) {
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
            // Collections and genres are server slices of a library this device
            // does not hold, so offline there is nothing here to slice.
            state.offline -> OfflineBrowse(repository, nav) {
                repository.network.recheck()
                model.refresh(force = true)
            }
            state.error != null -> ErrorState(state.error, onRetry = { model.refresh() })
            state.collections.isEmpty() && state.genres.isEmpty() ->
                EmptyState("Nothing to browse yet. Scan the library from Settings.")
            else -> BrowseContent(state, repository, nav)
        }
    }
}

/**
 * Browse with no Loom. Collections and genres are server slices of a library
 * this device does not hold, so what is left to browse is everything downloaded,
 * in one grid.
 */
@Composable
private fun OfflineBrowse(repository: LoomRepository, nav: NavState, onRetry: () -> Unit) {
    val catalog by repository.offlineCatalog.collectAsStateWithLifecycle()
    val reason by repository.network.reason.collectAsStateWithLifecycle()
    val items = catalog.all()
    if (items.isEmpty()) {
        OfflineNotice(
            reason = "$reason Nothing is downloaded to this device yet.",
            onRetry = onRetry,
            onSettings = { nav.push(Screen.Settings) },
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        return
    }
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
        item(key = "offline-banner", span = { GridItemSpan(maxLineSpan) }) {
            OfflineBanner("$reason Collections and genres come from Loom.", onRetry)
        }
        items(items, key = { it.id }) { item ->
            PosterCard(
                title = item.title,
                imageUrl = repository.posterFor(item, offline = true),
                badgeCount = if (item.kind == "show") {
                    catalog.episodes(item.id).count { it.progress?.played != true }
                } else {
                    0
                },
                progress = progressFraction(item),
                progressColor = Violet,
                fallbackTint = Violet,
                onClick = { nav.push(Screen.Detail(item.id)) },
            )
        }
    }
}

private enum class Room { Collections, Genres }

@Composable
private fun BrowseContent(state: BrowseState, repository: LoomRepository, nav: NavState) {
    var room by rememberSaveable { mutableStateOf(Room.Collections) }
    Column(Modifier.fillMaxSize()) {
        RoomSwitch(room, onSelect = { room = it })
        when (room) {
            Room.Collections -> CollectionsRoom(state.collections, repository, nav)
            Room.Genres -> GenresRoom(state.genres, nav)
        }
    }
}

@Composable
private fun RoomSwitch(selected: Room, onSelect: (Room) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 20.dp, bottom = 12.dp),
    ) {
        RoomPill("Collections", active = selected == Room.Collections, onClick = { onSelect(Room.Collections) })
        RoomPill("Genres", active = selected == Room.Genres, onClick = { onSelect(Room.Genres) })
    }
}

@Composable
private fun RoomPill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (active) {
                    Modifier.background(Violet)
                } else {
                    Modifier.border(1.dp, Line, RoundedCornerShape(50))
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) Stage else Muted,
        )
    }
}

/**
 * Every collection as a 16:9 cover: one member's backdrop stands in for the
 * whole set, deduped so collections that share an opening chapter (Spielberg
 * and Indiana Jones both start with Raiders) don't show the same frame twice.
 */
@Composable
private fun CollectionsRoom(collections: List<Collection>, repository: LoomRepository, nav: NavState) {
    if (collections.isEmpty()) {
        EmptyState("No collections yet.")
        return
    }
    val faces = remember(collections) { collectionFaces(collections) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
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
        items(collections, key = { it.slug }) { collection ->
            CollectionCard(
                collection = collection,
                imageUrl = faces[collection.slug]?.let { repository.api.backdropUrl(it, 480) },
                onClick = { nav.push(Screen.CollectionGrid(collection.slug, collection.title)) },
            )
        }
    }
}

/**
 * Picks each collection's cover in server order: the first member whose
 * backdrop is not already claimed by an earlier collection. If every member's
 * backdrop is claimed (or none has one), falls back to the first member with
 * any backdrop; if no member has a backdrop at all, the collection has no face.
 */
fun collectionFaces(collections: List<Collection>): Map<String, Item?> {
    val claimed = mutableSetOf<Long>()
    val faces = mutableMapOf<String, Item?>()
    for (collection in collections) {
        val hasBackdrop = { item: Item -> item.backdropImageId > 0 }
        val face = collection.items.firstOrNull { hasBackdrop(it) && it.backdropImageId !in claimed }
            ?: collection.items.firstOrNull { hasBackdrop(it) }
        if (face != null) claimed += face.backdropImageId
        faces[collection.slug] = face
    }
    return faces
}

@Composable
private fun CollectionCard(collection: Collection, imageUrl: String?, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = collection.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            MissingArt(collection.title, Violet)
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(0.35f to Color.Transparent, 1f to Stage.copy(alpha = 0.92f)),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                collection.title,
                style = MaterialTheme.typography.labelLarge,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${collection.items.size} films",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
    }
}

/** Every genre as a flat color tile in its curated hue, with a quiet icon watermark. */
@Composable
private fun GenresRoom(genres: List<Genre>, nav: NavState) {
    if (genres.isEmpty()) {
        EmptyState("No genres yet.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
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
        items(genres, key = { it.id }) { genre ->
            GenreTile(genre, onClick = { nav.push(Screen.GenreGrid(genre.id, genre.name)) })
        }
    }
}

// Only Browse draws genre tiles, so the id -> icon map lives here rather than
// in the theme package; Color.kt stays the single source of truth for hue.
// Ids not in this map (there should not be any, TMDB's movie genre set is
// fixed) simply get no watermark.
private val genreIcons: Map<Long, ImageVector> = mapOf(
    28L to Icons.Rounded.Bolt, // Action
    12L to Icons.Rounded.Terrain, // Adventure
    16L to Icons.Rounded.Animation, // Animation
    35L to Icons.Rounded.TheaterComedy, // Comedy
    80L to Icons.Rounded.Fingerprint, // Crime
    99L to Icons.Rounded.Videocam, // Documentary
    18L to Icons.Rounded.Theaters, // Drama
    10751L to Icons.Rounded.FamilyRestroom, // Family
    14L to Icons.Rounded.AutoFixHigh, // Fantasy
    36L to Icons.Rounded.HistoryEdu, // History
    27L to Icons.Rounded.NightsStay, // Horror
    10402L to Icons.Rounded.MusicNote, // Music
    9648L to Icons.Rounded.Search, // Mystery
    10749L to Icons.Rounded.Favorite, // Romance
    878L to Icons.Rounded.RocketLaunch, // SciFi
    53L to Icons.Rounded.Visibility, // Thriller
    10770L to Icons.Rounded.Tv, // TVMovie
    10752L to Icons.Rounded.MilitaryTech, // War
    37L to Icons.Rounded.Cabin, // Western
)

@Composable
private fun GenreTile(genre: Genre, onClick: () -> Unit) {
    val fieldStart = genreField(genre.id)
    val fieldEnd = genreDarken(genre.id, valueScale = 0.55f)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(2.05f / 1f)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(listOf(fieldStart, fieldEnd)))
            .clickable(onClick = onClick),
    ) {
        genreIcons[genre.id]?.let { icon ->
            Icon(
                icon,
                contentDescription = null,
                tint = Ink.copy(alpha = 0.30f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(56.dp),
            )
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                genre.name,
                style = MaterialTheme.typography.labelLarge,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                genre.itemCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Ink.copy(alpha = 0.7f),
            )
        }
    }
}
