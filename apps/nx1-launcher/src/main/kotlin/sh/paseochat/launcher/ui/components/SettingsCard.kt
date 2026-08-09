package sh.paseochat.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import sh.paseochat.launcher.daemon.models.ConnectionState

@Composable
fun SettingsCard(
    host: String,
    onHostChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val containerColor = cs.surfaceContainerHighest
    val onContainerColor = cs.onSurface

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Box(Modifier.fillMaxHeight()) {
            Image(
                imageVector = Icons.Outlined.Settings,
                contentDescription = null,
                colorFilter = ColorFilter.tint(onContainerColor),
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
                    "Connection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onContainerColor,
                )
                Text(
                    "Paseo daemon",
                    style = MaterialTheme.typography.labelSmall,
                    color = onContainerColor.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = onHostChange,
                    label = { Text("Host:port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ConnectionDot(connectionState)
                    Text(
                        connectionState.name.lowercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = onContainerColor.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.weight(1f))
                    ConnectButton(
                        connectionState = connectionState,
                        onConnect = onConnect,
                        onDisconnect = onDisconnect,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectButton(
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val connected = connectionState == ConnectionState.Connected
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (connected) cs.error else cs.primary)
            .clickable {
                if (connected) onDisconnect() else onConnect()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (connected) "Disconnect" else "Connect",
            color = if (connected) cs.onError else cs.onPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun ConnectionDot(state: ConnectionState) {
    val cs = MaterialTheme.colorScheme
    val color = when (state) {
        ConnectionState.Disconnected -> cs.outline
        ConnectionState.Connecting -> cs.tertiary
        ConnectionState.Connected -> cs.primary
        ConnectionState.Error -> cs.error
    }
    Box(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}
