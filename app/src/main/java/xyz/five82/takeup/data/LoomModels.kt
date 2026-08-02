package xyz.five82.takeup.data

import java.net.URLEncoder

data class LoomItem(
    val id: Long,
    val kind: String,
    val title: String,
    val year: Int,
    val overview: String,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val episodeEndNumber: Int = 0,
    val releaseDate: String = "",
    val posterImageId: Long = 0,
    val posterImageTag: String = "",
    val backdropImageId: Long = 0,
    val backdropImageTag: String = "",
    val mediaDurationMs: Long = 0,
    val progress: PlaybackProgress? = null,
) {
    fun posterUrl(serverUrl: String): String? =
        imageUrl(serverUrl, posterImageId, posterImageTag)

    fun backdropUrl(serverUrl: String): String? =
        imageUrl(serverUrl, backdropImageId, backdropImageTag)

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
