package xyz.five82.takeup.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.serverDataStore by preferencesDataStore(name = "server")

class ServerPreferences(context: Context) {
    private val dataStore = context.serverDataStore

    suspend fun serverUrl(): String? = dataStore.data
        .map { preferences -> preferences[SERVER_URL] }
        .first()

    suspend fun saveServerUrl(value: String) {
        dataStore.edit { preferences -> preferences[SERVER_URL] = value }
    }


    private companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
    }
}
