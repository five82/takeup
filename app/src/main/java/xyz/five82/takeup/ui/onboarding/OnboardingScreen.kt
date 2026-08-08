package xyz.five82.takeup.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.launch
import xyz.five82.takeup.api.LoomApi
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.ui.components.Selvedge
import xyz.five82.takeup.ui.takeupViewModel
import xyz.five82.takeup.ui.theme.Ink
import xyz.five82.takeup.ui.theme.Muted
import xyz.five82.takeup.ui.theme.Stage

class OnboardingViewModel(private val repository: LoomRepository) : ViewModel() {
    var address by mutableStateOf("")
    var checking by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun connect() {
        val input = address.trim()
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
            } catch (e: Exception) {
                repository.api.baseUrl = null
                error = "Loom isn't answering at $input"
            } finally {
                checking = false
            }
        }
    }
}

/**
 * First run: the app needs exactly one thing, a server address. Multicast
 * does not cross the emulator NAT, so manual entry is the primary path.
 */
@Composable
fun OnboardingScreen(repository: LoomRepository) {
    val model = takeupViewModel { OnboardingViewModel(repository) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Stage)
            .imePadding()
            .padding(horizontal = 32.dp),
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
            modifier = Modifier.padding(top = 4.dp, bottom = 40.dp),
        )
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
