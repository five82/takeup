package xyz.five82.takeup.ui

import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.LoomApi
import xyz.five82.takeup.data.LoomRepository

// Image URL shorthands. Widths are pixel hints snapped to Loom's resize
// buckets (240/480/960/1440) by LoomApi.imageUrl.

fun LoomApi.posterUrl(item: Item, widthPx: Int = 480): String? =
    imageUrl(item.posterImageId, item.posterImageTag, widthPx)

fun LoomApi.backdropUrl(item: Item, widthPx: Int = 1440): String? =
    imageUrl(item.backdropImageId, item.backdropImageTag, widthPx)

fun LoomApi.logoUrl(item: Item, widthPx: Int = 480): String? =
    imageUrl(item.logoImageId, item.logoImageTag, widthPx)

fun LoomApi.thumbUrl(item: Item, widthPx: Int = 480): String? =
    imageUrl(item.thumbImageId, item.thumbImageTag, widthPx)

// The same four, resolved for whichever world the screen is in. Offline the only
// art that loads is the copy saved beside the download - Loom's URLs are behind
// the gate - and shows and seasons have their own copies because they were
// captured with the episodes beneath them.

fun LoomRepository.posterFor(item: Item, offline: Boolean, widthPx: Int = 480): String? =
    if (offline) downloads.artwork.posterPath(item.id) else api.posterUrl(item, widthPx)

/** Falls back to the poster offline: a downloaded short may have no backdrop. */
fun LoomRepository.backdropFor(item: Item, offline: Boolean, widthPx: Int = 1440): String? =
    if (offline) {
        downloads.artwork.backdropPath(item.id) ?: downloads.artwork.posterPath(item.id)
    } else {
        api.backdropUrl(item, widthPx)
    }

fun LoomRepository.logoFor(item: Item, offline: Boolean, widthPx: Int = 480): String? =
    if (offline) downloads.artwork.logoPath(item.id) else api.logoUrl(item, widthPx)

fun LoomRepository.thumbFor(item: Item, offline: Boolean, widthPx: Int = 480): String? =
    if (offline) downloads.artwork.thumbPath(item.id) else api.thumbUrl(item, widthPx)
