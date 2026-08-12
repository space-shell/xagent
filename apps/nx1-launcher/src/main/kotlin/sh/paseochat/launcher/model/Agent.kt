package sh.paseochat.launcher.model

enum class AgentState { Idle, Queued, Running, AwaitingInput, Done, Error }
enum class AgentMode { Plan, Build }

data class PermOption(
    val id: String,
    val label: String,
    val allow: Boolean,
)

data class PendingPermission(
    val id: String,
    val kind: String,
    val title: String?,
    val description: String?,
    val options: List<PermOption>,
)

data class AgentSession(
    val id: String,
    val title: String,
    val provider: String,
    val model: String,
    val state: AgentState,
    val summary: String,
    val userInput: String = "",
    val mode: AgentMode = AgentMode.Build,
    val planModeId: String = "plan",
    val buildModeId: String = "auto",
    val pendingPermissions: List<PendingPermission> = emptyList(),
    val connectionId: String = "",
    val serverName: String = "",
    val serverId: String = "",
    val cwd: String = "",
)
