package xyz.five82.takeup.data

import java.io.IOException
import java.util.Locale

/**
 * Download types and decision logic. Deliberately free of Android and Media3
 * imports so every rule here stays reachable from plain JVM unit tests.
 */

enum class DownloadState { Queued, Downloading, Completed, Failed, Removing }

data class DownloadEntry(
    val item: LoomItem,
    val state: DownloadState,
    // The exact tagged stream URL the cached bytes are keyed by. Playback must reuse
    // this string verbatim or it will miss the cache and re-stream the whole file.
    val uri: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val logoPath: String? = null,
) {
    val tag: String = tagFromStreamUrl(uri)
}

/** Loom keys a media version with a `tag` query parameter on the stream URL. */
fun tagFromStreamUrl(url: String): String {
    val query = url.substringAfter('?', "").ifEmpty { return "" }
    return query.split('&')
        .firstOrNull { it.startsWith("tag=") }
        ?.removePrefix("tag=")
        .orEmpty()
}

/**
 * Chooses between cached bytes and the live stream. Pins to the downloaded URL when
 * its version still matches so the cache hits even if the server address changed;
 * a superseded tag falls through to streaming the current version.
 */
fun resolveStreamUrl(
    downloaded: DownloadEntry?,
    playbackTag: String,
    resolvedUrl: String,
): String {
    if (downloaded == null || downloaded.state != DownloadState.Completed) return resolvedUrl
    return if (downloaded.tag == playbackTag) downloaded.uri else resolvedUrl
}

/** A completed download is stale once Loom reports a different version for the item. */
fun isStaleDownload(entry: DownloadEntry, itemTag: String): Boolean =
    entry.state == DownloadState.Completed && itemTag.isNotBlank() && entry.tag != itemTag

/** Leaves headroom so a download cannot fill the last of the device's storage. */
fun hasRoomFor(sizeBytes: Long, usableBytes: Long): Boolean =
    sizeBytes > 0 && usableBytes - sizeBytes >= STORAGE_HEADROOM_BYTES

/**
 * Queue a retry only for failures that a later attempt could plausibly fix. A
 * [LoomHttpException] is an IOException too, but a 4xx would just fail forever.
 */
fun isOfflineError(error: Throwable): Boolean =
    error !is LoomHttpException && error is IOException

fun downloadProgressFraction(entry: DownloadEntry): Float {
    if (entry.state == DownloadState.Completed) return 1f
    // Media3 reports an unset content length until the first response arrives.
    if (entry.totalBytes <= 0L || entry.bytesDownloaded <= 0L) return 0f
    return (entry.bytesDownloaded.toFloat() / entry.totalBytes).coerceIn(0f, 1f)
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val gb = bytes / 1_000_000_000.0
    if (gb >= 1.0) return String.format(Locale.US, "%.1f GB", gb)
    return String.format(Locale.US, "%.0f MB", bytes / 1_000_000.0)
}

/** Active downloads first so in-progress work stays visible, then newest additions. */
fun downloadedRowItems(entries: List<DownloadEntry>): List<DownloadEntry> =
    entries.sortedWith(
        compareBy(
            { it.state == DownloadState.Completed },
            { it.item.title },
        ),
    )

private const val STORAGE_HEADROOM_BYTES = 512L * 1024 * 1024
