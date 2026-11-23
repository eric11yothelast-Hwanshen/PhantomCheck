package com.phantom.checker.ui.partner

import com.phantom.checker.ui.components.PhantomMotion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phantom.checker.data.DiaryDayEntity
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.service.DiaryPolicy
import com.phantom.checker.ui.PhantomGlassCard
import com.phantom.checker.util.PhantomPrefs
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * #378 AI 日記／每日回顧卡片（嵌入夥伴分頁頂部＋展開月份瀏覽 Sheet）：
 * - 時間軸為主（純本機 SQL 零 token）、AI 註記為客（預設收合、展開生成一次快取、可滑除與「不準」回饋降權）。
 * - 無 API key 時 100% 可用（月份切換、每月總覽、垂直時間軸、立即整理今日皆零 AI）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PartnerDiaryCard(
    glassState: HazeState
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    var showSheet by remember { mutableStateOf(false) }
    var latestSummary by remember { mutableStateOf<DiaryPolicy.DayTimelineSummary?>(null) }

    LaunchedEffect(Unit) {
        if (!PhantomPrefs.isDiaryDailyEnabled(appCtx)) return@LaunchedEffect
        DiaryPolicy.maybeCatchUp(appCtx)
        val latest = withContext(Dispatchers.IO) {
            val dao = AppDatabaseHolder.db(appCtx).diaryDao()
            val todayKey = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            dao.byDayKey(todayKey) ?: dao.recent(1).firstOrNull()
        }
        latestSummary = latest?.let { DiaryPolicy.parseSummary(it) }
    }

    PhantomGlassCard(
        glassState = glassState,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showSheet = true },
        cornerRadius = 20.dp,
        contentPadding = 14.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📖 每日回顧・生活時間軸",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "翻閱月份 ▸",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            val s = latestSummary
            if (s == null) {
                Text(
                    text = "尚未建立時間軸紀錄，點擊即可免費離線整理今日回顧（零 token）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "${s.dayKey}｜訊息 ${s.messageCount} · 記事 ${s.noteCount} · 完成待辦 ${s.todoDoneCount} · 防詐 ${s.scamCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var currentMonth by remember { mutableStateOf(YearMonth.now()) }
        var monthDays by remember { mutableStateOf<List<DiaryDayEntity>>(emptyList()) }
        var selectedDayKey by remember { mutableStateOf<String?>(null) }
        var annotationExpanded by remember(selectedDayKey) { mutableStateOf(false) }
        var annotationDismissed by remember(selectedDayKey) { mutableStateOf(false) }
        var annotationText by remember(selectedDayKey) { mutableStateOf<String?>(null) }
        var annotationLoading by remember(selectedDayKey) { mutableStateOf(false) }
        var refreshKey by remember { mutableStateOf(0) }

        LaunchedEffect(currentMonth, refreshKey) {
            val prefix = currentMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"))
            val loaded = withContext(Dispatchers.IO) {
                AppDatabaseHolder.db(appCtx).diaryDao().byMonth(prefix)
            }
            monthDays = loaded
            if (selectedDayKey == null || loaded.none { it.dayKey == selectedDayKey }) {
                selectedDayKey = loaded.firstOrNull()?.dayKey
            }
            latestSummary = loaded.firstOrNull()?.let { DiaryPolicy.parseSummary(it) } ?: latestSummary
        }

        val activeEntity = monthDays.firstOrNull { it.dayKey == selectedDayKey }
        val activeSummary = remember(activeEntity) { activeEntity?.let { DiaryPolicy.parseSummary(it) } }
        LaunchedEffect(activeEntity?.dayKey, activeEntity?.annotation) {
            annotationText = activeEntity?.annotation
        }

        val monthSummaries = remember(monthDays) { monthDays.map { DiaryPolicy.parseSummary(it) } }
        val monthMsgTotal = monthSummaries.sumOf { it.messageCount }
        val monthNoteTotal = monthSummaries.sumOf { it.noteCount }
        val monthTodoTotal = monthSummaries.sumOf { it.todoDoneCount }
        val monthScamTotal = monthSummaries.sumOf { it.scamCount }

        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 月份切換器
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                        Text("◀ 上月")
                    }
                    Text(
                        text = currentMonth.format(DateTimeFormatter.ofPattern("yyyy 年 MM 月")),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = { currentMonth = currentMonth.plusMonths(1) },
                        enabled = currentMonth.isBefore(YearMonth.now())
                    ) {
                        Text("下月 ▶")
                    }
                }

                // 每月總覽
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "本月總覽（已收錄 ${monthDays.size} 天）",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "💬 訊息 $monthMsgTotal 則 · 📝 記事 $monthNoteTotal 則 · ✅ 待辦完成 $monthTodoTotal 筆 · 🛡️ 防詐攔截 $monthScamTotal 則",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 立即整理今日（零 token）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val entity = DiaryPolicy.buildAndSaveDayTimeline(appCtx, LocalDate.now())
                                currentMonth = YearMonth.now()
                                selectedDayKey = entity.dayKey
                                refreshKey++
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("立即整理今日時間軸（免費離線）", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (monthDays.isEmpty()) {
                    Text(
                        text = "本月尚無每日回顧紀錄，每日 00:30 會自動本地整理前一日時間軸，也可點上方按鈕立即整理今日。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(monthDays, key = { it.dayKey }) { day ->
                            FilterChip(
                                selected = day.dayKey == selectedDayKey,
                                onClick = { selectedDayKey = day.dayKey },
                                label = { Text(day.dayKey.takeLast(5), style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    activeSummary?.let { summary ->
                        HorizontalDivider()
                        Text(
                            text = "${summary.dayKey} 事實時間軸",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (summary.items.isEmpty()) {
                            Text(
                                text = "當日無可記錄之訊息、記事或待辦活動。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 260.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                summary.items.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = item.timeLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(text = item.icon, style = MaterialTheme.typography.bodyMedium)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (item.detail.isNotBlank()) {
                                                Text(
                                                    text = item.detail,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // AI 註記為客：預設收合、展開生成一次快取、可滑除與標記「不準」降權
                        if (!annotationDismissed && summary.items.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val next = !annotationExpanded
                                                annotationExpanded = next
                                                if (next && annotationText.isNullOrBlank() && !annotationLoading) {
                                                    annotationLoading = true
                                                    scope.launch {
                                                        annotationText = DiaryPolicy.getOrGenerateAnnotation(appCtx, summary.dayKey)
                                                        annotationLoading = false
                                                        refreshKey++
                                                    }
                                                }
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (annotationExpanded) "✨ AI 回顧註記（點擊收合）" else "✨ AI 回顧註記（預設收合・點擊展開）",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(
                                            onClick = { annotationDismissed = true },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("隱藏", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    AnimatedVisibility(
 visible = annotationExpanded,
 enter = PhantomMotion.ExpandEnter,
 exit = PhantomMotion.ExpandExit
 ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            when {
                                                annotationLoading -> Text(
                                                    text = "正在依據時間軸事實生成註記…",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                !annotationText.isNullOrBlank() -> {
                                                    Text(
                                                        text = annotationText.orEmpty(),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.End
                                                    ) {
                                                        TextButton(
                                                            onClick = {
                                                                scope.launch {
                                                                    DiaryPolicy.markAnnotationInaccurate(
                                                                        appCtx,
                                                                        summary.dayKey,
                                                                        summary.annotationCategory
                                                                    )
                                                                    annotationText = null
                                                                    annotationDismissed = true
                                                                    refreshKey++
                                                                }
                                                            }
                                                        ) {
                                                            Text("不準（降低同類註記權重）", style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    }
                                                }
                                                else -> {
                                                    val reason = DiaryPolicy.diagnoseAnnotationUnavailable(appCtx, summary)
                                                    val dominantCat = summary.annotationCategory?.ifBlank { null }
                                                        ?: DiaryPolicy.dominantCategoryOf(summary)
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = when (reason) {
                                                                DiaryPolicy.AnnotationUnavailableReason.TOKEN_SAVER ->
                                                                    "省 token 模式已開啟，回顧註記暫停"
                                                                DiaryPolicy.AnnotationUnavailableReason.NO_PROFILE ->
                                                                    "尚未設定 AI 供應商，註記暫停"
                                                                DiaryPolicy.AnnotationUnavailableReason.DISLIKED_SUPPRESSED ->
                                                                    "你先前將同類註記標記不準，已自動停權"
                                                                DiaryPolicy.AnnotationUnavailableReason.OTHER ->
                                                                    "暫時無法生成註記；上方本地時間軸仍可完整瀏覽"
                                                            },
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        when (reason) {
                                                            DiaryPolicy.AnnotationUnavailableReason.TOKEN_SAVER -> {
                                                                TextButton(
                                                                    onClick = {
                                                                        ctx.startActivity(
                                                                            android.content.Intent(
                                                                                ctx,
                                                                                com.phantom.checker.ui.AssistantHubActivity::class.java
                                                                            )
                                                                        )
                                                                    },
                                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                                ) {
                                                                    Text("設定", style = MaterialTheme.typography.labelSmall)
                                                                }
                                                            }
                                                            DiaryPolicy.AnnotationUnavailableReason.NO_PROFILE -> {
                                                                TextButton(
                                                                    onClick = {
                                                                        ctx.startActivity(
                                                                            android.content.Intent(
                                                                                ctx,
                                                                                com.phantom.checker.ui.ApiKeySettingsActivity::class.java
                                                                            )
                                                                        )
                                                                    },
                                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                                ) {
                                                                    Text("前往設定", style = MaterialTheme.typography.labelSmall)
                                                                }
                                                            }
                                                            DiaryPolicy.AnnotationUnavailableReason.DISLIKED_SUPPRESSED -> {
                                                                TextButton(
                                                                    onClick = {
                                                                        DiaryPolicy.resetDislikeForCategory(appCtx, dominantCat)
                                                                        annotationLoading = true
                                                                        scope.launch {
                                                                            annotationText = DiaryPolicy.getOrGenerateAnnotation(appCtx, summary.dayKey)
                                                                            annotationLoading = false
                                                                            refreshKey++
                                                                        }
                                                                    },
                                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                                ) {
                                                                    Text("重新啟用", style = MaterialTheme.typography.labelSmall)
                                                                }
                                                            }
                                                            DiaryPolicy.AnnotationUnavailableReason.OTHER -> {
                                                                TextButton(
                                                                    onClick = {
                                                                        annotationLoading = true
                                                                        scope.launch {
                                                                            annotationText = DiaryPolicy.getOrGenerateAnnotation(appCtx, summary.dayKey)
                                                                            annotationLoading = false
                                                                            refreshKey++
                                                                        }
                                                                    },
                                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                                ) {
                                                                    Text("重試", style = MaterialTheme.typography.labelSmall)
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

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = { showSheet = false }) {
                        Text("完成")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
