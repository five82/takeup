package xyz.five82.takeup.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.five82.takeup.api.LoomApi
import xyz.five82.takeup.api.ScanStatus
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.data.Reach
import xyz.five82.takeup.ui.NavState
import xyz.five82.takeup.ui.Screen
import xyz.five82.takeup.ui.components.RowLabel
import xyz.five82.takeup.ui.components.threeThreads
import xyz.five82.takeup.ui.formatTimestamp
import xyz.five82.takeup.ui.takeupViewModel
import xyz.five82.takeup.ui.theme.Amber
import xyz.five82.takeup.ui.theme.Ember
import xyz.five82.takeup.ui.theme.Ink
import xyz.five82.takeup.ui.theme.Muted
import xyz.five82.takeup.ui.theme.Teal
import xyz.five82.takeup.ui.theme.Violet

class SettingsViewModel(private val repository: LoomRepository) : ViewModel() {
    var address by mutableStateOf(repository.server.value.address ?: "")
    var saving by mutableStateOf(false)
    var saveResult by mutableStateOf<String?>(null)
    var saveFailed by mutableStateOf(false)
    var scan by mutableStateOf<ScanStatus?>(null)
    var scanError by mutableStateOf<String?>(null)

    fun save() {
        val input = address.trim()
        val normalized = LoomApi.normalizeAddress(input)
        if (normalized == null) {
            saveFailed = true
            saveResult = "Enter an address like 192.168.1.20:8097"
            return
        }
        viewModelScope.launch {
            saving = true
            saveResult = null
            val previous = repository.api.baseUrl
            try {
                repository.api.baseUrl = normalized
                repository.api.health()
                repository.setServerAddress(input)
                saveFailed = false
                saveResult = "Connected"
            } catch (e: Exception) {
                repository.api.baseUrl = previous
                saveFailed = true
                saveResult = "Loom isn't answering at $input"
            } finally {
                saving = false
            }
        }
    }

    fun setAllowCellular(value: Boolean) {
        viewModelScope.launch { repository.network.setAllowCellular(value) }
    }

    fun triggerScan() {
        viewModelScope.launch {
            try {
                repository.api.triggerScan()
                scanError = null
                refreshScan()
            } catch (e: Exception) {
                scanError = e.message
            }
        }
    }

    suspend fun refreshScan() {
        try {
            scan = repository.api.scanStatus()
            scanError = null
        } catch (e: CancellationException) {
            // Unlike every other call in this app, this one is awaited in the
            // polling effect's own coroutine rather than launched into
            // viewModelScope, so it really is cancelled from under us whenever
            // that effect restarts. Reading that as a failed request put the
            // app's own teardown on screen as "Scan status unavailable".
            throw e
        } catch (e: Exception) {
            scanError = e.message
        }
    }
}

@Composable
fun SettingsScreen(repository: LoomRepository, nav: NavState) {
    val model = takeupViewModel { SettingsViewModel(repository) }
    // Poll scan status while the screen is open; it is the only live thing here.
    // Offline there is nothing to poll, and polling anyway is what made the app
    // feel like it expected a server to always be there.
    val online by repository.network.reach.collectAsStateWithLifecycle()
    // Keyed on the verdict rather than on reach itself: only crossing the
    // offline line starts or stops the poll, so the first probe answering -
    // or home settling into remote - no longer restarts the effect and
    // cancels the request already in flight. The loop still reads `online`
    // live, so it stops the moment the app goes offline.
    LaunchedEffect(online != Reach.Offline) {
        while (online != Reach.Offline) {
            model.refreshScan()
            delay(3000)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            // The brand threads as still, dim fields: branded without artwork.
            .threeThreads(listOf(Ember, Teal, Violet))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(bottom = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, end = 20.dp)) {
            IconButton(onClick = { nav.pop() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            Text("Settings", style = MaterialTheme.typography.displaySmall, color = Ink)
        }

        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RowLabel("Server", color = Teal, modifier = Modifier.padding(top = 20.dp))
            OutlinedTextField(
                value = model.address,
                onValueChange = { model.address = it },
                label = { Text("Loom address") },
                singleLine = true,
                isError = model.saveFailed && model.saveResult != null,
                supportingText = model.saveResult?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { model.save() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { model.save() }, enabled = !model.saving && model.address.isNotBlank()) {
                Text(if (model.saving) "Checking..." else "Save")
            }

            RowLabel("Network", color = Ember, modifier = Modifier.padding(top = 24.dp))
            val reach by repository.network.reach.collectAsStateWithLifecycle()
            val reason by repository.network.reason.collectAsStateWithLifecycle()
            val allowCellular by repository.network.allowCellular.collectAsStateWithLifecycle()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when (reach) {
                        Reach.Home -> "Loom is answering on your home network."
                        Reach.Remote -> "Loom is answering through the tunnel."
                        Reach.Offline -> reason
                        Reach.Unknown -> "Checking for Loom..."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (reach == Reach.Offline) Muted else Ink,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                TextButton(onClick = { repository.network.recheck() }) { Text("Check") }
            }
            SettingSwitch(
                title = "Allow cellular data",
                detail = if (allowCellular) {
                    "Streaming and downloads may use the data plan."
                } else {
                    "Off Wi-Fi, only downloaded titles are available."
                },
                checked = allowCellular,
                onCheckedChange = { model.setAllowCellular(it) },
            )

            RowLabel("Library", color = Amber, modifier = Modifier.padding(top = 24.dp))
            val scan = model.scan
            val statusLine = when {
                online == Reach.Offline -> "Scanning needs Loom."
                model.scanError != null -> "Scan status unavailable: ${model.scanError}"
                scan == null -> "Checking scan status..."
                scan.running -> "Scanning ${scan.library?.takeIf { it.isNotEmpty() } ?: "all libraries"}..."
                scan.lastError?.isNotEmpty() == true -> "Last scan failed: ${scan.lastError}"
                scan.lastEndedAt?.isNotEmpty() == true ->
                    "Last scan finished ${formatTimestamp(scan.lastEndedAt)}"
                else -> "No scan has run yet"
            }
            Text(statusLine, style = MaterialTheme.typography.bodyMedium, color = Muted)
            OutlinedButton(
                onClick = { model.triggerScan() },
                enabled = model.scan?.running != true && online != Reach.Offline,
            ) {
                Text("Scan libraries now")
            }

            val downloads by repository.downloads.downloads.collectAsStateWithLifecycle()
            if (downloads.isNotEmpty()) {
                RowLabel("Downloads", color = Violet, modifier = Modifier.padding(top = 24.dp))
                OutlinedButton(onClick = { nav.push(Screen.Downloads) }) {
                    Text("Manage downloads")
                }
            }

            RowLabel("About", modifier = Modifier.padding(top = 24.dp))
            Text(
                "Takeup is the take-up reel on a loom: the beam that winds finished cloth as it is woven.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
        }
    }
}

/** A switch with the sentence that says what it currently means. */
@Composable
private fun SettingSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
