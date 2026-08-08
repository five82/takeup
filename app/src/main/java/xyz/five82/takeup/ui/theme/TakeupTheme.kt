package xyz.five82.takeup.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec

// The three fixed accents. Artwork no longer feeds the theme at all: movie
// grading is overwhelmingly warm, and deriving accents from it kept dragging
// the whole app orange. These seeds are cool by construction and permanent.
//   Iris   - the signature: every CTA, progress bar, and selection.
//   Mint   - state only (downloaded, watched), never actions.
//   Violet - genre chips, keeping browse distinct from actions.
private val IrisSeed = Color(0xFF6B7CFF)
private val MintSeed = Color(0xFF7EDCC3)
private val VioletSeed = Color(0xFFC6ADFF)

// The stage: fixed cool near-neutrals for every large surface. Tones sit above
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

// Derived once at load: the palette is static, so there is nothing to remember
// or animate per frame. Vibrant keeps the accent roles at full chroma; the
// stage neutrals then replace every surface role the derivation tinted.
private val TakeupColorScheme: ColorScheme by lazy {
    dynamicColorScheme(
        seedColor = IrisSeed,
        isDark = true,
        isAmoled = false,
        secondary = MintSeed,
        tertiary = VioletSeed,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
        style = PaletteStyle.Vibrant,
    ).copy(
        background = StageBackground,
        onBackground = StageOnSurface,
        surface = StageBackground,
        onSurface = StageOnSurface,
        // Tonal components (FilledTonalButton and friends) draw from
        // secondaryContainer, and letting Mint tint them would spread the
        // state color across every secondary action. Neutral containers keep
        // Mint exclusively on things that are done: badges use `secondary`.
        secondaryContainer = StageContainerHigh,
        onSecondaryContainer = StageOnSurface,
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
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TakeupTheme(content: @Composable () -> Unit) {
    // Motion is left at the Expressive default (springy spatial scheme).
    MaterialExpressiveTheme(
        colorScheme = TakeupColorScheme,
        shapes = TakeupShapes,
        typography = TakeupTypography,
        content = content,
    )
}
