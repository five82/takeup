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

// Fallback seed for screens without dominant artwork (and for weak/grayscale
// artwork). High chroma on purpose: the derived accent must read as a vivid
// red, never the brown that a dark seed produces.
internal val TakeupBrandSeed = Color(0xFFFF4D55)

// The stage: fixed cool near-neutrals for every large surface. Artwork color
// may only enter the UI through the ambient glow and the seed-derived accent
// roles, so no warm backdrop can ever tint the walls brown. Tones sit above
// OLED black to keep the app off the dated pure-black look; hard black exists
// only behind video in the player.
private val StageBackground = Color(0xFF111318)
private val StageSurfaceDim = Color(0xFF0D0F13)
private val StageSurfaceBright = Color(0xFF373B45)
private val StageContainerLowest = Color(0xFF0C0E12)
private val StageContainerLow = Color(0xFF14161C)
private val StageContainer = Color(0xFF171A20)
private val StageContainerHigh = Color(0xFF20242C)
private val StageContainerHighest = Color(0xFF262A33)
private val StageOnSurface = Color(0xFFE9EBF1)
private val StageOnSurfaceVariant = Color(0xFFA6ABB6)
private val StageOutline = Color(0xFF80858F)
private val StageOutlineVariant = Color(0xFF3B404B)
private val StageInverseSurface = Color(0xFFE9EBF1)
private val StageInverseOnSurface = Color(0xFF23262C)

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
    // Vibrant keeps the seed's hue but maximizes accent chroma, then the stage
    // neutrals replace every surface role the derivation would have tinted.
    val colorScheme = rememberDynamicColorScheme(
        seedColor = animatedSeed,
        isDark = true,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
        style = PaletteStyle.Vibrant,
    ).copy(
        background = StageBackground,
        onBackground = StageOnSurface,
        surface = StageBackground,
        onSurface = StageOnSurface,
        surfaceVariant = StageContainerHigh,
        onSurfaceVariant = StageOnSurfaceVariant,
        surfaceDim = StageSurfaceDim,
        surfaceBright = StageSurfaceBright,
        surfaceContainerLowest = StageContainerLowest,
        surfaceContainerLow = StageContainerLow,
        surfaceContainer = StageContainer,
        surfaceContainerHigh = StageContainerHigh,
        surfaceContainerHighest = StageContainerHighest,
        outline = StageOutline,
        outlineVariant = StageOutlineVariant,
        inverseSurface = StageInverseSurface,
        inverseOnSurface = StageInverseOnSurface,
    )
    // Motion is left at the Expressive default (springy spatial scheme).
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        shapes = TakeupShapes,
        typography = TakeupTypography,
        content = content,
    )
}
