package xyz.five82.takeup.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TakeupColors = darkColorScheme(
    primary = Color(0xFFE45C5C),
    onPrimary = Color(0xFF2C0001),
    background = Color(0xFF101014),
    onBackground = Color(0xFFE5E1E6),
    surface = Color(0xFF101014),
    onSurface = Color(0xFFE5E1E6),
    surfaceVariant = Color(0xFF28262B),
    onSurfaceVariant = Color(0xFFCAC4CC),
)

@Composable
fun TakeupTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TakeupColors,
        content = content,
    )
}
