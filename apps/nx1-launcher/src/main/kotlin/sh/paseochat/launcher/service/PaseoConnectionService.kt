package sh.paseochat.launcher.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import sh.paseochat.launcher.MainActivity
import sh.paseochat.launcher.daemon.ConnectionManager
import sh.paseochat.launcher.daemon.models.ConnectionState
import sh.paseochat.launcher.model.AgentSession
import sh.paseochat.launcher.model.AgentState
import sh.paseochat.launcher.model.ConnectionProfile

private const val TAG = "PaseoConnectionService"
private const val CHANNEL_ID = "paseo_connection"
private const val NOTIFICATION_ID = 4711
private const val CHANNEL_ID_ATTENTION = "agent_attention"
private const val NOTIFICATION_ID_ATTENTION = 4712
const val EXTRA_ATTENTION_AGENT_ID = "sh.paseochat.launcher.EXTRA_ATTENTION_AGENT_ID"

class PaseoConnectionService : Service() {

    private val httpClient = OkHttpClient()
    val connectionManager: ConnectionManager = ConnectionManager(httpClient)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var stateJob: Job? = null
    private var wakeLockJob: Job? = null
    private var attentionJob: Job? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var foreground = false

    private val binder = LocalBinder()

    private var previousAgentStates: Map<String, AgentState> = emptyMap()
    private val attentionAgentIds: MutableSet<String> = linkedSetOf()
    private var lastAttentionAgentId: String? = null

    inner class LocalBinder : android.os.Binder() {
        fun service(): PaseoConnectionService = this@PaseoConnectionService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        ensureAttentionChannel()
        startForeground(NOTIFICATION_ID, buildNotification(0))
        foreground = true
        observeConnections()
        observeWakeLock()
        observeAgentAttention()
    }

    private fun observeConnections() {
        stateJob?.cancel()
        stateJob = scope.launch {
            connectionManager.connectionStates.collectLatest { states ->
                refreshNotification(states.count { it.value == ConnectionState.Connected })
            }
        }
    }

    private fun observeWakeLock() {
        wakeLockJob?.cancel()
        wakeLockJob = scope.launch {
            connectionManager.allAgents.collectLatest { agents ->
                val hasActive = agents.any { agent ->
                    agent.state == AgentState.Running ||
                        agent.state == AgentState.AwaitingInput ||
                        agent.state == AgentState.Queued
                }
                if (hasActive) acquireWakeLock() else releaseWakeLock()
            }
        }
    }

    private fun observeAgentAttention() {
        attentionJob?.cancel()
        attentionJob = scope.launch {
            connectionManager.allAgents.collectLatest { agents ->
                val byId = agents.associateBy { it.id }
                val currentStates = agents.associate { it.id to it.state }
                val newAttention = linkedSetOf<String>()
                for (agent in agents) {
                    val prev = previousAgentStates[agent.id]
                    val now = agent.state
                    if (now == AgentState.AwaitingInput || now == AgentState.Error) {
                        if (prev == null) {
                            Log.d(TAG, "agent ${agent.id} entered $now — added to attention set")
                            lastAttentionAgentId = agent.id
                        }
                        newAttention.add(agent.id)
                    }
                }
                val dropped = attentionAgentIds.filter { it !in newAttention }
                for (id in dropped) Log.d(TAG, "agent $id left attention set")
                previousAgentStates = currentStates
                attentionAgentIds.clear()
                attentionAgentIds.addAll(newAttention)
                refreshAttentionNotification(agents)
            }
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Paseo Connection",
                    NotificationManager.IMPORTANCE_MIN,
                ).apply {
                    description = "Keeps the daemon connection alive in the background"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun ensureAttentionChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID_ATTENTION) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID_ATTENTION,
                    "Agent attention",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Notifies when an agent needs approval or hit an error"
                    setShowBadge(true)
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "xagent:paseo-connection").apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.d(TAG, "wake lock acquired")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
            wakeLock = null
            Log.d(TAG, "wake lock released")
        }
    }

    private fun refreshNotification(connectedCount: Int) {
        if (!foreground) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(connectedCount))
    }

    private fun buildNotification(connectedCount: Int): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pi = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (connectedCount == 0) "Idle" else "Connected to $connectedCount server${if (connectedCount == 1) "" else "s"}"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("xagent")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pi)
            .build()
    }

    private fun refreshAttentionNotification(allAgents: List<AgentSession>) {
        val nm = getSystemService(NotificationManager::class.java)
        if (attentionAgentIds.isEmpty()) {
            nm.cancel(NOTIFICATION_ID_ATTENTION)
            lastAttentionAgentId = null
            return
        }
        val byId = allAgents.associateBy { it.id }
        val titles = attentionAgentIds.mapNotNull { byId[it]?.title?.takeIf(String::isNotBlank) ?: byId[it]?.id?.take(8) }
        val count = attentionAgentIds.size
        val title = if (count == 1) "1 agent needs attention" else "$count agents need attention"
        val text = titles.joinToString(", ").let { if (it.length > 80) it.take(77) + "..." else it }
        val targetId = lastAttentionAgentId ?: attentionAgentIds.firstOrNull()
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_ATTENTION_AGENT_ID, targetId)
        }
        val pi = PendingIntent.getActivity(
            this, 1, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID_ATTENTION)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(pi)
            .build()
        nm.notify(NOTIFICATION_ID_ATTENTION, notif)
    }

    fun setProfiles(profiles: List<ConnectionProfile>) {
        val currentIds = connectionManager.connectionStates.value.keys
        currentIds.minus(profiles.map { it.id }.toSet()).forEach { connectionManager.disconnect(it) }
        profiles.forEach { profile ->
            val currentState = connectionManager.getConnectionState(profile.id)
            if (currentState == ConnectionState.Disconnected || currentState == ConnectionState.Error) {
                connectionManager.connect(profile)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        return START_STICKY
    }

    override fun onDestroy() {
        stateJob?.cancel()
        wakeLockJob?.cancel()
        attentionJob?.cancel()
        releaseWakeLock()
        connectionManager.close()
        scope.cancel()
        super.onDestroy()
    }
}
