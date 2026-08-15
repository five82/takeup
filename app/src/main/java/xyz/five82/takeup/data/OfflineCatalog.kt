package xyz.five82.takeup.data

import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.Progress

/**
 * The downloads as a library. Movies and short films stand for themselves,
 * episodes gather under the seasons and shows captured alongside them, and every
 * screen with no Loom reads this instead of the API - the same items, the same
 * hierarchy, the same three questions ([library], [item], [children]).
 *
 * Listings only offer completed downloads, because a half-transferred file
 * cannot play. [item] answers for any download so a detail screen opened
 * mid-transfer still has something to draw.
 *
 * Deliberately free of Android imports so the whole shape stays reachable from
 * plain JVM tests.
 */
class OfflineCatalog(
    entries: List<DownloadEntry> = emptyList(),
    ancestors: List<Item> = emptyList(),
    private val libraryKinds: Map<Long, String> = emptyMap(),
    pending: Map<Long, PendingProgress> = emptyMap(),
) {
    private val ancestorsById = ancestors.associateBy { it.id }

    // Newest download first, so anything derived from this order leads with what
    // landed most recently.
    private val downloaded = entries.sortedByDescending { it.startTimeMs }

    private val itemsById: Map<Long, Item> =
        downloaded.associate { it.item.id to withPendingProgress(it.item, pending[it.item.id]) }

    private val ready: List<Item> = downloaded
        .filter { it.state == DownloadState.Completed }
        .mapNotNull { itemsById[it.item.id] }

    private val episodesByShow: Map<Long, List<Item>> = ready
        .filter { it.kind == "episode" }
        .mapNotNull { episode ->
            showIdOf(episode)?.takeIf(ancestorsById::containsKey)?.let { it to episode }
        }
        .groupBy({ it.first }, { it.second })

    private val shows: List<Item> = episodesByShow.keys.mapNotNull { ancestorsById[it] }

    /**
     * Episodes downloaded before their show was captured. Shown as themselves
     * rather than dropped: the file is on the device either way.
     */
    private val looseEpisodes: List<Item> = run {
        val grouped = episodesByShow.values.flatten().mapTo(mutableSetOf()) { it.id }
        ready.filter { it.kind == "episode" && it.id !in grouped }
    }

    /** What a library tab holds offline, in the same A-Z order Loom serves. */
    fun library(kind: String): List<Item> = when (kind) {
        "tv" -> (shows + looseEpisodes).sortedBy { it.title }
        // An item does not carry its library's kind, so the cached id-to-kind map
        // fills that in. Anything downloaded before the map knew its library lands
        // under Movies rather than vanishing from every tab.
        else -> ready
            .filter { it.kind == "movie" && (libraryKinds[it.libraryId] ?: "movies") == kind }
            .sortedBy { it.title }
    }

    fun item(id: Long): Item? = itemsById[id] ?: ancestorsById[id]

    /** A show's downloaded seasons, or a season's downloaded episodes. */
    fun children(id: Long): List<Item> = when (ancestorsById[id]?.kind) {
        "show" -> seasonsOf(id)
        "season" -> episodesOfSeason(id)
        else -> emptyList()
    }

    /** Every downloaded episode of a show, in running order. */
    fun episodes(showId: Long): List<Item> = episodesByShow[showId]
        .orEmpty()
        .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))

    /** The show an episode belongs to, when it was captured with the download. */
    fun showFor(episodeId: Long): Item? =
        itemsById[episodeId]?.let { showIdOf(it) }?.let(ancestorsById::get)

    /**
     * The downloaded episodes the player can chain through after this one: its
     * show's, or its season's when the show was never captured.
     */
    fun siblingEpisodes(episodeId: Long): List<Item> {
        val episode = itemsById[episodeId] ?: return emptyList()
        val showId = showIdOf(episode)
        return if (showId != null && episodesByShow.containsKey(showId)) {
            episodes(showId)
        } else {
            episodesOfSeason(episode.parentId ?: return emptyList())
        }
    }

    /** Started and unfinished, most recently downloaded first. */
    fun continueWatching(): List<Item> = ready.filter { item ->
        val progress = item.progress ?: return@filter false
        !progress.played && progress.positionMs > 0 && progress.durationMs > 0
    }

    /** The newest downloads, each show standing in for the episodes beneath it. */
    fun recent(): List<Item> = ready
        .map { item ->
            if (item.kind == "episode") showFor(item.id) ?: item else item
        }
        .distinctBy { it.id }

    /** Everything on the device in one A-Z grid, for a tab with no libraries to split. */
    fun all(): List<Item> = recent().sortedBy { it.title }

    /**
     * Offline search. Loom matches on word starts across titles and credited
     * people; with no server there is only what the snapshots carry, so this is a
     * plain substring match over the title and the show a title sits under.
     */
    fun search(query: String): List<Item> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return (shows + ready)
            .distinctBy { it.id }
            .filter { item ->
                item.title.contains(trimmed, ignoreCase = true) ||
                    showFor(item.id)?.title?.contains(trimmed, ignoreCase = true) == true
            }
            .sortedBy { it.title }
    }

    private fun seasonsOf(showId: Long): List<Item> = episodesByShow[showId]
        .orEmpty()
        .mapNotNull { it.parentId?.let(ancestorsById::get) }
        .distinctBy { it.id }
        .sortedBy { it.seasonNumber }

    private fun episodesOfSeason(seasonId: Long): List<Item> = ready
        .filter { it.kind == "episode" && it.parentId == seasonId }
        .sortedBy { it.episodeNumber }

    private fun showIdOf(episode: Item): Long? =
        episode.parentId?.let(ancestorsById::get)?.parentId
}

/**
 * Folds a position queued by offline playback over the snapshot it belongs to.
 * The snapshot froze when the download started, so without this an item watched
 * offline keeps offering the resume point it had before it was watched.
 *
 * The played and resume thresholds mirror Loom's own, so a title finished offline
 * leaves Continue Watching here exactly as it will once the position flushes.
 */
internal fun withPendingProgress(item: Item, queued: PendingProgress?): Item {
    if (queued == null || queued.durationMs <= 0) return item
    val fraction = queued.positionMs.toDouble() / queued.durationMs
    val played = fraction >= PLAYED_FRACTION
    val resumable = !played &&
        queued.durationMs >= MIN_RESUME_DURATION_MS &&
        fraction >= MIN_RESUME_FRACTION
    return item.copy(
        progress = Progress(
            positionMs = queued.positionMs,
            durationMs = queued.durationMs,
            played = played,
            resumePositionMs = if (resumable) queued.positionMs else 0,
        ),
    )
}

private const val PLAYED_FRACTION = 0.90
private const val MIN_RESUME_FRACTION = 0.05
private const val MIN_RESUME_DURATION_MS = 5 * 60 * 1000L
