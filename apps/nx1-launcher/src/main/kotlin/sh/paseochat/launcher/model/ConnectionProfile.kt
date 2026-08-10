package sh.paseochat.launcher.model

import kotlinx.serialization.Serializable

enum class ConnectionType { DIRECT, RELAY }

@Serializable
data class ConnectionProfile(
    val id: String,
    val label: String = "",
    val host: String = "",
    val password: String = "",
    val connectionType: ConnectionType = ConnectionType.DIRECT,
    val serverId: String = "",
    val daemonPublicKeyB64: String = "",
    val relayEndpoint: String = "",
    val relayUseTls: Boolean = true,
)
