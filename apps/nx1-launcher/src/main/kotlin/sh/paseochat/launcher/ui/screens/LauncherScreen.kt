package sh.paseochat.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import sh.paseochat.launcher.ui.components.AgentCard
import sh.paseochat.launcher.voice.rememberVoiceController
import sh.paseochat.launcher.ui.components.AgentSession
import sh.paseochat.launcher.ui.components.AgentMode
import sh.paseochat.launcher.ui.components.AgentState
import sh.paseochat.launcher.ui.components.ConnectionState
import sh.paseochat.launcher.ui.components.SettingsCard
import sh.paseochat.launcher.ui.components.stateDotColor
import sh.paseochat.launcher.ui.theme.PaseoTheme

private val DECK_CARD_HEIGHT = 360.dp
private val FAN_STEP = 32.dp
private val BELOW_STEP = 480.dp
private const val Z_PER_RANK = 0.20f

@Composable
fun LauncherScreen() {
    val sessions = remember { mutableStateListOf(*stubSessions().toTypedArray()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val voice = rememberVoiceController()
    val context = LocalContext.current
    var listeningId by remember { mutableStateOf<String?>(null) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingListenId by remember { mutableStateOf<String?>(null) }

    var settingsHost by remember { mutableStateOf("100.127.193.39:6767") }
    var settingsPassword by remember { mutableStateOf("") }
    var connectionState by remember { mutableStateOf(ConnectionState.Disconnected) }

    LaunchedEffect(Unit) {
        voice.onError = { msg ->
            listeningId = null
            scope.launch { snackbarHostState.showSnackbar(msg) }
        }
    }

    fun startListening(sessionId: String) {
        voice.onFinal = { text ->
            val idx = sessions.indexOfFirst { it.id == sessionId }
            if (idx >= 0) sessions[idx] = sessions[idx].copy(userInput = text)
            listeningId = null
            voice.reset()
        }
        voice.start()
        if (voice.error != null) {
            listeningId = null
        } else {
            listeningId = sessionId
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasMicPermission = granted
        val pid = pendingListenId
        pendingListenId = null
        if (granted && pid != null) {
            startListening(pid)
        } else if (!granted) {
            scope.launch { snackbarHostState.showSnackbar("Microphone permission denied") }
        }
    }

    val pagerState = rememberPagerState(pageCount = { sessions.size + 1 })

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        if (sessions.isEmpty()) {
            EmptyState(Modifier.padding(padding))
        } else {
            BoxWithConstraints(modifier = Modifier.padding(padding)) {
                val viewportHeight = maxHeight
                val focusedTop = (viewportHeight - DECK_CARD_HEIGHT) / 2f
                VerticalPager(
                    state = pagerState,
                    pageSize = PageSize.Fill,
                    pageSpacing = 0.dp,
                    contentPadding = PaddingValues(0.dp),
                    beyondViewportPageCount = 3,
                    key = { if (it < sessions.size) sessions[it].id else "__settings__" },
                ) { pageIndex ->
                    val isSettings = pageIndex == sessions.size
                    val o = pagerState.getOffsetDistanceInPages(pageIndex)
                    val k = abs(o)
                    val isPeek = o <= 0f
                    val ty = if (isPeek) focusedTop - FAN_STEP * k else focusedTop + BELOW_STEP * o
                    val cardAlpha = if (isPeek) (4f + o).coerceIn(0f, 1f) else 1f
                    val z =
                        if (isPeek) 1f - Z_PER_RANK * k else (1f - 0.5f * o).coerceIn(0f, 1f)

                    Box(Modifier.fillMaxSize().zIndex(z)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(DECK_CARD_HEIGHT)
                                .padding(start = 40.dp, end = 16.dp)
                                .graphicsLayer {
                                    val naturalY = o * viewportHeight.toPx()
                                    translationY = ty.toPx() - naturalY
                                    alpha = cardAlpha
                                },
                        ) {
                            if (isSettings) {
                                SettingsCard(
                                    host = settingsHost,
                                    onHostChange = { settingsHost = it },
                                    password = settingsPassword,
                                    onPasswordChange = { settingsPassword = it },
                                    connectionState = connectionState,
                                    onConnect = {
                                        connectionState = ConnectionState.Connected
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Connected to $settingsHost")
                                        }
                                    },
                                    onDisconnect = {
                                        connectionState = ConnectionState.Disconnected
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                val session = sessions[pageIndex]
                                AgentCard(
                                    session,
                                    listening = listeningId == session.id,
                                    partialText = if (listeningId == session.id) voice.partialText else "",
                                    onMicDown = {
                                        if (!voice.isListening) {
                                            if (hasMicPermission) {
                                                startListening(session.id)
                                            } else {
                                                pendingListenId = session.id
                                                permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                    },
                                    onMicUp = {
                                        if (listeningId == session.id) voice.stop()
                                    },
                                    onCycleMode = {
                                        val idx = sessions.indexOfFirst { it.id == session.id }
                                        if (idx >= 0) {
                                            val newMode = if (sessions[idx].mode == AgentMode.Plan)
                                                AgentMode.Build else AgentMode.Plan
                                            sessions[idx] = sessions[idx].copy(mode = newMode)
                                            scope.launch {
                                                snackbarHostState.showSnackbar("${newMode.name.lowercase()} mode")
                                            }
                                        }
                                    },
                                    onApprove = {
                                        val idx = sessions.indexOfFirst { it.id == session.id }
                                        if (idx >= 0) {
                                            sessions[idx] = sessions[idx].copy(state = AgentState.Running)
                                        }
                                    },
                                    onApproveAlways = {
                                        val idx = sessions.indexOfFirst { it.id == session.id }
                                        if (idx >= 0) {
                                            sessions[idx] = sessions[idx].copy(state = AgentState.Running)
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Allowed always \u2014 future requests auto-approved")
                                            }
                                        }
                                    },
                                    onDeny = {
                                        val idx = sessions.indexOfFirst { it.id == session.id }
                                        if (idx >= 0) {
                                            sessions[idx] = sessions[idx].copy(state = AgentState.Idle)
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }

                StatusRail(
                    sessions = sessions,
                    currentIndex = pagerState.currentPage,
                    onTap = { idx -> scope.launch { pagerState.animateScrollToPage(idx) } },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 6.dp)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun StatusRail(
    sessions: List<AgentSession>,
    currentIndex: Int,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        sessions.forEachIndexed { index, session ->
            val focused = index == currentIndex
            val color = stateDotColor(session.state)
            val shape = if (focused) RoundedCornerShape(50) else CircleShape
            Box(
                modifier = Modifier
                    .pointerInput(index) { detectTapGestures { onTap(index) } }
                    .width(24.dp)
                    .height(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(8.dp)
                        .height(if (focused) 24.dp else 8.dp)
                        .background(color, shape),
                )
            }
        }
        val settingsFocused = sessions.size == currentIndex
        val settingsShape = if (settingsFocused) RoundedCornerShape(50) else CircleShape
        Box(
            modifier = Modifier
                .pointerInput(sessions.size) { detectTapGestures { onTap(sessions.size) } }
                .width(24.dp)
                .height(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(8.dp)
                    .height(if (settingsFocused) 24.dp else 8.dp)
                    .background(cs.outline, settingsShape),
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "Hold the button to start.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, widthDp = 270, heightDp = 584, name = "NX1 \u2014 roller deck")
@Composable
private fun LauncherDeckPreview() {
    PaseoTheme(darkTheme = false) { LauncherScreen() }
}

private fun stubSessions(): List<AgentSession> = listOf(
    AgentSession("1", "Refactor auth module", "claude", "opus-4.6", AgentState.Running,
        "Splitting AuthController into per-concern files; adding tests for token-refresh."),
    AgentSession("2", "Add retry on 429", "codex", "gpt-5.4", AgentState.AwaitingInput,
        "Wants to run `npm install` in /repo to add the retry dependency."),
    AgentSession("3", "Generate fixtures", "opencode", "glm-5.2", AgentState.Done,
        "Wrote 40 fixtures into tests/fixtures/ and updated the snapshot index."),
    AgentSession("4", "Bump deps + renovate", "codex", "gpt-5.4", AgentState.Queued,
        "Waiting for a free slot \u2014 one agent is already running on this provider."),
    AgentSession("5", "Triage CI failures", "copilot", "gpt-5", AgentState.Error,
        "Exited 1 after 42s: could not resolve host github.com (network was blocked).",
        mode = AgentMode.Plan),
    AgentSession("6", "New session", "claude", "opus-4.6", AgentState.Idle,
        "Agent ready. Hold the button to give it a task."),
    AgentSession("7", "Migrate to flake-parts", "claude", "opus-4.6", AgentState.Running,
        "Converting the devshell to flake-parts modules; verifying nix develop.",
        mode = AgentMode.Plan),
    AgentSession("8", "Write migration guide", "opencode", "glm-5.2", AgentState.Done,
        "Drafted docs/migrate.md covering the move and rollback steps."),
    AgentSession("9", "Fix off-by-one in pager", "codex", "gpt-5.4", AgentState.AwaitingInput,
        "Wants to overwrite src/config.ts with the corrected page count."),
    AgentSession("10", "Seed test DB", "copilot", "gpt-5", AgentState.Queued,
        "Queued behind the auth refactor; starts when CPU frees up."),
)
