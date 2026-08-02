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

internal enum class BrowseOrigin {
    Home,
    Movies,
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

    data class Movies(
        val serverUrl: String,
        val items: List<LoomItem> = emptyList(),
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

    fun showMovies() {
        val state = _uiState.value as? MainUiState.Home ?: return
        _uiState.value = MainUiState.Movies(
            serverUrl = state.serverUrl,
            items = homeContent.movies,
        )
    }

    fun retryMovies() {
        val state = _uiState.value as? MainUiState.Movies ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            runCatching { repository.movies(state.serverUrl) }
                .onSuccess {
                    homeContent = homeContent.copy(movies = it)
                    _uiState.value = state.copy(items = it, isLoading = false, error = null)
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
        val state = _uiState.value as? MainUiState.Movies ?: return
        activeJob?.cancel()
        _uiState.value = MainUiState.Home(serverUrl = state.serverUrl, content = homeContent)
    }

    fun changeServer() {
        val serverUrl = when (val state = _uiState.value) {
            is MainUiState.Home -> state.serverUrl
            is MainUiState.Movies -> state.serverUrl
            is MainUiState.Details -> state.serverUrl
            is MainUiState.Playback -> state.serverUrl
            else -> ""
        }
        activeJob?.cancel()
        _uiState.value = MainUiState.Connect(serverUrl = serverUrl)
    }

    fun selectHomeItem(item: LoomItem) {
        selectItem(item, BrowseOrigin.Home)
    }

    fun selectMovie(item: LoomItem) {
        selectItem(item, BrowseOrigin.Movies)
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

    private fun selectItem(item: LoomItem, origin: BrowseOrigin) {
        val serverUrl = when (val state = _uiState.value) {
            is MainUiState.Home -> state.serverUrl
            is MainUiState.Movies -> state.serverUrl
            else -> return
        }
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
            BrowseOrigin.Movies -> MainUiState.Movies(
                serverUrl = serverUrl,
                items = homeContent.movies,
            )
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
        )
    }

    private fun findCachedItem(itemId: Long): LoomItem? =
        homeContent.movies.firstOrNull { it.id == itemId }
            ?: homeContent.continueWatching.firstOrNull { it.id == itemId }
            ?: homeContent.recentlyAdded.firstOrNull { it.id == itemId }

    private fun List<LoomItem>.replaceItem(updated: LoomItem): List<LoomItem> =
        map { if (it.id == updated.id) updated else it }

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
