package xyz.five82.takeup.ui.theme

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
 * known (or when the title has no artwork) the static Takeup scheme shows, so
 * screens never flash a wrong color, just a neutral one.
 */
@Composable
fun WovenTheme(seed: Color?, content: @Composable () -> Unit) {
    if (seed == null) {
        MaterialTheme(colorScheme = TakeupColors, typography = TakeupType, content = content)
        return
    }
    val woven = rememberDynamicColorScheme(
        seedColor = seed,
        isDark = true,
        isAmoled = false,
        style = PaletteStyle.Vibrant,
    )
    // Keep the shared stage: only the accent roles change per title, so
    // pushing a detail screen never repaints the background a new hue.
    val scheme = woven.copy(
        background = Stage,
        onBackground = Ink,
        surface = Stage,
        onSurface = Ink,
    )
    MaterialTheme(colorScheme = scheme, typography = TakeupType, content = content)
}
