package xyz.five82.takeup.ui.theme

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

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
 * One curated tile color per TMDB movie genre id: a cool, saturated jewel
 * palette (crimson through magenta, violet, indigo, azure, cyan, teal,
 * forest) picked and approved by hand - no browns, tans, golds, or olives.
 * This table is the single source of truth for genre color; nothing else in
 * the app should hard-code a genre hue.
 */
private val genreFields: Map<Long, Color> = mapOf(
    28L to Color(0xFF9E2B3A), // Action - garnet
    12L to Color(0xFF2268B8), // Adventure - azure
    16L to Color(0xFF6C33C4), // Animation - violet
    35L to Color(0xFFA62887), // Comedy - magenta
    80L to Color(0xFF3D51C9), // Crime - indigo
    99L to Color(0xFF2E7D46), // Documentary - forest
    18L to Color(0xFF7A2140), // Drama - deep wine
    10751L to Color(0xFF0F8A6D), // Family - teal
    14L to Color(0xFF8146E0), // Fantasy - bright violet
    36L to Color(0xFF1D5A9E), // History - deep azure
    27L to Color(0xFF24523B), // Horror - dark forest
    10402L to Color(0xFF0E7E96), // Music - cyan
    9648L to Color(0xFF2C3B9E), // Mystery - deep indigo
    10749L to Color(0xFFB13D6F), // Romance - rose
    878L to Color(0xFF1899B4), // SciFi - bright cyan
    53L to Color(0xFF39456E), // Thriller - slate indigo
    10770L to Color(0xFF6D5591), // TVMovie - dusty violet
    10752L to Color(0xFF2F5D84), // War - steel azure
    37L to Color(0xFF216D54), // Western - spruce
)

// Genre ids outside TMDB's fixed movie set (there should not be any) fall
// back to the pre-palette golden-angle-on-id spin rather than a hard failure.
private fun genreFallbackHue(genreId: Long): Float = (genreId * 137.508f) % 360f

/** A genre's literal tile field color; see [genreFields]. */
fun genreField(genreId: Long): Color =
    genreFields[genreId] ?: Color.hsv(genreFallbackHue(genreId), 0.48f, 0.32f)

/** The tile color with its HSV value scaled down - the gradient field's dark end. */
fun genreDarken(genreId: Long, valueScale: Float): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(genreField(genreId).toArgb(), hsv)
    return Color.hsv(hsv[0], hsv[1], hsv[2] * valueScale)
}

/** The tile color lifted to an absolute HSV value with saturation scaled - the bright accent thread. */
private fun genreLift(genreId: Long, satScale: Float, value: Float): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(genreField(genreId).toArgb(), hsv)
    return Color.hsv(hsv[0], (hsv[1] * satScale).coerceAtMost(1f), value)
}

/** Bright accent color for a genre, e.g. the item-grid screen's theme color. */
fun genreThread(genreId: Long): Color =
    if (genreId in genreFields) {
        genreLift(genreId, satScale = 0.8f, value = 0.92f)
    } else {
        Color.hsv(genreFallbackHue(genreId), 0.48f, 0.92f)
    }
