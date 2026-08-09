package sh.paseochat.launcher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch
import sh.paseochat.launcher.ui.components.AgentCard
import sh.paseochat.launcher.ui.components.AgentSession
import sh.paseochat.launcher.ui.components.AgentState
import sh.paseochat.launcher.ui.theme.PaseoTheme

private val DECK_PAGE_HEIGHT = 360.dp

@Composable
fun LauncherScreen() {
    val sessions = remember { mutableStateListOf(*stubSessions().toTypedArray()) }
    var detailId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val detail = detailId?.let { id -> sessions.firstOrNull { it.id == id } }
    BackHandler(enabled = detail != null) { detailId = null }

    if (detail != null) {
        AgentDetail(detail)
        return
    }

    val pagerState = rememberPagerState(pageCount = { sessions.size })

    val onDismiss: (AgentSession) -> Unit = { session ->
        val idx = sessions.indexOf(session)
        val wasCurrent = pagerState.currentPage == idx
        sessions.remove(session)
        scope.launch {
            if (wasCurrent) {
                val target = (sessions.size - 1).coerceAtLeast(0)
                if (pagerState.currentPage > target) {
                    pagerState.scrollToPage(target)
                }
            }
            val result = snackbarHostState.showSnackbar(
                message = "Dismissed \u201C${session.title}\u201D",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                sessions.add(idx.coerceIn(0, sessions.size), session)
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        if (sessions.isEmpty()) {
            EmptyState(Modifier.padding(padding))
        } else {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.padding(padding),
                pageSize = PageSize.Fixed(DECK_PAGE_HEIGHT),
                pageSpacing = (-48).dp,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                beyondViewportPageCount = 1,
                key = { sessions[it].id },
            ) { pageIndex ->
                val session = sessions[pageIndex]
                val pageOff = ((pagerState.currentPage - pageIndex) +
                    pagerState.currentPageOffsetFraction).absoluteValue
                val t = pageOff.coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(if (pageIndex == pagerState.currentPage) 1f else 0f)
                        .graphicsLayer {
                            scaleX = 1f - 0.06f * t
                            scaleY = 1f - 0.06f * t
                            alpha = 1f - 0.15f * t
                        },
                ) {
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { it != SwipeToDismissBoxValue.Settled }
                    )
                    val dismissed = remember { mutableStateOf(false) }
                    LaunchedEffect(dismissState.currentValue) {
                        if (!dismissed.value &&
                            dismissState.currentValue != SwipeToDismissBoxValue.Settled
                        ) {
                            dismissed.value = true
                            onDismiss(session)
                        }
                    }
                    SwipeToDismissBox(
                        state = dismissState,
                        modifier = Modifier.fillMaxSize(),
                        backgroundContent = { DismissBackground() },
                    ) {
                        AgentCard(
                            session,
                            onClick = { detailId = session.id },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
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
private fun DismissBackground() {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            Icons.Outlined.Delete,
            contentDescription = "Dismiss",
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(end = 24.dp),
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

@Preview(showBackground = true, widthDp = 270, heightDp = 584, name = "NX1 \u2014 deck")
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
