package xyz.five82.takeup.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/** Only hinge-equipped devices opt in; rotating a regular phone never changes its layout. */
enum class FoldLayout(val sidebarWidth: Int = 0) {
    Phone,
    CoverPortrait,
    InnerPortrait(180),
    InnerLandscape(180),
    CoverLandscape(160),
    ;

    val hasSidebar: Boolean get() = sidebarWidth > 0
    val inlineHeroIdentity: Boolean get() = this == InnerLandscape || this == CoverLandscape
    val thumbWidth: Int get() = if (this == InnerLandscape) 180 else 200
}

/**
 * Use available window space, not model names or physical pixel resolutions.
 * The compact fallback also covers a narrow multi-window on a foldable. A
 * sidebar needs at least 600dp of total width to leave a useful content pane.
 */
fun foldLayout(hasHinge: Boolean, widthDp: Float, heightDp: Float): FoldLayout = when {
    !hasHinge -> FoldLayout.Phone
    widthDp >= 600 && heightDp >= 600 ->
        if (widthDp > heightDp) FoldLayout.InnerLandscape else FoldLayout.InnerPortrait
    widthDp >= 600 && widthDp > heightDp -> FoldLayout.CoverLandscape
    else -> FoldLayout.CoverPortrait
}

// The inner portrait camera is reported as a top inset, so keep its sidebar on
// the known clear right side. Landscape layouts use physical cutout edges: a
// 180-degree rotation moves the camera to the other side of the window. Keep
// inner landscape on the right when neither edge reports a cutout.
internal fun foldSidebarOnRight(layout: FoldLayout, cutoutLeft: Int, cutoutRight: Int): Boolean = when (layout) {
    FoldLayout.InnerPortrait -> true
    FoldLayout.InnerLandscape -> cutoutLeft >= cutoutRight
    FoldLayout.CoverLandscape -> cutoutLeft > cutoutRight
    else -> false
}

internal fun showNavPill(layout: FoldLayout, screen: Screen?): Boolean =
    !layout.hasSidebar && screen == null

/** Apply after the background so status/camera safety never creates an unpainted gutter. */
@Composable
internal fun Modifier.browsingContentInsets(safeInsets: WindowInsets = WindowInsets.safeDrawing): Modifier =
    if (LocalFoldLayout.current == FoldLayout.Phone) statusBarsPadding()
    else windowInsetsPadding(safeInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))

/** Keep the last utility-screen row clear of the system bar, inside the scrollable content. */
@Composable
internal fun foldBottomPadding(margin: Dp, navigationInsets: WindowInsets = WindowInsets.navigationBars): Dp =
    if (LocalFoldLayout.current == FoldLayout.Phone) margin
    else margin + navigationInsets.asPaddingValues().calculateBottomPadding()

// Browsing screens share the Fold layout; playback always uses the full window.
val LocalFoldLayout = staticCompositionLocalOf { FoldLayout.Phone }

/** Protect content locally without taking the camera edge away from artwork. */
@Composable
internal fun Modifier.foldContentInsets(): Modifier =
    if (LocalFoldLayout.current == FoldLayout.Phone) this
    else windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
