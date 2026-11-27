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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
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
    MESSAGES, STATS, TODOS, CHAT, RULES, SETTINGS, REPORTS, TOOLS, HOME
}

private const val MAX_CACHED_MESSAGES = 2000
private const val MESSAGES_PAGE_SIZE = 60
private const val MESSAGES_LIST_DEBOUNCE_MS = 1000L
private const val MAX_CACHED_CHAT_BUBBLES = 120
private const val FULL_REFRESH_COOLDOWN_MS = 10_000L

internal data class ConversationSummary(
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

internal fun conversationGroupKey(m: MessageSummaryDto): String =
    if (m.conversationKey.isNotBlank()) "${m.packageName}::${m.conversationKey}"
    else "${m.packageName}::${m.sender}"

/**
 * #531 標題三段回退鏈：sender → appLabel →「未命名訊息」（雙語）。
 */
internal fun resolveMessageTitle(sender: String, packageName: String, appLabel: String): String {
    val cleanSender = sender.trim()
    if (cleanSender.isNotEmpty()) return cleanSender
    val cleanApp = if (appLabel.isNotBlank() || packageName.isNotBlank()) {
        DesktopAppLabels.resolveLabel(packageName, appLabel).trim()
    } else ""
    if (cleanApp.isNotEmpty()) return cleanApp
    return DesktopStrings.unnamedMessage
}

/**
 * #531 新舊訊息合併：同一 id 保留較新狀態（isRead / isStarred 不得舊蓋新），按 timestamp 降序。
 */
internal fun mergeMessagesById(
    existing: List<MessageSummaryDto>,
    incoming: List<MessageSummaryDto>,
    preferIncoming: Boolean
): List<MessageSummaryDto> {
    if (existing.isEmpty()) return incoming.distinctBy { it.id }.sortedByDescending { it.timestamp }.take(MAX_CACHED_MESSAGES)
    if (incoming.isEmpty()) return existing
    val map = LinkedHashMap<Long, MessageSummaryDto>(existing.size + incoming.size)
    existing.forEach { map[it.id] = it }
    incoming.forEach { inc ->
        val cur = map[inc.id]
        map[inc.id] = if (cur == null) {
            inc
        } else if (preferIncoming) {
            inc.copy(isRead = inc.isRead || cur.isRead)
        } else {
            // Older page load: keep existing fresher isRead / isStarred state
            inc.copy(isRead = cur.isRead || inc.isRead, isStarred = cur.isStarred)
        }
    }
    return map.values.sortedByDescending { it.timestamp }.take(MAX_CACHED_MESSAGES)
}

private val CONV_TIME_24_FMT = ThreadLocal.withInitial { SimpleDateFormat("HH:mm", Locale.getDefault()) }
private val CONV_TIME_12_FMT = ThreadLocal.withInitial { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
private val CONV_DATE_FMT = ThreadLocal.withInitial { SimpleDateFormat("MM/dd", Locale.getDefault()) }
private val TODO_DUE_FMT = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

internal fun formatRelativeConvTime(timestamp: Long, use24Hour: Boolean): String {
    if (timestamp <= 0L) return ""
    val nowCal = java.util.Calendar.getInstance()
    val msgCal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = nowCal.get(java.util.Calendar.YEAR) == msgCal.get(java.util.Calendar.YEAR) &&
        nowCal.get(java.util.Calendar.DAY_OF_YEAR) == msgCal.get(java.util.Calendar.DAY_OF_YEAR)
    val fmt = if (sameDay) {
        if (use24Hour) CONV_TIME_24_FMT.get() else CONV_TIME_12_FMT.get()
    } else {
        CONV_DATE_FMT.get()
    }
    return fmt.format(Date(timestamp))
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

    val accentColor = remember(settings.accentPreset, settings.customAccentHex) {
        DesktopPalette.resolveAccentColor(settings.accentPreset, settings.customAccentHex)
    }
    val (bgTop, bgBottom) = remember(settings.bgTheme, settings.lightTheme) {
        if (settings.lightTheme) Color(0xFFE8EEF7) to Color(0xFFDDE6F2)
        else DesktopPalette.bgGradientColors(settings.bgTheme)
    }
    var batchSelectMode by remember { mutableStateOf(false) }
    var selectedBatchKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var latestReceivedClipboard by remember { mutableStateOf("") }
    var groupReports by remember { mutableStateOf<List<GroupReportSummaryDto>>(emptyList()) }
    var proactiveEvents by remember { mutableStateOf<List<com.phantom.checker.shared.ProactiveEventPush>>(emptyList()) }
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
        mutableStateOf(if (uiState.isPaired) initial else DesktopTab.HOME)
    }

    // Messages Master-Detail state (#494 conversation-grouped)
    var msgFilter by remember { mutableStateOf("all") }
    var msgSearch by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    var messages by remember { mutableStateOf<List<MessageSummaryDto>>(emptyList()) }
    var selectedConvKey by remember { mutableStateOf("") }
    var selectedMsgId by remember { mutableLongStateOf(-1L) }
    var latestCursor by remember { mutableLongStateOf(0L) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMoreMessages by remember { mutableStateOf(true) }
    var loadedPageOffset by remember { mutableIntStateOf(0) }
    var lastSyncedAtMs by remember { mutableLongStateOf(0L) }
    var loadMoreError by remember { mutableStateOf<String?>(null) }
    var pendingPagedReqId by remember { mutableStateOf<String?>(null) }
    var lastMessagesListReqAtMs by remember { mutableLongStateOf(0L) }
    var loadMoreTimeoutJob by remember { mutableStateOf<Job?>(null) }
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
        val now = System.currentTimeMillis()
        if (now - lastMessagesListReqAtMs < MESSAGES_LIST_DEBOUNCE_MS) return
        lastMessagesListReqAtMs = now
        scope.launch {
            val cursor = if (useCursor) latestCursor else 0L
            if (!useCursor) {
                loadedPageOffset = 0
                hasMoreMessages = true
                loadMoreError = null
                pendingPagedReqId = null
            }
            sendOrNotify(
                LinkMsgType.MESSAGES_LIST,
                MessagesListReq(filter = msgFilter, offset = 0, limit = MESSAGES_PAGE_SIZE, sinceCursor = cursor)
            )
        }
    }

    fun loadMoreMessages() {
        if (isLoadingMore || !hasMoreMessages) return
        val now = System.currentTimeMillis()
        if (now - lastMessagesListReqAtMs < MESSAGES_LIST_DEBOUNCE_MS) return
        if (uiState.connState != LinkConnState.CONNECTED) {
            loadMoreError = DesktopStrings.loadMoreFailed
            return
        }
        lastMessagesListReqAtMs = now
        isLoadingMore = true
        loadMoreError = null
        val rid = reqId()
        pendingPagedReqId = rid
        val nextOffset = loadedPageOffset.coerceAtLeast(messages.size)
        scope.launch {
            val ok = server.sendCommand(
                LinkMsgType.MESSAGES_LIST,
                rid,
                MessagesListReq(filter = msgFilter, offset = nextOffset, limit = MESSAGES_PAGE_SIZE, sinceCursor = 0L)
            )
            if (!ok) {
                isLoadingMore = false
                pendingPagedReqId = null
                loadMoreError = DesktopStrings.loadMoreFailed
                return@launch
            }
            loadMoreTimeoutJob?.cancel()
            loadMoreTimeoutJob = scope.launch {
                delay(15_000L)
                if (isLoadingMore && pendingPagedReqId == rid) {
                    isLoadingMore = false
                    pendingPagedReqId = null
                    loadMoreError = DesktopStrings.loadMoreFailed
                }
            }
        }
    }

    var lastFullRefreshAtMs by remember { mutableLongStateOf(0L) }

    suspend fun refreshAllOnConnect() {
        val now = System.currentTimeMillis()
        server.sendCommand(LinkMsgType.MESSAGES_SUBSCRIBE, reqId(), emptyMap<String, String>())
        delay(120L)
        lastMessagesListReqAtMs = System.currentTimeMillis()
        server.sendCommand(
            LinkMsgType.MESSAGES_LIST,
            reqId(),
            MessagesListReq(filter = msgFilter, offset = 0, limit = MESSAGES_PAGE_SIZE, sinceCursor = latestCursor)
        )
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
            if (isLoadingMore) {
                loadMoreTimeoutJob?.cancel()
                loadMoreTimeoutJob = null
                isLoadingMore = false
                pendingPagedReqId = null
                loadMoreError = DesktopStrings.loadMoreFailed
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
                    val isPagedResponse = pendingPagedReqId != null && env.reqId == pendingPagedReqId
                    if (isPagedResponse) {
                        loadMoreTimeoutJob?.cancel()
                        loadMoreTimeoutJob = null
                        isLoadingMore = false
                        pendingPagedReqId = null
                        loadMoreError = null
                        loadedPageOffset = (loadedPageOffset.coerceAtLeast(messages.size)) + res.items.size
                        hasMoreMessages = res.hasMore && res.items.isNotEmpty()
                    } else if (messages.isEmpty()) {
                        loadedPageOffset = res.items.size
                        hasMoreMessages = res.hasMore
                        loadMoreError = null
                    }
                    lastSyncedAtMs = System.currentTimeMillis()
                    val wasEmpty = messages.isEmpty()
                    messages = mergeMessagesById(
                        existing = messages,
                        incoming = res.items,
                        preferIncoming = !isPagedResponse
                    )
                    DesktopHoverDataHolder.updateLatestMessage(messages.firstOrNull())
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
                    lastSyncedAtMs = System.currentTimeMillis()
                    messages = mergeMessagesById(
                        existing = messages,
                        incoming = push.items,
                        preferIncoming = true
                    )
                    DesktopHoverDataHolder.updateLatestMessage(messages.firstOrNull())
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
                    DesktopHoverDataHolder.updateStats(stats)
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
                LinkMsgType.CLIPBOARD_SYNC -> {
                    val payload = runCatching { gson.fromJson(env.payloadJson, ClipboardSyncPayload::class.java) }.getOrNull() ?: return@collect
                    if (settings.clipboardSyncEnabled && payload.text.isNotBlank()) {
                        if (settings.clipboardOnlyWhenFocused && !isWindowVisible) return@collect
                        val text = payload.text.take(settings.clipboardMaxChars)
                        if (settings.clipboardOtpGuard && DesktopClipboardGuard.looksLikeOtp(text)) return@collect
                        latestReceivedClipboard = text
                        runCatching {
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
                        }
                    }
                }
                LinkMsgType.OPEN_URL_ON_PEER -> {
                    val req = runCatching { gson.fromJson(env.payloadJson, OpenUrlReq::class.java) }.getOrNull() ?: return@collect
                    val url = req.url.trim().take(2048)
                    if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                        runCatching {
                            if (java.awt.Desktop.isDesktopSupported()) {
                                java.awt.Desktop.getDesktop().browse(java.net.URI(url))
                            }
                        }
                    }
                }
                LinkMsgType.GROUP_REPORTS_LIST_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, GroupReportsListRes::class.java) }.getOrNull() ?: return@collect
                    groupReports = res.items
                    res.items.firstOrNull()?.let { first ->
                        DesktopHoverDataHolder.updateLatestIntelOrReport("${first.appLabel} · ${first.conversationTitle}: ${first.summaryMarkdown.take(60)}")
                    }
                }
                LinkMsgType.PROACTIVE_EVENT_PUSH -> {
                    val ev = runCatching { gson.fromJson(env.payloadJson, com.phantom.checker.shared.ProactiveEventPush::class.java) }.getOrNull() ?: return@collect
                    proactiveEvents = (listOf(ev) + proactiveEvents).distinctBy { it.id }.take(30)
                    DesktopHoverDataHolder.updateLatestIntelOrReport("${ev.title}: ${ev.summary}")
                    val curSettings = DesktopSettingsStore.state.value
                    DesktopNotificationBus.dispatchNewMessage(
                        msg = MessageSummaryDto(
                            id = ev.id,
                            packageName = "com.phantom.checker.proactive",
                            appLabel = DesktopStrings.proactiveIntelMirrorTitle,
                            sender = ev.title,
                            preview = ev.summary.take(60),
                            riskLabel = "safe",
                            conversationKey = "",
                            timestamp = ev.timestamp.takeIf { it > 0L } ?: System.currentTimeMillis()
                        ),
                        settings = curSettings,
                        onNativeTrayNotify = onMirrorNotification
                    )
                }
                LinkMsgType.WATCH_TOPIC_MUTE_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, com.phantom.checker.shared.WatchTopicMuteRes::class.java) }.getOrNull() ?: return@collect
                    if (res.ok) {
                        actionFeedback = DesktopStrings.proactiveIntelMutedFeedback
                    }
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
                    sender = resolveMessageTitle(latest.sender, latest.packageName, resolvedApp),
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

    ProvideDesktopTheme(settings) {
    val tokens = LocalDesktopTokens.current
    val activeColorScheme = if (settings.lightTheme) {
        androidx.compose.material3.lightColorScheme(
            background = Color(0xFFE2E8F0),
            surface = tokens.surface,
            surfaceVariant = tokens.surfaceElevated,
            primary = accentColor,
            onPrimary = tokens.onAccent,
            onBackground = tokens.textPrimary,
            onSurface = tokens.textPrimary,
            onSurfaceVariant = tokens.textSecondary,
            error = tokens.error
        )
    } else {
        darkColorScheme(
            background = bgTop,
            surface = tokens.surface,
            surfaceVariant = tokens.surfaceElevated,
            primary = accentColor,
            onPrimary = tokens.onAccent,
            onBackground = tokens.textPrimary,
            onSurface = tokens.textPrimary,
            onSurfaceVariant = tokens.textSecondary,
            error = tokens.error
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
                        tabMod && ev.key == Key.Zero -> { activeTab = DesktopTab.HOME; true }
                        tabMod && ev.key == Key.One -> { activeTab = DesktopTab.MESSAGES; true }
                        tabMod && ev.key == Key.Two -> { activeTab = DesktopTab.STATS; true }
                        tabMod && ev.key == Key.Three -> { activeTab = DesktopTab.TODOS; true }
                        tabMod && ev.key == Key.Four -> { activeTab = DesktopTab.CHAT; true }
                        tabMod && ev.key == Key.Five -> { activeTab = DesktopTab.RULES; true }
                        tabMod && ev.key == Key.Six -> { activeTab = DesktopTab.SETTINGS; true }
                        tabMod && ev.key == Key.Seven -> { activeTab = DesktopTab.REPORTS; true }
                        tabMod && ev.key == Key.Eight -> { activeTab = DesktopTab.TOOLS; true }
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

                androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val windowClass = remember(maxWidth) { DesktopWindowClass.fromWidth(maxWidth) }
                    var compactManualExpand by remember(windowClass) { mutableStateOf(false) }
                    val effectiveSidebarCollapsed = if (windowClass == DesktopWindowClass.COMPACT) {
                        !compactManualExpand
                    } else {
                        settings.sidebarCollapsed
                    }
                    val totalUnread = remember(stats.unread, conversations) {
                        maxOf(stats.unread, conversations.sumOf { it.unreadCount })
                    }

                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Sidebar (COMPACT auto-collapses to 76dp; MEDIUM caps at 210dp; EXPANDED/LARGE uses setting)
                    val sidebarW = when {
                        effectiveSidebarCollapsed -> 76.dp
                        windowClass == DesktopWindowClass.MEDIUM -> settings.sidebarWidthDp.coerceAtMost(210).dp
                        else -> settings.sidebarWidthDp.dp
                    }
                    Column(
                        modifier = Modifier
                            .width(sidebarW)
                            .fillMaxHeight()
                            .background(tokens.sidebarBg)
                            .border(width = 1.dp, color = tokens.border)
                            .padding(horizontal = if (effectiveSidebarCollapsed) 10.dp else 16.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!effectiveSidebarCollapsed) {
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
                                onClick = {
                                    if (windowClass == DesktopWindowClass.COMPACT) {
                                        compactManualExpand = !compactManualExpand
                                    } else {
                                        DesktopSettingsStore.update { it.copy(sidebarCollapsed = !it.sidebarCollapsed) }
                                    }
                                }
                            ) {
                                Text(if (effectiveSidebarCollapsed) "»" else "«", color = tokens.accentText)
                            }
                        }

                        HorizontalDivider(color = tokens.border, modifier = Modifier.padding(vertical = 4.dp))

                        val navItems = listOf(
                            Triple(DesktopTab.HOME, DesktopStrings.tabHome, DesktopIcons.Messages),
                            Triple(DesktopTab.MESSAGES, DesktopStrings.navMessages, DesktopIcons.Messages),
                            Triple(DesktopTab.REPORTS, DesktopStrings.tabReports, DesktopIcons.Stats),
                            Triple(DesktopTab.STATS, DesktopStrings.navStats, DesktopIcons.Stats),
                            Triple(DesktopTab.TODOS, DesktopStrings.navTodos, DesktopIcons.Todos),
                            Triple(DesktopTab.CHAT, DesktopStrings.navChat, DesktopIcons.Chat),
                            Triple(DesktopTab.TOOLS, DesktopStrings.tabTools, DesktopIcons.Rules),
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
                                targetValue = if (selected) tokens.selectedBg else Color.Transparent,
                                animationSpec = tween(if (settings.animationsEnabled) 150 else 0),
                                label = "nav_bg_${tab.name}"
                            )
                            val navBorder by animateColorAsState(
                                targetValue = if (selected) accentColor.copy(alpha = 0.38f) else Color.Transparent,
                                animationSpec = tween(if (settings.animationsEnabled) 150 else 0),
                                label = "nav_border_${tab.name}"
                            )
                            val showUnreadBadge = tab == DesktopTab.MESSAGES && totalUnread > 0
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { scaleX = navScale; scaleY = navScale }
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(navBg)
                                    .border(1.dp, navBorder, RoundedCornerShape(12.dp))
                                    .clickable(interactionSource = navInteraction, indication = null) {
                                        activeTab = tab
                                        if (windowClass == DesktopWindowClass.COMPACT) compactManualExpand = false
                                    }
                                    .padding(horizontal = if (effectiveSidebarCollapsed) 8.dp else 14.dp, vertical = 11.dp),
                                contentAlignment = if (effectiveSidebarCollapsed) Alignment.Center else Alignment.CenterStart
                            ) {
                                if (effectiveSidebarCollapsed) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = tabIcon,
                                            contentDescription = label,
                                            tint = if (selected) tokens.accentText else tokens.textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        if (showUnreadBadge) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(tokens.error)
                                            )
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = tabIcon,
                                            contentDescription = null,
                                            tint = if (selected) tokens.accentText else tokens.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (selected) tokens.accentText else tokens.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (showUnreadBadge) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(accentColor)
                                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (totalUnread > 99) "99+" else totalUnread.toString(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = tokens.onAccent
                                                )
                                            }
                                        }
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
                                    text = if (effectiveSidebarCollapsed) "☾" else if (settings.quickDndActive) "☾ ${DesktopStrings.quickDndOn}" else "☾ ${DesktopStrings.quickDndOff}",
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
                            LinkConnState.CONNECTED -> tokens.success
                            LinkConnState.WAITING_FOR_PHONE -> accentColor
                            LinkConnState.ERROR -> tokens.error
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
                                modifier = Modifier.fillMaxWidth().padding(if (effectiveSidebarCollapsed) 8.dp else 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                horizontalAlignment = if (effectiveSidebarCollapsed) Alignment.CenterHorizontally else Alignment.Start
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PulsingStatusDot(
                                        color = dotColor,
                                        pulsing = isPulsing && isWindowVisible,
                                        animationsEnabled = settings.animationsEnabled
                                    )
                                    if (!effectiveSidebarCollapsed) {
                                        Text(statusText, style = MaterialTheme.typography.labelSmall, color = tokens.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                if (!effectiveSidebarCollapsed) {
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
                                            color = if (isOnline) tokens.success else tokens.textMuted,
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
                                        Text(err, style = MaterialTheme.typography.labelSmall, color = tokens.error, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }

                    // Right Main Content Area (responsive pagePadding from DesktopWindowClass)
                    Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(windowClass.pagePadding)) {
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

                        if (uiState.connState != LinkConnState.CONNECTED && activeTab != DesktopTab.SETTINGS && activeTab != DesktopTab.HOME) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                GlassCard {
                                    Column(
                                        modifier = Modifier.padding(28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(DesktopStrings.notConnectedHint, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            SpringButton(onClick = { activeTab = DesktopTab.HOME }, animationsEnabled = settings.animationsEnabled, containerColor = accentColor) {
                                                Text(DesktopStrings.tabHome)
                                            }
                                            SpringOutlinedButton(onClick = { scope.launch(kotlinx.coroutines.Dispatchers.IO) { server.restart(uiState.port) } }, animationsEnabled = settings.animationsEnabled) {
                                                Text(DesktopStrings.serverRestartBtn)
                                            }
                                            SpringOutlinedButton(onClick = { activeTab = DesktopTab.SETTINGS }, animationsEnabled = settings.animationsEnabled) {
                                                Text(DesktopStrings.navSettings)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            val renderTab: @Composable (DesktopTab) -> Unit = { tab ->
                                when (tab) {
                                    DesktopTab.MESSAGES -> {
                                        DesktopMessagesPage(
                                            windowClass = windowClass,
                                            settings = settings,
                                            tokens = tokens,
                                            accentColor = accentColor,
                                            cardPad = cardPad,
                                            listSpace = listSpace,
                                            timeFmt = timeFmt,
                                            conversations = conversations,
                                            messages = messages,
                                            tags = tags,
                                            conversationThread = conversationThread,
                                            msgFilter = msgFilter,
                                            onMsgFilterChange = { msgFilter = it },
                                            msgSearch = msgSearch,
                                            onMsgSearchChange = { msgSearch = it },
                                            searchFocusRequester = searchFocusRequester,
                                            selectedConvKey = selectedConvKey,
                                            onSelectConversation = { convKey, msgId ->
                                                selectedConvKey = convKey
                                                selectedMsgId = msgId
                                            },
                                            selectedMsg = selectedMsg,
                                            selectedConv = selectedConv,
                                            onSelectMessageId = { selectedMsgId = it },
                                            batchSelectMode = batchSelectMode,
                                            onToggleBatchSelectMode = {
                                                batchSelectMode = !batchSelectMode
                                                if (!batchSelectMode) selectedBatchKeys = emptySet()
                                            },
                                            selectedBatchKeys = selectedBatchKeys,
                                            onSelectedBatchKeysChange = { selectedBatchKeys = it },
                                            onClearBatchAndExit = {
                                                selectedBatchKeys = emptySet()
                                                batchSelectMode = false
                                            },
                                            deepAnalyzingMsgId = deepAnalyzingMsgId,
                                            onSetDeepAnalyzingMsgId = { deepAnalyzingMsgId = it },
                                            replyText = replyText,
                                            onReplyTextChange = { replyText = it },
                                            messagesListState = messagesListState,
                                            threadListState = threadListState,
                                            isLoadingMore = isLoadingMore,
                                            hasMoreMessages = hasMoreMessages,
                                            lastSyncedAtMs = lastSyncedAtMs,
                                            loadMoreError = loadMoreError,
                                            onLoadMoreMessages = { loadMoreMessages() },
                                            onRefreshMessages = {
                                                messages = emptyList()
                                                loadedPageOffset = 0
                                                hasMoreMessages = true
                                                isLoadingMore = false
                                                pendingPagedReqId = null
                                                loadMoreError = null
                                                lastMessagesListReqAtMs = System.currentTimeMillis()
                                                scope.launch {
                                                    sendOrNotify(
                                                        LinkMsgType.MESSAGES_LIST,
                                                        MessagesListReq(filter = msgFilter, offset = 0, limit = MESSAGES_PAGE_SIZE, sinceCursor = 0L)
                                                    )
                                                }
                                            },
                                            sendOrNotify = { type, payload -> sendOrNotify(type, payload) }
                                        )
                                    }

                                    DesktopTab.STATS -> {
                                        DesktopStatsPage(
                                            windowClass = windowClass,
                                            tokens = tokens,
                                            accentColor = accentColor,
                                            stats = stats,
                                            scrollState = statsScrollState,
                                            onNavigateMessagesWithFilter = { filterKey ->
                                                msgFilter = filterKey
                                                activeTab = DesktopTab.MESSAGES
                                                if (filterKey in setOf("unread", "starred", "scam")) {
                                                    scope.launch {
                                                        sendOrNotify(
                                                            LinkMsgType.MESSAGES_LIST,
                                                            MessagesListReq(filter = filterKey, offset = 0, limit = 60, sinceCursor = 0L)
                                                        )
                                                    }
                                                }
                                            }
                                        )
                                    }

                                    DesktopTab.TODOS -> {
                                        DesktopTodosNotesPage(
                                            windowClass = windowClass,
                                            settings = settings,
                                            tokens = tokens,
                                            accentColor = accentColor,
                                            cardPad = cardPad,
                                            listSpace = listSpace,
                                            todos = todos,
                                            notes = notes,
                                            newTodoTitle = newTodoTitle,
                                            onNewTodoTitleChange = { newTodoTitle = it },
                                            onOpenNewTodoDialog = {
                                                editTodoTitle = newTodoTitle.trim()
                                                editTodoNotes = ""
                                                editTodoDueAt = System.currentTimeMillis() + 60L * 60L * 1000L
                                                editTodoDueCustom = formatDueTime(editTodoDueAt)
                                                editTodoPriority = 1
                                                editTodoRepeat = ""
                                                editingTodo = null
                                                showNewTodoDialog = true
                                            },
                                            onOpenEditTodoDialog = { t ->
                                                editingTodo = t
                                                editTodoTitle = t.title
                                                editTodoNotes = t.notes
                                                editTodoDueAt = if (t.dueAt > 0L) t.dueAt else System.currentTimeMillis() + 3600_000L
                                                editTodoDueCustom = formatDueTime(editTodoDueAt)
                                                editTodoPriority = t.priority
                                                editTodoRepeat = t.repeatRule
                                                showNewTodoDialog = true
                                            },
                                            newNoteContent = newNoteContent,
                                            onNewNoteContentChange = { newNoteContent = it },
                                            newNotePinned = newNotePinned,
                                            onNewNotePinnedChange = { newNotePinned = it },
                                            newNoteColor = newNoteColor,
                                            onNewNoteColorChange = { newNoteColor = it },
                                            onOpenEditNoteDialog = { n ->
                                                editingNote = n
                                                editNoteContent = n.content
                                                editNotePinned = n.pinned
                                                editNoteColor = n.colorHex
                                            },
                                            todosListState = todosListState,
                                            notesListState = notesListState,
                                            sendOrNotify = { type, payload -> sendOrNotify(type, payload) }
                                        )
                                    }

                                    DesktopTab.CHAT -> {
                                        DesktopChatPage(
                                            windowClass = windowClass,
                                            settings = settings,
                                            tokens = tokens,
                                            accentColor = accentColor,
                                            isWindowVisible = isWindowVisible,
                                            chatSessions = chatSessions,
                                            chatSessionId = chatSessionId,
                                            onSelectSession = { chatSessionId = it },
                                            chatBubbles = chatBubbles,
                                            chatSending = chatSending,
                                            chatInput = chatInput,
                                            onChatInputChange = { chatInput = it },
                                            messages = messages,
                                            chatListState = chatListState,
                                            onSendChatMessage = { sendChatMessage(it) },
                                            onCopyToClipboard = { copyToClipboard(it) },
                                            proactiveEvents = proactiveEvents,
                                            onMuteProactiveTopics = { ev ->
                                                if (ev.topicIds.isNotEmpty()) {
                                                    scope.launch {
                                                        if (sendOrNotify(LinkMsgType.WATCH_TOPIC_MUTE, com.phantom.checker.shared.WatchTopicMuteReq(ev.topicIds))) {
                                                            proactiveEvents = proactiveEvents.filterNot { it.id == ev.id }
                                                        }
                                                    }
                                                } else {
                                                    proactiveEvents = proactiveEvents.filterNot { it.id == ev.id }
                                                }
                                            },
                                            sendOrNotify = { type, payload -> sendOrNotify(type, payload) }
                                        )
                                    }

                                    DesktopTab.RULES -> {
                                        DesktopRulesPage(
                                            windowClass = windowClass,
                                            settings = settings,
                                            tokens = tokens,
                                            accentColor = accentColor,
                                            whitelistRules = whitelistRules,
                                            tags = tags,
                                            mutedApps = mutedApps,
                                            newRuleSender = newRuleSender,
                                            onNewRuleSenderChange = { newRuleSender = it },
                                            newRulePkg = newRulePkg,
                                            onNewRulePkgChange = { newRulePkg = it },
                                            newTagName = newTagName,
                                            onNewTagNameChange = { newTagName = it },
                                            scrollState = rulesScrollState,
                                            sendOrNotify = { type, payload -> sendOrNotify(type, payload) }
                                        )
                                    }

                                    DesktopTab.REPORTS -> {
                                        LaunchedEffect(uiState.connState) {
                                            if (uiState.connState == LinkConnState.CONNECTED) {
                                                server.sendCommand(LinkMsgType.GROUP_REPORTS_LIST, reqId(), emptyMap<String, String>())
                                            }
                                        }
                                        DesktopGroupReportsPage(
                                            settings = settings,
                                            tokens = tokens,
                                            accentColor = accentColor,
                                            isConnected = uiState.connState == LinkConnState.CONNECTED,
                                            reports = groupReports,
                                            onRefresh = {
                                                scope.launch {
                                                    server.sendCommand(LinkMsgType.GROUP_REPORTS_LIST, reqId(), emptyMap<String, String>())
                                                }
                                            }
                                        )
                                    }

                                    DesktopTab.TOOLS -> {
                                        DesktopToolsPage(
                                            settings = settings,
                                            tokens = tokens,
                                            accentColor = accentColor,
                                            isConnected = uiState.connState == LinkConnState.CONNECTED,
                                            latestReceivedClipboard = latestReceivedClipboard,
                                            server = server,
                                            onFeedback = { actionFeedback = it }
                                        )
                                    }

                                    DesktopTab.HOME -> {
                                        DesktopHomePage(
                                            settings = settings,
                                            tokens = tokens,
                                            accentColor = accentColor,
                                            windowClass = windowClass,
                                            uiState = uiState,
                                            stats = stats,
                                            recentMessages = messages,
                                            server = server,
                                            onNavigateTab = { tabKey ->
                                                runCatching { DesktopTab.valueOf(tabKey) }.getOrNull()?.let { activeTab = it }
                                            },
                                            onFeedback = { actionFeedback = it }
                                        )
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
                }

                // Deep Analysis Result Dialog (rendered with Markdown, PhantomDialog shell maxWidth=720dp)
                deepAnalysisResult?.let { (_, markdown) ->
                    PhantomDialog(
                        onDismissRequest = { deepAnalysisResult = null },
                        title = DesktopStrings.deepAnalysisTitle,
                        maxWidth = 720.dp,
                        confirmButton = {
                            TextButton(onClick = { deepAnalysisResult = null }) { Text(DesktopStrings.ok) }
                        }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                            DesktopMarkdownText(markdown = markdown, accentColor = accentColor)
                        }
                    }
                }

                // #499 / #520 Full Todo Add / Edit Dialog (PhantomDialog shell maxWidth=560dp)
                if (showNewTodoDialog) {
                    val target = editingTodo
                    PhantomDialog(
                        onDismissRequest = { showNewTodoDialog = false; editingTodo = null },
                        title = if (target == null) DesktopStrings.todoAddFull else DesktopStrings.todoEdit,
                        maxWidth = 560.dp,
                        dismissButton = {
                            TextButton(onClick = { showNewTodoDialog = false; editingTodo = null }) { Text(DesktopStrings.dismiss) }
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
                        }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
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
                    }
                }

                // #499 / #520 Edit Note Dialog (PhantomDialog shell maxWidth=560dp)
                editingNote?.let { targetNote ->
                    val noteColorChoices = listOf("", "#A78BFA", "#6EA8FE", "#34D399", "#FBBF24", "#FB7185")
                    PhantomDialog(
                        onDismissRequest = { editingNote = null },
                        title = DesktopStrings.noteEditTitle,
                        maxWidth = 560.dp,
                        dismissButton = {
                            TextButton(onClick = { editingNote = null }) { Text(DesktopStrings.dismiss) }
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
                        }
                    ) {
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
                    }
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
internal fun StatBox(
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
 * #489 / #520 隔離式 draw-phase 呼吸點：三閘門（pulsing ∧ animationsEnabled ∧ 未逾 120 秒），
 * 省電模式或逾時零 rememberInfiniteTransition，且僅於 graphicsLayer lambda 讀取動畫值（零重組）。
 */
@Composable
private fun PulsingStatusDot(
    color: Color,
    pulsing: Boolean,
    animationsEnabled: Boolean
) {
    var timedOut by remember(pulsing) { mutableStateOf(false) }
    LaunchedEffect(pulsing, animationsEnabled) {
        timedOut = false
        if (pulsing && animationsEnabled) {
            delay(120_000L)
            timedOut = true
        }
    }
    if (!pulsing || !animationsEnabled || timedOut) {
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
internal fun PulsingThinkingLabel(
    text: String,
    color: Color,
    animationsEnabled: Boolean
) {
    var timedOut by remember(text) { mutableStateOf(false) }
    LaunchedEffect(text, animationsEnabled) {
        timedOut = false
        if (animationsEnabled) {
            delay(130_000L)
            timedOut = true
        }
    }
    if (!animationsEnabled || timedOut) {
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

internal fun formatDueTime(ts: Long): String {
    if (ts <= 0L) return ""
    return runCatching {
        TODO_DUE_FMT.get().format(java.util.Date(ts))
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
