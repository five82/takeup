package xyz.five82.takeup.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.loomGson
import java.io.File

/**
 * The shows and seasons above a downloaded episode, captured when the episode is
 * downloaded. Media3 carries the episode's own snapshot in its download request,
 * but a show has no file to hang one on, so its response body is stored here
 * verbatim - the same raw JSON, decoded by the same Gson, so there is one
 * decoder for online and offline items.
 *
 * Without these an offline library could only ever show loose episodes.
 */
class OfflineItemStore(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "offline-items")

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        _items.value = readAll()
    }

    suspend fun save(item: Item, json: String) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        runCatching { file(item.id).writeText(json) }
        _items.value = _items.value.filterNot { it.id == item.id } + item
    }

    suspend fun delete(itemIds: Collection<Long>) = withContext(Dispatchers.IO) {
        if (itemIds.isEmpty()) return@withContext
        itemIds.forEach { file(it).delete() }
        _items.value = _items.value.filterNot { it.id in itemIds }
    }

    private fun readAll(): List<Item> = directory.listFiles().orEmpty().mapNotNull { file ->
        // A truncated or superseded file is only a cached copy of a server
        // document; dropping it costs a re-download of metadata, not media.
        runCatching { loomGson.fromJson(file.readText(), Item::class.java) }.getOrNull()
    }

    private fun file(itemId: Long) = File(directory, "$itemId.json")
}
