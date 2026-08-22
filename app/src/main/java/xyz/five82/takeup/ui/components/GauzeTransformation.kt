package xyz.five82.takeup.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import coil3.size.Size
import coil3.size.pxOrElse
import coil3.transform.Transformation
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Bakes the gauze treatment - the saturation boost and the heavy blur - into
 * the small source bitmap at decode time. As live layer effects the same
 * treatment re-ran a full-screen RenderEffect blur on the GPU every redrawn
 * frame (every frame of a scroll) for output that never changes; baked before
 * upscale it looks the same, because the upscale interpolation adds no detail
 * a screen-space blur would remove. Mirrors the iOS app's GauzeStore. Coil
 * caches the transformed bitmap, so each artwork pays the bake once.
 *
 * [screenBlurPx] is the on-screen radius the bake must match: the 64dp the
 * old live blur used, converted at the caller's density. A data class so
 * recomposition builds equal ImageRequests instead of restarting the load.
 */
data class GauzeTransformation(private val screenBlurPx: Float) : Transformation() {

    override val cacheKey = "gauze:${screenBlurPx.roundToInt()}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        // The live blur ran after the bitmap was crop-scaled to the screen,
        // so the equivalent source-space radius is the screen radius divided
        // by that upscale factor.
        val scale = max(
            size.width.pxOrElse { input.width }.toFloat() / input.width,
            size.height.pxOrElse { input.height }.toFloat() / input.height,
        )
        val radius = (screenBlurPx / max(scale, 1f)).roundToInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            input, 0f, 0f,
            Paint().apply {
                colorFilter = ColorMatrixColorFilter(
                    // The boost the live treatment applied as a ColorFilter:
                    // blur washes saturation out, so the weather stays
                    // colorful and the scrim can do the darkening alone.
                    ColorMatrix().apply { setSaturation(1.4f) },
                )
            },
        )
        val pixels = IntArray(out.width * out.height)
        out.getPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
        boxBlur(pixels, out.width, out.height, radius)
        out.setPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
        return out
    }
}

/**
 * Three-pass separable box blur, in place: three box passes of radius [radius]
 * approximate a Gaussian of sigma ~= radius (per pass sigma^2 is
 * ((2r+1)^2 - 1) / 12, so three sum to r^2 + r). At the gauze's 240-bucket
 * sizes the whole thing is well under a millisecond, with no dependency on
 * the deprecated RenderScript or a hardware canvas. Edges clamp, so the
 * border blurs toward the edge pixels instead of darkening toward
 * transparent.
 */
internal fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int) {
    val scratch = IntArray(pixels.size)
    repeat(3) {
        blurLines(pixels, scratch, width, height, radius)
        blurLines(scratch, pixels, height, width, radius)
    }
}

/**
 * One box pass along each of [height] lines of [width] pixels, written
 * transposed into [dst] - so running it twice blurs both axes and restores
 * the original orientation.
 */
private fun blurLines(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
    val window = 2 * radius + 1
    for (y in 0 until height) {
        val row = y * width
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (i in -radius..radius) {
            val p = src[row + i.coerceIn(0, width - 1)]
            a += p ushr 24
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
        }
        for (x in 0 until width) {
            dst[x * height + y] =
                ((a / window) shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
            val add = src[row + (x + radius + 1).coerceIn(0, width - 1)]
            val sub = src[row + (x - radius).coerceIn(0, width - 1)]
            a += (add ushr 24) - (sub ushr 24)
            r += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
            g += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
            b += (add and 0xFF) - (sub and 0xFF)
        }
    }
}
