package xyz.five82.takeup.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.five82.takeup.TakeupApplication
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.ui.nextEpisodeAfter

/**
 * Owns the ExoPlayer and the progress conversation with Loom: resume from
 * the server's resume position, report every ten seconds while playing and
 * on every pause, and report the full duration at the end so the server's
 * 90% rule marks the title played.
 */
class PlayerViewModel(
    private val application: TakeupApplication,
    private val repository: LoomRepository,
    private val itemId: Long,
) : ViewModel() {

    val player: ExoPlayer = ExoPlayer.Builder(application)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    var item by mutableStateOf<Item?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var ended by mutableStateOf(false)
    var nextEpisode by mutableStateOf<Item?>(null)
        private set

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && !ended) {
                    ended = true
                    val duration = player.duration.takeIf { it > 0 }
                        ?: item?.media?.durationMs ?: 0
                    if (duration > 0) report(duration, duration)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying && !ended) reportNow()
            }
        })
        load()
        viewModelScope.launch {
            while (true) {
                delay(10_000)
                if (player.isPlaying) reportNow()
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            error = null
            try {
                val loaded = repository.api.item(itemId)
                item = loaded
                val playback = repository.api.playback(itemId)
                player.setMediaItem(MediaItem.fromUri(repository.api.absoluteUrl(playback.streamUrl)))
                player.prepare()
                val resume = loaded.progress?.takeIf { !it.played }?.resumePositionMs ?: 0
                if (resume > 0) player.seekTo(resume)
                player.playWhenReady = true
                if (loaded.kind == "episode" && loaded.parentId != null) {
                    loadNextEpisode(loaded)
                }
            } catch (e: Exception) {
                error = e.message ?: "Playback failed"
            }
        }
    }

    /**
     * Loom's Next Up cannot say what follows this specific episode, so the
     * successor is computed locally from the show's own episode list.
     */
    private suspend fun loadNextEpisode(episode: Item) {
        runCatching {
            val season = repository.api.item(episode.parentId ?: return)
            val showId = season.parentId
            val episodes = if (showId != null) {
                coroutineScope {
                    repository.api.children(showId)
                        .filter { it.kind == "season" }
                        .map { async { repository.api.children(it.id) } }
                        .awaitAll()
                        .flatten()
                }
            } else {
                repository.api.children(season.id)
            }
            nextEpisode = nextEpisodeAfter(episodes, episode.id)
        }
    }

    private fun reportNow() {
        val duration = player.duration.takeIf { it > 0 } ?: item?.media?.durationMs ?: return
        report(player.currentPosition.coerceAtLeast(0), duration)
    }

    private fun report(positionMs: Long, durationMs: Long) {
        val target = itemId
        application.appScope.launch {
            runCatching { repository.api.saveProgress(target, positionMs, durationMs) }
        }
    }

    override fun onCleared() {
        // Capture the final position before the player is torn down; the
        // report itself outlives this ViewModel on the application scope.
        if (!ended) {
            val duration = player.duration.takeIf { it > 0 } ?: item?.media?.durationMs
            if (duration != null && player.currentPosition > 0) {
                report(player.currentPosition, duration)
            }
        }
        player.release()
    }
}
