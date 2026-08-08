package xyz.five82.takeup.ui

import xyz.five82.takeup.api.Item
import xyz.five82.takeup.api.LoomApi

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
