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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import xyz.five82.takeup.R
import xyz.five82.takeup.data.DownloadEntry
import xyz.five82.takeup.data.Genre
import xyz.five82.takeup.data.GenreSummary
import xyz.five82.takeup.data.LoomCollection
import xyz.five82.takeup.data.LoomItem
import xyz.five82.takeup.data.downloadedRowItems
import xyz.five82.takeup.ui.theme.heroBottomScrim

/**
 * A home carrying nothing but the downloads row is the offline layout, so rendering
 * one while the first load is still in flight flashes offline on every cold start:
 * the library arrives a second later and the screen changes shape. Placeholders hold
 * that second instead, downloads included.
 *
 * Offline is the exception rather than a case of the same thing. There the
 * downloads-only home is the answer and not a half-loaded one, so a reconnect
 * attempt must leave it on screen rather than blank it behind a skeleton.
 */
internal fun showsPlaceholders(state: MainUiState.Home): Boolean =
    state.isLoading && !state.isOffline && state.content.isEmpty()

@Composable
internal fun HomeScreen(
    state: MainUiState.Home,
    downloads: List<DownloadEntry>,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowMovies: () -> Unit,
    onShowShorts: () -> Unit,
    onShowShows: () -> Unit,
    onItemSelected: (LoomItem) -> Unit,
    onPlayItem: (LoomItem) -> Unit,
    onGenreSelected: (Genre) -> Unit,
    onOpenGenreHub: () -> Unit,
    onCollectionSelected: (LoomCollection) -> Unit,
    onOpenCollectionHub: () -> Unit,
) {
    UseLightStatusBarIcons()
    // Downloads are playable without Loom, so a library that is empty only because
    // the server is unreachable is not empty as far as the user is concerned.
    val isEmpty = state.content.isEmpty() && downloads.isEmpty()
    Box(modifier = modifier.fillMaxSize()) {
        when {
            showsPlaceholders(state) -> LoadingHome()
            state.error != null && isEmpty -> FullScreenError(
                message = state.error,
                onRetry = onRetry,
                secondaryLabel = stringResource(R.string.settings),
                onSecondary = onOpenSettings,
            )
            else -> HomeList(
                state = state,
                downloads = downloads,
                onRetry = onRetry,
                onShowMovies = onShowMovies,
                onShowShorts = onShowShorts,
                onShowShows = onShowShows,
                onItemSelected = onItemSelected,
                onPlayItem = onPlayItem,
                onGenreSelected = onGenreSelected,
                onOpenGenreHub = onOpenGenreHub,
                onCollectionSelected = onCollectionSelected,
                onOpenCollectionHub = onOpenCollectionHub,
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
            ArtworkPlaceholder(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
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
    downloads: List<DownloadEntry>,
    onRetry: () -> Unit,
    onShowMovies: () -> Unit,
    onShowShorts: () -> Unit,
    onShowShows: () -> Unit,
    onItemSelected: (LoomItem) -> Unit,
    onPlayItem: (LoomItem) -> Unit,
    onGenreSelected: (Genre) -> Unit,
    onOpenGenreHub: () -> Unit,
    onCollectionSelected: (LoomCollection) -> Unit,
    onOpenCollectionHub: () -> Unit,
) {
    val content = state.content
    val today = remember { LocalDate.now() }
    val spotlight = remember(content, today) { state.spotlightItem(today.toEpochDay()) }
    val spotlights = remember(content, today) { state.genreSpotlights(today.dayOfYear) }
    val genreEntries = remember(content) { state.genreBrowseEntries() }

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = onRetry,
        modifier = Modifier.fillMaxSize(),
    ) {
        AmbientGlow()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // The toolbar is hidden offline, so its reserved space would be a gap.
            contentPadding = PaddingValues(
                bottom = if (state.isOffline) 24.dp else BottomToolbarInset,
            ),
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
            spotlight?.let { item ->
                item(key = "spotlight") {
                    SpotlightHero(
                        serverUrl = state.serverUrl,
                        item = item,
                        onPlay = { onPlayItem(item) },
                        onDetails = { onItemSelected(item) },
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
            // Directly below Continue Watching: the two rows are one story, and a
            // show crosses from one to the other as an episode finishes.
            if (content.nextUp.isNotEmpty()) {
                item {
                    MediaRow(
                        title = stringResource(R.string.next_up),
                        serverUrl = state.serverUrl,
                        items = content.nextUp,
                        landscape = true,
                        onItemSelected = onItemSelected,
                    )
                }
            }
            if (state.isOffline) {
                item {
                    Text(
                        text = stringResource(R.string.offline_downloads_only),
                        modifier = Modifier
                            .statusBarsPadding()
                            // Wide end padding keeps the banner clear of the
                            // settings button floating in the top-end corner.
                            .padding(start = 16.dp, end = 72.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (downloads.isNotEmpty()) {
                item {
                    DownloadRow(
                        entries = remember(downloads) { downloadedRowItems(downloads) },
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
            // Above the generated rows: a curated shelf is the most interesting
            // thing on Home that the user did not already ask for.
            if (content.collections.isNotEmpty()) {
                item(key = "collections") {
                    CollectionRow(
                        serverUrl = state.serverUrl,
                        collections = content.collections,
                        onCollectionSelected = onCollectionSelected,
                        onOpenCollectionHub = onOpenCollectionHub,
                    )
                }
            }
            spotlights.forEach { (genre, items) ->
                item(key = "spotlight:${genre.id}") {
                    MediaRow(
                        title = stringResource(R.string.genre_spotlight, genre.name),
                        serverUrl = state.serverUrl,
                        items = items,
                        actionText = stringResource(R.string.see_all),
                        onAction = { onGenreSelected(genre) },
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
            if (content.shorts.isNotEmpty()) {
                item {
                    MediaRow(
                        title = stringResource(R.string.shorts),
                        serverUrl = state.serverUrl,
                        items = content.shorts.take(12),
                        actionText = stringResource(R.string.see_all),
                        onAction = onShowShorts,
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
            if (genreEntries.isNotEmpty()) {
                item(key = "genreBrowse") {
                    GenreBrowseRow(
                        entries = genreEntries,
                        onGenreSelected = onGenreSelected,
                        onOpenGenreHub = onOpenGenreHub,
                    )
                }
            }
            if (spotlight == null && downloads.isEmpty()) {
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
 * The one hero: a static full-width card for the day's pick. Deliberately not
 * a carousel - no paging state, nothing animating over image loads, and the
 * single card can never mirror the rows below it because the pick excludes
 * anything watched or in progress.
 */
@Composable
private fun SpotlightHero(
    serverUrl: String,
    item: LoomItem,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
) {
    Box(
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = 8.dp)
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .aspectRatio(4f / 5f)
            .clip(MaterialTheme.shapes.extraLarge),
    ) {
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
                    text = stringResource(R.string.tonights_pick),
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
                    style = MaterialTheme.typography.displaySmallEmphasized,
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
                    style = MaterialTheme.typography.labelLarge,
                )
            }
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
                            if (item.kind == "show") R.string.view_details else R.string.play,
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

/** Horizontal strip of collection cards with a "See all" entry into the hub. */
@Composable
private fun CollectionRow(
    serverUrl: String,
    collections: List<LoomCollection>,
    onCollectionSelected: (LoomCollection) -> Unit,
    onOpenCollectionHub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.collections),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            TextButton(
                onClick = onOpenCollectionHub,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.see_all))
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(collections.take(8), key = { it.slug }) { collection ->
                CollectionCard(
                    serverUrl = serverUrl,
                    collection = collection,
                    onClick = { onCollectionSelected(collection) },
                    modifier = Modifier
                        .width(200.dp)
                        .height(112.dp),
                )
            }
        }
    }
}

/** Horizontal strip of genre cards with a "See all" entry into the hub. */
@Composable
private fun GenreBrowseRow(
    entries: List<GenreSummary>,
    onGenreSelected: (Genre) -> Unit,
    onOpenGenreHub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.browse_by_genre),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            TextButton(
                onClick = onOpenGenreHub,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.see_all))
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(entries.take(8), key = { it.id }) { entry ->
                GenreCard(
                    name = entry.name,
                    itemCount = entry.itemCount,
                    onClick = { onGenreSelected(Genre(id = entry.id, name = entry.name)) },
                    modifier = Modifier
                        .width(156.dp)
                        .height(88.dp),
                )
            }
        }
    }
}

/** Mirrors [MediaRow] but renders locally stored artwork and transfer state. */
@Composable
private fun DownloadRow(
    entries: List<DownloadEntry>,
    onItemSelected: (LoomItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.downloads),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(entries, key = { it.item.id }) { entry ->
                DownloadCard(
                    entry = entry,
                    onClick = { onItemSelected(entry.item) },
                    modifier = Modifier.width(140.dp),
                )
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
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    landscape: Boolean = false,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
