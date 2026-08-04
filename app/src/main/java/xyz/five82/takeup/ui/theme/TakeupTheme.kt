package xyz.five82.takeup.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme

// Seed for screens without dominant artwork (and for weak/grayscale artwork).
// The red family carries over from Takeup's original hand-tuned palette.
internal val TakeupBrandSeed = Color(0xFF93000A)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TakeupTheme(
    seedColor: Color = TakeupBrandSeed,
    content: @Composable () -> Unit,
) {
    // Animating the seed (one color) instead of the whole scheme keeps screen
    // transitions smooth: the scheme is re-derived each frame from pure HCT
    // math, which is cheap and always internally consistent.
    val animatedSeed by animateColorAsState(
        targetValue = seedColor,
        animationSpec = tween(durationMillis = 500),
        label = "seedColor",
    )
    // Dark-only by design: SPEC_2025 dark surfaces stay near-black with a
    // faint artwork tint, so backgrounds blend with backdrops without glare.
    val colorScheme = rememberDynamicColorScheme(
        seedColor = animatedSeed,
        isDark = true,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
        style = PaletteStyle.TonalSpot,
    )
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = TakeupTypography,
        content = content,
    )
}
