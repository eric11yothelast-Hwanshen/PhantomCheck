package com.phantom.checker.ui

import com.phantom.checker.ui.components.pressableScale
import com.phantom.checker.ui.components.phantomSheen
import com.phantom.checker.ui.components.PhantomMotion
import com.phantom.checker.ui.components.PhantomChevron
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
import androidx.compose.material.icons.filled.MoreVert
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
import com.phantom.checker.data.WhitelistRuleHelper
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
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource
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
    onOpenSettings: () -> Unit = {},
    requestedBriefingMsgId: Long? = null,
    onConsumedBriefingMsgId: () -> Unit = {}
) {
    val resCtx = LocalContext.current
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
    var webSearchEnabled by remember { mutableStateOf(false) }
    val webSearchSupported = remember(activeProfile) {
        AiClient.isGeminiGroundingSupported(activeProfile) ||
            com.phantom.checker.util.EncryptedByokStore.isExternalSearchConfigured(appCtx)
    }
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
    var pendingGoalCandidate by remember { mutableStateOf<Pair<Long, com.phantom.checker.util.GoalEngine.GoalCandidate>?>(null) }
    var searchResultCard by remember { mutableStateOf<SearchResultCardState?>(null) }
    var showUsagePermissionGuideCard by remember { mutableStateOf(false) }
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
    var proactiveLastReadAt by remember { mutableStateOf(PhantomPrefs.getProactiveChatLastReadAt(appCtx)) }
    val proactiveUnreadCount by remember(appCtx, proactiveLastReadAt) {
        AppDatabaseHolder.db(appCtx).chatDao().observeProactiveUnreadCount(
            com.phantom.checker.service.ProactiveEngine.PROACTIVE_SESSION_TITLE,
            proactiveLastReadAt
        )
    }.collectAsState(initial = 0)
    var isViewingProactiveRoom by remember { mutableStateOf(false) }
    var previousNormalSessionId by remember { mutableStateOf<Long?>(null) }
    val allMemoriesLive by remember(appCtx) {
        AppDatabaseHolder.db(appCtx).memoryDao().observeAll()
    }.collectAsState(initial = emptyList())
    var inspectAutoMemoriesMsgId by remember { mutableStateOf<Long?>(null) }
    var sensitiveAutoMemDeleteTarget by remember { mutableStateOf<com.phantom.checker.data.MemoryEntity?>(null) }
    var dismissedCapsuleIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var continuityAnchorText by remember { mutableStateOf(PhantomPrefs.getActiveChatContinuityAnchor(appCtx)) }
    var pendingCapsulesEnabled by remember { mutableStateOf(PhantomPrefs.isChatPendingCapsulesEnabled(appCtx)) }
    var briefingChannelBlocked by remember { mutableStateOf(com.phantom.checker.service.ProactiveEngine.isBriefingChannelBlocked(appCtx)) }
    var briefingChannelGuideDismissed by remember { mutableStateOf(PhantomPrefs.isBriefingChannelGuideDismissed(appCtx)) }

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
            val nowMs = System.currentTimeMillis()
            val startOfDay = NotificationCollectorService.startOfTodayMillis()
            val stats = db.messageDao().statsOverview(startOfDay)
            val topApps = db.messageDao().todayRecentApps(startOfDay)

            // #424 狀態驅動動態建議 chips（純本機條件、零 AI、零 token，最多 4 顆，不足以既有靜態 chips 補齊）
            val dynamicChips = LinkedHashSet<String>()
            if (stats.unread > 50) {
                dynamicChips.add(resCtx.getString(R.string.chat_s_bb7042))
            }
            val hasTodayConflict = if (com.phantom.checker.util.CalendarBridge.isCalendarEnabledForAi(appCtx)) {
                runCatching {
                    val endOfDay = startOfDay + 24L * 60L * 60L * 1000L
                    val timed = com.phantom.checker.util.CalendarBridge.queryInstances(appCtx, startOfDay, endOfDay)
                        .filter { !it.allDay }
                        .sortedBy { it.beginMs }
                    timed.zipWithNext().any { (a, b) -> b.beginMs < a.endMs }
                }.getOrDefault(false)
            } else false
            if (hasTodayConflict) {
                dynamicChips.add(resCtx.getString(R.string.chat_s_c586bf))
            }
            val hasHcData = runCatching {
                com.phantom.checker.util.HealthConnectBridge.hasAnyReadPermission(appCtx) &&
                    com.phantom.checker.util.HealthConnectBridge.readLast7DaysTrend(appCtx).any {
                        it.sleepHours > 0f || it.steps > 0L || it.avgHeartRateBpm > 0L || it.distanceMeters > 0.0 || it.caloriesKcal > 0.0
                    }
            }.getOrDefault(false)
            if (hasHcData) {
                dynamicChips.add(resCtx.getString(R.string.chat_s_dffb5b))
            }
            val hasOverdueTodo = runCatching {
                db.todoDao().pendingTodos().any { it.dueAt < nowMs }
            }.getOrDefault(false)
            if (hasOverdueTodo) {
                dynamicChips.add(resCtx.getString(R.string.chat_s_69faf1))
            }

            // Fallback 沿用現行靜態 chips 補齊至最多 4 顆
            val fallbackChips = buildList {
                add(resCtx.getString(R.string.chat_s_4d0373))
                if (stats.today > 0) {
                    add(resCtx.getString(R.string.chat_s_bb2e25))
                } else {
                    add(resCtx.getString(R.string.chat_s_407a71))
                }
                if (stats.scam > 0) {
                    add(resCtx.getString(R.string.chat_s_15831d))
                } else if (topApps.isNotEmpty()) {
                    add(resCtx.getString(R.string.chat_s_0a9c05))
                } else {
                    add(resCtx.getString(R.string.chat_s_c8de07))
                }
            }
            for (c in fallbackChips) {
                if (dynamicChips.size >= 4) break
                dynamicChips.add(c)
            }

            sessions = allSessions
            suggestionChips = dynamicChips.take(4)
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
                briefingChannelBlocked = com.phantom.checker.service.ProactiveEngine.isBriefingChannelBlocked(appCtx)
                briefingChannelGuideDismissed = PhantomPrefs.isBriefingChannelGuideDismissed(appCtx)
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

    var lastScrolledSessionId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(currentSessionId, messages.size, isSending) {
        if (messages.isNotEmpty()) {
            if (lastScrolledSessionId != currentSessionId) {
                listState.scrollToItem(messages.lastIndex)
                lastScrolledSessionId = currentSessionId
            } else {
                listState.animateScrollToItem(messages.lastIndex)
            }
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
        val query = rawTrimmed.ifBlank { resCtx.getString(R.string.chat_s_47f861) }
        val displayUserText = if (attachedImage != null) {
            if (rawTrimmed.isNotEmpty()) resCtx.getString(R.string.chat_s_19907c, rawTrimmed) else resCtx.getString(R.string.chat_s_08e22d)
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
                    val title = displayUserText.take(20).ifBlank { resCtx.getString(R.string.chat_s_a7975c) }
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
                    if (session != null && session.title == resCtx.getString(R.string.chat_s_a7975c)) {
                        db.chatDao().updateSessionTitle(sessionId, displayUserText.take(20))
                    }
                }
                loadMessages(sessionId)
                loadSessionsAndSuggestions()
                com.phantom.checker.util.GoalEngine.detectGoalCandidate(appCtx, displayUserText)?.let { cand ->
                    pendingGoalCandidate = sessionId to cand
                }
            }

            // #389 聊天頁「🔍 聯網」開關：走 runGroundedQuery（Gemini 3+ tools=[google_search] 或 BYOK），查無／失敗誠實回報
            if (webSearchEnabled && attachedImage == null) {
                val groundedPrompt =
                    resCtx.getString(R.string.chat_s_e42fdf) +
                        resCtx.getString(R.string.chat_s_621925)
                val groundedRes = AiClient.runGroundedQuery(
                    context = appCtx,
                    systemPrompt = groundedPrompt,
                    userQuery = query,
                    maxTokens = 480,
                    purpose = com.phantom.checker.util.AiPurpose.CHAT
                )
                val finalContent = groundedRes.fold(
                    onSuccess = { gr ->
                        buildString {
                            append(resCtx.getString(R.string.chat_s_240512))
                            append(gr.text.trim())
                            if (gr.sources.isNotEmpty()) {
                                append(resCtx.getString(R.string.chat_s_8d4936))
                                gr.sources.forEach { url -> appendLine("- $url") }
                            }
                        }.trim()
                    },
                    onFailure = { err ->
                        resCtx.getString(R.string.chat_s_9c7a09, err.message ?: resCtx.getString(R.string.chat_s_1e9720))
                    }
                )
                withContext(Dispatchers.IO) {
                    db.chatDao().insertMessage(
                        ChatMessageEntity(
                            sessionId = sessionId,
                            role = ChatMessageEntity.ROLE_ASSISTANT,
                            content = finalContent,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
                loadMessages(sessionId)
                isSending = false
                return@launch
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
                                val targetHint = mp.messageRef ?: mp.messageIds.joinToString(", ").ifBlank { resCtx.getString(R.string.chat_s_8c577d) }
                                resolutionErrors.add(resCtx.getString(R.string.chat_s_4d5429, targetHint, mp.action))
                            } else if (mp.action == "tag_message") {
                                val tName = mp.tagName?.trim().orEmpty()
                                val matchedTag = allTags.firstOrNull { it.name.equals(tName, ignoreCase = true) }
                                if (matchedTag == null) {
                                    val avail = allTags.joinToString("、") { it.name }.ifBlank { resCtx.getString(R.string.chat_s_9ecc4e) }
                                    resolutionErrors.add(resCtx.getString(R.string.chat_s_a33681, tName, avail))
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
                                val label = ref.ifBlank { resCtx.getString(R.string.chat_s_8c577d) }
                                failedWhitelistRefs.add(label)
                                resolutionErrors.add(resCtx.getString(R.string.chat_s_ee0a4f, label))
                            } else {
                                resolvedWhitelistItems.add(WhitelistTargetItem(kind = wp.kind, message = resolved))
                            }
                        }
                    }
                    if (failedWhitelistRefs.size > 1 && resolvedWhitelistItems.isEmpty()) {
                        resolutionErrors.add(resCtx.getString(R.string.chat_s_6d1e23, failedWhitelistRefs.size))
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
                            resolutionErrors.add(resCtx.getString(R.string.chat_s_a4caa6, tp.settingId))
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
                            resolutionErrors.add(resCtx.getString(R.string.chat_s_a4caa6, op.settingId))
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
                        resolutionErrors.add(resCtx.getString(R.string.chat_s_06a03c, ap.settingId))
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
                            resolutionErrors.add(resCtx.getString(R.string.chat_s_71832d, targetName))
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
                            resolutionErrors.add(resCtx.getString(R.string.chat_s_827006))
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
                                    resolutionErrors.add(resCtx.getString(R.string.chat_s_7c8148, pastFmt))
                                }
                                is NaturalTimeParseResult.Unparseable -> {
                                    resolutionErrors.add(resCtx.getString(R.string.chat_s_1499ac, title))
                                }
                            }
                        }
                    }
                } else if (isNaturalReminderIntent(query) && resolvedAdjustStates.isEmpty() && newAppliedSettings.isEmpty()) {
                    val resolvedRepeat = detectRepeatRuleFromText(query)
                    if (resolvedRepeat.isBlank() && isUnsupportedRecurringExpression(query)) {
                        resolutionErrors.add(resCtx.getString(R.string.chat_s_827006))
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
                                resolutionErrors.add(resCtx.getString(R.string.chat_s_7c8148, pastFmt))
                            }
                            is NaturalTimeParseResult.Unparseable -> {
                                resolutionErrors.add(resCtx.getString(R.string.chat_s_a30290))
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
                    parsed.openSettingProposals.isNotEmpty() ||
                    parsed.crossAppActions.isNotEmpty() ||
                    parsed.hasExtendedProposals()

                val storedReply = parsed.cleanReply.ifBlank {
                    when {
                        hasValidAction -> resCtx.getString(R.string.chat_s_680d68)
                        allActionErrors.isNotEmpty() -> resCtx.getString(R.string.chat_s_a0c7aa)
                        else -> resCtx.getString(R.string.chat_s_d2fad1)
                    }
                }

                val assistantMsgId = withContext(Dispatchers.IO) {
                    val insertedId = db.chatDao().insertMessage(
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
                    // #366 本地壓縮錨點：若有待辦未決事項，記錄最新一條供下次開啟單行接續提示（零 AI）
                    val firstPendingTodo = db.todoDao().pendingTodos().firstOrNull()
                    if (firstPendingTodo != null) {
                        PhantomPrefs.saveChatContinuityAnchor(appCtx, firstPendingTodo.title)
                    }
                    insertedId
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

                if (ragBuild.needsUsagePermissionPrompt) {
                    showUsagePermissionGuideCard = true
                }

                val hasExtendedProposals = parsed.hasExtendedProposals()

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
                                immediateExtraReplies.add(resCtx.getString(R.string.chat_s_dcceb9, days))
                            } else {
                                val summaryParts = listOfNotNull(
                                    sp.query?.let { resCtx.getString(R.string.chat_s_a2fa1c, it) },
                                    sp.sender?.let { resCtx.getString(R.string.chat_s_52f82a, it) },
                                    sp.packageName?.let { "App「$it」" }
                                ).joinToString("、").ifBlank { resCtx.getString(R.string.chat_s_a4ec06, days) }
                                resolvedSearchCard = SearchResultCardState(
                                    sessionId = sessionId,
                                    querySummary = summaryParts,
                                    results = hits
                                )
                                immediateExtraReplies.add(resCtx.getString(R.string.chat_s_ce8327, hits.size, summaryParts))
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
                                            immediateExtraReplies.add(resCtx.getString(R.string.chat_s_2fe85a))
                                        } else {
                                            val rows = allTodos.take(20).joinToString("\n") { t ->
                                                val status = if (t.isDone) resCtx.getString(R.string.chat_s_e99b48) else resCtx.getString(R.string.chat_s_f2083e)
                                                val timeStr = PhantomPrefs.formatMessageTime(appCtx, t.dueAt, includeDate = true)
                                                val safeTitle = t.title.replace("|", "｜").replace("\n", " ")
                                                "| $status | $safeTitle | $timeStr |"
                                            }
                                            immediateExtraReplies.add(resCtx.getString(R.string.chat_s_a7d6ba, rows))
                                        }
                                    }
                                    "todo_done" -> {
                                        val needle = tp.title.orEmpty()
                                        val matches = pendingTodos.filter { it.title.contains(needle, ignoreCase = true) }
                                        when {
                                            matches.isEmpty() -> resolutionErrors.add(resCtx.getString(R.string.chat_s_13c1e1, needle))
                                            matches.size > 1 -> resolutionErrors.add(
                                                resCtx.getString(R.string.chat_s_1e11d0, needle) +
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
                                            matches.isEmpty() -> resolutionErrors.add(resCtx.getString(R.string.chat_s_185d7b, needle))
                                            matches.size > 1 -> resolutionErrors.add(
                                                resCtx.getString(R.string.chat_s_1e11d0, needle) +
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
                                            matches.isEmpty() -> resolutionErrors.add(resCtx.getString(R.string.chat_s_185d7b, needle))
                                            matches.size > 1 -> resolutionErrors.add(
                                                resCtx.getString(R.string.chat_s_1e11d0, needle) +
                                                    matches.take(5).joinToString("\n") { "• ${it.title}" }
                                            )
                                            else -> {
                                                val target = matches.first()
                                                val updatedTitle = tp.newTitle?.takeIf { it.isNotBlank() } ?: target.title
                                                val updatedDueAt = if (!tp.time.isNullOrBlank()) {
                                                    when (val tr = ClientTimeParser.parseNaturalReminderTime(tp.time, query, System.currentTimeMillis())) {
                                                        is NaturalTimeParseResult.Success -> tr.epochMillis
                                                        is NaturalTimeParseResult.PastTime -> {
                                                            resolutionErrors.add(resCtx.getString(R.string.chat_s_4bb888))
                                                            null
                                                        }
                                                        NaturalTimeParseResult.Unparseable -> {
                                                            resolutionErrors.add(resCtx.getString(R.string.chat_s_32c4c7, tp.time))
                                                            null
                                                        }
                                                    }
                                                } else target.dueAt
                                                if (updatedDueAt != null) {
                                                    if (updatedTitle == target.title && updatedDueAt == target.dueAt) {
                                                        resolutionErrors.add(resCtx.getString(R.string.chat_s_7f3c79))
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
                                    resolutionErrors.add(resCtx.getString(R.string.chat_s_674b65, vp.sender))
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
                                    if (candidatesText.isBlank()) resCtx.getString(R.string.chat_s_9d6a80)
                                    else resCtx.getString(R.string.chat_s_d90aaf, mp.appLabel, candidatesText)
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
                                    PendingExtendedAction.MarkRead(sessionId, "today", null, resCtx.getString(R.string.chat_s_05ffe3))
                                )
                                "all" -> resolvedExtendedActions.add(
                                    PendingExtendedAction.MarkRead(sessionId, "all", null, resCtx.getString(R.string.chat_s_6645ce))
                                )
                                "app" -> {
                                    val resolved = resolveAppPackage(mr.packageName)
                                    if (resolved == null) {
                                        val candidatesText = appGroups.take(8).joinToString("、") { it.appLabel.ifBlank { it.packageName } }
                                        resolutionErrors.add(resCtx.getString(R.string.chat_s_108773, candidatesText))
                                    } else {
                                        resolvedExtendedActions.add(
                                            PendingExtendedAction.MarkRead(
                                                sessionId,
                                                "app",
                                                resolved.first,
                                                resCtx.getString(R.string.chat_s_276120, resolved.second)
                                            )
                                        )
                                    }
                                }
                                else -> resolutionErrors.add(resCtx.getString(R.string.chat_s_54566b, mr.scope))
                            }
                        }

                        // 6. export_messages
                        parsed.exportProposals.forEach { ep ->
                            val resolvedApp = if (ep.packageName != null) resolveAppPackage(ep.packageName) else null
                            if (ep.packageName != null && resolvedApp == null) {
                                val candidatesText = appGroups.take(8).joinToString("、") { it.appLabel.ifBlank { it.packageName } }
                                resolutionErrors.add(resCtx.getString(R.string.chat_s_5f9489, ep.packageName, candidatesText))
                            } else {
                                val appDesc = resolvedApp?.second ?: resCtx.getString(R.string.common_all_apps)
                                val daysDesc = ep.days?.let { resCtx.getString(R.string.chat_s_0cfebf, it) } ?: resCtx.getString(R.string.chat_s_9a11ba)
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
                                        resolutionErrors.add(resCtx.getString(R.string.chat_s_b569a7, q))
                                    } else {
                                        matchedRules.addAll(hits)
                                    }
                                } else {
                                    resolutionErrors.add(resCtx.getString(R.string.chat_s_f684bc))
                                }
                            }
                            val distinctMatched = matchedRules.distinctBy { it.id }
                            if (distinctMatched.isNotEmpty()) {
                                val summaryText = distinctMatched.joinToString("\n") { r ->
                                    val target = r.sender?.takeIf { it.isNotBlank() } ?: r.ruleText.take(40)
                                    val mode = if (!r.sender.isNullOrBlank()) resCtx.getString(R.string.chat_s_b6365c) else resCtx.getString(R.string.chat_s_e8f3df)
                                    val pkg = r.packageName?.takeIf { it.isNotBlank() } ?: resCtx.getString(R.string.chat_s_d22581)
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
                                    immediateExtraReplies.add(resCtx.getString(R.string.chat_s_225f65))
                                } else {
                                    val rows = filtered.take(25).joinToString("\n") { m ->
                                        val catLbl = com.phantom.checker.data.MemoryEngine.categoryLabel(m.category)
                                        val layerLbl = com.phantom.checker.data.MemoryEngine.layerLabel(m.layer)
                                        val statusLbl = com.phantom.checker.data.MemoryEngine.statusLabel(m.status)
                                        val confLbl = com.phantom.checker.data.MemoryEngine.confidenceLabel(m.confidence)
                                        val isSensitive = m.privacyTier == com.phantom.checker.data.MemoryEntity.PRIVACY_SENSITIVE
                                        val privTag = if (isSensitive) resCtx.getString(R.string.chat_s_d5f48d) else resCtx.getString(R.string.chat_s_f0aacc)
                                        val safeSubj = m.subject.replace("|", "｜").replace("\n", " ").take(20)
                                        val safeContent = if (isSensitive) {
                                            resCtx.getString(R.string.chat_s_e2bf8a)
                                        } else {
                                            m.content.replace("|", "｜").replace("\n", " ").take(45)
                                        }
                                        "| #${m.id} | $catLbl | $layerLbl | $statusLbl/$confLbl ($privTag) | $safeSubj：$safeContent |"
                                    }
                                    immediateExtraReplies.add(
                                        resCtx.getString(R.string.chat_s_e87d61, filtered.size) +
                                            resCtx.getString(R.string.chat_s_dd28d3, rows)
                                    )
                                }
                            }
                        }

                        // 9. #314 / #347 / #366 memory_add & memory_update（自動擷取僅限非敏感無衝突至多 2 筆；敏感記憶與衝突一律進確認卡）
                        val autoExtractEnabled = PhantomPrefs.isMemoryAutoExtractEnabled(appCtx)
                        val sourceRefNow = if (autoExtractEnabled) {
                            "chat_auto|sid:$sessionId|msg:$assistantMsgId|${formatChatTime(nowMs)}"
                        } else {
                            com.phantom.checker.data.MemoryEngine.formatChatSourceRef(
                                sessionId = sessionId,
                                userSnippet = query,
                                timeFormatted = formatChatTime(nowMs)
                            )
                        }
                        if (PhantomPrefs.isMemoryChatCaptureEnabled(appCtx)) {
                            val confirmRequired = PhantomPrefs.isMemoryWriteConfirmEnabled(appCtx)
                            var autoExtractedCount = 0
                            val handleWriteCandidate: suspend (PendingMemoryWriteState) -> Unit = { candidate ->
                                val isSensitive = candidate.privacyTier == com.phantom.checker.data.MemoryEntity.PRIVACY_SENSITIVE
                                val hasConflict = candidate.conflictingMemory != null
                                val allowDirectAuto = autoExtractEnabled && !isSensitive && !hasConflict && autoExtractedCount < 2
                                if (isSensitive || hasConflict || (!allowDirectAuto && confirmRequired)) {
                                    resolvedMemoryWrites.add(candidate)
                                } else {
                                    val outcome = com.phantom.checker.data.MemoryEngine.writeMemoryWithConflictDetection(
                                        db = db,
                                        category = candidate.category,
                                        subject = candidate.subject,
                                        content = candidate.content,
                                        layer = candidate.layer,
                                        expiresAt = candidate.expiresAt,
                                        privacyTier = candidate.privacyTier,
                                        confidence = com.phantom.checker.data.MemoryEntity.CONFIDENCE_INFERRED,
                                        sourceType = com.phantom.checker.data.MemoryEntity.SOURCE_CHAT,
                                        sourceRef = candidate.sourceRef,
                                        forceReplaceConflict = false
                                    )
                                    val catLbl = com.phantom.checker.data.MemoryEngine.categoryLabel(candidate.category)
                                    val layerLbl = com.phantom.checker.data.MemoryEngine.layerLabel(candidate.layer)
                                    when (outcome) {
                                        is com.phantom.checker.data.MemoryEngine.WriteOutcome.Inserted -> {
                                            if (allowDirectAuto) {
                                                autoExtractedCount++
                                            } else {
                                                val report = resCtx.getString(R.string.chat_s_22468b, outcome.memory.id, catLbl, layerLbl, outcome.memory.subject, outcome.memory.content)
                                                db.chatDao().insertMessage(
                                                    ChatMessageEntity(
                                                        sessionId = sessionId,
                                                        role = ChatMessageEntity.ROLE_ASSISTANT,
                                                        content = report,
                                                        createdAt = System.currentTimeMillis()
                                                    )
                                                )
                                            }
                                        }
                                        is com.phantom.checker.data.MemoryEngine.WriteOutcome.Deduplicated -> {
                                            if (allowDirectAuto) {
                                                autoExtractedCount++
                                            } else {
                                                val report = resCtx.getString(R.string.chat_s_10541d, outcome.memory.id, catLbl, layerLbl, outcome.memory.subject)
                                                db.chatDao().insertMessage(
                                                    ChatMessageEntity(
                                                        sessionId = sessionId,
                                                        role = ChatMessageEntity.ROLE_ASSISTANT,
                                                        content = report,
                                                        createdAt = System.currentTimeMillis()
                                                    )
                                                )
                                            }
                                        }
                                        is com.phantom.checker.data.MemoryEngine.WriteOutcome.ConflictDetected -> {
                                            resolvedMemoryWrites.add(
                                                candidate.copy(conflictingMemory = outcome.existingMemory)
                                            )
                                        }
                                    }
                                }
                            }

                            for (map in parsed.memoryAddProposals) {
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
                                handleWriteCandidate(
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

                            for (mup in parsed.memoryUpdateProposals) {
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
                                handleWriteCandidate(
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
                                    mdp.subject?.let { resCtx.getString(R.string.chat_s_8ce871, it) },
                                    mdp.query?.let { resCtx.getString(R.string.chat_s_a2fa1c, it) }
                                ).joinToString("、").ifBlank { resCtx.getString(R.string.chat_s_89f1d1) }
                                if (distinctTargets.isEmpty()) {
                                    immediateExtraReplies.add(resCtx.getString(R.string.chat_s_e3cbdd, labelDesc))
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

                        // #387 本機日曆行程／課表查詢（純本機 CalendarBridge.queryInstances，嚴禁編造）
                        if (parsed.calendarQueryProposals.isNotEmpty()) {
                            parsed.calendarQueryProposals.forEach { q ->
                                val summary = com.phantom.checker.util.CalendarBridge.queryScheduleSummary(
                                    ctx = appCtx,
                                    dateRaw = q.date,
                                    dayFilter = q.day
                                )
                                immediateExtraReplies.add(summary)
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

                        // #364 App 使用情況查詢（UsageStatsManager 真實數據；未授權則誠實回報並顯示授權引導）
                        if (parsed.usageQueryProposals.isNotEmpty()) {
                            parsed.usageQueryProposals.forEach { uq ->
                                val usageRes = com.phantom.checker.util.UsageStatsBridge.queryUsageSummary(
                                    ctx = appCtx,
                                    days = uq.days,
                                    top = uq.top
                                )
                                if (!usageRes.granted) {
                                    showUsagePermissionGuideCard = true
                                }
                                immediateExtraReplies.add(usageRes.markdownTable)
                            }
                        }

                        // #419 本機健康明細查詢（HealthConnectBridge 純本機聚合，零 token）
                        if (parsed.healthQueryProposals.isNotEmpty()) {
                            parsed.healthQueryProposals.forEach { hq ->
                                val report = com.phantom.checker.util.HealthConnectBridge.buildHealthQueryReport(
                                    ctx = appCtx,
                                    days = hq.days
                                )
                                immediateExtraReplies.add(report)
                            }
                        }

                        // #364 單意圖單卡降級提示（不靜默遺漏任何次要查詢意圖）
                        if (parsed.demotedQueryHints.isNotEmpty()) {
                            immediateExtraReplies.add(
                                resCtx.getString(R.string.chat_s_7b45f1, parsed.demotedQueryHints.joinToString("、"))
                            )
                        }

                        // #329 / #348 本機日曆新增行程（一律先做重疊撞期檢查；依開關決定直接寫入或彈出確認卡）
                        if (parsed.calendarAddProposals.isNotEmpty()) {
                            val writableCals = com.phantom.checker.util.CalendarBridge.listSelectedWritableCalendars(appCtx)
                            val confirmRequired = PhantomPrefs.isCalendarWriteConfirmEnabled(appCtx)
                            val hasWritePerm = com.phantom.checker.util.CalendarBridge.hasWritePermission(appCtx)
                            parsed.calendarAddProposals.forEach { cap ->
                                val parsedStart = com.phantom.checker.util.ClientTimeParser.parseStructuredDueAt(cap.startRaw)
                                    ?: (com.phantom.checker.util.ClientTimeParser.parseNaturalReminderTime(null, cap.startRaw) as? com.phantom.checker.util.NaturalTimeParseResult.Success)?.epochMillis
                                if (parsedStart == null) {
                                    immediateExtraReplies.add(resCtx.getString(R.string.chat_s_b462d7, cap.title, cap.startRaw))
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
                                    val allDayEvents = if (conflicts.isEmpty()) {
                                        com.phantom.checker.util.CalendarBridge.findAllDayEventsInWindow(
                                            ctx = appCtx,
                                            startMs = parsedStart,
                                            endMs = parsedEnd
                                        )
                                    } else emptyList()
                                    if (!confirmRequired && hasWritePerm && writableCals.isNotEmpty() && conflicts.isEmpty()) {
                                        val targetCal = writableCals.first()
                                        val res = com.phantom.checker.util.CalendarBridge.insertEvent(
                                            ctx = appCtx,
                                            calendarId = targetCal.id,
                                            title = cap.title,
                                            startMs = parsedStart,
                                            endMs = parsedEnd,
                                            allDay = cap.allDay,
                                            location = cap.location,
                                            description = cap.notes
                                        )
                                        val startFormatted = formatChatTime(parsedStart)
                                        res.fold(
                                            onSuccess = {
                                                val allDaySuffix = com.phantom.checker.util.CalendarBridge.formatAllDayInfoNote(allDayEvents)?.let { "\n$it" } ?: ""
                                                immediateExtraReplies.add(resCtx.getString(R.string.chat_s_243400, targetCal.displayName, cap.title, startFormatted, allDaySuffix))
                                            },
                                            onFailure = { err ->
                                                immediateExtraReplies.add(resCtx.getString(R.string.chat_s_f33c16, err.localizedMessage ?: resCtx.getString(R.string.common_unknown_error)))
                                            }
                                        )
                                    } else {
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
                                                conflicts = conflicts,
                                                allDayEvents = allDayEvents
                                            )
                                        )
                                    }
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
                lastError = err.message ?: resCtx.getString(R.string.chat_s_360c9b)
                lastFailedQuery = query
            }
        }
    }

    deleteSessionConfirm?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteSessionConfirm = null },
            title = { Text(stringResource(R.string.chat_s_dc4a8a)) },
            text = { Text(stringResource(R.string.chat_s_1df552, target.title)) },
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
                            onShowMessage(resCtx.getString(R.string.chat_s_87a1ab))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.common_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteSessionConfirm = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showClearAllSessionsConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllSessionsConfirm = false },
            title = { Text(stringResource(R.string.chat_s_a2f68b)) },
            text = { Text(stringResource(R.string.chat_s_73403e)) },
            confirmButton = {
                Button(
                    onClick = {
                        if (isSending) {
                            showClearAllSessionsConfirm = false
                            onShowMessage(resCtx.getString(R.string.chat_s_fae667))
                            return@Button
                        }
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                AppDatabaseHolder.db(appCtx).chatDao().clearAllNonProactiveSessions(
                                    com.phantom.checker.service.ProactiveEngine.PROACTIVE_SESSION_TITLE
                                )
                            }
                            if (!isViewingProactiveRoom) {
                                currentSessionId = null
                                messages = emptyList()
                            }
                            lastError = null
                            lastFailedQuery = null
                            showClearAllSessionsConfirm = false
                            showSessionDrawer = false
                            loadSessionsAndSuggestions()
                            onShowMessage(resCtx.getString(R.string.chat_s_f519c9))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.common_confirm_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllSessionsConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    inspectAutoMemoriesMsgId?.let { targetMsgId ->
        val memList = allMemoriesLive.filter { it.sourceRef?.contains("|msg:$targetMsgId|") == true }
        ModalBottomSheet(onDismissRequest = { inspectAutoMemoriesMsgId = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = resCtx.getString(R.string.chat_s_d5f658, memList.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (memList.isEmpty()) {
                    Text(
                        text = resCtx.getString(R.string.chat_s_2a2311),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    memList.forEach { mem ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "[${com.phantom.checker.data.MemoryEngine.categoryLabel(mem.category)}／${com.phantom.checker.data.MemoryEngine.layerLabel(mem.layer)}] ${mem.subject}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = mem.content,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = resCtx.getString(R.string.chat_s_c7cde7, mem.sourceRef ?: resCtx.getString(R.string.chat_s_c6af2a)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        if (mem.privacyTier == com.phantom.checker.data.MemoryEntity.PRIVACY_SENSITIVE) {
                                            sensitiveAutoMemDeleteTarget = mem
                                        } else {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    AppDatabaseHolder.db(appCtx).memoryDao().deleteById(mem.id)
                                                }
                                                onShowMessage(resCtx.getString(R.string.chat_s_e16a59, mem.subject))
                                            }
                                        }
                                    }
                                ) {
                                    Text(resCtx.getString(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    sensitiveAutoMemDeleteTarget?.let { targetMem ->
        AlertDialog(
            onDismissRequest = { sensitiveAutoMemDeleteTarget = null },
            title = { Text(stringResource(R.string.chat_s_cc810d)) },
            text = { Text(stringResource(R.string.chat_s_762a1d, targetMem.subject, targetMem.content)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDel = targetMem
                        sensitiveAutoMemDeleteTarget = null
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                AppDatabaseHolder.db(appCtx).memoryDao().deleteById(toDel.id)
                            }
                            onShowMessage(resCtx.getString(R.string.chat_s_1fc6e9, toDel.subject))
                        }
                    }
                ) {
                    Text(stringResource(R.string.common_confirm_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { sensitiveAutoMemDeleteTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    pendingRuleQueue.firstOrNull()?.let { pending ->
        val proposal = pending.proposal
        val remainingCount = pendingRuleQueue.size
        val kindLabel = when (proposal.kind) {
            AiRuleEntity.KIND_WHITELIST -> resCtx.getString(R.string.chat_s_eedcf6)
            "memo" -> resCtx.getString(R.string.chat_s_987cbd)
            else -> resCtx.getString(R.string.chat_s_63084e)
        }
        AlertDialog(
            onDismissRequest = { pendingRuleQueue = pendingRuleQueue.drop(1) },
            title = {
                Text(if (remainingCount > 1) stringResource(R.string.chat_s_ff3a24, remainingCount) else stringResource(R.string.chat_s_c888ee))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.chat_s_050095, kindLabel),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!proposal.tagName.isNullOrBlank() && proposal.kind == AiRuleEntity.KIND_TAG) {
                        Text(
                            text = stringResource(R.string.chat_s_a4337a, proposal.tagName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = stringResource(R.string.chat_s_766651, proposal.ruleText),
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
                                            resCtx.getString(R.string.chat_s_f4ae38, proposal.ruleText)
                                        } else if (ok) {
                                            resCtx.getString(R.string.chat_s_cd6ece, proposal.ruleText)
                                        } else {
                                            resCtx.getString(R.string.chat_s_8468fb, proposal.ruleText)
                                        },
                                        createdAt = System.currentTimeMillis()
                                    )
                                )
                                ok
                            } else if (proposal.kind == AiRuleEntity.KIND_WHITELIST) {
                                val upsertRes = WhitelistRuleHelper.upsertWhitelist(
                                    dao = db.aiRuleDao(),
                                    ruleText = proposal.ruleText,
                                    sender = null,
                                    packageName = null,
                                    onCacheInvalidate = { NotificationCollectorService.invalidateRuleCaches() }
                                )
                                val ok = upsertRes.isSuccess
                                db.chatDao().insertMessage(
                                    ChatMessageEntity(
                                        sessionId = sid,
                                        role = ChatMessageEntity.ROLE_ASSISTANT,
                                        content = when (upsertRes.status) {
                                            WhitelistRuleHelper.Status.INSERTED ->
                                                resCtx.getString(R.string.chat_s_02901b, upsertRes.rule?.id, proposal.ruleText)
                                            WhitelistRuleHelper.Status.REACTIVATED ->
                                                "✓ ${upsertRes.message} #${upsertRes.rule?.id}：${proposal.ruleText}"
                                            WhitelistRuleHelper.Status.DUPLICATE ->
                                                "ℹ️ ${upsertRes.message}（#${upsertRes.rule?.id}）：${proposal.ruleText}"
                                            WhitelistRuleHelper.Status.FAILED ->
                                                resCtx.getString(R.string.chat_s_6f8db5, proposal.ruleText)
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
                                            resCtx.getString(R.string.chat_s_cf567e, verified!!.id, proposal.ruleText)
                                        } else {
                                            resCtx.getString(R.string.chat_s_c11b0a, proposal.ruleText)
                                        },
                                        createdAt = System.currentTimeMillis()
                                    )
                                )
                                ok
                            }
                        }
                        loadMessages(sid)
                        onShowMessage(if (verifiedOk) resCtx.getString(R.string.chat_s_b22612) else resCtx.getString(R.string.chat_s_e51036))
                    }
                }) {
                    Text(stringResource(R.string.common_confirm_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRuleQueue = pendingRuleQueue.drop(1) }) {
                    Text(stringResource(R.string.common_cancel))
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
            p.appPackage?.let { add(resCtx.getString(R.string.chat_s_f2a0e4, it)) }
            p.senderPattern?.let { add(resCtx.getString(R.string.chat_s_db4eb4, it)) }
            p.contentKeyword?.let { add(resCtx.getString(R.string.chat_s_a2fa1c, it)) }
            p.contentRegex?.let { add(resCtx.getString(R.string.chat_s_3af783, it)) }
            com.phantom.checker.util.TagRuleEngine.semanticLabel(p.semanticCategory)?.let { add(resCtx.getString(R.string.chat_s_7a9b5b, it)) }
        }.joinToString(" ＋ ").ifBlank { p.description }
        AlertDialog(
            onDismissRequest = { pendingTagRuleQueue = pendingTagRuleQueue.drop(1) },
            title = {
                Text(if (remaining > 1) stringResource(R.string.chat_s_aac697, remaining) else stringResource(R.string.chat_s_ed2da5))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.chat_s_30aee0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.chat_s_c30ddb, p.description),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.chat_s_fb1e70, previewConditions),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.chat_s_2f04e0),
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
                                            resCtx.getString(R.string.chat_s_a97467, insertedId, p.description, tagName)
                                        } else {
                                            resCtx.getString(R.string.chat_s_c18e8f, p.description)
                                        },
                                        createdAt = System.currentTimeMillis()
                                    )
                                )
                                ok
                            }
                            loadMessages(sid)
                            onShowMessage(if (verifiedOk) resCtx.getString(R.string.chat_s_e72b10, tagName) else resCtx.getString(R.string.chat_s_2dbeb3))
                        }
                    }
                ) {
                    Text(stringResource(R.string.chat_s_598492))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTagRuleQueue = pendingTagRuleQueue.drop(1) }) {
                    Text(stringResource(R.string.common_cancel))
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
                        text = stringResource(R.string.chat_s_e53dad),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.chat_s_4daa9c, todoListItems.count { !it.isDone }),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (todoListItems.isEmpty()) {
                    Text(
                        text = stringResource(R.string.chat_s_a745ea),
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
                                            text = if (todo.isDone) stringResource(R.string.chat_s_213354, dueStr) else "⏰ $dueStr",
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
                                            contentDescription = stringResource(R.string.chat_s_ae9e5f),
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
        // §4.2 精簡頂部應用列：頭像＋助手名＋模型副標（省 token 開啟時顯示徽章）｜右：＋新對話、歷史、⋮ overflow
        var showChatOverflow by remember { mutableStateOf(false) }
        val pendingTodoCount = todoListItems.count { !it.isDone }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistantAvatarCircle(
                    avatarBitmap = assistantAvatar,
                    assistantName = assistantName,
                    size = 28.dp
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (assistantName == PhantomPrefs.DEFAULT_ASSISTANT_NAME) {
                            stringResource(R.string.chat_s_8dd976)
                        } else {
                            assistantName
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = activeProfile?.model?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chat_s_288570),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (tokenSaverEnabled) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.chat_s_3dcfdd),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        currentSessionId = null
                        messages = emptyList()
                        lastError = null
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.chat_s_a7975c))
                }
                IconButton(
                    onClick = { showSessionDrawer = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.chat_s_4e6af9))
                }
                Box {
                    IconButton(
                        onClick = { showChatOverflow = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.common_more_options)
                        )
                    }
                    DropdownMenu(
                        expanded = showChatOverflow,
                        onDismissRequest = { showChatOverflow = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (tokenSaverEnabled) stringResource(R.string.chat_s_ee0e3d) else stringResource(R.string.chat_s_654d89)) },
                            onClick = {
                                showChatOverflow = false
                                val next = !tokenSaverEnabled
                                tokenSaverEnabled = next
                                SettingsCatalog.applyBooleanSetting(appCtx, "quick_token_saver", next)
                                onShowMessage(if (next) resCtx.getString(R.string.chat_s_c934ec) else resCtx.getString(R.string.chat_s_5d174b))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_s_7c0637)) },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                            onClick = {
                                showChatOverflow = false
                                showTokenSaverInfoDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (pendingTodoCount > 0) stringResource(R.string.chat_s_e384f5, pendingTodoCount) else stringResource(R.string.chat_s_3cbab4)) },
                            onClick = {
                                showChatOverflow = false
                                showTodoListSheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isSearchActive) stringResource(R.string.common_s_27c39c) else stringResource(R.string.chat_s_b71873)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            onClick = {
                                showChatOverflow = false
                                onToggleSearch()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_refresh)) },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                showChatOverflow = false
                                onRefresh()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_settings)) },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = {
                                showChatOverflow = false
                                onOpenSettings()
                            }
                        )
                    }
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
                        text = stringResource(R.string.chat_s_7c0637),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.chat_s_a73dd4, if (tokenSaverEnabled) resCtx.getString(R.string.chat_s_df42b3) else resCtx.getString(R.string.chat_s_e8a92f)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.chat_s_b9cf01),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(R.string.chat_s_8d6ebb),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(R.string.chat_s_40ff00, normalRag),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(R.string.chat_s_c6bf69, normalMemTopK, saverMemTopK),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(R.string.chat_s_f1b189),
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
                            showTokenSaverInfoDialog = false
                            onShowMessage(if (next) resCtx.getString(R.string.chat_s_c934ec) else resCtx.getString(R.string.chat_s_fe4663))
                        }
                    ) {
                        Text(if (tokenSaverEnabled) stringResource(R.string.chat_s_adf1c7) else stringResource(R.string.chat_s_fdd725))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTokenSaverInfoDialog = false }) {
                        Text(stringResource(R.string.chat_s_16779d))
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
                    text = stringResource(R.string.chat_s_fb05e0),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.chat_s_29a268),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = { ctx.startActivity(Intent(ctx, ApiKeySettingsActivity::class.java)) }) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.chat_s_bfb145))
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // #404 通知可見性健檢：偵測 assistant_briefing channel 被停用時於聊天室頂部顯示一次引導卡
        if (briefingChannelBlocked && !briefingChannelGuideDismissed) {
            PhantomGlassCard(
                glassState = glassState,
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 18.dp,
                contentPadding = 12.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.chat_s_a1aa92),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(R.string.chat_s_5d28a0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            briefingChannelGuideDismissed = true
                            PhantomPrefs.setBriefingChannelGuideDismissed(appCtx, true)
                        }) {
                            Text(stringResource(R.string.chat_s_cb63c6))
                        }
                        Spacer(Modifier.width(6.dp))
                        Button(onClick = {
                            com.phantom.checker.service.ProactiveEngine.openBriefingChannelSettings(ctx)
                        }) {
                            Text(stringResource(R.string.chat_s_1d8949))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // #366 主動訊息聊天室入口（預設收起＋未讀徽章；點開即已讀並進入專屬主動訊息聊天室）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isViewingProactiveRoom) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                },
                border = BorderStroke(
                    width = 0.75.dp,
                    color = if (isViewingProactiveRoom || proactiveUnreadCount > 0) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    }
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        if (isViewingProactiveRoom) {
                            messages = emptyList()
                            isViewingProactiveRoom = false
                            val restoreId = previousNormalSessionId
                            currentSessionId = restoreId
                            if (restoreId != null) {
                                scope.launch { loadMessages(restoreId) }
                            }
                        } else {
                            messages = emptyList()
                            previousNormalSessionId = currentSessionId
                            isViewingProactiveRoom = true
                            scope.launch {
                                val proactiveSid = withContext(Dispatchers.IO) {
                                    com.phantom.checker.service.ProactiveEngine.getOrCreateProactiveSessionId(
                                        AppDatabaseHolder.db(appCtx)
                                    )
                                }
                                val now = System.currentTimeMillis()
                                PhantomPrefs.setProactiveChatLastReadAt(appCtx, now)
                                proactiveLastReadAt = now
                                if (isViewingProactiveRoom) {
                                    currentSessionId = proactiveSid
                                    loadMessages(proactiveSid)
                                }
                            }
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isViewingProactiveRoom) stringResource(R.string.chat_s_c79e39) else stringResource(R.string.chat_s_9fb6b7),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isViewingProactiveRoom) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1
                    )
                    if (!isViewingProactiveRoom && proactiveUnreadCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error
                        ) {
                            Text(
                                text = if (proactiveUnreadCount > 99) "99+" else proactiveUnreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onError,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }

        // #366 / #407 單一對話框連續性：壓縮錨點（點擊展開全文或帶入對話，過期自動消失）＋未決事項膠囊列（至多 3 顆，無未決不佔位）
        if (!isViewingProactiveRoom && pendingCapsulesEnabled) {
            continuityAnchorText?.takeIf { it.isNotBlank() }?.let { anchor ->
                var anchorExpanded by remember(anchor) { mutableStateOf(false) }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                anchorExpanded = !anchorExpanded
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = resCtx.getString(R.string.chat_s_b5598c, anchor),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = if (anchorExpanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = resCtx.getString(R.string.chat_s_504b4b),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clickable {
                                        inputText = resCtx.getString(R.string.chat_s_154143, anchor)
                                    }
                            )
                            Text(
                                text = "✕",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clickable {
                                        PhantomPrefs.dismissChatContinuityAnchor(appCtx)
                                        continuityAnchorText = null
                                    }
                            )
                        }
                    }
                }
            }

            val pendingCapsules = remember(todoListItems, dismissedCapsuleIds) {
                todoListItems.filter { !it.isDone && it.id !in dismissedCapsuleIds }.take(3)
            }
            if (pendingCapsules.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(pendingCapsules, key = { it.id }) { todo ->
                        var capsuleExpanded by remember(todo.id) { mutableStateOf(false) }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    if (todo.title.length > 24 && !capsuleExpanded) {
                                        capsuleExpanded = true
                                    } else {
                                        inputText = resCtx.getString(R.string.chat_s_58eb88, todo.title)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "📌 ${todo.title}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = if (capsuleExpanded) Modifier else Modifier.widthIn(max = 220.dp)
                                )
                                Text(
                                    text = "✕",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clickable {
                                        dismissedCapsuleIds = dismissedCapsuleIds + todo.id
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 對話內容區
        val chatLayoutVer by PhantomSettingsBus.version.collectAsState()
        val chatFontScale = remember(chatLayoutVer) { PhantomPrefs.getChatFontScale(appCtx) }
        val bubbleCornerDp = remember(chatLayoutVer) { PhantomPrefs.getChatBubbleCornerDp(appCtx) }
        val bubbleWidthPct = remember(chatLayoutVer) { PhantomPrefs.getChatBubbleWidthPercent(appCtx) }
        val baseDensity = LocalDensity.current
        val chatDensity = remember(baseDensity, chatFontScale) {
            androidx.compose.ui.unit.Density(
                density = baseDensity.density,
                fontScale = baseDensity.fontScale * chatFontScale
            )
        }
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val userBubbleMaxWidth = maxWidth * (bubbleWidthPct / 100f)
            val assistantBubbleMaxWidth = maxWidth * ((bubbleWidthPct + 6).coerceAtMost(96) / 100f)
            if (messages.isEmpty() && !isSending) {
                EmptyStateView(
                    icon = AutoAwesomeIcon,
                    title = stringResource(R.string.chat_s_8c4bdf),
                    description = stringResource(R.string.chat_s_c39ab1, assistantName)
                )
            } else {
                val showBubbleTimestamp = remember(chatLayoutVer) {
                    PhantomPrefs.isChatTimestampEnabled(appCtx)
                }
                androidx.compose.runtime.key(currentSessionId) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }, contentType = { it.role }) { msg ->
                        val isUser = msg.role == ChatMessageEntity.ROLE_USER
                        androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides chatDensity) {
                        Row(
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            val bubbleTimeText = remember(msg.id, msg.createdAt) {
                                formatChatBubbleTime(if (msg.createdAt > 0L) msg.createdAt else System.currentTimeMillis())
                            }
                            if (isUser) {
                                Surface(
                                    shape = RoundedCornerShape(bubbleCornerDp.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .widthIn(max = userBubbleMaxWidth)
                                        .clip(RoundedCornerShape(bubbleCornerDp.dp))
                                        .phantomSheen(bubbleCornerDp.dp, isDark = true)
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = {
                                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(ClipData.newPlainText("chat_message", msg.content))
                                                onShowMessage(resCtx.getString(R.string.detail_s_00399e))
                                            }
                                        )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(
                                            start = 14.dp,
                                            end = 14.dp,
                                            top = 10.dp,
                                            bottom = if (showBubbleTimestamp) 6.dp else 10.dp
                                        )
                                    ) {
                                        Text(
                                            text = msg.content,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (showBubbleTimestamp) {
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = bubbleTimeText,
                                                fontSize = 10.sp,
                                                lineHeight = 12.sp,
                                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.76f),
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.align(Alignment.End)
                                            )
                                        }
                                    }
                                }
                            } else {
                                val isBriefingMsg = msg.id > 0L && (
                                    msg.id == PhantomPrefs.getLastBriefingMessageId(appCtx) ||
                                        ((msg.content.startsWith("**☀️ ") || msg.content.startsWith("**🌙 ")) &&
                                            msg.content.contains(stringResource(R.string.chat_s_8aa9b3)))
                                )
                                if (isBriefingMsg) {
                                    LaunchedEffect(msg.id) {
                                        PhantomPrefs.markBriefingReadOnce(appCtx, msg.id)
                                    }
                                }
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
                                            .clip(RoundedCornerShape(bubbleCornerDp.dp))
                                            .combinedClickable(
                                                onClick = {},
                                                onLongClick = {
                                                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    cm.setPrimaryClip(
                                                        ClipData.newPlainText("assistant_reply", stripMarkdown(msg.content))
                                                    )
                                                    onShowMessage(resCtx.getString(R.string.chat_s_ee4465))
                                                }
                                            ),
                                        cornerRadius = bubbleCornerDp.dp,
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
                                                            text = if (thinkingExpanded) stringResource(R.string.chat_s_faaf22) else stringResource(R.string.chat_s_90507f),
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        PhantomChevron(
 expanded = thinkingExpanded,
                                                            contentDescription = if (thinkingExpanded) stringResource(R.string.chat_s_fa38cb) else stringResource(R.string.chat_s_c44ad5),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    AnimatedVisibility(
                                                        visible = thinkingExpanded,
                                                        enter = PhantomMotion.ExpandEnter,
                                                        exit = PhantomMotion.ExpandExit
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
                                        if (showBubbleTimestamp) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 4.dp),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                Text(
                                                    text = bubbleTimeText,
                                                    fontSize = 10.sp,
                                                    lineHeight = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                    val isProactiveMsg = msg.id > 0L && (
                                        isBriefingMsg ||
                                            msg.content.startsWith("**🔔 ") ||
                                            msg.content.startsWith("**📅 ") ||
                                            msg.content.startsWith("**📋 ")
                                        )
                                    if (isProactiveMsg) {
                                        var feedbackChoice by remember(msg.id) {
                                            mutableStateOf(PhantomPrefs.getProactiveMessageFeedback(appCtx, msg.id))
                                        }
                                        Row(
                                            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (feedbackChoice == "up") stringResource(R.string.chat_s_90dd08) else stringResource(R.string.chat_s_c279e3),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (feedbackChoice == "up") {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        PhantomPrefs.recordProactiveMessageFeedback(appCtx, msg.id, helpful = true)
                                                        feedbackChoice = "up"
                                                        onShowMessage(resCtx.getString(R.string.chat_s_460322))
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                            Text(
                                                text = if (feedbackChoice == "down") stringResource(R.string.chat_s_b6b88e) else stringResource(R.string.chat_s_7b1b5e),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (feedbackChoice == "down") {
                                                    MaterialTheme.colorScheme.error
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        PhantomPrefs.recordProactiveMessageFeedback(appCtx, msg.id, helpful = false)
                                                        feedbackChoice = "down"
                                                        onShowMessage(resCtx.getString(R.string.chat_s_005a42))
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (msg.content.startsWith(stringResource(R.string.chat_s_7c9f5f))) {
                                        val topicHeader = remember(msg.content) {
                                            msg.content.lineSequence().firstOrNull()
                                                ?.removePrefix(resCtx.getString(R.string.chat_s_7c9f5f))
                                                ?.removeSuffix("**")
                                                ?.trim()
                                                .orEmpty()
                                        }
                                        TextButton(
                                            onClick = {
                                                inputText = if (topicHeader.isNotBlank()) {
                                                    resCtx.getString(R.string.chat_s_732759, topicHeader)
                                                } else {
                                                    resCtx.getString(R.string.chat_s_2954c1)
                                                }
                                                if (webSearchSupported) {
                                                    webSearchEnabled = true
                                                }
                                                onShowMessage(resCtx.getString(R.string.chat_s_8059a4))
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Text(stringResource(R.string.chat_s_d9dd89), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    val autoMems = remember(allMemoriesLive, msg.id) {
                                        allMemoriesLive.filter { it.sourceRef?.contains("|msg:${msg.id}|") == true }
                                    }
                                    if (autoMems.isNotEmpty()) {
                                        Text(
                                            text = stringResource(R.string.chat_s_80ff6f, autoMems.size),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .padding(start = 6.dp, top = 2.dp)
                                                .clickable { inspectAutoMemoriesMsgId = msg.id }
                                        )
                                    }
                                }
                            }
                        }
                        }
                    }

                    pendingGoalCandidate?.takeIf { it.first == currentSessionId }?.let { (_, candidate) ->
                        item(key = "pending_goal_confirm_card") {
                            var editTitle by remember(candidate.messageHash) { mutableStateOf(candidate.title) }
                            var editCadence by remember(candidate.messageHash) { mutableStateOf(candidate.cadenceDays) }
                            PhantomGlassCard(
                                glassState = glassState,
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                contentPadding = 14.dp
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = stringResource(R.string.chat_s_f6220d, candidate.confidence),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = stringResource(R.string.chat_s_26720d),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedTextField(
                                        value = editTitle,
                                        onValueChange = { editTitle = it },
                                        label = { Text(stringResource(R.string.chat_s_76f1c6)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(1 to stringResource(R.string.chat_s_5e4715), 7 to stringResource(R.string.chat_s_d6847a), 14 to stringResource(R.string.chat_s_7d6acc), 30 to stringResource(R.string.chat_s_24aedc)).forEach { (days, label) ->
                                            FilterChip(
                                                selected = editCadence == days,
                                                onClick = { editCadence = days },
                                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val finalTitle = editTitle.trim()
                                                if (finalTitle.isNotEmpty()) {
                                                    scope.launch {
                                                        com.phantom.checker.util.GoalEngine.createGoalAndScheduleNextTodo(
                                                            appCtx,
                                                            finalTitle,
                                                            editCadence
                                                        )
                                                        onShowMessage(resCtx.getString(R.string.chat_s_2a17b5, finalTitle))
                                                    }
                                                    pendingGoalCandidate = null
                                                }
                                            },
                                            enabled = editTitle.trim().isNotEmpty()
                                        ) {
                                            Text(stringResource(R.string.chat_s_0769fa), style = MaterialTheme.typography.labelMedium)
                                        }
                                        TextButton(
                                            onClick = {
                                                PhantomPrefs.markGoalCandidateDismissed(appCtx, candidate.messageHash)
                                                pendingGoalCandidate = null
                                            }
                                        ) {
                                            Text(stringResource(R.string.chat_s_ee4503), style = MaterialTheme.typography.labelSmall)
                                        }
                                        TextButton(
                                            onClick = {
                                                PhantomPrefs.markGoalCandidateDismissed(appCtx, candidate.messageHash)
                                                PhantomPrefs.recordDecisionFeedback(appCtx, "goal_wrong")
                                                pendingGoalCandidate = null
                                                onShowMessage(resCtx.getString(R.string.chat_s_0abe42))
                                            }
                                        ) {
                                            Text(stringResource(R.string.chat_s_3f00c8), style = MaterialTheme.typography.labelSmall)
                                        }
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
                                        stringResource(R.string.chat_s_1f8b3e, pending.items.size)
                                    } else {
                                        val only = pending.items.first()
                                        if (only.kind == "sender") stringResource(R.string.chat_s_9dc19e) else stringResource(R.string.chat_s_2eb1a2)
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
                                                        text = if (isSenderMode) resCtx.getString(R.string.chat_s_80cb14) else resCtx.getString(R.string.chat_s_a7be2b),
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
                                                            content = resCtx.getString(R.string.chat_s_3f4c06),
                                                            createdAt = System.currentTimeMillis()
                                                        )
                                                    )
                                                }
                                                loadMessages(sid)
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.common_cancel))
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
                                                            val upsertRes = WhitelistRuleHelper.upsertWhitelist(
                                                                dao = db.aiRuleDao(),
                                                                ruleText = ruleText,
                                                                sender = if (isSenderMode) targetMsg.sender.trim() else null,
                                                                packageName = targetMsg.packageName,
                                                                onCacheInvalidate = { NotificationCollectorService.invalidateRuleCaches() }
                                                            )
                                                            val label = targetMsg.sender.ifBlank { targetMsg.appLabel }
                                                            val modeDesc = if (isSenderMode) resCtx.getString(R.string.chat_s_ccde9b) else resCtx.getString(R.string.chat_s_beb8cb)
                                                            if (upsertRes.isSuccess) {
                                                                db.messageDao().update(
                                                                    targetMsg.copy(
                                                                        riskScore = 10,
                                                                        riskLabel = "safe",
                                                                        riskReason = "使用者透過 AI 助手加入白名單"
                                                                    )
                                                                )
                                                                val statusNote = when (upsertRes.status) {
                                                                    WhitelistRuleHelper.Status.INSERTED -> resCtx.getString(R.string.chat_s_56f3b2)
                                                                    WhitelistRuleHelper.Status.REACTIVATED -> upsertRes.message
                                                                    WhitelistRuleHelper.Status.DUPLICATE -> upsertRes.message
                                                                    WhitelistRuleHelper.Status.FAILED -> upsertRes.message
                                                                }
                                                                lines.add("✓ #${upsertRes.rule?.id} $label（${targetMsg.appLabel}，$modeDesc，$statusNote）")
                                                            } else {
                                                                lines.add("✗ $label（${targetMsg.appLabel}，$modeDesc）：${upsertRes.message}")
                                                            }
                                                        } catch (e: Exception) {
                                                            lines.add(resCtx.getString(R.string.chat_s_8be643, targetMsg.sender.ifBlank { targetMsg.appLabel }, e.message ?: resCtx.getString(R.string.chat_s_424bfd)))
                                                        }
                                                    }
                                                    val summary = buildString {
                                                        appendLine(resCtx.getString(R.string.chat_s_cc1169, chosen.size))
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
                                                onShowMessage(resCtx.getString(R.string.chat_s_e3bc5f, reportLines.size))
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(if (pending.items.size > 1) stringResource(R.string.chat_s_944383, selectedCount) else stringResource(R.string.common_confirm_add))
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
                                text = if (isApplied) stringResource(R.string.chat_s_9a4447) else stringResource(R.string.chat_s_f59370),
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
                                val beforeStr = if (card.beforeEnabled) stringResource(R.string.common_on) else stringResource(R.string.common_close)
                                val afterStr = if (card.afterEnabled) stringResource(R.string.common_on) else stringResource(R.string.common_close)
                                Text(
                                    text = stringResource(R.string.chat_s_002d30, beforeStr, afterStr),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else if (card.beforeText != null && card.afterText != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.chat_s_05b863, card.beforeText, card.afterText),
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
                                    Text(stringResource(R.string.chat_s_5d5815))
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
                                    Text(stringResource(R.string.chat_s_a5ea79))
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
                                    text = stringResource(R.string.chat_s_d1774e, adjState.item.title),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.chat_s_beae5f, spec.formatValue(adjState.currentValue), spec.formatValue(draftValue)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (adjState.wasClamped) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.chat_s_5348df, spec.min, spec.max, spec.formatValue(adjState.proposedValue)),
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
                                        text = stringResource(R.string.chat_s_69239a, spec.min, spec.max, spec.step),
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
                                                            content = resCtx.getString(R.string.chat_s_3e1177, adjState.item.title),
                                                            createdAt = System.currentTimeMillis()
                                                        )
                                                    )
                                                }
                                                loadMessages(sid)
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.common_cancel))
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
                                                val note = if (clamped || adjState.wasClamped) resCtx.getString(R.string.chat_s_54b7d7) else ""
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        AppDatabaseHolder.db(appCtx).chatDao().insertMessage(
                                                            ChatMessageEntity(
                                                                sessionId = sid,
                                                                role = ChatMessageEntity.ROLE_ASSISTANT,
                                                                content = resCtx.getString(R.string.chat_s_57f8be, adjState.item.title, spec.formatValue(afterVal), note),
                                                                createdAt = System.currentTimeMillis()
                                                            )
                                                        )
                                                    }
                                                    loadMessages(sid)
                                                }
                                                onShowMessage(resCtx.getString(R.string.chat_s_578d3d, adjState.item.title, spec.formatValue(afterVal)))
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.chat_s_9ff907))
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
                                    text = stringResource(R.string.chat_s_fc6628),
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
                                                            resCtx.getString(R.string.chat_s_ed93d8, item.title, if (currentOn) resCtx.getString(R.string.common_on) else resCtx.getString(R.string.common_close))
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
                                                                onShowMessage(resCtx.getString(R.string.chat_s_f9cee0, item.title, if (target) resCtx.getString(R.string.common_on) else resCtx.getString(R.string.common_close)))
                                                            }
                                                        }
                                                    ) {
                                                        Text(if (currentOn) resCtx.getString(R.string.common_close) else resCtx.getString(R.string.common_on))
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
                                                        Text(resCtx.getString(R.string.chat_s_23926d))
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
                                    Text(stringResource(R.string.common_cancel))
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
                                "reply_message" -> stringResource(R.string.chat_s_f92569)
                                "open_app" -> stringResource(R.string.chat_s_ec1f14)
                                "delete_message" -> stringResource(R.string.chat_s_932343, actState.messages.size)
                                "tag_message" -> if (actState.tagOperation == "remove") {
                                    stringResource(R.string.chat_s_3914e0, actState.tagName.orEmpty())
                                } else {
                                    stringResource(R.string.chat_s_d71346, actState.tagName.orEmpty())
                                }
                                else -> stringResource(R.string.chat_s_e6d941)
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
                                        text = stringResource(R.string.chat_s_d1c6a5),
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
                                            text = stringResource(R.string.chat_s_9faca6, actState.messages.size - 5),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (actState.action == "reply_message" && primaryMsg != null) {
                                    Spacer(Modifier.height(8.dp))
                                    if (OtpDetector.extractOtp(primaryMsg.content) != null) {
                                        Text(
                                            text = stringResource(R.string.chat_s_267c1b),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(Modifier.height(4.dp))
                                    }
                                    OutlinedTextField(
                                        value = draftReply,
                                        onValueChange = { draftReply = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text(stringResource(R.string.chat_s_107884)) },
                                        maxLines = 4
                                    )
                                }

                                if (actState.action == "delete_message" && scamMsgs.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = stringResource(R.string.chat_s_1ce96d, scamMsgs.size),
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
                                                                content = resCtx.getString(R.string.chat_s_9fce43),
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
                                        Text(stringResource(R.string.common_cancel))
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
                                                                resCtx.getString(R.string.chat_s_8cd4d0)
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
                                                                                    sender = resCtx.getString(R.string.chat_s_34f201),
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
                                                                        resCtx.getString(R.string.chat_s_ec2456, primaryMsg.sender.ifBlank { primaryMsg.appLabel }, draftReply)
                                                                    }
                                                                    is NotificationCollectorService.Companion.ReplySendResult.Unavailable ->
                                                                        res.reason
                                                                }
                                                            }
                                                        }
                                                        "open_app" -> {
                                                            if (primaryMsg == null) {
                                                                resCtx.getString(R.string.chat_s_ab510d)
                                                            } else {
                                                                val launch = ctx.packageManager.getLaunchIntentForPackage(primaryMsg.packageName)
                                                                if (launch != null) {
                                                                    try {
                                                                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                        ctx.startActivity(launch)
                                                                        resCtx.getString(R.string.chat_s_25dfa8, primaryMsg.appLabel)
                                                                    } catch (e: Exception) {
                                                                        Log.e("PhantomChatAction", "open_app launch failed pkg=${primaryMsg.packageName}", e)
                                                                        resCtx.getString(R.string.chat_s_33c2e7, primaryMsg.appLabel, e.message ?: resCtx.getString(R.string.chat_s_94c54a))
                                                                    }
                                                                } else {
                                                                    val detailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                                        data = Uri.parse("package:${primaryMsg.packageName}")
                                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                    }
                                                                    try {
                                                                        ctx.startActivity(detailsIntent)
                                                                        resCtx.getString(R.string.chat_s_be7b2d, primaryMsg.appLabel)
                                                                    } catch (e: Exception) {
                                                                        Log.e("PhantomChatAction", "open_app details failed pkg=${primaryMsg.packageName}", e)
                                                                        resCtx.getString(R.string.chat_s_6dd457, primaryMsg.appLabel, primaryMsg.packageName)
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
                                                                resCtx.getString(R.string.chat_s_d3abf6, safeDeleteMsgs.size, scamMsgs.size)
                                                            } else {
                                                                resCtx.getString(R.string.chat_s_2def7c, safeDeleteMsgs.size)
                                                            }
                                                        }
                                                        "tag_message" -> {
                                                            val tid = actState.tagId
                                                            val tName = actState.tagName.orEmpty()
                                                            if (tid == null) {
                                                                resCtx.getString(R.string.chat_s_8299ef, tName)
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
                                                                val verb = if (actState.tagOperation == "remove") resCtx.getString(R.string.chat_s_2f752c) else resCtx.getString(R.string.chat_s_411539)
                                                                resCtx.getString(R.string.chat_s_10e4d8, actState.messages.size, verb, tName)
                                                            }
                                                        }
                                                        else -> resCtx.getString(R.string.chat_s_564a88)
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("PhantomChatAction", "Action ${actState.action} failed sid=$sid", e)
                                                    resCtx.getString(R.string.chat_s_b69832, e.message ?: resCtx.getString(R.string.common_unknown_error))
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
                                                "reply_message" -> stringResource(R.string.chat_s_ba15ed)
                                                "open_app" -> stringResource(R.string.chat_s_15d6cb)
                                                "delete_message" -> stringResource(R.string.common_confirm_delete)
                                                else -> stringResource(R.string.chat_s_eba931)
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
                                    text = stringResource(R.string.chat_s_1408b1),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.chat_s_61e47e, pending.proposal.label),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.chat_s_e51be0, pending.proposal.prompt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = selectedMode == PhantomPrefs.QuickChatCommand.MODE_DIRECT,
                                        onClick = { selectedMode = PhantomPrefs.QuickChatCommand.MODE_DIRECT },
                                        label = { Text(stringResource(R.string.chat_s_aa0b15)) }
                                    )
                                    FilterChip(
                                        selected = selectedMode == PhantomPrefs.QuickChatCommand.MODE_PREFILL,
                                        onClick = { selectedMode = PhantomPrefs.QuickChatCommand.MODE_PREFILL },
                                        label = { Text(stringResource(R.string.chat_s_5af577)) }
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
                                            onShowMessage(resCtx.getString(R.string.chat_s_964319, pending.proposal.label))
                                        }
                                    ) {
                                        Text(stringResource(R.string.common_confirm_add))
                                    }
                                    OutlinedButton(
                                        onClick = { pendingQuickCommandQueue = pendingQuickCommandQueue.drop(1) }
                                    ) {
                                        Text(stringResource(R.string.common_cancel))
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
                                        stringResource(R.string.chat_s_3e400c, sessionPendingCalendars.size)
                                    } else {
                                        stringResource(R.string.chat_s_88c7a6)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.chat_s_4d5339, pendingCal.title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (pendingCal.allDay) {
                                        stringResource(R.string.chat_s_37233d, startFormatted)
                                    } else {
                                        stringResource(R.string.detail_s_a99e2d, startFormatted, endFormatted)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val calRoute = remember(pendingCal.title, pendingCal.writableCalendars.size) {
                                    com.phantom.checker.util.IntentCapabilityRegistry.resolve(
                                        ctx = appCtx,
                                        intent = com.phantom.checker.util.IntentCapabilityRegistry.IntentType.CALENDAR_ADD,
                                        hasSelectedWritableCalendar = pendingCal.writableCalendars.isNotEmpty()
                                    )
                                }
                                Text(
                                    text = calRoute.executionWayLine(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                calRoute.rerouteNoticeText()?.let { notice ->
                                    Text(
                                        text = "ℹ️ $notice",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (calRoute.isAllBlocked) {
                                    TextButton(
                                        onClick = {
                                            ctx.startActivity(
                                                com.phantom.checker.util.IntentCapabilityRegistry.buildPermissionHubIntent(ctx)
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(stringResource(R.string.chat_s_523cfe), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                if (!pendingCal.location.isNullOrBlank()) {
                                    Text(
                                        text = stringResource(R.string.chat_s_85a7f1, pendingCal.location),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (pendingCal.writableCalendars.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = stringResource(R.string.chat_s_156bd1),
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
                                            text = stringResource(R.string.detail_s_93ec95, com.phantom.checker.util.CalendarBridge.formatConflictSummary(pendingCal.conflicts)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                } else {
                                    com.phantom.checker.util.CalendarBridge.formatAllDayInfoNote(pendingCal.allDayEvents)?.let { infoNote ->
                                        Spacer(Modifier.height(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = infoNote,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
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
                                                            content = resCtx.getString(R.string.chat_s_cfbd11, pendingCal.title),
                                                            createdAt = System.currentTimeMillis()
                                                        )
                                                    )
                                                }
                                                loadMessages(sid)
                                            }
                                        }
                                    ) {
                                        Text(stringResource(R.string.common_cancel))
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
                                                            resCtx.getString(R.string.chat_s_9d1596, pendingCal.title, startFormatted)
                                                        },
                                                        onFailure = { err ->
                                                            resCtx.getString(R.string.chat_s_f33c16, err.localizedMessage ?: resCtx.getString(R.string.common_unknown_error))
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
                                                    onShowMessage(resCtx.getString(R.string.chat_s_0011b0))
                                                }.onFailure {
                                                    onShowMessage(resCtx.getString(R.string.detail_s_670c4a))
                                                }
                                            }
                                        }
                                    ) {
                                        Text(
                                            if (hasWritePerm && selectedCalId != null) {
                                                if (pendingCal.conflicts.isNotEmpty()) stringResource(R.string.chat_s_9b4489) else stringResource(R.string.chat_s_6f20e5)
                                            } else {
                                                stringResource(R.string.detail_s_f12b81)
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
                                        stringResource(R.string.chat_s_06c6fe, sessionPendingTodos.size)
                                    } else {
                                        stringResource(R.string.chat_s_cfb6c0)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.chat_s_9264f8, pendingTodo.title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.chat_s_584831, dueFormatted),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val remRoute = remember(pendingTodo.title, pendingTodo.dueAt) {
                                    com.phantom.checker.util.IntentCapabilityRegistry.resolve(
                                        ctx = appCtx,
                                        intent = com.phantom.checker.util.IntentCapabilityRegistry.IntentType.REMINDER
                                    )
                                }
                                Text(
                                    text = remRoute.executionWayLine(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                remRoute.rerouteNoticeText()?.let { notice ->
                                    Text(
                                        text = "ℹ️ $notice",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (remRoute.isAllBlocked) {
                                    TextButton(
                                        onClick = {
                                            ctx.startActivity(
                                                com.phantom.checker.util.IntentCapabilityRegistry.buildPermissionHubIntent(ctx)
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(stringResource(R.string.chat_s_523cfe), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { editingTodoTimeTarget = pendingTodo }
                                    ) {
                                        Text(stringResource(R.string.chat_s_8e34a1))
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
                                                            content = resCtx.getString(R.string.chat_s_3250dd, pendingTodo.title),
                                                            createdAt = System.currentTimeMillis()
                                                        )
                                                    )
                                                }
                                                loadMessages(sid)
                                            }
                                        }
                                    ) {
                                        Text(stringResource(R.string.common_cancel))
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
                                                                resCtx.getString(R.string.chat_s_6eff80, todoId, pendingTodo.title, dueFormatted)
                                                            } else {
                                                                resCtx.getString(R.string.chat_s_04c6dd, pendingTodo.title)
                                                            },
                                                            createdAt = System.currentTimeMillis()
                                                        )
                                                    )
                                                    ok
                                                }
                                                loadMessages(sid)
                                                onShowMessage(if (verifiedOk) resCtx.getString(R.string.chat_s_6d610a, dueFormatted) else resCtx.getString(R.string.chat_s_3b05b7))
                                            }
                                        }
                                    ) {
                                        Text(stringResource(R.string.common_confirm_create))
                                    }
                                }
                            }
                        }
                    }

                    if (showUsagePermissionGuideCard) {
                        item(key = "usage_permission_guide_card") {
                            PhantomGlassCard(
                                glassState = glassState,
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                contentPadding = 12.dp
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = stringResource(R.string.chat_s_abd19d),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = stringResource(R.string.chat_s_a66196),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                com.phantom.checker.util.UsageStatsBridge.openUsageAccessSettings(ctx)
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(stringResource(R.string.chat_s_6c301d), maxLines = 1)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                ctx.startActivity(Intent(ctx, PermissionHubActivity::class.java))
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(stringResource(R.string.chat_s_37a4b3), maxLines = 1)
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = { showUsagePermissionGuideCard = false }) {
                                            Text(stringResource(R.string.common_dismiss_tip))
                                        }
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
                                        text = stringResource(R.string.chat_s_30ee2c, srCard.results.size, srCard.querySummary),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    TextButton(onClick = { searchResultCard = null }) {
                                        Text(stringResource(R.string.common_close))
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
                                                        text = resCtx.getString(R.string.chat_s_c1aacd, msg.appLabel.ifBlank { msg.packageName }, msg.sender.ifBlank { resCtx.getString(R.string.common_s_877f9a) }),
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
                                is PendingExtendedAction.TodoDone -> stringResource(R.string.chat_s_b7459e)
                                is PendingExtendedAction.TodoDelete -> stringResource(R.string.chat_s_e19a41)
                                is PendingExtendedAction.TodoEdit -> stringResource(R.string.chat_s_acf16c)
                                is PendingExtendedAction.VipPin -> if (ext.pin) stringResource(R.string.chat_s_9020aa) else stringResource(R.string.chat_s_5f7879)
                                is PendingExtendedAction.MuteApp -> if (ext.mute) stringResource(R.string.chat_s_61827e) else stringResource(R.string.chat_s_947626)
                                is PendingExtendedAction.MarkRead -> stringResource(R.string.chat_s_01796b)
                                is PendingExtendedAction.ExportMessages -> stringResource(R.string.chat_s_a87253)
                                is PendingExtendedAction.WhitelistDelete -> stringResource(R.string.chat_s_246d18, ext.rules.size)
                            }
                            val bodyText = when (ext) {
                                is PendingExtendedAction.TodoDone -> stringResource(R.string.chat_s_00fa86, ext.todo.title)
                                is PendingExtendedAction.TodoDelete -> stringResource(R.string.chat_s_10363b, ext.todo.title)
                                is PendingExtendedAction.TodoEdit -> {
                                    val oldTime = PhantomPrefs.formatMessageTime(appCtx, ext.original.dueAt, includeDate = true)
                                    val newTime = PhantomPrefs.formatMessageTime(appCtx, ext.newDueAt, includeDate = true)
                                    stringResource(R.string.chat_s_266bc5, ext.original.title, ext.newTitle, oldTime, newTime)
                                }
                                is PendingExtendedAction.VipPin -> "${ext.appLabel} · ${ext.sender}"
                                is PendingExtendedAction.MuteApp -> "${ext.appLabel}（${ext.packageName}）"
                                is PendingExtendedAction.MarkRead -> stringResource(R.string.chat_s_02aa0f, ext.scopeLabel)
                                is PendingExtendedAction.ExportMessages -> stringResource(R.string.chat_s_20ec96, ext.scopeLabel)
                                is PendingExtendedAction.WhitelistDelete -> stringResource(R.string.chat_s_a2cf52, ext.summaryText)
                            }
                            PhantomGlassCard(
                                glassState = glassState,
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                contentPadding = 12.dp
                            ) {
                                Text(
                                    text = if (sessionExtActions.size > 1) stringResource(R.string.chat_s_19262e, titleText, sessionExtActions.size) else titleText,
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
                                                            content = resCtx.getString(R.string.chat_s_1f7127, titleText),
                                                            createdAt = System.currentTimeMillis()
                                                        )
                                                    )
                                                }
                                                loadMessages(sid)
                                            }
                                        }
                                    ) {
                                        Text(stringResource(R.string.common_cancel))
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
                                                                resCtx.getString(R.string.chat_s_8986db, ext.todo.title)
                                                            } else if (spawned != null) {
                                                                resCtx.getString(R.string.chat_s_8a3167, ext.todo.title)
                                                            } else {
                                                                resCtx.getString(R.string.chat_s_795d78, ext.todo.title)
                                                            }
                                                        }
                                                        is PendingExtendedAction.TodoDelete -> {
                                                            db.todoDao().deleteById(ext.todo.id)
                                                            val stillExists = db.todoDao().byId(ext.todo.id) != null
                                                            if (!stillExists) {
                                                                ReminderScheduler.cancelTodo(appCtx, ext.todo.id)
                                                                ReminderWidgetProvider.updateAllIfChanged(appCtx)
                                                                resCtx.getString(R.string.chat_s_24bc12, ext.todo.title)
                                                            } else {
                                                                resCtx.getString(R.string.chat_s_6fd8e5, ext.todo.title)
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
                                                                resCtx.getString(R.string.chat_s_a5b305, updated.title, newTimeStr)
                                                            } else {
                                                                resCtx.getString(R.string.chat_s_fbafcb, ext.original.title)
                                                            }
                                                        }
                                                        is PendingExtendedAction.VipPin -> {
                                                            val isCurrentlyVip = ext.sender in PhantomPrefs.getVipSenders(appCtx, ext.packageName)
                                                            if (isCurrentlyVip != ext.pin) {
                                                                PhantomPrefs.toggleVipSender(appCtx, ext.packageName, ext.sender)
                                                            }
                                                            if (ext.pin) resCtx.getString(R.string.chat_s_7c4901, ext.sender, ext.appLabel)
                                                            else resCtx.getString(R.string.chat_s_2561d1, ext.sender, ext.appLabel)
                                                        }
                                                        is PendingExtendedAction.MuteApp -> {
                                                            PhantomPrefs.setAppMuted(appCtx, ext.packageName, ext.mute)
                                                            if (ext.mute) resCtx.getString(R.string.chat_s_ec3c41, ext.appLabel)
                                                            else resCtx.getString(R.string.chat_s_702a00, ext.appLabel)
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
                                                            resCtx.getString(R.string.chat_s_f51f1c, count)
                                                        }
                                                        is PendingExtendedAction.ExportMessages -> {
                                                            val res = com.phantom.checker.util.MessageExporter.exportToDownloads(
                                                                ctx = appCtx,
                                                                packageName = ext.packageName,
                                                                daysBack = ext.days
                                                            )
                                                            res.fold(
                                                                onSuccess = { fileName -> resCtx.getString(R.string.chat_s_5734dd, fileName) },
                                                                onFailure = { err -> resCtx.getString(R.string.chat_s_55fba2, err.message ?: resCtx.getString(R.string.common_unknown_error)) }
                                                            )
                                                        }
                                                        is PendingExtendedAction.WhitelistDelete -> {
                                                            val lines = ext.rules.map { r ->
                                                                db.aiRuleDao().deleteById(r.id)
                                                                val stillExists = db.aiRuleDao().byId(r.id) != null
                                                                val label = r.sender?.takeIf { it.isNotBlank() } ?: r.ruleText.take(40)
                                                                if (!stillExists) {
                                                                    resCtx.getString(R.string.chat_s_aee760, r.id, label)
                                                                } else {
                                                                    resCtx.getString(R.string.chat_s_b00068, r.id, label)
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
                                        Text(stringResource(R.string.chat_s_eba931))
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
                            val expStr = memWrite.expiresAt?.let { resCtx.getString(R.string.chat_s_be1c17, formatChatTime(it)) }.orEmpty()
                            PhantomGlassCard(
                                glassState = glassState,
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                contentPadding = 12.dp
                            ) {
                                Text(
                                    text = when {
                                        hasConflict -> resCtx.getString(R.string.chat_s_d5cd08, memWrite.subject)
                                        memWrite.isUpdate -> resCtx.getString(R.string.chat_s_0eab01)
                                        else -> resCtx.getString(R.string.chat_s_306fd1)
                                    } + if (sessionMemWrites.size > 1) resCtx.getString(R.string.chat_s_5ed028, sessionMemWrites.size) else "",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasConflict) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = resCtx.getString(R.string.chat_s_d3bfc6, catLbl, layerLbl, expStr, privLbl),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = resCtx.getString(R.string.chat_s_6de585, memWrite.subject),
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
                                                text = resCtx.getString(R.string.chat_s_1879b3, oldMem.id, oldMem.content),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = resCtx.getString(R.string.chat_s_347f4a, memWrite.content),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = resCtx.getString(R.string.chat_s_d08d9c),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = resCtx.getString(R.string.chat_s_e51be0, memWrite.content),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Text(
                                    text = resCtx.getString(R.string.chat_s_c7cde7, memWrite.sourceRef),
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
                                                            resCtx.getString(R.string.chat_s_2ac638)
                                                        } else {
                                                            oldMem.content
                                                        }
                                                        db.chatDao().insertMessage(
                                                            ChatMessageEntity(
                                                                sessionId = sid,
                                                                role = ChatMessageEntity.ROLE_ASSISTANT,
                                                                content = resCtx.getString(R.string.chat_s_cec181, oldMem.id, oldMem.subject, oldContentDisplay),
                                                                createdAt = System.currentTimeMillis()
                                                            )
                                                        )
                                                    }
                                                    loadMessages(sid)
                                                    onShowMessage(resCtx.getString(R.string.chat_s_9378f3))
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(resCtx.getString(R.string.chat_s_cb42f5))
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
                                                            resCtx.getString(R.string.chat_s_2ac638)
                                                        } else {
                                                            memWrite.content
                                                        }
                                                        db.chatDao().insertMessage(
                                                            ChatMessageEntity(
                                                                sessionId = sid,
                                                                role = ChatMessageEntity.ROLE_ASSISTANT,
                                                                content = resCtx.getString(R.string.chat_s_86f153, savedId, memWrite.subject, newContentDisplay, oldMem.id),
                                                                createdAt = System.currentTimeMillis()
                                                            )
                                                        )
                                                    }
                                                    loadMessages(sid)
                                                    if (memWrite.category == com.phantom.checker.data.MemoryEntity.CATEGORY_SCAM_IMMUNITY &&
                                                        onShowActionMessage != null && onOpenMemoryWithFilter != null
                                                    ) {
                                                        onShowActionMessage(resCtx.getString(R.string.chat_s_6336ff), resCtx.getString(R.string.chat_s_f7acef)) {
                                                            onOpenMemoryWithFilter(com.phantom.checker.data.MemoryEntity.CATEGORY_SCAM_IMMUNITY)
                                                        }
                                                    } else {
                                                        onShowMessage(resCtx.getString(R.string.chat_s_391a64))
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(resCtx.getString(R.string.chat_s_c75fb0))
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
                                                                content = resCtx.getString(R.string.chat_s_42693b),
                                                                createdAt = System.currentTimeMillis()
                                                            )
                                                        )
                                                    }
                                                    loadMessages(sid)
                                                    onShowMessage(resCtx.getString(R.string.chat_s_9ddeb0))
                                                }
                                            }
                                        ) {
                                            Text(resCtx.getString(R.string.chat_s_501aad))
                                        }
                                        TextButton(
                                            onClick = {
                                                pendingMemoryWriteQueue = pendingMemoryWriteQueue.filterNot { it === memWrite }
                                            }
                                        ) {
                                            Text(resCtx.getString(R.string.chat_s_4771e7))
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
                                                                content = resCtx.getString(R.string.chat_s_39b882, memWrite.subject),
                                                                createdAt = System.currentTimeMillis()
                                                            )
                                                        )
                                                    }
                                                    loadMessages(sid)
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(resCtx.getString(R.string.chat_s_0179de))
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
                                                                    resCtx.getString(R.string.chat_s_2ac638)
                                                                } else {
                                                                    outcome.memory.content
                                                                }
                                                                resCtx.getString(R.string.chat_s_22468b, outcome.memory.id, catLbl, layerLbl, outcome.memory.subject, contentDisplay)
                                                            }
                                                            is com.phantom.checker.data.MemoryEngine.WriteOutcome.Deduplicated ->
                                                                resCtx.getString(R.string.chat_s_af6ebf, outcome.memory.id)
                                                            is com.phantom.checker.data.MemoryEngine.WriteOutcome.ConflictDetected ->
                                                                resCtx.getString(R.string.chat_s_245709, outcome.existingMemory.id)
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
                                                        onShowActionMessage(resCtx.getString(R.string.chat_s_6d6862), resCtx.getString(R.string.chat_s_f7acef)) {
                                                            onOpenMemoryWithFilter(com.phantom.checker.data.MemoryEntity.CATEGORY_SCAM_IMMUNITY)
                                                        }
                                                    } else {
                                                        onShowMessage(resCtx.getString(R.string.chat_s_4de516))
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(resCtx.getString(R.string.chat_s_598492))
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
                                        text = resCtx.getString(R.string.chat_s_1e9df7, crossAct.title()),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = crossAct.summary(),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    val openAppRoute = remember(crossAct) {
                                        val pkg = (crossAct as? com.phantom.checker.util.CrossAppActionProposal.OpenApp)?.packageName
                                        com.phantom.checker.util.IntentCapabilityRegistry.resolve(
                                            ctx = appCtx,
                                            intent = com.phantom.checker.util.IntentCapabilityRegistry.IntentType.OPEN_APP,
                                            targetPackage = pkg
                                        )
                                    }
                                    Text(
                                        text = openAppRoute.executionWayLine(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    openAppRoute.rerouteNoticeText()?.let { notice ->
                                        Text(
                                            text = "ℹ️ $notice",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = resCtx.getString(R.string.chat_s_5688db),
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
                                            Text(resCtx.getString(R.string.common_cancel))
                                        }
                                        Button(
                                            onClick = {
                                                pendingCrossAppQueue = pendingCrossAppQueue.drop(1)
                                                com.phantom.checker.util.CrossAppActionExecutor.execute(ctx, crossAct)
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(resCtx.getString(R.string.chat_s_88675e))
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
                                        text = stringResource(R.string.chat_s_733970),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = stringResource(R.string.chat_s_0c2141, aliasCand.alias, aliasCand.matchedDisplayName),
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
                                            Text(stringResource(R.string.common_skip))
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
                                                                note = resCtx.getString(R.string.chat_s_12816a)
                                                            )
                                                        )
                                                    }
                                                    com.phantom.checker.util.ContactContext.invalidateCache()
                                                    onShowMessage(resCtx.getString(R.string.chat_s_366ff8, aliasCand.alias, aliasCand.matchedDisplayName))
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(stringResource(R.string.chat_s_40d4cd))
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
                                    text = stringResource(R.string.chat_s_7e222e, memDel.filterSummary, memDel.targets.size),
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
                                            text = stringResource(R.string.chat_s_de7c4a, memDel.targets.size - 10),
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
                                                            content = resCtx.getString(R.string.chat_s_e9efb8, memDel.filterSummary),
                                                            createdAt = System.currentTimeMillis()
                                                        )
                                                    )
                                                }
                                                loadMessages(sid)
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.common_cancel))
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
                                                            content = resCtx.getString(R.string.chat_s_5dfd16, idsToDelete.size, memDel.filterSummary),
                                                            createdAt = System.currentTimeMillis()
                                                        )
                                                    )
                                                }
                                                loadMessages(sid)
                                                onShowMessage(resCtx.getString(R.string.chat_s_cba5f8, idsToDelete.size))
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.chat_s_1ecc3e, selectedIds.size))
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
                                    text = stringResource(R.string.chat_s_111b22, err),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                lastFailedQuery?.let { q ->
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedButton(onClick = { sendQuery(q, isRetry = true) }) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(resCtx.getString(R.string.chat_s_a7d12d))
                                    }
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
                        contentDescription = resCtx.getString(R.string.chat_s_892dff),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = resCtx.getString(R.string.chat_s_000cac),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = resCtx.getString(R.string.chat_s_3193f7),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { pendingImageAttachment = null },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = resCtx.getString(R.string.chat_s_3e9b6f))
                    }
                }
            }
        }

        // §4.2 輸入框上方工具列（Provider/模型切換 ＋ 🔍 聯網，單排 LazyRow 不擠壓輸入框）
        Spacer(Modifier.height(4.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item(key = "chat_tool_provider") {
                AiProviderSwitcherChip(
                    profiles = profiles,
                    activeProfile = activeProfile,
                    onSelectProfile = { selected ->
                        PhantomPrefs.setPurposeProfileId(appCtx, com.phantom.checker.util.AiPurpose.CHAT.id, selected.id)
                        activeProfile = selected
                        hasApiKey = true
                        onShowMessage(resCtx.getString(R.string.chat_s_93e855, selected.name, selected.model))
                    },
                    label = ""
                )
            }
            item(key = "chat_tool_websearch") {
                FilterChip(
                    selected = webSearchEnabled && webSearchSupported,
                    onClick = {
                        if (webSearchSupported) {
                            webSearchEnabled = !webSearchEnabled
                        } else {
                            webSearchEnabled = false
                            onShowMessage(resCtx.getString(R.string.chat_s_ea28d8))
                        }
                    },
                    label = {
                        Text(
                            text = if (webSearchEnabled && webSearchSupported) stringResource(R.string.chat_s_89f22e) else stringResource(R.string.chat_s_c5c547),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    },
                    modifier = Modifier.alpha(if (webSearchSupported) 1f else 0.5f)
                )
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
                        contentDescription = if (showSuggestions) stringResource(R.string.chat_s_987c2c) else stringResource(R.string.chat_s_d90d2f)
                    )
                }
                DropdownMenu(
                    expanded = showInputToolsMenu,
                    onDismissRequest = { showInputToolsMenu = false }
                ) {
                    if (showSuggestions) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_s_dda835)) },
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
                        text = { Text(stringResource(R.string.chat_s_de4406)) },
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
                        text = stringResource(R.string.chat_s_3898c4),
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
                modifier = Modifier
                    .size(44.dp)
                    .pressableScale(0.90f)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.common_send), modifier = Modifier.size(20.dp))
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
                                text = stringResource(R.string.chat_s_4e6af9),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (sessions.isNotEmpty()) {
                                    TextButton(onClick = {
                                        if (isSending) {
                                            onShowMessage(resCtx.getString(R.string.chat_s_fae667))
                                        } else {
                                            showClearAllSessionsConfirm = true
                                        }
                                    }) {
                                        Text(stringResource(R.string.chat_s_5d2541), color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                TextButton(onClick = {
                                    currentSessionId = null
                                    messages = emptyList()
                                    lastError = null
                                    showSessionDrawer = false
                                }) {
                                    Text(stringResource(R.string.chat_s_b0d90a))
                                }
                            }
                        }
                        if (sessions.isEmpty()) {
                            Text(
                                text = stringResource(R.string.chat_s_09c780),
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
                                            .animateItem()
                                            .fillMaxWidth()
                                            .pressableScale(0.98f, RoundedCornerShape(12.dp))
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
                                                    contentDescription = stringResource(R.string.chat_s_0399b3),
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
            title = { Text(stringResource(R.string.chat_s_dda835)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (quickCommands.isEmpty()) {
                        Text(
                            text = stringResource(R.string.chat_s_d76c24),
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
                                resCtx.getString(R.string.chat_s_5fa372, if (isDirect) resCtx.getString(R.string.chat_s_aa0b15) else resCtx.getString(R.string.chat_s_5af577))
                            } else if (isDirect) {
                                resCtx.getString(R.string.chat_s_aa0b15)
                            } else {
                                resCtx.getString(R.string.chat_s_5af577)
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
                    Text(stringResource(R.string.common_close))
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
    label: String = stringResource(R.string.chat_s_b2351f)
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (profiles.isEmpty()) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        text = stringResource(R.string.chat_s_01bd1a),
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

