package xyz.five82.takeup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.ui.components.Selvedge
import xyz.five82.takeup.ui.theme.Amber
import xyz.five82.takeup.ui.theme.Ember
import xyz.five82.takeup.ui.theme.Ink
import xyz.five82.takeup.ui.theme.Teal
import xyz.five82.takeup.ui.theme.Violet
import xyz.five82.takeup.ui.theme.Line
import xyz.five82.takeup.ui.theme.Muted
import xyz.five82.takeup.ui.theme.Surface1
import xyz.five82.takeup.ui.theme.Surface2

@Composable
internal fun FoldSidebar(
    nav: NavState,
    layout: FoldLayout,
    modifier: Modifier = Modifier,
    safeInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val compact = layout == FoldLayout.CoverLandscape
    Column(
        modifier
            .width(layout.sidebarWidth.dp)
            .fillMaxHeight()
            .background(Surface1)
            // The cover sidebar sits opposite the camera, not beside a camera
            // gutter. Keep its width and horizontal spacing independent of it.
            .windowInsetsPadding(safeInsets.only(WindowInsetsSides.Vertical))
            .padding(horizontal = if (compact) 8.dp else 10.dp),
    ) {
        // Scroll the destinations when accessibility text or a short window
        // needs more height. Utility actions remain reachable at the bottom.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                "Takeup",
                style = MaterialTheme.typography.displaySmall,
                color = Ink,
                modifier = Modifier.padding(start = 10.dp, top = 20.dp),
            )
            Selvedge(Modifier.padding(start = 10.dp, top = 8.dp).width(66.dp), height = 3f)
            Spacer(Modifier.height(20.dp))
            Column(Modifier.selectableGroup()) {
                for (tab in Tab.entries) {
                    SidebarDestination(
                        tab.label,
                        when (tab) {
                            Tab.Home -> Icons.Filled.Home
                            Tab.Movies -> MovieIcon
                            Tab.Tv -> TvIcon
                            Tab.Shorts -> TheatersIcon
                            Tab.Browse -> ExploreIcon
                        },
                        selected = nav.tab == tab,
                        role = Role.Tab,
                        thread = when (tab) {
                            Tab.Home -> null
                            Tab.Movies -> Ember
                            Tab.Tv -> Teal
                            Tab.Shorts -> Amber
                            Tab.Browse -> Violet
                        },
                        onClick = { nav.selectTab(tab) },
                    )
                }
            }
            if (!compact) {
                HorizontalDivider(Modifier.padding(horizontal = 10.dp, vertical = 20.dp), color = Line)
                SidebarDestination("Search", Icons.Filled.Search) { nav.openSidebarScreen(Screen.Search()) }
                SidebarDestination("Downloads", DownloadIcon) { nav.openSidebarScreen(Screen.Downloads) }
                SidebarDestination("Settings", Icons.Filled.Settings) { nav.openSidebarScreen(Screen.Settings) }
            }
        }
        if (compact) {
            HorizontalDivider(Modifier.padding(horizontal = 6.dp), color = Line)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Three 48dp targets fit the compact sidebar's 144dp interior.
                IconButton(onClick = { nav.openSidebarScreen(Screen.Search()) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Search, "Search", tint = Muted)
                }
                IconButton(onClick = { nav.openSidebarScreen(Screen.Downloads) }, modifier = Modifier.size(48.dp)) {
                    Icon(DownloadIcon, "Downloads", tint = Muted)
                }
                IconButton(onClick = { nav.openSidebarScreen(Screen.Settings) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Settings, "Settings", tint = Muted)
                }
            }
        }
    }
}

// The sidebar remains reachable on utility screens; tapping the current utility
// should not create another copy that Back would have to unwind.
internal fun NavState.openSidebarScreen(screen: Screen) {
    if (stack.lastOrNull() != screen) push(screen)
}

@Composable
private fun SidebarDestination(
    label: String,
    icon: ImageVector,
    selected: Boolean = false,
    role: Role = Role.Button,
    thread: Color? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Surface2 else Surface1)
            .then(
                if (role == Role.Tab) Modifier.selectable(selected, role = role, onClick = onClick)
                else Modifier.clickable(role = role, onClick = onClick),
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) Ink else Muted, modifier = Modifier.size(22.dp))
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall, color = if (selected) Ink else Muted)
            if (selected) {
                Box(Modifier.padding(top = 4.dp)) {
                    if (thread == null) Selvedge(Modifier.width(20.dp), height = 2f)
                    else Box(Modifier.width(20.dp).height(2.dp).background(thread))
                }
            }
        }
    }
}
