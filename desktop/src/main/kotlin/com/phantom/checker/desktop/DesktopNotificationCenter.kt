package com.phantom.checker.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.google.gson.Gson
import com.phantom.checker.shared.ChatBubbleDto
import com.phantom.checker.shared.ChatReplyRes
import com.phantom.checker.shared.ChatSendReq
import com.phantom.checker.shared.LinkMsgType
import com.phantom.checker.shared.MessageSummaryDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.util.UUID

/**
 * #497 桌面通知事件負載（供頂部橫幅、右下 Toast、懸浮助手氣泡共用，僅含既有鏡射欄位、嚴守隱私紅線）。
 */
data class MirrorNotificationEvent(
    val id: Long,
    val packageName: String,
    val appLabel: String,
    val sender: String,
    val preview: String,
    val riskLabel: String,
    val conversationKey: String,
    val timestamp: Long = System.currentTimeMillis(),
    val seq: Long = System.nanoTime()
)

/**
 * #497 桌面通知匯流排（application scope 單例，不干擾主視窗生命週期）。
 */
object DesktopNotificationBus {
    private val _latestBanner = MutableStateFlow<MirrorNotificationEvent?>(null)
    val latestBanner: StateFlow<MirrorNotificationEvent?> = _latestBanner.asStateFlow()

    private val _latestToast = MutableStateFlow<MirrorNotificationEvent?>(null)
    val latestToast: StateFlow<MirrorNotificationEvent?> = _latestToast.asStateFlow()

    private val _latestFloatingBubble = MutableStateFlow<MirrorNotificationEvent?>(null)
    val latestFloatingBubble: StateFlow<MirrorNotificationEvent?> = _latestFloatingBubble.asStateFlow()

    private val _floatingUnreadBadge = MutableStateFlow(0)
    val floatingUnreadBadge: StateFlow<Int> = _floatingUnreadBadge.asStateFlow()

    private val _pausedUntilMs = MutableStateFlow(0L)
    val pausedUntilMs: StateFlow<Long> = _pausedUntilMs.asStateFlow()

    private val _navigateToConversation = MutableStateFlow<MirrorNotificationEvent?>(null)
    val navigateToConversation: StateFlow<MirrorNotificationEvent?> = _navigateToConversation.asStateFlow()

    fun isPausedNow(nowMs: Long = System.currentTimeMillis()): Boolean = _pausedUntilMs.value > nowMs

    fun pauseForOneHour() {
        _pausedUntilMs.value = System.currentTimeMillis() + 60L * 60L * 1000L
    }

    fun resumeNotifications() {
        _pausedUntilMs.value = 0L
    }

    fun requestNavigateTo(event: MirrorNotificationEvent) {
        _navigateToConversation.value = event
    }

    fun consumeNavigation() {
        _navigateToConversation.value = null
    }

    fun clearBanner() {
        _latestBanner.value = null
    }

    fun clearToast() {
        _latestToast.value = null
    }

    fun clearFloatingBubble() {
        _latestFloatingBubble.value = null
    }

    fun clearFloatingBadge() {
        _floatingUnreadBadge.value = 0
    }

    fun dispatchNewMessage(
        msg: MessageSummaryDto,
        settings: DesktopSettings,
        onNativeTrayNotify: (title: String, body: String) -> Unit
    ) {
        val appName = DesktopAppLabels.resolveLabel(msg.packageName, msg.appLabel)
        val preview = msg.preview.take(settings.previewLength)
        val event = MirrorNotificationEvent(
            id = msg.id,
            packageName = msg.packageName,
            appLabel = appName,
            sender = msg.sender.ifBlank { appName },
            preview = preview,
            riskLabel = msg.riskLabel,
            conversationKey = msg.conversationKey,
            timestamp = msg.timestamp
        )

        val dndOrPaused = settings.isInDndWindow() || isPausedNow()

        // A. In-app top banner
        if (settings.bannerEnabled && (settings.mirrorMode == MirrorDisplayMode.BANNER || settings.mirrorMode == MirrorDisplayMode.BOTH || settings.mirrorMode == MirrorDisplayMode.COMBO)) {
            _latestBanner.value = event
        }

        // B. Bottom-right desktop toast (suppressed in DND/paused or fullscreen)
        if (!dndOrPaused && settings.toastEnabled && (settings.mirrorMode == MirrorDisplayMode.TOAST || settings.mirrorMode == MirrorDisplayMode.BOTH || settings.mirrorMode == MirrorDisplayMode.COMBO)) {
            if (!DesktopFullscreenDetector.isFullscreenAppActive()) {
                _latestToast.value = event
            }
        }

        // C. Floating assistant bubble or badge-only in DND/paused
        if (settings.floatingAssistantEnabled) {
            if (dndOrPaused) {
                _floatingUnreadBadge.value = (_floatingUnreadBadge.value + 1).coerceAtMost(99)
            } else {
                _latestFloatingBubble.value = event
                _floatingUnreadBadge.value = (_floatingUnreadBadge.value + 1).coerceAtMost(99)
            }
        }

        // Native OS tray notification
        if (!dndOrPaused && settings.nativeNotifications &&
            (settings.mirrorMode == MirrorDisplayMode.NATIVE || settings.mirrorMode == MirrorDisplayMode.BOTH || settings.mirrorMode == MirrorDisplayMode.COMBO)
        ) {
            onNativeTrayNotify("$appName · ${event.sender}", preview)
        }
    }
}

/**
 * #497 前景全螢幕偵測器（Windows 使用 SHQueryUserNotificationState 並快取 2 秒；非 Windows 退回 GraphicsDevice；僅於彈出 Toast 前按需檢查，零輪詢）。
 */
object DesktopFullscreenDetector {
    @Volatile
    private var lastCheckMs: Long = 0L

    @Volatile
    private var lastResult: Boolean = false

    private const val CACHE_MS = 2_000L

    fun isFullscreenAppActive(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastCheckMs < CACHE_MS) return lastResult
        val detected = runCatching {
            if (DesktopSettingsStore.isWindowsOs()) {
                queryWindowsNotificationStateBusy()
            } else {
                val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
                ge.screenDevices.any { it.fullScreenWindow != null }
            }
        }.getOrDefault(false)
        lastCheckMs = now
        lastResult = detected
        return detected
    }

    private fun queryWindowsNotificationStateBusy(): Boolean {
        // SHQueryUserNotificationState: QUNS_BUSY(2), QUNS_RUNNING_D3D_FULL_SCREEN(3), QUNS_PRESENTATION_MODE(4)
        val psScript = """
            Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public class QunsHelper { [DllImport("shell32.dll")] public static extern int SHQueryUserNotificationState(out int state); }' -ErrorAction SilentlyContinue
            ${'$'}s = 0; [void][QunsHelper]::SHQueryUserNotificationState([ref]${'$'}s); Write-Output ${'$'}s
        """.trimIndent()
        val proc = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", psScript)
            .redirectErrorStream(true)
            .start()
        val out = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor()
        val state = out.lineSequence().lastOrNull()?.trim()?.toIntOrNull() ?: return false
        return state in setOf(2, 3, 4)
    }
}

internal fun primaryWorkAreaBounds(): Rectangle = runCatching {
    GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
}.getOrDefault(Rectangle(0, 0, 1920, 1040))

/**
 * #497 B：獨立 always-on-top 無邊框右下 Toast 視窗（application scope）。
 */
@Composable
fun DesktopToastOverlayWindow(
    settings: DesktopSettings,
    onOpenConversation: (MirrorNotificationEvent) -> Unit
) {
    val toastEvent by DesktopNotificationBus.latestToast.collectAsState()
    val current = toastEvent ?: return

    val tokens = remember(settings.surfaceOpacity, settings.bgScrimAlpha, settings.highContrastText, settings.bgTheme) {
        DesktopThemeTokens.from(settings)
    }
    val accentColor = remember(settings.accentPreset, settings.customAccentHex) {
        DesktopPalette.resolveAccentColor(settings.accentPreset, settings.customAccentHex)
    }

    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()

    val bounds = remember(current.seq) { primaryWorkAreaBounds() }
    val toastW = 360
    val toastH = 112
    val toastX = (bounds.x + bounds.width - toastW - 18).coerceAtLeast(bounds.x)
    val toastY = (bounds.y + bounds.height - toastH - 18).coerceAtLeast(bounds.y)

    var visible by remember(current.seq) { mutableStateOf(false) }

    LaunchedEffect(current.seq) {
        visible = true
    }

    LaunchedEffect(current.seq, isHovered, settings.toastDurationSec) {
        val sec = settings.toastDurationSec
        if (sec <= 0 || isHovered) return@LaunchedEffect
        delay(sec * 1000L)
        visible = false
        delay(180L)
        DesktopNotificationBus.clearToast()
    }

    val toastWindowState = rememberWindowState(
        position = WindowPosition(toastX.dp, toastY.dp),
        size = DpSize(toastW.dp, toastH.dp)
    )

    Window(
        onCloseRequest = { DesktopNotificationBus.clearToast() },
        state = toastWindowState,
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = false,
        alwaysOnTop = true,
        title = DesktopStrings.appTitle
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                surface = tokens.surface,
                primary = accentColor,
                onSurface = tokens.textPrimary
            )
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(180)) + slideInHorizontally(tween(200)) { it / 2 },
                    exit = fadeOut(tween(150)) + slideOutHorizontally(tween(160)) { it / 2 }
                ) {
                    val appColor = DesktopAppLabels.resolveColor(current.packageName)
                    val riskColor = when (current.riskLabel.lowercase()) {
                        "scam" -> DesktopPalette.GlassError
                        "suspicious" -> DesktopPalette.GlassWarning
                        else -> DesktopPalette.GlassSuccess
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xF0151C2C))
                            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                            .hoverable(hoverSource)
                            .clickable {
                                DesktopNotificationBus.clearToast()
                                onOpenConversation(current)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(appColor.copy(alpha = 0.24f))
                                .border(1.dp, appColor.copy(alpha = 0.7f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = DesktopAppLabels.avatarInitial(current.sender, current.appLabel),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = appColor
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${current.appLabel} · ${current.sender}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tokens.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = DesktopStrings.riskLabelText(current.riskLabel),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = riskColor
                                )
                            }
                            Text(
                                text = current.preview.take(settings.previewLength),
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * #497 C：懸浮助手球（可拖曳＋放開吸附螢幕四邊＋位置持久化＋小型 AI 對話窗＋自適應方向氣泡通知）。
 */
@Composable
fun DesktopFloatingAssistantWindow(
    settings: DesktopSettings,
    server: DesktopLinkServer,
    onOpenMainWindow: () -> Unit,
    onOpenConversation: (MirrorNotificationEvent) -> Unit
) {
    if (!settings.floatingAssistantEnabled) return

    val gson = remember { Gson() }
    val scope = rememberCoroutineScope()
    val tokens = remember(settings.surfaceOpacity, settings.bgScrimAlpha, settings.highContrastText, settings.bgTheme) {
        DesktopThemeTokens.from(settings)
    }
    val accentColor = remember(settings.accentPreset, settings.customAccentHex) {
        DesktopPalette.resolveAccentColor(settings.accentPreset, settings.customAccentHex)
    }

    val bounds = remember { primaryWorkAreaBounds() }
    val orbSize = settings.floatingOrbSizeDp.coerceIn(28, 56)

    var orbX by remember {
        mutableIntStateOf(
            if (settings.floatingOrbX != Int.MIN_VALUE) settings.floatingOrbX.coerceIn(bounds.x, bounds.x + bounds.width - orbSize - 16)
            else bounds.x + bounds.width - orbSize - 24
        )
    }
    var orbY by remember {
        mutableIntStateOf(
            if (settings.floatingOrbY != Int.MIN_VALUE) settings.floatingOrbY.coerceIn(bounds.y, bounds.y + bounds.height - orbSize - 16)
            else bounds.y + bounds.height - orbSize - 120
        )
    }

    var aiExpanded by remember { mutableStateOf(false) }
    var miniChatInput by remember { mutableStateOf("") }
    var miniChatSending by remember { mutableStateOf(false) }
    var miniChatSessionId by remember { mutableLongStateOf(0L) }
    var miniChatBubbles by remember { mutableStateOf<List<ChatBubbleDto>>(emptyList()) }

    val bubbleEvent by DesktopNotificationBus.latestFloatingBubble.collectAsState()
    val unreadBadge by DesktopNotificationBus.floatingUnreadBadge.collectAsState()

    val orbHoverSource = remember { MutableInteractionSource() }
    val orbHovered by orbHoverSource.collectIsHoveredAsState()
    val bubbleHoverSource = remember { MutableInteractionSource() }
    val bubbleHovered by bubbleHoverSource.collectIsHoveredAsState()

    // Collect CHAT_REPLY for mini AI popup
    LaunchedEffect(server) {
        server.events.collect { env ->
            if (env.type == LinkMsgType.CHAT_REPLY) {
                val res = runCatching { gson.fromJson(env.payloadJson, ChatReplyRes::class.java) }.getOrNull() ?: return@collect
                if (miniChatSending) {
                    miniChatSending = false
                    if (res.sessionId > 0L) miniChatSessionId = res.sessionId
                    val content = if (res.ok) res.content else "${res.errorCode}: ${res.errorMessage}"
                    miniChatBubbles = (miniChatBubbles + ChatBubbleDto(System.currentTimeMillis(), "assistant", content, System.currentTimeMillis()))
                        .takeLast(40)
                }
            }
        }
    }

    // Auto-dismiss floating notification bubble (paused on hover)
    LaunchedEffect(bubbleEvent?.seq, bubbleHovered, settings.floatingBubbleSec) {
        if (bubbleEvent == null || bubbleHovered) return@LaunchedEffect
        val sec = settings.floatingBubbleSec.coerceIn(3, 12)
        delay(sec * 1000L)
        DesktopNotificationBus.clearFloatingBubble()
    }

    val isOnRightHalf = orbX > bounds.x + bounds.width / 2
    val isOnBottomHalf = orbY > bounds.y + bounds.height / 2

    val winW: Int
    val winH: Int
    val winX: Int
    val winY: Int
    when {
        aiExpanded -> {
            winW = 360
            winH = 440
            winX = (if (isOnRightHalf) orbX + orbSize - winW else orbX).coerceIn(bounds.x, (bounds.x + bounds.width - winW).coerceAtLeast(bounds.x))
            winY = (if (isOnBottomHalf) orbY + orbSize - winH else orbY).coerceIn(bounds.y, (bounds.y + bounds.height - winH).coerceAtLeast(bounds.y))
        }
        bubbleEvent != null -> {
            winW = orbSize + 296
            winH = maxOf(orbSize + 24, 108)
            winX = (if (isOnRightHalf) orbX + orbSize - winW else orbX).coerceIn(bounds.x, (bounds.x + bounds.width - winW).coerceAtLeast(bounds.x))
            winY = (if (isOnBottomHalf) orbY + orbSize - winH else orbY).coerceIn(bounds.y, (bounds.y + bounds.height - winH).coerceAtLeast(bounds.y))
        }
        else -> {
            winW = orbSize + 18
            winH = orbSize + 18
            winX = orbX.coerceIn(bounds.x, (bounds.x + bounds.width - winW).coerceAtLeast(bounds.x))
            winY = orbY.coerceIn(bounds.y, (bounds.y + bounds.height - winH).coerceAtLeast(bounds.y))
        }
    }

    val floatingState = rememberWindowState(
        position = WindowPosition(winX.dp, winY.dp),
        size = DpSize(winW.dp, winH.dp)
    )
    LaunchedEffect(winX, winY, winW, winH) {
        floatingState.position = WindowPosition(winX.dp, winY.dp)
        floatingState.size = DpSize(winW.dp, winH.dp)
    }

    fun snapOrbToNearestEdge() {
        val leftDist = orbX - bounds.x
        val rightDist = (bounds.x + bounds.width - orbSize - 18) - orbX
        val topDist = orbY - bounds.y
        val bottomDist = (bounds.y + bounds.height - orbSize - 18) - orbY
        val minDist = minOf(leftDist, rightDist, topDist, bottomDist)
        when (minDist) {
            leftDist -> orbX = bounds.x + 8
            rightDist -> orbX = bounds.x + bounds.width - orbSize - 24
            topDist -> orbY = bounds.y + 8
            else -> orbY = bounds.y + bounds.height - orbSize - 24
        }
        DesktopSettingsStore.update { it.copy(floatingOrbX = orbX, floatingOrbY = orbY) }
    }

    Window(
        onCloseRequest = { aiExpanded = false },
        state = floatingState,
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = aiExpanded,
        alwaysOnTop = true,
        title = DesktopStrings.floatingAssistantTitle
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                surface = tokens.surface,
                primary = accentColor,
                onSurface = tokens.textPrimary
            )
        ) {
            if (aiExpanded) {
                // Compact AI Chat Popup
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xF5121826))
                        .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(DesktopStrings.floatingAssistantTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { aiExpanded = false; onOpenMainWindow() }) { Text(DesktopStrings.trayOpen) }
                            TextButton(onClick = { aiExpanded = false }) { Text("✕") }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (miniChatBubbles.isEmpty()) {
                            item {
                                Text(DesktopStrings.emptyChat, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                            }
                        } else {
                            items(miniChatBubbles, key = { "${it.role}_${it.id}_${it.createdAt}" }) { b ->
                                val isUser = b.role == "user"
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isUser) accentColor.copy(alpha = 0.18f) else tokens.surfaceElevated)
                                        .padding(8.dp)
                                ) {
                                    if (isUser) {
                                        Text(b.content, style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
                                    } else {
                                        DesktopMarkdownText(markdown = b.content, textColor = tokens.textPrimary, accentColor = accentColor)
                                    }
                                }
                            }
                        }
                        if (miniChatSending) {
                            item {
                                Text(DesktopStrings.chatThinking, style = MaterialTheme.typography.labelSmall, color = accentColor)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = miniChatInput,
                            onValueChange = { miniChatInput = it },
                            placeholder = { Text(DesktopStrings.floatingAiHint, style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        SpringButton(
                            enabled = miniChatInput.isNotBlank() && !miniChatSending,
                            animationsEnabled = settings.animationsEnabled,
                            onClick = {
                                val text = miniChatInput.trim()
                                if (text.isNotBlank()) {
                                    miniChatInput = ""
                                    miniChatSending = true
                                    val now = System.currentTimeMillis()
                                    miniChatBubbles = (miniChatBubbles + ChatBubbleDto(now, "user", text, now)).takeLast(40)
                                    scope.launch {
                                        val ok = server.sendCommand(LinkMsgType.CHAT_SEND, UUID.randomUUID().toString(), ChatSendReq(text = text, sessionId = miniChatSessionId))
                                        if (!ok) {
                                            miniChatSending = false
                                            miniChatBubbles = (miniChatBubbles + ChatBubbleDto(System.currentTimeMillis(), "assistant", DesktopStrings.commandNotSentDisconnected, System.currentTimeMillis())).takeLast(40)
                                        }
                                    }
                                }
                            }
                        ) { Text(DesktopStrings.chatSend) }
                    }
                }
            } else {
                // Orb + Adaptive Direction Notification Bubble
                val currentBubble = bubbleEvent
                Row(
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = if (isOnBottomHalf) Alignment.Bottom else Alignment.Top
                ) {
                    val renderBubble: @Composable () -> Unit = {
                        currentBubble?.let { ev ->
                            val displayText = when (settings.floatingBubbleContent) {
                                FloatingBubbleContent.APP_ONLY -> ev.appLabel
                                FloatingBubbleContent.SENDER_PREVIEW -> "${ev.sender}: ${ev.preview.take(48)}"
                                FloatingBubbleContent.FULL_PREVIEW -> "${ev.appLabel} · ${ev.sender}\n${ev.preview.take(settings.previewLength)}"
                            }
                            Box(
                                modifier = Modifier
                                    .width(272.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xEE151C2C))
                                    .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                    .hoverable(bubbleHoverSource)
                                    .clickable {
                                        DesktopNotificationBus.clearFloatingBubble()
                                        DesktopNotificationBus.clearFloatingBadge()
                                        onOpenConversation(ev)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = displayText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.textPrimary,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    val renderOrb: @Composable () -> Unit = {
                        val idleAlpha = if (orbHovered || currentBubble != null) 1f else settings.floatingOrbOpacity.coerceIn(0.35f, 1f)
                        Box(
                            modifier = Modifier
                                .size((orbSize + 8).dp)
                                .graphicsLayer { alpha = idleAlpha }
                                .hoverable(orbHoverSource)
                                .pointerInput(orbSize) {
                                    detectDragGestures(
                                        onDragEnd = { snapOrbToNearestEdge() },
                                        onDragCancel = { snapOrbToNearestEdge() },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            orbX = (orbX + dragAmount.x.toInt()).coerceIn(bounds.x, bounds.x + bounds.width - orbSize - 18)
                                            orbY = (orbY + dragAmount.y.toInt()).coerceIn(bounds.y, bounds.y + bounds.height - orbSize - 18)
                                        }
                                    )
                                }
                                .clickable {
                                    DesktopNotificationBus.clearFloatingBadge()
                                    if (settings.floatingClickAction == FloatingClickAction.OPEN_AI) {
                                        aiExpanded = true
                                    } else {
                                        onOpenMainWindow()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(orbSize.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xEE101726))
                                    .border(2.dp, accentColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "P",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor
                                )
                            }
                            if (unreadBadge > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .clip(CircleShape)
                                        .background(DesktopPalette.GlassError)
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = if (unreadBadge > 99) "99+" else unreadBadge.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (isOnRightHalf) {
                        if (currentBubble != null) renderBubble()
                        renderOrb()
                    } else {
                        renderOrb()
                        if (currentBubble != null) renderBubble()
                    }
                }
            }
        }
    }
}
