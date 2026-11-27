// #301 來源：MainActivity.kt（StatsTab、RiskCountCard、StatsUiState、DayCountUi、DailyAppRankUi）
package com.phantom.checker.ui.stats

import com.phantom.checker.ui.components.phantomItem
import com.phantom.checker.ui.components.PhantomChevron
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.phantom.checker.data.AiSummaryEntity
import com.phantom.checker.data.AiTagCountRow
import com.phantom.checker.data.AppGroupRow
import com.phantom.checker.data.AppRiskRow
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.service.WeeklySummaryPolicy
import com.phantom.checker.ui.components.pressableScale
import com.phantom.checker.util.MessageExporter
import com.phantom.checker.util.PhantomPrefs
import com.phantom.checker.util.PhantomSettingsBus
import com.phantom.checker.util.ShareCardReportData
import com.phantom.checker.util.StatsShareCardGenerator
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.phantom.checker.ui.*
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource

private val MULTI_WHITESPACE_REGEX = Regex("\\s+")

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
    val warningCount: Int = 0,
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
    val resCtx = LocalContext.current
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var isSharingCard by remember { mutableStateOf(false) }
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

    if (showExportDialog) {
        ExportCsvDialog(
            appGroups = state.rawAppGroups,
            onDismiss = { showExportDialog = false },
            onConfirm = { pkg, days ->
                showExportDialog = false
                if (isExporting) return@ExportCsvDialog
                scope.launch {
                    isExporting = true
                    try {
                        val result = MessageExporter.exportToDownloads(
                            ctx = appCtx,
                            packageName = pkg,
                            daysBack = days
                        )
                        result.onSuccess { fileName ->
                            onShowMessage(resCtx.getString(R.string.settings_s_11c369, fileName))
                        }.onFailure { err ->
                            val reason = err.localizedMessage?.takeIf { it.isNotBlank() }
                                ?: err.javaClass.simpleName
                            onShowMessage(resCtx.getString(R.string.chat_s_55fba2, reason))
                        }
                    } finally {
                        isExporting = false
                    }
                }
            }
        )
    }

    val tokenSaverOn = remember(statsSettingsVersion) { PhantomPrefs.isTokenSaverEnabled(appCtx) }

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
                        text = stringResource(R.string.stats_s_d4b60a),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.stats_s_605ebc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            OutlinedButton(
                enabled = !isSharingCard,
                onClick = {
                    if (isSharingCard) return@OutlinedButton
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
                            .map { it.appLabel to it.cnt },
                        warningCount = state.warningCount
                    )
                    isSharingCard = true
                    scope.launch {
                        try {
                            val res = StatsShareCardGenerator.generateAndShareSuspend(ctx, data)
                            if (res.isFailure) {
                                onShowMessage(resCtx.getString(R.string.stats_s_db9eb8, res.exceptionOrNull()?.message ?: resCtx.getString(R.string.common_unknown_error)))
                            }
                        } finally {
                            isSharingCard = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .pressableScale(0.97f)
            ) {
                Text(stringResource(R.string.stats_s_eb824c))
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
                        text = stringResource(R.string.stats_s_8650b3),
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
                                text = stringResource(R.string.stats_s_f042c8),
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
                                text = stringResource(R.string.stats_s_0120ed),
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
                                text = stringResource(R.string.stats_s_4bcda6),
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
                            runCatching {
                                AppDatabaseHolder.db(appCtx).openHelper.readableDatabase.query(
                                    "SELECT content FROM chat_messages WHERE id = ? LIMIT 1",
                                    arrayOf<Any>(lastMsgId)
                                ).use { cursor ->
                                    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
                                }
                            }.getOrNull()
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
                        text = stringResource(R.string.stats_s_76a0cd),
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
                            contentDescription = if (briefingExpanded) stringResource(R.string.stats_s_8020ab) else stringResource(R.string.stats_s_55b595)
                        )
                    }
                }
                if (!briefingExpanded) {
                    val previewText = lastSummary.takeIf { it.isNotBlank() }
                        ?.replace("\n", " ")
                        ?.replace(MULTI_WHITESPACE_REGEX, " ")
                        ?.trim()
                        ?: stringResource(R.string.stats_s_5395df)
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
                                onShowMessage(resCtx.getString(R.string.stats_s_758581))
                            }) {
                                Text(stringResource(R.string.common_copy))
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.stats_s_8d6939),
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
                            text = stringResource(R.string.stats_s_920d4b),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.stats_s_ef458a),
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
                        Text(if (weeklyHistoryExpanded) stringResource(R.string.stats_s_5679c3) else stringResource(R.string.stats_s_f42d23))
                    }
                    IconButton(onClick = {
                        weeklyExpanded = !weeklyExpanded
                        PhantomPrefs.setWeeklySummaryExpanded(appCtx, weeklyExpanded)
                    }) {
                        PhantomChevron(
                            expanded = weeklyExpanded,
                            contentDescription = if (weeklyExpanded) stringResource(R.string.stats_s_f1f241) else stringResource(R.string.stats_s_7860c9)
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                if (!weeklyExpanded) {
                    val previewText = weekly?.let {
                        val label = WeeklySummaryPolicy.formatWeekLabel(it.date)
                        val body = stripMarkdown(it.summary).replace("\n", " ").replace(MULTI_WHITESPACE_REGEX, " ").trim()
                        "$label：$body"
                    } ?: stringResource(R.string.stats_s_8aa322)
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
                                Text(stringResource(R.string.stats_s_04dd5a))
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
                                        try {
                                            val result = withContext(Dispatchers.IO) {
                                                WeeklySummaryPolicy.generateWeeklySummary(appCtx, fromSchedule = false)
                                            }
                                            result.onSuccess {
                                                weeklyExpanded = true
                                                PhantomPrefs.setWeeklySummaryExpanded(appCtx, true)
                                                onSummaryGenerated()
                                                onShowMessage(resCtx.getString(R.string.stats_s_0ec53f))
                                            }.onFailure { err ->
                                                val msg = err.message ?: resCtx.getString(R.string.stats_s_0ccbe0)
                                                weeklyError = msg
                                                onShowMessage(msg)
                                            }
                                        } finally {
                                            isGeneratingWeekly = false
                                        }
                                    }
                                },
                                enabled = !isGeneratingWeekly
                            ) {
                                Text(if (isGeneratingWeekly) stringResource(R.string.common_generating) else if (weekly != null) stringResource(R.string.common_regenerate) else stringResource(R.string.stats_s_d0864a))
                            }
                            if (weekly != null) {
                                TextButton(onClick = {
                                    val plain = stripMarkdown(weekly.summary)
                                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("weekly_ai_summary", plain))
                                    onShowMessage(resCtx.getString(R.string.stats_s_50f9d0))
                                }) {
                                    Text(stringResource(R.string.common_copy))
                                }
                                TextButton(onClick = {
                                    val plain = stripMarkdown(weekly.summary)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, resCtx.getString(R.string.stats_s_7ae9e5))
                                        putExtra(Intent.EXTRA_TEXT, plain)
                                    }
                                    ctx.startActivity(Intent.createChooser(shareIntent, resCtx.getString(R.string.stats_s_d460c6)))
                                }) {
                                    Text(stringResource(R.string.stats_s_7a9243))
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
                    val weeklyTimeFmt = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
                    if (weekly != null) {
                        val timeStr = remember(weekly.generatedAt) { weeklyTimeFmt.format(Date(weekly.generatedAt)) }
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
                                text = stringResource(R.string.stats_s_fab088, timeStr),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = {
                                    val plain = stripMarkdown(weekly.summary)
                                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("weekly_ai_summary", plain))
                                    onShowMessage(resCtx.getString(R.string.stats_s_50f9d0))
                                }) {
                                    Text(stringResource(R.string.common_copy))
                                }
                                TextButton(onClick = {
                                    val plain = stripMarkdown(weekly.summary)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, resCtx.getString(R.string.stats_s_7ae9e5))
                                        putExtra(Intent.EXTRA_TEXT, plain)
                                    }
                                    ctx.startActivity(Intent.createChooser(shareIntent, resCtx.getString(R.string.stats_s_d460c6)))
                                }) {
                                    Text(stringResource(R.string.stats_s_7a9243))
                                }
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.stats_s_2820fc),
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
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        WeeklySummaryPolicy.generateWeeklySummary(appCtx, fromSchedule = false)
                                    }
                                    result.onSuccess {
                                        if (weeklyHistoryExpanded) {
                                            weeklyHistory = withContext(Dispatchers.IO) {
                                                AppDatabaseHolder.db(appCtx).aiSummaryDao().weeklyHistory()
                                            }
                                        }
                                        onSummaryGenerated()
                                        onShowMessage(resCtx.getString(R.string.stats_s_0ec53f))
                                    }.onFailure { err ->
                                        val msg = err.message ?: resCtx.getString(R.string.stats_s_0ccbe0)
                                        weeklyError = msg
                                        onShowMessage(msg)
                                    }
                                } finally {
                                    isGeneratingWeekly = false
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
                            Text(stringResource(R.string.stats_s_13a0eb))
                        } else {
                            Text(stringResource(R.string.stats_s_d97efe))
                        }
                    }
                }

                if (weeklyHistoryExpanded) {
                    val historyTimeFmt = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    if (weeklyHistory.isEmpty()) {
                        Text(
                            text = stringResource(R.string.stats_s_b29d64),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        weeklyHistory.forEachIndexed { idx, item ->
                            if (idx > 0) {
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            }
                            val genTime = remember(item.generatedAt) { historyTimeFmt.format(Date(item.generatedAt)) }
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
                    text = stringResource(R.string.stats_s_b1fd50),
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
                    title = stringResource(R.string.stats_s_8e662a),
                    count = state.safeCount,
                    accentColor = MaterialTheme.colorScheme.primary,
                    glassState = glassState,
                    modifier = Modifier.weight(1f)
                )
                RiskCountCard(
                    title = stringResource(R.string.stats_s_4ea2af),
                    count = state.suspiciousCount,
                    accentColor = MaterialTheme.colorScheme.warning,
                    glassState = glassState,
                    modifier = Modifier.weight(1f)
                )
                RiskCountCard(
                    title = stringResource(R.string.common_scam),
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
                        text = stringResource(R.string.stats_s_a167e9),
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
                    text = stringResource(R.string.stats_s_5c449d),
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
                    listOf(stringResource(R.string.stats_s_231c29), stringResource(R.string.stats_s_b1dd4d), stringResource(R.string.stats_s_1733a5), stringResource(R.string.stats_s_271ff7), stringResource(R.string.stats_s_5f6ff7)).forEach { label ->
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
                        text = stringResource(R.string.stats_s_a5df5e),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    if (state.dailyTopApps.isEmpty()) {
                        Text(
                            text = stringResource(R.string.stats_s_50add5),
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
                                            text = resCtx.getString(R.string.main_s_ddadbf, app.count),
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
            val arcColor = MaterialTheme.colorScheme.warning

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
                            text = stringResource(R.string.stats_s_c04c0d),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.stats_s_b031c4, harass, total),
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
                    text = stringResource(R.string.stats_s_147fec),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                if (state.topRiskyApps.isEmpty()) {
                    Text(
                        text = stringResource(R.string.stats_s_230bf1),
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
                                text = resCtx.getString(R.string.main_s_ddadbf, row.cnt),
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
                    Text(stringResource(R.string.stats_s_f2dbe8))
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
                        text = stringResource(R.string.stats_s_34b1d9),
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
                                text = stringResource(R.string.stats_s_24fb54),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.stats_s_848388, unread, read, total),
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
                        text = stringResource(R.string.stats_s_3a3cc7),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    if (top8.isEmpty()) {
                        Text(
                            text = stringResource(R.string.stats_s_e8325f),
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
                                            text = resCtx.getString(R.string.stats_s_35439a, row.cnt, pct),
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
                                text = stringResource(R.string.stats_s_4bcda6),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (total == 0) stringResource(R.string.stats_s_1295ad) else stringResource(R.string.stats_s_5efdbe, done, total),
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
                Text(if (isExporting) stringResource(R.string.settings_s_0fa176) else stringResource(R.string.common_s_07c286))
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
    val resCtx = LocalContext.current
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
            try {
                trend30d = com.phantom.checker.util.HealthConnectBridge.readRecentDaysTrend(appCtx, days = 30)
            } finally {
                loading30d = false
            }
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
                    text = stringResource(R.string.stats_s_e0e678),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (!hcPerm.allGranted) {
                    TextButton(onClick = openPermHub) {
                        Text(
                            text = if (!hcPerm.anyGranted) stringResource(R.string.settings_s_5df1b3) else stringResource(R.string.perm_hub_s_a1dcba),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            if (hcAvailability != com.phantom.checker.util.HealthConnectBridge.SdkAvailability.AVAILABLE || !hcPerm.anyGranted) {
                Text(
                    text = when {
                        hcAvailability == com.phantom.checker.util.HealthConnectBridge.SdkAvailability.UNAVAILABLE ->
                            stringResource(R.string.stats_s_71fb23)
                        hcAvailability == com.phantom.checker.util.HealthConnectBridge.SdkAvailability.PROVIDER_UPDATE_REQUIRED ->
                            stringResource(R.string.stats_s_3bd313)
                        else ->
                            stringResource(R.string.stats_s_9cbf7a)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(stringResource(R.string.stats_s_15917f) to 0, stringResource(R.string.stats_s_c7803d) to 1, stringResource(R.string.stats_s_d9b598) to 2).forEach { (label, idx) ->
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
                                text = stringResource(R.string.stats_s_ef342a),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val sleepVal = when {
                                !hcPerm.sleep -> stringResource(R.string.stats_s_6e428a)
                                todayPt.sleepHours > 0f -> String.format(Locale.US, stringResource(R.string.stats_s_343e92), todayPt.sleepHours)
                                else -> stringResource(R.string.stats_s_2f7adc)
                            }
                            val stepsVal = when {
                                !hcPerm.steps -> stringResource(R.string.stats_s_6e428a)
                                yesterdayPt != null && yesterdayPt.steps > 0L -> String.format(Locale.US, stringResource(R.string.stats_s_efd1c8), yesterdayPt.steps)
                                else -> stringResource(R.string.stats_s_2f7adc)
                            }
                            val hrVal = when {
                                !hcPerm.heartRate -> stringResource(R.string.stats_s_6e428a)
                                yesterdayPt != null && yesterdayPt.avgHeartRateBpm > 0L -> "${yesterdayPt.avgHeartRateBpm} bpm"
                                else -> stringResource(R.string.stats_s_2f7adc)
                            }
                            val distVal = when {
                                !hcPerm.distance -> stringResource(R.string.stats_s_6e428a)
                                yesterdayPt != null && yesterdayPt.distanceMeters > 0.0 -> com.phantom.checker.util.HealthConnectBridge.formatDistance(yesterdayPt.distanceMeters)
                                else -> stringResource(R.string.stats_s_2f7adc)
                            }
                            val calVal = when {
                                !hcPerm.calories -> stringResource(R.string.stats_s_6e428a)
                                yesterdayPt != null && yesterdayPt.caloriesKcal > 0.0 -> "${yesterdayPt.caloriesKcal.toLong()} kcal"
                                else -> stringResource(R.string.stats_s_2f7adc)
                            }
                            Text(
                                text = stringResource(R.string.stats_s_915383),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    stringResource(R.string.stats_s_42866c) to sleepVal,
                                    stringResource(R.string.stats_s_ccf2fb) to stepsVal,
                                    stringResource(R.string.stats_s_4045b4) to hrVal,
                                    stringResource(R.string.stats_s_cc17c2) to distVal,
                                    stringResource(R.string.stats_s_bb81d3) to calVal
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
                                            color = if (v == resCtx.getString(R.string.stats_s_6e428a)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            modifier = if (v == resCtx.getString(R.string.stats_s_6e428a)) Modifier.clickable(onClick = openPermHub) else Modifier
                                        )
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        if (trend7d.isEmpty() || !hasAny7d) {
                            Text(
                                text = stringResource(R.string.stats_s_340b3d),
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
                                        text = stringResource(R.string.stats_s_da9ed0),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val stepsSummary = if (!hcPerm.steps) stringResource(R.string.stats_s_6e428a) else if (totalSteps7 > 0L) String.format(Locale.US, stringResource(R.string.stats_s_efd1c8), totalSteps7) else stringResource(R.string.stats_s_2f7adc)
                                    val sleepSummary = if (!hcPerm.sleep) stringResource(R.string.stats_s_6e428a) else if (avgSleep7 > 0f) String.format(Locale.US, stringResource(R.string.stats_s_617b4a), avgSleep7) else stringResource(R.string.stats_s_2f7adc)
                                    val hrSummary = if (!hcPerm.heartRate) stringResource(R.string.stats_s_6e428a) else if (avgHr7 > 0L) stringResource(R.string.stats_s_28fc11, avgHr7) else stringResource(R.string.stats_s_2f7adc)
                                    val distSummary = if (!hcPerm.distance) stringResource(R.string.stats_s_6e428a) else if (totalDist7 > 0.0) com.phantom.checker.util.HealthConnectBridge.formatDistance(totalDist7) else stringResource(R.string.stats_s_2f7adc)
                                    val calSummary = if (!hcPerm.calories) stringResource(R.string.stats_s_6e428a) else if (totalCal7 > 0.0) "${totalCal7.toLong()} kcal" else stringResource(R.string.stats_s_2f7adc)
                                    Text(
                                        text = stringResource(R.string.stats_s_e1df89, stepsSummary, sleepSummary, hrSummary),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.stats_s_24f0b4, distSummary, calSummary),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            val maxSleep = trend7d.maxOf { it.sleepHours }.coerceAtLeast(8f)
                            val maxSteps = trend7d.maxOf { it.steps }.coerceAtLeast(8000L)
                            Text(
                                text = stringResource(R.string.stats_s_db073f),
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
                                text = stringResource(R.string.stats_s_5680f2),
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
                                text = stringResource(R.string.stats_s_4bb17f),
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
                                    Text(stringResource(R.string.stats_s_b6fed9), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
                                    Text(stringResource(R.string.stats_s_ca5f73), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
                                    Text(stringResource(R.string.stats_s_9cbfad), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(68.dp))
                                    Text(stringResource(R.string.stats_s_fc4c08), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
                                    Text(stringResource(R.string.stats_s_081faa), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(74.dp))
                                    Text(stringResource(R.string.stats_s_c09196), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(76.dp))
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                trend7d.reversed().forEach { pt ->
                                    val sCell = if (!hcPerm.sleep) resCtx.getString(R.string.stats_s_6e428a) else if (pt.sleepHours > 0f) String.format(Locale.US, "%.1fh", pt.sleepHours) else "—"
                                    val stCell = if (!hcPerm.steps) resCtx.getString(R.string.stats_s_6e428a) else if (pt.steps > 0L) String.format(Locale.US, "%,d", pt.steps) else "—"
                                    val hrCell = if (!hcPerm.heartRate) resCtx.getString(R.string.stats_s_6e428a) else if (pt.avgHeartRateBpm > 0L) "${pt.avgHeartRateBpm}" else "—"
                                    val dCell = if (!hcPerm.distance) resCtx.getString(R.string.stats_s_6e428a) else com.phantom.checker.util.HealthConnectBridge.formatDistance(pt.distanceMeters)
                                    val cCell = if (!hcPerm.calories) resCtx.getString(R.string.stats_s_6e428a) else if (pt.caloriesKcal > 0.0) "${pt.caloriesKcal.toLong()} kcal" else "—"
                                    Row(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(pt.dayLabel, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.width(48.dp))
                                        Text(sCell, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = if (sCell == resCtx.getString(R.string.stats_s_6e428a)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(56.dp))
                                        Text(stCell, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = if (stCell == resCtx.getString(R.string.stats_s_6e428a)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(68.dp))
                                        Text(hrCell, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = if (hrCell == resCtx.getString(R.string.stats_s_6e428a)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(64.dp))
                                        Text(dCell, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = if (dCell == resCtx.getString(R.string.stats_s_6e428a)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(74.dp))
                                        Text(cCell, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = if (cCell == resCtx.getString(R.string.stats_s_6e428a)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(76.dp))
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        if (loading30d) {
                            Text(
                                text = stringResource(R.string.stats_s_aabaa1),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val hasAny30d = trend30d.any {
                                it.sleepHours > 0f || it.steps > 0L || it.avgHeartRateBpm > 0L || it.distanceMeters > 0.0 || it.caloriesKcal > 0.0
                            }
                            if (trend30d.isEmpty() || !hasAny30d) {
                                Text(
                                    text = stringResource(R.string.stats_s_18bf90),
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
                                            text = stringResource(R.string.stats_s_de6736),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val stepsText = if (!hcPerm.steps) stringResource(R.string.stats_s_6e428a) else if (totalSteps30 > 0L) String.format(Locale.US, stringResource(R.string.stats_s_2c740a), totalSteps30, totalSteps30 / stepDays30) else stringResource(R.string.stats_s_2f7adc)
                                        val sleepText = if (!hcPerm.sleep) stringResource(R.string.stats_s_6e428a) else if (avgSleep30 > 0f) String.format(Locale.US, stringResource(R.string.stats_s_617b4a), avgSleep30) else stringResource(R.string.stats_s_2f7adc)
                                        val hrText = if (!hcPerm.heartRate) stringResource(R.string.stats_s_6e428a) else if (avgHr30 > 0L) stringResource(R.string.stats_s_28fc11, avgHr30) else stringResource(R.string.stats_s_2f7adc)
                                        val distText = if (!hcPerm.distance) stringResource(R.string.stats_s_6e428a) else if (totalDist30 > 0.0) com.phantom.checker.util.HealthConnectBridge.formatDistance(totalDist30) else stringResource(R.string.stats_s_2f7adc)
                                        val calText = if (!hcPerm.calories) stringResource(R.string.stats_s_6e428a) else if (totalCal30 > 0.0) "${totalCal30.toLong()} kcal" else stringResource(R.string.stats_s_2f7adc)
                                        Text(stringResource(R.string.stats_s_ee02a6, stepsText), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                        Text(stringResource(R.string.stats_s_3b4d6c, sleepText, hrText, distText, calText), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }

                                val maxSleep30 = trend30d.maxOf { it.sleepHours }.coerceAtLeast(8f)
                                val maxSteps30 = trend30d.maxOf { it.steps }.coerceAtLeast(8000L)
                                Text(stringResource(R.string.stats_s_293e0d), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text(stringResource(R.string.stats_s_5de13d), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        text = stringResource(R.string.stats_s_aa68ed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = openPermHub)
                    )
                }
                Text(
                    text = stringResource(R.string.stats_s_f9f1dc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


