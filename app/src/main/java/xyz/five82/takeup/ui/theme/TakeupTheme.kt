package xyz.five82.takeup.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkTakeupColors = darkColorScheme(
    primary = Color(0xFFE45C5C),
    onPrimary = Color(0xFF2C0001),
    background = Color(0xFF101014),
    onBackground = Color(0xFFE5E1E6),
    surface = Color(0xFF101014),
    onSurface = Color(0xFFE5E1E6),
    surfaceVariant = Color(0xFF28262B),
    onSurfaceVariant = Color(0xFFCAC4CC),
)

private val LightTakeupColors = lightColorScheme(
    primary = Color(0xFFB3261E),
    onPrimary = Color.White,
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
)

@Composable
fun TakeupTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkTakeupColors else LightTakeupColors,
        content = content,
    )
}
