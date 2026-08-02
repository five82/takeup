package xyz.five82.takeup.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

internal class LoomClient(
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 15_000,
) {
    suspend fun checkHealth(server: ServerAddress) {
        val body = request(server.api("api/v1/health"))
        LoomJson.requireHealthy(body)
    }

    suspend fun movies(server: ServerAddress): List<LoomItem> =
        pagedItems(server, "api/v1/items", "library=movies&kind=movie")

    suspend fun shows(server: ServerAddress): List<LoomItem> =
        pagedItems(server, "api/v1/items", "library=tv&kind=show")

    suspend fun children(server: ServerAddress, itemId: Long): List<LoomItem> {
        require(itemId > 0)
        return pagedItems(server, "api/v1/items/$itemId/children")
    }

    suspend fun continueWatching(server: ServerAddress): List<LoomItem> =
        LoomJson.items(request(server.api("api/v1/continue-watching?limit=20")))

    suspend fun recentlyAdded(server: ServerAddress): List<LoomItem> =
        LoomJson.items(request(server.api("api/v1/recently-added?limit=20")))

    suspend fun item(server: ServerAddress, itemId: Long): LoomItem {
        require(itemId > 0)
        return LoomJson.item(request(server.api("api/v1/items/$itemId")))
    }

    suspend fun playback(server: ServerAddress, itemId: Long): PlaybackResponse {
        require(itemId > 0)
        return LoomJson.playback(request(server.api("api/v1/items/$itemId/playback")))
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
            connection.setRequestProperty("User-Agent", "Takeup/0.5.0")
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

    private companion object {
        const val PAGE_SIZE = 200
    }
}

class LoomHttpException(
    val status: Int,
    detail: String,
) : IOException("Loom returned HTTP $status: $detail")
