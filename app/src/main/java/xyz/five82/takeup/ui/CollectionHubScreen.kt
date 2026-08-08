@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package xyz.five82.takeup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.R
import xyz.five82.takeup.data.LoomCollection

/**
 * Every shelf Loom curates, as artwork cards. The shelves ride along with the
 * home load, so this screen has nothing to fetch and no failure to report - the
 * row that opens it only appears once they are in hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionHubScreen(
    state: MainUiState.CollectionHub,
    onBack: () -> Unit,
    onCollectionSelected: (LoomCollection) -> Unit,
) {
    BackHandler(onBack = onBack)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // Transparent so the app-level ambient glow reads behind the grid.
        containerColor = Color.Transparent,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.collections)) },
                navigationIcon = { NavigationBackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 176.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 4.dp,
                end = 12.dp,
                bottom = 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.collections, key = { it.slug }) { collection ->
                CollectionCard(
                    serverUrl = state.serverUrl,
                    collection = collection,
                    onClick = { onCollectionSelected(collection) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                )
            }
        }
    }
}
