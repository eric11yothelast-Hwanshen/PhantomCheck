// #301 來源：MainActivity.kt（MessagesHomeTab、SearchResultsSection、MessageFilter、SearchRiskFilter、AppGroupUi）
package com.phantom.checker.ui.home

import com.phantom.checker.ui.components.PhantomAnimatedCount
import com.phantom.checker.ui.components.PhantomChevron
import com.phantom.checker.ui.components.phantomEnter
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import com.phantom.checker.ui.components.AuroraTokens
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

import com.phantom.checker.ui.*

private const val PAGE_SIZE = 100
private const val MAX_BATCH_SELECT = 100

internal sealed interface MessageFilter {
    data object All : MessageFilter
    data object Unread : MessageFilter
    data object Starred : MessageFilter
    data object Scams : MessageFilter
    data object Filtered : MessageFilter
    data object Noted : MessageFilter
    data object Todoed : MessageFilter
    data class Tag(val tag: TagEntity) : MessageFilter
}

internal enum class SearchRiskFilter(val label: String) {
    ALL("全部風險"),
    SCAM("僅詐騙"),
    SUSPICIOUS("僅疑似"),
    STARRED("僅星標")
}

internal data class AppGroupUi(
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


@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SearchResultsSection(
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun MessagesHomeTab(
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

    val homeSettingsVersion by PhantomSettingsBus.version.collectAsState()
    val layoutMode = remember(homeSettingsVersion) { PhantomPrefs.getHomeCardLayoutMode(ctx) }
    val messagesFontScale = remember(homeSettingsVersion) { PhantomPrefs.getMessagesFontScale(ctx) }
    val baseDensity = LocalDensity.current
    val messagesDensity = remember(baseDensity, messagesFontScale) {
        androidx.compose.ui.unit.Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * messagesFontScale
        )
    }
    val isOverlapMode = activeFilter == MessageFilter.All &&
        layoutMode == PhantomPrefs.HomeCardLayoutMode.CENTER

    androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides messagesDensity) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(if (isOverlapMode) 8.dp else 12.dp)
    ) {
        AnimatedVisibility(
 visible = offlineRetryEnabled && pendingRetryCount > 0,
 enter = PhantomMotion.ExpandEnter,
 exit = PhantomMotion.ExpandExit
 ) {
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

        // §4.1 統計條：單張 20dp surfaceContainer 三格等寬（移除右側重複之重新整理／設定鈕，已搬至頂部應用列）
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    onReportBounds(OnboardingTargets.STATS_CARD, coords.boundsInRoot())
                },
            shape = RoundedCornerShape(AuroraTokens.CornerCard),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PhantomAnimatedCount(
                            count = todayCount,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "今日",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PhantomAnimatedCount(
                            count = unreadCount,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (unreadCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "未讀",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PhantomAnimatedCount(
                            count = todayScamCount,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (todayScamCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "詐騙",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (unreadCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "長按 App 可釘選、靜音或刪除",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onMarkAllRead,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("全部標已讀", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // §4.1 篩選列：單排 3 顆智慧 chips（全部／未讀／高風險）＋「更多」ModalBottomSheet
        val activeTag = (activeFilter as? MessageFilter.Tag)?.tag
        val visibleFilters = PhantomPrefs.getVisibleHomeFilters(ctx)
        var filterChipsCollapsed by remember { mutableStateOf(PhantomPrefs.isHomeFilterChipsCollapsed(ctx)) }
        var showMoreFiltersSheet by remember { mutableStateOf(false) }
        val chipShape = RoundedCornerShape(AuroraTokens.CornerChip)
        val hasExtraActiveFilter = activeFilter != MessageFilter.All &&
            activeFilter != MessageFilter.Unread &&
            activeFilter != MessageFilter.Scams

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    onReportBounds(OnboardingTargets.FILTER_CHIPS, coords.boundsInRoot())
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item(key = "fc_all") {
                    FilterChip(
                        selected = activeFilter == MessageFilter.All,
                        onClick = { onSelectFilter(MessageFilter.All) },
                        shape = chipShape,
                        label = { Text("全部", style = MaterialTheme.typography.labelMedium) }
                    )
                }
                item(key = "fc_unread") {
                    FilterChip(
                        selected = activeFilter == MessageFilter.Unread,
                        onClick = { onSelectFilter(MessageFilter.Unread) },
                        shape = chipShape,
                        label = { Text("未讀", style = MaterialTheme.typography.labelMedium) }
                    )
                }
                item(key = "fc_scams") {
                    FilterChip(
                        selected = activeFilter == MessageFilter.Scams,
                        onClick = { onSelectFilter(MessageFilter.Scams) },
                        shape = chipShape,
                        label = { Text("高風險", style = MaterialTheme.typography.labelMedium) }
                    )
                }
                if (hasExtraActiveFilter) {
                    item(key = "fc_active_extra") {
                        val extraLabel = when (activeFilter) {
                            MessageFilter.Starred -> "⭐ 星標"
                            MessageFilter.Filtered -> "已過濾"
                            MessageFilter.Noted -> "已記事"
                            MessageFilter.Todoed -> "已待辦"
                            is MessageFilter.Tag -> "🏷 ${activeFilter.tag.name}"
                            else -> ""
                        }
                        FilterChip(
                            selected = true,
                            onClick = { onSelectFilter(MessageFilter.All) },
                            shape = chipShape,
                            label = { Text("$extraLabel ×", style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
                if (!filterChipsCollapsed) {
                    if ("starred" in visibleFilters && activeFilter != MessageFilter.Starred) {
                        item(key = "fc_starred") {
                            FilterChip(
                                selected = false,
                                onClick = { onSelectFilter(MessageFilter.Starred) },
                                shape = chipShape,
                                label = { Text("星標", style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                    if ("filtered" in visibleFilters && activeFilter != MessageFilter.Filtered) {
                        item(key = "fc_filtered") {
                            FilterChip(
                                selected = false,
                                onClick = { onSelectFilter(MessageFilter.Filtered) },
                                shape = chipShape,
                                label = { Text("已過濾", style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                }
                item(key = "fc_more") {
                    AssistChip(
                        onClick = { showMoreFiltersSheet = true },
                        shape = chipShape,
                        label = { Text("更多", style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        }

        if (showMoreFiltersSheet) {
            ModalBottomSheet(onDismissRequest = { showMoreFiltersSheet = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "更多篩選條件",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = activeFilter == MessageFilter.Starred,
                            onClick = {
                                onSelectFilter(MessageFilter.Starred)
                                showMoreFiltersSheet = false
                            },
                            label = { Text("⭐ 星標訊息") }
                        )
                        FilterChip(
                            selected = activeFilter == MessageFilter.Filtered,
                            onClick = {
                                onSelectFilter(MessageFilter.Filtered)
                                showMoreFiltersSheet = false
                            },
                            label = { Text("已過濾訊息") }
                        )
                        FilterChip(
                            selected = activeFilter == MessageFilter.Noted,
                            onClick = {
                                onSelectFilter(MessageFilter.Noted)
                                showMoreFiltersSheet = false
                            },
                            label = { Text("📝 已記事") }
                        )
                        FilterChip(
                            selected = activeFilter == MessageFilter.Todoed,
                            onClick = {
                                onSelectFilter(MessageFilter.Todoed)
                                showMoreFiltersSheet = false
                            },
                            label = { Text("✓ 已待辦") }
                        )
                        FilterChip(
                            selected = activeTag != null,
                            onClick = {
                                showMoreFiltersSheet = false
                                onOpenTagPicker()
                            },
                            label = { Text(if (activeTag != null) "🏷 ${activeTag.name} ▾" else "🏷 依標籤篩選 ▾") }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "預設單排收合篩選列",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        com.phantom.checker.ui.components.FluidSwitch(
                            checked = filterChipsCollapsed,
                            onCheckedChange = { next ->
                                filterChipsCollapsed = next
                                PhantomPrefs.setHomeFilterChipsCollapsed(ctx, next)
                            }
                        )
                    }
                }
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
                    val favMax = remember(homeSettingsVersion) { PhantomPrefs.getFavoriteAppsMaxVisible(ctx) }
                    val visibleFavorites = remember(favoritePackages, favMax) {
                        if (favMax > 0) favoritePackages.take(favMax) else favoritePackages
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(visibleFavorites, key = { it }) { pkg ->
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
        val isCompactCard = remember(homeSettingsVersion) {
            PhantomPrefs.getCardDensity(ctx) == com.phantom.checker.util.CardDensity.COMPACT
        }
        val is24h = remember(homeSettingsVersion) { PhantomPrefs.is24HourFormat(ctx) }
        val listSpacing = if (activeFilter == MessageFilter.All) {
            if (layoutMode == PhantomPrefs.HomeCardLayoutMode.CENTER) 0.dp else 2.dp
        } else {
            8.dp
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.spacedBy(listSpacing)
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
                else -> if (layoutMode == PhantomPrefs.HomeCardLayoutMode.CENTER) {
                    // #270 中央聚焦垂直滾輪堆疊：92dp 緊湊卡高、40dp 露出帶、12dp 內距三段式佈局
                    item(key = "home_center_stack") {
                        CenterFocusCardStack(
                            items = appGroups,
                            key = { it.packageName },
                            modifier = Modifier.fillParentMaxSize(),
                            edgeGap = 6.dp
                        ) { app ->
                            val isMuted = app.packageName in mutedPackages
                            val isFirstApp = app.packageName == appGroups.firstOrNull()?.packageName
                            val timeText = remember(app.lastTs, is24h) {
                                PhantomPrefs.formatMessageTime(ctx, app.lastTs, includeDate = true)
                            }
                            PhantomGlassCard(
                                glassState = glassState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (isFirstApp) {
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
                                    ),
                                cornerRadius = 20.dp,
                                contentPadding = 12.dp,
                                overlapOpaqueBoost = true
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(),
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
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // 第一段：標題列
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
                                        // 第二段：單行訊息預覽
                                        Text(
                                            text = app.lastPreview.ifBlank { "共 ${app.totalCount} 則訊息" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        // 第三段（底部 40dp 露出帶）：App 名稱＋未讀數＋分類標籤列
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = buildString {
                                                    append(app.appLabel)
                                                    when {
                                                        isMuted -> append(" · 靜音")
                                                        app.unreadTodayCount > 0 -> append(" · 未讀 ${app.unreadTodayCount}")
                                                        app.todayCount > 0 -> append(" · 今日 ${app.todayCount}")
                                                        else -> append(" · ${app.totalCount} 則")
                                                    }
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (app.latestTags.isNotEmpty()) {
                                                MessageTagChipRow(
                                                    tags = app.latestTags,
                                                    maxShow = 2
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    itemsIndexed(appGroups, key = { _, app -> app.packageName }) { idx, app ->
                        val isMuted = app.packageName in mutedPackages
                        val timeText = remember(app.lastTs, is24h) {
                            PhantomPrefs.formatMessageTime(ctx, app.lastTs, includeDate = true)
                        }
                        val itemStackModifier = Modifier
                        val rowModifier = Modifier
                            .animateItem()
                            .phantomEnter(idx, key = app.packageName)
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
                        val segShape = AuroraTokens.segmentedItemShape(idx, appGroups.size)
                        Box(modifier = Modifier.fillMaxWidth().then(itemStackModifier)) {
                        Surface(
                            modifier = rowModifier.clip(segShape),
                            shape = segShape,
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 72.dp)
                                    .padding(horizontal = 14.dp, vertical = if (isCompactCard) 8.dp else 12.dp),
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
                    itemsIndexed(filteredMessages, key = { _, it -> it.id }, contentType = { _, _ -> "message_card" }) { msgIndex, m ->
                        val checked = m.id in selectedIds
                        val msgTags = messageTagsMap[m.id].orEmpty()
                        Row(
                            modifier = Modifier.animateItem().phantomEnter(msgIndex, key = m.id).fillMaxWidth(),
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
}

