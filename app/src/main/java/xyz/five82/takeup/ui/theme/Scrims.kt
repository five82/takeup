package xyz.five82.takeup.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// The single scrim language for artwork legibility. Every gradient over
// artwork must come from here so heroes, headers, and the player read as one
// system instead of ad-hoc blacks.

/**
 * Bottom-anchored hero scrim: darkens the text baseline and dissolves the
 * artwork into [target] (the screen surface) with no hard edge.
 */
internal fun heroBottomScrim(target: Color): Brush = Brush.verticalGradient(
    0f to Color.Transparent,
    0.45f to Color.Transparent,
    0.8f to target.copy(alpha = 0.85f),
    1f to target,
)

/** Top-anchored scrim keeping status-bar icons and header controls legible. */
internal fun topScrim(): Brush = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.6f),
    0.3f to Color.Transparent,
)

/** Player-only bottom scrim; playback stays in a true-black context. */
internal fun playerBottomScrim(): Brush = Brush.verticalGradient(
    0f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.85f),
)

/** Container for translucent controls and badges floating over artwork. */
internal val OverlayPillColor = Color.Black.copy(alpha = 0.45f)
