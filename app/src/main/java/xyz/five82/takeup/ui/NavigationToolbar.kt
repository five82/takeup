@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi::class,
)

package xyz.five82.takeup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials
import xyz.five82.takeup.R

// Clearance top-level lists add to their bottom content padding so the last
// items can scroll out from under the floating toolbar.
internal val BottomToolbarInset = 96.dp

@Composable
internal fun NavigationToolbar(
    current: TopDestination?,
    onSelect: (TopDestination) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier
            .clip(FloatingToolbarDefaults.ContainerShape)
            .hazeEffect(hazeState, HazeMaterials.thin(MaterialTheme.colorScheme.surface)),
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
            // The haze glass provides the container; the toolbar itself stays clear.
            toolbarContainerColor = Color.Transparent,
        ),
        scrollBehavior = scrollBehavior,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NavigationToolbarItem(
                destination = TopDestination.Home,
                iconResource = R.drawable.ic_home,
                label = stringResource(R.string.home),
                current = current,
                onSelect = onSelect,
            )
            NavigationToolbarItem(
                destination = TopDestination.Movies,
                iconResource = R.drawable.ic_movie,
                label = stringResource(R.string.movies),
                current = current,
                onSelect = onSelect,
            )
            NavigationToolbarItem(
                destination = TopDestination.Shows,
                iconResource = R.drawable.ic_tv,
                label = stringResource(R.string.shows),
                current = current,
                onSelect = onSelect,
            )
            NavigationToolbarItem(
                destination = TopDestination.Search,
                iconResource = R.drawable.ic_search,
                label = stringResource(R.string.search),
                current = current,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun NavigationToolbarItem(
    destination: TopDestination,
    iconResource: Int,
    label: String,
    current: TopDestination?,
    onSelect: (TopDestination) -> Unit,
) {
    FilledIconToggleButton(
        checked = destination == current,
        onCheckedChange = { onSelect(destination) },
        shapes = IconButtonDefaults.toggleableShapes(),
    ) {
        Icon(
            painter = painterResource(iconResource),
            contentDescription = label,
        )
    }
}
