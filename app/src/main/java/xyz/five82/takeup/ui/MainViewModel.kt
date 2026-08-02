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
import xyz.five82.takeup.data.LoomHttpException
import xyz.five82.takeup.data.LoomItem
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.data.PreparedPlayback
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal sealed interface MainUiState {
    data object Starting : MainUiState

    data class Connect(
        val serverUrl: String = "",
        val isConnecting: Boolean = false,
        val error: String? = null,
    ) : MainUiState

    data class Movies(
        val serverUrl: String,
        val items: List<LoomItem> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
    ) : MainUiState

    data class Playback(
        val serverUrl: String,
        val item: LoomItem,
        val prepared: PreparedPlayback? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
    ) : MainUiState
}

internal class MainViewModel(
    private val repository: LoomRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Starting)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var started = false
    private var activeJob: Job? = null
    private var progressJob: Job? = null
    private var movies: List<LoomItem> = emptyList()

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
                loadMovies(serverUrl)
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
                .onSuccess { loadMovies(it) }
                .onFailure {
                    _uiState.value = state.copy(
                        isConnecting = false,
                        error = readableError(it),
                    )
                }
        }
    }

    fun retryMovies() {
        val state = _uiState.value as? MainUiState.Movies ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch { loadMovies(state.serverUrl) }
    }

    fun changeServer() {
        val state = _uiState.value
        val serverUrl = when (state) {
            is MainUiState.Movies -> state.serverUrl
            is MainUiState.Playback -> state.serverUrl
            else -> ""
        }
        activeJob?.cancel()
        _uiState.value = MainUiState.Connect(serverUrl = serverUrl)
    }

    fun selectMovie(item: LoomItem) {
        val state = _uiState.value as? MainUiState.Movies ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = MainUiState.Playback(
                serverUrl = state.serverUrl,
                item = item,
                isLoading = true,
            )
            loadPlayback(state.serverUrl, item)
        }
    }

    fun retryPlayback() {
        val state = _uiState.value as? MainUiState.Playback ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            loadPlayback(state.serverUrl, state.item)
        }
    }

    fun backToMovies() {
        val state = _uiState.value as? MainUiState.Playback ?: return
        activeJob?.cancel()
        _uiState.value = MainUiState.Movies(serverUrl = state.serverUrl, items = movies)
    }

    fun saveProgress(
        serverUrl: String,
        itemId: Long,
        positionMs: Long,
        durationMs: Long,
    ) {
        if (durationMs <= 0) return
        val position = positionMs.coerceIn(0, durationMs)
        val state = _uiState.value as? MainUiState.Playback
        val prepared = state?.prepared
        if (prepared?.itemId == itemId) {
            _uiState.value = state.copy(
                prepared = prepared.copy(resumePositionMs = position),
            )
        }
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

    private suspend fun loadMovies(serverUrl: String) {
        _uiState.value = MainUiState.Movies(
            serverUrl = serverUrl,
            items = movies,
            isLoading = true,
        )
        runCatching { repository.movies(serverUrl) }
            .onSuccess {
                movies = it
                _uiState.value = MainUiState.Movies(serverUrl = serverUrl, items = it)
            }
            .onFailure {
                _uiState.value = MainUiState.Movies(
                    serverUrl = serverUrl,
                    items = movies,
                    error = readableError(it),
                )
            }
    }

    private suspend fun loadPlayback(serverUrl: String, item: LoomItem) {
        runCatching { repository.preparePlayback(serverUrl, item.id) }
            .onSuccess {
                _uiState.value = MainUiState.Playback(
                    serverUrl = serverUrl,
                    item = item,
                    prepared = it,
                )
            }
            .onFailure {
                _uiState.value = MainUiState.Playback(
                    serverUrl = serverUrl,
                    item = item,
                    error = readableError(it),
                )
            }
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
