package com.phantom.checker.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.phantom.checker.shared.ChatBubbleDto
import com.phantom.checker.shared.ChatHistoryReq
import com.phantom.checker.shared.ChatHistoryRes
import com.phantom.checker.shared.ChatReplyRes
import com.phantom.checker.shared.ChatSendReq
import com.phantom.checker.shared.LinkMsgType
import com.phantom.checker.shared.MarkReadReq
import com.phantom.checker.shared.MessageNewPush
import com.phantom.checker.shared.MessageReadSyncPush
import com.phantom.checker.shared.MessageSummaryDto
import com.phantom.checker.shared.MessagesListReq
import com.phantom.checker.shared.MessagesListRes
import com.phantom.checker.shared.QuickReplySendReq
import com.phantom.checker.shared.QuickReplySendRes
import com.phantom.checker.shared.RuleItemDto
import com.phantom.checker.shared.RuleMutateRes
import com.phantom.checker.shared.RuleRemoveReq
import com.phantom.checker.shared.RulesListRes
import com.phantom.checker.shared.StatsSnapshotDto
import com.phantom.checker.shared.TagAddReq
import com.phantom.checker.shared.TagItemDto
import com.phantom.checker.shared.TagMutateRes
import com.phantom.checker.shared.TagRemoveReq
import com.phantom.checker.shared.TagsListRes
import com.phantom.checker.shared.TodoItemDto
import com.phantom.checker.shared.TodosListReq
import com.phantom.checker.shared.TodosListRes
import com.phantom.checker.shared.TodosToggleReq
import com.phantom.checker.shared.TodosToggleRes
import com.phantom.checker.shared.WhitelistAddReq
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private enum class DesktopTab {
    MESSAGES, STATS, TODOS, CHAT, RULES, SETTINGS
}

// #457 深色玻璃設計系統（與手機端一致：16dp 圓角、半透明玻璃 surface、明確排版階層、spring 0.85f/MediumLow）
private val GlassBgTop = Color(0xFF0A0E17)
private val GlassBgBottom = Color(0xFF101726)
private val GlassSidebarBg = Color(0xFF121826).copy(alpha = 0.86f)
private val GlassSurface = Color(0xFF182032).copy(alpha = 0.74f)
private val GlassSurfaceElevated = Color(0xFF202A40).copy(alpha = 0.80f)
private val GlassBorder = Color.White.copy(alpha = 0.09f)
private val GlassAccent = Color(0xFF6EA8FE)
private val GlassSuccess = Color(0xFF4ADE80)
private val GlassWarning = Color(0xFFFBBF24)
private val GlassError = Color(0xFFFF7B72)
private val TextPrimary = Color(0xFFEBF1F8)
private val TextSecondary = Color(0xFFB4C0D3)
private val TextMuted = Color(0xFF7D8AA1)

private val DesktopSpringFloat = spring<Float>(
    dampingRatio = 0.85f,
    stiffness = Spring.StiffnessMediumLow
)
private val DesktopSpringOffset = spring<IntOffset>(
    dampingRatio = 0.85f,
    stiffness = Spring.StiffnessMediumLow
)

private val DesktopTypography = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp)
)

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    containerColor: Color = GlassSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, GlassBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content
    )
}

@Composable
private fun SpringButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = DesktopSpringFloat,
        label = "btn_scale"
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        content = content
    )
}

@Composable
private fun SpringOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = DesktopSpringFloat,
        label = "outlined_btn_scale"
    )
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, GlassBorder),
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        content = content
    )
}

@Composable
fun DesktopApp() {
    val gson = remember { Gson() }
    val scanner = remember { MdnsScanner() }
    val client = remember { DesktopLinkClient() }
    val scope = rememberCoroutineScope()

    val devices by scanner.devices.collectAsState()
    val isScanning by scanner.isScanning.collectAsState()
    val uiState by client.state.collectAsState()

    var activeTab by remember { mutableStateOf(DesktopTab.MESSAGES) }

    // M1 Pairing inputs
    var manualHostPort by remember { mutableStateOf("") }
    var manualCode by remember { mutableStateOf("") }
    var selectedFingerprintPrefix by remember { mutableStateOf("") }

    // M2 Data states
    var msgFilter by remember { mutableStateOf("all") }
    var messages by remember { mutableStateOf<List<MessageSummaryDto>>(emptyList()) }
    var latestCursor by remember { mutableLongStateOf(0L) }
    var mirrorToast by remember { mutableStateOf<String?>(null) }
    var replyTargetId by remember { mutableLongStateOf(-1L) }
    var replyText by remember { mutableStateOf("") }
    var actionFeedback by remember { mutableStateOf<String?>(null) }

    var stats by remember { mutableStateOf(StatsSnapshotDto()) }
    var todos by remember { mutableStateOf<List<TodoItemDto>>(emptyList()) }

    var chatSessionId by remember { mutableLongStateOf(0L) }
    var chatBubbles by remember { mutableStateOf<List<ChatBubbleDto>>(emptyList()) }
    var chatInput by remember { mutableStateOf("") }
    var chatSending by remember { mutableStateOf(false) }

    var whitelistRules by remember { mutableStateOf<List<RuleItemDto>>(emptyList()) }
    var newRuleSender by remember { mutableStateOf("") }
    var newRulePkg by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf<List<TagItemDto>>(emptyList()) }
    var newTagName by remember { mutableStateOf("") }

    var chatTimeoutJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun reqId() = UUID.randomUUID().toString()

    suspend fun sendOrNotify(type: String, payload: Any): Boolean {
        val ok = client.sendCommand(type, reqId(), payload)
        if (!ok) {
            actionFeedback = DesktopStrings.commandNotSentDisconnected
        }
        return ok
    }

    fun refreshMessages(useCursor: Boolean = false) {
        scope.launch {
            val cursor = if (useCursor) latestCursor else 0L
            sendOrNotify(LinkMsgType.MESSAGES_LIST, MessagesListReq(filter = msgFilter, offset = 0, limit = 40, sinceCursor = cursor))
        }
    }

    suspend fun refreshAllOnConnect() {
        client.sendCommand(LinkMsgType.MESSAGES_SUBSCRIBE, reqId(), emptyMap<String, String>())
        delay(100L)
        client.sendCommand(LinkMsgType.MESSAGES_LIST, reqId(), MessagesListReq(filter = msgFilter, offset = 0, limit = 40, sinceCursor = latestCursor))
        delay(100L)
        client.sendCommand(LinkMsgType.STATS_SNAPSHOT, reqId(), emptyMap<String, String>())
        delay(100L)
        client.sendCommand(LinkMsgType.TODOS_LIST, reqId(), TodosListReq())
        delay(100L)
        client.sendCommand(LinkMsgType.CHAT_HISTORY, reqId(), ChatHistoryReq(chatSessionId))
        delay(100L)
        client.sendCommand(LinkMsgType.WHITELIST_LIST, reqId(), emptyMap<String, String>())
        delay(100L)
        client.sendCommand(LinkMsgType.TAGS_LIST, reqId(), emptyMap<String, String>())
    }

    LaunchedEffect(Unit) {
        scanner.start()
        if (uiState.isPaired) {
            client.connectSaved()
        } else {
            activeTab = DesktopTab.SETTINGS
        }
    }

    LaunchedEffect(uiState.connState) {
        if (uiState.connState == LinkConnState.CONNECTED) {
            refreshAllOnConnect()
        } else if (chatSending) {
            chatTimeoutJob?.cancel()
            chatTimeoutJob = null
            chatSending = false
            actionFeedback = DesktopStrings.chatTimeoutError
        }
    }

    LaunchedEffect(Unit) {
        client.events.collect { env ->
            when (env.type) {
                LinkMsgType.MESSAGES_LIST_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, MessagesListRes::class.java) }.getOrNull() ?: return@collect
                    if (res.latestCursor > latestCursor) latestCursor = res.latestCursor
                    messages = if (messages.isEmpty()) res.items else (res.items + messages).distinctBy { it.id }.sortedByDescending { it.timestamp }
                }
                LinkMsgType.MESSAGE_NEW -> {
                    val push = runCatching { gson.fromJson(env.payloadJson, MessageNewPush::class.java) }.getOrNull() ?: return@collect
                    if (push.latestCursor > latestCursor) latestCursor = push.latestCursor
                    messages = (push.items + messages).distinctBy { it.id }.sortedByDescending { it.timestamp }
                    push.items.firstOrNull()?.let { newest ->
                        mirrorToast = "${DesktopStrings.newMessageMirror}${newest.sender}: ${newest.preview}"
                    }
                }
                LinkMsgType.MESSAGE_READ_SYNC -> {
                    val sync = runCatching { gson.fromJson(env.payloadJson, MessageReadSyncPush::class.java) }.getOrNull() ?: return@collect
                    messages = if (sync.all) {
                        messages.map { it.copy(isRead = true) }
                    } else {
                        val idSet = sync.ids.toSet()
                        messages.map { if (it.id in idSet) it.copy(isRead = true) else it }
                    }
                }
                LinkMsgType.STATS_SNAPSHOT_RES -> {
                    stats = runCatching { gson.fromJson(env.payloadJson, StatsSnapshotDto::class.java) }.getOrNull() ?: stats
                }
                LinkMsgType.TODOS_LIST_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, TodosListRes::class.java) }.getOrNull() ?: return@collect
                    todos = res.items
                }
                LinkMsgType.TODOS_TOGGLE_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, TodosToggleRes::class.java) }.getOrNull() ?: return@collect
                    if (res.ok) {
                        scope.launch { client.sendCommand(LinkMsgType.TODOS_LIST, reqId(), TodosListReq()) }
                    } else {
                        actionFeedback = res.errorMessage
                    }
                }
                LinkMsgType.CHAT_HISTORY_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, ChatHistoryRes::class.java) }.getOrNull() ?: return@collect
                    if (res.sessionId > 0L) chatSessionId = res.sessionId
                    chatBubbles = res.messages
                }
                LinkMsgType.CHAT_REPLY -> {
                    chatTimeoutJob?.cancel()
                    chatTimeoutJob = null
                    chatSending = false
                    val res = runCatching { gson.fromJson(env.payloadJson, ChatReplyRes::class.java) }.getOrNull() ?: return@collect
                    if (res.sessionId > 0L) chatSessionId = res.sessionId
                    if (res.ok) {
                        chatBubbles = chatBubbles + ChatBubbleDto(System.currentTimeMillis(), "assistant", res.content, System.currentTimeMillis())
                    } else {
                        actionFeedback = "${res.errorCode}: ${res.errorMessage}"
                    }
                }
                LinkMsgType.QUICK_REPLY_SEND_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, QuickReplySendRes::class.java) }.getOrNull() ?: return@collect
                    actionFeedback = if (res.ok) DesktopStrings.replySent else "${res.errorCode}: ${res.errorMessage}"
                }
                LinkMsgType.WHITELIST_LIST_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, RulesListRes::class.java) }.getOrNull() ?: return@collect
                    whitelistRules = res.items
                }
                LinkMsgType.WHITELIST_ADD_RES, LinkMsgType.WHITELIST_REMOVE_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, RuleMutateRes::class.java) }.getOrNull() ?: return@collect
                    actionFeedback = res.message
                    scope.launch { client.sendCommand(LinkMsgType.WHITELIST_LIST, reqId(), emptyMap<String, String>()) }
                }
                LinkMsgType.TAGS_LIST_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, TagsListRes::class.java) }.getOrNull() ?: return@collect
                    tags = res.items
                }
                LinkMsgType.TAGS_ADD_RES, LinkMsgType.TAGS_REMOVE_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, TagMutateRes::class.java) }.getOrNull() ?: return@collect
                    actionFeedback = res.message
                    scope.launch { client.sendCommand(LinkMsgType.TAGS_LIST, reqId(), emptyMap<String, String>()) }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            scanner.stop()
            client.disconnect()
        }
    }

    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    // 連線中 pulse alpha（1.0f -> 0.4f 循環，draw-phase graphicsLayer）
    val pulseTransition = rememberInfiniteTransition(label = "conn_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "conn_pulse_alpha"
    )

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = GlassBgTop,
            surface = GlassSurface,
            surfaceVariant = GlassSurfaceElevated,
            primary = GlassAccent,
            error = GlassError,
            onBackground = TextPrimary,
            onSurface = TextPrimary,
            onSurfaceVariant = TextSecondary
        ),
        typography = DesktopTypography
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(GlassBgTop, GlassBgBottom)))
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // 側邊玻璃導覽列
                    Column(
                        modifier = Modifier
                            .width(220.dp)
                            .fillMaxHeight()
                            .background(GlassSidebarBg)
                            .border(BorderStroke(1.dp, GlassBorder))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)) {
                            Text(
                                text = DesktopStrings.appTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = DesktopStrings.versionLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        val navItems = listOf(
                            DesktopTab.MESSAGES to DesktopStrings.navMessages,
                            DesktopTab.STATS to DesktopStrings.navStats,
                            DesktopTab.TODOS to DesktopStrings.navTodos,
                            DesktopTab.CHAT to DesktopStrings.navChat,
                            DesktopTab.RULES to DesktopStrings.navRules,
                            DesktopTab.SETTINGS to DesktopStrings.navSettings
                        )
                        navItems.forEach { (tab, label) ->
                            val selected = activeTab == tab
                            val navInteraction = remember { MutableInteractionSource() }
                            val navPressed by navInteraction.collectIsPressedAsState()
                            val navScale by animateFloatAsState(
                                targetValue = if (navPressed) 0.97f else 1f,
                                animationSpec = DesktopSpringFloat,
                                label = "nav_scale_${tab.name}"
                            )
                            val navBg by animateColorAsState(
                                targetValue = if (selected) GlassAccent.copy(alpha = 0.18f) else Color.Transparent,
                                animationSpec = tween(150),
                                label = "nav_bg_${tab.name}"
                            )
                            val navBorder by animateColorAsState(
                                targetValue = if (selected) GlassAccent.copy(alpha = 0.35f) else Color.Transparent,
                                animationSpec = tween(150),
                                label = "nav_border_${tab.name}"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        scaleX = navScale
                                        scaleY = navScale
                                    }
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(navBg)
                                    .border(1.dp, navBorder, RoundedCornerShape(12.dp))
                                    .clickable(interactionSource = navInteraction, indication = null) {
                                        activeTab = tab
                                    }
                                    .padding(horizontal = 14.dp, vertical = 11.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) GlassAccent else TextSecondary
                                )
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        // 常駐連線狀態玻璃卡（含連線中 pulse 與重連 1s→30s 倒數進度）
                        val isPulsing = uiState.connState == LinkConnState.CONNECTING || uiState.connState == LinkConnState.RECONNECTING
                        val dotColor = when (uiState.connState) {
                            LinkConnState.CONNECTED -> GlassSuccess
                            LinkConnState.CONNECTING -> GlassAccent
                            LinkConnState.RECONNECTING -> GlassWarning
                            LinkConnState.DISCONNECTED -> TextMuted
                        }
                        val statusText = when (uiState.connState) {
                            LinkConnState.CONNECTED -> DesktopStrings.statusConnected + uiState.pairedDeviceName
                            LinkConnState.CONNECTING -> DesktopStrings.connectingPulse
                            LinkConnState.RECONNECTING -> if (uiState.reconnectTotalSec > 0) {
                                DesktopStrings.reconnectingCountdown(uiState.reconnectRemainingSec, uiState.reconnectTotalSec)
                            } else {
                                DesktopStrings.statusReconnecting
                            }
                            LinkConnState.DISCONNECTED -> if (uiState.isPaired) DesktopStrings.statusReconnecting else DesktopStrings.statusUnpaired
                        }
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = GlassSurfaceElevated
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .graphicsLayer { alpha = if (isPulsing) pulseAlpha else 1f }
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextPrimary
                                    )
                                }
                                AnimatedVisibility(
                                    visible = uiState.connState == LinkConnState.RECONNECTING && uiState.reconnectTotalSec > 0,
                                    enter = fadeIn(tween(150)) + expandVertically(),
                                    exit = fadeOut(tween(150)) + shrinkVertically()
                                ) {
                                    val targetProgress = if (uiState.reconnectTotalSec > 0) {
                                        (uiState.reconnectRemainingSec.toFloat() / uiState.reconnectTotalSec.toFloat()).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                    val animatedProgress by animateFloatAsState(
                                        targetValue = targetProgress,
                                        animationSpec = DesktopSpringFloat,
                                        label = "reconnect_progress"
                                    )
                                    LinearProgressIndicator(
                                        progress = { animatedProgress },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)),
                                        color = GlassWarning,
                                        trackColor = GlassBorder
                                    )
                                }
                            }
                        }
                    }

                    // 主內容區
                    Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(22.dp)) {
                        // 錯誤通知橫幅（AnimatedVisibility 150ms fade + slide）
                        AnimatedVisibility(
                            visible = !uiState.lastError.isNullOrBlank(),
                            enter = fadeIn(tween(150)) + slideInVertically(DesktopSpringOffset) { -it / 3 },
                            exit = fadeOut(tween(150)) + slideOutVertically { -it / 3 }
                        ) {
                            GlassCard(
                                containerColor = Color(0xFF3A1D24).copy(alpha = 0.85f),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(uiState.lastError.orEmpty(), color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodySmall)
                                    Text(DesktopStrings.firewallHint, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }
                            }
                        }

                        // 新訊息鏡射 Toast（AnimatedVisibility 150ms fade + slide）
                        AnimatedVisibility(
                            visible = !mirrorToast.isNullOrBlank(),
                            enter = fadeIn(tween(150)) + slideInVertically(DesktopSpringOffset) { -it / 3 },
                            exit = fadeOut(tween(150)) + slideOutVertically { -it / 3 }
                        ) {
                            GlassCard(
                                containerColor = Color(0xFF1A3358).copy(alpha = 0.85f),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(mirrorToast.orEmpty(), style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { mirrorToast = null }) { Text(DesktopStrings.dismiss) }
                                }
                            }
                        }

                        // 操作回饋橫幅
                        AnimatedVisibility(
                            visible = !actionFeedback.isNullOrBlank(),
                            enter = fadeIn(tween(150)) + slideInVertically(DesktopSpringOffset) { -it / 3 },
                            exit = fadeOut(tween(150)) + slideOutVertically { -it / 3 }
                        ) {
                            GlassCard(
                                containerColor = GlassSurfaceElevated,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(actionFeedback.orEmpty(), style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { actionFeedback = null }) { Text(DesktopStrings.ok) }
                                }
                            }
                        }

                        // 側邊 Tab 切換 AnimatedContent（spring dampingRatio 0.85f / stiffness MediumLow）
                        AnimatedContent(
                            targetState = activeTab,
                            transitionSpec = {
                                (fadeIn(tween(150)) + slideInHorizontally(DesktopSpringOffset) { it / 16 })
                                    .togetherWith(fadeOut(tween(120)) + slideOutHorizontally { -it / 16 })
                            },
                            modifier = Modifier.fillMaxSize(),
                            label = "desktop_tab_content"
                        ) { tab ->
                            if (uiState.connState != LinkConnState.CONNECTED && tab != DesktopTab.SETTINGS) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    GlassCard(modifier = Modifier.padding(24.dp)) {
                                        Text(
                                            text = DesktopStrings.notConnectedHint,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary,
                                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp)
                                        )
                                    }
                                }
                            } else when (tab) {
                                DesktopTab.MESSAGES -> {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            listOf(
                                                "all" to DesktopStrings.filterAll,
                                                "unread" to DesktopStrings.filterUnread,
                                                "starred" to DesktopStrings.filterStarred,
                                                "scam" to DesktopStrings.filterScam
                                            ).forEach { (key, label) ->
                                                FilterChip(
                                                    selected = msgFilter == key,
                                                    onClick = {
                                                        msgFilter = key
                                                        messages = emptyList()
                                                        refreshMessages(useCursor = false)
                                                    },
                                                    label = { Text(label) }
                                                )
                                            }
                                            Spacer(Modifier.weight(1f))
                                            SpringOutlinedButton(onClick = {
                                                scope.launch {
                                                    sendOrNotify(LinkMsgType.MESSAGES_MARK_READ, MarkReadReq(all = true))
                                                }
                                            }) {
                                                Text(DesktopStrings.markAllRead)
                                            }
                                            SpringOutlinedButton(onClick = {
                                                messages = emptyList()
                                                refreshMessages(useCursor = false)
                                            }) {
                                                Text(DesktopStrings.refresh)
                                            }
                                        }
                                        Text(
                                            text = DesktopStrings.markReadNote,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                        if (messages.isEmpty()) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text(DesktopStrings.emptyMessages, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        } else {
                                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                                                items(messages, key = { it.id }) { m ->
                                                    GlassCard(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .animateItem(
                                                                fadeInSpec = tween(180),
                                                                placementSpec = DesktopSpringOffset
                                                            )
                                                    ) {
                                                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(
                                                                    text = "${if (m.isRead) "" else "● "}${m.sender} (${m.packageName})",
                                                                    fontWeight = if (m.isRead) FontWeight.Normal else FontWeight.Bold,
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    color = TextPrimary
                                                                )
                                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                    Text(
                                                                        text = DesktopStrings.riskLabelText(m.riskLabel),
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = if (m.riskLabel.equals("scam", ignoreCase = true)) GlassError else TextMuted
                                                                    )
                                                                    Text(
                                                                        text = timeFmt.format(Date(m.timestamp)),
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = TextMuted
                                                                    )
                                                                }
                                                            }
                                                            Spacer(Modifier.height(6.dp))
                                                            Text(m.preview, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                                            Spacer(Modifier.height(6.dp))
                                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                if (!m.isRead) {
                                                                    TextButton(onClick = {
                                                                        scope.launch {
                                                                            sendOrNotify(LinkMsgType.MESSAGES_MARK_READ, MarkReadReq(ids = listOf(m.id)))
                                                                        }
                                                                    }) { Text(DesktopStrings.markRead) }
                                                                }
                                                                TextButton(onClick = {
                                                                    replyTargetId = if (replyTargetId == m.id) -1L else m.id
                                                                    replyText = ""
                                                                }) { Text(DesktopStrings.sendReply) }
                                                            }
                                                            AnimatedVisibility(
                                                                visible = replyTargetId == m.id,
                                                                enter = fadeIn(tween(150)) + expandVertically(),
                                                                exit = fadeOut(tween(120)) + shrinkVertically()
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    OutlinedTextField(
                                                                        value = replyText,
                                                                        onValueChange = { replyText = it },
                                                                        label = { Text(DesktopStrings.replyPlaceholder) },
                                                                        singleLine = true,
                                                                        modifier = Modifier.weight(1f)
                                                                    )
                                                                    SpringButton(onClick = {
                                                                        if (replyText.isNotBlank()) {
                                                                            scope.launch {
                                                                                if (sendOrNotify(
                                                                                        LinkMsgType.QUICK_REPLY_SEND,
                                                                                        QuickReplySendReq(packageName = m.packageName, sender = m.sender, text = replyText)
                                                                                    )
                                                                                ) {
                                                                                    replyTargetId = -1L
                                                                                    replyText = ""
                                                                                }
                                                                            }
                                                                        }
                                                                    }) { Text(DesktopStrings.sendReply) }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                DesktopTab.STATS -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                            StatBox(DesktopStrings.statsTotal, stats.total.toString(), Modifier.weight(1f))
                                            StatBox(DesktopStrings.statsToday, stats.today.toString(), Modifier.weight(1f))
                                            StatBox(DesktopStrings.statsUnread, stats.unread.toString(), Modifier.weight(1f))
                                            StatBox(DesktopStrings.statsScam, stats.scam.toString(), Modifier.weight(1f))
                                        }
                                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                                                Text(DesktopStrings.statsRecentApps, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                                Spacer(Modifier.height(8.dp))
                                                Text(stats.recentPackages.joinToString(" · ").ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                            }
                                        }
                                    }
                                }

                                DesktopTab.TODOS -> {
                                    if (todos.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(DesktopStrings.todosEmpty, color = TextMuted)
                                        }
                                    } else {
                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            items(todos, key = { it.id }) { t ->
                                                GlassCard(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .animateItem(
                                                            fadeInSpec = tween(150),
                                                            placementSpec = DesktopSpringOffset
                                                        )
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Checkbox(
                                                            checked = t.isDone,
                                                            onCheckedChange = { checked ->
                                                                scope.launch {
                                                                    sendOrNotify(LinkMsgType.TODOS_TOGGLE, TodosToggleReq(id = t.id, done = checked))
                                                                }
                                                            }
                                                        )
                                                        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                                            Text(t.title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                                            if (t.repeatRule.isNotBlank()) {
                                                                Text(t.repeatRule, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                DesktopTab.CHAT -> {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        if (chatBubbles.isEmpty()) {
                                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                Text(DesktopStrings.emptyChat, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        } else {
                                            LazyColumn(
                                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                items(chatBubbles, key = { "${it.id}_${it.role}_${it.createdAt}" }) { b ->
                                                    val isUser = b.role == "user"
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .animateItem(
                                                                fadeInSpec = tween(150),
                                                                placementSpec = DesktopSpringOffset
                                                            ),
                                                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(16.dp))
                                                                .background(if (isUser) GlassAccent.copy(alpha = 0.22f) else GlassSurface)
                                                                .border(1.dp, if (isUser) GlassAccent.copy(alpha = 0.35f) else GlassBorder, RoundedCornerShape(16.dp))
                                                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                                        ) {
                                                            Text(b.content, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        AnimatedVisibility(
                                            visible = chatSending,
                                            enter = fadeIn(tween(150)),
                                            exit = fadeOut(tween(150))
                                        ) {
                                            Text(
                                                text = DesktopStrings.chatThinking,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = GlassAccent,
                                                modifier = Modifier
                                                    .padding(vertical = 6.dp)
                                                    .graphicsLayer { alpha = pulseAlpha }
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = chatInput,
                                                onValueChange = { chatInput = it },
                                                label = { Text(DesktopStrings.chatInputPlaceholder) },
                                                modifier = Modifier.weight(1f)
                                            )
                                            SpringButton(
                                                onClick = {
                                                    val msg = chatInput.trim()
                                                    if (msg.isNotBlank()) {
                                                        scope.launch {
                                                            val sent = sendOrNotify(LinkMsgType.CHAT_SEND, ChatSendReq(text = msg, sessionId = chatSessionId))
                                                            if (sent) {
                                                                chatInput = ""
                                                                chatSending = true
                                                                val nowTs = System.currentTimeMillis()
                                                                chatBubbles = chatBubbles + ChatBubbleDto(nowTs, "user", msg, nowTs)
                                                                chatTimeoutJob?.cancel()
                                                                chatTimeoutJob = scope.launch {
                                                                    delay(130_000L)
                                                                    if (chatSending) {
                                                                        chatSending = false
                                                                        actionFeedback = DesktopStrings.chatTimeoutError
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                                enabled = !chatSending && chatInput.isNotBlank()
                                            ) {
                                                Text(DesktopStrings.chatSend)
                                            }
                                        }
                                    }
                                }

                                DesktopTab.RULES -> {
                                    Column(
                                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Text(DesktopStrings.whitelistTitle, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    OutlinedTextField(value = newRuleSender, onValueChange = { newRuleSender = it }, label = { Text(DesktopStrings.whitelistSenderHint) }, modifier = Modifier.weight(1f), singleLine = true)
                                                    OutlinedTextField(value = newRulePkg, onValueChange = { newRulePkg = it }, label = { Text(DesktopStrings.whitelistPkgHint) }, modifier = Modifier.weight(1f), singleLine = true)
                                                    SpringButton(onClick = {
                                                        if (newRuleSender.isNotBlank()) {
                                                            scope.launch {
                                                                if (sendOrNotify(LinkMsgType.WHITELIST_ADD, WhitelistAddReq(sender = newRuleSender, packageName = newRulePkg, ruleText = newRuleSender))) {
                                                                    newRuleSender = ""
                                                                    newRulePkg = ""
                                                                }
                                                            }
                                                        }
                                                    }) { Text(DesktopStrings.add) }
                                                }
                                                if (whitelistRules.isEmpty()) {
                                                    Text(DesktopStrings.emptyWhitelist, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                                } else {
                                                    whitelistRules.forEach { r ->
                                                        HorizontalDivider(color = GlassBorder)
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                            Text("${r.ruleText} ${if (r.packageName.isNotBlank()) "(${r.packageName})" else ""}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                                            TextButton(onClick = {
                                                                scope.launch { sendOrNotify(LinkMsgType.WHITELIST_REMOVE, RuleRemoveReq(r.id)) }
                                                            }) { Text(DesktopStrings.remove, color = GlassError) }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Text(DesktopStrings.tagsTitle, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    OutlinedTextField(value = newTagName, onValueChange = { newTagName = it }, label = { Text(DesktopStrings.tagNameHint) }, modifier = Modifier.weight(1f), singleLine = true)
                                                    SpringButton(onClick = {
                                                        if (newTagName.isNotBlank()) {
                                                            scope.launch {
                                                                if (sendOrNotify(LinkMsgType.TAGS_ADD, TagAddReq(name = newTagName))) {
                                                                    newTagName = ""
                                                                }
                                                            }
                                                        }
                                                    }) { Text(DesktopStrings.add) }
                                                }
                                                if (tags.isEmpty()) {
                                                    Text(DesktopStrings.emptyTags, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                                } else {
                                                    tags.forEach { t ->
                                                        HorizontalDivider(color = GlassBorder)
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                            Text(t.name, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                                            TextButton(onClick = {
                                                                scope.launch { sendOrNotify(LinkMsgType.TAGS_REMOVE, TagRemoveReq(t.id)) }
                                                            }) { Text(DesktopStrings.remove, color = GlassError) }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                DesktopTab.SETTINGS -> {
                                    Column(
                                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        AnimatedVisibility(
                                            visible = uiState.isPaired,
                                            enter = fadeIn(tween(150)) + expandVertically(),
                                            exit = fadeOut(tween(150)) + shrinkVertically()
                                        ) {
                                            GlassCard(containerColor = GlassSurfaceElevated, modifier = Modifier.fillMaxWidth()) {
                                                Row(modifier = Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Text("${uiState.pairedDeviceName} (${uiState.savedWssUrl})", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        SpringOutlinedButton(onClick = { client.disconnect() }) { Text(DesktopStrings.disconnectBtn) }
                                                        TextButton(onClick = { client.clearPairing() }) { Text(DesktopStrings.unpairBtn, color = GlassError) }
                                                    }
                                                }
                                            }
                                        }

                                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Text(
                                                    text = if (isScanning) DesktopStrings.statusScanning else DesktopStrings.discoveredDevices,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = TextPrimary
                                                )
                                                if (devices.isEmpty()) {
                                                    Text(DesktopStrings.noDevicesFound, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                                } else {
                                                    devices.forEach { dev ->
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                            Column {
                                                                Text(dev.friendlyName, fontWeight = FontWeight.Medium, color = TextPrimary)
                                                                Text(
                                                                    text = "${dev.host}:${dev.port} · ${DesktopStrings.fingerprintPrefix}${dev.fingerprintPrefix}",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontFamily = FontFamily.Monospace,
                                                                    color = TextMuted
                                                                )
                                                            }
                                                            SpringOutlinedButton(onClick = {
                                                                manualHostPort = "${dev.host}:${dev.port}"
                                                                selectedFingerprintPrefix = dev.fingerprintPrefix
                                                            }) { Text(DesktopStrings.selectDevice) }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Text(DesktopStrings.manualPairTitle, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                                OutlinedTextField(value = manualHostPort, onValueChange = { manualHostPort = it }, label = { Text(DesktopStrings.hostPortLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                                OutlinedTextField(value = manualCode, onValueChange = { manualCode = it.filter { c -> c.isDigit() }.take(6) }, label = { Text(DesktopStrings.pairCodeLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                                SpringButton(
                                                    onClick = {
                                                        if (manualHostPort.isNotBlank() && manualCode.length == 6) {
                                                            client.pairAndConnect(manualHostPort, manualCode, selectedFingerprintPrefix)
                                                        }
                                                    },
                                                    enabled = manualHostPort.isNotBlank() && manualCode.length == 6
                                                ) { Text(DesktopStrings.pairBtn) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        }
    }
}
