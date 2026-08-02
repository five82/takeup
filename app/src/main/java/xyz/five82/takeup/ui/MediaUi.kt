package xyz.five82.takeup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import xyz.five82.takeup.R

@Composable
internal fun NavigationBackButton(
    onClick: () -> Unit,
    tint: Color = LocalContentColor.current,
) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = stringResource(R.string.navigate_back),
            tint = tint,
        )
    }
}

@Composable
internal fun MediaArtwork(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_image_placeholder),
            contentDescription = null,
            modifier = Modifier.size(42.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        )
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun WatchedBadge(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.watched)
    Surface(
        modifier = modifier.semantics { contentDescription = description },
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = CircleShape,
        shadowElevation = 2.dp,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            modifier = Modifier
                .padding(4.dp)
                .size(16.dp),
        )
    }
}

@Composable
internal fun PosterCardPlaceholder(modifier: Modifier = Modifier) {
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(placeholder),
        )
        Box(
            Modifier
                .fillMaxWidth(0.82f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(placeholder),
        )
        Box(
            Modifier
                .fillMaxWidth(0.48f)
                .height(11.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(placeholder.copy(alpha = 0.75f)),
        )
    }
}

@Composable
internal fun EpisodeCardPlaceholder(modifier: Modifier = Modifier) {
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .width(128.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(placeholder),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.35f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(placeholder),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(placeholder),
            )
            Box(
                Modifier
                    .fillMaxWidth(0.72f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(placeholder.copy(alpha = 0.75f)),
            )
        }
    }
}
