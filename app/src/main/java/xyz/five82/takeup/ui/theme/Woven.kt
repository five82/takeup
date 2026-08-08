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
 * The color a title weaves through its screens, extracted from poster art.
 * Cached per item id so a detail screen revisit never re-decodes.
 */
object WovenColors {

    private val cache = ConcurrentHashMap<Long, Color>()

    suspend fun seedFor(context: Context, itemId: Long, posterUrl: String?): Color? {
        if (posterUrl == null) return null
        cache[itemId]?.let { return it }
        val request = ImageRequest.Builder(context)
            .data(posterUrl)
            .size(64)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
        val seed = dominantColor(result.image.toBitmap()) ?: return null
        cache[itemId] = seed
        return seed
    }

    /**
     * Most chromatic frequent color: a coarse RGB histogram scored by count
     * times saturation, so a big beige sky loses to a smaller saturated
     * costume. Good enough for a seed the palette engine will tone anyway.
     */
    fun dominantColor(bitmap: Bitmap): Color? {
        val width = bitmap.width
        val height = bitmap.height
        if (width == 0 || height == 0) return null
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val counts = HashMap<Int, Int>()
        for (pixel in pixels) {
            // 4 bits per channel: 4096 buckets.
            val key = (pixel shr 12 and 0xF00) or (pixel shr 8 and 0x0F0) or (pixel shr 4 and 0x00F)
            counts[key] = (counts[key] ?: 0) + 1
        }
        var best: Int? = null
        var bestScore = 0f
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
            if (score > bestScore) {
                bestScore = score
                best = android.graphics.Color.rgb(r, g, b)
            }
        }
        return best?.let { Color(it) }
    }
}

/** Seed color for an item, resolving off the main thread; null until known. */
@Composable
fun rememberWovenSeed(itemId: Long, posterUrl: String?): Color? {
    val context = LocalContext.current
    val seed by produceState<Color?>(initialValue = null, itemId, posterUrl) {
        value = WovenColors.seedFor(context, itemId, posterUrl)
    }
    return seed
}
