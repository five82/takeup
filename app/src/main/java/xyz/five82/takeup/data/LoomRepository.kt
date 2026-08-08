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
import xyz.five82.takeup.api.Library
import xyz.five82.takeup.api.LoomApi

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
}
