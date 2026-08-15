package xyz.five82.takeup.ui.theme

import androidx.compose.ui.graphics.Color

// The unlit loom: a deep indigo stage, never pure black, so elevated
// surfaces can sit above it without gray murk.
val Stage = Color(0xFF0B0E14)
val Surface1 = Color(0xFF131826)
val Surface2 = Color(0xFF1A2032)
val Line = Color(0xFF232B3F)
val Ink = Color(0xFFE9EDF6)
val Muted = Color(0xFF8C96AB)
val Faint = Color(0xFF5C667C)

// The app's own threads: one fixed hue per world. Ember doubles as the brand
// color and already lives in the launcher icon background.
val Ember = Color(0xFFFF4D55)
val Teal = Color(0xFF3FD1C4)
val Amber = Color(0xFFFFB84D)
val Violet = Color(0xFFA78BFA)

/** Thread color for a Loom library kind; ember is also the brand fallback. */
fun libraryThread(kind: String?): Color = when (kind) {
    "tv" -> Teal
    "shorts" -> Amber
    else -> Ember
}

/**
 * Hue for a genre's position in the sorted genre list, spun around the wheel
 * with the golden angle so neighboring positions land far apart. Position,
 * not the TMDB id, drives the spin: golden-angle on raw ids clumps (Music id
 * 10402 and Mystery id 9648 land about a degree apart), while spinning by
 * position spreads all nineteen genres evenly.
 */
fun genreHue(position: Int): Float = (position * 137.508f) % 360f

/** Stable accent color for a genre by its position; see [genreHue]. */
fun genreThread(position: Int): Color = Color.hsv(genreHue(position), 0.48f, 0.92f)
