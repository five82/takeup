package xyz.five82.takeup.data

import com.google.gson.JsonArray
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

    fun items(body: String): List<LoomItem> =
        itemsOf(objectFrom(body)).map { item(it.asJsonObject) }

    fun item(body: String): LoomItem = item(objectFrom(body))

    // Every shelf arrives with its members already resolved, so a collection is
    // an items array wrapped in a name.
    fun collections(body: String): List<LoomCollection> =
        itemsOf(objectFrom(body)).map { element ->
            val collection = element.asJsonObject
            LoomCollection(
                slug = collection.requiredString("slug"),
                title = collection.requiredString("title"),
                items = itemsOf(collection).map { item(it.asJsonObject) },
            )
        }

    fun genres(body: String): List<GenreSummary> =
        itemsOf(objectFrom(body)).map { element ->
            val genre = element.asJsonObject
            GenreSummary(
                id = genre.long("id"),
                name = genre.requiredString("name"),
                itemCount = genre.int("item_count"),
            )
        }

    fun artworkOptions(body: String): List<ArtworkOption> =
        itemsOf(objectFrom(body)).map { element ->
            val option = element.asJsonObject
            ArtworkOption(
                provider = option.requiredString("provider"),
                providerPath = option.requiredString("provider_path"),
                language = option.string("language"),
                width = option.int("width"),
                height = option.int("height"),
                thumbnailUrl = option.requiredString("thumbnail_url"),
                selected = option.boolean("selected"),
            )
        }

    fun playback(body: String): PlaybackResponse {
        val root = objectFrom(body)
        val media = root.getAsJsonObject("media")
            ?: throw JsonParseException("Loom playback response is missing media")
        return PlaybackResponse(
            streamPath = root.requiredString("stream_url"),
            durationMs = media.long("duration_ms"),
            container = media.string("container"),
            tag = media.requiredString("tag"),
            sizeBytes = media.long("size"),
            chapterStartsMs = chapterStartsMs(media),
        )
    }

    fun error(body: String): String? = runCatching {
        objectFrom(body).string("error").ifBlank { null }
    }.getOrNull()

    private fun item(value: JsonObject): LoomItem {
        val media = value.getAsJsonObject("media")
        val streamValues = media?.get("streams")
        val streams = when {
            media == null -> emptyList()
            streamValues == null || streamValues.isJsonNull -> {
                throw JsonParseException("Loom media is missing streams")
            }
            !streamValues.isJsonArray -> throw JsonParseException("Loom media streams must be an array")
            streamValues.asJsonArray.isEmpty -> throw JsonParseException("Loom media streams are empty")
            else -> streamValues.asJsonArray.map { element -> mediaStream(element.asJsonObject) }
        }
        val progress = value.getAsJsonObject("progress")?.let {
            PlaybackProgress(
                positionMs = it.long("position_ms"),
                durationMs = it.long("duration_ms"),
                played = it.boolean("played"),
                resumePositionMs = it.long("resume_position_ms"),
            )
        }
        val genreValues = value.get("genres")
        val genres = when {
            genreValues == null || genreValues.isJsonNull -> emptyList()
            !genreValues.isJsonArray -> throw JsonParseException("Loom item genres must be an array")
            else -> genreValues.asJsonArray.map { element ->
                val genre = element.asJsonObject
                Genre(
                    id = genre.long("id"),
                    name = genre.requiredString("name"),
                )
            }
        }
        val creditValues = value.get("credits")
        val credits = when {
            creditValues == null || creditValues.isJsonNull -> emptyList()
            !creditValues.isJsonArray -> throw JsonParseException("Loom item credits must be an array")
            else -> creditValues.asJsonArray.map { element ->
                val credit = element.asJsonObject
                Credit(
                    personId = credit.long("person_id"),
                    name = credit.requiredString("name"),
                    role = credit.requiredString("role"),
                    character = credit.string("character"),
                )
            }
        }
        return LoomItem(
            id = value.long("id"),
            kind = value.requiredString("kind"),
            title = value.requiredString("title"),
            year = value.int("year"),
            overview = value.string("overview"),
            tagline = value.string("tagline"),
            voteAverage = value.double("vote_average"),
            contentRating = value.string("content_rating"),
            status = value.string("status"),
            totalSeasons = value.int("total_seasons"),
            tmdbId = value.long("tmdb_id"),
            parentId = value.long("parent_id"),
            seasonNumber = value.int("season_number"),
            episodeNumber = value.int("episode_number"),
            episodeEndNumber = value.int("episode_end_number"),
            releaseDate = value.string("release_date"),
            posterImageId = value.long("poster_image_id"),
            posterImageTag = value.string("poster_image_tag"),
            backdropImageId = value.long("backdrop_image_id"),
            backdropImageTag = value.string("backdrop_image_tag"),
            logoImageId = value.long("logo_image_id"),
            logoImageTag = value.string("logo_image_tag"),
            mediaDurationMs = media?.long("duration_ms") ?: 0L,
            mediaStreams = streams,
            mediaChapterStartsMs = media?.let(::chapterStartsMs).orEmpty(),
            // A single-item response nests the version in media; every response,
            // list endpoints included, also carries it as media_tag. Both are the
            // version the last scan recorded, and reading the flat one is what
            // lets a list notice that a download has been superseded.
            mediaTag = media?.string("tag").orEmpty().ifBlank { value.string("media_tag") },
            mediaSizeBytes = media?.long("size") ?: 0L,
            progress = progress,
            episodeCount = value.int("episode_count"),
            unwatchedCount = value.int("unwatched_count"),
            seriesTitle = value.string("series_title"),
            seasonTitle = value.string("season_title"),
            genres = genres,
            credits = credits,
        )
    }

    // Loom sends chapters in file order, already dropping the degenerate case of a
    // single chapter spanning the whole runtime. Only the offsets are read: titles
    // on a disc rip are almost always "Chapter 01" and nothing here shows them.
    private fun chapterStartsMs(media: JsonObject): List<Long> {
        val chapters = media.get("chapters")?.takeUnless { it.isJsonNull } ?: return emptyList()
        if (!chapters.isJsonArray) {
            throw JsonParseException("Loom media chapters must be an array")
        }
        return chapters.asJsonArray.map { element -> element.asJsonObject.long("start_ms") }
    }

    private fun mediaStream(stream: JsonObject): MediaStream {
        val kind = stream.requiredString("kind")
        val codec = stream.requiredString("codec")
        return when (kind) {
            "video" -> MediaStream(
                kind = kind,
                codec = codec,
                profile = stream.string("profile"),
                width = stream.requiredPositiveInt("width"),
                height = stream.requiredPositiveInt("height"),
                dynamicRange = when (stream.requiredString("dynamic_range")) {
                    "sdr" -> MediaDynamicRange.SDR
                    "hdr" -> MediaDynamicRange.HDR
                    "dolby_vision" -> MediaDynamicRange.DOLBY_VISION
                    else -> throw JsonParseException("Loom video stream has invalid dynamic_range")
                },
                isDefault = stream.requiredBoolean("is_default"),
            )
            "audio" -> MediaStream(
                kind = kind,
                codec = codec,
                profile = stream.string("profile"),
                channels = stream.requiredPositiveInt("channels"),
                channelLayout = stream.requiredString("channel_layout"),
                isDefault = stream.requiredBoolean("is_default"),
            )
            "subtitle" -> MediaStream(
                kind = kind,
                codec = codec,
                isDefault = stream.requiredBoolean("is_default"),
            )
            else -> throw JsonParseException("Loom media contains an unsupported stream kind")
        }
    }

    // Every list Loom serves - items, genres, artwork options, collections -
    // arrives under the same "items" key, so they share one unwrapping.
    private fun itemsOf(value: JsonObject): JsonArray {
        val items = value.get("items")
            ?: throw JsonParseException("Loom response is missing items")
        if (items.isJsonNull) return JsonArray()
        if (!items.isJsonArray) {
            throw JsonParseException("Loom response items must be an array")
        }
        return items.asJsonArray
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

    private fun JsonObject.double(name: String): Double =
        get(name)?.takeUnless { it.isJsonNull }?.asDouble ?: 0.0

    private fun JsonObject.int(name: String): Int =
        get(name)?.takeUnless { it.isJsonNull }?.asInt ?: 0

    private fun JsonObject.requiredPositiveInt(name: String): Int =
        int(name).takeIf { it > 0 }
            ?: throw JsonParseException("Loom response is missing $name")

    private fun JsonObject.requiredBoolean(name: String): Boolean =
        get(name)?.takeUnless { it.isJsonNull }?.asBoolean
            ?: throw JsonParseException("Loom response is missing $name")

    private fun JsonObject.boolean(name: String): Boolean =
        get(name)?.takeUnless { it.isJsonNull }?.asBoolean ?: false
}
