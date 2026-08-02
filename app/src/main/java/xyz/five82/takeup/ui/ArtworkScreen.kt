package xyz.five82.takeup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.R
import xyz.five82.takeup.data.ArtworkKind
import xyz.five82.takeup.data.ArtworkOption
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArtworkScreen(
    state: MainUiState.Artwork,
    onBack: () -> Unit,
    onKindSelected: (ArtworkKind) -> Unit,
    onOptionSelected: (ArtworkOption) -> Unit,
    onReset: () -> Unit,
    onRetry: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val kinds = if (state.item.kind == "season") {
        listOf(ArtworkKind.POSTER)
    } else {
        ArtworkKind.entries
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.artwork_title, state.item.title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { NavigationBackButton(onClick = onBack) },
                actions = {
                    TextButton(
                        onClick = onReset,
                        enabled = state.options.isNotEmpty() && !state.isLoading && !state.isSaving,
                    ) {
                        Text(stringResource(R.string.reset_artwork))
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            if (kinds.size > 1) {
                PrimaryTabRow(selectedTabIndex = kinds.indexOf(state.kind)) {
                    kinds.forEach { kind ->
                        Tab(
                            selected = state.kind == kind,
                            onClick = { onKindSelected(kind) },
                            enabled = !state.isSaving,
                            text = { Text(stringResource(kind.labelResource())) },
                        )
                    }
                }
            }
            if (state.isLoading || state.isSaving) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            state.error?.let { error ->
                Surface(
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = onRetry, enabled = !state.isSaving) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
            when {
                state.options.isNotEmpty() -> ArtworkGrid(
                    kind = state.kind,
                    options = state.options,
                    enabled = !state.isLoading && !state.isSaving,
                    onOptionSelected = onOptionSelected,
                )
                !state.isLoading && state.error == null -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.no_artwork_options),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtworkGrid(
    kind: ArtworkKind,
    options: List<ArtworkOption>,
    enabled: Boolean,
    onOptionSelected: (ArtworkOption) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = options,
            key = { "${it.provider}:${it.providerPath}" },
        ) { option ->
            ArtworkOptionCard(
                kind = kind,
                option = option,
                enabled = enabled && !option.selected,
                onClick = { onOptionSelected(option) },
            )
        }
    }
}

@Composable
private fun ArtworkOptionCard(
    kind: ArtworkKind,
    option: ArtworkOption,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        border = if (option.selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        colors = CardDefaults.cardColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Box {
            MediaArtwork(
                url = option.thumbnailUrl,
                contentDescription = stringResource(kind.descriptionResource()),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(kind.aspectRatio())
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
            )
            if (option.selected) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = stringResource(R.string.selected_artwork),
                        modifier = Modifier.padding(6.dp),
                    )
                }
            }
        }
        val details = listOfNotNull(
            if (option.width > 0 && option.height > 0) "${option.width} x ${option.height}" else null,
            option.language.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT),
        ).joinToString(" \u00B7 ")
        if (details.isNotBlank()) {
            Text(
                text = details,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private fun ArtworkKind.labelResource(): Int = when (this) {
    ArtworkKind.POSTER -> R.string.poster
    ArtworkKind.BACKDROP -> R.string.backdrop
    ArtworkKind.LOGO -> R.string.logo
}

private fun ArtworkKind.descriptionResource(): Int = when (this) {
    ArtworkKind.POSTER -> R.string.poster_artwork
    ArtworkKind.BACKDROP -> R.string.backdrop_artwork
    ArtworkKind.LOGO -> R.string.logo_artwork
}

private fun ArtworkKind.aspectRatio(): Float = when (this) {
    ArtworkKind.POSTER -> 2f / 3f
    ArtworkKind.BACKDROP -> 16f / 9f
    ArtworkKind.LOGO -> 5f / 2f
}
