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
 * Stable hue for a TMDB genre id, spun around the wheel with the golden
 * angle so neighboring ids land far apart. The chip row becomes a band of
 * thread without anyone hand-picking twenty colors.
 */
fun genreThread(genreId: Long): Color {
    val hue = ((genreId * 137.508) % 360.0).toFloat()
    return Color.hsv(hue, 0.48f, 0.92f)
}
