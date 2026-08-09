package sh.paseochat.launcher.daemon

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import sh.paseochat.launcher.daemon.models.ConnectionState
import sh.paseochat.launcher.daemon.models.DaemonJson
import sh.paseochat.launcher.daemon.models.HelloEnvelope
import sh.paseochat.launcher.daemon.models.PingEnvelope
import sh.paseochat.launcher.model.AgentMode
import sh.paseochat.launcher.model.AgentSession
import sh.paseochat.launcher.model.AgentState
import java.util.UUID

private const val TAG = "PaseoDaemonClient"
private const val PING_INTERVAL_MS = 30_000L
private const val MAX_RECONNECT_MS = 30_000L

class PaseoDaemonClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _agents = MutableStateFlow<List<AgentSession>>(emptyList())
    val agents: StateFlow<List<AgentSession>> = _agents.asStateFlow()

    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private var currentHost: String? = null
    private var currentPassword: String = ""
    private val clientId = UUID.randomUUID().toString()
    private var reconnectAttempts = 0
    private var sessionReady = false

    fun connect(host: String, password: String) {
        Log.d(TAG, "connect() host=$host")
        currentHost = host
        currentPassword = password
        sessionReady = false
        pingJob?.cancel()
        reconnectJob?.cancel()
        webSocket?.close(1000, "reconnect")
        webSocket = null
        reconnectAttempts = 0
        _connectionState.value = ConnectionState.Connecting
        doConnect()
    }

    fun disconnect() {
        Log.d(TAG, "disconnect()")
        currentHost = null
        sessionReady = false
        pingJob?.cancel()
        reconnectJob?.cancel()
        webSocket?.close(1000, "client disconnect")
        webSocket = null
        _agents.value = emptyList()
        _connectionState.value = ConnectionState.Disconnected
    }

    fun close() {
        disconnect()
        scope.cancel()
    }

    fun respondToPermission(agentId: String, permissionRequestId: String, allow: Boolean) {
        val msg = buildJsonObject {
            put("type", "session")
            putJsonObject("message") {
                put("type", "agent_permission_response")
                put("agentId", agentId)
                put("requestId", permissionRequestId)
                putJsonObject("response") {
                    if (allow) {
                        put("behavior", "allow")
                    } else {
                        put("behavior", "deny")
                    }
                }
            }
        }
        val json = DaemonJson.encodeToString(JsonObject.serializer(), msg)
        Log.d(TAG, "respondToPermission agentId=$agentId reqId=$permissionRequestId allow=$allow")
        webSocket?.send(json)
    }

    fun setAgentMode(agentId: String, modeId: String) {
        val msg = buildJsonObject {
            put("type", "session")
            putJsonObject("message") {
                put("type", "set_agent_mode_request")
                put("agentId", agentId)
                put("modeId", modeId)
                put("requestId", UUID.randomUUID().toString())
            }
        }
        val json = DaemonJson.encodeToString(JsonObject.serializer(), msg)
        Log.d(TAG, "setAgentMode agentId=$agentId modeId=$modeId")
        webSocket?.send(json)
    }

    fun sendAgentMessage(agentId: String, text: String) {
        val msg = buildJsonObject {
            put("type", "session")
            putJsonObject("message") {
                put("type", "send_agent_message_request")
                put("requestId", UUID.randomUUID().toString())
                put("agentId", agentId)
                put("text", text)
            }
        }
        val json = DaemonJson.encodeToString(JsonObject.serializer(), msg)
        Log.d(TAG, "sendAgentMessage agentId=$agentId text=${text.take(80)}")
        webSocket?.send(json)
    }

    private fun doConnect() {
        val host = currentHost ?: return
        val url = "ws://$host/ws"
        sessionReady = false
        Log.d(TAG, "doConnect() url=$url")
        val request = Request.Builder().url(url).apply {
            if (currentPassword.isNotEmpty()) {
                header("Sec-WebSocket-Protocol", "paseo.bearer.$currentPassword")
            }
        }.build()
        webSocket = httpClient.newWebSocket(request, DaemonListener())
    }

    private fun sendHello() {
        val hello = HelloEnvelope(clientId = clientId)
        val json = DaemonJson.encodeToString(HelloEnvelope.serializer(), hello)
        Log.d(TAG, "sendHello() $json")
        webSocket?.send(json)
    }

    private fun sendPing() {
        val ping = PingEnvelope(
            requestId = UUID.randomUUID().toString(),
            clientSentAt = System.currentTimeMillis(),
        )
        val json = DaemonJson.encodeToString(PingEnvelope.serializer(), ping)
        webSocket?.send(json)
    }

    private fun sendFetchAgents() {
        val subscriptionId = UUID.randomUUID().toString()
        val msg = buildJsonObject {
            put("type", "session")
            putJsonObject("message") {
                put("type", "fetch_agents_request")
                put("requestId", UUID.randomUUID().toString())
                putJsonObject("subscribe") {
                    put("subscriptionId", subscriptionId)
                }
            }
        }
        val json = DaemonJson.encodeToString(JsonObject.serializer(), msg)
        Log.d(TAG, "sendFetchAgents() $json")
        webSocket?.send(json)
    }

    private fun scheduleReconnect() {
        if (currentHost == null) return
        reconnectAttempts++
        val delayMs = (1000L shl (reconnectAttempts - 1).coerceAtMost(4))
            .coerceAtMost(MAX_RECONNECT_MS)
        Log.d(TAG, "scheduleReconnect() attempt=$reconnectAttempts delay=${delayMs}ms")
        reconnectJob = scope.launch {
            delay(delayMs)
            if (currentHost != null) {
                _connectionState.value = ConnectionState.Connecting
                doConnect()
            }
        }
    }

    private fun handleMessage(text: String) {
        try {
            val element = DaemonJson.parseToJsonElement(text)
            val obj = element.jsonObject
            val type = obj["type"]?.jsonPrimitive?.contentOrNull
            when (type) {
                "pong" -> { Log.v(TAG, "pong received") }
                "session" -> {
                    val message = obj["message"]?.jsonObject
                    if (message != null) handleSessionMessage(message)
                }
                else -> Log.d(TAG, "unknown message type=$type: ${text.take(200)}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "failed to parse message: ${text.take(200)}", e)
        }
    }

    private fun handleSessionMessage(message: JsonObject) {
        val msgType = message["type"]?.jsonPrimitive?.contentOrNull ?: return
        when (msgType) {
            "status" -> handleStatusMessage(message)
            "fetch_agents_response" -> handleFetchAgentsResponse(message)
            "agent_update" -> handleAgentUpdate(message)
            "rpc_error" -> {
                val payload = message["payload"]?.jsonObject
                val error = payload?.get("error")?.jsonPrimitive?.contentOrNull
                Log.w(TAG, "rpc_error: $error")
            }
            "send_agent_message_response" -> {
                val payload = message["payload"]?.jsonObject
                val accepted = payload?.get("accepted")?.jsonPrimitive?.contentOrNull
                val error = payload?.get("error")?.jsonPrimitive?.contentOrNull
                if (accepted == "false" || error != null) {
                    Log.w(TAG, "send_agent_message rejected: $error")
                } else {
                    Log.d(TAG, "send_agent_message accepted")
                }
            }
            else -> Log.d(TAG, "session message type=$msgType: ${message.toString().take(200)}")
        }
    }

    private fun handleStatusMessage(message: JsonObject) {
        val payload = message["payload"]?.jsonObject ?: return
        val status = payload["status"]?.jsonPrimitive?.contentOrNull ?: return
        if (status == "server_info" && !sessionReady) {
            Log.d(TAG, "server_info received — requesting agents")
            sessionReady = true
            sendFetchAgents()
        }
    }

    private fun handleFetchAgentsResponse(message: JsonObject) {
        val payload = message["payload"]?.jsonObject ?: return
        val entries = payload["entries"]?.jsonArray ?: return
        val agents = entries.mapNotNull { entry ->
            val agentObj = entry.jsonObject["agent"]?.jsonObject
            if (agentObj != null) parseAgentSnapshot(agentObj) else null
        }
        Log.d(TAG, "fetch_agents_response: ${agents.size} agents")
        _agents.value = agents
    }

    private fun handleAgentUpdate(message: JsonObject) {
        val payload = message["payload"]?.jsonObject ?: return
        val kind = payload["kind"]?.jsonPrimitive?.contentOrNull ?: return
        when (kind) {
            "upsert" -> {
                val agentObj = payload["agent"]?.jsonObject ?: return
                val session = parseAgentSnapshot(agentObj) ?: return
                Log.d(TAG, "agent_update upsert: ${session.id} state=${session.state}")
                _agents.value = _agents.value.toMutableList().apply {
                    val idx = indexOfFirst { it.id == session.id }
                    if (idx >= 0) this[idx] = session else add(session)
                }
            }
            "remove" -> {
                val agentId = payload["agentId"]?.jsonPrimitive?.contentOrNull ?: return
                Log.d(TAG, "agent_update remove: $agentId")
                _agents.value = _agents.value.filter { it.id != agentId }
            }
        }
    }

    private fun parseAgentSnapshot(agent: JsonObject): AgentSession? {
        val id = agent["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val cwd = agent["cwd"]?.jsonPrimitive?.contentOrNull ?: ""
        val title = agent["title"]?.jsonPrimitive?.contentOrNull
            ?: cwd.substringAfterLast('/').ifBlank { "Agent" }
        val provider = agent["provider"]?.jsonPrimitive?.contentOrNull ?: "unknown"
        val model = agent["model"]?.jsonPrimitive?.contentOrNull ?: ""
        val status = agent["status"]?.jsonPrimitive?.contentOrNull ?: "idle"

        val pendingPerms = agent["pendingPermissions"] as? JsonArray
        val hasPendingPerms = pendingPerms != null && pendingPerms.isNotEmpty()
        val attentionReason = agent["attentionReason"]?.jsonPrimitive?.contentOrNull
        val lastError = agent["lastError"]?.jsonPrimitive?.contentOrNull

        val firstPerm = pendingPerms?.firstOrNull()?.jsonObject
        val permId = firstPerm?.get("id")?.jsonPrimitive?.contentOrNull
        val permDesc = firstPerm?.get("description")?.jsonPrimitive?.contentOrNull
            ?: firstPerm?.get("title")?.jsonPrimitive?.contentOrNull
            ?: firstPerm?.get("name")?.jsonPrimitive?.contentOrNull

        val state = when {
            hasPendingPerms -> AgentState.AwaitingInput
            status == "running" -> AgentState.Running
            status == "error" -> AgentState.Error
            status == "closed" -> AgentState.Done
            status == "initializing" -> AgentState.Queued
            attentionReason == "finished" -> AgentState.Done
            attentionReason == "error" -> AgentState.Error
            else -> AgentState.Idle
        }

        val summary = lastError ?: permDesc ?: when (state) {
            AgentState.Running -> "Working in $cwd"
            AgentState.Done -> "Task complete."
            AgentState.Error -> "Agent encountered an error."
            AgentState.Queued -> "Starting up\u2026"
            AgentState.AwaitingInput -> "Waiting for approval."
            AgentState.Idle -> "Ready. Hold the button to talk."
        }

        val currentModeId = agent["currentModeId"]?.jsonPrimitive?.contentOrNull
        val mode = if (currentModeId == "plan") AgentMode.Plan else AgentMode.Build

        return AgentSession(
            id = id,
            title = title,
            provider = provider,
            model = model,
            state = state,
            summary = summary,
            mode = mode,
            pendingPermissionId = permId,
        )
    }

    private inner class DaemonListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "onOpen()")
            reconnectAttempts = 0
            sendHello()
            _connectionState.value = ConnectionState.Connected
            pingJob?.cancel()
            pingJob = scope.launch {
                while (true) {
                    delay(PING_INTERVAL_MS)
                    sendPing()
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "onClosing() code=$code reason=$reason")
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "onClosed() code=$code reason=$reason")
            pingJob?.cancel()
            if (currentHost != null) scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "onFailure()", t)
            pingJob?.cancel()
            _connectionState.value = ConnectionState.Error
            if (currentHost != null) scheduleReconnect()
        }
    }
}
