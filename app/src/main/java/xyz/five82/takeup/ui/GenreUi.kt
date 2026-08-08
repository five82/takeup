@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package xyz.five82.takeup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import xyz.five82.takeup.R
import kotlin.math.abs

// Curated card palette: cool-leaning dark saturated bases with white text.
// Twelve entries so repeats are rare across a genre grid; no browns or tans -
// the two warm survivors (crimson, magenta) stay because dark red reads as
// velvet rather than rust.
private val GenreCardColors = listOf(
    Color(0xFF7D2231), // crimson
    Color(0xFF6A2550), // magenta
    Color(0xFF7A2D6E), // orchid
    Color(0xFF4A1876), // violet
    Color(0xFF35318F), // indigo
    Color(0xFF3D49A5), // iris
    Color(0xFF1D4B7D), // cobalt
    Color(0xFF135F8C), // azure
    Color(0xFF0E5A6B), // cyan
    Color(0xFF12525E), // teal
    Color(0xFF175A34), // emerald
    Color(0xFF3A4354), // slate
)

// Decorative marks from the Material shape library.
private val GenreMotifs = listOf(
    MaterialShapes.Sunny,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.SoftBurst,
    MaterialShapes.Gem,
    MaterialShapes.Flower,
    MaterialShapes.Cookie12Sided,
    MaterialShapes.Puffy,
)

internal data class ShelfIdentity(val color: Color, val motif: RoundedPolygon)

/**
 * The stable visual identity for a named shelf: color and motif derived from
 * the name, so a genre or collection looks the same wherever it appears -
 * card, browse row, and landing header alike.
 */
internal fun shelfIdentity(name: String): ShelfIdentity {
    val hash = abs(name.hashCode())
    return ShelfIdentity(
        color = GenreCardColors[hash % GenreCardColors.size],
        motif = GenreMotifs[(hash / GenreCardColors.size) % GenreMotifs.size],
    )
}

// The gradient's dark end, shared by cards and landing headers. Pulls toward
// the stage's indigo (surfaceContainerLowest) so cards sink into the app's
// background rather than toward black.
internal fun shelfGradientEnd(base: Color): Color = lerp(base, Color(0xFF0F1421), 0.55f)

/** Color-blocked genre card with a Material-shape motif. */
@Composable
internal fun GenreCard(
    name: String,
    itemCount: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val identity = shelfIdentity(name)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = identity.color,
        contentColor = Color.White,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        0f to identity.color,
                        1f to shelfGradientEnd(identity.color),
                    ),
                ),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 26.dp, y = (-26).dp)
                    .size(92.dp)
                    .rotate(-15f)
                    .background(Color.White.copy(alpha = 0.18f), identity.motif.toShape()),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            ) {
                Text(
                    text = name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                if (itemCount != null) {
                    Text(
                        text = pluralStringResource(R.plurals.title_count, itemCount, itemCount),
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
