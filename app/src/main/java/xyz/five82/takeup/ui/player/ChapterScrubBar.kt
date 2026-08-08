package xyz.five82.takeup.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.api.Chapter
import xyz.five82.takeup.ui.theme.Ink

/**
 * The progress thread grown up: a thin woven line with chapter marks as
 * warp ticks and a glowing thumb. Dragging previews the position through
 * [onPreview]; the seek lands on release.
 */
@Composable
fun ChapterScrubBar(
    positionMs: Long,
    durationMs: Long,
    chapters: List<Chapter>,
    accent: Color,
    onPreview: (Long?) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    fun fractionToMs(fraction: Float): Long =
        (fraction.coerceIn(0f, 1f) * durationMs).toLong()

    Canvas(
        modifier
            // Tall enough to grab with a thumb; the thread itself stays thin
            // because everything is drawn around the vertical center.
            .height(44.dp)
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (durationMs > 0) onSeek(fractionToMs(offset.x / size.width))
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (durationMs > 0) {
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            onPreview(fractionToMs(dragFraction!!))
                        }
                    },
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        val current = dragFraction ?: return@detectHorizontalDragGestures
                        dragFraction = (current + delta / size.width).coerceIn(0f, 1f)
                        onPreview(fractionToMs(dragFraction!!))
                    },
                    onDragEnd = {
                        dragFraction?.let { onSeek(fractionToMs(it)) }
                        dragFraction = null
                        onPreview(null)
                    },
                    onDragCancel = {
                        dragFraction = null
                        onPreview(null)
                    },
                )
            },
    ) {
        val centerY = size.height / 2f
        val lineHeight = 3.dp.toPx()
        val radius = CornerRadius(lineHeight / 2f)
        val fraction = dragFraction
            ?: if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
        val fillWidth = size.width * fraction.coerceIn(0f, 1f)

        // Unwoven track.
        drawRoundRect(
            color = accent.copy(alpha = 0.22f),
            topLeft = Offset(0f, centerY - lineHeight / 2f),
            size = Size(size.width, lineHeight),
            cornerRadius = radius,
        )
        // The woven part.
        drawRoundRect(
            color = accent,
            topLeft = Offset(0f, centerY - lineHeight / 2f),
            size = Size(fillWidth, lineHeight),
            cornerRadius = radius,
        )
        // Chapter marks as warp ticks.
        if (durationMs > 0) {
            val tickHeight = 9.dp.toPx()
            for (chapter in chapters) {
                val x = size.width * (chapter.startMs.toFloat() / durationMs).coerceIn(0f, 1f)
                drawLine(
                    color = Ink.copy(alpha = 0.45f),
                    start = Offset(x, centerY - tickHeight / 2f),
                    end = Offset(x, centerY + tickHeight / 2f),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
        }
        // Thumb with a soft glow.
        drawCircle(accent.copy(alpha = 0.25f), radius = 9.dp.toPx(), center = Offset(fillWidth, centerY))
        drawCircle(accent, radius = 5.dp.toPx(), center = Offset(fillWidth, centerY))
    }
}
