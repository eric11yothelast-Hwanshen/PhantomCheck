package com.phantom.checker.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import com.phantom.checker.ui.components.PhantomMotion
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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
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
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.data.MessageTagCrossRef
import com.phantom.checker.data.MessageTagRow
import com.phantom.checker.data.TagEntity
import com.phantom.checker.data.TagTotalCountRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.ui.platform.LocalConfiguration
import com.phantom.checker.ui.components.AuroraTokens
import com.phantom.checker.ui.components.LiquidGlassTabBar
import com.phantom.checker.ui.components.LiquidGlassTabBarItem
import com.phantom.checker.ui.components.PhantomSecondaryTabRow
import com.phantom.checker.ui.components.auroraHorizontalPadding
import com.phantom.checker.util.AppUpdateHelper
import com.phantom.checker.util.MuteMode
import com.phantom.checker.util.PhantomPrefs
import com.phantom.checker.util.PhantomSettingsBus
import com.phantom.checker.util.TabBarDisplayMode
import com.phantom.checker.util.UpdateCheckResult
import com.phantom.checker.widget.WidgetUpdater
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

import com.phantom.checker.ui.home.*
import com.phantom.checker.ui.memory.*
import com.phantom.checker.ui.notes.*
import com.phantom.checker.ui.stats.*
import com.phantom.checker.ui.todo.*
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
private const val PAGE_SIZE = 100
private const val MAX_BATCH_SELECT = 100

private sealed interface MainLoadSnapshot {
    data object NoOp : MainLoadSnapshot
    data class Search(
        val notedMsgIds: Set<Long>,
        val todoedMsgIds: Set<Long>,
        val searchResults: List<MessageEntity>,
        val activeFilterKeywords: List<String>,
        val messageTagsMap: Map<Long, List<MessageTagRow>>
    ) : MainLoadSnapshot
    data class MessagesAll(
        val todayCount: Int,
        val unreadCount: Int,
        val todayScamCount: Int,
        val availableTags: List<TagEntity>,
        val tagCounts: List<TagTotalCountRow>,
        val notedMsgIds: Set<Long>,
        val todoedMsgIds: Set<Long>,
        val appGroups: List<AppGroupUi>
    ) : MainLoadSnapshot
    data class MessagesFiltered(
        val todayCount: Int,
        val unreadCount: Int,
        val todayScamCount: Int,
        val availableTags: List<TagEntity>,
        val tagCounts: List<TagTotalCountRow>,
        val notedMsgIds: Set<Long>,
        val todoedMsgIds: Set<Long>,
        val filteredMessages: List<MessageEntity>,
        val messageTagsMap: Map<Long, List<MessageTagRow>>,
        val hasMoreFiltered: Boolean
    ) : MainLoadSnapshot
    data class Stats(val statsState: StatsUiState) : MainLoadSnapshot
}

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
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.phantom.checker.util.AppLocaleHelper.wrapContext(newBase))
    }
    companion object {
        internal val hasAutoCheckedUpdateThisProcess = java.util.concurrent.atomic.AtomicBoolean(false)
        internal val autoUpdateCheckFailuresThisProcess = java.util.concurrent.atomic.AtomicInteger(0)
    }

    private val requestedTabState = mutableStateOf<MainTab?>(null)
    private val requestedInsightSubTabState = mutableStateOf<InsightSubTab?>(null)
    private val requestedEditTodoIdState = mutableStateOf<Long?>(null)
    private val requestedBriefingMsgIdState = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AiClient.init(applicationContext)
        RetentionPolicy.scheduleOrUpdate(applicationContext)
        DailySummaryPolicy.schedule(applicationContext)
        WeeklySummaryPolicy.schedule(applicationContext)
        WeeklySummaryPolicy.maybeCatchUp(applicationContext)
        HeartbeatPolicy.schedule(applicationContext)
        com.phantom.checker.data.MemoryExpiryPolicy.schedule(applicationContext)
        com.phantom.checker.data.ScamImmunityPolicy.schedule(applicationContext)
        com.phantom.checker.service.GroupReportEngine.scheduleOrCancel(applicationContext)
        StatsForegroundService.startOrRefresh(applicationContext)
        parseIntentTab(intent)
        setContent {
            PhantomCheckTheme {
                PhantomCheckApp(
                    requestedTab = requestedTabState.value,
                    requestedInsightSubTab = requestedInsightSubTabState.value,
                    onConsumedRequestedTab = {
                        requestedTabState.value = null
                        requestedInsightSubTabState.value = null
                    },
                    requestedEditTodoId = requestedEditTodoIdState.value,
                    onConsumedEditTodoId = { requestedEditTodoIdState.value = null },
                    requestedBriefingMsgId = requestedBriefingMsgIdState.value,
                    onConsumedBriefingMsgId = { requestedBriefingMsgIdState.value = null }
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
        mapLegacyTabExtra(intent?.getStringExtra(DailySummaryPolicy.EXTRA_OPEN_TAB))?.let { (tab, subTab) ->
            requestedTabState.value = tab
            if (subTab != null) requestedInsightSubTabState.value = subTab
        }
        val briefingMsgId = intent?.getLongExtra(com.phantom.checker.service.BriefingScheduler.EXTRA_BRIEFING_MSG_ID, -1L) ?: -1L
        if (briefingMsgId > 0L) {
            requestedTabState.value = MainTab.ASSISTANT
            requestedBriefingMsgIdState.value = briefingMsgId
            PhantomPrefs.markBriefingReadOnce(applicationContext, briefingMsgId)
        }
        val todoId = intent?.getLongExtra(com.phantom.checker.widget.ReminderWidgetProvider.EXTRA_EDIT_TODO_ID, -1L) ?: -1L
        if (todoId > 0L) {
            requestedTabState.value = MainTab.NOTES
            requestedEditTodoIdState.value = todoId
        }
    }
}

/**
 * 單一深連結映射函式（R2 相容保證）：所有既有通知、Widget、教學跳轉不中斷。
 * 既有字串契約（DailySummaryPolicy.TAB_STATS、ReminderWidgetProvider.TAB_NOTES、
 * BriefingScheduler.TAB_ASSISTANT、"memory"、"partner"）完全不變。
 */
private fun mapLegacyTabExtra(raw: String?): Pair<MainTab, InsightSubTab?>? = when (raw) {
    DailySummaryPolicy.TAB_STATS -> MainTab.INSIGHT to InsightSubTab.STATS
    "partner" -> MainTab.INSIGHT to InsightSubTab.PARTNER
    "insight" -> MainTab.INSIGHT to null
    com.phantom.checker.widget.ReminderWidgetProvider.TAB_NOTES -> MainTab.NOTES to null
    "memory" -> MainTab.MEMORY to null
    com.phantom.checker.service.BriefingScheduler.TAB_ASSISTANT -> MainTab.ASSISTANT to null
    else -> null
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

/** #466 桌面端連線分頁專屬圖示（Material Devices 風格純向量，無需額外 icons-extended 依賴） */
internal val LinkDevicesIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "LinkDevices",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 6f)
            horizontalLineToRelative(18f)
            verticalLineTo(4f)
            horizontalLineTo(4f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(11f)
            horizontalLineTo(0f)
            verticalLineToRelative(3f)
            horizontalLineToRelative(14f)
            verticalLineToRelative(-3f)
            horizontalLineTo(4f)
            verticalLineTo(6f)
            close()
            moveTo(23f, 8f)
            horizontalLineToRelative(-6f)
            curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
            verticalLineToRelative(10f)
            curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
            horizontalLineToRelative(6f)
            curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
            verticalLineTo(9f)
            curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
            close()
            moveTo(22f, 17f)
            horizontalLineToRelative(-4f)
            verticalLineToRelative(-7f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(7f)
            close()
        }
    }.build()
}

/** #466 底部導覽 5 → 6（新增桌面端「連線」分頁） */
private enum class MainTab(@StringRes val titleRes: Int) {
    MESSAGES(R.string.common_messages),
    ASSISTANT(R.string.common_assistant),
    NOTES(R.string.common_notes),
    MEMORY(R.string.common_memory),
    INSIGHT(R.string.common_insight),
    LINK(R.string.common_link)
}

private enum class InsightSubTab(@StringRes val titleRes: Int) {
    PARTNER(R.string.common_partner),
    STATS(R.string.common_stats)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PhantomCheckApp(
    requestedTab: MainTab? = null,
    requestedInsightSubTab: InsightSubTab? = null,
    onConsumedRequestedTab: () -> Unit = {},
    requestedEditTodoId: Long? = null,
    onConsumedEditTodoId: () -> Unit = {},
    requestedBriefingMsgId: Long? = null,
    onConsumedBriefingMsgId: () -> Unit = {}
) {
    val resCtx = LocalContext.current
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val assistantSnackbarHostState = remember { SnackbarHostState() }

    var selectedTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(MainTab.MESSAGES) }
    var insightSubTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(InsightSubTab.PARTNER) }
    var requestedMemoryFilter by remember { mutableStateOf<String?>(null) }
    val saveableStateHolder = rememberSaveableStateHolder()
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchRiskFilter by remember { mutableStateOf(SearchRiskFilter.ALL) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val tabLastLoadedMs = remember { mutableStateMapOf<MainTab, Long>() }
    var lastLoadedFilter by remember { mutableStateOf<MessageFilter?>(null) }
    var lastLoadedRefreshTrigger by remember { mutableIntStateOf(-1) }

    LaunchedEffect(requestedTab, requestedInsightSubTab) {
        if (requestedTab != null) {
            selectedTab = requestedTab
            if (requestedInsightSubTab != null) {
                insightSubTab = requestedInsightSubTab
            }
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

    // #511 跨 App 多選批次刪除狀態（唯一入口：長按 App 功能表）
    var isAppMultiSelectMode by remember { mutableStateOf(false) }
    var selectedAppPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var estimatedAppBatchDeleteCount by remember { mutableIntStateOf(0) }
    var showAppBatchDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(isAppMultiSelectMode, selectedAppPackages, refreshTrigger) {
        if (!isAppMultiSelectMode || selectedAppPackages.isEmpty()) {
            estimatedAppBatchDeleteCount = 0
        } else {
            estimatedAppBatchDeleteCount = withContext(Dispatchers.IO) {
                AppDatabaseHolder.db(appCtx).messageDao().countDeletableByApps(selectedAppPackages.toList())
            }
        }
    }

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
                OnboardingStep.NOTES_TAB -> MainTab.NOTES
                OnboardingStep.MEMORY_TAB -> MainTab.MEMORY
                OnboardingStep.INSIGHT_TAB -> {
                    insightSubTab = InsightSubTab.PARTNER
                    MainTab.INSIGHT
                }
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
    var partnerTabLabel by remember(settingsVersion) { mutableStateOf(PhantomPrefs.getPartnerTabLabel(appCtx)) }
    var partnerAvatarBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(settingsVersion) {
        partnerTabLabel = PhantomPrefs.getPartnerTabLabel(appCtx)
        partnerAvatarBitmap = com.phantom.checker.util.AssistantAvatarHelper.loadAvatarBitmap(appCtx)
    }
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
                    AppUpdateHelper.isNetworkAvailable(appCtx) &&
                    !AppUpdateHelper.isSnoozed(appCtx) &&
                    MainActivity.hasAutoCheckedUpdateThisProcess.compareAndSet(false, true)
                ) {
                    scope.launch {
                        when (val outcome = AppUpdateHelper.checkAndNotifyWithOutcome(appCtx)) {
                            is AppUpdateHelper.AutoUpdateCheckOutcome.Available -> {
                                pendingUpdateDialog = outcome.update
                            }
                            is AppUpdateHelper.AutoUpdateCheckOutcome.Failed -> {
                                // #374 單一進程失敗最多允許重試一次（總計最多 2 次嘗試）
                                if (MainActivity.autoUpdateCheckFailuresThisProcess.incrementAndGet() < 2) {
                                    MainActivity.hasAutoCheckedUpdateThisProcess.set(false)
                                }
                            }
                            is AppUpdateHelper.AutoUpdateCheckOutcome.Skipped -> {
                                // 無網路或尚在 snooze，釋放旗標待條件齊備後補檢
                                MainActivity.hasAutoCheckedUpdateThisProcess.set(false)
                            }
                            is AppUpdateHelper.AutoUpdateCheckOutcome.UpToDate -> Unit
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
    LaunchedEffect(selectedTab, insightSubTab, searchQuery, activeFilter, refreshTrigger) {
        val q = searchQuery.trim()
        val now = System.currentTimeMillis()
        val filterChanged = lastLoadedFilter != activeFilter
        val refreshChanged = lastLoadedRefreshTrigger != refreshTrigger
        if (q.isEmpty() && !filterChanged && !refreshChanged && !(selectedTab == MainTab.INSIGHT && insightSubTab == InsightSubTab.STATS)) {
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
        val loadSnapshot: MainLoadSnapshot = withContext(Dispatchers.IO) {
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
                MainLoadSnapshot.Search(
                    notedMsgIds = dao.notedMessageIds().toSet(),
                    todoedMsgIds = dao.todoedMessageIds().toSet(),
                    searchResults = found,
                    activeFilterKeywords = keywords,
                    messageTagsMap = foundTags
                )
            } else {
                when (selectedTab) {
                    MainTab.MESSAGES -> {
                        val overview = dao.statsOverview(startOfDay)
                        val loadedTags = db.tagDao().all()
                        val loadedTagCounts = db.tagDao().allTagCounts()
                        val loadedNotedIds = dao.notedMessageIds().toSet()
                        val loadedTodoedIds = dao.todoedMessageIds().toSet()

                        if (activeFilter == MessageFilter.All) {
                            val groups = dao.groupByApp()
                            val todayMap = dao.todayCountsByApp(startOfDay)
                                .associate { it.packageName to it.cnt }
                            val unreadTodayMap = dao.todayUnreadCountsByApp(startOfDay)
                                .associate { it.packageName to it.cnt }
                            val totalUnreadMap = dao.unreadCountsByApp()
                                .associate { it.packageName to it.cnt }
                            val latestIdsByApp = dao.latestMessageIdsByApp()
                                .associate { it.packageName to it.messageId }
                            val latestMsgIds = latestIdsByApp.values.distinct()
                            // P0: 單次批次 SQL 取得各 App 最新訊息預覽（自動 chunked(500)），消除 N+1 dao.byApp() OOM
                            val latestMsgsById = if (latestMsgIds.isEmpty()) {
                                emptyMap()
                            } else {
                                dao.byIds(latestMsgIds).associateBy { it.id }
                            }
                            val tagsByLatestMsgId = if (latestMsgIds.isEmpty()) {
                                emptyMap()
                            } else {
                                db.tagDao().tagsForMessages(latestMsgIds).groupBy { it.messageId }
                            }
                            val sortOrder = PhantomPrefs.getHomeSortOrder(appCtx)
                            val loadedAppGroups = groups.map { g ->
                                val latestMsgId = latestIdsByApp[g.packageName]
                                val preview = latestMsgId?.let { latestMsgsById[it]?.content }.orEmpty()
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
                            MainLoadSnapshot.MessagesAll(
                                todayCount = overview.today,
                                unreadCount = overview.unread,
                                todayScamCount = overview.scam,
                                availableTags = loadedTags,
                                tagCounts = loadedTagCounts,
                                notedMsgIds = loadedNotedIds,
                                todoedMsgIds = loadedTodoedIds,
                                appGroups = loadedAppGroups
                            )
                        } else {
                            val page = queryFilteredPage(0)
                            val pageTags = if (page.isEmpty()) {
                                emptyMap()
                            } else {
                                db.tagDao().tagsForMessages(page.map { it.id }).groupBy { it.messageId }
                            }
                            MainLoadSnapshot.MessagesFiltered(
                                todayCount = overview.today,
                                unreadCount = overview.unread,
                                todayScamCount = overview.scam,
                                availableTags = loadedTags,
                                tagCounts = loadedTagCounts,
                                notedMsgIds = loadedNotedIds,
                                todoedMsgIds = loadedTodoedIds,
                                filteredMessages = page,
                                messageTagsMap = pageTags,
                                hasMoreFiltered = page.size >= PAGE_SIZE
                            )
                        }
                    }
                    MainTab.ASSISTANT -> MainLoadSnapshot.NoOp
                    MainTab.MEMORY -> MainLoadSnapshot.NoOp
                    MainTab.NOTES -> MainLoadSnapshot.NoOp
                    MainTab.LINK -> MainLoadSnapshot.NoOp
                    MainTab.INSIGHT -> if (insightSubTab == InsightSubTab.STATS) {
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
                        val warning = dao.warningCount()
                        val suspicious = dao.countByRiskLabel("suspicious")
                        val scam = dao.countByRiskLabel("scam")
                        val harassmentTagIds = db.tagDao().all()
                            .filter { it.name == "行銷" || it.name == "垃圾通知" }
                            .map { it.id }
                            .toSet()
                        val harassmentCnt = if (harassmentTagIds.isEmpty()) {
                            0
                        } else {
                            db.tagDao().allTagCounts()
                                .filter { it.tagId in harassmentTagIds }
                                .sumOf { it.cnt }
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
                        MainLoadSnapshot.Stats(
                            StatsUiState(
                                latestSummary = summary,
                                latestWeeklySummary = weeklySummary,
                                weeklyCounts = weekly,
                                weeklyRiskCounts = weeklyRisk,
                                safeCount = safe,
                                suspiciousCount = suspicious,
                                warningCount = warning,
                                scamCount = scam,
                                topRiskyApps = topRisky,
                                dailyTopApps = dailyTopApps,
                                rawAppGroups = rawGroups,
                                todayAiTagCounts = aiTagCounts,
                                todayHourlyCounts = hourly.toList(),
                                harassmentCount = harassmentCnt,
                                totalMessageCount = safe + suspicious + warning + scam,
                                unreadTotalCount = unreadTotal,
                                todoTotalCount = todoCompletion.total,
                                todoDoneCount = todoCompletion.done
                            )
                        )
                    } else {
                        MainLoadSnapshot.NoOp
                    }
                }
            }
        }
        when (loadSnapshot) {
            is MainLoadSnapshot.Search -> {
                notedMsgIds = loadSnapshot.notedMsgIds
                todoedMsgIds = loadSnapshot.todoedMsgIds
                searchResults = loadSnapshot.searchResults
                activeFilterKeywords = loadSnapshot.activeFilterKeywords
                messageTagsMap = loadSnapshot.messageTagsMap
            }
            is MainLoadSnapshot.MessagesAll -> {
                todayCount = loadSnapshot.todayCount
                unreadCount = loadSnapshot.unreadCount
                todayScamCount = loadSnapshot.todayScamCount
                availableTags = loadSnapshot.availableTags
                tagCounts = loadSnapshot.tagCounts
                notedMsgIds = loadSnapshot.notedMsgIds
                todoedMsgIds = loadSnapshot.todoedMsgIds
                appGroups = loadSnapshot.appGroups
            }
            is MainLoadSnapshot.MessagesFiltered -> {
                todayCount = loadSnapshot.todayCount
                unreadCount = loadSnapshot.unreadCount
                todayScamCount = loadSnapshot.todayScamCount
                availableTags = loadSnapshot.availableTags
                tagCounts = loadSnapshot.tagCounts
                notedMsgIds = loadSnapshot.notedMsgIds
                todoedMsgIds = loadSnapshot.todoedMsgIds
                filteredMessages = loadSnapshot.filteredMessages
                messageTagsMap = loadSnapshot.messageTagsMap
                hasMoreFiltered = loadSnapshot.hasMoreFiltered
            }
            is MainLoadSnapshot.Stats -> {
                statsState = loadSnapshot.statsState
            }
            MainLoadSnapshot.NoOp -> Unit
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
            title = { Text(stringResource(R.string.main_s_0712f5)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.main_s_8e723c, update.latestVersion, update.publishedDate),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    ReleaseNotesPreviewBox(
                        title = stringResource(R.string.main_s_0d3826, update.latestVersion, update.publishedDate),
                        releaseNotes = update.releaseNotes
                    )
                    Text(
                        stringResource(R.string.main_s_650b91),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                    Text(stringResource(R.string.main_s_b9af4a))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        PhantomPrefs.setUpdateSnoozeUntilMs(
                            appCtx,
                            System.currentTimeMillis() + PhantomPrefs.getUpdateSnoozeMinutes(appCtx) * 60L * 1000L
                        )
                        pendingUpdateDialog = null
                    }
                ) {
                    Text(stringResource(R.string.main_s_7172a7))
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
                    Text(stringResource(R.string.main_s_47bfac, target.appLabel))
                    OutlinedButton(
                        onClick = {
                            deleteAppConfirm = target
                            actionTargetApp = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.main_s_7a2223), color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(
                        onClick = {
                            isAppMultiSelectMode = true
                            selectedAppPackages = setOf(target.packageName)
                            actionTargetApp = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.batch_app_multi_select_btn))
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
                            if (pinned) resCtx.getString(R.string.main_s_3baee6, target.appLabel) else resCtx.getString(R.string.main_s_12ce1c, target.appLabel)
                        )
                    }
                }) {
                    Text(if (isFav) stringResource(R.string.main_s_8e10e2) else stringResource(R.string.main_s_aec3bf))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (isMuted) {
                        PhantomPrefs.setAppMuted(appCtx, target.packageName, false)
                        mutedPackages = PhantomPrefs.getMutedApps(appCtx)
                        actionTargetApp = null
                        scope.launch {
                            snackbarHostState.showSnackbar(resCtx.getString(R.string.main_s_f7690c, target.appLabel))
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
                            snackbarHostState.showSnackbar(resCtx.getString(R.string.main_s_d95152, target.appLabel))
                        }
                    }
                }) {
                    Text(
                        text = if (isMuted) stringResource(R.string.main_s_fd9e9a) else stringResource(R.string.main_s_ab2d5c),
                        color = if (isMuted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        )
    }

    deleteAppConfirm?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteAppConfirm = null },
            title = { Text(stringResource(R.string.main_s_7a2223)) },
            text = { Text(stringResource(R.string.main_s_13d218, target.appLabel)) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                db.withTransaction {
                                    db.messageDao().deleteByApp(target.packageName)
                                    db.messageDao().deleteAllMessageOrphans()
                                }
                            }
                            StatsForegroundService.startOrRefresh(appCtx)
                            WidgetUpdater.scheduleRefresh(appCtx)
                            deleteAppConfirm = null
                            refreshTrigger++
                            snackbarHostState.showSnackbar(resCtx.getString(R.string.main_s_af39ba, target.appLabel))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.common_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteAppConfirm = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    deleteMessageConfirm?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteMessageConfirm = null },
            title = { Text(stringResource(R.string.common_delete_message)) },
            text = { Text(stringResource(R.string.main_s_a21edd, target.sender)) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                db.messageDao().deleteByIdWithOrphans(target.id)
                            }
                            StatsForegroundService.startOrRefresh(appCtx)
                            WidgetUpdater.scheduleRefresh(appCtx)
                            deleteMessageConfirm = null
                            refreshTrigger++
                            snackbarHostState.showSnackbar(resCtx.getString(R.string.common_message_deleted))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.common_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteMessageConfirm = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showBatchDeleteConfirm && selectedMessageIds.isNotEmpty()) {
        val count = selectedMessageIds.size
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text(stringResource(R.string.common_batch_delete_messages)) },
            text = { Text(stringResource(R.string.main_s_02bcec, count)) },
            confirmButton = {
                Button(
                    onClick = {
                        val ids = selectedMessageIds.toList()
                        scope.launch {
                            val (deletedCount, protectedCount) = withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                val deleted = db.messageDao().deleteNonScamByIdsWithOrphans(ids)
                                val protected = (ids.size - deleted).coerceAtLeast(0)
                                deleted to protected
                            }
                            StatsForegroundService.startOrRefresh(appCtx)
                            WidgetUpdater.scheduleRefresh(appCtx)
                            showBatchDeleteConfirm = false
                            isMultiSelectMode = false
                            selectedMessageIds = emptySet()
                            refreshTrigger++
                            val msgText = when {
                                deletedCount > 0 && protectedCount > 0 ->
                                    resCtx.getString(R.string.threads_s_8b7bd6, deletedCount, protectedCount)
                                deletedCount == 0 && protectedCount > 0 ->
                                    resCtx.getString(R.string.conv_s_6703f2, protectedCount)
                                else ->
                                    resCtx.getString(R.string.main_s_330b12, deletedCount)
                            }
                            snackbarHostState.showSnackbar(msgText)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.common_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showAppBatchDeleteConfirm && selectedAppPackages.isNotEmpty()) {
        val pkgs = selectedAppPackages.toList()
        val estCount = estimatedAppBatchDeleteCount
        AlertDialog(
            onDismissRequest = { showAppBatchDeleteConfirm = false },
            title = { Text(stringResource(R.string.batch_app_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.batch_app_delete_confirm_body, pkgs.size, estCount))
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val (deletedCount, remainingCount) = withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                val ids = db.messageDao().deletableIdsByApps(pkgs, limit = 100)
                                if (ids.isEmpty()) {
                                    0 to 0
                                } else {
                                    val deleted = db.messageDao().deleteNonScamByIdsWithOrphans(ids)
                                    val rem = db.messageDao().countDeletableByApps(pkgs)
                                    deleted to rem
                                }
                            }
                            StatsForegroundService.startOrRefresh(appCtx)
                            WidgetUpdater.scheduleRefresh(appCtx)
                            showAppBatchDeleteConfirm = false
                            if (remainingCount <= 0) {
                                isAppMultiSelectMode = false
                                selectedAppPackages = emptySet()
                            }
                            refreshTrigger++
                            val msg = when {
                                deletedCount == 0 -> resCtx.getString(R.string.batch_app_delete_none)
                                remainingCount > 0 -> resCtx.getString(R.string.batch_app_delete_done_more, deletedCount, remainingCount)
                                else -> resCtx.getString(R.string.batch_app_delete_done, deletedCount)
                            }
                            snackbarHostState.showSnackbar(msg)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.common_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAppBatchDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
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
                    text = resCtx.getString(R.string.common_message_actions),
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
                                snackbarHostState.showSnackbar(if (nextStar) resCtx.getString(R.string.common_starred_added) else resCtx.getString(R.string.common_starred_removed))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (msg.isStarred) resCtx.getString(R.string.common_remove_star) else resCtx.getString(R.string.common_add_star))
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
                                        WidgetUpdater.scheduleRefresh(appCtx)
                                    }
                                    messageSheetTarget = null
                                    refreshTrigger++
                                    snackbarHostState.showSnackbar(resCtx.getString(R.string.main_s_b57b71, res.riskLabel, res.riskScore))
                                } else {
                                    snackbarHostState.showSnackbar(resCtx.getString(R.string.common_ai_analyze_failed))
                                }
                            }
                        },
                        enabled = !isAnalyzingSheetMsg,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isAnalyzingSheetMsg) resCtx.getString(R.string.common_analyzing) else resCtx.getString(R.string.common_ai_analyze))
                    }
                }

                if (availableTags.isNotEmpty()) {
                    Text(resCtx.getString(R.string.common_tags), style = MaterialTheme.typography.labelMedium)
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
                                            WidgetUpdater.scheduleRefresh(appCtx)
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
                        Text(resCtx.getString(R.string.common_remind_later))
                    }
                    OutlinedButton(
                        onClick = {
                            messageSheetTarget = null
                            ctx.startActivity(MessageDetailActivity.createIntent(ctx, msg.id))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(resCtx.getString(R.string.common_details))
                    }
                    Button(
                        onClick = {
                            messageSheetTarget = null
                            deleteMessageConfirm = msg
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(resCtx.getString(R.string.common_delete))
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
                    WidgetUpdater.scheduleRefresh(appCtx)
                    refreshTrigger++
                    val formatted = PhantomPrefs.formatMessageTime(appCtx, ts, includeDate = true)
                    snackbarHostState.showSnackbar(resCtx.getString(R.string.main_s_ed678a, formatted))
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
                        text = stringResource(R.string.main_s_f92e2a),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (selectedTagId != null) {
                        TextButton(onClick = {
                            activeFilter = MessageFilter.All
                            showTagPickerSheet = false
                        }) {
                            Text(stringResource(R.string.main_s_2628cd))
                        }
                    }
                }
                OutlinedTextField(
                    value = tagPickerQuery,
                    onValueChange = { tagPickerQuery = it },
                    placeholder = { Text(stringResource(R.string.main_s_e7dc9e)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (tagPickerQuery.isNotEmpty()) {
                            IconButton(onClick = { tagPickerQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.common_clear))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (filteredTagRows.isEmpty()) {
                    Text(
                        text = if (tagCounts.isEmpty()) stringResource(R.string.main_s_09327a) else stringResource(R.string.main_s_db2846, tagPickerQuery),
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
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = com.phantom.checker.ui.phantomSurfaceAlpha(0.45f))
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
                                            text = stringResource(R.string.main_s_ddadbf, row.cnt),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = stringResource(R.string.common_selected),
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
                                        WidgetUpdater.scheduleRefresh(appCtx)
                                        isMultiSelectMode = false
                                        selectedMessageIds = emptySet()
                                        refreshTrigger++
                                        snackbarHostState.showSnackbar(resCtx.getString(R.string.main_s_9ebfdd, ids.size))
                                    }
                                }
                            ) { Text(stringResource(R.string.common_read)) }
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
                                        snackbarHostState.showSnackbar(resCtx.getString(R.string.main_s_2a496c, ids.size))
                                    }
                                }
                            ) { Text(stringResource(R.string.common_star)) }
                            TextButton(
                                enabled = selectedMessageIds.isNotEmpty(),
                                onClick = { showBatchDeleteConfirm = true }
                            ) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
                            TextButton(
                                onClick = {
                                    isMultiSelectMode = false
                                    selectedMessageIds = emptySet()
                                }
                            ) { Text(stringResource(R.string.common_cancel)) }
                        }
                    }
                } else if (isAppMultiSelectMode) {
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
                            Text(
                                text = stringResource(
                                    R.string.batch_app_selected_summary,
                                    selectedAppPackages.size,
                                    estimatedAppBatchDeleteCount
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                enabled = selectedAppPackages.isNotEmpty(),
                                onClick = { showAppBatchDeleteConfirm = true }
                            ) {
                                Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                            }
                            TextButton(
                                onClick = {
                                    isAppMultiSelectMode = false
                                    selectedAppPackages = emptySet()
                                }
                            ) {
                                Text(stringResource(R.string.common_cancel))
                            }
                        }
                    }
                } else if (LocalConfiguration.current.screenWidthDp < 600) {
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
                                        MainTab.MEMORY -> MemoryIcon
                                        MainTab.INSIGHT -> Icons.Default.Person
                                        MainTab.LINK -> LinkDevicesIcon
                                    }
                                    val tabLabel = stringResource(tab.titleRes)
                                    val itemModifier = Modifier
                                        .weight(1f)
                                        .onGloballyPositioned { coords ->
                                            val rect = coords.boundsInRoot()
                                            when (tab) {
                                                MainTab.ASSISTANT -> targetBounds = targetBounds + (OnboardingTargets.AI_ASSISTANT_TAB to rect)
                                                MainTab.MEMORY -> targetBounds = targetBounds + (OnboardingTargets.MEMORY_TAB to rect)
                                                MainTab.NOTES -> targetBounds = targetBounds + (OnboardingTargets.NOTES_TAB to rect)
                                                MainTab.INSIGHT -> targetBounds = targetBounds +
                                                    (OnboardingTargets.PARTNER_TAB to rect) +
                                                    (OnboardingTargets.STATS_TAB to rect)
                                                MainTab.MESSAGES -> Unit
                                                MainTab.LINK -> Unit
                                            }
                                        }
                                    val customIcon: (@Composable () -> Unit)? = if (tab == MainTab.INSIGHT && partnerAvatarBitmap != null) {
                                        {
                                            com.phantom.checker.util.AssistantAvatarCircle(
                                                avatarBitmap = partnerAvatarBitmap,
                                                assistantName = partnerTabLabel,
                                                size = if (isCompactTabBar) 20.dp else 22.dp
                                            )
                                        }
                                    } else null
                                    LiquidGlassTabBarItem(
                                        selected = selectedTab == tab,
                                        onClick = {
                                            haptic(HapticFeedbackType.TextHandleMove)
                                            selectedTab = tab
                                        },
                                        icon = icon,
                                        label = tabLabel,
                                        modifier = itemModifier,
                                        compact = isCompactTabBar,
                                        customIcon = customIcon
                                    )
                                }
                            }
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        val isExpandedWidth = screenWidthDp >= 600
        val horizPad = auroraHorizontalPadding()
        val pullThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
        val pullDownSearchConnection = remember(selectedTab, pullThresholdPx) {
            object : NestedScrollConnection {
                private var overscrollAccum = 0f
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    if (selectedTab != MainTab.ASSISTANT && source == NestedScrollSource.UserInput) {
                        if (available.y > 0f) {
                            overscrollAccum += available.y
                            if (overscrollAccum >= pullThresholdPx) {
                                overscrollAccum = 0f
                                showSearchBar = true
                            }
                        } else if (available.y < 0f) {
                            overscrollAccum = 0f
                        }
                    }
                    return Offset.Zero
                }
            }
        }
        PhantomBackgroundBox(
            layer = if (selectedTab == MainTab.ASSISTANT) {
                com.phantom.checker.util.BackgroundLayer.CHAT
            } else {
                com.phantom.checker.util.BackgroundLayer.GLOBAL
            },
            glassState = glassState,
            modifier = Modifier.fillMaxSize()
        ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // D7：大螢幕（≥600dp）導覽軌取代底欄
            if (isExpandedWidth && !isMultiSelectMode) {
                NavigationRail(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(80.dp)
                        .padding(top = padding.calculateTopPadding()),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f)
                ) {
                    Spacer(Modifier.height(12.dp))
                    MainTab.entries.forEach { tab ->
                        val icon = when (tab) {
                            MainTab.MESSAGES -> Icons.Default.Email
                            MainTab.ASSISTANT -> AutoAwesomeIcon
                            MainTab.NOTES -> Icons.Default.Edit
                            MainTab.MEMORY -> MemoryIcon
                            MainTab.INSIGHT -> Icons.Default.Person
                            MainTab.LINK -> LinkDevicesIcon
                        }
                        NavigationRailItem(
                            selected = selectedTab == tab,
                            onClick = {
                                haptic(HapticFeedbackType.TextHandleMove)
                                selectedTab = tab
                            },
                            icon = { Icon(icon, contentDescription = stringResource(tab.titleRes)) },
                            label = {
                                Text(
                                    text = stringResource(tab.titleRes),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .nestedScroll(pullDownSearchConnection)
                .padding(padding)
                .padding(horizontal = horizPad, vertical = 4.dp)
        ) {
            // §4.0 頂部 Small 應用列（助手分頁由 ChatScreen 自繪應用列；D5 移除 22dp 把手視覺、保留下拉手勢）
            if (selectedTab != MainTab.ASSISTANT) {
                var showTopOverflow by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AuroraTokens.SmallTopBarHeight),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(selectedTab.titleRes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(4.dp))
                    if (screenWidthDp >= 360) {
                        Surface(
                            onClick = { showSearchBar = true },
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .widthIn(min = 120.dp)
                                .onGloballyPositioned { coords ->
                                    targetBounds = targetBounds + (OnboardingTargets.SEARCH_PILL to coords.boundsInRoot())
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(R.string.common_open_global_search),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (searchQuery.isNotBlank()) searchQuery else stringResource(R.string.main_s_b11f3c),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (searchQuery.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = { showSearchBar = true },
                            modifier = Modifier
                                .size(AuroraTokens.MinTouchTarget)
                                .onGloballyPositioned { coords ->
                                    targetBounds = targetBounds + (OnboardingTargets.SEARCH_PILL to coords.boundsInRoot())
                                }
                        ) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.common_open_global_search))
                        }
                    }
                    IconButton(
                        onClick = { ctx.startActivity(Intent(ctx, SettingsActivity::class.java)) },
                        modifier = Modifier
                            .size(AuroraTokens.MinTouchTarget)
                            .onGloballyPositioned { coords ->
                                targetBounds = targetBounds + (OnboardingTargets.SETTINGS_GEAR to coords.boundsInRoot())
                            }
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.common_settings))
                    }
                    Box {
                        IconButton(
                            onClick = { showTopOverflow = true },
                            modifier = Modifier.size(AuroraTokens.MinTouchTarget)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.common_more_options))
                        }
                        DropdownMenu(
                            expanded = showTopOverflow,
                            onDismissRequest = { showTopOverflow = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_refresh)) },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = {
                                    showTopOverflow = false
                                    refreshTrigger++
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_settings_center)) },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    showTopOverflow = false
                                    ctx.startActivity(Intent(ctx, SettingsActivity::class.java))
                                }
                            )
                        }
                    }
                }
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
                                snackbarHostState.showSnackbar(resCtx.getString(R.string.main_s_10fa92))
                            } else {
                                snackbarHostState.showSnackbar(resCtx.getString(R.string.main_s_9190d2))
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
                        snackbarHostState.showSnackbar(resCtx.getString(R.string.main_s_73dad7, MAX_BATCH_SELECT))
                    }
                } else {
                    selectedMessageIds = selectedMessageIds + id
                }
            }

            // #398 M4：分頁切換進出場（AnimatedContent，stable）——依分頁順序決定滑動方向；
            // 退場僅短淡出以縮短新舊分頁同時組合的時間。各分頁狀態仍以 SaveableStateProvider(tab.name) 保留。
            AnimatedContent(
                targetState = selectedTab,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                    (fadeIn(
                        animationSpec = tween(220, delayMillis = 60, easing = PhantomMotion.EmphasizedDecelerate)
                    ) + slideInHorizontally(
                        animationSpec = tween(260, easing = PhantomMotion.EmphasizedDecelerate)
                    ) { full -> dir * full / 12 })
                        .togetherWith(fadeOut(animationSpec = tween(110)))
                        .using(SizeTransform(clip = false) { _, _ -> snap() })
                },
                label = "mainTabContent"
            ) { tab ->
            saveableStateHolder.SaveableStateProvider(tab.name) {
              androidx.compose.runtime.CompositionLocalProvider(
                  com.phantom.checker.ui.components.LocalPhantomEnterRegistry provides remember { HashSet<Any>() }
              ) {
                when (tab) {
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
                                WidgetUpdater.scheduleRefresh(appCtx)
                                refreshTrigger++
                                snackbarHostState.showSnackbar(resCtx.getString(R.string.main_s_b23686))
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
                                snackbarHostState.showSnackbar(resCtx.getString(R.string.main_s_0f6b4a))
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
                        },
                        isAppMultiSelectMode = isAppMultiSelectMode,
                        selectedAppPackages = selectedAppPackages,
                        onToggleAppSelect = { pkg ->
                            selectedAppPackages = if (pkg in selectedAppPackages) {
                                selectedAppPackages - pkg
                            } else {
                                selectedAppPackages + pkg
                            }
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
                                onShowActionMessage = { msg, actionLabel, onAction ->
                                    scope.launch {
                                        val res = assistantSnackbarHostState.showSnackbar(
                                            message = msg,
                                            actionLabel = actionLabel,
                                            withDismissAction = true,
                                            duration = SnackbarDuration.Short
                                        )
                                        if (res == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                            onAction()
                                        }
                                    }
                                },
                                onOpenMemoryWithFilter = { filterKey ->
                                    requestedMemoryFilter = filterKey
                                    selectedTab = MainTab.MEMORY
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
                                },
                                requestedBriefingMsgId = requestedBriefingMsgId,
                                onConsumedBriefingMsgId = onConsumedBriefingMsgId
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
                        },
                        initialFilterKey = requestedMemoryFilter,
                        onConsumedInitialFilter = { requestedMemoryFilter = null }
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
                    MainTab.INSIGHT -> Column(modifier = Modifier.fillMaxSize()) {
                        PhantomSecondaryTabRow(
                            tabs = InsightSubTab.entries.map { stringResource(it.titleRes) },
                            selectedIndex = insightSubTab.ordinal,
                            onSelect = { idx ->
                                haptic(HapticFeedbackType.TextHandleMove)
                                insightSubTab = InsightSubTab.entries[idx]
                            },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        AnimatedContent(
                            targetState = insightSubTab,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            transitionSpec = {
                                val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                                (fadeIn(
                                    animationSpec = tween(220, delayMillis = 60, easing = PhantomMotion.EmphasizedDecelerate)
                                ) + slideInHorizontally(
                                    animationSpec = tween(260, easing = PhantomMotion.EmphasizedDecelerate)
                                ) { full -> dir * full / 12 })
                                    .togetherWith(fadeOut(animationSpec = tween(110)))
                                    .using(SizeTransform(clip = false) { _, _ -> snap() })
                            },
                            label = "insightSubTabContent"
                        ) { sub ->
                            when (sub) {
                                InsightSubTab.PARTNER -> com.phantom.checker.ui.partner.PartnerTab(
                                    glassState = glassState,
                                    onStartChat = { selectedTab = MainTab.ASSISTANT },
                                    onOpenPersonaSettings = {
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
                                InsightSubTab.STATS -> StatsTab(
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
                    MainTab.LINK -> com.phantom.checker.ui.link.LinkHubContent(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier = Modifier.fillMaxSize()
                    )
                }
              }
            }
            }
            }
        }
        }
    }

        val overlayResults = remember(searchResults, searchRiskFilter) {
            when (searchRiskFilter) {
                SearchRiskFilter.ALL -> searchResults
                SearchRiskFilter.SCAM -> searchResults.filter { it.riskLabel == "scam" }
                SearchRiskFilter.SUSPICIOUS -> searchResults.filter { it.riskLabel == "suspicious" || it.riskLabel == "warning" }
                SearchRiskFilter.STARRED -> searchResults.filter { it.isStarred }
            }
        }
        val defaultSearchScope = when (selectedTab) {
            MainTab.MESSAGES -> GlobalSearchScope.ALL
            MainTab.NOTES -> GlobalSearchScope.NOTES
            MainTab.MEMORY -> GlobalSearchScope.MEMORY
            MainTab.INSIGHT -> GlobalSearchScope.ALL
            MainTab.ASSISTANT -> GlobalSearchScope.ALL
            MainTab.LINK -> GlobalSearchScope.ALL
        }
        PhantomGlobalSearchOverlay(
            visible = showSearchBar,
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onDismiss = { showSearchBar = false },
            initialScope = defaultSearchScope,
            glassState = glassState,
            messageResults = overlayResults,
            searchRiskFilter = searchRiskFilter,
            onSearchRiskFilterChange = { searchRiskFilter = it },
            activeFilterKeywords = activeFilterKeywords,
            isLoadingMessages = isLoading,
            onOpenMessageDetail = { messageId ->
                ctx.startActivity(MessageDetailActivity.createIntent(ctx, messageId))
            },
            onOpenSettings = { settingId, legacyCardKey ->
                ctx.startActivity(
                    Intent(ctx, SettingsActivity::class.java).apply {
                        if (!settingId.isNullOrBlank()) putExtra("highlight_setting_id", settingId)
                        if (!legacyCardKey.isNullOrBlank()) putExtra("highlight_card_key", legacyCardKey)
                    }
                )
            },
            onOpenPermissionHub = {
                ctx.startActivity(Intent(ctx, PermissionHubActivity::class.java))
            },
            onReplayTour = {
                PhantomPrefs.setOnboardingCompleted(appCtx, false)
                onboardingCompleted = false
                onboardingStep = OnboardingStep.PERMISSION
            },
            onOpenMemoryTab = { selectedTab = MainTab.MEMORY },
            onOpenNotesTab = { selectedTab = MainTab.NOTES },
            onAiMessageSearch = { q ->
                showSearchBar = false
                selectedTab = MainTab.ASSISTANT
                if (q.isNotEmpty()) {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("ai_search_query", q))
                    scope.launch {
                        snackbarHostState.showSnackbar(resCtx.getString(R.string.main_s_c0d952, q))
                    }
                }
            },
            onShowSnackbar = { msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
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
                            snackbarHostState.showSnackbar(resCtx.getString(R.string.main_s_10fa92))
                        } else {
                            snackbarHostState.showSnackbar(resCtx.getString(R.string.main_s_9190d2))
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
                        OnboardingStep.PERMISSION -> OnboardingStep.SEARCH_PILL
                        OnboardingStep.SEARCH_PILL -> OnboardingStep.MESSAGES
                        OnboardingStep.MESSAGES -> OnboardingStep.AI_ASSISTANT
                        OnboardingStep.AI_ASSISTANT -> OnboardingStep.NOTES_TAB
                        OnboardingStep.NOTES_TAB -> OnboardingStep.MEMORY_TAB
                        OnboardingStep.MEMORY_TAB -> OnboardingStep.INSIGHT_TAB
                        OnboardingStep.INSIGHT_TAB -> OnboardingStep.SETTINGS_CENTER
                        OnboardingStep.SETTINGS_CENTER -> {
                            PhantomPrefs.setOnboardingCompleted(appCtx, true)
                            onboardingCompleted = true
                            OnboardingStep.SETTINGS_CENTER
                        }
                    }
                    PhantomPrefs.setOnboardingStep(appCtx, onboardingStep.index)
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
