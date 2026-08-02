package xyz.five82.takeup.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val TakeupTypography = BaseTypography.copy(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = DisplayFontFamily),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = DisplayFontFamily),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = DisplayFontFamily),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = HeadlineFontFamily),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = HeadlineFontFamily),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = HeadlineFontFamily),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = HeadlineFontFamily),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = TextFontFamily),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = TextFontFamily),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = TextFontFamily),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = TextFontFamily),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = TextFontFamily),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = TextFontFamily),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = TextFontFamily),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = TextFontFamily),
    displayLargeEmphasized = BaseTypography.displayLargeEmphasized.copy(
        fontFamily = DisplayFontFamily,
    ),
    displayMediumEmphasized = BaseTypography.displayMediumEmphasized.copy(
        fontFamily = DisplayFontFamily,
    ),
    displaySmallEmphasized = BaseTypography.displaySmallEmphasized.copy(
        fontFamily = DisplayFontFamily,
    ),
    headlineLargeEmphasized = BaseTypography.headlineLargeEmphasized.copy(
        fontFamily = HeadlineFontFamily,
    ),
    headlineMediumEmphasized = BaseTypography.headlineMediumEmphasized.copy(
        fontFamily = HeadlineFontFamily,
    ),
    headlineSmallEmphasized = BaseTypography.headlineSmallEmphasized.copy(
        fontFamily = HeadlineFontFamily,
    ),
    titleLargeEmphasized = BaseTypography.titleLargeEmphasized.copy(
        fontFamily = HeadlineFontFamily,
    ),
    titleMediumEmphasized = BaseTypography.titleMediumEmphasized.copy(
        fontFamily = TextFontFamily,
    ),
    titleSmallEmphasized = BaseTypography.titleSmallEmphasized.copy(
        fontFamily = TextFontFamily,
    ),
    bodyLargeEmphasized = BaseTypography.bodyLargeEmphasized.copy(fontFamily = TextFontFamily),
    bodyMediumEmphasized = BaseTypography.bodyMediumEmphasized.copy(fontFamily = TextFontFamily),
    bodySmallEmphasized = BaseTypography.bodySmallEmphasized.copy(fontFamily = TextFontFamily),
    labelLargeEmphasized = BaseTypography.labelLargeEmphasized.copy(fontFamily = TextFontFamily),
    labelMediumEmphasized = BaseTypography.labelMediumEmphasized.copy(fontFamily = TextFontFamily),
    labelSmallEmphasized = BaseTypography.labelSmallEmphasized.copy(fontFamily = TextFontFamily),
)
