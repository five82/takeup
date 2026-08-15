package xyz.five82.takeup.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.Library
import xyz.five82.takeup.api.LoomApi
import xyz.five82.takeup.api.LoomException
import xyz.five82.takeup.api.loomGson

private val Context.dataStore by preferencesDataStore(name = "takeup")

/** Persisted client settings: the server address and the one network gate. */
class Settings(private val context: Context) {
    private val serverKey = stringPreferencesKey("server_address")
    private val allowCellularKey = booleanPreferencesKey("allow_cellular")
    private val libraryKindsKey = stringPreferencesKey("library_kinds")

    val serverAddress = context.dataStore.data.map { it[serverKey] }

    /** Off until asked for; a phone plan should not pay for a home library. */
    val allowCellular = context.dataStore.data.map { it[allowCellularKey] ?: false }

    /**
     * Library kind per library id, learned whenever Loom answers. An item knows
     * only the id, so with no server this is the only thing that can tell a short
     * film from a feature and keep the offline tabs from being three copies of
     * one grid.
     */
    val libraryKinds = context.dataStore.data.map { decodeLibraryKinds(it[libraryKindsKey].orEmpty()) }

    suspend fun setServerAddress(value: String) {
        context.dataStore.edit { it[serverKey] = value }
    }

    suspend fun setAllowCellular(value: Boolean) {
        context.dataStore.edit { it[allowCellularKey] = value }
    }

    suspend fun setLibraryKinds(value: Map<Long, String>) {
        context.dataStore.edit { it[libraryKindsKey] = encodeLibraryKinds(value) }
    }
}

internal fun encodeLibraryKinds(value: Map<Long, String>): String =
    loomGson.toJson(value.mapKeys { (id, _) -> id.toString() })

/** Malformed state degrades to empty; it is only a cache of what Loom will say again. */
internal fun decodeLibraryKinds(raw: String): Map<Long, String> {
    if (raw.isBlank()) return emptyMap()
    return runCatching {
        JsonParser.parseString(raw).asJsonObject.entrySet().mapNotNull { (key, value) ->
            val id = key.toLongOrNull() ?: return@mapNotNull null
            id to value.asString
        }.toMap()
    }.getOrDefault(emptyMap())
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
    val network: NetworkPolicy,
    private val offlineItems: OfflineItemStore,
) {
    init {
        // Media3 wants its manager driven from the main thread, the way the
        // download service drives it.
        scope.launch(Dispatchers.Main) {
            network.blocked.collect { downloads.setTransfersPaused(it) }
        }
        scope.launch {
            downloads.downloads.collect { entries ->
                if (downloads.loaded.value) pruneAncestors(entries)
            }
        }
    }

    val server: StateFlow<ServerConfig> = settings.serverAddress
        .map { address ->
            api.baseUrl = address?.let(LoomApi::normalizeAddress)
            ServerConfig(loaded = true, address = address)
        }
        .stateIn(scope, SharingStarted.Eagerly, ServerConfig(loaded = false, address = null))

    /**
     * What this device holds, shaped like a library. Rebuilt whenever a download,
     * a captured show, an offline position, or the library map changes, so the
     * screens reading it never have to ask twice.
     */
    val offlineCatalog: StateFlow<OfflineCatalog> = combine(
        downloads.downloads,
        offlineItems.items,
        settings.libraryKinds,
        offlineProgress.pending,
    ) { entries, ancestors, kinds, pending ->
        OfflineCatalog(entries, ancestors, kinds, pending)
    }.stateIn(scope, SharingStarted.Eagerly, OfflineCatalog())

    suspend fun setServerAddress(value: String) = settings.setServerAddress(value)

    /** Picks up downloads interrupted by a process death, if the gate is open. */
    fun resumeDownloads() {
        if (!network.blocked.value) downloads.resumeQueued()
    }

    @Volatile
    private var libraries: List<Library> = emptyList()

    /** Library kind ("movies", "shorts", "tv") for an item's library_id. */
    suspend fun libraryKind(libraryId: Long): String? {
        if (libraries.none { it.id == libraryId }) {
            runCatching {
                libraries = api.libraries()
                // Remembered across restarts: offline this is the only thing that
                // can say which tab a download belongs in.
                settings.setLibraryKinds(libraries.associate { it.id to it.kind })
            }
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
        // Both of these describe where the file belongs rather than the file
        // itself, and both are only answerable while Loom is reachable. A failure
        // costs context offline, never the download.
        runCatching { captureAncestors(item) }
        runCatching { libraryKind(item.libraryId) }
        return DownloadResult.Started
    }

    /**
     * Stores the season and show above an episode, artwork included, so an
     * offline library can group episodes the way Loom does instead of listing
     * them loose.
     */
    private suspend fun captureAncestors(item: Item) {
        var parentId = item.parentId
        while (parentId != null) {
            val json = api.itemJson(parentId)
            val parent = loomGson.fromJson(json, Item::class.java)
            offlineItems.save(parent, json)
            runCatching { downloads.artwork.save(parent) }
            parentId = parent.parentId
        }
    }

    /**
     * Drops a captured show or season once the last download beneath it is gone.
     * Their artwork is the largest part of what they cost, so this runs on every
     * change rather than waiting for a restart.
     */
    private suspend fun pruneAncestors(entries: List<DownloadEntry>) {
        val captured = offlineItems.items.value.associateBy { it.id }
        if (captured.isEmpty()) return
        val keep = mutableSetOf<Long>()
        entries.forEach { entry ->
            var parentId = entry.item.parentId
            // A chain already walked cannot add anything new above it.
            while (parentId != null && keep.add(parentId)) {
                parentId = captured[parentId]?.parentId
            }
        }
        val dead = captured.keys - keep
        if (dead.isEmpty()) return
        offlineItems.delete(dead)
        dead.forEach { downloads.artwork.delete(it) }
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
