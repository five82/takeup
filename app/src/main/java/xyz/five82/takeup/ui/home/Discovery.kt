package xyz.five82.takeup.ui.home

import kotlin.random.Random
import xyz.five82.takeup.api.Collection
import xyz.five82.takeup.api.Item

/** One rotating discovery shelf on the home screen. */
data class DiscoveryRow(val key: String, val title: String, val items: List<Item>)

private const val ROW_COUNT = 3
private const val ROW_ITEMS = 12
// A shelf with a couple of stragglers reads as a mistake, so thin rows are
// skipped and another candidate takes the slot.
private const val MIN_ROW_ITEMS = 4
private const val QUICK_WATCH_MAX_MS = 90 * 60 * 1000L
private const val HIGH_RATING = 7.5

/**
 * One unstarted movie or show for the home hero. The date is the only source
 * of randomness, so the title stays put while its "Today's/Tonight's" label
 * changes with the time of day.
 */
fun dailyPick(movies: List<Item>, shows: List<Item>, epochDay: Long): Item? {
    val unstarted = (movies + shows).filter { !it.isStarted }.sortedBy { it.id }
    val candidates = unstarted.filter { it.voteAverage >= HIGH_RATING && it.backdropImageId > 0 }
        .ifEmpty { unstarted.filter { it.voteAverage >= HIGH_RATING } }
        .ifEmpty { unstarted.filter { it.backdropImageId > 0 } }
        .ifEmpty { unstarted }
    return candidates.shuffled(Random(epochDay)).firstOrNull()
}

fun dailyPickLabel(hour: Int): String = if (hour >= 18) "Tonight's Pick" else "Today's Pick"

/**
 * The day's discovery shelves, drawn from a pool of candidates seeded by
 * [epochDay]: stable all day, different tomorrow. Candidates that cannot fill
 * a shelf drop out and the next takes the slot.
 */
fun discoveryRows(
    movies: List<Item>,
    shows: List<Item>,
    collections: List<Collection>,
    recentlyPlayed: List<Item>,
    epochDay: Long,
): List<DiscoveryRow> {
    val random = Random(epochDay)
    val library = movies + shows
    val unstarted = library.filter { !it.isStarted }
    val builders: List<() -> DiscoveryRow?> = listOf(
        { genreSpotlight(unstarted, random) },
        { row("unstarted", "Haven't Started", unstarted.shuffled(random)) },
        { row("rated", "Highly Rated", unstarted.filter { it.voteAverage >= HIGH_RATING }.shuffled(random)) },
        { collectionSpotlight(collections, random) },
        { row("different", "Something Different", library.shuffled(random)) },
        // The server orders these by finish time; keep that order.
        { row("again", "Watch It Again", recentlyPlayed) },
        {
            row(
                "quick",
                "A Quick Watch",
                movies.filter { !it.isStarted && it.durationMs in 1 until QUICK_WATCH_MAX_MS }
                    .shuffled(random),
            )
        },
    )
    return builders.shuffled(random).mapNotNull { it() }.take(ROW_COUNT)
}

// A show counts as started once any episode is watched; a movie once it has
// any playback state at all.
private val Item.isStarted: Boolean
    get() = if (kind == "show") episodeCount > 0 && unwatchedCount < episodeCount else progress != null

private fun row(key: String, title: String, items: List<Item>): DiscoveryRow? =
    if (items.size < MIN_ROW_ITEMS) null else DiscoveryRow(key, title, items.take(ROW_ITEMS))

private fun genreSpotlight(unstarted: List<Item>, random: Random): DiscoveryRow? {
    val byGenre = mutableMapOf<String, MutableList<Item>>()
    for (item in unstarted) {
        for (genre in item.genres.orEmpty()) {
            byGenre.getOrPut(genre.name) { mutableListOf() }.add(item)
        }
    }
    // Sorted so the seeded pick does not depend on map iteration order.
    val candidates = byGenre.entries.filter { it.value.size >= MIN_ROW_ITEMS }.sortedBy { it.key }
    if (candidates.isEmpty()) return null
    val pick = candidates[random.nextInt(candidates.size)]
    return row("genre", "Tonight: ${pick.key}", pick.value.shuffled(random))
}

// Loom only serves collections with at least two owned members, and a
// two-movie franchise is still a real shelf, so this skips the usual floor.
private fun collectionSpotlight(collections: List<Collection>, random: Random): DiscoveryRow? {
    if (collections.isEmpty()) return null
    val pick = collections[random.nextInt(collections.size)]
    return DiscoveryRow("col-${pick.slug}", pick.title, pick.items.take(ROW_ITEMS))
}
