package sh.paseochat.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sh.paseochat.launcher.model.SidebarSide

@Composable
fun SettingsCard(
    hideStatusBar: Boolean,
    onHideStatusBarChange: (Boolean) -> Unit,
    sidebarSide: SidebarSide,
    onSidebarSideChange: (SidebarSide) -> Unit,
    keepAlive: Boolean,
    onKeepAliveChange: (Boolean) -> Unit,
    onAddConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val containerColor = cs.surfaceContainerHighest
    val onContainerColor = cs.onSurface

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, cs.onSurface, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = onContainerColor,
            )
            Text(
                "xagent",
                style = MaterialTheme.typography.labelSmall,
                color = onContainerColor.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(20.dp))

            Text(
                "Status bar",
                style = MaterialTheme.typography.bodyMedium,
                color = onContainerColor,
            )
            Spacer(Modifier.height(6.dp))
            SegmentedToggle(
                options = listOf("Show" to false, "Hide" to true),
                selected = hideStatusBar,
                onSelect = onHideStatusBarChange,
            )

            Spacer(Modifier.height(20.dp))

            Text(
                "Sidebar side",
                style = MaterialTheme.typography.bodyMedium,
                color = onContainerColor,
            )
            Spacer(Modifier.height(6.dp))
            SegmentedToggle(
                options = listOf("Left" to SidebarSide.Left, "Off" to SidebarSide.Off, "Right" to SidebarSide.Right),
                selected = sidebarSide,
                onSelect = onSidebarSideChange,
            )

            Spacer(Modifier.height(20.dp))

            Text(
                "Keep alive in background",
                style = MaterialTheme.typography.bodyMedium,
                color = onContainerColor,
            )
            Spacer(Modifier.height(6.dp))
            SegmentedToggle(
                options = listOf("Off" to false, "On" to true),
                selected = keepAlive,
                onSelect = onKeepAliveChange,
            )

            Spacer(Modifier.height(24.dp))

            AddConnectionButton(onClick = onAddConnection)
        }
    }
}

@Composable
private fun <T> SegmentedToggle(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surfaceVariant),
    ) {
        options.forEach { (label, value) ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (selected == value) cs.primary else Color.Transparent)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected == value) cs.onPrimary else cs.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun AddConnectionButton(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(cs.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = cs.onPrimary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "Add Connection",
                color = cs.onPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
