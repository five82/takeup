package xyz.five82.takeup.data

import java.net.URLEncoder

data class LoomItem(
    val id: Long,
    val kind: String,
    val title: String,
    val year: Int,
    val overview: String,
    val tagline: String = "",
    // TMDB's audience score, out of ten. Zero when TMDB has no votes for the
    // title, which is why nothing here treats it as a real score of zero.
    val voteAverage: Double = 0.0,
    // The certification from the US board, blank when TMDB files the title
    // under no US board at all.
    val contentRating: String = "",
    // Status and totalSeasons describe a show's whole run rather than the part
    // of it this library holds, so nothing should read them as a count of what
    // is here. Loom leaves both empty for movies and episodes.
    val status: String = "",
    val totalSeasons: Int = 0,
    val tmdbId: Long = 0,
    val parentId: Long = 0,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val episodeEndNumber: Int = 0,
    val releaseDate: String = "",
    val posterImageId: Long = 0,
    val posterImageTag: String = "",
    val backdropImageId: Long = 0,
    val backdropImageTag: String = "",
    val logoImageId: Long = 0,
    val logoImageTag: String = "",
    // Loom only attaches the media object to single-item responses, so duration,
    // streams, and size stay empty for items that came from a list endpoint.
    // mediaTag is the exception: every item response carries the version the last
    // scan recorded. A download whose tag no longer matches the item's was taken
    // from a superseded copy of the file.
    val mediaDurationMs: Long = 0,
    val mediaStreams: List<MediaStream> = emptyList(),
    val mediaChapterStartsMs: List<Long> = emptyList(),
    val mediaTag: String = "",
    val mediaSizeBytes: Long = 0,
    val progress: PlaybackProgress? = null,
    // Shows and seasons carry a rollup of the episodes beneath them so a grid can
    // badge a series without walking down to every episode. Loom leaves both at
    // zero for movies and episodes, and for a show with nothing left to watch.
    val episodeCount: Int = 0,
    val unwatchedCount: Int = 0,
    val seriesTitle: String = "",
    val seasonTitle: String = "",
    val genres: List<Genre> = emptyList(),
    // Loom attaches credits to single-item responses only, so a listed item never
    // carries them. Movies get their directors and billed cast, shows the cast
    // alone, and episodes nothing at all.
    val credits: List<Credit> = emptyList(),
) {
    fun posterUrl(serverUrl: String): String? =
        imageUrl(serverUrl, posterImageId, posterImageTag)

    fun backdropUrl(serverUrl: String): String? =
        imageUrl(serverUrl, backdropImageId, backdropImageTag)

    fun logoUrl(serverUrl: String): String? =
        imageUrl(serverUrl, logoImageId, logoImageTag)

    fun episodeLabel(): String? {
        if (kind != "episode" || episodeNumber <= 0) return null
        return buildString {
            append("S")
            append(seasonNumber.toString().padStart(2, '0'))
            append("E")
            append(episodeNumber.toString().padStart(2, '0'))
            if (episodeEndNumber > episodeNumber) {
                append("-")
                append(episodeEndNumber.toString().padStart(2, '0'))
            }
        }
    }

    fun episodeContext(): String = listOfNotNull(
        seriesTitle.takeIf { it.isNotBlank() },
        seasonTitle.takeIf { it.isNotBlank() },
        episodeLabel(),
    ).joinToString(" \u00B7 ")

    private fun imageUrl(serverUrl: String, imageId: Long, tag: String): String? {
        if (imageId <= 0) return null
        val query = if (tag.isBlank()) {
            ""
        } else {
            "?tag=${URLEncoder.encode(tag, Charsets.UTF_8.name())}"
        }
        return ServerAddress.parse(serverUrl)
            .api("api/v1/images/$imageId$query")
            .toString()
    }
}

// Loom serves resized artwork variants at these fixed width buckets, snapping
// any requested width up to the next one. Mirroring the buckets here keeps the
// URLs stable, so slots of a similar size share one cached variant instead of
// each pulling its own resize.
private val imageWidthBuckets = listOf(240, 480, 960, 1440)

private const val LOOM_IMAGE_PATH = "/api/v1/images/"

/**
 * Returns [url] asking Loom for a variant at least [pixels] wide, so a phone
 * never decodes a 4K TMDB original for a small card. URLs that do not point at
 * Loom's image endpoint (TMDB artwork options, which arrive pre-sized) are
 * returned unchanged.
 */
fun imageUrlAtWidth(url: String, pixels: Int): String {
    if (!url.contains(LOOM_IMAGE_PATH)) return url
    val bucket = imageWidthBuckets.firstOrNull { pixels <= it } ?: imageWidthBuckets.last()
    val separator = if (url.contains('?')) '&' else '?'
    return "$url${separator}width=$bucket"
}

/**
 * A hand-curated shelf Loom groups movies into - a franchise, a studio, a
 * director. Loom resolves the members and serves them alongside the shelf, so a
 * collection is complete the moment it arrives and never fetches on its own.
 */
data class LoomCollection(
    val slug: String,
    val title: String,
    val items: List<LoomItem>,
) {
    // Card artwork for the shelf. A backdrop is preferred because the card is
    // landscape and crops a poster badly; a poster is the fallback for a shelf
    // whose members all lack one.
    fun artworkUrl(serverUrl: String): String? =
        items.firstNotNullOfOrNull { it.backdropUrl(serverUrl) }
            ?: items.firstNotNullOfOrNull { it.posterUrl(serverUrl) }
}

data class Genre(
    val id: Long,
    val name: String,
)

/**
 * One person's billing on a title, in the order Loom serves them: directors
 * first, then the cast as TMDB bills it. [role] stays a string rather than an
 * enum so a role Loom adds later still lists instead of failing the parse.
 * [character] is routinely blank - TMDB leaves it off older entries.
 */
data class Credit(
    val personId: Long,
    val name: String,
    val role: String,
    val character: String = "",
)

const val CREDIT_ROLE_DIRECTOR = "director"

data class GenreSummary(
    val id: Long,
    val name: String,
    val itemCount: Int,
)

data class MediaStream(
    val kind: String,
    val codec: String,
    val profile: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val channels: Int = 0,
    val channelLayout: String = "",
    val dynamicRange: MediaDynamicRange? = null,
    val isDefault: Boolean = false,
)

enum class MediaDynamicRange {
    SDR,
    HDR,
    DOLBY_VISION,
}

enum class ArtworkKind(val apiValue: String) {
    POSTER("poster"),
    BACKDROP("backdrop"),
    LOGO("logo"),

    // Loom splits TMDB backdrops into textless backdrops and thumbs, the
    // language-tagged ones with title art baked in. Takeup only selects thumbs
    // for now; no screen draws them yet.
    THUMB("thumb"),
}

data class ArtworkOption(
    val provider: String,
    val providerPath: String,
    val language: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val thumbnailUrl: String,
    val selected: Boolean = false,
)

data class PlaybackProgress(
    val positionMs: Long,
    val durationMs: Long,
    val played: Boolean,
    val resumePositionMs: Long,
)

data class PlaybackResponse(
    val streamPath: String,
    val durationMs: Long,
    val container: String,
    val tag: String,
    val sizeBytes: Long,
    val chapterStartsMs: List<Long> = emptyList(),
)

data class PreparedPlayback(
    val itemId: Long,
    val title: String,
    val contextTitle: String,
    val streamUrl: String,
    val durationMs: Long,
    val resumePositionMs: Long,
    val container: String,
    // Ascending chapter offsets, as Loom recorded them from the container. Loom
    // omits chapters entirely for a file with fewer than two, so an empty list
    // means the file has nowhere to skip to and the player hides its chapter
    // controls.
    val chapterStartsMs: List<Long> = emptyList(),
)

data class HomeContent(
    val continueWatching: List<LoomItem>,
    // Continue Watching holds only partially watched items, so a show leaves it
    // the moment an episode finishes. Next Up carries the following episode, and
    // Loom keeps a show out of one row while the other is offering it.
    val nextUp: List<LoomItem>,
    val recentlyAdded: List<LoomItem>,
    val movies: List<LoomItem>,
    val shorts: List<LoomItem>,
    val shows: List<LoomItem>,
    val collections: List<LoomCollection>,
)
