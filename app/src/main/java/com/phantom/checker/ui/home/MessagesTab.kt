// #301 來源：MainActivity.kt（MessagesHomeTab、SearchResultsSection、MessageFilter、SearchRiskFilter、AppGroupUi）
package com.phantom.checker.ui.home

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phantom.checker.R
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.data.MessageTagRow
import com.phantom.checker.data.TagEntity
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.ui.*
import com.phantom.checker.ui.components.AuroraTokens
import com.phantom.checker.ui.components.PhantomAnimatedCount
import com.phantom.checker.ui.components.PhantomMotion
import com.phantom.checker.ui.components.phantomEnter
import com.phantom.checker.ui.components.pressableScale
import com.phantom.checker.util.PhantomPrefs
import com.phantom.checker.util.PhantomSettingsBus
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGE_SIZE = 100
private const val MAX_BATCH_SELECT = 100

/** #527 Material Outlined Summarize vector (no material-icons-extended dependency required). */
private val GroupReportSummaryIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "GroupReportSummary",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(15f, 3f)
            horizontalLineTo(5f)
            curveTo(3.9f, 3f, 3.01f, 3.9f, 3.01f, 5f)
            lineTo(3f, 19f)
            curveTo(3f, 20.1f, 3.89f, 21f, 4.99f, 21f)
            horizontalLineTo(19f)
            curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
            verticalLineTo(9f)
            lineTo(15f, 3f)
            close()
            moveTo(5f, 19f)
            verticalLineTo(5f)
            horizontalLineTo(14f)
            verticalLineTo(10f)
            horizontalLineTo(19f)
            verticalLineTo(19f)
            horizontalLineTo(5f)
            close()
            moveTo(8f, 12f)
            horizontalLineTo(16f)
            verticalLineTo(14f)
            horizontalLineTo(8f)
            close()
            moveTo(8f, 15.5f)
            horizontalLineTo(13f)
            verticalLineTo(17.5f)
            horizontalLineTo(8f)
            close()
            moveTo(8f, 8.5f)
            horizontalLineTo(11f)
            verticalLineTo(10.5f)
            horizontalLineTo(8f)
            close()
        }
    }.build()
}

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

internal enum class SearchRiskFilter(@StringRes val labelRes: Int) {
    ALL(R.string.messages_risk_filter_all),
    SCAM(R.string.messages_risk_filter_scam),
    SUSPICIOUS(R.string.messages_risk_filter_suspicious),
    STARRED(R.string.messages_risk_filter_starred)
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
                title = stringResource(R.string.messages_s_82c6f3),
                description = stringResource(R.string.messages_s_26c779, query)
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
                            stringResource(R.string.messages_s_9ae592, selectedIds.size, MAX_BATCH_SELECT)
                        } else {
                            stringResource(R.string.messages_s_1a256e, results.size)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onToggleMultiSelect) {
                        Text(if (isMultiSelectMode) stringResource(R.string.common_done) else stringResource(R.string.common_select))
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
    onReportBounds: (String, Rect) -> Unit,
    isAppMultiSelectMode: Boolean = false,
    selectedAppPackages: Set<String> = emptySet(),
    onToggleAppSelect: (String) -> Unit = {}
) {
    val resCtx = LocalContext.current
    val ctx = LocalContext.current
    val groupMap = remember(appGroups) { appGroups.associateBy { it.packageName } }
    val listState = rememberLazyListState()
    val pendingRetryCount by remember(ctx) {
        AppDatabaseHolder.db(ctx).pendingAiAnalysisDao().observePendingCount()
    }.collectAsState(initial = 0)
    val homeSettingsVersion by PhantomSettingsBus.version.collectAsState()
    var isRetryPaused by remember(homeSettingsVersion) { mutableStateOf(PhantomPrefs.isAiOfflineRetryPaused(ctx)) }
    val offlineRetryEnabled = remember(homeSettingsVersion) { PhantomPrefs.isAiOfflineRetryEnabled(ctx) }

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
                                stringResource(R.string.messages_s_937ff4, pendingRetryCount)
                            } else {
                                stringResource(R.string.messages_s_d3b2d2, pendingRetryCount)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isRetryPaused) {
                                stringResource(R.string.messages_s_a0dd17)
                            } else {
                                stringResource(R.string.messages_s_95abea)
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
                            Text(if (isRetryPaused) stringResource(R.string.common_resume) else stringResource(R.string.common_pause))
                        }
                        TextButton(
                            onClick = {
                                com.phantom.checker.service.PendingAiAnalysisPolicy.retryNow(ctx)
                                isRetryPaused = false
                            }
                        ) {
                            Text(stringResource(R.string.common_retry_now))
                        }
                    }
                }
            }
        }

        // §4.1 / #416 統計條：單行三段式（1dp×28dp 垂直分隔線）＋同行尾端「全部標已讀」，unread 99→0 高度零跳動
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    onReportBounds(OnboardingTargets.STATS_CARD, coords.boundsInRoot())
                },
            shape = RoundedCornerShape(AuroraTokens.CornerCard),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PhantomAnimatedCount(
                        count = todayCount,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.common_today),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )
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
                        text = stringResource(R.string.common_unread),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )
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
                        text = stringResource(R.string.common_scam),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (unreadCount > 0) {
                    TextButton(
                        onClick = onMarkAllRead,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.common_mark_all_read),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // §4.1 篩選列：單排 3 顆智慧 chips（全部／未讀／高風險）＋「更多」ModalBottomSheet
        val activeTag = (activeFilter as? MessageFilter.Tag)?.tag
        val visibleFilters = remember(homeSettingsVersion) { PhantomPrefs.getVisibleHomeFilters(ctx) }
        var filterChipsCollapsed by remember(homeSettingsVersion) { mutableStateOf(PhantomPrefs.isHomeFilterChipsCollapsed(ctx)) }
        var showMoreFiltersSheet by remember { mutableStateOf(false) }
        var showGroupReportsSheet by remember { mutableStateOf(false) }
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
                        label = { Text(stringResource(R.string.common_all), style = MaterialTheme.typography.labelMedium) }
                    )
                }
                item(key = "fc_unread") {
                    FilterChip(
                        selected = activeFilter == MessageFilter.Unread,
                        onClick = { onSelectFilter(MessageFilter.Unread) },
                        shape = chipShape,
                        label = { Text(stringResource(R.string.common_unread), style = MaterialTheme.typography.labelMedium) }
                    )
                }
                item(key = "fc_scams") {
                    FilterChip(
                        selected = activeFilter == MessageFilter.Scams,
                        onClick = { onSelectFilter(MessageFilter.Scams) },
                        shape = chipShape,
                        label = { Text(stringResource(R.string.common_high_risk), style = MaterialTheme.typography.labelMedium) }
                    )
                }
                if (hasExtraActiveFilter) {
                    item(key = "fc_active_extra") {
                        val extraLabel = when (activeFilter) {
                            MessageFilter.Starred -> stringResource(R.string.messages_s_e051e8)
                            MessageFilter.Filtered -> stringResource(R.string.common_filtered)
                            MessageFilter.Noted -> stringResource(R.string.common_noted)
                            MessageFilter.Todoed -> stringResource(R.string.common_todoed)
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
                                label = { Text(stringResource(R.string.common_star), style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                    if ("filtered" in visibleFilters && activeFilter != MessageFilter.Filtered) {
                        item(key = "fc_filtered") {
                            FilterChip(
                                selected = false,
                                onClick = { onSelectFilter(MessageFilter.Filtered) },
                                shape = chipShape,
                                label = { Text(stringResource(R.string.common_filtered), style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                }
                item(key = "fc_more") {
                    AssistChip(
                        onClick = { showMoreFiltersSheet = true },
                        shape = chipShape,
                        label = { Text(stringResource(R.string.common_more), style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
            // #527 Group Reports icon + text entry button (34dp pill height, >=44dp touch target)
            Box(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .pressableScale()
                    .clickable { showGroupReportsSheet = true },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = chipShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.heightIn(min = 34.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = GroupReportSummaryIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.group_report_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (showGroupReportsSheet) {
            val reportDao = remember(ctx) { AppDatabaseHolder.db(ctx.applicationContext).groupReportDao() }
            val recentReports by reportDao.observeReports(limit = 50).collectAsState(initial = emptyList())
            val sheetScope = rememberCoroutineScope()
            ModalBottomSheet(onDismissRequest = { showGroupReportsSheet = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.group_report_viewer_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                showGroupReportsSheet = false
                                ctx.startActivity(
                                    Intent(ctx, SettingsActivity::class.java)
                                        .putExtra("highlight_card_key", "ai")
                                )
                            }
                        ) {
                            Text(stringResource(R.string.group_report_manage_settings))
                        }
                    }
                    GroupReportsViewer(
                        reports = recentReports,
                        onDeleteReport = { reportId ->
                            sheetScope.launch {
                                withContext(Dispatchers.IO) { reportDao.deleteReportById(reportId) }
                            }
                        },
                        maxListHeight = 460.dp
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
                        text = stringResource(R.string.messages_s_4f346f),
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
                            label = { Text(stringResource(R.string.messages_s_046721)) }
                        )
                        FilterChip(
                            selected = activeFilter == MessageFilter.Filtered,
                            onClick = {
                                onSelectFilter(MessageFilter.Filtered)
                                showMoreFiltersSheet = false
                            },
                            label = { Text(stringResource(R.string.messages_s_aff09c)) }
                        )
                        FilterChip(
                            selected = activeFilter == MessageFilter.Noted,
                            onClick = {
                                onSelectFilter(MessageFilter.Noted)
                                showMoreFiltersSheet = false
                            },
                            label = { Text(stringResource(R.string.messages_s_c474a3)) }
                        )
                        FilterChip(
                            selected = activeFilter == MessageFilter.Todoed,
                            onClick = {
                                onSelectFilter(MessageFilter.Todoed)
                                showMoreFiltersSheet = false
                            },
                            label = { Text(stringResource(R.string.messages_s_1a1ad5)) }
                        )
                        FilterChip(
                            selected = activeTag != null,
                            onClick = {
                                showMoreFiltersSheet = false
                                onOpenTagPicker()
                            },
                            label = { Text(if (activeTag != null) "🏷 ${activeTag.name} ▾" else stringResource(R.string.messages_s_42a0b8)) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.messages_s_48b057),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        com.phantom.checker.ui.components.FluidSwitch(
                            checked = filterChipsCollapsed,
                            onCheckedChange = { next ->
                                filterChipsCollapsed = next
                                PhantomPrefs.setHomeFilterChipsCollapsed(ctx, next)
                                PhantomSettingsBus.notifyChanged()
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
                    text = stringResource(R.string.messages_s_ba2585),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (favoritePackages.isEmpty()) {
                    Text(
                        text = stringResource(R.string.messages_s_a1b7c0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val favMax = remember(homeSettingsVersion) { PhantomPrefs.getFavoriteAppsMaxVisible(ctx) }
                    val visibleFavorites = remember(favoritePackages, favMax) {
                        if (favMax > 0) favoritePackages.take(favMax) else favoritePackages
                    }
                    val resolvedFavLabels = remember { mutableStateMapOf<String, String>() }
                    val missingLabelPkgs = remember(visibleFavorites, groupMap, resolvedFavLabels.size) {
                        visibleFavorites.filter { pkg -> groupMap[pkg] == null && pkg !in resolvedFavLabels }
                    }
                    LaunchedEffect(missingLabelPkgs) {
                        if (missingLabelPkgs.isEmpty()) return@LaunchedEffect
                        val appCtx = ctx.applicationContext
                        val resolved = withContext(Dispatchers.IO) {
                            val pm = appCtx.packageManager
                            missingLabelPkgs.associateWith { pkg ->
                                try {
                                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                                } catch (_: Exception) {
                                    pkg
                                }
                            }
                        }
                        resolvedFavLabels.putAll(resolved)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(visibleFavorites, key = { it }) { pkg ->
                            val group = groupMap[pkg]
                            val label = group?.appLabel ?: resolvedFavLabels[pkg] ?: pkg
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
                                title = stringResource(R.string.messages_s_d0ea34),
                                description = if (isListenerEnabled) {
                                    stringResource(R.string.messages_s_e5e8cd)
                                } else {
                                    stringResource(R.string.messages_s_777115)
                                },
                                actionLabel = if (!isListenerEnabled) stringResource(R.string.messages_s_d04019) else null,
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
                                        onClick = {
                                            if (isAppMultiSelectMode) {
                                                onToggleAppSelect(app.packageName)
                                            } else {
                                                onOpenApp(app.packageName, app.appLabel)
                                            }
                                        },
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
                                    if (isAppMultiSelectMode) {
                                        Checkbox(
                                            checked = app.packageName in selectedAppPackages,
                                            onCheckedChange = { onToggleAppSelect(app.packageName) }
                                        )
                                    }
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
                                                            text = stringResource(R.string.common_muted),
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
                                                            text = stringResource(R.string.messages_s_b7a0c3, app.unreadTodayCount),
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
                                                            text = stringResource(R.string.messages_s_1e18b2, app.todayCount),
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
                                            text = app.lastPreview.ifBlank { resCtx.getString(R.string.messages_s_2d62bd, app.totalCount) },
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
                                                        isMuted -> append(resCtx.getString(R.string.messages_s_8199d4))
                                                        app.unreadTodayCount > 0 -> append(resCtx.getString(R.string.messages_s_b81fb6, app.unreadTodayCount))
                                                        app.todayCount > 0 -> append(resCtx.getString(R.string.messages_s_74f22c, app.todayCount))
                                                        else -> append(resCtx.getString(R.string.messages_s_11bd16, app.totalCount))
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
                                onClick = {
                                    if (isAppMultiSelectMode) {
                                        onToggleAppSelect(app.packageName)
                                    } else {
                                        onOpenApp(app.packageName, app.appLabel)
                                    }
                                },
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
                                if (isAppMultiSelectMode) {
                                    Checkbox(
                                        checked = app.packageName in selectedAppPackages,
                                        onCheckedChange = { onToggleAppSelect(app.packageName) }
                                    )
                                }
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
                                                        text = stringResource(R.string.common_muted),
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
                                                        text = stringResource(R.string.messages_s_b7a0c3, app.unreadTodayCount),
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
                                                        text = stringResource(R.string.messages_s_1e18b2, app.todayCount),
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
                                        text = app.lastPreview.ifBlank { resCtx.getString(R.string.messages_s_2d62bd, app.totalCount) },
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
                                title = stringResource(R.string.messages_s_dc276c),
                                description = stringResource(R.string.messages_s_89b5c3),
                                actionLabel = stringResource(R.string.messages_s_cdee4b),
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
                                    stringResource(R.string.messages_s_9ae592, selectedIds.size, MAX_BATCH_SELECT)
                                } else {
                                    stringResource(R.string.messages_s_f0a3ca, filteredMessages.size)
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
                                Text(if (isMultiSelectMode) stringResource(R.string.common_done) else stringResource(R.string.common_select))
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

