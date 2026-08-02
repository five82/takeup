package xyz.five82.takeup.data

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

internal object LoomJson {
    fun requireHealthy(body: String) {
        val status = objectFrom(body).string("status")
        if (status != "ok") {
            throw JsonParseException("Loom returned an unexpected health response")
        }
    }

    fun items(body: String): List<LoomItem> {
        val value = objectFrom(body).get("items")
            ?: throw JsonParseException("Loom response is missing items")
        if (value.isJsonNull) return emptyList()
        if (!value.isJsonArray) {
            throw JsonParseException("Loom response items must be an array")
        }
        return value.asJsonArray.map { item(it.asJsonObject) }
    }

    fun item(body: String): LoomItem = item(objectFrom(body))

    fun playback(body: String): PlaybackResponse {
        val root = objectFrom(body)
        val media = root.getAsJsonObject("media")
            ?: throw JsonParseException("Loom playback response is missing media")
        return PlaybackResponse(
            streamPath = root.requiredString("stream_url"),
            durationMs = media.long("duration_ms"),
            container = media.string("container"),
        )
    }

    fun error(body: String): String? = runCatching {
        objectFrom(body).string("error").ifBlank { null }
    }.getOrNull()

    private fun item(value: JsonObject): LoomItem {
        val progress = value.getAsJsonObject("progress")?.let {
            PlaybackProgress(
                positionMs = it.long("position_ms"),
                durationMs = it.long("duration_ms"),
                played = it.boolean("played"),
                resumePositionMs = it.long("resume_position_ms"),
            )
        }
        return LoomItem(
            id = value.long("id"),
            kind = value.requiredString("kind"),
            title = value.requiredString("title"),
            year = value.int("year"),
            overview = value.string("overview"),
            parentId = value.long("parent_id"),
            seasonNumber = value.int("season_number"),
            episodeNumber = value.int("episode_number"),
            episodeEndNumber = value.int("episode_end_number"),
            releaseDate = value.string("release_date"),
            posterImageId = value.long("poster_image_id"),
            posterImageTag = value.string("poster_image_tag"),
            backdropImageId = value.long("backdrop_image_id"),
            backdropImageTag = value.string("backdrop_image_tag"),
            mediaDurationMs = value.getAsJsonObject("media")?.long("duration_ms") ?: 0L,
            progress = progress,
        )
    }

    private fun objectFrom(body: String): JsonObject = try {
        JsonParser.parseString(body).asJsonObject
    } catch (error: RuntimeException) {
        throw JsonParseException("Loom returned invalid JSON", error)
    }

    private fun JsonObject.requiredString(name: String): String =
        get(name)?.takeUnless { it.isJsonNull }?.asString
            ?: throw JsonParseException("Loom response is missing $name")

    private fun JsonObject.string(name: String): String =
        get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

    private fun JsonObject.long(name: String): Long =
        get(name)?.takeUnless { it.isJsonNull }?.asLong ?: 0L

    private fun JsonObject.int(name: String): Int =
        get(name)?.takeUnless { it.isJsonNull }?.asInt ?: 0

    private fun JsonObject.boolean(name: String): Boolean =
        get(name)?.takeUnless { it.isJsonNull }?.asBoolean ?: false
}
