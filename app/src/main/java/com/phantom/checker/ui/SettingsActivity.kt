package com.phantom.checker.ui

import com.phantom.checker.ui.components.PhantomMotion
import com.phantom.checker.ui.components.PhantomChevron
import android.app.DownloadManager
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.work.WorkManager
import com.phantom.checker.service.DbMaintenance
import kotlinx.coroutines.flow.first
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.ContextCompat
import com.phantom.checker.util.AppUpdateHelper
import com.phantom.checker.util.AssistantAvatarCircle
import com.phantom.checker.util.AssistantAvatarCropDialog
import com.phantom.checker.util.AssistantAvatarHelper
import com.phantom.checker.util.BackgroundImageHelper
import com.phantom.checker.util.BackgroundLayer
import com.phantom.checker.util.UpdateCheckResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.mutableStateMapOf
import com.phantom.checker.ui.components.AuroraCenteredContent
import com.phantom.checker.ui.components.AuroraTokens
import com.phantom.checker.ui.components.PhantomPageScaffold
import com.phantom.checker.ui.components.PhantomPredictiveBackContainer
import com.phantom.checker.ui.components.PhantomTopBarStyle
import com.phantom.checker.ui.components.SettingsAdvancedSection
import com.phantom.checker.ui.components.auroraHorizontalPadding
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.room.withTransaction
import com.phantom.checker.data.AppGroupRow
import com.phantom.checker.data.FilterRule
import com.phantom.checker.network.AiClient
import com.phantom.checker.network.ApiKeySource
import com.phantom.checker.network.IntelClient
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.service.HeartbeatPolicy
import com.phantom.checker.service.RetentionConfig
import com.phantom.checker.service.RetentionPolicy
import com.phantom.checker.service.StatsForegroundService
import com.phantom.checker.shizuku.ShizukuDiagItem
import com.phantom.checker.shizuku.ShizukuKeepAlive
import com.phantom.checker.shizuku.ShizukuManager
import com.phantom.checker.ui.components.FluidSwitch
import com.phantom.checker.ui.components.pressableScale
import com.phantom.checker.util.FontScaleMode
import com.phantom.checker.util.MessageExporter
import com.phantom.checker.util.MuteMode
import com.phantom.checker.util.NotifClickBehavior
import com.phantom.checker.util.PersistentNotifStyle
import com.phantom.checker.util.PhantomPrefs
import com.phantom.checker.util.PhantomSettingsBus
import com.phantom.checker.util.PresetKeywords
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private data class InstalledAppInfo(
    val packageName: String,
    val label: String
)

private data class OemGuideInfo(
    val brandTitle: String,
    val steps: String,
    val intents: List<Intent>
)

private fun buildOemGuide(ctx: Context): OemGuideInfo {
    val manufacturer = Build.MANUFACTURER.lowercase(Locale.US)
    val pkg = ctx.packageName
    val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$pkg")
    }
    return when {
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") ->
            OemGuideInfo(
                brandTitle = "小米 / Redmi / POCO（HyperOS / MIUI）保活指引",
                steps = "1. 開啟「自啟動」權限\n2. 省電策略改為「無限制」\n3. 最近任務下拉鎖定幻訊卡片",
                intents = listOf(
                    Intent().setComponent(
                        ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    ),
                    appDetailsIntent
                )
            )
        manufacturer.contains("samsung") ->
            OemGuideInfo(
                brandTitle = "Samsung（One UI）保活指引",
                steps = "前往「設定 → 電池 → 背景使用限制 → 永不休眠的應用程式」，將幻訊加入名單。",
                intents = listOf(
                    Intent("com.samsung.android.sm.ACTION_OPEN_APP_BATTERY_USAGE"),
                    appDetailsIntent
                )
            )
        manufacturer.contains("huawei") || manufacturer.contains("honor") ->
            OemGuideInfo(
                brandTitle = "華為 / 榮耀（EMUI / MagicOS）保活指引",
                steps = "前往「手機管家 → 應用程式啟動管理」，關閉幻訊自動管理並開啟「允許自啟動／關聯啟動／背景活動」三開關。",
                intents = listOf(
                    Intent().setComponent(
                        ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                    ),
                    appDetailsIntent
                )
            )
        manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") ->
            OemGuideInfo(
                brandTitle = "OPPO / realme / OnePlus（ColorOS）保活指引",
                steps = "開啟「自啟動」並於電池設定中允許「完全背景行為」。",
                intents = listOf(
                    Intent().setComponent(
                        ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                        )
                    ),
                    appDetailsIntent
                )
            )
        manufacturer.contains("vivo") || manufacturer.contains("iqoo") ->
            OemGuideInfo(
                brandTitle = "vivo / iQOO（OriginOS / FuntouchOS）保活指引",
                steps = "前往「i 管家 → 應用程式管理 → 權限管理 → 自啟動」，開啟幻訊自啟動與高耗電背景執行。",
                intents = listOf(
                    Intent().setComponent(
                        ComponentName(
                            "com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                        )
                    ),
                    appDetailsIntent
                )
            )
        else ->
            OemGuideInfo(
                brandTitle = "系統背景保活建議",
                steps = "請在「應用程式資訊 → 電池」中將幻訊設為「無限制」或「不受電池最佳化限制」，避免系統凍結統計服務。",
                intents = listOf(appDetailsIntent)
            )
    }
}

private fun tryLaunchIntents(ctx: Context, intents: List<Intent>) {
    for (intent in intents) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            return
        } catch (_: Exception) {
        }
    }
}

private fun isIgnoringBatteryOptimizations(ctx: Context): Boolean {
    val pm = ctx.getSystemService(PowerManager::class.java) ?: return true
    return pm.isIgnoringBatteryOptimizations(ctx.packageName)
}

private fun isStatsForegroundServiceRunning(ctx: Context): Boolean {
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
    return nm.activeNotifications.any { it.id == StatsForegroundService.STATS_NOTIF_ID }
}

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialCardKey = intent?.getStringExtra("highlight_card_key")?.let { raw ->
            if (raw == "permission_hub" || raw == "assistant_hub") "hub" else raw
        }
        val initialSettingId = intent?.getStringExtra("highlight_setting_id")
        setContent {
            PhantomCheckTheme {
                FilterSettingsScreen(
                    onBack = { finish() },
                    initialHighlightCardKey = initialCardKey,
                    initialHighlightSettingId = initialSettingId
                )
            }
        }
    }
}

private val SETTINGS_CARD_ORDER = listOf(
    "quick", "hub", "personalization", "stats", "ai", "memory_assistant", "notif", "data", "appearance", "shizuku", "intel", "about"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun FilterSettingsScreen(
    onBack: () -> Unit,
    initialHighlightCardKey: String? = null,
    initialHighlightSettingId: String? = null
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 規則與資料
    var rules by remember { mutableStateOf(listOf<FilterRule>()) }
    var appGroups by remember { mutableStateOf(listOf<AppGroupRow>()) }
    var newKeyword by remember { mutableStateOf("") }
    var newRuleAction by remember { mutableStateOf(FilterRule.ACTION_FILTER) }
    var isAllApps by remember { mutableStateOf(true) }
    var targetPackageName by remember { mutableStateOf("") }
    var isLoadingRules by remember { mutableStateOf(true) }
    var showPresetDialog by remember { mutableStateOf(false) }
    var isImportingPresets by remember { mutableStateOf(false) }

    // 通知設定
    var persistentStats by remember { mutableStateOf(PhantomPrefs.isPersistentStatsEnabled(appCtx)) }
    var persistentShowIcons by remember { mutableStateOf(PhantomPrefs.isPersistentNotifShowIcons(appCtx)) }
    var persistentNotifStyle by remember { mutableStateOf(PhantomPrefs.getPersistentNotifStyle(appCtx)) }
    var swipeToDeleteEnabled by remember { mutableStateOf(PhantomPrefs.isSwipeToDeleteEnabled(appCtx)) }
    var scamAlertNotif by remember { mutableStateOf(PhantomPrefs.isScamAlertNotifEnabled(appCtx)) }
    var recordOngoing by remember { mutableStateOf(PhantomPrefs.isRecordOngoingEnabled(appCtx)) }
    var clickBehavior by remember { mutableStateOf(PhantomPrefs.getNotifClickBehavior(appCtx)) }
    var throttleSec by remember { mutableFloatStateOf(PhantomPrefs.getSameKeyThrottleSeconds(appCtx).toFloat()) }
    var digitVarianceThrottle by remember { mutableStateOf(PhantomPrefs.isSameKeyDigitVarianceThrottleEnabled(appCtx)) }
    var appThrottleMap by remember { mutableStateOf(PhantomPrefs.getAppSameKeyThrottleMap(appCtx)) }
    var showAppThrottlePicker by remember { mutableStateOf(false) }
    var alertKeywords by remember { mutableStateOf(PhantomPrefs.getAlertKeywords(appCtx)) }
    var newAlertKeyword by remember { mutableStateOf("") }
    var quietHoursEnabled by remember { mutableStateOf(PhantomPrefs.isQuietHoursEnabled(appCtx)) }
    var quietStartMinute by remember { mutableIntStateOf(PhantomPrefs.getQuietHoursStartMinute(appCtx)) }
    var quietEndMinute by remember { mutableIntStateOf(PhantomPrefs.getQuietHoursEndMinute(appCtx)) }

    // AI 與分析設定
    var tokenSaverEnabled by remember { mutableStateOf(PhantomPrefs.isTokenSaverEnabled(appCtx)) }
    var aiRiskEnabled by remember { mutableStateOf(PhantomPrefs.isAiRiskEnabled(appCtx)) }
    var aiAutoTagEnabled by remember { mutableStateOf(PhantomPrefs.isAiAutoTagEnabled(appCtx)) }
    var aiOfflineRetryEnabled by remember { mutableStateOf(PhantomPrefs.isAiOfflineRetryEnabled(appCtx)) }
    var aiAutoMemoEnabled by remember { mutableStateOf(PhantomPrefs.isAiAutoMemoEnabled(appCtx)) }
    var aiTagFailureNotifEnabled by remember { mutableStateOf(PhantomPrefs.isAiTagFailureNotifEnabled(appCtx)) }
    var dualModelRoutingEnabled by remember { mutableStateOf(PhantomPrefs.isDualModelRoutingEnabled(appCtx)) }
    var dualModelRoutingModel by remember { mutableStateOf(PhantomPrefs.getDualModelRoutingModel(appCtx)) }
    var chatSuggestionsEnabled by remember { mutableStateOf(PhantomPrefs.isChatSuggestionsEnabled(appCtx)) }
    var aiChatTabContinue by remember { mutableStateOf(PhantomPrefs.isAiChatTabContinue(appCtx)) }
    var quickCommandDefaultMode by remember { mutableStateOf(PhantomPrefs.getQuickCommandDefaultMode(appCtx)) }
    var quickChatCommands by remember { mutableStateOf(PhantomPrefs.getQuickChatCommands(appCtx)) }
    var dailyAiLimit by remember { mutableIntStateOf(PhantomPrefs.getDailyAiLimit(appCtx)) }
    var chatRagLimit by remember { mutableIntStateOf(PhantomPrefs.getChatRagMessageLimit(appCtx)) }
    var aiMinTriggerLength by remember { mutableIntStateOf(PhantomPrefs.getAiMinTriggerLength(appCtx)) }
    var todayAiUsage by remember { mutableIntStateOf(PhantomPrefs.getTodayAiUsage(appCtx)) }


    // #315 B4 記憶庫與 AI 助手設定
    var memoryChatCaptureEnabled by remember { mutableStateOf(PhantomPrefs.isMemoryChatCaptureEnabled(appCtx)) }
    var memoryWriteConfirmEnabled by remember { mutableStateOf(PhantomPrefs.isMemoryWriteConfirmEnabled(appCtx)) }
    var antiFraudCrossCheckEnabled by remember { mutableStateOf(PhantomPrefs.isAntiFraudCrossCheckEnabled(appCtx)) }
    var contactsPermissionGranted by remember { mutableStateOf(com.phantom.checker.util.ContactContext.hasContactsPermission(appCtx)) }
    val contactsPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        contactsPermissionGranted = granted
        com.phantom.checker.util.ContactContext.invalidateCache()
        Toast.makeText(
            ctx,
            if (granted) "已授予通訊錄讀取權限（僅於本機離線比對熟人冒充，絕不外送）" else "未授予通訊錄權限，將自動降級為純記憶庫比對",
            Toast.LENGTH_SHORT
        ).show()
    }
    var calendarReadGranted by remember { mutableStateOf(com.phantom.checker.util.CalendarBridge.hasReadPermission(appCtx)) }
    var calendarWriteGranted by remember { mutableStateOf(com.phantom.checker.util.CalendarBridge.hasWritePermission(appCtx)) }
    var deviceCalendars by remember { mutableStateOf<List<com.phantom.checker.util.CalendarBridge.CalendarAccountInfo>>(emptyList()) }
    var selectedCalendarIds by remember { mutableStateOf(PhantomPrefs.getSelectedCalendarIds(appCtx)) }
    var calendarWriteConfirmEnabled by remember { mutableStateOf(PhantomPrefs.isCalendarWriteConfirmEnabled(appCtx)) }
    var calendarListExpanded by remember { mutableStateOf(PhantomPrefs.isCalendarListExpanded(appCtx)) }
    var scheduleMemoRoute by remember { mutableStateOf(PhantomPrefs.getScheduleMemoRoute(appCtx)) }
    val calendarPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultMap ->
        calendarReadGranted = resultMap[android.Manifest.permission.READ_CALENDAR] == true ||
            com.phantom.checker.util.CalendarBridge.hasReadPermission(appCtx)
        calendarWriteGranted = resultMap[android.Manifest.permission.WRITE_CALENDAR] == true ||
            com.phantom.checker.util.CalendarBridge.hasWritePermission(appCtx)
        Toast.makeText(
            ctx,
            if (calendarReadGranted) "已授予日曆權限，請勾選欲開放給助手的本機日曆" else "未授予日曆權限，日曆查詢功能維持停用",
            Toast.LENGTH_SHORT
        ).show()
    }
    LaunchedEffect(calendarReadGranted) {
        deviceCalendars = if (calendarReadGranted) {
            com.phantom.checker.util.CalendarBridge.listCalendars(appCtx)
        } else {
            emptyList()
        }
    }
    var memoryNormalTopK by remember { mutableIntStateOf(PhantomPrefs.getMemoryNormalTopK(appCtx)) }
    var memoryNormalPerItemChars by remember { mutableIntStateOf(PhantomPrefs.getMemoryNormalPerItemChars(appCtx)) }
    var memorySaverTopK by remember { mutableIntStateOf(PhantomPrefs.getMemorySaverTopK(appCtx)) }
    var assistantName by remember { mutableStateOf(PhantomPrefs.getAssistantName(appCtx)) }
    var assistantPersonalityPreset by remember { mutableStateOf(PhantomPrefs.getAssistantPersonalityPreset(appCtx)) }
    var assistantCustomPersonality by remember { mutableStateOf(PhantomPrefs.getAssistantCustomPersonality(appCtx)) }
    var assistantVerbosity by remember { mutableStateOf(PhantomPrefs.getAssistantVerbosity(appCtx)) }
    var assistantHasAvatar by remember { mutableStateOf(PhantomPrefs.hasAssistantAvatar(appCtx)) }
    var assistantAvatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var pendingAvatarCropUri by remember { mutableStateOf<Uri?>(null) }
    var isSavingAvatar by remember { mutableStateOf(false) }

    val avatarDocumentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingAvatarCropUri = uri
        }
    }
    val avatarVisualPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingAvatarCropUri = uri
        }
    }

    val settingsVersion by PhantomSettingsBus.version.collectAsState()
    LaunchedEffect(assistantHasAvatar, settingsVersion) {
        assistantAvatarBitmap = if (assistantHasAvatar) {
            AssistantAvatarHelper.loadAvatarBitmap(appCtx)
        } else {
            null
        }
    }

    // 資料與靜音管理
    var retentionDays by remember { mutableIntStateOf(RetentionConfig.getRetentionDays(appCtx)) }
    var retentionMaxCount by remember { mutableIntStateOf(PhantomPrefs.getRetentionMaxCount(appCtx)) }
    var mutedApps by remember { mutableStateOf(PhantomPrefs.getMutedApps(appCtx).sorted()) }
    var muteModes by remember { mutableStateOf(PhantomPrefs.getAppMuteModeMap(appCtx)) }
    var showAppPickerDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var showClearReadConfirm by remember { mutableStateOf(false) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    // 可收合卡狀態
    var expandQuick by remember { mutableStateOf(PhantomPrefs.isSettingsCardExpanded(appCtx, "quick")) }
    var expandAi by remember { mutableStateOf(PhantomPrefs.isSettingsCardExpanded(appCtx, "ai")) }
    var expandMemoryAssistant by remember { mutableStateOf(PhantomPrefs.isSettingsCardExpanded(appCtx, "memory_assistant")) }
    var expandNotif by remember { mutableStateOf(PhantomPrefs.isSettingsCardExpanded(appCtx, "notif")) }
    var expandData by remember { mutableStateOf(PhantomPrefs.isSettingsCardExpanded(appCtx, "data")) }
    var expandAppearance by remember { mutableStateOf(PhantomPrefs.isSettingsCardExpanded(appCtx, "appearance")) }
    var expandHub by remember { mutableStateOf(PhantomPrefs.isSettingsCardExpanded(appCtx, "hub")) }
    var expandShizuku by remember { mutableStateOf(PhantomPrefs.isSettingsCardExpanded(appCtx, "shizuku_advanced")) }
    var expandIntel by remember { mutableStateOf(PhantomPrefs.isSettingsCardExpanded(appCtx, "intel")) }
    var expandPersonalization by remember { mutableStateOf(PhantomPrefs.isSettingsCardExpanded(appCtx, "personalization")) }
    var expandStats by remember { mutableStateOf(PhantomPrefs.isSettingsCardExpanded(appCtx, "stats")) }
    var expandAbout by remember { mutableStateOf(PhantomPrefs.isSettingsCardExpanded(appCtx, "about")) }

    // 個人化設定
    var cardDensity by remember { mutableStateOf(PhantomPrefs.getCardDensity(appCtx)) }
    var homeSortOrder by remember { mutableStateOf(PhantomPrefs.getHomeSortOrder(appCtx)) }
    var visibleHomeFilters by remember { mutableStateOf(PhantomPrefs.getVisibleHomeFilters(appCtx)) }
    var summaryDefaultCollapsed by remember { mutableStateOf(PhantomPrefs.isSummaryDefaultCollapsed(appCtx)) }
    var homeCardLayoutMode by remember { mutableStateOf(PhantomPrefs.getHomeCardLayoutMode(appCtx)) }
    var noteCardLayoutMode by remember { mutableStateOf(PhantomPrefs.getNoteCardLayoutMode(appCtx)) }
    var use24HourFormat by remember { mutableStateOf(PhantomPrefs.is24HourFormat(appCtx)) }
    var visibleStatsCards by remember { mutableStateOf(PhantomPrefs.getVisibleStatsCards(appCtx)) }

    // 設定頁搜尋狀態（本地即時過濾 + AI 語意導覽）
    var searchQuery by remember { mutableStateOf("") }
    var isAiSearching by remember { mutableStateOf(false) }
    var aiMatchedIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var aiSearchNote by remember { mutableStateOf<String?>(null) }

    // 設定頁高亮導覽狀態（0 = 未顯示，1..8 = 對應步驟）
    var tourStepIndex by remember {
        mutableIntStateOf(if (PhantomPrefs.isSettingsTourCompleted(appCtx)) 0 else 1)
    }
    val cardBounds = remember { mutableStateMapOf<String, Rect>() }
    val listState = rememberLazyListState()
    val whitelistBringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(tourStepIndex) {
        if (tourStepIndex in 1..SettingsTourStep.entries.size) {
            if (searchQuery.isNotEmpty() || aiMatchedIds.isNotEmpty()) {
                searchQuery = ""
                aiMatchedIds = emptyList()
                aiSearchNote = null
            }
            val step = SettingsTourStep.fromIndex(tourStepIndex)
            when (step) {
                SettingsTourStep.QUICK -> {
                    expandQuick = true
                    PhantomPrefs.setSettingsCardExpanded(appCtx, "quick", true)
                }
                SettingsTourStep.HUB -> {
                    expandHub = true
                    PhantomPrefs.setSettingsCardExpanded(appCtx, "hub", true)
                }
                SettingsTourStep.PERSONALIZATION -> {
                    expandPersonalization = true
                    PhantomPrefs.setSettingsCardExpanded(appCtx, "personalization", true)
                }
                SettingsTourStep.AI -> {
                    expandAi = true
                    PhantomPrefs.setSettingsCardExpanded(appCtx, "ai", true)
                }
                SettingsTourStep.NOTIF -> {
                    expandNotif = true
                    PhantomPrefs.setSettingsCardExpanded(appCtx, "notif", true)
                }
                SettingsTourStep.DATA,
                SettingsTourStep.WHITELIST -> {
                    expandData = true
                    PhantomPrefs.setSettingsCardExpanded(appCtx, "data", true)
                }
                SettingsTourStep.APPEARANCE -> {
                    expandAppearance = true
                    PhantomPrefs.setSettingsCardExpanded(appCtx, "appearance", true)
                }
                SettingsTourStep.SHIZUKU -> {
                    expandShizuku = true
                    PhantomPrefs.setSettingsCardExpanded(appCtx, "shizuku_advanced", true)
                }
            }
            val scrollIdx = SETTINGS_CARD_ORDER.indexOf(step.scrollCardKey)
            if (scrollIdx >= 0) {
                listState.animateScrollToItem(scrollIdx)
            }
            withFrameNanos { }
            if (step == SettingsTourStep.WHITELIST) {
                whitelistBringIntoViewRequester.bringIntoView()
                withFrameNanos { }
            }
        }
    }

    // 外觀與互動
    val currentThemeMode = ThemePreferences.getThemeMode(appCtx)
    val dynamicColorEnabled = ThemePreferences.isDynamicColor(appCtx)
    val fontScaleMode = ThemePreferences.getFontScaleMode(appCtx)
    var hapticEnabled by remember { mutableStateOf(PhantomPrefs.isHapticFeedbackEnabled(appCtx)) }
    val glassEffectEnabled = ThemePreferences.isGlassEffectEnabled(appCtx)
    var tabBarDisplayMode by remember { mutableStateOf(PhantomPrefs.getTabBarDisplayMode(appCtx)) }

    // 保持運作狀態
    var listenerEnabled by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(appCtx).contains(appCtx.packageName))
    }
    var batteryWhitelisted by remember { mutableStateOf(isIgnoringBatteryOptimizations(appCtx)) }
    var oemAutostartConfirmed by remember { mutableStateOf(PhantomPrefs.isOemAutostartConfirmed(appCtx)) }
    var fgServiceRunning by remember { mutableStateOf(isStatsForegroundServiceRunning(appCtx)) }
    var whitelistCount by remember { mutableIntStateOf(0) }
    val oemGuide = remember { buildOemGuide(appCtx) }

    LaunchedEffect(Unit) {
        whitelistCount = withContext(Dispatchers.IO) {
            AppDatabaseHolder.db(appCtx).aiRuleDao().all()
                .count { it.kind == com.phantom.checker.data.AiRuleEntity.KIND_WHITELIST }
        }
    }

    LaunchedEffect(settingsVersion) {
        if (settingsVersion > 0L) {
            mutedApps = PhantomPrefs.getMutedApps(appCtx).sorted()
            muteModes = PhantomPrefs.getAppMuteModeMap(appCtx)
            tokenSaverEnabled = PhantomPrefs.isTokenSaverEnabled(appCtx)
            persistentStats = PhantomPrefs.isPersistentStatsEnabled(appCtx)
            persistentShowIcons = PhantomPrefs.isPersistentNotifShowIcons(appCtx)
            persistentNotifStyle = PhantomPrefs.getPersistentNotifStyle(appCtx)
            swipeToDeleteEnabled = PhantomPrefs.isSwipeToDeleteEnabled(appCtx)
            scamAlertNotif = PhantomPrefs.isScamAlertNotifEnabled(appCtx)
            recordOngoing = PhantomPrefs.isRecordOngoingEnabled(appCtx)
            clickBehavior = PhantomPrefs.getNotifClickBehavior(appCtx)
            throttleSec = PhantomPrefs.getSameKeyThrottleSeconds(appCtx).toFloat()
            digitVarianceThrottle = PhantomPrefs.isSameKeyDigitVarianceThrottleEnabled(appCtx)
            appThrottleMap = PhantomPrefs.getAppSameKeyThrottleMap(appCtx)
            alertKeywords = PhantomPrefs.getAlertKeywords(appCtx)
            quietHoursEnabled = PhantomPrefs.isQuietHoursEnabled(appCtx)
            quietStartMinute = PhantomPrefs.getQuietHoursStartMinute(appCtx)
            quietEndMinute = PhantomPrefs.getQuietHoursEndMinute(appCtx)
            aiRiskEnabled = PhantomPrefs.isAiRiskEnabled(appCtx)
            aiAutoTagEnabled = PhantomPrefs.isAiAutoTagEnabled(appCtx)
            aiOfflineRetryEnabled = PhantomPrefs.isAiOfflineRetryEnabled(appCtx)
            aiAutoMemoEnabled = PhantomPrefs.isAiAutoMemoEnabled(appCtx)
            aiTagFailureNotifEnabled = PhantomPrefs.isAiTagFailureNotifEnabled(appCtx)
            dualModelRoutingEnabled = PhantomPrefs.isDualModelRoutingEnabled(appCtx)
            dualModelRoutingModel = PhantomPrefs.getDualModelRoutingModel(appCtx)
            chatSuggestionsEnabled = PhantomPrefs.isChatSuggestionsEnabled(appCtx)
            aiChatTabContinue = PhantomPrefs.isAiChatTabContinue(appCtx)
            quickCommandDefaultMode = PhantomPrefs.getQuickCommandDefaultMode(appCtx)
            quickChatCommands = PhantomPrefs.getQuickChatCommands(appCtx)
            dailyAiLimit = PhantomPrefs.getDailyAiLimit(appCtx)
            chatRagLimit = PhantomPrefs.getChatRagMessageLimit(appCtx)
            aiMinTriggerLength = PhantomPrefs.getAiMinTriggerLength(appCtx)
            todayAiUsage = PhantomPrefs.getTodayAiUsage(appCtx)

            memoryChatCaptureEnabled = PhantomPrefs.isMemoryChatCaptureEnabled(appCtx)
            memoryWriteConfirmEnabled = PhantomPrefs.isMemoryWriteConfirmEnabled(appCtx)
            antiFraudCrossCheckEnabled = PhantomPrefs.isAntiFraudCrossCheckEnabled(appCtx)
            contactsPermissionGranted = com.phantom.checker.util.ContactContext.hasContactsPermission(appCtx)
            calendarReadGranted = com.phantom.checker.util.CalendarBridge.hasReadPermission(appCtx)
            calendarWriteGranted = com.phantom.checker.util.CalendarBridge.hasWritePermission(appCtx)
            selectedCalendarIds = PhantomPrefs.getSelectedCalendarIds(appCtx)
            calendarWriteConfirmEnabled = PhantomPrefs.isCalendarWriteConfirmEnabled(appCtx)
            calendarListExpanded = PhantomPrefs.isCalendarListExpanded(appCtx)
            scheduleMemoRoute = PhantomPrefs.getScheduleMemoRoute(appCtx)
            memoryNormalTopK = PhantomPrefs.getMemoryNormalTopK(appCtx)
            memoryNormalPerItemChars = PhantomPrefs.getMemoryNormalPerItemChars(appCtx)
            memorySaverTopK = PhantomPrefs.getMemorySaverTopK(appCtx)
            assistantName = PhantomPrefs.getAssistantName(appCtx)
            assistantPersonalityPreset = PhantomPrefs.getAssistantPersonalityPreset(appCtx)
            assistantCustomPersonality = PhantomPrefs.getAssistantCustomPersonality(appCtx)
            assistantVerbosity = PhantomPrefs.getAssistantVerbosity(appCtx)
            assistantHasAvatar = PhantomPrefs.hasAssistantAvatar(appCtx)
            retentionDays = RetentionConfig.getRetentionDays(appCtx)
            retentionMaxCount = PhantomPrefs.getRetentionMaxCount(appCtx)
            cardDensity = PhantomPrefs.getCardDensity(appCtx)
            homeSortOrder = PhantomPrefs.getHomeSortOrder(appCtx)
            visibleHomeFilters = PhantomPrefs.getVisibleHomeFilters(appCtx)
            summaryDefaultCollapsed = PhantomPrefs.isSummaryDefaultCollapsed(appCtx)
            homeCardLayoutMode = PhantomPrefs.getHomeCardLayoutMode(appCtx)
            noteCardLayoutMode = PhantomPrefs.getNoteCardLayoutMode(appCtx)
            use24HourFormat = PhantomPrefs.is24HourFormat(appCtx)
            visibleStatsCards = PhantomPrefs.getVisibleStatsCards(appCtx)
            hapticEnabled = PhantomPrefs.isHapticFeedbackEnabled(appCtx)
            tabBarDisplayMode = PhantomPrefs.getTabBarDisplayMode(appCtx)
        }
    }

    val lifecycleOwner = ctx as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(appCtx)
                    .contains(appCtx.packageName)
                batteryWhitelisted = isIgnoringBatteryOptimizations(appCtx)
                oemAutostartConfirmed = PhantomPrefs.isOemAutostartConfirmed(appCtx)
                fgServiceRunning = isStatsForegroundServiceRunning(appCtx)
                scope.launch {
                    whitelistCount = withContext(Dispatchers.IO) {
                        AppDatabaseHolder.db(appCtx).aiRuleDao().all()
                            .count { it.kind == com.phantom.checker.data.AiRuleEntity.KIND_WHITELIST }
                    }
                }
                mutedApps = PhantomPrefs.getMutedApps(appCtx).sorted()
                muteModes = PhantomPrefs.getAppMuteModeMap(appCtx)
                tokenSaverEnabled = PhantomPrefs.isTokenSaverEnabled(appCtx)
                persistentStats = PhantomPrefs.isPersistentStatsEnabled(appCtx)
                persistentShowIcons = PhantomPrefs.isPersistentNotifShowIcons(appCtx)
                swipeToDeleteEnabled = PhantomPrefs.isSwipeToDeleteEnabled(appCtx)
                scamAlertNotif = PhantomPrefs.isScamAlertNotifEnabled(appCtx)
                recordOngoing = PhantomPrefs.isRecordOngoingEnabled(appCtx)
                digitVarianceThrottle = PhantomPrefs.isSameKeyDigitVarianceThrottleEnabled(appCtx)
                quietHoursEnabled = PhantomPrefs.isQuietHoursEnabled(appCtx)
                aiRiskEnabled = PhantomPrefs.isAiRiskEnabled(appCtx)
                aiAutoTagEnabled = PhantomPrefs.isAiAutoTagEnabled(appCtx)
                chatSuggestionsEnabled = PhantomPrefs.isChatSuggestionsEnabled(appCtx)
                aiChatTabContinue = PhantomPrefs.isAiChatTabContinue(appCtx)
                quickCommandDefaultMode = PhantomPrefs.getQuickCommandDefaultMode(appCtx)
                quickChatCommands = PhantomPrefs.getQuickChatCommands(appCtx)
                chatRagLimit = PhantomPrefs.getChatRagMessageLimit(appCtx)

                memoryChatCaptureEnabled = PhantomPrefs.isMemoryChatCaptureEnabled(appCtx)
                memoryWriteConfirmEnabled = PhantomPrefs.isMemoryWriteConfirmEnabled(appCtx)
                calendarWriteConfirmEnabled = PhantomPrefs.isCalendarWriteConfirmEnabled(appCtx)
                calendarListExpanded = PhantomPrefs.isCalendarListExpanded(appCtx)
                selectedCalendarIds = PhantomPrefs.getSelectedCalendarIds(appCtx)
                antiFraudCrossCheckEnabled = PhantomPrefs.isAntiFraudCrossCheckEnabled(appCtx)
                memoryNormalTopK = PhantomPrefs.getMemoryNormalTopK(appCtx)
                memoryNormalPerItemChars = PhantomPrefs.getMemoryNormalPerItemChars(appCtx)
                memorySaverTopK = PhantomPrefs.getMemorySaverTopK(appCtx)
                assistantName = PhantomPrefs.getAssistantName(appCtx)
                assistantPersonalityPreset = PhantomPrefs.getAssistantPersonalityPreset(appCtx)
                assistantCustomPersonality = PhantomPrefs.getAssistantCustomPersonality(appCtx)
                assistantVerbosity = PhantomPrefs.getAssistantVerbosity(appCtx)
                assistantHasAvatar = PhantomPrefs.hasAssistantAvatar(appCtx)
                summaryDefaultCollapsed = PhantomPrefs.isSummaryDefaultCollapsed(appCtx)
                homeCardLayoutMode = PhantomPrefs.getHomeCardLayoutMode(appCtx)
                noteCardLayoutMode = PhantomPrefs.getNoteCardLayoutMode(appCtx)
                visibleStatsCards = PhantomPrefs.getVisibleStatsCards(appCtx)
                hapticEnabled = PhantomPrefs.isHapticFeedbackEnabled(appCtx)
                ShizukuManager.refreshState(appCtx)
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        isLoadingRules = true
        withContext(Dispatchers.IO) {
            val db = AppDatabaseHolder.db(appCtx)
            rules = db.filterDao().all()
            appGroups = db.messageDao().groupByApp()
        }
        isLoadingRules = false
    }

    if (showAppPickerDialog) {
        AppMutePickerDialog(
            currentlyMuted = mutedApps.toSet(),
            onDismiss = { showAppPickerDialog = false },
            onSelectApp = { pkg, mode ->
                PhantomPrefs.setAppMuted(appCtx, pkg, true, mode)
                mutedApps = PhantomPrefs.getMutedApps(appCtx).sorted()
                muteModes = PhantomPrefs.getAppMuteModeMap(appCtx)
                showAppPickerDialog = false
                scope.launch { snackbarHostState.showSnackbar("已將 $pkg 設為靜音（${mode.label}）") }
            }
        )
    }

    if (showAppThrottlePicker) {
        AppThrottlePickerDialog(
            currentOverrides = appThrottleMap,
            onDismiss = { showAppThrottlePicker = false },
            onSelectApp = { pkg, seconds ->
                PhantomPrefs.setAppSameKeyThrottle(appCtx, pkg, seconds)
                appThrottleMap = PhantomPrefs.getAppSameKeyThrottleMap(appCtx)
                showAppThrottlePicker = false
                val msg = if (seconds == 0) "已設定 $pkg 永不節流（0 秒）" else "已設定 $pkg 同 key 節流 $seconds 秒"
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        )
    }

    if (showExportDialog) {
        ExportCsvDialog(
            appGroups = appGroups,
            onDismiss = { showExportDialog = false },
            onConfirm = { pkg, daysBack ->
                showExportDialog = false
                if (isExporting) return@ExportCsvDialog
                scope.launch {
                    isExporting = true
                    val result = MessageExporter.exportToDownloads(appCtx, pkg, daysBack)
                    isExporting = false
                    result.onSuccess { fileName ->
                        snackbarHostState.showSnackbar("匯出成功：$fileName")
                    }.onFailure { err ->
                        val reason = err.localizedMessage?.takeIf { it.isNotBlank() } ?: err.javaClass.simpleName
                        snackbarHostState.showSnackbar("匯出失敗：$reason")
                    }
                }
            }
        )
    }

    if (showClearReadConfirm) {
        AlertDialog(
            onDismissRequest = { showClearReadConfirm = false },
            title = { Text("清除已讀訊息") },
            text = { Text("確定要永久刪除所有已讀訊息嗎？未讀訊息將保留。") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                db.withTransaction {
                                    db.tagDao().deleteCrossRefsForReadMessages()
                                    db.messageDao().deleteReadMessages()
                                }
                                DbMaintenance.enqueueVacuum(appCtx)
                                StatsForegroundService.startOrRefresh(appCtx)
                            }
                            showClearReadConfirm = false
                            snackbarHostState.showSnackbar("已清除所有已讀訊息")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確認清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearReadConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("刪除全部訊息", color = MaterialTheme.colorScheme.error) },
            text = { Text("確定要永久刪除所有攔截到的訊息與標籤關聯嗎？此操作無法復原。") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                db.withTransaction {
                                    db.tagDao().deleteAllCrossRefs()
                                    db.messageDao().deleteAll()
                                }
                                DbMaintenance.enqueueVacuum(appCtx)
                                StatsForegroundService.startOrRefresh(appCtx)
                            }
                            showDeleteAllConfirm = false
                            snackbarHostState.showSnackbar("已刪除全部訊息")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確認全部刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showPresetDialog) {
        val existingGlobal = rules
            .filter { it.packageName == null }
            .map { it.keyword.lowercase() }
            .toSet()
        val newCount = PresetKeywords.keywords.count { it.lowercase() !in existingGlobal }
        val skipCount = PresetKeywords.keywords.size - newCount

        AlertDialog(
            onDismissRequest = { if (!isImportingPresets) showPresetDialog = false },
            title = { Text("匯入預設詐騙關鍵字") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "共 ${PresetKeywords.keywords.size} 個常見詐騙關鍵字（將新增 $newCount 個，略過 $skipCount 個已存在項目）：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = PresetKeywords.keywords.joinToString("、"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isImportingPresets) return@Button
                        scope.launch {
                            isImportingPresets = true
                            val (added, skipped, updatedRules) = withContext(Dispatchers.IO) {
                                val dao = AppDatabaseHolder.db(appCtx).filterDao()
                                val currentRules = dao.all()
                                val existingSet = currentRules
                                    .filter { it.packageName == null }
                                    .map { it.keyword.lowercase() }
                                    .toSet()
                                var addedCount = 0
                                var skippedCount = 0
                                for (kw in PresetKeywords.keywords) {
                                    if (kw.lowercase() in existingSet) {
                                        skippedCount++
                                    } else {
                                        dao.insert(
                                            FilterRule(
                                                keyword = kw,
                                                isActive = true,
                                                packageName = null,
                                                action = FilterRule.ACTION_FILTER
                                            )
                                        )
                                        addedCount++
                                    }
                                }
                                Triple(addedCount, skippedCount, dao.all())
                            }
                            rules = updatedRules
                            isImportingPresets = false
                            showPresetDialog = false
                            val msg = if (added > 0) {
                                "已匯入 $added 個預設關鍵字（略過 $skipped 個重複）"
                            } else {
                                "所有預設關鍵字皆已存在，未新增項目"
                            }
                            snackbarHostState.showSnackbar(msg)
                        }
                    },
                    enabled = !isImportingPresets
                ) {
                    Text(if (isImportingPresets) "匯入中…" else "確認匯入")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPresetDialog = false },
                    enabled = !isImportingPresets
                ) {
                    Text("取消")
                }
            }
        )
    }

    pendingAvatarCropUri?.let { cropUri ->
        AssistantAvatarCropDialog(
            uri = cropUri,
            onDismiss = { pendingAvatarCropUri = null },
            onLoadError = { errMsg ->
                scope.launch { snackbarHostState.showSnackbar("頭像圖片載入失敗：$errMsg") }
            },
            onConfirmCrop = { zoomScale, offsetXNorm, offsetYNorm ->
                pendingAvatarCropUri = null
                if (isSavingAvatar) return@AssistantAvatarCropDialog
                scope.launch {
                    isSavingAvatar = true
                    val res = AssistantAvatarHelper.saveAvatarFromUri(
                        ctx = appCtx,
                        uri = cropUri,
                        zoomScale = zoomScale,
                        offsetXNorm = offsetXNorm,
                        offsetYNorm = offsetYNorm
                    )
                    isSavingAvatar = false
                    res.onSuccess {
                        assistantHasAvatar = true
                        assistantAvatarBitmap = AssistantAvatarHelper.loadAvatarBitmap(appCtx)
                        snackbarHostState.showSnackbar("已更新 AI 助手圓形頭像")
                    }.onFailure { err ->
                        snackbarHostState.showSnackbar("頭像儲存失敗：${err.message ?: "未知錯誤"}")
                    }
                }
            }
        )
    }

    val glassState = remember { HazeState() }
    var activeCategory by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
    var activeHighlightSettingId by remember { mutableStateOf<String?>(null) }
    var inCategorySearchOpen by remember { mutableStateOf(false) }
    var inCategorySearchQuery by remember { mutableStateOf("") }
    val advancedExpandedMap = remember { mutableStateMapOf<SettingsCategory, Boolean>() }

    LaunchedEffect(activeHighlightSettingId) {
        if (activeHighlightSettingId != null) {
            kotlinx.coroutines.delay(3000L)
            activeHighlightSettingId = null
        }
    }

    val allCardKeys = SETTINGS_CARD_ORDER
    val hasActiveQuery = remember(searchQuery) { searchQuery.isNotBlank() }
    val localMatchedItems = remember(searchQuery) {
        SettingsCatalog.searchByKeywords(searchQuery)
    }
    val isLocalFiltering = hasActiveQuery && aiMatchedIds.isEmpty()
    val aiMatchedCardKeys = remember(aiMatchedIds) {
        aiMatchedIds.mapNotNull { id ->
            SettingsCatalog.findById(id)?.cardKey
        }.toSet()
    }
    val highlightedCardKeys = remember(isLocalFiltering, localMatchedItems, aiMatchedCardKeys, activeHighlightSettingId) {
        val fromSetting = activeHighlightSettingId?.let { SettingsCatalog.findById(it)?.cardKey }
        when {
            fromSetting != null -> setOf(fromSetting)
            aiMatchedCardKeys.isNotEmpty() -> aiMatchedCardKeys
            isLocalFiltering -> localMatchedItems.map { it.cardKey }.toSet()
            else -> emptySet()
        }
    }

    fun navigateToSettingItem(item: SettingSearchItem) {
        UiLayoutPrefs.recordRecentSetting(appCtx, item.id)
        val targetActivityClass = when (item.page) {
            "AutoReplySettingsActivity" -> AutoReplySettingsActivity::class.java
            "ScamCenterActivity" -> ScamCenterActivity::class.java
            "TagManagementActivity" -> TagManagementActivity::class.java
            "WhitelistManagementActivity" -> WhitelistManagementActivity::class.java
            "ApiKeySettingsActivity" -> ApiKeySettingsActivity::class.java
            "PermissionHubActivity" -> PermissionHubActivity::class.java
            "AssistantHubActivity" -> AssistantHubActivity::class.java
            "LinkedAppsActivity" -> LinkedAppsActivity::class.java
            else -> null
        }
        if (targetActivityClass != null) {
            ctx.startActivity(Intent(ctx, targetActivityClass))
            return
        }
        val route = SettingsCategoryRegistry.routeFor(item.id)
        val targetCat = route?.category ?: SettingsCategoryRegistry.categoryForLegacyCardKey(item.cardKey)
        if (targetCat != null) {
            if (route?.isFoldedByDefault == true) {
                advancedExpandedMap[targetCat] = true
            }
            activeCategory = targetCat
            activeHighlightSettingId = item.id
            when (item.cardKey) {
                "quick" -> expandQuick = true
                "personalization" -> expandPersonalization = true
                "stats" -> expandStats = true
                "ai" -> expandAi = true
                "memory_assistant" -> expandMemoryAssistant = true
                "notif" -> expandNotif = true
                "data" -> expandData = true
                "appearance" -> expandAppearance = true
                "shizuku" -> expandShizuku = true
                "intel" -> expandIntel = true
                "about" -> expandAbout = true
            }
        }
    }

    LaunchedEffect(initialHighlightCardKey, initialHighlightSettingId) {
        if (!initialHighlightSettingId.isNullOrBlank()) {
            val item = SettingsCatalog.findById(initialHighlightSettingId)
            if (item != null) {
                navigateToSettingItem(item)
                return@LaunchedEffect
            }
        }
        val key = initialHighlightCardKey ?: return@LaunchedEffect
        val mappedCat = SettingsCategoryRegistry.categoryForLegacyCardKey(key)
        if (mappedCat != null) {
            if (key == "shizuku") advancedExpandedMap[mappedCat] = true
            activeCategory = mappedCat
        }
        when (key) {
            "quick" -> expandQuick = true
            "personalization" -> expandPersonalization = true
            "stats" -> expandStats = true
            "ai" -> expandAi = true
            "memory_assistant" -> expandMemoryAssistant = true
            "notif" -> expandNotif = true
            "data" -> expandData = true
            "appearance" -> expandAppearance = true
            "shizuku" -> expandShizuku = true
            "intel" -> expandIntel = true
            "about" -> expandAbout = true
        }
    }

    fun runAiSettingsSearch() {
        val q = searchQuery.trim()
        if (q.isEmpty() || isAiSearching) return
        scope.launch {
            isAiSearching = true
            val catalogText = SettingsCatalog.buildAiSearchCatalogText()
            val allowedIds = SettingsCatalog.ITEMS.map { it.id }.toSet()
            val result = AiClient.searchSettings(
                query = q,
                catalogText = catalogText,
                allowedIds = allowedIds,
                context = appCtx
            )
            isAiSearching = false
            result.onSuccess { ids ->
                val matchedItems = ids.mapNotNull { id -> SettingsCatalog.findById(id) }
                if (matchedItems.isEmpty()) {
                    aiMatchedIds = emptyList()
                    aiSearchNote = "查無相符的設定項目"
                    snackbarHostState.showSnackbar("查無相符的設定項目")
                    return@onSuccess
                }
                aiMatchedIds = matchedItems.map { it.id }
                aiSearchNote = "✨ AI 語意命中 ${matchedItems.size} 項設定（點選直達）"
            }.onFailure { err ->
                snackbarHostState.showSnackbar(err.message ?: "AI 搜尋失敗")
            }
        }
    }

    val shizukuReady = ShizukuManager.state.collectAsState().value.isReady
    val homeSummaries = remember(
        tokenSaverEnabled,
        aiRiskEnabled,
        dailyAiLimit,
        todayAiUsage,
        quietHoursEnabled,
        quietStartMinute,
        quietEndMinute,
        persistentStats,
        memoryChatCaptureEnabled,
        assistantName,
        homeCardLayoutMode,
        retentionDays,
        mutedApps,
        batteryWhitelisted,
        shizukuReady
    ) {
        val profileCount = AiClient.getProfiles(appCtx).size
        val activeModel = AiClient.getActiveProfile(appCtx)?.model?.takeIf { it.isNotBlank() } ?: "未設定"
        val quietSummary = if (quietHoursEnabled) {
            "勿擾 %02d:%02d–%02d:%02d".format(
                quietStartMinute / 60, quietStartMinute % 60,
                quietEndMinute / 60, quietEndMinute % 60
            )
        } else {
            "勿擾關閉 · 常駐通知${if (persistentStats) "開" else "關"}"
        }
        val displayAssistantName = assistantName.ifBlank { "幻訊" }
        val themeLabel = ThemePreferences.getThemeMode(appCtx).label
        val layoutLabel = if (homeCardLayoutMode == PhantomPrefs.HomeCardLayoutMode.CENTER) "中央堆疊" else "分組清單"
        val notifAccessGranted = androidx.core.app.NotificationManagerCompat
            .getEnabledListenerPackages(appCtx).contains(appCtx.packageName)
        val pendingPermCount = listOf(
            notifAccessGranted,
            batteryWhitelisted
        ).count { !it }
        val permSummary = buildString {
            append(if (pendingPermCount > 0) "$pendingPermCount 項待授權" else "核心權限已就緒")
            if (shizukuReady) append(" · Shizuku 已連線")
        }
        val versionName = runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        }.getOrDefault("")
        SettingsHomeSummaries(
            quickSummary = "省 token ${if (tokenSaverEnabled) "開" else "關"} · AI 風險分析${if (aiRiskEnabled) "開" else "關"}",
            aiSummary = "$profileCount 組 Profile · 對話：$activeModel · 今日 $todayAiUsage/$dailyAiLimit",
            notifSummary = quietSummary,
            assistantSummary = "助手：$displayAssistantName · 記憶擷取${if (memoryChatCaptureEnabled) "開" else "關"}",
            permissionsSummary = permSummary,
            permissionsBadge = if (pendingPermCount > 0) "$pendingPermCount 待處理" else null,
            appearanceSummary = "$themeLabel · 排列：$layoutLabel",
            dataSummary = "保留 $retentionDays 天 · 靜音 ${mutedApps.size} 個 App",
            aboutSummary = "v$versionName · 自動檢查更新${if (PhantomPrefs.isUpdateAutoCheckEnabled(appCtx)) "開" else "關"}",
            versionDisplay = "v$versionName"
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PhantomPredictiveBackContainer(
            enabled = activeCategory != null,
            onBack = {
                inCategorySearchOpen = false
                inCategorySearchQuery = ""
                activeCategory = null
            }
        ) {
            AnimatedContent(
                targetState = activeCategory,
                transitionSpec = {
                    if (targetState != null && initialState == null) {
                        PhantomMotion.PageEnter togetherWith PhantomMotion.PageExit
                    } else {
                        PhantomMotion.PagePopEnter togetherWith PhantomMotion.PagePopExit
                    }
                },
                label = "settingsCategoryNav"
            ) { currentCategory ->
                if (currentCategory == null) {
                    SettingsHomeScreen(
                        onBack = onBack,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { next ->
                            searchQuery = next
                            aiMatchedIds = emptyList()
                            aiSearchNote = null
                        },
                        isAiSearching = isAiSearching,
                        aiMatchedIds = aiMatchedIds,
                        aiSearchNote = aiSearchNote,
                        onRunAiSearch = { runAiSettingsSearch() },
                        onClearAiSearch = {
                            aiMatchedIds = emptyList()
                            aiSearchNote = null
                        },
                        summaries = homeSummaries,
                        snackbarHostState = snackbarHostState,
                        onOpenCategory = { cat ->
                            inCategorySearchOpen = false
                            inCategorySearchQuery = ""
                            activeCategory = cat
                        },
                        onSelectSettingItem = { item -> navigateToSettingItem(item) },
                        onReplayTour = { tourStepIndex = 1 },
                        onReportTourBounds = { key, rect ->
                            cardBounds[key] = rect
                        },
                        onShowMessage = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    )
                } else {
                    LaunchedEffect(currentCategory) {
                        when (currentCategory) {
                            SettingsCategory.QUICK -> expandQuick = true
                            SettingsCategory.AI -> expandAi = true
                            SettingsCategory.NOTIF -> expandNotif = true
                            SettingsCategory.ASSISTANT -> {
                                expandHub = true
                                expandMemoryAssistant = true
                                expandIntel = true
                            }
                            SettingsCategory.PERMISSIONS -> expandHub = true
                            SettingsCategory.APPEARANCE -> {
                                expandPersonalization = true
                                expandAppearance = true
                                expandStats = true
                            }
                            SettingsCategory.DATA -> expandData = true
                            SettingsCategory.ABOUT -> expandAbout = true
                        }
                    }
                    val categoryCardKeys = remember(currentCategory) {
                        when (currentCategory) {
                            SettingsCategory.QUICK -> setOf("quick")
                            SettingsCategory.AI -> setOf("ai")
                            SettingsCategory.NOTIF -> setOf("notif")
                            SettingsCategory.ASSISTANT -> setOf("hub", "memory_assistant", "intel")
                            SettingsCategory.PERMISSIONS -> setOf("permissions_links")
                            SettingsCategory.APPEARANCE -> setOf("personalization", "appearance", "stats")
                            SettingsCategory.DATA -> setOf("data")
                            SettingsCategory.ABOUT -> setOf("about")
                        }
                    }
                    val visibleCardKeys = categoryCardKeys
                    val horizPad = auroraHorizontalPadding()
                    val isAdvancedExpanded = advancedExpandedMap[currentCategory] == true
                    val advancedItemsInCat = remember(currentCategory) {
                        SettingsCategoryRegistry.advancedItemsInCategory(currentCategory)
                    }
                    val inCatMatches = remember(currentCategory, inCategorySearchQuery) {
                        val q = inCategorySearchQuery.trim()
                        if (q.isEmpty()) emptyList()
                        else SettingsCatalog.searchByKeywords(q).filter {
                            SettingsCategoryRegistry.categoryFor(it.id) == currentCategory
                        }
                    }

                    PhantomPageScaffold(
                        title = currentCategory.title,
                        onBack = {
                            inCategorySearchOpen = false
                            inCategorySearchQuery = ""
                            activeCategory = null
                        },
                        topBarStyle = PhantomTopBarStyle.Medium,
                        actions = {
                            IconButton(
                                onClick = {
                                    inCategorySearchOpen = !inCategorySearchOpen
                                    if (!inCategorySearchOpen) inCategorySearchQuery = ""
                                }
                            ) {
                                Icon(
                                    imageVector = if (inCategorySearchOpen) Icons.Default.Clear else Icons.Default.Search,
                                    contentDescription = if (inCategorySearchOpen) "關閉頁內搜尋" else "搜尋本分類設定"
                                )
                            }
                        },
                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                    ) { padding ->
                        AuroraCenteredContent(
                            modifier = Modifier
                                .fillMaxSize()
                                .phantomGlassSource(glassState)
                                .padding(padding)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = horizPad, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AnimatedVisibility(
                                    visible = inCategorySearchOpen,
                                    enter = PhantomMotion.ExpandEnter,
                                    exit = PhantomMotion.ExpandExit
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = inCategorySearchQuery,
                                            onValueChange = { inCategorySearchQuery = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            shape = RoundedCornerShape(24.dp),
                                            placeholder = { Text("在「${currentCategory.title}」內搜尋…") },
                                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                            trailingIcon = {
                                                if (inCategorySearchQuery.isNotEmpty()) {
                                                    IconButton(onClick = { inCategorySearchQuery = "" }) {
                                                        Icon(Icons.Default.Clear, contentDescription = "清除")
                                                    }
                                                }
                                            }
                                        )
                                        if (inCategorySearchQuery.isNotBlank()) {
                                            if (inCatMatches.isEmpty()) {
                                                Text(
                                                    text = "本分類中查無符合「${inCategorySearchQuery.trim()}」的設定",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                )
                                            } else {
                                                inCatMatches.take(5).forEach { matched ->
                                                    Surface(
                                                        onClick = {
                                                            if (SettingsCategoryRegistry.isFoldedByDefault(matched.id)) {
                                                                advancedExpandedMap[currentCategory] = true
                                                            }
                                                            activeHighlightSettingId = matched.id
                                                            inCategorySearchOpen = false
                                                            inCategorySearchQuery = ""
                                                        },
                                                        shape = RoundedCornerShape(AuroraTokens.CornerChip),
                                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = matched.title,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.Medium,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            if (SettingsCategoryRegistry.isFoldedByDefault(matched.id)) {
                                                                Text(
                                                                    text = "進階",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.primary
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // 1. 快速設定（置頂）
                                    if ("quick" in visibleCardKeys) {
                        item(key = "card_quick") {
                            CollapsibleSettingsCard(
                                title = "快速設定",
                                expanded = expandQuick || ("quick" in highlightedCardKeys),
                                highlighted = "quick" in highlightedCardKeys,
                                onToggle = {
                                    expandQuick = !expandQuick
                                    PhantomPrefs.setSettingsCardExpanded(appCtx, "quick", expandQuick)
                                },
                                glassState = glassState,
                                modifier = Modifier.onGloballyPositioned { coords ->
                                    cardBounds["quick"] = coords.boundsInRoot()
                                }
                            ) {
                    SettingSwitchRow(
                        title = "省 token 模式",
                        subtitle = "暫停每則 AI 掃描並降載對話注入，僅命中可疑特徵才分析；關閉即恢復原設定",
                        checked = tokenSaverEnabled,
                        onCheckedChange = { checked ->
                            tokenSaverEnabled = checked
                            PhantomPrefs.setTokenSaverEnabled(appCtx, checked)
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SettingSwitchRow(
                        title = "常駐統計通知",
                        subtitle = "以獨立前景服務維持通知欄即時統計與 App 圖示列",
                        checked = persistentStats,
                        onCheckedChange = { checked ->
                            persistentStats = checked
                            PhantomPrefs.setPersistentStatsEnabled(appCtx, checked)
                            if (checked) {
                                HeartbeatPolicy.schedule(appCtx)
                                StatsForegroundService.startOrRefresh(appCtx)
                            } else {
                                StatsForegroundService.stop(appCtx)
                            }
                            fgServiceRunning = isStatsForegroundServiceRunning(appCtx)
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SettingSwitchRow(
                        title = "AI 風險分析",
                        subtitle = if (tokenSaverEnabled) {
                            "內文達 $aiMinTriggerLength 字元且未命中過濾規則時自動評估風險（可於 AI 與分析調整）；省 token 模式生效中：僅命中可疑特徵才分析"
                        } else {
                            "內文達 $aiMinTriggerLength 字元且未命中過濾規則時自動評估風險（觸發字數門檻可於 AI 與分析調整）"
                        },
                        checked = aiRiskEnabled,
                        onCheckedChange = { checked ->
                            aiRiskEnabled = checked
                            PhantomPrefs.setAiRiskEnabled(appCtx, checked)
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SettingSwitchRow(
                        title = "每則訊息 AI 智慧掃描",
                        subtitle = if (tokenSaverEnabled) {
                            "AI 分析時自動指派「必看／行銷／垃圾通知」等標籤；省 token 模式生效中：已暫停"
                        } else {
                            "AI 分析時自動指派「必看／行銷／垃圾通知」等標籤"
                        },
                        checked = aiAutoTagEnabled,
                        onCheckedChange = { checked ->
                            aiAutoTagEnabled = checked
                            PhantomPrefs.setAiAutoTagEnabled(appCtx, checked)
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SettingSwitchRow(
                        title = "詐騙警示彈出通知",
                        subtitle = "偵測到 scam 高風險訊息時立即推送通知",
                        checked = scamAlertNotif,
                        onCheckedChange = { checked ->
                            scamAlertNotif = checked
                            PhantomPrefs.setScamAlertNotifEnabled(appCtx, checked)
                        }
                    )
                }
            }
            }

            // 1.2 #354 權限與 AI 夥伴整併樞紐卡（可展開收合，收合顯示權限數＋簡報摘要）
            if ("hub" in visibleCardKeys) {
                item(key = "card_hub") {
                    val notifPostOk = com.phantom.checker.service.ProactiveEngine.areNotificationsEnabled(appCtx)
                    val smsOk = androidx.core.content.ContextCompat.checkSelfPermission(
                        appCtx, android.Manifest.permission.RECEIVE_SMS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    val exactAlarmOk = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        (appCtx.getSystemService(android.app.AlarmManager::class.java)?.canScheduleExactAlarms() == true)
                    } else true
                    val grantedCount = listOf(
                        listenerEnabled,
                        notifPostOk,
                        smsOk,
                        contactsPermissionGranted,
                        calendarReadGranted,
                        exactAlarmOk,
                        batteryWhitelisted
                    ).count { it }
                    val morningOn = PhantomPrefs.isMorningBriefingEnabled(appCtx)
                    val eveningOn = PhantomPrefs.isEveningBriefingEnabled(appCtx)
                    val briefingStatus = when {
                        morningOn && eveningOn -> "早 ${PhantomPrefs.getMorningBriefingTime(appCtx)}／晚 ${PhantomPrefs.getEveningBriefingTime(appCtx)}"
                        morningOn -> "早間 ${PhantomPrefs.getMorningBriefingTime(appCtx)}"
                        eveningOn -> "晚間 ${PhantomPrefs.getEveningBriefingTime(appCtx)}"
                        else -> "已關閉"
                    }
                    CollapsibleSettingsCard(
                        title = "權限與 AI 夥伴",
                        collapsedSummary = "權限已授予 $grantedCount/7 項 · 早晚簡報：$briefingStatus",
                        expanded = expandHub || ("hub" in highlightedCardKeys),
                        highlighted = "hub" in highlightedCardKeys,
                        onToggle = {
                            expandHub = !expandHub
                            PhantomPrefs.setSettingsCardExpanded(appCtx, "hub", expandHub)
                        },
                        glassState = glassState,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            cardBounds["hub"] = coords.boundsInRoot()
                        }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "集中管理系統權限（通知存取、簡訊、通訊錄、日曆、精確鬧鐘、電池優化、使用情況）、早晚簡報、主動推播、畫像卡與外部連結應用。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!notifPostOk) {
                                Text(
                                    text = "⚠️ 主動通知未開啟，簡報將不會出現在系統通知欄。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                OutlinedButton(
                                    onClick = { com.phantom.checker.service.ProactiveEngine.openAppNotificationSettings(ctx) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("前往系統通知設定")
                                }
                            }
                            Button(
                                onClick = { ctx.startActivity(Intent(ctx, PermissionHubActivity::class.java)) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🔐 權限授予中心（已授予 $grantedCount/7 項）")
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { ctx.startActivity(Intent(ctx, AssistantHubActivity::class.java)) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("AI 夥伴與簡報", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                OutlinedButton(
                                    onClick = { ctx.startActivity(Intent(ctx, LinkedAppsActivity::class.java)) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("連結應用中心", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            // 1.5 個人化
            if ("personalization" in visibleCardKeys) {
                item(key = "card_personalization") {
                    CollapsibleSettingsCard(
                        title = "個人化",
                        expanded = expandPersonalization || ("personalization" in highlightedCardKeys),
                        highlighted = "personalization" in highlightedCardKeys,
                        onToggle = {
                            expandPersonalization = !expandPersonalization
                            PhantomPrefs.setSettingsCardExpanded(appCtx, "personalization", expandPersonalization)
                        },
                        glassState = glassState,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            cardBounds["personalization"] = coords.boundsInRoot()
                        }
                    ) {
                        Text("卡片密度", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.phantom.checker.util.CardDensity.entries.forEach { density ->
                                FilterChip(
                                    selected = cardDensity == density,
                                    onClick = {
                                        cardDensity = density
                                        PhantomPrefs.setCardDensity(appCtx, density)
                                    },
                                    label = { Text(density.label) }
                                )
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 8.dp))

                        Text("首頁 App 排序", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.phantom.checker.util.HomeSortOrder.entries.forEach { order ->
                                FilterChip(
                                    selected = homeSortOrder == order,
                                    onClick = {
                                        homeSortOrder = order
                                        PhantomPrefs.setHomeSortOrder(appCtx, order)
                                    },
                                    label = { Text(order.label) }
                                )
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 8.dp))

                        Text("首頁篩選欄顯示項目", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            text = "「全部」與「未讀」固定顯示，可自訂其餘按鈕",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        val filterOptions = listOf(
                            "starred" to "星標",
                            "scams" to "詐騙",
                            "filtered" to "已過濾",
                            "noted" to "已記事",
                            "todoed" to "已待辦",
                            "tag" to "標籤"
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            filterOptions.forEach { (key, label) ->
                                val selected = key in visibleHomeFilters
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        val next = if (selected) visibleHomeFilters - key else visibleHomeFilters + key
                                        visibleHomeFilters = next
                                        PhantomPrefs.setVisibleHomeFilters(appCtx, next)
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 8.dp))

                        SettingSwitchRow(
                            title = "摘要卡預設收合",
                            subtitle = "統計頁「每日／本週 AI 摘要」預設收合為單行預覽",
                            checked = summaryDefaultCollapsed,
                            onCheckedChange = { checked ->
                                summaryDefaultCollapsed = checked
                                PhantomPrefs.setSummaryDefaultCollapsed(appCtx, checked)
                            }
                        )

                        HorizontalDivider(Modifier.padding(vertical = 8.dp))

                        Text("首頁卡片排列模式", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            text = "一般條列：垂直獨立卡片列表；中央堆疊：焦點卡片在畫面正中央、上下對稱縮小堆疊的垂直滾輪",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PhantomPrefs.HomeCardLayoutMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = homeCardLayoutMode == mode,
                                    onClick = {
                                        homeCardLayoutMode = mode
                                        PhantomPrefs.setHomeCardLayoutMode(appCtx, mode)
                                        PhantomSettingsBus.notifyChanged()
                                    },
                                    label = { Text(mode.label) }
                                )
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 8.dp))

                        Text("記事卡片排列模式", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            text = "獨立設定記事分頁的卡片排列（不影響首頁）：一般條列或中央堆疊滾輪",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PhantomPrefs.NoteCardLayoutMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = noteCardLayoutMode == mode,
                                    onClick = {
                                        noteCardLayoutMode = mode
                                        PhantomPrefs.setNoteCardLayoutMode(appCtx, mode)
                                        PhantomSettingsBus.notifyChanged()
                                    },
                                    label = { Text(mode.label) }
                                )
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 8.dp))

                        Text("時間顯示格式", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = use24HourFormat,
                                onClick = {
                                    use24HourFormat = true
                                    PhantomPrefs.set24HourFormat(appCtx, true)
                                },
                                label = { Text("24 小時制（14:30）") }
                            )
                            FilterChip(
                                selected = !use24HourFormat,
                                onClick = {
                                    use24HourFormat = false
                                    PhantomPrefs.set24HourFormat(appCtx, false)
                                },
                                label = { Text("12 小時制（下午 02:30）") }
                            )
                        }

                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text("背景圖片", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "未設定專屬背景時會自動沿用全域背景，選圖後將降採樣並儲存至本機私有空間。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))

                        var pickingLayerName by rememberSaveable { mutableStateOf<String?>(null) }
                        var bgUiVersion by remember { mutableIntStateOf(ThemePreferences.getBackgroundVersion()) }
                        var bgStatusMsg by rememberSaveable { mutableStateOf<String?>(null) }
                        var bgPickerFailed by rememberSaveable { mutableStateOf(false) }
                        var failedLayerName by rememberSaveable { mutableStateOf<String?>(null) }

                        fun handlePickedBackgroundUri(uri: Uri?, fromFallback: Boolean) {
                            val targetLayer = pickingLayerName?.let { name ->
                                BackgroundLayer.entries.firstOrNull { it.name == name }
                            }
                            pickingLayerName = null
                            if (uri == null) return
                            if (targetLayer == null) {
                                bgStatusMsg = "選擇圖片時圖層狀態已重置，請再點擊一次欲設定的背景圖層"
                                bgPickerFailed = false
                                return
                            }
                            scope.launch {
                                val res = BackgroundImageHelper.saveBackgroundFromUri(appCtx, targetLayer, uri)
                                res.onSuccess {
                                    ThemePreferences.bumpBackgroundVersion()
                                    bgUiVersion = ThemePreferences.getBackgroundVersion()
                                    bgStatusMsg = "已設定「${targetLayer.label}」"
                                    bgPickerFailed = false
                                    failedLayerName = null
                                }.onFailure { err ->
                                    bgStatusMsg = err.message ?: "圖片處理失敗"
                                    if (!fromFallback) {
                                        bgPickerFailed = true
                                        failedLayerName = targetLayer.name
                                    }
                                }
                            }
                        }

                        val bgPickerLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.PickVisualMedia()
                        ) { uri ->
                            handlePickedBackgroundUri(uri, fromFallback = false)
                        }
                        val bgDocPickerLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.OpenDocument()
                        ) { uri ->
                            handlePickedBackgroundUri(uri, fromFallback = true)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BackgroundLayer.entries.forEach { layer ->
                                val hasBg = remember(bgUiVersion, layer) {
                                    PhantomPrefs.hasBackgroundImage(appCtx, layer) &&
                                        BackgroundImageHelper.backgroundFile(appCtx, layer).exists()
                                }
                                var thumb by remember(bgUiVersion, layer, hasBg) {
                                    mutableStateOf<ImageBitmap?>(null)
                                }
                                LaunchedEffect(bgUiVersion, layer, hasBg) {
                                    thumb = if (hasBg) {
                                        BackgroundImageHelper.loadPreviewThumbnail(
                                            BackgroundImageHelper.backgroundFile(appCtx, layer)
                                        )
                                    } else {
                                        null
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (thumb != null) {
                                        Image(
                                            bitmap = thumb!!,
                                            contentDescription = layer.label,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = layer.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = if (hasBg) "已自訂" else if (layer == BackgroundLayer.GLOBAL) "預設純色" else "沿用全域背景",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            pickingLayerName = layer.name
                                            bgPickerFailed = false
                                            failedLayerName = null
                                            try {
                                                bgPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            } catch (e: Exception) {
                                                bgStatusMsg = "無法開啟系統相簿選擇器（${e.localizedMessage ?: "不支援"}），請改用檔案選擇器"
                                                bgPickerFailed = true
                                                failedLayerName = layer.name
                                            }
                                        }
                                    ) {
                                        Text("選擇圖片")
                                    }
                                    if (hasBg) {
                                        TextButton(
                                            onClick = {
                                                BackgroundImageHelper.removeBackground(appCtx, layer)
                                                ThemePreferences.bumpBackgroundVersion()
                                                bgUiVersion = ThemePreferences.getBackgroundVersion()
                                                bgStatusMsg = "已移除「${layer.label}」"
                                                bgPickerFailed = false
                                            }
                                        ) {
                                            Text("移除")
                                        }
                                    }
                                }
                            }

                            // #392 夥伴（助手）頁頭部橫幅背景
                            val hasPartnerHeaderBg = remember(bgUiVersion) {
                                BackgroundImageHelper.resolveAssistantHeaderBackgroundFile(appCtx) != null
                            }
                            var partnerHeaderThumb by remember(bgUiVersion, hasPartnerHeaderBg) {
                                mutableStateOf<ImageBitmap?>(null)
                            }
                            LaunchedEffect(bgUiVersion, hasPartnerHeaderBg) {
                                partnerHeaderThumb = if (hasPartnerHeaderBg) {
                                    BackgroundImageHelper.loadPreviewThumbnail(
                                        BackgroundImageHelper.assistantHeaderBackgroundFile(appCtx)
                                    )
                                } else {
                                    null
                                }
                            }
                            val partnerHeaderPicker = rememberLauncherForActivityResult(
                                ActivityResultContracts.PickVisualMedia()
                            ) { uri ->
                                if (uri == null) return@rememberLauncherForActivityResult
                                scope.launch {
                                    val res = BackgroundImageHelper.saveAssistantHeaderBackgroundFromUri(appCtx, uri)
                                    res.fold(
                                        onSuccess = {
                                            ThemePreferences.bumpBackgroundVersion()
                                            PhantomSettingsBus.notifyChanged()
                                            bgUiVersion = ThemePreferences.getBackgroundVersion()
                                            bgStatusMsg = "已更新「夥伴頁頭部背景」"
                                            bgPickerFailed = false
                                        },
                                        onFailure = { err ->
                                            bgStatusMsg = "設定夥伴頁頭部背景失敗：${err.localizedMessage ?: "未知錯誤"}"
                                            bgPickerFailed = true
                                        }
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (partnerHeaderThumb != null) {
                                    Image(
                                        bitmap = partnerHeaderThumb!!,
                                        contentDescription = "夥伴頁頭部背景",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "夥伴頁頭部背景",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (hasPartnerHeaderBg) "已自訂" else "預設主題底色",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            partnerHeaderPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        } catch (e: Exception) {
                                            bgStatusMsg = "無法開啟系統相簿選擇器（${e.localizedMessage ?: "不支援"}）"
                                            bgPickerFailed = true
                                        }
                                    }
                                ) {
                                    Text("選擇圖片")
                                }
                                if (hasPartnerHeaderBg) {
                                    TextButton(
                                        onClick = {
                                            BackgroundImageHelper.removeAssistantHeaderBackground(appCtx)
                                            ThemePreferences.bumpBackgroundVersion()
                                            PhantomSettingsBus.notifyChanged()
                                            bgUiVersion = ThemePreferences.getBackgroundVersion()
                                            bgStatusMsg = "已移除「夥伴頁頭部背景」"
                                            bgPickerFailed = false
                                        }
                                    ) {
                                        Text("移除")
                                    }
                                }
                            }
                        }

                        bgStatusMsg?.let { msg ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (bgPickerFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                        if (bgPickerFailed) {
                            val fallbackLayer = failedLayerName?.let { name ->
                                BackgroundLayer.entries.firstOrNull { it.name == name }
                            } ?: BackgroundLayer.GLOBAL
                            TextButton(
                                onClick = {
                                    pickingLayerName = fallbackLayer.name
                                    try {
                                        bgDocPickerLauncher.launch(arrayOf("image/*"))
                                    } catch (e: Exception) {
                                        bgStatusMsg = "無法開啟檔案選擇器：${e.localizedMessage ?: "未知錯誤"}"
                                    }
                                }
                            ) {
                                Text("改用檔案選擇器（${fallbackLayer.label}）")
                            }
                        }

                        val anyBgSet = remember(bgUiVersion) {
                            BackgroundImageHelper.hasAnyBackground(appCtx)
                        }
                        if (anyBgSet) {
                            Spacer(Modifier.height(12.dp))
                            var scrimPref by remember { mutableIntStateOf(ThemePreferences.getBackgroundScrimPercent(appCtx)) }
                            val isDarkNow = MaterialTheme.colorScheme.background.luminance() < 0.5f
                            val effectiveScrim = if (scrimPref < 0) (if (isDarkNow) 60 else 35) else scrimPref
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (scrimPref < 0) "背景暗色遮罩強度（預設 $effectiveScrim%）" else "背景暗色遮罩強度（$effectiveScrim%）",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                if (scrimPref >= 0) {
                                    TextButton(
                                        onClick = {
                                            scrimPref = -1
                                            ThemePreferences.setBackgroundScrimPercent(appCtx, -1)
                                        }
                                    ) {
                                        Text("重設預設")
                                    }
                                }
                            }
                            Slider(
                                value = effectiveScrim.toFloat(),
                                onValueChange = { v ->
                                    val pct = v.roundToInt().coerceIn(0, 100)
                                    scrimPref = pct
                                    ThemePreferences.setBackgroundScrimPercent(appCtx, pct)
                                },
                                valueRange = 0f..100f
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        var glassStrength by remember { mutableIntStateOf(ThemePreferences.getGlassStrengthPercent(appCtx)) }
                        Text(
                            text = if (glassStrength == 0) "玻璃卡片強度（預設）" else "玻璃卡片強度（+$glassStrength%）",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = glassStrength.toFloat(),
                            onValueChange = { v ->
                                val pct = v.roundToInt().coerceIn(0, 100)
                                glassStrength = pct
                                ThemePreferences.setGlassStrengthPercent(appCtx, pct)
                            },
                            valueRange = 0f..100f
                        )

                        // #393 頁面細節自訂（字體獨立縮放、聊天氣泡圓角／寬度、最愛 App 橫列顯示上限）
                        HorizontalDivider(Modifier.padding(vertical = 10.dp))
                        var chatFontScale by remember { mutableStateOf(PhantomPrefs.getChatFontScale(appCtx)) }
                        var msgFontScale by remember { mutableStateOf(PhantomPrefs.getMessagesFontScale(appCtx)) }
                        var bubbleCornerDp by remember { mutableIntStateOf(PhantomPrefs.getChatBubbleCornerDp(appCtx)) }
                        var bubbleWidthPct by remember { mutableIntStateOf(PhantomPrefs.getChatBubbleWidthPercent(appCtx)) }
                        var favMaxVisible by remember { mutableIntStateOf(PhantomPrefs.getFavoriteAppsMaxVisible(appCtx)) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "頁面細節自訂",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(
                                onClick = {
                                    chatFontScale = 1.0f
                                    msgFontScale = 1.0f
                                    bubbleCornerDp = 18
                                    bubbleWidthPct = 82
                                    favMaxVisible = 0
                                    PhantomPrefs.setChatFontScale(appCtx, 1.0f)
                                    PhantomPrefs.setMessagesFontScale(appCtx, 1.0f)
                                    PhantomPrefs.setChatBubbleCornerDp(appCtx, 18)
                                    PhantomPrefs.setChatBubbleWidthPercent(appCtx, 82)
                                    PhantomPrefs.setFavoriteAppsMaxVisible(appCtx, 0)
                                    PhantomSettingsBus.notifyChanged()
                                }
                            ) {
                                Text("恢復預設")
                            }
                        }
                        Text(
                            text = "AI 聊天室字體獨立縮放（${String.format(Locale.US, "%.2fx", chatFontScale)}）",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = chatFontScale,
                            onValueChange = { v ->
                                val stepped = ((v * 20f).roundToInt() / 20f).coerceIn(0.85f, 1.30f)
                                chatFontScale = stepped
                                PhantomPrefs.setChatFontScale(appCtx, stepped)
                                PhantomSettingsBus.notifyChanged()
                            },
                            valueRange = 0.85f..1.30f,
                            steps = 8
                        )
                        Text(
                            text = "訊息中心字體獨立縮放（${String.format(Locale.US, "%.2fx", msgFontScale)}）",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = msgFontScale,
                            onValueChange = { v ->
                                val stepped = ((v * 20f).roundToInt() / 20f).coerceIn(0.85f, 1.30f)
                                msgFontScale = stepped
                                PhantomPrefs.setMessagesFontScale(appCtx, stepped)
                                PhantomSettingsBus.notifyChanged()
                            },
                            valueRange = 0.85f..1.30f,
                            steps = 8
                        )
                        Text(
                            text = "聊天氣泡圓角半徑（${bubbleCornerDp} dp）",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = bubbleCornerDp.toFloat(),
                            onValueChange = { v ->
                                val dpVal = v.roundToInt().coerceIn(8, 24)
                                bubbleCornerDp = dpVal
                                PhantomPrefs.setChatBubbleCornerDp(appCtx, dpVal)
                                PhantomSettingsBus.notifyChanged()
                            },
                            valueRange = 8f..24f,
                            steps = 15
                        )
                        Text(
                            text = "聊天氣泡寬度比例（${bubbleWidthPct}%）",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = bubbleWidthPct.toFloat(),
                            onValueChange = { v ->
                                val pctVal = v.roundToInt().coerceIn(70, 95)
                                bubbleWidthPct = pctVal
                                PhantomPrefs.setChatBubbleWidthPercent(appCtx, pctVal)
                                PhantomSettingsBus.notifyChanged()
                            },
                            valueRange = 70f..95f
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "首頁我的最愛 App 橫列顯示上限",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            listOf(0 to "全部（預設）", 4 to "最多 4 個", 6 to "最多 6 個", 8 to "最多 8 個").forEach { (limit, label) ->
                                FilterChip(
                                    selected = favMaxVisible == limit,
                                    onClick = {
                                        favMaxVisible = limit
                                        PhantomPrefs.setFavoriteAppsMaxVisible(appCtx, limit)
                                        PhantomSettingsBus.notifyChanged()
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        var cardBgMode by remember { mutableStateOf(ThemePreferences.getCardBgMode(appCtx)) }
                        var cardBgAlpha by remember { mutableIntStateOf(ThemePreferences.getCardBgAlphaPercent(appCtx)) }
                        Text(
                            text = "卡片底色模式",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "自訂訊息卡、對話氣泡與待辦卡的底色不透明度",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PhantomPrefs.CardBgMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = cardBgMode == mode,
                                    onClick = {
                                        cardBgMode = mode
                                        ThemePreferences.setCardBgMode(appCtx, mode)
                                    },
                                    label = { Text(mode.label) }
                                )
                            }
                        }
                        if (cardBgMode == PhantomPrefs.CardBgMode.TRANSPARENT) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "卡片底色透明度（不透明度 $cardBgAlpha%）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = cardBgAlpha.coerceIn(15, 100).toFloat(),
                                onValueChange = { v ->
                                    val pct = v.roundToInt().coerceIn(15, 100)
                                    cardBgAlpha = pct
                                    ThemePreferences.setCardBgAlphaPercent(appCtx, pct)
                                },
                                valueRange = 15f..100f
                            )
                        }
                    }
                }
            }

            // 1.6 統計
            if ("stats" in visibleCardKeys) {
                item(key = "card_stats") {
                    CollapsibleSettingsCard(
                        title = "統計",
                        expanded = expandStats || ("stats" in highlightedCardKeys),
                        highlighted = "stats" in highlightedCardKeys,
                        onToggle = {
                            expandStats = !expandStats
                            PhantomPrefs.setSettingsCardExpanded(appCtx, "stats", expandStats)
                        },
                        glassState = glassState,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            cardBounds["stats"] = coords.boundsInRoot()
                        }
                    ) {
                        Text(
                            text = "統計頁卡片顯示自訂",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "勾選要在統計分頁顯示的圖表與統計卡片（分享圖卡、省 token 提示與每週摘要維持常駐）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        PhantomPrefs.STATS_CARD_OPTIONS.forEachIndexed { idx, option ->
                            if (idx > 0) {
                                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            }
                            SettingSwitchRow(
                                title = option.title,
                                subtitle = option.subtitle,
                                checked = option.key in visibleStatsCards,
                                onCheckedChange = { checked ->
                                    PhantomPrefs.setStatsCardVisible(appCtx, option.key, checked)
                                    visibleStatsCards = PhantomPrefs.getVisibleStatsCards(appCtx)
                                }
                            )
                        }
                    }
                }
            }

            // 2. AI 與分析
            if ("ai" in visibleCardKeys) {
            item(key = "card_ai") {
                CollapsibleSettingsCard(
                    title = "AI 與分析",
                    expanded = expandAi || ("ai" in highlightedCardKeys),
                    highlighted = "ai" in highlightedCardKeys,
                    onToggle = {
                        expandAi = !expandAi
                        PhantomPrefs.setSettingsCardExpanded(appCtx, "ai", expandAi)
                    },
                    glassState = glassState,
                    modifier = Modifier.onGloballyPositioned { coords ->
                        cardBounds["ai"] = coords.boundsInRoot()
                    }
                ) {
                    OutlinedButton(
                        onClick = { ctx.startActivity(Intent(ctx, ApiKeySettingsActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth().pressableScale()
                    ) {
                        Text("多 Provider AI 與 API Key 設定")
                    }
                    Spacer(Modifier.height(8.dp))

                    var tagDispatchTesting by remember { mutableStateOf(false) }
                    var tagDispatchResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
                    val remainingQuotaText = if (dailyAiLimit <= 0) {
                        "不限"
                    } else {
                        "${(dailyAiLimit - todayAiUsage).coerceAtLeast(0)} / $dailyAiLimit 則"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            enabled = !tagDispatchTesting,
                            onClick = {
                                if (!PhantomPrefs.canUseAiTodayAndIncrement(appCtx)) {
                                    tagDispatchResult = false to "今日 AI 額度已達上限（$dailyAiLimit 則），無法執行測試。"
                                    return@OutlinedButton
                                }
                                todayAiUsage = PhantomPrefs.getTodayAiUsage(appCtx)
                                tagDispatchTesting = true
                                tagDispatchResult = null
                                scope.launch {
                                    val allTags = withContext(Dispatchers.IO) {
                                        AppDatabaseHolder.db(appCtx).tagDao().all()
                                    }
                                    val res = AiClient.testAutoTagDispatch(allTags, appCtx)
                                    tagDispatchTesting = false
                                    res.onSuccess { analysis ->
                                        val tagMap = allTags.associateBy { it.id }
                                        val tagNames = analysis.tagIds.mapNotNull { tagMap[it]?.name }
                                        val tagStr = if (tagNames.isEmpty()) "（未指派標籤）" else tagNames.joinToString("、")
                                        tagDispatchResult = true to "測試成功：風險=${analysis.riskLabel} (${analysis.riskScore})｜標籤=$tagStr\n理由：${analysis.riskReason}"
                                    }.onFailure { err ->
                                        PhantomPrefs.refundTodayAiUsage(appCtx)
                                        todayAiUsage = PhantomPrefs.getTodayAiUsage(appCtx)
                                        tagDispatchResult = false to (err.message ?: "測試失敗，請檢查網路或 API Key 設定。")
                                    }
                                }
                            },
                            modifier = Modifier.pressableScale()
                        ) {
                            Text(if (tagDispatchTesting) "測試中..." else "AI 標籤分發測試")
                        }
                        Text(
                            text = "計入每日額度（剩餘：$remainingQuotaText）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    tagDispatchResult?.let { (ok, msg) ->
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (ok) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    SettingSwitchRow(
                        title = "AI 標籤分發失敗通知",
                        subtitle = "24 小時內累積失敗 5 次時發送低干擾提醒（6 小時冷卻）",
                        checked = aiTagFailureNotifEnabled,
                        onCheckedChange = { checked ->
                            aiTagFailureNotifEnabled = checked
                            PhantomPrefs.setAiTagFailureNotifEnabled(appCtx, checked)
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SettingSwitchRow(
                        title = "雙模型信心路由（省 token）",
                        subtitle = "訊息分析先打輕量模型，僅 30~70 分模糊帶升級主模型重判（不重複扣額度）",
                        checked = dualModelRoutingEnabled,
                        onCheckedChange = { checked ->
                            dualModelRoutingEnabled = checked
                            PhantomPrefs.setDualModelRoutingEnabled(appCtx, checked)
                        }
                    )
                    if (dualModelRoutingEnabled) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = dualModelRoutingModel,
                            onValueChange = { next ->
                                dualModelRoutingModel = next
                                PhantomPrefs.setDualModelRoutingModel(appCtx, next)
                            },
                            label = { Text("第一關輕量路由模型") },
                            placeholder = { Text(PhantomPrefs.DEFAULT_DUAL_MODEL_ROUTING_MODEL) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SettingSwitchRow(
                        title = "AI 助手常用指令選單",
                        subtitle = "在 AI 助手輸入列 + 選單顯示常用指令（關閉後僅保留圖片上傳）",
                        checked = chatSuggestionsEnabled,
                        onCheckedChange = { checked ->
                            chatSuggestionsEnabled = checked
                            PhantomPrefs.setChatSuggestionsEnabled(appCtx, checked)
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SettingSwitchRow(
                        title = "切到 AI 助手時接續上次對話",
                        subtitle = "預設為開啟新對話",
                        checked = aiChatTabContinue,
                        onCheckedChange = { checked ->
                            aiChatTabContinue = checked
                            PhantomPrefs.setAiChatTabBehavior(
                                appCtx,
                                if (checked) PhantomPrefs.AI_CHAT_TAB_CONTINUE else PhantomPrefs.AI_CHAT_TAB_NEW
                            )
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    var showAddQuickCommandDialog by remember { mutableStateOf(false) }
                    Text(
                        text = "AI 助手常用指令預設模式",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "設定點擊常用指令時的預設行為（每則指令亦可個別調整）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = quickCommandDefaultMode == PhantomPrefs.QuickChatCommand.MODE_DIRECT,
                            onClick = {
                                quickCommandDefaultMode = PhantomPrefs.QuickChatCommand.MODE_DIRECT
                                PhantomPrefs.setQuickCommandDefaultMode(appCtx, PhantomPrefs.QuickChatCommand.MODE_DIRECT)
                                quickChatCommands = PhantomPrefs.getQuickChatCommands(appCtx)
                            },
                            label = { Text("直接執行") }
                        )
                        FilterChip(
                            selected = quickCommandDefaultMode == PhantomPrefs.QuickChatCommand.MODE_PREFILL,
                            onClick = {
                                quickCommandDefaultMode = PhantomPrefs.QuickChatCommand.MODE_PREFILL
                                PhantomPrefs.setQuickCommandDefaultMode(appCtx, PhantomPrefs.QuickChatCommand.MODE_PREFILL)
                                quickChatCommands = PhantomPrefs.getQuickChatCommands(appCtx)
                            },
                            label = { Text("預輸入") }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val quickCommandsExpanded = remember {
                        mutableStateOf(PhantomPrefs.isSettingsSubCardExpanded(appCtx, "sub_ai_quick_commands"))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                quickCommandsExpanded.value = !quickCommandsExpanded.value
                                PhantomPrefs.setSettingsSubCardExpanded(appCtx, "sub_ai_quick_commands", quickCommandsExpanded.value)
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "自訂常用指令（${quickChatCommands.size} 則）",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        PhantomChevron(
 expanded = quickCommandsExpanded.value,
                            contentDescription = if (quickCommandsExpanded.value) "收起自訂常用指令" else "展開自訂常用指令",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AnimatedVisibility(
                        visible = quickCommandsExpanded.value,
                        enter = PhantomMotion.ExpandEnter,
                        exit = PhantomMotion.ExpandExit
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (quickChatCommands.any { it.id.startsWith("default_") && it.mode != PhantomPrefs.QuickChatCommand.MODE_FOLLOW }) {
                                    TextButton(onClick = {
                                        quickChatCommands = PhantomPrefs.resetBuiltInQuickCommandsToFollow(appCtx)
                                    }) {
                                        Text("內建重設為跟隨預設")
                                    }
                                }
                                TextButton(onClick = { showAddQuickCommandDialog = true }) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("新增指令")
                                }
                            }
                    if (quickChatCommands.isEmpty()) {
                        Text(
                            text = "尚未自訂指令，可在這裡新增或在 AI 助手輸入「加入常用指令：…」",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            quickChatCommands.forEach { cmd ->
                                val resolvedMode = PhantomPrefs.resolveQuickCommandMode(cmd.mode, quickCommandDefaultMode)
                                val isFollow = cmd.mode == PhantomPrefs.QuickChatCommand.MODE_FOLLOW
                                val resolvedText = if (resolvedMode == PhantomPrefs.QuickChatCommand.MODE_DIRECT) "直接執行" else "預輸入"
                                val chipLabel = if (isFollow) "跟隨預設($resolvedText)" else resolvedText
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = cmd.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = cmd.prompt,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        FilterChip(
                                            selected = resolvedMode == PhantomPrefs.QuickChatCommand.MODE_DIRECT,
                                            onClick = {
                                                val nextMode = when (cmd.mode) {
                                                    PhantomPrefs.QuickChatCommand.MODE_FOLLOW -> PhantomPrefs.QuickChatCommand.MODE_DIRECT
                                                    PhantomPrefs.QuickChatCommand.MODE_DIRECT -> PhantomPrefs.QuickChatCommand.MODE_PREFILL
                                                    else -> PhantomPrefs.QuickChatCommand.MODE_FOLLOW
                                                }
                                                val updated = quickChatCommands.map {
                                                    if (it.id == cmd.id) it.copy(mode = nextMode) else it
                                                }
                                                PhantomPrefs.saveQuickChatCommands(appCtx, updated)
                                                quickChatCommands = PhantomPrefs.getQuickChatCommands(appCtx)
                                            },
                                            label = { Text(chipLabel) }
                                        )
                                        IconButton(
                                            onClick = {
                                                PhantomPrefs.deleteQuickChatCommand(appCtx, cmd.id)
                                                quickChatCommands = PhantomPrefs.getQuickChatCommands(appCtx)
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "刪除指令",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }                        }
                    }

                    if (showAddQuickCommandDialog) {
                        var draftLabel by remember { mutableStateOf("") }
                        var draftPrompt by remember { mutableStateOf("") }
                        var draftMode by remember { mutableStateOf(PhantomPrefs.QuickChatCommand.MODE_FOLLOW) }
                        AlertDialog(
                            onDismissRequest = { showAddQuickCommandDialog = false },
                            title = { Text("新增常用指令") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = draftLabel,
                                        onValueChange = { draftLabel = it },
                                        label = { Text("指令名稱") },
                                        placeholder = { Text("例如：開啟昨日簡報") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = draftPrompt,
                                        onValueChange = { draftPrompt = it },
                                        label = { Text("指令內容") },
                                        placeholder = { Text("例如：請幫我整理昨日簡報") },
                                        maxLines = 3,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = draftMode == PhantomPrefs.QuickChatCommand.MODE_FOLLOW,
                                            onClick = { draftMode = PhantomPrefs.QuickChatCommand.MODE_FOLLOW },
                                            label = { Text("跟隨預設") }
                                        )
                                        FilterChip(
                                            selected = draftMode == PhantomPrefs.QuickChatCommand.MODE_DIRECT,
                                            onClick = { draftMode = PhantomPrefs.QuickChatCommand.MODE_DIRECT },
                                            label = { Text("直接執行") }
                                        )
                                        FilterChip(
                                            selected = draftMode == PhantomPrefs.QuickChatCommand.MODE_PREFILL,
                                            onClick = { draftMode = PhantomPrefs.QuickChatCommand.MODE_PREFILL },
                                            label = { Text("預輸入") }
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    enabled = draftLabel.isNotBlank() || draftPrompt.isNotBlank(),
                                    onClick = {
                                        val finalLabel = draftLabel.trim().ifBlank { draftPrompt.trim().take(16) }
                                        val finalPrompt = draftPrompt.trim().ifBlank { finalLabel }
                                        PhantomPrefs.addQuickChatCommand(appCtx, finalLabel, finalPrompt, draftMode)
                                        quickChatCommands = PhantomPrefs.getQuickChatCommands(appCtx)
                                        showAddQuickCommandDialog = false
                                    }
                                ) {
                                    Text("新增")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showAddQuickCommandDialog = false }) {
                                    Text("取消")
                                }
                            }
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "每日 AI 額度上限（今日已用 $todayAiUsage 次）",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val presetLimits = listOf(0, 20, 50, 100)
                        val visibleLimits = if (dailyAiLimit !in presetLimits && dailyAiLimit >= 0) {
                            presetLimits + dailyAiLimit
                        } else {
                            presetLimits
                        }
                        visibleLimits.sorted().forEach { limit ->
                            FilterChip(
                                selected = dailyAiLimit == limit,
                                onClick = {
                                    dailyAiLimit = limit
                                    PhantomPrefs.setDailyAiLimit(appCtx, limit)
                                },
                                label = { Text(if (limit == 0) "不限" else "$limit 次") }
                            )
                        }
                    }
                    Text(
                        text = "限制每日 AI 分析呼叫次數，0 為不限；亦可用 AI 助手以 10 次為步進調整（0–100 次）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(
                        text = if (tokenSaverEnabled) {
                            "AI 助手單次閱讀訊息數：$chatRagLimit 則（省 token 模式生效中：實際 100 則）"
                        } else {
                            "AI 助手單次閱讀訊息數：$chatRagLimit 則"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "範圍 50–800 則（預設 400 則，全量讀取將消耗較多 token，請留意額度）；省 token 模式開啟時自動降為 100 則。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = chatRagLimit.toFloat(),
                        onValueChange = { raw ->
                            val stepped = ((raw / 10f).roundToInt() * 10)
                                .coerceIn(PhantomPrefs.MIN_CHAT_RAG_MESSAGE_LIMIT, PhantomPrefs.MAX_CHAT_RAG_MESSAGE_LIMIT)
                            if (stepped != chatRagLimit) {
                                chatRagLimit = stepped
                                PhantomPrefs.setChatRagMessageLimit(appCtx, stepped)
                            }
                        },
                        valueRange = PhantomPrefs.MIN_CHAT_RAG_MESSAGE_LIMIT.toFloat()..PhantomPrefs.MAX_CHAT_RAG_MESSAGE_LIMIT.toFloat(),
                        steps = 74
                    )

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "AI 分析觸發字數門檻：$aiMinTriggerLength 字",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "新訊息內容至少達此字數才觸發 AI 分析（1–30 字，預設 10 字）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = aiMinTriggerLength.toFloat(),
                        onValueChange = { raw ->
                            val stepped = raw.roundToInt().coerceIn(1, 30)
                            if (stepped != aiMinTriggerLength) {
                                aiMinTriggerLength = stepped
                                PhantomPrefs.setAiMinTriggerLength(appCtx, stepped)
                            }
                        },
                        valueRange = 1f..30f,
                        steps = 28
                    )

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SettingSwitchRow(
                        title = "離線 AI 分析自動補佇列",
                        subtitle = "斷網或暫時性錯誤失敗時自動排入佇列，恢復網路後補齊分析（關閉將清空佇列）",
                        checked = aiOfflineRetryEnabled,
                        onCheckedChange = { checked ->
                            aiOfflineRetryEnabled = checked
                            PhantomPrefs.setAiOfflineRetryEnabled(appCtx, checked)
                            if (!checked) {
                                scope.launch {
                                    com.phantom.checker.service.PendingAiAnalysisPolicy.disableAndClear(appCtx)
                                }
                            }
                        }
                    )

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SettingSwitchRow(
                        title = "自動備忘錄",
                        subtitle = "AI 分析新訊息時自動擷取約定時間、繳費期限、取貨編號等備忘寫入「記事」分頁",
                        checked = aiAutoMemoEnabled,
                        onCheckedChange = { checked ->
                            aiAutoMemoEnabled = checked
                            PhantomPrefs.setAiAutoMemoEnabled(appCtx, checked)
                        }
                    )

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "即時提醒關鍵字（${alertKeywords.size}/${PhantomPrefs.MAX_ALERT_KEYWORDS}）",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "獨立於過濾規則，命中即發送高優先「🔔 重要訊息」通知",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newAlertKeyword,
                            onValueChange = { newAlertKeyword = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("輸入重要提醒關鍵字…") }
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (PhantomPrefs.addAlertKeyword(appCtx, newAlertKeyword)) {
                                    alertKeywords = PhantomPrefs.getAlertKeywords(appCtx)
                                    newAlertKeyword = ""
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("關鍵字重複或已達 20 組上限")
                                    }
                                }
                            },
                            enabled = newAlertKeyword.isNotBlank() &&
                                alertKeywords.size < PhantomPrefs.MAX_ALERT_KEYWORDS
                        ) {
                            Text("新增")
                        }
                    }
                    if (alertKeywords.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            alertKeywords.forEach { kw ->
                                FilterChip(
                                    selected = true,
                                    onClick = {
                                        PhantomPrefs.removeAlertKeyword(appCtx, kw)
                                        alertKeywords = PhantomPrefs.getAlertKeywords(appCtx)
                                    },
                                    label = { Text("$kw ✕") }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { ctx.startActivity(Intent(ctx, AutoReplySettingsActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth().pressableScale()
                    ) {
                        Text("SMS 自動回覆設定")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { ctx.startActivity(Intent(ctx, ScamCenterActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth().pressableScale()
                    ) {
                        Text("詐騙警示中心")
                    }
                }
            }
            }

            // 2.5 記憶庫與 AI 助手（#315 B4）
            if ("memory_assistant" in visibleCardKeys) {
            item(key = "card_memory_assistant") {
                CollapsibleSettingsCard(
                    title = "記憶庫與 AI 助手",
                    expanded = expandMemoryAssistant || ("memory_assistant" in highlightedCardKeys),
                    highlighted = "memory_assistant" in highlightedCardKeys,
                    onToggle = {
                        expandMemoryAssistant = !expandMemoryAssistant
                        PhantomPrefs.setSettingsCardExpanded(appCtx, "memory_assistant", expandMemoryAssistant)
                    },
                    glassState = glassState,
                    modifier = Modifier.onGloballyPositioned { coords ->
                        cardBounds["memory_assistant"] = coords.boundsInRoot()
                    }
                ) {
                    // (1) 記憶擷取與確認卡說明
                    Text(
                        text = "一、記憶擷取與確認機制",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    SettingSwitchRow(
                        title = "聊天記憶擷取",
                        subtitle = "允許 AI 在對話中提議記住人物、地點、行程、偏好與關係",
                        checked = memoryChatCaptureEnabled,
                        onCheckedChange = { checked ->
                            memoryChatCaptureEnabled = checked
                            PhantomPrefs.setMemoryChatCaptureEnabled(appCtx, checked)
                            PhantomSettingsBus.notifyChanged()
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    SettingSwitchRow(
                        title = "記憶寫入前先顯示確認卡",
                        subtitle = "開啟時，AI 產生的新記憶會先在 AI 助手對話中顯示確認卡，由您選擇寫入／保留；關閉時直接寫入（🔒 敏感記憶與衝突仍必經確認）。您隨時可在記憶頁手動新增、編輯或刪除記憶。",
                        checked = memoryWriteConfirmEnabled,
                        onCheckedChange = { checked ->
                            memoryWriteConfirmEnabled = checked
                            PhantomPrefs.setMemoryWriteConfirmEnabled(appCtx, checked)
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    SettingSwitchRow(
                        title = "記憶 × 防詐交叉檢查",
                        subtitle = "離線比對本機通訊錄與一般記憶（熟人冒充、情境矛盾、防詐免疫），絕不外送原始通訊錄或敏感記憶；關閉時行為與 v3.0.0 完全一致",
                        checked = antiFraudCrossCheckEnabled,
                        onCheckedChange = { checked ->
                            antiFraudCrossCheckEnabled = checked
                            PhantomPrefs.setAntiFraudCrossCheckEnabled(appCtx, checked)
                            PhantomSettingsBus.notifyChanged()
                        }
                    )
                    if (antiFraudCrossCheckEnabled) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (contactsPermissionGranted) {
                                    "通訊錄權限：已授權（僅本機離線比對，不外送）"
                                } else {
                                    "通訊錄權限：未授權（目前降級為純記憶庫比對）"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            if (!contactsPermissionGranted) {
                                TextButton(
                                    onClick = {
                                        contactsPermLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                                    }
                                ) {
                                    Text("授予權限")
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "• 確認保護：開啟「記憶寫入前先顯示確認卡」時，AI 產生的新記憶會先在 AI 助手對話中由您確認；即使關閉，標為「🔒 敏感」的記憶、與既有記憶衝突的項目，以及任何刪除動作，仍一律彈出確認卡保護。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(Modifier.padding(vertical = 10.dp))

                    // #329 / #348 本機日曆整合（多日曆勾選，預設全不勾，日曆資料絕不外送 API）
                    Text(
                        text = "本機日曆整合（僅本機存取，絕不外送 API）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "僅會查詢與寫入您明確勾選的本機日曆（預設全不勾）；AI 助手空檔查詢由本機直接計算，寫入前自動檢查撞期。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    SettingSwitchRow(
                        title = "日曆寫入前先顯示確認卡",
                        subtitle = "開啟時，寫入行程前會在 AI 助手對話中顯示行程確認卡；關閉時仍會檢查撞期，無撞期且已授權寫入權限則直接寫入已勾選日曆。",
                        checked = calendarWriteConfirmEnabled,
                        onCheckedChange = { checked ->
                            calendarWriteConfirmEnabled = checked
                            PhantomPrefs.setCalendarWriteConfirmEnabled(appCtx, checked)
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    if (!calendarReadGranted) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "日曆權限：未授權（查詢與自動行程建議目前停用）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    calendarPermLauncher.launch(
                                        arrayOf(
                                            android.Manifest.permission.READ_CALENDAR,
                                            android.Manifest.permission.WRITE_CALENDAR
                                        )
                                    )
                                }
                            ) {
                                Text("授予日曆權限")
                            }
                        }
                    } else {
                        if (!calendarWriteGranted) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "已授權讀取；未授予寫入權限時將以日曆 App 預填方式新增行程",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        calendarPermLauncher.launch(
                                            arrayOf(
                                                android.Manifest.permission.READ_CALENDAR,
                                                android.Manifest.permission.WRITE_CALENDAR
                                            )
                                        )
                                    }
                                ) {
                                    Text("補授寫入")
                                }
                            }
                        }
                        if (deviceCalendars.isEmpty()) {
                            Text(
                                text = "裝置目前沒有可讀取的本機日曆帳號。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            var showHiddenCalendars by remember { mutableStateOf(false) }
                            val visibleCals = remember(deviceCalendars) {
                                deviceCalendars.filter { it.visible }
                            }
                            val hiddenCals = remember(deviceCalendars) {
                                deviceCalendars.filter { !it.visible }
                            }
                            val selectedTotalCount = deviceCalendars.count { it.id in selectedCalendarIds }
                            val selectedVisibleCount = visibleCals.count { it.id in selectedCalendarIds }
                            val allVisibleSelected = visibleCals.isNotEmpty() && visibleCals.all { it.id in selectedCalendarIds }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val next = !calendarListExpanded
                                        calendarListExpanded = next
                                        PhantomPrefs.setCalendarListExpanded(appCtx, next)
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "已勾選 $selectedTotalCount 個（顯示中日曆 $selectedVisibleCount / ${visibleCals.size}）",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (hiddenCals.isNotEmpty()) {
                                        Text(
                                            text = "已自動隱藏 ${hiddenCals.size} 個未顯示／訂閱日曆",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                PhantomChevron(
 expanded = calendarListExpanded,
                                    contentDescription = if (calendarListExpanded) "收合日曆清單" else "展開日曆清單",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // #373 一鍵勾選所有顯示中日曆主按鈕（解決預設零勾選導致空查詢）
                            if (visibleCals.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            val next = if (allVisibleSelected) {
                                                selectedCalendarIds - visibleCals.map { it.id }.toSet()
                                            } else {
                                                selectedCalendarIds + visibleCals.map { it.id }.toSet()
                                            }
                                            selectedCalendarIds = next
                                            PhantomPrefs.setSelectedCalendarIds(appCtx, next)
                                            PhantomSettingsBus.notifyChanged()
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = if (allVisibleSelected) "取消所有顯示中日曆" else "一鍵勾選所有顯示中日曆（${visibleCals.size}）",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(
 visible = calendarListExpanded,
 enter = PhantomMotion.ExpandEnter,
 exit = PhantomMotion.ExpandExit
 ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val renderCalendarGroups: @Composable (List<com.phantom.checker.util.CalendarBridge.CalendarAccountInfo>, Boolean) -> Unit =
                                        { cals, isHiddenSection ->
                                            val grouped = cals.groupBy { it.accountName.ifBlank { "本機／未分類帳號" } }
                                            grouped.forEach { (accountLabel, groupCals) ->
                                                val groupIds = groupCals.map { it.id }.toSet()
                                                val groupAllChecked = groupIds.all { it in selectedCalendarIds }
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 4.dp, bottom = 2.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "$accountLabel（${groupCals.size}）",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        TextButton(
                                                            onClick = {
                                                                val next = if (groupAllChecked) {
                                                                    selectedCalendarIds - groupIds
                                                                } else {
                                                                    selectedCalendarIds + groupIds
                                                                }
                                                                selectedCalendarIds = next
                                                                PhantomPrefs.setSelectedCalendarIds(appCtx, next)
                                                                PhantomSettingsBus.notifyChanged()
                                                            },
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = if (groupAllChecked) "全取消" else "全選",
                                                                style = MaterialTheme.typography.labelSmall
                                                            )
                                                        }
                                                    }
                                                    groupCals.forEach { cal ->
                                                        val checked = cal.id in selectedCalendarIds
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable {
                                                                    val next = if (checked) selectedCalendarIds - cal.id else selectedCalendarIds + cal.id
                                                                    selectedCalendarIds = next
                                                                    PhantomPrefs.setSelectedCalendarIds(appCtx, next)
                                                                    PhantomSettingsBus.notifyChanged()
                                                                }
                                                                .padding(vertical = 2.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Checkbox(
                                                                checked = checked,
                                                                onCheckedChange = { isChecked ->
                                                                    val next = if (isChecked) selectedCalendarIds + cal.id else selectedCalendarIds - cal.id
                                                                    selectedCalendarIds = next
                                                                    PhantomPrefs.setSelectedCalendarIds(appCtx, next)
                                                                    PhantomSettingsBus.notifyChanged()
                                                                }
                                                            )
                                                            Surface(
                                                                modifier = Modifier.size(10.dp),
                                                                shape = androidx.compose.foundation.shape.CircleShape,
                                                                color = if (cal.color != 0) Color(cal.color) else MaterialTheme.colorScheme.primary
                                                            ) {}
                                                            Spacer(Modifier.width(8.dp))
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                val badges = buildString {
                                                                    append(if (cal.isWritable) "（可讀寫）" else "（唯讀）")
                                                                    if (isHiddenSection) append("・已隱藏")
                                                                    if (!cal.synced) append("・未同步")
                                                                }
                                                                Text(
                                                                    text = cal.displayName + badges,
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    maxLines = 2,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                    if (visibleCals.isEmpty()) {
                                        Text(
                                            text = "目前無顯示中的本機日曆，可展開下方隱藏日曆檢視。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        renderCalendarGroups(visibleCals, false)
                                    }

                                    if (hiddenCals.isNotEmpty()) {
                                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { showHiddenCalendars = !showHiddenCalendars }
                                                .padding(vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "顯示隱藏／未同步日曆（${hiddenCals.size}）",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            PhantomChevron(
 expanded = showHiddenCalendars,
                                                contentDescription = if (showHiddenCalendars) "收合隱藏日曆" else "展開隱藏日曆",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        AnimatedVisibility(
 visible = showHiddenCalendars,
 enter = PhantomMotion.ExpandEnter,
 exit = PhantomMotion.ExpandExit
 ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                renderCalendarGroups(hiddenCals, true)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "行程類通知分流",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "行程類通知優先提示加入日曆，不再自動建立待辦/記事（明確設定的備忘規則仍優先）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = scheduleMemoRoute == PhantomPrefs.SCHEDULE_MEMO_ROUTE_SUGGEST_CALENDAR,
                            onClick = {
                                scheduleMemoRoute = PhantomPrefs.SCHEDULE_MEMO_ROUTE_SUGGEST_CALENDAR
                                PhantomPrefs.setScheduleMemoRoute(appCtx, scheduleMemoRoute)
                                PhantomSettingsBus.notifyChanged()
                            },
                            label = { Text("優先日曆建議（預設）", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = scheduleMemoRoute == PhantomPrefs.SCHEDULE_MEMO_ROUTE_LEGACY_TODO_NOTE,
                            onClick = {
                                scheduleMemoRoute = PhantomPrefs.SCHEDULE_MEMO_ROUTE_LEGACY_TODO_NOTE
                                PhantomPrefs.setScheduleMemoRoute(appCtx, scheduleMemoRoute)
                                PhantomSettingsBus.notifyChanged()
                            },
                            label = { Text("沿用舊版（待辦/記事）", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 10.dp))

                    // #336 / #340 通訊錄整合已集中收納於「AI 夥伴與主動推播」獨立設定頁
                    OutlinedButton(
                        onClick = { ctx.startActivity(Intent(ctx, AssistantHubActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("管理通訊錄整合（暱稱綁定／關係匯入／VIP 信任圈）")
                    }

                    HorizontalDivider(Modifier.padding(vertical = 10.dp))

                    // (2) 注入預算（正常模式 vs 省 token 模式分開設定、互不覆寫）
                    Text(
                        text = "二、對話記憶注入預算（雙模式獨立保存）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    val effectiveTopK = PhantomPrefs.getEffectiveMemoryInjectionTopK(appCtx)
                    val effectiveBudget = PhantomPrefs.getEffectiveMemoryTotalTokenBudget(appCtx)
                    Text(
                        text = if (tokenSaverEnabled) {
                            "目前生效模式：省 token 模式（上限 $effectiveTopK 筆／總預算 ≤$effectiveBudget tokens；正常模式設定值已安全保留不覆寫）"
                        } else {
                            "目前生效模式：正常模式（上限 $effectiveTopK 筆／總預算 ≤$effectiveBudget tokens）"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "正常模式注入筆數（Top-K）：$memoryNormalTopK 筆（範圍 1–8，預設 8，總預算 ≤250 tokens）",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = memoryNormalTopK.toFloat(),
                        onValueChange = { raw ->
                            val stepped = raw.roundToInt().coerceIn(
                                PhantomPrefs.MIN_MEMORY_NORMAL_TOP_K,
                                PhantomPrefs.MAX_MEMORY_NORMAL_TOP_K
                            )
                            if (stepped != memoryNormalTopK) {
                                memoryNormalTopK = stepped
                                PhantomPrefs.setMemoryNormalTopK(appCtx, stepped)
                                PhantomSettingsBus.notifyChanged()
                            }
                        },
                        valueRange = PhantomPrefs.MIN_MEMORY_NORMAL_TOP_K.toFloat()..PhantomPrefs.MAX_MEMORY_NORMAL_TOP_K.toFloat(),
                        steps = (PhantomPrefs.MAX_MEMORY_NORMAL_TOP_K - PhantomPrefs.MIN_MEMORY_NORMAL_TOP_K - 1).coerceAtLeast(0)
                    )

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "正常模式單筆預覽截斷字數：$memoryNormalPerItemChars 字（範圍 30–120 字，預設 70 字 ≈ 50 tokens）",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = memoryNormalPerItemChars.toFloat(),
                        onValueChange = { raw ->
                            val stepped = ((raw / 10f).roundToInt() * 10).coerceIn(
                                PhantomPrefs.MIN_MEMORY_NORMAL_PER_ITEM_CHARS,
                                PhantomPrefs.MAX_MEMORY_NORMAL_PER_ITEM_CHARS
                            )
                            if (stepped != memoryNormalPerItemChars) {
                                memoryNormalPerItemChars = stepped
                                PhantomPrefs.setMemoryNormalPerItemChars(appCtx, stepped)
                                PhantomSettingsBus.notifyChanged()
                            }
                        },
                        valueRange = PhantomPrefs.MIN_MEMORY_NORMAL_PER_ITEM_CHARS.toFloat()..PhantomPrefs.MAX_MEMORY_NORMAL_PER_ITEM_CHARS.toFloat(),
                        steps = 8
                    )

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "省 token 模式注入筆數（Top-K）：$memorySaverTopK 筆（範圍 1–4，預設 4，單筆約 25 tokens，總預算 ≤120 tokens）",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = memorySaverTopK.toFloat(),
                        onValueChange = { raw ->
                            val stepped = raw.roundToInt().coerceIn(
                                PhantomPrefs.MIN_MEMORY_SAVER_TOP_K,
                                PhantomPrefs.MAX_MEMORY_SAVER_TOP_K
                            )
                            if (stepped != memorySaverTopK) {
                                memorySaverTopK = stepped
                                PhantomPrefs.setMemorySaverTopK(appCtx, stepped)
                                PhantomSettingsBus.notifyChanged()
                            }
                        },
                        valueRange = PhantomPrefs.MIN_MEMORY_SAVER_TOP_K.toFloat()..PhantomPrefs.MAX_MEMORY_SAVER_TOP_K.toFloat(),
                        steps = (PhantomPrefs.MAX_MEMORY_SAVER_TOP_K - PhantomPrefs.MIN_MEMORY_SAVER_TOP_K - 1).coerceAtLeast(0)
                    )

                    HorizontalDivider(Modifier.padding(vertical = 10.dp))

                    // (3) 自訂 AI 助手（名字、個性 preset + 自訂、詳細度、頭像）
                    Text(
                        text = "三、自訂 AI 助手（名字／個性／詳細度／頭像）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))

                    // 頭像預覽與上傳／重設
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AssistantAvatarCircle(
                            avatarBitmap = assistantAvatarBitmap,
                            assistantName = assistantName,
                            size = 56.dp
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "AI 助手頭像（聊天頂部與回覆氣泡即時顯示）",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    enabled = !isSavingAvatar,
                                    onClick = {
                                        try {
                                            avatarVisualPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        } catch (_: Exception) {
                                            try {
                                                avatarDocumentPicker.launch(arrayOf("image/*"))
                                            } catch (_: Exception) {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("無法啟動系統圖片選擇器")
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Text(if (assistantHasAvatar) "更換頭像" else "上傳頭像")
                                }
                                if (assistantHasAvatar) {
                                    OutlinedButton(
                                        enabled = !isSavingAvatar,
                                        onClick = {
                                            AssistantAvatarHelper.removeAvatar(appCtx)
                                            assistantHasAvatar = false
                                            assistantAvatarBitmap = null
                                            scope.launch {
                                                snackbarHostState.showSnackbar("已重設為預設頭像")
                                            }
                                        }
                                    ) {
                                        Text("重設")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = assistantName,
                            onValueChange = { next ->
                                val clamped = next.take(PhantomPrefs.MAX_ASSISTANT_NAME_LENGTH)
                                assistantName = clamped
                                PhantomPrefs.setAssistantName(appCtx, clamped)
                                PhantomSettingsBus.notifyChanged()
                            },
                            label = { Text("AI 助手名字（預設「幻訊」）") },
                            placeholder = { Text(PhantomPrefs.DEFAULT_ASSISTANT_NAME) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        if (assistantName != PhantomPrefs.DEFAULT_ASSISTANT_NAME) {
                            TextButton(
                                onClick = {
                                    assistantName = PhantomPrefs.DEFAULT_ASSISTANT_NAME
                                    PhantomPrefs.setAssistantName(appCtx, PhantomPrefs.DEFAULT_ASSISTANT_NAME)
                                    PhantomSettingsBus.notifyChanged()
                                }
                            ) {
                                Text("重設")
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "個性與說話風格（Preset ＋ 自訂描述，注入預算 ≤120 tokens）",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PhantomPrefs.AssistantPersonalityPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = assistantPersonalityPreset == preset,
                                onClick = {
                                    assistantPersonalityPreset = preset
                                    PhantomPrefs.setAssistantPersonalityPreset(appCtx, preset)
                                    PhantomSettingsBus.notifyChanged()
                                },
                                label = { Text(preset.label) }
                            )
                        }
                    }
                    if (assistantPersonalityPreset != PhantomPrefs.AssistantPersonalityPreset.CUSTOM &&
                        assistantPersonalityPreset.promptInstruction.isNotBlank()
                    ) {
                        Text(
                            text = "Preset 風格：${assistantPersonalityPreset.promptInstruction}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = assistantCustomPersonality,
                        onValueChange = { next ->
                            val clamped = next.take(PhantomPrefs.MAX_ASSISTANT_CUSTOM_PERSONALITY_CHARS)
                            assistantCustomPersonality = clamped
                            PhantomPrefs.setAssistantCustomPersonality(appCtx, clamped)
                            PhantomSettingsBus.notifyChanged()
                        },
                        label = {
                            Text(
                                if (assistantPersonalityPreset == PhantomPrefs.AssistantPersonalityPreset.CUSTOM) {
                                    "自訂個性描述（${assistantCustomPersonality.length}/${PhantomPrefs.MAX_ASSISTANT_CUSTOM_PERSONALITY_CHARS} 字）"
                                } else {
                                    "補充自訂個性指令（選填，${assistantCustomPersonality.length}/${PhantomPrefs.MAX_ASSISTANT_CUSTOM_PERSONALITY_CHARS} 字）"
                                }
                            )
                        },
                        placeholder = { Text("例如：稱呼我為店長，回答金融訊息時特別標註金額與期限") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "回答詳細度",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PhantomPrefs.AssistantVerbosity.entries.forEach { verb ->
                            FilterChip(
                                selected = assistantVerbosity == verb,
                                onClick = {
                                    assistantVerbosity = verb
                                    PhantomPrefs.setAssistantVerbosity(appCtx, verb)
                                    PhantomSettingsBus.notifyChanged()
                                },
                                label = { Text(verb.label) }
                            )
                        }
                    }
                    Text(
                        text = assistantVerbosity.promptInstruction,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(Modifier.padding(vertical = 10.dp))

                    // (4) 隱私分級說明
                    Text(
                        text = "四、記憶庫隱私分級說明",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "• 一般（normal）：僅在本地關鍵字、分類或時間匹配時，依上方 Top-K 預算並經 sanitizeForPrompt 防注入處理後才注入 AI 助手對話。",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• 🔒 敏感（sensitive）：由 SQL 與檢索閘門雙重硬性排除，在任何對外 AI 請求（AI 助手對話、訊息深度分析、每日／每週摘要、設定語意搜尋）中 100% 絕不外送，僅供您於本機「記憶」分頁檢視與管理。",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            }

            // 3. 訊息與通知
            if ("notif" in visibleCardKeys) {
            item(key = "card_notif") {
                CollapsibleSettingsCard(
                    title = "訊息與通知",
                    expanded = expandNotif || ("notif" in highlightedCardKeys),
                    highlighted = "notif" in highlightedCardKeys,
                    onToggle = {
                        expandNotif = !expandNotif
                        PhantomPrefs.setSettingsCardExpanded(appCtx, "notif", expandNotif)
                    },
                    glassState = glassState,
                    modifier = Modifier.onGloballyPositioned { coords ->
                        cardBounds["notif"] = coords.boundsInRoot()
                    }
                ) {
                    // 過濾規則新增
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("過濾關鍵字規則", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        TextButton(onClick = { showPresetDialog = true }) {
                            Text("匯入預設關鍵字")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newKeyword,
                            onValueChange = { newKeyword = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("輸入過濾關鍵字…") }
                        )
                        Spacer(Modifier.width(8.dp))
                        val trimmedKeyword = newKeyword.trim()
                        val trimmedPkg = targetPackageName.trim()
                        val resolvedPkg = if (isAllApps) null else trimmedPkg
                        val hasValidScope = isAllApps || trimmedPkg.isNotEmpty()
                        val canAdd = trimmedKeyword.isNotEmpty() &&
                            hasValidScope &&
                            rules.none {
                                it.keyword.equals(trimmedKeyword, ignoreCase = true) &&
                                    it.packageName == resolvedPkg
                            }
                        Button(
                            onClick = {
                                scope.launch {
                                    rules = withContext(Dispatchers.IO) {
                                        val dao = AppDatabaseHolder.db(appCtx).filterDao()
                                        dao.insert(
                                            FilterRule(
                                                keyword = trimmedKeyword,
                                                isActive = true,
                                                packageName = resolvedPkg,
                                                action = newRuleAction
                                            )
                                        )
                                        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                                        dao.all()
                                    }
                                    newKeyword = ""
                                    targetPackageName = ""
                                }
                            },
                            enabled = canAdd
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "新增")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("命中動作：", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            FilterRule.ACTION_FILTER to "標記已過濾",
                            FilterRule.ACTION_DROP to "不記錄",
                            FilterRule.ACTION_SCAM to "標記詐騙警示"
                        ).forEach { (act, label) ->
                            FilterChip(
                                selected = newRuleAction == act,
                                onClick = { newRuleAction = act },
                                label = { Text(label) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isAllApps,
                            onClick = { isAllApps = true },
                            label = { Text("全部 App") }
                        )
                        FilterChip(
                            selected = !isAllApps,
                            onClick = { isAllApps = false },
                            label = { Text("指定 App") }
                        )
                    }
                    if (!isAllApps) {
                        OutlinedTextField(
                            value = targetPackageName,
                            onValueChange = { targetPackageName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("來源 App Package 名稱") },
                            placeholder = { Text("例如：com.android.mms") }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (isLoadingRules) {
                        CircularProgressIndicator()
                    } else if (rules.isEmpty()) {
                        Text("目前尚無過濾關鍵字", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        rules.forEach { rule ->
                            FilterRuleRow(
                                rule = rule,
                                onToggleActive = { checked ->
                                    scope.launch {
                                        val updated = rule.copy(isActive = checked)
                                        withContext(Dispatchers.IO) {
                                            AppDatabaseHolder.db(appCtx).filterDao().update(updated)
                                            com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                                        }
                                        rules = rules.map { if (it.id == rule.id) updated else it }
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            AppDatabaseHolder.db(appCtx).filterDao().delete(rule)
                                            com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                                        }
                                        rules = rules.filterNot { it.id == rule.id }
                                    }
                                }
                            )
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SettingSwitchRow(
                        title = "記錄持續性通知",
                        subtitle = "關閉時略過系統／前景服務等持續刷新的通知",
                        checked = recordOngoing,
                        onCheckedChange = { checked ->
                            recordOngoing = checked
                            PhantomPrefs.setRecordOngoingEnabled(appCtx, checked)
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    val secInt = throttleSec.roundToInt()
                    Text(
                        text = if (secInt == 0) "同 key 重複節流：已關閉" else "同 key 重複節流：$secInt 秒",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "僅作用於內容相同的重複更新（實質新訊息一律收錄，不漏訊）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = throttleSec,
                        onValueChange = { throttleSec = it },
                        onValueChangeFinished = {
                            PhantomPrefs.setSameKeyThrottleSeconds(appCtx, throttleSec.roundToInt())
                        },
                        valueRange = 0f..300f,
                        steps = 29
                    )
                    Spacer(Modifier.height(6.dp))
                    SettingSwitchRow(
                        title = "數字微變視為同一則更新",
                        subtitle = "抑制監控類通知（如流量／電量統計）僅數字跳動時的洗版",
                        checked = digitVarianceThrottle,
                        onCheckedChange = { enabled ->
                            digitVarianceThrottle = enabled
                            PhantomPrefs.setSameKeyDigitVarianceThrottleEnabled(appCtx, enabled)
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    val appThrottleExpanded = remember {
                        mutableStateOf(PhantomPrefs.isSettingsSubCardExpanded(appCtx, "sub_notif_app_throttle"))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                appThrottleExpanded.value = !appThrottleExpanded.value
                                PhantomPrefs.setSettingsSubCardExpanded(appCtx, "sub_notif_app_throttle", appThrottleExpanded.value)
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "個別 App 節流覆寫（${appThrottleMap.size} 個）",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        PhantomChevron(
 expanded = appThrottleExpanded.value,
                            contentDescription = if (appThrottleExpanded.value) "收起個別 App 節流覆寫" else "展開個別 App 節流覆寫",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "0 秒 = 永不節流，且通訊類若 postTime 更新則收錄同內容訊息",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AnimatedVisibility(
                        visible = appThrottleExpanded.value,
                        enter = PhantomMotion.ExpandEnter,
                        exit = PhantomMotion.ExpandExit
                    ) {
                        Column {
                    if (appThrottleMap.isEmpty()) {
                        Text(
                            text = "尚未設定個別 App 覆寫，全部套用上方全域秒數",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Spacer(Modifier.height(4.dp))
                        appThrottleMap.entries.sortedBy { it.key }.forEach { (pkg, sec) ->
                            val label = if (sec == 0) "0 秒（永不節流）" else "$sec 秒"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$pkg：$label",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                TextButton(onClick = {
                                    PhantomPrefs.setAppSameKeyThrottle(appCtx, pkg, null)
                                    appThrottleMap = PhantomPrefs.getAppSameKeyThrottleMap(appCtx)
                                }) {
                                    Text("移除", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { showAppThrottlePicker = true },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text("新增 App 節流覆寫")
                    }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    Text("通知點擊行為", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NotifClickBehavior.entries.forEach { behavior ->
                            FilterChip(
                                selected = clickBehavior == behavior,
                                onClick = {
                                    clickBehavior = behavior
                                    PhantomPrefs.setNotifClickBehavior(appCtx, behavior)
                                },
                                label = { Text(behavior.label) }
                            )
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SettingSwitchRow(
                        title = "常駐通知顯示 App 圖示條",
                        subtitle = "在通知欄常駐通知下方顯示近期通知 App 圖示",
                        checked = persistentShowIcons,
                        onCheckedChange = { checked ->
                            persistentShowIcons = checked
                            PhantomPrefs.setPersistentNotifShowIcons(appCtx, checked)
                            if (persistentStats) {
                                StatsForegroundService.startOrRefresh(appCtx)
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    Text("常駐通知樣式", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PersistentNotifStyle.entries.forEach { style ->
                            FilterChip(
                                selected = persistentNotifStyle == style,
                                onClick = {
                                    persistentNotifStyle = style
                                    PhantomPrefs.setPersistentNotifStyle(appCtx, style)
                                    if (persistentStats) {
                                        StatsForegroundService.startOrRefresh(appCtx)
                                    }
                                },
                                label = { Text(style.label) }
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SettingSwitchRow(
                        title = "對話頁滑動刪除",
                        subtitle = "向左滑動訊息氣泡快速刪除（關閉時仍可長按刪除）",
                        checked = swipeToDeleteEnabled,
                        onCheckedChange = { checked ->
                            swipeToDeleteEnabled = checked
                            PhantomPrefs.setSwipeToDeleteEnabled(appCtx, checked)
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SettingSwitchRow(
                        title = "勿擾時段",
                        subtitle = "時段內暫停推送詐騙警示與重要提醒通知（照常記錄）",
                        checked = quietHoursEnabled,
                        onCheckedChange = { checked ->
                            quietHoursEnabled = checked
                            PhantomPrefs.setQuietHoursEnabled(appCtx, checked)
                        }
                    )
                    if (quietHoursEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    TimePickerDialog(
                                        ctx,
                                        { _, h, m ->
                                            quietStartMinute = h * 60 + m
                                            PhantomPrefs.setQuietHoursRange(appCtx, quietStartMinute, quietEndMinute)
                                        },
                                        quietStartMinute / 60,
                                        quietStartMinute % 60,
                                        true
                                    ).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    String.format(
                                        Locale.getDefault(),
                                        "開始 %02d:%02d",
                                        quietStartMinute / 60,
                                        quietStartMinute % 60
                                    )
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    TimePickerDialog(
                                        ctx,
                                        { _, h, m ->
                                            quietEndMinute = h * 60 + m
                                            PhantomPrefs.setQuietHoursRange(appCtx, quietStartMinute, quietEndMinute)
                                        },
                                        quietEndMinute / 60,
                                        quietEndMinute % 60,
                                        true
                                    ).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    String.format(
                                        Locale.getDefault(),
                                        "結束 %02d:%02d",
                                        quietEndMinute / 60,
                                        quietEndMinute % 60
                                    )
                                )
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    var replyProxyEnabled by remember { mutableStateOf(PhantomPrefs.isReplyProxyEnabled(appCtx)) }
                    var replyProxyTimeout by remember { mutableStateOf(PhantomPrefs.getReplyProxyTimeoutMinutes(appCtx)) }
                    val replyProxyBusVer by PhantomSettingsBus.version.collectAsState()
                    LaunchedEffect(replyProxyBusVer) {
                        if (replyProxyBusVer > 0L) {
                            replyProxyEnabled = PhantomPrefs.isReplyProxyEnabled(appCtx)
                            replyProxyTimeout = PhantomPrefs.getReplyProxyTimeoutMinutes(appCtx)
                        }
                    }
                    SettingSwitchRow(
                        title = "通知欄連續快速回覆",
                        subtitle = "透過 AI 助手回覆後於通知欄保留對話輸入框，可連續回覆多則",
                        checked = replyProxyEnabled,
                        onCheckedChange = {
                            replyProxyEnabled = it
                            PhantomPrefs.setReplyProxyEnabled(appCtx, it)
                        }
                    )
                    if (replyProxyEnabled) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "回覆通知閒置自動收起",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                15 to "15 分鐘",
                                30 to "30 分鐘",
                                60 to "60 分鐘",
                                0 to "不自動收起"
                            ).forEach { (mins, label) ->
                                FilterChip(
                                    selected = replyProxyTimeout == mins,
                                    onClick = {
                                        replyProxyTimeout = mins
                                        PhantomPrefs.setReplyProxyTimeoutMinutes(appCtx, mins)
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    var myDisplayNames by remember { mutableStateOf(PhantomPrefs.getMyDisplayNames(appCtx).sorted()) }
                    var newDisplayNameInput by remember { mutableStateOf("") }
                    Text(
                        text = "我的名稱（自我身分偵測）",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "群組通知寄件者命中任一名稱（不分大小寫）時標記為自己傳送，不重複警示也不計入統計",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    if (myDisplayNames.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            myDisplayNames.forEach { displayName ->
                                FilterChip(
                                    selected = true,
                                    onClick = {
                                        val updated = myDisplayNames.filterNot { it.equals(displayName, ignoreCase = true) }.toSet()
                                        PhantomPrefs.setMyDisplayNames(appCtx, updated)
                                        myDisplayNames = updated.sorted()
                                    },
                                    label = { Text(displayName) },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "移除 $displayName",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newDisplayNameInput,
                            onValueChange = { newDisplayNameInput = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("新增我的顯示名稱（如自己在群組中的名稱或暱稱）") }
                        )
                        Spacer(Modifier.width(8.dp))
                        val trimmedName = newDisplayNameInput.trim()
                        val canAddName = trimmedName.isNotEmpty() &&
                            myDisplayNames.none { it.equals(trimmedName, ignoreCase = true) }
                        Button(
                            onClick = {
                                val updated = (myDisplayNames + trimmedName).toSet()
                                PhantomPrefs.setMyDisplayNames(appCtx, updated)
                                myDisplayNames = updated.sorted()
                                newDisplayNameInput = ""
                            },
                            enabled = canAddName
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "新增名稱")
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    OutlinedButton(
                        onClick = { showClearReadConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("清除已讀訊息")
                    }
                }
            }
            }

            // 4. 資料管理
            if ("data" in visibleCardKeys) {
            item(key = "card_data") {
                CollapsibleSettingsCard(
                    title = "資料管理",
                    expanded = expandData || ("data" in highlightedCardKeys),
                    highlighted = "data" in highlightedCardKeys,
                    onToggle = {
                        expandData = !expandData
                        PhantomPrefs.setSettingsCardExpanded(appCtx, "data", expandData)
                    },
                    glassState = glassState,
                    modifier = Modifier.onGloballyPositioned { coords ->
                        cardBounds["data"] = coords.boundsInRoot()
                    }
                ) {
                    // App 靜音清單
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("App 靜音清單", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "已靜音 App 可選擇完全不記錄或僅靜默記錄（不影響既有歷史訊息）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { showAppPickerDialog = true }) {
                            Text("選擇 App")
                        }
                    }
                    if (mutedApps.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        mutedApps.forEach { pkg ->
                            val mode = MuteMode.fromPref(muteModes[pkg])
                            val appLabel = remember(pkg) {
                                try {
                                    ctx.packageManager.getApplicationLabel(
                                        ctx.packageManager.getApplicationInfo(pkg, 0)
                                    ).toString()
                                } catch (_: Exception) {
                                    pkg
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIconImage(
                                        packageName = pkg,
                                        contentDescription = appLabel,
                                        size = 32.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = appLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = mode.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                TextButton(onClick = {
                                    PhantomPrefs.setAppMuted(appCtx, pkg, false)
                                    mutedApps = PhantomPrefs.getMutedApps(appCtx).sorted()
                                    muteModes = PhantomPrefs.getAppMuteModeMap(appCtx)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("已解除靜音：$appLabel")
                                    }
                                }) {
                                    Text("解除靜音")
                                }
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))

                    OutlinedButton(
                        onClick = { ctx.startActivity(Intent(ctx, TagManagementActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth().pressableScale()
                    ) {
                        Text("自訂標籤管理")
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))

                    OutlinedButton(
                        onClick = { ctx.startActivity(Intent(ctx, WhitelistManagementActivity::class.java)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressableScale()
                            .bringIntoViewRequester(whitelistBringIntoViewRequester)
                            .onGloballyPositioned { coords ->
                                cardBounds["whitelist"] = coords.boundsInRoot()
                            }
                    ) {
                        Text("白名單管理（$whitelistCount 筆）")
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))

                    // 保留天數與則數上限
                    Text("訊息保留天數（詐騙記錄永久保留）", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RetentionConfig.OPTIONS.forEach { days ->
                            FilterChip(
                                selected = retentionDays == days,
                                onClick = {
                                    retentionDays = days
                                    RetentionConfig.setRetentionDays(appCtx, days)
                                    RetentionPolicy.scheduleOrUpdate(appCtx)
                                },
                                label = { Text(RetentionConfig.labelForDays(days)) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("訊息保留則數上限", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RetentionConfig.MAX_COUNT_OPTIONS.forEach { cnt ->
                            FilterChip(
                                selected = retentionMaxCount == cnt,
                                onClick = {
                                    retentionMaxCount = cnt
                                    PhantomPrefs.setRetentionMaxCount(appCtx, cnt)
                                    RetentionPolicy.scheduleOrUpdate(appCtx)
                                },
                                label = { Text(RetentionConfig.labelForMaxCount(cnt)) }
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))

                    // 儲存空間明細（唯讀）＋ 手動壓縮資料庫
                    var storageStats by remember { mutableStateOf<DbMaintenance.StorageStats?>(null) }
                    var isVacuuming by remember { mutableStateOf(false) }
                    LaunchedEffect(expandData) {
                        if (expandData) {
                            storageStats = withContext(Dispatchers.IO) {
                                DbMaintenance.measureStorageStats(appCtx)
                            }
                        }
                    }
                    Text("儲存空間明細", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    val statsSnapshot = storageStats
                    if (statsSnapshot == null) {
                        Text(
                            text = "計算中…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "資料庫（含 WAL/SHM）：${Formatter.formatShortFileSize(appCtx, statsSnapshot.dbBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "背景圖片目錄：${Formatter.formatShortFileSize(appCtx, statsSnapshot.backgroundsBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "快取目錄：${Formatter.formatShortFileSize(appCtx, statsSnapshot.cacheBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "合計：${Formatter.formatShortFileSize(appCtx, statsSnapshot.totalBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isVacuuming = true
                                val (beforeBytes, afterBytes, updatedStats) = withContext(Dispatchers.IO) {
                                    val before = DbMaintenance.measureDatabaseBytes(appCtx)
                                    DbMaintenance.enqueueVacuum(appCtx)
                                    WorkManager.getInstance(appCtx)
                                        .getWorkInfosForUniqueWorkFlow(DbMaintenance.UNIQUE_VACUUM_WORK)
                                        .first { infos -> infos.isEmpty() || infos.all { it.state.isFinished } }
                                    val after = DbMaintenance.measureDatabaseBytes(appCtx)
                                    val refreshed = DbMaintenance.measureStorageStats(appCtx)
                                    Triple(before, after, refreshed)
                                }
                                storageStats = updatedStats
                                isVacuuming = false
                                snackbarHostState.showSnackbar(
                                    "資料庫壓縮完成：${Formatter.formatShortFileSize(appCtx, beforeBytes)} → ${Formatter.formatShortFileSize(appCtx, afterBytes)}"
                                )
                            }
                        },
                        enabled = !isVacuuming,
                        modifier = Modifier.fillMaxWidth().pressableScale()
                    ) {
                        Text(if (isVacuuming) "壓縮中…" else "立即壓縮資料庫")
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))

                    OutlinedButton(
                        onClick = { showExportDialog = true },
                        enabled = !isExporting,
                        modifier = Modifier.fillMaxWidth().pressableScale()
                    ) {
                        Text(if (isExporting) "匯出中…" else "匯出訊息 CSV（可選範圍）")
                    }
                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showDeleteAllConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("刪除全部訊息")
                    }
                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            PhantomPrefs.setOnboardingStep(appCtx, 1)
                            PhantomPrefs.setOnboardingCompleted(appCtx, false)
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth().pressableScale()
                    ) {
                        Text("重新播放新手教學")
                    }
                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            PhantomPrefs.setSettingsTourCompleted(appCtx, false)
                            tourStepIndex = 1
                        },
                        modifier = Modifier.fillMaxWidth().pressableScale()
                    ) {
                        Text("重新播放設定教學")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "桌面小工具：桌面長按 → 小工具 → 幻訊統計",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            }

            // 5. 外觀與保活
            if ("appearance" in visibleCardKeys) {
            item(key = "card_appearance") {
                CollapsibleSettingsCard(
                    title = "外觀與保活",
                    expanded = expandAppearance || ("appearance" in highlightedCardKeys),
                    highlighted = "appearance" in highlightedCardKeys,
                    onToggle = {
                        expandAppearance = !expandAppearance
                        PhantomPrefs.setSettingsCardExpanded(appCtx, "appearance", expandAppearance)
                    },
                    glassState = glassState,
                    modifier = Modifier.onGloballyPositioned { coords ->
                        cardBounds["appearance"] = coords.boundsInRoot()
                    }
                ) {
                    Text("主題模式", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = currentThemeMode == mode,
                                onClick = { ThemePreferences.setThemeMode(appCtx, mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        SettingSwitchRow(
                            title = "Material You 動態色",
                            subtitle = "依系統桌布配色（保留 Phantom 紫為主色調）",
                            checked = dynamicColorEnabled,
                            onCheckedChange = { checked ->
                                ThemePreferences.setDynamicColor(appCtx, checked)
                            }
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("字體大小", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FontScaleMode.entries.forEach { mode ->
                            FilterChip(
                                selected = fontScaleMode == mode,
                                onClick = { ThemePreferences.setFontScaleMode(appCtx, mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SettingSwitchRow(
                        title = "觸覺回饋",
                        subtitle = "切換分頁與長按操作時提供震動回饋；若無震動請檢查系統設定（HyperOS/MIUI：設定→音效與震動→觸感回饋；Samsung：設定→聲音與震動→觸覺回饋）",
                        checked = hapticEnabled,
                        onCheckedChange = { checked ->
                            hapticEnabled = checked
                            PhantomPrefs.setHapticFeedbackEnabled(appCtx, checked)
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SettingSwitchRow(
                        title = "液態玻璃模糊效果",
                        subtitle = "Android 12+ 模糊；更低版本或省電模式自動停用並改為半透明壓色",
                        checked = glassEffectEnabled,
                        onCheckedChange = { checked ->
                            ThemePreferences.setGlassEffectEnabled(appCtx, checked)
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("底部分頁欄顯示模式", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        text = "標準（預設）／縮小（80% 高度與圖示）／自動隱藏（向下捲動隱藏、向上捲動或點底部浮現）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.phantom.checker.util.TabBarDisplayMode.entries.forEach { mode ->
                            FilterChip(
                                selected = tabBarDisplayMode == mode,
                                onClick = {
                                    tabBarDisplayMode = mode
                                    PhantomPrefs.setTabBarDisplayMode(appCtx, mode)
                                },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))

                    Text(
                        text = if (listenerEnabled) "1. 通知存取權限：已開啟 ✓" else "1. 通知存取權限：尚未開啟 ✗",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (listenerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    if (!listenerEnabled) {
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = {
                                openNotificationListenerSettings(ctx)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("前往開啟通知存取權限")
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    Text(
                        text = if (batteryWhitelisted) "2. 電池優化白名單：已豁免 ✓" else "2. 電池優化白名單：尚未豁免 ✗（建議開啟）",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (batteryWhitelisted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    if (!batteryWhitelisted) {
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${appCtx.packageName}")
                                    }
                                    ctx.startActivity(intent)
                                } catch (_: Exception) {
                                    tryLaunchIntents(ctx, oemGuide.intents)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("申請忽略電池最佳化")
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "3. OEM 自啟動權限：請手動確認（系統無法自動偵測）",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${oemGuide.brandTitle}：${oemGuide.steps}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = oemAutostartConfirmed,
                            onCheckedChange = { checked ->
                                oemAutostartConfirmed = checked
                                PhantomPrefs.setOemAutostartConfirmed(appCtx, checked)
                            }
                        )
                        Text(
                            text = "我已確認完成裝置自啟動設定",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    OutlinedButton(
                        onClick = { tryLaunchIntents(ctx, oemGuide.intents) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("開啟系統設定 / 應用程式詳情")
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    Text(
                        text = if (fgServiceRunning) "4. 前景保活服務：運行中 ✓" else "4. 前景保活服務：未偵測到常駐通知 ✗",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (fgServiceRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    if (!fgServiceRunning && persistentStats) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                StatsForegroundService.startOrRefresh(appCtx)
                                fgServiceRunning = isStatsForegroundServiceRunning(appCtx)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("重新啟動前景保活服務")
                        }
                    }
                }
            }
            }

            // ⑤ 權限與連結：權限中心＋連結應用程式中心（Shizuku 收進下方 D4 進階折疊）
            if ("permissions_links" in visibleCardKeys) {
                item(key = "card_permissions_links") {
                    com.phantom.checker.ui.components.SettingsSegmentedGroup {
                        com.phantom.checker.ui.components.SettingsNavigationRow(
                            title = "權限授予中心",
                            subtitle = "集中檢視通知、簡訊、通訊錄、日曆、鬧鐘、電池與 Health Connect 權限",
                            shape = AuroraTokens.segmentedItemShape(0, 2),
                            highlighted = activeHighlightSettingId == "permission_hub",
                            onClick = {
                                UiLayoutPrefs.recordRecentSetting(appCtx, "permission_hub")
                                ctx.startActivity(Intent(ctx, PermissionHubActivity::class.java))
                            }
                        )
                        com.phantom.checker.ui.components.SettingsNavigationRow(
                            title = "連結應用程式中心",
                            subtitle = "日曆、通訊錄、通知、鬧鐘狀態與自填金鑰雲端擴充（GitHub／Discord）",
                            shape = AuroraTokens.segmentedItemShape(1, 2),
                            highlighted = activeHighlightSettingId == "linked_apps_center",
                            onClick = {
                                UiLayoutPrefs.recordRecentSetting(appCtx, "linked_apps_center")
                                ctx.startActivity(Intent(ctx, LinkedAppsActivity::class.java))
                            }
                        )
                    }
                }
            }

            // ⑦ 資料與隱私：白名單／標籤規則／詐騙警示中心分段入口（§5.2）
            if (currentCategory == SettingsCategory.DATA) {
                item(key = "card_data_shortcuts") {
                    com.phantom.checker.ui.components.SettingsSegmentedGroup {
                        com.phantom.checker.ui.components.SettingsNavigationRow(
                            title = "白名單管理",
                            subtitle = "依 App 分組管理信任寄件者與訊息白名單",
                            shape = AuroraTokens.segmentedItemShape(0, 3),
                            highlighted = activeHighlightSettingId == "whitelist_management",
                            onClick = {
                                UiLayoutPrefs.recordRecentSetting(appCtx, "whitelist_management")
                                ctx.startActivity(Intent(ctx, WhitelistManagementActivity::class.java))
                            }
                        )
                        com.phantom.checker.ui.components.SettingsNavigationRow(
                            title = "自訂標籤規則管理",
                            subtitle = "管理自訂標籤、App 分類與條件規則",
                            shape = AuroraTokens.segmentedItemShape(1, 3),
                            highlighted = activeHighlightSettingId == "ai_rules",
                            onClick = {
                                UiLayoutPrefs.recordRecentSetting(appCtx, "ai_rules")
                                ctx.startActivity(Intent(ctx, TagManagementActivity::class.java))
                            }
                        )
                        com.phantom.checker.ui.components.SettingsNavigationRow(
                            title = "詐騙警示中心",
                            subtitle = "檢視高風險詐騙紀錄、趨勢與處置動作",
                            shape = AuroraTokens.segmentedItemShape(2, 3),
                            highlighted = activeHighlightSettingId == "ai_scam_center",
                            onClick = {
                                UiLayoutPrefs.recordRecentSetting(appCtx, "ai_scam_center")
                                ctx.startActivity(Intent(ctx, ScamCenterActivity::class.java))
                            }
                        )
                    }
                }
            }

            // 7. 防詐情報
            if ("intel" in visibleCardKeys) {
            item(key = "card_intel") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FraudIntelCard(
                        expanded = expandIntel || ("intel" in highlightedCardKeys),
                        highlighted = "intel" in highlightedCardKeys,
                        onToggle = {
                            expandIntel = !expandIntel
                            PhantomPrefs.setSettingsCardExpanded(appCtx, "intel", expandIntel)
                        },
                        glassState = glassState,
                        onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                    )
                    ProactiveIntelSettingsCard(
                        expanded = expandIntel || ("intel" in highlightedCardKeys),
                        highlighted = "intel" in highlightedCardKeys,
                        onToggle = {
                            expandIntel = !expandIntel
                            PhantomPrefs.setSettingsCardExpanded(appCtx, "intel", expandIntel)
                        },
                        glassState = glassState,
                        onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                    )
                }
            }
            }

            // 8. 關於
            if ("about" in visibleCardKeys) {
            item(key = "card_about") {
                AboutSettingsCard(
                    expanded = expandAbout || ("about" in highlightedCardKeys),
                    highlighted = "about" in highlightedCardKeys,
                    onToggle = {
                        expandAbout = !expandAbout
                        PhantomPrefs.setSettingsCardExpanded(appCtx, "about", expandAbout)
                    },
                    glassState = glassState
                )
            }
            }

            // D4：各分類頁尾「進階設定」折疊區（預設收合；搜尋命中自動展開；含長按釘選）
            if (advancedItemsInCat.isNotEmpty()) {
                item(key = "cat_advanced_${currentCategory.id}") {
                    val settingsBusVer by PhantomSettingsBus.version.collectAsState()
                    SettingsAdvancedSection(
                        expanded = isAdvancedExpanded,
                        onToggle = { advancedExpandedMap[currentCategory] = !isAdvancedExpanded },
                        count = advancedItemsInCat.size,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            cardBounds["advanced"] = coords.boundsInRoot()
                        }
                    ) {
                        if (currentCategory == SettingsCategory.PERMISSIONS) {
                            ShizukuAdvancedCard(
                                expanded = true,
                                highlighted = activeHighlightSettingId == "shizuku_mode" || ("shizuku" in highlightedCardKeys),
                                onToggle = {
                                    expandShizuku = !expandShizuku
                                    PhantomPrefs.setSettingsCardExpanded(appCtx, "shizuku_advanced", expandShizuku)
                                },
                                glassState = glassState,
                                onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                            )
                        }
                        val nonShizukuAdv = advancedItemsInCat.filterNot { it.id == "shizuku_mode" }
                        if (nonShizukuAdv.isNotEmpty()) {
                            com.phantom.checker.ui.components.SettingsSegmentedGroup {
                                nonShizukuAdv.forEachIndexed { idx, advItem ->
                                    val shape = AuroraTokens.segmentedItemShape(idx, nonShizukuAdv.size)
                                    val isHl = activeHighlightSettingId == advItem.id
                                    val toggleable = SettingsCatalog.isToggleable(advItem.id)
                                    val onLongPin: () -> Unit = {
                                        if (UiLayoutPrefs.isPinned(appCtx, advItem.id)) {
                                            UiLayoutPrefs.removePinned(appCtx, advItem.id)
                                            scope.launch { snackbarHostState.showSnackbar("已取消釘選「${advItem.title}」") }
                                        } else {
                                            val added = UiLayoutPrefs.addPinned(appCtx, advItem.id)
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    if (added) "已釘選「${advItem.title}」到設定首頁"
                                                    else "釘選已達上限（${UiLayoutPrefs.MAX_PINNED} 項），請先取消一項"
                                                )
                                            }
                                        }
                                    }
                                    if (toggleable) {
                                        var checked by remember(advItem.id, settingsBusVer) {
                                            mutableStateOf(SettingsCatalog.getBooleanState(appCtx, advItem.id) == true)
                                        }
                                        com.phantom.checker.ui.components.SettingsSwitchRow(
                                            title = advItem.title,
                                            subtitle = advItem.description,
                                            checked = checked,
                                            onCheckedChange = { next ->
                                                checked = next
                                                SettingsCatalog.applyBooleanSetting(appCtx, advItem.id, next)
                                                UiLayoutPrefs.recordRecentSetting(appCtx, advItem.id)
                                            },
                                            shape = shape,
                                            highlighted = isHl,
                                            onLongClick = onLongPin
                                        )
                                    } else {
                                        val numSpec = SettingsCatalog.findNumericSpec(advItem.id)
                                        val valueLabel = numSpec?.let { spec ->
                                            SettingsCatalog.getNumericValue(appCtx, advItem.id)?.let { spec.formatValue(it) }
                                        }
                                        if (valueLabel != null) {
                                            com.phantom.checker.ui.components.SettingsOptionRow(
                                                title = advItem.title,
                                                valueText = valueLabel,
                                                subtitle = advItem.description,
                                                shape = shape,
                                                highlighted = isHl,
                                                onClick = { navigateToSettingItem(advItem) },
                                                onLongClick = onLongPin
                                            )
                                        } else {
                                            com.phantom.checker.ui.components.SettingsNavigationRow(
                                                title = advItem.title,
                                                subtitle = advItem.description,
                                                shape = shape,
                                                highlighted = isHl,
                                                onClick = { navigateToSettingItem(advItem) },
                                                onLongClick = onLongPin
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
                    }
                }
            }
        }

        val totalTourSteps = SettingsTourStep.entries.size
        if (tourStepIndex in 1..totalTourSteps) {
            SettingsTourOverlay(
                stepIndex = tourStepIndex,
                cardBounds = cardBounds,
                glassState = glassState,
                onNext = {
                    if (tourStepIndex < totalTourSteps) {
                        tourStepIndex += 1
                    } else {
                        tourStepIndex = 0
                        PhantomPrefs.setSettingsTourCompleted(appCtx, true)
                    }
                },
                onSkip = {
                    tourStepIndex = 0
                    PhantomPrefs.setSettingsTourCompleted(appCtx, true)
                }
            )
        }
    }
}

@Composable
private fun FraudIntelCard(
    expanded: Boolean,
    onToggle: () -> Unit,
    glassState: HazeState,
    highlighted: Boolean = false,
    onShowMessage: (String) -> Unit
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()

    var intelEnabled by remember { mutableStateOf(PhantomPrefs.isIntelEnabled(appCtx)) }
    val intelBusVer by PhantomSettingsBus.version.collectAsState()
    LaunchedEffect(intelBusVer) {
        if (intelBusVer > 0L) {
            intelEnabled = PhantomPrefs.isIntelEnabled(appCtx)
        }
    }
    var fraudTwEnabled by remember { mutableStateOf(PhantomPrefs.isIntelFraudTwEnabled(appCtx)) }
    var safeBrowsingEnabled by remember { mutableStateOf(PhantomPrefs.isIntelSafeBrowsingEnabled(appCtx)) }
    var phishTankEnabled by remember { mutableStateOf(PhantomPrefs.isIntelPhishTankEnabled(appCtx)) }
    var chainabuseEnabled by remember { mutableStateOf(PhantomPrefs.isIntelChainabuseEnabled(appCtx)) }

    var sbKeySource by remember { mutableStateOf(IntelClient.getSafeBrowsingKeySource(appCtx)) }
    var sbKeyInput by remember { mutableStateOf("") }
    var ptKeyConfigured by remember { mutableStateOf(IntelClient.getPhishTankAppKey(appCtx) != null) }
    var ptKeyInput by remember { mutableStateOf("") }
    var caKeyConfigured by remember { mutableStateOf(IntelClient.isChainabuseKeyConfigured(appCtx)) }
    var caKeyInput by remember { mutableStateOf("") }

    val testResults = remember { mutableStateMapOf<IntelClient.IntelProvider, IntelClient.ProviderTestResult>() }
    var testingProvider by remember { mutableStateOf<IntelClient.IntelProvider?>(null) }

    CollapsibleSettingsCard(
        title = "防詐情報",
        expanded = expanded,
        onToggle = onToggle,
        glassState = glassState,
        highlighted = highlighted
    ) {
        SettingSwitchRow(
            title = "啟用公開防詐情報預查",
            subtitle = "AI 分析前先以 URL / 加密貨幣地址比對公開情報（不傳送訊息全文）；命中直接警示且不消耗每日 AI 額度",
            checked = intelEnabled,
            onCheckedChange = { checked ->
                intelEnabled = checked
                PhantomPrefs.setIntelEnabled(appCtx, checked)
            }
        )

        if (intelEnabled) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SettingSwitchRow(
                title = "165 通報（fraud.tw）",
                subtitle = "查詢台灣 165 涉詐網域通報資料庫（免金鑰，失敗時自動靜默降級）",
                checked = fraudTwEnabled,
                onCheckedChange = { checked ->
                    fraudTwEnabled = checked
                    PhantomPrefs.setIntelFraudTwEnabled(appCtx, checked)
                }
            )

            Spacer(Modifier.height(8.dp))

            SettingSwitchRow(
                title = "Google Safe Browsing v4",
                subtitle = when (sbKeySource) {
                    ApiKeySource.ENCRYPTED_PREFS -> "Key 狀態：已於 App 加密儲存"
                    else -> "Key 狀態：未設定（需 API key 方可使用）"
                },
                checked = safeBrowsingEnabled,
                onCheckedChange = { checked ->
                    safeBrowsingEnabled = checked
                    PhantomPrefs.setIntelSafeBrowsingEnabled(appCtx, checked)
                }
            )
            IntelApiKeyEditor(
                label = "Safe Browsing API Key",
                placeholder = if (sbKeySource == ApiKeySource.ENCRYPTED_PREFS) "已加密儲存（輸入新 key 可覆蓋）" else "貼上 Google Cloud Safe Browsing Key",
                helperText = "至 Google Cloud 啟用 Safe Browsing API 後建立 API key（加密儲存於本機）。",
                value = sbKeyInput,
                onValueChange = { sbKeyInput = it },
                hasSavedKey = sbKeySource == ApiKeySource.ENCRYPTED_PREFS,
                onSave = {
                    IntelClient.setSafeBrowsingApiKey(appCtx, sbKeyInput)
                    sbKeyInput = ""
                    sbKeySource = IntelClient.getSafeBrowsingKeySource(appCtx)
                    onShowMessage("已加密儲存 Safe Browsing API Key")
                },
                onClear = {
                    IntelClient.setSafeBrowsingApiKey(appCtx, "")
                    sbKeyInput = ""
                    sbKeySource = IntelClient.getSafeBrowsingKeySource(appCtx)
                    onShowMessage("已清除 App 內儲存的 Safe Browsing Key")
                }
            )

            Spacer(Modifier.height(8.dp))

            SettingSwitchRow(
                title = "PhishTank（可選）",
                subtitle = if (ptKeyConfigured) {
                    "Key 狀態：已設定 app_key（加密儲存）"
                } else {
                    "未填 app_key，可能受較嚴格限流（預設關閉）"
                },
                checked = phishTankEnabled,
                onCheckedChange = { checked ->
                    phishTankEnabled = checked
                    PhantomPrefs.setIntelPhishTankEnabled(appCtx, checked)
                }
            )
            IntelApiKeyEditor(
                label = "PhishTank app_key（選填）",
                placeholder = if (ptKeyConfigured) "已加密儲存（輸入新 key 可覆蓋）" else "選填：至 phishtank.net 註冊應用取得",
                helperText = "未填亦可查詢，但限流較嚴格；填入後加密儲存。",
                value = ptKeyInput,
                onValueChange = { ptKeyInput = it },
                hasSavedKey = ptKeyConfigured,
                onSave = {
                    IntelClient.setPhishTankAppKey(appCtx, ptKeyInput)
                    ptKeyInput = ""
                    ptKeyConfigured = IntelClient.getPhishTankAppKey(appCtx) != null
                    onShowMessage("已加密儲存 PhishTank app_key")
                },
                onClear = {
                    IntelClient.setPhishTankAppKey(appCtx, "")
                    ptKeyInput = ""
                    ptKeyConfigured = false
                    onShowMessage("已清除 PhishTank app_key")
                }
            )

            Spacer(Modifier.height(8.dp))

            SettingSwitchRow(
                title = "Chainabuse 鏈上地址（可選）",
                subtitle = if (caKeyConfigured) {
                    "Key 狀態：已設定 API key（加密儲存）｜標準 key 每月限 10 次呼叫（72h 快取緩解）"
                } else {
                    "Key 狀態：未設定（需至 chainabuse.com Profile → Settings 產生 API key；標準 key 每月僅 10 次呼叫）"
                },
                checked = chainabuseEnabled,
                onCheckedChange = { checked ->
                    chainabuseEnabled = checked
                    PhantomPrefs.setIntelChainabuseEnabled(appCtx, checked)
                }
            )
            IntelApiKeyEditor(
                label = "Chainabuse API Key",
                placeholder = if (caKeyConfigured) "已加密儲存（輸入新 key 可覆蓋）" else "貼上 Chainabuse API Key",
                helperText = "官方限制：標準 key 每月僅 10 次呼叫（每次最多 50 筆報告），已搭配 72h 快取緩解。",
                value = caKeyInput,
                onValueChange = { caKeyInput = it },
                hasSavedKey = caKeyConfigured,
                onSave = {
                    IntelClient.setChainabuseApiKey(appCtx, caKeyInput)
                    caKeyInput = ""
                    caKeyConfigured = IntelClient.isChainabuseKeyConfigured(appCtx)
                    onShowMessage("已加密儲存 Chainabuse API Key")
                },
                onClear = {
                    IntelClient.setChainabuseApiKey(appCtx, "")
                    caKeyInput = ""
                    caKeyConfigured = false
                    onShowMessage("已清除 Chainabuse API Key")
                }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(
                text = "逐源連線測試",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))

            IntelClient.IntelProvider.entries.forEach { provider ->
                val isTesting = testingProvider == provider
                val result = testResults[provider]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(provider.displayName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        if (result != null) {
                            Text(
                                text = result.message,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (result.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        enabled = testingProvider == null,
                        onClick = {
                            scope.launch {
                                testingProvider = provider
                                val res = IntelClient.testProvider(appCtx, provider)
                                testResults[provider] = res
                                testingProvider = null
                            }
                        }
                    ) {
                        Text(if (isTesting) "測試中…" else "測試連線")
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            OutlinedButton(
                onClick = {
                    scope.launch {
                        IntelClient.clearCache(appCtx)
                        onShowMessage("已清除 72 小時情報快取")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("清除情報快取（72h）")
            }
        }
    }
}

@Composable
private fun IntelApiKeyEditor(
    label: String,
    placeholder: String,
    helperText: String,
    value: String,
    onValueChange: (String) -> Unit,
    hasSavedKey: Boolean,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            Button(
                enabled = value.isNotBlank(),
                onClick = onSave
            ) {
                Text("儲存")
            }
            if (hasSavedKey) {
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onClear) {
                    Text("清除")
                }
            }
        }
        Text(
            text = helperText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun ShizukuAdvancedCard(
    expanded: Boolean,
    onToggle: () -> Unit,
    glassState: HazeState,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onShowMessage: (String) -> Unit
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val shizukuState by ShizukuManager.state.collectAsState()
    var watchdogEnabled by remember { mutableStateOf(PhantomPrefs.isShizukuWatchdogEnabled(appCtx)) }
    var suspendedApps by remember { mutableStateOf(PhantomPrefs.getShizukuSuspendedApps(appCtx)) }
    var diagItems by remember { mutableStateOf<List<ShizukuDiagItem>>(emptyList()) }
    var isBusy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ShizukuManager.init(appCtx)
    }

    val statusText = when {
        !shizukuState.isInstalled -> "未安裝 Shizuku（目前停用，不影響一般保活）"
        !shizukuState.isBinderAlive -> "Shizuku 服務未啟動（重開機或斷線後需重新啟動 Shizuku）"
        !shizukuState.isPermissionGranted -> "Shizuku 已運行，尚未授權幻訊"
        else -> "已連線授權（UID=${shizukuState.uid}）"
    }

    CollapsibleSettingsCard(
        title = "Shizuku 進階",
        expanded = expanded,
        onToggle = onToggle,
        glassState = glassState,
        modifier = modifier,
        highlighted = highlighted
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (shizukuState.isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Shizuku 為選用進階能力；即使未啟動或重開機斷線，一般保活機制仍正常運作。若被系統「強行停止 (force-stop)」，屬 OS 硬邊界，任何 App 皆無法自行復活。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (shizukuState.isBinderAlive && !shizukuState.isPermissionGranted) {
                Button(
                    onClick = { ShizukuManager.requestPermission(appCtx) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("請求 Shizuku 授權")
                }
            }
            OutlinedButton(
                onClick = {
                    ShizukuManager.refreshState(appCtx)
                    suspendedApps = PhantomPrefs.getShizukuSuspendedApps(appCtx)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("重新檢查連線")
            }
        }

        if (shizukuState.isReady) {
            Spacer(Modifier.height(10.dp))
            Button(
                enabled = !isBusy,
                onClick = {
                    scope.launch {
                        isBusy = true
                        val res = ShizukuKeepAlive.applyKeepAliveEnhancements(appCtx)
                        diagItems = res
                        isBusy = false
                        val okCount = res.count { it.ok }
                        onShowMessage("Shizuku 保活強化完成：$okCount/${res.size} 項成功")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isBusy) "套用中…" else "一鍵套用保活強化（Doze / AppOps / 權限自癒）")
            }

            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                enabled = !isBusy,
                onClick = {
                    scope.launch {
                        isBusy = true
                        diagItems = ShizukuKeepAlive.runDiagnostics(appCtx)
                        isBusy = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("執行保活診斷總覽")
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Shizuku 跨行程看門狗", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (shizukuState.isWatchdogActive) "運作中（每 60 秒檢查前景服務）" else "未啟動",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FluidSwitch(
                    checked = watchdogEnabled,
                    onCheckedChange = { checked ->
                        watchdogEnabled = checked
                        PhantomPrefs.setShizukuWatchdogEnabled(appCtx, checked)
                        scope.launch {
                            if (checked) {
                                ShizukuManager.startWatchdog(appCtx)
                            } else {
                                ShizukuManager.stopWatchdog(appCtx)
                            }
                        }
                    }
                )
            }
        }

        if (diagItems.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                text = "診斷結果",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            diagItems.forEach { item ->
                Text(
                    text = "${if (item.ok) "✓" else "✗"} ${item.label}：${item.detail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }

        if (suspendedApps.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                text = "已凍結（pm suspend）App 清單",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            suspendedApps.forEach { pkg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pkg,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        enabled = shizukuState.isReady && !isBusy,
                        onClick = {
                            scope.launch {
                                isBusy = true
                                val res = ShizukuKeepAlive.unsuspendTargetApp(appCtx, pkg)
                                suspendedApps = PhantomPrefs.getShizukuSuspendedApps(appCtx)
                                isBusy = false
                                onShowMessage(if (res.isSuccess) "已解除凍結 $pkg" else "解除失敗：${res.output}")
                            }
                        }
                    ) {
                        Text("解除凍結")
                    }
                }
            }
        }
    }
}

/** #390 Phase D 主動情報設定區塊：總開關（首次開啟完整說明）、萃取開關、查詢頻率、關注清單入口、立即查詢預覽 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProactiveIntelSettingsCard(
    expanded: Boolean,
    highlighted: Boolean,
    onToggle: () -> Unit,
    glassState: HazeState,
    onShowMessage: (String) -> Unit
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(PhantomPrefs.isProactiveIntelEnabled(appCtx)) }
    var extractEnabled by remember { mutableStateOf(PhantomPrefs.isProactiveExtractEnabled(appCtx)) }
    var freqHours by remember { mutableIntStateOf(PhantomPrefs.getProactiveIntelFreqHours(appCtx)) }
    var showConsentDialog by remember { mutableStateOf(false) }
    var showWatchTopicDialog by remember { mutableStateOf(false) }
    var isQueryingNow by remember { mutableStateOf(false) }
    var previewStatus by remember { mutableStateOf<String?>(null) }
    var previewDigest by remember { mutableStateOf<String?>(null) }

    CollapsibleSettingsCard(
        title = "主動情報中樞",
        collapsedSummary = if (enabled) "已開啟｜每 ${freqHours} 小時查證關注主題最新動態" else "預設關閉｜零 AI 呼叫、零外部查詢",
        expanded = expanded,
        onToggle = onToggle,
        glassState = glassState,
        highlighted = highlighted
    ) {
        SettingSwitchRow(
            title = "啟用主動情報查詢",
            subtitle = "僅外送關注清單中的查詢詞（queryHint），絕不送原始訊息、聯絡人或敏感記憶。",
            checked = enabled,
            onCheckedChange = { wantEnable ->
                if (wantEnable && !PhantomPrefs.hasSeenProactiveIntelConsent(appCtx)) {
                    showConsentDialog = true
                } else {
                    enabled = wantEnable
                    PhantomPrefs.setProactiveIntelEnabled(appCtx, wantEnable)
                    com.phantom.checker.service.WatchTopicEngine.syncSchedule(appCtx)
                    com.phantom.checker.service.ProactiveIntelScheduler.syncSchedule(appCtx)
                    PhantomSettingsBus.notifyChanged()
                }
            }
        )
        Text(
            text = "路由建議（二選一）：主力使用 Mistral 時，可於「API 金鑰設定」將「主動情報萃取與摘要」用途單獨綁定一個 Gemini 3+ 設定檔（原生 Google Search grounding），或填寫 BYOK 外部搜尋端點。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
        )
        SettingSwitchRow(
            title = "每日自動萃取關注主題",
            subtitle = "每日至多一次從近期非敏感素材萃取最多 5 個待確認候選（絕不直接推播；敏感記憶硬排除）。",
            checked = extractEnabled,
            onCheckedChange = { v ->
                extractEnabled = v
                PhantomPrefs.setProactiveExtractEnabled(appCtx, v)
                com.phantom.checker.service.WatchTopicEngine.syncSchedule(appCtx)
                PhantomSettingsBus.notifyChanged()
            }
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "背景查詢頻率（僅連網且電量充足時執行）",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            listOf(6 to "每 6 小時", 12 to "每 12 小時", 24 to "每 24 小時").forEach { (h, label) ->
                FilterChip(
                    selected = freqHours == h,
                    onClick = {
                        freqHours = h
                        PhantomPrefs.setProactiveIntelFreqHours(appCtx, h)
                        com.phantom.checker.service.ProactiveIntelScheduler.syncSchedule(appCtx)
                        PhantomSettingsBus.notifyChanged()
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { showWatchTopicDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Text("管理關注清單")
            }
            Button(
                onClick = {
                    if (!isQueryingNow) {
                        isQueryingNow = true
                        previewStatus = "查詢中…"
                        previewDigest = null
                        scope.launch {
                            val res = com.phantom.checker.service.DigestComposer.runIntelCycle(appCtx, forceManual = true)
                            previewStatus = res.statusMessage
                            previewDigest = res.digestMarkdown
                            isQueryingNow = false
                        }
                    }
                },
                enabled = !isQueryingNow,
                modifier = Modifier.weight(1f)
            ) {
                if (isQueryingNow) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text("查詢中…")
                } else {
                    Text("立即查詢")
                }
            }
        }
        previewStatus?.let { status ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        previewDigest?.let { md ->
            Spacer(Modifier.height(6.dp))
            ReleaseNotesPreviewBox(
                title = "主動情報預覽 Digest",
                releaseNotes = md
            )
        }
    }

    if (showConsentDialog) {
        AlertDialog(
            onDismissRequest = { showConsentDialog = false },
            title = { Text("開啟主動情報中樞前說明") },
            text = {
                Text(
                    "開啟後，本 App 會將您「關注主題清單」中的查詢關鍵字字串（queryHint）送往您自行設定的 AI provider 查詢最新動態。\n\n" +
                        "• 外送最小化：僅送出主題查詢字串，絕不傳送原始通知全文、聯絡人或敏感記憶。\n" +
                        "• 省 Token 模式開啟時將自動暫停所有萃取與查詢。\n" +
                        "• 每日自動萃取的候選僅留存本機待確認，不會直接推播。"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        PhantomPrefs.setSeenProactiveIntelConsent(appCtx, true)
                        enabled = true
                        PhantomPrefs.setProactiveIntelEnabled(appCtx, true)
                        com.phantom.checker.service.WatchTopicEngine.syncSchedule(appCtx)
                        com.phantom.checker.service.ProactiveIntelScheduler.syncSchedule(appCtx)
                        PhantomSettingsBus.notifyChanged()
                        showConsentDialog = false
                        onShowMessage("已啟用主動情報中樞")
                    }
                ) { Text("了解並開啟") }
            },
            dismissButton = {
                TextButton(onClick = { showConsentDialog = false }) { Text("暫不開啟") }
            }
        )
    }

    if (showWatchTopicDialog) {
        com.phantom.checker.ui.memory.WatchTopicManagerDialog(
            onDismiss = { showWatchTopicDialog = false },
            onShowMessage = onShowMessage
        )
    }
}

/** #391 捲動式 Markdown 更新內容預覽區塊（關於卡與自動更新彈窗共用） */
@Composable
internal fun ReleaseNotesPreviewBox(
    title: String,
    releaseNotes: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                if (releaseNotes.isBlank()) {
                    Text(
                        text = "此版本無更新說明",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    PhantomMarkdownText(
                        markdown = releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutSettingsCard(
    expanded: Boolean,
    onToggle: () -> Unit,
    glassState: HazeState,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val repoUrl = "https://github.com/eric11yothelast-Hwanshen/PhantomCheck"
    val versionDisplay = remember(appCtx) {
        try {
            val pm = appCtx.packageManager
            val pkgInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    appCtx.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(appCtx.packageName, 0)
            }
            val verName = pkgInfo.versionName.orEmpty()
            val verCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }
            if (verName.isNotBlank()) "$verName ($verCode)" else "($verCode)"
        } catch (_: Exception) {
            "—"
        }
    }

    val scope = rememberCoroutineScope()
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var activeDownloadId by remember { mutableStateOf(PhantomPrefs.getActiveApkDownloadId(appCtx).takeIf { it > 0L }) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgressPct by remember { mutableStateOf<Int?>(null) }
    var downloadStatusText by remember { mutableStateOf<String?>(null) }
    var isDownloadError by remember { mutableStateOf(false) }
    var showUnknownSourceDialog by remember { mutableStateOf(false) }
    var pendingInstallAfterUnknownSourceGrant by remember { mutableStateOf(false) }

    fun attemptInstall(file: File, isAutoTrigger: Boolean = false) {
        if (!file.exists() || file.length() <= 0L) {
            downloadedApkFile = null
            isDownloading = false
            isDownloadError = true
            PhantomPrefs.clearActiveApkDownload(appCtx)
            downloadStatusText = "更新檔不存在或已被移除，請點擊重新下載。"
            return
        }
        if (!AppUpdateHelper.canRequestPackageInstallsCompat(ctx)) {
            pendingInstallAfterUnknownSourceGrant = true
            showUnknownSourceDialog = true
            isDownloadError = false
            downloadStatusText = "下載完成：請先允許「安裝未知應用程式」，返回後將自動開啟安裝頁，或點擊「安裝更新」。"
            return
        }
        pendingInstallAfterUnknownSourceGrant = false
        if (isAutoTrigger) {
            PhantomPrefs.setActiveApkAutoInstallLaunched(appCtx, true)
        }
        AppUpdateHelper.launchApkInstaller(ctx, file).onSuccess {
            isDownloadError = false
            downloadStatusText = "下載完成，已啟動系統安裝器（若未完成安裝，可隨時點擊上方按鈕安裝）。"
        }.onFailure { err ->
            isDownloadError = true
            downloadStatusText = err.message ?: "無法開啟系統安裝器，請點擊重試或前往檔案管理員手動安裝。"
        }
    }

    suspend fun syncDownloadState(targetVersion: String, allowAutoInstall: Boolean) {
        val state = withContext(Dispatchers.IO) {
            AppUpdateHelper.inspectApkDownloadState(appCtx, targetVersion)
        }
        when (state) {
            is AppUpdateHelper.ApkDownloadState.Idle -> {
                isDownloading = false
                activeDownloadId = null
                downloadedApkFile = null
                downloadProgressPct = null
            }
            is AppUpdateHelper.ApkDownloadState.Downloading -> {
                isDownloading = true
                isDownloadError = false
                activeDownloadId = state.downloadId
                downloadedApkFile = state.file
                downloadProgressPct = state.progressPercent
                val pctSuffix = state.progressPercent?.let { "（$it%）" }.orEmpty()
                downloadStatusText = "正在下載 ${state.file.name}$pctSuffix…"
            }
            is AppUpdateHelper.ApkDownloadState.ReadyToInstall -> {
                val wasDownloading = isDownloading
                isDownloading = false
                isDownloadError = false
                activeDownloadId = null
                downloadedApkFile = state.file
                downloadProgressPct = 100
                val alreadyAutoLaunched = PhantomPrefs.isActiveApkAutoInstallLaunched(appCtx)
                if (allowAutoInstall && (wasDownloading || !alreadyAutoLaunched) && state.downloadId > 0L && !alreadyAutoLaunched) {
                    downloadStatusText = "下載完成：${state.file.name}，正在啟動安裝頁面…"
                    attemptInstall(state.file, isAutoTrigger = true)
                } else {
                    downloadStatusText = "下載完成，點擊「安裝更新（v${state.version}）」即可安裝。"
                }
            }
            is AppUpdateHelper.ApkDownloadState.Failed -> {
                isDownloading = false
                isDownloadError = true
                activeDownloadId = null
                downloadedApkFile = null
                downloadProgressPct = null
                downloadStatusText = state.reasonText
            }
        }
    }

    // 初始化：若有進行中或已完成的持久化下載，或展開「關於」卡時，自動對齊最新發佈與本地下載狀態
    LaunchedEffect(Unit) {
        val persistedVer = PhantomPrefs.getActiveApkDownloadVersion(appCtx)
        if (persistedVer.isNotBlank()) {
            syncDownloadState(persistedVer, allowAutoInstall = false)
            if (updateResult == null && !isCheckingUpdate) {
                isCheckingUpdate = true
                updateResult = AppUpdateHelper.checkLatestRelease(appCtx)
                isCheckingUpdate = false
            }
        }
    }

    LaunchedEffect(updateResult) {
        when (val res = updateResult) {
            is UpdateCheckResult.UpdateAvailable -> {
                syncDownloadState(res.latestVersion, allowAutoInstall = false)
            }
            is UpdateCheckResult.UpToDate -> {
                withContext(Dispatchers.IO) {
                    AppUpdateHelper.cleanupStaleApkDownloads(appCtx, keepVersion = null)
                }
                isDownloading = false
                activeDownloadId = null
                downloadedApkFile = null
            }
            else -> Unit
        }
    }

    // 下載中：IO 輪詢 DownloadManager.query（~1s 間隔，UI 可見時），確保不漏接廣播且即時彈出安裝頁
    LaunchedEffect(isDownloading, activeDownloadId, updateResult) {
        val targetVer = (updateResult as? UpdateCheckResult.UpdateAvailable)?.latestVersion
            ?: PhantomPrefs.getActiveApkDownloadVersion(appCtx)
        if (!isDownloading || targetVer.isBlank()) return@LaunchedEffect
        while (isDownloading) {
            syncDownloadState(targetVer, allowAutoInstall = true)
            if (!isDownloading) break
            kotlinx.coroutines.delay(1000L)
        }
    }

    // 保留 ACTION_DOWNLOAD_COMPLETE 作為即時觸發輔助，統一交由 syncDownloadState 查驗
    DisposableEffect(activeDownloadId) {
        val dlId = activeDownloadId
        if (dlId == null) {
            onDispose { }
        } else {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                    val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (completedId != dlId) return
                    val targetVer = (updateResult as? UpdateCheckResult.UpdateAvailable)?.latestVersion
                        ?: PhantomPrefs.getActiveApkDownloadVersion(appCtx)
                    if (targetVer.isNotBlank()) {
                        scope.launch {
                            syncDownloadState(targetVer, allowAutoInstall = true)
                        }
                    }
                }
            }
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            onDispose {
                try {
                    ctx.unregisterReceiver(receiver)
                } catch (_: Exception) {
                }
            }
        }
    }

    // ON_RESUME 重查：使用者切走再回來、下載於背景完成、或剛從「安裝未知應用程式」設定頁授權返回
    val lifecycleOwner = ctx as? LifecycleOwner
    DisposableEffect(lifecycleOwner, updateResult) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val targetVer = (updateResult as? UpdateCheckResult.UpdateAvailable)?.latestVersion
                    ?: PhantomPrefs.getActiveApkDownloadVersion(appCtx)
                if (targetVer.isNotBlank()) {
                    scope.launch {
                        syncDownloadState(targetVer, allowAutoInstall = false)
                        val ready = downloadedApkFile
                        if (pendingInstallAfterUnknownSourceGrant &&
                            ready != null &&
                            ready.exists() &&
                            ready.length() > 0L &&
                            AppUpdateHelper.canRequestPackageInstallsCompat(ctx)
                        ) {
                            attemptInstall(ready, isAutoTrigger = true)
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

    if (showUnknownSourceDialog) {
        AlertDialog(
            onDismissRequest = { showUnknownSourceDialog = false },
            title = { Text("需要授權「安裝未知應用程式」") },
            text = {
                Text("Android 8+ 需要您允許「幻訊」安裝未知來源應用程式，才能啟動系統 APK 安裝器。\n\n點擊「前往授權」開啟開關後，返回此頁即可自動或點擊啟動安裝（小米 HyperOS／Samsung One UI／OPPO ColorOS 皆適用）。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnknownSourceDialog = false
                        AppUpdateHelper.openUnknownAppSourcesSettings(ctx)
                    }
                ) {
                    Text("前往授權")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnknownSourceDialog = false }) {
                    Text("稍後")
                }
            }
        )
    }

    CollapsibleSettingsCard(
        title = "關於",
        expanded = expanded,
        onToggle = onToggle,
        glassState = glassState,
        modifier = modifier,
        highlighted = highlighted
    ) {
        Text(
            text = "幻訊 PhantomCheck",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "版本：$versionDisplay",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "作者：幻神（Hwanshen）",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "本 App 只在本機被動讀取通知備份，不上傳訊息內容。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        var updateAutoCheck by remember { mutableStateOf(PhantomPrefs.isUpdateAutoCheckEnabled(appCtx)) }
        val updateBusVer by PhantomSettingsBus.version.collectAsState()
        LaunchedEffect(updateBusVer) {
            if (updateBusVer > 0L) {
                updateAutoCheck = PhantomPrefs.isUpdateAutoCheckEnabled(appCtx)
            }
        }
        SettingSwitchRow(
            title = "自動檢查更新",
            subtitle = "每次開啟 App 自動檢查更新；選擇「稍後更新」將於設定間隔後再次提醒。",
            checked = updateAutoCheck,
            onCheckedChange = {
                updateAutoCheck = it
                PhantomPrefs.setUpdateAutoCheckEnabled(appCtx, it)
                PhantomSettingsBus.notifyChanged()
            }
        )
        var updateSnoozeMin by remember { mutableStateOf(PhantomPrefs.getUpdateSnoozeMinutes(appCtx)) }
        var chatTimestampOn by remember { mutableStateOf(PhantomPrefs.isChatTimestampEnabled(appCtx)) }
        LaunchedEffect(updateBusVer) {
            if (updateBusVer > 0L) {
                updateSnoozeMin = PhantomPrefs.getUpdateSnoozeMinutes(appCtx)
                chatTimestampOn = PhantomPrefs.isChatTimestampEnabled(appCtx)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "「稍後更新」延後提醒間隔",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(30 to "30 分鐘", 60 to "1 小時", 240 to "4 小時").forEach { (mins, label) ->
                FilterChip(
                    selected = updateSnoozeMin == mins,
                    onClick = {
                        updateSnoozeMin = mins
                        PhantomPrefs.setUpdateSnoozeMinutes(appCtx, mins)
                        PhantomSettingsBus.notifyChanged()
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        SettingSwitchRow(
            title = "聊天室訊息右下角顯示時間戳",
            subtitle = "在 AI 對話與主動訊息氣泡右下角顯示 HH:mm 發送時間（預設開啟）",
            checked = chatTimestampOn,
            onCheckedChange = {
                chatTimestampOn = it
                PhantomPrefs.setChatTimestampEnabled(appCtx, it)
                PhantomSettingsBus.notifyChanged()
            }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                if (!isCheckingUpdate) {
                    isCheckingUpdate = true
                    if (!isDownloading) {
                        downloadStatusText = null
                        isDownloadError = false
                    }
                    scope.launch {
                        updateResult = AppUpdateHelper.checkLatestRelease(ctx)
                        isCheckingUpdate = false
                    }
                }
            },
            enabled = !isCheckingUpdate,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isCheckingUpdate) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("檢查更新中…")
            } else {
                Text("檢查更新")
            }
        }
        when (val res = updateResult) {
            is UpdateCheckResult.UpToDate -> {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "已是最新版本（${res.currentVersion}）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                val dateSuffix = res.publishedDate.takeIf { it.isNotBlank() }?.let { "／$it" }.orEmpty()
                ReleaseNotesPreviewBox(
                    title = "本版更新內容（v${res.currentVersion}$dateSuffix）",
                    releaseNotes = res.releaseNotes
                )
            }
            is UpdateCheckResult.UpdateAvailable -> {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "最新版本 ${res.latestVersion}（${res.publishedDate}）",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                ReleaseNotesPreviewBox(
                    title = "最新版本更新內容（v${res.latestVersion}／${res.publishedDate}）",
                    releaseNotes = res.releaseNotes
                )
                Spacer(Modifier.height(8.dp))
                if (res.apkDownloadUrl != null) {
                    val readyFile = downloadedApkFile
                    if (readyFile != null && readyFile.exists() && readyFile.length() > 0L && !isDownloading) {
                        Button(
                            onClick = { attemptInstall(readyFile, isAutoTrigger = false) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("下載完成，點擊安裝（v${res.latestVersion}）")
                        }
                    } else {
                        Button(
                            onClick = {
                                if (!isDownloading) {
                                    scope.launch {
                                        isDownloadError = false
                                        val enqueued = withContext(Dispatchers.IO) {
                                            AppUpdateHelper.enqueueApkDownload(
                                                ctx = appCtx,
                                                version = res.latestVersion,
                                                downloadUrl = res.apkDownloadUrl
                                            )
                                        }
                                        if (enqueued != null) {
                                            activeDownloadId = enqueued.first
                                            downloadedApkFile = enqueued.second
                                            isDownloading = true
                                            downloadProgressPct = null
                                            downloadStatusText = "正在下載 app-release-${res.latestVersion}.apk…"
                                        } else {
                                            isDownloadError = true
                                            downloadStatusText = "無法啟動系統下載管理員，請重試或前往 GitHub 下載。"
                                        }
                                    }
                                }
                            },
                            enabled = !isDownloading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                val pctLabel = downloadProgressPct?.let { "（$it%）" }.orEmpty()
                                Text("下載中$pctLabel…")
                            } else if (isDownloadError) {
                                Text("重新下載（v${res.latestVersion}）")
                            } else {
                                Text("下載更新")
                            }
                        }
                    }
                } else {
                    Text(
                        text = "此發佈版本未附帶 APK 資產，請前往 GitHub 下載。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(res.htmlUrl)))
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("前往 GitHub")
                    }
                }
            }
            is UpdateCheckResult.Error -> {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = res.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        try {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppUpdateHelper.REPO_RELEASES_URL)))
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("前往 GitHub")
                }
            }
            null -> Unit
        }
        downloadStatusText?.let { msg ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (isDownloadError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl)))
                } catch (_: Exception) {
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("GitHub 倉庫：eric11yothelast-Hwanshen/PhantomCheck")
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(
            text = "第三方元件",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Jetpack Compose / Material 3、Room、WorkManager、Retrofit、OkHttp、Gson、Haze、Shizuku API",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CollapsibleSettingsCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    glassState: HazeState,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    collapsedSummary: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    PhantomGlassCard(
        glassState = glassState,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (highlighted) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                } else {
                    Modifier
                }
            ),
        cornerRadius = 20.dp,
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .pressableScale(0.99f)
                .clickable(onClick = onToggle),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (highlighted) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "符合",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (!expanded && !collapsedSummary.isNullOrBlank()) {
                    Text(
                        text = collapsedSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            PhantomChevron(
 expanded = expanded,
                contentDescription = if (expanded) "收合" else "展開",
                modifier = Modifier.size(24.dp)
            )
        }
        AnimatedVisibility(
 visible = expanded,
 enter = PhantomMotion.ExpandEnter,
 exit = PhantomMotion.ExpandExit
 ) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        FluidSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun FilterRuleRow(
    rule: FilterRule,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val actionLabel = when (rule.action) {
        FilterRule.ACTION_DROP -> "不記錄"
        FilterRule.ACTION_SCAM -> "標記詐騙警示"
        else -> "標記已過濾"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.keyword,
                style = MaterialTheme.typography.bodyMedium,
                color = if (rule.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${if (rule.packageName.isNullOrBlank()) "全部 App" else rule.packageName}・$actionLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FluidSwitch(checked = rule.isActive, onCheckedChange = onToggleActive)
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "刪除關鍵字",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppMutePickerDialog(
    currentlyMuted: Set<String>,
    onDismiss: () -> Unit,
    onSelectApp: (String, MuteMode) -> Unit
) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(MuteMode.BLOCK_RECORD) }
    var apps by remember { mutableStateOf(listOf<InstalledAppInfo>()) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            pm.queryIntentActivities(mainIntent, 0)
                .mapNotNull { ri ->
                    val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == ctx.packageName) return@mapNotNull null
                    InstalledAppInfo(
                        packageName = pkg,
                        label = ri.loadLabel(pm)?.toString()?.ifBlank { pkg } ?: pkg
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase(Locale.getDefault()) }
        }
    }

    val filtered = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter {
            it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇要靜音的 App") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("靜音模式：", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MuteMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(mode.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("搜尋 App 名稱或套件…") }
                )
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(filtered, key = { it.packageName }) { app ->
                        val alreadyMuted = app.packageName in currentlyMuted
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectApp(app.packageName, selectedMode) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconImage(
                                packageName = app.packageName,
                                contentDescription = app.label,
                                size = 32.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            if (alreadyMuted) {
                                Text("已靜音", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppThrottlePickerDialog(
    currentOverrides: Map<String, Int>,
    onDismiss: () -> Unit,
    onSelectApp: (String, Int) -> Unit
) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var selectedSeconds by remember { mutableIntStateOf(0) }
    var apps by remember { mutableStateOf(listOf<InstalledAppInfo>()) }
    val presetOptions = listOf(0 to "0 秒 (永不節流)", 30 to "30 秒", 60 to "60 秒", 120 to "120 秒", 300 to "300 秒")

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            pm.queryIntentActivities(mainIntent, 0)
                .mapNotNull { ri ->
                    val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == ctx.packageName) return@mapNotNull null
                    InstalledAppInfo(
                        packageName = pkg,
                        label = ri.loadLabel(pm)?.toString()?.ifBlank { pkg } ?: pkg
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase(Locale.getDefault()) }
        }
    }

    val filtered = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter {
            it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("設定個別 App 同 key 節流") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("選擇節流秒數（0 = 永不節流）：", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presetOptions.forEach { (sec, label) ->
                        FilterChip(
                            selected = selectedSeconds == sec,
                            onClick = { selectedSeconds = sec },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("搜尋 App 名稱或套件…") }
                )
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(filtered, key = { it.packageName }) { app ->
                        val existing = currentOverrides[app.packageName]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectApp(app.packageName, selectedSeconds) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconImage(
                                packageName = app.packageName,
                                contentDescription = app.label,
                                size = 32.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            if (existing != null) {
                                Text(
                                    text = if (existing == 0) "0s" else "${existing}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}

private enum class SettingsTourStep(
    val index: Int,
    val cardKey: String,
    val scrollCardKey: String,
    val title: String,
    val description: String
) {
    QUICK(
        index = 1,
        cardKey = "quick",
        scrollCardKey = "quick",
        title = "快速設定與省 Token",
        description = "最常用的核心開關都集中於此，包含「省 token 模式」、常駐統計通知與快速操作；你可以直接點擊洞內開關立即試用。"
    ),
    HUB(
        index = 2,
        cardKey = "hub",
        scrollCardKey = "hub",
        title = "權限與 AI 夥伴中心",
        description = "收合時一眼查看已授予權限數與早晚簡報時間；展開後可直達權限授予中心、AI 夥伴與主動推播及連結應用程式中心。"
    ),
    PERSONALIZATION(
        index = 3,
        cardKey = "personalization",
        scrollCardKey = "personalization",
        title = "個人化、玻璃強度與背景圖片",
        description = "調整卡片密度、時間格式、首頁排序與篩選欄，並可自訂全域／AI 聊天／對話頁三層背景圖片與液態玻璃強度。"
    ),
    AI(
        index = 4,
        cardKey = "ai",
        scrollCardKey = "ai",
        title = "AI 用途模型與雙模型路由",
        description = "管理多 Provider API Key 與各用途模型（含簡報與畫像卡），並可開啟「雙模型信心路由」進一步省 token。"
    ),
    NOTIF(
        index = 5,
        cardKey = "notif",
        scrollCardKey = "notif",
        title = "訊息與通知",
        description = "設定勿擾時段、即時關鍵字提醒、重複通知節流秒數、滑動刪除與點擊開啟行為，並可使用「清除已讀訊息」。"
    ),
    DATA(
        index = 6,
        cardKey = "data",
        scrollCardKey = "data",
        title = "資料管理與自訂標籤",
        description = "管理 App 靜音清單、自訂標籤規則、訊息保留天數與則數上限、CSV 匯出，也可隨時重播新手或設定教學。"
    ),
    WHITELIST(
        index = 7,
        cardKey = "whitelist",
        scrollCardKey = "data",
        title = "白名單管理卡",
        description = "依 App 分組收合檢視所有寄件者／內容白名單，支援手動新增（防重複）、開關啟停與確認刪除；AI 助手也會同步讀取這份清單。"
    ),
    APPEARANCE(
        index = 8,
        cardKey = "appearance",
        scrollCardKey = "appearance",
        title = "外觀、卡片底色與保活",
        description = "切換深淺主題、Material You 動態取色、分頁欄模式，以及「卡片底色模式」（跟隨玻璃強度／透明模式滑桿／深色底），並檢查電池最佳化與前景保活狀態。"
    ),
    SHIZUKU(
        index = 9,
        cardKey = "shizuku",
        scrollCardKey = "shizuku",
        title = "Shizuku 進階",
        description = "進階使用者可透過 Shizuku 進行系統級保活強化、權限補齊與連線診斷；未安裝時可略過不影響一般功能。"
    );

    companion object {
        fun fromIndex(index: Int): SettingsTourStep =
            entries.firstOrNull { it.index == index } ?: QUICK
    }
}

@Composable
private fun SettingsTourOverlay(
    stepIndex: Int,
    cardBounds: Map<String, Rect>,
    glassState: HazeState,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val step = SettingsTourStep.fromIndex(stepIndex)
    val density = LocalDensity.current
    val expandPx = with(density) { 6.dp.toPx() }
    val cornerPx = with(density) { 20.dp.toPx() }

    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var cardVisible by remember { mutableStateOf(false) }
    var lastNextClickMs by remember { mutableLongStateOf(0L) }

    val throttledNext: () -> Unit = {
        val now = System.currentTimeMillis()
        if (now - lastNextClickMs >= 450L) {
            lastNextClickMs = now
            onNext()
        }
    }

    LaunchedEffect(stepIndex) {
        cardVisible = false
        cardVisible = true
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                overlayOrigin = coords.positionInRoot()
            }
    ) {
        val totalWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val totalHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

        val targetRect = cardBounds[step.cardKey]
        val rawHoleRect = targetRect?.let {
            val l = (it.left - overlayOrigin.x - expandPx).coerceIn(0f, totalWidthPx)
            val t = (it.top - overlayOrigin.y - expandPx).coerceIn(0f, totalHeightPx)
            val r = (it.right - overlayOrigin.x + expandPx).coerceIn(l, totalWidthPx)
            val b = (it.bottom - overlayOrigin.y + expandPx).coerceIn(t, totalHeightPx)
            Rect(l, t, r, b)
        }
        val hasHole = rawHoleRect != null && rawHoleRect.width > 1f && rawHoleRect.height > 1f

        val animLeft by animateFloatAsState(
            targetValue = rawHoleRect?.left ?: 0f,
            animationSpec = tween(durationMillis = 200),
            label = "settingsHoleLeft"
        )
        val animTop by animateFloatAsState(
            targetValue = rawHoleRect?.top ?: 0f,
            animationSpec = tween(durationMillis = 200),
            label = "settingsHoleTop"
        )
        val animRight by animateFloatAsState(
            targetValue = rawHoleRect?.right ?: 0f,
            animationSpec = tween(durationMillis = 200),
            label = "settingsHoleRight"
        )
        val animBottom by animateFloatAsState(
            targetValue = rawHoleRect?.bottom ?: 0f,
            animationSpec = tween(durationMillis = 200),
            label = "settingsHoleBottom"
        )

        val holeLeft = animLeft.coerceIn(0f, totalWidthPx)
        val holeTop = animTop.coerceIn(0f, totalHeightPx)
        val holeRight = animRight.coerceIn(holeLeft, totalWidthPx)
        val holeBottom = animBottom.coerceIn(holeTop, totalHeightPx)

        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = if (isDark) 0.74f else 0.54f)
        val holeOutlineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)

        // 1. 半透明遮罩（PathFillType.EvenOdd 挖洞，純繪製不攔截觸控）
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                if (hasHole && holeRight > holeLeft && holeBottom > holeTop) {
                    addRoundRect(
                        RoundRect(
                            left = holeLeft,
                            top = holeTop,
                            right = holeRight,
                            bottom = holeBottom,
                            cornerRadius = CornerRadius(cornerPx, cornerPx)
                        )
                    )
                }
            }
            drawPath(
                path = path,
                color = scrimColor
            )
            if (hasHole && holeRight > holeLeft && holeBottom > holeTop) {
                drawRoundRect(
                    color = holeOutlineColor,
                    topLeft = Offset(holeLeft, holeTop),
                    size = androidx.compose.ui.geometry.Size(holeRight - holeLeft, holeBottom - holeTop),
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
        }

        // 2. 洞外四個區域攔截點擊進下一步；洞內無任何觸控節點，觸控 100% 穿透至底層開關與按鈕
        if (hasHole && holeRight > holeLeft && holeBottom > holeTop) {
            val noIndication = remember { MutableInteractionSource() }
            // 上方區域
            if (holeTop > 0f) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, 0) }
                        .size(
                            width = with(density) { totalWidthPx.toDp() },
                            height = with(density) { holeTop.toDp() }
                        )
                        .clickable(
                            interactionSource = noIndication,
                            indication = null,
                            onClick = throttledNext
                        )
                )
            }
            // 下方區域
            if (holeBottom < totalHeightPx) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, holeBottom.roundToInt()) }
                        .size(
                            width = with(density) { totalWidthPx.toDp() },
                            height = with(density) { (totalHeightPx - holeBottom).toDp() }
                        )
                        .clickable(
                            interactionSource = noIndication,
                            indication = null,
                            onClick = throttledNext
                        )
                )
            }
            // 左側區域
            if (holeLeft > 0f) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, holeTop.roundToInt()) }
                        .size(
                            width = with(density) { holeLeft.toDp() },
                            height = with(density) { (holeBottom - holeTop).toDp() }
                        )
                        .clickable(
                            interactionSource = noIndication,
                            indication = null,
                            onClick = throttledNext
                        )
                )
            }
            // 右側區域
            if (holeRight < totalWidthPx) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(holeRight.roundToInt(), holeTop.roundToInt()) }
                        .size(
                            width = with(density) { (totalWidthPx - holeRight).toDp() },
                            height = with(density) { (holeBottom - holeTop).toDp() }
                        )
                        .clickable(
                            interactionSource = noIndication,
                            indication = null,
                            onClick = throttledNext
                        )
                )
            }
        } else {
            val noIndication = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = noIndication,
                        indication = null,
                        onClick = throttledNext
                    )
            )
        }

        // 3. 說明卡自動避開亮區（洞偏下時卡片放上方，否則放下方）
        val holeCenterY = if (hasHole) (holeTop + holeBottom) / 2f else totalHeightPx * 0.3f
        val placeCardAtTop = hasHole && holeCenterY > totalHeightPx * 0.52f

        AnimatedVisibility(
            visible = cardVisible,
            enter = fadeIn(tween(200)) + slideInVertically(
                animationSpec = tween(200),
                initialOffsetY = { if (placeCardAtTop) -it / 2 else it / 2 }
            ),
            modifier = Modifier
                .align(if (placeCardAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                .then(
                    if (placeCardAtTop) {
                        Modifier.statusBarsPadding().padding(top = 64.dp, start = 16.dp, end = 16.dp)
                    } else {
                        Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 20.dp)
                    }
                )
        ) {
            PhantomGlassCard(
                glassState = glassState,
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                contentPadding = 4.dp
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        val totalSteps = SettingsTourStep.entries.size
                        Text(
                            text = "步驟 ${step.index} / $totalSteps・${step.title}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = step.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onSkip) {
                                Text("跳過")
                            }
                            Button(onClick = throttledNext) {
                                Text(if (step.index == totalSteps) "完成" else "下一步")
                            }
                        }
                    }
                }
            }
        }
    }
}




