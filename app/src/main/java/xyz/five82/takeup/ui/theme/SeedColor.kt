package xyz.five82.takeup.ui.theme

import android.content.Context
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.materialkolor.ktx.themeColors
import com.materialkolor.ktx.toColor
import com.materialkolor.ktx.toHct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Seeds are deterministic per artwork URL, so a small process-wide cache keeps
// revisited screens from re-running quantization or flashing the fallback.
private val seedCache = LruCache<String, Color>(64)

// The seed should be the artwork's most vivid voice, not its average. Among
// the top scored candidates we pick the most chromatic one, then lift it to a
// chroma floor: brown is just low-chroma orange, so the floor turns muddy
// artwork into a confident amber instead of letting it drag the accent khaki.
private const val SeedCandidates = 4
private const val MinSeedChroma = 40.0

// Below this chroma the artwork is effectively grayscale; boosting it would
// invent a hue the artwork doesn't have, so fall back to the brand seed.
private const val GrayscaleChroma = 10.0

/**
 * Resolves the theme seed color for an artwork URL. Returns the cached seed
 * immediately when available; otherwise keeps the previously resolved seed on
 * screen until extraction finishes, so the theme never snaps to the fallback
 * mid-transition. A null URL resolves to the brand seed.
 */
@Composable
internal fun rememberSeedColor(url: String?): Color {
    val context = LocalContext.current.applicationContext
    var seed by remember { mutableStateOf(url?.let { seedCache.get(it) } ?: TakeupBrandSeed) }
    LaunchedEffect(url) {
        seed = when (url) {
            null -> TakeupBrandSeed
            else -> seedCache.get(url)
                ?: extractSeedColor(context, url).also { seedCache.put(url, it) }
        }
    }
    return seed
}

private suspend fun extractSeedColor(context: Context, url: String): Color {
    // A tiny software bitmap is all quantization needs; the request rides the
    // same Coil cache as the visible artwork.
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(128)
        .allowHardware(false)
        .build()
    val result = SingletonImageLoader.get(context).execute(request)
    val image = (result as? SuccessResult)?.image ?: return TakeupBrandSeed
    return withContext(Dispatchers.Default) {
        image.toBitmap().asImageBitmap().vibrantSeedOrNull() ?: TakeupBrandSeed
    }
}

internal fun ImageBitmap.vibrantSeedOrNull(): Color? {
    // Unspecified marks the "nothing suitable" fallback so it can be dropped.
    val candidates = themeColors(
        fallback = Color.Unspecified,
        desired = SeedCandidates,
    ).filter { it != Color.Unspecified }
    val best = candidates.maxByOrNull { it.toHct().chroma } ?: return null
    val hct = best.toHct()
    return when {
        hct.chroma < GrayscaleChroma -> null
        hct.chroma < MinSeedChroma -> hct.withChroma(MinSeedChroma).toColor()
        else -> best
    }
}
