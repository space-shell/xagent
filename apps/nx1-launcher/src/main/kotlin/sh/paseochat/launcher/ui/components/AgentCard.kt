package sh.paseochat.launcher.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import sh.paseochat.launcher.model.AgentMode
import sh.paseochat.launcher.model.AgentSession
import sh.paseochat.launcher.model.AgentState
import sh.paseochat.launcher.model.PermOption
import sh.paseochat.launcher.ui.rememberHaptics
import sh.paseochat.launcher.ui.theme.DoneContainerDark
import sh.paseochat.launcher.ui.theme.DoneContainerLight
import sh.paseochat.launcher.ui.theme.OnDoneDark
import sh.paseochat.launcher.ui.theme.OnDoneLight
import sh.paseochat.launcher.ui.theme.PaseoTheme
import sh.paseochat.launcher.ui.theme.R1Orange

@Composable
fun stateDotColor(state: AgentState): Color = stateMeta(state).containerColor

private val ArchiveRed = Color(0xFFE57373)

@Composable
fun AgentCard(
    session: AgentSession,
    serverName: String = "",
    listening: Boolean = false,
    partialText: String = "",
    pendingTranscript: String? = null,
    onMicDown: () -> Unit = {},
    onMicUp: () -> Unit = {},
    onCycleMode: () -> Unit = {},
    onApprove: () -> Unit = {},
    onDeny: () -> Unit = {},
    onApproveAlways: (() -> Unit)? = null,
    onSelectOption: (PermOption) -> Unit = {},
    onArchive: () -> Unit = {},
    onConfirmTranscript: () -> Unit = {},
    onCancelTranscript: () -> Unit = {},
    onOpenInPaseo: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val meta = stateMeta(session.state)
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val maxSwipePx = with(density) { 120.dp.toPx() }
    val borderColor = MaterialTheme.colorScheme.onSurface

    var isDragging by remember(session.id) { mutableStateOf(false) }
    var swipeOffset by remember(session.id) { mutableFloatStateOf(0f) }
    val visualOffset by animateFloatAsState(
        targetValue = swipeOffset,
        animationSpec = if (isDragging) snap() else spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "swipe",
    )

    val modeDotColor = if (session.mode == AgentMode.Plan) {
        Color(0xFFC9A227)
    } else {
        Color(0xFF666666)
    }

    Box(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .pointerInput(session.id) {
                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        swipeOffset = if (swipeOffset > maxSwipePx / 2) maxSwipePx else 0f
                    },
                    onDragCancel = {
                        isDragging = false
                        swipeOffset = 0f
                    },
                ) { _, dragAmount ->
                    swipeOffset = (swipeOffset + dragAmount).coerceIn(0f, maxSwipePx)
                }
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(ArchiveRed),
        ) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .clickable {
                        haptics.confirm()
                        onArchive()
                    }
                    .padding(start = 24.dp, top = 24.dp, bottom = 24.dp),
            ) {
                Text("Archive", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(visualOffset.roundToInt(), 0) }
                .border(2.dp, borderColor, RoundedCornerShape(28.dp))
                .pointerInput(session.id, session.mode) {
                    detectTapGestures(
                        onTap = {
                            haptics.tick()
                            onOpenInPaseo()
                        },
                        onLongPress = {
                            haptics.longPress()
                            onCycleMode()
                        },
                    )
                },
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = meta.containerColor),
        ) {
            Column(Modifier.fillMaxHeight()) {
                Box(Modifier.weight(1f)) {
                    Column(
                        Modifier
                            .fillMaxHeight()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(modeDotColor)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                session.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = meta.onContainerColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val workspaceName = session.cwd.substringAfterLast('/').ifBlank { "?" }
                        val modelName = session.model.substringAfterLast('/')
                        Text(
                            "${serverName.ifBlank { "?" }}/$workspaceName/$modelName",
                            style = MaterialTheme.typography.labelSmall,
                            color = meta.onContainerColor.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(12.dp))
                        val isQuestion = session.state == AgentState.AwaitingInput &&
                            session.permissionKind == "question"
                        if (isQuestion) {
                            Text(
                                session.permissionTitle ?: session.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = meta.onContainerColor,
                                fontWeight = FontWeight.Medium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Text(
                                session.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = meta.onContainerColor,
                                maxLines = 16,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        if (listening) {
                            ListeningLine(partialText)
                        } else if (pendingTranscript != null) {
                            TranscriptBubble(pendingTranscript, meta.onContainerColor)
                        } else if (session.userInput.isNotBlank()) {
                            TranscriptBubble(session.userInput, meta.onContainerColor)
                        }
                        Spacer(Modifier.weight(1f))
                        if (pendingTranscript != null) {
                            ConfirmCancelBar(
                                onConfirm = { haptics.confirm(); onConfirmTranscript() },
                                onCancel = { haptics.reject(); onCancelTranscript() },
                            )
                        } else if (isQuestion) {
                            QuestionBar(
                                options = session.permissionOptions,
                                onSelectOption = { opt ->
                                    haptics.confirm()
                                    onSelectOption(opt)
                                },
                                listening = listening,
                                onMicDown = onMicDown,
                                onMicUp = onMicUp,
                            )
                        } else if (session.state == AgentState.AwaitingInput) {
                            ApprovalBar(
                                onApprove = { haptics.confirm(); onApprove() },
                                onDeny = { haptics.reject(); onDeny() },
                                onApproveAlways = onApproveAlways,
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                MicButton(
                                    listening = listening,
                                    onMicDown = { haptics.tick(); onMicDown() },
                                    onMicUp = onMicUp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ApprovalBar(
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onApproveAlways: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(cs.primary)
                .then(
                    if (onApproveAlways != null) {
                        Modifier.combinedClickable(
                            onClick = onApprove,
                            onLongClick = onApproveAlways,
                        )
                    } else {
                        Modifier.clickable(onClick = onApprove)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("Allow", color = cs.onPrimary, fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(cs.error)
                .clickable(onClick = onDeny),
            contentAlignment = Alignment.Center,
        ) {
            Text("Deny", color = cs.onError, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun QuestionBar(
    options: List<PermOption>,
    onSelectOption: (PermOption) -> Unit,
    listening: Boolean,
    onMicDown: () -> Unit,
    onMicUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
        ) {
            options.forEach { option ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (option.allow) cs.primary else cs.error)
                        .clickable { onSelectOption(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option.label,
                        color = if (option.allow) cs.onPrimary else cs.onError,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
        MicButton(
            listening = listening,
            onMicDown = onMicDown,
            onMicUp = onMicUp,
        )
    }
}

private data class StateMeta(
    val icon: ImageVector,
    val containerColor: Color,
    val onContainerColor: Color,
)

@Composable
private fun stateMeta(state: AgentState): StateMeta {
    val cs = MaterialTheme.colorScheme
    val dark = cs.surface.luminance() < 0.5f
    return when (state) {
        AgentState.Idle -> StateMeta(
            icon = Icons.Outlined.HourglassEmpty,
            containerColor = cs.surfaceVariant,
            onContainerColor = cs.onSurfaceVariant,
        )
        AgentState.Queued -> StateMeta(
            icon = Icons.Outlined.HourglassEmpty,
            containerColor = cs.tertiaryContainer,
            onContainerColor = cs.onTertiaryContainer,
        )
        AgentState.Running -> StateMeta(
            icon = Icons.Outlined.Bolt,
            containerColor = cs.primaryContainer,
            onContainerColor = cs.onPrimaryContainer,
        )
        AgentState.AwaitingInput -> StateMeta(
            icon = Icons.Outlined.PauseCircleOutline,
            containerColor = cs.secondaryContainer,
            onContainerColor = cs.onSecondaryContainer,
        )
        AgentState.Done -> StateMeta(
            icon = Icons.Outlined.CheckCircle,
            containerColor = if (dark) DoneContainerDark else DoneContainerLight,
            onContainerColor = if (dark) OnDoneDark else OnDoneLight,
        )
        AgentState.Error -> StateMeta(
            icon = Icons.Outlined.ErrorOutline,
            containerColor = cs.errorContainer,
            onContainerColor = cs.onErrorContainer,
        )
    }
}

@Preview(showBackground = true, widthDp = 270, heightDp = 584, name = "Reference \u2014 six states (light)")
@Composable
private fun AgentCardStatesPreview() {
    PaseoTheme(darkTheme = false) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AgentCard(sample(AgentState.Running))
            AgentCard(sample(AgentState.AwaitingInput))
            AgentCard(sample(AgentState.Done))
            AgentCard(sample(AgentState.Error, mode = AgentMode.Plan))
            AgentCard(sample(AgentState.Queued))
            AgentCard(sample(AgentState.Idle))
        }
    }
}

@Preview(showBackground = true, widthDp = 270, heightDp = 584, name = "Reference \u2014 six states (dark)")
@Composable
private fun AgentCardStatesDarkPreview() {
    PaseoTheme(darkTheme = true) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AgentCard(sample(AgentState.Running))
            AgentCard(sample(AgentState.AwaitingInput))
            AgentCard(sample(AgentState.Done))
            AgentCard(sample(AgentState.Error, mode = AgentMode.Plan))
            AgentCard(sample(AgentState.Queued))
            AgentCard(sample(AgentState.Idle))
        }
    }
}

private fun sample(
    state: AgentState,
    mode: AgentMode = AgentMode.Build,
): AgentSession = AgentSession(
    id = state.name,
    title = "Refactor auth module",
    provider = "claude",
    model = "opus-4.6",
    state = state,
    summary = "Splitting the monolithic AuthController into per-concern files and adding tests for the token-refresh edge case.",
    mode = mode,
)
