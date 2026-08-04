@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package xyz.five82.takeup.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

// Nullable so previews and tests compose without the transition plumbing.
internal val LocalSharedTransitionScope =
    staticCompositionLocalOf<SharedTransitionScope?> { null }
internal val LocalNavAnimatedContentScope =
    staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * One-shot entrance for stacked rows: each row fades in and settles upward
 * with a slight overshoot, offset by its index. Saveable, so returning to a
 * kept screen does not replay the choreography.
 */
@Composable
internal fun Modifier.staggeredEntrance(index: Int): Modifier {
    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val delayMillis = (index * 70).coerceAtMost(420)
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 350, delayMillis = delayMillis),
        label = "entranceAlpha",
    )
    val settle by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(
            durationMillis = 500,
            delayMillis = delayMillis,
            easing = EaseOutBack,
        ),
        label = "entranceOffset",
    )
    return graphicsLayer {
        this.alpha = alpha
        translationY = (1f - settle) * 28.dp.toPx()
    }
}

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
