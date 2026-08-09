package sh.paseochat.launcher.model

enum class AgentState { Idle, Queued, Running, AwaitingInput, Done, Error }
enum class AgentMode { Plan, Build }

data class PermOption(
    val id: String,
    val label: String,
    val allow: Boolean,
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
    val pendingPermissionId: String? = null,
    val permissionKind: String? = null,
    val permissionTitle: String? = null,
    val permissionOptions: List<PermOption> = emptyList(),
)

