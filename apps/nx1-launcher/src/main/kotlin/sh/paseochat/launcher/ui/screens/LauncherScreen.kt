package sh.paseochat.launcher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
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
import sh.paseochat.launcher.ui.components.AgentState
import sh.paseochat.launcher.ui.components.stateDotColor
import sh.paseochat.launcher.ui.theme.PaseoTheme

private val DECK_CARD_HEIGHT = 360.dp
private val FAN_STEP = 32.dp
private val BELOW_STEP = 480.dp
private const val Z_PER_RANK = 0.20f

@Composable
fun LauncherScreen() {
    val sessions = remember { mutableStateListOf(*stubSessions().toTypedArray()) }
    var detailId by remember { mutableStateOf<String?>(null) }
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

    val detail = detailId?.let { id -> sessions.firstOrNull { it.id == id } }
    BackHandler(enabled = detail != null) { detailId = null }

    if (detail != null) {
        AgentDetail(detail)
        return
    }

    val pagerState = rememberPagerState(pageCount = { sessions.size })

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
                    key = { sessions[it].id },
                ) { pageIndex ->
                    val session = sessions[pageIndex]
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
                            AgentCard(
                                session,
                                onClick = { detailId = session.id },
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
                                modifier = Modifier.fillMaxSize(),
                            )
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

@Composable
private fun AgentDetail(session: AgentSession) {
    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                session.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${session.provider}/${session.model}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "state: ${session.state.name}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(session.summary, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            Text(
                "Live stream will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "(stub \u2014 wired in L4)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 270, heightDp = 584, name = "NX1 \u2014 roller deck")
@Composable
private fun LauncherDeckPreview() {
    PaseoTheme(darkTheme = false) { LauncherScreen() }
}

private fun stubSessions(): List<AgentSession> = listOf(
    AgentSession("1", "Refactor auth module", "claude", "opus-4.6", AgentState.Running,
        "Splitting AuthController into per-concern files; adding tests for token-refresh.", 0.62f),
    AgentSession("2", "Add retry on 429", "codex", "gpt-5.4", AgentState.AwaitingInput,
        "Which backoff ceiling do you want \u2014 30s or 120s?"),
    AgentSession("3", "Generate fixtures", "opencode", "glm-5.2", AgentState.Done,
        "Wrote 40 fixtures into tests/fixtures/ and updated the snapshot index."),
    AgentSession("4", "Bump deps + renovate", "codex", "gpt-5.4", AgentState.Queued,
        "Waiting for a free slot \u2014 one agent is already running on this provider."),
    AgentSession("5", "Triage CI failures", "copilot", "gpt-5", AgentState.Error,
        "Exited 1 after 42s: could not resolve host github.com (network was blocked)."),
    AgentSession("6", "New session", "claude", "opus-4.6", AgentState.Idle,
        "Agent ready. Hold the button to give it a task."),
    AgentSession("7", "Migrate to flake-parts", "claude", "opus-4.6", AgentState.Running,
        "Converting the devshell to flake-parts modules; verifying nix develop.", 0.28f),
    AgentSession("8", "Write migration guide", "opencode", "glm-5.2", AgentState.Done,
        "Drafted docs/migrate.md covering the move and rollback steps."),
    AgentSession("9", "Fix off-by-one in pager", "codex", "gpt-5.4", AgentState.AwaitingInput,
        "Should the last card snap, or loop back to the first?"),
    AgentSession("10", "Seed test DB", "copilot", "gpt-5", AgentState.Queued,
        "Queued behind the auth refactor; starts when CPU frees up."),
)
