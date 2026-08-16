package xyz.five82.takeup.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import xyz.five82.takeup.ui.theme.Stage
import xyz.five82.takeup.ui.theme.fieldTone

// The background system: every screen takes its light from what's on it,
// while staying under the luminance ceiling so white logo art always lands.
// All treatments draw over the Stage the scaffold already paints; none of
// them replaces the signature bias cut or the woven accents.

/**
 * Dye Bath: the Stage soaked with a trace of the title's seed, plus one soft
 * glow from above. The quietest treatment - flipping between two titles
 * feels like changing rooms without anyone noticing why.
 */
fun Modifier.dyeBath(seed: Color?): Modifier {
    if (seed == null) return this
    return drawWithCache {
        val base = lerp(Stage, seed.fieldTone(), 0.16f)
        val glow = Brush.radialGradient(
            0f to seed.fieldTone().copy(alpha = 0.20f),
            1f to Color.Transparent,
            center = Offset(size.width / 2f, -size.width * 0.2f),
            radius = size.width * 1.3f,
        )
        onDrawBehind {
            drawRect(base)
            drawRect(glow)
        }
    }
}

/**
 * House Lights: a thread-colored field pouring from the top-left corner,
 * like house lights coming up in one wing of the theater. Screens without a
 * focal title get their orientation color from this alone.
 */
fun Modifier.houseLights(thread: Color): Modifier = drawWithCache {
    // A circle flattened to an ellipse: wide enough to light the header,
    // shallow enough to leave the lower screen on plain Stage.
    val radius = size.width * 1.15f
    val brush = Brush.radialGradient(
        0f to thread.copy(alpha = 0.20f),
        1f to Color.Transparent,
        center = Offset.Zero,
        radius = radius,
    )
    onDrawBehind {
        withTransform({ scale(1f, 0.55f, pivot = Offset.Zero) }) {
            drawRect(brush, size = Size(radius, radius))
        }
    }
}

/**
 * Three Threads: up to three extracted (or brand) colors as large soft
 * fields of light. [drifting] moves them almost imperceptibly - stage
 * lighting warming up - and holds still when animations are disabled.
 */
@Composable
fun Modifier.threeThreads(colors: List<Color>, drifting: Boolean = false): Modifier {
    if (colors.isEmpty()) return this
    val palette = colors.map { it.fieldTone() }
    val t1: State<Float>
    val t2: State<Float>
    if (drifting) {
        val transition = rememberInfiniteTransition(label = "threads")
        t1 = transition.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(21_000, easing = LinearEasing), RepeatMode.Reverse),
            label = "drift1",
        )
        t2 = transition.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(27_000, easing = LinearEasing), RepeatMode.Reverse),
            label = "drift2",
        )
    } else {
        t1 = remember { mutableFloatStateOf(0.5f) }
        t2 = remember { mutableFloatStateOf(0.5f) }
    }
    return drawWithCache {
        val w = size.width
        val h = size.height
        // Anchors chosen so the fields frame content rather than sit under it.
        val sway1 = (t1.value - 0.5f) * 0.12f * w
        val sway2 = (t2.value - 0.5f) * 0.12f * w
        val blobs = listOf(
            Triple(Offset(0.12f * w + sway1, 0.18f * h + sway2 * 0.5f), 0.70f * w, 0.30f),
            Triple(Offset(0.92f * w - sway2, 0.42f * h + sway1 * 0.5f), 0.80f * w, 0.24f),
            Triple(Offset(0.35f * w + sway2, 0.98f * h - sway1 * 0.5f), 0.85f * w, 0.26f),
        )
        onDrawBehind {
            blobs.forEachIndexed { index, (center, radius, alpha) ->
                val color = palette[index % palette.size]
                drawCircle(
                    Brush.radialGradient(
                        0f to color.copy(alpha = alpha),
                        1f to Color.Transparent,
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }
        }
    }
}

/**
 * Shadow Weave: the grid's lead item casts a soft two-color echo into the
 * top of the screen, cross-fading when the lead changes so browsing washes
 * rather than strobes. [fallback] lights the echo until art decodes.
 */
@Composable
fun Modifier.shadowWeave(swatches: List<Color>, fallback: Color): Modifier {
    val first by animateColorAsState(
        (swatches.getOrNull(0) ?: fallback).fieldTone(),
        tween(400),
        label = "weave1",
    )
    val second by animateColorAsState(
        (swatches.getOrNull(1) ?: swatches.getOrNull(0) ?: fallback).fieldTone(),
        tween(400),
        label = "weave2",
    )
    return drawWithCache {
        val w = size.width
        val h = size.height
        val one = Brush.radialGradient(
            0f to first.copy(alpha = 0.26f),
            1f to Color.Transparent,
            center = Offset(0.30f * w, 0.02f * h),
            radius = 0.75f * w,
        )
        val two = Brush.radialGradient(
            0f to second.copy(alpha = 0.20f),
            1f to Color.Transparent,
            center = Offset(0.80f * w, 0.10f * h),
            radius = 0.60f * w,
        )
        onDrawBehind {
            drawRect(one)
            drawRect(two)
        }
    }
}

/**
 * Gauze: the title's backdrop blurred past recognition - no faces, no
 * composition, just the film's color weather - darkened toward the ceiling
 * and hung behind the whole screen. The image is the small resize bucket
 * (a blur has no detail to lose), so this costs one tiny decode. [scrimAlphaScale]
 * is 1 by default to preserve the Detail treatment; Home uses a slightly
 * lighter scrim so it keeps more of the backdrop's color.
 */
@Composable
fun GauzeBackground(
    imageUrl: String?,
    seed: Color?,
    modifier: Modifier = Modifier,
    scrimAlphaScale: Float = 1f,
) {
    // The dye bath catches whatever the gauze doesn't cover: missing
    // backdrops, and the moment before the image decodes.
    Box(modifier.fillMaxSize().dyeBath(seed)) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // Blur washes saturation out; the boost keeps the weather
                // colorful so the scrim can do the darkening alone.
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.4f) }),
                modifier = Modifier.fillMaxSize().blur(64.dp),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val scrim = Brush.verticalGradient(
                            0f to Stage.copy(alpha = 0.55f * scrimAlphaScale),
                            0.55f to Stage.copy(alpha = 0.68f * scrimAlphaScale),
                            1f to Stage.copy(alpha = 0.88f * scrimAlphaScale),
                        )
                        onDrawBehind { drawRect(scrim) }
                    },
            )
        }
    }
}
