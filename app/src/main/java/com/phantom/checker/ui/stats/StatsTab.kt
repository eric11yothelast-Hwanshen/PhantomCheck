// #301 來源：MainActivity.kt（StatsTab、RiskCountCard、StatsUiState、DayCountUi、DailyAppRankUi）
package com.phantom.checker.ui.stats

import com.phantom.checker.ui.components.phantomItem
import com.phantom.checker.ui.components.PhantomChevron
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
import androidx.compose.foundation.horizontalScroll
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

import com.phantom.checker.ui.*

internal data class DayCountUi(
    val label: String,
    val count: Int
)

internal data class DailyAppRankUi(
    val packageName: String,
    val appLabel: String,
    val count: Int
)

internal data class StatsUiState(
    val latestSummary: AiSummaryEntity? = null,
    val latestWeeklySummary: AiSummaryEntity? = null,
    val weeklyCounts: List<DayCountUi> = emptyList(),
    val weeklyRiskCounts: List<DayCountUi> = emptyList(),
    val safeCount: Int = 0,
    val suspiciousCount: Int = 0,
    val scamCount: Int = 0,
    val topRiskyApps: List<AppRiskRow> = emptyList(),
    val dailyTopApps: List<DailyAppRankUi> = emptyList(),
    val rawAppGroups: List<AppGroupRow> = emptyList(),
    val todayAiTagCounts: List<AiTagCountRow> = emptyList(),
    val todayHourlyCounts: List<Int> = List(24) { 0 },
    val harassmentCount: Int = 0,
    val totalMessageCount: Int = 0,
    val unreadTotalCount: Int = 0,
    val todoTotalCount: Int = 0,
    val todoDoneCount: Int = 0
)


@Composable
internal fun StatsTab(
    state: StatsUiState,
    glassState: HazeState,
    visibleCards: Set<String> = PhantomPrefs.DEFAULT_VISIBLE_STATS_CARDS,
    onSummaryGenerated: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    val statsSettingsVersion by PhantomSettingsBus.version.collectAsState()
    var statsResumeTrigger by remember { mutableIntStateOf(0) }
    val statsLifecycleOwner = ctx as? LifecycleOwner
    DisposableEffect(statsLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                statsResumeTrigger++
            }
        }
        statsLifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            statsLifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }
    val profiles = remember(statsSettingsVersion, statsResumeTrigger) { AiClient.getProfiles(appCtx) }

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

    val settingsVersion by PhantomSettingsBus.version.collectAsState()
    val tokenSaverOn = remember(settingsVersion) { PhantomPrefs.isTokenSaverEnabled(appCtx) }

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
                        text = "已暫停每則 AI 掃描並降載對話注入，僅命中可疑特徵時才進行風險分析。",
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
                modifier = Modifier
                    .fillMaxWidth()
                    .pressableScale(0.97f)
            ) {
                Text("分享本週統計報告圖卡（本機產生）")
            }
        }
        // §4.5 本週重點摘要條（純本機聚合自既有 StatsUiState，不新增資料來源，不動 13 卡順序）
        item(key = "stats_weekly_highlights") {
            val todayTotal = state.weeklyCounts.lastOrNull()?.count ?: 0
            val riskTotal = state.scamCount + state.suspiciousCount
            val todoRateText = if (state.todoTotalCount > 0) {
                "${(state.todoDoneCount * 100) / state.todoTotalCount}%"
            } else {
                "—"
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(com.phantom.checker.ui.components.AuroraTokens.CornerCard),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "本週重點",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            com.phantom.checker.ui.components.PhantomAnimatedCount(
                                count = todayTotal,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "今日訊息",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            com.phantom.checker.ui.components.PhantomAnimatedCount(
                                count = riskTotal,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (riskTotal > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "風險攔截",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = todoRateText,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                            Text(
                                text = "待辦完成率",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        // #349 早晚簡報玻璃卡（取代每日 AI 摘要，首次展開閱讀計入親密值）
        if ("stats_briefing" in visibleCards) {
        phantomItem(0, key = "stats_briefing") {
            val lastSummary = remember(statsSettingsVersion, statsResumeTrigger) {
                PhantomPrefs.getLastBriefingSummary(appCtx)
            }
            val lastTs = remember(statsSettingsVersion, statsResumeTrigger) {
                PhantomPrefs.getLastBriefingTimestamp(appCtx)
            }
            val lastMsgId = remember(statsSettingsVersion, statsResumeTrigger) {
                PhantomPrefs.getLastBriefingMessageId(appCtx)
            }
            var briefingExpanded by remember { mutableStateOf(false) }
            var fullBriefingText by remember(lastMsgId) { mutableStateOf<String?>(null) }

            LaunchedEffect(briefingExpanded, lastMsgId) {
                if (briefingExpanded && lastMsgId > 0L) {
                    if (fullBriefingText == null) {
                        fullBriefingText = withContext(Dispatchers.IO) {
                            val dao = AppDatabaseHolder.db(appCtx).chatDao()
                            dao.allSessions().firstNotNullOfOrNull { s ->
                                dao.messagesForSession(s.id).firstOrNull { it.id == lastMsgId }?.content
                            }
                        }
                    }
                    PhantomPrefs.markBriefingReadOnce(appCtx, lastMsgId)
                }
            }

            PhantomGlassCard(
                glassState = glassState,
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 14.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { briefingExpanded = !briefingExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "☀️🌙 早晚簡報",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (lastTs > 0L) {
                        val timeStr = remember(lastTs) {
                            SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(lastTs))
                        }
                        Text(
                            text = timeStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { briefingExpanded = !briefingExpanded }) {
                        PhantomChevron(
 expanded = briefingExpanded,
                            contentDescription = if (briefingExpanded) "收合早晚簡報" else "展開閱讀早晚簡報"
                        )
                    }
                }
                if (!briefingExpanded) {
                    val previewText = lastSummary.takeIf { it.isNotBlank() }
                        ?.replace("\n", " ")
                        ?.replace(Regex("\\s+"), " ")
                        ?.trim()
                        ?: "尚無早晚簡報，可於「設定 → 權限與 AI 夥伴 → AI 夥伴與主動推播」試發或等待排程"
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { briefingExpanded = true }
                    )
                } else {
                    Spacer(Modifier.height(6.dp))
                    val displayMarkdown = fullBriefingText?.takeIf { it.isNotBlank() } ?: lastSummary
                    if (displayMarkdown.isNotBlank()) {
                        PhantomMarkdownText(
                            markdown = displayMarkdown,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                val plain = stripMarkdown(displayMarkdown)
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("briefing_summary", plain))
                                onShowMessage("已複製早晚簡報")
                            }) {
                                Text("複製")
                            }
                        }
                    } else {
                        Text(
                            text = "尚無早晚簡報紀錄，可於「設定 → 權限與 AI 夥伴 → AI 夥伴與主動推播」點擊試發，或等待每日早晚排程。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        PhantomChevron(
 expanded = weeklyExpanded,
                            contentDescription = if (weeklyExpanded) "收合本週摘要" else "展開本週摘要"
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                if (!weeklyExpanded) {
                    val previewText = weekly?.let {
                        val label = WeeklySummaryPolicy.formatWeekLabel(it.date)
                        val body = stripMarkdown(it.summary).replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                        "$label：$body"
                    } ?: "尚無本週摘要，點擊展開或立即生成"
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (weekly != null) {
                            TextButton(onClick = {
                                weeklyExpanded = true
                                PhantomPrefs.setWeeklySummaryExpanded(appCtx, true)
                            }) {
                                Text("展開全文")
                            }
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }
                        Row(
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
                                            weeklyExpanded = true
                                            PhantomPrefs.setWeeklySummaryExpanded(appCtx, true)
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
        if ("stats_weekly_bar" in visibleCards) {
        phantomItem(1, key = "stats_weekly_bar") {
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
        }

        // 風險分布三色計數卡
        if ("stats_risk_dist" in visibleCards) {
        phantomItem(2, key = "stats_risk_dist") {
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
        }

        // 今日 AI 智慧標記分布
        if ("stats_ai_tags" in visibleCards && state.todayAiTagCounts.isNotEmpty()) {
            phantomItem(3, key = "stats_ai_tags") {
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
        if ("stats_hourly" in visibleCards) {
        phantomItem(4, key = "stats_hourly") {
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
        }

        // 今日 App 訊息量排行榜 Top 5（#218）
        if ("stats_daily_top_apps" in visibleCards) {
            phantomItem(5, key = "stats_daily_top_apps") {
                val barColor = MaterialTheme.colorScheme.primary
                val trackColor = MaterialTheme.colorScheme.surfaceVariant
                PhantomGlassCard(
                    glassState = glassState,
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    contentPadding = 14.dp
                ) {
                    Text(
                        text = "今日 App 訊息量排行榜 Top 5",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    if (state.dailyTopApps.isEmpty()) {
                        Text(
                            text = "今日尚無收到任何訊息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val maxCnt = (state.dailyTopApps.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            state.dailyTopApps.forEachIndexed { idx, app ->
                                val ratio = (app.count.toFloat() / maxCnt.toFloat()).coerceIn(0f, 1f)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "${idx + 1}.",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        AppIconImage(
                                            packageName = app.packageName,
                                            contentDescription = app.appLabel,
                                            size = 24.dp
                                        )
                                        Text(
                                            text = app.appLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${app.count} 則",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                    ) {
                                        val r = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                                        drawRoundRect(
                                            color = trackColor,
                                            size = size,
                                            cornerRadius = r
                                        )
                                        if (ratio > 0f) {
                                            drawRoundRect(
                                                color = barColor,
                                                size = Size(size.width * ratio, size.height),
                                                cornerRadius = r
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

        // 騷擾佔比（Task J2：行銷 + 垃圾通知 / 總訊息，Canvas arc）
        if ("stats_harassment" in visibleCards) {
        phantomItem(6, key = "stats_harassment") {
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
        }

        // Top 5 高風險 App 清單
        if ("stats_top_risky" in visibleCards) {
        phantomItem(7, key = "stats_top_risky") {
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
        }

        // 7 日風險趨勢折線圖（#218）
        if ("stats_risk_trend" in visibleCards) {
            phantomItem(8, key = "stats_risk_trend") {
                val lineColor = MaterialTheme.colorScheme.error
                val gridColor = MaterialTheme.colorScheme.surfaceVariant
                PhantomGlassCard(
                    glassState = glassState,
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    contentPadding = 14.dp
                ) {
                    Text(
                        text = "7 日風險趨勢折線（疑似＋詐騙）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    val points = state.weeklyRiskCounts
                    val maxRisk = (points.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    ) {
                        if (points.isEmpty()) return@Canvas
                        val padV = 10.dp.toPx()
                        val usableH = (size.height - padV * 2).coerceAtLeast(1f)
                        val slotW = size.width / points.size
                        // 參考底線
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, size.height - padV),
                            end = Offset(size.width, size.height - padV),
                            strokeWidth = 1.dp.toPx()
                        )
                        val coords = points.mapIndexed { idx, d ->
                            val x = idx * slotW + slotW / 2f
                            val y = (size.height - padV) - (d.count.toFloat() / maxRisk.toFloat()) * usableH
                            Offset(x, y)
                        }
                        for (i in 0 until coords.lastIndex) {
                            drawLine(
                                color = lineColor,
                                start = coords[i],
                                end = coords[i + 1],
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                        coords.forEach { pt ->
                            drawCircle(
                                color = lineColor,
                                radius = 4.dp.toPx(),
                                center = pt
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        points.forEach { day ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${day.count}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (day.count > 0) lineColor else MaterialTheme.colorScheme.onSurfaceVariant
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
        }

        // 未讀 / 已讀比例環形（#218）
        if ("stats_unread_ratio" in visibleCards) {
            phantomItem(9, key = "stats_unread_ratio") {
                val total = state.totalMessageCount.coerceAtLeast(0)
                val unread = state.unreadTotalCount.coerceIn(0, total.coerceAtLeast(0))
                val read = (total - unread).coerceAtLeast(0)
                val ratio = if (total > 0) unread.toFloat() / total.toFloat() else 0f
                val pct = (ratio * 100f).toInt()
                val arcTrack = MaterialTheme.colorScheme.surfaceVariant
                val arcColor = MaterialTheme.colorScheme.primary
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
                                text = "未讀 / 已讀比例",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "未讀 $unread 則 · 已讀 $read 則（共 $total 則）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 各 App 訊息佔比橫條前 8（#218）
        if ("stats_app_share" in visibleCards) {
            phantomItem(10, key = "stats_app_share") {
                val top8 = state.rawAppGroups.sortedByDescending { it.cnt }.take(8)
                val totalAll = state.rawAppGroups.sumOf { it.cnt }.coerceAtLeast(1)
                val barColor = MaterialTheme.colorScheme.tertiary
                val trackColor = MaterialTheme.colorScheme.surfaceVariant
                PhantomGlassCard(
                    glassState = glassState,
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    contentPadding = 14.dp
                ) {
                    Text(
                        text = "各 App 訊息佔比 Top 8",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    if (top8.isEmpty()) {
                        Text(
                            text = "目前尚無訊息紀錄",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            top8.forEach { row ->
                                val ratio = (row.cnt.toFloat() / totalAll.toFloat()).coerceIn(0f, 1f)
                                val pct = (ratio * 100f).toInt()
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        AppIconImage(
                                            packageName = row.packageName,
                                            contentDescription = row.appLabel,
                                            size = 22.dp
                                        )
                                        Text(
                                            text = row.appLabel.ifBlank { row.packageName },
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${row.cnt} 則 ($pct%)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                    ) {
                                        val r = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
                                        drawRoundRect(color = trackColor, size = size, cornerRadius = r)
                                        if (ratio > 0f) {
                                            drawRoundRect(
                                                color = barColor,
                                                size = Size(size.width * ratio, size.height),
                                                cornerRadius = r
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

        // 待辦完成率環形（#218）
        if ("stats_todo_done" in visibleCards) {
            phantomItem(11, key = "stats_todo_done") {
                val total = state.todoTotalCount.coerceAtLeast(0)
                val done = state.todoDoneCount.coerceIn(0, total.coerceAtLeast(0))
                val ratio = if (total > 0) done.toFloat() / total.toFloat() else 0f
                val pct = (ratio * 100f).toInt()
                val arcTrack = MaterialTheme.colorScheme.surfaceVariant
                val arcColor = MaterialTheme.colorScheme.tertiary
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
                                text = "待辦完成率",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (total == 0) "尚無待辦事項" else "$done / $total 項待辦已完成",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // #381 / #420 Health Connect 健康明細（日／週／月切換＋每日數值表＋週彙總＋迷你條；本機聚合零 token）
        phantomItem(12, key = "stats_health_7d") {
            StatsHealthDetailCard(glassState = glassState)
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
internal fun RiskCountCard(
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
private fun StatsHealthDetailCard(
    glassState: HazeState
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    var selectedPeriod by remember { mutableStateOf(1) } // 0=日, 1=週, 2=月
    var trend7d by remember { mutableStateOf<List<com.phantom.checker.util.HealthConnectBridge.DailyHealthPoint>>(emptyList()) }
    var trend30d by remember { mutableStateOf<List<com.phantom.checker.util.HealthConnectBridge.DailyHealthPoint>>(emptyList()) }
    var loading30d by remember { mutableStateOf(false) }
    var hcAvailability by remember { mutableStateOf(com.phantom.checker.util.HealthConnectBridge.SdkAvailability.AVAILABLE) }
    var hcPerm by remember { mutableStateOf(com.phantom.checker.util.HealthConnectBridge.HealthPermissionStatus()) }

    LaunchedEffect(Unit) {
        hcAvailability = com.phantom.checker.util.HealthConnectBridge.checkAvailability(appCtx)
        hcPerm = com.phantom.checker.util.HealthConnectBridge.getPermissionStatus(appCtx)
        if (hcPerm.anyGranted) {
            trend7d = com.phantom.checker.util.HealthConnectBridge.readLast7DaysTrend(appCtx)
        }
    }
    LaunchedEffect(selectedPeriod, hcPerm.anyGranted) {
        if (selectedPeriod == 2 && hcPerm.anyGranted && trend30d.isEmpty()) {
            loading30d = true
            trend30d = com.phantom.checker.util.HealthConnectBridge.readRecentDaysTrend(appCtx, days = 30)
            loading30d = false
        }
    }

    val openPermHub = {
        ctx.startActivity(
            android.content.Intent(ctx, com.phantom.checker.ui.PermissionHubActivity::class.java)
        )
    }

    PhantomGlassCard(
        glassState = glassState,
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        contentPadding = 14.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "❤️ Health Connect 健康明細",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (!hcPerm.allGranted) {
                    TextButton(onClick = openPermHub) {
                        Text(
                            text = if (!hcPerm.anyGranted) "前往授權" else "補授權",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            if (hcAvailability != com.phantom.checker.util.HealthConnectBridge.SdkAvailability.AVAILABLE || !hcPerm.anyGranted) {
                Text(
                    text = when {
                        hcAvailability == com.phantom.checker.util.HealthConnectBridge.SdkAvailability.UNAVAILABLE ->
                            "此裝置未支援或未安裝 Health Connect，App 其餘功能不受影響。"
                        hcAvailability == com.phantom.checker.util.HealthConnectBridge.SdkAvailability.PROVIDER_UPDATE_REQUIRED ->
                            "Health Connect 需要安裝或更新，請前往權限中心操作。"
                        else ->
                            "尚未授權 Health Connect（選配功能）。授權後可在此檢視每日睡眠、步數、心率、距離與熱量明細，原始數值絕不出本機。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("日" to 0, "週" to 1, "月" to 2).forEach { (label, idx) ->
                        FilterChip(
                            selected = selectedPeriod == idx,
                            onClick = { selectedPeriod = idx },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }

                val hasAny7d = trend7d.any {
                    it.sleepHours > 0f || it.steps > 0L || it.avgHeartRateBpm > 0L || it.distanceMeters > 0.0 || it.caloriesKcal > 0.0
                }

                when (selectedPeriod) {
                    0 -> {
                        val todayPt = trend7d.lastOrNull()
                        val yesterdayPt = trend7d.getOrNull(trend7d.size - 2) ?: todayPt
                        if (todayPt == null || !hasAny7d) {
                            Text(
                                text = "近期尚無穿戴裝置或系統健康紀錄（無紀錄）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val sleepVal = when {
                                !hcPerm.sleep -> "未授權"
                                todayPt.sleepHours > 0f -> String.format(Locale.US, "%.1f 小時", todayPt.sleepHours)
                                else -> "無紀錄"
                            }
                            val stepsVal = when {
                                !hcPerm.steps -> "未授權"
                                yesterdayPt != null && yesterdayPt.steps > 0L -> String.format(Locale.US, "%,d 步", yesterdayPt.steps)
                                else -> "無紀錄"
                            }
                            val hrVal = when {
                                !hcPerm.heartRate -> "未授權"
                                yesterdayPt != null && yesterdayPt.avgHeartRateBpm > 0L -> "${yesterdayPt.avgHeartRateBpm} bpm"
                                else -> "無紀錄"
                            }
                            val distVal = when {
                                !hcPerm.distance -> "未授權"
                                yesterdayPt != null && yesterdayPt.distanceMeters > 0.0 -> com.phantom.checker.util.HealthConnectBridge.formatDistance(yesterdayPt.distanceMeters)
                                else -> "無紀錄"
                            }
                            val calVal = when {
                                !hcPerm.calories -> "未授權"
                                yesterdayPt != null && yesterdayPt.caloriesKcal > 0.0 -> "${yesterdayPt.caloriesKcal.toLong()} kcal"
                                else -> "無紀錄"
                            }
                            Text(
                                text = "當日健康快照（昨夜睡眠＋昨日活動）",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    "昨夜睡眠" to sleepVal,
                                    "昨日步數" to stepsVal,
                                    "昨日均心率" to hrVal,
                                    "昨日距離" to distVal,
                                    "昨日消耗熱量" to calVal
                                ).forEach { (k, v) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = k,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = v,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (v == "未授權") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            modifier = if (v == "未授權") Modifier.clickable(onClick = openPermHub) else Modifier
                                        )
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        if (trend7d.isEmpty() || !hasAny7d) {
                            Text(
                                text = "近 7 日尚無穿戴裝置或系統健康紀錄（無紀錄）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val totalSteps7 = trend7d.sumOf { it.steps }
                            val sleepSamples = trend7d.mapNotNull { it.sleepHours.takeIf { h -> h > 0f } }
                            val avgSleep7 = if (sleepSamples.isNotEmpty()) sleepSamples.average().toFloat() else 0f
                            val hrSamples = trend7d.mapNotNull { it.avgHeartRateBpm.takeIf { b -> b > 0L } }
                            val avgHr7 = if (hrSamples.isNotEmpty()) hrSamples.average().toLong() else 0L
                            val totalDist7 = trend7d.sumOf { it.distanceMeters }
                            val totalCal7 = trend7d.sumOf { it.caloriesKcal }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "近 7 日彙總",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val stepsSummary = if (!hcPerm.steps) "未授權" else if (totalSteps7 > 0L) String.format(Locale.US, "%,d 步", totalSteps7) else "無紀錄"
                                    val sleepSummary = if (!hcPerm.sleep) "未授權" else if (avgSleep7 > 0f) String.format(Locale.US, "均 %.1fh", avgSleep7) else "無紀錄"
                                    val hrSummary = if (!hcPerm.heartRate) "未授權" else if (avgHr7 > 0L) "均 $avgHr7 bpm" else "無紀錄"
                                    val distSummary = if (!hcPerm.distance) "未授權" else if (totalDist7 > 0.0) com.phantom.checker.util.HealthConnectBridge.formatDistance(totalDist7) else "無紀錄"
                                    val calSummary = if (!hcPerm.calories) "未授權" else if (totalCal7 > 0.0) "${totalCal7.toLong()} kcal" else "無紀錄"
                                    Text(
                                        text = "總步數 $stepsSummary · 睡眠 $sleepSummary · 心率 $hrSummary",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "總距離 $distSummary · 總熱量 $calSummary",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            val maxSleep = trend7d.maxOf { it.sleepHours }.coerceAtLeast(8f)
                            val maxSteps = trend7d.maxOf { it.steps }.coerceAtLeast(8000L)
                            Text(
                                text = "睡眠時數（近 7 日迷你條）",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                trend7d.forEach { pt ->
                                    val hFrac = (pt.sleepHours / maxSleep).coerceIn(0.08f, 1f)
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Bottom
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.65f)
                                                .height((34f * hFrac).dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                        )
                                        Text(
                                            text = pt.dayLabel.takeLast(2),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "每日步數（近 7 日迷你條）",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                trend7d.forEach { pt ->
                                    val hFrac = (pt.steps.toFloat() / maxSteps.toFloat()).coerceIn(0.08f, 1f)
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Bottom
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.65f)
                                                .height((34f * hFrac).dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f),
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                        )
                                        Text(
                                            text = pt.dayLabel.takeLast(2),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "近 7 日每日明細表（可左右滑動）",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val tableScroll = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(tableScroll)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("日期", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
                                    Text("睡眠", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
                                    Text("步數", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(68.dp))
                                    Text("均心率", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
                                    Text("距離", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(74.dp))
                                    Text("熱量", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(76.dp))
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                trend7d.reversed().forEach { pt ->
                                    val sCell = if (!hcPerm.sleep) "未授權" else if (pt.sleepHours > 0f) String.format(Locale.US, "%.1fh", pt.sleepHours) else "—"
                                    val stCell = if (!hcPerm.steps) "未授權" else if (pt.steps > 0L) String.format(Locale.US, "%,d", pt.steps) else "—"
                                    val hrCell = if (!hcPerm.heartRate) "未授權" else if (pt.avgHeartRateBpm > 0L) "${pt.avgHeartRateBpm}" else "—"
                                    val dCell = if (!hcPerm.distance) "未授權" else com.phantom.checker.util.HealthConnectBridge.formatDistance(pt.distanceMeters)
                                    val cCell = if (!hcPerm.calories) "未授權" else if (pt.caloriesKcal > 0.0) "${pt.caloriesKcal.toLong()} kcal" else "—"
                                    Row(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(pt.dayLabel, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.width(48.dp))
                                        Text(sCell, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = if (sCell == "未授權") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(56.dp))
                                        Text(stCell, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = if (stCell == "未授權") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(68.dp))
                                        Text(hrCell, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = if (hrCell == "未授權") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(64.dp))
                                        Text(dCell, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = if (dCell == "未授權") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(74.dp))
                                        Text(cCell, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = if (cCell == "未授權") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(76.dp))
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        if (loading30d) {
                            Text(
                                text = "正在本機聚合近 30 日健康資料…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val hasAny30d = trend30d.any {
                                it.sleepHours > 0f || it.steps > 0L || it.avgHeartRateBpm > 0L || it.distanceMeters > 0.0 || it.caloriesKcal > 0.0
                            }
                            if (trend30d.isEmpty() || !hasAny30d) {
                                Text(
                                    text = "近 30 日尚無穿戴裝置或系統健康紀錄（無紀錄）。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                val totalSteps30 = trend30d.sumOf { it.steps }
                                val stepDays30 = trend30d.count { it.steps > 0L }.coerceAtLeast(1)
                                val sleepSamples30 = trend30d.mapNotNull { it.sleepHours.takeIf { h -> h > 0f } }
                                val avgSleep30 = if (sleepSamples30.isNotEmpty()) sleepSamples30.average().toFloat() else 0f
                                val hrSamples30 = trend30d.mapNotNull { it.avgHeartRateBpm.takeIf { b -> b > 0L } }
                                val avgHr30 = if (hrSamples30.isNotEmpty()) hrSamples30.average().toLong() else 0L
                                val totalDist30 = trend30d.sumOf { it.distanceMeters }
                                val totalCal30 = trend30d.sumOf { it.caloriesKcal }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "近 30 日彙總",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val stepsText = if (!hcPerm.steps) "未授權" else if (totalSteps30 > 0L) String.format(Locale.US, "%,d 步（日均 %,d）", totalSteps30, totalSteps30 / stepDays30) else "無紀錄"
                                        val sleepText = if (!hcPerm.sleep) "未授權" else if (avgSleep30 > 0f) String.format(Locale.US, "均 %.1fh", avgSleep30) else "無紀錄"
                                        val hrText = if (!hcPerm.heartRate) "未授權" else if (avgHr30 > 0L) "均 $avgHr30 bpm" else "無紀錄"
                                        val distText = if (!hcPerm.distance) "未授權" else if (totalDist30 > 0.0) com.phantom.checker.util.HealthConnectBridge.formatDistance(totalDist30) else "無紀錄"
                                        val calText = if (!hcPerm.calories) "未授權" else if (totalCal30 > 0.0) "${totalCal30.toLong()} kcal" else "無紀錄"
                                        Text("步數 $stepsText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                        Text("睡眠 $sleepText · 心率 $hrText · 距離 $distText · 熱量 $calText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }

                                val maxSleep30 = trend30d.maxOf { it.sleepHours }.coerceAtLeast(8f)
                                val maxSteps30 = trend30d.maxOf { it.steps }.coerceAtLeast(8000L)
                                Text("睡眠時數（近 30 日）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    trend30d.forEach { pt ->
                                        val hFrac = if (pt.sleepHours > 0f) (pt.sleepHours / maxSleep30).coerceIn(0.08f, 1f) else 0.04f
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height((38f * hFrac).dp)
                                                .background(
                                                    color = if (pt.sleepHours > 0f) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                                    shape = RoundedCornerShape(2.dp)
                                                )
                                        )
                                    }
                                }
                                Text("每日步數（近 30 日）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    trend30d.forEach { pt ->
                                        val hFrac = if (pt.steps > 0L) (pt.steps.toFloat() / maxSteps30.toFloat()).coerceIn(0.08f, 1f) else 0.04f
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height((38f * hFrac).dp)
                                                .background(
                                                    color = if (pt.steps > 0L) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                                    shape = RoundedCornerShape(2.dp)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (!hcPerm.allGranted) {
                    Text(
                        text = "部分型別未授權（顯示「未授權」），點此前往權限中心補授權",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = openPermHub)
                    )
                }
                Text(
                    text = "資料由 Health Connect 本機聚合（零額度，原始數值絕不出本機）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


