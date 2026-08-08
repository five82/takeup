package xyz.five82.takeup.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.five82.takeup.api.LoomApi
import xyz.five82.takeup.api.ScanStatus
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.ui.NavState
import xyz.five82.takeup.ui.components.RowLabel
import xyz.five82.takeup.ui.takeupViewModel
import xyz.five82.takeup.ui.theme.Amber
import xyz.five82.takeup.ui.theme.Ink
import xyz.five82.takeup.ui.theme.Muted
import xyz.five82.takeup.ui.theme.Stage
import xyz.five82.takeup.ui.theme.Teal

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
        } catch (e: Exception) {
            scanError = e.message
        }
    }
}

@Composable
fun SettingsScreen(repository: LoomRepository, nav: NavState) {
    val model = takeupViewModel { SettingsViewModel(repository) }

    // Poll scan status while the screen is open; it is the only live thing here.
    LaunchedEffect(Unit) {
        while (true) {
            model.refreshScan()
            delay(3000)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Stage)
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

            RowLabel("Library", color = Amber, modifier = Modifier.padding(top = 24.dp))
            val scan = model.scan
            val statusLine = when {
                model.scanError != null -> "Scan status unavailable: ${model.scanError}"
                scan == null -> "Checking scan status..."
                scan.running -> "Scanning ${scan.library?.takeIf { it.isNotEmpty() } ?: "all libraries"}..."
                scan.lastError?.isNotEmpty() == true -> "Last scan failed: ${scan.lastError}"
                scan.lastEndedAt?.isNotEmpty() == true -> "Last scan finished ${scan.lastEndedAt}"
                else -> "No scan has run yet"
            }
            Text(statusLine, style = MaterialTheme.typography.bodyMedium, color = Muted)
            OutlinedButton(
                onClick = { model.triggerScan() },
                enabled = model.scan?.running != true,
            ) {
                Text("Scan libraries now")
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
