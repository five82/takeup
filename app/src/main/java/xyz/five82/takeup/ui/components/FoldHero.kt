package xyz.five82.takeup.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import xyz.five82.takeup.ui.FoldLayout
import xyz.five82.takeup.ui.theme.Ink

/** Shared Fold artwork geometry: full-bleed photo, real cut, measured safe identity. */
@Composable
internal fun FoldHero(
    title: String,
    backdrop: String?,
    logo: String?,
    layout: FoldLayout,
    modifier: Modifier = Modifier,
    safeInsets: WindowInsets = WindowInsets.safeDrawing,
    details: @Composable (inline: Boolean) -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        BiasCutBackdrop(
            imageUrl = backdrop,
            solidLeft = 16.dp,
            contentDescription = title,
            artAspectRatio = 16f / 9f,
            modifier = Modifier.fillMaxWidth(),
        )
        // Measure after camera clearance, not against the raw photo width.
        // Larger text can grow or stack the identity without cropping the art.
        BoxWithConstraints(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(safeInsets.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 20.dp),
        ) {
            val inline = layout.inlineHeroIdentity && maxWidth.value / LocalDensity.current.fontScale >= 420f
            val compact = layout == FoldLayout.CoverLandscape && inline
            val identity: @Composable () -> Unit = {
                val identityModifier = if (inline) Modifier.width(if (compact) 152.dp else 176.dp)
                else Modifier.fillMaxWidth(0.8f)
                if (logo != null) {
                    AsyncImage(
                        model = logo,
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.BottomStart,
                        modifier = identityModifier.height(if (compact) 56.dp else 64.dp),
                    )
                } else {
                    Text(title, style = MaterialTheme.typography.displaySmall, color = Ink, modifier = identityModifier)
                }
            }
            if (inline) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    identity()
                    Column(Modifier.weight(1f)) { details(true) }
                }
            } else {
                Column {
                    identity()
                    details(false)
                }
            }
        }
    }
}
