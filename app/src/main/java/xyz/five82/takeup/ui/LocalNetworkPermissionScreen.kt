package xyz.five82.takeup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xyz.five82.takeup.ui.components.Selvedge
import xyz.five82.takeup.ui.components.threeThreads
import xyz.five82.takeup.ui.theme.Ember
import xyz.five82.takeup.ui.theme.Ink
import xyz.five82.takeup.ui.theme.Muted
import xyz.five82.takeup.ui.theme.Stage
import xyz.five82.takeup.ui.theme.Teal
import xyz.five82.takeup.ui.theme.Violet

/** Android 17 blocks LAN sockets until the user grants this runtime permission. */
@Composable
fun LocalNetworkPermissionScreen(wasDenied: Boolean, onGrantAccess: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Stage)
            .threeThreads(listOf(Ember, Teal, Violet), drifting = true)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Selvedge(Modifier.width(96.dp), height = 4f)
        Text(
            "Local network access",
            style = MaterialTheme.typography.displaySmall,
            color = Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            "Takeup needs access to your local network to connect to Loom.",
            style = MaterialTheme.typography.bodyLarge,
            color = Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (wasDenied) {
            Text(
                "Access is off. Turn on Local network in Takeup's app settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Button(
            onClick = onGrantAccess,
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(if (wasDenied) "Open settings" else "Allow access")
        }
    }
}
