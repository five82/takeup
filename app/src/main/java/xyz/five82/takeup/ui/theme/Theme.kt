package xyz.five82.takeup.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme

// Takeup is dark-only by design: Loom's logo artwork is light-on-dark and a
// player lives in dim rooms. There is no light scheme and that is a feature.
val TakeupColors = darkColorScheme(
    primary = Ember,
    onPrimary = Color(0xFF33060A),
    primaryContainer = Color(0xFF3C1216),
    onPrimaryContainer = Color(0xFFFFD9DA),
    secondary = Teal,
    onSecondary = Color(0xFF00201D),
    secondaryContainer = Color(0xFF0E3733),
    onSecondaryContainer = Color(0xFFBFF2EC),
    tertiary = Amber,
    onTertiary = Color(0xFF2A1B00),
    tertiaryContainer = Color(0xFF3E2E0A),
    onTertiaryContainer = Color(0xFFFFE3B3),
    background = Stage,
    onBackground = Ink,
    surface = Stage,
    onSurface = Ink,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Stage,
    surfaceContainerLow = Color(0xFF0F1320),
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Color(0xFF212840),
    outline = Faint,
    outlineVariant = Line,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    scrim = Color(0xFF000000),
)

@Composable
fun TakeupTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TakeupColors,
        typography = TakeupType,
        content = content,
    )
}

/**
 * Wraps a screen in a scheme woven from a title's artwork. Until the seed is
 * known (or when the title has no artwork) a gray scheme woven from Muted
 * shows instead - falling back to the brand scheme here made every push
 * flash Ember-red buttons for the beat before the art decoded. When the
 * seed lands the accents crossfade from gray into the title's color.
 */
@Composable
fun WovenTheme(seed: Color?, content: @Composable () -> Unit) {
    val woven = rememberDynamicColorScheme(
        seedColor = seed ?: Muted,
        isDark = true,
        isAmoled = false,
        // Content follows the seed's own chroma, so muted art (a brown
        // warehouse still) yields muted accents while vivid art stays vivid.
        // Vibrant forced max chroma from the hue alone, which turned
        // desaturated backdrops into loud acid accents - and would turn the
        // gray placeholder into a loud blue; Neutral keeps it actually gray.
        style = if (seed == null) PaletteStyle.Neutral else PaletteStyle.Content,
    )
    // Keep the shared stage: only the accent roles change per title, so
    // pushing a detail screen never repaints the background a new hue.
    val scheme = woven.copy(
        background = Stage,
        onBackground = Ink,
        surface = Stage,
        onSurface = Ink,
    )
    MaterialTheme(colorScheme = scheme.bloomed(), typography = TakeupType, content = content)
}

/**
 * Crossfades the accent-bearing roles toward their targets so the gray to
 * woven swap blooms instead of popping. Cached seeds start at their target,
 * so revisits do not animate. Stage-pinned, error, and scrim roles never
 * change between schemes and stay as-is.
 */
@Composable
private fun ColorScheme.bloomed(): ColorScheme {
    @Composable
    fun Color.fade(): Color = animateColorAsState(this, tween(450), label = "woven role").value
    return copy(
        primary = primary.fade(),
        onPrimary = onPrimary.fade(),
        primaryContainer = primaryContainer.fade(),
        onPrimaryContainer = onPrimaryContainer.fade(),
        inversePrimary = inversePrimary.fade(),
        secondary = secondary.fade(),
        onSecondary = onSecondary.fade(),
        secondaryContainer = secondaryContainer.fade(),
        onSecondaryContainer = onSecondaryContainer.fade(),
        tertiary = tertiary.fade(),
        onTertiary = onTertiary.fade(),
        tertiaryContainer = tertiaryContainer.fade(),
        onTertiaryContainer = onTertiaryContainer.fade(),
        surfaceVariant = surfaceVariant.fade(),
        onSurfaceVariant = onSurfaceVariant.fade(),
        surfaceTint = surfaceTint.fade(),
        inverseSurface = inverseSurface.fade(),
        inverseOnSurface = inverseOnSurface.fade(),
        outline = outline.fade(),
        outlineVariant = outlineVariant.fade(),
        surfaceBright = surfaceBright.fade(),
        surfaceDim = surfaceDim.fade(),
        surfaceContainer = surfaceContainer.fade(),
        surfaceContainerLow = surfaceContainerLow.fade(),
        surfaceContainerLowest = surfaceContainerLowest.fade(),
        surfaceContainerHigh = surfaceContainerHigh.fade(),
        surfaceContainerHighest = surfaceContainerHighest.fade(),
    )
}
