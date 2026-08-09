package sh.paseochat.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import sh.paseochat.launcher.ui.screens.LauncherScreen
import sh.paseochat.launcher.ui.theme.PaseoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PaseoTheme {
                LauncherScreen()
            }
        }
    }
}
