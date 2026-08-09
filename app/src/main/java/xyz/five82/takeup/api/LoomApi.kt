package xyz.five82.takeup.api

import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

/** Loom answered with an HTTP error; [message] is the server's error field. */
class LoomException(val code: Int, message: String) : IOException(message)

/**
 * Hand-rolled client for Loom's unauthenticated LAN API. The base URL is
 * mutable because the server address is user configuration, not build
 * configuration.
 */
class LoomApi(@Volatile var baseUrl: String? = null) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val gson = loomGson

    private val jsonType = "application/json".toMediaType()

    // -- browse --------------------------------------------------------------

    suspend fun health() {
        get("/api/v1/health")
    }

    suspend fun libraries(): List<Library> = itemsList(get("/api/v1/libraries"), Library::class.java)

    suspend fun genres(): List<Genre> = itemsList(get("/api/v1/genres"), Genre::class.java)

    suspend fun collections(): List<Collection> =
        itemsList(get("/api/v1/collections"), Collection::class.java)

    suspend fun item(id: Long): Item = gson.fromJson(itemJson(id), Item::class.java)

    /**
     * Raw item response body. A download stores this verbatim as its offline
     * snapshot so there is exactly one decoder for online and offline items.
     */
    suspend fun itemJson(id: Long): String = get("/api/v1/items/$id")

    /** One page of top-level items. Loom caps limit at 200. */
    suspend fun items(
        library: String,
        genreId: Long? = null,
        limit: Int = PAGE_LIMIT,
        offset: Int = 0,
    ): List<Item> = itemsList(
        get(
            "/api/v1/items",
            "library" to library,
            "genre_id" to genreId?.toString(),
            "limit" to limit.toString(),
            "offset" to offset.toString(),
        ),
        Item::class.java,
    )

    /** Every top-level item in a library, walking pages until a short one. */
    suspend fun allItems(library: String, genreId: Long? = null): List<Item> {
        val all = mutableListOf<Item>()
        var offset = 0
        while (true) {
            val page = items(library, genreId, PAGE_LIMIT, offset)
            all += page
            if (page.size < PAGE_LIMIT) return all
            offset += PAGE_LIMIT
        }
    }

    suspend fun children(id: Long): List<Item> {
        val all = mutableListOf<Item>()
        var offset = 0
        while (true) {
            val page = itemsList(
                get(
                    "/api/v1/items/$id/children",
                    "limit" to PAGE_LIMIT.toString(),
                    "offset" to offset.toString(),
                ),
                Item::class.java,
            )
            all += page
            if (page.size < PAGE_LIMIT) return all
            offset += PAGE_LIMIT
        }
    }

    suspend fun search(query: String, limit: Int = 100): SearchResponse =
        gson.fromJson(
            get("/api/v1/search", "q" to query, "limit" to limit.toString()),
            SearchResponse::class.java,
        )

    suspend fun continueWatching(limit: Int = 20): List<Item> =
        itemsList(get("/api/v1/continue-watching", "limit" to limit.toString()), Item::class.java)

    suspend fun nextUp(limit: Int = 20): List<Item> =
        itemsList(get("/api/v1/next-up", "limit" to limit.toString()), Item::class.java)

    suspend fun recentlyAdded(limit: Int = 20): List<Item> =
        itemsList(get("/api/v1/recently-added", "limit" to limit.toString()), Item::class.java)

    /** Finished movies and fully watched shows, most recently finished first. */
    suspend fun recentlyPlayed(limit: Int = 20): List<Item> =
        itemsList(get("/api/v1/recently-played", "limit" to limit.toString()), Item::class.java)

    // -- playback ------------------------------------------------------------

    suspend fun playback(id: Long): PlaybackInfo =
        gson.fromJson(get("/api/v1/items/$id/playback"), PlaybackInfo::class.java)

    suspend fun saveProgress(id: Long, positionMs: Long, durationMs: Long): Progress {
        val body = gson.toJson(mapOf("position_ms" to positionMs, "duration_ms" to durationMs))
        return gson.fromJson(send("PUT", "/api/v1/items/$id/progress", body), Progress::class.java)
    }

    suspend fun markPlayed(id: Long) {
        send("POST", "/api/v1/items/$id/played", null)
    }

    suspend fun clearPlayed(id: Long) {
        send("DELETE", "/api/v1/items/$id/played", null)
    }

    // -- artwork -------------------------------------------------------------

    suspend fun imageOptions(id: Long, kind: String): List<ImageOption> =
        itemsList(get("/api/v1/items/$id/images/$kind/options"), ImageOption::class.java)

    suspend fun selectImage(id: Long, kind: String, provider: String, providerPath: String) {
        val body = gson.toJson(mapOf("provider" to provider, "provider_path" to providerPath))
        send("PUT", "/api/v1/items/$id/images/$kind", body)
    }

    suspend fun resetImage(id: Long, kind: String) {
        send("POST", "/api/v1/items/$id/images/$kind/reset", null)
    }

    // -- scanning ------------------------------------------------------------

    suspend fun triggerScan() {
        send("POST", "/api/v1/scan", null)
    }

    suspend fun scanStatus(): ScanStatus = gson.fromJson(get("/api/v1/scan"), ScanStatus::class.java)

    // -- URLs ----------------------------------------------------------------

    /**
     * URL for a Loom-served image. Width is snapped up to the server's resize
     * buckets so the whole app shares a handful of cached variants.
     */
    fun imageUrl(imageId: Long, tag: String?, widthPx: Int): String? {
        if (imageId <= 0) return null
        val base = baseUrl ?: return null
        val width = WIDTH_BUCKETS.firstOrNull { it >= widthPx } ?: WIDTH_BUCKETS.last()
        val builder = StringBuilder("$base/api/v1/images/$imageId?width=$width")
        if (!tag.isNullOrEmpty()) builder.append("&tag=").append(tag)
        return builder.toString()
    }

    /** Absolute form of a server-relative URL such as a playback stream_url. */
    fun absoluteUrl(path: String): String {
        val base = baseUrl ?: return path
        return if (path.startsWith("http")) path else base + path
    }

    /** Fetches a URL's body bytes, for copying artwork into offline storage. */
    suspend fun fetchBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw LoomException(response.code, "Loom returned HTTP ${response.code}")
            }
            response.body.bytes()
        }
    }

    // -- plumbing ------------------------------------------------------------

    private fun requestUrl(path: String, vararg query: Pair<String, String?>): HttpUrl {
        val base = baseUrl ?: throw LoomException(0, "No server configured")
        val builder = (base + path).toHttpUrlOrNull()?.newBuilder()
            ?: throw LoomException(0, "Invalid server address")
        for ((key, value) in query) {
            if (value != null) builder.addQueryParameter(key, value)
        }
        return builder.build()
    }

    private suspend fun get(path: String, vararg query: Pair<String, String?>): String =
        execute(Request.Builder().url(requestUrl(path, *query)).build())

    private suspend fun send(method: String, path: String, jsonBody: String?): String {
        val body = jsonBody?.toRequestBody(jsonType)
            ?: if (method == "POST" || method == "PUT") ByteArray(0).toRequestBody(null) else null
        return execute(Request.Builder().url(requestUrl(path)).method(method, body).build())
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw LoomException(response.code, errorMessage(body, response.code))
            }
            body
        }
    }

    private fun errorMessage(body: String, code: Int): String = try {
        JsonParser.parseString(body).asJsonObject.get("error").asString
    } catch (_: Exception) {
        "Loom returned HTTP $code"
    }

    private fun <T> itemsList(body: String, elementClass: Class<T>): List<T> {
        val items = JsonParser.parseString(body).asJsonObject.get("items") ?: return emptyList()
        if (items.isJsonNull) return emptyList()
        val listType: Type = TypeToken.getParameterized(List::class.java, elementClass).type
        return gson.fromJson(items, listType) ?: emptyList()
    }

    companion object {
        const val PAGE_LIMIT = 200
        val WIDTH_BUCKETS = listOf(240, 480, 960, 1440)

        /**
         * Turns user input like "loom:8097" or "http://192.168.1.20:8097/"
         * into a base URL, or null when it cannot be one.
         */
        fun normalizeAddress(input: String): String? {
            var address = input.trim()
            if (address.isEmpty()) return null
            if (!address.contains("://")) address = "http://$address"
            address = address.trimEnd('/')
            if (!address.startsWith("http://") && !address.startsWith("https://")) return null
            val url = address.toHttpUrlOrNull() ?: return null
            return address.takeIf { url.host.isNotEmpty() }
        }
    }
}
