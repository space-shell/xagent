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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import sh.paseochat.launcher.daemon.models.ConnectionState
import sh.paseochat.launcher.daemon.models.DaemonJson
import sh.paseochat.launcher.daemon.models.HelloEnvelope
import sh.paseochat.launcher.daemon.models.PingEnvelope
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

    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private var currentHost: String? = null
    private var currentPassword: String = ""
    private val clientId = UUID.randomUUID().toString()
    private var reconnectAttempts = 0

    fun connect(host: String, password: String) {
        Log.d(TAG, "connect() host=$host")
        currentHost = host
        currentPassword = password
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
        pingJob?.cancel()
        reconnectJob?.cancel()
        webSocket?.close(1000, "client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    fun close() {
        disconnect()
        scope.cancel()
    }

    private fun doConnect() {
        val host = currentHost ?: return
        val url = "ws://$host/ws"
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
            val type = obj["type"]?.jsonPrimitive?.content
            when (type) {
                "pong" -> { Log.v(TAG, "pong received") }
                "session" -> {
                    Log.d(TAG, "session message: ${text.take(200)}")
                }
                else -> Log.d(TAG, "unknown message type=$type: ${text.take(200)}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "failed to parse message: ${text.take(200)}", e)
        }
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
