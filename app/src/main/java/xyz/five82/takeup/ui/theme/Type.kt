package xyz.five82.takeup.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import xyz.five82.takeup.R

// One variable font, worked hard. Google Sans Flex carries the whole app:
// a refined display cut for titles (and the text fallback when a title has
// no logo art), and the workhorse cut for everything else. The display cut
// pushes the optical-size axis to its display end (144) at a lower weight,
// which Compose never sets on its own; that, not width, is what makes it a
// different voice.

val TakeupSans = FontFamily(
    Font(
        R.font.google_sans_flex,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.google_sans_flex,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.google_sans_flex,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(620)),
    ),
    Font(
        R.font.google_sans_flex,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

// Subtitles are the one place that steps outside Google Sans Flex. Lato's
// plainer, humanist letterforms read better in motion over a moving picture,
// and it is what the iPad app draws cues in - the same voice on both. Static
// faces rather than one variable file, so italic and bold runs in a cue get
// the real cuts instead of a synthesized slant.

val TakeupSubtitle = FontFamily(
    Font(R.font.lato_medium, weight = FontWeight.Medium),
    Font(R.font.lato_medium_italic, weight = FontWeight.Medium, style = FontStyle.Italic),
    Font(R.font.lato_bold, weight = FontWeight.Bold),
    Font(R.font.lato_bold_italic, weight = FontWeight.Bold, style = FontStyle.Italic),
)

val TakeupDisplay = FontFamily(
    Font(
        R.font.google_sans_flex,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(580),
            FontVariation.Setting("opsz", 144f),
        ),
    ),
)

val TakeupType = Typography(
    displayLarge = TextStyle(
        fontFamily = TakeupDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.01).em,
    ),
    displayMedium = TextStyle(
        fontFamily = TakeupDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 33.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = TakeupDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 27.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = TakeupDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 25.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = TakeupSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = TakeupSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = TakeupSans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = TakeupSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = TakeupSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = TakeupSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = TakeupSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    // Row labels: caps and tracking, the proposal's "Continue Watching" voice.
    labelMedium = TextStyle(
        fontFamily = TakeupSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.14.em,
    ),
    labelSmall = TextStyle(
        fontFamily = TakeupSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.04.em,
    ),
)
