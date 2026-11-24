package com.phantom.checker.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.phantom.checker.shared.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private enum class DesktopTab {
    MESSAGES, STATS, TODOS, CHAT, RULES, SETTINGS
}

/**
 * #468 桌面端主畫面（10 系統設定＋8 自訂設定＋大螢幕 Master-Detail＋Canvas 統計＋多會話 Markdown AI 助手＋鍵盤捷徑）。
 */
@Composable
fun DesktopApp(
    server: DesktopLinkServer = remember { DesktopLinkServer() },
    manageLifecycle: Boolean = true,
    onMirrorNotification: (title: String, body: String) -> Unit = { _, _ -> }
) {
    val gson = remember { Gson() }
    val scope = rememberCoroutineScope()

    val settings by DesktopSettingsStore.state.collectAsState()
    val uiState by server.state.collectAsState()
    val mdnsStatus by server.mdnsStatus.collectAsState()

    val accentColor = remember(settings.accentPreset, settings.customAccentHex) {
        DesktopPalette.resolveAccentColor(settings.accentPreset, settings.customAccentHex)
    }
    val (bgTop, bgBottom) = remember(settings.bgTheme) { DesktopPalette.bgGradientColors(settings.bgTheme) }
    val customBgBitmap = remember(settings.customBgImagePath) { DesktopPalette.loadBackgroundImage(settings.customBgImagePath) }
    val cardPad = DesktopPalette.cardPadding(settings.density)
    val listSpace = DesktopPalette.listSpacing(settings.density)
    val typography = remember(settings.fontScale) { DesktopPalette.typography(settings.fontScale) }

    var activeTab by remember {
        val initial = runCatching { DesktopTab.valueOf(settings.defaultStartupTab) }.getOrDefault(DesktopTab.MESSAGES)
        mutableStateOf(if (uiState.isPaired) initial else DesktopTab.SETTINGS)
    }

    // Messages Master-Detail state
    var msgFilter by remember { mutableStateOf("all") }
    var msgSearch by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    var messages by remember { mutableStateOf<List<MessageSummaryDto>>(emptyList()) }
    var selectedMsgId by remember { mutableLongStateOf(-1L) }
    var latestCursor by remember { mutableLongStateOf(0L) }
    var mirrorToast by remember { mutableStateOf<String?>(null) }
    var replyText by remember { mutableStateOf("") }
    var actionFeedback by remember { mutableStateOf<String?>(null) }

    // Stats / Todos / Notes / Chat / Rules / Muted / Phone Status
    var stats by remember { mutableStateOf(StatsSnapshotDto()) }
    var todos by remember { mutableStateOf<List<TodoItemDto>>(emptyList()) }
    var newTodoTitle by remember { mutableStateOf("") }
    var editingTodo by remember { mutableStateOf<TodoItemDto?>(null) }
    var editTodoTitle by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf<List<NoteItemDto>>(emptyList()) }
    var newNoteContent by remember { mutableStateOf("") }

    var chatSessions by remember { mutableStateOf<List<ChatSessionSummaryDto>>(emptyList()) }
    var chatSessionId by remember { mutableLongStateOf(0L) }
    var chatBubbles by remember { mutableStateOf<List<ChatBubbleDto>>(emptyList()) }
    var chatInput by remember { mutableStateOf("") }
    var chatSending by remember { mutableStateOf(false) }
    var chatTimeoutJob by remember { mutableStateOf<Job?>(null) }

    var whitelistRules by remember { mutableStateOf<List<RuleItemDto>>(emptyList()) }
    var newRuleSender by remember { mutableStateOf("") }
    var newRulePkg by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf<List<TagItemDto>>(emptyList()) }
    var newTagName by remember { mutableStateOf("") }
    var mutedApps by remember { mutableStateOf<List<MutedAppDto>>(emptyList()) }
    var phoneStatus by remember { mutableStateOf<PhoneStatusDto?>(null) }

    var deepAnalyzingMsgId by remember { mutableLongStateOf(-1L) }
    var deepAnalysisResult by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var conversationThread by remember { mutableStateOf<ConversationRes?>(null) }
    var pendingUpdateDialog by remember { mutableStateOf<DesktopUpdateOutcome.UpdateAvailable?>(null) }

    fun reqId() = UUID.randomUUID().toString()

    suspend fun sendOrNotify(type: String, payload: Any): Boolean {
        val ok = server.sendCommand(type, reqId(), payload)
        if (!ok) {
            actionFeedback = DesktopStrings.commandNotSentDisconnected
        }
        return ok
    }

    fun copyToClipboard(text: String) {
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
            actionFeedback = DesktopStrings.chatCopied
        }
    }

    fun refreshMessages(useCursor: Boolean = false) {
        scope.launch {
            val cursor = if (useCursor) latestCursor else 0L
            sendOrNotify(LinkMsgType.MESSAGES_LIST, MessagesListReq(filter = msgFilter, offset = 0, limit = 40, sinceCursor = cursor))
        }
    }

    suspend fun refreshAllOnConnect() {
        server.sendCommand(LinkMsgType.MESSAGES_SUBSCRIBE, reqId(), emptyMap<String, String>())
        delay(120L)
        server.sendCommand(LinkMsgType.MESSAGES_LIST, reqId(), MessagesListReq(filter = msgFilter, offset = 0, limit = 40, sinceCursor = latestCursor))
        delay(120L)
        server.sendCommand(LinkMsgType.STATS_SNAPSHOT, reqId(), emptyMap<String, String>())
        delay(120L)
        server.sendCommand(LinkMsgType.TODOS_LIST, reqId(), TodosListReq())
        delay(120L)
        server.sendCommand(LinkMsgType.CHAT_SESSIONS_LIST, reqId(), emptyMap<String, String>())
        delay(120L)
        server.sendCommand(LinkMsgType.CHAT_HISTORY, reqId(), ChatHistoryReq(chatSessionId))
        delay(120L)
        server.sendCommand(LinkMsgType.WHITELIST_LIST, reqId(), emptyMap<String, String>())
        delay(120L)
        server.sendCommand(LinkMsgType.TAGS_LIST, reqId(), emptyMap<String, String>())
        delay(120L)
        server.sendCommand(LinkMsgType.NOTES_LIST, reqId(), NotesListReq(limit = 50))
        delay(120L)
        server.sendCommand(LinkMsgType.MUTED_APPS_LIST, reqId(), emptyMap<String, String>())
        delay(120L)
        server.sendCommand(LinkMsgType.PHONE_STATUS_GET, reqId(), emptyMap<String, String>())
    }

    fun sendChatMessage(rawText: String) {
        val msg = rawText.trim()
        if (msg.isBlank() || chatSending) return
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

    LaunchedEffect(Unit) {
        if (manageLifecycle) {
            server.start()
        }
        if (!uiState.isPaired) {
            activeTab = DesktopTab.SETTINGS
        }
        // #478 Process-once startup auto update check (silent on offline / 403 / 429, no background polling)
        scope.launch {
            val outcome = DesktopUpdater.checkForUpdate(isAutoStartup = true)
            if (outcome is DesktopUpdateOutcome.UpdateAvailable) {
                pendingUpdateDialog = outcome
                val curSettings = DesktopSettingsStore.state.value
                if (curSettings.nativeNotifications &&
                    !curSettings.isInDndWindow() &&
                    DesktopUpdater.shouldSendDailyUpdateNotification(outcome.latestVersion)
                ) {
                    onMirrorNotification(
                        DesktopStrings.updateNotifTitle,
                        DesktopStrings.updateNotifBody(outcome.latestVersion)
                    )
                }
            }
        }
    }

    LaunchedEffect(uiState.connState) {
        if (uiState.connState == LinkConnState.CONNECTED) {
            DesktopDiagnostics.info("Link", "Phone connected: ${uiState.pairedDeviceName}")
            refreshAllOnConnect()
        } else if (chatSending) {
            chatTimeoutJob?.cancel()
            chatTimeoutJob = null
            chatSending = false
            actionFeedback = DesktopStrings.chatTimeoutError
        }
    }

    LaunchedEffect(Unit) {
        server.events.collect { env ->
            when (env.type) {
                LinkMsgType.MESSAGES_LIST_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, MessagesListRes::class.java) }.getOrNull() ?: return@collect
                    if (res.latestCursor > latestCursor) latestCursor = res.latestCursor
                    messages = if (messages.isEmpty()) res.items else (res.items + messages).distinctBy { it.id }.sortedByDescending { it.timestamp }
                    if (selectedMsgId <= 0L && messages.isNotEmpty()) selectedMsgId = messages.first().id
                }
                LinkMsgType.MESSAGE_NEW -> {
                    val push = runCatching { gson.fromJson(env.payloadJson, MessageNewPush::class.java) }.getOrNull() ?: return@collect
                    if (push.latestCursor > latestCursor) latestCursor = push.latestCursor
                    messages = (push.items + messages).distinctBy { it.id }.sortedByDescending { it.timestamp }
                    push.items.firstOrNull()?.let { newest ->
                        val curSettings = DesktopSettingsStore.state.value
                        val preview = newest.preview.take(curSettings.previewLength)
                        if (curSettings.mirrorMode == MirrorDisplayMode.BANNER || curSettings.mirrorMode == MirrorDisplayMode.BOTH) {
                            mirrorToast = "${DesktopStrings.newMessageMirror}${newest.sender}: $preview"
                        }
                        if ((curSettings.mirrorMode == MirrorDisplayMode.NATIVE || curSettings.mirrorMode == MirrorDisplayMode.BOTH) &&
                            curSettings.nativeNotifications && !curSettings.isInDndWindow()
                        ) {
                            onMirrorNotification(newest.sender, preview)
                        }
                        if (curSettings.notificationSoundEnabled && !curSettings.isInDndWindow()) {
                            runCatching { Toolkit.getDefaultToolkit().beep() }
                        }
                    }
                }
                LinkMsgType.MESSAGE_READ_SYNC -> {
                    val sync = runCatching { gson.fromJson(env.payloadJson, MessageReadSyncPush::class.java) }.getOrNull() ?: return@collect
                    messages = if (sync.all) messages.map { it.copy(isRead = true) } else {
                        val idSet = sync.ids.toSet()
                        messages.map { if (it.id in idSet) it.copy(isRead = true) else it }
                    }
                }
                LinkMsgType.MESSAGE_STAR_SYNC -> {
                    val sync = runCatching { gson.fromJson(env.payloadJson, MessageStarSyncPush::class.java) }.getOrNull() ?: return@collect
                    messages = messages.map { if (it.id == sync.id) it.copy(isStarred = sync.isStarred) else it }
                }
                LinkMsgType.MESSAGES_STAR_TOGGLE_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, StarToggleRes::class.java) }.getOrNull() ?: return@collect
                    if (res.ok) messages = messages.map { if (it.id == res.id) it.copy(isStarred = res.isStarred) else it }
                    else actionFeedback = res.errorMessage
                }
                LinkMsgType.MESSAGES_TAG_APPLY_RES, LinkMsgType.MESSAGES_TAG_REMOVE_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, MessageTagRes::class.java) }.getOrNull() ?: return@collect
                    if (res.ok) messages = messages.map { if (it.id == res.messageId) it.copy(tagIds = res.tagIds) else it }
                    else actionFeedback = res.errorMessage
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
                    if (res.ok) scope.launch { server.sendCommand(LinkMsgType.TODOS_LIST, reqId(), TodosListReq()) }
                    else actionFeedback = res.errorMessage
                }
                LinkMsgType.TODOS_ADD_RES, LinkMsgType.TODOS_EDIT_RES, LinkMsgType.TODOS_DELETE_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, TodoMutateRes::class.java) }.getOrNull() ?: return@collect
                    if (res.ok) scope.launch { server.sendCommand(LinkMsgType.TODOS_LIST, reqId(), TodosListReq()) }
                    else actionFeedback = res.errorMessage
                }
                LinkMsgType.NOTES_LIST_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, NotesListRes::class.java) }.getOrNull() ?: return@collect
                    notes = res.items
                }
                LinkMsgType.NOTE_ADD_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, NoteMutateRes::class.java) }.getOrNull() ?: return@collect
                    if (res.ok) {
                        actionFeedback = DesktopStrings.noteAdded
                        scope.launch { server.sendCommand(LinkMsgType.NOTES_LIST, reqId(), NotesListReq(limit = 50)) }
                    } else actionFeedback = res.errorMessage
                }
                LinkMsgType.MESSAGE_DEEP_ANALYZE_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, DeepAnalyzeRes::class.java) }.getOrNull() ?: return@collect
                    deepAnalyzingMsgId = -1L
                    if (res.ok) deepAnalysisResult = res.messageId to res.markdown
                    else actionFeedback = "${res.errorCode}: ${res.errorMessage}"
                }
                LinkMsgType.MUTED_APPS_LIST_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, MutedAppsListRes::class.java) }.getOrNull() ?: return@collect
                    mutedApps = res.items
                }
                LinkMsgType.MUTED_APPS_TOGGLE_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, MutedAppToggleRes::class.java) }.getOrNull() ?: return@collect
                    if (res.ok) mutedApps = mutedApps.map { if (it.packageName == res.packageName) it.copy(muted = res.muted) else it }
                    else actionFeedback = res.errorMessage
                }
                LinkMsgType.MESSAGES_CONVERSATION_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, ConversationRes::class.java) }.getOrNull() ?: return@collect
                    conversationThread = res
                }
                LinkMsgType.PHONE_STATUS_RES -> {
                    phoneStatus = runCatching { gson.fromJson(env.payloadJson, PhoneStatusDto::class.java) }.getOrNull() ?: phoneStatus
                }
                LinkMsgType.CHAT_SESSIONS_LIST_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, ChatSessionsListRes::class.java) }.getOrNull() ?: return@collect
                    chatSessions = res.sessions
                }
                LinkMsgType.CHAT_SESSION_NEW_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, ChatSessionNewRes::class.java) }.getOrNull() ?: return@collect
                    val created = res.session
                    if (res.ok && created != null) {
                        chatSessionId = created.id
                        chatBubbles = emptyList()
                        chatSessions = listOf(created) + chatSessions
                    } else if (!res.ok) actionFeedback = res.errorMessage
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
                    scope.launch { server.sendCommand(LinkMsgType.WHITELIST_LIST, reqId(), emptyMap<String, String>()) }
                }
                LinkMsgType.TAGS_LIST_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, TagsListRes::class.java) }.getOrNull() ?: return@collect
                    tags = res.items
                }
                LinkMsgType.TAGS_ADD_RES, LinkMsgType.TAGS_REMOVE_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, TagMutateRes::class.java) }.getOrNull() ?: return@collect
                    actionFeedback = res.message
                    scope.launch { server.sendCommand(LinkMsgType.TAGS_LIST, reqId(), emptyMap<String, String>()) }
                }
                LinkMsgType.ERROR -> {
                    val err = runCatching { gson.fromJson(env.payloadJson, ErrorPayload::class.java) }.getOrNull() ?: return@collect
                    if (err.errorCode == LinkErrorCode.TIMEOUT && err.errorMessage == LinkMsgType.MESSAGE_DEEP_ANALYZE) {
                        deepAnalyzingMsgId = -1L
                    }
                    actionFeedback = DesktopStrings.classifiedError(err.errorCode)
                }
            }
        }
    }

    if (manageLifecycle) {
        DisposableEffect(Unit) {
            onDispose {
                server.stop()
            }
        }
    }

    val timeFmt = remember(settings.use24HourTime) {
        SimpleDateFormat(if (settings.use24HourTime) "HH:mm:ss" else "hh:mm:ss a", Locale.getDefault())
    }

    val pulseTransition = rememberInfiniteTransition(label = "conn_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (settings.animationsEnabled) 0.4f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "conn_pulse_alpha"
    )

    // Load thread automatically when selecting a message in Master-Detail
    val selectedMsg = remember(messages, selectedMsgId) { messages.firstOrNull { it.id == selectedMsgId } }
    LaunchedEffect(selectedMsg?.id, selectedMsg?.conversationKey) {
        val m = selectedMsg ?: return@LaunchedEffect
        if (uiState.connState == LinkConnState.CONNECTED && m.conversationKey.isNotBlank()) {
            server.sendCommand(
                LinkMsgType.MESSAGES_CONVERSATION,
                reqId(),
                ConversationReq(packageName = m.packageName, conversationKey = m.conversationKey, limit = 50)
            )
        } else {
            conversationThread = null
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = bgTop,
            surface = DesktopPalette.GlassSurface,
            surfaceVariant = DesktopPalette.GlassSurfaceElevated,
            primary = accentColor,
            onPrimary = Color(0xFF0A0E17),
            onBackground = DesktopPalette.TextPrimary,
            onSurface = DesktopPalette.TextPrimary,
            onSurfaceVariant = DesktopPalette.TextSecondary,
            error = DesktopPalette.GlassError
        ),
        typography = typography
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        ev.key == Key.Escape -> {
                            deepAnalysisResult = null
                            editingTodo = null
                            pendingUpdateDialog = null
                            mirrorToast = null
                            actionFeedback = null
                            true
                        }
                        ev.isCtrlPressed && ev.key == Key.One -> { activeTab = DesktopTab.MESSAGES; true }
                        ev.isCtrlPressed && ev.key == Key.Two -> { activeTab = DesktopTab.STATS; true }
                        ev.isCtrlPressed && ev.key == Key.Three -> { activeTab = DesktopTab.TODOS; true }
                        ev.isCtrlPressed && ev.key == Key.Four -> { activeTab = DesktopTab.CHAT; true }
                        ev.isCtrlPressed && ev.key == Key.Five -> { activeTab = DesktopTab.RULES; true }
                        ev.isCtrlPressed && ev.key == Key.Six -> { activeTab = DesktopTab.SETTINGS; true }
                        ev.isCtrlPressed && ev.key == Key.F -> {
                            activeTab = DesktopTab.MESSAGES
                            runCatching { searchFocusRequester.requestFocus() }
                            true
                        }
                        else -> false
                    }
                },
            color = bgTop
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Background: Custom image + readability scrim OR 4 preset gradients
                if (customBgBitmap != null) {
                    Image(
                        bitmap = customBgBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.64f)))
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(bgTop, bgBottom)))
                    )
                }

                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Sidebar (collapsible + configurable width)
                    val sidebarW = if (settings.sidebarCollapsed) 76.dp else settings.sidebarWidthDp.dp
                    Column(
                        modifier = Modifier
                            .width(sidebarW)
                            .fillMaxHeight()
                            .background(DesktopPalette.GlassSidebarBg)
                            .border(width = 1.dp, color = DesktopPalette.GlassBorder)
                            .padding(horizontal = if (settings.sidebarCollapsed) 10.dp else 16.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!settings.sidebarCollapsed) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = DesktopStrings.appTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = DesktopPalette.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = DesktopStrings.versionLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DesktopPalette.TextMuted
                                    )
                                }
                            }
                            TextButton(
                                onClick = { DesktopSettingsStore.update { it.copy(sidebarCollapsed = !it.sidebarCollapsed) } }
                            ) {
                                Text(if (settings.sidebarCollapsed) "»" else "«", color = accentColor)
                            }
                        }

                        HorizontalDivider(color = DesktopPalette.GlassBorder, modifier = Modifier.padding(vertical = 4.dp))

                        val navItems = listOf(
                            Triple(DesktopTab.MESSAGES, DesktopStrings.navMessages, "1"),
                            Triple(DesktopTab.STATS, DesktopStrings.navStats, "2"),
                            Triple(DesktopTab.TODOS, DesktopStrings.navTodos, "3"),
                            Triple(DesktopTab.CHAT, DesktopStrings.navChat, "4"),
                            Triple(DesktopTab.RULES, DesktopStrings.navRules, "5"),
                            Triple(DesktopTab.SETTINGS, DesktopStrings.navSettings, "6")
                        )
                        navItems.forEach { (tab, label, shortBadge) ->
                            val selected = activeTab == tab
                            val navInteraction = remember { MutableInteractionSource() }
                            val navPressed by navInteraction.collectIsPressedAsState()
                            val navScale by animateFloatAsState(
                                targetValue = if (settings.animationsEnabled && navPressed) 0.97f else 1f,
                                animationSpec = DesktopSpringFloat,
                                label = "nav_scale_${tab.name}"
                            )
                            val navBg by animateColorAsState(
                                targetValue = if (selected) accentColor.copy(alpha = 0.18f) else Color.Transparent,
                                animationSpec = tween(if (settings.animationsEnabled) 150 else 0),
                                label = "nav_bg_${tab.name}"
                            )
                            val navBorder by animateColorAsState(
                                targetValue = if (selected) accentColor.copy(alpha = 0.38f) else Color.Transparent,
                                animationSpec = tween(if (settings.animationsEnabled) 150 else 0),
                                label = "nav_border_${tab.name}"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { scaleX = navScale; scaleY = navScale }
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(navBg)
                                    .border(1.dp, navBorder, RoundedCornerShape(12.dp))
                                    .clickable(interactionSource = navInteraction, indication = null) { activeTab = tab }
                                    .padding(horizontal = if (settings.sidebarCollapsed) 8.dp else 14.dp, vertical = 11.dp),
                                contentAlignment = if (settings.sidebarCollapsed) Alignment.Center else Alignment.CenterStart
                            ) {
                                Text(
                                    text = if (settings.sidebarCollapsed) shortBadge else label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) accentColor else DesktopPalette.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        // Persistent Connection Status Card
                        val isPulsing = uiState.connState == LinkConnState.WAITING_FOR_PHONE
                        val dotColor = when (uiState.connState) {
                            LinkConnState.CONNECTED -> DesktopPalette.GlassSuccess
                            LinkConnState.WAITING_FOR_PHONE -> accentColor
                            LinkConnState.ERROR -> DesktopPalette.GlassError
                            LinkConnState.STOPPED -> DesktopPalette.TextMuted
                        }
                        val statusText = when (uiState.connState) {
                            LinkConnState.CONNECTED -> DesktopStrings.statusConnected + uiState.pairedDeviceName
                            LinkConnState.WAITING_FOR_PHONE -> DesktopStrings.serverListeningOn(uiState.port, 0)
                            LinkConnState.ERROR -> DesktopStrings.statusServerError
                            LinkConnState.STOPPED -> DesktopStrings.statusServerStopped
                        }
                        GlassCard(modifier = Modifier.fillMaxWidth(), containerColor = DesktopPalette.GlassSurfaceElevated) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(if (settings.sidebarCollapsed) 8.dp else 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                horizontalAlignment = if (settings.sidebarCollapsed) Alignment.CenterHorizontally else Alignment.Start
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .graphicsLayer { alpha = if (isPulsing) pulseAlpha else 1f }
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                    if (!settings.sidebarCollapsed) {
                                        Text(statusText, style = MaterialTheme.typography.labelSmall, color = DesktopPalette.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                if (!settings.sidebarCollapsed) {
                                    val ps = phoneStatus
                                    if (uiState.connState == LinkConnState.CONNECTED && ps != null && ps.batteryPercent >= 0) {
                                        val chargeTag = if (ps.isCharging) " · ${DesktopStrings.charging}" else ""
                                        Text("${DesktopStrings.phoneBattery}: ${ps.batteryPercent}%$chargeTag", style = MaterialTheme.typography.labelSmall, color = DesktopPalette.GlassSuccess)
                                    }
                                    uiState.lastError?.takeIf { it.isNotBlank() }?.let { err ->
                                        Text(err, style = MaterialTheme.typography.labelSmall, color = DesktopPalette.GlassError, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }

                    // Right Main Content Area
                    Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp)) {
                        // Mirror notification banner
                        AnimatedVisibility(
                            visible = mirrorToast != null,
                            enter = fadeIn(tween(150)) + slideInVertically { -it / 2 },
                            exit = fadeOut(tween(150)) + slideOutVertically { -it / 2 }
                        ) {
                            mirrorToast?.let { toast ->
                                GlassCard(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                    containerColor = accentColor.copy(alpha = 0.16f)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(toast, style = MaterialTheme.typography.bodySmall, color = DesktopPalette.TextPrimary, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { mirrorToast = null }) { Text(DesktopStrings.dismiss) }
                                    }
                                }
                            }
                        }

                        // Action feedback banner
                        AnimatedVisibility(
                            visible = actionFeedback != null,
                            enter = fadeIn(tween(150)) + expandVertically(),
                            exit = fadeOut(tween(120)) + shrinkVertically()
                        ) {
                            actionFeedback?.let { fb ->
                                GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), containerColor = DesktopPalette.GlassSurfaceElevated) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(fb, style = MaterialTheme.typography.bodySmall, color = DesktopPalette.TextSecondary, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { actionFeedback = null }) { Text(DesktopStrings.ok) }
                                    }
                                }
                            }
                        }

                        if (uiState.connState != LinkConnState.CONNECTED && activeTab != DesktopTab.SETTINGS) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                GlassCard {
                                    Column(
                                        modifier = Modifier.padding(28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(DesktopStrings.notConnectedHint, style = MaterialTheme.typography.titleMedium, color = DesktopPalette.TextPrimary)
                                        SpringButton(onClick = { activeTab = DesktopTab.SETTINGS }, animationsEnabled = settings.animationsEnabled) {
                                            Text(DesktopStrings.navSettings)
                                        }
                                    }
                                }
                            }
                        } else {
                            val renderTab: @Composable (DesktopTab) -> Unit = { tab ->
                                when (tab) {
                                    DesktopTab.MESSAGES -> {
                                        // Master-Detail Messages Layout
                                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                                OutlinedTextField(
                                                    value = msgSearch,
                                                    onValueChange = { msgSearch = it },
                                                    placeholder = { Text(DesktopStrings.searchMessagesHint, style = MaterialTheme.typography.bodySmall) },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f).focusRequester(searchFocusRequester)
                                                )
                                                SpringOutlinedButton(
                                                    onClick = { scope.launch { sendOrNotify(LinkMsgType.MESSAGES_MARK_READ, MarkReadReq(all = true)) } },
                                                    animationsEnabled = settings.animationsEnabled
                                                ) { Text(DesktopStrings.markAllRead) }
                                                SpringOutlinedButton(
                                                    onClick = { messages = emptyList(); refreshMessages(useCursor = false) },
                                                    animationsEnabled = settings.animationsEnabled
                                                ) { Text(DesktopStrings.refresh) }
                                            }
                                            Text(DesktopStrings.markReadNote, style = MaterialTheme.typography.labelSmall, color = DesktopPalette.TextMuted)

                                            val visibleMessages = remember(messages, msgSearch) {
                                                val q = msgSearch.trim()
                                                if (q.isBlank()) messages
                                                else messages.filter {
                                                    it.sender.contains(q, ignoreCase = true) ||
                                                        it.preview.contains(q, ignoreCase = true) ||
                                                        it.packageName.contains(q, ignoreCase = true)
                                                }
                                            }

                                            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                                // Left Master List
                                                Box(modifier = Modifier.weight(0.46f).fillMaxHeight()) {
                                                    if (visibleMessages.isEmpty()) {
                                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                            Text(DesktopStrings.emptyMessages, color = DesktopPalette.TextMuted)
                                                        }
                                                    } else {
                                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(if (settings.messageCardStyle == MessageCardStyle.COMPACT_LIST) 4.dp else listSpace)) {
                                                            items(visibleMessages, key = { it.id }) { m ->
                                                                val isSelected = m.id == selectedMsgId
                                                                val cardBg = if (isSelected) accentColor.copy(alpha = 0.16f) else DesktopPalette.glassSurface(settings.glassBlurLevel)
                                                                val corner = if (settings.messageCardStyle == MessageCardStyle.COMPACT_LIST) {
                                                                    (settings.messageCardCornerDp - 4).coerceAtLeast(8).dp
                                                                } else {
                                                                    settings.messageCardCornerDp.dp
                                                                }
                                                                val cardModifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .clip(RoundedCornerShape(corner))
                                                                    .background(cardBg)
                                                                    .border(
                                                                        1.dp,
                                                                        if (isSelected) accentColor.copy(alpha = 0.5f) else DesktopPalette.GlassBorder,
                                                                        RoundedCornerShape(corner)
                                                                    )
                                                                    .clickable { selectedMsgId = m.id }
                                                                    .padding(if (settings.messageCardStyle == MessageCardStyle.COMPACT_LIST) 10.dp else cardPad)
                                                                Column(modifier = cardModifier) {
                                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                                        Text(
                                                                            text = "${if (m.isRead) "" else "● "}${if (m.isStarred) "★ " else ""}${m.sender}",
                                                                            fontWeight = if (m.isRead) FontWeight.Normal else FontWeight.Bold,
                                                                            style = MaterialTheme.typography.bodyMedium,
                                                                            color = DesktopPalette.TextPrimary,
                                                                            maxLines = 1,
                                                                            overflow = TextOverflow.Ellipsis,
                                                                            modifier = Modifier.weight(1f)
                                                                        )
                                                                        Text(
                                                                            text = "${DesktopStrings.riskLabelText(m.riskLabel)} · ${timeFmt.format(Date(m.timestamp))}",
                                                                            style = MaterialTheme.typography.labelSmall,
                                                                            color = if (m.riskLabel.equals("scam", ignoreCase = true)) DesktopPalette.GlassError else DesktopPalette.TextMuted
                                                                        )
                                                                    }
                                                                    Spacer(Modifier.height(4.dp))
                                                                    Text(
                                                                        text = m.preview.take(settings.previewLength),
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        color = DesktopPalette.TextSecondary,
                                                                        maxLines = if (settings.messageCardStyle == MessageCardStyle.COMPACT_LIST) 1 else 2,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                // Right Detail Pane (Details + Tags + Actions + Thread)
                                                GlassCard(modifier = Modifier.weight(0.54f).fillMaxHeight()) {
                                                    val detail = selectedMsg
                                                    if (detail == null) {
                                                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                                            Text(DesktopStrings.selectMessageHint, color = DesktopPalette.TextMuted)
                                                        }
                                                    } else {
                                                        Column(
                                                            modifier = Modifier.fillMaxSize().padding(18.dp),
                                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                                        ) {
                                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Text(detail.sender, style = MaterialTheme.typography.titleMedium, color = DesktopPalette.TextPrimary)
                                                                    Text("${detail.packageName} · ${timeFmt.format(Date(detail.timestamp))}", style = MaterialTheme.typography.labelSmall, color = DesktopPalette.TextMuted)
                                                                }
                                                                Text(
                                                                    text = DesktopStrings.riskLabelText(detail.riskLabel),
                                                                    style = MaterialTheme.typography.labelMedium,
                                                                    color = when (detail.riskLabel.lowercase()) {
                                                                        "scam" -> DesktopPalette.GlassError
                                                                        "suspicious" -> DesktopPalette.GlassWarning
                                                                        else -> DesktopPalette.GlassSuccess
                                                                    }
                                                                )
                                                            }
                                                            Text(detail.preview.take(settings.previewLength), style = MaterialTheme.typography.bodyMedium, color = DesktopPalette.TextSecondary)

                                                            // Action Row
                                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                if (!detail.isRead) {
                                                                    TextButton(onClick = { scope.launch { sendOrNotify(LinkMsgType.MESSAGES_MARK_READ, MarkReadReq(ids = listOf(detail.id))) } }) {
                                                                        Text(DesktopStrings.markRead)
                                                                    }
                                                                }
                                                                TextButton(onClick = {
                                                                    scope.launch { sendOrNotify(LinkMsgType.MESSAGES_STAR_TOGGLE, StarToggleReq(id = detail.id, starred = !detail.isStarred)) }
                                                                }) { Text(if (detail.isStarred) "★ ${DesktopStrings.unstar}" else "☆ ${DesktopStrings.star}", color = if (detail.isStarred) DesktopPalette.GlassWarning else accentColor) }
                                                                TextButton(
                                                                    enabled = deepAnalyzingMsgId != detail.id,
                                                                    onClick = {
                                                                        scope.launch {
                                                                            if (sendOrNotify(LinkMsgType.MESSAGE_DEEP_ANALYZE, DeepAnalyzeReq(id = detail.id))) {
                                                                                deepAnalyzingMsgId = detail.id
                                                                            }
                                                                        }
                                                                    }
                                                                ) { Text(if (deepAnalyzingMsgId == detail.id) DesktopStrings.deepAnalyzing else DesktopStrings.deepAnalyze) }
                                                                TextButton(onClick = {
                                                                    scope.launch { sendOrNotify(LinkMsgType.NOTE_ADD, NoteAddReq(content = "[${detail.sender}] ${detail.preview}", relatedMessageId = detail.id)) }
                                                                }) { Text(DesktopStrings.addNote) }
                                                            }

                                                            // Tags row
                                                            if (tags.isNotEmpty()) {
                                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                    tags.forEach { tg ->
                                                                        val applied = tg.id in detail.tagIds
                                                                        FilterChip(
                                                                            selected = applied,
                                                                            onClick = {
                                                                                scope.launch {
                                                                                    val cmd = if (applied) LinkMsgType.MESSAGES_TAG_REMOVE else LinkMsgType.MESSAGES_TAG_APPLY
                                                                                    sendOrNotify(cmd, MessageTagReq(messageId = detail.id, tagId = tg.id))
                                                                                }
                                                                            },
                                                                            label = { Text(tg.name) }
                                                                        )
                                                                    }
                                                                }
                                                            }

                                                            HorizontalDivider(color = DesktopPalette.GlassBorder)
                                                            Text(DesktopStrings.conversationTitle, style = MaterialTheme.typography.titleSmall, color = DesktopPalette.TextPrimary)
                                                            val thread = conversationThread
                                                            if (thread == null || thread.items.isEmpty()) {
                                                                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                                    Text(DesktopStrings.conversationEmpty, style = MaterialTheme.typography.bodySmall, color = DesktopPalette.TextMuted)
                                                                }
                                                            } else {
                                                                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                    items(thread.items, key = { it.id }) { item ->
                                                                        Column(
                                                                            modifier = Modifier
                                                                                .fillMaxWidth()
                                                                                .clip(RoundedCornerShape(10.dp))
                                                                                .background(DesktopPalette.GlassSurfaceElevated.copy(alpha = 0.65f))
                                                                                .padding(10.dp)
                                                                        ) {
                                                                            Text("${item.sender} · ${timeFmt.format(Date(item.timestamp))}", style = MaterialTheme.typography.labelSmall, color = accentColor)
                                                                            Spacer(Modifier.height(2.dp))
                                                                            Text(item.preview, style = MaterialTheme.typography.bodySmall, color = DesktopPalette.TextSecondary)
                                                                        }
                                                                    }
                                                                }
                                                            }

                                                            // Quick Reply Bar
                                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                OutlinedTextField(
                                                                    value = replyText,
                                                                    onValueChange = { replyText = it },
                                                                    label = { Text(DesktopStrings.replyPlaceholder) },
                                                                    singleLine = true,
                                                                    modifier = Modifier.weight(1f)
                                                                )
                                                                SpringButton(
                                                                    enabled = replyText.isNotBlank(),
                                                                    animationsEnabled = settings.animationsEnabled,
                                                                    onClick = {
                                                                        val txt = replyText.trim()
                                                                        if (txt.isNotBlank()) {
                                                                            scope.launch {
                                                                                if (sendOrNotify(
                                                                                        LinkMsgType.QUICK_REPLY_SEND,
                                                                                        QuickReplySendReq(
                                                                                            packageName = detail.packageName,
                                                                                            sender = detail.sender,
                                                                                            conversationKey = detail.conversationKey,
                                                                                            text = txt
                                                                                        )
                                                                                    )
                                                                                ) {
                                                                                    replyText = ""
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                ) { Text(DesktopStrings.sendReply) }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    DesktopTab.STATS -> {
                                        Column(
                                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                                StatBox(DesktopStrings.statsTotal, stats.total.toString(), Modifier.weight(1f))
                                                StatBox(DesktopStrings.statsToday, stats.today.toString(), Modifier.weight(1f))
                                                StatBox(DesktopStrings.statsUnread, stats.unread.toString(), Modifier.weight(1f))
                                                StatBox(DesktopStrings.statsScam, stats.scam.toString(), Modifier.weight(1f))
                                            }
                                            DesktopStatsCharts(stats = stats, accentColor = accentColor)
                                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                                Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                                                    Text(DesktopStrings.statsRecentApps, style = MaterialTheme.typography.titleSmall, color = DesktopPalette.TextPrimary)
                                                    Spacer(Modifier.height(8.dp))
                                                    Text(stats.recentPackages.joinToString(" · ").ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium, color = DesktopPalette.TextSecondary)
                                                }
                                            }
                                        }
                                    }

                                    DesktopTab.TODOS -> {
                                        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            // Left: Todos CRUD
                                            Column(modifier = Modifier.weight(0.55f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    OutlinedTextField(
                                                        value = newTodoTitle,
                                                        onValueChange = { newTodoTitle = it },
                                                        label = { Text(DesktopStrings.todoAddHint) },
                                                        singleLine = true,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    SpringButton(onClick = {
                                                        val title = newTodoTitle.trim()
                                                        if (title.isNotBlank()) {
                                                            scope.launch {
                                                                if (sendOrNotify(LinkMsgType.TODOS_ADD, TodoAddReq(title = title))) newTodoTitle = ""
                                                            }
                                                        }
                                                    }, animationsEnabled = settings.animationsEnabled) { Text(DesktopStrings.add) }
                                                }
                                                if (todos.isEmpty()) {
                                                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                        Text(DesktopStrings.todosEmpty, color = DesktopPalette.TextMuted)
                                                    }
                                                } else {
                                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(listSpace), modifier = Modifier.weight(1f)) {
                                                        items(todos, key = { it.id }) { t ->
                                                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                                                Row(modifier = Modifier.fillMaxWidth().padding(cardPad), verticalAlignment = Alignment.CenterVertically) {
                                                                    Checkbox(
                                                                        checked = t.isDone,
                                                                        onCheckedChange = { checked ->
                                                                            scope.launch { sendOrNotify(LinkMsgType.TODOS_TOGGLE, TodosToggleReq(id = t.id, done = checked)) }
                                                                        }
                                                                    )
                                                                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                                                        Text(t.title, style = MaterialTheme.typography.bodyMedium, color = DesktopPalette.TextPrimary)
                                                                        if (t.repeatRule.isNotBlank()) {
                                                                            Text(t.repeatRule, style = MaterialTheme.typography.labelSmall, color = DesktopPalette.TextMuted)
                                                                        }
                                                                    }
                                                                    TextButton(onClick = { editingTodo = t; editTodoTitle = t.title }) { Text(DesktopStrings.todoEdit) }
                                                                    TextButton(onClick = { scope.launch { sendOrNotify(LinkMsgType.TODOS_DELETE, TodoDeleteReq(id = t.id)) } }) {
                                                                        Text(DesktopStrings.todoDelete, color = DesktopPalette.GlassError)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // Right: Notes Panel
                                            GlassCard(modifier = Modifier.weight(0.45f).fillMaxHeight()) {
                                                Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    Text(DesktopStrings.notesTitle, style = MaterialTheme.typography.titleMedium, color = DesktopPalette.TextPrimary)
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        OutlinedTextField(
                                                            value = newNoteContent,
                                                            onValueChange = { newNoteContent = it },
                                                            label = { Text(DesktopStrings.noteInputHint) },
                                                            singleLine = true,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        SpringButton(onClick = {
                                                            val c = newNoteContent.trim()
                                                            if (c.isNotBlank()) {
                                                                scope.launch {
                                                                    if (sendOrNotify(LinkMsgType.NOTE_ADD, NoteAddReq(content = c))) newNoteContent = ""
                                                                }
                                                            }
                                                        }, animationsEnabled = settings.animationsEnabled) { Text(DesktopStrings.add) }
                                                    }
                                                    if (notes.isEmpty()) {
                                                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                            Text(DesktopStrings.notesEmpty, color = DesktopPalette.TextMuted)
                                                        }
                                                    } else {
                                                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            items(notes, key = { it.id }) { n ->
                                                                Column(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .clip(RoundedCornerShape(10.dp))
                                                                        .background(DesktopPalette.GlassSurfaceElevated.copy(alpha = 0.6f))
                                                                        .padding(10.dp)
                                                                ) {
                                                                    if (n.relatedAppLabel.isNotBlank()) {
                                                                        Text(n.relatedAppLabel, style = MaterialTheme.typography.labelSmall, color = accentColor)
                                                                    }
                                                                    Text(n.content, style = MaterialTheme.typography.bodySmall, color = DesktopPalette.TextSecondary)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    DesktopTab.CHAT -> {
                                        // Multi-Session AI Chat with Markdown + Copy + Quick Commands + Ctrl+Enter
                                        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            // Session Sidebar
                                            GlassCard(modifier = Modifier.width(220.dp).fillMaxHeight()) {
                                                Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                        Text(DesktopStrings.chatSessionsTitle, style = MaterialTheme.typography.titleSmall, color = DesktopPalette.TextPrimary)
                                                        TextButton(onClick = {
                                                            scope.launch { sendOrNotify(LinkMsgType.CHAT_SESSION_NEW, ChatSessionNewReq(title = "Desktop Chat")) }
                                                        }) { Text(DesktopStrings.chatNewSession, color = accentColor) }
                                                    }
                                                    HorizontalDivider(color = DesktopPalette.GlassBorder)
                                                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        items(chatSessions, key = { it.id }) { s ->
                                                            val sel = s.id == chatSessionId
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .clip(RoundedCornerShape(10.dp))
                                                                    .background(if (sel) accentColor.copy(alpha = 0.18f) else Color.Transparent)
                                                                    .clickable {
                                                                        chatSessionId = s.id
                                                                        scope.launch { sendOrNotify(LinkMsgType.CHAT_HISTORY, ChatHistoryReq(s.id)) }
                                                                    }
                                                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                                            ) {
                                                                Text(s.title, style = MaterialTheme.typography.bodySmall, color = if (sel) accentColor else DesktopPalette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // Chat Main Column
                                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                                // Quick Commands Bar
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                                                    listOf(
                                                        DesktopStrings.quickPromptSummary,
                                                        DesktopStrings.quickPromptScam,
                                                        DesktopStrings.quickPromptTodos
                                                    ).forEach { prompt ->
                                                        FilterChip(selected = false, onClick = { sendChatMessage(prompt) }, label = { Text(prompt) })
                                                    }
                                                }

                                                if (chatBubbles.isEmpty()) {
                                                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                        Text(DesktopStrings.emptyChat, color = DesktopPalette.TextMuted, style = MaterialTheme.typography.bodyMedium)
                                                    }
                                                } else {
                                                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        itemsIndexed(
                                                            items = chatBubbles,
                                                            key = { idx, b -> "${idx}_${b.id}_${b.role}_${b.createdAt}" }
                                                        ) { _, b ->
                                                            val isUser = b.role == "user"
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                                            ) {
                                                                val chatCorner = settings.messageCardCornerDp.dp
                                                                val chatTextStyle = MaterialTheme.typography.bodyMedium.copy(
                                                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * settings.chatFontSize.multiplier
                                                                )
                                                                Column(
                                                                    modifier = Modifier
                                                                        .clip(RoundedCornerShape(chatCorner))
                                                                        .background(if (isUser) accentColor.copy(alpha = 0.22f) else DesktopPalette.glassSurface(settings.glassBlurLevel))
                                                                        .border(1.dp, if (isUser) accentColor.copy(alpha = 0.35f) else DesktopPalette.GlassBorder, RoundedCornerShape(chatCorner))
                                                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                                                ) {
                                                                    if (isUser) {
                                                                        Text(b.content, style = chatTextStyle, color = DesktopPalette.TextPrimary)
                                                                    } else {
                                                                        androidx.compose.material3.ProvideTextStyle(chatTextStyle) {
                                                                            DesktopMarkdownText(markdown = b.content, accentColor = accentColor)
                                                                        }
                                                                        Row(modifier = Modifier.align(Alignment.End)) {
                                                                            TextButton(onClick = { copyToClipboard(b.content) }) {
                                                                                Text(DesktopStrings.chatCopy, style = MaterialTheme.typography.labelSmall)
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                AnimatedVisibility(visible = chatSending) {
                                                    Text(
                                                        text = DesktopStrings.chatThinking,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = accentColor,
                                                        modifier = Modifier.padding(vertical = 6.dp).graphicsLayer { alpha = pulseAlpha }
                                                    )
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    verticalAlignment = Alignment.Bottom
                                                ) {
                                                    OutlinedTextField(
                                                        value = chatInput,
                                                        onValueChange = { chatInput = it },
                                                        label = { Text(DesktopStrings.chatInputPlaceholder) },
                                                        maxLines = 4,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .onPreviewKeyEvent { ev ->
                                                                if (ev.type == KeyEventType.KeyDown && ev.isCtrlPressed && ev.key == Key.Enter) {
                                                                    sendChatMessage(chatInput)
                                                                    true
                                                                } else false
                                                            }
                                                    )
                                                    SpringButton(
                                                        onClick = { sendChatMessage(chatInput) },
                                                        enabled = !chatSending && chatInput.isNotBlank(),
                                                        animationsEnabled = settings.animationsEnabled
                                                    ) { Text(DesktopStrings.chatSend) }
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
                                                    Text(DesktopStrings.whitelistTitle, style = MaterialTheme.typography.titleMedium, color = DesktopPalette.TextPrimary)
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
                                                        }, animationsEnabled = settings.animationsEnabled) { Text(DesktopStrings.add) }
                                                    }
                                                    if (whitelistRules.isEmpty()) {
                                                        Text(DesktopStrings.emptyWhitelist, style = MaterialTheme.typography.bodySmall, color = DesktopPalette.TextMuted)
                                                    } else {
                                                        whitelistRules.forEach { r ->
                                                            HorizontalDivider(color = DesktopPalette.GlassBorder)
                                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                                Text("${r.ruleText} ${if (r.packageName.isNotBlank()) "(${r.packageName})" else ""}", style = MaterialTheme.typography.bodySmall, color = DesktopPalette.TextSecondary)
                                                                TextButton(onClick = { scope.launch { sendOrNotify(LinkMsgType.WHITELIST_REMOVE, RuleRemoveReq(r.id)) } }) {
                                                                    Text(DesktopStrings.remove, color = DesktopPalette.GlassError)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    Text(DesktopStrings.tagsTitle, style = MaterialTheme.typography.titleMedium, color = DesktopPalette.TextPrimary)
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        OutlinedTextField(value = newTagName, onValueChange = { newTagName = it }, label = { Text(DesktopStrings.tagNameHint) }, modifier = Modifier.weight(1f), singleLine = true)
                                                        SpringButton(onClick = {
                                                            if (newTagName.isNotBlank()) {
                                                                scope.launch {
                                                                    if (sendOrNotify(LinkMsgType.TAGS_ADD, TagAddReq(name = newTagName))) newTagName = ""
                                                                }
                                                            }
                                                        }, animationsEnabled = settings.animationsEnabled) { Text(DesktopStrings.add) }
                                                    }
                                                    if (tags.isEmpty()) {
                                                        Text(DesktopStrings.emptyTags, style = MaterialTheme.typography.bodySmall, color = DesktopPalette.TextMuted)
                                                    } else {
                                                        tags.forEach { t ->
                                                            HorizontalDivider(color = DesktopPalette.GlassBorder)
                                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                                Text(t.name, style = MaterialTheme.typography.bodySmall, color = DesktopPalette.TextSecondary)
                                                                TextButton(onClick = { scope.launch { sendOrNotify(LinkMsgType.TAGS_REMOVE, TagRemoveReq(t.id)) } }) {
                                                                    Text(DesktopStrings.remove, color = DesktopPalette.GlassError)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    Text(DesktopStrings.mutedAppsTitle, style = MaterialTheme.typography.titleMedium, color = DesktopPalette.TextPrimary)
                                                    if (mutedApps.isEmpty()) {
                                                        Text(DesktopStrings.mutedAppsEmpty, style = MaterialTheme.typography.bodySmall, color = DesktopPalette.TextMuted)
                                                    } else {
                                                        mutedApps.forEach { app ->
                                                            HorizontalDivider(color = DesktopPalette.GlassBorder)
                                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Text(app.appLabel, style = MaterialTheme.typography.bodySmall, color = DesktopPalette.TextPrimary)
                                                                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = DesktopPalette.TextMuted, fontFamily = FontFamily.Monospace)
                                                                }
                                                                Switch(
                                                                    checked = app.muted,
                                                                    onCheckedChange = { checked ->
                                                                        scope.launch { sendOrNotify(LinkMsgType.MUTED_APPS_TOGGLE, MutedAppToggleReq(packageName = app.packageName, muted = checked)) }
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    DesktopTab.SETTINGS -> {
                                        DesktopSettingsCenter(
                                            settings = settings,
                                            accentColor = accentColor,
                                            uiState = uiState,
                                            mdnsStatus = mdnsStatus,
                                            server = server,
                                            onUpdateAvailable = { pendingUpdateDialog = it },
                                            onFeedback = { actionFeedback = it }
                                        )
                                    }
                                }
                            }

                            if (settings.animationsEnabled) {
                                AnimatedContent(
                                    targetState = activeTab,
                                    transitionSpec = {
                                        (fadeIn(tween(180)) + slideInHorizontally(tween(200)) { it / 16 }) togetherWith
                                            (fadeOut(tween(120)) + slideOutHorizontally(tween(140)) { -it / 16 })
                                    },
                                    label = "desktop_tab_content",
                                    modifier = Modifier.fillMaxSize()
                                ) { tab -> renderTab(tab) }
                            } else {
                                Box(modifier = Modifier.fillMaxSize()) { renderTab(activeTab) }
                            }
                        }
                    }
                }

                // Deep Analysis Result Dialog (rendered with Markdown)
                deepAnalysisResult?.let { (_, markdown) ->
                    AlertDialog(
                        onDismissRequest = { deepAnalysisResult = null },
                        title = { Text(DesktopStrings.deepAnalysisTitle, color = DesktopPalette.TextPrimary) },
                        text = {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                DesktopMarkdownText(markdown = markdown, accentColor = accentColor)
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { deepAnalysisResult = null }) { Text(DesktopStrings.ok) }
                        },
                        containerColor = bgBottom
                    )
                }

                // Edit Todo Dialog
                editingTodo?.let { target ->
                    AlertDialog(
                        onDismissRequest = { editingTodo = null },
                        title = { Text(DesktopStrings.todoEdit, color = DesktopPalette.TextPrimary) },
                        text = {
                            OutlinedTextField(
                                value = editTodoTitle,
                                onValueChange = { editTodoTitle = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val title = editTodoTitle.trim()
                                if (title.isNotBlank()) {
                                    scope.launch {
                                        if (sendOrNotify(LinkMsgType.TODOS_EDIT, TodoEditReq(id = target.id, title = title, dueAt = target.dueAt, repeatRule = target.repeatRule))) {
                                            editingTodo = null
                                        }
                                    }
                                }
                            }) { Text(DesktopStrings.todoSave) }
                        },
                        dismissButton = {
                            TextButton(onClick = { editingTodo = null }) { Text(DesktopStrings.dismiss) }
                        },
                        containerColor = bgBottom
                    )
                }

                // #478 Desktop Update Available Dialog
                pendingUpdateDialog?.let { upd ->
                    DesktopUpdateDialog(
                        update = upd,
                        accentColor = accentColor,
                        containerColor = bgBottom,
                        animationsEnabled = settings.animationsEnabled,
                        onDismiss = { pendingUpdateDialog = null },
                        onFeedback = { actionFeedback = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = DesktopPalette.TextMuted)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, color = DesktopPalette.TextPrimary)
        }
    }
}
