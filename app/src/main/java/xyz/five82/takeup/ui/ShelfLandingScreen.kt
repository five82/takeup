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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.R
import xyz.five82.takeup.data.LoomItem

/**
 * A named shelf of movies: an identity header over the complete grid. Genres
 * and collections differ only in where the list comes from, so both land here.
 * The header carries the shelf's own color-and-motif identity (the same one
 * its card wears) rather than featuring one of its titles - a featured title
 * duplicated a movie the grid already showed one row further down.
 *
 * A collection arrives complete with the home load and cannot fail, which is why
 * the loading and error parameters default to having nothing to say.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShelfLandingScreen(
    serverUrl: String,
    title: String,
    items: List<LoomItem>,
    emptyMessage: String,
    onBack: () -> Unit,
    onItemSelected: (LoomItem) -> Unit,
    isLoading: Boolean = false,
    error: String? = null,
    onRetry: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    UseLightStatusBarIcons()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Box(Modifier.fillMaxSize()) {
    AmbientGlow()
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
                            text = title,
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
            error != null && items.isEmpty() -> FullScreenError(
                message = error,
                onRetry = onRetry,
                modifier = Modifier.padding(contentPadding),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 132.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ShelfHeader(title = title, itemCount = items.size)
                }
                error?.let { message ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ErrorCard(message = message, onRetry = onRetry)
                    }
                }
                if (isLoading && items.isEmpty()) {
                    items(6) { PosterCardPlaceholder() }
                } else if (items.isEmpty() && error == null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = emptyMessage,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(items, key = { it.id }) { item ->
                    MediaCard(
                        serverUrl = serverUrl,
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

/**
 * The shelf's identity writ large: the same color and motif its card wears,
 * with the name in the heaviest weight on the screen. No artwork - the grid
 * below is all artwork, and the header's job is to say where you are.
 */
@Composable
private fun ShelfHeader(
    title: String,
    itemCount: Int,
) {
    val identity = shelfIdentity(title)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = identity.color,
        contentColor = Color.White,
    ) {
        Box(
            modifier = Modifier
                .height(148.dp)
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
                    .offset(x = 40.dp, y = (-40).dp)
                    .size(164.dp)
                    .rotate(-15f)
                    .background(Color.White.copy(alpha = 0.18f), identity.motif.toShape()),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.headlineLargeEmphasized,
                )
                Text(
                    text = pluralStringResource(R.plurals.title_count, itemCount, itemCount),
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
