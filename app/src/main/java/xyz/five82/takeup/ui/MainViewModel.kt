package xyz.five82.takeup.ui

import androidx.lifecycle.ViewModel
import com.google.gson.JsonParseException
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.five82.takeup.data.ArtworkKind
import xyz.five82.takeup.data.ArtworkOption
import xyz.five82.takeup.data.DownloadResult
import xyz.five82.takeup.data.DownloadStore
import xyz.five82.takeup.data.Genre
import xyz.five82.takeup.data.GenreSummary
import xyz.five82.takeup.data.HomeContent
import xyz.five82.takeup.data.LoomCollection
import xyz.five82.takeup.data.LoomHttpException
import xyz.five82.takeup.data.LoomItem
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.data.NetworkMonitor
import xyz.five82.takeup.data.OfflineProgressStore
import xyz.five82.takeup.data.PlaybackProgress
import xyz.five82.takeup.data.PreparedPlayback
import xyz.five82.takeup.data.isOfflineError
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal enum class LibraryKind {
    Movies,
    Shorts,
    Shows,
}

internal enum class BrowseOrigin {
    Home,
    Movies,
    Shorts,
    Shows,
    Season,
    Search,
    Genre,
    Collection,
}

// Destinations reachable from the floating navigation toolbar.
internal enum class TopDestination {
    Home,
    Movies,
    Shorts,
    Shows,
    Search,
}

internal fun MainUiState.topDestination(): TopDestination? = when (this) {
    // Offline the toolbar would only offer destinations that cannot load, so it is
    // hidden entirely; Settings stays reachable from Home's overlay button.
    is MainUiState.Home -> TopDestination.Home.takeUnless { isOffline }
    is MainUiState.Library -> when (kind) {
        LibraryKind.Movies -> TopDestination.Movies
        LibraryKind.Shorts -> TopDestination.Shorts
        LibraryKind.Shows -> TopDestination.Shows
    }
    is MainUiState.Search -> TopDestination.Search
    else -> null
}

internal sealed interface MainUiState {
    data object Starting : MainUiState

    data class Connect(
        val serverUrl: String = "",
        val isConnecting: Boolean = false,
        val error: String? = null,
        val canNavigateBack: Boolean = false,
    ) : MainUiState

    data class Home(
        val serverUrl: String,
        val content: HomeContent = EMPTY_HOME_CONTENT,
        val isLoading: Boolean = false,
        val error: String? = null,
        // Loom is unreachable and downloads are all that can be played, so the rest
        // of the library is withheld rather than shown as something it is not.
        val isOffline: Boolean = false,
    ) : MainUiState

    data class Library(
        val serverUrl: String,
        val kind: LibraryKind,
        val items: List<LoomItem> = emptyList(),
        val genres: List<GenreSummary> = emptyList(),
        val selectedGenreId: Long = 0,
        val isLoading: Boolean = false,
        val error: String? = null,
    ) : MainUiState

    data class Search(
        val serverUrl: String,
        val query: String = "",
        val results: List<LoomItem> = emptyList(),
        // True once a search for the current query has completed.
        val searched: Boolean = false,
        val isLoading: Boolean = false,
        val error: String? = null,
        // Shown as browse cards while the query is empty.
        val genres: List<GenreSummary> = emptyList(),
    ) : MainUiState

    data class GenreHub(
        val serverUrl: String,
        val genres: List<GenreSummary> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
    ) : MainUiState

    data class GenreLanding(
        val serverUrl: String,
        val genre: Genre,
        val items: List<LoomItem> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
    ) : MainUiState

    // Both collection screens are served entirely from the shelves the home load
    // already carries, so neither one loads or can fail.
    data class CollectionHub(
        val serverUrl: String,
        val collections: List<LoomCollection> = emptyList(),
    ) : MainUiState

    data class CollectionLanding(
        val serverUrl: String,
        val collection: LoomCollection,
    ) : MainUiState

    data class ShowDetails(
        val serverUrl: String,
        val show: LoomItem,
        val seasons: List<LoomItem> = emptyList(),
        val origin: BrowseOrigin,
        val isLoading: Boolean = false,
        val error: String? = null,
    ) : MainUiState

    data class Season(
        val serverUrl: String,
        val show: LoomItem,
        val season: LoomItem,
        val episodes: List<LoomItem> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
    ) : MainUiState

    data class Details(
        val serverUrl: String,
        val item: LoomItem,
        val origin: BrowseOrigin,
        val isLoading: Boolean = false,
        val error: String? = null,
    ) : MainUiState

    data class Artwork(
        val serverUrl: String,
        val item: LoomItem,
        val kind: ArtworkKind = ArtworkKind.POSTER,
        val options: List<ArtworkOption> = emptyList(),
        val isLoading: Boolean = false,
        val isSaving: Boolean = false,
        val error: String? = null,
    ) : MainUiState

    data class Playback(
        val serverUrl: String,
        val item: LoomItem,
        val origin: BrowseOrigin,
        val nextEpisode: LoomItem? = null,
        val prepared: PreparedPlayback? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
    ) : MainUiState
}

// The home spotlight: one title a day instead of a carousel. A deterministic
// day-seeded pick among untouched movies with backdrop art - anything watched
// or in progress is excluded so the spotlight never mirrors the Continue
// Watching row below it. Stable within a day, rotates across days without any
// saved state. The pick can shift when the library changes size; fine for a
// daily rotation.
internal fun MainUiState.Home.spotlightItem(epochDay: Long): LoomItem? {
    val movies = content.movies
    val untouched = movies.filter {
        it.progress?.played != true && (it.progress?.resumePositionMs ?: 0L) <= 0L
    }
    val pool = untouched.filter { it.backdropImageId > 0 }
        .ifEmpty { movies.filter { it.backdropImageId > 0 } }
        .ifEmpty { movies }
    if (pool.isEmpty()) {
        return content.shows.firstOrNull() ?: content.shorts.firstOrNull()
    }
    return pool[(epochDay.mod(pool.size.toLong())).toInt()]
}

// Losing the last local network is definitive - Loom lives on the LAN, so it is
// unreachable - and Home switches to the downloads-only offline layout right
// away instead of waiting for a request to fail. Mirrors loadHome's failure
// rule: offline mode only exists when there is something downloaded to show.
internal fun offlineHomeAfterLoss(
    state: MainUiState,
    hasDownloads: Boolean,
): MainUiState.Home? {
    if (!hasDownloads) return null
    // A loading Home is left alone: the in-flight request fails on its own and
    // lands the same offline state through loadHome.
    if (state !is MainUiState.Home || state.isOffline || state.isLoading) return null
    return MainUiState.Home(
        serverUrl = state.serverUrl,
        content = EMPTY_HOME_CONTENT,
        isOffline = true,
    )
}

// Spotlight rows for Home: the library's richest genres, rotated by day so
// Home doesn't fossilize. Derived from the movies the home load already
// carries - no extra server calls.
internal fun MainUiState.Home.genreSpotlights(
    dayOfYear: Int,
    rowCount: Int = 2,
    minItems: Int = 4,
): List<Pair<Genre, List<LoomItem>>> {
    val byGenre = LinkedHashMap<Long, Pair<Genre, MutableList<LoomItem>>>()
    content.movies.forEach { item ->
        item.genres.forEach { genre ->
            byGenre.getOrPut(genre.id) { genre to mutableListOf() }.second.add(item)
        }
    }
    val eligible = byGenre.values
        .filter { it.second.size >= minItems }
        .sortedByDescending { it.second.size }
    if (eligible.isEmpty()) return emptyList()
    val start = dayOfYear % eligible.size
    return (0 until minOf(rowCount, eligible.size)).map { offset ->
        val (genre, items) = eligible[(start + offset) % eligible.size]
        genre to items.take(12)
    }
}

// Cards for Home's "Browse by genre" row, largest genres first.
internal fun MainUiState.Home.genreBrowseEntries(): List<GenreSummary> {
    val byGenre = LinkedHashMap<Long, Pair<Genre, Int>>()
    content.movies.forEach { item ->
        item.genres.forEach { genre ->
            val current = byGenre[genre.id]
            byGenre[genre.id] = genre to ((current?.second ?: 0) + 1)
        }
    }
    return byGenre.values
        .sortedByDescending { it.second }
        .map { (genre, count) -> GenreSummary(id = genre.id, name = genre.name, itemCount = count) }
}

internal val EMPTY_HOME_CONTENT = HomeContent(
    continueWatching = emptyList(),
    nextUp = emptyList(),
    recentlyAdded = emptyList(),
    movies = emptyList(),
    shorts = emptyList(),
    shows = emptyList(),
    collections = emptyList(),
)

internal fun HomeContent.isEmpty(): Boolean =
    continueWatching.isEmpty() && nextUp.isEmpty() && recentlyAdded.isEmpty() &&
        movies.isEmpty() && shorts.isEmpty() && shows.isEmpty() && collections.isEmpty()

/**
 * The state back from a person search returns to. Only the two detail screens
 * host a credit pill, and a remembered one must not resume the spinner it was
 * wearing when it was left. Anywhere else leaves no origin, so back falls
 * through to Home the way it does for the toolbar's own search.
 */
internal fun personSearchOrigin(state: MainUiState): MainUiState? = when (state) {
    is MainUiState.Details -> state.copy(isLoading = false)
    is MainUiState.ShowDetails -> state.copy(isLoading = false)
    else -> null
}

internal fun nextEpisodeAfter(episodes: List<LoomItem>, itemId: Long): LoomItem? {
    val index = episodes.indexOfFirst { it.id == itemId }
    if (index < 0) return null
    return episodes.getOrNull(index + 1)
}

/**
 * Moves this row's unwatched rollup by [delta], staying within the episodes Loom
 * counted beneath it. Rows carrying no rollup - movies, episodes, and anything
 * from an older Loom - are left alone.
 */
internal fun LoomItem.shiftUnwatched(delta: Int): LoomItem =
    if (episodeCount <= 0) {
        this
    } else {
        copy(unwatchedCount = (unwatchedCount + delta).coerceIn(0, episodeCount))
    }

/**
 * This row's rollup once [cascade], a show or season that was just marked or
 * cleared, has taken every episode beneath it along. Loom recounts server-side,
 * but the outcome is known outright, so cached rows do not need a refetch.
 */
internal fun LoomItem.afterWatchedCascade(cascade: LoomItem, watched: Boolean): LoomItem = when {
    episodeCount <= 0 -> this
    // The cascaded item itself, and the seasons under a cascaded show, all end up
    // wholly watched or wholly unwatched.
    id == cascade.id || (cascade.kind == "show" && parentId == cascade.id) ->
        copy(unwatchedCount = if (watched) 0 else episodeCount)
    // The show above a cascaded season moves by however many episodes that season
    // just changed hands; its other seasons are untouched.
    cascade.kind == "season" && id == cascade.parentId ->
        shiftUnwatched((if (watched) 0 else cascade.episodeCount) - cascade.unwatchedCount)
    else -> this
}

private data class ShowContent(
    val show: LoomItem,
    val seasons: List<LoomItem>,
    val origin: BrowseOrigin,
)

private data class SeasonContent(
    val show: LoomItem,
    val season: LoomItem,
    val episodes: List<LoomItem>,
)

internal class MainViewModel(
    private val repository: LoomRepository,
    private val downloads: DownloadStore? = null,
    private val offlineProgress: OfflineProgressStore? = null,
    private val networkMonitor: NetworkMonitor? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Starting)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var started = false
    private var activeJob: Job? = null
    private var progressJob: Job? = null
    private var homeContent = EMPTY_HOME_CONTENT
    private var movieGenres: List<GenreSummary> = emptyList()
    // Movies filtered by genre, kept so back navigation restores the filter.
    private var movieGenreItems: Pair<Long, List<LoomItem>>? = null
    private var showContent: ShowContent? = null
    private var seasonContent: SeasonContent? = null
    private var artworkReturnState: MainUiState? = null
    private var settingsReturnState: MainUiState.Home? = null
    private var searchReturnState: MainUiState.Search? = null
    // Where a person search was opened from, so back returns to the detail screen
    // that credited them rather than dropping to Home the way the toolbar's own
    // search does.
    private var searchOriginState: MainUiState? = null
    // Landing content survives a round trip into item details (origin Genre),
    // and the return state remembers where the landing was opened from.
    private var genreLandingContent: Pair<Genre, List<LoomItem>>? = null
    private var genreReturnState: MainUiState? = null
    // The shelf on screen is named rather than held, so returning to it rebuilds
    // from the cached collections and picks up any watched state written since.
    private var collectionLandingSlug: String? = null
    private var collectionReturnState: MainUiState? = null

    fun start() {
        if (started) return
        started = true
        watchForLoomComingBack()
        activeJob = viewModelScope.launch {
            val serverUrl = runCatching { repository.savedServerUrl() }
                .getOrElse {
                    _uiState.value = MainUiState.Connect(error = readableError(it))
                    return@launch
                }
            if (serverUrl == null) {
                _uiState.value = MainUiState.Connect()
            } else {
                loadHome(serverUrl)
            }
        }
    }

    /**
     * Connectivity drives Home in both directions. Joining a local network is
     * the moment Loom might be reachable again, so retry rather than making the
     * user pull to refresh - only while already offline, since a network change
     * proves nothing about a library that is working fine. Losing the last
     * local network flips Home to the downloads-only offline layout on the
     * spot: Loom is on the LAN, so no request needs to fail first.
     */
    private fun watchForLoomComingBack() {
        val monitor = networkMonitor ?: return
        viewModelScope.launch {
            monitor.wifiAvailable.collect { available ->
                val state = _uiState.value
                if (available) {
                    if (state is MainUiState.Home && state.isOffline && !state.isLoading) {
                        loadHome(state.serverUrl)
                    }
                } else {
                    offlineHomeAfterLoss(
                        state = state,
                        hasDownloads = !downloads?.downloads?.value.isNullOrEmpty(),
                    )?.let { _uiState.value = it }
                }
            }
        }
    }

    fun updateServerUrl(value: String) {
        val state = _uiState.value as? MainUiState.Connect ?: return
        _uiState.value = state.copy(serverUrl = value, error = null)
    }

    fun connect() {
        val state = _uiState.value as? MainUiState.Connect ?: return
        if (state.isConnecting) return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = state.copy(isConnecting = true, error = null)
            runCatching { repository.connect(state.serverUrl) }
                .onSuccess {
                    settingsReturnState = null
                    homeContent = EMPTY_HOME_CONTENT
                    movieGenres = emptyList()
                    movieGenreItems = null
                    genreLandingContent = null
                    genreReturnState = null
                    collectionLandingSlug = null
                    collectionReturnState = null
                    showContent = null
                    seasonContent = null
                    loadHome(it)
                }
                .onFailure {
                    _uiState.value = state.copy(
                        isConnecting = false,
                        error = readableError(it),
                    )
                }
        }
    }

    fun retryHome() {
        val state = _uiState.value as? MainUiState.Home ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch { loadHome(state.serverUrl) }
    }

    fun showMovies() = showLibrary(LibraryKind.Movies)

    fun showShorts() = showLibrary(LibraryKind.Shorts)

    fun showShows() = showLibrary(LibraryKind.Shows)

    // Toolbar navigation: works from any top-level destination, unlike the
    // Home-only entry points above.
    fun selectTopDestination(destination: TopDestination) {
        val current = _uiState.value
        if (current.topDestination() == null || current.topDestination() == destination) return
        val serverUrl = currentServerUrl() ?: return
        activeJob?.cancel()
        when (destination) {
            TopDestination.Home -> {
                searchReturnState = null
                searchOriginState = null
                _uiState.value = MainUiState.Home(serverUrl = serverUrl, content = homeContent)
            }
            TopDestination.Movies -> showLibraryContent(serverUrl, LibraryKind.Movies)
            TopDestination.Shorts -> showLibraryContent(serverUrl, LibraryKind.Shorts)
            TopDestination.Shows -> showLibraryContent(serverUrl, LibraryKind.Shows)
            TopDestination.Search -> {
                searchReturnState = null
                searchOriginState = null
                _uiState.value = MainUiState.Search(serverUrl = serverUrl, genres = movieGenres)
                if (movieGenres.isEmpty()) {
                    activeJob = viewModelScope.launch { loadSearchGenres(serverUrl) }
                }
            }
        }
    }

    fun retryLibrary() {
        val state = _uiState.value as? MainUiState.Library ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            val result = when (state.kind) {
                LibraryKind.Movies -> runCatching {
                    repository.movies(state.serverUrl, state.selectedGenreId)
                }
                LibraryKind.Shorts -> runCatching { repository.shorts(state.serverUrl) }
                LibraryKind.Shows -> runCatching { repository.shows(state.serverUrl) }
            }
            result
                .onSuccess { items ->
                    cacheLibraryItems(state.kind, state.selectedGenreId, items)
                    _uiState.value = state.copy(items = items, isLoading = false, error = null)
                }
                .onFailure {
                    _uiState.value = state.copy(
                        isLoading = false,
                        error = readableError(it),
                    )
                }
            if (state.kind == LibraryKind.Movies) {
                loadMovieGenres(state.serverUrl)
            }
        }
    }

    fun selectGenre(genreId: Long) {
        val state = _uiState.value as? MainUiState.Library ?: return
        if (state.kind != LibraryKind.Movies || state.isLoading) return
        if (genreId == state.selectedGenreId) return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = state.copy(
                selectedGenreId = genreId,
                isLoading = true,
                error = null,
            )
            runCatching { repository.movies(state.serverUrl, genreId) }
                .onSuccess { items ->
                    cacheLibraryItems(LibraryKind.Movies, genreId, items)
                    val current = _uiState.value as? MainUiState.Library ?: return@onSuccess
                    if (current.kind == LibraryKind.Movies && current.selectedGenreId == genreId) {
                        _uiState.value = current.copy(
                            items = items,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
                .onFailure {
                    val current = _uiState.value as? MainUiState.Library ?: return@onFailure
                    if (current.kind == LibraryKind.Movies && current.selectedGenreId == genreId) {
                        _uiState.value = state.copy(
                            isLoading = false,
                            error = readableError(it),
                        )
                    }
                }
        }
    }

    fun backToHome() {
        val state = _uiState.value as? MainUiState.Library ?: return
        activeJob?.cancel()
        _uiState.value = MainUiState.Home(serverUrl = state.serverUrl, content = homeContent)
    }

    fun openGenreHub() {
        val serverUrl = currentServerUrl() ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = MainUiState.GenreHub(
                serverUrl = serverUrl,
                genres = movieGenres,
                isLoading = movieGenres.isEmpty(),
            )
            loadGenreHub(serverUrl)
        }
    }

    fun retryGenreHub() {
        val state = _uiState.value as? MainUiState.GenreHub ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            loadGenreHub(state.serverUrl)
        }
    }

    fun backFromGenreHub() {
        val state = _uiState.value as? MainUiState.GenreHub ?: return
        activeJob?.cancel()
        _uiState.value = MainUiState.Home(serverUrl = state.serverUrl, content = homeContent)
    }

    // Opens a genre landing from anywhere genres appear: Home rows, the hub,
    // search browse cards, or detail chips. Remembers the state it was opened
    // from so back returns there.
    fun openGenre(genre: Genre) {
        val serverUrl = currentServerUrl() ?: return
        val current = _uiState.value
        if (current !is MainUiState.GenreLanding) {
            genreReturnState = when (current) {
                is MainUiState.Search -> current.copy(isLoading = false)
                else -> current
            }
        }
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            val cached = genreLandingContent?.takeIf { it.first.id == genre.id }
            _uiState.value = MainUiState.GenreLanding(
                serverUrl = serverUrl,
                genre = genre,
                items = cached?.second.orEmpty(),
                isLoading = true,
            )
            loadGenreLanding(serverUrl, genre)
        }
    }

    fun retryGenreLanding() {
        val state = _uiState.value as? MainUiState.GenreLanding ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            loadGenreLanding(state.serverUrl, state.genre)
        }
    }

    fun backFromGenreLanding() {
        val state = _uiState.value as? MainUiState.GenreLanding ?: return
        activeJob?.cancel()
        _uiState.value = genreReturnState
            ?: MainUiState.Home(serverUrl = state.serverUrl, content = homeContent)
        genreReturnState = null
    }

    fun selectGenreItem(item: LoomItem) {
        if (_uiState.value !is MainUiState.GenreLanding) return
        if (item.kind == "show") {
            selectShow(item, BrowseOrigin.Genre)
        } else {
            selectItem(item, BrowseOrigin.Genre)
        }
    }

    fun openCollectionHub() {
        val serverUrl = currentServerUrl() ?: return
        activeJob?.cancel()
        _uiState.value = MainUiState.CollectionHub(
            serverUrl = serverUrl,
            collections = homeContent.collections,
        )
    }

    fun backFromCollectionHub() {
        val state = _uiState.value as? MainUiState.CollectionHub ?: return
        activeJob?.cancel()
        _uiState.value = MainUiState.Home(serverUrl = state.serverUrl, content = homeContent)
    }

    // Opens a shelf from the Home row or the hub, remembering which so back
    // returns there.
    fun openCollection(collection: LoomCollection) {
        val serverUrl = currentServerUrl() ?: return
        val current = _uiState.value
        if (current !is MainUiState.CollectionLanding) {
            collectionReturnState = current
        }
        activeJob?.cancel()
        collectionLandingSlug = collection.slug
        _uiState.value = MainUiState.CollectionLanding(
            serverUrl = serverUrl,
            collection = collection,
        )
    }

    fun backFromCollectionLanding() {
        val state = _uiState.value as? MainUiState.CollectionLanding ?: return
        activeJob?.cancel()
        _uiState.value = collectionReturnState
            ?: MainUiState.Home(serverUrl = state.serverUrl, content = homeContent)
        collectionReturnState = null
    }

    // Loom builds collections from movies alone, so no member ever opens a show.
    // A shelf offers no play of its own; playback starts from the title's details.
    fun selectCollectionItem(item: LoomItem) {
        if (_uiState.value !is MainUiState.CollectionLanding) return
        selectItem(item, BrowseOrigin.Collection)
    }

    fun updateSearchQuery(query: String) {
        val state = _uiState.value as? MainUiState.Search ?: return
        activeJob?.cancel()
        _uiState.value = state.copy(
            query = query,
            results = emptyList(),
            searched = false,
            isLoading = false,
            error = null,
        )
        if (query.isBlank()) return
        activeJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            runSearch(query)
        }
    }

    fun retrySearch() {
        val state = _uiState.value as? MainUiState.Search ?: return
        if (state.query.isBlank()) return
        activeJob?.cancel()
        activeJob = viewModelScope.launch { runSearch(state.query) }
    }

    fun selectSearchItem(item: LoomItem) {
        val state = _uiState.value as? MainUiState.Search ?: return
        searchReturnState = state.copy(isLoading = false)
        if (item.kind == "show") {
            selectShow(item, BrowseOrigin.Search)
        } else {
            selectItem(item, BrowseOrigin.Search)
        }
    }

    // Opens search already run against a credited person's name, from a pill on a
    // detail screen. Loom matches cast and director names in search but has no
    // browse-by-person axis, so a search is the whole route to the rest of
    // someone's work.
    fun openPersonSearch(name: String) {
        if (name.isBlank()) return
        val current = _uiState.value
        val serverUrl = currentServerUrl() ?: return
        searchOriginState = personSearchOrigin(current)
        activeJob?.cancel()
        _uiState.value = MainUiState.Search(
            serverUrl = serverUrl,
            query = name,
            genres = movieGenres,
            isLoading = true,
        )
        activeJob = viewModelScope.launch { runSearch(name) }
    }

    fun backFromSearch() {
        val state = _uiState.value as? MainUiState.Search ?: return
        activeJob?.cancel()
        searchReturnState = null
        _uiState.value = searchOriginState
            ?: MainUiState.Home(serverUrl = state.serverUrl, content = homeContent)
        searchOriginState = null
    }

    fun openSettings() {
        val state = _uiState.value as? MainUiState.Home ?: return
        activeJob?.cancel()
        settingsReturnState = state.copy(isLoading = false)
        _uiState.value = MainUiState.Connect(
            serverUrl = state.serverUrl,
            canNavigateBack = true,
        )
    }

    fun backFromSettings() {
        val state = _uiState.value as? MainUiState.Connect ?: return
        if (!state.canNavigateBack) return
        val returnState = settingsReturnState ?: return
        activeJob?.cancel()
        settingsReturnState = null
        _uiState.value = returnState
    }

    fun selectHomeItem(item: LoomItem) {
        if (item.kind == "show") {
            selectShow(item, BrowseOrigin.Home)
        } else {
            selectItem(item, BrowseOrigin.Home)
        }
    }

    // Hero CTA: playable items start immediately; shows open their details
    // since there is no single obvious episode to play.
    fun playHomeItem(item: LoomItem) {
        val state = _uiState.value as? MainUiState.Home ?: return
        if (item.kind == "show") {
            selectShow(item, BrowseOrigin.Home)
        } else {
            startPlayback(state.serverUrl, item, BrowseOrigin.Home)
        }
    }

    fun selectLibraryItem(item: LoomItem) {
        val state = _uiState.value as? MainUiState.Library ?: return
        when (state.kind) {
            LibraryKind.Movies -> selectItem(item, BrowseOrigin.Movies)
            LibraryKind.Shorts -> selectItem(item, BrowseOrigin.Shorts)
            LibraryKind.Shows -> selectShow(item, BrowseOrigin.Shows)
        }
    }

    fun retryShowDetails() {
        val state = _uiState.value as? MainUiState.ShowDetails ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            loadShow(state.serverUrl, state.show, state.origin)
        }
    }

    fun selectSeason(season: LoomItem) {
        val state = _uiState.value as? MainUiState.ShowDetails ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = MainUiState.Season(
                serverUrl = state.serverUrl,
                show = state.show,
                season = season,
                isLoading = true,
            )
            loadSeason(state.serverUrl, state.show, season)
        }
    }

    fun backFromShowDetails() {
        val state = _uiState.value as? MainUiState.ShowDetails ?: return
        activeJob?.cancel()
        showBrowseState(state.serverUrl, state.origin)
    }

    fun retrySeason() {
        val state = _uiState.value as? MainUiState.Season ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            loadSeason(state.serverUrl, state.show, state.season)
        }
    }

    fun selectEpisode(item: LoomItem) {
        if (_uiState.value !is MainUiState.Season) return
        selectItem(item, BrowseOrigin.Season)
    }

    fun backFromSeason() {
        val state = _uiState.value as? MainUiState.Season ?: return
        activeJob?.cancel()
        showContent?.let {
            _uiState.value = MainUiState.ShowDetails(
                serverUrl = state.serverUrl,
                show = it.show,
                seasons = it.seasons,
                origin = it.origin,
            )
        } ?: run {
            _uiState.value = MainUiState.Home(state.serverUrl, homeContent)
        }
    }

    fun retryDetails() {
        val state = _uiState.value as? MainUiState.Details ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            loadDetails(state.serverUrl, state.item, state.origin)
        }
    }

    fun editArtwork() {
        val source = _uiState.value
        val item = when (source) {
            is MainUiState.Details -> source.item.takeIf {
                it.kind == "movie" && it.tmdbId > 0
            }
            is MainUiState.ShowDetails -> source.show.takeIf { it.tmdbId > 0 }
            is MainUiState.Season -> source.season.takeIf { source.show.tmdbId > 0 }
            else -> null
        } ?: return
        val serverUrl = currentServerUrl() ?: return
        activeJob?.cancel()
        artworkReturnState = source
        activeJob = viewModelScope.launch {
            _uiState.value = MainUiState.Artwork(
                serverUrl = serverUrl,
                item = item,
                isLoading = true,
            )
            loadArtworkOptions(serverUrl, item, ArtworkKind.POSTER)
        }
    }

    fun selectArtworkKind(kind: ArtworkKind) {
        val state = _uiState.value as? MainUiState.Artwork ?: return
        if (state.isSaving || kind == state.kind || kind !in state.item.artworkKinds()) return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = state.copy(
                kind = kind,
                options = emptyList(),
                isLoading = true,
                error = null,
            )
            loadArtworkOptions(state.serverUrl, state.item, kind)
        }
    }

    fun retryArtwork() {
        val state = _uiState.value as? MainUiState.Artwork ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            loadArtworkOptions(state.serverUrl, state.item, state.kind)
        }
    }

    fun selectArtwork(option: ArtworkOption) {
        val state = _uiState.value as? MainUiState.Artwork ?: return
        if (state.isLoading || state.isSaving || option.selected) return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, error = null)
            runCatching {
                repository.selectArtwork(state.serverUrl, state.item, state.kind, option)
            }.onSuccess { updated ->
                artworkChanged(updated)
                loadArtworkOptions(state.serverUrl, updated, state.kind)
            }.onFailure {
                if (isCurrentArtwork(state.item.id, state.kind)) {
                    _uiState.value = state.copy(isSaving = false, error = readableError(it))
                }
            }
        }
    }

    fun resetArtwork() {
        val state = _uiState.value as? MainUiState.Artwork ?: return
        if (state.isLoading || state.isSaving) return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, error = null)
            runCatching {
                repository.resetArtwork(state.serverUrl, state.item, state.kind)
            }.onSuccess { updated ->
                artworkChanged(updated)
                loadArtworkOptions(state.serverUrl, updated, state.kind)
            }.onFailure {
                if (isCurrentArtwork(state.item.id, state.kind)) {
                    _uiState.value = state.copy(isSaving = false, error = readableError(it))
                }
            }
        }
    }

    fun backFromArtwork() {
        if (_uiState.value !is MainUiState.Artwork) return
        activeJob?.cancel()
        artworkReturnState?.let { _uiState.value = it }
        artworkReturnState = null
    }

    fun playDetails() {
        val state = _uiState.value as? MainUiState.Details ?: return
        startPlayback(state.serverUrl, state.item, state.origin)
    }

    fun playNextEpisode() {
        val state = _uiState.value as? MainUiState.Playback ?: return
        val nextEpisode = state.nextEpisode ?: return
        startPlayback(state.serverUrl, nextEpisode, state.origin)
    }

    fun retryPlayback() {
        val state = _uiState.value as? MainUiState.Playback ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            loadPlayback(state.serverUrl, state.item, state.origin)
        }
    }

    fun backFromDetails() {
        val state = _uiState.value as? MainUiState.Details ?: return
        activeJob?.cancel()
        showBrowseState(state.serverUrl, state.origin)
    }

    fun backFromPlayback() {
        val state = _uiState.value as? MainUiState.Playback ?: return
        activeJob?.cancel()
        _uiState.value = MainUiState.Details(
            serverUrl = state.serverUrl,
            item = state.item,
            origin = state.origin,
        )
    }

    fun backToSeasonFromPlayback() {
        val state = _uiState.value as? MainUiState.Playback ?: return
        activeJob?.cancel()
        if (state.origin == BrowseOrigin.Season) {
            showBrowseState(state.serverUrl, BrowseOrigin.Season)
        } else {
            backFromPlayback()
        }
    }

    fun saveProgress(
        serverUrl: String,
        itemId: Long,
        positionMs: Long,
        durationMs: Long,
    ) {
        if (durationMs <= 0) return
        val position = positionMs.coerceIn(0, durationMs)
        val progress = playbackProgress(position, durationMs)
        updateLocalProgress(itemId, progress, position)

        val previousSave = progressJob
        progressJob = viewModelScope.launch {
            previousSave?.join()
            runCatching {
                repository.saveProgress(
                    serverUrl = serverUrl,
                    itemId = itemId,
                    positionMs = position,
                    durationMs = durationMs,
                )
            }.onSuccess {
                offlineProgress?.clear(itemId)
            }.onFailure { error ->
                // Hold the position locally so watching a download offline is not lost.
                if (isOfflineError(error)) {
                    offlineProgress?.enqueue(itemId, position, durationMs)
                }
            }
        }
    }

    /**
     * Writes watched state for the item on screen. Loom stores the file's duration
     * as the position when marking played, so the same progress can be applied
     * locally and Continue Watching retires the item without a home reload.
     */
    fun setDetailsWatched(watched: Boolean) {
        val state = _uiState.value as? MainUiState.Details ?: return
        if (state.isLoading) return
        val item = state.item
        viewModelScope.launch {
            runCatching { repository.setPlayed(state.serverUrl, item.id, watched) }
                .onSuccess { updateLocalProgress(item.id, watchedProgress(item, watched)) }
                .onFailure { showDetailsError(readableError(it)) }
        }
    }

    fun setShowWatched(watched: Boolean) {
        val state = _uiState.value as? MainUiState.ShowDetails ?: return
        if (state.isLoading) return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            runCatching { repository.setPlayed(state.serverUrl, state.show.id, watched) }
                .onSuccess {
                    applyWatchedCascade(state.show, watched)
                    refreshHomeContent(state.serverUrl)
                    val cascaded = showContent?.takeIf { it.show.id == state.show.id }
                    _uiState.value = state.copy(
                        show = cascaded?.show ?: state.show,
                        seasons = cascaded?.seasons ?: state.seasons,
                        isLoading = false,
                        error = null,
                    )
                }
                .onFailure {
                    _uiState.value = state.copy(isLoading = false, error = readableError(it))
                }
        }
    }

    fun setSeasonWatched(watched: Boolean) {
        val state = _uiState.value as? MainUiState.Season ?: return
        if (state.isLoading) return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            runCatching { repository.setPlayed(state.serverUrl, state.season.id, watched) }
                .onSuccess {
                    applyWatchedCascade(state.season, watched)
                    refreshHomeContent(state.serverUrl)
                    val cascaded = showContent?.seasons
                        ?.firstOrNull { it.id == state.season.id }
                        ?: state.season
                    loadSeason(state.serverUrl, state.show, cascaded)
                }
                .onFailure {
                    _uiState.value = state.copy(isLoading = false, error = readableError(it))
                }
        }
    }

    /**
     * Refetches the home rows after a cascade. Which episodes it touched is Loom's
     * answer to give: clearing a series restores a Next Up episode that only Loom
     * can name, and the client cannot tell which of many episodes left Continue
     * Watching. Failure leaves the cached rows in place; Home can be pulled to
     * refresh.
     */
    private suspend fun refreshHomeContent(serverUrl: String) {
        runCatching { repository.home(serverUrl) }.onSuccess { homeContent = it }
    }

    /**
     * Rolls a show- or season-wide cascade into every cached copy of the rows
     * above it. Call this before reloading home, so the rows Loom does resend win
     * over the local arithmetic rather than being counted twice.
     */
    private fun applyWatchedCascade(item: LoomItem, watched: Boolean) {
        mapCachedItems { it.afterWatchedCascade(item, watched) }
    }

    /**
     * Moves the show and season rollups by one when an episode's watched state
     * flips, so a badge is not stale on the way back out of the episode.
     */
    private fun adjustUnwatchedRollups(episode: LoomItem, wasPlayed: Boolean) {
        val nowPlayed = episode.progress?.played == true
        if (episode.kind != "episode" || nowPlayed == wasPlayed) return
        val delta = if (nowPlayed) -1 else 1
        val seasonId = episode.parentId
        val season = seasonContent?.season?.takeIf { it.id == seasonId }
            ?: showContent?.seasons?.firstOrNull { it.id == seasonId }
        val showId = season?.parentId ?: 0L
        mapCachedItems { row ->
            if (row.id == seasonId || row.id == showId) row.shiftUnwatched(delta) else row
        }
    }

    private fun watchedProgress(item: LoomItem, watched: Boolean): PlaybackProgress? {
        if (!watched) return null
        val duration = item.progress?.durationMs?.takeIf { it > 0 } ?: item.mediaDurationMs
        return PlaybackProgress(
            positionMs = duration,
            durationMs = duration,
            played = true,
            resumePositionMs = 0,
        )
    }

    fun startDownload(item: LoomItem) {
        val serverUrl = (_uiState.value as? MainUiState.Details)?.serverUrl ?: return
        viewModelScope.launch {
            val result = runCatching { repository.startDownload(serverUrl, item) }
            val message = when {
                result.getOrNull() == DownloadResult.NotEnoughSpace -> NOT_ENOUGH_SPACE
                result.isFailure -> readableError(result.exceptionOrNull()!!)
                else -> null
            }
            if (message != null) showDetailsError(message)
        }
    }

    fun cancelDownload(itemId: Long) {
        downloads?.remove(itemId)
    }

    fun removeAllDownloads() {
        downloads?.removeAll()
    }

    private fun showDetailsError(message: String) {
        val state = _uiState.value
        if (state is MainUiState.Details) {
            _uiState.value = state.copy(error = message)
        }
    }

    private fun showLibrary(kind: LibraryKind) {
        val state = _uiState.value as? MainUiState.Home ?: return
        showLibraryContent(state.serverUrl, kind)
    }

    private fun showLibraryContent(serverUrl: String, kind: LibraryKind) {
        if (kind == LibraryKind.Movies) movieGenreItems = null
        _uiState.value = MainUiState.Library(
            serverUrl = serverUrl,
            kind = kind,
            items = homeContent.items(kind),
            genres = if (kind == LibraryKind.Movies) movieGenres else emptyList(),
        )
        if (kind == LibraryKind.Movies && movieGenres.isEmpty()) {
            activeJob?.cancel()
            activeJob = viewModelScope.launch { loadMovieGenres(serverUrl) }
        }
    }

    private suspend fun runSearch(query: String) {
        val state = _uiState.value as? MainUiState.Search ?: return
        if (state.query != query) return
        _uiState.value = state.copy(isLoading = true, error = null)
        runCatching { repository.search(state.serverUrl, query) }
            .onSuccess { results ->
                val current = _uiState.value as? MainUiState.Search ?: return@onSuccess
                if (current.query == query) {
                    _uiState.value = current.copy(
                        results = results,
                        searched = true,
                        isLoading = false,
                    )
                }
            }
            .onFailure {
                if (it is CancellationException) return@onFailure
                val current = _uiState.value as? MainUiState.Search ?: return@onFailure
                if (current.query == query) {
                    _uiState.value = current.copy(
                        isLoading = false,
                        error = readableError(it),
                    )
                }
            }
    }

    private suspend fun loadMovieGenres(serverUrl: String) {
        runCatching { repository.genres(serverUrl) }
            .onSuccess { genres ->
                movieGenres = genres
                val state = _uiState.value as? MainUiState.Library ?: return@onSuccess
                if (state.kind == LibraryKind.Movies) {
                    _uiState.value = state.copy(genres = genres)
                }
            }
        // Genre chips are optional; leave them hidden on failure.
    }

    private suspend fun loadSearchGenres(serverUrl: String) {
        runCatching { repository.genres(serverUrl) }
            .onSuccess { genres ->
                movieGenres = genres
                val state = _uiState.value as? MainUiState.Search ?: return@onSuccess
                _uiState.value = state.copy(genres = genres)
            }
        // Browse cards are optional; search still works without them.
    }

    private suspend fun loadGenreHub(serverUrl: String) {
        runCatching { repository.genres(serverUrl) }
            .onSuccess { genres ->
                movieGenres = genres
                val state = _uiState.value as? MainUiState.GenreHub ?: return@onSuccess
                _uiState.value = state.copy(genres = genres, isLoading = false, error = null)
            }
            .onFailure {
                val state = _uiState.value as? MainUiState.GenreHub ?: return@onFailure
                _uiState.value = state.copy(isLoading = false, error = readableError(it))
            }
    }

    private suspend fun loadGenreLanding(serverUrl: String, genre: Genre) {
        runCatching { repository.movies(serverUrl, genre.id) }
            .onSuccess { items ->
                genreLandingContent = genre to items
                val state = _uiState.value as? MainUiState.GenreLanding ?: return@onSuccess
                if (state.genre.id == genre.id) {
                    _uiState.value = state.copy(items = items, isLoading = false, error = null)
                }
            }
            .onFailure {
                val state = _uiState.value as? MainUiState.GenreLanding ?: return@onFailure
                if (state.genre.id == genre.id) {
                    _uiState.value = state.copy(isLoading = false, error = readableError(it))
                }
            }
    }

    private fun cacheLibraryItems(
        kind: LibraryKind,
        genreId: Long,
        items: List<LoomItem>,
    ) {
        when {
            kind == LibraryKind.Shorts -> homeContent = homeContent.copy(shorts = items)
            kind == LibraryKind.Shows -> homeContent = homeContent.copy(shows = items)
            genreId == 0L -> {
                movieGenreItems = null
                homeContent = homeContent.copy(movies = items)
            }
            else -> movieGenreItems = genreId to items
        }
    }

    private fun selectShow(item: LoomItem, origin: BrowseOrigin) {
        val serverUrl = currentServerUrl() ?: return
        activeJob?.cancel()
        seasonContent = null
        activeJob = viewModelScope.launch {
            _uiState.value = MainUiState.ShowDetails(
                serverUrl = serverUrl,
                show = item,
                origin = origin,
                isLoading = true,
            )
            loadShow(serverUrl, item, origin)
        }
    }

    private fun selectItem(item: LoomItem, origin: BrowseOrigin) {
        val serverUrl = currentServerUrl() ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = MainUiState.Details(
                serverUrl = serverUrl,
                item = item,
                origin = origin,
                isLoading = true,
            )
            loadDetails(serverUrl, item, origin)
        }
    }

    private fun startPlayback(
        serverUrl: String,
        item: LoomItem,
        origin: BrowseOrigin,
    ) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = MainUiState.Playback(
                serverUrl = serverUrl,
                item = item,
                origin = origin,
                nextEpisode = nextEpisodeFor(item),
                isLoading = true,
            )
            loadPlayback(serverUrl, item, origin)
        }
    }

    private suspend fun loadHome(serverUrl: String) {
        val offlineHome = (_uiState.value as? MainUiState.Home)?.takeIf { it.isOffline }
        // Attempting a reconnect is not evidence Loom is back, so the offline home
        // stays exactly as it is until the request settles. Rebuilding it as an
        // online load would flash the banner away, put the withheld library and the
        // toolbar back, and take them again the moment the attempt fails.
        _uiState.value = offlineHome?.copy(serverUrl = serverUrl, isLoading = true)
            ?: MainUiState.Home(
                serverUrl = serverUrl,
                content = homeContent,
                isLoading = true,
            )
        runCatching { repository.home(serverUrl) }
            .onSuccess {
                homeContent = it
                _uiState.value = MainUiState.Home(serverUrl = serverUrl, content = it)
                // Loom is reachable again, so hand it anything watched offline.
                runCatching { repository.flushPendingProgress(serverUrl) }
            }
            .onFailure { error ->
                // Only a connection failure means "offline". A malformed response is
                // a real error and must keep saying so.
                val offline = isOfflineError(error) &&
                    !downloads?.downloads?.value.isNullOrEmpty()
                _uiState.value = MainUiState.Home(
                    serverUrl = serverUrl,
                    // Drop the cached library offline: every one of those titles
                    // would open a details screen that cannot play.
                    content = if (offline) EMPTY_HOME_CONTENT else homeContent,
                    error = if (offline) null else readableError(error),
                    isOffline = offline,
                )
            }
    }

    private suspend fun loadShow(
        serverUrl: String,
        show: LoomItem,
        origin: BrowseOrigin,
    ) {
        runCatching {
            val detailedShow = repository.item(serverUrl, show)
            ShowContent(
                show = detailedShow,
                seasons = repository.seasons(serverUrl, detailedShow),
                origin = origin,
            )
        }.onSuccess {
            showContent = it
            updateCachedItem(it.show)
            _uiState.value = MainUiState.ShowDetails(
                serverUrl = serverUrl,
                show = it.show,
                seasons = it.seasons,
                origin = origin,
            )
        }.onFailure {
            val cached = showContent?.takeIf { it.show.id == show.id }
            _uiState.value = MainUiState.ShowDetails(
                serverUrl = serverUrl,
                show = cached?.show ?: show,
                seasons = cached?.seasons.orEmpty(),
                origin = origin,
                error = readableError(it),
            )
        }
    }

    private suspend fun loadSeason(
        serverUrl: String,
        show: LoomItem,
        season: LoomItem,
    ) {
        runCatching { repository.episodes(serverUrl, show, season) }
            .onSuccess { episodes ->
                seasonContent = SeasonContent(show, season, episodes)
                _uiState.value = MainUiState.Season(
                    serverUrl = serverUrl,
                    show = show,
                    season = season,
                    episodes = episodes,
                )
            }
            .onFailure {
                val cached = seasonContent?.takeIf { it.season.id == season.id }
                _uiState.value = MainUiState.Season(
                    serverUrl = serverUrl,
                    show = cached?.show ?: show,
                    season = cached?.season ?: season,
                    episodes = cached?.episodes.orEmpty(),
                    error = readableError(it),
                )
            }
    }

    private suspend fun loadDetails(
        serverUrl: String,
        item: LoomItem,
        origin: BrowseOrigin,
    ) {
        runCatching { repository.item(serverUrl, item) }
            .onSuccess {
                updateCachedItem(it)
                _uiState.value = MainUiState.Details(
                    serverUrl = serverUrl,
                    item = it,
                    origin = origin,
                )
            }
            .onFailure {
                val downloaded = downloads?.entry(item.id)
                _uiState.value = MainUiState.Details(
                    serverUrl = serverUrl,
                    // Prefer the download's snapshot: it carries the streams and
                    // duration a list summary omits, so the screen stays complete.
                    item = downloaded?.item?.copy(
                        seriesTitle = item.seriesTitle.ifBlank { downloaded.item.seriesTitle },
                        seasonTitle = item.seasonTitle.ifBlank { downloaded.item.seasonTitle },
                    ) ?: item,
                    origin = origin,
                    // A downloaded title needs nothing from Loom, so do not tell the
                    // user something failed when everything they asked for is here.
                    error = if (downloaded == null) readableError(it) else null,
                )
            }
    }

    private suspend fun loadArtworkOptions(
        serverUrl: String,
        item: LoomItem,
        kind: ArtworkKind,
    ) {
        runCatching { repository.artworkOptions(serverUrl, item.id, kind) }
            .onSuccess { options ->
                if (!isCurrentArtwork(item.id, kind)) return@onSuccess
                _uiState.value = MainUiState.Artwork(
                    serverUrl = serverUrl,
                    item = item,
                    kind = kind,
                    options = options,
                )
            }
            .onFailure {
                if (!isCurrentArtwork(item.id, kind)) return@onFailure
                _uiState.value = MainUiState.Artwork(
                    serverUrl = serverUrl,
                    item = item,
                    kind = kind,
                    error = readableError(it),
                )
            }
    }

    private fun isCurrentArtwork(itemId: Long, kind: ArtworkKind): Boolean {
        val current = _uiState.value as? MainUiState.Artwork ?: return false
        return current.item.id == itemId && current.kind == kind
    }

    private fun artworkChanged(updated: LoomItem) {
        updateCachedItem(updated)
        artworkReturnState = when (val source = artworkReturnState) {
            is MainUiState.Details -> source.copy(item = updated)
            is MainUiState.ShowDetails -> source.copy(show = updated)
            is MainUiState.Season -> source.copy(season = updated)
            else -> source
        }
    }

    private suspend fun loadPlayback(
        serverUrl: String,
        item: LoomItem,
        origin: BrowseOrigin,
    ) {
        val cachedNextEpisode = nextEpisodeFor(item)
        val prepared = runCatching { repository.preparePlayback(serverUrl, item) }
            .getOrElse {
                _uiState.value = MainUiState.Playback(
                    serverUrl = serverUrl,
                    item = item,
                    origin = origin,
                    nextEpisode = cachedNextEpisode,
                    error = readableError(it),
                )
                return
            }
        _uiState.value = MainUiState.Playback(
            serverUrl = serverUrl,
            item = item,
            origin = origin,
            nextEpisode = cachedNextEpisode,
            prepared = prepared,
        )

        if (cachedNextEpisode == null && item.kind == "episode") {
            val nextEpisode = runCatching { repository.nextEpisode(serverUrl, item) }.getOrNull()
            val current = _uiState.value as? MainUiState.Playback
            if (nextEpisode != null && current?.item?.id == item.id) {
                _uiState.value = current.copy(nextEpisode = nextEpisode)
            }
        }
    }

    private fun showBrowseState(serverUrl: String, origin: BrowseOrigin) {
        _uiState.value = when (origin) {
            BrowseOrigin.Home -> MainUiState.Home(serverUrl = serverUrl, content = homeContent)
            BrowseOrigin.Movies -> {
                val selection = movieGenreItems
                MainUiState.Library(
                    serverUrl = serverUrl,
                    kind = LibraryKind.Movies,
                    items = selection?.second ?: homeContent.movies,
                    genres = movieGenres,
                    selectedGenreId = selection?.first ?: 0,
                )
            }
            BrowseOrigin.Shorts -> MainUiState.Library(
                serverUrl = serverUrl,
                kind = LibraryKind.Shorts,
                items = homeContent.shorts,
            )
            BrowseOrigin.Shows -> MainUiState.Library(
                serverUrl = serverUrl,
                kind = LibraryKind.Shows,
                items = homeContent.shows,
            )
            BrowseOrigin.Season -> seasonContent?.let {
                MainUiState.Season(
                    serverUrl = serverUrl,
                    show = it.show,
                    season = it.season,
                    episodes = it.episodes,
                )
            } ?: MainUiState.Home(serverUrl = serverUrl, content = homeContent)
            BrowseOrigin.Search -> searchReturnState
                ?: MainUiState.Home(serverUrl = serverUrl, content = homeContent)
            BrowseOrigin.Genre -> genreLandingContent?.let { (genre, items) ->
                MainUiState.GenreLanding(
                    serverUrl = serverUrl,
                    genre = genre,
                    items = items,
                )
            } ?: MainUiState.Home(serverUrl = serverUrl, content = homeContent)
            // Rebuilt from the cached shelves rather than a snapshot, so a title
            // marked watched in details comes back badged.
            BrowseOrigin.Collection -> collectionLandingSlug
                ?.let { slug -> homeContent.collections.firstOrNull { it.slug == slug } }
                ?.let { MainUiState.CollectionLanding(serverUrl = serverUrl, collection = it) }
                ?: MainUiState.Home(serverUrl = serverUrl, content = homeContent)
        }
    }

    // A null progress is an item whose playback state was discarded outright.
    private fun updateLocalProgress(
        itemId: Long,
        progress: PlaybackProgress?,
        exactPositionMs: Long = progress?.resumePositionMs ?: 0L,
    ) {
        when (val state = _uiState.value) {
            is MainUiState.Playback -> if (state.item.id == itemId) {
                _uiState.value = state.copy(
                    item = state.item.copy(progress = progress),
                    prepared = state.prepared?.copy(resumePositionMs = exactPositionMs),
                )
            }
            is MainUiState.Details -> if (state.item.id == itemId) {
                _uiState.value = state.copy(item = state.item.copy(progress = progress))
            }
            else -> Unit
        }

        val previous = findCachedItem(itemId) ?: return
        val cached = previous.copy(progress = progress)
        updateCachedItem(cached)
        adjustUnwatchedRollups(cached, wasPlayed = previous.progress?.played == true)
        val continuing = homeContent.continueWatching
            .filterNot { it.id == itemId }
            .toMutableList()
        if ((progress?.resumePositionMs ?: 0L) > 0) {
            continuing.add(0, cached)
        }
        homeContent = homeContent.copy(
            continueWatching = continuing.take(20),
            // Writing playback state retires an episode from Next Up whichever
            // way it went: a partial watch moves the show to Continue Watching,
            // and anything else leaves Loom to name the row's next occupant on
            // the following home load.
            nextUp = homeContent.nextUp.filterNot { it.id == itemId },
        )
    }

    private fun updateCachedItem(updated: LoomItem) = mapCachedItems { item ->
        if (item.id == updated.id) updated else item
    }

    /** Rewrites every cached copy of every item, wherever a screen might read it. */
    private fun mapCachedItems(transform: (LoomItem) -> LoomItem) {
        homeContent = homeContent.copy(
            continueWatching = homeContent.continueWatching.map(transform),
            nextUp = homeContent.nextUp.map(transform),
            recentlyAdded = homeContent.recentlyAdded.map(transform),
            movies = homeContent.movies.map(transform),
            shorts = homeContent.shorts.map(transform),
            shows = homeContent.shows.map(transform),
            collections = homeContent.collections.map { collection ->
                collection.copy(items = collection.items.map(transform))
            },
        )
        movieGenreItems = movieGenreItems?.let { it.first to it.second.map(transform) }
        showContent = showContent?.let {
            it.copy(show = transform(it.show), seasons = it.seasons.map(transform))
        }
        seasonContent = seasonContent?.let {
            it.copy(
                show = transform(it.show),
                season = transform(it.season),
                episodes = it.episodes.map(transform),
            )
        }
    }

    private fun findCachedItem(itemId: Long): LoomItem? =
        seasonContent?.episodes?.firstOrNull { it.id == itemId }
            ?: homeContent.movies.firstOrNull { it.id == itemId }
            ?: homeContent.shorts.firstOrNull { it.id == itemId }
            ?: homeContent.shows.firstOrNull { it.id == itemId }
            ?: homeContent.continueWatching.firstOrNull { it.id == itemId }
            ?: homeContent.nextUp.firstOrNull { it.id == itemId }
            ?: homeContent.recentlyAdded.firstOrNull { it.id == itemId }
            ?: homeContent.collections.firstNotNullOfOrNull { collection ->
                collection.items.firstOrNull { it.id == itemId }
            }

    private fun HomeContent.items(kind: LibraryKind): List<LoomItem> = when (kind) {
        LibraryKind.Movies -> movies
        LibraryKind.Shorts -> shorts
        LibraryKind.Shows -> shows
    }

    private fun nextEpisodeFor(item: LoomItem): LoomItem? =
        seasonContent?.episodes?.let { nextEpisodeAfter(it, item.id) }

    private fun currentServerUrl(): String? = when (val state = _uiState.value) {
        is MainUiState.Home -> state.serverUrl
        is MainUiState.Library -> state.serverUrl
        is MainUiState.Search -> state.serverUrl
        is MainUiState.ShowDetails -> state.serverUrl
        is MainUiState.Season -> state.serverUrl
        is MainUiState.Details -> state.serverUrl
        is MainUiState.Artwork -> state.serverUrl
        is MainUiState.Playback -> state.serverUrl
        is MainUiState.GenreHub -> state.serverUrl
        is MainUiState.GenreLanding -> state.serverUrl
        is MainUiState.CollectionHub -> state.serverUrl
        is MainUiState.CollectionLanding -> state.serverUrl
        else -> null
    }

    private fun LoomItem.artworkKinds(): Set<ArtworkKind> = when (kind) {
        "season" -> setOf(ArtworkKind.POSTER)
        "movie", "show" -> ArtworkKind.entries.toSet()
        else -> emptySet()
    }

    private fun playbackProgress(positionMs: Long, durationMs: Long): PlaybackProgress {
        val fraction = positionMs.toDouble() / durationMs
        val played = fraction >= 0.90
        val resume = if (!played && durationMs >= 5 * 60 * 1000 && fraction >= 0.05) {
            positionMs
        } else {
            0L
        }
        return PlaybackProgress(
            positionMs = positionMs,
            durationMs = durationMs,
            played = played,
            resumePositionMs = resume,
        )
    }

    private fun readableError(error: Throwable): String = when (error) {
        is UnknownHostException -> "The Loom server name could not be found."
        is ConnectException -> "Takeup could not connect to Loom. Check the address and server."
        is SocketTimeoutException -> "The Loom server did not respond in time."
        is LoomHttpException -> error.message ?: "Loom rejected the request."
        is JsonParseException -> "Loom returned incompatible data. Make sure Loom is up to date."
        is IOException -> "The connection to Loom was interrupted. Try again."
        is SecurityException -> "Local network access is not available."
        is IllegalArgumentException -> error.message ?: "The server address is invalid."
        else -> error.message ?: "An unexpected error occurred."
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L
        private const val NOT_ENOUGH_SPACE = "Not enough free space to download this title."

        fun factory(
            repository: LoomRepository,
            downloads: DownloadStore,
            offlineProgress: OfflineProgressStore,
            networkMonitor: NetworkMonitor,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MainViewModel(repository, downloads, offlineProgress, networkMonitor)
            }
        }
    }
}
