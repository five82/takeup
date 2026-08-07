package xyz.five82.takeup.data

import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

internal class LoomClient(
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 15_000,
) {
    suspend fun checkHealth(server: ServerAddress) {
        val body = request(server.api("api/v1/health"))
        LoomJson.requireHealthy(body)
    }

    suspend fun movies(server: ServerAddress, genreId: Long = 0): List<LoomItem> {
        val query = if (genreId > 0) {
            "library=movies&kind=movie&genre_id=$genreId"
        } else {
            "library=movies&kind=movie"
        }
        return pagedItems(server, "api/v1/items", query)
    }

    suspend fun genres(server: ServerAddress): List<GenreSummary> =
        LoomJson.genres(request(server.api("api/v1/genres")))

    suspend fun search(server: ServerAddress, query: String): List<LoomItem> {
        val trimmed = query.trim()
        require(trimmed.isNotEmpty())
        val encoded = URLEncoder.encode(trimmed, Charsets.UTF_8.name())
        return pagedItems(server, "api/v1/search", "q=$encoded")
    }

    // Short films carry item kind "movie" but live in their own Loom library.
    suspend fun shorts(server: ServerAddress): List<LoomItem> =
        pagedItems(server, "api/v1/items", "library=shorts&kind=movie")

    suspend fun shows(server: ServerAddress): List<LoomItem> =
        pagedItems(server, "api/v1/items", "library=tv&kind=show")

    suspend fun children(server: ServerAddress, itemId: Long): List<LoomItem> {
        require(itemId > 0)
        return pagedItems(server, "api/v1/items/$itemId/children")
    }

    suspend fun continueWatching(server: ServerAddress): List<LoomItem> =
        LoomJson.items(request(server.api("api/v1/continue-watching?limit=20")))

    suspend fun nextUp(server: ServerAddress): List<LoomItem> =
        LoomJson.items(request(server.api("api/v1/next-up?limit=20")))

    suspend fun recentlyAdded(server: ServerAddress): List<LoomItem> =
        LoomJson.items(request(server.api("api/v1/recently-added?limit=20")))

    suspend fun item(server: ServerAddress, itemId: Long): LoomItem =
        LoomJson.item(itemJson(server, itemId))

    // Downloads keep the raw response as their offline snapshot so LoomJson stays the
    // single decoder and there is no second metadata format to drift out of sync.
    suspend fun itemJson(server: ServerAddress, itemId: Long): String {
        require(itemId > 0)
        return request(server.api("api/v1/items/$itemId"))
    }

    suspend fun fetchBytes(uri: URI): ByteArray = withContext(Dispatchers.IO) {
        val connection = uri.toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val status = connection.responseCode
            if (status !in 200..299) {
                throw LoomHttpException(status, connection.responseMessage.orEmpty())
            }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun playback(server: ServerAddress, itemId: Long): PlaybackResponse {
        require(itemId > 0)
        return LoomJson.playback(request(server.api("api/v1/items/$itemId/playback")))
    }

    suspend fun artworkOptions(
        server: ServerAddress,
        itemId: Long,
        kind: ArtworkKind,
    ): List<ArtworkOption> {
        require(itemId > 0)
        return LoomJson.artworkOptions(
            request(server.api("api/v1/items/$itemId/images/${kind.apiValue}/options")),
        )
    }

    suspend fun selectArtwork(
        server: ServerAddress,
        itemId: Long,
        kind: ArtworkKind,
        option: ArtworkOption,
    ) {
        require(itemId > 0)
        val body = JsonObject().apply {
            addProperty("provider", option.provider)
            addProperty("provider_path", option.providerPath)
        }.toString()
        request(
            uri = server.api("api/v1/items/$itemId/images/${kind.apiValue}"),
            method = "PUT",
            requestBody = body,
        )
    }

    suspend fun resetArtwork(
        server: ServerAddress,
        itemId: Long,
        kind: ArtworkKind,
    ) {
        require(itemId > 0)
        request(
            uri = server.api("api/v1/items/$itemId/images/${kind.apiValue}/reset"),
            method = "POST",
        )
    }

    suspend fun saveProgress(
        server: ServerAddress,
        itemId: Long,
        positionMs: Long,
        durationMs: Long,
    ) {
        require(itemId > 0)
        require(positionMs >= 0 && durationMs > 0)
        val body = "{\"position_ms\":$positionMs,\"duration_ms\":$durationMs}"
        request(
            uri = server.api("api/v1/items/$itemId/progress"),
            method = "PUT",
            requestBody = body,
        )
    }

    private suspend fun pagedItems(
        server: ServerAddress,
        path: String,
        query: String = "",
    ): List<LoomItem> {
        val items = mutableListOf<LoomItem>()
        do {
            val pageQuery = buildString {
                if (query.isNotBlank()) {
                    append(query)
                    append('&')
                }
                append("limit=$PAGE_SIZE&offset=${items.size}")
            }
            val page = LoomJson.items(request(server.api("$path?$pageQuery")))
            items += page
        } while (page.size == PAGE_SIZE)
        return items
    }

    private suspend fun request(
        uri: URI,
        method: String = "GET",
        requestBody: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val connection = uri.toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (requestBody != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(requestBody) }
            }

            val status = connection.responseCode
            val responseBody = (if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            })?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (status !in 200..299) {
                val detail = LoomJson.error(responseBody) ?: connection.responseMessage
                throw LoomHttpException(status, detail)
            }
            responseBody
        } finally {
            connection.disconnect()
        }
    }

    internal companion object {
        const val PAGE_SIZE = 200
        const val USER_AGENT = "Takeup/0.7.0"
    }
}

class LoomHttpException(
    val status: Int,
    detail: String,
) : IOException("Loom returned HTTP $status: $detail")
