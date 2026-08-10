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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val DarkGreyBorder = Color(0xFF444444)

@Composable
fun SettingsCard(
    hideStatusBar: Boolean,
    onHideStatusBarChange: (Boolean) -> Unit,
    onAddConnection: () -> Unit,
    onOpenLauncher: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val containerColor = cs.surfaceContainerHighest
    val onContainerColor = cs.onSurface

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, DarkGreyBorder, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            Modifier
                .fillMaxHeight()
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Hide status bar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainerColor,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = hideStatusBar,
                    onCheckedChange = onHideStatusBarChange,
                )
            }

            Spacer(Modifier.weight(1f))

            AddConnectionButton(onClick = onAddConnection)
            Spacer(Modifier.height(12.dp))
            LauncherButton(onOpenLauncher = onOpenLauncher)
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

@Composable
private fun LauncherButton(onOpenLauncher: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(cs.surfaceVariant)
            .clickable(onClick = onOpenLauncher),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Apps,
                contentDescription = null,
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "Switch launcher",
                color = cs.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
