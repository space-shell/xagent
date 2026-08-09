package sh.paseochat.launcher.model

enum class AgentState { Idle, Queued, Running, AwaitingInput, Done, Error }
enum class AgentMode { Plan, Build }

data class AgentSession(
    val id: String,
    val title: String,
    val provider: String,
    val model: String,
    val state: AgentState,
    val summary: String,
    val userInput: String = "",
    val mode: AgentMode = AgentMode.Build,
    val pendingPermissionId: String? = null,
)
