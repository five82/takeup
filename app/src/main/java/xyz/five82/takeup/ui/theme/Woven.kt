package xyz.five82.takeup.ui.theme

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import java.util.concurrent.ConcurrentHashMap

/**
 * The colors a title weaves through its screens, extracted from its art.
 * Cached per artwork URL - an item's poster and backdrop disagree often
 * enough that they must not share an entry - so a revisit never re-decodes.
 */
object WovenColors {

    private val cache = ConcurrentHashMap<String, List<Color>>()

    /** The already-extracted threads for this art: empty when there is no art
     * to extract from, null while extraction hasn't finished. Lets a revisit
     * paint the right colors on its very first frame. */
    fun cached(artUrl: String?): List<Color>? =
        if (artUrl == null) emptyList() else cache[artUrl]

    /** Up to three hue-separated swatches; empty until the art decodes. */
    suspend fun threadsFor(context: Context, artUrl: String?): List<Color> {
        if (artUrl == null) return emptyList()
        cache[artUrl]?.let { return it }
        val request = ImageRequest.Builder(context)
            .data(artUrl)
            .size(64)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return emptyList()
        val threads = threadColors(result.image.toBitmap())
        if (threads.isNotEmpty()) cache[artUrl] = threads
        return threads
    }

    /**
     * Most chromatic frequent color: a coarse RGB histogram scored by count
     * times saturation, so a big beige sky loses to a smaller saturated
     * costume. Good enough for a seed the palette engine will tone anyway.
     */
    fun dominantColor(bitmap: Bitmap): Color? = threadColors(bitmap, max = 1).firstOrNull()

    /**
     * The top [max] scored buckets, kept at least 40 degrees of hue apart so
     * the swatches read as different threads instead of shades of one. Best
     * bucket first, so callers can treat index zero as the seed.
     */
    fun threadColors(bitmap: Bitmap, max: Int = 3): List<Color> {
        val ranked = scoredBuckets(bitmap)
        val picked = mutableListOf<Pair<Color, Float>>()
        for ((color, hue) in ranked) {
            if (picked.size >= max) break
            if (picked.none { hueDistance(it.second, hue) < 40f }) {
                picked += color to hue
            }
        }
        return picked.map { it.first }
    }

    /** Buckets worth using, best score first, paired with their hue. */
    private fun scoredBuckets(bitmap: Bitmap): List<Pair<Color, Float>> {
        val width = bitmap.width
        val height = bitmap.height
        if (width == 0 || height == 0) return emptyList()
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val counts = HashMap<Int, Int>()
        for (pixel in pixels) {
            // 4 bits per channel: 4096 buckets.
            val key = (pixel shr 12 and 0xF00) or (pixel shr 8 and 0x0F0) or (pixel shr 4 and 0x00F)
            counts[key] = (counts[key] ?: 0) + 1
        }
        val scored = mutableListOf<Triple<Int, Float, Float>>()
        val hsv = FloatArray(3)
        for ((key, count) in counts) {
            val r = (key shr 8 and 0xF) * 17
            val g = (key shr 4 and 0xF) * 17
            val b = (key and 0xF) * 17
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            val saturation = hsv[1]
            val value = hsv[2]
            // Skip near-black and near-white buckets; they are backdrop, not thread.
            if (value < 0.12f || (saturation < 0.08f && value > 0.85f)) continue
            val score = count * (0.15f + saturation) * (0.25f + value)
            scored += Triple(android.graphics.Color.rgb(r, g, b), score, hsv[0])
        }
        return scored
            .sortedByDescending { it.second }
            .map { Color(it.first) to it.third }
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = kotlin.math.abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }
}

/**
 * The tone backgrounds use a thread color at: HSV value clamped into a
 * narrow band. The ceiling keeps white logo art and Ink text landing on
 * every field; the floor keeps the field visible at all - posters often
 * yield near-black swatches that would otherwise vanish into the Stage.
 * Saturation gets a small floor so quantized swatches stay colorful rather
 * than murky; hue is never touched.
 */
fun Color.fieldTone(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
        hsv,
    )
    return Color.hsv(hsv[0], hsv[1].coerceAtLeast(0.30f), hsv[2].coerceIn(0.48f, 0.62f))
}

/** Seed color for an artwork, resolving off the main thread; null until known. */
@Composable
fun rememberWovenSeed(artUrl: String?): Color? =
    rememberWovenThreads(artUrl)?.firstOrNull()

/** Up to three thread colors for an artwork: null while extraction is still
 * running, empty when there is no art or it yielded nothing. Cached art is
 * served synchronously so revisits never wait a frame for their colors. */
@Composable
fun rememberWovenThreads(artUrl: String?): List<Color>? {
    val context = LocalContext.current
    val threads by produceState(initialValue = WovenColors.cached(artUrl), artUrl) {
        value = WovenColors.threadsFor(context, artUrl)
    }
    return threads
}
