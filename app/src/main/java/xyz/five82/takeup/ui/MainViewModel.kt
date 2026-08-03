package xyz.five82.takeup.ui

import androidx.lifecycle.ViewModel
import com.google.gson.JsonParseException
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.five82.takeup.data.ArtworkKind
import xyz.five82.takeup.data.ArtworkOption
import xyz.five82.takeup.data.GenreSummary
import xyz.five82.takeup.data.HomeContent
import xyz.five82.takeup.data.LoomHttpException
import xyz.five82.takeup.data.LoomItem
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.data.PlaybackProgress
import xyz.five82.takeup.data.PreparedPlayback
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal enum class LibraryKind {
    Movies,
    Shows,
}

internal enum class BrowseOrigin {
    Home,
    Movies,
    Shows,
    Season,
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

internal val EMPTY_HOME_CONTENT = HomeContent(
    continueWatching = emptyList(),
    recentlyAdded = emptyList(),
    movies = emptyList(),
    shows = emptyList(),
)

internal fun nextEpisodeAfter(episodes: List<LoomItem>, itemId: Long): LoomItem? {
    val index = episodes.indexOfFirst { it.id == itemId }
    if (index < 0) return null
    return episodes.getOrNull(index + 1)
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

    fun start() {
        if (started) return
        started = true
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

    fun showShows() = showLibrary(LibraryKind.Shows)

    fun retryLibrary() {
        val state = _uiState.value as? MainUiState.Library ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            val result = when (state.kind) {
                LibraryKind.Movies -> runCatching {
                    repository.movies(state.serverUrl, state.selectedGenreId)
                }
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

    fun selectLibraryItem(item: LoomItem) {
        val state = _uiState.value as? MainUiState.Library ?: return
        when (state.kind) {
            LibraryKind.Movies -> selectItem(item, BrowseOrigin.Movies)
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
            }
        }
    }

    private fun showLibrary(kind: LibraryKind) {
        val state = _uiState.value as? MainUiState.Home ?: return
        if (kind == LibraryKind.Movies) movieGenreItems = null
        _uiState.value = MainUiState.Library(
            serverUrl = state.serverUrl,
            kind = kind,
            items = homeContent.items(kind),
            genres = if (kind == LibraryKind.Movies) movieGenres else emptyList(),
        )
        if (kind == LibraryKind.Movies && movieGenres.isEmpty()) {
            activeJob?.cancel()
            activeJob = viewModelScope.launch { loadMovieGenres(state.serverUrl) }
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

    private fun cacheLibraryItems(
        kind: LibraryKind,
        genreId: Long,
        items: List<LoomItem>,
    ) {
        when {
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
        _uiState.value = MainUiState.Home(
            serverUrl = serverUrl,
            content = homeContent,
            isLoading = true,
        )
        runCatching { repository.home(serverUrl) }
            .onSuccess {
                homeContent = it
                _uiState.value = MainUiState.Home(serverUrl = serverUrl, content = it)
            }
            .onFailure {
                _uiState.value = MainUiState.Home(
                    serverUrl = serverUrl,
                    content = homeContent,
                    error = readableError(it),
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
                _uiState.value = MainUiState.Details(
                    serverUrl = serverUrl,
                    item = item,
                    origin = origin,
                    error = readableError(it),
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
        }
    }

    private fun updateLocalProgress(
        itemId: Long,
        progress: PlaybackProgress,
        exactPositionMs: Long,
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

        val cached = findCachedItem(itemId)?.copy(progress = progress) ?: return
        updateCachedItem(cached)
        val continuing = homeContent.continueWatching
            .filterNot { it.id == itemId }
            .toMutableList()
        if (progress.resumePositionMs > 0) {
            continuing.add(0, cached)
        }
        homeContent = homeContent.copy(continueWatching = continuing.take(20))
    }

    private fun updateCachedItem(updated: LoomItem) {
        homeContent = homeContent.copy(
            continueWatching = homeContent.continueWatching.replaceItem(updated),
            recentlyAdded = homeContent.recentlyAdded.replaceItem(updated),
            movies = homeContent.movies.replaceItem(updated),
            shows = homeContent.shows.replaceItem(updated),
        )
        movieGenreItems = movieGenreItems?.let { it.first to it.second.replaceItem(updated) }
        showContent = showContent?.let {
            it.copy(
                show = if (it.show.id == updated.id) updated else it.show,
                seasons = it.seasons.replaceItem(updated),
            )
        }
        seasonContent = seasonContent?.let {
            it.copy(
                show = if (it.show.id == updated.id) updated else it.show,
                season = if (it.season.id == updated.id) updated else it.season,
                episodes = it.episodes.replaceItem(updated),
            )
        }
    }

    private fun findCachedItem(itemId: Long): LoomItem? =
        seasonContent?.episodes?.firstOrNull { it.id == itemId }
            ?: homeContent.movies.firstOrNull { it.id == itemId }
            ?: homeContent.shows.firstOrNull { it.id == itemId }
            ?: homeContent.continueWatching.firstOrNull { it.id == itemId }
            ?: homeContent.recentlyAdded.firstOrNull { it.id == itemId }

    private fun List<LoomItem>.replaceItem(updated: LoomItem): List<LoomItem> =
        map { if (it.id == updated.id) updated else it }

    private fun HomeContent.items(kind: LibraryKind): List<LoomItem> = when (kind) {
        LibraryKind.Movies -> movies
        LibraryKind.Shows -> shows
    }

    private fun nextEpisodeFor(item: LoomItem): LoomItem? =
        seasonContent?.episodes?.let { nextEpisodeAfter(it, item.id) }

    private fun currentServerUrl(): String? = when (val state = _uiState.value) {
        is MainUiState.Home -> state.serverUrl
        is MainUiState.Library -> state.serverUrl
        is MainUiState.ShowDetails -> state.serverUrl
        is MainUiState.Season -> state.serverUrl
        is MainUiState.Details -> state.serverUrl
        is MainUiState.Artwork -> state.serverUrl
        is MainUiState.Playback -> state.serverUrl
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
        fun factory(repository: LoomRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { MainViewModel(repository) }
        }
    }
}
