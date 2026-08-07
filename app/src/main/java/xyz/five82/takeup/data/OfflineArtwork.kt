package xyz.five82.takeup.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

/**
 * Local copies of a downloaded item's artwork. Coil's disk cache is evictable and
 * sized by the platform, so it cannot be relied on once the server is unreachable.
 */
internal class OfflineArtwork(
    context: Context,
    private val client: LoomClient,
) {
    private val directory = File(context.applicationContext.filesDir, "offline-art")

    fun posterPath(itemId: Long): String? = pathIfPresent(itemId, POSTER)

    fun backdropPath(itemId: Long): String? = pathIfPresent(itemId, BACKDROP)

    fun logoPath(itemId: Long): String? = pathIfPresent(itemId, LOGO)

    suspend fun save(serverUrl: String, item: LoomItem) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        // Request the same bucketed widths the UI asks for so Loom serves an already
        // cached variant rather than resizing an original for each download.
        download(item.posterUrl(serverUrl), POSTER_WIDTH, file(item.id, POSTER))
        download(item.backdropUrl(serverUrl), BACKDROP_WIDTH, file(item.id, BACKDROP))
        download(item.logoUrl(serverUrl), POSTER_WIDTH, file(item.id, LOGO))
    }

    suspend fun delete(itemId: Long) = withContext(Dispatchers.IO) {
        listOf(POSTER, BACKDROP, LOGO).forEach { file(itemId, it).delete() }
        Unit
    }

    private suspend fun download(url: String?, width: Int, target: File) {
        val source = url ?: return
        runCatching {
            val bytes = client.fetchBytes(URI(imageUrlAtWidth(source, width)))
            target.writeBytes(bytes)
        }
    }

    private fun file(itemId: Long, kind: String) = File(directory, "$itemId-$kind")

    // Returned as a file:// URI rather than a bare path so Coil routes it to the
    // file fetcher instead of trying to parse a scheme-less string.
    private fun pathIfPresent(itemId: Long, kind: String): String? =
        file(itemId, kind).takeIf { it.isFile }?.let { "file://${it.absolutePath}" }

    private companion object {
        const val POSTER = "poster"
        const val BACKDROP = "backdrop"
        const val LOGO = "logo"
        const val POSTER_WIDTH = 480
        const val BACKDROP_WIDTH = 1440
    }
}
