package sh.paseochat.launcher.model

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

sealed class CreateAgentResult {
    data class Success(val agentId: String) : CreateAgentResult()
    data class Failure(val error: String, val errorCode: String? = null) : CreateAgentResult()
}
