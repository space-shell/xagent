package sh.paseochat.launcher.daemon

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import sh.paseochat.launcher.daemon.models.ConnectionState
import sh.paseochat.launcher.model.AgentSession
import sh.paseochat.launcher.model.ConnectionProfile
import sh.paseochat.launcher.model.ConnectionType
import sh.paseochat.launcher.model.CreateAgentResult
import sh.paseochat.launcher.model.ProviderModelOption
import sh.paseochat.launcher.model.WorkspaceOption

private const val TAG = "ConnectionManager"

class ConnectionManager(
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class ManagedConnection(
        val client: PaseoDaemonClient,
        val profile: ConnectionProfile,
    )

    private val connections = mutableMapOf<String, ManagedConnection>()

    private val _allAgents = MutableStateFlow<List<AgentSession>>(emptyList())
    val allAgents: StateFlow<List<AgentSession>> = _allAgents.asStateFlow()

    private val _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, ConnectionState>> = _connectionStates.asStateFlow()

    private val _serverNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val serverNames: StateFlow<Map<String, String>> = _serverNames.asStateFlow()

    private val _serverIds = MutableStateFlow<Map<String, String>>(emptyMap())
    val serverIds: StateFlow<Map<String, String>> = _serverIds.asStateFlow()

    private val _workspaces = MutableStateFlow<Map<String, List<WorkspaceOption>>>(emptyMap())
    val workspaces: StateFlow<Map<String, List<WorkspaceOption>>> = _workspaces.asStateFlow()

    private val _providerModels = MutableStateFlow<Map<String, List<ProviderModelOption>>>(emptyMap())
    val providerModels: StateFlow<Map<String, List<ProviderModelOption>>> = _providerModels.asStateFlow()

    private val _lastCreatedAgentId = MutableStateFlow<String?>(null)
    val lastCreatedAgentId: StateFlow<String?> = _lastCreatedAgentId.asStateFlow()

    private val _wizardError = MutableStateFlow<String?>(null)
    val wizardError: StateFlow<String?> = _wizardError.asStateFlow()

    fun connect(profile: ConnectionProfile) {
        disconnect(profile.id)

        val client = PaseoDaemonClient(httpClient)
        connections[profile.id] = ManagedConnection(client, profile)

        scope.launch {
            client.agents.collect { remerge() }
        }
        scope.launch {
            client.connectionState.collect { state ->
                _connectionStates.value = _connectionStates.value.toMutableMap().apply {
                    this[profile.id] = state
                }
            }
        }
        scope.launch {
            client.serverName.collect { name ->
                _serverNames.value = _serverNames.value.toMutableMap().apply {
                    this[profile.id] = name
                }
                remerge()
            }
        }
        scope.launch {
            client.serverId.collect { sid ->
                _serverIds.value = _serverIds.value.toMutableMap().apply {
                    this[profile.id] = sid
                }
                remerge()
            }
        }
        scope.launch {
            client.workspaces.collect { ws ->
                _workspaces.value = _workspaces.value.toMutableMap().apply {
                    this[profile.id] = ws
                }
            }
        }
        scope.launch {
            client.providerModels.collect { pm ->
                _providerModels.value = _providerModels.value.toMutableMap().apply {
                    this[profile.id] = pm
                }
            }
        }

        Log.d(TAG, "connect() profile=${profile.id} type=${profile.connectionType}")
        when (profile.connectionType) {
            ConnectionType.RELAY -> {
                if (profile.serverId.isNotBlank() && profile.daemonPublicKeyB64.isNotBlank()) {
                    client.connectRelay(
                        RelayConfig(
                            serverId = profile.serverId,
                            daemonPublicKeyB64 = profile.daemonPublicKeyB64,
                            relayEndpoint = profile.relayEndpoint.ifBlank { "relay.paseo.sh:443" },
                            relayUseTls = profile.relayUseTls,
                        )
                    )
                }
            }
            ConnectionType.DIRECT -> {
                client.connect(profile.host, profile.password)
            }
        }
    }

    fun disconnect(profileId: String) {
        connections.remove(profileId)?.let { conn ->
            Log.d(TAG, "disconnect() profile=$profileId")
            conn.client.close()
        }
        _connectionStates.value = _connectionStates.value.toMutableMap().apply {
            remove(profileId)
        }
        _serverNames.value = _serverNames.value.toMutableMap().apply {
            remove(profileId)
        }
        _serverIds.value = _serverIds.value.toMutableMap().apply {
            remove(profileId)
        }
        _workspaces.value = _workspaces.value.toMutableMap().apply {
            remove(profileId)
        }
        _providerModels.value = _providerModels.value.toMutableMap().apply {
            remove(profileId)
        }
        remerge()
    }

    fun getConnectionState(profileId: String): ConnectionState {
        return _connectionStates.value[profileId] ?: ConnectionState.Disconnected
    }

    fun getServerName(profileId: String): String {
        return connections[profileId]?.client?.serverName?.value ?: ""
    }

    private fun remerge() {
        val sortedEntries = connections.entries.sortedBy { entry ->
            if (entry.value.profile.connectionType == ConnectionType.DIRECT) 0 else 1
        }
        val merged = sortedEntries.flatMap { (profileId, entry) ->
            entry.client.agents.value.map { session ->
                session.copy(
                    connectionId = profileId,
                    serverName = entry.client.serverName.value,
                    serverId = entry.client.serverId.value,
                )
            }
        }
        val seen = HashSet<String>()
        _allAgents.value = merged.filter { seen.add(it.id) }.toList()
    }

    private fun clientForAgent(agentId: String): PaseoDaemonClient? {
        return connections.values.firstOrNull { conn ->
            conn.client.agents.value.any { it.id == agentId }
        }?.client
    }

    fun respondToPermission(agentId: String, permissionRequestId: String, allow: Boolean) {
        clientForAgent(agentId)?.respondToPermission(agentId, permissionRequestId, allow)
    }

    fun respondToPermissionWithAction(
        agentId: String,
        permissionRequestId: String,
        selectedActionId: String? = null,
        customAnswer: String? = null,
    ) {
        clientForAgent(agentId)?.respondToPermissionWithAction(
            agentId, permissionRequestId, selectedActionId, customAnswer,
        )
    }

    fun setAgentMode(agentId: String, modeId: String) {
        clientForAgent(agentId)?.setAgentMode(agentId, modeId)
    }

    fun sendAgentMessage(agentId: String, text: String) {
        clientForAgent(agentId)?.sendAgentMessage(agentId, text)
    }

    fun archiveAgent(agentId: String) {
        _allAgents.value = _allAgents.value.filter { it.id != agentId }
        clientForAgent(agentId)?.archiveAgent(agentId)
    }

    fun refreshWizardData() {
        for ((_, conn) in connections) {
            conn.client.fetchWorkspaces()
            conn.client.fetchProviderModels()
        }
    }

    fun allWorkspaces(): List<Pair<String, WorkspaceOption>> {
        return connections.entries.sortedBy { entry ->
            if (entry.value.profile.connectionType == ConnectionType.DIRECT) 0 else 1
        }.flatMap { (profileId, conn) ->
            conn.client.workspaces.value.map { profileId to it }
        }
    }

    fun allProviderModels(): List<ProviderModelOption> {
        val seen = HashSet<String>()
        return connections.values
            .flatMap { it.client.providerModels.value }
            .filter { seen.add("${it.provider}/${it.modelId}") }
    }

    suspend fun createAgent(
        profileId: String,
        workspaceId: String,
        cwd: String,
        provider: String,
        modelId: String?,
        initialPrompt: String,
    ): CreateAgentResult {
        val client = connections[profileId]?.client
            ?: return CreateAgentResult.Failure("connection_not_found", "connection_not_found")
        _wizardError.value = null
        val result = client.createAgent(workspaceId, cwd, provider, modelId, initialPrompt)
        when (result) {
            is CreateAgentResult.Success -> _lastCreatedAgentId.value = result.agentId
            is CreateAgentResult.Failure -> _wizardError.value = result.error
        }
        return result
    }

    fun consumeLastCreatedAgentId(): String? {
        val v = _lastCreatedAgentId.value
        _lastCreatedAgentId.value = null
        return v
    }

    fun clearWizardError() {
        _wizardError.value = null
    }

    fun close() {
        connections.values.forEach { it.client.close() }
        connections.clear()
        scope.cancel()
    }
}
