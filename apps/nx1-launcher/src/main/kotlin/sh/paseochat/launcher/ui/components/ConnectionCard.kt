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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt
import sh.paseochat.launcher.daemon.models.ConnectionState
import sh.paseochat.launcher.model.ConnectionProfile
import sh.paseochat.launcher.model.ConnectionType
import sh.paseochat.launcher.ui.rememberHaptics

private val DeleteRed = Color(0xFFE57373)
private val DarkGreyBorder = Color(0xFF444444)

@Composable
fun ConnectionCard(
    profile: ConnectionProfile,
    connectionState: ConnectionState,
    serverName: String,
    onProfileChange: (ConnectionProfile) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onDelete: () -> Unit,
    onQrScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val containerColor = cs.surfaceContainerHighest
    val onContainerColor = cs.onSurface

    var mode by remember(profile.id) {
        mutableStateOf(if (profile.connectionType == ConnectionType.RELAY) "qr" else "url")
    }

    var hostText by remember(profile.id) { mutableStateOf(profile.host) }
    var passwordText by remember(profile.id) { mutableStateOf(profile.password) }

    LaunchedEffect(profile.host) {
        if (profile.host != hostText) hostText = profile.host
    }
    LaunchedEffect(profile.password) {
        if (profile.password != passwordText) passwordText = profile.password
    }
    LaunchedEffect(hostText) {
        delay(400)
        if (hostText != profile.host) onProfileChange(profile.copy(host = hostText))
    }
    LaunchedEffect(passwordText) {
        delay(400)
        if (passwordText != profile.password) onProfileChange(profile.copy(password = passwordText))
    }

    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val context = LocalContext.current
    val maxSwipePx = with(density) { 120.dp.toPx() }

    val hasCameraPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission.value = granted }

    var isDragging by remember { mutableStateOf(false) }
    var swipeOffset by remember(profile.id) { mutableFloatStateOf(0f) }
    val visualOffset by animateFloatAsState(
        targetValue = swipeOffset,
        animationSpec = if (isDragging) snap() else spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "conn-swipe",
    )

    Box(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .pointerInput(profile.id) {
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
                .background(DeleteRed),
        ) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .clickable {
                        haptics.confirm()
                        onDelete()
                    }
                    .padding(start = 24.dp, top = 24.dp, bottom = 24.dp),
            ) {
                Text("Delete", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }

        val borderColor = DarkGreyBorder
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(visualOffset.roundToInt(), 0) }
                .border(1.dp, borderColor, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
        ) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(cs.surfaceVariant),
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (mode == "url") cs.primary else Color.Transparent)
                            .clickable { mode = "url" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "URL",
                            color = if (mode == "url") cs.onPrimary else cs.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (mode == "qr") cs.primary else Color.Transparent)
                            .clickable { mode = "qr" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "QR",
                            color = if (mode == "qr") cs.onPrimary else cs.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (mode == "url") {
                    OutlinedTextField(
                        value = hostText,
                        onValueChange = { hostText = it },
                        label = { Text("Host:port") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passwordText,
                        onValueChange = { passwordText = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (serverName.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            serverName,
                            style = MaterialTheme.typography.labelSmall,
                            color = onContainerColor.copy(alpha = 0.6f),
                        )
                    }
                } else {
                    if (profile.connectionType == ConnectionType.RELAY && profile.serverId.isNotBlank()) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Relay connection",
                                style = MaterialTheme.typography.labelMedium,
                                color = onContainerColor.copy(alpha = 0.5f),
                            )
                            Text(
                                profile.serverId,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = onContainerColor,
                                maxLines = 2,
                            )
                            if (serverName.isNotBlank()) {
                                Text(
                                    serverName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onContainerColor.copy(alpha = 0.6f),
                                )
                            }
                        }
                    } else {
                        BoxWithConstraints(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (hasCameraPermission.value) {
                                val camSize = minOf(maxWidth, maxHeight)
                                QrScannerWindow(
                                    onScanned = onQrScanned,
                                    modifier = Modifier
                                        .size(camSize)
                                        .clip(RoundedCornerShape(16.dp)),
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        "Camera permission needed",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = onContainerColor.copy(alpha = 0.5f),
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(cs.primary)
                                            .clickable { cameraPermLauncher.launch(Manifest.permission.CAMERA) }
                                            .padding(horizontal = 20.dp, vertical = 10.dp),
                                    ) {
                                        Text(
                                            "Grant camera",
                                            color = cs.onPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

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
