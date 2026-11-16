package com.phantom.checker.ui

import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.service.DailySummaryPolicy
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
        setContent {
            PhantomCheckTheme {
                FilterSettingsScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSettingsScreen(onBack: () -> Unit) {
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
    var alertKeywords by remember { mutableStateOf(PhantomPrefs.getAlertKeywords(appCtx)) }
    var newAlertKeyword by remember { mutableStateOf("") }
    var quietHoursEnabled by remember { mutableStateOf(PhantomPrefs.isQuietHoursEnabled(appCtx)) }
    var quietStartMinute by remember { mutableIntStateOf(PhantomPrefs.getQuietHoursStartMinute(appCtx)) }
    var quietEndMinute by remember { mutableIntStateOf(PhantomPrefs.getQuietHoursEndMinute(appCtx)) }

    // AI 與分析設定
    var tokenSaverEnabled by remember { mutableStateOf(PhantomPrefs.isTokenSaverEnabled(appCtx)) }
    var aiRiskEnabled by remember { mutableStateOf(PhantomPrefs.isAiRiskEnabled(appCtx)) }
    var aiAutoTagEnabled by remember { mutableStateOf(PhantomPrefs.isAiAutoTagEnabled(appCtx)) }
    var chatSuggestionsEnabled by remember { mutableStateOf(PhantomPrefs.isChatSuggestionsEnabled(appCtx)) }
    var dailyAiLimit by remember { mutableIntStateOf(PhantomPrefs.getDailyAiLimit(appCtx)) }
    val todayAiUsage = remember { PhantomPrefs.getTodayAiUsage(appCtx) }
    var dailySummaryEnabled by remember { mutableStateOf(PhantomPrefs.isDailySummaryEnabled(appCtx)) }
    var dailySummaryNotifEnabled by remember { mutableStateOf(PhantomPrefs.isDailySummaryNotifEnabled(appCtx)) }
    var summaryHour by remember { mutableIntStateOf(PhantomPrefs.getDailySummaryHour(appCtx)) }
    var summaryMinute by remember { mutableIntStateOf(PhantomPrefs.getDailySummaryMinute(appCtx)) }

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

    // 六卡收合狀態
    var expandQuick by remember { mutableStateOf(PhantomPrefs.isSettingsCardExpanded(appCtx, "quick")) }
    var expandAi by remember { mutableStateOf(PhantomPrefs.isSettingsCardExpanded(appCtx, "ai")) }
    var expandNotif by remember { mutableStateOf(PhantomPrefs.isSettingsCardExpanded(appCtx, "notif")) }
    var expandData by remember { mutableStateOf(PhantomPrefs.isSettingsCardExpanded(appCtx, "data")) }
    var expandAppearance by remember { mutableStateOf(PhantomPrefs.isSettingsCardExpanded(appCtx, "appearance")) }
    var expandShizuku by remember { mutableStateOf(PhantomPrefs.isSettingsCardExpanded(appCtx, "shizuku_advanced")) }

    // 設定頁高亮導覽狀態（0 = 未顯示，1..6 = 對應步驟）
    var tourStepIndex by remember {
        mutableIntStateOf(if (PhantomPrefs.isSettingsTourCompleted(appCtx)) 0 else 1)
    }
    val cardBounds = remember { mutableStateMapOf<String, Rect>() }
    val listState = rememberLazyListState()

    LaunchedEffect(tourStepIndex) {
        if (tourStepIndex in 1..6) {
            when (tourStepIndex) {
                1 -> {
                    expandQuick = true
                    PhantomPrefs.setSettingsCardExpanded(appCtx, "quick", true)
                }
                2 -> {
                    expandAi = true
                    PhantomPrefs.setSettingsCardExpanded(appCtx, "ai", true)
                }
                3 -> {
                    expandNotif = true
                    PhantomPrefs.setSettingsCardExpanded(appCtx, "notif", true)
                }
                4 -> {
                    expandData = true
                    PhantomPrefs.setSettingsCardExpanded(appCtx, "data", true)
                }
                5 -> {
                    expandAppearance = true
                    PhantomPrefs.setSettingsCardExpanded(appCtx, "appearance", true)
                }
                6 -> {
                    expandShizuku = true
                    PhantomPrefs.setSettingsCardExpanded(appCtx, "shizuku_advanced", true)
                }
            }
            listState.animateScrollToItem(tourStepIndex - 1)
        }
    }

    // 外觀與互動
    val currentThemeMode = ThemePreferences.getThemeMode(appCtx)
    val dynamicColorEnabled = ThemePreferences.isDynamicColor(appCtx)
    val fontScaleMode = ThemePreferences.getFontScaleMode(appCtx)
    var hapticEnabled by remember { mutableStateOf(PhantomPrefs.isHapticFeedbackEnabled(appCtx)) }
    val glassEffectEnabled = ThemePreferences.isGlassEffectEnabled(appCtx)

    // 保持運作狀態
    var listenerEnabled by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(appCtx).contains(appCtx.packageName))
    }
    var batteryWhitelisted by remember { mutableStateOf(isIgnoringBatteryOptimizations(appCtx)) }
    var oemAutostartConfirmed by remember { mutableStateOf(PhantomPrefs.isOemAutostartConfirmed(appCtx)) }
    var fgServiceRunning by remember { mutableStateOf(isStatsForegroundServiceRunning(appCtx)) }
    val oemGuide = remember { buildOemGuide(appCtx) }

    val lifecycleOwner = ctx as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(appCtx)
                    .contains(appCtx.packageName)
                batteryWhitelisted = isIgnoringBatteryOptimizations(appCtx)
                oemAutostartConfirmed = PhantomPrefs.isOemAutostartConfirmed(appCtx)
                fgServiceRunning = isStatsForegroundServiceRunning(appCtx)
                mutedApps = PhantomPrefs.getMutedApps(appCtx).sorted()
                muteModes = PhantomPrefs.getAppMuteModeMap(appCtx)
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
                        color = Color.Gray
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

    val glassState = remember { HazeState() }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("全部設定") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .phantomGlassSource(glassState)
                    .padding(padding)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. 快速設定（置頂）
                item {
                    CollapsibleSettingsCard(
                        title = "快速設定",
                        expanded = expandQuick,
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
                        subtitle = "暫停每則 AI 掃描與每日摘要排程，僅命中可疑特徵才分析；關閉即恢復原設定",
                        checked = tokenSaverEnabled,
                        onCheckedChange = { checked ->
                            tokenSaverEnabled = checked
                            PhantomPrefs.setTokenSaverEnabled(appCtx, checked)
                            DailySummaryPolicy.schedule(appCtx)
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
                        subtitle = "內文滿 10 字元且未命中過濾規則時自動評估風險",
                        checked = aiRiskEnabled,
                        onCheckedChange = { checked ->
                            aiRiskEnabled = checked
                            PhantomPrefs.setAiRiskEnabled(appCtx, checked)
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SettingSwitchRow(
                        title = "每則訊息 AI 智慧掃描",
                        subtitle = "AI 分析時自動指派「必看／行銷／垃圾通知」等標籤",
                        checked = aiAutoTagEnabled,
                        onCheckedChange = { checked ->
                            aiAutoTagEnabled = checked
                            PhantomPrefs.setAiAutoTagEnabled(appCtx, checked)
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SettingSwitchRow(
                        title = "每日 AI 摘要",
                        subtitle = String.format(Locale.getDefault(), "排程時間：每日 %02d:%02d", summaryHour, summaryMinute),
                        checked = dailySummaryEnabled,
                        onCheckedChange = { checked ->
                            dailySummaryEnabled = checked
                            PhantomPrefs.setDailySummaryEnabled(appCtx, checked)
                            DailySummaryPolicy.schedule(appCtx)
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

            // 2. AI 與分析
            item {
                CollapsibleSettingsCard(
                    title = "AI 與分析",
                    expanded = expandAi,
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
                    Spacer(Modifier.height(10.dp))

                    SettingSwitchRow(
                        title = "AI 助手建議問題",
                        subtitle = "在 AI 助手分頁顯示快速提問與昨日簡報建議",
                        checked = chatSuggestionsEnabled,
                        onCheckedChange = { checked ->
                            chatSuggestionsEnabled = checked
                            PhantomPrefs.setChatSuggestionsEnabled(appCtx, checked)
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "每日 AI 分析上限（今日已用 $todayAiUsage 次）",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 20, 50, 100).forEach { limit ->
                            FilterChip(
                                selected = dailyAiLimit == limit,
                                onClick = {
                                    dailyAiLimit = limit
                                    PhantomPrefs.setDailyAiLimit(appCtx, limit)
                                },
                                label = { Text(if (limit == 0) "不限" else "$limit 則") }
                            )
                        }
                    }

                    if (dailySummaryEnabled) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(
                                    ctx,
                                    { _, h, m ->
                                        summaryHour = h
                                        summaryMinute = m
                                        PhantomPrefs.setDailySummaryTime(appCtx, h, m)
                                        DailySummaryPolicy.schedule(appCtx)
                                    },
                                    summaryHour,
                                    summaryMinute,
                                    true
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(String.format(Locale.getDefault(), "調整摘要排程時間（%02d:%02d）", summaryHour, summaryMinute))
                        }
                        Spacer(Modifier.height(8.dp))
                        SettingSwitchRow(
                            title = "每日摘要完成推播",
                            subtitle = "摘要生成後發送低干擾通知，點擊直達統計頁",
                            checked = dailySummaryNotifEnabled,
                            onCheckedChange = { checked ->
                                dailySummaryNotifEnabled = checked
                                PhantomPrefs.setDailySummaryNotifEnabled(appCtx, checked)
                            }
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "即時提醒關鍵字（${alertKeywords.size}/${PhantomPrefs.MAX_ALERT_KEYWORDS}）",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "獨立於過濾規則，命中即發送高優先「🔔 重要訊息」通知",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
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

            // 3. 訊息與通知
            item {
                CollapsibleSettingsCard(
                    title = "訊息與通知",
                    expanded = expandNotif,
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
                    Text("命中動作：", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
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
                        Text("目前尚無過濾關鍵字", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    } else {
                        rules.forEach { rule ->
                            FilterRuleRow(
                                rule = rule,
                                onToggleActive = { checked ->
                                    scope.launch {
                                        val updated = rule.copy(isActive = checked)
                                        withContext(Dispatchers.IO) {
                                            AppDatabaseHolder.db(appCtx).filterDao().update(updated)
                                        }
                                        rules = rules.map { if (it.id == rule.id) updated else it }
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            AppDatabaseHolder.db(appCtx).filterDao().delete(rule)
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
                        text = if (secInt == 0) "同 key 更新節流：已關閉" else "同 key 更新節流：$secInt 秒",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "避免監控類通知（如流量統計）在短時間內重複洗版",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
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
                }
            }

            // 4. 資料管理
            item {
                CollapsibleSettingsCard(
                    title = "資料管理",
                    expanded = expandData,
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
                                color = Color.Gray
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
                                            color = Color.Gray
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
                    Spacer(Modifier.height(8.dp))

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

                    OutlinedButton(
                        onClick = { showExportDialog = true },
                        enabled = !isExporting,
                        modifier = Modifier.fillMaxWidth().pressableScale()
                    ) {
                        Text(if (isExporting) "匯出中…" else "匯出訊息 CSV（可選範圍）")
                    }
                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showClearReadConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("清除已讀訊息")
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
                }
            }

            // 5. 外觀與保活
            item {
                CollapsibleSettingsCard(
                    title = "外觀與保活",
                    expanded = expandAppearance,
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
                        subtitle = "切換分頁與長按操作時提供震動回饋",
                        checked = hapticEnabled,
                        onCheckedChange = { checked ->
                            hapticEnabled = checked
                            PhantomPrefs.setHapticFeedbackEnabled(appCtx, checked)
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SettingSwitchRow(
                        title = "液態玻璃模糊效果",
                        subtitle = "僅 Android 13+ 有效；關閉後改用半透明純色背景以節省 GPU 與電量",
                        checked = glassEffectEnabled,
                        onCheckedChange = { checked ->
                            ThemePreferences.setGlassEffectEnabled(appCtx, checked)
                        }
                    )
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
                                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
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

            // 6. Shizuku 進階
            item {
                ShizukuAdvancedCard(
                    expanded = expandShizuku,
                    onToggle = {
                        expandShizuku = !expandShizuku
                        PhantomPrefs.setSettingsCardExpanded(appCtx, "shizuku_advanced", expandShizuku)
                    },
                    glassState = glassState,
                    modifier = Modifier.onGloballyPositioned { coords ->
                        cardBounds["shizuku"] = coords.boundsInRoot()
                    },
                    onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                )
            }
        }
    }

        if (tourStepIndex in 1..6) {
            SettingsTourOverlay(
                stepIndex = tourStepIndex,
                cardBounds = cardBounds,
                glassState = glassState,
                onNext = {
                    if (tourStepIndex < 6) {
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
private fun ShizukuAdvancedCard(
    expanded: Boolean,
    onToggle: () -> Unit,
    glassState: HazeState,
    modifier: Modifier = Modifier,
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
        modifier = modifier
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

@Composable
private fun CollapsibleSettingsCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    glassState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    PhantomGlassCard(
        glassState = glassState,
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        contentPadding = 14.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "收合" else "展開"
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.keyword,
                style = MaterialTheme.typography.bodyMedium,
                color = if (rule.isActive) MaterialTheme.colorScheme.onSurface else Color.Gray
            )
            Text(
                text = "${if (rule.packageName.isNullOrBlank()) "全部 App" else rule.packageName}・$actionLabel",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
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
                                Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 1)
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

private enum class SettingsTourStep(
    val index: Int,
    val cardKey: String,
    val title: String,
    val description: String
) {
    QUICK(
        index = 1,
        cardKey = "quick",
        title = "快速設定",
        description = "最常用的核心開關都集中於此，包含省 token 模式、常駐統計通知與快速操作；你可以直接點擊洞內開關立即試用。"
    ),
    AI(
        index = 2,
        cardKey = "ai",
        title = "AI 與分析",
        description = "管理多 Provider API Key、每日分析額度、自然語言白名單規則、AI 自動標記與每日 AI 摘要排程。"
    ),
    NOTIF(
        index = 3,
        cardKey = "notif",
        title = "訊息與通知",
        description = "設定勿擾時段、即時關鍵字提醒、重複通知節流秒數，以及滑動刪除與點擊開啟行為。"
    ),
    DATA(
        index = 4,
        cardKey = "data",
        title = "資料管理",
        description = "管理 App 靜音清單、自訂標籤、訊息保留天數與則數上限、CSV 匯出，也可隨時重播新手或設定教學。"
    ),
    APPEARANCE(
        index = 5,
        cardKey = "appearance",
        title = "外觀與保活",
        description = "切換主題、動態取色、液態玻璃效果、觸覺震動回饋，並檢查系統電池最佳化與前景服務運作狀態。"
    ),
    SHIZUKU(
        index = 6,
        cardKey = "shizuku",
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
                color = Color.Black.copy(alpha = 0.6f)
            )
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
                contentPadding = 16.dp
            ) {
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
                    style = MaterialTheme.typography.bodyMedium
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

