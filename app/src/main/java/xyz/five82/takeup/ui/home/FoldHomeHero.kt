package xyz.five82.takeup.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.api.Item
import xyz.five82.takeup.ui.FoldLayout
import xyz.five82.takeup.ui.components.FoldHero
import xyz.five82.takeup.ui.components.ThreadProgress
import xyz.five82.takeup.ui.progressFraction
import xyz.five82.takeup.ui.theme.Ember
import xyz.five82.takeup.ui.theme.Ink

@Composable
internal fun FoldHomeHero(
    item: Item,
    backdrop: String?,
    logo: String?,
    label: String,
    layout: FoldLayout,
    onOpen: () -> Unit,
    safeInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val fraction = progressFraction(item)
    val details = buildList {
        if (item.year > 0) add(item.year.toString())
        item.genres?.firstOrNull()?.name?.let(::add)
    }.joinToString(" \u00b7 ")
    FoldHero(
        item.title, backdrop, logo, layout,
        modifier = Modifier.clickable(onClick = onOpen), safeInsets = safeInsets,
    ) { inline ->
        if (inline) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = Ink)
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.85f))
                }
                if (fraction != null) {
                    ThreadProgress(fraction, Ember, Modifier.padding(top = 4.dp).fillMaxWidth(0.8f))
                }
            }
        } else {
            Text(
                listOf(label, details).filter { it.isNotEmpty() }.joinToString(" \u00b7 "),
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 12.dp),
            )
            if (fraction != null) {
                ThreadProgress(fraction, Ember, Modifier.padding(top = 8.dp).fillMaxWidth(0.6f))
            }
        }
    }
}
