package sh.paseochat.launcher.model

import kotlinx.serialization.Serializable

data class WorkspaceOption(
    val id: String,
    val projectId: String,
    val label: String,
    val rootPath: String,
)

data class ProviderModelOption(
    val provider: String,
    val modelId: String,
    val label: String,
    val isDefault: Boolean = false,
)

@Serializable
data class SessionShortcut(
    val profileId: String,
    val workspaceId: String,
    val cwd: String,
    val provider: String,
    val modelId: String?,
    val serverLabel: String,
    val workspaceLabel: String,
    val modelLabel: String,
)

sealed class CreateAgentResult {
    data class Success(val agentId: String) : CreateAgentResult()
    data class Failure(val error: String, val errorCode: String? = null) : CreateAgentResult()
}
