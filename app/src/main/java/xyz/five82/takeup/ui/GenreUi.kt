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
import xyz.five82.takeup.R
import kotlin.math.abs

// Curated card palette: genre cards keep a fixed identity regardless of the
// current artwork seed. Dark, saturated bases with white text.
private val GenreCardColors = listOf(
    Color(0xFF7D2231), // crimson
    Color(0xFF7D3A14), // ember
    Color(0xFF7A5A18), // gold
    Color(0xFF175A34), // forest
    Color(0xFF12525E), // teal
    Color(0xFF1D4B7D), // azure
    Color(0xFF4A1876), // violet
    Color(0xFF6A2550), // magenta
)

// Decorative marks from the Material shape library, one per card corner.
private val genreMotifs
    @Composable get() = listOf(
        MaterialShapes.Sunny,
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.SoftBurst,
        MaterialShapes.Gem,
        MaterialShapes.Flower,
        MaterialShapes.Cookie12Sided,
        MaterialShapes.Puffy,
    )

/**
 * Color-blocked genre card with a Material-shape motif. Color and motif are
 * derived from the genre name, so a genre looks the same wherever it appears.
 */
@Composable
internal fun GenreCard(
    name: String,
    itemCount: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hash = abs(name.hashCode())
    val base = GenreCardColors[hash % GenreCardColors.size]
    val motifs = genreMotifs
    val motif = motifs[(hash / GenreCardColors.size) % motifs.size].toShape()
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = base,
        contentColor = Color.White,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        0f to base,
                        1f to lerp(base, Color(0xFF0C0E12), 0.55f),
                    ),
                ),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 26.dp, y = (-26).dp)
                    .size(92.dp)
                    .rotate(-15f)
                    .background(Color.White.copy(alpha = 0.18f), motif),
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
