package sh.paseochat.launcher.ui.components

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
                        .background(meta.tint),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        meta.icon,
                        contentDescription = session.provider,
                        tint = meta.iconTint,
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
                StateChip(session.state)
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
private fun StateChip(state: AgentState) {
    val cs = MaterialTheme.colorScheme
    val (text, color) = when (state) {
        AgentState.Idle -> "idle" to cs.onSurfaceVariant
        AgentState.Queued -> "queued" to cs.tertiary
        AgentState.Running -> "running" to cs.primary
        AgentState.AwaitingInput -> "waiting" to cs.secondary
        AgentState.Done -> "done" to cs.primary
        AgentState.Error -> "error" to cs.error
    }
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private data class StateMeta(
    val icon: ImageVector,
    val tint: Color,
    val iconTint: Color,
)

@Composable
private fun stateMeta(state: AgentState): StateMeta {
    val cs = MaterialTheme.colorScheme
    return when (state) {
        AgentState.Idle -> StateMeta(
            Icons.Outlined.HourglassEmpty,
            cs.surfaceContainerHighest,
            cs.onSurfaceVariant,
        )
        AgentState.Queued -> StateMeta(
            Icons.Outlined.HourglassEmpty,
            cs.tertiaryContainer,
            cs.onTertiaryContainer,
        )
        AgentState.Running -> StateMeta(
            Icons.Outlined.Bolt,
            cs.primary,
            cs.onPrimary,
        )
        AgentState.AwaitingInput -> StateMeta(
            Icons.Outlined.PauseCircleOutline,
            cs.secondaryContainer,
            cs.onSecondaryContainer,
        )
        AgentState.Done -> StateMeta(
            Icons.Outlined.CheckCircle,
            cs.primaryContainer,
            cs.onPrimaryContainer,
        )
        AgentState.Error -> StateMeta(
            Icons.Outlined.ErrorOutline,
            cs.errorContainer,
            cs.onErrorContainer,
        )
    }
}

@Preview(showBackground = true, widthDp = 270, heightDp = 584, name = "NX1 — card states (light)")
@Composable
private fun AgentCardStatesPreview() {
    PaseoTheme(darkTheme = false) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AgentCard(sample("a", AgentState.Running, progress = 0.62f))
            AgentCard(sample("b", AgentState.AwaitingInput))
            AgentCard(sample("c", AgentState.Done))
            AgentCard(sample("d", AgentState.Error))
        }
    }
}

@Preview(showBackground = true, widthDp = 270, heightDp = 584, name = "NX1 — card states (dark)")
@Composable
private fun AgentCardStatesDarkPreview() {
    PaseoTheme(darkTheme = true) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AgentCard(sample("a", AgentState.Running, progress = 0.3f))
            AgentCard(sample("b", AgentState.Queued))
            AgentCard(sample("c", AgentState.Idle))
        }
    }
}

private fun sample(
    id: String,
    state: AgentState,
    progress: Float = 0f,
): AgentSession = AgentSession(
    id = id,
    title = "Refactor auth module",
    provider = "claude",
    model = "opus-4.6",
    state = state,
    summary = "Splitting the monolithic AuthController into per-concern files and adding tests for the token-refresh edge case.",
    progress = progress,
)
