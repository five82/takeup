@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package xyz.five82.takeup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.R
import xyz.five82.takeup.data.LoomItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ShowDetailsScreen(
    state: MainUiState.ShowDetails,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onEditArtwork: () -> Unit,
    onSeasonSelected: (LoomItem) -> Unit,
) {
    BackHandler(onBack = onBack)
    UseLightStatusBarIcons()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Box(Modifier.fillMaxSize()) {
    AmbientGlow(
        url = state.show.backdropUrl(state.serverUrl) ?: state.show.posterUrl(state.serverUrl),
    )
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    MediaOverlayIconButton(
                        iconResource = R.drawable.ic_arrow_back,
                        contentDescription = stringResource(R.string.navigate_back),
                        onClick = onBack,
                    )
                },
                actions = {
                    if (state.show.tmdbId > 0) {
                        MediaOverlayIconButton(
                            iconResource = R.drawable.ic_artwork,
                            contentDescription = stringResource(R.string.artwork),
                            onClick = onEditArtwork,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                FadingBackdropArtwork(
                    url = state.show.backdropUrl(state.serverUrl),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .itemArtworkSharedBounds(state.show.id),
                )
            }
            if (state.isLoading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            state.error?.let { error ->
                item {
                    ErrorCard(
                        message = error,
                        onRetry = onRetry,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        MediaArtwork(
                            url = state.show.posterUrl(state.serverUrl),
                            modifier = Modifier
                                .width(112.dp)
                                .aspectRatio(2f / 3f)
                                .clip(MaterialTheme.shapes.medium),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val logoUrl = state.show.logoUrl(state.serverUrl)
                            if (logoUrl != null) {
                                TitleLogo(
                                    url = logoUrl,
                                    title = state.show.title,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                Text(
                                    text = state.show.title,
                                    style = MaterialTheme.typography.headlineSmallEmphasized,
                                )
                            }
                            state.show.subtitle()?.let {
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
            }
            if (state.show.overview.isNotBlank()) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.overview),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = state.show.overview,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            if (state.seasons.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.seasons),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                }
                items(state.seasons, key = { it.id }) { season ->
                    Card(
                        onClick = { onSeasonSelected(season) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MediaArtwork(
                                url = season.posterUrl(state.serverUrl),
                                modifier = Modifier
                                    .width(72.dp)
                                    .aspectRatio(2f / 3f),
                            )
                            Text(
                                text = season.title,
                                modifier = Modifier.padding(18.dp),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            } else if (!state.isLoading && state.error == null) {
                item {
                    Text(
                        text = stringResource(R.string.no_seasons),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    }
}
