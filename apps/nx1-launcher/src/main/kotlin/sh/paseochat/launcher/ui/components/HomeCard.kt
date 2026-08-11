package sh.paseochat.launcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

@Composable
fun HomeCard(
    profiles: List<ConnectionProfile>,
    connectionStates: Map<String, ConnectionState>,
    workspaces: List<Pair<String, WorkspaceOption>>,
    providerModels: List<ProviderModelOption>,
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
    onCreateAgent: (profileId: String, workspaceId: String, provider: String, modelId: String?, prompt: String) -> Unit,
    onLaunchPaseo: () -> Unit,
    onAddConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme

    var step by rememberSaveable { mutableIntStateOf(STEP_WELCOME) }
    var selectedProfileId by remember { mutableStateOf<String?>(null) }
    var selectedWorkspace by remember { mutableStateOf<Pair<String, WorkspaceOption>?>(null) }
    var selectedModel by remember { mutableStateOf<ProviderModelOption?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(resetSignal) {
        step = STEP_WELCOME
        selectedProfileId = null
        selectedWorkspace = null
        selectedModel = null
        localError = null
    }

    val displayedError = wizardError ?: localError

    val connectedProfiles = profiles.filter {
        connectionStates[it.id] == ConnectionState.Connected
    }

    Card(
        modifier = modifier
            .fillMaxSize()
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
                Spacer(Modifier.height(8.dp))
            }

            when (step) {
                STEP_WELCOME -> WelcomeStep(
                    shPaseoInstalled = shPaseoInstalled,
                    sidebarSide = sidebarSide,
                    onAddConnection = {
                        if (connectedProfiles.isEmpty()) {
                            localError = "Connect to a server first"
                            onAddConnection()
                        } else {
                            step = if (connectedProfiles.size == 1) {
                                selectedProfileId = connectedProfiles.first().id
                                STEP_PROJECT
                            } else {
                                STEP_SERVER
                            }
                        }
                    },
                    onLaunchPaseo = onLaunchPaseo,
                )
                STEP_SERVER -> ServerStep(
                    profiles = connectedProfiles,
                    connectionStates = connectionStates,
                    selectedProfileId = selectedProfileId,
                    onSelect = {
                        selectedProfileId = it
                        step = STEP_PROJECT
                    },
                    onBack = { step = STEP_WELCOME },
                )
                STEP_PROJECT -> ProjectStep(
                    workspaces = workspaces,
                    isLoading = workspaces.isEmpty(),
                    onSelect = {
                        selectedWorkspace = it
                        step = STEP_MODEL
                    },
                    onBack = {
                        step = if (connectedProfiles.size == 1) STEP_WELCOME else STEP_SERVER
                    },
                )
                STEP_MODEL -> ModelStep(
                    models = providerModels,
                    isLoading = providerModels.isEmpty(),
                    selected = selectedModel,
                    onSelect = {
                        selectedModel = it
                        step = STEP_PROMPT
                    },
                    onBack = { step = STEP_PROJECT },
                )
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
                            onCreateAgent(pid, ws.second.id, m?.provider ?: "", m?.modelId, transcript)
                        }
                    },
                    onCancel = onCancelTranscript,
                    onBack = { step = STEP_MODEL },
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(
    shPaseoInstalled: Boolean,
    sidebarSide: SidebarSide,
    onAddConnection: () -> Unit,
    onLaunchPaseo: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(cs.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.SmartToy,
                contentDescription = null,
                tint = cs.onPrimary,
                modifier = Modifier.size(52.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "xagent",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Start a new chat",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (sidebarSide == SidebarSide.Right) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BigPlusButton(onClick = onAddConnection)
            if (shPaseoInstalled) {
                Spacer(Modifier.width(12.dp))
                PaseoPill(onClick = onLaunchPaseo)
            }
        }
    }
}

@Composable
private fun ServerStep(
    profiles: List<ConnectionProfile>,
    connectionStates: Map<String, ConnectionState>,
    selectedProfileId: String?,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        StepHeader(title = "Choose server", onBack = onBack)
        Spacer(Modifier.height(12.dp))
        if (profiles.isEmpty()) {
            EmptyState("No connected servers. Tap + to add one.")
        } else {
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    val connected = connectionStates[profile.id] == ConnectionState.Connected
                    SelectableRow(
                        title = profile.label.ifBlank { profile.host.ifBlank { profile.id } },
                        subtitle = if (connected) "connected" else "not connected",
                        selected = profile.id == selectedProfileId,
                        enabled = connected,
                        onClick = { if (connected) onSelect(profile.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectStep(
    workspaces: List<Pair<String, WorkspaceOption>>,
    isLoading: Boolean,
    onSelect: (Pair<String, WorkspaceOption>) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        StepHeader(title = "Choose project", onBack = onBack)
        Spacer(Modifier.height(12.dp))
        when {
            isLoading -> EmptyState("Loading projects…")
            workspaces.isEmpty() -> EmptyState("No projects available.")
            else -> {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(workspaces, key = { "${it.first}/${it.second.id}" }) { (profileId, ws) ->
                        SelectableRow(
                            title = ws.label,
                            subtitle = ws.rootPath.ifBlank { ws.id },
                            selected = false,
                            enabled = true,
                            onClick = { onSelect(profileId to ws) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelStep(
    models: List<ProviderModelOption>,
    isLoading: Boolean,
    selected: ProviderModelOption?,
    onSelect: (ProviderModelOption) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        StepHeader(title = "Choose model", onBack = onBack)
        Spacer(Modifier.height(12.dp))
        when {
            isLoading -> EmptyState("Loading models…")
            models.isEmpty() -> EmptyState("No models available.")
            else -> {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(models, key = { "${it.provider}/${it.modelId}" }) { m ->
                        SelectableRow(
                            title = m.label,
                            subtitle = m.provider,
                            selected = selected?.modelId == m.modelId && selected?.provider == m.provider,
                            enabled = true,
                            onClick = { onSelect(m) },
                        )
                    }
                }
            }
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
    onBack: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        StepHeader(title = "Speak your prompt", onBack = onBack)
        Spacer(Modifier.height(8.dp))
        Text(
            "$profileLabel · $workspaceLabel · $modelLabel",
            style = MaterialTheme.typography.bodySmall,
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
        }

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

@Composable
private fun StepHeader(title: String, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(cs.surfaceVariant)
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Text("←", color = cs.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface,
        )
    }
}

@Composable
private fun SelectableRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val bg = when {
        selected -> cs.primary.copy(alpha = 0.16f)
        !enabled -> cs.surfaceVariant.copy(alpha = 0.4f)
        else -> cs.surfaceVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Folder,
            contentDescription = null,
            tint = if (enabled) cs.onSurfaceVariant else cs.outline,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (enabled) cs.onSurface else cs.outline,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) cs.onSurfaceVariant else cs.outline,
            )
        }
        if (selected) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = cs.primary,
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun BigPlusButton(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(cs.primary)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Add,
            contentDescription = "New chat",
            tint = cs.onPrimary,
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
private fun PaseoPill(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(cs.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.SmartToy,
            contentDescription = null,
            tint = cs.onSurface,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "Paseo",
            style = MaterialTheme.typography.labelLarge,
            color = cs.onSurface,
        )
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
