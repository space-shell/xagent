package sh.paseochat.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sh.paseochat.launcher.ui.components.AgentCard
import sh.paseochat.launcher.ui.components.AgentSession
import sh.paseochat.launcher.ui.components.AgentState
import sh.paseochat.launcher.ui.theme.PaseoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PaseoTheme {
                LauncherScreen(stubSessions())
            }
        }
    }
}

@Composable
fun LauncherScreen(sessions: List<AgentSession>) {
    Scaffold { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(sessions, key = { it.id }) { session ->
                AgentCard(session)
            }
        }
    }
}

private fun stubSessions(): List<AgentSession> = listOf(
    AgentSession(
        id = "1",
        title = "Refactor auth module",
        provider = "claude",
        model = "opus-4.6",
        state = AgentState.Running,
        summary = "Splitting AuthController into per-concern files; adding tests for token-refresh.",
        progress = 0.62f,
    ),
    AgentSession(
        id = "2",
        title = "Add retry on 429",
        provider = "codex",
        model = "gpt-5.4",
        state = AgentState.AwaitingInput,
        summary = "Which backoff ceiling do you want — 30s or 120s?",
    ),
    AgentSession(
        id = "3",
        title = "Generate fixtures",
        provider = "opencode",
        model = "glm-5.2",
        state = AgentState.Done,
        summary = "Wrote 40 fixtures into tests/fixtures/ and updated the snapshot index.",
    ),
    AgentSession(
        id = "4",
        title = "Bump deps + renovate",
        provider = "codex",
        model = "gpt-5.4",
        state = AgentState.Queued,
        summary = "Waiting for a free slot — one agent is already running on this provider.",
    ),
    AgentSession(
        id = "5",
        title = "Triage CI failures",
        provider = "copilot",
        model = "gpt-5",
        state = AgentState.Error,
        summary = "Exited 1 after 42s: could not resolve host github.com (network was blocked).",
    ),
    AgentSession(
        id = "6",
        title = "New session",
        provider = "claude",
        model = "opus-4.6",
        state = AgentState.Idle,
        summary = "Agent ready. Hold the button to give it a task.",
    ),
)
