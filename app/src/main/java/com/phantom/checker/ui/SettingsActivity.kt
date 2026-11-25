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
import androidx.compose.foundation.background
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
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource

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
                brandTitle = com.phantom.checker.PhantomApplication.str(R.string.settings_oem_xiaomi_title),
                steps = com.phantom.checker.PhantomApplication.str(R.string.settings_oem_xiaomi_body),
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
                brandTitle = com.phantom.checker.PhantomApplication.str(R.string.settings_oem_samsung_title),
                steps = com.phantom.checker.PhantomApplication.str(R.string.settings_oem_samsung_body),
                intents = listOf(
                    Intent("com.samsung.android.sm.ACTION_OPEN_APP_BATTERY_USAGE"),
                    appDetailsIntent
                )
            )
        manufacturer.contains("huawei") || manufacturer.contains("honor") ->
            OemGuideInfo(
                brandTitle = com.phantom.checker.PhantomApplication.str(R.string.settings_oem_huawei_title),
                steps = com.phantom.checker.PhantomApplication.str(R.string.settings_oem_huawei_body),
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
                brandTitle = com.phantom.checker.PhantomApplication.str(R.string.settings_oem_oppo_title),
                steps = com.phantom.checker.PhantomApplication.str(R.string.settings_oem_oppo_body),
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
                brandTitle = com.phantom.checker.PhantomApplication.str(R.string.settings_oem_vivo_title),
                steps = com.phantom.checker.PhantomApplication.str(R.string.settings_oem_vivo_body),
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
                brandTitle = com.phantom.checker.PhantomApplication.str(R.string.settings_oem_default_title),
                steps = com.phantom.checker.PhantomApplication.str(R.string.settings_oem_default_body),
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
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.phantom.checker.util.AppLocaleHelper.wrapContext(newBase))
    }
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
    val resCtx = LocalContext.current
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
            if (granted) resCtx.getString(R.string.settings_s_9e473c) else resCtx.getString(R.string.settings_s_1b5759),
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
            if (calendarReadGranted) resCtx.getString(R.string.settings_s_469985) else resCtx.getString(R.string.settings_s_20e13e),
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
            withFrameNanos { }
        }
    }

    // 外觀與互動
    val currentThemeMode = ThemePreferences.getThemeMode(appCtx)
    val highContrastEnabled = ThemePreferences.isHighContrast(appCtx)
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
                scope.launch { snackbarHostState.showSnackbar(resCtx.getString(R.string.settings_s_e88b08, pkg, mode.label)) }
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
                val msg = if (seconds == 0) resCtx.getString(R.string.settings_s_5d94cf, pkg) else resCtx.getString(R.string.settings_s_62eb29, pkg, seconds)
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
                        snackbarHostState.showSnackbar(resCtx.getString(R.string.settings_s_11c369, fileName))
                    }.onFailure { err ->
                        val reason = err.localizedMessage?.takeIf { it.isNotBlank() } ?: err.javaClass.simpleName
                        snackbarHostState.showSnackbar(resCtx.getString(R.string.chat_s_55fba2, reason))
                    }
                }
            }
        )
    }

    if (showClearReadConfirm) {
        AlertDialog(
            onDismissRequest = { showClearReadConfirm = false },
            title = { Text(stringResource(R.string.catalog_notif_clear_read_title)) },
            text = { Text(stringResource(R.string.settings_s_f50993)) },
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
                            snackbarHostState.showSnackbar(resCtx.getString(R.string.settings_s_4fd207))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.common_confirm_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearReadConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text(stringResource(R.string.settings_s_20a08d), color = MaterialTheme.colorScheme.error) },
            text = { Text(stringResource(R.string.settings_s_1cbd17)) },
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
                            snackbarHostState.showSnackbar(resCtx.getString(R.string.settings_s_5fa0a6))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.settings_s_010656))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
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
            title = { Text(stringResource(R.string.settings_s_607ffc)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.settings_s_503ca6, PresetKeywords.keywords.size, newCount, skipCount),
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
                                resCtx.getString(R.string.settings_s_ce0699, added, skipped)
                            } else {
                                resCtx.getString(R.string.settings_s_61b771)
                            }
                            snackbarHostState.showSnackbar(msg)
                        }
                    },
                    enabled = !isImportingPresets
                ) {
                    Text(if (isImportingPresets) stringResource(R.string.settings_s_5bf153) else stringResource(R.string.settings_s_7ddb23))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPresetDialog = false },
                    enabled = !isImportingPresets
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    pendingAvatarCropUri?.let { cropUri ->
        AssistantAvatarCropDialog(
            uri = cropUri,
            onDismiss = { pendingAvatarCropUri = null },
            onLoadError = { errMsg ->
                scope.launch { snackbarHostState.showSnackbar(resCtx.getString(R.string.settings_s_3da8fe, errMsg)) }
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
                        snackbarHostState.showSnackbar(resCtx.getString(R.string.settings_s_14270b))
                    }.onFailure { err ->
                        snackbarHostState.showSnackbar(resCtx.getString(R.string.settings_s_31b8e6, err.message ?: resCtx.getString(R.string.common_unknown_error)))
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
            "LinkHubActivity" -> LinkHubActivity::class.java
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
                    aiSearchNote = resCtx.getString(R.string.search_s_4de5af)
                    snackbarHostState.showSnackbar(resCtx.getString(R.string.search_s_4de5af))
                    return@onSuccess
                }
                aiMatchedIds = matchedItems.map { it.id }
                aiSearchNote = resCtx.getString(R.string.settings_s_91d2e5, matchedItems.size)
            }.onFailure { err ->
                snackbarHostState.showSnackbar(err.message ?: resCtx.getString(R.string.settings_s_896497))
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
        val activeModel = AiClient.getActiveProfile(appCtx)?.model?.takeIf { it.isNotBlank() } ?: resCtx.getString(R.string.settings_s_79aacc)
        val quietSummary = if (quietHoursEnabled) {
            resCtx.getString(R.string.settings_s_461b79).format(
                quietStartMinute / 60, quietStartMinute % 60,
                quietEndMinute / 60, quietEndMinute % 60
            )
        } else {
            resCtx.getString(R.string.settings_s_528d2f, if (persistentStats) resCtx.getString(R.string.settings_s_99529c) else resCtx.getString(R.string.settings_s_c56d81))
        }
        val displayAssistantName = assistantName.ifBlank { resCtx.getString(R.string.chat_s_8dd976) }
        val themeLabel = ThemePreferences.getThemeMode(appCtx).label
        val layoutLabel = if (homeCardLayoutMode == PhantomPrefs.HomeCardLayoutMode.CENTER) resCtx.getString(R.string.catalog_layout_stack) else resCtx.getString(R.string.settings_s_0c69cd)
        val notifAccessGranted = androidx.core.app.NotificationManagerCompat
            .getEnabledListenerPackages(appCtx).contains(appCtx.packageName)
        val pendingPermCount = listOf(
            notifAccessGranted,
            batteryWhitelisted
        ).count { !it }
        val permSummary = buildString {
            append(if (pendingPermCount > 0) resCtx.getString(R.string.settings_s_02d540, pendingPermCount) else resCtx.getString(R.string.settings_s_ace3a4))
            if (shizukuReady) append(resCtx.getString(R.string.settings_s_f4d8a0))
        }
        val versionName = runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        }.getOrDefault("")
        SettingsHomeSummaries(
            quickSummary = resCtx.getString(R.string.settings_s_d185c0, if (tokenSaverEnabled) resCtx.getString(R.string.settings_s_99529c) else resCtx.getString(R.string.settings_s_c56d81), if (aiRiskEnabled) resCtx.getString(R.string.settings_s_99529c) else resCtx.getString(R.string.settings_s_c56d81)),
            aiSummary = resCtx.getString(R.string.settings_s_360eec, profileCount, activeModel, todayAiUsage, dailyAiLimit),
            notifSummary = quietSummary,
            assistantSummary = resCtx.getString(R.string.settings_s_79736a, displayAssistantName, if (memoryChatCaptureEnabled) resCtx.getString(R.string.settings_s_99529c) else resCtx.getString(R.string.settings_s_c56d81)),
            permissionsSummary = permSummary,
            permissionsBadge = if (pendingPermCount > 0) resCtx.getString(R.string.settings_s_b6b5df, pendingPermCount) else null,
            appearanceSummary = resCtx.getString(R.string.settings_s_8ee5e7, themeLabel, layoutLabel),
            dataSummary = resCtx.getString(R.string.settings_s_97d64e, retentionDays, mutedApps.size),
            aboutSummary = resCtx.getString(R.string.settings_s_96f247, versionName, if (PhantomPrefs.isUpdateAutoCheckEnabled(appCtx)) resCtx.getString(R.string.settings_s_99529c) else resCtx.getString(R.string.settings_s_c56d81)),
            versionDisplay = "v$versionName"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
                                    contentDescription = if (inCategorySearchOpen) stringResource(R.string.settings_s_44b160) else stringResource(R.string.settings_s_6cdd18)
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
                                            placeholder = { Text(stringResource(R.string.settings_s_31d41b, currentCategory.title)) },
                                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                            trailingIcon = {
                                                if (inCategorySearchQuery.isNotEmpty()) {
                                                    IconButton(onClick = { inCategorySearchQuery = "" }) {
                                                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.common_clear))
                                                    }
                                                }
                                            }
                                        )
                                        if (inCategorySearchQuery.isNotBlank()) {
                                            if (inCatMatches.isEmpty()) {
                                                Text(
                                                    text = stringResource(R.string.settings_s_730d7e, inCategorySearchQuery.trim()),
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
                                                                    text = resCtx.getString(R.string.settings_s_ed9b32),
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
                                title = stringResource(R.string.settings_s_e024ca),
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
                        title = stringResource(R.string.catalog_quick_token_saver_title),
                        subtitle = stringResource(R.string.settings_s_7d1a1f),
                        checked = tokenSaverEnabled,
                        onCheckedChange = { checked ->
                            tokenSaverEnabled = checked
                            PhantomPrefs.setTokenSaverEnabled(appCtx, checked)
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SettingSwitchRow(
                        title = stringResource(R.string.catalog_quick_persistent_notif_title),
                        subtitle = stringResource(R.string.settings_s_d2d0b4),
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
                        title = stringResource(R.string.catalog_quick_ai_risk_title),
                        subtitle = if (tokenSaverEnabled) {
                            stringResource(R.string.settings_s_b0be28, aiMinTriggerLength)
                        } else {
                            stringResource(R.string.settings_s_75d4dd, aiMinTriggerLength)
                        },
                        checked = aiRiskEnabled,
                        onCheckedChange = { checked ->
                            aiRiskEnabled = checked
                            PhantomPrefs.setAiRiskEnabled(appCtx, checked)
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SettingSwitchRow(
                        title = stringResource(R.string.catalog_ai_auto_tag_title),
                        subtitle = if (tokenSaverEnabled) {
                            stringResource(R.string.settings_s_7ed809)
                        } else {
                            stringResource(R.string.catalog_ai_auto_tag_desc)
                        },
                        checked = aiAutoTagEnabled,
                        onCheckedChange = { checked ->
                            aiAutoTagEnabled = checked
                            PhantomPrefs.setAiAutoTagEnabled(appCtx, checked)
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SettingSwitchRow(
                        title = stringResource(R.string.catalog_quick_scam_popup_title),
                        subtitle = stringResource(R.string.settings_s_0e2fde),
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
                        morningOn && eveningOn -> stringResource(R.string.settings_s_f6aec5, PhantomPrefs.getMorningBriefingTime(appCtx), PhantomPrefs.getEveningBriefingTime(appCtx))
                        morningOn -> stringResource(R.string.settings_s_b16699, PhantomPrefs.getMorningBriefingTime(appCtx))
                        eveningOn -> stringResource(R.string.settings_s_c7348a, PhantomPrefs.getEveningBriefingTime(appCtx))
                        else -> stringResource(R.string.chat_s_e8a92f)
                    }
                    CollapsibleSettingsCard(
                        title = stringResource(R.string.settings_s_c8c8ec),
                        collapsedSummary = stringResource(R.string.settings_s_490c77, grantedCount, briefingStatus),
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
                                text = stringResource(R.string.settings_s_1d238b),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!notifPostOk) {
                                Text(
                                    text = stringResource(R.string.settings_s_74f4a2),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                OutlinedButton(
                                    onClick = { com.phantom.checker.service.ProactiveEngine.openAppNotificationSettings(ctx) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.settings_s_e9a953))
                                }
                            }
                            Button(
                                onClick = { ctx.startActivity(Intent(ctx, PermissionHubActivity::class.java)) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.settings_s_01c27b, grantedCount))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { ctx.startActivity(Intent(ctx, AssistantHubActivity::class.java)) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.settings_s_a1eeb3), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                OutlinedButton(
                                    onClick = { ctx.startActivity(Intent(ctx, LinkedAppsActivity::class.java)) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.settings_s_5a83e6), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            OutlinedButton(
                                onClick = { ctx.startActivity(Intent(ctx, LinkHubActivity::class.java)) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.link_hub_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            // 1.5 個人化
            if ("personalization" in visibleCardKeys) {
                item(key = "card_personalization") {
                    CollapsibleSettingsCard(
                        title = stringResource(R.string.settings_s_3cdf28),
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
                        Text(stringResource(R.string.settings_s_e49b0f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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

                        Text(stringResource(R.string.catalog_personal_sort_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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

                        Text(stringResource(R.string.catalog_personal_filters_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            text = stringResource(R.string.settings_s_611c54),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        val filterOptions = listOf(
                            "starred" to stringResource(R.string.common_star),
                            "scams" to stringResource(R.string.common_scam),
                            "filtered" to stringResource(R.string.common_filtered),
                            "noted" to stringResource(R.string.common_noted),
                            "todoed" to stringResource(R.string.common_todoed),
                            "tag" to stringResource(R.string.common_tags)
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
                            title = stringResource(R.string.catalog_personal_summary_collapse_title),
                            subtitle = stringResource(R.string.settings_s_9ca41d),
                            checked = summaryDefaultCollapsed,
                            onCheckedChange = { checked ->
                                summaryDefaultCollapsed = checked
                                PhantomPrefs.setSummaryDefaultCollapsed(appCtx, checked)
                            }
                        )

                        HorizontalDivider(Modifier.padding(vertical = 8.dp))

                        Text(stringResource(R.string.catalog_personal_home_card_layout_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            text = stringResource(R.string.settings_s_622451),
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

                        Text(stringResource(R.string.catalog_personal_notes_card_layout_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            text = stringResource(R.string.settings_s_f9d284),
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

                        Text(stringResource(R.string.settings_s_3854c1), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = use24HourFormat,
                                onClick = {
                                    use24HourFormat = true
                                    PhantomPrefs.set24HourFormat(appCtx, true)
                                },
                                label = { Text(stringResource(R.string.settings_s_35f989)) }
                            )
                            FilterChip(
                                selected = !use24HourFormat,
                                onClick = {
                                    use24HourFormat = false
                                    PhantomPrefs.set24HourFormat(appCtx, false)
                                },
                                label = { Text(stringResource(R.string.settings_s_d24adf)) }
                            )
                        }

                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text(stringResource(R.string.settings_s_faa4c5), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.settings_s_1baffa),
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
                                bgStatusMsg = resCtx.getString(R.string.settings_s_189dca)
                                bgPickerFailed = false
                                return
                            }
                            scope.launch {
                                val res = BackgroundImageHelper.saveBackgroundFromUri(appCtx, targetLayer, uri)
                                res.onSuccess {
                                    ThemePreferences.bumpBackgroundVersion()
                                    bgUiVersion = ThemePreferences.getBackgroundVersion()
                                    bgStatusMsg = resCtx.getString(R.string.settings_s_9c7167, targetLayer.label)
                                    bgPickerFailed = false
                                    failedLayerName = null
                                }.onFailure { err ->
                                    bgStatusMsg = err.message ?: resCtx.getString(R.string.settings_s_60e020)
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
                                            text = if (hasBg) resCtx.getString(R.string.settings_s_355d4f) else if (layer == BackgroundLayer.GLOBAL) resCtx.getString(R.string.settings_s_6525ea) else resCtx.getString(R.string.settings_s_195b76),
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
                                                bgStatusMsg = resCtx.getString(R.string.settings_s_74e0bc, e.localizedMessage ?: resCtx.getString(R.string.settings_s_fe6b26))
                                                bgPickerFailed = true
                                                failedLayerName = layer.name
                                            }
                                        }
                                    ) {
                                        Text(resCtx.getString(R.string.settings_s_a4f0c2))
                                    }
                                    if (hasBg) {
                                        TextButton(
                                            onClick = {
                                                BackgroundImageHelper.removeBackground(appCtx, layer)
                                                ThemePreferences.bumpBackgroundVersion()
                                                bgUiVersion = ThemePreferences.getBackgroundVersion()
                                                bgStatusMsg = resCtx.getString(R.string.settings_s_244bee, layer.label)
                                                bgPickerFailed = false
                                            }
                                        ) {
                                            Text(resCtx.getString(R.string.chat_s_2f752c))
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
                                            bgStatusMsg = resCtx.getString(R.string.settings_s_2d1e64)
                                            bgPickerFailed = false
                                        },
                                        onFailure = { err ->
                                            bgStatusMsg = resCtx.getString(R.string.settings_s_8c9d83, err.localizedMessage ?: resCtx.getString(R.string.common_unknown_error))
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
                                        contentDescription = stringResource(R.string.catalog_assistant_header_background_title),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.catalog_assistant_header_background_title),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (hasPartnerHeaderBg) stringResource(R.string.settings_s_355d4f) else stringResource(R.string.settings_s_2a62bc),
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
                                            bgStatusMsg = resCtx.getString(R.string.settings_s_1c36f5, e.localizedMessage ?: resCtx.getString(R.string.settings_s_fe6b26))
                                            bgPickerFailed = true
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.settings_s_a4f0c2))
                                }
                                if (hasPartnerHeaderBg) {
                                    TextButton(
                                        onClick = {
                                            BackgroundImageHelper.removeAssistantHeaderBackground(appCtx)
                                            ThemePreferences.bumpBackgroundVersion()
                                            PhantomSettingsBus.notifyChanged()
                                            bgUiVersion = ThemePreferences.getBackgroundVersion()
                                            bgStatusMsg = resCtx.getString(R.string.settings_s_c0b2b4)
                                            bgPickerFailed = false
                                        }
                                    ) {
                                        Text(stringResource(R.string.chat_s_2f752c))
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
                                        bgStatusMsg = resCtx.getString(R.string.settings_s_625a6a, e.localizedMessage ?: resCtx.getString(R.string.common_unknown_error))
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.settings_s_2bef07, fallbackLayer.label))
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
                                    text = if (scrimPref < 0) stringResource(R.string.settings_s_de91cf, effectiveScrim) else stringResource(R.string.settings_s_578c46, effectiveScrim),
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
                                        Text(stringResource(R.string.settings_home_s_3cbc2a))
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
                            text = if (glassStrength == 0) stringResource(R.string.settings_s_53f8ee) else stringResource(R.string.settings_s_897fae, glassStrength),
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
                                text = stringResource(R.string.settings_s_603242),
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
                                Text(stringResource(R.string.settings_s_9dbafd))
                            }
                        }
                        Text(
                            text = stringResource(R.string.settings_s_5cb26a, String.format(Locale.US, "%.2fx", chatFontScale)),
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
                            text = stringResource(R.string.settings_s_1d5819, String.format(Locale.US, "%.2fx", msgFontScale)),
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
                            text = stringResource(R.string.settings_s_96c840, bubbleCornerDp),
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
                            text = stringResource(R.string.settings_s_017b20, bubbleWidthPct),
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
                            text = stringResource(R.string.settings_s_4aec2a),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            listOf(0 to stringResource(R.string.settings_s_e31dcd), 4 to stringResource(R.string.settings_s_d3f063), 6 to stringResource(R.string.settings_s_d67660), 8 to stringResource(R.string.settings_s_7e4112)).forEach { (limit, label) ->
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
                            text = stringResource(R.string.catalog_appearance_card_bg_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.settings_s_c4228c),
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
                                text = stringResource(R.string.settings_s_2d1b21, cardBgAlpha),
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
                        title = stringResource(R.string.common_stats),
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
                            text = stringResource(R.string.catalog_stats_card_visibility_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.settings_s_c59c19),
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
                    title = stringResource(R.string.settings_s_e0b81f),
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
                        Text(stringResource(R.string.settings_s_7263f2))
                    }
                    Spacer(Modifier.height(8.dp))

                    var tagDispatchTesting by remember { mutableStateOf(false) }
                    var tagDispatchResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
                    val remainingQuotaText = if (dailyAiLimit <= 0) {
                        stringResource(R.string.common_unlimited)
                    } else {
                        stringResource(R.string.settings_s_d82c05, (dailyAiLimit - todayAiUsage).coerceAtLeast(0), dailyAiLimit)
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
                                    tagDispatchResult = false to resCtx.getString(R.string.settings_s_1dbec3, dailyAiLimit)
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
                                        val tagStr = if (tagNames.isEmpty()) resCtx.getString(R.string.settings_s_9751b8) else tagNames.joinToString("、")
                                        tagDispatchResult = true to resCtx.getString(R.string.settings_s_56bcf6, analysis.riskLabel, analysis.riskScore, tagStr, analysis.riskReason)
                                    }.onFailure { err ->
                                        PhantomPrefs.refundTodayAiUsage(appCtx)
                                        todayAiUsage = PhantomPrefs.getTodayAiUsage(appCtx)
                                        tagDispatchResult = false to (err.message ?: resCtx.getString(R.string.settings_s_6d8218))
                                    }
                                }
                            },
                            modifier = Modifier.pressableScale()
                        ) {
                            Text(if (tagDispatchTesting) stringResource(R.string.settings_s_ba08af) else stringResource(R.string.catalog_ai_tag_dispatch_test_title))
                        }
                        Text(
                            text = stringResource(R.string.settings_s_07426c, remainingQuotaText),
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
                        title = stringResource(R.string.catalog_ai_tag_failure_notif_enabled_title),
                        subtitle = stringResource(R.string.settings_s_2d40bd),
                        checked = aiTagFailureNotifEnabled,
                        onCheckedChange = { checked ->
                            aiTagFailureNotifEnabled = checked
                            PhantomPrefs.setAiTagFailureNotifEnabled(appCtx, checked)
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SettingSwitchRow(
                        title = stringResource(R.string.settings_s_e81d45),
                        subtitle = stringResource(R.string.settings_s_b44c61),
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
                            label = { Text(stringResource(R.string.settings_s_0dae5f)) },
                            placeholder = { Text(PhantomPrefs.DEFAULT_DUAL_MODEL_ROUTING_MODEL) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SettingSwitchRow(
                        title = stringResource(R.string.catalog_ai_chat_suggestions_title),
                        subtitle = stringResource(R.string.catalog_ai_chat_suggestions_desc),
                        checked = chatSuggestionsEnabled,
                        onCheckedChange = { checked ->
                            chatSuggestionsEnabled = checked
                            PhantomPrefs.setChatSuggestionsEnabled(appCtx, checked)
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SettingSwitchRow(
                        title = stringResource(R.string.catalog_ai_chat_tab_behavior_title),
                        subtitle = stringResource(R.string.settings_s_6f03f2),
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
                        text = stringResource(R.string.settings_s_41d9b9),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_s_c452a8),
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
                            label = { Text(stringResource(R.string.chat_s_aa0b15)) }
                        )
                        FilterChip(
                            selected = quickCommandDefaultMode == PhantomPrefs.QuickChatCommand.MODE_PREFILL,
                            onClick = {
                                quickCommandDefaultMode = PhantomPrefs.QuickChatCommand.MODE_PREFILL
                                PhantomPrefs.setQuickCommandDefaultMode(appCtx, PhantomPrefs.QuickChatCommand.MODE_PREFILL)
                                quickChatCommands = PhantomPrefs.getQuickChatCommands(appCtx)
                            },
                            label = { Text(stringResource(R.string.chat_s_5af577)) }
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
                            text = stringResource(R.string.settings_s_bf22cf, quickChatCommands.size),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        PhantomChevron(
 expanded = quickCommandsExpanded.value,
                            contentDescription = if (quickCommandsExpanded.value) stringResource(R.string.settings_s_0001fa) else stringResource(R.string.settings_s_e846db),
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
                                        Text(stringResource(R.string.settings_s_14c7d8))
                                    }
                                }
                                TextButton(onClick = { showAddQuickCommandDialog = true }) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.settings_s_8ad53f))
                                }
                            }
                    if (quickChatCommands.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_s_31e1b0),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            quickChatCommands.forEach { cmd ->
                                val resolvedMode = PhantomPrefs.resolveQuickCommandMode(cmd.mode, quickCommandDefaultMode)
                                val isFollow = cmd.mode == PhantomPrefs.QuickChatCommand.MODE_FOLLOW
                                val resolvedText = if (resolvedMode == PhantomPrefs.QuickChatCommand.MODE_DIRECT) resCtx.getString(R.string.chat_s_aa0b15) else resCtx.getString(R.string.chat_s_5af577)
                                val chipLabel = if (isFollow) resCtx.getString(R.string.settings_s_a4ede0, resolvedText) else resolvedText
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
                                                contentDescription = resCtx.getString(R.string.settings_s_b9ec94),
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
                            title = { Text(stringResource(R.string.chat_s_1408b1)) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = draftLabel,
                                        onValueChange = { draftLabel = it },
                                        label = { Text(stringResource(R.string.settings_s_7613fb)) },
                                        placeholder = { Text(stringResource(R.string.settings_s_bcac9f)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = draftPrompt,
                                        onValueChange = { draftPrompt = it },
                                        label = { Text(stringResource(R.string.settings_s_f163bc)) },
                                        placeholder = { Text(stringResource(R.string.settings_s_248fbf)) },
                                        maxLines = 3,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = draftMode == PhantomPrefs.QuickChatCommand.MODE_FOLLOW,
                                            onClick = { draftMode = PhantomPrefs.QuickChatCommand.MODE_FOLLOW },
                                            label = { Text(stringResource(R.string.settings_s_4f2580)) }
                                        )
                                        FilterChip(
                                            selected = draftMode == PhantomPrefs.QuickChatCommand.MODE_DIRECT,
                                            onClick = { draftMode = PhantomPrefs.QuickChatCommand.MODE_DIRECT },
                                            label = { Text(stringResource(R.string.chat_s_aa0b15)) }
                                        )
                                        FilterChip(
                                            selected = draftMode == PhantomPrefs.QuickChatCommand.MODE_PREFILL,
                                            onClick = { draftMode = PhantomPrefs.QuickChatCommand.MODE_PREFILL },
                                            label = { Text(stringResource(R.string.chat_s_5af577)) }
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
                                    Text(stringResource(R.string.common_add))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showAddQuickCommandDialog = false }) {
                                    Text(stringResource(R.string.common_cancel))
                                }
                            }
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    Text(
                        text = stringResource(R.string.settings_s_32984e, todayAiUsage),
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
                                label = { Text(if (limit == 0) stringResource(R.string.common_unlimited) else stringResource(R.string.settings_s_12783d, limit)) }
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.settings_s_fa2717),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(
                        text = if (tokenSaverEnabled) {
                            stringResource(R.string.settings_s_0444c3, chatRagLimit)
                        } else {
                            stringResource(R.string.settings_s_4ea4df, chatRagLimit)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_s_b4c2cc),
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
                        text = stringResource(R.string.settings_s_3ca69a, aiMinTriggerLength),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_s_bd7106),
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
                        title = stringResource(R.string.catalog_ai_offline_retry_enabled_title),
                        subtitle = stringResource(R.string.settings_s_0fcae3),
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
                        title = stringResource(R.string.catalog_ai_auto_memo_enabled_title),
                        subtitle = stringResource(R.string.settings_s_4bfeaa),
                        checked = aiAutoMemoEnabled,
                        onCheckedChange = { checked ->
                            aiAutoMemoEnabled = checked
                            PhantomPrefs.setAiAutoMemoEnabled(appCtx, checked)
                        }
                    )

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    Text(
                        text = stringResource(R.string.settings_s_8138bb, alertKeywords.size, PhantomPrefs.MAX_ALERT_KEYWORDS),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_s_7a8d71),
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
                            placeholder = { Text(stringResource(R.string.settings_s_754ad7)) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (PhantomPrefs.addAlertKeyword(appCtx, newAlertKeyword)) {
                                    alertKeywords = PhantomPrefs.getAlertKeywords(appCtx)
                                    newAlertKeyword = ""
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(resCtx.getString(R.string.settings_s_22ec67))
                                    }
                                }
                            },
                            enabled = newAlertKeyword.isNotBlank() &&
                                alertKeywords.size < PhantomPrefs.MAX_ALERT_KEYWORDS
                        ) {
                            Text(stringResource(R.string.common_add))
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
                        Text(stringResource(R.string.catalog_ai_auto_reply_title))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { ctx.startActivity(Intent(ctx, ScamCenterActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth().pressableScale()
                    ) {
                        Text(stringResource(R.string.catalog_ai_scam_center_title))
                    }
                }
            }
            }

            // 2.5 記憶庫與 AI 助手（#315 B4）
            if ("memory_assistant" in visibleCardKeys) {
            item(key = "card_memory_assistant") {
                CollapsibleSettingsCard(
                    title = stringResource(R.string.settings_s_79f0ee),
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
                        text = stringResource(R.string.settings_s_8fbd91),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    SettingSwitchRow(
                        title = stringResource(R.string.catalog_memory_chat_capture_title),
                        subtitle = stringResource(R.string.settings_s_0c815b),
                        checked = memoryChatCaptureEnabled,
                        onCheckedChange = { checked ->
                            memoryChatCaptureEnabled = checked
                            PhantomPrefs.setMemoryChatCaptureEnabled(appCtx, checked)
                            PhantomSettingsBus.notifyChanged()
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    SettingSwitchRow(
                        title = stringResource(R.string.catalog_memory_write_confirm_title),
                        subtitle = stringResource(R.string.settings_s_62c1db),
                        checked = memoryWriteConfirmEnabled,
                        onCheckedChange = { checked ->
                            memoryWriteConfirmEnabled = checked
                            PhantomPrefs.setMemoryWriteConfirmEnabled(appCtx, checked)
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    SettingSwitchRow(
                        title = stringResource(R.string.catalog_ai_anti_fraud_cross_check_title),
                        subtitle = stringResource(R.string.settings_s_e1151a),
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
                                    stringResource(R.string.settings_s_5c263e)
                                } else {
                                    stringResource(R.string.settings_s_55fc12)
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
                                    Text(stringResource(R.string.settings_s_45df2d))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_s_ce2e80),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(Modifier.padding(vertical = 10.dp))

                    // #329 / #348 本機日曆整合（多日曆勾選，預設全不勾，日曆資料絕不外送 API）
                    Text(
                        text = stringResource(R.string.settings_s_0cc878),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_s_8eb62d),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    SettingSwitchRow(
                        title = stringResource(R.string.catalog_calendar_write_confirm_title),
                        subtitle = stringResource(R.string.settings_s_8ff871),
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
                                text = stringResource(R.string.settings_s_773d30),
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
                                Text(stringResource(R.string.settings_s_a478d0))
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
                                    text = stringResource(R.string.settings_s_952369),
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
                                    Text(stringResource(R.string.settings_s_b20fcd))
                                }
                            }
                        }
                        if (deviceCalendars.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_s_ba000a),
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
                                        text = stringResource(R.string.settings_s_91e9d8, selectedTotalCount, selectedVisibleCount, visibleCals.size),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (hiddenCals.isNotEmpty()) {
                                        Text(
                                            text = stringResource(R.string.settings_s_807999, hiddenCals.size),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                PhantomChevron(
 expanded = calendarListExpanded,
                                    contentDescription = if (calendarListExpanded) stringResource(R.string.settings_s_4d50a8) else stringResource(R.string.settings_s_0a7a54),
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
                                            text = if (allVisibleSelected) stringResource(R.string.settings_s_4e1a95) else stringResource(R.string.settings_s_860e84, visibleCals.size),
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
                                            val grouped = cals.groupBy { it.accountName.ifBlank { resCtx.getString(R.string.settings_s_5c61b5) } }
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
                                                                text = if (groupAllChecked) resCtx.getString(R.string.settings_s_0aa556) else resCtx.getString(R.string.common_select_all),
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
                                                                    append(if (cal.isWritable) resCtx.getString(R.string.settings_s_6ec0ba) else resCtx.getString(R.string.settings_s_2d19d5))
                                                                    if (isHiddenSection) append(resCtx.getString(R.string.settings_s_87c091))
                                                                    if (!cal.synced) append(resCtx.getString(R.string.settings_s_f75137))
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
                                            text = stringResource(R.string.settings_s_8d1eec),
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
                                                text = stringResource(R.string.settings_s_c343d1, hiddenCals.size),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            PhantomChevron(
 expanded = showHiddenCalendars,
                                                contentDescription = if (showHiddenCalendars) stringResource(R.string.settings_s_d3260f) else stringResource(R.string.settings_s_0ede1d),
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
                        text = stringResource(R.string.catalog_schedule_memo_route_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_s_c4c55d),
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
                            label = { Text(stringResource(R.string.settings_s_80ec5b), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = scheduleMemoRoute == PhantomPrefs.SCHEDULE_MEMO_ROUTE_LEGACY_TODO_NOTE,
                            onClick = {
                                scheduleMemoRoute = PhantomPrefs.SCHEDULE_MEMO_ROUTE_LEGACY_TODO_NOTE
                                PhantomPrefs.setScheduleMemoRoute(appCtx, scheduleMemoRoute)
                                PhantomSettingsBus.notifyChanged()
                            },
                            label = { Text(stringResource(R.string.settings_s_e13901), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 10.dp))

                    // #336 / #340 通訊錄整合已集中收納於「AI 夥伴與主動推播」獨立設定頁
                    OutlinedButton(
                        onClick = { ctx.startActivity(Intent(ctx, AssistantHubActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_s_b63b6e))
                    }

                    HorizontalDivider(Modifier.padding(vertical = 10.dp))

                    // (2) 注入預算（正常模式 vs 省 token 模式分開設定、互不覆寫）
                    Text(
                        text = stringResource(R.string.settings_s_c194ea),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    val effectiveTopK = PhantomPrefs.getEffectiveMemoryInjectionTopK(appCtx)
                    val effectiveBudget = PhantomPrefs.getEffectiveMemoryTotalTokenBudget(appCtx)
                    Text(
                        text = if (tokenSaverEnabled) {
                            stringResource(R.string.settings_s_389112, effectiveTopK, effectiveBudget)
                        } else {
                            stringResource(R.string.settings_s_2b0d05, effectiveTopK, effectiveBudget)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.settings_s_2b5dc2, memoryNormalTopK),
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
                        text = stringResource(R.string.settings_s_09968d, memoryNormalPerItemChars),
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
                        text = stringResource(R.string.settings_s_87d932, memorySaverTopK),
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
                        text = stringResource(R.string.settings_s_981f24),
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
                                text = stringResource(R.string.settings_s_7678e2),
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
                                                    snackbarHostState.showSnackbar(resCtx.getString(R.string.settings_s_c57697))
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Text(if (assistantHasAvatar) stringResource(R.string.settings_s_d98c67) else stringResource(R.string.settings_s_91c4c0))
                                }
                                if (assistantHasAvatar) {
                                    OutlinedButton(
                                        enabled = !isSavingAvatar,
                                        onClick = {
                                            AssistantAvatarHelper.removeAvatar(appCtx)
                                            assistantHasAvatar = false
                                            assistantAvatarBitmap = null
                                            scope.launch {
                                                snackbarHostState.showSnackbar(resCtx.getString(R.string.settings_s_9baf41))
                                            }
                                        }
                                    ) {
                                        Text(stringResource(R.string.common_reset))
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
                            label = { Text(stringResource(R.string.settings_s_c1d4a5)) },
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
                                Text(stringResource(R.string.common_reset))
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_s_bcb30b),
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
                            text = stringResource(R.string.settings_s_043dab, assistantPersonalityPreset.promptInstruction),
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
                                    stringResource(R.string.settings_s_c287a9, assistantCustomPersonality.length, PhantomPrefs.MAX_ASSISTANT_CUSTOM_PERSONALITY_CHARS)
                                } else {
                                    stringResource(R.string.settings_s_68765d, assistantCustomPersonality.length, PhantomPrefs.MAX_ASSISTANT_CUSTOM_PERSONALITY_CHARS)
                                }
                            )
                        },
                        placeholder = { Text(stringResource(R.string.settings_s_e1b2a2)) },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_s_c3194a),
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
                        text = stringResource(R.string.settings_s_6355d0),
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
                                text = stringResource(R.string.settings_s_08ce4e),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = stringResource(R.string.settings_s_0b2dda),
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
                    title = stringResource(R.string.settings_s_053081),
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
                        Text(stringResource(R.string.settings_s_58a1df), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        TextButton(onClick = { showPresetDialog = true }) {
                            Text(stringResource(R.string.settings_s_a2ede6))
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
                            placeholder = { Text(stringResource(R.string.settings_s_799a6b)) }
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
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.common_add))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.settings_s_0ad3a4), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            FilterRule.ACTION_FILTER to stringResource(R.string.settings_s_d4ac33),
                            FilterRule.ACTION_DROP to stringResource(R.string.settings_s_72fbe9),
                            FilterRule.ACTION_SCAM to stringResource(R.string.settings_s_3dc2a7)
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
                            label = { Text(stringResource(R.string.common_all_apps)) }
                        )
                        FilterChip(
                            selected = !isAllApps,
                            onClick = { isAllApps = false },
                            label = { Text(stringResource(R.string.settings_s_cff87d)) }
                        )
                    }
                    if (!isAllApps) {
                        OutlinedTextField(
                            value = targetPackageName,
                            onValueChange = { targetPackageName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(stringResource(R.string.settings_s_180245)) },
                            placeholder = { Text(stringResource(R.string.settings_s_a2075f)) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (isLoadingRules) {
                        CircularProgressIndicator()
                    } else if (rules.isEmpty()) {
                        Text(stringResource(R.string.settings_s_f80118), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        title = stringResource(R.string.catalog_notif_ongoing_title),
                        subtitle = stringResource(R.string.settings_s_709191),
                        checked = recordOngoing,
                        onCheckedChange = { checked ->
                            recordOngoing = checked
                            PhantomPrefs.setRecordOngoingEnabled(appCtx, checked)
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    val secInt = throttleSec.roundToInt()
                    Text(
                        text = if (secInt == 0) stringResource(R.string.settings_s_25ffab) else stringResource(R.string.settings_s_24faf8, secInt),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_s_d2c2bf),
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
                        title = stringResource(R.string.catalog_notif_digit_variance_title),
                        subtitle = stringResource(R.string.settings_s_75a650),
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
                            text = stringResource(R.string.settings_s_1ec470, appThrottleMap.size),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        PhantomChevron(
 expanded = appThrottleExpanded.value,
                            contentDescription = if (appThrottleExpanded.value) stringResource(R.string.settings_s_f574a7) else stringResource(R.string.settings_s_67f5ca),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_s_4d1a36),
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
                            text = stringResource(R.string.settings_s_8bafeb),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Spacer(Modifier.height(4.dp))
                        appThrottleMap.entries.sortedBy { it.key }.forEach { (pkg, sec) ->
                            val label = if (sec == 0) resCtx.getString(R.string.settings_s_0a50ae) else resCtx.getString(R.string.settings_s_10d1e2, sec)
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
                                    Text(resCtx.getString(R.string.chat_s_2f752c), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { showAppThrottlePicker = true },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(stringResource(R.string.settings_s_b3286b))
                    }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    Text(stringResource(R.string.catalog_notif_interaction_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
                        title = stringResource(R.string.catalog_notif_persistent_icons_title),
                        subtitle = stringResource(R.string.settings_s_f6d6b0),
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

                    Text(stringResource(R.string.catalog_notif_style_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
                        title = stringResource(R.string.catalog_notif_swipe_delete_title),
                        subtitle = stringResource(R.string.settings_s_47fff5),
                        checked = swipeToDeleteEnabled,
                        onCheckedChange = { checked ->
                            swipeToDeleteEnabled = checked
                            PhantomPrefs.setSwipeToDeleteEnabled(appCtx, checked)
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SettingSwitchRow(
                        title = stringResource(R.string.catalog_notif_quiet_hours_title),
                        subtitle = stringResource(R.string.settings_s_9fd480),
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
                                        stringResource(R.string.settings_s_0160f4),
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
                                        stringResource(R.string.settings_s_da88ea),
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
                        title = stringResource(R.string.catalog_notif_reply_proxy_title),
                        subtitle = stringResource(R.string.catalog_notif_reply_proxy_desc),
                        checked = replyProxyEnabled,
                        onCheckedChange = {
                            replyProxyEnabled = it
                            PhantomPrefs.setReplyProxyEnabled(appCtx, it)
                        }
                    )
                    if (replyProxyEnabled) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.catalog_notif_reply_proxy_timeout_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                15 to stringResource(R.string.settings_s_fc4d47),
                                30 to stringResource(R.string.settings_s_e3dc27),
                                60 to stringResource(R.string.settings_s_81bff1),
                                0 to stringResource(R.string.settings_s_77bb14)
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
                        text = stringResource(R.string.catalog_notif_my_display_names_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_s_911977),
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
                                            contentDescription = stringResource(R.string.settings_s_58c704, displayName),
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
                            placeholder = { Text(stringResource(R.string.settings_s_d77ac6)) }
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
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.settings_s_3780b5))
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    OutlinedButton(
                        onClick = { showClearReadConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.catalog_notif_clear_read_title))
                    }
                }
            }
            }

            // 4. 資料管理
            if ("data" in visibleCardKeys) {
            item(key = "card_data") {
                CollapsibleSettingsCard(
                    title = stringResource(R.string.settings_s_ed7498),
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
                            Text(stringResource(R.string.catalog_data_muted_apps_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                stringResource(R.string.settings_s_ad4f43),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { showAppPickerDialog = true }) {
                            Text(stringResource(R.string.settings_s_6c291d))
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
                                        snackbarHostState.showSnackbar(resCtx.getString(R.string.settings_s_216aba, appLabel))
                                    }
                                }) {
                                    Text(resCtx.getString(R.string.main_s_fd9e9a))
                                }
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))

                    OutlinedButton(
                        onClick = { ctx.startActivity(Intent(ctx, TagManagementActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth().pressableScale()
                    ) {
                        Text(stringResource(R.string.settings_s_885cfb))
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
                        Text(stringResource(R.string.settings_s_c51dc4, whitelistCount))
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))

                    // 保留天數與則數上限
                    Text(stringResource(R.string.settings_s_6b381c), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
                    Text(stringResource(R.string.settings_s_51b373), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
                    Text(stringResource(R.string.settings_s_7d464e), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    val statsSnapshot = storageStats
                    if (statsSnapshot == null) {
                        Text(
                            text = stringResource(R.string.settings_s_b2e223),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.settings_s_011b70, Formatter.formatShortFileSize(appCtx, statsSnapshot.dbBytes)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.settings_s_fc5d65, Formatter.formatShortFileSize(appCtx, statsSnapshot.backgroundsBytes)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.settings_s_0832a8, Formatter.formatShortFileSize(appCtx, statsSnapshot.cacheBytes)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.settings_s_9a230c, Formatter.formatShortFileSize(appCtx, statsSnapshot.totalBytes)),
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
                                    resCtx.getString(R.string.settings_s_7767a6, Formatter.formatShortFileSize(appCtx, beforeBytes), Formatter.formatShortFileSize(appCtx, afterBytes))
                                )
                            }
                        },
                        enabled = !isVacuuming,
                        modifier = Modifier.fillMaxWidth().pressableScale()
                    ) {
                        Text(if (isVacuuming) stringResource(R.string.settings_s_e594e4) else stringResource(R.string.settings_s_bf7818))
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))

                    OutlinedButton(
                        onClick = { showExportDialog = true },
                        enabled = !isExporting,
                        modifier = Modifier.fillMaxWidth().pressableScale()
                    ) {
                        Text(if (isExporting) stringResource(R.string.settings_s_0fa176) else stringResource(R.string.settings_s_50de8e))
                    }
                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showDeleteAllConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.settings_s_20a08d))
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
                        Text(stringResource(R.string.settings_s_ffe0d8))
                    }
                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            PhantomPrefs.setSettingsTourCompleted(appCtx, false)
                            tourStepIndex = 1
                        },
                        modifier = Modifier.fillMaxWidth().pressableScale()
                    ) {
                        Text(stringResource(R.string.settings_s_7d2e2f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_s_f1336b),
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
                    title = stringResource(R.string.settings_s_754ec0),
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
                    Text(stringResource(R.string.settings_s_5285ae), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SettingSwitchRow(
                        title = stringResource(R.string.catalog_appearance_high_contrast_title),
                        subtitle = stringResource(R.string.catalog_appearance_high_contrast_desc),
                        checked = highContrastEnabled,
                        onCheckedChange = { checked ->
                            ThemePreferences.setHighContrast(appCtx, checked)
                        }
                    )

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    var currentLangMode by remember { mutableStateOf(com.phantom.checker.util.AppLocaleHelper.getEffectiveMode(appCtx)) }
                    Text(
                        text = stringResource(R.string.catalog_appearance_language_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_language_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val langOptions = listOf(
                            PhantomPrefs.APP_LOCALE_SYSTEM to stringResource(R.string.settings_language_follow_system),
                            PhantomPrefs.APP_LOCALE_ZH_TW to stringResource(R.string.settings_language_zh_tw),
                            PhantomPrefs.APP_LOCALE_EN to stringResource(R.string.settings_language_en)
                        )
                        langOptions.forEach { (mode, label) ->
                            FilterChip(
                                selected = currentLangMode == mode,
                                onClick = {
                                    currentLangMode = mode
                                    val hostActivity = ctx as? android.app.Activity
                                    if (hostActivity != null) {
                                        com.phantom.checker.util.AppLocaleHelper.applyLanguageMode(hostActivity, mode)
                                    } else {
                                        PhantomPrefs.setAppLanguageMode(appCtx, mode)
                                    }
                                },
                                label = { Text(label) }
                            )
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        SettingSwitchRow(
                            title = stringResource(R.string.settings_s_bd12a6),
                            subtitle = stringResource(R.string.settings_s_17bb7c),
                            checked = dynamicColorEnabled,
                            onCheckedChange = { checked ->
                                ThemePreferences.setDynamicColor(appCtx, checked)
                            }
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(stringResource(R.string.settings_s_431cae), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
                        title = stringResource(R.string.catalog_appearance_haptic_title),
                        subtitle = stringResource(R.string.settings_s_6aee22),
                        checked = hapticEnabled,
                        onCheckedChange = { checked ->
                            hapticEnabled = checked
                            PhantomPrefs.setHapticFeedbackEnabled(appCtx, checked)
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SettingSwitchRow(
                        title = stringResource(R.string.catalog_appearance_glass_effect_title),
                        subtitle = stringResource(R.string.settings_s_7cc88f),
                        checked = glassEffectEnabled,
                        onCheckedChange = { checked ->
                            ThemePreferences.setGlassEffectEnabled(appCtx, checked)
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(stringResource(R.string.catalog_appearance_tab_bar_mode_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        text = stringResource(R.string.settings_s_02be9a),
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
                        text = if (listenerEnabled) stringResource(R.string.settings_s_637aa5) else stringResource(R.string.settings_s_adc1ad),
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
                            Text(stringResource(R.string.common_s_dc348a))
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    Text(
                        text = if (batteryWhitelisted) stringResource(R.string.settings_s_1a44b0) else stringResource(R.string.settings_s_2268ff),
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
                            Text(stringResource(R.string.settings_s_b29e94))
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    Text(
                        text = stringResource(R.string.settings_s_a126ca),
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
                            text = stringResource(R.string.settings_s_e008e2),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    OutlinedButton(
                        onClick = { tryLaunchIntents(ctx, oemGuide.intents) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_s_173e7b))
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    Text(
                        text = if (fgServiceRunning) stringResource(R.string.settings_s_26059e) else stringResource(R.string.settings_s_eb75a2),
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
                            Text(stringResource(R.string.settings_s_755ae3))
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
                            title = stringResource(R.string.search_s_1579f9),
                            subtitle = stringResource(R.string.settings_s_5e72dc),
                            shape = AuroraTokens.segmentedItemShape(0, 2),
                            highlighted = activeHighlightSettingId == "permission_hub",
                            onClick = {
                                UiLayoutPrefs.recordRecentSetting(appCtx, "permission_hub")
                                ctx.startActivity(Intent(ctx, PermissionHubActivity::class.java))
                            }
                        )
                        com.phantom.checker.ui.components.SettingsNavigationRow(
                            title = stringResource(R.string.catalog_linked_apps_center_title),
                            subtitle = stringResource(R.string.settings_s_8943b2),
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
                            title = stringResource(R.string.catalog_whitelist_management_title),
                            subtitle = stringResource(R.string.settings_s_18a130),
                            shape = AuroraTokens.segmentedItemShape(0, 3),
                            highlighted = activeHighlightSettingId == "whitelist_management",
                            onClick = {
                                UiLayoutPrefs.recordRecentSetting(appCtx, "whitelist_management")
                                ctx.startActivity(Intent(ctx, WhitelistManagementActivity::class.java))
                            }
                        )
                        com.phantom.checker.ui.components.SettingsNavigationRow(
                            title = stringResource(R.string.catalog_ai_rules_title),
                            subtitle = stringResource(R.string.settings_s_300b47),
                            shape = AuroraTokens.segmentedItemShape(1, 3),
                            highlighted = activeHighlightSettingId == "ai_rules",
                            onClick = {
                                UiLayoutPrefs.recordRecentSetting(appCtx, "ai_rules")
                                ctx.startActivity(Intent(ctx, TagManagementActivity::class.java))
                            }
                        )
                        com.phantom.checker.ui.components.SettingsNavigationRow(
                            title = stringResource(R.string.catalog_ai_scam_center_title),
                            subtitle = stringResource(R.string.settings_s_214be4),
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
                                            scope.launch { snackbarHostState.showSnackbar(resCtx.getString(R.string.main_s_12ce1c, advItem.title)) }
                                        } else {
                                            val added = UiLayoutPrefs.addPinned(appCtx, advItem.id)
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    if (added) resCtx.getString(R.string.settings_s_15b5b5, advItem.title)
                                                    else resCtx.getString(R.string.settings_home_s_73c93d, UiLayoutPrefs.MAX_PINNED)
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
    val resCtx = LocalContext.current
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
        title = stringResource(R.string.settings_s_3ced8e),
        expanded = expanded,
        onToggle = onToggle,
        glassState = glassState,
        highlighted = highlighted
    ) {
        SettingSwitchRow(
            title = stringResource(R.string.catalog_intel_enabled_title),
            subtitle = stringResource(R.string.settings_s_9ac465),
            checked = intelEnabled,
            onCheckedChange = { checked ->
                intelEnabled = checked
                PhantomPrefs.setIntelEnabled(appCtx, checked)
            }
        )

        if (intelEnabled) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SettingSwitchRow(
                title = stringResource(R.string.settings_s_8bfc95),
                subtitle = stringResource(R.string.settings_s_94cb6a),
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
                    ApiKeySource.ENCRYPTED_PREFS -> stringResource(R.string.settings_s_0f529c)
                    else -> stringResource(R.string.settings_s_8c933e)
                },
                checked = safeBrowsingEnabled,
                onCheckedChange = { checked ->
                    safeBrowsingEnabled = checked
                    PhantomPrefs.setIntelSafeBrowsingEnabled(appCtx, checked)
                }
            )
            IntelApiKeyEditor(
                label = "Safe Browsing API Key",
                placeholder = if (sbKeySource == ApiKeySource.ENCRYPTED_PREFS) stringResource(R.string.settings_s_e0c612) else stringResource(R.string.settings_s_c2f617),
                helperText = stringResource(R.string.settings_s_69035b),
                value = sbKeyInput,
                onValueChange = { sbKeyInput = it },
                hasSavedKey = sbKeySource == ApiKeySource.ENCRYPTED_PREFS,
                onSave = {
                    IntelClient.setSafeBrowsingApiKey(appCtx, sbKeyInput)
                    sbKeyInput = ""
                    sbKeySource = IntelClient.getSafeBrowsingKeySource(appCtx)
                    onShowMessage(resCtx.getString(R.string.settings_s_f478e4))
                },
                onClear = {
                    IntelClient.setSafeBrowsingApiKey(appCtx, "")
                    sbKeyInput = ""
                    sbKeySource = IntelClient.getSafeBrowsingKeySource(appCtx)
                    onShowMessage(resCtx.getString(R.string.settings_s_14faa5))
                }
            )

            Spacer(Modifier.height(8.dp))

            SettingSwitchRow(
                title = stringResource(R.string.settings_s_3bd63d),
                subtitle = if (ptKeyConfigured) {
                    stringResource(R.string.settings_s_b2fbef)
                } else {
                    stringResource(R.string.settings_s_f6c991)
                },
                checked = phishTankEnabled,
                onCheckedChange = { checked ->
                    phishTankEnabled = checked
                    PhantomPrefs.setIntelPhishTankEnabled(appCtx, checked)
                }
            )
            IntelApiKeyEditor(
                label = stringResource(R.string.settings_s_cbc93b),
                placeholder = if (ptKeyConfigured) stringResource(R.string.settings_s_e0c612) else stringResource(R.string.settings_s_399e39),
                helperText = stringResource(R.string.settings_s_c8cf4f),
                value = ptKeyInput,
                onValueChange = { ptKeyInput = it },
                hasSavedKey = ptKeyConfigured,
                onSave = {
                    IntelClient.setPhishTankAppKey(appCtx, ptKeyInput)
                    ptKeyInput = ""
                    ptKeyConfigured = IntelClient.getPhishTankAppKey(appCtx) != null
                    onShowMessage(resCtx.getString(R.string.settings_s_a9d4a5))
                },
                onClear = {
                    IntelClient.setPhishTankAppKey(appCtx, "")
                    ptKeyInput = ""
                    ptKeyConfigured = false
                    onShowMessage(resCtx.getString(R.string.settings_s_0cf0bb))
                }
            )

            Spacer(Modifier.height(8.dp))

            SettingSwitchRow(
                title = stringResource(R.string.settings_s_21d940),
                subtitle = if (caKeyConfigured) {
                    stringResource(R.string.settings_s_53cc89)
                } else {
                    stringResource(R.string.settings_s_f0c755)
                },
                checked = chainabuseEnabled,
                onCheckedChange = { checked ->
                    chainabuseEnabled = checked
                    PhantomPrefs.setIntelChainabuseEnabled(appCtx, checked)
                }
            )
            IntelApiKeyEditor(
                label = "Chainabuse API Key",
                placeholder = if (caKeyConfigured) stringResource(R.string.settings_s_e0c612) else stringResource(R.string.settings_s_1a99d5),
                helperText = stringResource(R.string.settings_s_e65b6e),
                value = caKeyInput,
                onValueChange = { caKeyInput = it },
                hasSavedKey = caKeyConfigured,
                onSave = {
                    IntelClient.setChainabuseApiKey(appCtx, caKeyInput)
                    caKeyInput = ""
                    caKeyConfigured = IntelClient.isChainabuseKeyConfigured(appCtx)
                    onShowMessage(resCtx.getString(R.string.settings_s_25c0f3))
                },
                onClear = {
                    IntelClient.setChainabuseApiKey(appCtx, "")
                    caKeyInput = ""
                    caKeyConfigured = false
                    onShowMessage(resCtx.getString(R.string.settings_s_fadb22))
                }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(
                text = stringResource(R.string.settings_s_afbe5f),
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
                        Text(if (isTesting) resCtx.getString(R.string.settings_s_58b883) else resCtx.getString(R.string.settings_s_48b807))
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            OutlinedButton(
                onClick = {
                    scope.launch {
                        IntelClient.clearCache(appCtx)
                        onShowMessage(resCtx.getString(R.string.settings_s_760d76))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_s_846354))
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
                Text(stringResource(R.string.common_save))
            }
            if (hasSavedKey) {
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.common_clear))
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
    val resCtx = LocalContext.current
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
        !shizukuState.isInstalled -> stringResource(R.string.settings_s_dc0d42)
        !shizukuState.isBinderAlive -> stringResource(R.string.settings_s_7cdc0c)
        !shizukuState.isPermissionGranted -> stringResource(R.string.settings_s_d7dfa8)
        else -> stringResource(R.string.settings_s_9e1c90, shizukuState.uid)
    }

    CollapsibleSettingsCard(
        title = stringResource(R.string.settings_s_9244b5),
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
            text = stringResource(R.string.settings_s_a8d2ef),
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
                    Text(stringResource(R.string.settings_s_dcc8f7))
                }
            }
            OutlinedButton(
                onClick = {
                    ShizukuManager.refreshState(appCtx)
                    suspendedApps = PhantomPrefs.getShizukuSuspendedApps(appCtx)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.settings_s_00c95b))
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
                        onShowMessage(resCtx.getString(R.string.settings_s_042209, okCount, res.size))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isBusy) stringResource(R.string.settings_s_797374) else stringResource(R.string.settings_s_7a3463))
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
                Text(stringResource(R.string.settings_s_19b8df))
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_s_bde005), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (shizukuState.isWatchdogActive) stringResource(R.string.settings_s_239685) else stringResource(R.string.settings_s_a38205),
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
                text = stringResource(R.string.settings_s_05f82f),
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
                text = stringResource(R.string.settings_s_75a98c),
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
                                onShowMessage(if (res.isSuccess) resCtx.getString(R.string.settings_s_a8a192, pkg) else resCtx.getString(R.string.settings_s_159c42, res.output))
                            }
                        }
                    ) {
                        Text(resCtx.getString(R.string.settings_s_14e05f))
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
    val resCtx = LocalContext.current
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
        title = stringResource(R.string.settings_s_c77782),
        collapsedSummary = if (enabled) stringResource(R.string.settings_s_7ef24d, freqHours) else stringResource(R.string.settings_s_3cbf66),
        expanded = expanded,
        onToggle = onToggle,
        glassState = glassState,
        highlighted = highlighted
    ) {
        SettingSwitchRow(
            title = stringResource(R.string.settings_s_30d213),
            subtitle = stringResource(R.string.settings_s_76d807),
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
            text = stringResource(R.string.settings_s_02bba4),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
        )
        SettingSwitchRow(
            title = stringResource(R.string.catalog_proactive_extract_enabled_title),
            subtitle = stringResource(R.string.settings_s_9495a3),
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
            text = stringResource(R.string.settings_s_3a10f2),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            listOf(6 to stringResource(R.string.settings_s_5aba96), 12 to stringResource(R.string.settings_s_80bb29), 24 to stringResource(R.string.settings_s_67cb4b)).forEach { (h, label) ->
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
                Text(stringResource(R.string.settings_s_28535d))
            }
            Button(
                onClick = {
                    if (!isQueryingNow) {
                        isQueryingNow = true
                        previewStatus = resCtx.getString(R.string.settings_s_115411)
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
                    Text(stringResource(R.string.settings_s_115411))
                } else {
                    Text(stringResource(R.string.settings_s_b6a76b))
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
                title = resCtx.getString(R.string.settings_s_01ce6b),
                releaseNotes = md
            )
        }
    }

    if (showConsentDialog) {
        AlertDialog(
            onDismissRequest = { showConsentDialog = false },
            title = { Text(stringResource(R.string.settings_s_89a3fc)) },
            text = {
                Text(
                    stringResource(R.string.settings_s_3f78ab) +
                        stringResource(R.string.settings_s_f2800c) +
                        stringResource(R.string.settings_s_fe901d) +
                        stringResource(R.string.settings_s_689e68)
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
                        onShowMessage(resCtx.getString(R.string.settings_s_ebdae5))
                    }
                ) { Text(stringResource(R.string.settings_s_5a597a)) }
            },
            dismissButton = {
                TextButton(onClick = { showConsentDialog = false }) { Text(stringResource(R.string.settings_s_c16eb1)) }
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
                        text = stringResource(R.string.settings_s_48f575),
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
    val resCtx = LocalContext.current
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
            downloadStatusText = resCtx.getString(R.string.settings_s_039d16)
            return
        }
        if (!AppUpdateHelper.canRequestPackageInstallsCompat(ctx)) {
            pendingInstallAfterUnknownSourceGrant = true
            showUnknownSourceDialog = true
            isDownloadError = false
            downloadStatusText = resCtx.getString(R.string.settings_s_5feb09)
            return
        }
        pendingInstallAfterUnknownSourceGrant = false
        if (isAutoTrigger) {
            PhantomPrefs.setActiveApkAutoInstallLaunched(appCtx, true)
        }
        AppUpdateHelper.launchApkInstaller(ctx, file).onSuccess {
            isDownloadError = false
            downloadStatusText = resCtx.getString(R.string.settings_s_26343c)
        }.onFailure { err ->
            isDownloadError = true
            downloadStatusText = err.message ?: resCtx.getString(R.string.settings_s_2fda8a)
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
                downloadStatusText = resCtx.getString(R.string.settings_s_503bd5, state.file.name, pctSuffix)
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
                    downloadStatusText = resCtx.getString(R.string.settings_s_610a25, state.file.name)
                    attemptInstall(state.file, isAutoTrigger = true)
                } else {
                    downloadStatusText = resCtx.getString(R.string.settings_s_cc1889, state.version)
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
            title = { Text(stringResource(R.string.settings_s_cc961c)) },
            text = {
                Text(stringResource(R.string.settings_s_c67663))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnknownSourceDialog = false
                        AppUpdateHelper.openUnknownAppSourcesSettings(ctx)
                    }
                ) {
                    Text(stringResource(R.string.settings_s_5df1b3))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnknownSourceDialog = false }) {
                    Text(stringResource(R.string.settings_s_12826a))
                }
            }
        )
    }

    CollapsibleSettingsCard(
        title = stringResource(R.string.settings_cat_about_title),
        expanded = expanded,
        onToggle = onToggle,
        glassState = glassState,
        modifier = modifier,
        highlighted = highlighted
    ) {
        // §4.9 版本狀態卡（保留 AppUpdateHelper / ReleaseNotesPreviewBox / 未知來源 / snooze 狀態機 0 diff）
        val statusPillText = when {
            isDownloading -> stringResource(R.string.settings_s_128dd0)
            isDownloadError -> stringResource(R.string.settings_s_747158)
            updateResult is UpdateCheckResult.UpdateAvailable ->
                stringResource(R.string.settings_s_61d2c6, (updateResult as UpdateCheckResult.UpdateAvailable).latestVersion)
            updateResult is UpdateCheckResult.UpToDate -> stringResource(R.string.settings_s_5985ee)
            isCheckingUpdate -> stringResource(R.string.settings_s_d61e98)
            else -> stringResource(R.string.settings_s_d82d0d)
        }
        val statusPillColor = when {
            isDownloadError -> MaterialTheme.colorScheme.errorContainer
            updateResult is UpdateCheckResult.UpdateAvailable -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        }
        Surface(
            shape = RoundedCornerShape(AuroraTokens.CornerCard),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_s_e38167),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = statusPillColor
                    ) {
                        Text(
                            text = statusPillText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.settings_s_125779, versionDisplay),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.settings_s_86483f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        var updateAutoCheck by remember { mutableStateOf(PhantomPrefs.isUpdateAutoCheckEnabled(appCtx)) }
        val updateBusVer by PhantomSettingsBus.version.collectAsState()
        LaunchedEffect(updateBusVer) {
            if (updateBusVer > 0L) {
                updateAutoCheck = PhantomPrefs.isUpdateAutoCheckEnabled(appCtx)
            }
        }
        SettingSwitchRow(
            title = stringResource(R.string.catalog_update_auto_check_title),
            subtitle = stringResource(R.string.catalog_update_auto_check_desc),
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
            text = stringResource(R.string.settings_s_6f24ce),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(30 to stringResource(R.string.settings_s_e3dc27), 60 to stringResource(R.string.settings_s_599488), 240 to stringResource(R.string.settings_s_b1b72c)).forEach { (mins, label) ->
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
            title = stringResource(R.string.settings_s_6c69bc),
            subtitle = stringResource(R.string.settings_s_2a74bc),
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
                Text(stringResource(R.string.settings_s_b5231f))
            } else {
                Text(stringResource(R.string.settings_s_57d698))
            }
        }
        when (val res = updateResult) {
            is UpdateCheckResult.UpToDate -> {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_s_960aec, res.currentVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                val dateSuffix = res.publishedDate.takeIf { it.isNotBlank() }?.let { "／$it" }.orEmpty()
                ReleaseNotesPreviewBox(
                    title = stringResource(R.string.settings_s_58c50e, res.currentVersion, dateSuffix),
                    releaseNotes = res.releaseNotes
                )
            }
            is UpdateCheckResult.UpdateAvailable -> {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_s_f59e55, res.latestVersion, res.publishedDate),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                ReleaseNotesPreviewBox(
                    title = stringResource(R.string.main_s_0d3826, res.latestVersion, res.publishedDate),
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
                            Text(stringResource(R.string.settings_s_ce9649, res.latestVersion))
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
                                            downloadStatusText = resCtx.getString(R.string.settings_s_584371, res.latestVersion)
                                        } else {
                                            isDownloadError = true
                                            downloadStatusText = resCtx.getString(R.string.settings_s_bbeee2)
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
                                Text(stringResource(R.string.settings_s_80fa27, pctLabel))
                            } else if (isDownloadError) {
                                Text(stringResource(R.string.settings_s_a23598, res.latestVersion))
                            } else {
                                Text(stringResource(R.string.settings_s_cc54e3))
                            }
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.settings_s_1b4cdf),
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
                        Text(stringResource(R.string.settings_s_ceecc9))
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
                    Text(stringResource(R.string.settings_s_ceecc9))
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
            Text(stringResource(R.string.settings_s_490ace))
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(
            text = stringResource(R.string.settings_s_bdf986),
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
                                text = stringResource(R.string.settings_s_7f8eeb),
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
                contentDescription = if (expanded) stringResource(R.string.settings_s_52c158) else stringResource(R.string.settings_s_6ce777),
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
    val resCtx = LocalContext.current
    val actionLabel = when (rule.action) {
        FilterRule.ACTION_DROP -> stringResource(R.string.settings_s_72fbe9)
        FilterRule.ACTION_SCAM -> stringResource(R.string.settings_s_3dc2a7)
        else -> stringResource(R.string.settings_s_d4ac33)
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
                text = stringResource(R.string.settings_s_773aaa, if (rule.packageName.isNullOrBlank()) resCtx.getString(R.string.common_all_apps) else rule.packageName, actionLabel),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FluidSwitch(checked = rule.isActive, onCheckedChange = onToggleActive)
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.settings_s_7a5ff6),
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
        title = { Text(stringResource(R.string.settings_s_c3e7db)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.settings_s_782eec), style = MaterialTheme.typography.labelMedium)
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
                    placeholder = { Text(stringResource(R.string.settings_s_5bbd9b)) }
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
                                Text(stringResource(R.string.settings_s_fd8f57), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
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
    val presetOptions = listOf(0 to stringResource(R.string.settings_s_3e6acf), 30 to stringResource(R.string.settings_s_262ed7), 60 to stringResource(R.string.settings_s_005d45), 120 to stringResource(R.string.settings_s_184007), 300 to stringResource(R.string.settings_s_1464e0))

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
        title = { Text(stringResource(R.string.settings_s_fb8fce)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.settings_s_47e13d), style = MaterialTheme.typography.labelMedium)
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
                    placeholder = { Text(stringResource(R.string.settings_s_5bbd9b)) }
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
                Text(stringResource(R.string.common_close))
            }
        }
    )
}

/** D6 裁定：設定教學由 9 步精簡為 5 步（搜尋／釘選／分類／進階折疊／重播） */
private enum class SettingsTourStep(
    val index: Int,
    val cardKey: String,
    val scrollCardKey: String,
    val title: String,
    val description: String
) {
    SEARCH(
        index = 1,
        cardKey = "search",
        scrollCardKey = "search",
        title = com.phantom.checker.PhantomApplication.str(R.string.settings_tour_1_title),
        description = com.phantom.checker.PhantomApplication.str(R.string.settings_tour_1_body)
    ),
    PINNED(
        index = 2,
        cardKey = "pinned",
        scrollCardKey = "pinned",
        title = com.phantom.checker.PhantomApplication.str(R.string.settings_tour_2_title),
        description = com.phantom.checker.PhantomApplication.str(R.string.settings_tour_2_body)
    ),
    CATEGORIES(
        index = 3,
        cardKey = "categories",
        scrollCardKey = "categories",
        title = com.phantom.checker.PhantomApplication.str(R.string.settings_tour_3_title),
        description = com.phantom.checker.PhantomApplication.str(R.string.settings_tour_3_body)
    ),
    ADVANCED(
        index = 4,
        cardKey = "advanced",
        scrollCardKey = "advanced",
        title = com.phantom.checker.PhantomApplication.str(R.string.settings_tour_4_title),
        description = com.phantom.checker.PhantomApplication.str(R.string.settings_tour_4_body)
    ),
    REPLAY(
        index = 5,
        cardKey = "replay",
        scrollCardKey = "replay",
        title = com.phantom.checker.PhantomApplication.str(R.string.settings_tour_5_title),
        description = com.phantom.checker.PhantomApplication.str(R.string.settings_tour_5_body)
    );

    companion object {
        fun fromIndex(index: Int): SettingsTourStep =
            entries.firstOrNull { it.index == index } ?: SEARCH
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
                            text = stringResource(R.string.settings_s_0ce969, step.index, totalSteps, step.title),
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
                                Text(stringResource(R.string.settings_s_3002a7))
                            }
                            Button(onClick = throttledNext) {
                                Text(if (step.index == totalSteps) stringResource(R.string.common_done) else stringResource(R.string.settings_s_ea0ef2))
                            }
                        }
                    }
                }
            }
        }
    }
}




