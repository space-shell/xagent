package sh.paseochat.launcher.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import sh.paseochat.launcher.ui.theme.DoneContainerDark
import sh.paseochat.launcher.ui.theme.DoneContainerLight
import sh.paseochat.launcher.ui.theme.OnDoneDark
import sh.paseochat.launcher.ui.theme.OnDoneLight
import sh.paseochat.launcher.ui.theme.PaseoTheme

enum class AgentState { Idle, Queued, Running, AwaitingInput, Done, Error }

data class AgentSession(
    val id: String,
    val title: String,
    val provider: String,
    val model: String,
    val state: AgentState,
    val summary: String,
    val progress: Float = 0f,
)

@Composable
fun AgentCard(
    session: AgentSession,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val meta = stateMeta(session.state)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(meta.avatarBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        meta.icon,
                        contentDescription = session.provider,
                        tint = meta.avatarIcon,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        session.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${session.provider}/${session.model}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StateChip(session.state, meta)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                session.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (session.state == AgentState.Running) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { session.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StateChip(state: AgentState, meta: StateMeta) {
    val pulseAlpha = if (state == AgentState.AwaitingInput) {
        val transition = rememberInfiniteTransition(label = "awaiting-pulse")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.45f,
            animationSpec = infiniteRepeatable(
                animation = tween(900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "awaiting-alpha",
        ).value
    } else {
        1f
    }
    Surface(
        modifier = Modifier.alpha(pulseAlpha),
        color = meta.chipBg,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            meta.label,
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = meta.chipText,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private data class StateMeta(
    val label: String,
    val icon: ImageVector,
    val avatarBg: Color,
    val avatarIcon: Color,
    val chipBg: Color,
    val chipText: Color,
)

@Composable
private fun stateMeta(state: AgentState): StateMeta {
    val cs = MaterialTheme.colorScheme
    val dark = cs.surface.luminance() < 0.5f
    return when (state) {
        AgentState.Idle -> StateMeta(
            label = "idle",
            icon = Icons.Outlined.HourglassEmpty,
            avatarBg = cs.surfaceVariant,
            avatarIcon = cs.onSurfaceVariant,
            chipBg = cs.surfaceVariant,
            chipText = cs.onSurfaceVariant,
        )
        AgentState.Queued -> StateMeta(
            label = "queued",
            icon = Icons.Outlined.HourglassEmpty,
            avatarBg = cs.tertiaryContainer,
            avatarIcon = cs.onTertiaryContainer,
            chipBg = cs.tertiaryContainer,
            chipText = cs.onTertiaryContainer,
        )
        AgentState.Running -> StateMeta(
            label = "running",
            icon = Icons.Outlined.Bolt,
            avatarBg = cs.primaryContainer,
            avatarIcon = cs.onPrimaryContainer,
            chipBg = cs.primaryContainer,
            chipText = cs.onPrimaryContainer,
        )
        AgentState.AwaitingInput -> StateMeta(
            label = "waiting",
            icon = Icons.Outlined.PauseCircleOutline,
            avatarBg = cs.secondaryContainer,
            avatarIcon = cs.onSecondaryContainer,
            chipBg = cs.secondaryContainer,
            chipText = cs.onSecondaryContainer,
        )
        AgentState.Done -> StateMeta(
            label = "done",
            icon = Icons.Outlined.CheckCircle,
            avatarBg = if (dark) DoneContainerDark else DoneContainerLight,
            avatarIcon = if (dark) OnDoneDark else OnDoneLight,
            chipBg = if (dark) DoneContainerDark else DoneContainerLight,
            chipText = if (dark) OnDoneDark else OnDoneLight,
        )
        AgentState.Error -> StateMeta(
            label = "error",
            icon = Icons.Outlined.ErrorOutline,
            avatarBg = cs.errorContainer,
            avatarIcon = cs.onErrorContainer,
            chipBg = cs.errorContainer,
            chipText = cs.onErrorContainer,
        )
    }
}

@Preview(showBackground = true, widthDp = 270, heightDp = 584, name = "NX1 — six states (light)")
@Composable
private fun AgentCardStatesPreview() {
    PaseoTheme(darkTheme = false) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AgentCard(sample(AgentState.Running, progress = 0.62f))
            AgentCard(sample(AgentState.AwaitingInput))
            AgentCard(sample(AgentState.Done))
            AgentCard(sample(AgentState.Error))
            AgentCard(sample(AgentState.Queued))
            AgentCard(sample(AgentState.Idle))
        }
    }
}

@Preview(showBackground = true, widthDp = 270, heightDp = 584, name = "NX1 — six states (dark)")
@Composable
private fun AgentCardStatesDarkPreview() {
    PaseoTheme(darkTheme = true) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AgentCard(sample(AgentState.Running, progress = 0.3f))
            AgentCard(sample(AgentState.AwaitingInput))
            AgentCard(sample(AgentState.Done))
            AgentCard(sample(AgentState.Error))
            AgentCard(sample(AgentState.Queued))
            AgentCard(sample(AgentState.Idle))
        }
    }
}

private fun sample(
    state: AgentState,
    progress: Float = 0f,
): AgentSession = AgentSession(
    id = state.name,
    title = "Refactor auth module",
    provider = "claude",
    model = "opus-4.6",
    state = state,
    summary = "Splitting the monolithic AuthController into per-concern files and adding tests for the token-refresh edge case.",
    progress = progress,
)
