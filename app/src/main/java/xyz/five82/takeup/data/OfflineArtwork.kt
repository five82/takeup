package xyz.five82.takeup.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.LoomApi
import java.io.File

/**
 * Local copies of a downloaded item's artwork. Coil's disk cache is evictable and
 * sized by the platform, so it cannot be relied on once the server is unreachable.
 */
class OfflineArtwork(
    context: Context,
    private val api: LoomApi,
) {
    private val directory = File(context.applicationContext.filesDir, "offline-art")

    fun posterPath(itemId: Long): String? = pathIfPresent(itemId, POSTER)

    fun backdropPath(itemId: Long): String? = pathIfPresent(itemId, BACKDROP)

    fun thumbPath(itemId: Long): String? = pathIfPresent(itemId, THUMB)

    fun logoPath(itemId: Long): String? = pathIfPresent(itemId, LOGO)

    suspend fun save(item: Item) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        // Request the same bucketed widths the UI asks for so Loom serves an already
        // cached variant rather than resizing an original for each download.
        download(item.posterImageId, item.posterImageTag, POSTER_WIDTH, file(item.id, POSTER))
        download(item.backdropImageId, item.backdropImageTag, BACKDROP_WIDTH, file(item.id, BACKDROP))
        download(item.thumbImageId, item.thumbImageTag, POSTER_WIDTH, file(item.id, THUMB))
        download(item.logoImageId, item.logoImageTag, POSTER_WIDTH, file(item.id, LOGO))
    }

    suspend fun delete(itemId: Long) = withContext(Dispatchers.IO) {
        listOf(POSTER, BACKDROP, THUMB, LOGO).forEach { file(itemId, it).delete() }
        Unit
    }

    private suspend fun download(imageId: Long, tag: String?, width: Int, target: File) {
        val url = api.imageUrl(imageId, tag, width) ?: return
        runCatching { target.writeBytes(api.fetchBytes(url)) }
    }

    private fun file(itemId: Long, kind: String) = File(directory, "$itemId-$kind")

    // Returned as a file:// URI rather than a bare path so Coil routes it to the
    // file fetcher instead of trying to parse a scheme-less string.
    private fun pathIfPresent(itemId: Long, kind: String): String? =
        file(itemId, kind).takeIf { it.isFile }?.let { "file://${it.absolutePath}" }

    private companion object {
        const val POSTER = "poster"
        const val BACKDROP = "backdrop"
        const val THUMB = "thumb"
        const val LOGO = "logo"
        const val POSTER_WIDTH = 480
        const val BACKDROP_WIDTH = 1440
    }
}
