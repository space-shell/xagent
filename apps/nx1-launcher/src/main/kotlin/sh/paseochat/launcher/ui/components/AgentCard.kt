package sh.paseochat.launcher.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import sh.paseochat.launcher.ui.rememberHaptics
import sh.paseochat.launcher.ui.theme.DoneContainerDark
import sh.paseochat.launcher.ui.theme.DoneContainerLight
import sh.paseochat.launcher.ui.theme.OnDoneDark
import sh.paseochat.launcher.ui.theme.OnDoneLight
import sh.paseochat.launcher.ui.theme.PaseoTheme
import sh.paseochat.launcher.ui.theme.R1Orange

enum class AgentState { Idle, Queued, Running, AwaitingInput, Done, Error }
enum class AgentMode { Plan, Build }

data class AgentSession(
    val id: String,
    val title: String,
    val provider: String,
    val model: String,
    val state: AgentState,
    val summary: String,
    val userInput: String = "",
    val mode: AgentMode = AgentMode.Build,
)

@Composable
fun stateDotColor(state: AgentState): Color = stateMeta(state).containerColor

@Composable
fun AgentCard(
    session: AgentSession,
    listening: Boolean = false,
    partialText: String = "",
    onMicDown: () -> Unit = {},
    onMicUp: () -> Unit = {},
    onCycleMode: () -> Unit = {},
    onApprove: () -> Unit = {},
    onDeny: () -> Unit = {},
    onApproveAlways: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val meta = stateMeta(session.state)
    val haptics = rememberHaptics()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        haptics.longPress()
                        onCycleMode()
                    },
                )
            },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = meta.containerColor),
    ) {
        Box(Modifier.fillMaxHeight()) {
            Image(
                imageVector = meta.icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(meta.onContainerColor),
                contentScale = ContentScale.Crop,
                alpha = 0.07f,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            )
            Column(
                Modifier
                    .fillMaxHeight()
                    .padding(16.dp)
            ) {
                Text(
                    session.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = meta.onContainerColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${session.provider}/${session.model} · ${session.mode.name.lowercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = meta.onContainerColor.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    session.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = meta.onContainerColor,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                if (listening) {
                    ListeningLine(partialText)
                } else if (session.userInput.isNotBlank()) {
                    TranscriptBubble(session.userInput, meta.onContainerColor)
                }
                Spacer(Modifier.weight(1f))
                if (session.state == AgentState.AwaitingInput) {
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

@Composable
private fun MicButton(
    listening: Boolean,
    onMicDown: () -> Unit,
    onMicUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val pulseScale = if (listening) {
        rememberInfiniteTransition(label = "mic-scale-t").animateFloat(
            initialValue = 1f,
            targetValue = 1.18f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "mic-scale",
        ).value
    } else {
        1f
    }
    val ringAlpha = if (listening) {
        rememberInfiniteTransition(label = "mic-ring-t").animateFloat(
            initialValue = 0.85f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "mic-ring",
        ).value
    } else {
        0f
    }
    Box(
        modifier = modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onMicDown()
                        try {
                            tryAwaitRelease()
                        } finally {
                            onMicUp()
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (listening) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(R1Orange.copy(alpha = ringAlpha)),
            )
        }
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (listening) R1Orange else cs.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (listening) Icons.Filled.Mic else Icons.Outlined.Mic,
                contentDescription = "Hold to talk",
                tint = if (listening) Color.White else cs.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ListeningLine(partialText: String) {
    val dotAlpha = rememberInfiniteTransition(label = "listen-t").animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "listen-dot",
    ).value
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(R1Orange.copy(alpha = dotAlpha)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (partialText.isBlank()) "Listening\u2026" else partialText,
            style = MaterialTheme.typography.bodySmall,
            color = R1Orange,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TranscriptBubble(text: String, textColor: Color) {
    Surface(
        color = textColor.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
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

@Preview(showBackground = true, widthDp = 270, heightDp = 584, name = "NX1 \u2014 six states (light)")
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

@Preview(showBackground = true, widthDp = 270, heightDp = 584, name = "NX1 \u2014 six states (dark)")
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
