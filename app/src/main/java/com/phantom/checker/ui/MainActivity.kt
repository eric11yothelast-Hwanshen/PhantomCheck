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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.phantom.checker.shizuku.ShizukuManager
import com.phantom.checker.ui.components.LiquidGlassTabBar
import com.phantom.checker.ui.components.LiquidGlassTabBarItem
import com.phantom.checker.ui.components.pressableScale
import com.phantom.checker.util.AppUpdateHelper
import com.phantom.checker.util.MessageExporter
import com.phantom.checker.util.MuteMode
import com.phantom.checker.util.PhantomPrefs
import com.phantom.checker.util.PhantomSettingsBus
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

import com.phantom.checker.ui.home.*
import com.phantom.checker.ui.memory.*
import com.phantom.checker.ui.notes.*
import com.phantom.checker.ui.stats.*
import com.phantom.checker.ui.todo.*
private const val PAGE_SIZE = 100
private const val MAX_BATCH_SELECT = 100

/** 記憶庫專屬圖示（純向量繪製，無需額外依賴） */
internal val MemoryIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "MemoryBrain",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(15f, 9f)
            horizontalLineTo(9f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(6f)
            verticalLineTo(9f)
            close()
            moveTo(13f, 13f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            close()
            moveTo(21f, 11f)
            verticalLineTo(9f)
            horizontalLineToRelative(-2f)
            verticalLineTo(7f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            horizontalLineToRelative(-2f)
            verticalLineTo(3f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(-2f)
            verticalLineTo(3f)
            horizontalLineTo(9f)
            verticalLineToRelative(2f)
            horizontalLineTo(7f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(2f)
            horizontalLineTo(3f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            horizontalLineTo(3f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(2f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(2f)
            close()
            moveTo(17f, 17f)
            horizontalLineTo(7f)
            verticalLineTo(7f)
            horizontalLineToRelative(10f)
            verticalLineToRelative(10f)
            close()
        }
    }.build()
}

class MainActivity : ComponentActivity() {
    companion object {
        internal val hasAutoCheckedUpdateThisProcess = java.util.concurrent.atomic.AtomicBoolean(false)
    }

    private val requestedTabState = mutableStateOf<MainTab?>(null)
    private val requestedEditTodoIdState = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AiClient.init(applicationContext)
        RetentionPolicy.scheduleOrUpdate(applicationContext)
        DailySummaryPolicy.schedule(applicationContext)
        DailySummaryPolicy.maybeCatchUp(applicationContext)
        WeeklySummaryPolicy.schedule(applicationContext)
        WeeklySummaryPolicy.maybeCatchUp(applicationContext)
        HeartbeatPolicy.schedule(applicationContext)
        com.phantom.checker.data.MemoryExpiryPolicy.schedule(applicationContext)
        com.phantom.checker.data.ScamImmunityPolicy.schedule(applicationContext)
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
            "memory" -> requestedTabState.value = MainTab.MEMORY
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
    MEMORY("記憶"),
    NOTES("記事"),
    STATS("統計")
}

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
    val saveableStateHolder = rememberSaveableStateHolder()
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
    val needsPostPrompt = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isPostNotificationsGranted(appCtx)
    }
    var postNotificationPromptDone by remember { mutableStateOf(!needsPostPrompt) }
    val shizukuState by ShizukuManager.state.collectAsState()

    LaunchedEffect(onboardingStep, onboardingCompleted, postNotificationPromptDone) {
        if (!onboardingCompleted && postNotificationPromptDone) {
            selectedTab = when (onboardingStep) {
                OnboardingStep.AI_ASSISTANT -> MainTab.ASSISTANT
                OnboardingStep.NOTES_TAB,
                OnboardingStep.MEMO_RULES,
                OnboardingStep.TODO_AND_BATCH -> MainTab.NOTES
                OnboardingStep.STATS_AND_SUMMARY -> MainTab.STATS
                else -> MainTab.MESSAGES
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPostNotifications = granted
        postNotificationPromptDone = true
        if (granted) {
            StatsForegroundService.startOrRefresh(appCtx)
        }
    }

    // Android 13+ 啟動時主動請求 POST_NOTIFICATIONS 權限
    LaunchedEffect(Unit) {
        ShizukuManager.init(appCtx)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotifications) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var tabBarDisplayMode by remember { mutableStateOf(PhantomPrefs.getTabBarDisplayMode(appCtx)) }
    var visibleStatsCards by remember { mutableStateOf(PhantomPrefs.getVisibleStatsCards(appCtx)) }
    val settingsVersion by PhantomSettingsBus.version.collectAsState()
    LaunchedEffect(settingsVersion) {
        if (settingsVersion > 0L) {
            favoritePackages = PhantomPrefs.getFavoriteApps(appCtx)
            mutedPackages = PhantomPrefs.getMutedApps(appCtx)
            tabBarDisplayMode = PhantomPrefs.getTabBarDisplayMode(appCtx)
            visibleStatsCards = PhantomPrefs.getVisibleStatsCards(appCtx)
        }
    }
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
                visibleStatsCards = PhantomPrefs.getVisibleStatsCards(appCtx)
                val completedNow = PhantomPrefs.isOnboardingCompleted(appCtx)
                if (onboardingCompleted && !completedNow) {
                    onboardingCompleted = false
                    onboardingStep = OnboardingStep.PERMISSION
                    PhantomPrefs.setOnboardingStep(appCtx, OnboardingStep.PERMISSION.index)
                }
                refreshTrigger++
                if (PhantomPrefs.isUpdateAutoCheckEnabled(appCtx) &&
                    MainActivity.hasAutoCheckedUpdateThisProcess.compareAndSet(false, true)
                ) {
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
                    MainTab.MEMORY -> Unit
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
                        val riskByDayKey = dao.riskyCountByDaySince(sevenDaysAgo).associate { it.dayKey to it.cnt }
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
                        val weeklyRisk = (0..6).map { offset ->
                            val dayCal = Calendar.getInstance().apply {
                                timeInMillis = sevenDaysAgo
                                add(Calendar.DAY_OF_YEAR, offset)
                            }
                            val key = dayKeyFormat.format(dayCal.time)
                            DayCountUi(
                                label = dayFormat.format(dayCal.time),
                                count = riskByDayKey[key] ?: 0
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
                        val labelByPkg = rawGroups.associate { it.packageName to it.appLabel.ifBlank { it.packageName } }
                        val dailyTopApps = dao.todayCountsByApp(startOfDay)
                            .sortedByDescending { it.cnt }
                            .take(5)
                            .map { row ->
                                val resolvedLabel = labelByPkg[row.packageName] ?: try {
                                    appCtx.packageManager.getApplicationLabel(
                                        appCtx.packageManager.getApplicationInfo(row.packageName, 0)
                                    ).toString()
                                } catch (_: Exception) {
                                    row.packageName
                                }
                                DailyAppRankUi(
                                    packageName = row.packageName,
                                    appLabel = resolvedLabel,
                                    count = row.cnt
                                )
                            }
                        val unreadTotal = dao.totalUnreadCount()
                        val todoCompletion = db.todoDao().completionStats()
                        val aiTagCounts = db.tagDao().todayAiTagCounts(startOfDay)
                        val weeklySummary = db.aiSummaryDao().latestWeekly()
                        statsState = StatsUiState(
                            latestSummary = summary,
                            latestWeeklySummary = weeklySummary,
                            weeklyCounts = weekly,
                            weeklyRiskCounts = weeklyRisk,
                            safeCount = safe,
                            suspiciousCount = suspicious,
                            scamCount = scam,
                            topRiskyApps = topRisky,
                            dailyTopApps = dailyTopApps,
                            rawAppGroups = rawGroups,
                            todayAiTagCounts = aiTagCounts,
                            todayHourlyCounts = hourly.toList(),
                            harassmentCount = harassmentCnt,
                            totalMessageCount = safe + suspicious + scam,
                            unreadTotalCount = unreadTotal,
                            todoTotalCount = todoCompletion.total,
                            todoDoneCount = todoCompletion.done
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
        ModalBottomSheet(
            onDismissRequest = { messageSheetTarget = null },
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
        ModalBottomSheet(
            onDismissRequest = { showTagPickerSheet = false },
            containerColor = Color.Transparent
        ) {
            PhantomFloatingSurface(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 0.dp
            ) {
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
                                        MainTab.MEMORY -> MemoryIcon
                                        MainTab.NOTES -> Icons.Default.Edit
                                        MainTab.STATS -> Icons.Default.Info
                                    }
                                    val targetKey = when (tab) {
                                        MainTab.ASSISTANT -> OnboardingTargets.AI_ASSISTANT_TAB
                                        MainTab.NOTES -> OnboardingTargets.NOTES_TAB
                                        MainTab.STATS -> OnboardingTargets.STATS_TAB
                                        MainTab.MESSAGES, MainTab.MEMORY -> null
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
                    onOpenSettings = { openNotificationListenerSettings(ctx) },
                    glassState = glassState,
                    onOpenAppDetails = { openAppDetailsSettings(ctx) },
                    shizukuReady = shizukuState.isReady,
                    onShizukuGrant = {
                        scope.launch {
                            val ok = ShizukuManager.grantNotificationListener(appCtx)
                            if (ok) {
                                isListenerEnabled = true
                                snackbarHostState.showSnackbar("已透過 Shizuku 開啟通知存取權限")
                            } else {
                                snackbarHostState.showSnackbar("Shizuku 授權失敗，請手動開啟")
                            }
                        }
                    },
                    onReportBounds = { rect ->
                        targetBounds = targetBounds + (OnboardingTargets.NOTIFICATION_GUIDE to rect)
                    }
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

            saveableStateHolder.SaveableStateProvider(selectedTab.name) {
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
                            openNotificationListenerSettings(ctx)
                        },
                        onReportBounds = { key, rect ->
                            targetBounds = targetBounds + (key to rect)
                        }
                    )
                    MainTab.ASSISTANT -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .consumeWindowInsets(padding)
                            .imePadding()
                    ) {
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
                    MainTab.MEMORY -> MemoryTab(
                        glassState = glassState,
                        onOpenMessageDetail = { messageId ->
                            ctx.startActivity(MessageDetailActivity.createIntent(ctx, messageId))
                        },
                        onOpenMemorySettings = {
                            ctx.startActivity(
                                Intent(ctx, SettingsActivity::class.java).apply {
                                    putExtra("highlight_card_key", "memory_assistant")
                                }
                            )
                        },
                        onShowMessage = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    )
                    MainTab.NOTES -> NotesTab(
                        glassState = glassState,
                        initialEditTodoId = requestedEditTodoId,
                        onConsumedEditTodoId = onConsumedEditTodoId,
                        onOpenMessageDetail = { messageId ->
                            ctx.startActivity(MessageDetailActivity.createIntent(ctx, messageId))
                        },
                        onShowMessage = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        },
                        onReportBounds = { key, rect ->
                            targetBounds = targetBounds + (key to rect)
                        }
                    )
                    MainTab.STATS -> StatsTab(
                        state = statsState,
                        glassState = glassState,
                        visibleCards = visibleStatsCards,
                        onSummaryGenerated = { refreshTrigger++ },
                        onShowMessage = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    )
                }
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

        if (!onboardingCompleted && postNotificationPromptDone) {
            val hasAnyAppRows = appGroups.isNotEmpty()
            OnboardingOverlay(
                step = onboardingStep,
                isListenerEnabled = isListenerEnabled,
                isBatteryWhitelistOk = isBatteryWhitelistOk,
                oemAutostartConfirmed = oemAutostartConfirmed,
                targetBounds = targetBounds,
                glassState = glassState,
                hasAnyAppRows = hasAnyAppRows,
                onOpenNotificationSettings = { openNotificationListenerSettings(ctx) },
                onOpenAppDetails = { openAppDetailsSettings(ctx) },
                shizukuReady = shizukuState.isReady,
                onShizukuGrant = {
                    scope.launch {
                        val ok = ShizukuManager.grantNotificationListener(appCtx)
                        if (ok) {
                            isListenerEnabled = true
                            snackbarHostState.showSnackbar("已透過 Shizuku 開啟通知存取權限")
                        } else {
                            snackbarHostState.showSnackbar("Shizuku 授權失敗，請手動開啟")
                        }
                    }
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
                        OnboardingStep.LONG_PRESS ->
                            if (hasAnyAppRows) OnboardingStep.SWIPE_DELETE else OnboardingStep.FILTER_CHIPS
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
