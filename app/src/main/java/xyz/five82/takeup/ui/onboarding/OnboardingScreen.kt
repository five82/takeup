package xyz.five82.takeup.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import xyz.five82.takeup.api.LoomApi
import xyz.five82.takeup.data.DiscoveredLoom
import xyz.five82.takeup.data.LoomDiscovery
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.ui.components.Selvedge
import xyz.five82.takeup.ui.components.threeThreads
import xyz.five82.takeup.ui.takeupViewModel
import xyz.five82.takeup.ui.theme.Ember
import xyz.five82.takeup.ui.theme.Ink
import xyz.five82.takeup.ui.theme.Muted
import xyz.five82.takeup.ui.theme.Stage
import xyz.five82.takeup.ui.theme.Teal
import xyz.five82.takeup.ui.theme.Violet

class OnboardingViewModel(
    private val repository: LoomRepository,
    private val discovery: LoomDiscovery,
) : ViewModel() {
    var address by mutableStateOf("")
    var checking by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var discovered by mutableStateOf<List<DiscoveredLoom>>(emptyList())
    var discoveryFailed by mutableStateOf(false)

    init {
        discovery.start(
            onUpdate = { discovered = it },
            onFailure = { discoveryFailed = true },
        )
    }

    fun connect(candidate: String = address) {
        val input = candidate.trim()
        address = input
        val normalized = LoomApi.normalizeAddress(input)
        if (normalized == null) {
            error = "Enter an address like 192.168.1.20:8097"
            return
        }
        viewModelScope.launch {
            checking = true
            error = null
            try {
                repository.api.baseUrl = normalized
                repository.api.health()
                repository.setServerAddress(input)
                discovery.stop()
            } catch (e: Exception) {
                repository.api.baseUrl = null
                error = "Loom isn't answering at $input"
            } finally {
                checking = false
            }
        }
    }

    fun stopDiscovery() = discovery.stop()

    override fun onCleared() {
        discovery.stop()
    }
}

/** First run: discover Loom on the LAN, with manual entry as a fallback. */
@Composable
fun OnboardingScreen(repository: LoomRepository) {
    val context = LocalContext.current
    val model = takeupViewModel {
        OnboardingViewModel(repository, LoomDiscovery(context.applicationContext))
    }
    DisposableEffect(model) {
        onDispose { model.stopDiscovery() }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Stage)
            // First run breathes in the brand threads; every screen after
            // this takes its color from the library instead.
            .threeThreads(listOf(Ember, Teal, Violet), drifting = true)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Selvedge(Modifier.width(96.dp), height = 4f)
        Text(
            "Takeup",
            style = MaterialTheme.typography.displayLarge,
            color = Ink,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            "a client for Loom",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            modifier = Modifier.padding(top = 4.dp, bottom = 36.dp),
        )

        if (model.discovered.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 24.dp),
            ) {
                if (!model.discoveryFailed) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Teal)
                }
                Text(
                    if (model.discoveryFailed) "Automatic discovery unavailable" else "Looking for Loom on your network...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
        } else {
            Text(
                "Available on your network",
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
            model.discovered.forEach { server ->
                OutlinedButton(
                    onClick = { model.connect(server.address) },
                    enabled = !model.checking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(server.name)
                        Text(
                            server.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                }
            }
            Text(
                "or enter an address",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
        }

        OutlinedTextField(
            value = model.address,
            onValueChange = { model.address = it },
            label = { Text("Server address") },
            placeholder = { Text("192.168.1.20:8097") },
            singleLine = true,
            isError = model.error != null,
            supportingText = model.error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { model.connect() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { model.connect() },
            enabled = !model.checking && model.address.isNotBlank(),
            modifier = Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (model.checking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Connect")
            }
        }
    }
}
