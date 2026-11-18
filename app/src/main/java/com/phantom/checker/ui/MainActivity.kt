package com.phantom.checker.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.room.withTransaction
import com.phantom.checker.data.AiSummaryEntity
import com.phantom.checker.data.AiTagCountRow
import com.phantom.checker.data.AppGroupRow
import com.phantom.checker.data.AppRiskRow
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.data.MessageTagCrossRef
import com.phantom.checker.data.MessageTagRow
import com.phantom.checker.data.NoteEntity
import com.phantom.checker.data.TagEntity
import com.phantom.checker.data.TagTotalCountRow
import com.phantom.checker.data.TodoEntity
import com.phantom.checker.network.AiClient
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.service.DailySummaryPolicy
import com.phantom.checker.service.WeeklySummaryPolicy
import com.phantom.checker.service.HeartbeatPolicy
import com.phantom.checker.service.NotificationCollectorService
import com.phantom.checker.service.ReminderScheduler
import com.phantom.checker.service.RetentionPolicy
import com.phantom.checker.service.StatsForegroundService
import com.phantom.checker.ui.components.LiquidGlassTabBar
import com.phantom.checker.ui.components.LiquidGlassTabBarItem
import com.phantom.checker.ui.components.pressableScale
import com.phantom.checker.util.AppUpdateHelper
import com.phantom.checker.util.MessageExporter
import com.phantom.checker.util.MuteMode
import com.phantom.checker.util.PhantomPrefs
import com.phantom.checker.util.ShareCardReportData
import com.phantom.checker.util.StatsShareCardGenerator
import com.phantom.checker.util.TabBarDisplayMode
import com.phantom.checker.util.UpdateCheckResult
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val PAGE_SIZE = 100
private const val MAX_BATCH_SELECT = 100

class MainActivity : ComponentActivity() {
    private val requestedTabState = mutableStateOf<MainTab?>(null)
    private val requestedEditTodoIdState = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AiClient.init(applicationContext)
        RetentionPolicy.scheduleOrUpdate(applicationContext)
        DailySummaryPolicy.schedule(applicationContext)
        WeeklySummaryPolicy.schedule(applicationContext)
        HeartbeatPolicy.schedule(applicationContext)
        StatsForegroundService.startOrRefresh(applicationContext)
        parseIntentTab(intent)
        setContent {
            PhantomCheckTheme {
                PhantomCheckApp(
                    requestedTab = requestedTabState.value,
                    onConsumedRequestedTab = { requestedTabState.value = null },
                    requestedEditTodoId = requestedEditTodoIdState.value,
                    onConsumedEditTodoId = { requestedEditTodoIdState.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseIntentTab(intent)
    }

    private fun parseIntentTab(intent: Intent?) {
        when (intent?.getStringExtra(DailySummaryPolicy.EXTRA_OPEN_TAB)) {
            DailySummaryPolicy.TAB_STATS -> requestedTabState.value = MainTab.STATS
            com.phantom.checker.widget.ReminderWidgetProvider.TAB_NOTES -> requestedTabState.value = MainTab.NOTES
        }
        val todoId = intent?.getLongExtra(com.phantom.checker.widget.ReminderWidgetProvider.EXTRA_EDIT_TODO_ID, -1L) ?: -1L
        if (todoId > 0L) {
            requestedTabState.value = MainTab.NOTES
            requestedEditTodoIdState.value = todoId
        }
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    return NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)
}

private fun isPostNotificationsGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private enum class MainTab(val title: String) {
    MESSAGES("訊息"),
    ASSISTANT("AI 助手"),
    NOTES("記事"),
    STATS("統計")
}

private sealed interface MessageFilter {
    data object All : MessageFilter
    data object Unread : MessageFilter
    data object Starred : MessageFilter
    data object Scams : MessageFilter
    data object Filtered : MessageFilter
    data object Noted : MessageFilter
    data object Todoed : MessageFilter
    data class Tag(val tag: TagEntity) : MessageFilter
}

private enum class SearchRiskFilter(val label: String) {
    ALL("全部風險"),
    SCAM("僅詐騙"),
    SUSPICIOUS("僅疑似"),
    STARRED("僅星標")
}

private data class AppGroupUi(
    val packageName: String,
    val appLabel: String,
    val totalCount: Int,
    val todayCount: Int,
    val unreadTodayCount: Int,
    val totalUnreadCount: Int = 0,
    val lastTs: Long,
    val lastPreview: String,
    val latestTags: List<MessageTagRow> = emptyList(),
    val riskScore: Int = 0
)

private data class DayCountUi(
    val label: String,
    val count: Int
)

private data class StatsUiState(
    val latestSummary: AiSummaryEntity? = null,
    val latestWeeklySummary: AiSummaryEntity? = null,
    val weeklyCounts: List<DayCountUi> = emptyList(),
    val safeCount: Int = 0,
    val suspiciousCount: Int = 0,
    val scamCount: Int = 0,
    val topRiskyApps: List<AppRiskRow> = emptyList(),
    val rawAppGroups: List<AppGroupRow> = emptyList(),
    val todayAiTagCounts: List<AiTagCountRow> = emptyList(),
    val todayHourlyCounts: List<Int> = List(24) { 0 },
    val harassmentCount: Int = 0,
    val totalMessageCount: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PhantomCheckApp(
    requestedTab: MainTab? = null,
    onConsumedRequestedTab: () -> Unit = {},
    requestedEditTodoId: Long? = null,
    onConsumedEditTodoId: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val assistantSnackbarHostState = remember { SnackbarHostState() }

    var selectedTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(MainTab.MESSAGES) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchRiskFilter by remember { mutableStateOf(SearchRiskFilter.ALL) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val tabLastLoadedMs = remember { mutableStateMapOf<MainTab, Long>() }
    var lastLoadedFilter by remember { mutableStateOf<MessageFilter?>(null) }
    var lastLoadedRefreshTrigger by remember { mutableIntStateOf(-1) }

    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            selectedTab = requestedTab
            onConsumedRequestedTab()
        }
    }

    // 訊息 Tab 狀態
    var todayCount by remember { mutableIntStateOf(0) }
    var unreadCount by remember { mutableIntStateOf(0) }
    var todayScamCount by remember { mutableIntStateOf(0) }
    var appGroups by remember { mutableStateOf(listOf<AppGroupUi>()) }
    var favoritePackages by remember { mutableStateOf(PhantomPrefs.getFavoriteApps(appCtx)) }
    var mutedPackages by remember { mutableStateOf(PhantomPrefs.getMutedApps(appCtx)) }
    var availableTags by remember { mutableStateOf(listOf<TagEntity>()) }
    var tagCounts by remember { mutableStateOf(listOf<TagTotalCountRow>()) }
    var messageTagsMap by remember { mutableStateOf<Map<Long, List<MessageTagRow>>>(emptyMap()) }
    var showTagPickerSheet by remember { mutableStateOf(false) }
    var tagPickerQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf<MessageFilter>(MessageFilter.All) }

    // 分頁清單狀態（非「全部」篩選時顯示）
    var filteredMessages by remember { mutableStateOf(listOf<MessageEntity>()) }
    var hasMoreFiltered by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }

    // 搜尋 / 詐騙 / 統計狀態
    var searchResults by remember { mutableStateOf(listOf<MessageEntity>()) }
    var activeFilterKeywords by remember { mutableStateOf(listOf<String>()) }
    var statsState by remember { mutableStateOf(StatsUiState()) }
    var isLoading by remember { mutableStateOf(true) }
    var notedMsgIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var todoedMsgIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var actionTargetApp by remember { mutableStateOf<AppGroupUi?>(null) }
    var deleteAppConfirm by remember { mutableStateOf<AppGroupUi?>(null) }

    // 平面清單長按 Bottom Sheet & 單則刪除確認
    var messageSheetTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var sheetMsgTagIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var isAnalyzingSheetMsg by remember { mutableStateOf(false) }
    var deleteMessageConfirm by remember { mutableStateOf<MessageEntity?>(null) }
    var reminderTarget by remember { mutableStateOf<MessageEntity?>(null) }

    // 多選批次操作狀態
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedMessageIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    val haptic = rememberPhantomHaptic()

    // 新手引導狀態
    var onboardingCompleted by remember { mutableStateOf(PhantomPrefs.isOnboardingCompleted(appCtx)) }
    var onboardingStep by remember {
        mutableStateOf(OnboardingStep.fromIndex(PhantomPrefs.getOnboardingStep(appCtx)))
    }
    var targetBounds by remember { mutableStateOf<Map<String, Rect>>(emptyMap()) }
    var wasActionDialogOpenInStep3 by remember { mutableStateOf(false) }
    var isBatteryWhitelistOk by remember { mutableStateOf(isBatteryOptimizationIgnored(appCtx)) }
    var oemAutostartConfirmed by remember { mutableStateOf(PhantomPrefs.isOemAutostartConfirmed(appCtx)) }

    LaunchedEffect(onboardingStep) {
        PhantomPrefs.setOnboardingStep(appCtx, onboardingStep.index)
    }

    LaunchedEffect(activeFilter, selectedTab) {
        isMultiSelectMode = false
        selectedMessageIds = emptySet()
    }

    LaunchedEffect(actionTargetApp, onboardingStep, onboardingCompleted) {
        if (!onboardingCompleted && onboardingStep == OnboardingStep.LONG_PRESS) {
            if (actionTargetApp != null) {
                wasActionDialogOpenInStep3 = true
            } else if (wasActionDialogOpenInStep3) {
                wasActionDialogOpenInStep3 = false
                onboardingStep = OnboardingStep.SWIPE_DELETE
            }
        }
    }

    var isListenerEnabled by remember {
        mutableStateOf(isNotificationListenerEnabled(appCtx))
    }
    var hasPostNotifications by remember {
        mutableStateOf(isPostNotificationsGranted(appCtx))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPostNotifications = granted
        if (granted) {
            StatsForegroundService.startOrRefresh(appCtx)
        }
    }

    // Android 13+ 啟動時主動請求 POST_NOTIFICATIONS 權限
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotifications) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var tabBarDisplayMode by remember { mutableStateOf(PhantomPrefs.getTabBarDisplayMode(appCtx)) }
    var tabBarVisible by remember { mutableStateOf(true) }
    var pendingUpdateDialog by remember { mutableStateOf<UpdateCheckResult.UpdateAvailable?>(null) }
    LaunchedEffect(selectedTab, tabBarDisplayMode) {
        tabBarVisible = true
    }
    val tabBarScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (tabBarDisplayMode == TabBarDisplayMode.AUTO_HIDE) {
                    if (available.y < -12f) {
                        tabBarVisible = false
                    } else if (available.y > 12f) {
                        tabBarVisible = true
                    }
                }
                return Offset.Zero
            }
        }
    }

    // 回到前景時重新檢查權限與偏好，不再自動把全部訊息標已讀
    val lifecycleOwner = ctx as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isListenerEnabled = isNotificationListenerEnabled(appCtx)
                hasPostNotifications = isPostNotificationsGranted(appCtx)
                isBatteryWhitelistOk = isBatteryOptimizationIgnored(appCtx)
                oemAutostartConfirmed = PhantomPrefs.isOemAutostartConfirmed(appCtx)
                favoritePackages = PhantomPrefs.getFavoriteApps(appCtx)
                mutedPackages = PhantomPrefs.getMutedApps(appCtx)
                tabBarDisplayMode = PhantomPrefs.getTabBarDisplayMode(appCtx)
                val completedNow = PhantomPrefs.isOnboardingCompleted(appCtx)
                if (onboardingCompleted && !completedNow) {
                    onboardingCompleted = false
                    onboardingStep = OnboardingStep.PERMISSION
                    PhantomPrefs.setOnboardingStep(appCtx, OnboardingStep.PERMISSION.index)
                }
                refreshTrigger++
                if (PhantomPrefs.isUpdateAutoCheckEnabled(appCtx)) {
                    scope.launch {
                        val found = AppUpdateHelper.checkAndNotifyIfNeeded(appCtx)
                        if (found != null) {
                            pendingUpdateDialog = found
                        }
                    }
                }
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }

    suspend fun queryFilteredPage(offset: Int): List<MessageEntity> = withContext(Dispatchers.IO) {
        val dao = AppDatabaseHolder.db(appCtx).messageDao()
        when (val f = activeFilter) {
            MessageFilter.All -> dao.paged(PAGE_SIZE, offset)
            MessageFilter.Unread -> dao.unreadPaged(PAGE_SIZE, offset)
            MessageFilter.Starred -> dao.starredPaged(PAGE_SIZE, offset)
            MessageFilter.Scams -> dao.scamsPaged(PAGE_SIZE, offset)
            MessageFilter.Filtered -> dao.filteredPaged(PAGE_SIZE, offset)
            MessageFilter.Noted -> dao.notedPaged(PAGE_SIZE, offset)
            MessageFilter.Todoed -> dao.todoedPaged(PAGE_SIZE, offset)
            is MessageFilter.Tag -> dao.byTagPaged(f.tag.id, PAGE_SIZE, offset)
        }
    }

    // 依分頁、篩選或搜尋詞載入資料（15s TTL 快取，切換分頁不重複查詢 DB）
    LaunchedEffect(selectedTab, searchQuery, activeFilter, refreshTrigger) {
        val q = searchQuery.trim()
        val now = System.currentTimeMillis()
        val filterChanged = lastLoadedFilter != activeFilter
        val refreshChanged = lastLoadedRefreshTrigger != refreshTrigger
        if (q.isEmpty() && !filterChanged && !refreshChanged) {
            val lastMs = tabLastLoadedMs[selectedTab] ?: 0L
            if (now - lastMs < 15_000L) {
                isLoading = false
                return@LaunchedEffect
            }
        }
        if (q.isNotEmpty()) {
            delay(300L)
        }
        isLoading = true
        withContext(Dispatchers.IO) {
            val db = AppDatabaseHolder.db(appCtx)
            val dao = db.messageDao()
            val startOfDay = NotificationCollectorService.startOfTodayMillis()

            if (q.isNotEmpty()) {
                val found = dao.search(q)
                val keywords = if (found.any { it.isFiltered }) {
                    db.filterDao().active().map { it.keyword }
                } else {
                    emptyList()
                }
                val foundTags = if (found.isEmpty()) {
                    emptyMap()
                } else {
                    db.tagDao().tagsForMessages(found.map { it.id }).groupBy { it.messageId }
                }
                notedMsgIds = dao.notedMessageIds().toSet()
                todoedMsgIds = dao.todoedMessageIds().toSet()
                searchResults = found
                activeFilterKeywords = keywords
                messageTagsMap = foundTags
            } else {
                when (selectedTab) {
                    MainTab.MESSAGES -> {
                        val overview = dao.statsOverview(startOfDay)
                        todayCount = overview.today
                        unreadCount = overview.unread
                        todayScamCount = overview.scam
                        availableTags = db.tagDao().all()
                        tagCounts = db.tagDao().allTagCounts()
                        notedMsgIds = dao.notedMessageIds().toSet()
                        todoedMsgIds = dao.todoedMessageIds().toSet()

                        if (activeFilter == MessageFilter.All) {
                            val groups = dao.groupByApp()
                            val todayMap = dao.todayCountsByApp(startOfDay)
                                .associate { it.packageName to it.cnt }
                            val unreadTodayMap = dao.todayUnreadCountsByApp(startOfDay)
                                .associate { it.packageName to it.cnt }
                            val totalUnreadMap = dao.unreadCountsByApp()
                                .associate { it.packageName to it.cnt }
                            val recentMsgs = dao.paged(PAGE_SIZE, 0)
                            val latestPreviewByPkg = recentMsgs
                                .groupBy { it.packageName }
                                .mapValues { (_, list) -> list.maxByOrNull { it.timestamp }?.content.orEmpty() }
                            val latestIdsByApp = dao.latestMessageIdsByApp()
                                .associate { it.packageName to it.messageId }
                            val latestMsgIds = latestIdsByApp.values.distinct()
                            val tagsByLatestMsgId = if (latestMsgIds.isEmpty()) {
                                emptyMap()
                            } else {
                                db.tagDao().tagsForMessages(latestMsgIds).groupBy { it.messageId }
                            }
                            val sortOrder = PhantomPrefs.getHomeSortOrder(appCtx)
                            appGroups = groups.map { g ->
                                val preview = latestPreviewByPkg[g.packageName]
                                    ?: dao.byApp(g.packageName).firstOrNull()?.content.orEmpty()
                                val latestMsgId = latestIdsByApp[g.packageName]
                                AppGroupUi(
                                    packageName = g.packageName,
                                    appLabel = g.appLabel.ifBlank { g.packageName },
                                    totalCount = g.cnt,
                                    todayCount = todayMap[g.packageName] ?: 0,
                                    unreadTodayCount = unreadTodayMap[g.packageName] ?: 0,
                                    totalUnreadCount = totalUnreadMap[g.packageName] ?: 0,
                                    lastTs = g.lastTs,
                                    lastPreview = preview,
                                    latestTags = latestMsgId?.let { tagsByLatestMsgId[it] }.orEmpty(),
                                    riskScore = g.riskScore
                                )
                            }.let { list ->
                                when (sortOrder) {
                                    com.phantom.checker.util.HomeSortOrder.RECENT ->
                                        list.sortedByDescending { it.lastTs }
                                    com.phantom.checker.util.HomeSortOrder.UNREAD ->
                                        list.sortedWith(compareByDescending<AppGroupUi> { it.totalUnreadCount }.thenByDescending { it.lastTs })
                                    com.phantom.checker.util.HomeSortOrder.RISK ->
                                        list.sortedWith(compareByDescending<AppGroupUi> { it.riskScore }.thenByDescending { it.lastTs })
                                }
                            }
                        } else {
                            val page = queryFilteredPage(0)
                            val pageTags = if (page.isEmpty()) {
                                emptyMap()
                            } else {
                                db.tagDao().tagsForMessages(page.map { it.id }).groupBy { it.messageId }
                            }
                            filteredMessages = page
                            messageTagsMap = pageTags
                            hasMoreFiltered = page.size >= PAGE_SIZE
                        }
                    }
                    MainTab.ASSISTANT -> Unit
                    MainTab.NOTES -> Unit
                    MainTab.STATS -> {
                        val summary = db.aiSummaryDao().latest()
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                            add(Calendar.DAY_OF_YEAR, -6)
                        }
                        val sevenDaysAgo = cal.timeInMillis
                        val countsByDayKey = dao.countByDaySince(sevenDaysAgo).associate { it.dayKey to it.cnt }
                        val dayFormat = SimpleDateFormat("M/d", Locale.getDefault())
                        val dayKeyFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
                        val weekly = (0..6).map { offset ->
                            val dayCal = Calendar.getInstance().apply {
                                timeInMillis = sevenDaysAgo
                                add(Calendar.DAY_OF_YEAR, offset)
                            }
                            val key = dayKeyFormat.format(dayCal.time)
                            DayCountUi(
                                label = dayFormat.format(dayCal.time),
                                count = countsByDayKey[key] ?: 0
                            )
                        }
                        val hourly = IntArray(24)
                        dao.countByHourSince(startOfDay).forEach { row ->
                            if (row.hour in 0..23) {
                                hourly[row.hour] = row.cnt
                            }
                        }
                        val safe = dao.countByRiskLabel("safe") + dao.countByRiskLabel("")
                        val suspicious = dao.countByRiskLabel("suspicious")
                        val scam = dao.countByRiskLabel("scam")
                        val harassmentTagIds = db.tagDao().all()
                            .filter { it.name == "行銷" || it.name == "垃圾通知" }
                            .map { it.id }
                            .toSet()
                        val harassmentCnt = if (harassmentTagIds.isEmpty()) {
                            0
                        } else {
                            db.tagDao().allCrossRefs()
                                .filter { it.tagId in harassmentTagIds }
                                .map { it.messageId }
                                .toSet()
                                .size
                        }
                        val topRisky = dao.topRiskyApps()
                        val rawGroups = dao.groupByApp()
                        val aiTagCounts = db.tagDao().todayAiTagCounts(startOfDay)
                        val weeklySummary = db.aiSummaryDao().latestWeekly()
                        statsState = StatsUiState(
                            latestSummary = summary,
                            latestWeeklySummary = weeklySummary,
                            weeklyCounts = weekly,
                            safeCount = safe,
                            suspiciousCount = suspicious,
                            scamCount = scam,
                            topRiskyApps = topRisky,
                            rawAppGroups = rawGroups,
                            todayAiTagCounts = aiTagCounts,
                            todayHourlyCounts = hourly.toList(),
                            harassmentCount = harassmentCnt,
                            totalMessageCount = safe + suspicious + scam
                        )
                    }
                }
            }
        }
        if (q.isEmpty()) {
            tabLastLoadedMs[selectedTab] = System.currentTimeMillis()
            lastLoadedFilter = activeFilter
            lastLoadedRefreshTrigger = refreshTrigger
        }
        isLoading = false
    }

    pendingUpdateDialog?.let { update ->
        AlertDialog(
            onDismissRequest = { pendingUpdateDialog = null },
            title = { Text("發現新版本") },
            text = {
                Text(
                    "已推出新版本 v${update.latestVersion}（發佈於 ${update.publishedDate}）\n\n" +
                        "不想再收到更新提醒？可至 設定 → 關於 關閉自動檢查更新。"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingUpdateDialog = null
                        ctx.startActivity(
                            Intent(ctx, SettingsActivity::class.java)
                                .putExtra("highlight_card_key", "about")
                        )
                    }
                ) {
                    Text("前往下載")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUpdateDialog = null }) {
                    Text("稍後再說")
                }
            }
        )
    }

    // 長按 App 動作對話框（釘選最愛／靜音或解除靜音／刪除此 App 所有訊息）
    actionTargetApp?.let { target ->
        val isFav = target.packageName in favoritePackages
        val isMuted = target.packageName in mutedPackages
        AlertDialog(
            onDismissRequest = { actionTargetApp = null },
            title = { Text(target.appLabel) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("選擇要對「${target.appLabel}」執行的操作：")
                    OutlinedButton(
                        onClick = {
                            deleteAppConfirm = target
                            actionTargetApp = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("刪除此 App 的所有訊息", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val pinned = PhantomPrefs.toggleFavoriteApp(appCtx, target.packageName)
                    favoritePackages = PhantomPrefs.getFavoriteApps(appCtx)
                    actionTargetApp = null
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (pinned) "已釘選「${target.appLabel}」至我的最愛" else "已取消釘選「${target.appLabel}」"
                        )
                    }
                }) {
                    Text(if (isFav) "取消最愛釘選" else "釘選至我的最愛")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (isMuted) {
                        PhantomPrefs.setAppMuted(appCtx, target.packageName, false)
                        mutedPackages = PhantomPrefs.getMutedApps(appCtx)
                        actionTargetApp = null
                        scope.launch {
                            snackbarHostState.showSnackbar("已解除靜音「${target.appLabel}」")
                        }
                    } else {
                        PhantomPrefs.setAppMuted(
                            appCtx,
                            target.packageName,
                            true,
                            MuteMode.BLOCK_RECORD
                        )
                        mutedPackages = PhantomPrefs.getMutedApps(appCtx)
                        actionTargetApp = null
                        scope.launch {
                            snackbarHostState.showSnackbar("已靜音「${target.appLabel}」（不再記錄新通知）")
                        }
                    }
                }) {
                    Text(
                        text = if (isMuted) "解除靜音" else "靜音此 App",
                        color = if (isMuted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        )
    }

    deleteAppConfirm?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteAppConfirm = null },
            title = { Text("刪除此 App 的所有訊息") },
            text = { Text("確定要永久刪除「${target.appLabel}」的所有訊息嗎？此操作無法復原。") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                db.withTransaction {
                                    db.tagDao().deleteCrossRefsByApp(target.packageName)
                                    db.messageDao().deleteByApp(target.packageName)
                                }
                            }
                            StatsForegroundService.startOrRefresh(appCtx)
                            deleteAppConfirm = null
                            refreshTrigger++
                            snackbarHostState.showSnackbar("已刪除「${target.appLabel}」的所有訊息")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確認刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteAppConfirm = null }) {
                    Text("取消")
                }
            }
        )
    }

    deleteMessageConfirm?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteMessageConfirm = null },
            title = { Text("刪除訊息") },
            text = { Text("確定要永久刪除這則來自「${target.sender}」的訊息嗎？") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                db.withTransaction {
                                    db.tagDao().deleteCrossRefsByMessage(target.id)
                                    db.messageDao().deleteById(target.id)
                                }
                            }
                            StatsForegroundService.startOrRefresh(appCtx)
                            deleteMessageConfirm = null
                            refreshTrigger++
                            snackbarHostState.showSnackbar("已刪除訊息")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確認刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteMessageConfirm = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showBatchDeleteConfirm && selectedMessageIds.isNotEmpty()) {
        val count = selectedMessageIds.size
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("批次刪除訊息") },
            text = { Text("確定要永久刪除已選取的 $count 則訊息嗎？此操作無法復原。") },
            confirmButton = {
                Button(
                    onClick = {
                        val ids = selectedMessageIds.toList()
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                db.withTransaction {
                                    db.tagDao().deleteCrossRefsByMessageIds(ids)
                                    db.messageDao().deleteByIds(ids)
                                }
                            }
                            StatsForegroundService.startOrRefresh(appCtx)
                            showBatchDeleteConfirm = false
                            isMultiSelectMode = false
                            selectedMessageIds = emptySet()
                            refreshTrigger++
                            snackbarHostState.showSnackbar("已刪除 $count 則訊息")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確認刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    messageSheetTarget?.let { msg ->
        LaunchedEffect(msg.id) {
            sheetMsgTagIds = withContext(Dispatchers.IO) {
                AppDatabaseHolder.db(appCtx).tagDao().tagsForMessage(msg.id).map { it.id }.toSet()
            }
        }
        ModalBottomSheet(onDismissRequest = { messageSheetTarget = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "訊息操作",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val nextStar = !msg.isStarred
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    AppDatabaseHolder.db(appCtx).messageDao().setStarred(msg.id, nextStar)
                                }
                                messageSheetTarget = null
                                refreshTrigger++
                                snackbarHostState.showSnackbar(if (nextStar) "已加入星標" else "已移除星標")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (msg.isStarred) "取消星標" else "加入星標")
                    }
                    OutlinedButton(
                        onClick = {
                            if (isAnalyzingSheetMsg) return@OutlinedButton
                            scope.launch {
                                isAnalyzingSheetMsg = true
                                val autoTagEnabled = PhantomPrefs.isAiAutoTagEnabled(appCtx)
                                val (aiTags, aiRules) = withContext(Dispatchers.IO) {
                                    val db = AppDatabaseHolder.db(appCtx)
                                    val tags = if (autoTagEnabled) db.tagDao().aiManagedTags() else emptyList()
                                    val rules = db.aiRuleDao().active()
                                    tags to rules
                                }
                                val res = AiClient.analyzeMessage(
                                    sender = msg.sender,
                                    content = msg.content,
                                    tags = aiTags,
                                    rules = aiRules,
                                    enableAutoTag = autoTagEnabled,
                                    context = appCtx,
                                    bypassCache = true
                                )
                                isAnalyzingSheetMsg = false
                                if (res != null) {
                                    withContext(Dispatchers.IO) {
                                        val db = AppDatabaseHolder.db(appCtx)
                                        db.withTransaction {
                                            db.messageDao().update(
                                                msg.copy(
                                                    riskScore = res.riskScore,
                                                    riskLabel = res.riskLabel,
                                                    riskReason = res.riskReason
                                                )
                                            )
                                            if (autoTagEnabled && res.tagIds.isNotEmpty()) {
                                                val manualIds = db.tagDao().crossRefsForMessage(msg.id)
                                                    .filter { it.source == MessageTagCrossRef.SOURCE_MANUAL }
                                                    .map { it.tagId }
                                                    .toSet()
                                                val refs = res.tagIds
                                                    .filter { it !in manualIds }
                                                    .map { tid -> MessageTagCrossRef(msg.id, tid, MessageTagCrossRef.SOURCE_AUTO) }
                                                if (refs.isNotEmpty()) {
                                                    db.tagDao().addTagsToMessageBatch(refs)
                                                }
                                            }
                                        }
                                    }
                                    messageSheetTarget = null
                                    refreshTrigger++
                                    snackbarHostState.showSnackbar("AI 分析完成：${res.riskLabel} (${res.riskScore}分)")
                                } else {
                                    snackbarHostState.showSnackbar("AI 分析失敗，請確認 API 設定")
                                }
                            }
                        },
                        enabled = !isAnalyzingSheetMsg,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isAnalyzingSheetMsg) "分析中…" else "AI 分析")
                    }
                }

                if (availableTags.isNotEmpty()) {
                    Text("標籤", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        availableTags.forEach { tag ->
                            val selected = tag.id in sheetMsgTagIds
                            val tagColor = parseTagColor(tag.colorHex)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            val tagDao = AppDatabaseHolder.db(appCtx).tagDao()
                                            if (selected) {
                                                tagDao.removeTagFromMessage(msg.id, tag.id)
                                            } else {
                                                tagDao.addTagToMessage(
                                                    MessageTagCrossRef(msg.id, tag.id, MessageTagCrossRef.SOURCE_MANUAL)
                                                )
                                            }
                                        }
                                        sheetMsgTagIds = if (selected) sheetMsgTagIds - tag.id else sheetMsgTagIds + tag.id
                                        refreshTrigger++
                                    }
                                },
                                label = { Text(tag.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = tagColor.copy(alpha = 0.22f)
                                )
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            messageSheetTarget = null
                            reminderTarget = msg
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("稍後處理")
                    }
                    OutlinedButton(
                        onClick = {
                            messageSheetTarget = null
                            ctx.startActivity(MessageDetailActivity.createIntent(ctx, msg.id))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("詳細")
                    }
                    Button(
                        onClick = {
                            messageSheetTarget = null
                            deleteMessageConfirm = msg
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("刪除")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    reminderTarget?.let { target ->
        ReminderPickerDialog(
            onDismiss = { reminderTarget = null },
            onSelectTimestamp = { ts ->
                reminderTarget = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        AppDatabaseHolder.db(appCtx).messageDao().setRemindAt(target.id, ts)
                    }
                    com.phantom.checker.service.ReminderScheduler.schedule(appCtx, target.id, ts)
                    refreshTrigger++
                    val formatted = PhantomPrefs.formatMessageTime(appCtx, ts, includeDate = true)
                    snackbarHostState.showSnackbar("已排程提醒：$formatted")
                }
            }
        )
    }

    if (showTagPickerSheet) {
        ModalBottomSheet(onDismissRequest = { showTagPickerSheet = false }) {
            val filteredTagRows = remember(tagCounts, tagPickerQuery) {
                val q = tagPickerQuery.trim()
                if (q.isEmpty()) tagCounts
                else tagCounts.filter { it.name.contains(q, ignoreCase = true) }
            }
            val selectedTagId = (activeFilter as? MessageFilter.Tag)?.tag?.id
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "依標籤篩選",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (selectedTagId != null) {
                        TextButton(onClick = {
                            activeFilter = MessageFilter.All
                            showTagPickerSheet = false
                        }) {
                            Text("清除標籤篩選")
                        }
                    }
                }
                OutlinedTextField(
                    value = tagPickerQuery,
                    onValueChange = { tagPickerQuery = it },
                    placeholder = { Text("搜尋標籤名稱…") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (tagPickerQuery.isNotEmpty()) {
                            IconButton(onClick = { tagPickerQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "清除")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (filteredTagRows.isEmpty()) {
                    Text(
                        text = if (tagCounts.isEmpty()) "尚未建立任何標籤" else "找不到符合「$tagPickerQuery」的標籤",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredTagRows, key = { it.tagId }) { row ->
                            val isSelected = selectedTagId == row.tagId
                            val tagColor = parseTagColor(row.colorHex)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) {
                                    tagColor.copy(alpha = 0.16f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        activeFilter = MessageFilter.Tag(
                                            TagEntity(
                                                id = row.tagId,
                                                name = row.name,
                                                colorHex = row.colorHex
                                            )
                                        )
                                        showTagPickerSheet = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(tagColor)
                                        )
                                        Text(
                                            text = row.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "${row.cnt} 則",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "已選取",
                                                tint = tagColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
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

    val glassState = remember { HazeState() }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(tabBarScrollConnection),
            bottomBar = {
                if (isMultiSelectMode) {
                    Surface(
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                enabled = selectedMessageIds.isNotEmpty(),
                                onClick = {
                                    val ids = selectedMessageIds.toList()
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            AppDatabaseHolder.db(appCtx).messageDao().markReadByIds(ids)
                                        }
                                        StatsForegroundService.startOrRefresh(appCtx)
                                        isMultiSelectMode = false
                                        selectedMessageIds = emptySet()
                                        refreshTrigger++
                                        snackbarHostState.showSnackbar("已標記 ${ids.size} 則為已讀")
                                    }
                                }
                            ) { Text("已讀") }
                            TextButton(
                                enabled = selectedMessageIds.isNotEmpty(),
                                onClick = {
                                    val ids = selectedMessageIds.toList()
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            AppDatabaseHolder.db(appCtx).messageDao().setStarredByIds(ids, true)
                                        }
                                        isMultiSelectMode = false
                                        selectedMessageIds = emptySet()
                                        refreshTrigger++
                                        snackbarHostState.showSnackbar("已將 ${ids.size} 則加入星標")
                                    }
                                }
                            ) { Text("星標") }
                            TextButton(
                                enabled = selectedMessageIds.isNotEmpty(),
                                onClick = { showBatchDeleteConfirm = true }
                            ) { Text("刪除", color = MaterialTheme.colorScheme.error) }
                            TextButton(
                                onClick = {
                                    isMultiSelectMode = false
                                    selectedMessageIds = emptySet()
                                }
                            ) { Text("取消") }
                        }
                    }
                } else {
                    val isCompactTabBar = tabBarDisplayMode == TabBarDisplayMode.COMPACT
                    AnimatedVisibility(
                        visible = tabBarDisplayMode != TabBarDisplayMode.AUTO_HIDE || tabBarVisible,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        LiquidGlassTabBar(
                            tabCount = MainTab.entries.size,
                            selectedTabIndex = selectedTab.ordinal,
                            glassState = glassState,
                            compact = isCompactTabBar,
                            onSelect = { index ->
                                haptic(HapticFeedbackType.TextHandleMove)
                                selectedTab = MainTab.entries[index]
                            },
                            tabs = {
                                MainTab.entries.forEach { tab ->
                                    val icon = when (tab) {
                                        MainTab.MESSAGES -> Icons.Default.Email
                                        MainTab.ASSISTANT -> AutoAwesomeIcon
                                        MainTab.NOTES -> Icons.Default.Edit
                                        MainTab.STATS -> Icons.Default.Info
                                    }
                                    val targetKey = when (tab) {
                                        MainTab.ASSISTANT -> OnboardingTargets.AI_ASSISTANT_TAB
                                        MainTab.NOTES -> OnboardingTargets.NOTES_TAB
                                        MainTab.STATS -> OnboardingTargets.STATS_TAB
                                        MainTab.MESSAGES -> null
                                    }
                                    val itemModifier = if (targetKey != null) {
                                        Modifier
                                            .weight(1f)
                                            .onGloballyPositioned { coords ->
                                                targetBounds = targetBounds + (targetKey to coords.boundsInRoot())
                                            }
                                    } else {
                                        Modifier.weight(1f)
                                    }
                                    LiquidGlassTabBarItem(
                                        selected = selectedTab == tab,
                                        onClick = {
                                            haptic(HapticFeedbackType.TextHandleMove)
                                            selectedTab = tab
                                        },
                                        icon = icon,
                                        label = tab.title,
                                        modifier = itemModifier,
                                        compact = isCompactTabBar
                                    )
                                }
                            }
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        PhantomBackgroundBox(
            layer = if (selectedTab == MainTab.ASSISTANT) {
                com.phantom.checker.util.BackgroundLayer.CHAT
            } else {
                com.phantom.checker.util.BackgroundLayer.GLOBAL
            },
            glassState = glassState,
            modifier = Modifier.fillMaxSize()
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 頂部下拉搜尋把手（切到 AI 助手分頁時平滑隱藏）
            AnimatedVisibility(
                visible = selectedTab != MainTab.ASSISTANT,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
            ) {
                PhantomPullSearchHandle(
                    glassState = glassState,
                    onOpenSearch = { showSearchBar = true },
                    modifier = Modifier.onGloballyPositioned { coords ->
                        targetBounds = targetBounds + (OnboardingTargets.SEARCH_PILL to coords.boundsInRoot())
                    }
                )
            }

            if (isLoading) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (!isListenerEnabled) {
                Spacer(Modifier.height(8.dp))
                NotificationListenerGuideCard(
                    onOpenSettings = {
                        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    glassState = glassState
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotifications) {
                Spacer(Modifier.height(8.dp))
                PostNotificationGuideCard(
                    onRequestPermission = {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    glassState = glassState
                )
            }

            Spacer(Modifier.height(10.dp))

            val toggleSelection: (Long) -> Unit = { id ->
                if (id in selectedMessageIds) {
                    selectedMessageIds = selectedMessageIds - id
                } else if (selectedMessageIds.size >= MAX_BATCH_SELECT) {
                    scope.launch {
                        snackbarHostState.showSnackbar("單次最多選取 $MAX_BATCH_SELECT 則訊息")
                    }
                } else {
                    selectedMessageIds = selectedMessageIds + id
                }
            }

                when (selectedTab) {
                    MainTab.MESSAGES -> MessagesHomeTab(
                        todayCount = todayCount,
                        unreadCount = unreadCount,
                        todayScamCount = todayScamCount,
                        favoritePackages = favoritePackages,
                        mutedPackages = mutedPackages,
                        appGroups = appGroups,
                        activeFilter = activeFilter,
                        filteredMessages = filteredMessages,
                        messageTagsMap = messageTagsMap,
                        notedMsgIds = notedMsgIds,
                        todoedMsgIds = todoedMsgIds,
                        hasMoreFiltered = hasMoreFiltered,
                        isLoading = isLoading,
                        isLoadingMore = isLoadingMore,
                        isListenerEnabled = isListenerEnabled,
                        glassState = glassState,
                        isMultiSelectMode = isMultiSelectMode,
                        selectedIds = selectedMessageIds,
                        onToggleMultiSelect = {
                            isMultiSelectMode = !isMultiSelectMode
                            if (!isMultiSelectMode) selectedMessageIds = emptySet()
                        },
                        onToggleSelect = toggleSelection,
                        onSelectFilter = { f -> activeFilter = f },
                        onOpenTagPicker = {
                            tagPickerQuery = ""
                            showTagPickerSheet = true
                        },
                        onMarkAllRead = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    AppDatabaseHolder.db(appCtx).messageDao().markAllRead()
                                }
                                StatsForegroundService.startOrRefresh(appCtx)
                                refreshTrigger++
                                snackbarHostState.showSnackbar("已將全部訊息標記為已讀")
                            }
                        },
                        onRefresh = { refreshTrigger++ },
                        onOpenSettings = {
                            ctx.startActivity(Intent(ctx, SettingsActivity::class.java))
                        },
                        onLoadMore = {
                            if (!isLoadingMore && hasMoreFiltered) {
                                scope.launch {
                                    isLoadingMore = true
                                    val next = queryFilteredPage(filteredMessages.size)
                                    val nextTags = if (next.isEmpty()) {
                                        emptyMap()
                                    } else {
                                        withContext(Dispatchers.IO) {
                                            AppDatabaseHolder.db(appCtx).tagDao()
                                                .tagsForMessages(next.map { it.id })
                                                .groupBy { it.messageId }
                                        }
                                    }
                                    filteredMessages = filteredMessages + next
                                    messageTagsMap = messageTagsMap + nextTags
                                    hasMoreFiltered = next.size >= PAGE_SIZE
                                    isLoadingMore = false
                                }
                            }
                        },
                        onOpenApp = { pkg, label ->
                            ctx.startActivity(AppThreadsActivity.createIntent(ctx, pkg, label))
                        },
                        onLongPressApp = { app ->
                            haptic(HapticFeedbackType.LongPress)
                            actionTargetApp = app
                        },
                        onLongPressFavorite = { pkg ->
                            haptic(HapticFeedbackType.LongPress)
                            PhantomPrefs.toggleFavoriteApp(appCtx, pkg)
                            favoritePackages = PhantomPrefs.getFavoriteApps(appCtx)
                            scope.launch {
                                snackbarHostState.showSnackbar("已取消最愛釘選")
                            }
                        },
                        onOpenMessageDetail = { msg ->
                            ctx.startActivity(MessageDetailActivity.createIntent(ctx, msg.id))
                        },
                        onLongPressMessage = { msg ->
                            haptic(HapticFeedbackType.LongPress)
                            messageSheetTarget = msg
                        },
                        onOpenNotificationAccess = {
                            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        onReportBounds = { key, rect ->
                            targetBounds = targetBounds + (key to rect)
                        }
                    )
                    MainTab.ASSISTANT -> Column(modifier = Modifier.fillMaxSize()) {
                        SnackbarHost(hostState = assistantSnackbarHostState)
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            ChatAssistantTab(
                                glassState = glassState,
                                onShowMessage = { msg ->
                                    scope.launch {
                                        assistantSnackbarHostState.showSnackbar(
                                            message = msg,
                                            withDismissAction = true,
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                },
                                isSearchActive = showSearchBar,
                                onToggleSearch = {
                                    showSearchBar = !showSearchBar
                                    if (!showSearchBar) {
                                        searchQuery = ""
                                        searchRiskFilter = SearchRiskFilter.ALL
                                    }
                                },
                                onRefresh = { refreshTrigger++ },
                                onOpenSettings = {
                                    ctx.startActivity(Intent(ctx, SettingsActivity::class.java))
                                }
                            )
                        }
                    }
                    MainTab.NOTES -> NotesTab(
                        glassState = glassState,
                        initialEditTodoId = requestedEditTodoId,
                        onConsumedEditTodoId = onConsumedEditTodoId,
                        onOpenMessageDetail = { messageId ->
                            ctx.startActivity(MessageDetailActivity.createIntent(ctx, messageId))
                        },
                        onShowMessage = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    )
                    MainTab.STATS -> StatsTab(
                        state = statsState,
                        glassState = glassState,
                        onSummaryGenerated = { refreshTrigger++ },
                        onShowMessage = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    )
                }
            }
        }
    }

        val overlayResults = remember(searchResults, searchRiskFilter) {
            when (searchRiskFilter) {
                SearchRiskFilter.ALL -> searchResults
                SearchRiskFilter.SCAM -> searchResults.filter { it.riskLabel == "scam" }
                SearchRiskFilter.SUSPICIOUS -> searchResults.filter { it.riskLabel == "suspicious" }
                SearchRiskFilter.STARRED -> searchResults.filter { it.isStarred }
            }
        }
        PhantomFullScreenSearchOverlay(
            visible = showSearchBar,
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onDismiss = { showSearchBar = false },
            placeholder = "搜尋寄件人、App 或訊息內容…",
            onAiSemanticSearch = {
                val q = searchQuery.trim()
                showSearchBar = false
                selectedTab = MainTab.ASSISTANT
                if (q.isNotEmpty()) {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("ai_search_query", q))
                    scope.launch {
                        snackbarHostState.showSnackbar("已切換至 AI 助手並複製「$q」，可直接貼上進行語意搜尋（消耗 1 次額度）")
                    }
                }
            },
            aiQuotaHint = "AI 語意搜尋（跳轉 AI 助手，消耗 1 次額度）",
            filterRow = {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SearchRiskFilter.entries, key = { it.name }) { filter ->
                        FilterChip(
                            selected = searchRiskFilter == filter,
                            onClick = { searchRiskFilter = filter },
                            label = { Text(filter.label) }
                        )
                    }
                }
            },
            resultsContent = {
                if (searchQuery.trim().isEmpty()) {
                    item(key = "search_hint") {
                        Text(
                            text = "輸入關鍵字即時搜尋訊息，或使用上方 AI 語意搜尋。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                } else if (overlayResults.isEmpty()) {
                    item(key = "search_empty") {
                        Text(
                            text = if (isLoading) "搜尋中…" else "找不到符合「${searchQuery.trim()}」的訊息。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                } else {
                    items(overlayResults, key = { it.id }) { msg ->
                        MessageCard(
                            message = msg,
                            glassState = glassState,
                            highlightAsScam = msg.riskLabel == "scam",
                            highlightQuery = searchQuery.trim(),
                            matchedKeywords = activeFilterKeywords.filter { kw ->
                                msg.content.contains(kw, ignoreCase = true) || msg.sender.contains(kw, ignoreCase = true)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showSearchBar = false
                                    ctx.startActivity(MessageDetailActivity.createIntent(ctx, msg.id))
                                }
                        )
                    }
                }
            }
        )

        if (tabBarDisplayMode == TabBarDisplayMode.AUTO_HIDE && !tabBarVisible && !isMultiSelectMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(28.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { tabBarVisible = true }
                    }
            )
        }

        if (!onboardingCompleted) {
            OnboardingOverlay(
                step = onboardingStep,
                isListenerEnabled = isListenerEnabled,
                isBatteryWhitelistOk = isBatteryWhitelistOk,
                oemAutostartConfirmed = oemAutostartConfirmed,
                targetBounds = targetBounds,
                glassState = glassState,
                onOpenNotificationSettings = {
                    ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                onOpenBatterySettings = {
                    openBatteryOptimizationSettings(ctx)
                },
                onConfirmOemAutostart = { confirmed ->
                    PhantomPrefs.setOemAutostartConfirmed(appCtx, confirmed)
                    oemAutostartConfirmed = confirmed
                },
                onOpenSettings = {
                    PhantomPrefs.setOnboardingCompleted(appCtx, true)
                    onboardingCompleted = true
                    ctx.startActivity(Intent(ctx, SettingsActivity::class.java))
                },
                onNextStep = {
                    onboardingStep = when (onboardingStep) {
                        OnboardingStep.PERMISSION -> OnboardingStep.HOME_APP
                        OnboardingStep.HOME_APP -> OnboardingStep.LONG_PRESS
                        OnboardingStep.LONG_PRESS -> OnboardingStep.SWIPE_DELETE
                        OnboardingStep.SWIPE_DELETE -> OnboardingStep.QUICK_REPLY
                        OnboardingStep.QUICK_REPLY -> OnboardingStep.MULTI_SELECT
                        OnboardingStep.MULTI_SELECT -> OnboardingStep.FILTER_CHIPS
                        OnboardingStep.FILTER_CHIPS -> OnboardingStep.AI_ASSISTANT
                        OnboardingStep.AI_ASSISTANT -> OnboardingStep.NOTES_TAB
                        OnboardingStep.NOTES_TAB -> OnboardingStep.MEMO_RULES
                        OnboardingStep.MEMO_RULES -> OnboardingStep.NOTED_TODOED_SOURCE
                        OnboardingStep.NOTED_TODOED_SOURCE -> OnboardingStep.STATS_AND_SUMMARY
                        OnboardingStep.STATS_AND_SUMMARY -> OnboardingStep.TAGS_AND_RULES
                        OnboardingStep.TAGS_AND_RULES -> OnboardingStep.TODO_AND_BATCH
                        OnboardingStep.TODO_AND_BATCH -> OnboardingStep.APPEARANCE_AND_BG
                        OnboardingStep.APPEARANCE_AND_BG -> OnboardingStep.KEEP_ALIVE
                        OnboardingStep.KEEP_ALIVE -> OnboardingStep.SEARCH_PILL
                        OnboardingStep.SEARCH_PILL -> OnboardingStep.DONE
                        OnboardingStep.DONE -> {
                            PhantomPrefs.setOnboardingCompleted(appCtx, true)
                            onboardingCompleted = true
                            OnboardingStep.DONE
                        }
                    }
                },
                onSkip = {
                    PhantomPrefs.setOnboardingCompleted(appCtx, true)
                    onboardingCompleted = true
                },
                onFinish = {
                    PhantomPrefs.setOnboardingCompleted(appCtx, true)
                    onboardingCompleted = true
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultsSection(
    query: String,
    isLoading: Boolean,
    results: List<MessageEntity>,
    activeFilterKeywords: List<String>,
    messageTagsMap: Map<Long, List<MessageTagRow>>,
    glassState: HazeState,
    isMultiSelectMode: Boolean,
    selectedIds: Set<Long>,
    onToggleMultiSelect: () -> Unit,
    onToggleSelect: (Long) -> Unit,
    onOpenDetail: (MessageEntity) -> Unit,
    onLongPressMessage: (MessageEntity) -> Unit,
    notedMsgIds: Set<Long> = emptySet(),
    todoedMsgIds: Set<Long> = emptySet()
) {
    when {
        isLoading && results.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        results.isEmpty() -> {
            EmptyStateView(
                icon = Icons.Default.Search,
                title = "找不到符合的訊息",
                description = "沒有符合「$query」或所選風險條件的訊息紀錄。"
            )
        }
        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isMultiSelectMode) {
                            "已選 ${selectedIds.size}/$MAX_BATCH_SELECT 則"
                        } else {
                            "搜尋結果 (${results.size})"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onToggleMultiSelect) {
                        Text(if (isMultiSelectMode) "完成" else "選取")
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(results, key = { it.id }) { m ->
                        val matchedKeywords = if (m.isFiltered) {
                            activeFilterKeywords.filter { kw ->
                                m.content.contains(kw, ignoreCase = true)
                            }
                        } else {
                            emptyList()
                        }
                        val msgTags = messageTagsMap[m.id].orEmpty()
                        val checked = m.id in selectedIds
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isMultiSelectMode) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { onToggleSelect(m.id) }
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                MessageCard(
                                    message = m,
                                    glassState = glassState,
                                    highlightAsScam = m.riskLabel == "scam",
                                    matchedKeywords = matchedKeywords,
                                    highlightQuery = query,
                                    hasNote = m.id in notedMsgIds,
                                    hasTodo = m.id in todoedMsgIds,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (isMultiSelectMode) onToggleSelect(m.id) else onOpenDetail(m)
                                            },
                                            onLongClick = {
                                                if (!isMultiSelectMode) onLongPressMessage(m)
                                            }
                                        )
                                )
                                if (msgTags.isNotEmpty()) {
                                    MessageTagChipRow(
                                        tags = msgTags,
                                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun MessagesHomeTab(
    todayCount: Int,
    unreadCount: Int,
    todayScamCount: Int,
    favoritePackages: List<String>,
    mutedPackages: Set<String>,
    appGroups: List<AppGroupUi>,
    activeFilter: MessageFilter,
    filteredMessages: List<MessageEntity>,
    messageTagsMap: Map<Long, List<MessageTagRow>>,
    notedMsgIds: Set<Long> = emptySet(),
    todoedMsgIds: Set<Long> = emptySet(),
    hasMoreFiltered: Boolean,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    isListenerEnabled: Boolean,
    glassState: HazeState,
    isMultiSelectMode: Boolean,
    selectedIds: Set<Long>,
    onToggleMultiSelect: () -> Unit,
    onToggleSelect: (Long) -> Unit,
    onSelectFilter: (MessageFilter) -> Unit,
    onOpenTagPicker: () -> Unit,
    onMarkAllRead: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenApp: (String, String) -> Unit,
    onLongPressApp: (AppGroupUi) -> Unit,
    onLongPressFavorite: (String) -> Unit,
    onOpenMessageDetail: (MessageEntity) -> Unit,
    onLongPressMessage: (MessageEntity) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onReportBounds: (String, Rect) -> Unit
) {
    val ctx = LocalContext.current
    val groupMap = remember(appGroups) { appGroups.associateBy { it.packageName } }
    val listState = rememberLazyListState()
    val pendingRetryCount by remember(ctx) {
        AppDatabaseHolder.db(ctx).pendingAiAnalysisDao().observePendingCount()
    }.collectAsState(initial = 0)
    var isRetryPaused by remember { mutableStateOf(PhantomPrefs.isAiOfflineRetryPaused(ctx)) }
    val offlineRetryEnabled = PhantomPrefs.isAiOfflineRetryEnabled(ctx)

    // 捲動到底部自動載入下一頁（非「全部」篩選模式）
    LaunchedEffect(listState, activeFilter, hasMoreFiltered, isLoadingMore) {
        if (activeFilter == MessageFilter.All || !hasMoreFiltered) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 3
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                onLoadMore()
            }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnimatedVisibility(visible = offlineRetryEnabled && pendingRetryCount > 0) {
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isRetryPaused) {
                                "已暫停補分析（待處理 $pendingRetryCount 則）"
                            } else {
                                "$pendingRetryCount 則訊息待 AI 補分析"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isRetryPaused) {
                                "點擊「繼續」於網路恢復後自動補分析"
                            } else {
                                "網路恢復後將自動排程補齊風險分析"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        TextButton(
                            onClick = {
                                if (isRetryPaused) {
                                    com.phantom.checker.service.PendingAiAnalysisPolicy.resume(ctx)
                                    isRetryPaused = false
                                } else {
                                    com.phantom.checker.service.PendingAiAnalysisPolicy.pause(ctx)
                                    isRetryPaused = true
                                }
                            }
                        ) {
                            Text(if (isRetryPaused) "繼續" else "暫停")
                        }
                        TextButton(
                            onClick = {
                                com.phantom.checker.service.PendingAiAnalysisPolicy.retryNow(ctx)
                                isRetryPaused = false
                            }
                        ) {
                            Text("立即重試")
                        }
                    }
                }
            }
        }

        // 頂部統計玻璃卡（固定）
        PhantomGlassCard(
            glassState = glassState,
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    onReportBounds(OnboardingTargets.STATS_CARD, coords.boundsInRoot())
                },
            cornerRadius = 20.dp,
            contentPadding = 14.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "今日 $todayCount 則・未讀 $unreadCount・詐騙 $todayScamCount",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "點選 App 查看訊息；長按可釘選、靜音或刪除",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "重新整理",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "設定",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    if (unreadCount > 0) {
                        TextButton(onClick = onMarkAllRead) {
                            Text("全部標已讀")
                        }
                    }
                }
            }
        }

        // 快速篩選 Chip 列（固定，FlowRow 自動換行，免橫向滑動全部可見；全部與未讀固定顯示）
        val activeTag = (activeFilter as? MessageFilter.Tag)?.tag
        val visibleFilters = PhantomPrefs.getVisibleHomeFilters(ctx)
        val chipShape = RoundedCornerShape(8.dp)
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    onReportBounds(OnboardingTargets.FILTER_CHIPS, coords.boundsInRoot())
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = activeFilter == MessageFilter.All,
                onClick = { onSelectFilter(MessageFilter.All) },
                shape = chipShape,
                modifier = Modifier.height(32.dp),
                label = { Text("全部", style = MaterialTheme.typography.labelMedium) }
            )
            FilterChip(
                selected = activeFilter == MessageFilter.Unread,
                onClick = { onSelectFilter(MessageFilter.Unread) },
                shape = chipShape,
                modifier = Modifier.height(32.dp),
                label = { Text("未讀", style = MaterialTheme.typography.labelMedium) }
            )
            if ("starred" in visibleFilters || activeFilter == MessageFilter.Starred) {
                FilterChip(
                    selected = activeFilter == MessageFilter.Starred,
                    onClick = { onSelectFilter(MessageFilter.Starred) },
                    shape = chipShape,
                    modifier = Modifier.height(32.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    label = { Text("星標", style = MaterialTheme.typography.labelMedium) }
                )
            }
            if ("scams" in visibleFilters || activeFilter == MessageFilter.Scams) {
                FilterChip(
                    selected = activeFilter == MessageFilter.Scams,
                    onClick = { onSelectFilter(MessageFilter.Scams) },
                    shape = chipShape,
                    modifier = Modifier.height(32.dp),
                    label = { Text("詐騙", style = MaterialTheme.typography.labelMedium) }
                )
            }
            if ("filtered" in visibleFilters || activeFilter == MessageFilter.Filtered) {
                FilterChip(
                    selected = activeFilter == MessageFilter.Filtered,
                    onClick = { onSelectFilter(MessageFilter.Filtered) },
                    shape = chipShape,
                    modifier = Modifier.height(32.dp),
                    label = { Text("已過濾", style = MaterialTheme.typography.labelMedium) }
                )
            }
            if ("noted" in visibleFilters || activeFilter == MessageFilter.Noted) {
                FilterChip(
                    selected = activeFilter == MessageFilter.Noted,
                    onClick = { onSelectFilter(MessageFilter.Noted) },
                    shape = chipShape,
                    modifier = Modifier.height(32.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    label = { Text("已記事", style = MaterialTheme.typography.labelMedium) }
                )
            }
            if ("todoed" in visibleFilters || activeFilter == MessageFilter.Todoed) {
                FilterChip(
                    selected = activeFilter == MessageFilter.Todoed,
                    onClick = { onSelectFilter(MessageFilter.Todoed) },
                    shape = chipShape,
                    modifier = Modifier.height(32.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    label = { Text("已待辦", style = MaterialTheme.typography.labelMedium) }
                )
            }
            if (activeTag != null) {
                val tagColor = parseTagColor(activeTag.colorHex)
                FilterChip(
                    selected = true,
                    onClick = onOpenTagPicker,
                    shape = chipShape,
                    modifier = Modifier.height(32.dp),
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(tagColor)
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "關閉標籤篩選",
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onSelectFilter(MessageFilter.All) }
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = tagColor.copy(alpha = 0.2f)
                    ),
                    label = { Text(activeTag.name, style = MaterialTheme.typography.labelMedium) }
                )
            }
            if ("tag" in visibleFilters || activeTag != null) {
                FilterChip(
                    selected = activeTag != null,
                    onClick = onOpenTagPicker,
                    shape = chipShape,
                    modifier = Modifier.height(32.dp),
                    label = { Text("標籤 ▾", style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        if (activeFilter == MessageFilter.All) {
            // 我的最愛 App 橫向列（固定）
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "我的最愛 App",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (favoritePackages.isEmpty()) {
                    Text(
                        text = "尚未釘選最愛 App（長按下方任一 App 即可釘選）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(favoritePackages, key = { it }) { pkg ->
                            val group = groupMap[pkg]
                            val label = group?.appLabel ?: try {
                                ctx.packageManager.getApplicationLabel(
                                    ctx.packageManager.getApplicationInfo(pkg, 0)
                                ).toString()
                            } catch (_: Exception) {
                                pkg
                            }
                            val unreadBadge = group?.totalUnreadCount ?: 0
                            val favTags = group?.latestTags.orEmpty()
                            PhantomGlassCard(
                                glassState = glassState,
                                modifier = Modifier
                                    .width(108.dp)
                                    .pressableScale()
                                    .combinedClickable(
                                        onClick = { onOpenApp(pkg, label) },
                                        onLongClick = { onLongPressFavorite(pkg) }
                                    ),
                                cornerRadius = 20.dp,
                                contentPadding = 10.dp
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box {
                                        AppIconImage(
                                            packageName = pkg,
                                            contentDescription = label,
                                            size = 38.dp
                                        )
                                        if (unreadBadge > 0) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.align(Alignment.TopEnd)
                                            ) {
                                                Text(
                                                    text = if (unreadBadge > 99) "99+" else "$unreadBadge",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (favTags.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        MessageTagChipRow(tags = favTags)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 可捲動訊息清單區
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (activeFilter == MessageFilter.All) {
                // App 分組清單
                when {
                    isLoading && appGroups.isEmpty() -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                appGroups.isEmpty() -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                        ) {
                            EmptyStateView(
                                icon = Icons.Default.Notifications,
                                title = "目前尚無攔截到的訊息",
                                description = if (isListenerEnabled) {
                                    "當支援的通訊 App 收到新通知時，將自動備份並顯示於此。"
                                } else {
                                    "請先開啟通知存取權限，幻訊才能即時備份通知並分析詐騙風險。"
                                },
                                actionLabel = if (!isListenerEnabled) "前往開啟通知存取" else null,
                                onAction = if (!isListenerEnabled) onOpenNotificationAccess else null
                            )
                        }
                    }
                }
                else -> {
                    itemsIndexed(appGroups, key = { _, app -> app.packageName }) { idx, app ->
                        val isMuted = app.packageName in mutedPackages
                        val isCompact = PhantomPrefs.getCardDensity(ctx) == com.phantom.checker.util.CardDensity.COMPACT
                        val is24h = PhantomPrefs.is24HourFormat(ctx)
                        val timeText = remember(app.lastTs, is24h) {
                            PhantomPrefs.formatMessageTime(ctx, app.lastTs, includeDate = true)
                        }
                        val rowModifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (idx == 0) {
                                    Modifier.onGloballyPositioned { coords ->
                                        onReportBounds(OnboardingTargets.FIRST_APP_ROW, coords.boundsInRoot())
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .alpha(if (isMuted) 0.5f else 1f)
                            .pressableScale()
                            .combinedClickable(
                                onClick = { onOpenApp(app.packageName, app.appLabel) },
                                onLongClick = { onLongPressApp(app) }
                            )
                        PhantomGlassCard(
                            glassState = glassState,
                            modifier = rowModifier,
                            cornerRadius = 20.dp,
                            contentPadding = if (isCompact) 8.dp else 12.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box {
                                    AppIconImage(
                                        packageName = app.packageName,
                                        contentDescription = app.appLabel,
                                        size = 44.dp
                                    )
                                    if (app.unreadTodayCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .align(Alignment.TopEnd)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.error)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = app.appLabel,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isMuted) {
                                                Spacer(Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant
                                                ) {
                                                    Text(
                                                        text = "靜音",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            } else if (app.unreadTodayCount > 0) {
                                                Spacer(Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.errorContainer
                                                ) {
                                                    Text(
                                                        text = "未讀 ${app.unreadTodayCount}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            } else if (app.todayCount > 0) {
                                                Spacer(Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        text = "今日 ${app.todayCount}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = timeText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = app.lastPreview.ifBlank { "共 ${app.totalCount} 則訊息" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (app.latestTags.isNotEmpty()) {
                                        Spacer(Modifier.height(6.dp))
                                        MessageTagChipRow(tags = app.latestTags)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // 分頁篩選訊息清單（未讀 / 星標 / 詐騙 / 已過濾 / 標籤）
            when {
                isLoading && filteredMessages.isEmpty() -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                filteredMessages.isEmpty() -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                        ) {
                            EmptyStateView(
                                icon = Icons.Default.Email,
                                title = "此篩選條件下無訊息",
                                description = "目前沒有符合條件的訊息紀錄。",
                                actionLabel = "返回全部訊息",
                                onAction = { onSelectFilter(MessageFilter.All) }
                            )
                        }
                    }
                }
                else -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isMultiSelectMode) {
                                    "已選 ${selectedIds.size}/$MAX_BATCH_SELECT 則"
                                } else {
                                    "共 ${filteredMessages.size} 則"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = onToggleMultiSelect,
                                modifier = Modifier.onGloballyPositioned { coords ->
                                    onReportBounds(OnboardingTargets.SELECT_BUTTON, coords.boundsInRoot())
                                }
                            ) {
                                Text(if (isMultiSelectMode) "完成" else "選取")
                            }
                        }
                    }
                    items(filteredMessages, key = { it.id }, contentType = { "message_card" }) { m ->
                        val checked = m.id in selectedIds
                        val msgTags = messageTagsMap[m.id].orEmpty()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isMultiSelectMode) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { onToggleSelect(m.id) }
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                MessageCard(
                                    message = m,
                                    glassState = glassState,
                                    highlightAsScam = m.riskLabel == "scam",
                                    hasNote = m.id in notedMsgIds,
                                    hasTodo = m.id in todoedMsgIds,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (isMultiSelectMode) onToggleSelect(m.id) else onOpenMessageDetail(m)
                                            },
                                            onLongClick = {
                                                if (!isMultiSelectMode) onLongPressMessage(m)
                                            }
                                        )
                                )
                                if (msgTags.isNotEmpty()) {
                                    MessageTagChipRow(
                                        tags = msgTags,
                                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

private enum class TodoFilterMode(val label: String) {
    ALL("全部"),
    PENDING("未完成"),
    DONE("已完成"),
    OVERDUE("逾期")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotesTab(
    glassState: HazeState,
    initialEditTodoId: Long? = null,
    onConsumedEditTodoId: () -> Unit = {},
    onOpenMessageDetail: (Long) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val db = remember(appCtx) { AppDatabaseHolder.db(appCtx) }

    val notes by remember(db) { db.noteDao().observeAll() }.collectAsState(initial = emptyList())
    val rawTodos by remember(db) { db.todoDao().observeAll() }.collectAsState(initial = emptyList())
    val memoRules by remember(db) { db.memoRuleDao().observeAll() }.collectAsState(initial = emptyList())
    val todos = remember(rawTodos) { ReminderScheduler.sortTodosSmart(rawTodos) }
    var todoFilter by remember { mutableStateOf(TodoFilterMode.ALL) }
    var showEditor by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<NoteEntity?>(null) }
    var showTodoEditor by remember { mutableStateOf(false) }
    var editingTodo by remember { mutableStateOf<TodoEntity?>(null) }
    var showManualMemoRuleDialog by remember { mutableStateOf(false) }
    var nlMemoRuleInput by remember { mutableStateOf("") }
    var isParsingMemoRule by remember { mutableStateOf(false) }
    var pendingNlMemoRule by remember { mutableStateOf<AiClient.ParsedMemoRule?>(null) }
    var neverMemoTargetMsg by remember { mutableStateOf<MessageEntity?>(null) }
    var neverMemoFallbackNote by remember { mutableStateOf<NoteEntity?>(null) }

    LaunchedEffect(initialEditTodoId, todos) {
        val targetId = initialEditTodoId ?: return@LaunchedEffect
        val found = todos.firstOrNull { it.id == targetId }
            ?: withContext(Dispatchers.IO) { db.todoDao().byId(targetId) }
        if (found != null) {
            editingTodo = found
            showTodoEditor = true
            onConsumedEditTodoId()
        }
    }

    if (showTodoEditor) {
        TodoEditorDialog(
            initial = editingTodo,
            onDismiss = {
                showTodoEditor = false
                editingTodo = null
            },
            onSave = { title, dueAt, priority, repeatRule, notesText ->
                val target = editingTodo
                showTodoEditor = false
                editingTodo = null
                scope.launch {
                    val savedId = withContext(Dispatchers.IO) {
                        if (target == null) {
                            db.todoDao().insert(
                                TodoEntity(
                                    title = title,
                                    dueAt = dueAt,
                                    isDone = false,
                                    createdAt = System.currentTimeMillis(),
                                    priority = priority,
                                    notes = notesText,
                                    repeatRule = repeatRule
                                )
                            )
                        } else {
                            db.todoDao().update(
                                target.copy(
                                    title = title,
                                    dueAt = dueAt,
                                    priority = priority,
                                    notes = notesText,
                                    repeatRule = repeatRule
                                )
                            )
                            target.id
                        }
                    }
                    if (target?.isDone != true) {
                        if (dueAt > System.currentTimeMillis()) {
                            ReminderScheduler.scheduleTodo(appCtx, savedId, dueAt)
                        } else {
                            ReminderScheduler.cancelTodo(appCtx, savedId)
                        }
                    }
                    com.phantom.checker.widget.ReminderWidgetProvider.updateAllIfChanged(appCtx)
                    onShowMessage(if (target == null) "已新增待辦事項" else "已更新待辦事項")
                }
            }
        )
    }

    if (showEditor) {
        NoteEditorDialog(
            initial = editingNote,
            onDismiss = {
                showEditor = false
                editingNote = null
            },
            onSave = { content, colorHex ->
                val target = editingNote
                showEditor = false
                editingNote = null
                scope.launch {
                    val now = System.currentTimeMillis()
                    withContext(Dispatchers.IO) {
                        if (target == null) {
                            db.noteDao().insert(
                                NoteEntity(
                                    content = content,
                                    colorHex = colorHex,
                                    source = NoteEntity.SOURCE_MANUAL,
                                    createdAt = now,
                                    updatedAt = now
                                )
                            )
                        } else {
                            db.noteDao().update(
                                target.copy(
                                    content = content,
                                    colorHex = colorHex,
                                    updatedAt = now
                                )
                            )
                        }
                    }
                    onShowMessage(if (target == null) "已新增記事" else "已更新記事")
                }
            }
        )
    }

    val now = System.currentTimeMillis()
    val startOfToday = NotificationCollectorService.startOfTodayMillis()
    val dayMs = 24L * 60L * 60L * 1000L
    val endOfToday = startOfToday + dayMs - 1L
    val filteredTodos = remember(todos, todoFilter, now) {
        val sorted = ReminderScheduler.sortTodosSmart(todos, now)
        when (todoFilter) {
            TodoFilterMode.ALL -> sorted
            TodoFilterMode.PENDING -> sorted.filter { !it.isDone }
            TodoFilterMode.DONE -> sorted.filter { it.isDone }
            TodoFilterMode.OVERDUE -> sorted.filter { !it.isDone && it.dueAt < now }
        }
    }

    var showNotesSearch by remember { mutableStateOf(false) }
    var notesSearchQuery by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "notes_search_pill") {
            PhantomSearchBarPill(
                glassState = glassState,
                query = notesSearchQuery,
                placeholder = "搜尋待辦或備忘錄…",
                onClick = { showNotesSearch = true }
            )
        }
        // 1. 待辦事項區（與 AI 助手、桌面小工具同步）
        item(key = "notes_todos_section") {
            val pendingCount = todos.count { !it.isDone }
            val overdueCount = todos.count { !it.isDone && it.dueAt < now }
            PhantomGlassCard(
                glassState = glassState,
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 14.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "待辦事項（$pendingCount 未完成" +
                                (if (overdueCount > 0) " · $overdueCount 逾期" else "") + "）",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "與 AI 助手及桌面小工具同步",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            editingTodo = null
                            showTodoEditor = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新增待辦事項",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TodoFilterMode.entries.forEach { mode ->
                        FilterChip(
                            selected = todoFilter == mode,
                            onClick = { todoFilter = mode },
                            label = {
                                val badge = when (mode) {
                                    TodoFilterMode.ALL -> todos.size
                                    TodoFilterMode.PENDING -> pendingCount
                                    TodoFilterMode.DONE -> todos.count { it.isDone }
                                    TodoFilterMode.OVERDUE -> overdueCount
                                }
                                Text("${mode.label}($badge)", style = MaterialTheme.typography.labelSmall)
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (filteredTodos.isEmpty()) {
                    Text(
                        text = if (todos.isEmpty()) {
                            "尚無待辦事項（點右上角 + 手動新增，或在 AI 助手對話中建立）"
                        } else {
                            "此篩選條件下無待辦事項"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    filteredTodos.forEachIndexed { idx, todo ->
                        if (idx > 0) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                        val isOverdue = !todo.isDone && todo.dueAt < now
                        val isDueToday = !todo.isDone && todo.dueAt in startOfToday..endOfToday && !isOverdue
                        val overdueDays = if (isOverdue) {
                            ((startOfToday - todo.dueAt) / dayMs).toInt().coerceAtLeast(0)
                        } else 0
                        val priorityColor = when (todo.priority) {
                            TodoEntity.PRIORITY_HIGH -> MaterialTheme.colorScheme.error
                            TodoEntity.PRIORITY_LOW -> Color(0xFF26A69A)
                            else -> Color(0xFFFFB300)
                        }
                        val overdueColor = Color(0xFFFF6D00)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    editingTodo = todo
                                    showTodoEditor = true
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = todo.isDone,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        val spawned = withContext(Dispatchers.IO) {
                                            ReminderScheduler.completeTodoWithRecurrence(appCtx, todo, checked)
                                        }
                                                            if (spawned != null) {
                                            onShowMessage("已完成並自動建立下一次重複待辦")
                                        }
                                    }
                                }
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(priorityColor)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = todo.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isOverdue) FontWeight.SemiBold else FontWeight.Normal,
                                    textDecoration = if (todo.isDone) {
                                        androidx.compose.ui.text.style.TextDecoration.LineThrough
                                    } else {
                                        null
                                    },
                                    color = when {
                                        todo.isDone -> MaterialTheme.colorScheme.onSurfaceVariant
                                        isOverdue -> overdueColor
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                val statusBadge = when {
                                    isOverdue -> if (overdueDays >= 1) "已逾期 $overdueDays 天 · " else "已逾期 · "
                                    isDueToday -> "今天到期 · "
                                    else -> ""
                                }
                                val repeatTag = when (todo.repeatRule) {
                                    TodoEntity.REPEAT_DAILY -> " · 每日"
                                    TodoEntity.REPEAT_WEEKLY -> " · 每週"
                                    else -> ""
                                }
                                Text(
                                    text = "${statusBadge}到期：${PhantomPrefs.formatMessageTime(ctx, todo.dueAt, includeDate = true)}$repeatTag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOverdue) overdueColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (todo.isDone && todo.completedAt != null) {
                                    Text(
                                        text = "完成於：${PhantomPrefs.formatMessageTime(ctx, todo.completedAt, includeDate = true)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (todo.notes.isNotBlank()) {
                                    Text(
                                        text = todo.notes,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (todo.relatedMessageId != null) {
                                IconButton(
                                    onClick = { onOpenMessageDetail(todo.relatedMessageId) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "查看來源訊息",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    editingTodo = todo
                                    showTodoEditor = true
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "編輯待辦",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        ReminderScheduler.cancelTodo(appCtx, todo.id)
                                        withContext(Dispatchers.IO) {
                                            db.todoDao().deleteById(todo.id)
                                        }
                                        com.phantom.checker.widget.ReminderWidgetProvider.updateAllIfChanged(appCtx)
                                                            onShowMessage("已刪除待辦事項")
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

        // 2. 記事／備忘錄標題列 + 新增按鈕
        item(key = "notes_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "記事／備忘錄（${notes.size}）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = {
                        editingNote = null
                        showEditor = true
                    },
                    modifier = Modifier.pressableScale()
                ) {
                    Text("新增記事")
                }
            }
        }

        if (notes.isEmpty()) {
            item(key = "notes_empty") {
                EmptyStateView(
                    icon = Icons.Default.Edit,
                    title = "尚無記事",
                    description = "點擊「新增記事」記錄備忘事項，或至設定頁開啟「自動備忘錄」。"
                )
            }
        } else {
            items(notes, key = { it.id }) { note ->
                val accentColor = remember(note.colorHex) {
                    note.colorHex?.let { hex ->
                        runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
                    }
                }
                val isAuto = note.source == NoteEntity.SOURCE_AUTO
                PhantomGlassCard(
                    glassState = glassState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isAuto) {
                                Modifier.combinedClickable(
                                    onClick = {
                                        if (note.relatedMessageId != null) {
                                            onOpenMessageDetail(note.relatedMessageId)
                                        }
                                    },
                                    onLongClick = {
                                        val relId = note.relatedMessageId
                                        if (relId != null) {
                                            scope.launch {
                                                val src = withContext(Dispatchers.IO) {
                                                    db.messageDao().byId(relId)
                                                }
                                                if (src != null) {
                                                    neverMemoTargetMsg = src
                                                } else {
                                                    neverMemoFallbackNote = note
                                                }
                                            }
                                        } else {
                                            neverMemoFallbackNote = note
                                        }
                                    }
                                )
                            } else {
                                Modifier
                            }
                        ),
                    cornerRadius = 18.dp,
                    contentPadding = 14.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        if (accentColor != null) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(accentColor)
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (note.pinned) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "置頂",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (isAuto) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "自動",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                val appLabel = note.relatedAppLabel
                                if (!appLabel.isNullOrBlank()) {
                                    Text(
                                        text = appLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (note.pinned || isAuto || !note.relatedAppLabel.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                            }
                            Text(
                                text = note.content,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = PhantomPrefs.formatMessageTime(ctx, note.updatedAt, includeDate = true),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            db.noteDao().update(
                                                note.copy(
                                                    pinned = !note.pinned,
                                                    updatedAt = System.currentTimeMillis()
                                                )
                                            )
                                        }
                                    }
                                }
                            ) {
                                Text(if (note.pinned) "取消置頂" else "置頂")
                            }
                            Row {
                                TextButton(
                                    onClick = {
                                        editingNote = note
                                        showEditor = true
                                    }
                                ) {
                                    Text("編輯")
                                }
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                db.noteDao().deleteById(note.id)
                                            }
                                            onShowMessage("已刪除記事")
                                        }
                                    }
                                ) {
                                    Text("刪除", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ===== 自動記事規則區（#203） =====
        item(key = "memo_rules_header") {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "自動記事規則 (${memoRules.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = { showManualMemoRuleDialog = true },
                    modifier = Modifier.pressableScale()
                ) {
                    Text("手動新增")
                }
            }
        }

        item(key = "memo_rules_nl_input") {
            PhantomGlassCard(
                glassState = glassState,
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 18.dp,
                contentPadding = 12.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "自然語言新增規則",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = nlMemoRuleInput,
                            onValueChange = { nlMemoRuleInput = it },
                            placeholder = { Text("例如：Gogolook 驗證碼不要加入待辦") },
                            singleLine = true,
                            enabled = !isParsingMemoRule,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val text = nlMemoRuleInput.trim()
                                if (text.isEmpty() || isParsingMemoRule) return@Button
                                isParsingMemoRule = true
                                scope.launch {
                                    val result = AiClient.parseMemoRule(appCtx, text)
                                    isParsingMemoRule = false
                                    result.fold(
                                        onSuccess = { parsed ->
                                            pendingNlMemoRule = parsed
                                        },
                                        onFailure = { err ->
                                            onShowMessage(err.message ?: "解析規則失敗")
                                        }
                                    )
                                }
                            },
                            enabled = nlMemoRuleInput.isNotBlank() && !isParsingMemoRule
                        ) {
                            if (isParsingMemoRule) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("送出")
                            }
                        }
                    }
                }
            }
        }

        if (memoRules.isEmpty()) {
            item(key = "memo_rules_empty") {
                PhantomGlassCard(
                    glassState = glassState,
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    contentPadding = 14.dp
                ) {
                    Text(
                        text = "尚無自動記事規則。可於上方用自然語言或「手動新增」設定哪些 App、寄件者或關鍵字要一律記事、一律待辦或永不加入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(memoRules, key = { "memo_rule_${it.id}" }) { rule ->
                val (badgeContainer, badgeContent, badgeText) = when (rule.action) {
                    com.phantom.checker.data.MemoRuleEntity.ACTION_NOTE -> Triple(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer,
                        "一律記事"
                    )
                    com.phantom.checker.data.MemoRuleEntity.ACTION_TODO -> Triple(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        "一律待辦"
                    )
                    else -> Triple(
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer,
                        "永不加入"
                    )
                }
                PhantomGlassCard(
                    glassState = glassState,
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    contentPadding = 12.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(color = badgeContainer, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = badgeContent,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = com.phantom.checker.util.MemoRuleEngine.formatConditionSummary(rule),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Switch(
                            checked = rule.isEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        db.memoRuleDao().update(rule.copy(isEnabled = enabled))
                                        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                                    }
                                }
                            }
                        )
                        IconButton(
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        db.memoRuleDao().deleteById(rule.id)
                                        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                                    }
                                    onShowMessage("已刪除自動記事規則")
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "刪除規則",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    if (showManualMemoRuleDialog) {
        ManualMemoRuleDialog(
            onDismiss = { showManualMemoRuleDialog = false },
            onSave = { pkg, sender, keyword, action ->
                showManualMemoRuleDialog = false
                scope.launch {
                    withContext(Dispatchers.IO) {
                        db.memoRuleDao().insert(
                            com.phantom.checker.data.MemoRuleEntity(
                                packageName = pkg,
                                sender = sender,
                                keyword = keyword,
                                action = action,
                                isEnabled = true,
                                createdAt = System.currentTimeMillis(),
                                source = com.phantom.checker.data.MemoRuleEntity.SOURCE_MANUAL
                            )
                        )
                        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                    }
                    onShowMessage("已新增自動記事規則")
                }
            }
        )
    }

    pendingNlMemoRule?.let { parsed ->
        val summary = buildString {
            val parts = listOfNotNull(
                parsed.app?.let { "App $it" },
                parsed.sender?.let { "寄件者 $it" },
                parsed.keyword?.let { "含「$it」" }
            )
            append(parts.joinToString("／"))
            append(" → ")
            append(com.phantom.checker.util.MemoRuleEngine.actionLabel(parsed.memoAction))
        }
        AlertDialog(
            onDismissRequest = { pendingNlMemoRule = null },
            title = { Text("確認新增自動記事規則") },
            text = { Text(summary) },
            confirmButton = {
                Button(onClick = {
                    val rule = parsed
                    pendingNlMemoRule = null
                    nlMemoRuleInput = ""
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            db.memoRuleDao().insert(
                                com.phantom.checker.data.MemoRuleEntity(
                                    packageName = rule.app,
                                    sender = rule.sender,
                                    keyword = rule.keyword,
                                    action = rule.memoAction,
                                    isEnabled = true,
                                    createdAt = System.currentTimeMillis(),
                                    source = com.phantom.checker.data.MemoRuleEntity.SOURCE_CHAT
                                )
                            )
                            com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                        }
                        onShowMessage("已新增自動記事規則")
                    }
                }) {
                    Text("確認新增")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingNlMemoRule = null }) {
                    Text("取消")
                }
            }
        )
    }

    neverMemoTargetMsg?.let { srcMsg ->
        NeverAutoMemoConfirmDialog(
            packageName = srcMsg.packageName,
            appLabel = srcMsg.appLabel,
            sender = srcMsg.sender,
            defaultKeyword = srcMsg.content,
            onDismiss = { neverMemoTargetMsg = null },
            onConfirm = { pkg, snd, kw ->
                neverMemoTargetMsg = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        db.memoRuleDao().insert(
                            com.phantom.checker.data.MemoRuleEntity(
                                packageName = pkg,
                                sender = snd,
                                keyword = kw,
                                action = com.phantom.checker.data.MemoRuleEntity.ACTION_NEVER,
                                isEnabled = true,
                                createdAt = System.currentTimeMillis(),
                                source = com.phantom.checker.data.MemoRuleEntity.SOURCE_DETAIL
                            )
                        )
                        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                    }
                    onShowMessage("已設定不再自動加入記事／待辦")
                }
            }
        )
    }

    neverMemoFallbackNote?.let { targetNote ->
        NeverAutoMemoConfirmDialog(
            packageName = null,
            appLabel = targetNote.relatedAppLabel.orEmpty(),
            sender = targetNote.relatedAppLabel,
            defaultKeyword = targetNote.content,
            onDismiss = { neverMemoFallbackNote = null },
            onConfirm = { pkg, snd, kw ->
                neverMemoFallbackNote = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        db.memoRuleDao().insert(
                            com.phantom.checker.data.MemoRuleEntity(
                                packageName = pkg,
                                sender = snd,
                                keyword = kw,
                                action = com.phantom.checker.data.MemoRuleEntity.ACTION_NEVER,
                                isEnabled = true,
                                createdAt = System.currentTimeMillis(),
                                source = com.phantom.checker.data.MemoRuleEntity.SOURCE_DETAIL
                            )
                        )
                        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                    }
                    onShowMessage("已設定不再自動加入記事／待辦")
                }
            }
        )
    }

    val nq = notesSearchQuery.trim()
    val matchedTodos = remember(todos, nq) {
        if (nq.isEmpty()) emptyList()
        else todos.filter {
            it.title.contains(nq, ignoreCase = true) || it.notes.contains(nq, ignoreCase = true)
        }
    }
    val matchedNotes = remember(notes, nq) {
        if (nq.isEmpty()) emptyList()
        else notes.filter {
            (it.relatedAppLabel?.contains(nq, ignoreCase = true) == true) || it.content.contains(nq, ignoreCase = true)
        }
    }
    PhantomFullScreenSearchOverlay(
        visible = showNotesSearch,
        query = notesSearchQuery,
        onQueryChange = { notesSearchQuery = it },
        onDismiss = { showNotesSearch = false },
        placeholder = "搜尋待辦事項或備忘錄…",
        resultsContent = {
            if (nq.isEmpty()) {
                item(key = "notes_search_hint") {
                    Text(
                        text = "輸入關鍵字即時搜尋待辦事項與備忘錄。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else if (matchedTodos.isEmpty() && matchedNotes.isEmpty()) {
                item(key = "notes_search_empty") {
                    Text(
                        text = "找不到符合「$nq」的待辦或記事。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else {
                if (matchedTodos.isNotEmpty()) {
                    item(key = "notes_search_todos_hdr") {
                        Text(
                            text = "待辦事項（${matchedTodos.size}）",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(matchedTodos, key = { "todo_${it.id}" }) { todo ->
                        PhantomGlassCard(
                            glassState = glassState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showNotesSearch = false
                                    editingTodo = todo
                                    showTodoEditor = true
                                },
                            cornerRadius = 14.dp,
                            contentPadding = 12.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = todo.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        textDecoration = if (todo.isDone) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                    Text(
                                        text = PhantomPrefs.formatMessageTime(appCtx, todo.dueAt, includeDate = true),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (todo.notes.isNotBlank()) {
                                        Text(
                                            text = todo.notes,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (todo.relatedMessageId != null) {
                                    IconButton(
                                        onClick = {
                                            showNotesSearch = false
                                            onOpenMessageDetail(todo.relatedMessageId)
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "查看來源訊息",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (matchedNotes.isNotEmpty()) {
                    item(key = "notes_search_notes_hdr") {
                        Text(
                            text = "備忘錄（${matchedNotes.size}）",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(matchedNotes, key = { "note_${it.id}" }) { note ->
                        PhantomGlassCard(
                            glassState = glassState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showNotesSearch = false
                                    editingNote = note
                                    showEditor = true
                                },
                            cornerRadius = 14.dp,
                            contentPadding = 12.dp
                        ) {
                            val appLabel = note.relatedAppLabel
                            if (!appLabel.isNullOrBlank()) {
                                Text(
                                    text = appLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = note.content,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodoEditorDialog(
    initial: TodoEntity?,
    onDismiss: () -> Unit,
    onSave: (title: String, dueAt: Long, priority: Int, repeatRule: String, notes: String) -> Unit
) {
    val ctx = LocalContext.current
    var title by remember(initial) { mutableStateOf(initial?.title ?: "") }
    var dueAt by remember(initial) {
        mutableLongStateOf(initial?.dueAt ?: (System.currentTimeMillis() + 60L * 60L * 1000L))
    }
    var priority by remember(initial) { mutableIntStateOf(initial?.priority ?: TodoEntity.PRIORITY_MEDIUM) }
    var repeatRule by remember(initial) { mutableStateOf(initial?.repeatRule ?: TodoEntity.REPEAT_NONE) }
    var notes by remember(initial) { mutableStateOf(initial?.notes ?: "") }

    val priorityOptions = listOf(
        TodoEntity.PRIORITY_LOW to "低",
        TodoEntity.PRIORITY_MEDIUM to "中",
        TodoEntity.PRIORITY_HIGH to "高"
    )
    val repeatOptions = listOf(
        TodoEntity.REPEAT_NONE to "不重複",
        TodoEntity.REPEAT_DAILY to "每日",
        TodoEntity.REPEAT_WEEKLY to "每週"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增待辦事項" else "編輯待辦事項") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("待辦內容") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val cal = java.util.Calendar.getInstance().apply { timeInMillis = dueAt }
                            android.app.DatePickerDialog(
                                ctx,
                                { _, y, m, d ->
                                    cal.set(java.util.Calendar.YEAR, y)
                                    cal.set(java.util.Calendar.MONTH, m)
                                    cal.set(java.util.Calendar.DAY_OF_MONTH, d)
                                    dueAt = cal.timeInMillis
                                },
                                cal.get(java.util.Calendar.YEAR),
                                cal.get(java.util.Calendar.MONTH),
                                cal.get(java.util.Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        val dateFmt = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
                        Text(dateFmt.format(Date(dueAt)), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            val cal = java.util.Calendar.getInstance().apply { timeInMillis = dueAt }
                            android.app.TimePickerDialog(
                                ctx,
                                { _, h, min ->
                                    cal.set(java.util.Calendar.HOUR_OF_DAY, h)
                                    cal.set(java.util.Calendar.MINUTE, min)
                                    cal.set(java.util.Calendar.SECOND, 0)
                                    dueAt = cal.timeInMillis
                                },
                                cal.get(java.util.Calendar.HOUR_OF_DAY),
                                cal.get(java.util.Calendar.MINUTE),
                                PhantomPrefs.is24HourFormat(ctx)
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(PhantomPrefs.formatMessageTime(ctx, dueAt, includeDate = false), maxLines = 1)
                    }
                }
                Text("優先級", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    priorityOptions.forEach { (pVal, pLabel) ->
                        FilterChip(
                            selected = priority == pVal,
                            onClick = { priority = pVal },
                            label = { Text(pLabel) }
                        )
                    }
                }
                Text("重複提醒", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeatOptions.forEach { (rVal, rLabel) ->
                        FilterChip(
                            selected = repeatRule == rVal,
                            onClick = { repeatRule = rVal },
                            label = { Text(rLabel) }
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("備註（可選）") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.trim().isNotEmpty(),
                onClick = { onSave(title.trim(), dueAt, priority, repeatRule, notes.trim()) }
            ) {
                Text("儲存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteEditorDialog(
    initial: NoteEntity?,
    onDismiss: () -> Unit,
    onSave: (content: String, colorHex: String?) -> Unit
) {
    var content by remember(initial) { mutableStateOf(initial?.content ?: "") }
    var selectedColor by remember(initial) { mutableStateOf(initial?.colorHex) }
    val colorOptions = listOf<Pair<String?, String>>(
        null to "預設",
        "#EF5350" to "紅",
        "#FFA726" to "橘",
        "#66BB6A" to "綠",
        "#42A5F5" to "藍",
        "#AB47BC" to "紫"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增記事" else "編輯記事") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("記事內容") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "標記顏色（選填）",
                    style = MaterialTheme.typography.labelMedium
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    colorOptions.forEach { (hex, label) ->
                        FilterChip(
                            selected = selectedColor == hex,
                            onClick = { selectedColor = hex },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = content.trim().isNotEmpty(),
                onClick = { onSave(content.trim(), selectedColor) }
            ) {
                Text("儲存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun StatsTab(
    state: StatsUiState,
    glassState: HazeState,
    onSummaryGenerated: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    var isGeneratingSummary by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var summaryHistory by remember { mutableStateOf(listOf<AiSummaryEntity>()) }
    val profiles = remember { AiClient.getProfiles(appCtx) }
    var activeProfile by remember { mutableStateOf(AiClient.getActiveProfile(appCtx)) }

    if (showExportDialog) {
        ExportCsvDialog(
            appGroups = state.rawAppGroups,
            onDismiss = { showExportDialog = false },
            onConfirm = { pkg, days ->
                showExportDialog = false
                if (isExporting) return@ExportCsvDialog
                scope.launch {
                    isExporting = true
                    val result = MessageExporter.exportToDownloads(
                        ctx = appCtx,
                        packageName = pkg,
                        daysBack = days
                    )
                    isExporting = false
                    result.onSuccess { fileName ->
                        onShowMessage("匯出成功：$fileName")
                    }.onFailure { err ->
                        val reason = err.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: err.javaClass.simpleName
                        onShowMessage("匯出失敗：$reason")
                    }
                }
            }
        )
    }

    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("近 7 日 AI 摘要歷史") },
            text = {
                if (summaryHistory.isEmpty()) {
                    Text("近 7 日尚無 AI 摘要紀錄。", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        summaryHistory.forEach { item ->
                            val dateStr = item.date.toString().let { d ->
                                if (d.length == 8) "${d.substring(0, 4)}/${d.substring(4, 6)}/${d.substring(6, 8)}" else d
                            }
                            Column {
                                Text(
                                    text = dateStr,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(2.dp))
                                PhantomMarkdownText(
                                    markdown = item.summary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text("關閉")
                }
            }
        )
    }

    val tokenSaverOn = remember { PhantomPrefs.isTokenSaverEnabled(appCtx) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (tokenSaverOn) {
            item {
                PhantomGlassCard(
                    glassState = glassState,
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    contentPadding = 12.dp
                ) {
                    Text(
                        text = "省 token 模式啟用中",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "已暫停每則 AI 掃描與每日摘要排程，僅命中可疑特徵時才進行風險分析。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    val data = ShareCardReportData(
                        todayCount = state.weeklyCounts.lastOrNull()?.count ?: 0,
                        weekTotalCount = state.weeklyCounts.sumOf { it.count },
                        safeCount = state.safeCount,
                        suspiciousCount = state.suspiciousCount,
                        scamCount = state.scamCount,
                        dailyLabels = state.weeklyCounts.map { it.label },
                        dailyCounts = state.weeklyCounts.map { it.count },
                        topApps = state.rawAppGroups
                            .sortedByDescending { it.cnt }
                            .take(5)
                            .map { it.appLabel to it.cnt }
                    )
                    val res = StatsShareCardGenerator.generateAndShare(ctx, data)
                    if (res.isFailure) {
                        onShowMessage("產生分享圖卡失敗：${res.exceptionOrNull()?.message ?: "未知錯誤"}")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("分享本週統計報告圖卡（本機產生）")
            }
        }
        // 每日 AI 摘要玻璃卡
        item {
            var dailyExpanded by remember { mutableStateOf(PhantomPrefs.isDailySummaryExpanded(appCtx)) }
            val summary = state.latestSummary
            PhantomGlassCard(
                glassState = glassState,
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 14.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            dailyExpanded = !dailyExpanded
                            PhantomPrefs.setDailySummaryExpanded(appCtx, dailyExpanded)
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "每日 AI 摘要",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        scope.launch {
                            summaryHistory = withContext(Dispatchers.IO) {
                                AppDatabaseHolder.db(appCtx).aiSummaryDao().byDateRange(
                                    DailySummaryPolicy.dateKeyDaysAgo(6),
                                    DailySummaryPolicy.todayDateKey()
                                )
                            }
                            showHistoryDialog = true
                        }
                    }) {
                        Text("查看歷史")
                    }
                    IconButton(onClick = {
                        dailyExpanded = !dailyExpanded
                        PhantomPrefs.setDailySummaryExpanded(appCtx, dailyExpanded)
                    }) {
                        Icon(
                            imageVector = if (dailyExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (dailyExpanded) "收合今日摘要" else "展開今日摘要"
                        )
                    }
                }
                if (!dailyExpanded) {
                    val previewText = summary?.let {
                        stripMarkdown(it.summary).replace("\n", " ").replace(Regex("\\s+"), " ").trim().take(80)
                    } ?: "尚無摘要，點擊展開或立即生成"
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                dailyExpanded = true
                                PhantomPrefs.setDailySummaryExpanded(appCtx, true)
                            }
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                if (isGeneratingSummary) return@TextButton
                                scope.launch {
                                    isGeneratingSummary = true
                                    val result = withContext(Dispatchers.IO) {
                                        DailySummaryPolicy.generateTodaySummary(appCtx)
                                    }
                                    isGeneratingSummary = false
                                    result.onSuccess {
                                        onSummaryGenerated()
                                        onShowMessage("今日 AI 摘要已生成")
                                    }.onFailure { err ->
                                        onShowMessage(err.message ?: "摘要生成失敗")
                                    }
                                }
                            },
                            enabled = !isGeneratingSummary
                        ) {
                            Text(if (isGeneratingSummary) "生成中…" else if (summary != null) "重新生成" else "立即生成")
                        }
                        if (summary != null) {
                            TextButton(onClick = {
                                val plain = stripMarkdown(summary.summary)
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("daily_ai_summary", plain))
                                onShowMessage("已複製今日 AI 摘要")
                            }) {
                                Text("複製")
                            }
                            TextButton(onClick = {
                                val plain = stripMarkdown(summary.summary)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "幻訊每日 AI 摘要")
                                    putExtra(Intent.EXTRA_TEXT, plain)
                                }
                                ctx.startActivity(Intent.createChooser(shareIntent, "分享每日 AI 摘要"))
                            }) {
                                Text("分享")
                            }
                        }
                    }
                } else {
                    AiProviderSwitcherChip(
                        profiles = profiles,
                        activeProfile = activeProfile,
                        onSelectProfile = { selected ->
                            AiClient.setActiveProvider(appCtx, selected.id)
                            activeProfile = selected
                            onShowMessage("已切換模型：${selected.name}・${selected.model}")
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    if (summary != null) {
                        val timeStr = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                            .format(Date(summary.generatedAt))
                        PhantomMarkdownText(
                            markdown = summary.summary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "更新於 $timeStr",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = {
                                    val plain = stripMarkdown(summary.summary)
                                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("daily_ai_summary", plain))
                                    onShowMessage("已複製今日 AI 摘要")
                                }) {
                                    Text("複製")
                                }
                                TextButton(onClick = {
                                    val plain = stripMarkdown(summary.summary)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "幻訊每日 AI 摘要")
                                        putExtra(Intent.EXTRA_TEXT, plain)
                                    }
                                    ctx.startActivity(Intent.createChooser(shareIntent, "分享每日 AI 摘要"))
                                }) {
                                    Text("分享")
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "尚無摘要",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (isGeneratingSummary) return@Button
                            scope.launch {
                                isGeneratingSummary = true
                                val result = withContext(Dispatchers.IO) {
                                    DailySummaryPolicy.generateTodaySummary(appCtx)
                                }
                                isGeneratingSummary = false
                                result.onSuccess {
                                    onSummaryGenerated()
                                    onShowMessage("今日 AI 摘要已生成")
                                }.onFailure { err ->
                                    onShowMessage(err.message ?: "摘要生成失敗")
                                }
                            }
                        },
                        enabled = !isGeneratingSummary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressableScale()
                    ) {
                        if (isGeneratingSummary) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("生成中…")
                        } else {
                            Text("立即生成今日摘要")
                        }
                    }
                }
            }
        }

        // 本週 AI 摘要報告（每週日 22:00 自動生成＋手動生成＋歷史展開）
        item {
            var weeklyExpanded by remember { mutableStateOf(PhantomPrefs.isWeeklySummaryExpanded(appCtx)) }
            var isGeneratingWeekly by remember { mutableStateOf(false) }
            var weeklyError by remember { mutableStateOf<String?>(null) }
            var weeklyHistoryExpanded by remember { mutableStateOf(false) }
            var weeklyHistory by remember { mutableStateOf<List<AiSummaryEntity>>(emptyList()) }
            val weekly = state.latestWeeklySummary

            PhantomGlassCard(
                glassState = glassState,
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 14.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            weeklyExpanded = !weeklyExpanded
                            PhantomPrefs.setWeeklySummaryExpanded(appCtx, weeklyExpanded)
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "本週 AI 摘要",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "每週日 22:00 自動整理（不受省 token 模式限制）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = {
                        if (!weeklyExpanded) {
                            weeklyExpanded = true
                            PhantomPrefs.setWeeklySummaryExpanded(appCtx, true)
                        }
                        if (!weeklyHistoryExpanded) {
                            scope.launch {
                                weeklyHistory = withContext(Dispatchers.IO) {
                                    AppDatabaseHolder.db(appCtx).aiSummaryDao().weeklyHistory()
                                }
                                weeklyHistoryExpanded = true
                            }
                        } else {
                            weeklyHistoryExpanded = false
                        }
                    }) {
                        Text(if (weeklyHistoryExpanded) "收合歷史" else "歷史週報")
                    }
                    IconButton(onClick = {
                        weeklyExpanded = !weeklyExpanded
                        PhantomPrefs.setWeeklySummaryExpanded(appCtx, weeklyExpanded)
                    }) {
                        Icon(
                            imageVector = if (weeklyExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (weeklyExpanded) "收合本週摘要" else "展開本週摘要"
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                if (!weeklyExpanded) {
                    val previewText = weekly?.let {
                        val label = WeeklySummaryPolicy.formatWeekLabel(it.date)
                        val body = stripMarkdown(it.summary).replace("\n", " ").replace(Regex("\\s+"), " ").trim().take(70)
                        "$label：$body"
                    } ?: "尚無本週摘要，點擊展開或立即生成"
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                weeklyExpanded = true
                                PhantomPrefs.setWeeklySummaryExpanded(appCtx, true)
                            }
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                if (isGeneratingWeekly) return@TextButton
                                scope.launch {
                                    isGeneratingWeekly = true
                                    weeklyError = null
                                    val result = withContext(Dispatchers.IO) {
                                        WeeklySummaryPolicy.generateWeeklySummary(appCtx, fromSchedule = false)
                                    }
                                    isGeneratingWeekly = false
                                    result.onSuccess {
                                        onSummaryGenerated()
                                        onShowMessage("本週 AI 摘要已生成")
                                    }.onFailure { err ->
                                        val msg = err.message ?: "週報生成失敗"
                                        weeklyError = msg
                                        onShowMessage(msg)
                                    }
                                }
                            },
                            enabled = !isGeneratingWeekly
                        ) {
                            Text(if (isGeneratingWeekly) "生成中…" else if (weekly != null) "重新生成" else "生成週報")
                        }
                        if (weekly != null) {
                            TextButton(onClick = {
                                val plain = stripMarkdown(weekly.summary)
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("weekly_ai_summary", plain))
                                onShowMessage("已複製本週 AI 摘要")
                            }) {
                                Text("複製")
                            }
                            TextButton(onClick = {
                                val plain = stripMarkdown(weekly.summary)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "幻訊本週 AI 摘要")
                                    putExtra(Intent.EXTRA_TEXT, plain)
                                }
                                ctx.startActivity(Intent.createChooser(shareIntent, "分享本週 AI 摘要"))
                            }) {
                                Text("分享")
                            }
                        }
                    }
                    if (!weeklyError.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = weeklyError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    if (weekly != null) {
                        val timeStr = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                            .format(Date(weekly.generatedAt))
                        Text(
                            text = WeeklySummaryPolicy.formatWeekLabel(weekly.date),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        PhantomMarkdownText(
                            markdown = weekly.summary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "更新於 $timeStr",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = {
                                    val plain = stripMarkdown(weekly.summary)
                                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("weekly_ai_summary", plain))
                                    onShowMessage("已複製本週 AI 摘要")
                                }) {
                                    Text("複製")
                                }
                                TextButton(onClick = {
                                    val plain = stripMarkdown(weekly.summary)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "幻訊本週 AI 摘要")
                                        putExtra(Intent.EXTRA_TEXT, plain)
                                    }
                                    ctx.startActivity(Intent.createChooser(shareIntent, "分享本週 AI 摘要"))
                                }) {
                                    Text("分享")
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "尚無本週摘要",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!weeklyError.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = weeklyError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (isGeneratingWeekly) return@Button
                            scope.launch {
                                isGeneratingWeekly = true
                                weeklyError = null
                                val result = withContext(Dispatchers.IO) {
                                    WeeklySummaryPolicy.generateWeeklySummary(appCtx, fromSchedule = false)
                                }
                                isGeneratingWeekly = false
                                result.onSuccess {
                                    if (weeklyHistoryExpanded) {
                                        weeklyHistory = withContext(Dispatchers.IO) {
                                            AppDatabaseHolder.db(appCtx).aiSummaryDao().weeklyHistory()
                                        }
                                    }
                                    onSummaryGenerated()
                                    onShowMessage("本週 AI 摘要已生成")
                                }.onFailure { err ->
                                    val msg = err.message ?: "週報生成失敗"
                                    weeklyError = msg
                                    onShowMessage(msg)
                                }
                            }
                        },
                        enabled = !isGeneratingWeekly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressableScale()
                    ) {
                        if (isGeneratingWeekly) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("生成週報中…")
                        } else {
                            Text("生成本週摘要（本週至今）")
                        }
                    }
                }

                if (weeklyHistoryExpanded) {
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    if (weeklyHistory.isEmpty()) {
                        Text(
                            text = "尚無歷史週報紀錄",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        weeklyHistory.forEachIndexed { idx, item ->
                            if (idx > 0) {
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            }
                            val genTime = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                                .format(Date(item.generatedAt))
                            Text(
                                text = "${WeeklySummaryPolicy.formatWeekLabel(item.date)}（$genTime）",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            PhantomMarkdownText(
                                markdown = item.summary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // 本週 7 日訊息量簡易 Bar Chart（純 Compose Canvas）
        item {
            val barColor = MaterialTheme.colorScheme.primary
            val trackColor = MaterialTheme.colorScheme.surfaceVariant
            PhantomGlassCard(
                glassState = glassState,
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 14.dp
            ) {
                Text(
                    text = "本週 7 日訊息量",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                val maxCount = (state.weeklyCounts.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                ) {
                    val items = state.weeklyCounts
                    if (items.isEmpty()) return@Canvas
                    val barWidthPx = 12.dp.toPx()
                    val slotWidth = size.width / items.size
                    val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

                    items.forEachIndexed { idx, day ->
                        val left = idx * slotWidth + (slotWidth - barWidthPx) / 2f
                        drawRoundRect(
                            color = trackColor,
                            topLeft = Offset(left, 0f),
                            size = Size(barWidthPx, size.height),
                            cornerRadius = radius
                        )
                        val ratio = day.count.toFloat() / maxCount.toFloat()
                        val barH = (size.height * ratio).coerceAtLeast(if (day.count > 0) 6.dp.toPx() else 0f)
                        if (barH > 0f) {
                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(left, size.height - barH),
                                size = Size(barWidthPx, barH),
                                cornerRadius = radius
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    state.weeklyCounts.forEach { day ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${day.count}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = day.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 風險分布三色計數卡
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RiskCountCard(
                    title = "安全",
                    count = state.safeCount,
                    accentColor = MaterialTheme.colorScheme.primary,
                    glassState = glassState,
                    modifier = Modifier.weight(1f)
                )
                RiskCountCard(
                    title = "疑似",
                    count = state.suspiciousCount,
                    accentColor = Color(0xFFE6A100),
                    glassState = glassState,
                    modifier = Modifier.weight(1f)
                )
                RiskCountCard(
                    title = "詐騙",
                    count = state.scamCount,
                    accentColor = MaterialTheme.colorScheme.error,
                    glassState = glassState,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 今日 AI 智慧標記分布
        if (state.todayAiTagCounts.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "今日 AI 智慧標記",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.todayAiTagCounts.take(3).forEach { item ->
                            RiskCountCard(
                                title = item.name,
                                count = item.cnt,
                                accentColor = parseTagColor(item.colorHex),
                                glassState = glassState,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 今日每小時分布（Task J1：純 Canvas 24 小時 mini bar）
        item {
            val hourBarColor = MaterialTheme.colorScheme.secondary
            val hourTrackColor = MaterialTheme.colorScheme.surfaceVariant
            PhantomGlassCard(
                glassState = glassState,
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 14.dp
            ) {
                Text(
                    text = "今日每小時分布",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                val maxHour = (state.todayHourlyCounts.maxOrNull() ?: 1).coerceAtLeast(1)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                ) {
                    val counts = state.todayHourlyCounts
                    if (counts.isEmpty()) return@Canvas
                    val slotW = size.width / counts.size
                    val barW = (slotW * 0.6f).coerceAtLeast(3.dp.toPx())
                    val radius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    counts.forEachIndexed { idx, cnt ->
                        val left = idx * slotW + (slotW - barW) / 2f
                        drawRoundRect(
                            color = hourTrackColor,
                            topLeft = Offset(left, 0f),
                            size = Size(barW, size.height),
                            cornerRadius = radius
                        )
                        val barH = if (cnt > 0) {
                            (size.height * (cnt.toFloat() / maxHour.toFloat())).coerceAtLeast(4.dp.toPx())
                        } else {
                            0f
                        }
                        if (barH > 0f) {
                            drawRoundRect(
                                color = hourBarColor,
                                topLeft = Offset(left, size.height - barH),
                                size = Size(barW, barH),
                                cornerRadius = radius
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("0時", "6時", "12時", "18時", "23時").forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 騷擾佔比（Task J2：行銷 + 垃圾通知 / 總訊息，Canvas arc）
        item {
            val total = state.totalMessageCount.coerceAtLeast(0)
            val harass = state.harassmentCount.coerceIn(0, total.coerceAtLeast(0))
            val ratio = if (total > 0) harass.toFloat() / total.toFloat() else 0f
            val pct = (ratio * 100f).toInt()
            val arcTrack = MaterialTheme.colorScheme.surfaceVariant
            val arcColor = Color(0xFFFF9800)

            PhantomGlassCard(
                glassState = glassState,
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 14.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier.size(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeW = 8.dp.toPx()
                            drawArc(
                                color = arcTrack,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round)
                            )
                            if (ratio > 0f) {
                                drawArc(
                                    color = arcColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f * ratio,
                                    useCenter = false,
                                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                                )
                            }
                        }
                        Text(
                            text = "$pct%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "騷擾佔比（行銷＋垃圾通知）",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$harass / $total 則訊息屬於行銷或垃圾通知",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Top 5 高風險 App 清單
        item {
            PhantomGlassCard(
                glassState = glassState,
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 14.dp
            ) {
                Text(
                    text = "Top 5 高風險 App",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                if (state.topRiskyApps.isEmpty()) {
                    Text(
                        text = "目前尚無疑似或詐騙紀錄",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.topRiskyApps.forEachIndexed { idx, row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}. ",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                AppIconImage(
                                    packageName = row.packageName,
                                    contentDescription = row.appLabel,
                                    size = 24.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = row.appLabel.ifBlank { row.packageName },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${row.cnt} 則",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { ctx.startActivity(Intent(ctx, ScamCenterActivity::class.java)) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("查看詐騙警示中心")
                }
            }
        }

        // 匯出 CSV 按鈕
        item {
            OutlinedButton(
                onClick = { showExportDialog = true },
                enabled = !isExporting,
                modifier = Modifier
                    .fillMaxWidth()
                    .pressableScale()
            ) {
                Text(if (isExporting) "匯出中…" else "匯出訊息 CSV")
            }
        }
    }
}

@Composable
private fun RiskCountCard(
    title: String,
    count: Int,
    accentColor: Color,
    glassState: HazeState,
    modifier: Modifier = Modifier
) {
    PhantomGlassCard(
        glassState = glassState,
        modifier = modifier,
        cornerRadius = 20.dp,
        contentPadding = 12.dp
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = accentColor,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ManualMemoRuleDialog(
    onDismiss: () -> Unit,
    onSave: (packageName: String?, sender: String?, keyword: String?, action: String) -> Unit
) {
    val ctx = LocalContext.current
    var pkgText by remember { mutableStateOf("") }
    var senderText by remember { mutableStateOf("") }
    var keywordText by remember { mutableStateOf("") }
    var selectedAction by remember { mutableStateOf(com.phantom.checker.data.MemoRuleEntity.ACTION_NEVER) }
    var showAppDropdown by remember { mutableStateOf(false) }
    var appOptions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(Unit) {
        appOptions = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val fromDb = runCatching {
                AppDatabaseHolder.db(ctx.applicationContext).messageDao().groupByApp()
                    .map { it.packageName to it.appLabel.ifBlank { it.packageName } }
            }.getOrDefault(emptyList())
            val installed = runCatching {
                pm.getInstalledApplications(0)
                    .filter { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
                    .map { it.packageName to pm.getApplicationLabel(it).toString() }
            }.getOrDefault(emptyList())
            (fromDb + installed)
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
        }
    }

    val isValid = pkgText.isNotBlank() || senderText.isNotBlank() || keywordText.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手動新增自動記事規則") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "以下三項條件至少填寫一項（同時填寫時須全部符合）：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box {
                    OutlinedTextField(
                        value = pkgText,
                        onValueChange = { pkgText = it },
                        label = { Text("App 套件名（可選）") },
                        placeholder = { Text("例如 com.gogolook.whoscall") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showAppDropdown = true }) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "選擇已安裝 App"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = showAppDropdown,
                        onDismissRequest = { showAppDropdown = false }
                    ) {
                        appOptions.take(40).forEach { (pkg, label) ->
                            DropdownMenuItem(
                                text = { Text("$label ($pkg)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = {
                                    pkgText = pkg
                                    showAppDropdown = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = senderText,
                    onValueChange = { senderText = it },
                    label = { Text("寄件者（可選）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = keywordText,
                    onValueChange = { keywordText = it },
                    label = { Text("關鍵字（可選）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "規則動作：",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                listOf(
                    com.phantom.checker.data.MemoRuleEntity.ACTION_NEVER to "永不加入記事／待辦",
                    com.phantom.checker.data.MemoRuleEntity.ACTION_TODO to "一律加入待辦",
                    com.phantom.checker.data.MemoRuleEntity.ACTION_NOTE to "一律加入記事"
                ).forEach { (act, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedAction = act },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedAction == act,
                            onClick = { selectedAction = act }
                        )
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        pkgText.trim().takeIf { it.isNotEmpty() },
                        senderText.trim().takeIf { it.isNotEmpty() },
                        keywordText.trim().takeIf { it.isNotEmpty() },
                        selectedAction
                    )
                },
                enabled = isValid
            ) {
                Text("儲存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

