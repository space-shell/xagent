package sh.paseochat.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import sh.paseochat.launcher.service.EXTRA_ATTENTION_AGENT_ID
import sh.paseochat.launcher.ui.screens.LauncherScreen
import sh.paseochat.launcher.ui.theme.PaseoTheme

class MainActivity : ComponentActivity() {

    private var attentionAgentId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        attentionAgentId = intent?.getStringExtra(EXTRA_ATTENTION_AGENT_ID)
        setContent {
            PaseoTheme {
                LauncherScreen(attentionAgentId = attentionAgentId)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        attentionAgentId = intent.getStringExtra(EXTRA_ATTENTION_AGENT_ID)
    }
}
