package xyz.five82.takeup.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import xyz.five82.takeup.R

private val FlexWeights = listOf(
    FontWeight.Thin,
    FontWeight.ExtraLight,
    FontWeight.Light,
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
    FontWeight.ExtraBold,
    FontWeight.Black,
)

private fun googleSansFlexFamily(
    opticalSize: Float,
    roundness: Float,
): FontFamily = FontFamily(
    FlexWeights.map { weight ->
        Font(
            resId = R.font.google_sans_flex,
            weight = weight,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight.weight),
                FontVariation.Setting("opsz", opticalSize),
                FontVariation.Setting("ROND", roundness),
            ),
        )
    },
)

private val DisplayFontFamily = googleSansFlexFamily(opticalSize = 48f, roundness = 35f)
private val HeadlineFontFamily = googleSansFlexFamily(opticalSize = 32f, roundness = 20f)
private val TextFontFamily = googleSansFlexFamily(opticalSize = 16f, roundness = 5f)

private val BaseTypography = Typography()

// The weight ladder is the hierarchy: heroes and screen titles land heavy,
// section titles stay clearly bold, and running text keeps stock weights so
// the jump reads intentional. The variable font has all of 100-900 registered,
// so each override just picks a different wght instance.
private fun TextStyle.display() = copy(
    fontFamily = DisplayFontFamily,
    fontWeight = FontWeight.ExtraBold,
)

private fun TextStyle.headline() = copy(
    fontFamily = HeadlineFontFamily,
    fontWeight = FontWeight.Bold,
)

private fun TextStyle.text(weight: FontWeight? = null) = copy(
    fontFamily = TextFontFamily,
    fontWeight = weight ?: fontWeight,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val TakeupTypography = BaseTypography.copy(
    displayLarge = BaseTypography.displayLarge.display(),
    displayMedium = BaseTypography.displayMedium.display(),
    displaySmall = BaseTypography.displaySmall.display(),
    headlineLarge = BaseTypography.headlineLarge.headline(),
    headlineMedium = BaseTypography.headlineMedium.headline(),
    headlineSmall = BaseTypography.headlineSmall.headline(),
    titleLarge = BaseTypography.titleLarge.headline(),
    titleMedium = BaseTypography.titleMedium.text(FontWeight.SemiBold),
    titleSmall = BaseTypography.titleSmall.text(FontWeight.SemiBold),
    bodyLarge = BaseTypography.bodyLarge.text(),
    bodyMedium = BaseTypography.bodyMedium.text(),
    bodySmall = BaseTypography.bodySmall.text(),
    labelLarge = BaseTypography.labelLarge.text(FontWeight.SemiBold),
    labelMedium = BaseTypography.labelMedium.text(),
    labelSmall = BaseTypography.labelSmall.text(),
    displayLargeEmphasized = BaseTypography.displayLargeEmphasized.display(),
    displayMediumEmphasized = BaseTypography.displayMediumEmphasized.display(),
    displaySmallEmphasized = BaseTypography.displaySmallEmphasized.display(),
    headlineLargeEmphasized = BaseTypography.headlineLargeEmphasized.headline(),
    headlineMediumEmphasized = BaseTypography.headlineMediumEmphasized.headline(),
    headlineSmallEmphasized = BaseTypography.headlineSmallEmphasized.headline(),
    titleLargeEmphasized = BaseTypography.titleLargeEmphasized.headline(),
    titleMediumEmphasized = BaseTypography.titleMediumEmphasized.text(FontWeight.Bold),
    titleSmallEmphasized = BaseTypography.titleSmallEmphasized.text(FontWeight.Bold),
    bodyLargeEmphasized = BaseTypography.bodyLargeEmphasized.text(),
    bodyMediumEmphasized = BaseTypography.bodyMediumEmphasized.text(),
    bodySmallEmphasized = BaseTypography.bodySmallEmphasized.text(),
    labelLargeEmphasized = BaseTypography.labelLargeEmphasized.text(FontWeight.Bold),
    labelMediumEmphasized = BaseTypography.labelMediumEmphasized.text(FontWeight.SemiBold),
    labelSmallEmphasized = BaseTypography.labelSmallEmphasized.text(FontWeight.SemiBold),
)
