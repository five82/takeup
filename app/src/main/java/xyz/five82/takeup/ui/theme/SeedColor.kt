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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.materialkolor.ktx.themeColorOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Seeds are deterministic per artwork URL, so a small process-wide cache keeps
// revisited screens from re-running quantization or flashing the fallback.
private val seedCache = LruCache<String, Color>(64)

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
        // themeColorOrNull filters low-chroma candidates, so grayscale artwork
        // falls back to the brand seed instead of a washed-out gray scheme.
        image.toBitmap().asImageBitmap().themeColorOrNull() ?: TakeupBrandSeed
    }
}
