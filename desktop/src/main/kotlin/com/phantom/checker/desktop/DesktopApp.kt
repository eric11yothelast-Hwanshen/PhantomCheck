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
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isAltPressed
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

private const val MAX_CACHED_MESSAGES = 240
private const val MAX_CACHED_CHAT_BUBBLES = 120
private const val FULL_REFRESH_COOLDOWN_MS = 10_000L

private data class ConversationSummary(
    val groupKey: String,
    val packageName: String,
    val conversationKey: String,
    val appLabel: String,
    val sender: String,
    val latestMessage: MessageSummaryDto,
    val unreadCount: Int,
    val hasStarred: Boolean,
    val isGroup: Boolean,
    val highestRisk: String,
    val messageCount: Int
)

private fun conversationGroupKey(m: MessageSummaryDto): String =
    if (m.conversationKey.isNotBlank()) "${m.packageName}::${m.conversationKey}"
    else "${m.packageName}::${m.sender}"

private fun formatRelativeConvTime(timestamp: Long, use24Hour: Boolean): String {
    if (timestamp <= 0L) return ""
    val nowCal = java.util.Calendar.getInstance()
    val msgCal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = nowCal.get(java.util.Calendar.YEAR) == msgCal.get(java.util.Calendar.YEAR) &&
        nowCal.get(java.util.Calendar.DAY_OF_YEAR) == msgCal.get(java.util.Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) {
        if (use24Hour) "HH:mm" else "hh:mm a"
    } else {
        "MM/dd"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
}

/**
 * #468 / #488 / #489 / #494 桌面端主畫面（10 系統設定＋16 自訂設定＋深淺/轉場＋大螢幕 Master-Detail 對話串化＋Canvas 統計＋多會話 Markdown AI 助手＋鍵盤捷徑）。
 */
@Composable
fun DesktopApp(
    server: DesktopLinkServer = remember { DesktopLinkServer() },
    manageLifecycle: Boolean = true,
    isWindowVisible: Boolean = true,
    requestedTabKey: String? = null,
    onRequestedTabConsumed: () -> Unit = {},
    triggerUpdateCheckTick: Int = 0,
    onMirrorNotification: (title: String, body: String) -> Unit = { _, _ -> }
) {
    val gson = remember { Gson() }
    val scope = rememberCoroutineScope()

    val settings by DesktopSettingsStore.state.collectAsState()
    val uiState by server.state.collectAsState()
    val mdnsStatus by server.mdnsStatus.collectAsState()

    val tokens = remember(settings.surfaceOpacity, settings.bgScrimAlpha, settings.highContrastText, settings.bgTheme) {
        DesktopThemeTokens.from(settings)
    }
    val accentColor = remember(settings.accentPreset, settings.customAccentHex) {
        DesktopPalette.resolveAccentColor(settings.accentPreset, settings.customAccentHex)
    }
    val (bgTop, bgBottom) = remember(settings.bgTheme, settings.lightTheme) {
        if (settings.lightTheme) Color(0xFFE8EEF7) to Color(0xFFDDE6F2)
        else DesktopPalette.bgGradientColors(settings.bgTheme)
    }
    var batchSelectMode by remember { mutableStateOf(false) }
    var selectedBatchKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    val customBgBitmap = remember(settings.customBgImagePath) { DesktopPalette.loadBackgroundImage(settings.customBgImagePath) }
    val cardPad = DesktopPalette.cardPadding(settings.density)
    val listSpace = DesktopPalette.listSpacing(settings.density)
    val typography = remember(settings.fontScale) { DesktopPalette.typography(settings.fontScale) }

    // #488 / #494 Preserve scroll states across AnimatedContent tab transitions
    val messagesListState = rememberLazyListState()
    val threadListState = rememberLazyListState()
    val todosListState = rememberLazyListState()
    val notesListState = rememberLazyListState()
    val chatListState = rememberLazyListState()
    val statsScrollState = rememberScrollState()
    val rulesScrollState = rememberScrollState()

    var activeTab by remember {
        val initial = runCatching { DesktopTab.valueOf(settings.defaultStartupTab) }.getOrDefault(DesktopTab.MESSAGES)
        mutableStateOf(if (uiState.isPaired) initial else DesktopTab.SETTINGS)
    }

    // Messages Master-Detail state (#494 conversation-grouped)
    var msgFilter by remember { mutableStateOf("all") }
    var msgSearch by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    var messages by remember { mutableStateOf<List<MessageSummaryDto>>(emptyList()) }
    var selectedConvKey by remember { mutableStateOf("") }
    var selectedMsgId by remember { mutableLongStateOf(-1L) }
    var latestCursor by remember { mutableLongStateOf(0L) }
    var mirrorToast by remember { mutableStateOf<String?>(null) }
    var replyText by remember { mutableStateOf("") }
    var actionFeedback by remember { mutableStateOf<String?>(null) }

    // Stats / Todos / Notes / Chat / Rules / Muted / Phone Status
    var stats by remember { mutableStateOf(StatsSnapshotDto()) }
    var todos by remember { mutableStateOf<List<TodoItemDto>>(emptyList()) }
    var newTodoTitle by remember { mutableStateOf("") }
    var showNewTodoDialog by remember { mutableStateOf(false) }
    var editingTodo by remember { mutableStateOf<TodoItemDto?>(null) }
    var editTodoTitle by remember { mutableStateOf("") }
    var editTodoNotes by remember { mutableStateOf("") }
    var editTodoDueAt by remember { mutableLongStateOf(0L) }
    var editTodoDueCustom by remember { mutableStateOf("") }
    var editTodoPriority by remember { mutableIntStateOf(1) }
    var editTodoRepeat by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf<List<NoteItemDto>>(emptyList()) }
    var newNoteContent by remember { mutableStateOf("") }
    var newNotePinned by remember { mutableStateOf(false) }
    var newNoteColor by remember { mutableStateOf("") }
    var editingNote by remember { mutableStateOf<NoteItemDto?>(null) }
    var editNoteContent by remember { mutableStateOf("") }
    var editNotePinned by remember { mutableStateOf(false) }
    var editNoteColor by remember { mutableStateOf("") }
    val notifiedDueTodoIds = remember { mutableSetOf<Long>() }

    // #499 Desktop local due-reminder loop (deduplicated per todo id, respects DND/pause)
    LaunchedEffect(todos) {
        while (true) {
            val now = System.currentTimeMillis()
            val cur = DesktopSettingsStore.state.value
            if (!cur.isInDndWindow() && !DesktopNotificationBus.isPausedNow(now)) {
                todos.forEach { t ->
                    if (!t.isDone && t.dueAt > 0L && t.id !in notifiedDueTodoIds) {
                        if (t.dueAt in (now - 60_000L)..(now + 30_000L)) {
                            notifiedDueTodoIds.add(t.id)
                            DesktopNotificationBus.dispatchNewMessage(
                                msg = MessageSummaryDto(
                                    id = t.id,
                                    packageName = "com.phantom.checker.todo",
                                    appLabel = DesktopStrings.todoReminderBannerTitle,
                                    sender = t.title,
                                    preview = t.notes.ifBlank { formatDueTime(t.dueAt) },
                                    riskLabel = if (t.priority >= 2) "suspicious" else "safe",
                                    conversationKey = "",
                                    timestamp = now
                                ),
                                settings = cur,
                                onNativeTrayNotify = onMirrorNotification
                            )
                        }
                    }
                }
            }
            delay(30_000L)
        }
    }

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

    var lastFullRefreshAtMs by remember { mutableLongStateOf(0L) }

    suspend fun refreshAllOnConnect() {
        val now = System.currentTimeMillis()
        server.sendCommand(LinkMsgType.MESSAGES_SUBSCRIBE, reqId(), emptyMap<String, String>())
        delay(120L)
        server.sendCommand(LinkMsgType.MESSAGES_LIST, reqId(), MessagesListReq(filter = msgFilter, offset = 0, limit = 40, sinceCursor = latestCursor))
        if (now - lastFullRefreshAtMs < FULL_REFRESH_COOLDOWN_MS && stats.total > 0) {
            return
        }
        lastFullRefreshAtMs = now
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
        } else {
            if (chatSending) {
                chatTimeoutJob?.cancel()
                chatTimeoutJob = null
                chatSending = false
                actionFeedback = DesktopStrings.chatTimeoutError
            }
            if (deepAnalyzingMsgId > 0L) {
                deepAnalyzingMsgId = -1L
            }
        }
    }

    // #497 Handle tray menu tab navigation & update check trigger
    LaunchedEffect(requestedTabKey) {
        val key = requestedTabKey ?: return@LaunchedEffect
        runCatching { DesktopTab.valueOf(key) }.getOrNull()?.let { activeTab = it }
        onRequestedTabConsumed()
    }
    LaunchedEffect(triggerUpdateCheckTick) {
        if (triggerUpdateCheckTick <= 0) return@LaunchedEffect
        when (val res = DesktopUpdater.checkForUpdate(isAutoStartup = false)) {
            is DesktopUpdateOutcome.UpdateAvailable -> pendingUpdateDialog = res
            is DesktopUpdateOutcome.UpToDate -> actionFeedback = "${DesktopStrings.updateUpToDate} (v${res.currentVersion})"
            is DesktopUpdateOutcome.Error -> actionFeedback = res.message
            is DesktopUpdateOutcome.SilentSkipped -> Unit
        }
    }
    val navToConv by DesktopNotificationBus.navigateToConversation.collectAsState()
    LaunchedEffect(navToConv?.seq) {
        val ev = navToConv ?: return@LaunchedEffect
        activeTab = DesktopTab.MESSAGES
        val targetKey = if (ev.conversationKey.isNotBlank()) "${ev.packageName}::${ev.conversationKey}" else "${ev.packageName}::${ev.sender}"
        selectedConvKey = targetKey
        if (ev.id > 0L) selectedMsgId = ev.id
        DesktopNotificationBus.consumeNavigation()
    }

    LaunchedEffect(Unit) {
        server.events.collect { env ->
            when (env.type) {
                LinkMsgType.MESSAGES_LIST_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, MessagesListRes::class.java) }.getOrNull() ?: return@collect
                    if (res.latestCursor > latestCursor) latestCursor = res.latestCursor
                    val wasEmpty = messages.isEmpty()
                    messages = (if (wasEmpty) res.items else (res.items + messages).distinctBy { it.id }.sortedByDescending { it.timestamp })
                        .take(MAX_CACHED_MESSAGES)
                    if (selectedConvKey.isBlank() && messages.isNotEmpty()) {
                        val first = messages.first()
                        selectedConvKey = conversationGroupKey(first)
                        selectedMsgId = first.id
                    }
                    if (wasEmpty && messages.isNotEmpty()) {
                        scope.launch { runCatching { messagesListState.scrollToItem(0) } }
                    }
                }
                LinkMsgType.MESSAGE_NEW -> {
                    val push = runCatching { gson.fromJson(env.payloadJson, MessageNewPush::class.java) }.getOrNull() ?: return@collect
                    if (push.latestCursor > latestCursor) latestCursor = push.latestCursor
                    messages = (push.items + messages).distinctBy { it.id }.sortedByDescending { it.timestamp }
                        .take(MAX_CACHED_MESSAGES)
                    // #494 Smart auto-scroll left conversation list only if user is at top (not reading history)
                    if (push.items.isNotEmpty() && activeTab == DesktopTab.MESSAGES && messagesListState.firstVisibleItemIndex <= 1) {
                        scope.launch { runCatching { messagesListState.animateScrollToItem(0) } }
                    }
                    // #494 Append matching new messages to open thread and smart auto-scroll to latest
                    val matchingForThread = push.items.filter { conversationGroupKey(it) == selectedConvKey }
                    if (matchingForThread.isNotEmpty()) {
                        val existingThread = conversationThread
                        val mergedItems = ((existingThread?.items.orEmpty()) + matchingForThread)
                            .distinctBy { it.id }
                            .sortedByDescending { it.timestamp }
                            .take(100)
                        conversationThread = ConversationRes(
                            packageName = existingThread?.packageName?.ifBlank { matchingForThread.first().packageName } ?: matchingForThread.first().packageName,
                            conversationKey = existingThread?.conversationKey?.ifBlank { matchingForThread.first().conversationKey } ?: matchingForThread.first().conversationKey,
                            items = mergedItems,
                            hasMore = existingThread?.hasMore ?: false
                        )
                        val layoutInfo = threadListState.layoutInfo
                        val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        val total = layoutInfo.totalItemsCount
                        val isAtBottom = total == 0 || lastVisible >= (total - 2).coerceAtLeast(0)
                        if (isAtBottom && mergedItems.isNotEmpty()) {
                            scope.launch { runCatching { threadListState.animateScrollToItem(mergedItems.lastIndex) } }
                        }
                    }
                    push.items.firstOrNull()?.let { newest ->
                        val curSettings = DesktopSettingsStore.state.value
                        DesktopNotificationBus.dispatchNewMessage(newest, curSettings, onMirrorNotification)
                        if (curSettings.notificationSoundEnabled && !curSettings.isInDndWindow() && !DesktopNotificationBus.isPausedNow()) {
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
                LinkMsgType.NOTE_EDIT_RES, LinkMsgType.NOTE_PIN_TOGGLE_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, NoteMutateRes::class.java) }.getOrNull() ?: return@collect
                    if (res.ok) {
                        actionFeedback = DesktopStrings.noteUpdated
                        scope.launch { server.sendCommand(LinkMsgType.NOTES_LIST, reqId(), NotesListReq(limit = 50)) }
                    } else actionFeedback = res.errorMessage
                }
                LinkMsgType.NOTE_DELETE_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, NoteMutateRes::class.java) }.getOrNull() ?: return@collect
                    if (res.ok) {
                        actionFeedback = DesktopStrings.noteDeleted
                        scope.launch { server.sendCommand(LinkMsgType.NOTES_LIST, reqId(), NotesListReq(limit = 50)) }
                    } else actionFeedback = res.errorMessage
                }
                LinkMsgType.DESKTOP_SETTINGS_SYNC -> {
                    val push = runCatching { gson.fromJson(env.payloadJson, DesktopSettingsSyncPush::class.java) }.getOrNull() ?: return@collect
                    DesktopSettingsStore.update { cur ->
                        cur.copy(
                            mirrorMode = MirrorDisplayMode.entries.firstOrNull { it.key == push.mirrorMode } ?: cur.mirrorMode,
                            dndEnabled = push.dndEnabled ?: cur.dndEnabled,
                            floatingAssistantEnabled = push.floatingAssistantEnabled ?: cur.floatingAssistantEnabled,
                            bannerDurationSec = push.bannerDurationSec?.takeIf { it in listOf(0, 3, 5, 8) } ?: cur.bannerDurationSec
                        )
                    }
                    actionFeedback = DesktopStrings.notifCenterTitle
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
                    if (res.items.isNotEmpty()) {
                        scope.launch { runCatching { threadListState.scrollToItem(res.items.lastIndex) } }
                    }
                }
                LinkMsgType.PHONE_STATUS_RES -> {
                    val ps = runCatching { gson.fromJson(env.payloadJson, PhoneStatusDto::class.java) }.getOrNull()
                    if (ps != null) {
                        phoneStatus = ps
                        DesktopDeviceStatusHolder.updateFromLegacy(ps)
                    }
                }
                LinkMsgType.DEVICE_STATUS -> {
                    val ds = runCatching { gson.fromJson(env.payloadJson, DeviceStatusPush::class.java) }.getOrNull()
                    if (ds != null) {
                        DesktopDeviceStatusHolder.update(ds)
                        phoneStatus = PhoneStatusDto(
                            batteryPercent = ds.batteryLevel,
                            isCharging = ds.isCharging,
                            serverUptimeMs = phoneStatus?.serverUptimeMs ?: 0L,
                            deviceName = ds.deviceName.ifBlank { phoneStatus?.deviceName.orEmpty() }
                        )
                    }
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
                    chatBubbles = res.messages.takeLast(MAX_CACHED_CHAT_BUBBLES)
                }
                LinkMsgType.CHAT_REPLY -> {
                    chatTimeoutJob?.cancel()
                    chatTimeoutJob = null
                    chatSending = false
                    val res = runCatching { gson.fromJson(env.payloadJson, ChatReplyRes::class.java) }.getOrNull() ?: return@collect
                    if (res.sessionId > 0L) chatSessionId = res.sessionId
                    if (res.ok) {
                        chatBubbles = (chatBubbles + ChatBubbleDto(System.currentTimeMillis(), "assistant", res.content, System.currentTimeMillis()))
                            .takeLast(MAX_CACHED_CHAT_BUBBLES)
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
                    if (err.errorCode == LinkErrorCode.TIMEOUT) {
                        when (err.errorMessage) {
                            LinkMsgType.MESSAGE_DEEP_ANALYZE -> deepAnalyzingMsgId = -1L
                            LinkMsgType.CHAT_SEND -> {
                                chatTimeoutJob?.cancel()
                                chatTimeoutJob = null
                                chatSending = false
                            }
                        }
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

    // #494 Group flat messages into conversations keyed by packageName + conversationKey/sender
    val conversations = remember(messages, settings.language) {
        messages.groupBy { conversationGroupKey(it) }
            .map { (groupKey, groupMsgs) ->
                val sorted = groupMsgs.sortedByDescending { it.timestamp }
                val latest = sorted.first()
                val resolvedApp = DesktopAppLabels.resolveLabel(latest.packageName, latest.appLabel)
                val unread = sorted.count { !it.isRead }
                val hasStarred = sorted.any { it.isStarred }
                val inferredGroup = sorted.any { it.isGroup } ||
                    latest.conversationKey.contains("group", ignoreCase = true) ||
                    latest.conversationKey.contains("@g.us", ignoreCase = true) ||
                    sorted.map { it.sender }.distinct().size > 1
                val highestRisk = when {
                    sorted.any { it.riskLabel.equals("scam", ignoreCase = true) } -> "scam"
                    sorted.any { it.riskLabel.equals("suspicious", ignoreCase = true) } -> "suspicious"
                    else -> "safe"
                }
                ConversationSummary(
                    groupKey = groupKey,
                    packageName = latest.packageName,
                    conversationKey = latest.conversationKey,
                    appLabel = resolvedApp,
                    sender = latest.sender.ifBlank { resolvedApp },
                    latestMessage = latest,
                    unreadCount = unread,
                    hasStarred = hasStarred,
                    isGroup = inferredGroup,
                    highestRisk = highestRisk,
                    messageCount = sorted.size
                )
            }
            .sortedByDescending { it.latestMessage.timestamp }
    }

    val selectedConv = remember(conversations, selectedConvKey) {
        conversations.firstOrNull { it.groupKey == selectedConvKey }
    }
    val selectedMsg = remember(messages, selectedMsgId, selectedConv, conversationThread) {
        val fromCache = messages.firstOrNull { it.id == selectedMsgId }
        val fromThread = conversationThread?.items?.firstOrNull { it.id == selectedMsgId }
        val candidate = fromCache ?: fromThread
        if (candidate != null && (selectedConv == null || conversationGroupKey(candidate) == selectedConv.groupKey)) {
            candidate
        } else {
            selectedConv?.latestMessage
        }
    }

    LaunchedEffect(selectedConv?.groupKey, uiState.connState) {
        val conv = selectedConv ?: run {
            conversationThread = null
            return@LaunchedEffect
        }
        val convKeyForReq = conv.conversationKey.ifBlank { conv.sender }
        if (uiState.connState == LinkConnState.CONNECTED && conv.packageName.isNotBlank() && convKeyForReq.isNotBlank()) {
            server.sendCommand(
                LinkMsgType.MESSAGES_CONVERSATION,
                reqId(),
                ConversationReq(packageName = conv.packageName, conversationKey = convKeyForReq, limit = 100)
            )
        } else {
            // Offline/fallback: synthesize thread from local cached messages of this conversation
            val localItems = messages.filter { conversationGroupKey(it) == conv.groupKey }
            conversationThread = ConversationRes(
                packageName = conv.packageName,
                conversationKey = conv.conversationKey,
                items = localItems,
                hasMore = false
            )
        }
    }

    CompositionLocalProvider(LocalDesktopTokens provides tokens) {
    val activeColorScheme = if (settings.lightTheme) {
        androidx.compose.material3.lightColorScheme(
            background = Color(0xFFE2E8F0),
            surface = tokens.surface,
            surfaceVariant = tokens.surfaceElevated,
            primary = accentColor,
            onPrimary = Color.White,
            onBackground = tokens.textPrimary,
            onSurface = tokens.textPrimary,
            onSurfaceVariant = tokens.textSecondary,
            error = DesktopPalette.GlassError
        )
    } else {
        darkColorScheme(
            background = bgTop,
            surface = tokens.surface,
            surfaceVariant = tokens.surfaceElevated,
            primary = accentColor,
            onPrimary = Color(0xFF0A0E17),
            onBackground = tokens.textPrimary,
            onSurface = tokens.textPrimary,
            onSurfaceVariant = tokens.textSecondary,
            error = DesktopPalette.GlassError
        )
    }
    MaterialTheme(
        colorScheme = activeColorScheme,
        typography = typography
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val tabMod = ev.isCtrlPressed || ev.isAltPressed
                    when {
                        ev.key == Key.Escape -> {
                            deepAnalysisResult = null
                            editingTodo = null
                            showNewTodoDialog = false
                            editingNote = null
                            pendingUpdateDialog = null
                            mirrorToast = null
                            DesktopNotificationBus.clearBanner()
                            actionFeedback = null
                            true
                        }
                        tabMod && ev.key == Key.One -> { activeTab = DesktopTab.MESSAGES; true }
                        tabMod && ev.key == Key.Two -> { activeTab = DesktopTab.STATS; true }
                        tabMod && ev.key == Key.Three -> { activeTab = DesktopTab.TODOS; true }
                        tabMod && ev.key == Key.Four -> { activeTab = DesktopTab.CHAT; true }
                        tabMod && ev.key == Key.Five -> { activeTab = DesktopTab.RULES; true }
                        tabMod && ev.key == Key.Six -> { activeTab = DesktopTab.SETTINGS; true }
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
                // Background: Custom image (cover semantics) + draw-phase readability scrim OR 4 preset gradients
                if (customBgBitmap != null) {
                    Image(
                        bitmap = customBgBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(modifier = Modifier.fillMaxSize().drawBehind { drawRect(tokens.scrimColor) })
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
                            .background(tokens.sidebarBg)
                            .border(width = 1.dp, color = tokens.border)
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
                                        color = tokens.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = DesktopStrings.versionLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.textMuted
                                    )
                                }
                            }
                            TextButton(
                                onClick = { DesktopSettingsStore.update { it.copy(sidebarCollapsed = !it.sidebarCollapsed) } }
                            ) {
                                Text(if (settings.sidebarCollapsed) "»" else "«", color = accentColor)
                            }
                        }

                        HorizontalDivider(color = tokens.border, modifier = Modifier.padding(vertical = 4.dp))

                        val navItems = listOf(
                            Triple(DesktopTab.MESSAGES, DesktopStrings.navMessages, DesktopIcons.Messages),
                            Triple(DesktopTab.STATS, DesktopStrings.navStats, DesktopIcons.Stats),
                            Triple(DesktopTab.TODOS, DesktopStrings.navTodos, DesktopIcons.Todos),
                            Triple(DesktopTab.CHAT, DesktopStrings.navChat, DesktopIcons.Chat),
                            Triple(DesktopTab.RULES, DesktopStrings.navRules, DesktopIcons.Rules),
                            Triple(DesktopTab.SETTINGS, DesktopStrings.navSettings, DesktopIcons.Settings)
                        )
                        navItems.forEach { (tab, label, tabIcon) ->
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
                                if (settings.sidebarCollapsed) {
                                    Icon(
                                        imageVector = tabIcon,
                                        contentDescription = label,
                                        tint = if (selected) accentColor else tokens.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = tabIcon,
                                            contentDescription = null,
                                            tint = if (selected) accentColor else tokens.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (selected) accentColor else tokens.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        // #513 一鍵勿擾開關（靜音彈窗/懸浮球氣泡/音效，記錄不中斷，與 Tray 同步）
                        FilterChip(
                            selected = settings.quickDndActive,
                            onClick = { DesktopSettingsStore.update { it.copy(quickDndActive = !it.quickDndActive) } },
                            label = {
                                Text(
                                    text = if (settings.sidebarCollapsed) "☾" else if (settings.quickDndActive) "☾ ${DesktopStrings.quickDndOn}" else "☾ ${DesktopStrings.quickDndOff}",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))

                        // Persistent Connection Status Card
                        val isPulsing = uiState.connState == LinkConnState.WAITING_FOR_PHONE
                        val dotColor = when (uiState.connState) {
                            LinkConnState.CONNECTED -> DesktopPalette.GlassSuccess
                            LinkConnState.WAITING_FOR_PHONE -> accentColor
                            LinkConnState.ERROR -> DesktopPalette.GlassError
                            LinkConnState.STOPPED -> tokens.textMuted
                        }
                        val statusText = when (uiState.connState) {
                            LinkConnState.CONNECTED -> DesktopStrings.statusConnected + uiState.pairedDeviceName
                            LinkConnState.WAITING_FOR_PHONE -> DesktopStrings.serverListeningOn(uiState.port, 0)
                            LinkConnState.ERROR -> DesktopStrings.statusServerError
                            LinkConnState.STOPPED -> DesktopStrings.statusServerStopped
                        }
                        GlassCard(modifier = Modifier.fillMaxWidth(), containerColor = tokens.surfaceElevated) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(if (settings.sidebarCollapsed) 8.dp else 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                horizontalAlignment = if (settings.sidebarCollapsed) Alignment.CenterHorizontally else Alignment.Start
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PulsingStatusDot(
                                        color = dotColor,
                                        pulsing = isPulsing && isWindowVisible,
                                        animationsEnabled = settings.animationsEnabled
                                    )
                                    if (!settings.sidebarCollapsed) {
                                        Text(statusText, style = MaterialTheme.typography.labelSmall, color = tokens.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                if (!settings.sidebarCollapsed) {
                                    val ds by DesktopDeviceStatusHolder.status.collectAsState()
                                    val ps = phoneStatus
                                    val batPct = ds?.batteryLevel?.takeIf { it >= 0 } ?: ps?.batteryPercent ?: -1
                                    val isChg = ds?.isCharging ?: ps?.isCharging ?: false
                                    if (batPct >= 0) {
                                        val chargeLabel = if (isChg) {
                                            when (ds?.chargeType) {
                                                "usb" -> " ⚡USB"
                                                "wireless" -> " ⚡${DesktopStrings.chargeWireless}"
                                                else -> " ⚡${DesktopStrings.charging}"
                                            }
                                        } else ""
                                        val netTag = when (ds?.networkType) {
                                            "wifi" -> " · Wi-Fi"
                                            "cellular" -> " · 5G/4G"
                                            else -> ""
                                        }
                                        val isOnline = uiState.connState == LinkConnState.CONNECTED
                                        val statusLine = if (isOnline) {
                                            "${DesktopStrings.phoneBattery}: $batPct%$chargeLabel$netTag"
                                        } else {
                                            val lastAt = ds?.updatedAtMs?.takeIf { it > 0L }?.let { formatRelativeConvTime(it, settings.use24HourTime) }.orEmpty()
                                            "${DesktopStrings.phoneBattery}: $batPct% (${DesktopStrings.lastKnownAt} $lastAt)"
                                        }
                                        Text(
                                            statusLine,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isOnline) DesktopPalette.GlassSuccess else tokens.textMuted,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isOnline && ds != null && ds!!.storageAvailMb > 0L) {
                                            val gb = String.format("%.1f GB", ds!!.storageAvailMb / 1024.0)
                                            val dndTag = if (ds!!.isDndActive) " · ${DesktopStrings.dndShort}" else ""
                                            Text(
                                                "${DesktopStrings.storageAvail}: $gb$dndTag",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = tokens.textMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
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
                        // #497 A: Top Mirror Notification Banner (auto-dismiss + hover-pause + overwrite latest + click to jump)
                        val bannerEvent by DesktopNotificationBus.latestBanner.collectAsState()
                        val bannerHoverSource = remember { MutableInteractionSource() }
                        val bannerHovered by bannerHoverSource.collectIsHoveredAsState()
                        LaunchedEffect(bannerEvent?.seq, bannerHovered, settings.bannerDurationSec) {
                            if (bannerEvent == null || bannerHovered) return@LaunchedEffect
                            val sec = settings.bannerDurationSec
                            if (sec <= 0) return@LaunchedEffect
                            delay(sec * 1000L)
                            DesktopNotificationBus.clearBanner()
                        }
                        AnimatedVisibility(
                            visible = bannerEvent != null,
                            enter = fadeIn(tween(150)) + slideInVertically { -it / 2 },
                            exit = fadeOut(tween(150)) + slideOutVertically { -it / 2 }
                        ) {
                            bannerEvent?.let { ev ->
                                val riskDotColor = when (ev.riskLabel.lowercase()) {
                                    "scam" -> DesktopPalette.GlassError
                                    "suspicious" -> DesktopPalette.GlassWarning
                                    else -> DesktopPalette.GlassSuccess
                                }
                                GlassCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                        .hoverable(bannerHoverSource)
                                        .clickable {
                                            DesktopNotificationBus.clearBanner()
                                            DesktopNotificationBus.requestNavigateTo(ev)
                                        },
                                    containerColor = accentColor.copy(alpha = 0.16f)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(riskDotColor)
                                        )
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = "${ev.appLabel} · ${ev.sender}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = tokens.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = ev.preview.take(settings.previewLength),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = tokens.textSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        TextButton(onClick = { DesktopNotificationBus.clearBanner() }) { Text(DesktopStrings.dismiss) }
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
                                GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), containerColor = tokens.surfaceElevated) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(fb, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, modifier = Modifier.weight(1f))
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
                                        Text(DesktopStrings.notConnectedHint, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
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
                                        // #494 Conversation-Grouped Master-Detail Messages Layout
                                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                listOf(
                                                    "all" to DesktopStrings.filterAll,
                                                    "unread" to DesktopStrings.filterUnread,
                                                    "starred" to DesktopStrings.filterStarred,
                                                    "group" to DesktopStrings.filterGroup,
                                                    "scam" to DesktopStrings.filterScam
                                                ).forEach { (key, label) ->
                                                    FilterChip(
                                                        selected = msgFilter == key,
                                                        onClick = {
                                                            msgFilter = key
                                                            if (key in setOf("unread", "starred", "scam")) {
                                                                scope.launch {
                                                                    sendOrNotify(
                                                                        LinkMsgType.MESSAGES_LIST,
                                                                        MessagesListReq(filter = key, offset = 0, limit = 60, sinceCursor = 0L)
                                                                    )
                                                                }
                                                            }
                                                        },
                                                        label = { Text(label) }
                                                    )
                                                }
                                                OutlinedTextField(
                                                    value = msgSearch,
                                                    onValueChange = { msgSearch = it },
                                                    leadingIcon = { Icon(DesktopIcons.Search, contentDescription = null, tint = tokens.textMuted, modifier = Modifier.size(18.dp)) },
                                                    placeholder = { Text(DesktopStrings.searchMessagesHint, style = MaterialTheme.typography.bodySmall) },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f).focusRequester(searchFocusRequester)
                                                )
                                                SpringOutlinedButton(
                                                    onClick = { scope.launch { sendOrNotify(LinkMsgType.MESSAGES_MARK_READ, MarkReadReq(all = true)) } },
                                                    animationsEnabled = settings.animationsEnabled
                                                ) {
                                                    Icon(DesktopIcons.MarkRead, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(DesktopStrings.markAllRead)
                                                }
                                                SpringOutlinedButton(
                                                    onClick = {
                                                        batchSelectMode = !batchSelectMode
                                                        if (!batchSelectMode) selectedBatchKeys = emptySet()
                                                    },
                                                    animationsEnabled = settings.animationsEnabled
                                                ) {
                                                    Text(if (batchSelectMode) DesktopStrings.batchDoneBtn else DesktopStrings.batchSelectBtn)
                                                }
                                                SpringOutlinedButton(
                                                    onClick = {
                                                        messages = emptyList()
                                                        scope.launch {
                                                            sendOrNotify(
                                                                LinkMsgType.MESSAGES_LIST,
                                                                MessagesListReq(filter = "all", offset = 0, limit = 60, sinceCursor = 0L)
                                                            )
                                                        }
                                                    },
                                                    animationsEnabled = settings.animationsEnabled
                                                ) {
                                                    Icon(DesktopIcons.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(DesktopStrings.refresh)
                                                }
                                            }
                                            Text(DesktopStrings.markReadNote, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)

                                            val visibleConversations = remember(conversations, msgFilter, msgSearch) {
                                                val byFilter = when (msgFilter) {
                                                    "unread" -> conversations.filter { it.unreadCount > 0 }
                                                    "starred" -> conversations.filter { it.hasStarred }
                                                    "group" -> conversations.filter { it.isGroup }
                                                    "scam" -> conversations.filter { it.highestRisk.equals("scam", ignoreCase = true) }
                                                    else -> conversations
                                                }
                                                val q = msgSearch.trim()
                                                if (q.isBlank()) byFilter
                                                else byFilter.filter { conv ->
                                                    conv.appLabel.contains(q, ignoreCase = true) ||
                                                        conv.sender.contains(q, ignoreCase = true) ||
                                                        conv.latestMessage.preview.contains(q, ignoreCase = true) ||
                                                        conv.packageName.contains(q, ignoreCase = true)
                                                }
                                            }

                                            // #504 Batch Multi-Select Action Bar
                                            AnimatedVisibility(visible = batchSelectMode && selectedBatchKeys.isNotEmpty()) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    SpringButton(
                                                        onClick = {
                                                            val targets = visibleConversations.filter { it.groupKey in selectedBatchKeys }
                                                            scope.launch {
                                                                targets.forEach { c ->
                                                                    sendOrNotify(
                                                                        LinkMsgType.MESSAGES_MARK_READ,
                                                                        MarkReadReq(packageName = c.packageName, conversationKey = c.conversationKey)
                                                                    )
                                                                }
                                                                selectedBatchKeys = emptySet()
                                                                batchSelectMode = false
                                                            }
                                                        },
                                                        animationsEnabled = settings.animationsEnabled
                                                    ) { Text("${DesktopStrings.batchMarkReadBtn} (${selectedBatchKeys.size})") }
                                                    SpringOutlinedButton(
                                                        onClick = {
                                                            val pkgs = visibleConversations
                                                                .filter { it.groupKey in selectedBatchKeys }
                                                                .map { it.packageName }
                                                                .distinct()
                                                            scope.launch {
                                                                pkgs.forEach { pkg ->
                                                                    sendOrNotify(LinkMsgType.MUTED_APPS_TOGGLE, MutedAppToggleReq(packageName = pkg, muted = true))
                                                                }
                                                                selectedBatchKeys = emptySet()
                                                                batchSelectMode = false
                                                            }
                                                        },
                                                        animationsEnabled = settings.animationsEnabled
                                                    ) { Text(DesktopStrings.batchMuteAppsBtn) }
                                                }
                                            }

                                            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                                // Left Master List: Conversation List
                                                Box(modifier = Modifier.weight(0.46f).fillMaxHeight()) {
                                                    if (visibleConversations.isEmpty()) {
                                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                            Text(DesktopStrings.emptyMessages, color = tokens.textMuted)
                                                        }
                                                    } else {
                                                        LazyColumn(
                                                            state = messagesListState,
                                                            verticalArrangement = Arrangement.spacedBy(if (settings.messageCardStyle == MessageCardStyle.COMPACT_LIST) 4.dp else listSpace)
                                                        ) {
                                                            items(visibleConversations, key = { it.groupKey }) { conv ->
                                                                val isSelected = conv.groupKey == selectedConvKey
                                                                val isBatchChecked = conv.groupKey in selectedBatchKeys
                                                                val cardBg = if (isSelected || isBatchChecked) accentColor.copy(alpha = 0.16f) else tokens.surface
                                                                val corner = if (settings.messageCardStyle == MessageCardStyle.COMPACT_LIST) {
                                                                    (settings.messageCardCornerDp - 4).coerceAtLeast(8).dp
                                                                } else {
                                                                    settings.messageCardCornerDp.dp
                                                                }
                                                                val avatarColor = DesktopAppLabels.resolveColor(conv.packageName)
                                                                val riskDotColor = when (conv.highestRisk.lowercase()) {
                                                                    "scam" -> DesktopPalette.GlassError
                                                                    "suspicious" -> DesktopPalette.GlassWarning
                                                                    else -> DesktopPalette.GlassSuccess
                                                                }
                                                                val relTime = formatRelativeConvTime(conv.latestMessage.timestamp, settings.use24HourTime)
                                                                Row(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .clip(RoundedCornerShape(corner))
                                                                        .background(cardBg)
                                                                        .border(
                                                                            1.dp,
                                                                            if (isSelected || isBatchChecked) accentColor.copy(alpha = 0.5f) else tokens.border,
                                                                            RoundedCornerShape(corner)
                                                                        )
                                                                        .clickable {
                                                                            if (batchSelectMode) {
                                                                                selectedBatchKeys = if (isBatchChecked) selectedBatchKeys - conv.groupKey else selectedBatchKeys + conv.groupKey
                                                                            } else {
                                                                                selectedConvKey = conv.groupKey
                                                                                selectedMsgId = conv.latestMessage.id
                                                                            }
                                                                        }
                                                                        .padding(if (settings.messageCardStyle == MessageCardStyle.COMPACT_LIST) 10.dp else cardPad),
                                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    if (batchSelectMode) {
                                                                        Checkbox(
                                                                            checked = isBatchChecked,
                                                                            onCheckedChange = { checked ->
                                                                                selectedBatchKeys = if (checked) selectedBatchKeys + conv.groupKey else selectedBatchKeys - conv.groupKey
                                                                            }
                                                                        )
                                                                    }
                                                                    // Circular Avatar with risk indicator dot
                                                                    Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .size(36.dp)
                                                                                .clip(CircleShape)
                                                                                .background(avatarColor.copy(alpha = 0.24f))
                                                                                .border(1.dp, avatarColor.copy(alpha = 0.65f), CircleShape),
                                                                            contentAlignment = Alignment.Center
                                                                        ) {
                                                                            Text(
                                                                                text = DesktopAppLabels.avatarInitial(conv.sender, conv.appLabel),
                                                                                style = MaterialTheme.typography.titleSmall,
                                                                                fontWeight = FontWeight.Bold,
                                                                                color = avatarColor
                                                                            )
                                                                        }
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .align(Alignment.BottomEnd)
                                                                                .size(10.dp)
                                                                                .clip(CircleShape)
                                                                                .background(riskDotColor)
                                                                                .border(1.5.dp, tokens.surface, CircleShape)
                                                                        )
                                                                    }

                                                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                                        Row(
                                                                            modifier = Modifier.fillMaxWidth(),
                                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                                            verticalAlignment = Alignment.CenterVertically
                                                                        ) {
                                                                            Row(
                                                                                modifier = Modifier.weight(1f),
                                                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                                verticalAlignment = Alignment.CenterVertically
                                                                            ) {
                                                                                Text(
                                                                                    text = "${if (conv.hasStarred) "★ " else ""}${conv.sender}",
                                                                                    fontWeight = if (conv.unreadCount > 0) FontWeight.Bold else FontWeight.Medium,
                                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                                    color = tokens.textPrimary,
                                                                                    maxLines = 1,
                                                                                    overflow = TextOverflow.Ellipsis,
                                                                                    modifier = Modifier.weight(1f, fill = false)
                                                                                )
                                                                                Text(
                                                                                    text = conv.appLabel,
                                                                                    style = MaterialTheme.typography.labelSmall,
                                                                                    color = avatarColor,
                                                                                    maxLines = 1,
                                                                                    overflow = TextOverflow.Ellipsis
                                                                                )
                                                                                if (conv.isGroup) {
                                                                                    Text(
                                                                                        text = DesktopStrings.groupBadge,
                                                                                        style = MaterialTheme.typography.labelSmall,
                                                                                        color = accentColor,
                                                                                        modifier = Modifier
                                                                                            .clip(RoundedCornerShape(6.dp))
                                                                                            .background(accentColor.copy(alpha = 0.14f))
                                                                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                                                                    )
                                                                                }
                                                                            }
                                                                            Spacer(Modifier.width(6.dp))
                                                                            Text(
                                                                                text = relTime,
                                                                                style = MaterialTheme.typography.labelSmall,
                                                                                color = if (conv.highestRisk.equals("scam", ignoreCase = true)) DesktopPalette.GlassError else tokens.textMuted
                                                                            )
                                                                        }

                                                                        Row(
                                                                            modifier = Modifier.fillMaxWidth(),
                                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                                            verticalAlignment = Alignment.CenterVertically
                                                                        ) {
                                                                            Text(
                                                                                text = conv.latestMessage.preview.take(settings.previewLength),
                                                                                style = MaterialTheme.typography.bodySmall,
                                                                                color = tokens.textSecondary,
                                                                                maxLines = 1,
                                                                                overflow = TextOverflow.Ellipsis,
                                                                                modifier = Modifier.weight(1f)
                                                                            )
                                                                            if (conv.unreadCount > 0) {
                                                                                Spacer(Modifier.width(8.dp))
                                                                                Box(
                                                                                    modifier = Modifier
                                                                                        .clip(CircleShape)
                                                                                        .background(accentColor)
                                                                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                                                                    contentAlignment = Alignment.Center
                                                                                ) {
                                                                                    Text(
                                                                                        text = if (conv.unreadCount > 99) "99+" else conv.unreadCount.toString(),
                                                                                        style = MaterialTheme.typography.labelSmall,
                                                                                        fontWeight = FontWeight.Bold,
                                                                                        color = Color(0xFF0A0E17)
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                // Right Detail Pane (Conversation Header + Actions + Chronological Thread + Quick Reply)
                                                GlassCard(modifier = Modifier.weight(0.54f).fillMaxHeight()) {
                                                    val detail = selectedMsg
                                                    val conv = selectedConv
                                                    if (detail == null) {
                                                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                                            Text(DesktopStrings.selectMessageHint, color = tokens.textMuted)
                                                        }
                                                    } else {
                                                        val resolvedApp = conv?.appLabel ?: DesktopAppLabels.resolveLabel(detail.packageName, detail.appLabel)
                                                        val appColor = DesktopAppLabels.resolveColor(detail.packageName)
                                                        Column(
                                                            modifier = Modifier.fillMaxSize().padding(18.dp),
                                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                                        ) {
                                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(34.dp)
                                                                            .clip(CircleShape)
                                                                            .background(appColor.copy(alpha = 0.24f))
                                                                            .border(1.dp, appColor.copy(alpha = 0.65f), CircleShape),
                                                                        contentAlignment = Alignment.Center
                                                                    ) {
                                                                        Text(
                                                                            text = DesktopAppLabels.avatarInitial(detail.sender, resolvedApp),
                                                                            style = MaterialTheme.typography.labelLarge,
                                                                            fontWeight = FontWeight.Bold,
                                                                            color = appColor
                                                                        )
                                                                    }
                                                                    Column(modifier = Modifier.weight(1f)) {
                                                                        Text(detail.sender, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                                        Text("$resolvedApp · ${timeFmt.format(Date(detail.timestamp))}", style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                                                                    }
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

                                                            // Action Row
                                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                if ((conv?.unreadCount ?: 0) > 0 && detail.conversationKey.isNotBlank()) {
                                                                    TextButton(onClick = {
                                                                        scope.launch {
                                                                            sendOrNotify(
                                                                                LinkMsgType.MESSAGES_MARK_READ,
                                                                                MarkReadReq(packageName = detail.packageName, conversationKey = detail.conversationKey)
                                                                            )
                                                                        }
                                                                    }) {
                                                                        Text(DesktopStrings.conversationMarkRead)
                                                                    }
                                                                } else if (!detail.isRead) {
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

                                                            HorizontalDivider(color = tokens.border)
                                                            Text(DesktopStrings.conversationTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                                                            val threadItems = remember(conversationThread, messages, conv?.groupKey) {
                                                                val raw = conversationThread?.items?.takeIf { it.isNotEmpty() }
                                                                    ?: if (conv != null) messages.filter { conversationGroupKey(it) == conv.groupKey } else emptyList()
                                                                raw.sortedBy { it.timestamp }
                                                            }
                                                            if (threadItems.isEmpty()) {
                                                                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                                    Text(DesktopStrings.conversationEmpty, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                                                                }
                                                            } else {
                                                                LazyColumn(
                                                                    state = threadListState,
                                                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                                ) {
                                                                    items(threadItems, key = { it.id }) { item ->
                                                                        val itemSelected = item.id == detail.id
                                                                        Column(
                                                                            modifier = Modifier
                                                                                .fillMaxWidth()
                                                                                .clip(RoundedCornerShape(10.dp))
                                                                                .background(if (itemSelected) accentColor.copy(alpha = 0.14f) else tokens.surfaceElevated.copy(alpha = 0.65f))
                                                                                .border(
                                                                                    width = 1.dp,
                                                                                    color = if (itemSelected) accentColor.copy(alpha = 0.45f) else Color.Transparent,
                                                                                    shape = RoundedCornerShape(10.dp)
                                                                                )
                                                                                .clickable { selectedMsgId = item.id }
                                                                                .padding(10.dp)
                                                                        ) {
                                                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                                                Text(
                                                                                    text = "${if (item.isStarred) "★ " else ""}${item.sender}",
                                                                                    style = MaterialTheme.typography.labelSmall,
                                                                                    fontWeight = FontWeight.SemiBold,
                                                                                    color = accentColor
                                                                                )
                                                                                Text(
                                                                                    text = "${DesktopStrings.riskLabelText(item.riskLabel)} · ${formatRelativeConvTime(item.timestamp, settings.use24HourTime)}",
                                                                                    style = MaterialTheme.typography.labelSmall,
                                                                                    color = if (item.riskLabel.equals("scam", ignoreCase = true)) DesktopPalette.GlassError else tokens.textMuted
                                                                                )
                                                                            }
                                                                            Spacer(Modifier.height(3.dp))
                                                                            Text(item.preview.take(settings.previewLength), style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
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
                                            modifier = Modifier.fillMaxSize().verticalScroll(statsScrollState),
                                            verticalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                                StatBox(DesktopStrings.statsTotal, stats.total.toString(), Modifier.weight(1f), icon = DesktopIcons.Messages, iconTint = accentColor)
                                                StatBox(DesktopStrings.statsToday, stats.today.toString(), Modifier.weight(1f), icon = DesktopIcons.Stats, iconTint = DesktopPalette.GlassSuccess)
                                                StatBox(DesktopStrings.statsUnread, stats.unread.toString(), Modifier.weight(1f), icon = DesktopIcons.Bell, iconTint = DesktopPalette.GlassWarning)
                                                StatBox(DesktopStrings.statsScam, stats.scam.toString(), Modifier.weight(1f), icon = DesktopIcons.Warning, iconTint = DesktopPalette.GlassError)
                                            }
                                            DesktopStatsCharts(stats = stats, accentColor = accentColor)
                                        }
                                    }

                                    DesktopTab.TODOS -> {
                                        val nowMs = System.currentTimeMillis()
                                        val noteColorChoices = listOf("", "#A78BFA", "#6EA8FE", "#34D399", "#FBBF24", "#FB7185")
                                        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            // Left: Todos Full CRUD (#499)
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
                                                    SpringOutlinedButton(onClick = {
                                                        editTodoTitle = newTodoTitle.trim()
                                                        editTodoNotes = ""
                                                        editTodoDueAt = System.currentTimeMillis() + 60L * 60L * 1000L
                                                        editTodoDueCustom = formatDueTime(editTodoDueAt)
                                                        editTodoPriority = 1
                                                        editTodoRepeat = ""
                                                        editingTodo = null
                                                        showNewTodoDialog = true
                                                    }, animationsEnabled = settings.animationsEnabled) { Text(DesktopStrings.todoAddFull) }
                                                }
                                                if (todos.isEmpty()) {
                                                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                        Text(DesktopStrings.todosEmpty, color = tokens.textMuted)
                                                    }
                                                } else {
                                                    LazyColumn(state = todosListState, verticalArrangement = Arrangement.spacedBy(listSpace), modifier = Modifier.weight(1f)) {
                                                        items(todos, key = { it.id }) { t ->
                                                            val isOverdue = !t.isDone && t.dueAt > 0L && t.dueAt < nowMs
                                                            val prioColor = when (t.priority) {
                                                                2 -> DesktopPalette.GlassError
                                                                0 -> DesktopPalette.GlassSuccess
                                                                else -> DesktopPalette.GlassWarning
                                                            }
                                                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                                                Row(modifier = Modifier.fillMaxWidth().padding(cardPad), verticalAlignment = Alignment.CenterVertically) {
                                                                    Checkbox(
                                                                        checked = t.isDone,
                                                                        onCheckedChange = { checked ->
                                                                            scope.launch { sendOrNotify(LinkMsgType.TODOS_TOGGLE, TodosToggleReq(id = t.id, done = checked)) }
                                                                        }
                                                                    )
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(8.dp)
                                                                            .clip(CircleShape)
                                                                            .background(prioColor)
                                                                    )
                                                                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                                                        Text(
                                                                            text = t.title,
                                                                            style = MaterialTheme.typography.bodyMedium,
                                                                            textDecoration = if (t.isDone) TextDecoration.LineThrough else null,
                                                                            color = if (t.isDone) tokens.textMuted else if (isOverdue) DesktopPalette.GlassError else tokens.textPrimary
                                                                        )
                                                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                            if (t.dueAt > 0L) {
                                                                                val duePrefix = if (isOverdue) "${DesktopStrings.todoOverdue} · " else ""
                                                                                Text(
                                                                                    text = "$duePrefix${formatDueTime(t.dueAt)}",
                                                                                    style = MaterialTheme.typography.labelSmall,
                                                                                    color = if (isOverdue) DesktopPalette.GlassError else tokens.textMuted
                                                                                )
                                                                            }
                                                                            if (t.repeatRule.isNotBlank()) {
                                                                                Text(
                                                                                    text = when (t.repeatRule) {
                                                                                        "daily" -> DesktopStrings.todoRepeatDaily
                                                                                        "weekly" -> DesktopStrings.todoRepeatWeekly
                                                                                        else -> t.repeatRule
                                                                                    },
                                                                                    style = MaterialTheme.typography.labelSmall,
                                                                                    color = accentColor
                                                                                )
                                                                            }
                                                                        }
                                                                        if (t.notes.isNotBlank()) {
                                                                            Text(
                                                                                text = t.notes,
                                                                                style = MaterialTheme.typography.labelSmall,
                                                                                color = tokens.textSecondary,
                                                                                maxLines = 2,
                                                                                overflow = TextOverflow.Ellipsis
                                                                            )
                                                                        }
                                                                    }
                                                                    TextButton(onClick = {
                                                                        editingTodo = t
                                                                        editTodoTitle = t.title
                                                                        editTodoNotes = t.notes
                                                                        editTodoDueAt = if (t.dueAt > 0L) t.dueAt else System.currentTimeMillis() + 3600_000L
                                                                        editTodoDueCustom = formatDueTime(editTodoDueAt)
                                                                        editTodoPriority = t.priority
                                                                        editTodoRepeat = t.repeatRule
                                                                        showNewTodoDialog = true
                                                                    }) { Text(DesktopStrings.todoEdit) }
                                                                    TextButton(onClick = { scope.launch { sendOrNotify(LinkMsgType.TODOS_DELETE, TodoDeleteReq(id = t.id)) } }) {
                                                                        Text(DesktopStrings.todoDelete, color = DesktopPalette.GlassError)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // Right: Notes Full CRUD Panel (#499)
                                            GlassCard(modifier = Modifier.weight(0.45f).fillMaxHeight()) {
                                                Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(DesktopIcons.Note, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                                            Text(DesktopStrings.notesTitle, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                                                        }
                                                        FilterChip(
                                                            selected = newNotePinned,
                                                            onClick = { newNotePinned = !newNotePinned },
                                                            label = { Text(DesktopStrings.notePin, style = MaterialTheme.typography.labelSmall) }
                                                        )
                                                    }
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
                                                                    if (sendOrNotify(LinkMsgType.NOTE_ADD, NoteAddReq(content = c, pinned = newNotePinned, colorHex = newNoteColor))) {
                                                                        newNoteContent = ""
                                                                        newNotePinned = false
                                                                    }
                                                                }
                                                            }
                                                        }, animationsEnabled = settings.animationsEnabled) { Text(DesktopStrings.add) }
                                                    }
                                                    // Color palette dots for new note
                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Text(DesktopStrings.noteColorLabel, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                                                        noteColorChoices.forEach { hex ->
                                                            val selected = newNoteColor == hex
                                                            val swatch = DesktopPalette.parseHexColor(hex) ?: tokens.border
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(if (selected) 20.dp else 16.dp)
                                                                    .clip(CircleShape)
                                                                    .background(swatch)
                                                                    .border(if (selected) 2.dp else 1.dp, if (selected) tokens.textPrimary else tokens.border, CircleShape)
                                                                    .clickable { newNoteColor = hex }
                                                            )
                                                        }
                                                    }
                                                    if (notes.isEmpty()) {
                                                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                            Text(DesktopStrings.notesEmpty, color = tokens.textMuted)
                                                        }
                                                    } else {
                                                        val sortedNotes = remember(notes) {
                                                            notes.sortedWith(compareByDescending<NoteItemDto> { it.pinned }.thenByDescending { it.updatedAt })
                                                        }
                                                        LazyColumn(state = notesListState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            items(sortedNotes, key = { it.id }) { n ->
                                                                val noteAccent = DesktopPalette.parseHexColor(n.colorHex)
                                                                Row(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .clip(RoundedCornerShape(10.dp))
                                                                        .background(tokens.surfaceElevated.copy(alpha = 0.6f))
                                                                        .border(1.dp, if (n.pinned) accentColor.copy(alpha = 0.4f) else tokens.border, RoundedCornerShape(10.dp))
                                                                        .clickable {
                                                                            editingNote = n
                                                                            editNoteContent = n.content
                                                                            editNotePinned = n.pinned
                                                                            editNoteColor = n.colorHex
                                                                        }
                                                                        .padding(10.dp),
                                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                    verticalAlignment = Alignment.Top
                                                                ) {
                                                                    if (noteAccent != null) {
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .width(4.dp)
                                                                                .height(36.dp)
                                                                                .clip(RoundedCornerShape(2.dp))
                                                                                .background(noteAccent)
                                                                        )
                                                                    }
                                                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                                        if (n.relatedAppLabel.isNotBlank() || n.pinned) {
                                                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                                if (n.pinned) {
                                                                                    Icon(DesktopIcons.Pin, contentDescription = null, tint = accentColor, modifier = Modifier.size(13.dp))
                                                                                }
                                                                                if (n.relatedAppLabel.isNotBlank()) {
                                                                                    Text(n.relatedAppLabel, style = MaterialTheme.typography.labelSmall, color = accentColor)
                                                                                }
                                                                            }
                                                                        }
                                                                        Text(n.content, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                                                    }
                                                                    Column(horizontalAlignment = Alignment.End) {
                                                                        TextButton(onClick = {
                                                                            scope.launch {
                                                                                sendOrNotify(LinkMsgType.NOTE_PIN_TOGGLE, NotePinToggleReq(id = n.id, pinned = !n.pinned))
                                                                            }
                                                                        }) {
                                                                            Text(if (n.pinned) DesktopStrings.noteUnpin else DesktopStrings.notePin, style = MaterialTheme.typography.labelSmall)
                                                                        }
                                                                        TextButton(onClick = {
                                                                            scope.launch { sendOrNotify(LinkMsgType.NOTE_DELETE, NoteDeleteReq(id = n.id)) }
                                                                        }) {
                                                                            Text(DesktopStrings.noteDelete, style = MaterialTheme.typography.labelSmall, color = DesktopPalette.GlassError)
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

                                    DesktopTab.CHAT -> {
                                        // Multi-Session AI Chat with Markdown + Copy + Quick Commands + Quote Notification + Ctrl+Enter (#499)
                                        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            // Session Sidebar
                                            GlassCard(modifier = Modifier.width(220.dp).fillMaxHeight()) {
                                                Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                        Text(DesktopStrings.chatSessionsTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                                                        TextButton(onClick = {
                                                            scope.launch { sendOrNotify(LinkMsgType.CHAT_SESSION_NEW, ChatSessionNewReq(title = "Desktop Chat")) }
                                                        }) { Text(DesktopStrings.chatNewSession, color = accentColor) }
                                                    }
                                                    HorizontalDivider(color = tokens.border)
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
                                                                Text(s.title, style = MaterialTheme.typography.bodySmall, color = if (sel) accentColor else tokens.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // Chat Main Column
                                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                                // Quick Commands Bar + Quote Latest Notification (#499)
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                                                    listOf(
                                                        DesktopStrings.quickPromptSummary,
                                                        DesktopStrings.quickPromptUnread,
                                                        DesktopStrings.quickPromptScam,
                                                        DesktopStrings.quickPromptTodos
                                                    ).forEach { prompt ->
                                                        FilterChip(selected = false, onClick = { sendChatMessage(prompt) }, label = { Text(prompt) })
                                                    }
                                                    val latestMsg = messages.maxByOrNull { it.timestamp }
                                                    if (latestMsg != null) {
                                                        FilterChip(
                                                            selected = false,
                                                            onClick = {
                                                                val app = DesktopAppLabels.resolveLabel(latestMsg.packageName, latestMsg.appLabel)
                                                                val quote = "[$app · ${latestMsg.sender}] ${latestMsg.preview.take(120)}\n"
                                                                chatInput = if (chatInput.isBlank()) quote else "$quote$chatInput"
                                                            },
                                                            label = { Text(DesktopStrings.chatQuoteLatest, color = accentColor) }
                                                        )
                                                    }
                                                }

                                                if (chatBubbles.isEmpty()) {
                                                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                        Text(DesktopStrings.emptyChat, color = tokens.textMuted, style = MaterialTheme.typography.bodyMedium)
                                                    }
                                                } else {
                                                    LazyColumn(state = chatListState, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                                                        .background(if (isUser) accentColor.copy(alpha = 0.22f) else tokens.surface)
                                                                        .border(1.dp, if (isUser) accentColor.copy(alpha = 0.35f) else tokens.border, RoundedCornerShape(chatCorner))
                                                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                                                ) {
                                                                    if (isUser) {
                                                                        Text(b.content, style = chatTextStyle, color = tokens.textPrimary)
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
                                                    PulsingThinkingLabel(
                                                        text = DesktopStrings.chatThinking,
                                                        color = accentColor,
                                                        animationsEnabled = settings.animationsEnabled && isWindowVisible
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
                                            modifier = Modifier.fillMaxSize().verticalScroll(rulesScrollState),
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            // 1. Collapsible Allowlist Rules (#501)
                                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { DesktopSettingsStore.update { it.copy(rulesCollapseAllowlist = !it.rulesCollapseAllowlist) } },
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(DesktopIcons.Rules, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                                            Text("${DesktopStrings.whitelistTitle} (${whitelistRules.size})", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                                                        }
                                                        Text(if (settings.rulesCollapseAllowlist) "▸" else "▾", color = tokens.textMuted)
                                                    }
                                                    AnimatedVisibility(visible = !settings.rulesCollapseAllowlist) {
                                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                                                Text(DesktopStrings.emptyWhitelist, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                                                            } else {
                                                                whitelistRules.forEach { r ->
                                                                    HorizontalDivider(color = tokens.border)
                                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                                        Text("${r.ruleText} ${if (r.packageName.isNotBlank()) "(${r.packageName})" else ""}", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                                                        TextButton(onClick = { scope.launch { sendOrNotify(LinkMsgType.WHITELIST_REMOVE, RuleRemoveReq(r.id)) } }) {
                                                                            Text(DesktopStrings.remove, color = DesktopPalette.GlassError)
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // 2. Collapsible Tags (#501)
                                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { DesktopSettingsStore.update { it.copy(rulesCollapseTags = !it.rulesCollapseTags) } },
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(DesktopIcons.Tag, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                                            Text("${DesktopStrings.tagsTitle} (${tags.size})", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                                                        }
                                                        Text(if (settings.rulesCollapseTags) "▸" else "▾", color = tokens.textMuted)
                                                    }
                                                    AnimatedVisibility(visible = !settings.rulesCollapseTags) {
                                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                                                Text(DesktopStrings.emptyTags, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                                                            } else {
                                                                tags.forEach { t ->
                                                                    HorizontalDivider(color = tokens.border)
                                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                                        Text(t.name, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                                                        TextButton(onClick = { scope.launch { sendOrNotify(LinkMsgType.TAGS_REMOVE, TagRemoveReq(t.id)) } }) {
                                                                            Text(DesktopStrings.remove, color = DesktopPalette.GlassError)
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // 3. Collapsible Muted Apps (#501)
                                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { DesktopSettingsStore.update { it.copy(rulesCollapseMuted = !it.rulesCollapseMuted) } },
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(DesktopIcons.Bell, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                                            Text("${DesktopStrings.mutedAppsTitle} (${mutedApps.count { it.muted }})", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                                                        }
                                                        Text(if (settings.rulesCollapseMuted) "▸" else "▾", color = tokens.textMuted)
                                                    }
                                                    AnimatedVisibility(visible = !settings.rulesCollapseMuted) {
                                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                            if (mutedApps.isEmpty()) {
                                                                Text(DesktopStrings.mutedAppsEmpty, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                                                            } else {
                                                                mutedApps.forEach { app ->
                                                                    HorizontalDivider(color = tokens.border)
                                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                                        Column(modifier = Modifier.weight(1f)) {
                                                                            Text(DesktopAppLabels.resolveLabel(app.packageName, app.appLabel), style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
                                                                            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted, fontFamily = FontFamily.Monospace)
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

                            val effectiveTransition = if (settings.animationsEnabled) settings.pageTransitionStyle else PageTransitionStyle.NONE
                            if (effectiveTransition != PageTransitionStyle.NONE) {
                                AnimatedContent(
                                    targetState = activeTab,
                                    transitionSpec = {
                                        val forward = targetState.ordinal >= initialState.ordinal
                                        DesktopMotion.pageTransition(effectiveTransition, forward)
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
                        title = { Text(DesktopStrings.deepAnalysisTitle, color = tokens.textPrimary) },
                        text = {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                DesktopMarkdownText(markdown = markdown, accentColor = accentColor)
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { deepAnalysisResult = null }) { Text(DesktopStrings.ok) }
                        },
                        containerColor = tokens.dialogContainer
                    )
                }

                // #499 Full Todo Add / Edit Dialog (title, notes, dueAt, priority, repeatRule)
                if (showNewTodoDialog) {
                    val target = editingTodo
                    AlertDialog(
                        onDismissRequest = { showNewTodoDialog = false; editingTodo = null },
                        title = { Text(if (target == null) DesktopStrings.todoAddFull else DesktopStrings.todoEdit, color = tokens.textPrimary) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = editTodoTitle,
                                    onValueChange = { editTodoTitle = it },
                                    label = { Text(DesktopStrings.todoAddHint) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editTodoNotes,
                                    onValueChange = { editTodoNotes = it },
                                    label = { Text(DesktopStrings.todoNotesHint) },
                                    maxLines = 3,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                // Priority chips
                                Text(DesktopStrings.todoPriorityLabel, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        0 to DesktopStrings.todoPriorityLow,
                                        1 to DesktopStrings.todoPriorityMed,
                                        2 to DesktopStrings.todoPriorityHigh
                                    ).forEach { (p, label) ->
                                        FilterChip(
                                            selected = editTodoPriority == p,
                                            onClick = { editTodoPriority = p },
                                            label = { Text(label) }
                                        )
                                    }
                                }
                                // Reminder time presets + custom input
                                Text(DesktopStrings.todoDueLabel, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            editTodoDueAt = System.currentTimeMillis() + 60L * 60L * 1000L
                                            editTodoDueCustom = formatDueTime(editTodoDueAt)
                                        },
                                        label = { Text(DesktopStrings.todoDue1h) }
                                    )
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            val cal = java.util.Calendar.getInstance().apply {
                                                set(java.util.Calendar.HOUR_OF_DAY, 20)
                                                set(java.util.Calendar.MINUTE, 0)
                                                set(java.util.Calendar.SECOND, 0)
                                                if (timeInMillis <= System.currentTimeMillis()) add(java.util.Calendar.DAY_OF_YEAR, 1)
                                            }
                                            editTodoDueAt = cal.timeInMillis
                                            editTodoDueCustom = formatDueTime(editTodoDueAt)
                                        },
                                        label = { Text(DesktopStrings.todoDueTonight) }
                                    )
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            val cal = java.util.Calendar.getInstance().apply {
                                                add(java.util.Calendar.DAY_OF_YEAR, 1)
                                                set(java.util.Calendar.HOUR_OF_DAY, 9)
                                                set(java.util.Calendar.MINUTE, 0)
                                                set(java.util.Calendar.SECOND, 0)
                                            }
                                            editTodoDueAt = cal.timeInMillis
                                            editTodoDueCustom = formatDueTime(editTodoDueAt)
                                        },
                                        label = { Text(DesktopStrings.todoDueTomorrow) }
                                    )
                                }
                                OutlinedTextField(
                                    value = editTodoDueCustom,
                                    onValueChange = { raw ->
                                        editTodoDueCustom = raw
                                        parseDueTime(raw)?.let { editTodoDueAt = it }
                                    },
                                    label = { Text(DesktopStrings.todoDueCustomHint) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                // Repeat rule chips
                                Text(DesktopStrings.todoRepeatLabel, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        "" to DesktopStrings.todoRepeatNone,
                                        "daily" to DesktopStrings.todoRepeatDaily,
                                        "weekly" to DesktopStrings.todoRepeatWeekly
                                    ).forEach { (rule, label) ->
                                        FilterChip(
                                            selected = editTodoRepeat == rule,
                                            onClick = { editTodoRepeat = rule },
                                            label = { Text(label) }
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val title = editTodoTitle.trim()
                                if (title.isNotBlank()) {
                                    val resolvedDue = parseDueTime(editTodoDueCustom) ?: editTodoDueAt
                                    scope.launch {
                                        val ok = if (target == null) {
                                            sendOrNotify(
                                                LinkMsgType.TODOS_ADD,
                                                TodoAddReq(
                                                    title = title,
                                                    dueAt = resolvedDue,
                                                    repeatRule = editTodoRepeat,
                                                    notes = editTodoNotes.trim(),
                                                    priority = editTodoPriority
                                                )
                                            )
                                        } else {
                                            sendOrNotify(
                                                LinkMsgType.TODOS_EDIT,
                                                TodoEditReq(
                                                    id = target.id,
                                                    title = title,
                                                    dueAt = resolvedDue,
                                                    repeatRule = editTodoRepeat,
                                                    notes = editTodoNotes.trim(),
                                                    priority = editTodoPriority
                                                )
                                            )
                                        }
                                        if (ok) {
                                            if (target == null) newTodoTitle = ""
                                            showNewTodoDialog = false
                                            editingTodo = null
                                        }
                                    }
                                }
                            }) { Text(DesktopStrings.todoSave) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showNewTodoDialog = false; editingTodo = null }) { Text(DesktopStrings.dismiss) }
                        },
                        containerColor = tokens.dialogContainer
                    )
                }

                // #499 Edit Note Dialog
                editingNote?.let { targetNote ->
                    val noteColorChoices = listOf("", "#A78BFA", "#6EA8FE", "#34D399", "#FBBF24", "#FB7185")
                    AlertDialog(
                        onDismissRequest = { editingNote = null },
                        title = { Text(DesktopStrings.noteEditTitle, color = tokens.textPrimary) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = editNoteContent,
                                    onValueChange = { editNoteContent = it },
                                    minLines = 3,
                                    maxLines = 8,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    FilterChip(
                                        selected = editNotePinned,
                                        onClick = { editNotePinned = !editNotePinned },
                                        label = { Text(if (editNotePinned) DesktopStrings.noteUnpin else DesktopStrings.notePin) }
                                    )
                                    Text(DesktopStrings.noteColorLabel, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                                    noteColorChoices.forEach { hex ->
                                        val selected = editNoteColor == hex
                                        val swatch = DesktopPalette.parseHexColor(hex) ?: tokens.border
                                        Box(
                                            modifier = Modifier
                                                .size(if (selected) 20.dp else 16.dp)
                                                .clip(CircleShape)
                                                .background(swatch)
                                                .border(if (selected) 2.dp else 1.dp, if (selected) tokens.textPrimary else tokens.border, CircleShape)
                                                .clickable { editNoteColor = hex }
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val content = editNoteContent.trim()
                                if (content.isNotBlank()) {
                                    scope.launch {
                                        if (sendOrNotify(LinkMsgType.NOTE_EDIT, NoteEditReq(id = targetNote.id, content = content, pinned = editNotePinned, colorHex = editNoteColor))) {
                                            editingNote = null
                                        }
                                    }
                                }
                            }) { Text(DesktopStrings.todoSave) }
                        },
                        dismissButton = {
                            TextButton(onClick = { editingNote = null }) { Text(DesktopStrings.dismiss) }
                        },
                        containerColor = tokens.dialogContainer
                    )
                }

                // #478 Desktop Update Available Dialog
                pendingUpdateDialog?.let { upd ->
                    DesktopUpdateDialog(
                        update = upd,
                        accentColor = accentColor,
                        containerColor = tokens.dialogContainer,
                        animationsEnabled = settings.animationsEnabled,
                        onDismiss = { pendingUpdateDialog = null },
                        onFeedback = { actionFeedback = it }
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = Color.Unspecified
) {
    val tokens = LocalDesktopTokens.current
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (iconTint == Color.Unspecified) tokens.textMuted else iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
            }
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, color = tokens.textPrimary)
        }
    }
}

/**
 * #489 隔離式 draw-phase 呼吸點：僅於 pulsing && animationsEnabled 時啟動 rememberInfiniteTransition，
 * 連線中或縮至系統匣時零影格喚醒，且僅於 graphicsLayer lambda 讀取動畫值（零重組）。
 */
@Composable
private fun PulsingStatusDot(
    color: Color,
    pulsing: Boolean,
    animationsEnabled: Boolean
) {
    if (!pulsing || !animationsEnabled) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        return
    }
    val transition = rememberInfiniteTransition(label = "conn_pulse")
    val alphaState = transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "conn_pulse_alpha"
    )
    Box(
        modifier = Modifier
            .size(9.dp)
            .graphicsLayer { alpha = alphaState.value }
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun PulsingThinkingLabel(
    text: String,
    color: Color,
    animationsEnabled: Boolean
) {
    if (!animationsEnabled) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        return
    }
    val transition = rememberInfiniteTransition(label = "chat_thinking_pulse")
    val alphaState = transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chat_thinking_alpha"
    )
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .padding(vertical = 6.dp)
            .graphicsLayer { alpha = alphaState.value }
    )
}

private fun formatDueTime(ts: Long): String {
    if (ts <= 0L) return ""
    return runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))
    }.getOrDefault("")
}

private fun parseDueTime(raw: String): Long? {
    val clean = raw.trim()
    if (clean.isEmpty()) return null
    val patterns = listOf("yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm", "MM-dd HH:mm", "HH:mm")
    for (p in patterns) {
        val parsed = runCatching {
            val fmt = java.text.SimpleDateFormat(p, java.util.Locale.getDefault()).apply { isLenient = false }
            val date = fmt.parse(clean) ?: return@runCatching null
            if (p == "HH:mm") {
                val now = java.util.Calendar.getInstance()
                val t = java.util.Calendar.getInstance().apply { time = date }
                now.set(java.util.Calendar.HOUR_OF_DAY, t.get(java.util.Calendar.HOUR_OF_DAY))
                now.set(java.util.Calendar.MINUTE, t.get(java.util.Calendar.MINUTE))
                now.set(java.util.Calendar.SECOND, 0)
                if (now.timeInMillis <= System.currentTimeMillis()) now.add(java.util.Calendar.DAY_OF_YEAR, 1)
                now.timeInMillis
            } else {
                date.time
            }
        }.getOrNull()
        if (parsed != null) return parsed
    }
    return null
}
