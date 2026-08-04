package xyz.five82.takeup.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// The single scrim language for artwork legibility. Every gradient over
// artwork must come from here, and every shade derives from the stage
// neutrals so overlays read cool and cohesive. Hard black is reserved for the
// video surface in the player.

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
@Composable
internal fun topScrim(): Brush = Brush.verticalGradient(
    0f to MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.65f),
    0.3f to Color.Transparent,
)

// Deep shade for the player's control scrims: darker than the stage so
// controls hold up over bright video, but still the stage's cool hue.
private val PlayerShade = Color(0xFF07090D)

/** Player-only bottom scrim behind the transport controls. */
internal fun playerBottomScrim(): Brush = Brush.verticalGradient(
    0f to Color.Transparent,
    1f to PlayerShade.copy(alpha = 0.85f),
)

/** Container for translucent controls and badges floating over artwork. */
@Composable
internal fun overlayPillColor(): Color =
    MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.55f)
