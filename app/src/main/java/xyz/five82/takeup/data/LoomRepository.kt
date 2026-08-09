package xyz.five82.takeup.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.Library
import xyz.five82.takeup.api.LoomApi
import xyz.five82.takeup.api.LoomException
import xyz.five82.takeup.api.loomGson

private val Context.dataStore by preferencesDataStore(name = "takeup")

/** Persisted client settings; just the server address today. */
class Settings(private val context: Context) {
    private val serverKey = stringPreferencesKey("server_address")

    val serverAddress = context.dataStore.data.map { it[serverKey] }

    suspend fun setServerAddress(value: String) {
        context.dataStore.edit { it[serverKey] = value }
    }
}

/** Server address as the UI sees it: unknown until DataStore's first emission. */
data class ServerConfig(val loaded: Boolean, val address: String?)

/**
 * Loom is the source of truth on a trusted LAN, so this layer stays thin:
 * the API client, the persisted address, and the one lookup (library id to
 * kind) that listings need but items do not carry.
 */
class LoomRepository(
    private val settings: Settings,
    val api: LoomApi,
    scope: CoroutineScope,
    val downloads: DownloadStore,
    val offlineProgress: OfflineProgressStore,
) {
    val server: StateFlow<ServerConfig> = settings.serverAddress
        .map { address ->
            api.baseUrl = address?.let(LoomApi::normalizeAddress)
            ServerConfig(loaded = true, address = address)
        }
        .stateIn(scope, SharingStarted.Eagerly, ServerConfig(loaded = false, address = null))

    suspend fun setServerAddress(value: String) = settings.setServerAddress(value)

    @Volatile
    private var libraries: List<Library> = emptyList()

    /** Library kind ("movies", "shorts", "tv") for an item's library_id. */
    suspend fun libraryKind(libraryId: Long): String? {
        if (libraries.none { it.id == libraryId }) {
            runCatching { libraries = api.libraries() }
        }
        return libraries.firstOrNull { it.id == libraryId }?.kind
    }

    /**
     * Snapshots the item, checks headroom against the file's live size, and hands
     * the transfer to Media3. The playback endpoint re-stats the file, so its tag
     * and size describe the file as of this request rather than the last scan.
     */
    suspend fun startDownload(itemId: Long): DownloadResult {
        val json = api.itemJson(itemId)
        val item = loomGson.fromJson(json, Item::class.java)
        val playback = api.playback(itemId)
        if (!hasRoomFor(playback.media.size, downloads.usableSpaceBytes())) {
            return DownloadResult.NotEnoughSpace
        }
        downloads.enqueue(itemId, api.absoluteUrl(playback.streamUrl), json)
        runCatching { downloads.artwork.save(item) }
        return DownloadResult.Started
    }

    /**
     * Replays watch positions queued while offline. A [LoomException] also clears
     * the entry: the server answered, so retrying the same write cannot help.
     */
    suspend fun flushPendingProgress() {
        offlineProgress.all().forEach { (itemId, progress) ->
            val sent = runCatching {
                api.saveProgress(itemId, progress.positionMs, progress.durationMs)
            }
            if (sent.isSuccess || sent.exceptionOrNull() is LoomException) {
                offlineProgress.clear(itemId)
            }
        }
    }
}
