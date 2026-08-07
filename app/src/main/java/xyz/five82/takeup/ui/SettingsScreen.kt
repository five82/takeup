package xyz.five82.takeup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.R
import xyz.five82.takeup.data.DownloadEntry
import xyz.five82.takeup.data.formatBytes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsScreen(
    state: MainUiState.Connect,
    downloads: List<DownloadEntry>,
    onServerUrlChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onRemoveDownload: (Long) -> Unit,
    onRemoveAllDownloads: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(enabled = state.canNavigateBack, onBack = onBack)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings),
                    )
                },
                navigationIcon = {
                    if (state.canNavigateBack) {
                        NavigationBackButton(onClick = onBack)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .padding(top = 20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.loom_server),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.connect_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(24.dp))
                    OutlinedTextField(
                        value = state.serverUrl,
                        onValueChange = onServerUrlChanged,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isConnecting,
                        label = { Text(stringResource(R.string.server_url_label)) },
                        placeholder = { Text(stringResource(R.string.server_url_example)) },
                        supportingText = state.error?.let { error ->
                            {
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        isError = state.error != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { if (state.serverUrl.isNotBlank()) onConnect() },
                        ),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onConnect,
                        shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ButtonDefaults.MediumContainerHeight),
                        enabled = state.serverUrl.isNotBlank() && !state.isConnecting,
                        contentPadding = ButtonDefaults.contentPaddingFor(
                            ButtonDefaults.MediumContainerHeight,
                        ),
                    ) {
                        if (state.isConnecting) {
                            LoadingIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.connecting))
                        } else {
                            Text(stringResource(R.string.connect))
                        }
                    }
                }
            }
            if (downloads.isNotEmpty()) {
                DownloadsCard(
                    downloads = downloads,
                    onRemoveDownload = onRemoveDownload,
                    onRemoveAllDownloads = onRemoveAllDownloads,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Storage management for downloaded titles, alongside the Loom server card. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadsCard(
    downloads: List<DownloadEntry>,
    onRemoveDownload: (Long) -> Unit,
    onRemoveAllDownloads: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .padding(top = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.downloads),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )
                TextButton(onClick = onRemoveAllDownloads, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.remove_all_downloads))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.download_summary,
                    pluralStringResource(R.plurals.download_count, downloads.size, downloads.size),
                    formatBytes(downloads.sumOf { it.totalBytes.coerceAtLeast(0) }),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            downloads.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DownloadStateDot(entry)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.item.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = downloadStatusLabel(entry),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    IconButton(onClick = { onRemoveDownload(entry.item.id) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = stringResource(R.string.remove_download),
                        )
                    }
                }
            }
        }
    }
}
