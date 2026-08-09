package xyz.five82.takeup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.sqrt
import kotlin.math.tan
import xyz.five82.takeup.ui.theme.Stage

/** One shallow angle everywhere, so the cut reads as the app's signature.
 *  Kept this flat so the climb costs the art's bottom-right corner little. */
private const val CUT_DEGREES = 4.0

/** Art runs this far below the cut's low point, so the cut always trims the
 *  art's raw bottom edge while hiding as little of the photo as possible. */
private val CutOverlap = 16.dp

/** Logos equalize on area, not bounding box: sized so width x height stays
 *  near this many dp^2, a wide wordmark and a stacked lockup carry the same
 *  visual weight. Tuned so a typical 3:1 wordmark lands at 64dp tall. */
private const val LOGO_AREA = 12300f

/**
 * Lane height for a logo of the given width/height aspect, area-normalized
 * and clamped. Null aspect (art not yet decoded) gets the wordmark default.
 */
fun logoLaneHeight(aspect: Float?): Dp {
    if (aspect == null || aspect <= 0f) return 64.dp
    return sqrt(LOGO_AREA / aspect).coerceIn(44f, 100f).dp
}

/**
 * Backdrop art ending on a bias cut: a shallow diagonal rising left to
 * right. Below the cut nothing is painted at all - the screen's own
 * background (gauze, house lights, dyed stage) runs seamlessly up to the
 * photo, and that background's scrim is what keeps logos legible, so the cut
 * needs no ground or wash of its own.
 *
 * The art is always the full width at 4:3, and the component sizes its own
 * height to art plus [solidLeft] minus the overlap - so a tall logo grows
 * the box downward instead of squeezing the photo into a flatter frame.
 * Since 4:3 is a narrower shape than the 16:9 art, Crop fits the photo's
 * full height and trims the sides only - never the top or bottom - and the
 * cut hides just the [CutOverlap] sliver. Callers wrap this in a Box that
 * takes its height rather than imposing one.
 *
 * [solidLeft] is the height of open ground at the left edge, where titles
 * and logos sit. The cut climbs away from there, so the clearance over
 * left-aligned content only grows to the right.
 */
@Composable
fun BiasCutBackdrop(
    imageUrl: String?,
    solidLeft: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    BoxWithConstraints(modifier) {
        val artHeight = maxWidth * 3 / 4
        Box(Modifier.fillMaxWidth().height(artHeight + solidLeft - CutOverlap)) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    // The cut is erased from the art itself rather than
                    // overpainted or clipPath'd: a path fill on an offscreen
                    // layer stays anti-aliased where a hardware clipPath can
                    // jag along the diagonal, and erasing leaves the wedge
                    // truly transparent for the background behind.
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(artHeight)
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            val leftY = size.height - CutOverlap.toPx()
                            val rightY =
                                leftY - size.width * tan(Math.toRadians(CUT_DEGREES)).toFloat()
                            val wedge = Path().apply {
                                moveTo(0f, leftY)
                                lineTo(size.width, rightY)
                                lineTo(size.width, size.height)
                                lineTo(0f, size.height)
                                close()
                            }
                            drawPath(wedge, Color.Black, blendMode = BlendMode.Clear)
                        },
                )
            }
            // Status bar and header icons still need dark ground over
            // bright art.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Stage.copy(alpha = 0.35f),
                            1f to Color.Transparent,
                        ),
                    ),
            )
        }
    }
}
