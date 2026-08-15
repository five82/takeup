package xyz.five82.takeup.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import xyz.five82.takeup.TakeupApplication
import xyz.five82.takeup.data.LoomRepository
import xyz.five82.takeup.ui.theme.TakeupTheme

private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val repository = (application as TakeupApplication).repository
        setContent {
            TakeupTheme {
                PermissionAwareApp(repository)
            }
        }
    }
}

@Composable
private fun PermissionAwareApp(repository: LoomRepository) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionRequired = Build.VERSION.SDK_INT >= 37
    var permissionGranted by remember {
        mutableStateOf(
            !permissionRequired ||
                ContextCompat.checkSelfPermission(context, LOCAL_NETWORK_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        permissionDenied = !granted
    }

    // A grant made from Android's app settings does not return a launcher result.
    DisposableEffect(context, lifecycleOwner, permissionRequired) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && permissionRequired) {
                permissionGranted = ContextCompat.checkSelfPermission(
                    context,
                    LOCAL_NETWORK_PERMISSION,
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (permissionGranted) {
        // No scheduler runs while the process is dead; pick interrupted downloads
        // back up only once LAN access is available.
        LaunchedEffect(repository) { repository.resumeDownloads() }
        TakeupApp(repository)
    } else {
        LocalNetworkPermissionScreen(
            wasDenied = permissionDenied,
            onGrantAccess = {
                if (permissionDenied) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        },
                    )
                } else {
                    permissionLauncher.launch(LOCAL_NETWORK_PERMISSION)
                }
            },
        )
    }
}
