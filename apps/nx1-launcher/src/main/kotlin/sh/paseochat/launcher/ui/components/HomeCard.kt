package sh.paseochat.launcher.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import sh.paseochat.launcher.daemon.models.ConnectionState
import sh.paseochat.launcher.model.ConnectionProfile
import sh.paseochat.launcher.model.ProviderModelOption
import sh.paseochat.launcher.model.SidebarSide
import sh.paseochat.launcher.model.WorkspaceOption

private const val STEP_WELCOME = 0
private const val STEP_SERVER = 1
private const val STEP_PROJECT = 2
private const val STEP_MODEL = 3
private const val STEP_PROMPT = 4

private val RestartRed = Color(0xFFE57373)

@Composable
fun HomeCard(
    profiles: List<ConnectionProfile>,
    connectionStates: Map<String, ConnectionState>,
    workspaces: List<Pair<String, WorkspaceOption>>,
    providerModels: List<ProviderModelOption>,
    serverNames: Map<String, String>,
    shPaseoInstalled: Boolean,
    sidebarSide: SidebarSide,
    resetSignal: Int,
    listening: Boolean,
    pendingTranscript: String?,
    wizardError: String?,
    onMicDown: () -> Unit,
    onMicUp: () -> Unit,
    onCancelTranscript: () -> Unit,
    onClearError: () -> Unit,
    onCreateAgent: (profileId: String, workspaceId: String, cwd: String, provider: String, modelId: String?, prompt: String) -> Unit,
    onLaunchPaseo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val maxSwipePx = with(density) { 120.dp.toPx() }

    var step by rememberSaveable { mutableIntStateOf(STEP_WELCOME) }
    var serverIndex by rememberSaveable { mutableIntStateOf(0) }
    var projectIndex by rememberSaveable { mutableIntStateOf(0) }
    var modelIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedWorkspace by remember { mutableStateOf<Pair<String, WorkspaceOption>?>(null) }
    var selectedModel by remember { mutableStateOf<ProviderModelOption?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }

    var swipeOffset by remember { mutableFloatStateOf(0f) }
    var swiping by remember { mutableStateOf(false) }
    val visualSwipe by animateFloatAsState(
        targetValue = swipeOffset,
        animationSpec = if (swiping) snap() else spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "wizard-swipe",
    )

    fun restartWizard() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        step = STEP_WELCOME
        serverIndex = 0
        projectIndex = 0
        modelIndex = 0
        selectedProfileId = null
        selectedWorkspace = null
        selectedModel = null
        localError = null
        swipeOffset = 0f
    }

    LaunchedEffect(resetSignal) {
        restartWizard()
    }

    val connectedProfiles = profiles.filter {
        connectionStates[it.id] == ConnectionState.Connected
    }
    val displayedError = wizardError ?: localError

    Box(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { swiping = true },
                    onDragEnd = {
                        swiping = false
                        swipeOffset = if (swipeOffset > maxSwipePx / 2) maxSwipePx else 0f
                    },
                    onDragCancel = {
                        swiping = false
                        swipeOffset = 0f
                    },
                ) { _, dragAmount ->
                    swipeOffset = (swipeOffset + dragAmount).coerceIn(0f, maxSwipePx)
                }
            },
    ) {
        Box(Modifier.fillMaxSize().background(RestartRed)) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .clickable { restartWizard() }
                    .padding(start = 24.dp, top = 24.dp, bottom = 24.dp),
            ) {
                Text("Restart", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }

        Card(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(visualSwipe.roundToInt(), 0) }
                .border(2.dp, cs.onSurface, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = cs.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(20.dp),
            ) {
                displayedError?.let { err ->
                    ErrorBanner(err) {
                        localError = null
                        onClearError()
                    }
                    Spacer(Modifier.height(12.dp))
                }

                when (step) {
                    STEP_WELCOME -> WelcomeStep(
                        shPaseoInstalled = shPaseoInstalled,
                        sidebarSide = sidebarSide,
                        onPlus = {
                            if (connectedProfiles.isEmpty()) {
                                localError = "No connected servers — add one in Settings"
                            } else if (connectedProfiles.size == 1) {
                                selectedProfileId = connectedProfiles.first().id
                                serverIndex = 0
                                step = STEP_PROJECT
                            } else {
                                step = STEP_SERVER
                            }
                        },
                        onLaunchPaseo = onLaunchPaseo,
                    )

                    STEP_SERVER -> {
                        val current = connectedProfiles.getOrNull(serverIndex)
                        CyclingStep(
                            title = "Server",
                            subtext = selectedProfileId?.let { id ->
                                connectedProfiles.firstOrNull { it.id == id }?.let { p ->
                                    serverNames[id]?.ifBlank { null } ?: p.label.ifBlank { p.host.ifBlank { p.id } }
                                }
                            },
                            optionCount = connectedProfiles.size,
                            currentTitle = current?.let { serverNames[it.id]?.ifBlank { null } ?: it.label.ifBlank { it.host.ifBlank { it.id } } },
                            currentSubtitle = if (current != null) "connected" else null,
                            sidebarSide = sidebarSide,
                            onCycle = { delta ->
                                if (connectedProfiles.isNotEmpty()) {
                                    val sz = connectedProfiles.size
                                    serverIndex = ((serverIndex + delta) % sz + sz) % sz
                                }
                            },
                            onCommit = {
                                val p = connectedProfiles.getOrNull(serverIndex) ?: return@CyclingStep
                                selectedProfileId = p.id
                                projectIndex = 0
                                step = STEP_PROJECT
                            },
                        )
                    }

                    STEP_PROJECT -> {
                        val current = workspaces.getOrNull(projectIndex)
                        CyclingStep(
                            title = "Project",
                            subtext = selectedWorkspace?.second?.label,
                            optionCount = workspaces.size,
                            currentTitle = current?.second?.label,
                            currentSubtitle = current?.second?.rootPath?.ifBlank { current?.second?.id },
                            sidebarSide = sidebarSide,
                            onCycle = { delta ->
                                if (workspaces.isNotEmpty()) {
                                    val sz = workspaces.size
                                    projectIndex = ((projectIndex + delta) % sz + sz) % sz
                                }
                            },
                            onCommit = {
                                val ws = workspaces.getOrNull(projectIndex) ?: return@CyclingStep
                                selectedWorkspace = ws
                                modelIndex = 0
                                step = STEP_MODEL
                            },
                        )
                    }

                    STEP_MODEL -> {
                        val current = providerModels.getOrNull(modelIndex)
                        CyclingStep(
                            title = "Model",
                            subtext = selectedModel?.label,
                            optionCount = providerModels.size,
                            currentTitle = current?.label,
                            currentSubtitle = current?.provider,
                            sidebarSide = sidebarSide,
                            onCycle = { delta ->
                                if (providerModels.isNotEmpty()) {
                                    val sz = providerModels.size
                                    modelIndex = ((modelIndex + delta) % sz + sz) % sz
                                }
                            },
                            onCommit = {
                                val m = providerModels.getOrNull(modelIndex) ?: return@CyclingStep
                                selectedModel = m
                                step = STEP_PROMPT
                            },
                        )
                    }

                    STEP_PROMPT -> PromptStep(
                        listening = listening,
                        pendingTranscript = pendingTranscript,
                        sidebarSide = sidebarSide,
                        profileLabel = connectedProfiles.firstOrNull { it.id == selectedProfileId }?.label ?: "",
                        workspaceLabel = selectedWorkspace?.second?.label ?: "",
                        modelLabel = selectedModel?.label ?: "",
                        onMicDown = onMicDown,
                        onMicUp = onMicUp,
                        onConfirm = {
                            val pid = selectedProfileId
                            val ws = selectedWorkspace
                            val m = selectedModel
                            val transcript = pendingTranscript?.trim()
                            if (pid == null || ws == null || transcript.isNullOrBlank()) {
                                localError = "Missing ${listOfNotNull(
                                    if (pid == null) "connection" else null,
                                    if (ws == null) "workspace" else null,
                                    if (transcript.isNullOrBlank()) "prompt" else null,
                                ).joinToString(", ")}"
                            } else {
                                onCreateAgent(pid, ws.second.id, ws.second.rootPath, m?.provider ?: "", m?.modelId, transcript)
                            }
                        },
                        onCancel = onCancelTranscript,
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(
    shPaseoInstalled: Boolean,
    sidebarSide: SidebarSide,
    onPlus: () -> Unit,
    onLaunchPaseo: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val plusAlign = if (sidebarSide == SidebarSide.Right) Alignment.BottomEnd else Alignment.BottomStart
    val paseoAlign = if (sidebarSide == SidebarSide.Right) Alignment.BottomStart else Alignment.BottomEnd

    Box(Modifier.fillMaxSize()) {
        CornerButton(
            modifier = Modifier.align(plusAlign),
            containerColor = cs.primary,
            onClick = onPlus,
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "New chat",
                tint = cs.onPrimary,
                modifier = Modifier.size(28.dp),
            )
        }

        if (shPaseoInstalled) {
            CornerButton(
                modifier = Modifier.align(paseoAlign),
                containerColor = cs.surfaceVariant,
                onClick = onLaunchPaseo,
            ) {
                Text(
                    "Paseo",
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun CornerButton(
    modifier: Modifier = Modifier,
    containerColor: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun CyclingStep(
    title: String,
    subtext: String?,
    optionCount: Int,
    currentTitle: String?,
    currentSubtitle: String?,
    sidebarSide: SidebarSide,
    onCycle: (Int) -> Unit,
    onCommit: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface,
        )
        if (subtext != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtext,
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            if (currentTitle == null || optionCount == 0) {
                Text(
                    "Nothing to choose yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            } else {
                SelectionButton(
                    title = currentTitle,
                    subtitle = currentSubtitle ?: "",
                    onClick = onCommit,
                )
            }
        }

        val leftIsPrev = sidebarSide == SidebarSide.Right
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ArrowButton(
                icon = if (leftIsPrev) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight,
                onClick = { onCycle(if (leftIsPrev) -1 else +1) },
            )
            ArrowButton(
                icon = if (leftIsPrev) Icons.Outlined.ChevronRight else Icons.Outlined.ChevronLeft,
                onClick = { onCycle(if (leftIsPrev) +1 else -1) },
            )
        }
    }
}

@Composable
private fun ArrowButton(
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(cs.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = cs.onSurface,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun SelectionButton(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface,
        )
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PromptStep(
    listening: Boolean,
    pendingTranscript: String?,
    sidebarSide: SidebarSide,
    profileLabel: String,
    workspaceLabel: String,
    modelLabel: String,
    onMicDown: () -> Unit,
    onMicUp: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Text(
            "Prompt",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "$profileLabel · $workspaceLabel · $modelLabel",
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            if (listening) {
                ListeningLine(partialText = pendingTranscript ?: "")
            } else if (!pendingTranscript.isNullOrBlank()) {
                TranscriptBubble(text = pendingTranscript, textColor = cs.onSurface)
            } else {
                Text(
                    "Hold the mic button to speak",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }
        }

        if (!pendingTranscript.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            ConfirmCancelBar(onConfirm = onConfirm, onCancel = onCancel)
        } else {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (sidebarSide == SidebarSide.Right) Arrangement.End else Arrangement.Start,
            ) {
                MicButton(
                    listening = listening,
                    onMicDown = onMicDown,
                    onMicUp = onMicUp,
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cs.errorContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Clear,
                contentDescription = "Dismiss",
                tint = cs.onErrorContainer,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
