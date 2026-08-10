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
import okio.ByteString
import sh.paseochat.launcher.daemon.models.ConnectionState
import sh.paseochat.launcher.daemon.models.DaemonJson
import sh.paseochat.launcher.daemon.models.HelloEnvelope
import sh.paseochat.launcher.daemon.models.PingEnvelope
import sh.paseochat.launcher.model.AgentMode
import sh.paseochat.launcher.model.AgentSession
import sh.paseochat.launcher.model.AgentState
import sh.paseochat.launcher.model.PermOption
import java.util.UUID

private const val TAG = "PaseoDaemonClient"
private const val PING_INTERVAL_MS = 30_000L
private const val MAX_RECONNECT_MS = 30_000L
private const val HANDSHAKE_RETRY_MS = 1_000L

data class RelayConfig(
    val serverId: String,
    val daemonPublicKeyB64: String,
    val relayEndpoint: String,
    val relayUseTls: Boolean,
)

class PaseoDaemonClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _agents = MutableStateFlow<List<AgentSession>>(emptyList())
    val agents: StateFlow<List<AgentSession>> = _agents.asStateFlow()

    private val _serverName = MutableStateFlow("")
    val serverName: StateFlow<String> = _serverName.asStateFlow()

    private val _serverId = MutableStateFlow("")
    val serverId: StateFlow<String> = _serverId.asStateFlow()

    private val timelineSummaries = mutableMapOf<String, String>()

    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private var currentHost: String? = null
    private var currentPassword: String = ""
    private val clientId = UUID.randomUUID().toString()
    private var reconnectAttempts = 0
    private var sessionReady = false

    private var relayMode = false
    private var relayConfig: RelayConfig? = null
    private var e2eeReady = false
    private var e2eeSharedKey: ByteArray? = null
    private var clientKeyPair: E2eeCrypto.KeyPairData? = null
    private var daemonPublicKey: ByteArray? = null
    private var handshakeJob: Job? = null

    fun connect(host: String, password: String) {
        Log.d(TAG, "connect() host=$host")
        relayMode = false
        relayConfig = null
        e2eeReady = false
        e2eeSharedKey = null
        handshakeJob?.cancel()
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

    fun connectRelay(config: RelayConfig) {
        Log.d(TAG, "connectRelay() serverId=${config.serverId} relay=${config.relayEndpoint}")
        relayMode = true
        relayConfig = config
        currentHost = null
        currentPassword = ""
        sessionReady = false
        _serverId.value = config.serverId
        pingJob?.cancel()
        reconnectJob?.cancel()
        handshakeJob?.cancel()
        webSocket?.close(1000, "reconnect")
        webSocket = null
        reconnectAttempts = 0
        e2eeReady = false
        e2eeSharedKey = null
        _connectionState.value = ConnectionState.Connecting
        doConnect()
    }

    fun disconnect() {
        Log.d(TAG, "disconnect()")
        currentHost = null
        relayMode = false
        relayConfig = null
        e2eeReady = false
        e2eeSharedKey = null
        handshakeJob?.cancel()
        sessionReady = false
        pingJob?.cancel()
        reconnectJob?.cancel()
        webSocket?.close(1000, "client disconnect")
        webSocket = null
        timelineSummaries.clear()
        _serverName.value = ""
        _serverId.value = ""
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
        sendOrEncrypt(json)
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
        sendOrEncrypt(json)

        val current = _agents.value
        val idx = current.indexOfFirst { it.id == agentId }
        if (idx >= 0) {
            val session = current[idx]
            val optimisticMode = if (modeId == session.planModeId) AgentMode.Plan else AgentMode.Build
            _agents.value = current.toMutableList().apply {
                this[idx] = session.copy(mode = optimisticMode)
            }
            Log.d(TAG, "optimistic mode update: $agentId -> $optimisticMode")
        }
    }

    fun respondToPermissionWithAction(
        agentId: String,
        permissionRequestId: String,
        selectedActionId: String? = null,
        customAnswer: String? = null,
    ) {
        val msg = buildJsonObject {
            put("type", "session")
            putJsonObject("message") {
                put("type", "agent_permission_response")
                put("agentId", agentId)
                put("requestId", permissionRequestId)
                putJsonObject("response") {
                    if (selectedActionId != null) {
                        put("behavior", "allow")
                        put("selectedActionId", selectedActionId)
                    } else if (customAnswer != null) {
                        put("behavior", "allow")
                        putJsonObject("updatedInput") {
                            put("answer", customAnswer)
                        }
                    } else {
                        put("behavior", "allow")
                    }
                }
            }
        }
        val json = DaemonJson.encodeToString(JsonObject.serializer(), msg)
        Log.d(TAG, "respondToPermissionWithAction agentId=$agentId reqId=$permissionRequestId actionId=$selectedActionId custom=${customAnswer != null}")
        sendOrEncrypt(json)
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
        sendOrEncrypt(json)
    }

    fun archiveAgent(agentId: String) {
        val msg = buildJsonObject {
            put("type", "session")
            putJsonObject("message") {
                put("type", "archive_agent_request")
                put("agentId", agentId)
                put("requestId", UUID.randomUUID().toString())
            }
        }
        val json = DaemonJson.encodeToString(JsonObject.serializer(), msg)
        Log.d(TAG, "archiveAgent agentId=$agentId")
        sendOrEncrypt(json)
    }

    private fun doConnect() {
        if (relayMode) {
            val config = relayConfig ?: return
            sessionReady = false
            e2eeReady = false
            e2eeSharedKey = null
            clientKeyPair = E2eeCrypto.generateKeyPair()
            daemonPublicKey = E2eeCrypto.decodePublicKeyBase64(config.daemonPublicKeyB64)
            val protocol = if (config.relayUseTls) "wss" else "ws"
            val url = "$protocol://${config.relayEndpoint}/ws?serverId=${config.serverId}&role=client&v=2"
            Log.d(TAG, "doConnect() relay url=$url")
            val request = Request.Builder().url(url).build()
            webSocket = httpClient.newWebSocket(request, DaemonListener())
            return
        }
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

    private fun sendOrEncrypt(json: String) {
        val key = e2eeSharedKey
        if (relayMode && e2eeReady && key != null) {
            val encrypted = E2eeCrypto.encrypt(json.toByteArray(Charsets.UTF_8), key)
            webSocket?.send(ByteString.of(*encrypted))
        } else {
            webSocket?.send(json)
        }
    }

    private fun sendE2eeHello() {
        val clientPubKey = clientKeyPair?.publicKey ?: return
        val pubB64 = E2eeCrypto.encodePublicKeyBase64(clientPubKey)
        val hello = """{"type":"e2ee_hello","key":"$pubB64","capabilities":{"binaryCiphertext":true}}"""
        Log.d(TAG, "sendE2eeHello()")
        webSocket?.send(hello)
    }

    private fun startHandshakeRetry() {
        handshakeJob?.cancel()
        handshakeJob = scope.launch {
            while (!e2eeReady && relayMode) {
                delay(HANDSHAKE_RETRY_MS)
                if (!e2eeReady && relayMode) {
                    Log.d(TAG, "E2EE handshake retry...")
                    sendE2eeHello()
                }
            }
        }
    }

    private fun handleE2eeReady() {
        val daemonPub = daemonPublicKey ?: return
        val clientKP = clientKeyPair ?: return
        e2eeSharedKey = E2eeCrypto.deriveSharedKey(daemonPub, clientKP.secretKey)
        e2eeReady = true
        handshakeJob?.cancel()
        Log.d(TAG, "E2EE handshake complete")
        _connectionState.value = ConnectionState.Connected
        sendHello()
        pingJob?.cancel()
        pingJob = scope.launch {
            while (true) {
                delay(PING_INTERVAL_MS)
                sendPing()
            }
        }
    }

    private fun sendHello() {
        val hello = HelloEnvelope(clientId = clientId)
        val json = DaemonJson.encodeToString(HelloEnvelope.serializer(), hello)
        Log.d(TAG, "sendHello() $json")
        sendOrEncrypt(json)
    }

    private fun sendPing() {
        val ping = PingEnvelope(
            requestId = UUID.randomUUID().toString(),
            clientSentAt = System.currentTimeMillis(),
        )
        val json = DaemonJson.encodeToString(PingEnvelope.serializer(), ping)
        sendOrEncrypt(json)
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
        sendOrEncrypt(json)
    }

    private fun scheduleReconnect() {
        if (currentHost == null && relayConfig == null) return
        reconnectAttempts++
        val delayMs = (1000L shl (reconnectAttempts - 1).coerceAtMost(4))
            .coerceAtMost(MAX_RECONNECT_MS)
        Log.d(TAG, "scheduleReconnect() attempt=$reconnectAttempts delay=${delayMs}ms")
        reconnectJob = scope.launch {
            delay(delayMs)
            if (currentHost != null || relayConfig != null) {
                _connectionState.value = ConnectionState.Connecting
                doConnect()
            }
        }
    }

    private fun shouldReconnect(): Boolean = currentHost != null || (relayMode && relayConfig != null)

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
            "agent_stream" -> handleAgentStream(message)
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
            val hostname = payload["hostname"]?.jsonPrimitive?.contentOrNull
            _serverName.value = hostname ?: ""
            val sid = payload["serverId"]?.jsonPrimitive?.contentOrNull
            if (!sid.isNullOrBlank()) _serverId.value = sid
            Log.d(TAG, "server_info received — hostname=$hostname serverId=$sid")
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
        }.map { session ->
            timelineSummaries[session.id]?.let { session.copy(summary = it) } ?: session
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
                    val merged = timelineSummaries[session.id]?.let { session.copy(summary = it) } ?: session
                    if (idx >= 0) this[idx] = merged else add(merged)
                }
            }
            "remove" -> {
                val agentId = payload["agentId"]?.jsonPrimitive?.contentOrNull ?: return
                Log.d(TAG, "agent_update remove: $agentId")
                timelineSummaries.remove(agentId)
                _agents.value = _agents.value.filter { it.id != agentId }
            }
        }
    }

    private fun handleAgentStream(message: JsonObject) {
        val payload = message["payload"]?.jsonObject ?: return
        val agentId = payload["agentId"]?.jsonPrimitive?.contentOrNull ?: return
        val event = payload["event"]?.jsonObject ?: return
        val eventType = event["type"]?.jsonPrimitive?.contentOrNull ?: return

        if (eventType != "timeline") return

        val item = event["item"]?.jsonObject ?: return
        val itemType = item["type"]?.jsonPrimitive?.contentOrNull ?: return

        if (itemType != "assistant_message") return

        val text = item["text"]?.jsonPrimitive?.contentOrNull ?: return
        val summary = text.take(140).trim()

        timelineSummaries[agentId] = summary

        val current = _agents.value
        val idx = current.indexOfFirst { it.id == agentId }
        if (idx >= 0) {
            _agents.value = current.toMutableList().apply {
                this[idx] = this[idx].copy(summary = summary)
            }
            Log.d(TAG, "timeline summary updated for $agentId: ${summary.take(60)}")
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

        val availableModes = agent["availableModes"] as? JsonArray
        val modeIds = availableModes?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull } ?: emptyList()
        val planModeId = modeIds.firstOrNull { it.contains("plan") } ?: "plan"
        val buildModeId = modeIds.firstOrNull { !it.contains("plan") } ?: "auto"

        val pendingPerms = agent["pendingPermissions"] as? JsonArray
        val hasPendingPerms = pendingPerms != null && pendingPerms.isNotEmpty()
        val attentionReason = agent["attentionReason"]?.jsonPrimitive?.contentOrNull
        val lastError = agent["lastError"]?.jsonPrimitive?.contentOrNull

        val firstPerm = pendingPerms?.firstOrNull()?.jsonObject
        val permId = firstPerm?.get("id")?.jsonPrimitive?.contentOrNull
        val permKind = firstPerm?.get("kind")?.jsonPrimitive?.contentOrNull
        val permTitle = firstPerm?.get("title")?.jsonPrimitive?.contentOrNull
            ?: firstPerm?.get("description")?.jsonPrimitive?.contentOrNull
            ?: firstPerm?.get("name")?.jsonPrimitive?.contentOrNull
        val permDesc = firstPerm?.get("description")?.jsonPrimitive?.contentOrNull
            ?: firstPerm?.get("title")?.jsonPrimitive?.contentOrNull

        val permOptions = if (permKind == "question") {
            val actions = firstPerm?.get("actions") as? JsonArray
            actions?.mapNotNull { action ->
                val actionObj = action.jsonObject
                val actionId = actionObj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val actionLabel = actionObj["label"]?.jsonPrimitive?.contentOrNull ?: actionId
                val actionBehavior = actionObj["behavior"]?.jsonPrimitive?.contentOrNull ?: "allow"
                PermOption(id = actionId, label = actionLabel, allow = actionBehavior == "allow")
            } ?: emptyList()
        } else {
            emptyList()
        }

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
        val mode = if (currentModeId != null && currentModeId.contains("plan")) AgentMode.Plan else AgentMode.Build

        return AgentSession(
            id = id,
            title = title,
            provider = provider,
            model = model,
            state = state,
            summary = summary,
            mode = mode,
            planModeId = planModeId,
            buildModeId = buildModeId,
            pendingPermissionId = permId,
            permissionKind = permKind,
            permissionTitle = if (permKind == "question") permTitle else null,
            permissionOptions = permOptions,
            cwd = cwd,
        )
    }

    private inner class DaemonListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "onOpen()")
            reconnectAttempts = 0
            if (relayMode) {
                _connectionState.value = ConnectionState.Connecting
                sendE2eeHello()
                startHandshakeRetry()
            } else {
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
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (relayMode && !e2eeReady) {
                if (text.contains("\"e2ee_ready\"")) {
                    handleE2eeReady()
                } else {
                    Log.d(TAG, "pre-handshake text: ${text.take(120)}")
                }
                return
            }
            if (relayMode && e2eeReady) {
                val key = e2eeSharedKey
                if (key != null) {
                    val ciphertext = try {
                        android.util.Base64.decode(text, android.util.Base64.DEFAULT)
                    } catch (t: Throwable) {
                        Log.w(TAG, "E2EE text frame not base64: ${text.take(40)}")
                        return
                    }
                    val plaintext = E2eeCrypto.decrypt(ciphertext, key)
                    if (plaintext != null) {
                        handleMessage(String(plaintext, Charsets.UTF_8))
                    } else {
                        Log.w(TAG, "E2EE text decrypt failed (${ciphertext.size} bytes)")
                    }
                }
                return
            }
            handleMessage(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (relayMode && e2eeReady) {
                val key = e2eeSharedKey ?: return
                val plaintext = E2eeCrypto.decrypt(bytes.toByteArray(), key)
                if (plaintext != null) {
                    handleMessage(String(plaintext, Charsets.UTF_8))
                } else {
                    Log.w(TAG, "E2EE decrypt failed (${bytes.size} bytes)")
                }
            } else {
                Log.w(TAG, "unexpected binary message in non-relay or pre-handshake")
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "onClosing() code=$code reason=$reason")
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "onClosed() code=$code reason=$reason")
            pingJob?.cancel()
            handshakeJob?.cancel()
            e2eeReady = false
            if (shouldReconnect()) scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "onFailure()", t)
            pingJob?.cancel()
            handshakeJob?.cancel()
            e2eeReady = false
            _connectionState.value = ConnectionState.Error
            if (shouldReconnect()) scheduleReconnect()
        }
    }
}
