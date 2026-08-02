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
        val array = objectFrom(body).getAsJsonArray("items")
            ?: throw JsonParseException("Loom response is missing items")
        return array.map { item(it.asJsonObject) }
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
            title = value.requiredString("title"),
            year = value.int("year"),
            overview = value.string("overview"),
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
