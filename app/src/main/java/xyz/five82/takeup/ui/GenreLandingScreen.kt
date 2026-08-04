@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package xyz.five82.takeup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.R
import xyz.five82.takeup.data.LoomItem
import xyz.five82.takeup.ui.theme.heroBottomScrim

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GenreLandingScreen(
    state: MainUiState.GenreLanding,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onItemSelected: (LoomItem) -> Unit,
    onPlayItem: (LoomItem) -> Unit,
) {
    BackHandler(onBack = onBack)
    UseLightStatusBarIcons()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val featured = state.items.firstOrNull { it.backdropUrl(state.serverUrl) != null }
        ?: state.items.firstOrNull()
    Box(Modifier.fillMaxSize()) {
    AmbientGlow(
        url = featured?.let { it.backdropUrl(state.serverUrl) ?: it.posterUrl(state.serverUrl) },
    )
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(
                        visible = scrollBehavior.state.overlappedFraction > 0.01f,
                        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                    ) {
                        Text(
                            text = state.genre.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMediumEmphasized,
                        )
                    }
                },
                navigationIcon = {
                    MediaOverlayIconButton(
                        iconResource = R.drawable.ic_arrow_back,
                        contentDescription = stringResource(R.string.navigate_back),
                        onClick = onBack,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        when {
            state.error != null && state.items.isEmpty() -> FullScreenError(
                message = state.error,
                onRetry = onRetry,
                modifier = Modifier.padding(contentPadding),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 132.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                featured?.let { item ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        GenreHero(
                            serverUrl = state.serverUrl,
                            genreName = state.genre.name,
                            itemCount = state.items.size,
                            item = item,
                            onPlay = { onPlayItem(item) },
                            onDetails = { onItemSelected(item) },
                        )
                    }
                }
                state.error?.let { error ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ErrorCard(message = error, onRetry = onRetry)
                    }
                }
                if (state.isLoading && state.items.isEmpty()) {
                    items(6) { PosterCardPlaceholder() }
                } else if (state.items.isEmpty() && state.error == null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(R.string.no_movies_for_genre),
                            modifier = Modifier
                                .padding(contentPadding)
                                .padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.items, key = { it.id }) { item ->
                    MediaCard(
                        serverUrl = state.serverUrl,
                        item = item,
                        onClick = { onItemSelected(item) },
                        modifier = Modifier.fillMaxWidth(),
                        sharedArtwork = true,
                    )
                }
            }
        }
    }
    }
}

/** Featured title for the genre: full-bleed backdrop with the hero CTA pair. */
@Composable
private fun GenreHero(
    serverUrl: String,
    genreName: String,
    itemCount: Int,
    item: LoomItem,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 11f),
    ) {
        FadingBackdropArtwork(
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.title_count,
                        itemCount,
                        itemCount,
                    ).let { "$genreName · $it" },
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.headlineMediumEmphasized,
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
