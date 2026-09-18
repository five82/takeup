package xyz.five82.takeup.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import xyz.five82.takeup.data.DownloadEntry
import xyz.five82.takeup.data.DownloadResult
import xyz.five82.takeup.data.DownloadState
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.data.Reach
import xyz.five82.takeup.data.downloadProgressFraction
import xyz.five82.takeup.data.downloadStatusLabel
import xyz.five82.takeup.data.downloadSummary
import xyz.five82.takeup.data.formatBytes
import xyz.five82.takeup.ui.DownloadIcon
import xyz.five82.takeup.ui.NavState
import xyz.five82.takeup.ui.components.EmptyState
import xyz.five82.takeup.ui.components.RowLabel
import xyz.five82.takeup.ui.components.ThreadProgress
import xyz.five82.takeup.ui.components.threeThreads
import xyz.five82.takeup.ui.takeupViewModel
import xyz.five82.takeup.ui.theme.Ember
import xyz.five82.takeup.ui.theme.Ink
import xyz.five82.takeup.ui.theme.Muted
import xyz.five82.takeup.ui.theme.Surface1
import xyz.five82.takeup.ui.theme.Teal
import xyz.five82.takeup.ui.theme.Violet

private class DownloadsViewModel(private val repository: LoomRepository) : ViewModel() {
    var message by mutableStateOf<String?>(null)
        private set

    fun retry(itemId: Long) {
        viewModelScope.launch {
            message = null
            try {
                if (repository.startDownload(itemId) == DownloadResult.NotEnoughSpace) {
                    message = "Not enough free space for this file"
                }
            } catch (e: Exception) {
                message = e.message ?: "Download failed to start"
            }
        }
    }
}

/** A local-first inventory: all management remains available when Loom is offline. */
@Composable
fun DownloadsScreen(repository: LoomRepository, nav: NavState) {
    val model = takeupViewModel { DownloadsViewModel(repository) }
    val downloads by repository.downloads.downloads.collectAsStateWithLifecycle()
    val reach by repository.network.reach.collectAsStateWithLifecycle()
    var removeEntry by remember { mutableStateOf<DownloadEntry?>(null) }
    var confirmRemoveAll by remember { mutableStateOf(false) }
    val active = downloads.filter { it.state != DownloadState.Completed }
    val completed = downloads.filter { it.state == DownloadState.Completed }
    val summary = downloadSummary(downloads)

    Column(
        Modifier
            .fillMaxSize()
            .threeThreads(listOf(Violet, Teal, Ember))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp, end = 20.dp),
        ) {
            IconButton(onClick = { nav.pop() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            Text("Downloads", style = MaterialTheme.typography.displaySmall, color = Ink)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            DownloadSummary(summary.activeCount, summary.completedCount, summary.completedBytes, repository.downloads.usableSpaceBytes())
            model.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            if (downloads.isEmpty()) {
                EmptyState("No downloads on this device.")
            } else {
                if (active.isNotEmpty()) {
                    RowLabel("Active", color = Ember, modifier = Modifier.padding(top = 16.dp))
                    active.forEach { entry ->
                        DownloadRow(
                            entry = entry,
                            retryEnabled = reach != Reach.Offline,
                            onCancel = { repository.downloads.remove(entry.item.id) },
                            onRetry = { model.retry(entry.item.id) },
                            onRemove = { removeEntry = entry },
                        )
                    }
                }
                if (completed.isNotEmpty()) {
                    RowLabel("On this device", color = Violet, modifier = Modifier.padding(top = 16.dp))
                    completed.forEach { entry ->
                        DownloadRow(
                            entry = entry,
                            retryEnabled = false,
                            onCancel = {},
                            onRetry = {},
                            onRemove = { removeEntry = entry },
                        )
                    }
                }
                OutlinedButton(
                    onClick = { confirmRemoveAll = true },
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text("Remove all downloads")
                }
            }
        }
    }

    removeEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { removeEntry = null },
            title = { Text("Remove download?") },
            text = {
                Text(
                    "\"${entry.item.title}\" (${formatBytes(entry.totalBytes.takeIf { it > 0 } ?: entry.bytesDownloaded)}) " +
                        "will no longer play offline.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    removeEntry = null
                    repository.downloads.remove(entry.item.id)
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { removeEntry = null }) { Text("Keep") }
            },
        )
    }
    if (confirmRemoveAll) {
        AlertDialog(
            onDismissRequest = { confirmRemoveAll = false },
            title = { Text("Remove all downloads?") },
            text = {
                Text(
                    "${downloads.size} ${itemNoun(downloads.size)} (${formatBytes(downloads.sumOf { it.bytesDownloaded })}) " +
                        "will be removed. Nothing will play offline until something is downloaded again.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemoveAll = false
                    repository.downloads.removeAll()
                }) { Text("Remove all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveAll = false }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun DownloadSummary(active: Int, completed: Int, usedBytes: Long, freeBytes: Long) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(
            "$active active · $completed on this device",
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
        )
        Text(
            "${formatBytes(usedBytes)} used · ${formatBytes(freeBytes)} free",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun DownloadRow(
    entry: DownloadEntry,
    retryEnabled: Boolean,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val failed = entry.state == DownloadState.Failed
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 44.dp, height = 66.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Surface1),
        ) {
            if (entry.posterPath != null) {
                AsyncImage(
                    model = entry.posterPath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    if (entry.state == DownloadState.Completed) Icons.Filled.Check else DownloadIcon,
                    contentDescription = null,
                    tint = if (failed) MaterialTheme.colorScheme.error else Muted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp, end = 8.dp)) {
            Text(
                entry.item.title,
                style = MaterialTheme.typography.titleSmall,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                downloadStatusLabel(entry),
                style = MaterialTheme.typography.labelSmall,
                color = if (failed) MaterialTheme.colorScheme.error else Muted,
                modifier = Modifier.padding(top = 1.dp),
            )
            if (entry.state == DownloadState.Downloading || entry.state == DownloadState.Queued) {
                ThreadProgress(
                    downloadProgressFraction(entry),
                    Ember,
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
        when (entry.state) {
            DownloadState.Queued, DownloadState.Downloading -> TextButton(onClick = onCancel) { Text("Cancel") }
            DownloadState.Failed -> TextButton(onClick = onRetry, enabled = retryEnabled) { Text("Retry") }
            DownloadState.Completed -> TextButton(onClick = onRemove) { Text("Remove") }
            DownloadState.Removing -> Unit
        }
    }
}

private fun itemNoun(count: Int): String = if (count == 1) "item" else "items"
