package xyz.five82.takeup.data

import java.net.URLEncoder

data class LoomItem(
    val id: Long,
    val kind: String,
    val title: String,
    val year: Int,
    val overview: String,
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
    val mediaDurationMs: Long = 0,
    val mediaStreams: List<MediaStream> = emptyList(),
    val progress: PlaybackProgress? = null,
    val seriesTitle: String = "",
    val seasonTitle: String = "",
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
)

data class PreparedPlayback(
    val itemId: Long,
    val title: String,
    val contextTitle: String,
    val streamUrl: String,
    val durationMs: Long,
    val resumePositionMs: Long,
    val container: String,
)

data class HomeContent(
    val continueWatching: List<LoomItem>,
    val recentlyAdded: List<LoomItem>,
    val movies: List<LoomItem>,
    val shows: List<LoomItem>,
)
