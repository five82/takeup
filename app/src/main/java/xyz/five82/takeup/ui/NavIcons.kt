package xyz.five82.takeup.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// The icons-core artifact ships only a small basic set (Home, Search, ...);
// the media glyphs this app needs live in icons-extended, which is far too
// large a dependency for a handful of icons. Their Material path data is
// inlined here instead. Fill color is irrelevant: Icon() tints on top of it.

private fun navIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(pathData),
        fill = SolidColor(Color.White),
    ).build()

/** Material "Movie": a clapperboard. */
val MovieIcon = navIcon(
    "Movie",
    "M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 " +
        "2 2h16c1.1 0 2-.9 2-2V4h-4z",
)

/** Material "Tv". */
val TvIcon = navIcon(
    "Tv",
    "M21 3H3c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h5v2h8v-2h5c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z" +
        "m0 14H3V5h18v12z",
)

/** Material "Theaters": a filmstrip. */
val TheatersIcon = navIcon(
    "Theaters",
    "M18 3v2h-2V3H8v2H6V3H4v18h2v-2h2v2h8v-2h2v2h2V3h-2zM8 17H6v-2h2v2zm0-4H6v-2h2v2z" +
        "m0-4H6V7h2v2zm10 8h-2v-2h2v2zm0-4h-2v-2h2v2zm0-4h-2V7h2v2z",
)

/** Material "Download": an arrow onto a shelf. */
val DownloadIcon = navIcon(
    "Download",
    "M12,16 7,11l1.41,-1.41L11,12.17V4h2v8.17l2.59,-2.58L17,11zM5,20v-2h14v2z",
)

/** Material "Explore": a compass. */
val ExploreIcon = navIcon(
    "Explore",
    "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z" +
        "m2.19 12.19L6 18l3.81-8.19L18 6l-3.81 8.19zM12 10.9c-.61 0-1.1.49-1.1 1.1" +
        "s.49 1.1 1.1 1.1c.61 0 1.1-.49 1.1-1.1s-.49-1.1-1.1-1.1z",
)
