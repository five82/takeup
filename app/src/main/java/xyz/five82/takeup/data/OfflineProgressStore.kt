package xyz.five82.takeup.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.offlineProgressDataStore by preferencesDataStore(name = "offline_progress")

/**
 * Watch positions that could not reach Loom. Keyed by item so only the newest
 * position survives - Loom's progress endpoint is last-write-wins per item, so an
 * ordered queue would just replay positions the user has already moved past.
 */
class OfflineProgressStore(context: Context) {
    private val dataStore = context.applicationContext.offlineProgressDataStore

    // Kept in memory so offline playback can resolve a resume position without
    // suspending in the middle of preparing the player.
    @Volatile
    private var cached: Map<Long, PendingProgress> = emptyMap()

    suspend fun load() {
        cached = decodePendingProgress(
            dataStore.data.map { it[PENDING] }.first().orEmpty(),
        )
    }

    fun pending(itemId: Long): PendingProgress? = cached[itemId]

    fun all(): Map<Long, PendingProgress> = cached

    suspend fun enqueue(itemId: Long, positionMs: Long, durationMs: Long) {
        write(cached + (itemId to PendingProgress(positionMs, durationMs)))
    }

    suspend fun clear(itemId: Long) {
        if (!cached.containsKey(itemId)) return
        write(cached - itemId)
    }

    private suspend fun write(value: Map<Long, PendingProgress>) {
        cached = value
        dataStore.edit { it[PENDING] = encodePendingProgress(value) }
    }

    private companion object {
        val PENDING = stringPreferencesKey("pending")
    }
}

data class PendingProgress(val positionMs: Long, val durationMs: Long)

internal fun encodePendingProgress(value: Map<Long, PendingProgress>): String =
    value.entries.joinToString(",", "{", "}") { (itemId, progress) ->
        "\"$itemId\":[${progress.positionMs},${progress.durationMs}]"
    }

/** Malformed state degrades to empty rather than throwing; it is only a retry queue. */
internal fun decodePendingProgress(raw: String): Map<Long, PendingProgress> {
    if (raw.isBlank()) return emptyMap()
    return runCatching {
        JsonParser.parseString(raw).asJsonObject.entrySet().mapNotNull { (key, value) ->
            val itemId = key.toLongOrNull() ?: return@mapNotNull null
            val pair = value.asJsonArray
            if (pair.size() != 2) return@mapNotNull null
            itemId to PendingProgress(pair[0].asLong, pair[1].asLong)
        }.toMap()
    }.getOrDefault(emptyMap())
}
