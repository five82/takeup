package xyz.five82.takeup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.five82.takeup.data.HomeContent
import xyz.five82.takeup.data.LoomHttpException
import xyz.five82.takeup.data.LoomItem
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.data.PlaybackProgress
import xyz.five82.takeup.data.PreparedPlayback
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

    data class Playback(
        val serverUrl: String,
        val item: LoomItem,
        val origin: BrowseOrigin,
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
    private var showContent: ShowContent? = null
    private var seasonContent: SeasonContent? = null

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
                    homeContent = EMPTY_HOME_CONTENT
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
                LibraryKind.Movies -> runCatching { repository.movies(state.serverUrl) }
                LibraryKind.Shows -> runCatching { repository.shows(state.serverUrl) }
            }
            result
                .onSuccess { items ->
                    homeContent = when (state.kind) {
                        LibraryKind.Movies -> homeContent.copy(movies = items)
                        LibraryKind.Shows -> homeContent.copy(shows = items)
                    }
                    _uiState.value = state.copy(items = items, isLoading = false, error = null)
                }
                .onFailure {
                    _uiState.value = state.copy(
                        isLoading = false,
                        error = readableError(it),
                    )
                }
        }
    }

    fun backToHome() {
        val state = _uiState.value as? MainUiState.Library ?: return
        activeJob?.cancel()
        _uiState.value = MainUiState.Home(serverUrl = state.serverUrl, content = homeContent)
    }

    fun changeServer() {
        val serverUrl = currentServerUrl().orEmpty()
        activeJob?.cancel()
        _uiState.value = MainUiState.Connect(serverUrl = serverUrl)
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

    fun playDetails() {
        val state = _uiState.value as? MainUiState.Details ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = MainUiState.Playback(
                serverUrl = state.serverUrl,
                item = state.item,
                origin = state.origin,
                isLoading = true,
            )
            loadPlayback(state.serverUrl, state.item, state.origin)
        }
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
        _uiState.value = MainUiState.Library(
            serverUrl = state.serverUrl,
            kind = kind,
            items = homeContent.items(kind),
        )
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
            val detailedShow = repository.item(serverUrl, show.id)
            ShowContent(
                show = detailedShow,
                seasons = repository.seasons(serverUrl, show.id),
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
            _uiState.value = MainUiState.ShowDetails(
                serverUrl = serverUrl,
                show = show,
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
        runCatching { repository.episodes(serverUrl, season.id) }
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
                _uiState.value = MainUiState.Season(
                    serverUrl = serverUrl,
                    show = show,
                    season = season,
                    error = readableError(it),
                )
            }
    }

    private suspend fun loadDetails(
        serverUrl: String,
        item: LoomItem,
        origin: BrowseOrigin,
    ) {
        runCatching { repository.item(serverUrl, item.id) }
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

    private suspend fun loadPlayback(
        serverUrl: String,
        item: LoomItem,
        origin: BrowseOrigin,
    ) {
        runCatching { repository.preparePlayback(serverUrl, item.id) }
            .onSuccess {
                _uiState.value = MainUiState.Playback(
                    serverUrl = serverUrl,
                    item = item,
                    origin = origin,
                    prepared = it,
                )
            }
            .onFailure {
                _uiState.value = MainUiState.Playback(
                    serverUrl = serverUrl,
                    item = item,
                    origin = origin,
                    error = readableError(it),
                )
            }
    }

    private fun showBrowseState(serverUrl: String, origin: BrowseOrigin) {
        _uiState.value = when (origin) {
            BrowseOrigin.Home -> MainUiState.Home(serverUrl = serverUrl, content = homeContent)
            BrowseOrigin.Movies -> MainUiState.Library(
                serverUrl = serverUrl,
                kind = LibraryKind.Movies,
                items = homeContent.movies,
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

    private fun currentServerUrl(): String? = when (val state = _uiState.value) {
        is MainUiState.Home -> state.serverUrl
        is MainUiState.Library -> state.serverUrl
        is MainUiState.ShowDetails -> state.serverUrl
        is MainUiState.Season -> state.serverUrl
        is MainUiState.Details -> state.serverUrl
        is MainUiState.Playback -> state.serverUrl
        else -> null
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
