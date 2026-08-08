package xyz.five82.takeup.ui

import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.MediaFile

// Pure text derivations shared across screens; kept free of Compose so they
// stay unit-testable.

/** "2 h 14 m" or "58 m". */
fun formatRuntime(durationMs: Long): String {
    val minutes = durationMs / 60_000
    val hours = minutes / 60
    return if (hours > 0) "$hours h ${minutes % 60} m" else "$minutes m"
}

/** "1:47:32" or "7:32" for player clocks. */
fun formatClock(positionMs: Long): String {
    val totalSeconds = (positionMs / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/** "S4E1" or "S4E1-2" for multi-episode files. */
fun episodeLabel(item: Item): String {
    val base = "S${item.seasonNumber}E${item.episodeNumber}"
    return if (item.episodeEndNumber > item.episodeNumber) "$base-${item.episodeEndNumber}" else base
}

/** Minutes left for a partially watched item, or null when not started. */
fun remainingLabel(item: Item): String? {
    val progress = item.progress ?: return null
    if (progress.durationMs <= 0 || progress.positionMs <= 0 || progress.played) return null
    return formatRuntime(progress.durationMs - progress.positionMs) + " left"
}

/** Watched fraction for progress threads, or null when there is nothing to draw. */
fun progressFraction(item: Item): Float? {
    val progress = item.progress ?: return null
    if (progress.durationMs <= 0 || progress.positionMs <= 0 || progress.played) return null
    return (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f)
}

/**
 * Badge strip for a detail screen, derived from ffprobe stream facts:
 * resolution, dynamic range, video codec, default audio, container.
 */
fun techBadges(media: MediaFile?): List<String> {
    media ?: return emptyList()
    val badges = mutableListOf<String>()
    val video = media.streams?.firstOrNull { it.kind == "video" }
    if (video != null) {
        badges += when {
            video.width >= 3200 -> "4K"
            video.width >= 1800 -> "1080p"
            video.width >= 1200 -> "720p"
            video.width > 0 -> "SD"
            else -> ""
        }
        when (video.dynamicRange) {
            "dolby_vision" -> badges += "Dolby Vision"
            "hdr" -> badges += "HDR"
        }
        codecLabel(video.codec)?.let { badges += it }
    }
    val audio = media.streams?.filter { it.kind == "audio" }
        ?.let { all -> all.firstOrNull { it.isDefault } ?: all.firstOrNull() }
    if (audio != null) {
        val name = codecLabel(audio.codec) ?: audio.codec.uppercase()
        val layout = audioLayout(audio.channelLayout, audio.channels)
        badges += if (layout != null) "$name $layout" else name
    }
    containerLabel(media)?.let { badges += it }
    return badges.filter { it.isNotEmpty() }
}

/**
 * The badge for the file's container. ffprobe reports demuxer names like
 * "matroska,webm", so the filename extension is the honest label; the
 * probe name is only a fallback.
 */
fun containerLabel(media: MediaFile): String? {
    val extension = media.filename.substringAfterLast('.', "")
    if (extension.isNotEmpty() && extension.length <= 4) return extension.uppercase()
    val probed = media.container.substringBefore(',')
    if (probed.isEmpty()) return null
    return if (probed == "matroska") "MKV" else probed.uppercase()
}

private fun codecLabel(codec: String): String? = when (codec.lowercase()) {
    "hevc", "h265" -> "HEVC"
    "h264", "avc" -> "H.264"
    "av1" -> "AV1"
    "vp9" -> "VP9"
    "mpeg2video" -> "MPEG-2"
    "truehd" -> "TrueHD"
    "eac3" -> "DD+"
    "ac3" -> "DD"
    "dts" -> "DTS"
    "aac" -> "AAC"
    "flac" -> "FLAC"
    "opus" -> "Opus"
    "mp3" -> "MP3"
    "pcm_s16le", "pcm_s24le" -> "PCM"
    else -> null
}

/** "5.1(side)" -> "5.1"; falls back to a count-derived layout. */
fun audioLayout(channelLayout: String?, channels: Int): String? {
    val layout = channelLayout?.substringBefore('(')?.trim()
    if (!layout.isNullOrEmpty()) return layout
    return when (channels) {
        8 -> "7.1"
        6 -> "5.1"
        2 -> "2.0"
        1 -> "1.0"
        0 -> null
        else -> "$channels ch"
    }
}

/**
 * The episode to offer after [currentId] finishes: the next number in its
 * season, else the first episode of the next season. Specials (season zero)
 * only chain within themselves, mirroring Loom's Next Up rule.
 * [episodes] must hold the show's episodes; order does not matter.
 */
fun nextEpisodeAfter(episodes: List<Item>, currentId: Long): Item? {
    val current = episodes.firstOrNull { it.id == currentId } ?: return null
    val ordered = episodes
        .filter { it.kind == "episode" && (it.seasonNumber == 0) == (current.seasonNumber == 0) }
        .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
    val index = ordered.indexOfFirst { it.id == currentId }
    if (index < 0) return null
    return ordered.getOrNull(index + 1)
}

