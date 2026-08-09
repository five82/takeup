package xyz.five82.takeup.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.ui.theme.Amber
import xyz.five82.takeup.ui.theme.Ember
import xyz.five82.takeup.ui.theme.Ink
import xyz.five82.takeup.ui.theme.Line
import xyz.five82.takeup.ui.theme.Muted
import xyz.five82.takeup.ui.theme.Teal
import xyz.five82.takeup.ui.theme.Violet

/**
 * The woven brand stripe: ember, teal, amber, violet in fixed proportion.
 * Narrow uses (the nav marker) get one full pattern scaled to fit; wide
 * uses tile it.
 */
@Composable
fun Selvedge(modifier: Modifier = Modifier, height: Float = 4f) {
    Canvas(modifier.height(height.dp)) {
        val pattern = listOf(Ember to 0.40f, Teal to 0.29f, Amber to 0.20f, Violet to 0.11f)
        val repeatWidth = minOf(size.width, 140.dp.toPx())
        var x = 0f
        outer@ while (x < size.width) {
            for ((color, share) in pattern) {
                val segment = share * repeatWidth
                drawRect(
                    color,
                    topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                    size = androidx.compose.ui.geometry.Size(minOf(segment, size.width - x), size.height),
                )
                x += segment
                if (x >= size.width) break@outer
            }
        }
    }
}

/**
 * Progress drawn as a thread being woven: a thin line in the given color
 * brightening toward its end, on a faint unwoven track.
 */
@Composable
fun ThreadProgress(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color.copy(alpha = 0.18f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(color, Color(
                            red = color.red + (1f - color.red) * 0.35f,
                            green = color.green + (1f - color.green) * 0.35f,
                            blue = color.blue + (1f - color.blue) * 0.35f,
                        )),
                    ),
                ),
        )
    }
}

/**
 * Bottom clearance for tab-root scrollables: the floating nav pill hovers
 * over content, so the last row needs room to scroll out from under it
 * (system inset + pill height + margins).
 */
@Composable
fun navPillClearance(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 84.dp

/** Caps-and-tracking row label, the proposal's "Continue Watching" voice. */
@Composable
fun RowLabel(text: String, color: Color = Muted, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier,
    )
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Ember, trackColor = Line)
    }
}

/** Full-screen failure: the loom is dark, honestly dark. */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryAction: String? = null,
    onSecondaryAction: () -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Selvedge(Modifier.width(72.dp), height = 3f)
        Text(
            "The loom is dark",
            style = MaterialTheme.typography.displaySmall,
            color = Ink,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRetry) { Text("Try again") }
            if (secondaryAction != null) {
                OutlinedButton(onClick = onSecondaryAction) { Text(secondaryAction) }
            }
        }
    }
}

/** Quiet inline emptiness, e.g. a library with nothing in it yet. */
@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Muted)
    }
}
