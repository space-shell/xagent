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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import sh.paseochat.launcher.daemon.models.ConnectionState
import sh.paseochat.launcher.model.ConnectionProfile
import sh.paseochat.launcher.model.ProviderModelOption
import sh.paseochat.launcher.model.SessionShortcut
import sh.paseochat.launcher.model.SidebarSide
import sh.paseochat.launcher.model.WorkspaceOption

private const val STEP_WELCOME = 0
private const val STEP_SERVER = 1
private const val STEP_PROJECT = 2
private const val STEP_MODEL = 3
private const val STEP_PROMPT = 4
private const val STEP_CREATING = 5
private const val STEP_CREATED = 6

private val RestartRed = Color(0xFFE57373)

@Composable
fun HomeCard(
    profiles: List<ConnectionProfile>,
    connectionStates: Map<String, ConnectionState>,
    workspacesByProfile: Map<String, List<WorkspaceOption>>,
    providerModelsByProfile: Map<String, List<ProviderModelOption>>,
    serverNames: Map<String, String>,
    shortcuts: List<SessionShortcut>,
    shPaseoInstalled: Boolean,
    sidebarSide: SidebarSide,
    resetSignal: Int,
    createdAgentSignal: Int,
    listening: Boolean,
    pendingTranscript: String?,
    wizardError: String?,
    onMicDown: () -> Unit,
    onMicUp: () -> Unit,
    onCancelTranscript: () -> Unit,
    onClearError: () -> Unit,
    onWizardReset: () -> Unit,
    onOpenCreatedAgent: () -> Unit,
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
        onWizardReset()
    }

    LaunchedEffect(resetSignal) {
        restartWizard()
    }

    LaunchedEffect(createdAgentSignal) {
        if (createdAgentSignal > 0) step = STEP_CREATED
    }

    LaunchedEffect(wizardError) {
        if (wizardError != null && step == STEP_CREATING) {
            step = STEP_PROMPT
        }
    }

    val connectedProfiles = profiles.filter {
        connectionStates[it.id] == ConnectionState.Connected
    }
    val profileWorkspaces: List<Pair<String, WorkspaceOption>> = selectedProfileId
        ?.let { pid -> workspacesByProfile[pid]?.map { pid to it } }
        ?: emptyList()
    val profileModels: List<ProviderModelOption> = selectedProfileId
        ?.let { pid -> providerModelsByProfile[pid] }
        ?: emptyList()
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
                        shortcuts = shortcuts,
                        connectedProfileIds = connectedProfiles.map { it.id }.toSet(),
                        shPaseoInstalled = shPaseoInstalled,
                        sidebarSide = sidebarSide,
                        onShortcutTap = { sc ->
                            selectedProfileId = sc.profileId
                            selectedWorkspace = sc.profileId to WorkspaceOption(
                                id = sc.workspaceId,
                                projectId = "",
                                label = sc.workspaceLabel,
                                rootPath = sc.cwd,
                            )
                            selectedModel = ProviderModelOption(
                                provider = sc.provider,
                                modelId = sc.modelId ?: "",
                                label = sc.modelLabel,
                            )
                            serverIndex = connectedProfiles.indexOfFirst { it.id == sc.profileId }.coerceAtLeast(0)
                            step = STEP_PROMPT
                        },
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
                        val current = profileWorkspaces.getOrNull(projectIndex)
                        CyclingStep(
                            title = "Project",
                            subtext = selectedWorkspace?.second?.label,
                            optionCount = profileWorkspaces.size,
                            currentTitle = current?.second?.label,
                            currentSubtitle = current?.second?.rootPath?.ifBlank { current?.second?.id },
                            sidebarSide = sidebarSide,
                            onCycle = { delta ->
                                if (profileWorkspaces.isNotEmpty()) {
                                    val sz = profileWorkspaces.size
                                    projectIndex = ((projectIndex + delta) % sz + sz) % sz
                                }
                            },
                            onCommit = {
                                val ws = profileWorkspaces.getOrNull(projectIndex) ?: return@CyclingStep
                                selectedWorkspace = ws
                                modelIndex = 0
                                step = STEP_MODEL
                            },
                        )
                    }

                    STEP_MODEL -> {
                        val current = profileModels.getOrNull(modelIndex)
                        CyclingStep(
                            title = "Model",
                            subtext = selectedModel?.label,
                            optionCount = profileModels.size,
                            currentTitle = current?.label,
                            currentSubtitle = current?.provider,
                            sidebarSide = sidebarSide,
                            onCycle = { delta ->
                                if (profileModels.isNotEmpty()) {
                                    val sz = profileModels.size
                                    modelIndex = ((modelIndex + delta) % sz + sz) % sz
                                }
                            },
                            onCommit = {
                                val m = profileModels.getOrNull(modelIndex) ?: return@CyclingStep
                                selectedModel = m
                                step = STEP_PROMPT
                            },
                        )
                    }

                    STEP_PROMPT -> PromptStep(
                        listening = listening,
                        pendingTranscript = pendingTranscript,
                        sidebarSide = sidebarSide,
                        profileLabel = selectedProfileId?.let { id ->
                            serverNames[id]?.ifBlank { null }
                                ?: connectedProfiles.firstOrNull { it.id == id }?.label?.ifBlank { null }
                                ?: ""
                        } ?: "",
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
                                step = STEP_CREATING
                                onCreateAgent(pid, ws.second.id, ws.second.rootPath, m?.provider ?: "", m?.modelId, transcript)
                            }
                        },
                        onCancel = onCancelTranscript,
                    )

                    STEP_CREATING -> CreatingStep()

                    STEP_CREATED -> CreatedStep(
                        sidebarSide = sidebarSide,
                        onOpenInPaseo = onOpenCreatedAgent,
                        onReset = { restartWizard() },
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(
    shortcuts: List<SessionShortcut>,
    connectedProfileIds: Set<String>,
    shPaseoInstalled: Boolean,
    sidebarSide: SidebarSide,
    onShortcutTap: (SessionShortcut) -> Unit,
    onPlus: () -> Unit,
    onLaunchPaseo: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val plusAlign = if (sidebarSide != SidebarSide.Left) Alignment.BottomEnd else Alignment.BottomStart
    val paseoAlign = if (sidebarSide != SidebarSide.Left) Alignment.BottomStart else Alignment.BottomEnd

    Box(Modifier.fillMaxSize()) {
        if (shortcuts.isNotEmpty()) {
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                shortcuts.forEach { sc ->
                    val enabled = sc.profileId in connectedProfileIds
                    val chipColor = if (enabled) cs.surfaceVariant else cs.surfaceVariant.copy(alpha = 0.4f)
                    val textColor = if (enabled) cs.onSurfaceVariant else cs.onSurfaceVariant.copy(alpha = 0.4f)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(chipColor)
                            .let { m -> if (enabled) m.clickable { onShortcutTap(sc) } else m }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${sc.serverLabel} · ${sc.workspaceLabel} · ${sc.modelLabel}",
                            style = MaterialTheme.typography.labelLarge,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(plusAlign)
                .size(44.dp)
                .clip(CircleShape)
                .background(cs.primary)
                .clickable(onClick = onPlus),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "New chat",
                tint = cs.onPrimary,
                modifier = Modifier.size(20.dp),
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
private fun CreatingStep() {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = cs.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Creating session\u2026",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
            )
        }
    }
}

@Composable
private fun CreatedStep(
    sidebarSide: SidebarSide,
    onOpenInPaseo: () -> Unit,
    onReset: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val openAlign = if (sidebarSide != SidebarSide.Left) Alignment.BottomEnd else Alignment.BottomStart
    val resetAlign = if (sidebarSide != SidebarSide.Left) Alignment.BottomStart else Alignment.BottomEnd

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Session Created",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
            )
        }

        Box(
            modifier = Modifier
                .align(openAlign)
                .size(44.dp)
                .clip(CircleShape)
                .background(cs.primary)
                .clickable(onClick = onOpenInPaseo),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = "Open in Paseo",
                tint = cs.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }

        Box(
            modifier = Modifier
                .align(resetAlign)
                .size(44.dp)
                .clip(CircleShape)
                .background(cs.surfaceVariant)
                .clickable(onClick = onReset),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.RestartAlt,
                contentDescription = "New chat",
                tint = cs.onSurface,
                modifier = Modifier.size(22.dp),
            )
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

        val leftIsPrev = sidebarSide != SidebarSide.Left
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
                horizontalArrangement = Arrangement.End,
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
