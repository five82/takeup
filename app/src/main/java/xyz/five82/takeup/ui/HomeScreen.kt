@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package xyz.five82.takeup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import xyz.five82.takeup.R
import xyz.five82.takeup.data.LoomItem
import xyz.five82.takeup.ui.theme.heroBottomScrim

@Composable
internal fun HomeScreen(
    state: MainUiState.Home,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowMovies: () -> Unit,
    onShowShows: () -> Unit,
    onItemSelected: (LoomItem) -> Unit,
    onPlayItem: (LoomItem) -> Unit,
    onHeroSeedUrlChanged: (String?) -> Unit,
) {
    UseLightStatusBarIcons()
    val content = state.content
    val isEmpty = content.continueWatching.isEmpty() &&
        content.recentlyAdded.isEmpty() && content.movies.isEmpty() && content.shows.isEmpty()
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading && isEmpty -> LoadingHome()
            state.error != null && isEmpty -> FullScreenError(
                message = state.error,
                onRetry = onRetry,
                secondaryLabel = stringResource(R.string.settings),
                onSecondary = onOpenSettings,
            )
            else -> HomeList(
                state = state,
                onRetry = onRetry,
                onShowMovies = onShowMovies,
                onShowShows = onShowShows,
                onItemSelected = onItemSelected,
                onPlayItem = onPlayItem,
                onHeroSeedUrlChanged = onHeroSeedUrlChanged,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp),
        ) {
            MediaOverlayIconButton(
                iconResource = R.drawable.ic_settings,
                contentDescription = stringResource(R.string.settings),
                onClick = onOpenSettings,
            )
        }
    }
}

@Composable
private fun LoadingHome() {
    val description = stringResource(R.string.loading_home)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .semantics { contentDescription = description },
        contentPadding = PaddingValues(top = 12.dp, bottom = BottomToolbarInset),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item {
            PulsingPlaceholder(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .aspectRatio(4f / 5f)
                    .clip(MaterialTheme.shapes.extraLarge),
            )
        }
        items(2) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(4) {
                    PosterCardPlaceholder(modifier = Modifier.width(140.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeList(
    state: MainUiState.Home,
    onRetry: () -> Unit,
    onShowMovies: () -> Unit,
    onShowShows: () -> Unit,
    onItemSelected: (LoomItem) -> Unit,
    onPlayItem: (LoomItem) -> Unit,
    onHeroSeedUrlChanged: (String?) -> Unit,
) {
    val content = state.content
    val heroes = remember(content) { state.heroItems() }
    val carouselState = rememberCarouselState { heroes.size }
    val heroItem = heroes.getOrNull(carouselState.currentItem)
    val heroBackdropUrl = heroItem?.let {
        it.backdropUrl(state.serverUrl) ?: it.posterUrl(state.serverUrl)
    }
    LaunchedEffect(heroBackdropUrl) { onHeroSeedUrlChanged(heroBackdropUrl) }

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = onRetry,
        modifier = Modifier.fillMaxSize(),
    ) {
        AmbientBackdrop(url = heroBackdropUrl)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = BottomToolbarInset),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            state.error?.let { error ->
                item {
                    ErrorCard(
                        message = error,
                        onRetry = onRetry,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp),
                    )
                }
            }
            if (heroes.isNotEmpty()) {
                item {
                    HeroCarousel(
                        serverUrl = state.serverUrl,
                        heroes = heroes,
                        continueWatchingIds = content.continueWatching.map { it.id }.toSet(),
                        recentlyAddedIds = content.recentlyAdded.map { it.id }.toSet(),
                        carouselState = carouselState,
                        onPlayItem = onPlayItem,
                        onItemSelected = onItemSelected,
                    )
                }
            }
            if (content.continueWatching.isNotEmpty()) {
                item {
                    MediaRow(
                        title = stringResource(R.string.continue_watching),
                        serverUrl = state.serverUrl,
                        items = content.continueWatching,
                        landscape = true,
                        onItemSelected = onItemSelected,
                    )
                }
            }
            if (content.recentlyAdded.isNotEmpty()) {
                item {
                    MediaRow(
                        title = stringResource(R.string.recently_added),
                        serverUrl = state.serverUrl,
                        items = content.recentlyAdded,
                        onItemSelected = onItemSelected,
                    )
                }
            }
            if (content.movies.isNotEmpty()) {
                item {
                    MediaRow(
                        title = stringResource(R.string.movies),
                        serverUrl = state.serverUrl,
                        items = content.movies.take(12),
                        actionText = stringResource(R.string.see_all),
                        onAction = onShowMovies,
                        onItemSelected = onItemSelected,
                    )
                }
            }
            if (content.shows.isNotEmpty()) {
                item {
                    MediaRow(
                        title = stringResource(R.string.shows),
                        serverUrl = state.serverUrl,
                        items = content.shows.take(12),
                        actionText = stringResource(R.string.see_all),
                        onAction = onShowShows,
                        onItemSelected = onItemSelected,
                    )
                }
            }
            if (heroes.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_home_items),
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(horizontal = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Blurred copy of the hero backdrop dissolving into the surface, so the top of
 * Home glows with the artwork's palette instead of sitting on a flat field.
 */
@Composable
private fun AmbientBackdrop(url: String?, modifier: Modifier = Modifier) {
    if (url == null) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(480.dp),
    ) {
        AsyncImage(
            // A tiny decode is all a heavy blur needs; keeps the effect cheap.
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .size(Size(64, 64))
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(40.dp)
                .alpha(0.55f),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.surface,
                    ),
                ),
        )
    }
}

@Composable
private fun HeroCarousel(
    serverUrl: String,
    heroes: List<LoomItem>,
    continueWatchingIds: Set<Long>,
    recentlyAddedIds: Set<Long>,
    carouselState: CarouselState,
    onPlayItem: (LoomItem) -> Unit,
    onItemSelected: (LoomItem) -> Unit,
) {
    HorizontalCenteredHeroCarousel(
        state = carouselState,
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = 8.dp)
            .fillMaxWidth()
            .aspectRatio(4f / 5f),
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) { index ->
        val item = heroes[index]
        HeroItem(
            serverUrl = serverUrl,
            item = item,
            label = stringResource(
                when {
                    item.id in continueWatchingIds -> R.string.continue_watching
                    item.id in recentlyAddedIds -> R.string.recently_added
                    item.kind == "show" -> R.string.shows
                    else -> R.string.movies
                },
            ),
            modifier = Modifier.maskClip(MaterialTheme.shapes.extraLarge),
            onPlay = { onPlayItem(item) },
            onDetails = { onItemSelected(item) },
        )
    }
}

@Composable
private fun HeroItem(
    serverUrl: String,
    item: LoomItem,
    label: String,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        MediaArtwork(
            url = item.backdropUrl(serverUrl) ?: item.posterUrl(serverUrl),
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(heroBottomScrim(MaterialTheme.colorScheme.surface)),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMediumEmphasized,
                )
            }
            val logoUrl = item.logoUrl(serverUrl)
            if (logoUrl != null) {
                TitleLogo(
                    url = logoUrl,
                    title = item.title,
                    modifier = Modifier.fillMaxWidth(0.72f),
                )
            } else {
                Text(
                    text = item.title,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                )
            }
            val metadata = listOfNotNull(
                item.cardSubtitle(),
                item.mediaDurationMs.takeIf { it > 0 }?.let(::formatRuntime),
            ).joinToString(" \u00B7 ")
            if (metadata.isNotBlank()) {
                Text(
                    text = metadata,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item.progress?.let { PlaybackStatus(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPlay,
                    shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                    modifier = Modifier.height(ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(
                        ButtonDefaults.MediumContainerHeight,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play),
                        contentDescription = null,
                    )
                    Text(
                        stringResource(
                            when {
                                item.kind == "show" -> R.string.view_details
                                (item.progress?.resumePositionMs ?: 0L) > 0 -> R.string.resume
                                else -> R.string.play
                            },
                        ),
                    )
                }
                if (item.kind != "show") {
                    FilledTonalButton(
                        onClick = onDetails,
                        shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                        modifier = Modifier.height(ButtonDefaults.MediumContainerHeight),
                        contentPadding = ButtonDefaults.contentPaddingFor(
                            ButtonDefaults.MediumContainerHeight,
                        ),
                    ) {
                        Text(stringResource(R.string.view_details))
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaRow(
    title: String,
    serverUrl: String,
    items: List<LoomItem>,
    onItemSelected: (LoomItem) -> Unit,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    landscape: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            if (actionText != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(actionText)
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { item ->
                if (landscape) {
                    LandscapeMediaCard(
                        serverUrl = serverUrl,
                        item = item,
                        onClick = { onItemSelected(item) },
                        modifier = Modifier.width(240.dp),
                    )
                } else {
                    MediaCard(
                        serverUrl = serverUrl,
                        item = item,
                        onClick = { onItemSelected(item) },
                        modifier = Modifier.width(140.dp),
                    )
                }
            }
        }
    }
}
