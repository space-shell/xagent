package sh.paseochat.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlinx.coroutines.launch
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import sh.paseochat.launcher.daemon.PaseoDaemonClient
import sh.paseochat.launcher.daemon.models.ConnectionState
import sh.paseochat.launcher.ui.components.AgentCard
import sh.paseochat.launcher.voice.rememberVoiceController
import sh.paseochat.launcher.model.AgentMode
import sh.paseochat.launcher.model.AgentSession
import sh.paseochat.launcher.model.PermOption
import sh.paseochat.launcher.ui.components.SettingsCard
import sh.paseochat.launcher.ui.components.stateDotColor
import sh.paseochat.launcher.ui.theme.PaseoTheme

private val DECK_CARD_HEIGHT = 360.dp
private val FAN_STEP = 32.dp
private val BELOW_STEP = 480.dp
private const val Z_PER_RANK = 0.20f

@Composable
fun LauncherScreen() {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val voice = rememberVoiceController()
    val context = LocalContext.current
    var listeningId by remember { mutableStateOf<String?>(null) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingListenId by remember { mutableStateOf<String?>(null) }

    val daemonClient = remember { PaseoDaemonClient() }
    val connectionState by daemonClient.connectionState.collectAsState()
    val sessions by daemonClient.agents.collectAsState()
    var settingsHost by remember { mutableStateOf("100.127.193.39:6767") }
    var settingsPassword by remember { mutableStateOf("") }

    DisposableEffect(daemonClient) {
        onDispose { daemonClient.close() }
    }

    LaunchedEffect(Unit) {
        daemonClient.connect(settingsHost, settingsPassword)
    }

    LaunchedEffect(Unit) {
        voice.onError = { msg ->
            listeningId = null
            scope.launch { snackbarHostState.showSnackbar(msg) }
        }
    }

    fun startListening(sessionId: String) {
        voice.onFinal = { text ->
            daemonClient.sendAgentMessage(sessionId, text)
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

    val pagerState = rememberPagerState(pageCount = { sessions.size + 1 })

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        BoxWithConstraints(modifier = Modifier.padding(padding)) {
            val viewportHeight = maxHeight
            val focusedTop = (viewportHeight - DECK_CARD_HEIGHT) / 2f
            VerticalPager(
                state = pagerState,
                pageSize = PageSize.Fill,
                pageSpacing = 0.dp,
                contentPadding = PaddingValues(0.dp),
                beyondViewportPageCount = 3,
                key = { if (it < sessions.size) sessions[it].id else "__settings__" },
            ) { pageIndex ->
                val isSettings = pageIndex == sessions.size
                val o = pagerState.getOffsetDistanceInPages(pageIndex)
                val k = abs(o)
                val isPeek = o <= 0f
                val ty = if (isPeek) focusedTop - FAN_STEP * k else focusedTop + BELOW_STEP * o
                val cardAlpha = if (isPeek) (4f + o).coerceIn(0f, 1f) else 1f
                val z =
                    if (isPeek) 1f - Z_PER_RANK * k else (1f - 0.5f * o).coerceIn(0f, 1f)

                Box(Modifier.fillMaxSize().zIndex(z)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(DECK_CARD_HEIGHT)
                            .padding(start = 40.dp, end = 16.dp)
                            .graphicsLayer {
                                val naturalY = o * viewportHeight.toPx()
                                translationY = ty.toPx() - naturalY
                                alpha = cardAlpha
                            },
                    ) {
                        if (isSettings) {
                            SettingsCard(
                                host = settingsHost,
                                onHostChange = { settingsHost = it },
                                password = settingsPassword,
                                onPasswordChange = { settingsPassword = it },
                                connectionState = connectionState,
                                onConnect = {
                                    daemonClient.connect(settingsHost, settingsPassword)
                                },
                                onDisconnect = {
                                    daemonClient.disconnect()
                                },
                                onOpenLauncher = {
                                    val pm = context.packageManager
                                    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                                    val launchers = pm.queryIntentActivities(homeIntent, 0)
                                        .filter { it.activityInfo.packageName != context.packageName }
                                    launchers.firstOrNull()?.let { resolveInfo ->
                                        val intent = pm.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName)
                                        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        intent?.let { context.startActivity(it) }
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            val session = sessions[pageIndex]
                            AgentCard(
                                session,
                                listening = listeningId == session.id,
                                partialText = if (listeningId == session.id) voice.partialText else "",
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
                                    daemonClient.setAgentMode(session.id, newModeId)
                                },
                                onApprove = {
                                    val permId = session.pendingPermissionId
                                    if (permId != null) {
                                        daemonClient.respondToPermission(session.id, permId, allow = true)
                                    }
                                },
                                onApproveAlways = {
                                    val permId = session.pendingPermissionId
                                    if (permId != null) {
                                        daemonClient.respondToPermission(session.id, permId, allow = true)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Allowed \u2014 future requests still need approval")
                                        }
                                    }
                                },
                                onDeny = {
                                    val permId = session.pendingPermissionId
                                    if (permId != null) {
                                        daemonClient.respondToPermission(session.id, permId, allow = false)
                                    }
                                },
                                onSelectOption = { opt ->
                                    val permId = session.pendingPermissionId
                                    if (permId != null) {
                                        daemonClient.respondToPermissionWithAction(
                                            session.id, permId,
                                            selectedActionId = opt.id,
                                        )
                                    }
                                },
                                onCustomAnswer = { text ->
                                    val permId = session.pendingPermissionId
                                    if (permId != null) {
                                        daemonClient.respondToPermissionWithAction(
                                            session.id, permId,
                                            customAnswer = text,
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            StatusRail(
                sessions = sessions,
                currentIndex = pagerState.currentPage,
                viewportHeight = viewportHeight,
                onTap = { idx -> scope.launch { pagerState.animateScrollToPage(idx) } },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun StatusRail(
    sessions: List<AgentSession>,
    currentIndex: Int,
    viewportHeight: Dp,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val itemCount = sessions.size + 1
    val slotHeight = minOf(26.dp, viewportHeight / itemCount)
    val dotSize = minOf(8.dp, slotHeight * 0.3f)
    val focusedHeight = minOf(22.dp, slotHeight * 0.8f)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        sessions.forEachIndexed { index, session ->
            val focused = index == currentIndex
            val color = stateDotColor(session.state)
            val shape = if (focused) RoundedCornerShape(50) else CircleShape
            Box(
                modifier = Modifier
                    .pointerInput(index) { detectTapGestures { onTap(index) } }
                    .width(20.dp)
                    .height(slotHeight),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(dotSize)
                        .height(if (focused) focusedHeight else dotSize)
                        .background(color, shape),
                )
            }
        }
        val settingsFocused = sessions.size == currentIndex
        val settingsShape = if (settingsFocused) RoundedCornerShape(50) else CircleShape
        Box(
            modifier = Modifier
                .pointerInput(sessions.size) { detectTapGestures { onTap(sessions.size) } }
                .width(20.dp)
                .height(slotHeight),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(dotSize)
                    .height(if (settingsFocused) focusedHeight else dotSize)
                    .background(cs.outline, settingsShape),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 270, heightDp = 584, name = "NX1 \u2014 roller deck")
@Composable
private fun LauncherDeckPreview() {
    PaseoTheme(darkTheme = false) { LauncherScreen() }
}
