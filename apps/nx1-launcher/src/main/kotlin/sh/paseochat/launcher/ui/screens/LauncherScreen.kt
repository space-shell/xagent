package sh.paseochat.launcher.ui.screens

import android.app.Activity
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID
import sh.paseochat.launcher.daemon.ConnectionManager
import sh.paseochat.launcher.daemon.models.ConnectionState
import sh.paseochat.launcher.daemon.models.DaemonJson
import sh.paseochat.launcher.model.AgentMode
import sh.paseochat.launcher.model.AgentSession
import sh.paseochat.launcher.model.AgentState
import sh.paseochat.launcher.model.ConnectionProfile
import sh.paseochat.launcher.model.ConnectionType
import sh.paseochat.launcher.model.CreateAgentResult
import sh.paseochat.launcher.model.SessionShortcut
import sh.paseochat.launcher.model.SidebarSide
import sh.paseochat.launcher.model.parseOfferFromUrl
import sh.paseochat.launcher.service.PaseoConnectionService
import sh.paseochat.launcher.ui.components.AgentCard
import sh.paseochat.launcher.ui.components.AppsCard
import sh.paseochat.launcher.ui.components.ConnectionCard
import sh.paseochat.launcher.ui.components.HomeCard
import sh.paseochat.launcher.ui.components.SettingsCard
import sh.paseochat.launcher.ui.theme.PaseoTheme
import sh.paseochat.launcher.voice.rememberVoiceController

private val DECK_CARD_HEIGHT = 360.dp
private val FAN_STEP = 32.dp
private val BELOW_STEP = 480.dp
private const val Z_PER_RANK = 0.20f

private const val SWIPE_SENSITIVITY = 1.6f

private const val HOME_LISTENING_ID = "__home_wizard__"
private sealed class DeckPage(val key: String) {
    data object Home : DeckPage("__home__")
    data class Agent(val session: AgentSession) : DeckPage(session.id)
    data object Apps : DeckPage("__apps__")
    data object Settings : DeckPage("__settings__")
    data class Connection(val profile: ConnectionProfile) : DeckPage("conn_${profile.id}")
}

@Composable
fun LauncherScreen(attentionAgentId: String? = null) {
    val context = LocalContext.current
    var service by remember { mutableStateOf<PaseoConnectionService?>(null) }
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val b = binder as? PaseoConnectionService.LocalBinder ?: return
                service = b.service()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
    }
    DisposableEffect(Unit) {
        val intent = Intent(context, PaseoConnectionService::class.java)
        ContextCompat.startForegroundService(context, intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        onDispose {
            context.unbindService(serviceConnection)
        }
    }
    val svc = service
    if (svc != null) {
        LauncherScreenContent(svc.connectionManager, attentionAgentId)
    } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun LauncherScreenContent(connectionManager: ConnectionManager, attentionAgentId: String? = null) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val voice = rememberVoiceController()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var listeningId by remember { mutableStateOf<String?>(null) }
    var pendingTranscript by remember { mutableStateOf<String?>(null) }
    var pendingSessionId by remember { mutableStateOf<String?>(null) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingListenId by remember { mutableStateOf<String?>(null) }

    val sessions by connectionManager.allAgents.collectAsState()
    val connectionStates by connectionManager.connectionStates.collectAsState()
    val serverNames by connectionManager.serverNames.collectAsState()

    val prefs = remember { context.getSharedPreferences("daemon", Context.MODE_PRIVATE) }
    var profiles by remember { mutableStateOf(loadProfiles(prefs)) }
    var sessionShortcuts by remember { mutableStateOf(loadShortcuts(prefs)) }
    var hideStatusBar by remember { mutableStateOf(prefs.getBoolean("hide_status_bar", false)) }
    var keepAlive by remember { mutableStateOf(prefs.getBoolean("keep_alive_in_background", true)) }
    var keepAlivePrompted by remember { mutableStateOf(prefs.getBoolean("keep_alive_prompted", false)) }

    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager }
    val isIgnoringBatteryOptimizations = remember(keepAlive) {
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun launchBatteryOptimizationPrompt() {
        runCatching {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun onKeepAliveToggled(value: Boolean) {
        keepAlive = value
        prefs.edit().putBoolean("keep_alive_in_background", value).apply()
        if (value && !isIgnoringBatteryOptimizations) {
            launchBatteryOptimizationPrompt()
        }
    }

    LaunchedEffect(Unit) {
        if (!keepAlivePrompted && keepAlive && !isIgnoringBatteryOptimizations) {
            prefs.edit().putBoolean("keep_alive_prompted", true).apply()
            keepAlivePrompted = true
            launchBatteryOptimizationPrompt()
        }
    }
    var sidebarSide by remember {
        mutableStateOf(
            when (prefs.getString("sidebar_side", "right")) {
                "left" -> SidebarSide.Left
                "off" -> SidebarSide.Off
                else -> SidebarSide.Right
            },
        )
    }
    LaunchedEffect(sidebarSide) {
        prefs.edit().putString(
            "sidebar_side",
            when (sidebarSide) {
                SidebarSide.Left -> "left"
                SidebarSide.Off -> "off"
                SidebarSide.Right -> "right"
            },
        ).apply()
    }

    LaunchedEffect(Unit) {
        var current = profiles
        if (current.isEmpty()) {
            val oldHost = prefs.getString("host", null)
            if (oldHost != null) {
                val migrated = ConnectionProfile(
                    id = UUID.randomUUID().toString(),
                    host = oldHost,
                    password = prefs.getString("password", "") ?: "",
                )
                current = listOf(migrated)
                profiles = current
                saveProfiles(prefs, current)
            }
        }
        current.forEach { p ->
            val canConnect = when (p.connectionType) {
                ConnectionType.DIRECT -> p.host.isNotBlank()
                ConnectionType.RELAY -> p.serverId.isNotBlank()
            }
            if (canConnect) connectionManager.connect(p)
        }
    }

    val view = LocalView.current
    LaunchedEffect(hideStatusBar) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        if (hideStatusBar) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
        prefs.edit().putBoolean("hide_status_bar", hideStatusBar).apply()
    }

    LaunchedEffect(Unit) {
        voice.onError = { msg ->
            listeningId = null
            scope.launch { snackbarHostState.showSnackbar(msg) }
        }
    }

    fun startListening(sessionId: String) {
        voice.onFinal = { text ->
            if (text.isNotBlank()) {
                pendingSessionId = sessionId
                pendingTranscript = text
            }
            listeningId = null
            voice.reset()
        }
        voice.start()
        if (voice.error != null) {
            listeningId = null
        } else {
            listeningId = sessionId
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasMicPermission = granted
        val pid = pendingListenId
        pendingListenId = null
        if (granted && pid != null) {
            startListening(pid)
        } else if (!granted) {
            scope.launch { snackbarHostState.showSnackbar("Microphone permission denied") }
        }
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        Log.d("LauncherScreen", "POST_NOTIFICATIONS granted=$granted")
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val pages = remember(sessions, profiles) {
        listOf(DeckPage.Home) +
            sessions.map { DeckPage.Agent(it) } +
            listOf(DeckPage.Apps, DeckPage.Settings) +
            profiles.map { DeckPage.Connection(it) }
    }

    val density = LocalDensity.current
    val maxIndex = (pages.size - 1).coerceAtLeast(0)
    val offset = remember { Animatable(0f) }
    val currentPage by remember(offset) {
        derivedStateOf {
            offset.value.roundToInt().coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        }
    }

    val workspacesByProfile by connectionManager.workspaces.collectAsState()
    val providerModelsByProfile by connectionManager.providerModels.collectAsState()
    val serverIds by connectionManager.serverIds.collectAsState()
    val lastCreatedAgentId by connectionManager.lastCreatedAgentId.collectAsState()
    val wizardError by connectionManager.wizardError.collectAsState()

    var homeResetSignal by remember { mutableIntStateOf(0) }
    var homeWizardVisible by remember { mutableStateOf(false) }
    var createdAgentSignal by remember { mutableIntStateOf(0) }
    var createdAgentInfo by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(currentPage) {
        val homeVisible = currentPage == 0 && pages.firstOrNull() is DeckPage.Home
        if (homeVisible && !homeWizardVisible) {
            connectionManager.refreshWizardData()
        }
        homeWizardVisible = homeVisible
    }

    LaunchedEffect(lastCreatedAgentId) {
        val id = lastCreatedAgentId ?: return@LaunchedEffect
        connectionManager.consumeLastCreatedAgentId()
    }

    LaunchedEffect(attentionAgentId, pages) {
        val id = attentionAgentId ?: return@LaunchedEffect
        val idx = pages.indexOfFirst { it is DeckPage.Agent && it.session.id == id }
        if (idx >= 0 && idx != offset.value.roundToInt()) {
            scope.launch {
                offset.animateTo(idx.toFloat(), spring(dampingRatio = Spring.DampingRatioLowBouncy))
            }
        }
    }

    val shPaseoInstalled = remember {
        runCatching {
            context.packageManager.getPackageInfo("sh.paseo", 0)
        }.isSuccess
    }
    val launchPaseo: () -> Unit = {
        runCatching {
            context.packageManager.getLaunchIntentForPackage("sh.paseo")?.let {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    LaunchedEffect(maxIndex) {
        if (offset.value > maxIndex) offset.snapTo(maxIndex.toFloat())
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        BoxWithConstraints(modifier = Modifier.padding(padding)) {
            val viewportHeight = maxHeight
            val pageHeightPx = with(density) { viewportHeight.toPx() }
            val focusedTopPx = with(density) { ((viewportHeight - DECK_CARD_HEIGHT) / 2f).toPx() }
            val fanStepPx = with(density) { FAN_STEP.toPx() }
            val belowStepPx = with(density) { BELOW_STEP.toPx() }

            var velocityTracker by remember { mutableStateOf(VelocityTracker()) }

            val deckScrollConnection = remember(pageHeightPx, maxIndex) {
                object : NestedScrollConnection {
                    var childOverflowed = false

                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        if (source != NestedScrollSource.UserInput) return Offset.Zero
                        if (pageHeightPx > 0f && available.y != 0f) {
                            childOverflowed = true
                            val delta = (-available.y / pageHeightPx) * SWIPE_SENSITIVITY
                            val target = (offset.value + delta).coerceIn(0f, maxIndex.toFloat())
                            scope.launch { offset.snapTo(target) }
                            return available
                        }
                        childOverflowed = false
                        return Offset.Zero
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (!childOverflowed) return Velocity.Zero
                        childOverflowed = false

                        val current = offset.value
                        val v = available.y
                        val predicted = current - (v / pageHeightPx) * 0.15f * SWIPE_SENSITIVITY
                        val target = predicted.roundToInt().coerceIn(0, maxIndex).toFloat()
                        if (target != current) {
                            offset.animateTo(target, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                        }
                        return available
                    }

                    override suspend fun onPostFling(
                        consumed: Velocity,
                        available: Velocity,
                    ): Velocity {
                        val current = offset.value
                        val target = current.roundToInt().coerceIn(0, maxIndex).toFloat()
                        if (target != current) {
                            offset.animateTo(target, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                        }
                        return available
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .nestedScroll(deckScrollConnection)
                    .pointerInput(pages.size, pageHeightPx) {
                        detectVerticalDragGestures(
                            onDragStart = { velocityTracker = VelocityTracker() },
                            onVerticalDrag = { change, dy ->
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                if (pageHeightPx > 0f) {
                                    val delta = (-dy / pageHeightPx) * SWIPE_SENSITIVITY
                                    val target = (offset.value + delta).coerceIn(0f, maxIndex.toFloat())
                                    scope.launch { offset.snapTo(target) }
                                }
                                change.consume()
                            },
                            onDragEnd = {
                                if (pageHeightPx > 0f) {
                                    val v = velocityTracker.calculateVelocity().y
                                    val predicted = offset.value - (v / pageHeightPx) * 0.15f * SWIPE_SENSITIVITY
                                    val target = predicted.roundToInt().coerceIn(0, maxIndex).toFloat()
                                    scope.launch {
                                        offset.animateTo(target, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                    }
                                }
                            },
                            onDragCancel = {
                                if (pageHeightPx > 0f) {
                                    val target = offset.value.roundToInt().coerceIn(0, maxIndex).toFloat()
                                    scope.launch {
                                        offset.animateTo(target, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                    }
                                }
                            },
                        )
                    }
            ) {
                    val cardPaddingStart: Dp
                    val cardPaddingEnd: Dp
                    when (sidebarSide) {
                        SidebarSide.Left -> {
                            cardPaddingStart = 40.dp; cardPaddingEnd = 16.dp
                        }
                        SidebarSide.Right -> {
                            cardPaddingStart = 16.dp; cardPaddingEnd = 40.dp
                        }
                        SidebarSide.Off -> {
                            cardPaddingStart = 16.dp; cardPaddingEnd = 16.dp
                        }
                    }

                    pages.forEachIndexed { pageIndex, page ->
                        val zIndex by remember(pageIndex, pages.size) {
                            derivedStateOf {
                                val oc = offset.value - pageIndex
                                val kc = abs(oc)
                                val outgoingBias = if (oc > 0f) 0.1f else 0f
                                1f - Z_PER_RANK * kc + outgoingBias
                            }
                        }

                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(DECK_CARD_HEIGHT)
                                .padding(start = cardPaddingStart, end = cardPaddingEnd)
                                .zIndex(zIndex)
                                .graphicsLayer {
                                    val oc = offset.value - pageIndex
                                    val kc = abs(oc)
                                    val peek = oc <= 0f
                                    translationY = if (peek) focusedTopPx + fanStepPx * kc
                                                   else focusedTopPx - belowStepPx * oc
                                    alpha = if (peek) (4f + oc).coerceIn(0f, 1f) else 1f
                                },
                        ) {
                        if (abs(currentPage - pageIndex) <= 3) when (page) {
                            is DeckPage.Agent -> {
                                val session = page.session
                                AgentCard(
                                    session,
                                    serverName = session.serverName,
                                    listening = listeningId == session.id,
                                    partialText = if (listeningId == session.id) voice.partialText else "",
                                    pendingTranscript = if (pendingSessionId == session.id) pendingTranscript else null,
                                    onMicDown = {
                                        if (!voice.isListening) {
                                            if (hasMicPermission) {
                                                startListening(session.id)
                                            } else {
                                                pendingListenId = session.id
                                                permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                    },
                                    onMicUp = {
                                        if (listeningId == session.id) voice.stop()
                                    },
                                    onCycleMode = {
                                        val newModeId = if (session.mode == AgentMode.Plan) session.buildModeId else session.planModeId
                                        connectionManager.setAgentMode(session.id, newModeId)
                                    },
                                    onApprove = { permId ->
                                        connectionManager.respondToPermission(session.id, permId, allow = true)
                                    },
                                    onApproveAlways = { permId ->
                                        connectionManager.respondToPermission(session.id, permId, allow = true)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Allowed \u2014 future requests still need approval")
                                        }
                                    },
                                    onDeny = { permId ->
                                        connectionManager.respondToPermission(session.id, permId, allow = false)
                                    },
                                    onAnswerQuestion = { permId, answers ->
                                        connectionManager.respondToQuestion(session.id, permId, answers)
                                    },
                                    onArchive = {
                                        connectionManager.archiveAgent(session.id)
                                    },
                                    onConfirmTranscript = {
                                        connectionManager.sendAgentMessage(session.id, pendingTranscript ?: "")
                                        pendingTranscript = null
                                        pendingSessionId = null
                                    },
                                    onCancelTranscript = {
                                        pendingTranscript = null
                                        pendingSessionId = null
                                    },
                                    onOpenInPaseo = {
                                        if (session.serverId.isNotBlank()) {
                                            try {
                                                val uri = Uri.parse("paseo://h/${session.serverId}/agent/${session.id}")
                                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                            } catch (e: ActivityNotFoundException) {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Paseo app not installed")
                                                }
                                            } catch (e: Throwable) {
                                                Log.w("LauncherScreen", "deep link failed", e)
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Paseo failed to open: ${e.message ?: e.javaClass.simpleName}")
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            is DeckPage.Home -> {
                                HomeCard(
                                    profiles = profiles,
                                    connectionStates = connectionStates,
                                    workspacesByProfile = workspacesByProfile,
                                    providerModelsByProfile = providerModelsByProfile,
                                    serverNames = serverNames,
                                    shortcuts = sessionShortcuts,
                                    shPaseoInstalled = shPaseoInstalled,
                                    sidebarSide = sidebarSide,
                                    resetSignal = homeResetSignal,
                                    createdAgentSignal = createdAgentSignal,
                                    onOpenCreatedAgent = {
                                        createdAgentInfo?.let { (agentId, sid) ->
                                            if (sid.isNotBlank()) {
                                                try {
                                                    val uri = Uri.parse("paseo://h/$sid/agent/$agentId")
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                                } catch (e: ActivityNotFoundException) {
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("Paseo app not installed")
                                                    }
                                                } catch (e: Throwable) {
                                                    Log.w("LauncherScreen", "deep link failed", e)
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("Paseo failed to open: ${e.message ?: e.javaClass.simpleName}")
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    listening = listeningId == HOME_LISTENING_ID,
                                    pendingTranscript = if (pendingSessionId == HOME_LISTENING_ID) pendingTranscript else null,
                                    wizardError = wizardError,
                                    onMicDown = {
                                        if (!voice.isListening) {
                                            if (hasMicPermission) {
                                                startListening(HOME_LISTENING_ID)
                                            } else {
                                                pendingListenId = HOME_LISTENING_ID
                                                permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                    },
                                    onMicUp = {
                                        if (listeningId == HOME_LISTENING_ID) voice.stop()
                                    },
                                    onCancelTranscript = {
                                        pendingTranscript = null
                                        pendingSessionId = null
                                    },
                                    onClearError = { connectionManager.clearWizardError() },
                                    onWizardReset = { createdAgentSignal = 0 },
                                    onCreateAgent = { pid, wid, cwd, prov, mid, prompt ->
                                        scope.launch {
                                            val result = connectionManager.createAgent(
                                                profileId = pid,
                                                workspaceId = wid,
                                                cwd = cwd,
                                                provider = prov,
                                                modelId = mid,
                                                initialPrompt = prompt,
                                            )
                                            if (result is CreateAgentResult.Success) {
                                                pendingTranscript = null
                                                pendingSessionId = null
                                                createdAgentInfo = result.agentId to (serverIds[pid] ?: "")
                                                createdAgentSignal++
                                                val serverLabel = serverNames[pid]?.ifBlank { null }
                                                    ?: profiles.firstOrNull { it.id == pid }?.label?.ifBlank { null }
                                                    ?: pid
                                                val wsLabel = workspacesByProfile[pid]
                                                    ?.firstOrNull { it.id == wid }?.label ?: wid
                                                val modLabel = providerModelsByProfile[pid]
                                                    ?.firstOrNull { it.provider == prov && it.modelId == mid }?.label
                                                    ?: "$prov/${mid ?: ""}"
                                                val shortcut = SessionShortcut(
                                                    profileId = pid,
                                                    workspaceId = wid,
                                                    cwd = cwd,
                                                    provider = prov,
                                                    modelId = mid,
                                                    serverLabel = serverLabel,
                                                    workspaceLabel = wsLabel,
                                                    modelLabel = modLabel,
                                                )
                                                val updated = listOf(shortcut) + sessionShortcuts.filterNot {
                                                    it.profileId == pid &&
                                                        it.workspaceId == wid &&
                                                        it.provider == prov &&
                                                        it.modelId == mid
                                                }.take(2)
                                                sessionShortcuts = updated
                                                saveShortcuts(prefs, updated)
                                            }
                                        }
                                    },
                                    onLaunchPaseo = launchPaseo,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            is DeckPage.Apps -> {
                                AppsCard(
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            is DeckPage.Settings -> {
                                SettingsCard(
                                    hideStatusBar = hideStatusBar,
                                    onHideStatusBarChange = { hideStatusBar = it },
                                    sidebarSide = sidebarSide,
                                    onSidebarSideChange = { sidebarSide = it },
                                    keepAlive = keepAlive,
                                    onKeepAliveChange = { onKeepAliveToggled(it) },
                                    onAddConnection = {
                                        val newProfile = ConnectionProfile(
                                            id = UUID.randomUUID().toString(),
                                        )
                                        profiles = profiles + newProfile
                                        saveProfiles(prefs, profiles)
                                        val newIdx = (sessions.size + 2 + profiles.size - 1).coerceAtLeast(0)
                                        scope.launch {
                                            offset.animateTo(newIdx.toFloat(), spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            is DeckPage.Connection -> {
                                val profile = page.profile
                                ConnectionCard(
                                    profile = profile,
                                    connectionState = connectionStates[profile.id] ?: ConnectionState.Disconnected,
                                    serverName = serverNames[profile.id] ?: "",
                                    onProfileChange = { updated ->
                                        profiles = profiles.map { if (it.id == profile.id) updated else it }
                                        saveProfiles(prefs, profiles)
                                    },
                                    onConnect = {
                                        connectionManager.connect(profile)
                                    },
                                    onDisconnect = {
                                        connectionManager.disconnect(profile.id)
                                    },
                                    onDelete = {
                                        connectionManager.disconnect(profile.id)
                                        profiles = profiles.filter { it.id != profile.id }
                                        saveProfiles(prefs, profiles)
                                    },
                                    onQrScanned = { url ->
                                        val offer = parseOfferFromUrl(url)
                                        if (offer != null) {
                                            val updated = profile.copy(
                                                connectionType = ConnectionType.RELAY,
                                                serverId = offer.serverId,
                                                daemonPublicKeyB64 = offer.daemonPublicKeyB64,
                                                relayEndpoint = offer.relay.endpoint,
                                                relayUseTls = offer.relay.useTls ?: true,
                                            )
                                            profiles = profiles.map { if (it.id == profile.id) updated else it }
                                            saveProfiles(prefs, profiles)
                                            connectionManager.connect(updated)
                                        } else {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Invalid QR code")
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }

            if (sidebarSide != SidebarSide.Off) {
                StatusRail(
                    pages = pages,
                    currentIndex = currentPage,
                    onTap = { idx ->
                        scope.launch {
                            offset.animateTo(idx.toFloat().coerceIn(0f, maxIndex.toFloat()), spring(dampingRatio = Spring.DampingRatioLowBouncy))
                        }
                    },
                    modifier = Modifier
                        .align(if (sidebarSide == SidebarSide.Left) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(
                            start = if (sidebarSide == SidebarSide.Left) 4.dp else 0.dp,
                            end = if (sidebarSide == SidebarSide.Right) 4.dp else 0.dp,
                        )
                        .fillMaxHeight(),
                )
            }
        }
    }
}

private fun loadProfiles(prefs: SharedPreferences): List<ConnectionProfile> {
    val json = prefs.getString("connection_profiles", null) ?: return emptyList()
    return try {
        DaemonJson.decodeFromString(ListSerializer(ConnectionProfile.serializer()), json)
    } catch (e: Exception) {
        emptyList()
    }
}

private fun saveProfiles(prefs: SharedPreferences, profiles: List<ConnectionProfile>) {
    val json = DaemonJson.encodeToString(ListSerializer(ConnectionProfile.serializer()), profiles)
    prefs.edit().putString("connection_profiles", json).apply()
}

private fun loadShortcuts(prefs: SharedPreferences): List<SessionShortcut> {
    val json = prefs.getString("session_shortcuts", null) ?: return emptyList()
    return try {
        DaemonJson.decodeFromString(ListSerializer(SessionShortcut.serializer()), json)
    } catch (e: Exception) {
        emptyList()
    }
}

private fun saveShortcuts(prefs: SharedPreferences, shortcuts: List<SessionShortcut>) {
    val json = DaemonJson.encodeToString(ListSerializer(SessionShortcut.serializer()), shortcuts)
    prefs.edit().putString("session_shortcuts", json).apply()
}

@Composable
private fun StatusRail(
    pages: List<DeckPage>,
    currentIndex: Int,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val appsIndex = pages.indexOfFirst { it is DeckPage.Apps }
    val settingsIndex = pages.indexOfFirst { it is DeckPage.Settings }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        SidebarIcon(
            icon = Icons.Outlined.Home,
            focused = currentIndex == 0,
            tint = cs.onSurfaceVariant,
            onTap = { onTap(0) },
        )

        pages.forEachIndexed { idx, page ->
            if (page is DeckPage.Agent) {
                val s = page.session
                if (s.state == AgentState.AwaitingInput || s.state == AgentState.Error) {
                    AttentionDot(
                        focused = currentIndex == idx,
                        onTap = { onTap(idx) },
                    )
                }
            }
        }

        if (appsIndex >= 0) {
            SidebarIcon(
                icon = Icons.Outlined.Dashboard,
                focused = currentIndex == appsIndex,
                tint = cs.onSurfaceVariant,
                onTap = { onTap(appsIndex) },
            )
        }

        if (settingsIndex >= 0) {
            SidebarIcon(
                icon = Icons.Outlined.Settings,
                focused = currentIndex == settingsIndex,
                tint = cs.onSurfaceVariant,
                onTap = { onTap(settingsIndex) },
            )
        }
    }
}

@Composable
private fun SidebarIcon(
    icon: ImageVector,
    focused: Boolean,
    tint: Color,
    onTap: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clickable { onTap() }
            .size(28.dp)
            .then(
                if (focused) Modifier.background(cs.primary.copy(alpha = 0.15f), CircleShape)
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (focused) cs.primary else tint.copy(alpha = 0.6f),
            modifier = Modifier.size(if (focused) 22.dp else 18.dp),
        )
    }
}

@Composable
private fun AttentionDot(
    focused: Boolean,
    onTap: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clickable { onTap() }
            .size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (focused) 12.dp else 8.dp)
                .background(cs.error, CircleShape),
        )
    }
}

@Preview(showBackground = true, widthDp = 270, heightDp = 584, name = "Reference \u2014 roller deck")
@Composable
private fun LauncherDeckPreview() {
    PaseoTheme(darkTheme = false) { LauncherScreen() }
}
