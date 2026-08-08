package xyz.five82.takeup.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import xyz.five82.takeup.TakeupApplication
import xyz.five82.takeup.ui.theme.TakeupTheme

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
                TakeupApp(repository)
            }
        }
    }
}
