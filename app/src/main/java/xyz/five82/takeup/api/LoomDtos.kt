package xyz.five82.takeup.api

// DTOs mirroring Loom's /api/v1 JSON. Gson maps snake_case field names via
// LOWER_CASE_WITH_UNDERSCORES, and every field carries a default so Gson can
// use the synthesized no-arg constructor and omitted fields stay at their
// defaults instead of surprising us with nulls in non-null slots.

data class Item(
    val id: Long = 0,
    val libraryId: Long = 0,
    val parentId: Long? = null,
    val kind: String = "",
    val title: String = "",
    val year: Int = 0,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val episodeEndNumber: Int = 0,
    val tmdbId: Long = 0,
    val overview: String? = null,
    val tagline: String? = null,
    val releaseDate: String? = null,
    val genres: List<Genre>? = null,
    val credits: List<Credit>? = null,
    val voteAverage: Double = 0.0,
    val contentRating: String? = null,
    val status: String? = null,
    val totalSeasons: Int = 0,
    val posterImageId: Long = 0,
    val posterImageTag: String? = null,
    val backdropImageId: Long = 0,
    val backdropImageTag: String? = null,
    val logoImageId: Long = 0,
    val logoImageTag: String? = null,
    val thumbImageId: Long = 0,
    val thumbImageTag: String? = null,
    val mediaTag: String? = null,
    // Runtime of the item's own file; zero for shows and seasons.
    val durationMs: Long = 0,
    val addedAt: String? = null,
    val updatedAt: String? = null,
    val media: MediaFile? = null,
    val progress: Progress? = null,
    val episodeCount: Int = 0,
    val unwatchedCount: Int = 0,
    // Present only on search results: context for episodes listed outside
    // their show hierarchy.
    val seriesTitle: String? = null,
    val seasonTitle: String? = null,
) {
    val isPlayable: Boolean get() = kind == "movie" || kind == "episode"
}

data class Genre(
    val id: Long = 0,
    val name: String = "",
    val itemCount: Int = 0,
)

data class Credit(
    val personId: Long = 0,
    val name: String = "",
    val role: String = "",
    val character: String? = null,
)

data class MediaFile(
    val id: Long = 0,
    val itemId: Long = 0,
    val filename: String = "",
    val size: Long = 0,
    val tag: String = "",
    val durationMs: Long = 0,
    val container: String = "",
    val probeError: String? = null,
    val streams: List<Stream>? = null,
    val chapters: List<Chapter>? = null,
)

data class Stream(
    val index: Int = 0,
    val kind: String = "",
    val codec: String = "",
    val profile: String? = null,
    val language: String? = null,
    val title: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val channels: Int = 0,
    val channelLayout: String? = null,
    val dynamicRange: String? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
)

data class Chapter(
    val index: Int = 0,
    val startMs: Long = 0,
    val title: String? = null,
)

data class Progress(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val played: Boolean = false,
    val resumePositionMs: Long = 0,
    val updatedAt: String? = null,
)

data class Library(
    val id: Long = 0,
    val kind: String = "",
    val name: String = "",
    val itemCount: Long = 0,
)

data class Collection(
    val slug: String = "",
    val title: String = "",
    val items: List<Item> = emptyList(),
)

data class PlaybackInfo(
    val itemId: Long = 0,
    val media: MediaFile = MediaFile(),
    val streamUrl: String = "",
)

data class ImageOption(
    val provider: String = "",
    val providerPath: String = "",
    val language: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val aspectRatio: Double = 0.0,
    val voteAverage: Double = 0.0,
    val voteCount: Int = 0,
    val thumbnailUrl: String = "",
    val selected: Boolean = false,
)

data class ScanStatus(
    val running: Boolean = false,
    val library: String? = null,
    val startedAt: String? = null,
    val lastEndedAt: String? = null,
    val lastError: String? = null,
)
