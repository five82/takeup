@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package xyz.five82.takeup.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

// Nullable so previews and tests compose without the transition plumbing.
internal val LocalSharedTransitionScope =
    staticCompositionLocalOf<SharedTransitionScope?> { null }
internal val LocalNavAnimatedContentScope =
    staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Container transform for an item's artwork between browse cards and the
 * detail hero. Applied only where an item appears at most once per screen
 * (grids, search results, detail heroes) - duplicate keys on one screen would
 * fight over the transition. No-op when the scopes are absent.
 */
@Composable
internal fun Modifier.itemArtworkSharedBounds(itemId: Long): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedContentScope.current ?: return this
    return with(shared) {
        this@itemArtworkSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "artwork-$itemId"),
            animatedVisibilityScope = animated,
        )
    }
}
