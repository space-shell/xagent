package sh.paseochat.launcher.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import sh.paseochat.launcher.ui.theme.R1Orange

@Composable
fun MicButton(
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
                        delay(250)
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
fun ConfirmCancelBar(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(cs.error)
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Cancel",
                tint = cs.onError,
                modifier = Modifier.size(22.dp),
            )
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(cs.primary)
                .clickable(onClick = onConfirm),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "Send",
                tint = cs.onPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun ListeningLine(partialText: String) {
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
fun TranscriptBubble(text: String, textColor: Color) {
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
