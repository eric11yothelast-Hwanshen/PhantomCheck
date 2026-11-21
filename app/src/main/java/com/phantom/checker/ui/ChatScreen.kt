package com.phantom.checker.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import android.net.Uri
import android.provider.Settings
import com.google.gson.JsonParser
import com.phantom.checker.data.AiRuleEntity
import com.phantom.checker.data.ChatMessageEntity
import com.phantom.checker.data.ChatSessionEntity
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.data.MessageTagCrossRef
import com.phantom.checker.data.TagEntity
import com.phantom.checker.data.TodoEntity
import com.phantom.checker.data.UserTagRuleEntity
import com.phantom.checker.network.AiClient
import com.phantom.checker.network.AiProviderProfile
import com.phantom.checker.network.ChatMessage
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.service.NotificationCollectorService
import com.phantom.checker.service.ReminderScheduler
import com.phantom.checker.service.StatsForegroundService
import androidx.compose.ui.graphics.ImageBitmap
import com.phantom.checker.util.AssistantAvatarCircle
import com.phantom.checker.util.AssistantAvatarHelper
import com.phantom.checker.util.ClientTimeParser
import com.phantom.checker.util.NaturalTimeParseResult
import com.phantom.checker.util.NotificationFoldUtil
import com.phantom.checker.util.OtpDetector
import com.phantom.checker.util.PhantomPrefs
import com.phantom.checker.util.PhantomSettingsBus
import com.phantom.checker.widget.ReminderWidgetProvider
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

import com.phantom.checker.ui.chat.*
/** 自製 Sparkle 圖示（無需額外 material-icons-extended 依賴） */
val AutoAwesomeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "AutoAwesome",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(19f, 9f)
            lineToRelative(1.25f, -2.75f)
            lineTo(23f, 5f)
            lineToRelative(-2.75f, -1.25f)
            lineTo(19f, 1f)
            lineToRelative(-1.25f, 2.75f)
            lineTo(15f, 5f)
            lineToRelative(2.75f, 1.25f)
            lineTo(19f, 9f)
            close()
            moveTo(11.5f, 9.5f)
            lineTo(9f, 4f)
            lineTo(6.5f, 9.5f)
            lineTo(1f, 12f)
            lineToRelative(5.5f, 2.5f)
            lineTo(9f, 20f)
            lineToRelative(2.5f, -5.5f)
            lineTo(17f, 12f)
            lineToRelative(-5.5f, -2.5f)
            close()
            moveTo(19f, 15f)
            lineToRelative(-1.25f, 2.75f)
            lineTo(15f, 19f)
            lineToRelative(2.75f, 1.25f)
            lineTo(19f, 23f)
            lineToRelative(1.25f, -2.75f)
            lineTo(23f, 19f)
            lineToRelative(-2.75f, -1.25f)
            lineTo(19f, 15f)
            close()
        }
    }.build()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ChatAssistantTab(
    glassState: HazeState,
    onShowMessage: (String) -> Unit,
    onShowActionMessage: ((String, String, () -> Unit) -> Unit)? = null,
    onOpenMemoryWithFilter: ((String?) -> Unit)? = null,
    isSearchActive: Boolean = false,
    onToggleSearch: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var sessions by remember { mutableStateOf(listOf<ChatSessionEntity>()) }
    var currentSessionId by remember { mutableStateOf<Long?>(null) }
    var messages by remember { mutableStateOf(listOf<ChatMessageEntity>()) }
    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var lastFailedQuery by remember { mutableStateOf<String?>(null) }
    var showSessionDrawer by remember { mutableStateOf(false) }
    var deleteSessionConfirm by remember { mutableStateOf<ChatSessionEntity?>(null) }
    var showClearAllSessionsConfirm by remember { mutableStateOf(false) }
    var suggestionChips by remember { mutableStateOf(listOf<String>()) }
    var profiles by remember { mutableStateOf(AiClient.getProfiles(appCtx)) }
    var activeProfile by remember { mutableStateOf(AiClient.getProfileForPurpose(appCtx, com.phantom.checker.util.AiPurpose.CHAT)) }
    var hasApiKey by remember { mutableStateOf(activeProfile != null) }
    var showSuggestions by remember { mutableStateOf(PhantomPrefs.isChatSuggestionsEnabled(appCtx)) }
    var assistantName by remember { mutableStateOf(PhantomPrefs.getAssistantName(appCtx)) }
    var assistantAvatar by remember { mutableStateOf<ImageBitmap?>(null) }
    var tokenSaverEnabled by remember { mutableStateOf(PhantomPrefs.isTokenSaverEnabled(appCtx)) }
    var showTokenSaverInfoDialog by remember { mutableStateOf(false) }
    val settingsVersion by PhantomSettingsBus.version.collectAsState()
    LaunchedEffect(settingsVersion) {
        showSuggestions = PhantomPrefs.isChatSuggestionsEnabled(appCtx)
        assistantName = PhantomPrefs.getAssistantName(appCtx)
        assistantAvatar = AssistantAvatarHelper.loadAvatarBitmap(appCtx)
        tokenSaverEnabled = PhantomPrefs.isTokenSaverEnabled(appCtx)
    }
    var quickCommands by remember { mutableStateOf(PhantomPrefs.getQuickChatCommands(appCtx)) }
    var showInputToolsMenu by remember { mutableStateOf(false) }
    var showQuickCommandsDialog by remember { mutableStateOf(false) }
    var pendingImageAttachment by remember { mutableStateOf<PendingImageAttachment?>(null) }
    var pendingQuickCommandQueue by remember { mutableStateOf<List<PendingQuickCommandAddition>>(emptyList()) }
    var pendingRuleQueue by remember { mutableStateOf<List<PendingRuleInsertion>>(emptyList()) }
    var pendingTagRuleQueue by remember { mutableStateOf<List<PendingTagRuleInsertion>>(emptyList()) }
    var pendingWhitelist by remember { mutableStateOf<PendingWhitelistAction?>(null) }
    var appliedSettingCards by remember { mutableStateOf<List<AppliedSettingCardState>>(emptyList()) }
    var pendingSettingCandidates by remember { mutableStateOf<PendingSettingCandidatesState?>(null) }
    var pendingSettingAdjustQueue by remember { mutableStateOf<List<PendingSettingAdjustState>>(emptyList()) }
    var pendingMsgActionQueue by remember { mutableStateOf<List<PendingMessageActionState>>(emptyList()) }
    var pendingTodoQueue by remember { mutableStateOf<List<PendingTodoInsertion>>(emptyList()) }
    var editingTodoTimeTarget by remember { mutableStateOf<PendingTodoInsertion?>(null) }
    var pendingCalendarQueue by remember { mutableStateOf<List<PendingCalendarAddition>>(emptyList()) }
    var searchResultCard by remember { mutableStateOf<SearchResultCardState?>(null) }
    var pendingExtendedActionQueue by remember { mutableStateOf<List<PendingExtendedAction>>(emptyList()) }
    var pendingMemoryWriteQueue by remember { mutableStateOf<List<PendingMemoryWriteState>>(emptyList()) }
    var pendingMemoryDeleteQueue by remember { mutableStateOf<List<PendingMemoryDeleteState>>(emptyList()) }
    var pendingAliasCandidate by remember { mutableStateOf<com.phantom.checker.util.ContactBridge.AliasCandidate?>(null) }
    var pendingCrossAppQueue by remember { mutableStateOf<List<com.phantom.checker.util.CrossAppActionProposal>>(emptyList()) }
    var showTodoListSheet by remember { mutableStateOf(false) }
    val rawTodoListItems by remember(appCtx) {
        AppDatabaseHolder.db(appCtx).todoDao().observeAll()
    }.collectAsState(initial = emptyList())
    val todoListItems = remember(rawTodoListItems) {
        ReminderScheduler.sortTodosSmart(rawTodoListItems)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                when (val result = withContext(Dispatchers.IO) { encodeImageUriToDataUri(appCtx, uri) }) {
                    is ImageEncodeResult.Success -> pendingImageAttachment = result.attachment
                    is ImageEncodeResult.Failure -> onShowMessage(result.reason)
                }
            }
        }
    }

    suspend fun loadSessionsAndSuggestions() {
        withContext(Dispatchers.IO) {
            val db = AppDatabaseHolder.db(appCtx)
            val allSessions = db.chatDao().allSessions()
            val startOfDay = NotificationCollectorService.startOfTodayMillis()
            val stats = db.messageDao().statsOverview(startOfDay)
            val topApps = db.messageDao().todayRecentApps(startOfDay)
            val chips = buildList {
                add("昨日簡報")
                if (stats.today > 0) {
                    add("今天有哪些必看訊息？")
                } else {
                    add("近 7 天有哪些重要訊息？")
                }
                if (stats.scam > 0) {
                    add("最可疑的訊息是哪則？")
                } else if (topApps.isNotEmpty()) {
                    add("本週哪個 App 最吵？")
                } else {
                    add("幫我檢查最近的訊息風險")
                }
            }
            sessions = allSessions
            suggestionChips = chips
            val loadedProfiles = AiClient.getProfiles(appCtx)
            val loadedActive = AiClient.getProfileForPurpose(appCtx, com.phantom.checker.util.AiPurpose.CHAT)
            profiles = loadedProfiles
            activeProfile = loadedActive
            hasApiKey = loadedActive != null
        }
    }

    suspend fun loadMessages(sessionId: Long?) {
        val loaded = if (sessionId == null) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                AppDatabaseHolder.db(appCtx).chatDao().messagesForSession(sessionId)
            }
        }
        if (currentSessionId == sessionId) {
            messages = loaded
        }
    }

    val lifecycleOwner = ctx as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                showSuggestions = PhantomPrefs.isChatSuggestionsEnabled(appCtx)
                assistantName = PhantomPrefs.getAssistantName(appCtx)
                tokenSaverEnabled = PhantomPrefs.isTokenSaverEnabled(appCtx)
                scope.launch {
                    assistantAvatar = AssistantAvatarHelper.loadAvatarBitmap(appCtx)
                }
                quickCommands = PhantomPrefs.getQuickChatCommands(appCtx)
                profiles = AiClient.getProfiles(appCtx)
                activeProfile = AiClient.getProfileForPurpose(appCtx, com.phantom.checker.util.AiPurpose.CHAT)
                hasApiKey = activeProfile != null
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        loadSessionsAndSuggestions()
        if (PhantomPrefs.isAiChatTabContinue(appCtx) && currentSessionId == null && messages.isEmpty()) {
            val latest = sessions.firstOrNull()
            if (latest != null) {
                currentSessionId = latest.id
                loadMessages(latest.id)
            }
        }
    }

    LaunchedEffect(messages.size, isSending) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val isImeVisible = WindowInsets.isImeVisible
    val imeBottomPx = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(isImeVisible, imeBottomPx) {
        if (isImeVisible && imeBottomPx > 0 && messages.isNotEmpty()) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalItems = listState.layoutInfo.totalItemsCount
            val isNearBottom = !listState.canScrollForward || (totalItems > 0 && lastVisible >= totalItems - 2)
            if (isNearBottom) {
                kotlinx.coroutines.delay(80)
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    fun sendQuery(rawQuery: String, isRetry: Boolean = false) {
        val rawTrimmed = rawQuery.trim()
        val attachedImage = if (!isRetry) pendingImageAttachment else null
        if ((rawTrimmed.isEmpty() && attachedImage == null) || isSending) return
        val query = rawTrimmed.ifBlank { "請檢視這張圖片的內容，並評估是否有任何詐騙或安全風險疑點。" }
        val displayUserText = if (attachedImage != null) {
            if (rawTrimmed.isNotEmpty()) "$rawTrimmed\n[已附加圖片]" else "[已附加圖片]"
        } else {
            query
        }
        lastError = null
        lastFailedQuery = null
        if (!isRetry) {
            inputText = ""
            pendingImageAttachment = null
        }

        scope.launch {
            isSending = true
            val db = AppDatabaseHolder.db(appCtx)
            val sessionId = withContext(Dispatchers.IO) {
                val existingId = currentSessionId
                if (existingId != null) {
                    existingId
                } else {
                    val title = displayUserText.take(20).ifBlank { "新對話" }
                    val newId = db.chatDao().insertSession(
                        ChatSessionEntity(title = title, createdAt = System.currentTimeMillis())
                    )
                    currentSessionId = newId
                    newId
                }
            }

            if (!isRetry) {
                val userEntity = ChatMessageEntity(
                    sessionId = sessionId,
                    role = ChatMessageEntity.ROLE_USER,
                    content = displayUserText,
                    createdAt = System.currentTimeMillis()
                )
                withContext(Dispatchers.IO) {
                    db.chatDao().insertMessage(userEntity)
                    // 若為首則訊息，更新標題
                    val session = db.chatDao().sessionById(sessionId)
                    if (session != null && session.title == "新對話") {
                        db.chatDao().updateSessionTitle(sessionId, displayUserText.take(20))
                    }
                }
                loadMessages(sessionId)
                loadSessionsAndSuggestions()
            }

            val history = messages
                .dropLast(if (!isRetry && messages.lastOrNull()?.role == ChatMessageEntity.ROLE_USER) 1 else 0)
                .map { ChatMessage(it.role, it.content) }

            val initialRagLimit = PhantomPrefs.getEffectiveChatRagMessageLimit(appCtx)
            val ragBuild = withContext(Dispatchers.IO) {
                buildRagSystemPrompt(
                    ctx = appCtx,
                    query = query,
                    overrideLimit = initialRagLimit,
                    sessionId = sessionId,
                    memoryDegradeFactor = 1.0f,
                    recordMemoryUsage = true
                )
            }

            var retryDegradeStep = 0
            val result = AiClient.chatAssistant(
                systemPrompt = ragBuild.prompt,
                history = history,
                userMessage = query,
                context = appCtx,
                ragLimit = initialRagLimit,
                imageDataUri = attachedImage?.dataUri,
                promptCacheKey = "phantom_chat_session_$sessionId",
                onRebuildSystemPromptForRetry = { reducedLimit ->
                    retryDegradeStep++
                    val memFactor = when {
                        retryDegradeStep == 1 -> 0.5f
                        retryDegradeStep == 2 -> 0.25f
                        else -> 0.0f
                    }
                    withContext(Dispatchers.IO) {
                        buildRagSystemPrompt(
                            ctx = appCtx,
                            query = query,
                            overrideLimit = reducedLimit,
                            sessionId = sessionId,
                            memoryDegradeFactor = memFactor,
                            recordMemoryUsage = false
                        ).prompt
                    }
                }
            )

            isSending = false
            result.onSuccess { outcome ->
                val reply = outcome.replyText
                val thinkingText = outcome.thinkingText
                val summarySourceText = ragBuild.ragMessagesTextForSummary
                if (!summarySourceText.isNullOrBlank() && ragBuild.maxMsgTimestamp > 0L) {
                    scope.launch(Dispatchers.IO) {
                        val summary = AiClient.summarizeRagContext(summarySourceText, appCtx)
                        if (!summary.isNullOrBlank()) {
                            PhantomPrefs.saveChatRagSessionCache(
                                appCtx,
                                PhantomPrefs.ChatRagSessionCache(
                                    sessionId = sessionId,
                                    summary = summary,
                                    lastMsgTimestamp = ragBuild.maxMsgTimestamp,
                                    ragLimit = ragBuild.ragLimit,
                                    dateKey = com.phantom.checker.service.DailySummaryPolicy.todayDateKey()
                                )
                            )
                        }
                    }
                } else if (ragBuild.usedIncrementalCache && ragBuild.maxMsgTimestamp > 0L) {
                    val existingCache = PhantomPrefs.getChatRagSessionCache(appCtx, sessionId)
                    if (existingCache != null && ragBuild.maxMsgTimestamp > existingCache.lastMsgTimestamp) {
                        PhantomPrefs.saveChatRagSessionCache(
                            appCtx,
                            existingCache.copy(lastMsgTimestamp = ragBuild.maxMsgTimestamp)
                        )
                    }
                }
                val parsed = parseAllChatActions(reply)
                val resolutionErrors = mutableListOf<String>()

                // 解析多筆訊息操作目標
                val resolvedMsgActions = mutableListOf<PendingMessageActionState>()
                if (parsed.messageActions.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        val allTags = db.tagDao().all()
                        parsed.messageActions.forEach { mp ->
                            val msgsFound = mp.messageIds.mapNotNull { db.messageDao().byId(it) }.toMutableList()
                            if (msgsFound.isEmpty() && !mp.messageRef.isNullOrBlank()) {
                                val ref = mp.messageRef.trim()
                                msgsFound.addAll(db.messageDao().search(ref).take(5))
                            }
                            val distinctMsgs = msgsFound.distinctBy { it.id }
                            if (distinctMsgs.isEmpty()) {
                                val targetHint = mp.messageRef ?: mp.messageIds.joinToString(", ").ifBlank { "未指定" }
                                resolutionErrors.add("找不到「$targetHint」對應的訊息紀錄，無法執行 ${mp.action}。")
                            } else if (mp.action == "tag_message") {
                                val tName = mp.tagName?.trim().orEmpty()
                                val matchedTag = allTags.firstOrNull { it.name.equals(tName, ignoreCase = true) }
                                if (matchedTag == null) {
                                    val avail = allTags.joinToString("、") { it.name }.ifBlank { "無" }
                                    resolutionErrors.add("找不到標籤「$tName」（目前可用標籤：$avail）。")
                                } else {
                                    resolvedMsgActions.add(
                                        PendingMessageActionState(
                                            sessionId = sessionId,
                                            action = mp.action,
                                            messages = distinctMsgs,
                                            selectedMessageId = distinctMsgs.first().id,
                                            replyText = mp.replyText.orEmpty(),
                                            tagId = matchedTag.id,
                                            tagName = matchedTag.name,
                                            tagOperation = mp.operation ?: "add"
                                        )
                                    )
                                }
                            } else {
                                resolvedMsgActions.add(
                                    PendingMessageActionState(
                                        sessionId = sessionId,
                                        action = mp.action,
                                        messages = distinctMsgs,
                                        selectedMessageId = distinctMsgs.first().id,
                                        replyText = mp.replyText.orEmpty(),
                                        tagId = null,
                                        tagName = null,
                                        tagOperation = mp.operation ?: "add"
                                    )
                                )
                            }
                        }
                    }
                }

                // 解析多筆白名單提案
                val resolvedWhitelistItems = mutableListOf<WhitelistTargetItem>()
                val failedWhitelistRefs = mutableListOf<String>()
                if (parsed.whitelistProposals.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        parsed.whitelistProposals.forEach { wp ->
                            val ref = wp.messageRef.trim()
                            val extractedId = ref.toLongOrNull()
                                ?: CHAT_ID_REF_REGEX
                                    .matchEntire(ref)?.groupValues?.getOrNull(1)?.toLongOrNull()
                            val byId = extractedId?.let { db.messageDao().byId(it) }
                            val resolved = if (byId != null) {
                                byId
                            } else if (ref.isNotEmpty()) {
                                val candidates = db.messageDao().search(ref)
                                val direct = candidates.firstOrNull { it.sender.equals(ref, ignoreCase = true) }
                                    ?: candidates.firstOrNull { it.sender.contains(ref, ignoreCase = true) }
                                    ?: candidates.firstOrNull()
                                if (direct != null) {
                                    direct
                                } else {
                                    val prefix20 = ref.take(20).trim()
                                    val prefixCandidates = if (prefix20.length >= 2 && prefix20 != ref) {
                                        db.messageDao().search(prefix20)
                                    } else {
                                        emptyList()
                                    }
                                    val prefixMatch = prefixCandidates.firstOrNull {
                                        it.sender.contains(prefix20, ignoreCase = true) ||
                                            it.content.contains(prefix20, ignoreCase = true) ||
                                            it.appLabel.contains(prefix20, ignoreCase = true)
                                    } ?: prefixCandidates.firstOrNull()
                                    if (prefixMatch != null) {
                                        prefixMatch
                                    } else {
                                        val tokens = ref.split(CHAT_REF_SPLIT_REGEX)
                                            .map { it.trim() }
                                            .filter { it.length >= 2 }
                                            .sortedByDescending { it.length }
                                        var tokenHit: MessageEntity? = null
                                        for (tok in tokens.take(3)) {
                                            val tokCandidates = db.messageDao().search(tok)
                                            if (tokCandidates.isNotEmpty()) {
                                                tokenHit = tokCandidates.firstOrNull {
                                                    it.sender.equals(tok, ignoreCase = true) ||
                                                        it.sender.contains(tok, ignoreCase = true)
                                                } ?: tokCandidates.firstOrNull()
                                                break
                                            }
                                        }
                                        tokenHit
                                    }
                                }
                            } else {
                                null
                            }
                            if (resolved == null) {
                                val label = ref.ifBlank { "未指定" }
                                failedWhitelistRefs.add(label)
                                resolutionErrors.add("找不到：$label")
                            } else {
                                resolvedWhitelistItems.add(WhitelistTargetItem(kind = wp.kind, message = resolved))
                            }
                        }
                    }
                    if (failedWhitelistRefs.size > 1 && resolvedWhitelistItems.isEmpty()) {
                        resolutionErrors.add("白名單動作全部失敗（共 ${failedWhitelistRefs.size} 筆找不到對應訊息），請確認寄件者或訊息 ID。")
                    }
                }
                val dedupedWhitelistItems = resolvedWhitelistItems.distinctBy { item ->
                    if (item.kind == "sender") {
                        "sender|${item.message.sender.trim().lowercase()}|${item.message.packageName}"
                    } else {
                        "msg|${item.message.id}"
                    }
                }

                // 驗證並執行 setting_toggle（僅 boolean 白名單可直改）
                val newAppliedSettings = mutableListOf<AppliedSettingCardState>()
                val fallbackCandidates = mutableListOf<SettingSearchItem>()
                var pendingOpenSettingItem: SettingSearchItem? = null
                parsed.toggleProposals.forEach { tp ->
                    val item = SettingsCatalog.findById(tp.settingId)
                    if (item != null && SettingsCatalog.isToggleable(item.id)) {
                        val applied = SettingsCatalog.applyBooleanSetting(appCtx, item.id, tp.targetEnabled)
                        if (applied != null) {
                            newAppliedSettings.add(
                                AppliedSettingCardState(
                                    sessionId = sessionId,
                                    item = item,
                                    beforeEnabled = applied.first,
                                    afterEnabled = applied.second
                                )
                            )
                        }
                    } else if (item != null) {
                        newAppliedSettings.add(
                            AppliedSettingCardState(
                                sessionId = sessionId,
                                item = item,
                                beforeEnabled = null,
                                afterEnabled = null
                            )
                        )
                    } else {
                        val matchedSettingCandidates = SettingsCatalog.keywordSearch(tp.settingId, limit = 5)
                        if (matchedSettingCandidates.isNotEmpty()) {
                            fallbackCandidates.addAll(matchedSettingCandidates)
                        } else {
                            resolutionErrors.add("找不到名為「${tp.settingId}」的設定項目，請換個說法或至設定頁搜尋。")
                        }
                    }
                }

                parsed.openSettingProposals.forEach { op ->
                    val item = SettingsCatalog.findById(op.settingId)
                    if (item != null) {
                        newAppliedSettings.add(
                            AppliedSettingCardState(
                                sessionId = sessionId,
                                item = item,
                                beforeEnabled = null,
                                afterEnabled = null
                            )
                        )
                        if (pendingOpenSettingItem == null) {
                            pendingOpenSettingItem = item
                        }
                    } else {
                        val matchedSettingCandidates = SettingsCatalog.keywordSearch(op.settingId, limit = 5)
                        if (matchedSettingCandidates.isNotEmpty()) {
                            fallbackCandidates.addAll(matchedSettingCandidates)
                        } else {
                            resolutionErrors.add("找不到名為「${op.settingId}」的設定項目，請換個說法或至設定頁搜尋。")
                        }
                    }
                }

                val explicitCandidates = parsed.candidatesProposals
                    .flatMap { it.candidateIds }
                    .mapNotNull { SettingsCatalog.findById(it) }
                val resolvedCandidates = (explicitCandidates + fallbackCandidates)
                    .distinctBy { it.id }
                    .take(6)
                    .takeIf { it.isNotEmpty() }

                // 解析數值調整提案（setting_adjust），若模型未產出則以用戶端確定性關鍵詞+數字 fallback
                val resolvedAdjustStates = mutableListOf<PendingSettingAdjustState>()
                parsed.adjustProposals.forEach { ap ->
                    val spec = SettingsCatalog.findNumericSpec(ap.settingId)
                    val item = SettingsCatalog.findById(ap.settingId)
                    if (spec != null && item != null) {
                        val currentVal = SettingsCatalog.getNumericValue(appCtx, spec.id) ?: spec.min
                        val (snappedVal, wasClamped) = spec.clampAndSnap(ap.value)
                        resolvedAdjustStates.add(
                            PendingSettingAdjustState(
                                sessionId = sessionId,
                                item = item,
                                spec = spec,
                                currentValue = currentVal,
                                proposedValue = snappedVal,
                                wasClamped = wasClamped
                            )
                        )
                    } else if (item != null) {
                        newAppliedSettings.add(
                            AppliedSettingCardState(
                                sessionId = sessionId,
                                item = item,
                                beforeEnabled = null,
                                afterEnabled = null
                            )
                        )
                    } else {
                        resolutionErrors.add("找不到名為「${ap.settingId}」的可調整數值設定。")
                    }
                }
                var usedNumericFallback = false
                if (resolvedAdjustStates.isEmpty()) {
                    SettingsCatalog.resolveNumericIntentFallback(query)?.let { (spec, rawVal) ->
                        val item = SettingsCatalog.findById(spec.id)
                        if (item != null) {
                            val currentVal = SettingsCatalog.getNumericValue(appCtx, spec.id) ?: spec.min
                            val (snappedVal, wasClamped) = spec.clampAndSnap(rawVal)
                            resolvedAdjustStates.add(
                                PendingSettingAdjustState(
                                    sessionId = sessionId,
                                    item = item,
                                    spec = spec,
                                    currentValue = currentVal,
                                    proposedValue = snappedVal,
                                    wasClamped = wasClamped
                                )
                            )
                            usedNumericFallback = true
                        }
                    }
                }
                val finalCandidates = if (usedNumericFallback) null else resolvedCandidates

                val resolvedTagRuleInsertions = if (parsed.tagRuleProposals.isNotEmpty()) {
                    val tags = withContext(Dispatchers.IO) {
                        AppDatabaseHolder.db(appCtx).tagDao().all()
                    }
                    parsed.tagRuleProposals.mapNotNull { tp ->
                        val targetName = tp.tagName.trim()
                        val matchedTag = tags.firstOrNull { it.name.equals(targetName, ignoreCase = true) }
                            ?: tags.firstOrNull { it.name.contains(targetName, ignoreCase = true) }
                        if (matchedTag == null) {
                            android.util.Log.w("ChatScreen", "tag_rule skipped: target tag '$targetName' not found")
                            resolutionErrors.add("找不到目標標籤「$targetName」，已略過建立標籤規則。")
                            null
                        } else {
                            PendingTagRuleInsertion(
                                sessionId = sessionId,
                                proposal = tp,
                                resolvedTagId = matchedTag.id,
                                availableTags = tags
                            )
                        }
                    }
                } else {
                    emptyList()
                }

                val resolvedTodoInsertions = mutableListOf<PendingTodoInsertion>()
                val nowMs = System.currentTimeMillis()
                if (parsed.reminderProposals.isNotEmpty()) {
                    parsed.reminderProposals.forEach { rp ->
                        val title = rp.title.trim().ifBlank { extractTodoTitleFromQuery(query) }
                        val resolvedRepeat = rp.repeatRule.ifBlank {
                            detectRepeatRuleFromText(rp.time.orEmpty()).ifBlank { detectRepeatRuleFromText(query) }
                        }
                        if (resolvedRepeat.isBlank() && (isUnsupportedRecurringExpression(rp.time.orEmpty()) || isUnsupportedRecurringExpression(query))) {
                            resolutionErrors.add("重複提醒目前僅支援「每日」與「每週」，暫不支援每月或每年。")
                        } else {
                            when (val timeRes = ClientTimeParser.parseNaturalReminderTime(rp.time, query, nowMs)) {
                                is NaturalTimeParseResult.Success -> {
                                    resolvedTodoInsertions.add(
                                        PendingTodoInsertion(
                                            sessionId = sessionId,
                                            title = title,
                                            dueAt = timeRes.epochMillis,
                                            priority = rp.priority,
                                            notes = rp.notes,
                                            repeatRule = resolvedRepeat
                                        )
                                    )
                                }
                                is NaturalTimeParseResult.PastTime -> {
                                    val pastFmt = PhantomPrefs.formatMessageTime(appCtx, timeRes.epochMillis, includeDate = true)
                                    resolutionErrors.add("待辦提醒時間「$pastFmt」已過去，請重新指定未來的時間。")
                                }
                                is NaturalTimeParseResult.Unparseable -> {
                                    resolutionErrors.add("無法辨識「$title」的提醒時間，請明確指定時間（例如「明天下午 3 點」或「2 小時後」）。")
                                }
                            }
                        }
                    }
                } else if (isNaturalReminderIntent(query) && resolvedAdjustStates.isEmpty() && newAppliedSettings.isEmpty()) {
                    val resolvedRepeat = detectRepeatRuleFromText(query)
                    if (resolvedRepeat.isBlank() && isUnsupportedRecurringExpression(query)) {
                        resolutionErrors.add("重複提醒目前僅支援「每日」與「每週」，暫不支援每月或每年。")
                    } else {
                        val title = extractTodoTitleFromQuery(query)
                        when (val timeRes = ClientTimeParser.parseNaturalReminderTime(null, query, nowMs)) {
                            is NaturalTimeParseResult.Success -> {
                                resolvedTodoInsertions.add(
                                    PendingTodoInsertion(
                                        sessionId = sessionId,
                                        title = title,
                                        dueAt = timeRes.epochMillis,
                                        repeatRule = resolvedRepeat
                                    )
                                )
                            }
                            is NaturalTimeParseResult.PastTime -> {
                                val pastFmt = PhantomPrefs.formatMessageTime(appCtx, timeRes.epochMillis, includeDate = true)
                                resolutionErrors.add("待辦提醒時間「$pastFmt」已過去，請重新指定未來的時間。")
                            }
                            is NaturalTimeParseResult.Unparseable -> {
                                resolutionErrors.add("請明確指定提醒時間（例如「明天下午 3 點」或「2 小時後」）。")
                            }
                        }
                    }
                }

                val allActionErrors = (parsed.invalidActionErrors + resolutionErrors).distinct()
                val hasValidAction = parsed.ruleProposals.isNotEmpty() ||
                    resolvedTagRuleInsertions.isNotEmpty() ||
                    parsed.quickCommandProposals.isNotEmpty() ||
                    dedupedWhitelistItems.isNotEmpty() ||
                    newAppliedSettings.isNotEmpty() ||
                    !finalCandidates.isNullOrEmpty() ||
                    resolvedAdjustStates.isNotEmpty() ||
                    resolvedMsgActions.isNotEmpty() ||
                    resolvedTodoInsertions.isNotEmpty() ||
                    parsed.memoryViewProposals.isNotEmpty() ||
                    parsed.memoryAddProposals.isNotEmpty() ||
                    parsed.memoryUpdateProposals.isNotEmpty() ||
                    parsed.memoryDeleteProposals.isNotEmpty() ||
                    parsed.calendarFreeQueryProposals.isNotEmpty() ||
                    parsed.calendarAddProposals.isNotEmpty()

                val storedReply = parsed.cleanReply.ifBlank {
                    when {
                        hasValidAction -> "已準備好以下操作，請確認後執行："
                        allActionErrors.isNotEmpty() -> "部分動作指令無法執行，請參考下方說明："
                        else -> "AI 未回傳可顯示的內容，請換個方式提問。"
                    }
                }

                withContext(Dispatchers.IO) {
                    db.chatDao().insertMessage(
                        ChatMessageEntity(
                            sessionId = sessionId,
                            role = ChatMessageEntity.ROLE_ASSISTANT,
                            content = storedReply,
                            createdAt = System.currentTimeMillis(),
                            thinkingContent = thinkingText
                        )
                    )
                    allActionErrors.forEach { errText ->
                        db.chatDao().insertMessage(
                            ChatMessageEntity(
                                sessionId = sessionId,
                                role = ChatMessageEntity.ROLE_ASSISTANT,
                                content = errText,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
                loadMessages(sessionId)
                if (parsed.ruleProposals.isNotEmpty()) {
                    pendingRuleQueue = parsed.ruleProposals.map { PendingRuleInsertion(sessionId, it) }
                }
                if (resolvedTagRuleInsertions.isNotEmpty()) {
                    pendingTagRuleQueue = resolvedTagRuleInsertions
                }
                if (parsed.quickCommandProposals.isNotEmpty()) {
                    pendingQuickCommandQueue = parsed.quickCommandProposals.map {
                        PendingQuickCommandAddition(
                            sessionId = sessionId,
                            proposal = it,
                            defaultMode = it.mode ?: PhantomPrefs.getQuickCommandDefaultMode(appCtx)
                        )
                    }
                }
                if (dedupedWhitelistItems.isNotEmpty()) {
                    pendingWhitelist = PendingWhitelistAction(
                        sessionId = sessionId,
                        items = dedupedWhitelistItems
                    )
                }
                if (resolvedAdjustStates.isNotEmpty()) {
                    pendingSettingAdjustQueue = resolvedAdjustStates
                }
                if (resolvedMsgActions.isNotEmpty()) {
                    pendingMsgActionQueue = resolvedMsgActions
                }
                if (resolvedTodoInsertions.isNotEmpty()) {
                    pendingTodoQueue = resolvedTodoInsertions
                }

                // #160 擴充動作解析與執行（唯讀直接執行，mutating 一律進確認卡佇列）
                var resolvedSearchCard: SearchResultCardState? = null
                val immediateExtraReplies = mutableListOf<String>()
                val resolvedExtendedActions = mutableListOf<PendingExtendedAction>()
                val resolvedMemoryWrites = mutableListOf<PendingMemoryWriteState>()
                val resolvedMemoryDeletes = mutableListOf<PendingMemoryDeleteState>()
                val resolvedCalendarAdds = mutableListOf<PendingCalendarAddition>()

                val hasExtendedProposals = parsed.searchProposals.isNotEmpty() ||
                    parsed.todoManageProposals.isNotEmpty() ||
                    parsed.vipPinProposals.isNotEmpty() ||
                    parsed.muteAppProposals.isNotEmpty() ||
                    parsed.markReadProposals.isNotEmpty() ||
                    parsed.exportProposals.isNotEmpty() ||
                    parsed.whitelistDeleteProposals.isNotEmpty() ||
                    parsed.memoryViewProposals.isNotEmpty() ||
                    parsed.memoryAddProposals.isNotEmpty() ||
                    parsed.memoryUpdateProposals.isNotEmpty() ||
                    parsed.memoryDeleteProposals.isNotEmpty() ||
                    parsed.calendarFreeQueryProposals.isNotEmpty() ||
                    parsed.calendarAddProposals.isNotEmpty()

                if (hasExtendedProposals) {
                    withContext(Dispatchers.IO) {
                        val db = AppDatabaseHolder.db(appCtx)
                        val appGroups = db.messageDao().groupByApp()

                        fun resolveAppPackage(rawPkgOrLabel: String?): Pair<String, String>? {
                            val needle = rawPkgOrLabel?.trim()?.takeIf { it.isNotEmpty() } ?: return null
                            val exact = appGroups.firstOrNull {
                                it.packageName.equals(needle, ignoreCase = true) ||
                                    it.appLabel.equals(needle, ignoreCase = true)
                            }
                            if (exact != null) return exact.packageName to exact.appLabel.ifBlank { exact.packageName }
                            val containsMatches = appGroups.filter {
                                it.appLabel.contains(needle, ignoreCase = true) ||
                                    it.packageName.contains(needle, ignoreCase = true)
                            }
                            return if (containsMatches.size == 1) {
                                val g = containsMatches.first()
                                g.packageName to g.appLabel.ifBlank { g.packageName }
                            } else null
                        }

                        // 1. search_messages（唯讀）
                        parsed.searchProposals.forEach { sp ->
                            val days = sp.days ?: 2
                            val cal = java.util.Calendar.getInstance().apply {
                                set(java.util.Calendar.HOUR_OF_DAY, 0)
                                set(java.util.Calendar.MINUTE, 0)
                                set(java.util.Calendar.SECOND, 0)
                                set(java.util.Calendar.MILLISECOND, 0)
                                add(java.util.Calendar.DAY_OF_YEAR, -(days - 1).coerceAtLeast(0))
                            }
                            val sinceTs = cal.timeInMillis
                            val resolvedPkg = if (sp.packageName != null) {
                                resolveAppPackage(sp.packageName)?.first ?: sp.packageName
                            } else null
                            val hits = db.messageDao().searchFiltered(
                                sinceTs = sinceTs,
                                packageName = resolvedPkg,
                                sender = sp.sender,
                                query = sp.query,
                                limit = 5
                            )
                            if (hits.isEmpty()) {
                                immediateExtraReplies.add("查無符合條件的訊息（搜尋範圍：近 ${days} 天）。")
                            } else {
                                val summaryParts = listOfNotNull(
                                    sp.query?.let { "關鍵字「$it」" },
                                    sp.sender?.let { "寄件者「$it」" },
                                    sp.packageName?.let { "App「$it」" }
                                ).joinToString("、").ifBlank { "近 ${days} 天" }
                                resolvedSearchCard = SearchResultCardState(
                                    sessionId = sessionId,
                                    querySummary = summaryParts,
                                    results = hits
                                )
                                immediateExtraReplies.add("已找到 ${hits.size} 筆符合「$summaryParts」的訊息，請點擊下方結果卡查看詳情。")
                            }
                        }

                        // 2. todo_list / todo_done / todo_delete / todo_edit
                        if (parsed.todoManageProposals.isNotEmpty()) {
                            val allTodos = db.todoDao().all()
                            val pendingTodos = allTodos.filter { !it.isDone }
                            parsed.todoManageProposals.forEach { tp ->
                                when (tp.action) {
                                    "todo_list" -> {
                                        if (allTodos.isEmpty()) {
                                            immediateExtraReplies.add("目前沒有任何待辦事項。")
                                        } else {
                                            val rows = allTodos.take(20).joinToString("\n") { t ->
                                                val status = if (t.isDone) "已完成" else "待辦"
                                                val timeStr = PhantomPrefs.formatMessageTime(appCtx, t.dueAt, includeDate = true)
                                                val safeTitle = t.title.replace("|", "｜").replace("\n", " ")
                                                "| $status | $safeTitle | $timeStr |"
                                            }
                                            immediateExtraReplies.add("### 待辦事項清單\n| 狀態 | 事項 | 提醒時間 |\n|---|---|---|\n$rows")
                                        }
                                    }
                                    "todo_done" -> {
                                        val needle = tp.title.orEmpty()
                                        val matches = pendingTodos.filter { it.title.contains(needle, ignoreCase = true) }
                                        when {
                                            matches.isEmpty() -> resolutionErrors.add("找不到包含「$needle」的未完成待辦事項。")
                                            matches.size > 1 -> resolutionErrors.add(
                                                "找到多筆符合「$needle」的待辦，請指定更精確的名稱：\n" +
                                                    matches.take(5).joinToString("\n") { "• ${it.title}" }
                                            )
                                            else -> resolvedExtendedActions.add(PendingExtendedAction.TodoDone(sessionId, matches.first()))
                                        }
                                    }
                                    "todo_delete" -> {
                                        val needle = tp.title.orEmpty()
                                        val matches = (pendingTodos.filter { it.title.contains(needle, ignoreCase = true) })
                                            .ifEmpty { allTodos.filter { it.title.contains(needle, ignoreCase = true) } }
                                        when {
                                            matches.isEmpty() -> resolutionErrors.add("找不到包含「$needle」的待辦事項。")
                                            matches.size > 1 -> resolutionErrors.add(
                                                "找到多筆符合「$needle」的待辦，請指定更精確的名稱：\n" +
                                                    matches.take(5).joinToString("\n") { "• ${it.title}" }
                                            )
                                            else -> resolvedExtendedActions.add(PendingExtendedAction.TodoDelete(sessionId, matches.first()))
                                        }
                                    }
                                    "todo_edit" -> {
                                        val needle = tp.title.orEmpty()
                                        val matches = (pendingTodos.filter { it.title.contains(needle, ignoreCase = true) })
                                            .ifEmpty { allTodos.filter { it.title.contains(needle, ignoreCase = true) } }
                                        when {
                                            matches.isEmpty() -> resolutionErrors.add("找不到包含「$needle」的待辦事項。")
                                            matches.size > 1 -> resolutionErrors.add(
                                                "找到多筆符合「$needle」的待辦，請指定更精確的名稱：\n" +
                                                    matches.take(5).joinToString("\n") { "• ${it.title}" }
                                            )
                                            else -> {
                                                val target = matches.first()
                                                val updatedTitle = tp.newTitle?.takeIf { it.isNotBlank() } ?: target.title
                                                val updatedDueAt = if (!tp.time.isNullOrBlank()) {
                                                    when (val tr = ClientTimeParser.parseNaturalReminderTime(tp.time, query, System.currentTimeMillis())) {
                                                        is NaturalTimeParseResult.Success -> tr.epochMillis
                                                        is NaturalTimeParseResult.PastTime -> {
                                                            resolutionErrors.add("修改後的提醒時間已過期，請指定未來的時間。")
                                                            null
                                                        }
                                                        NaturalTimeParseResult.Unparseable -> {
                                                            resolutionErrors.add("無法解析修改後的提醒時間「${tp.time}」。")
                                                            null
                                                        }
                                                    }
                                                } else target.dueAt
                                                if (updatedDueAt != null) {
                                                    if (updatedTitle == target.title && updatedDueAt == target.dueAt) {
                                                        resolutionErrors.add("請提供要修改的新標題或新提醒時間。")
                                                    } else {
                                                        resolvedExtendedActions.add(
                                                            PendingExtendedAction.TodoEdit(
                                                                sessionId = sessionId,
                                                                original = target,
                                                                newTitle = updatedTitle,
                                                                newDueAt = updatedDueAt
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. vip_pin
                        if (parsed.vipPinProposals.isNotEmpty()) {
                            val recentMsgs = db.messageDao().recent()
                            parsed.vipPinProposals.forEach { vp ->
                                val pkgFilter = vp.packageName?.let { resolveAppPackage(it)?.first ?: it }
                                val matchedMsg = recentMsgs.firstOrNull { m ->
                                    val senderHit = m.sender.equals(vp.sender, ignoreCase = true) ||
                                        m.sender.contains(vp.sender, ignoreCase = true)
                                    val pkgHit = pkgFilter == null || m.packageName.equals(pkgFilter, ignoreCase = true)
                                    senderHit && pkgHit
                                }
                                if (matchedMsg == null) {
                                    resolutionErrors.add("近期訊息中找不到寄件者「${vp.sender}」，無法設定 VIP 釘選。")
                                } else {
                                    resolvedExtendedActions.add(
                                        PendingExtendedAction.VipPin(
                                            sessionId = sessionId,
                                            sender = matchedMsg.sender,
                                            packageName = matchedMsg.packageName,
                                            appLabel = matchedMsg.appLabel.ifBlank { matchedMsg.packageName },
                                            pin = vp.pin
                                        )
                                    )
                                }
                            }
                        }

                        // 4. mute_app
                        parsed.muteAppProposals.forEach { mp ->
                            val resolved = resolveAppPackage(mp.appLabel)
                            if (resolved == null) {
                                val candidatesText = appGroups.take(8).joinToString("、") { it.appLabel.ifBlank { it.packageName } }
                                resolutionErrors.add(
                                    if (candidatesText.isBlank()) "目前無任何 App 訊息紀錄，無法設定靜音。"
                                    else "找不到名為「${mp.appLabel}」的 App，目前可選 App：$candidatesText"
                                )
                            } else {
                                resolvedExtendedActions.add(
                                    PendingExtendedAction.MuteApp(
                                        sessionId = sessionId,
                                        packageName = resolved.first,
                                        appLabel = resolved.second,
                                        mute = mp.mute
                                    )
                                )
                            }
                        }

                        // 5. mark_read
                        parsed.markReadProposals.forEach { mr ->
                            when (mr.scope) {
                                "today" -> resolvedExtendedActions.add(
                                    PendingExtendedAction.MarkRead(sessionId, "today", null, "今日未讀訊息（不含詐騙紀錄）")
                                )
                                "all" -> resolvedExtendedActions.add(
                                    PendingExtendedAction.MarkRead(sessionId, "all", null, "全部未讀訊息（不含詐騙紀錄）")
                                )
                                "app" -> {
                                    val resolved = resolveAppPackage(mr.packageName)
                                    if (resolved == null) {
                                        val candidatesText = appGroups.take(8).joinToString("、") { it.appLabel.ifBlank { it.packageName } }
                                        resolutionErrors.add("請指定有效的 App 名稱（可選：$candidatesText）。")
                                    } else {
                                        resolvedExtendedActions.add(
                                            PendingExtendedAction.MarkRead(
                                                sessionId,
                                                "app",
                                                resolved.first,
                                                "${resolved.second} 的未讀訊息（不含詐騙紀錄）"
                                            )
                                        )
                                    }
                                }
                                else -> resolutionErrors.add("不支援的標記已讀範圍「${mr.scope}」，請使用 today、app 或 all。")
                            }
                        }

                        // 6. export_messages
                        parsed.exportProposals.forEach { ep ->
                            val resolvedApp = if (ep.packageName != null) resolveAppPackage(ep.packageName) else null
                            if (ep.packageName != null && resolvedApp == null) {
                                val candidatesText = appGroups.take(8).joinToString("、") { it.appLabel.ifBlank { it.packageName } }
                                resolutionErrors.add("找不到要匯出的 App「${ep.packageName}」（可選：$candidatesText）。")
                            } else {
                                val appDesc = resolvedApp?.second ?: "全部 App"
                                val daysDesc = ep.days?.let { "近 $it 天" } ?: "全部時間"
                                resolvedExtendedActions.add(
                                    PendingExtendedAction.ExportMessages(
                                        sessionId = sessionId,
                                        packageName = resolvedApp?.first,
                                        days = ep.days,
                                        scopeLabel = "$appDesc · $daysDesc"
                                    )
                                )
                            }
                        }

                        // 7. whitelist_delete
                        if (parsed.whitelistDeleteProposals.isNotEmpty()) {
                            val allWl = db.aiRuleDao().all().filter { it.kind == AiRuleEntity.KIND_WHITELIST }
                            val matchedRules = mutableListOf<AiRuleEntity>()
                            parsed.whitelistDeleteProposals.forEach { wp ->
                                val byId = wp.ruleId?.let { id -> allWl.firstOrNull { it.id == id } }
                                if (byId != null) {
                                    matchedRules.add(byId)
                                } else if (!wp.query.isNullOrBlank()) {
                                    val q = wp.query.trim()
                                    val hits = allWl.filter { r ->
                                        r.sender?.contains(q, ignoreCase = true) == true ||
                                            r.ruleText.contains(q, ignoreCase = true) ||
                                            r.packageName?.contains(q, ignoreCase = true) == true
                                    }
                                    if (hits.isEmpty()) {
                                        resolutionErrors.add("找不到符合「$q」的白名單規則，請先確認白名單清單。")
                                    } else {
                                        matchedRules.addAll(hits)
                                    }
                                } else {
                                    resolutionErrors.add("移出白名單缺少規則 ID 或查詢條件。")
                                }
                            }
                            val distinctMatched = matchedRules.distinctBy { it.id }
                            if (distinctMatched.isNotEmpty()) {
                                val summaryText = distinctMatched.joinToString("\n") { r ->
                                    val target = r.sender?.takeIf { it.isNotBlank() } ?: r.ruleText.take(40)
                                    val mode = if (!r.sender.isNullOrBlank()) "全寄件者" else "僅內容"
                                    val pkg = r.packageName?.takeIf { it.isNotBlank() } ?: "全域"
                                    "• #${r.id} $target（$pkg，$mode，${formatChatTime(r.createdAt)}）"
                                }
                                resolvedExtendedActions.add(
                                    PendingExtendedAction.WhitelistDelete(
                                        sessionId = sessionId,
                                        rules = distinctMatched,
                                        summaryText = summaryText
                                    )
                                )
                            }
                        }

                        // 8. #314 A4 memory_view / memory_list（唯讀列出記憶含層級與狀態）
                        if (parsed.memoryViewProposals.isNotEmpty()) {
                            com.phantom.checker.data.MemoryEngine.lazyArchiveExpiredShortTerm(db, nowMs)
                            val allMemories = db.memoryDao().all()
                            parsed.memoryViewProposals.forEach { mvp ->
                                val filtered = allMemories.filter { m ->
                                    val layerOk = mvp.layer.isNullOrBlank() || mvp.layer == "all" || m.layer.equals(mvp.layer, ignoreCase = true)
                                    val statusOk = mvp.status.isNullOrBlank() || mvp.status == "all" || m.status.equals(mvp.status, ignoreCase = true)
                                    val isSensitiveMem = m.privacyTier == com.phantom.checker.data.MemoryEntity.PRIVACY_SENSITIVE
                                    val queryOk = mvp.query.isNullOrBlank() ||
                                        m.subject.contains(mvp.query, ignoreCase = true) ||
                                        (!isSensitiveMem && m.content.contains(mvp.query, ignoreCase = true)) ||
                                        com.phantom.checker.data.MemoryEngine.categoryLabel(m.category).contains(mvp.query, ignoreCase = true)
                                    layerOk && statusOk && queryOk
                                }
                                if (filtered.isEmpty()) {
                                    immediateExtraReplies.add("記憶庫中目前沒有符合條件的記憶紀錄。")
                                } else {
                                    val rows = filtered.take(25).joinToString("\n") { m ->
                                        val catLbl = com.phantom.checker.data.MemoryEngine.categoryLabel(m.category)
                                        val layerLbl = com.phantom.checker.data.MemoryEngine.layerLabel(m.layer)
                                        val statusLbl = com.phantom.checker.data.MemoryEngine.statusLabel(m.status)
                                        val confLbl = com.phantom.checker.data.MemoryEngine.confidenceLabel(m.confidence)
                                        val isSensitive = m.privacyTier == com.phantom.checker.data.MemoryEntity.PRIVACY_SENSITIVE
                                        val privTag = if (isSensitive) "🔒敏感" else "一般"
                                        val safeSubj = m.subject.replace("|", "｜").replace("\n", " ").take(20)
                                        val safeContent = if (isSensitive) {
                                            "內容已隱藏（僅本機可見，請至記憶頁查看）"
                                        } else {
                                            m.content.replace("|", "｜").replace("\n", " ").take(45)
                                        }
                                        "| #${m.id} | $catLbl | $layerLbl | $statusLbl/$confLbl ($privTag) | $safeSubj：$safeContent |"
                                    }
                                    immediateExtraReplies.add(
                                        "### 記憶庫清單（共 ${filtered.size} 筆）\n" +
                                            "| ID | 分類 | 層級 | 狀態/可信度 | 內容 |\n|---|---|---|---|---|\n$rows"
                                    )
                                }
                            }
                        }

                        // 9. #314 A2/A4 memory_add & memory_update（一律進確認卡，含衝突預覽）
                        val sourceRefNow = com.phantom.checker.data.MemoryEngine.formatChatSourceRef(
                            sessionId = sessionId,
                            userSnippet = query,
                            timeFormatted = formatChatTime(nowMs)
                        )
                        if (PhantomPrefs.isMemoryChatCaptureEnabled(appCtx)) {
                            parsed.memoryAddProposals.forEach { map ->
                                val expiresAt = if (map.layer == com.phantom.checker.data.MemoryEntity.LAYER_SHORT_TERM) {
                                    val days = (map.expiresDays ?: 7).coerceIn(1, 365)
                                    nowMs + java.util.concurrent.TimeUnit.DAYS.toMillis(days.toLong())
                                } else null
                                val conflict = com.phantom.checker.data.MemoryEngine.previewConflict(
                                    db = db,
                                    category = map.category,
                                    subject = map.subject,
                                    proposedContent = map.content,
                                    nowMs = nowMs
                                )
                                resolvedMemoryWrites.add(
                                    PendingMemoryWriteState(
                                        sessionId = sessionId,
                                        isUpdate = false,
                                        targetMemoryId = null,
                                        category = map.category,
                                        subject = map.subject,
                                        content = map.content,
                                        layer = map.layer,
                                        expiresAt = expiresAt,
                                        privacyTier = map.privacyTier,
                                        sourceRef = sourceRefNow,
                                        conflictingMemory = conflict
                                    )
                                )
                            }

                            parsed.memoryUpdateProposals.forEach { mup ->
                                val existingById = mup.memoryId?.let { db.memoryDao().byId(it) }
                                val existingBySubj = if (existingById == null && !mup.subject.isNullOrBlank()) {
                                    val normKey = com.phantom.checker.data.MemoryEngine.normalizeSubjectKey(mup.subject)
                                    db.memoryDao().all().firstOrNull {
                                        com.phantom.checker.data.MemoryEngine.normalizeSubjectKey(it.subject) == normKey
                                    } ?: db.memoryDao().all().firstOrNull {
                                        it.subject.contains(mup.subject, ignoreCase = true)
                                    }
                                } else null
                                val target = existingById ?: existingBySubj
                                val resolvedCat = mup.category ?: target?.category ?: com.phantom.checker.data.MemoryEntity.CATEGORY_OTHER
                                val resolvedSubj = mup.subject ?: target?.subject ?: mup.content.take(16).trim()
                                val resolvedLayer = mup.layer ?: target?.layer ?: com.phantom.checker.data.MemoryEntity.LAYER_LONG_TERM
                                val resolvedPrivacy = mup.privacyTier ?: target?.privacyTier ?: com.phantom.checker.data.MemoryEntity.PRIVACY_NORMAL
                                val conflict = if (target != null && !com.phantom.checker.data.MemoryEngine.isSubstantiallySameContent(target.content, mup.content)) {
                                    target
                                } else {
                                    com.phantom.checker.data.MemoryEngine.previewConflict(
                                        db = db,
                                        category = resolvedCat,
                                        subject = resolvedSubj,
                                        proposedContent = mup.content,
                                        nowMs = nowMs
                                    )
                                }
                                resolvedMemoryWrites.add(
                                    PendingMemoryWriteState(
                                        sessionId = sessionId,
                                        isUpdate = true,
                                        targetMemoryId = target?.id,
                                        category = resolvedCat,
                                        subject = resolvedSubj,
                                        content = mup.content,
                                        layer = resolvedLayer,
                                        expiresAt = target?.expiresAt,
                                        privacyTier = resolvedPrivacy,
                                        sourceRef = sourceRefNow,
                                        conflictingMemory = conflict
                                    )
                                )
                            }
                        }

                        // 10. #314 A4 memory_delete（單筆／依關鍵字／依人物批次，皆確認卡，絕不自動刪除）
                        if (parsed.memoryDeleteProposals.isNotEmpty()) {
                            val allMemories = db.memoryDao().all()
                            parsed.memoryDeleteProposals.forEach { mdp ->
                                val matched = mutableListOf<com.phantom.checker.data.MemoryEntity>()
                                if (mdp.memoryId != null) {
                                    allMemories.firstOrNull { it.id == mdp.memoryId }?.let { matched.add(it) }
                                }
                                if (matched.isEmpty() && !mdp.subject.isNullOrBlank()) {
                                    val s = mdp.subject.trim()
                                    matched.addAll(
                                        allMemories.filter {
                                            it.subject.contains(s, ignoreCase = true) ||
                                                it.content.contains(s, ignoreCase = true)
                                        }
                                    )
                                }
                                if (matched.isEmpty() && !mdp.query.isNullOrBlank()) {
                                    val q = mdp.query.trim()
                                    matched.addAll(
                                        allMemories.filter {
                                            it.subject.contains(q, ignoreCase = true) ||
                                                it.content.contains(q, ignoreCase = true) ||
                                                com.phantom.checker.data.MemoryEngine.categoryLabel(it.category).contains(q, ignoreCase = true)
                                        }
                                    )
                                }
                                val distinctTargets = matched.distinctBy { it.id }
                                val labelDesc = listOfNotNull(
                                    mdp.memoryId?.let { "#$it" },
                                    mdp.subject?.let { "人物/主題「$it」" },
                                    mdp.query?.let { "關鍵字「$it」" }
                                ).joinToString("、").ifBlank { "指定條件" }
                                if (distinctTargets.isEmpty()) {
                                    immediateExtraReplies.add("找不到符合「$labelDesc」的記憶，無法執行刪除。")
                                } else {
                                    resolvedMemoryDeletes.add(
                                        PendingMemoryDeleteState(
                                            sessionId = sessionId,
                                            targets = distinctTargets,
                                            filterSummary = labelDesc
                                        )
                                    )
                                }
                            }
                        }

                        // #329 本機日曆空檔查詢（本地查詢如實呈現，LLM 不編造）
                        if (parsed.calendarFreeQueryProposals.isNotEmpty()) {
                            parsed.calendarFreeQueryProposals.forEach { q ->
                                val summary = com.phantom.checker.util.CalendarBridge.queryFreeSlotsSummary(
                                    ctx = appCtx,
                                    days = q.days,
                                    fromHHmm = q.from,
                                    toHHmm = q.to,
                                    dayFilter = q.day
                                )
                                immediateExtraReplies.add(summary)
                            }
                        }

                        // #329 本機日曆新增行程（組裝確認卡並先做重疊撞期檢查）
                        if (parsed.calendarAddProposals.isNotEmpty()) {
                            val writableCals = com.phantom.checker.util.CalendarBridge.listSelectedWritableCalendars(appCtx)
                            parsed.calendarAddProposals.forEach { cap ->
                                val parsedStart = com.phantom.checker.util.ClientTimeParser.parseStructuredDueAt(cap.startRaw)
                                    ?: (com.phantom.checker.util.ClientTimeParser.parseNaturalReminderTime(null, cap.startRaw) as? com.phantom.checker.util.NaturalTimeParseResult.Success)?.epochMillis
                                if (parsedStart == null) {
                                    immediateExtraReplies.add("無法解析日曆行程「${cap.title}」的開始時間（${cap.startRaw}），請提供明確日期時間（如 2026-11-21 14:00）。")
                                } else {
                                    val parsedEnd = cap.endRaw?.let { rawEnd ->
                                        com.phantom.checker.util.ClientTimeParser.parseStructuredDueAt(rawEnd)
                                            ?: (com.phantom.checker.util.ClientTimeParser.parseNaturalReminderTime(null, rawEnd) as? com.phantom.checker.util.NaturalTimeParseResult.Success)?.epochMillis
                                    }?.takeIf { it > parsedStart } ?: (parsedStart + 60L * 60L * 1000L)
                                    val conflicts = com.phantom.checker.util.CalendarBridge.findConflictingEvents(
                                        ctx = appCtx,
                                        startMs = parsedStart,
                                        endMs = parsedEnd
                                    )
                                    resolvedCalendarAdds.add(
                                        PendingCalendarAddition(
                                            sessionId = sessionId,
                                            title = cap.title,
                                            startMs = parsedStart,
                                            endMs = parsedEnd,
                                            allDay = cap.allDay,
                                            location = cap.location,
                                            notes = cap.notes,
                                            writableCalendars = writableCals,
                                            conflicts = conflicts
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                if (resolvedCalendarAdds.isNotEmpty()) {
                    pendingCalendarQueue = resolvedCalendarAdds
                }

                if (immediateExtraReplies.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        val db = AppDatabaseHolder.db(appCtx)
                        immediateExtraReplies.forEach { extraText ->
                            db.chatDao().insertMessage(
                                ChatMessageEntity(
                                    sessionId = sessionId,
                                    role = ChatMessageEntity.ROLE_ASSISTANT,
                                    content = extraText,
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    loadMessages(sessionId)
                }
                if (resolvedSearchCard != null) {
                    searchResultCard = resolvedSearchCard
                }
                if (resolvedExtendedActions.isNotEmpty()) {
                    pendingExtendedActionQueue = pendingExtendedActionQueue.filterNot { it.sessionId == sessionId } + resolvedExtendedActions
                }
                if (resolvedMemoryWrites.isNotEmpty()) {
                    pendingMemoryWriteQueue = pendingMemoryWriteQueue + resolvedMemoryWrites
                }
                if (resolvedMemoryDeletes.isNotEmpty()) {
                    pendingMemoryDeleteQueue = pendingMemoryDeleteQueue + resolvedMemoryDeletes
                }
                // #336 純本機暱稱綁定候選偵測（絕不外送通訊錄給 AI）
                if (pendingAliasCandidate == null && com.phantom.checker.util.ContactBridge.hasReadContactsPermission(appCtx)) {
                    val detectedAlias = withContext(Dispatchers.IO) {
                        val db = AppDatabaseHolder.db(appCtx)
                        val recentSenders = db.messageDao().paged(30, 0)
                            .filter { it.sender.length >= 2 && query.contains(it.sender, ignoreCase = true) }
                            .distinctBy { it.sender to it.packageName }
                            .take(3)
                        recentSenders.firstNotNullOfOrNull { m ->
                            com.phantom.checker.util.ContactBridge.findAliasCandidateForSender(
                                ctx = appCtx,
                                db = db,
                                sender = m.sender,
                                packageName = m.packageName
                            )
                        }
                    }
                    if (detectedAlias != null) {
                        pendingAliasCandidate = detectedAlias
                    }
                }
                if (newAppliedSettings.isNotEmpty()) {
                    val updatedKeys = newAppliedSettings.map { it.sessionId to it.item.id }.toSet()
                    appliedSettingCards = appliedSettingCards.filterNot { (it.sessionId to it.item.id) in updatedKeys } + newAppliedSettings
                }
                if (!finalCandidates.isNullOrEmpty()) {
                    pendingSettingCandidates = PendingSettingCandidatesState(
                        sessionId = sessionId,
                        candidates = finalCandidates
                    )
                } else if (newAppliedSettings.isNotEmpty() || usedNumericFallback) {
                    pendingSettingCandidates = null
                }
                if (parsed.crossAppActions.isNotEmpty()) {
                    pendingCrossAppQueue = pendingCrossAppQueue + parsed.crossAppActions
                }
                pendingOpenSettingItem?.let { targetItem ->
                    val targetIntent = when (targetItem.page) {
                        "PermissionHubActivity" -> Intent(ctx, PermissionHubActivity::class.java)
                        "AssistantHubActivity" -> Intent(ctx, AssistantHubActivity::class.java)
                        "LinkedAppsActivity" -> Intent(ctx, LinkedAppsActivity::class.java)
                        "ApiKeySettingsActivity" -> Intent(ctx, ApiKeySettingsActivity::class.java)
                        else -> Intent(ctx, SettingsActivity::class.java).apply {
                            putExtra("highlight_card_key", targetItem.cardKey)
                        }
                    }
                    ctx.startActivity(targetIntent)
                }
            }.onFailure { err ->
                lastError = err.message ?: "AI 回覆失敗"
                lastFailedQuery = query
            }
        }
    }

    deleteSessionConfirm?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteSessionConfirm = null },
            title = { Text("刪除對話紀錄") },
            text = { Text("確定要刪除「${target.title}」的對話紀錄嗎？") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                AppDatabaseHolder.db(appCtx).chatDao().deleteSessionWithMessages(target.id)
                            }
                            if (currentSessionId == target.id) {
                                currentSessionId = null
                                messages = emptyList()
                            }
                            deleteSessionConfirm = null
                            loadSessionsAndSuggestions()
                            onShowMessage("已刪除對話")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確認刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteSessionConfirm = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showClearAllSessionsConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllSessionsConfirm = false },
            title = { Text("清除全部對話") },
            text = { Text("確定要永久刪除所有歷史對話與訊息紀錄嗎？此操作無法復原。") },
            confirmButton = {
                Button(
                    onClick = {
                        if (isSending) {
                            showClearAllSessionsConfirm = false
                            onShowMessage("AI 正在回覆中，請稍候再清除對話")
                            return@Button
                        }
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                AppDatabaseHolder.db(appCtx).chatDao().clearAllSessions()
                            }
                            currentSessionId = null
                            messages = emptyList()
                            lastError = null
                            lastFailedQuery = null
                            showClearAllSessionsConfirm = false
                            showSessionDrawer = false
                            loadSessionsAndSuggestions()
                            onShowMessage("已清除全部對話")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確認清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllSessionsConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    pendingRuleQueue.firstOrNull()?.let { pending ->
        val proposal = pending.proposal
        val remainingCount = pendingRuleQueue.size
        val kindLabel = when (proposal.kind) {
            AiRuleEntity.KIND_WHITELIST -> "白名單規則（跳過詐騙警示／情報）"
            "memo" -> "自動記事/待辦規則"
            else -> "AI 標籤規則"
        }
        AlertDialog(
            onDismissRequest = { pendingRuleQueue = pendingRuleQueue.drop(1) },
            title = {
                Text(if (remainingCount > 1) "確認新增規則（尚有 $remainingCount 筆）" else "確認新增規則")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "類型：$kindLabel",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!proposal.tagName.isNullOrBlank() && proposal.kind == AiRuleEntity.KIND_TAG) {
                        Text(
                            text = "目標標籤：${proposal.tagName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "規則內容：${proposal.ruleText}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val sid = pending.sessionId
                    pendingRuleQueue = pendingRuleQueue.drop(1)
                    scope.launch {
                        val verifiedOk = withContext(Dispatchers.IO) {
                            val db = AppDatabaseHolder.db(appCtx)
                            if (proposal.kind == "memo") {
                                val pkg = proposal.memoApp?.trim()?.takeIf { it.isNotEmpty() }
                                val snd = proposal.memoSender?.trim()?.takeIf { it.isNotEmpty() }
                                val kw = proposal.memoKeyword?.trim()?.takeIf { it.isNotEmpty() }
                                val act = proposal.memoAction ?: com.phantom.checker.data.MemoRuleEntity.ACTION_NEVER
                                val existing = db.memoRuleDao().all().any { r ->
                                    (r.packageName ?: "") == (pkg ?: "") &&
                                        (r.sender ?: "").equals(snd ?: "", ignoreCase = true) &&
                                        (r.keyword ?: "").equals(kw ?: "", ignoreCase = true) &&
                                        r.action == act
                                }
                                val ok = if (existing) {
                                    true
                                } else {
                                    val insertedId = db.memoRuleDao().insert(
                                        com.phantom.checker.data.MemoRuleEntity(
                                            packageName = pkg,
                                            sender = snd,
                                            keyword = kw,
                                            action = act,
                                            isEnabled = true,
                                            createdAt = System.currentTimeMillis(),
                                            source = com.phantom.checker.data.MemoRuleEntity.SOURCE_CHAT
                                        )
                                    )
                                    insertedId > 0L
                                }
                                NotificationCollectorService.invalidateRuleCaches()
                                db.chatDao().insertMessage(
                                    ChatMessageEntity(
                                        sessionId = sid,
                                        role = ChatMessageEntity.ROLE_ASSISTANT,
                                        content = if (existing) {
                                            "✓ 已有相同自動記事規則：${proposal.ruleText}"
                                        } else if (ok) {
                                            "✓ 已新增自動記事規則：${proposal.ruleText}"
                                        } else {
                                            "✗ 新增自動記事規則失敗：${proposal.ruleText}"
                                        },
                                        createdAt = System.currentTimeMillis()
                                    )
                                )
                                ok
                            } else {
                                val targetTagId = if (proposal.kind == AiRuleEntity.KIND_TAG && !proposal.tagName.isNullOrBlank()) {
                                    db.tagDao().all().firstOrNull { it.name.equals(proposal.tagName.trim(), ignoreCase = true) }?.id
                                } else {
                                    null
                                }
                                val insertedId = db.aiRuleDao().insert(
                                    AiRuleEntity(
                                        ruleText = proposal.ruleText,
                                        targetTagId = targetTagId,
                                        kind = proposal.kind,
                                        isEnabled = true,
                                        createdAt = System.currentTimeMillis()
                                    )
                                )
                                val verified = db.aiRuleDao().byId(insertedId)
                                NotificationCollectorService.invalidateRuleCaches()
                                val ok = insertedId > 0L && verified != null
                                db.chatDao().insertMessage(
                                    ChatMessageEntity(
                                        sessionId = sid,
                                        role = ChatMessageEntity.ROLE_ASSISTANT,
                                        content = if (ok) {
                                            "✓ 已新增規則 #${verified!!.id}：${proposal.ruleText}（已寫入並驗證）"
                                        } else {
                                            "✗ 新增規則失敗：${proposal.ruleText}（DB 寫入驗證失敗）"
                                        },
                                        createdAt = System.currentTimeMillis()
                                    )
                                )
                                ok
                            }
                        }
                        loadMessages(sid)
                        onShowMessage(if (verifiedOk) "已新增規則" else "新增規則失敗")
                    }
                }) {
                    Text("確認新增")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRuleQueue = pendingRuleQueue.drop(1) }) {
                    Text("取消")
                }
            }
        )
    }

    pendingTagRuleQueue.firstOrNull()?.let { pending ->
        val p = pending.proposal
        val remaining = pendingTagRuleQueue.size
        var selectedTagId by remember(pending) { mutableStateOf(pending.resolvedTagId) }
        val previewConditions = buildList {
            p.appLabel?.let { add("App：$it") }
            p.appPackage?.let { add("套件：$it") }
            p.senderPattern?.let { add("寄件者含「$it」") }
            p.contentKeyword?.let { add("關鍵字「$it」") }
            p.contentRegex?.let { add("正則 /$it/") }
            com.phantom.checker.util.TagRuleEngine.semanticLabel(p.semanticCategory)?.let { add("語意：$it") }
        }.joinToString(" ＋ ").ifBlank { p.description }
        AlertDialog(
            onDismissRequest = { pendingTagRuleQueue = pendingTagRuleQueue.drop(1) },
            title = {
                Text(if (remaining > 1) "確認建立標籤記憶規則（尚有 $remaining 筆）" else "確認建立標籤記憶規則")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "此規則將採確定性比對（零 token），優先於 App 分類與 AI 自動標籤。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "規則說明：${p.description}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "比對條件：$previewConditions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "目標標籤：",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        pending.availableTags.forEach { tag ->
                            FilterChip(
                                selected = selectedTagId == tag.id,
                                onClick = { selectedTagId = tag.id },
                                label = { Text(tag.name) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = selectedTagId != null,
                    onClick = {
                        val sid = pending.sessionId
                        val tagId = selectedTagId ?: return@Button
                        val tagName = pending.availableTags.firstOrNull { it.id == tagId }?.name ?: p.tagName
                        pendingTagRuleQueue = pendingTagRuleQueue.drop(1)
                        scope.launch {
                            val verifiedOk = withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                val insertedId = db.userTagRuleDao().insert(
                                    UserTagRuleEntity(
                                        description = p.description,
                                        appPackage = p.appPackage,
                                        appLabel = p.appLabel,
                                        senderPattern = p.senderPattern,
                                        contentKeyword = p.contentKeyword,
                                        contentRegex = p.contentRegex,
                                        semanticCategory = p.semanticCategory,
                                        targetTagId = tagId,
                                        isEnabled = true,
                                        priority = 100,
                                        source = "chat",
                                        createdAt = System.currentTimeMillis()
                                    )
                                )
                                val verified = db.userTagRuleDao().active().any { it.id == insertedId }
                                NotificationCollectorService.invalidateRuleCaches()
                                val ok = insertedId > 0L && verified
                                db.chatDao().insertMessage(
                                    ChatMessageEntity(
                                        sessionId = sid,
                                        role = ChatMessageEntity.ROLE_ASSISTANT,
                                        content = if (ok) {
                                            "✓ 已記住標籤規則 #$insertedId：「${p.description}」→ 標記為「$tagName」（已寫入並驗證）。"
                                        } else {
                                            "✗ 標籤規則寫入驗證失敗：「${p.description}」。"
                                        },
                                        createdAt = System.currentTimeMillis()
                                    )
                                )
                                ok
                            }
                            loadMessages(sid)
                            onShowMessage(if (verifiedOk) "已建立標籤記憶規則（$tagName）" else "標籤記憶規則寫入失敗")
                        }
                    }
                ) {
                    Text("確認記住")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTagRuleQueue = pendingTagRuleQueue.drop(1) }) {
                    Text("取消")
                }
            }
        )
    }

    editingTodoTimeTarget?.let { target ->
        ReminderPickerDialog(
            onDismiss = { editingTodoTimeTarget = null },
            onSelectTimestamp = { newTs ->
                editingTodoTimeTarget = null
                pendingTodoQueue = pendingTodoQueue.map { item ->
                    if (item === target) item.copy(dueAt = newTs) else item
                }
            }
        )
    }

    if (showTodoListSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTodoListSheet = false },
            containerColor = Color.Transparent
        ) {
            PhantomFloatingSurface(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 0.dp
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "待辦提醒清單",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${todoListItems.count { !it.isDone }} 項待辦",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (todoListItems.isEmpty()) {
                    Text(
                        text = "目前沒有待辦提醒。你可以對 AI 說「提醒我明天下午 3 點繳報告」來建立。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(todoListItems, key = { it.id }) { todo ->
                            val dueStr = remember(todo.dueAt) {
                                PhantomPrefs.formatMessageTime(appCtx, todo.dueAt, includeDate = true)
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(
                                    alpha = if (todo.isDone) 0.25f else 0.45f
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = todo.isDone,
                                        onCheckedChange = { checked ->
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    ReminderScheduler.completeTodoWithRecurrence(appCtx, todo, checked)
                                                }
                                            }
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = todo.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (todo.isDone) FontWeight.Normal else FontWeight.SemiBold,
                                            color = if (todo.isDone) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (todo.isDone) "已完成 · $dueStr" else "⏰ $dueStr",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    ReminderScheduler.cancelTodo(appCtx, todo.id)
                                                    AppDatabaseHolder.db(appCtx).todoDao().deleteById(todo.id)
                                                    ReminderWidgetProvider.updateAllIfChanged(appCtx)
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "刪除待辦",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val drawerWidth = minOf(320.dp, maxWidth * 0.82f)
        val drawerWidthPx = with(density) { drawerWidth.toPx() }
        val openSwipeThresholdPx = with(density) { 24.dp.toPx() }
        val openVelocityThresholdPx = with(density) { 400.dp.toPx() }
        val decaySpec = rememberSplineBasedDecay<Float>()
        val drawerState = remember {
            AnchoredDraggableState(
                initialValue = 0f,
                positionalThreshold = { distance: Float -> distance * 0.35f },
                velocityThreshold = { openVelocityThresholdPx },
                snapAnimationSpec = spring(stiffness = 380f, dampingRatio = 0.85f),
                decayAnimationSpec = decaySpec
            )
        }

        SideEffect {
            if (drawerWidthPx > 0f) {
                drawerState.updateAnchors(
                    DraggableAnchors {
                        0f at -drawerWidthPx
                        1f at 0f
                    }
                )
            }
        }

        LaunchedEffect(showSessionDrawer) {
            val target = if (showSessionDrawer) 1f else 0f
            if (drawerState.targetValue != target) {
                drawerState.animateTo(target)
            }
        }

        LaunchedEffect(drawerState) {
            snapshotFlow { drawerState.currentValue to drawerState.isAnimationRunning }
                .collect { (currentValue, isRunning) ->
                    if (!isRunning) {
                        if (currentValue == 0f && showSessionDrawer) {
                            showSessionDrawer = false
                        } else if (currentValue == 1f && !showSessionDrawer) {
                            showSessionDrawer = true
                        }
                    }
                }
        }

        BackHandler(enabled = showSessionDrawer) {
            showSessionDrawer = false
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(drawerWidthPx, showSessionDrawer) {
                    if (!showSessionDrawer && drawerWidthPx > 0f) {
                        var dragAccum = 0f
                        var triggered = false
                        val velocityTracker = VelocityTracker()
                        fun resolveDrawerTarget(velocityX: Float): Float {
                            val rawOffset = drawerState.offset
                            val currentOffset = if (rawOffset.isNaN()) -drawerWidthPx else rawOffset
                            val projectedOffset = currentOffset + velocityX * 0.15f
                            val shouldOpen = when {
                                velocityX > openVelocityThresholdPx -> true
                                velocityX < -openVelocityThresholdPx -> false
                                else -> projectedOffset > -drawerWidthPx * 0.75f
                            }
                            return if (shouldOpen) 1f else 0f
                        }
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragAccum = 0f
                                triggered = false
                                velocityTracker.resetTracking()
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                if (dragAmount > 0f || triggered) {
                                    dragAccum += dragAmount
                                    if (!triggered && dragAccum > openSwipeThresholdPx) {
                                        triggered = true
                                    }
                                    if (triggered) {
                                        drawerState.dispatchRawDelta(dragAmount)
                                    }
                                } else {
                                    dragAccum = 0f
                                }
                            },
                            onDragEnd = {
                                if (triggered) {
                                    val velocityX = velocityTracker.calculateVelocity().x
                                    val target = resolveDrawerTarget(velocityX)
                                    scope.launch {
                                        drawerState.animateTo(target)
                                        showSessionDrawer = target == 1f
                                    }
                                }
                                dragAccum = 0f
                                triggered = false
                                velocityTracker.resetTracking()
                            },
                            onDragCancel = {
                                if (triggered) {
                                    val velocityX = velocityTracker.calculateVelocity().x
                                    val target = resolveDrawerTarget(velocityX)
                                    scope.launch {
                                        drawerState.animateTo(target)
                                        showSessionDrawer = target == 1f
                                    }
                                }
                                dragAccum = 0f
                                triggered = false
                                velocityTracker.resetTracking()
                            }
                        )
                    }
                }
        ) {
        // 頂部操作列
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showSessionDrawer = true }) {
                    Icon(Icons.Default.Menu, contentDescription = "歷史對話")
                }
                AssistantAvatarCircle(
                    avatarBitmap = assistantAvatar,
                    assistantName = assistantName,
                    size = 28.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (assistantName == PhantomPrefs.DEFAULT_ASSISTANT_NAME) {
                        "幻訊 · AI 訊息助手"
                    } else {
                        "$assistantName · AI 助手"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pendingTodoCount = todoListItems.count { !it.isDone }
                TextButton(
                    onClick = {
                        showTodoListSheet = true
                    }
                ) {
                    Text(if (pendingTodoCount > 0) "待辦 ($pendingTodoCount)" else "待辦")
                }
                IconButton(onClick = {
                    currentSessionId = null
                    messages = emptyList()
                    lastError = null
                }) {
                    Icon(Icons.Default.Add, contentDescription = "新對話")
                }
            }
        }

        // Provider / 模型即時切換列＋省 token 快捷開關（含資訊說明）＋搜尋、重整、設定入口
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AiProviderSwitcherChip(
                    profiles = profiles,
                    activeProfile = activeProfile,
                    onSelectProfile = { selected ->
                        PhantomPrefs.setPurposeProfileId(appCtx, com.phantom.checker.util.AiPurpose.CHAT.id, selected.id)
                        activeProfile = selected
                        hasApiKey = true
                        onShowMessage("已切換對話模型：${selected.name}・${selected.model}")
                    },
                    modifier = Modifier.weight(1f, fill = false),
                    label = ""
                )
                FilterChip(
                    selected = tokenSaverEnabled,
                    onClick = {
                        val next = !tokenSaverEnabled
                        tokenSaverEnabled = next
                        SettingsCatalog.applyBooleanSetting(appCtx, "quick_token_saver", next)
                        if (!next) {
                            com.phantom.checker.service.DailySummaryPolicy.maybeCatchUp(appCtx)
                        }
                        onShowMessage(if (next) "已開啟省 token 模式" else "已關閉省 token 模式（已還原設定預算）")
                    },
                    label = {
                        Text(
                            text = if (tokenSaverEnabled) "省token:開" else "省token",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                )
                IconButton(
                    onClick = { showTokenSaverInfoDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "省 token 模式說明",
                        tint = if (tokenSaverEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleSearch, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (isSearchActive) Icons.Default.Clear else Icons.Default.Search,
                        contentDescription = "搜尋訊息",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "重新整理",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "設定",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (showTokenSaverInfoDialog) {
            val normalRag = PhantomPrefs.getChatRagMessageLimit(appCtx)
            val normalMemTopK = PhantomPrefs.getMemoryNormalTopK(appCtx)
            val saverMemTopK = PhantomPrefs.getMemorySaverTopK(appCtx)
            AlertDialog(
                onDismissRequest = { showTokenSaverInfoDialog = false },
                title = {
                    Text(
                        text = "省 token 模式影響清單",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "目前狀態：${if (tokenSaverEnabled) "已開啟（生效中）" else "已關閉"}。開啟時以 Override 層運作，絕不覆寫您原本的設定值，關閉後立即還原：",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "1. 每則訊息 AI 風險掃描：暫停一般訊息逐則 AI 掃描，僅在訊息命中可疑特徵（如可疑網址、短連結或過濾規則）時才觸發 AI 分析。",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "2. AI 智慧標籤與自動備忘錄：暫停每則訊息的 AI 自動標籤分發（isEffectiveAiAutoTagEnabled = false）與隨附的自動備忘錄擷取；確定性使用者標籤規則與 App 分類仍正常運作。",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "3. AI 助手單次閱讀訊息上限（RAG）：由原設定值（目前設定 $normalRag 則）暫時降載為 100 則（getEffectiveChatRagMessageLimit = 100），不覆寫原設定值。",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "4. 記憶庫對話注入預算：由正常模式（設定 $normalMemTopK 筆／每筆約 50 tokens／總預算 ≤250 tokens）切換為省 token 預算（設定 $saverMemTopK 筆／每筆約 25 tokens／總預算 ≤120 tokens），兩組設定獨立保存。",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "5. 聊天附加圖片壓縮：上傳圖片邊長上限由 768px 降至 512px、JPEG 品質由 72 降至 65，減少視覺模型圖片 token 消耗。",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val next = !tokenSaverEnabled
                            tokenSaverEnabled = next
                            SettingsCatalog.applyBooleanSetting(appCtx, "quick_token_saver", next)
                            if (!next) {
                                com.phantom.checker.service.DailySummaryPolicy.maybeCatchUp(appCtx)
                            }
                            showTokenSaverInfoDialog = false
                            onShowMessage(if (next) "已開啟省 token 模式" else "已關閉省 token 模式")
                        }
                    ) {
                        Text(if (tokenSaverEnabled) "關閉省 token" else "立即開啟省 token")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTokenSaverInfoDialog = false }) {
                        Text("關閉說明")
                    }
                }
            )
        }

        if (!hasApiKey) {
            PhantomGlassCard(
                glassState = glassState,
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 14.dp
            ) {
                Text(
                    text = "尚未設定 AI API Key",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "請先前往設定多 Provider API Key，即可啟用 AI 訊息助手查詢與昨日簡報功能。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = { ctx.startActivity(Intent(ctx, ApiKeySettingsActivity::class.java)) }) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("前往設定")
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // 對話內容區
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val userBubbleMaxWidth = maxWidth * 0.82f
            val assistantBubbleMaxWidth = maxWidth * 0.88f
            if (messages.isEmpty() && !isSending) {
                EmptyStateView(
                    icon = AutoAwesomeIcon,
                    title = "有什麼訊息想查嗎？",
                    description = "你可以問「今天 LINE 的訊息」、「昨日簡報」或「最近有可疑詐騙嗎」，${assistantName}會根據你的本地備份唯讀查詢並引用來源。"
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }, contentType = { it.role }) { msg ->
                        val isUser = msg.role == ChatMessageEntity.ROLE_USER
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            if (isUser) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .widthIn(max = userBubbleMaxWidth)
                                        .clip(RoundedCornerShape(18.dp))
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = {
                                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(ClipData.newPlainText("chat_message", msg.content))
                                                onShowMessage("已複製訊息內容")
                                            }
                                        )
                                ) {
                                    Text(
                                        text = msg.content,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier.widthIn(max = assistantBubbleMaxWidth),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(start = 4.dp)
                                    ) {
                                        AssistantAvatarCircle(
                                            avatarBitmap = assistantAvatar,
                                            assistantName = assistantName,
                                            size = 22.dp
                                        )
                                        Text(
                                            text = assistantName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    PhantomGlassCard(
                                        glassState = glassState,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(18.dp))
                                            .combinedClickable(
                                                onClick = {},
                                                onLongClick = {
                                                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    cm.setPrimaryClip(
                                                        ClipData.newPlainText("assistant_reply", stripMarkdown(msg.content))
                                                    )
                                                    onShowMessage("已複製助手回覆")
                                                }
                                            ),
                                        cornerRadius = 18.dp,
                                        contentPadding = 12.dp
                                    ) {
                                        val thinking = msg.thinkingContent?.takeIf { it.isNotBlank() }
                                        if (thinking != null) {
                                            var thinkingExpanded by remember(msg.id) { mutableStateOf(false) }
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                                border = BorderStroke(
                                                    width = 0.75.dp,
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 8.dp)
                                            ) {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { thinkingExpanded = !thinkingExpanded }
                                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = if (thinkingExpanded) "💭 推演思路（點擊收合）" else "💭 推演思路（點擊展開）",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Icon(
                                                            imageVector = if (thinkingExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                            contentDescription = if (thinkingExpanded) "收合推演思路" else "展開推演思路",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    AnimatedVisibility(
                                                        visible = thinkingExpanded,
                                                        enter = expandVertically() + fadeIn(),
                                                        exit = shrinkVertically() + fadeOut()
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(IntrinsicSize.Min)
                                                                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(2.dp)
                                                                    .fillMaxHeight()
                                                                    .background(
                                                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                                                                        shape = RoundedCornerShape(1.dp)
                                                                    )
                                                            )
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(
                                                                text = thinking,
                                                                fontSize = 12.sp,
                                                                lineHeight = 17.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        PhantomMarkdownText(
                                            markdown = msg.content,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    pendingWhitelist?.takeIf { it.sessionId == currentSessionId && it.items.isNotEmpty() }?.let { pending ->
                        item(key = "pending_whitelist_card") {
                            var selectedIndices by remember(pending) {
                                mutableStateOf(pending.items.indices.toSet())
                            }
                            val selectedCount = selectedIndices.size
                            PhantomGlassCard(
                                glassState = glassState,
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                contentPadding = 12.dp
                            ) {
                                Text(
                                    text = if (pending.items.size > 1) {
                                        "加入白名單（共 ${pending.items.size} 筆，可勾選確認）"
                                    } else {
                                        val only = pending.items.first()
                                        if (only.kind == "sender") "加入白名單（此寄件者全部訊息）" else "加入白名單（僅此則訊息）"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pending.items.forEachIndexed { idx, wItem ->
                                        val targetMsg = wItem.message
                                        val isSenderMode = wItem.kind == "sender"
                                        val checked = idx in selectedIndices
                                        val timeStr = remember(targetMsg.timestamp) {
                                            formatChatTime(targetMsg.timestamp)
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedIndices = if (checked) selectedIndices - idx else selectedIndices + idx
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Checkbox(
                                                    checked = checked,
                                                    onCheckedChange = { isChecked ->
                                                        selectedIndices = if (isChecked) selectedIndices + idx else selectedIndices - idx
                                                    }
                                                )
                                                AppIconImage(
                                                    packageName = targetMsg.packageName,
                                                    contentDescription = targetMsg.appLabel,
                                                    size = 32.dp
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "${targetMsg.sender.ifBlank { targetMsg.appLabel }} · ${targetMsg.appLabel} ($timeStr)",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = if (isSenderMode) "範圍：此寄件者全部訊息" else "範圍：僅此則訊息",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = targetMsg.content.replace("\n", " ").take(120),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val sid = pending.sessionId
                                            pendingWhitelist = null
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    AppDatabaseHolder.db(appCtx).chatDao().insertMessage(
                                                        ChatMessageEntity(
                                                            sessionId = sid,
                                                            role = ChatMessageEntity.ROLE_ASSISTANT,
                                                            content = "已取消加入白名單。",
                                                            createdAt = System.currentTimeMillis()
                                                        )
                                                    )
                                                }
                                                loadMessages(sid)
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("取消")
                                    }
                                    Button(
                                        enabled = selectedCount > 0,
                                        onClick = {
                                            val sid = pending.sessionId
                                            val chosen = pending.items.filterIndexed { idx, _ -> idx in selectedIndices }
                                            pendingWhitelist = null
                                            scope.launch {
                                                val reportLines = withContext(Dispatchers.IO) {
                                                    val db = AppDatabaseHolder.db(appCtx)
                                                    val lines = mutableListOf<String>()
                                                    chosen.forEach { wItem ->
                                                        val targetMsg = wItem.message
                                                        val isSenderMode = wItem.kind == "sender"
                                                        try {
                                                            val ruleText = if (isSenderMode) {
                                                                "白名單寄件者：${targetMsg.sender}（${targetMsg.appLabel}）"
                                                            } else {
                                                                targetMsg.content.trim().take(120).ifBlank { "白名單訊息（${targetMsg.sender}）" }
                                                            }
                                                            val insertedId = db.aiRuleDao().insert(
                                                                AiRuleEntity(
                                                                    ruleText = ruleText,
                                                                    kind = AiRuleEntity.KIND_WHITELIST,
                                                                    isEnabled = true,
                                                                    createdAt = System.currentTimeMillis(),
                                                                    sender = if (isSenderMode) targetMsg.sender.trim() else null,
                                                                    packageName = targetMsg.packageName
                                                                )
                                                            )
                                                            val verified = db.aiRuleDao().byId(insertedId)
                                                            NotificationCollectorService.invalidateRuleCaches()
                                                            val label = targetMsg.sender.ifBlank { targetMsg.appLabel }
                                                            val modeDesc = if (isSenderMode) "此寄件者全部訊息" else "單則訊息"
                                                            if (insertedId > 0L && verified != null) {
                                                                db.messageDao().update(
                                                                    targetMsg.copy(
                                                                        riskScore = 10,
                                                                        riskLabel = "safe",
                                                                        riskReason = "使用者透過 AI 助手加入白名單"
                                                                    )
                                                                )
                                                                lines.add("✓ #${verified.id} $label（${targetMsg.appLabel}，$modeDesc，已寫入並驗證）")
                                                            } else {
                                                                lines.add("✗ $label（${targetMsg.appLabel}，$modeDesc）：DB 寫入驗證失敗")
                                                            }
                                                        } catch (e: Exception) {
                                                            lines.add("✗ ${targetMsg.sender.ifBlank { targetMsg.appLabel }}：${e.message ?: "寫入失敗"}")
                                                        }
                                                    }
                                                    val summary = buildString {
                                                        appendLine("白名單處理結果（共 ${chosen.size} 筆）：")
                                                        lines.forEach { appendLine(it) }
                                                    }.trim()
                                                    db.chatDao().insertMessage(
                                                        ChatMessageEntity(
                                                            sessionId = sid,
                                                            role = ChatMessageEntity.ROLE_ASSISTANT,
                                                            content = summary,
                                                            createdAt = System.currentTimeMillis()
                                                        )
                                                    )
                                                    lines
                                                }
                                                loadMessages(sid)
                                                onShowMessage("已處理 ${reportLines.size} 筆白名單")
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(if (pending.items.size > 1) "確認加入（$selectedCount）" else "確認加入")
                                    }
                                }
                            }
                        }
                    }

                    val sessionAppliedCards = appliedSettingCards.filter { it.sessionId == currentSessionId }
                    items(
                        items = sessionAppliedCards,
                        key = { "applied_setting_${it.item.id}_${it.beforeEnabled}_${it.afterEnabled}_${it.beforeText}_${it.afterText}" }
                    ) { card ->
                        val isApplied = card.afterEnabled != null || card.afterText != null
                        PhantomGlassCard(
                            glassState = glassState,
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 18.dp,
                            contentPadding = 12.dp
                        ) {
                            Text(
                                text = if (isApplied) "設定已更新" else "需至設定頁調整",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = card.item.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = card.item.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (card.beforeEnabled != null && card.afterEnabled != null) {
                                Spacer(Modifier.height(6.dp))
                                val beforeStr = if (card.beforeEnabled) "開啟" else "關閉"
                                val afterStr = if (card.afterEnabled) "開啟" else "關閉"
                                Text(
                                    text = "狀態變更：$beforeStr → $afterStr",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else if (card.beforeText != null && card.afterText != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "數值變更：${card.beforeText} → ${card.afterText}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { appliedSettingCards = appliedSettingCards.filterNot { it === card } },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("收起")
                                }
                                Button(
                                    onClick = {
                                        val intent = Intent(ctx, SettingsActivity::class.java).apply {
                                            putExtra("highlight_card_key", card.item.cardKey)
                                        }
                                        ctx.startActivity(intent)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("前往設定頁")
                                }
                            }
                        }
                    }

                    pendingSettingAdjustQueue.firstOrNull { it.sessionId == currentSessionId }?.let { adjState ->
                        item(key = "pending_setting_adjust_card") {
                            val spec = adjState.spec
                            var draftValue by remember(adjState) { mutableStateOf(adjState.proposedValue) }
                            val stepsCount = ((spec.max - spec.min) / spec.step - 1).coerceAtLeast(0)
                            PhantomGlassCard(
                                glassState = glassState,
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                contentPadding = 12.dp
                            ) {
                                Text(
                                    text = "調整數值設定：${adjState.item.title}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "目前值：${spec.formatValue(adjState.currentValue)}　→　目標值：${spec.formatValue(draftValue)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (adjState.wasClamped) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "⚠ 原建議值超出範圍（${spec.min}–${spec.max}），已自動調整為 ${spec.formatValue(adjState.proposedValue)}。",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Slider(
                                    value = draftValue.toFloat(),
                                    onValueChange = { raw ->
                                        val (snapped, _) = spec.clampAndSnap(raw.roundToInt())
                                        draftValue = snapped
                                    },
                                    valueRange = spec.min.toFloat()..spec.max.toFloat(),
                                    steps = stepsCount
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        enabled = draftValue > spec.min,
                                        onClick = {
                                            val (snapped, _) = spec.clampAndSnap(draftValue - spec.step)
                                            draftValue = snapped
                                        }
                                    ) {
                                        Text("-${spec.step}")
                                    }
                                    Text(
                                        text = "範圍 ${spec.min}–${spec.max}（步進 ${spec.step}）",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(
                                        enabled = draftValue < spec.max,
                                        onClick = {
                                            val (snapped, _) = spec.clampAndSnap(draftValue + spec.step)
                                            draftValue = snapped
                                        }
                                    ) {
                                        Text("+${spec.step}")
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val sid = adjState.sessionId
                                            pendingSettingAdjustQueue = pendingSettingAdjustQueue.drop(1)
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    AppDatabaseHolder.db(appCtx).chatDao().insertMessage(
                                                        ChatMessageEntity(
                                                            sessionId = sid,
                                                            role = ChatMessageEntity.ROLE_ASSISTANT,
                                                            content = "已取消調整「${adjState.item.title}」。",
                                                            createdAt = System.currentTimeMillis()
                                                        )
                                                    )
                                                }
                                                loadMessages(sid)
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("取消")
                                    }
                                    Button(
                                        onClick = {
                                            val sid = adjState.sessionId
                                            val targetVal = draftValue
                                            pendingSettingAdjustQueue = pendingSettingAdjustQueue.drop(1)
                                            val applied = SettingsCatalog.applyNumericSetting(appCtx, spec.id, targetVal)
                                            if (applied != null) {
                                                val (beforeVal, afterVal, clamped) = applied
                                                appliedSettingCards = appliedSettingCards + AppliedSettingCardState(
                                                    sessionId = sid,
                                                    item = adjState.item,
                                                    beforeEnabled = null,
                                                    afterEnabled = null,
                                                    beforeText = spec.formatValue(beforeVal),
                                                    afterText = spec.formatValue(afterVal)
                                                )
                                                val note = if (clamped || adjState.wasClamped) "（已自動對齊至允許範圍）" else ""
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        AppDatabaseHolder.db(appCtx).chatDao().insertMessage(
                                                            ChatMessageEntity(
                                                                sessionId = sid,
                                                                role = ChatMessageEntity.ROLE_ASSISTANT,
                                                                content = "已將「${adjState.item.title}」調整為 ${spec.formatValue(afterVal)}$note。",
                                                                createdAt = System.currentTimeMillis()
                                                            )
                                                        )
                                                    }
                                                    loadMessages(sid)
                                                }
                                                onShowMessage("${adjState.item.title}已調整為 ${spec.formatValue(afterVal)}")
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("確認調整")
                                    }
                                }
                            }
                        }
                    }

                    pendingSettingCandidates?.takeIf { it.sessionId == currentSessionId }?.let { candState ->
                        item(key = "pending_setting_candidates") {
                            PhantomGlassCard(
                                glassState = glassState,
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                contentPadding = 12.dp
                            ) {
                                Text(
                                    text = "請選擇要調整的設定項目",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    candState.candidates.forEach { item ->
                                        val toggleable = SettingsCatalog.isToggleable(item.id)
                                        val currentOn = if (toggleable) SettingsCatalog.getBooleanState(appCtx, item.id) == true else false
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = if (toggleable) {
                                                            "${item.title}（目前：${if (currentOn) "開啟" else "關閉"}）"
                                                        } else {
                                                            item.title
                                                        },
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = item.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Spacer(Modifier.width(8.dp))
                                                if (toggleable) {
                                                    Button(
                                                        onClick = {
                                                            val target = !currentOn
                                                            val applied = SettingsCatalog.applyBooleanSetting(appCtx, item.id, target)
                                                            if (applied != null) {
                                                                pendingSettingCandidates = null
                                                                appliedSettingCards = appliedSettingCards + AppliedSettingCardState(
                                                                    sessionId = candState.sessionId,
                                                                    item = item,
                                                                    beforeEnabled = applied.first,
                                                                    afterEnabled = applied.second
                                                                )
                                                                onShowMessage("${item.title}已${if (target) "開啟" else "關閉"}")
                                                            }
                                                        }
                                                    ) {
                                                        Text(if (currentOn) "關閉" else "開啟")
                                                    }
                                                } else {
                                                    Button(
                                                        onClick = {
                                                            pendingSettingCandidates = null
                                                            ctx.startActivity(
                                                                Intent(ctx, SettingsActivity::class.java).apply {
                                                                    putExtra("highlight_card_key", item.cardKey)
                                                                }
                                                            )
                                                        }
                                                    ) {
                                                        Text("前往")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                TextButton(
                                    onClick = { pendingSettingCandidates = null },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("取消")
                                }
                            }
                        }
                    }

                    pendingMsgActionQueue.firstOrNull { it.sessionId == currentSessionId }?.let { actState ->
                        item(key = "pending_msg_action_card") {
                            var draftReply by remember(actState) { mutableStateOf(actState.replyText) }
                            var selectedId by remember(actState) { mutableStateOf(actState.selectedMessageId) }
                            val primaryMsg = actState.messages.firstOrNull { it.id == selectedId } ?: actState.messages.firstOrNull()
                            val isSingleTarget = actState.action == "reply_message" || actState.action == "open_app"
                            val scamMsgs = actState.messages.filter { it.riskLabel == "scam" }
                            val safeDeleteMsgs = actState.messages.filter { it.riskLabel != "scam" }
                            val titleLabel = when (actState.action) {
                                "reply_message" -> "確認傳送回覆"
                                "open_app" -> "確認開啟原 App"
                                "delete_message" -> "確認刪除訊息（${actState.messages.size} 則）"
                                "tag_message" -> if (actState.tagOperation == "remove") {
                                    "確認移除標籤「${actState.tagName}」"
                                } else {
                                    "確認加上標籤「${actState.tagName}」"
                                }
                                else -> "確認操作"
                            }

                            PhantomGlassCard(
                                glassState = glassState,
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                contentPadding = 12.dp
                            ) {
                                Text(
                                    text = titleLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (actState.action == "delete_message") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(8.dp))

                                if (isSingleTarget && actState.messages.size > 1) {
                                    Text(
                                        text = "找到多筆符合訊息，請點選目標：",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                }

                                val displayMsgs = if (isSingleTarget) actState.messages else actState.messages.take(5)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    displayMsgs.forEach { m ->
                                        val isPicked = primaryMsg != null && m.id == primaryMsg.id
                                        val mTime = remember(m.timestamp) {
                                            formatChatTime(m.timestamp)
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSingleTarget && isPicked) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .let { mod ->
                                                    if (isSingleTarget && actState.messages.size > 1) {
                                                        mod.clickable { selectedId = m.id }
                                                    } else {
                                                        mod
                                                    }
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                AppIconImage(
                                                    packageName = m.packageName,
                                                    contentDescription = m.appLabel,
                                                    size = 32.dp
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "${m.sender.ifBlank { m.appLabel }} · ${m.appLabel} ($mTime)",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = m.content.replace("\n", " ").take(120),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (!isSingleTarget && actState.messages.size > 5) {
                                        Text(
                                            text = "另有 ${actState.messages.size - 5} 則訊息…",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (actState.action == "reply_message" && primaryMsg != null) {
                                    Spacer(Modifier.height(8.dp))
                                    if (OtpDetector.extractOtp(primaryMsg.content) != null) {
                                        Text(
                                            text = "⚠ 此訊息含驗證碼/OTP，請確認對象安全再回覆。",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(Modifier.height(4.dp))
                                    }
                                    OutlinedTextField(
                                        value = draftReply,
                                        onValueChange = { draftReply = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("回覆內容（可編輯）") },
                                        maxLines = 4
                                    )
                                }

                                if (actState.action == "delete_message" && scamMsgs.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "⚠ 偵測到 ${scamMsgs.size} 則詐騙紀錄受保護，系統將自動略過不予刪除。",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val sid = actState.sessionId
                                            pendingMsgActionQueue = pendingMsgActionQueue.drop(1)
                                            scope.launch {
                                                try {
                                                    withContext(Dispatchers.IO) {
                                                        AppDatabaseHolder.db(appCtx).chatDao().insertMessage(
                                                            ChatMessageEntity(
                                                                sessionId = sid,
                                                                role = ChatMessageEntity.ROLE_ASSISTANT,
                                                                content = "已取消操作。",
                                                                createdAt = System.currentTimeMillis()
                                                            )
                                                        )
                                                    }
                                                    loadMessages(sid)
                                                } catch (e: Exception) {
                                                    Log.e("PhantomChatAction", "Cancel action failed sid=$sid", e)
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("取消")
                                    }
                                    val canConfirm = when (actState.action) {
                                        "reply_message" -> primaryMsg != null && draftReply.isNotBlank()
                                        "open_app" -> primaryMsg != null
                                        "delete_message" -> safeDeleteMsgs.isNotEmpty()
                                        else -> actState.messages.isNotEmpty()
                                    }
                                    Button(
                                        enabled = canConfirm,
                                        onClick = {
                                            val sid = actState.sessionId
                                            pendingMsgActionQueue = pendingMsgActionQueue.drop(1)
                                            scope.launch {
                                                val reportText: String = try {
                                                    when (actState.action) {
                                                        "reply_message" -> {
                                                            if (primaryMsg == null) {
                                                                "找不到可回覆的目標訊息。"
                                                            } else {
                                                                when (val res = NotificationCollectorService.sendReplyToNotification(
                                                                    context = appCtx,
                                                                    packageName = primaryMsg.packageName,
                                                                    sender = primaryMsg.sender,
                                                                    contentSnippet = primaryMsg.content,
                                                                    replyText = draftReply,
                                                                    conversationKey = primaryMsg.conversationKey
                                                                )) {
                                                                    is NotificationCollectorService.Companion.ReplySendResult.Success -> {
                                                                        val convKey = primaryMsg.conversationKey.ifBlank { primaryMsg.sender }
                                                                        withContext(Dispatchers.IO) {
                                                                            AppDatabaseHolder.db(appCtx).messageDao().insert(
                                                                                MessageEntity(
                                                                                    packageName = primaryMsg.packageName,
                                                                                    appLabel = primaryMsg.appLabel,
                                                                                    sender = "我",
                                                                                    content = draftReply,
                                                                                    timestamp = System.currentTimeMillis(),
                                                                                    riskScore = 0,
                                                                                    riskLabel = "",
                                                                                    riskReason = "",
                                                                                    isRead = true,
                                                                                    conversationKey = convKey,
                                                                                    dedupKey = null,
                                                                                    isOwn = true
                                                                                )
                                                                            )
                                                                        }
                                                                        com.phantom.checker.service.ReplyProxyManager.startOrRefreshSession(
                                                                            ctx = appCtx,
                                                                            packageName = primaryMsg.packageName,
                                                                            appLabel = primaryMsg.appLabel,
                                                                            sender = primaryMsg.sender,
                                                                            originalSnippet = primaryMsg.content,
                                                                            initialReplyText = draftReply
                                                                        )
                                                                        "已透過通知快速回覆送出給「${primaryMsg.sender.ifBlank { primaryMsg.appLabel }}」：$draftReply"
                                                                    }
                                                                    is NotificationCollectorService.Companion.ReplySendResult.Unavailable ->
                                                                        res.reason
                                                                }
                                                            }
                                                        }
                                                        "open_app" -> {
                                                            if (primaryMsg == null) {
                                                                "找不到目標 App 訊息。"
                                                            } else {
                                                                val launch = ctx.packageManager.getLaunchIntentForPackage(primaryMsg.packageName)
                                                                if (launch != null) {
                                                                    try {
                                                                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                        ctx.startActivity(launch)
                                                                        "已開啟「${primaryMsg.appLabel}」。"
                                                                    } catch (e: Exception) {
                                                                        Log.e("PhantomChatAction", "open_app launch failed pkg=${primaryMsg.packageName}", e)
                                                                        "無法開啟「${primaryMsg.appLabel}」（${e.message ?: "啟動失敗"}）。"
                                                                    }
                                                                } else {
                                                                    val detailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                                        data = Uri.parse("package:${primaryMsg.packageName}")
                                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                    }
                                                                    try {
                                                                        ctx.startActivity(detailsIntent)
                                                                        "「${primaryMsg.appLabel}」無可開啟的桌面入口，已改為開啟應用程式資訊頁。"
                                                                    } catch (e: Exception) {
                                                                        Log.e("PhantomChatAction", "open_app details failed pkg=${primaryMsg.packageName}", e)
                                                                        "裝置上未安裝「${primaryMsg.appLabel}（${primaryMsg.packageName}）」，無法開啟。"
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        "delete_message" -> {
                                                            withContext(Dispatchers.IO) {
                                                                AppDatabaseHolder.db(appCtx).messageDao()
                                                                    .deleteByIds(safeDeleteMsgs.map { it.id })
                                                            }
                                                            StatsForegroundService.startOrRefresh(appCtx, force = true)
                                                            if (scamMsgs.isNotEmpty()) {
                                                                "已刪除 ${safeDeleteMsgs.size} 則訊息（另有 ${scamMsgs.size} 則詐騙紀錄受保護未刪除）。"
                                                            } else {
                                                                "已刪除 ${safeDeleteMsgs.size} 則訊息。"
                                                            }
                                                        }
                                                        "tag_message" -> {
                                                            val tid = actState.tagId
                                                            val tName = actState.tagName.orEmpty()
                                                            if (tid == null) {
                                                                "找不到目標標籤「$tName」。"
                                                            } else {
                                                                withContext(Dispatchers.IO) {
                                                                    val tDao = AppDatabaseHolder.db(appCtx).tagDao()
                                                                    if (actState.tagOperation == "remove") {
                                                                        actState.messages.forEach { m ->
                                                                            tDao.removeTagFromMessage(m.id, tid)
                                                                        }
                                                                    } else {
                                                                        actState.messages.forEach { m ->
                                                                            tDao.addTagToMessage(
                                                                                MessageTagCrossRef(
                                                                                    messageId = m.id,
                                                                                    tagId = tid,
                                                                                    source = MessageTagCrossRef.SOURCE_MANUAL
                                                                                )
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                                val verb = if (actState.tagOperation == "remove") "移除" else "加上"
                                                                "已為 ${actState.messages.size} 則訊息${verb}標籤「$tName」。"
                                                            }
                                                        }
                                                        else -> "未知操作。"
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("PhantomChatAction", "Action ${actState.action} failed sid=$sid", e)
                                                    "⚠️ 執行失敗（${e.message ?: "未知錯誤"}），請重試或開啟原 App 操作。"
                                                }
                                                try {
                                                    withContext(Dispatchers.IO) {
                                                        AppDatabaseHolder.db(appCtx).chatDao().insertMessage(
                                                            ChatMessageEntity(
                                                                sessionId = sid,
                                                                role = ChatMessageEntity.ROLE_ASSISTANT,
                                                                content = reportText,
                                                                createdAt = System.currentTimeMillis()
                                                            )
                                                        )
                                                    }
                                                    loadMessages(sid)
                                                } catch (e: Exception) {
                                                    Log.e("PhantomChatAction", "Failed to save action report sid=$sid", e)
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            when (actState.action) {
                                                "reply_message" -> "確認送出"
                                                "open_app" -> "開啟 App"
                                                "delete_message" -> "確認刪除"
                                                else -> "確認執行"
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    pendingQuickCommandQueue.firstOrNull()?.takeIf { it.sessionId == currentSessionId }?.let { pending ->
                        item(key = "pending_quick_command_card_${pending.proposal.label}_${pending.proposal.prompt}") {
                            var selectedMode by remember(pending) { mutableStateOf(pending.defaultMode) }
                            PhantomGlassCard(
                                glassState = glassState,
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                contentPadding = 12.dp
                            ) {
                                Text(
                                    text = "新增常用指令",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "名稱：${pending.proposal.label}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "內容：${pending.proposal.prompt}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = selectedMode == PhantomPrefs.QuickChatCommand.MODE_DIRECT,
                                        onClick = { selectedMode = PhantomPrefs.QuickChatCommand.MODE_DIRECT },
                                        label = { Text("直接執行") }
                                    )
                                    FilterChip(
                                        selected = selectedMode == PhantomPrefs.QuickChatCommand.MODE_PREFILL,
                                        onClick = { selectedMode = PhantomPrefs.QuickChatCommand.MODE_PREFILL },
                                        label = { Text("預輸入") }
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            quickCommands = PhantomPrefs.addQuickChatCommand(
                                                appCtx,
                                                pending.proposal.label,
                                                pending.proposal.prompt,
                                                selectedMode
                                            )
                                            pendingQuickCommandQueue = pendingQuickCommandQueue.drop(1)
                                            onShowMessage("已加入常用指令「${pending.proposal.label}」")
                                        }
                                    ) {
                                        Text("確認加入")
                                    }
                                    OutlinedButton(
                                        onClick = { pendingQuickCommandQueue = pendingQuickCommandQueue.drop(1) }
                                    ) {
                                        Text("取消")
                                    }
                                }
                            }
                        }
                    }

                    val sessionPendingCalendars = pendingCalendarQueue.filter { it.sessionId == currentSessionId }
                    sessionPendingCalendars.firstOrNull()?.let { pendingCal ->
                        item(key = "pending_calendar_card") {
                            val startFormatted = remember(pendingCal.startMs) {
                                PhantomPrefs.formatMessageTime(appCtx, pendingCal.startMs, includeDate = true)
                            }
                            val endFormatted = remember(pendingCal.endMs) {
                                PhantomPrefs.formatMessageTime(appCtx, pendingCal.endMs, includeDate = false)
                            }
                            var selectedCalId by remember(pendingCal) {
                                mutableStateOf(pendingCal.writableCalendars.firstOrNull()?.id)
                            }
                            val hasWritePerm = remember(pendingCal) {
                                com.phantom.checker.util.CalendarBridge.hasWritePermission(appCtx)
                            }
                            PhantomGlassCard(
                                glassState = glassState,
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                contentPadding = 12.dp
                            ) {
                                Text(
                                    text = if (sessionPendingCalendars.size > 1) {
                                        "📅 新增本機日曆行程（尚有 ${sessionPendingCalendars.size} 筆）"
                                    } else {
                                        "📅 新增本機日曆行程"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "行程：${pendingCal.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (pendingCal.allDay) {
                                        "時間：$startFormatted（全天）"
                                    } else {
                                        "時間：$startFormatted – $endFormatted"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!pendingCal.location.isNullOrBlank()) {
                                    Text(
                                        text = "地點：${pendingCal.location}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (pendingCal.writableCalendars.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "目標日曆：",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        pendingCal.writableCalendars.forEach { cal ->
                                            FilterChip(
                                                selected = selectedCalId == cal.id,
                                                onClick = { selectedCalId = cal.id },
                                                label = { Text(cal.displayName, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                }
                                if (pendingCal.conflicts.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "⚠ 與既有行程撞期：${com.phantom.checker.util.CalendarBridge.formatConflictSummary(pendingCal.conflicts)}（仍可選擇照寫）",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val sid = pendingCal.sessionId
                                            pendingCalendarQueue = pendingCalendarQueue.filterNot { it === pendingCal }
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    AppDatabaseHolder.db(appCtx).chatDao().insertMessage(
                                                        ChatMessageEntity(
                                                            sessionId = sid,
                                                            role = ChatMessageEntity.ROLE_ASSISTANT,
                                                            content = "已取消新增日曆行程「${pendingCal.title}」。",
                                                            createdAt = System.currentTimeMillis()
                                                        )
                                                    )
                                                }
                                                loadMessages(sid)
                                            }
                                        }
                                    ) {
                                        Text("取消")
                                    }
                                    Button(
                                        onClick = {
                                            val sid = pendingCal.sessionId
                                            val targetCalId = selectedCalId
                                            pendingCalendarQueue = pendingCalendarQueue.filterNot { it === pendingCal }
                                            if (hasWritePerm && targetCalId != null) {
                                                scope.launch {
                                                    val res = com.phantom.checker.util.CalendarBridge.insertEvent(
                                                        ctx = appCtx,
                                                        calendarId = targetCalId,
                                                        title = pendingCal.title,
                                                        startMs = pendingCal.startMs,
                                                        endMs = pendingCal.endMs,
                                                        allDay = pendingCal.allDay,
                                                        location = pendingCal.location,
                                                        description = pendingCal.notes
                                                    )
                                                    val replyText = res.fold(
                                                        onSuccess = {
                                                            "📅 已寫入本機日曆：「${pendingCal.title}」（$startFormatted）。"
                                                        },
                                                        onFailure = { err ->
                                                            "寫入日曆失敗：${err.localizedMessage ?: "未知錯誤"}。"
                                                        }
                                                    )
                                                    withContext(Dispatchers.IO) {
                                                        AppDatabaseHolder.db(appCtx).chatDao().insertMessage(
                                                            ChatMessageEntity(
                                                                sessionId = sid,
                                                                role = ChatMessageEntity.ROLE_ASSISTANT,
                                                                content = replyText,
                                                                createdAt = System.currentTimeMillis()
                                                            )
                                                        )
                                                    }
                                                    loadMessages(sid)
                                                }
                                            } else {
                                                // 無 WRITE_CALENDAR 權限或無可寫日曆：以 ACTION_INSERT Intent 預填開啟系統日曆 App
                                                runCatching {
                                                    val intent = com.phantom.checker.util.CalendarBridge.buildInsertEventIntent(
                                                        title = pendingCal.title,
                                                        startMs = pendingCal.startMs,
                                                        endMs = pendingCal.endMs,
                                                        allDay = pendingCal.allDay,
                                                        location = pendingCal.location,
                                                        description = pendingCal.notes
                                                    )
                                                    ctx.startActivity(intent)
                                                    onShowMessage("已預填行程並開啟日曆 App")
                                                }.onFailure {
                                                    onShowMessage("裝置找不到可用的日曆 App")
                                                }
                                            }
                                        }
                                    ) {
                                        Text(
                                            if (hasWritePerm && selectedCalId != null) {
                                                if (pendingCal.conflicts.isNotEmpty()) "仍要寫入日曆" else "確認寫入日曆"
                                            } else {
                                                "於日曆 App 預填"
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val sessionPendingTodos = pendingTodoQueue.filter { it.sessionId == currentSessionId }
                    sessionPendingTodos.firstOrNull()?.let { pendingTodo ->
                        item(key = "pending_todo_card") {
                            val dueFormatted = remember(pendingTodo.dueAt) {
                                PhantomPrefs.formatMessageTime(appCtx, pendingTodo.dueAt, includeDate = true)
                            }
                            PhantomGlassCard(
                                glassState = glassState,
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                contentPadding = 12.dp
                            ) {
                                Text(
                                    text = if (sessionPendingTodos.size > 1) {
                                        "新增待辦提醒（尚有 ${sessionPendingTodos.size} 筆）"
                                    } else {
                                        "新增待辦提醒"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "事項：${pendingTodo.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "提醒時間：$dueFormatted",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { editingTodoTimeTarget = pendingTodo }
                                    ) {
                                        Text("修改時間")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val sid = pendingTodo.sessionId
                                            pendingTodoQueue = pendingTodoQueue.filterNot { it === pendingTodo }
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    AppDatabaseHolder.db(appCtx).chatDao().insertMessage(
                                                        ChatMessageEntity(
                                                            sessionId = sid,
                                                            role = ChatMessageEntity.ROLE_ASSISTANT,
                                                            content = "已取消新增待辦「${pendingTodo.title}」。",
                                                            createdAt = System.currentTimeMillis()
                                                        )
                                                    )
                                                }
                                                loadMessages(sid)
                                            }
                                        }
                                    ) {
                                        Text("取消")
                                    }
                                    Button(
                                        onClick = {
                                            val sid = pendingTodo.sessionId
                                            pendingTodoQueue = pendingTodoQueue.filterNot { it === pendingTodo }
                                            scope.launch {
                                                val verifiedOk = withContext(Dispatchers.IO) {
                                                    val db = AppDatabaseHolder.db(appCtx)
                                                    val todoId = db.todoDao().insert(
                                                        TodoEntity(
                                                            title = pendingTodo.title,
                                                            dueAt = pendingTodo.dueAt,
                                                            sourceSessionId = sid,
                                                            priority = pendingTodo.priority,
                                                            notes = pendingTodo.notes,
                                                            repeatRule = pendingTodo.repeatRule
                                                        )
                                                    )
                                                    val verified = db.todoDao().byId(todoId)
                                                    val ok = todoId > 0L && verified != null
                                                    if (ok) {
                                                        ReminderScheduler.scheduleTodo(appCtx, todoId, pendingTodo.dueAt)
                                                        ReminderWidgetProvider.updateAllIfChanged(appCtx)
                                                    }
                                                    db.chatDao().insertMessage(
                                                        ChatMessageEntity(
                                                            sessionId = sid,
                                                            role = ChatMessageEntity.ROLE_ASSISTANT,
                                                            content = if (ok) {
                                                                "✓ 已建立待辦提醒 #$todoId：「${pendingTodo.title}」（$dueFormatted，已寫入並驗證）。"
                                                            } else {
                                                                "✗ 建立待辦提醒失敗：「${pendingTodo.title}」（DB 寫入驗證失敗）。"
                                                            },
                                                            createdAt = System.currentTimeMillis()
                                                        )
                                                    )
                                                    ok
                                                }
                                                loadMessages(sid)
                                                onShowMessage(if (verifiedOk) "已排程待辦提醒：$dueFormatted" else "待辦寫入失敗")
                                            }
                                        }
                                    ) {
                                        Text("確認新增")
                                    }
                                }
                            }
                        }
                    }

                    searchResultCard?.takeIf { it.sessionId == currentSessionId }?.let { srCard ->
                        item(key = "search_result_card") {
                            PhantomGlassCard(
                                glassState = glassState,
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                contentPadding = 12.dp
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "搜尋結果（${srCard.results.size} 筆 · ${srCard.querySummary}）",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    TextButton(onClick = { searchResultCard = null }) {
                                        Text("關閉")
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    srCard.results.forEach { msg ->
                                        val timeStr = PhantomPrefs.formatMessageTime(appCtx, msg.timestamp, includeDate = true)
                                        val snippet = msg.content.replace("\n", " ").trim().take(60)
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    ctx.startActivity(MessageDetailActivity.createIntent(ctx, msg.id))
                                                }
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "${msg.appLabel.ifBlank { msg.packageName }} · ${msg.sender.ifBlank { "未知寄件者" }}",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = timeStr,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = snippet,
                                                    style = MaterialTheme.typography.bodySmall,
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

                    val sessionExtActions = pendingExtendedActionQueue.filter { it.sessionId == currentSessionId }
                    sessionExtActions.firstOrNull()?.let { ext ->
                        item(key = "pending_extended_action_card") {
                            val titleText = when (ext) {
                                is PendingExtendedAction.TodoDone -> "確認標記待辦完成"
                                is PendingExtendedAction.TodoDelete -> "確認刪除待辦"
                                is PendingExtendedAction.TodoEdit -> "確認修改待辦"
                                is PendingExtendedAction.VipPin -> if (ext.pin) "確認釘選 VIP 寄件者" else "確認取消 VIP 釘選"
                                is PendingExtendedAction.MuteApp -> if (ext.mute) "確認靜音 App" else "確認取消靜音 App"
                                is PendingExtendedAction.MarkRead -> "確認標記已讀"
                                is PendingExtendedAction.ExportMessages -> "確認匯出訊息 CSV"
                                is PendingExtendedAction.WhitelistDelete -> "確認移出白名單（共 ${ext.rules.size} 筆）"
                            }
                            val bodyText = when (ext) {
                                is PendingExtendedAction.TodoDone -> "將「${ext.todo.title}」標記為已完成並取消提醒排程。"
                                is PendingExtendedAction.TodoDelete -> "確定要刪除待辦「${ext.todo.title}」嗎？"
                                is PendingExtendedAction.TodoEdit -> {
                                    val oldTime = PhantomPrefs.formatMessageTime(appCtx, ext.original.dueAt, includeDate = true)
                                    val newTime = PhantomPrefs.formatMessageTime(appCtx, ext.newDueAt, includeDate = true)
                                    "事項：${ext.original.title} → ${ext.newTitle}\n時間：$oldTime → $newTime"
                                }
                                is PendingExtendedAction.VipPin -> "${ext.appLabel} · ${ext.sender}"
                                is PendingExtendedAction.MuteApp -> "${ext.appLabel}（${ext.packageName}）"
                                is PendingExtendedAction.MarkRead -> "範圍：${ext.scopeLabel}"
                                is PendingExtendedAction.ExportMessages -> "範圍：${ext.scopeLabel}\n將匯出至 Downloads 資料夾（含公式注入防護）。"
                                is PendingExtendedAction.WhitelistDelete -> "確定要將以下規則移出白名單嗎？\n${ext.summaryText}"
                            }
                            PhantomGlassCard(
                                glassState = glassState,
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                contentPadding = 12.dp
                            ) {
                                Text(
                                    text = if (sessionExtActions.size > 1) "$titleText（尚有 ${sessionExtActions.size} 項）" else titleText,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = bodyText,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val sid = ext.sessionId
                                            pendingExtendedActionQueue = pendingExtendedActionQueue.filterNot { it === ext }
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    AppDatabaseHolder.db(appCtx).chatDao().insertMessage(
                                                        ChatMessageEntity(
                                                            sessionId = sid,
                                                            role = ChatMessageEntity.ROLE_ASSISTANT,
                                                            content = "已取消操作（$titleText）。",
                                                            createdAt = System.currentTimeMillis()
                                                        )
                                                    )
                                                }
                                                loadMessages(sid)
                                            }
                                        }
                                    ) {
                                        Text("取消")
                                    }
                                    Button(
                                        onClick = {
                                            val sid = ext.sessionId
                                            pendingExtendedActionQueue = pendingExtendedActionQueue.filterNot { it === ext }
                                            scope.launch {
                                                val resultMsg = withContext(Dispatchers.IO) {
                                                    val db = AppDatabaseHolder.db(appCtx)
                                                    when (ext) {
                                                        is PendingExtendedAction.TodoDone -> {
                                                            val spawned = ReminderScheduler.completeTodoWithRecurrence(appCtx, ext.todo, true)
                                                            val verified = db.todoDao().byId(ext.todo.id)
                                                            if (verified?.isDone != true) {
                                                                "✗ 標記待辦完成失敗：「${ext.todo.title}」（DB 狀態未更新）。"
                                                            } else if (spawned != null) {
                                                                "✓ 已完成待辦「${ext.todo.title}」（已驗證），並已自動排程下一次重複待辦。"
                                                            } else {
                                                                "✓ 已完成待辦「${ext.todo.title}」（已驗證）。"
                                                            }
                                                        }
                                                        is PendingExtendedAction.TodoDelete -> {
                                                            db.todoDao().deleteById(ext.todo.id)
                                                            val stillExists = db.todoDao().byId(ext.todo.id) != null
                                                            if (!stillExists) {
                                                                ReminderScheduler.cancelTodo(appCtx, ext.todo.id)
                                                                ReminderWidgetProvider.updateAllIfChanged(appCtx)
                                                                "✓ 已刪除待辦「${ext.todo.title}」（已驗證刪除）。"
                                                            } else {
                                                                "✗ 刪除待辦失敗：「${ext.todo.title}」（DB 仍存在）。"
                                                            }
                                                        }
                                                        is PendingExtendedAction.TodoEdit -> {
                                                            val updated = ext.original.copy(
                                                                title = ext.newTitle,
                                                                dueAt = ext.newDueAt
                                                            )
                                                            db.todoDao().update(updated)
                                                            val verified = db.todoDao().byId(updated.id)
                                                            if (verified?.title == ext.newTitle && verified.dueAt == ext.newDueAt) {
                                                                ReminderScheduler.cancelTodo(appCtx, updated.id)
                                                                if (!updated.isDone && updated.dueAt > System.currentTimeMillis()) {
                                                                    ReminderScheduler.scheduleTodo(appCtx, updated.id, updated.dueAt)
                                                                }
                                                                ReminderWidgetProvider.updateAllIfChanged(appCtx)
                                                                val newTimeStr = PhantomPrefs.formatMessageTime(appCtx, updated.dueAt, includeDate = true)
                                                                "✓ 已更新待辦：「${updated.title}」（$newTimeStr，已驗證）。"
                                                            } else {
                                                                "✗ 更新待辦失敗：「${ext.original.title}」（DB 驗證不符）。"
                                                            }
                                                        }
                                                        is PendingExtendedAction.VipPin -> {
                                                            val isCurrentlyVip = ext.sender in PhantomPrefs.getVipSenders(appCtx, ext.packageName)
                                                            if (isCurrentlyVip != ext.pin) {
                                                                PhantomPrefs.toggleVipSender(appCtx, ext.packageName, ext.sender)
                                                            }
                                                            if (ext.pin) "已將「${ext.sender}」（${ext.appLabel}）釘選為 VIP。"
                                                            else "已取消「${ext.sender}」（${ext.appLabel}）的 VIP 釘選。"
                                                        }
                                                        is PendingExtendedAction.MuteApp -> {
                                                            PhantomPrefs.setAppMuted(appCtx, ext.packageName, ext.mute)
                                                            if (ext.mute) "已靜音「${ext.appLabel}」。"
                                                            else "已取消靜音「${ext.appLabel}」。"
                                                        }
                                                        is PendingExtendedAction.MarkRead -> {
                                                            val count = when (ext.scope) {
                                                                "all" -> db.messageDao().markAllNonScamRead()
                                                                "app" -> ext.packageName?.let { db.messageDao().markNonScamReadByApp(it) } ?: 0
                                                                else -> {
                                                                    val cal = java.util.Calendar.getInstance().apply {
                                                                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                                                                        set(java.util.Calendar.MINUTE, 0)
                                                                        set(java.util.Calendar.SECOND, 0)
                                                                        set(java.util.Calendar.MILLISECOND, 0)
                                                                    }
                                                                    db.messageDao().markNonScamReadSince(cal.timeInMillis)
                                                                }
                                                            }
                                                            StatsForegroundService.startOrRefresh(appCtx, force = true)
                                                            "已將 $count 則非詐騙訊息標記為已讀（詐騙紀錄維持不變）。"
                                                        }
                                                        is PendingExtendedAction.ExportMessages -> {
                                                            val res = com.phantom.checker.util.MessageExporter.exportToDownloads(
                                                                ctx = appCtx,
                                                                packageName = ext.packageName,
                                                                daysBack = ext.days
                                                            )
                                                            res.fold(
                                                                onSuccess = { fileName -> "已匯出 CSV 至 Downloads/$fileName。" },
                                                                onFailure = { err -> "匯出失敗：${err.message ?: "未知錯誤"}" }
                                                            )
                                                        }
                                                        is PendingExtendedAction.WhitelistDelete -> {
                                                            val lines = ext.rules.map { r ->
                                                                db.aiRuleDao().deleteById(r.id)
                                                                val stillExists = db.aiRuleDao().byId(r.id) != null
                                                                val label = r.sender?.takeIf { it.isNotBlank() } ?: r.ruleText.take(40)
                                                                if (!stillExists) {
                                                                    "✓ 已移出白名單：#${r.id} $label（已驗證刪除）"
                                                                } else {
                                                                    "✗ 移出失敗：#${r.id} $label（DB 仍存在）"
                                                                }
                                                            }
                                                            NotificationCollectorService.invalidateRuleCaches()
                                                            lines.joinToString("\n")
                                                        }
                                                    }
                                                }
                                                withContext(Dispatchers.IO) {
                                                    AppDatabaseHolder.db(appCtx).chatDao().insertMessage(
                                                        ChatMessageEntity(
                                                            sessionId = sid,
                                                            role = ChatMessageEntity.ROLE_ASSISTANT,
                                                            content = resultMsg,
                                                            createdAt = System.currentTimeMillis()
                                                        )
                                                    )
                                                }
                                                loadMessages(sid)
                                                onRefresh()
                                                onShowMessage(resultMsg)
                                            }
                                        }
                                    ) {
                                        Text("確認執行")
                                    }
                                }
                            }
                        }
                    }

                    val sessionMemWrites = pendingMemoryWriteQueue.filter { it.sessionId == currentSessionId }
                    sessionMemWrites.firstOrNull()?.let { memWrite ->
                        item(key = "pending_memory_write_card_${memWrite.subject}_${memWrite.content.hashCode()}") {
                            val hasConflict = memWrite.conflictingMemory != null
                            val catLbl = com.phantom.checker.data.MemoryEngine.categoryLabel(memWrite.category)
                            val layerLbl = com.phantom.checker.data.MemoryEngine.layerLabel(memWrite.layer)
                            val privLbl = com.phantom.checker.data.MemoryEngine.privacyLabel(memWrite.privacyTier)
                            val expStr = memWrite.expiresAt?.let { "（到期：${formatChatTime(it)}）" }.orEmpty()
                            PhantomGlassCard(
                                glassState = glassState,
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                contentPadding = 12.dp
                            ) {
                                Text(
                                    text = when {
                                        hasConflict -> "⚠️ 偵測到記憶衝突（同主題「${memWrite.subject}」內容不同）"
                                        memWrite.isUpdate -> "確認更新記憶"
                                        else -> "確認新增記憶（AI 推測）"
                                    } + if (sessionMemWrites.size > 1) "（尚有 ${sessionMemWrites.size} 筆）" else "",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasConflict) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "分類：$catLbl ｜ 層級：$layerLbl$expStr ｜ 隱私：$privLbl",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "主題：${memWrite.subject}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (hasConflict) {
                                    val oldMem = memWrite.conflictingMemory!!
                                    Spacer(Modifier.height(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = "既有記憶（#${oldMem.id}）：${oldMem.content}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "新提議內容：${memWrite.content}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "請選擇「更新記憶」（將舊筆封存並標記為已確認）或「保留原樣」（保留舊筆並標記為已確認，也可以稍後至「記憶」分頁衝突區裁定）：",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = "內容：${memWrite.content}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Text(
                                    text = "來源：${memWrite.sourceRef}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(10.dp))
                                if (hasConflict) {
                                    val oldMem = memWrite.conflictingMemory!!
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val sid = memWrite.sessionId
                                                pendingMemoryWriteQueue = pendingMemoryWriteQueue.filterNot { it === memWrite }
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        val db = AppDatabaseHolder.db(appCtx)
                                                        db.memoryDao().update(
                                                            oldMem.copy(
                                                                status = com.phantom.checker.data.MemoryEntity.STATUS_ACTIVE,
                                                                confidence = com.phantom.checker.data.MemoryEntity.CONFIDENCE_CONFIRMED,
                                                                updatedAt = System.currentTimeMillis()
                                                            )
                                                        )
                                                        val oldContentDisplay = if (oldMem.privacyTier == com.phantom.checker.data.MemoryEntity.PRIVACY_SENSITIVE) {
                                                            "🔒敏感（內容僅本機可見）"
                                                        } else {
                                                            oldMem.content
                                                        }
                                                        db.chatDao().insertMessage(
                                                            ChatMessageEntity(
                                                                sessionId = sid,
                                                                role = ChatMessageEntity.ROLE_ASSISTANT,
                                                                content = "✓ 已選擇「保留原樣」：維持既有記憶 #${oldMem.id}「${oldMem.subject}：$oldContentDisplay」（已標記為已確認）。",
                                                                createdAt = System.currentTimeMillis()
                                                            )
                                                        )
                                                    }
                                                    loadMessages(sid)
                                                    onShowMessage("已保留原記憶")
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("保留原樣")
                                        }
                                        Button(
                                            onClick = {
                                                val sid = memWrite.sessionId
                                                pendingMemoryWriteQueue = pendingMemoryWriteQueue.filterNot { it === memWrite }
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        val db = AppDatabaseHolder.db(appCtx)
                                                        val outcome = com.phantom.checker.data.MemoryEngine.writeMemoryWithConflictDetection(
                                                            db = db,
                                                            category = memWrite.category,
                                                            subject = memWrite.subject,
                                                            content = memWrite.content,
                                                            layer = memWrite.layer,
                                                            expiresAt = memWrite.expiresAt,
                                                            privacyTier = memWrite.privacyTier,
                                                            confidence = com.phantom.checker.data.MemoryEntity.CONFIDENCE_CONFIRMED,
                                                            sourceType = com.phantom.checker.data.MemoryEntity.SOURCE_CHAT,
                                                            sourceRef = memWrite.sourceRef,
                                                            forceReplaceConflict = true
                                                        )
                                                        val (savedId, savedPrivacyTier) = when (outcome) {
                                                            is com.phantom.checker.data.MemoryEngine.WriteOutcome.Inserted -> outcome.memory.id to outcome.memory.privacyTier
                                                            is com.phantom.checker.data.MemoryEngine.WriteOutcome.Deduplicated -> outcome.memory.id to outcome.memory.privacyTier
                                                            is com.phantom.checker.data.MemoryEngine.WriteOutcome.ConflictDetected -> outcome.incomingMemory.id to outcome.incomingMemory.privacyTier
                                                        }
                                                        val newContentDisplay = if (memWrite.privacyTier == com.phantom.checker.data.MemoryEntity.PRIVACY_SENSITIVE ||
                                                            savedPrivacyTier == com.phantom.checker.data.MemoryEntity.PRIVACY_SENSITIVE
                                                        ) {
                                                            "🔒敏感（內容僅本機可見）"
                                                        } else {
                                                            memWrite.content
                                                        }
                                                        db.chatDao().insertMessage(
                                                            ChatMessageEntity(
                                                                sessionId = sid,
                                                                role = ChatMessageEntity.ROLE_ASSISTANT,
                                                                content = "✓ 已選擇「更新記憶」：新記憶 #$savedId「${memWrite.subject}：$newContentDisplay」（已確認，舊筆 #${oldMem.id} 已封存）。",
                                                                createdAt = System.currentTimeMillis()
                                                            )
                                                        )
                                                    }
                                                    loadMessages(sid)
                                                    if (memWrite.category == com.phantom.checker.data.MemoryEntity.CATEGORY_SCAM_IMMUNITY &&
                                                        onShowActionMessage != null && onOpenMemoryWithFilter != null
                                                    ) {
                                                        onShowActionMessage("已更新防詐免疫記憶", "查看") {
                                                            onOpenMemoryWithFilter(com.phantom.checker.data.MemoryEntity.CATEGORY_SCAM_IMMUNITY)
                                                        }
                                                    } else {
                                                        onShowMessage("已更新記憶並封存舊版本")
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("更新記憶")
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        TextButton(
                                            onClick = {
                                                val sid = memWrite.sessionId
                                                pendingMemoryWriteQueue = pendingMemoryWriteQueue.filterNot { it === memWrite }
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        val db = AppDatabaseHolder.db(appCtx)
                                                        com.phantom.checker.data.MemoryEngine.writeMemoryWithConflictDetection(
                                                            db = db,
                                                            category = memWrite.category,
                                                            subject = memWrite.subject,
                                                            content = memWrite.content,
                                                            layer = memWrite.layer,
                                                            expiresAt = memWrite.expiresAt,
                                                            privacyTier = memWrite.privacyTier,
                                                            confidence = com.phantom.checker.data.MemoryEntity.CONFIDENCE_INFERRED,
                                                            sourceType = com.phantom.checker.data.MemoryEntity.SOURCE_CHAT,
                                                            sourceRef = memWrite.sourceRef,
                                                            forceReplaceConflict = false
                                                        )
                                                        db.chatDao().insertMessage(
                                                            ChatMessageEntity(
                                                                sessionId = sid,
                                                                role = ChatMessageEntity.ROLE_ASSISTANT,
                                                                content = "已將衝突記憶移至「記憶」分頁的「衝突待確認」區塊，您可隨時前往裁定。",
                                                                createdAt = System.currentTimeMillis()
                                                            )
                                                        )
                                                    }
                                                    loadMessages(sid)
                                                    onShowMessage("已移至記憶頁衝突待確認區")
                                                }
                                            }
                                        ) {
                                            Text("移至記憶頁稍後裁定")
                                        }
                                        TextButton(
                                            onClick = {
                                                pendingMemoryWriteQueue = pendingMemoryWriteQueue.filterNot { it === memWrite }
                                            }
                                        ) {
                                            Text("略過不寫入")
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val sid = memWrite.sessionId
                                                pendingMemoryWriteQueue = pendingMemoryWriteQueue.filterNot { it === memWrite }
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        AppDatabaseHolder.db(appCtx).chatDao().insertMessage(
                                                            ChatMessageEntity(
                                                                sessionId = sid,
                                                                role = ChatMessageEntity.ROLE_ASSISTANT,
                                                                content = "已取消記住「${memWrite.subject}」。",
                                                                createdAt = System.currentTimeMillis()
                                                            )
                                                        )
                                                    }
                                                    loadMessages(sid)
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("不符／取消")
                                        }
                                        Button(
                                            onClick = {
                                                val sid = memWrite.sessionId
                                                pendingMemoryWriteQueue = pendingMemoryWriteQueue.filterNot { it === memWrite }
                                                scope.launch {
                                                    val report = withContext(Dispatchers.IO) {
                                                        val db = AppDatabaseHolder.db(appCtx)
                                                        val outcome = com.phantom.checker.data.MemoryEngine.writeMemoryWithConflictDetection(
                                                            db = db,
                                                            category = memWrite.category,
                                                            subject = memWrite.subject,
                                                            content = memWrite.content,
                                                            layer = memWrite.layer,
                                                            expiresAt = memWrite.expiresAt,
                                                            privacyTier = memWrite.privacyTier,
                                                            confidence = com.phantom.checker.data.MemoryEntity.CONFIDENCE_INFERRED,
                                                            sourceType = com.phantom.checker.data.MemoryEntity.SOURCE_CHAT,
                                                            sourceRef = memWrite.sourceRef,
                                                            forceReplaceConflict = false
                                                        )
                                                        when (outcome) {
                                                            is com.phantom.checker.data.MemoryEngine.WriteOutcome.Inserted -> {
                                                                val contentDisplay = if (outcome.memory.privacyTier == com.phantom.checker.data.MemoryEntity.PRIVACY_SENSITIVE) {
                                                                    "🔒敏感（內容僅本機可見）"
                                                                } else {
                                                                    outcome.memory.content
                                                                }
                                                                "✓ 已記住 #${outcome.memory.id} [$catLbl/$layerLbl] ${outcome.memory.subject}：$contentDisplay"
                                                            }
                                                            is com.phantom.checker.data.MemoryEngine.WriteOutcome.Deduplicated ->
                                                                "✓ 記憶庫已有相同內容 #${outcome.memory.id}，已更新時間。"
                                                            is com.phantom.checker.data.MemoryEngine.WriteOutcome.ConflictDetected ->
                                                                "⚠️ 寫入時偵測到與既有記憶 #${outcome.existingMemory.id} 衝突，兩筆皆已標為「衝突待確認」，請至「記憶」分頁裁定。"
                                                        }
                                                    }
                                                    withContext(Dispatchers.IO) {
                                                        AppDatabaseHolder.db(appCtx).chatDao().insertMessage(
                                                            ChatMessageEntity(
                                                                sessionId = sid,
                                                                role = ChatMessageEntity.ROLE_ASSISTANT,
                                                                content = report,
                                                                createdAt = System.currentTimeMillis()
                                                            )
                                                        )
                                                    }
                                                    loadMessages(sid)
                                                    if (memWrite.category == com.phantom.checker.data.MemoryEntity.CATEGORY_SCAM_IMMUNITY &&
                                                        onShowActionMessage != null && onOpenMemoryWithFilter != null
                                                    ) {
                                                        onShowActionMessage("已加入防詐免疫記憶", "查看") {
                                                            onOpenMemoryWithFilter(com.phantom.checker.data.MemoryEntity.CATEGORY_SCAM_IMMUNITY)
                                                        }
                                                    } else {
                                                        onShowMessage("已寫入記憶庫")
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("確認記住")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    pendingCrossAppQueue.firstOrNull()?.let { crossAct ->
                        item(key = "pending_cross_app_${crossAct.hashCode()}") {
                            PhantomGlassCard(
                                glassState = glassState,
                                cornerRadius = 16.dp,
                                contentPadding = 12.dp
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "${crossAct.title()}（確認後執行）",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = crossAct.summary(),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "系統不會自動執行此操作；請確認參數無誤後點擊「執行」。",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                pendingCrossAppQueue = pendingCrossAppQueue.drop(1)
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("取消")
                                        }
                                        Button(
                                            onClick = {
                                                pendingCrossAppQueue = pendingCrossAppQueue.drop(1)
                                                com.phantom.checker.util.CrossAppActionExecutor.execute(ctx, crossAct)
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("執行")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    pendingAliasCandidate?.let { aliasCand ->
                        item(key = "pending_alias_bind_${aliasCand.alias}_${aliasCand.matchedDisplayName}") {
                            PhantomGlassCard(
                                glassState = glassState,
                                cornerRadius = 16.dp,
                                contentPadding = 12.dp
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "👤 疑似通訊錄聯絡人暱稱（純本機比對）",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "寄件者「${aliasCand.alias}」疑似本機聯絡人「${aliasCand.matchedDisplayName}」。綁定後防詐檢查將視其為熟人寄件者。",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { pendingAliasCandidate = null },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("略過")
                                        }
                                        Button(
                                            onClick = {
                                                pendingAliasCandidate = null
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        AppDatabaseHolder.db(appCtx).contactAliasDao().insert(
                                                            com.phantom.checker.data.ContactAliasEntity(
                                                                alias = aliasCand.alias,
                                                                displayName = aliasCand.matchedDisplayName,
                                                                packageName = aliasCand.packageName,
                                                                note = "由聊天確認卡綁定"
                                                            )
                                                        )
                                                    }
                                                    com.phantom.checker.util.ContactContext.invalidateCache()
                                                    onShowMessage("已綁定暱稱「${aliasCand.alias} → ${aliasCand.matchedDisplayName}」")
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("確認綁定")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val sessionMemDeletes = pendingMemoryDeleteQueue.filter { it.sessionId == currentSessionId }
                    sessionMemDeletes.firstOrNull()?.let { memDel ->
                        item(key = "pending_memory_delete_card_${memDel.filterSummary}") {
                            var selectedIds by remember(memDel) {
                                mutableStateOf(memDel.targets.map { it.id }.toSet())
                            }
                            PhantomGlassCard(
                                glassState = glassState,
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                contentPadding = 12.dp
                            ) {
                                Text(
                                    text = "確認刪除記憶（${memDel.filterSummary} · 共 ${memDel.targets.size} 筆）",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(6.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    memDel.targets.take(10).forEach { m ->
                                        val checked = m.id in selectedIds
                                        val catLbl = com.phantom.checker.data.MemoryEngine.categoryLabel(m.category)
                                        val layerLbl = com.phantom.checker.data.MemoryEngine.layerLabel(m.layer)
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedIds = if (checked) selectedIds - m.id else selectedIds + m.id
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Checkbox(
                                                    checked = checked,
                                                    onCheckedChange = { isChecked ->
                                                        selectedIds = if (isChecked) selectedIds + m.id else selectedIds - m.id
                                                    }
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "#${m.id} [$catLbl/$layerLbl] ${m.subject}",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = m.content,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (memDel.targets.size > 10) {
                                        Text(
                                            text = "…另有 ${memDel.targets.size - 10} 筆記憶",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val sid = memDel.sessionId
                                            pendingMemoryDeleteQueue = pendingMemoryDeleteQueue.filterNot { it === memDel }
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    AppDatabaseHolder.db(appCtx).chatDao().insertMessage(
                                                        ChatMessageEntity(
                                                            sessionId = sid,
                                                            role = ChatMessageEntity.ROLE_ASSISTANT,
                                                            content = "已取消刪除記憶（${memDel.filterSummary}）。",
                                                            createdAt = System.currentTimeMillis()
                                                        )
                                                    )
                                                }
                                                loadMessages(sid)
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("取消")
                                    }
                                    Button(
                                        enabled = selectedIds.isNotEmpty(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        onClick = {
                                            val sid = memDel.sessionId
                                            val idsToDelete = selectedIds.toList()
                                            pendingMemoryDeleteQueue = pendingMemoryDeleteQueue.filterNot { it === memDel }
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    val db = AppDatabaseHolder.db(appCtx)
                                                    db.memoryDao().deleteByIds(idsToDelete)
                                                    db.chatDao().insertMessage(
                                                        ChatMessageEntity(
                                                            sessionId = sid,
                                                            role = ChatMessageEntity.ROLE_ASSISTANT,
                                                            content = "✓ 已刪除 ${idsToDelete.size} 筆記憶（${memDel.filterSummary}）。",
                                                            createdAt = System.currentTimeMillis()
                                                        )
                                                    )
                                                }
                                                loadMessages(sid)
                                                onShowMessage("已刪除 ${idsToDelete.size} 筆記憶")
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("確認刪除（${selectedIds.size}）")
                                    }
                                }
                            }
                        }
                    }

                    if (isSending) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                PhantomGlassCard(
                                    glassState = glassState,
                                    cornerRadius = 18.dp,
                                    contentPadding = 12.dp
                                ) {
                                    TypingDotsIndicator()
                                }
                            }
                        }
                    }

                    lastError?.let { err ->
                        item {
                            PhantomGlassCard(
                                glassState = glassState,
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 16.dp,
                                contentPadding = 12.dp
                            ) {
                                Text(
                                    text = "回覆失敗：$err",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                lastFailedQuery?.let { q ->
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedButton(onClick = { sendQuery(q, isRetry = true) }) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("重試")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 圖片預覽 chip（取消後輸入框寬度完全復原）
        pendingImageAttachment?.let { attachment ->
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        bitmap = attachment.thumbnail.asImageBitmap(),
                        contentDescription = "已選擇的圖片預覽",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "已附加圖片（送出將由 AI 視覺模型檢視）",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "圖片會消耗較多 token，建議搭配簡短提問",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { pendingImageAttachment = null },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "移除圖片")
                    }
                }
            }
        }

        // 輸入列（單一收合工具圖標 + 精簡輸入框 + 送出按鈕）
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box {
                IconButton(
                    onClick = { showInputToolsMenu = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = if (showSuggestions) "常用指令與圖片上傳" else "圖片上傳"
                    )
                }
                DropdownMenu(
                    expanded = showInputToolsMenu,
                    onDismissRequest = { showInputToolsMenu = false }
                ) {
                    if (showSuggestions) {
                        DropdownMenuItem(
                            text = { Text("常用指令") },
                            leadingIcon = { Icon(AutoAwesomeIcon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            onClick = {
                                showInputToolsMenu = false
                                quickCommands = PhantomPrefs.getQuickChatCommands(appCtx)
                                showQuickCommandsDialog = true
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("上傳圖片讓 AI 檢視") },
                        leadingIcon = { Icon(PhotoIcon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        onClick = {
                            showInputToolsMenu = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                }
            }
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp),
                maxLines = 3,
                shape = RoundedCornerShape(22.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                placeholder = {
                    Text(
                        text = "詢問訊息紀錄、昨日簡報或風險…",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
            Button(
                onClick = { sendQuery(inputText) },
                enabled = (inputText.isNotBlank() || pendingImageAttachment != null) && !isSending,
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "送出", modifier = Modifier.size(20.dp))
            }
        }
        }

        val isDrawerVisible by remember(drawerWidthPx) {
            derivedStateOf {
                val raw = drawerState.offset
                val progress = if (!raw.isNaN() && drawerWidthPx > 0f) {
                    ((raw + drawerWidthPx) / drawerWidthPx).coerceIn(0f, 1f)
                } else {
                    0f
                }
                showSessionDrawer || progress > 0.005f
            }
        }
        if (isDrawerVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val raw = drawerState.offset
                        val offsetX = if (raw.isNaN()) -drawerWidthPx else raw
                        val progress = if (drawerWidthPx > 0f) {
                            ((offsetX + drawerWidthPx) / drawerWidthPx).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        drawRect(Color.Black.copy(alpha = 0.45f * progress))
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showSessionDrawer = false }
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(vertical = 10.dp)
                    .displayCutoutPadding()
                    .width(drawerWidth)
                    .fillMaxHeight()
                    .offset {
                        val raw = drawerState.offset
                        val offsetX = if (raw.isNaN()) -drawerWidthPx else raw
                        IntOffset(offsetX.roundToInt(), 0)
                    }
                    .anchoredDraggable(drawerState, Orientation.Horizontal)
            ) {
                PhantomFloatingSurface(
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 20.dp,
                    contentPadding = 14.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "歷史對話",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (sessions.isNotEmpty()) {
                                    TextButton(onClick = {
                                        if (isSending) {
                                            onShowMessage("AI 正在回覆中，請稍候再清除對話")
                                        } else {
                                            showClearAllSessionsConfirm = true
                                        }
                                    }) {
                                        Text("清除全部對話", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                TextButton(onClick = {
                                    currentSessionId = null
                                    messages = emptyList()
                                    lastError = null
                                    showSessionDrawer = false
                                }) {
                                    Text("+ 新對話")
                                }
                            }
                        }
                        if (sessions.isEmpty()) {
                            Text(
                                text = "尚無歷史對話紀錄",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(sessions, key = { it.id }) { session ->
                                    val isSelected = session.id == currentSessionId
                                    val dateStr = remember(session.createdAt) {
                                        formatChatTime(session.createdAt)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                currentSessionId = session.id
                                                lastError = null
                                                showSessionDrawer = false
                                                scope.launch { loadMessages(session.id) }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = session.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = dateStr,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(onClick = { deleteSessionConfirm = session }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "刪除會話",
                                                    tint = MaterialTheme.colorScheme.error
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
        }
    }

    if (showQuickCommandsDialog) {
        AlertDialog(
            onDismissRequest = { showQuickCommandsDialog = false },
            title = { Text("常用指令") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (quickCommands.isEmpty()) {
                        Text(
                            text = "尚未設定常用指令，可至設定頁新增或對 AI 說「加入常用指令：…」。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val defaultMode = PhantomPrefs.getQuickCommandDefaultMode(appCtx)
                        quickCommands.forEach { cmd ->
                            val resolvedMode = PhantomPrefs.resolveQuickCommandMode(cmd.mode, defaultMode)
                            val isDirect = resolvedMode == PhantomPrefs.QuickChatCommand.MODE_DIRECT
                            val isFollow = cmd.mode == PhantomPrefs.QuickChatCommand.MODE_FOLLOW
                            val modeLabel = if (isFollow) {
                                "跟隨預設・${if (isDirect) "直接執行" else "預輸入"}"
                            } else if (isDirect) {
                                "直接執行"
                            } else {
                                "預輸入"
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable {
                                        showQuickCommandsDialog = false
                                        if (isDirect) {
                                            sendQuery(cmd.prompt)
                                        } else {
                                            inputText = cmd.prompt
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cmd.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = cmd.prompt,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isDirect) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        }
                                    ) {
                                        Text(
                                            text = modeLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQuickCommandsDialog = false }) {
                    Text("關閉")
                }
            }
        )
    }
}

@Composable
fun AiProviderSwitcherChip(
    profiles: List<AiProviderProfile>,
    activeProfile: AiProviderProfile?,
    onSelectProfile: (AiProviderProfile) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "對話模型："
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (profiles.isEmpty()) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        text = "尚未設定 AI Provider",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        } else {
            val current = activeProfile ?: profiles.first()
            FilterChip(
                selected = true,
                onClick = { expanded = true },
                label = {
                    Text(
                        text = "$label${current.name}・${current.model} ▾",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                profiles.forEach { profile ->
                    val isCurrent = profile.id == current.id
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = (if (isCurrent) "✓ " else "   ") + "${profile.name}（${profile.model}）",
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelectProfile(profile)
                        }
                    )
                }
            }
        }
    }
}

