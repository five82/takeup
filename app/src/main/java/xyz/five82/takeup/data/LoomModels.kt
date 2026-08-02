package xyz.five82.takeup.data

data class LoomItem(
    val id: Long,
    val title: String,
    val year: Int,
    val overview: String,
    val progress: PlaybackProgress? = null,
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
    val streamUrl: String,
    val durationMs: Long,
    val resumePositionMs: Long,
    val container: String,
)
