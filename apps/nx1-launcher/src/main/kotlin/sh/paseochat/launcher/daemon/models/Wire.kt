package sh.paseochat.launcher.daemon.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class ConnectionState { Disconnected, Connecting, Connected, Error }

val DaemonJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

@Serializable
data class HelloEnvelope(
    val type: String = "hello",
    val clientId: String? = null,
    val clientType: String = "mobile",
    val protocolVersion: Int = 1,
    val capabilities: HelloCapabilities = HelloCapabilities(),
    val appVersion: String = "xagent-0.1.0",
)

@Serializable
data class HelloCapabilities(
    val permissions: Boolean = true,
    val modes: Boolean = true,
    val messaging: Boolean = true,
)

@Serializable
data class PingEnvelope(
    val type: String = "ping",
    val requestId: String,
    val clientSentAt: Long,
)

@Serializable
data class PongEnvelope(
    val type: String = "pong",
    val payload: PongData = PongData(),
)

@Serializable
data class PongData(
    val requestId: String = "",
    val serverReceivedAt: Long = 0,
    val serverSentAt: Long = 0,
)
