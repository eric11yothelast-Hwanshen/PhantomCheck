package com.phantom.checker.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.phantom.checker.shared.ConversationRes
import com.phantom.checker.shared.DeepAnalyzeReq
import com.phantom.checker.shared.LinkMsgType
import com.phantom.checker.shared.MarkReadReq
import com.phantom.checker.shared.MessageSummaryDto
import com.phantom.checker.shared.MessageTagReq
import com.phantom.checker.shared.MessagesListReq
import com.phantom.checker.shared.MutedAppToggleReq
import com.phantom.checker.shared.NoteAddReq
import com.phantom.checker.shared.QuickReplySendReq
import com.phantom.checker.shared.StarToggleReq
import com.phantom.checker.shared.TagItemDto
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private val MSG_DAY_FMT = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

private data class AppGroupSummary(
    val packageName: String,
    val appLabel: String,
    val unreadCount: Int,
    val latestMessage: MessageSummaryDto,
    val highestRisk: String,
    val hasStarred: Boolean,
    val conversations: List<ConversationSummary>
)

private fun riskSemanticColor(riskLabel: String, tokens: DesktopThemeTokens): Color = when (riskLabel.lowercase()) {
    "scam" -> tokens.error
    "suspicious", "warning" -> tokens.warning
    else -> tokens.success
}

/**
 * #520 §7 commit 10：桌面訊息頁重編
 * - 兩層工具列（第一層搜尋＋橫向捲動 5 篩選 chips；第二層動作列＋ⓘ已讀說明切換＋Large 資訊欄開關）
 * - 四級響應式窗格：COMPACT 單窗格推入與返回、MEDIUM/EXPANDED 雙窗格、LARGE 三窗格（含右側對話資訊欄）
 * - 詳情區新增日期分隔線、未讀「新訊息」分隔線與「捲到最新」浮動按鈕
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopMessagesPage(
    windowClass: DesktopWindowClass,
    settings: DesktopSettings,
    tokens: DesktopThemeTokens,
    accentColor: Color,
    cardPad: Dp,
    listSpace: Dp,
    timeFmt: SimpleDateFormat,
    conversations: List<ConversationSummary>,
    messages: List<MessageSummaryDto>,
    tags: List<TagItemDto>,
    conversationThread: ConversationRes?,
    msgFilter: String,
    onMsgFilterChange: (String) -> Unit,
    msgSearch: String,
    onMsgSearchChange: (String) -> Unit,
    searchFocusRequester: FocusRequester,
    selectedConvKey: String,
    onSelectConversation: (String, Long) -> Unit,
    selectedMsg: MessageSummaryDto?,
    selectedConv: ConversationSummary?,
    onSelectMessageId: (Long) -> Unit,
    batchSelectMode: Boolean,
    onToggleBatchSelectMode: () -> Unit,
    selectedBatchKeys: Set<String>,
    onSelectedBatchKeysChange: (Set<String>) -> Unit,
    onClearBatchAndExit: () -> Unit,
    deepAnalyzingMsgId: Long?,
    onSetDeepAnalyzingMsgId: (Long) -> Unit,
    replyText: String,
    onReplyTextChange: (String) -> Unit,
    messagesListState: LazyListState,
    threadListState: LazyListState,
    isLoadingMore: Boolean = false,
    hasMoreMessages: Boolean = false,
    lastSyncedAtMs: Long = 0L,
    loadMoreError: String? = null,
    onLoadMoreMessages: () -> Unit = {},
    onRefreshMessages: () -> Unit,
    sendOrNotify: suspend (String, Any) -> Boolean
) {
    val scope = rememberCoroutineScope()
    var showMarkReadHint by remember { mutableStateOf(false) }
    var showInfoPane by remember { mutableStateOf(true) }
    var compactShowDetail by remember(windowClass) { mutableStateOf(false) }
    var quickInfoNote by remember(selectedConvKey) { mutableStateOf("") }
    var expandedAppPackages by remember { mutableStateOf<Set<String>>(emptySet()) }

    val visibleConversations = remember(conversations, msgFilter, msgSearch) {
        val byFilter = when (msgFilter) {
            "unread" -> conversations.filter { it.unreadCount > 0 }
            "starred" -> conversations.filter { it.hasStarred }
            "group" -> conversations.filter { it.isGroup }
            "scam" -> conversations.filter { it.highestRisk.equals("scam", ignoreCase = true) }
            else -> conversations
        }
        val q = msgSearch.trim()
        if (q.isBlank()) byFilter
        else byFilter.filter { conv ->
            conv.appLabel.contains(q, ignoreCase = true) ||
                conv.sender.contains(q, ignoreCase = true) ||
                conv.latestMessage.preview.contains(q, ignoreCase = true) ||
                conv.packageName.contains(q, ignoreCase = true)
        }
    }

    val appGroups = remember(visibleConversations) {
        visibleConversations
            .groupBy { it.packageName }
            .map { (pkg, convList) ->
                val sortedConvs = convList.sortedByDescending { it.latestMessage.timestamp }
                val latestConv = sortedConvs.first()
                val highestRisk = when {
                    sortedConvs.any { it.highestRisk.equals("scam", ignoreCase = true) } -> "scam"
                    sortedConvs.any { it.highestRisk.equals("suspicious", ignoreCase = true) } -> "suspicious"
                    sortedConvs.any { it.highestRisk.equals("warning", ignoreCase = true) } -> "warning"
                    else -> "safe"
                }
                AppGroupSummary(
                    packageName = pkg,
                    appLabel = latestConv.appLabel,
                    unreadCount = sortedConvs.sumOf { it.unreadCount },
                    latestMessage = latestConv.latestMessage,
                    highestRisk = highestRisk,
                    hasStarred = sortedConvs.any { it.hasStarred },
                    conversations = sortedConvs
                )
            }
            .sortedByDescending { it.latestMessage.timestamp }
    }

    // #531 Scroll-driven pagination (zero polling): load next page when reaching bottom 3 items
    LaunchedEffect(messagesListState, hasMoreMessages, isLoadingMore, loadMoreError, visibleConversations.size) {
        if (!hasMoreMessages || isLoadingMore || loadMoreError != null) return@LaunchedEffect
        snapshotFlow {
            val info = messagesListState.layoutInfo
            val total = info.totalItemsCount
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            total > 0 && lastVisible >= (total - 3).coerceAtLeast(0)
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                onLoadMoreMessages()
            }
    }

    val isCompact = windowClass == DesktopWindowClass.COMPACT
    val isLarge = windowClass == DesktopWindowClass.LARGE
    val showButtonLabels = windowClass == DesktopWindowClass.EXPANDED || windowClass == DesktopWindowClass.LARGE
    val lastSyncTimeText = remember(lastSyncedAtMs, settings.use24HourTime) {
        if (lastSyncedAtMs > 0L) formatRelativeConvTime(lastSyncedAtMs, settings.use24HourTime) else ""
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Two-tier Toolbar
        // Tier 1: Search bar (Ctrl+F) + Horizontally scrollable 5 Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = msgSearch,
                onValueChange = onMsgSearchChange,
                leadingIcon = {
                    Icon(DesktopIcons.Search, contentDescription = null, tint = tokens.textMuted, modifier = Modifier.size(18.dp))
                },
                placeholder = { Text(DesktopStrings.searchMessagesHint, style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(searchFocusRequester)
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    "all" to DesktopStrings.filterAll,
                    "unread" to DesktopStrings.filterUnread,
                    "starred" to DesktopStrings.filterStarred,
                    "group" to DesktopStrings.filterGroup,
                    "scam" to DesktopStrings.filterScam
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = msgFilter == key,
                        onClick = {
                            onMsgFilterChange(key)
                            if (key in setOf("unread", "starred", "scam")) {
                                scope.launch {
                                    sendOrNotify(
                                        LinkMsgType.MESSAGES_LIST,
                                        MessagesListReq(filter = key, offset = 0, limit = 60, sinceCursor = 0L)
                                    )
                                }
                            }
                        },
                        label = { Text(label, maxLines = 1) }
                    )
                }
            }
        }

        // Tier 2: Action buttons + View Mode Toggle (#531) + ⓘ markReadNote toggle + Large Info Pane toggle + Sync Status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpringOutlinedButton(
                onClick = { scope.launch { sendOrNotify(LinkMsgType.MESSAGES_MARK_READ, MarkReadReq(all = true)) } },
                animationsEnabled = settings.animationsEnabled
            ) {
                Icon(DesktopIcons.MarkRead, contentDescription = DesktopStrings.markAllRead, modifier = Modifier.size(16.dp))
                if (showButtonLabels) {
                    Spacer(Modifier.width(6.dp))
                    Text(DesktopStrings.markAllRead)
                }
            }
            SpringOutlinedButton(
                onClick = onToggleBatchSelectMode,
                animationsEnabled = settings.animationsEnabled
            ) {
                Text(if (batchSelectMode) DesktopStrings.batchDoneBtn else DesktopStrings.batchSelectBtn)
            }
            SpringOutlinedButton(
                onClick = onRefreshMessages,
                animationsEnabled = settings.animationsEnabled
            ) {
                Icon(DesktopIcons.Refresh, contentDescription = DesktopStrings.refresh, modifier = Modifier.size(16.dp))
                if (showButtonLabels) {
                    Spacer(Modifier.width(6.dp))
                    Text(DesktopStrings.refresh)
                }
            }
            // #531 View Mode toggle: Conversations (default) vs Group by App
            FilterChip(
                selected = settings.desktopMessagesViewMode == DesktopMessagesViewMode.CONVERSATION,
                onClick = {
                    DesktopSettingsStore.update { it.copy(desktopMessagesViewMode = DesktopMessagesViewMode.CONVERSATION) }
                },
                label = { Text(DesktopStrings.messagesViewConversation, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            )
            FilterChip(
                selected = settings.desktopMessagesViewMode == DesktopMessagesViewMode.GROUP_BY_APP,
                onClick = {
                    DesktopSettingsStore.update { it.copy(desktopMessagesViewMode = DesktopMessagesViewMode.GROUP_BY_APP) }
                },
                label = { Text(DesktopStrings.messagesViewByApp, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            )
            FilterChip(
                selected = showMarkReadHint,
                onClick = { showMarkReadHint = !showMarkReadHint },
                label = { Text(DesktopStrings.markReadInfoBtn, style = MaterialTheme.typography.labelSmall) }
            )
            if (isLarge) {
                FilterChip(
                    selected = showInfoPane,
                    onClick = { showInfoPane = !showInfoPane },
                    label = { Text(DesktopStrings.infoPaneToggle, style = MaterialTheme.typography.labelSmall) }
                )
            }
            Text(
                text = DesktopStrings.syncedMessagesStatus(messages.size, lastSyncTimeText),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted,
                maxLines = 1
            )
        }

        AnimatedVisibility(visible = showMarkReadHint) {
            Text(
                text = DesktopStrings.markReadNote,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(tokens.surfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // #504 Batch Multi-Select Sticky Action Bar
        AnimatedVisibility(visible = batchSelectMode && selectedBatchKeys.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth(), containerColor = tokens.surfaceElevated) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    SpringButton(
                        onClick = {
                            val targets = visibleConversations.filter { it.groupKey in selectedBatchKeys }
                            scope.launch {
                                targets.forEach { c ->
                                    sendOrNotify(
                                        LinkMsgType.MESSAGES_MARK_READ,
                                        MarkReadReq(packageName = c.packageName, conversationKey = c.conversationKey)
                                    )
                                }
                                onClearBatchAndExit()
                            }
                        },
                        animationsEnabled = settings.animationsEnabled
                    ) { Text("${DesktopStrings.batchMarkReadBtn} (${selectedBatchKeys.size})") }
                    SpringOutlinedButton(
                        onClick = {
                            val pkgs = visibleConversations
                                .filter { it.groupKey in selectedBatchKeys }
                                .map { it.packageName }
                                .distinct()
                            scope.launch {
                                pkgs.forEach { pkg ->
                                    sendOrNotify(LinkMsgType.MUTED_APPS_TOGGLE, MutedAppToggleReq(packageName = pkg, muted = true))
                                }
                                onClearBatchAndExit()
                            }
                        },
                        animationsEnabled = settings.animationsEnabled
                    ) { Text(DesktopStrings.batchMuteAppsBtn) }
                }
            }
        }

        val renderConversationRow: @Composable (ConversationSummary, Modifier) -> Unit = { conv, rowMod ->
            val isSelected = conv.groupKey == selectedConvKey
            val isBatchChecked = conv.groupKey in selectedBatchKeys
            val cardBg = if (isSelected || isBatchChecked) tokens.selectedBg else tokens.surface
            val corner = if (settings.messageCardStyle == MessageCardStyle.COMPACT_LIST) {
                (settings.messageCardCornerDp - 4).coerceAtLeast(8).dp
            } else {
                settings.messageCardCornerDp.dp
            }
            val avatarColor = DesktopAppLabels.resolveColor(conv.packageName)
            val riskDotColor = riskSemanticColor(conv.highestRisk, tokens)
            val relTime = formatRelativeConvTime(conv.latestMessage.timestamp, settings.use24HourTime)
            Row(
                modifier = rowMod
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(corner))
                    .background(cardBg)
                    .border(
                        1.dp,
                        if (isSelected || isBatchChecked) accentColor.copy(alpha = 0.5f) else tokens.border,
                        RoundedCornerShape(corner)
                    )
                    .clickable {
                        if (batchSelectMode) {
                            onSelectedBatchKeysChange(
                                if (isBatchChecked) selectedBatchKeys - conv.groupKey else selectedBatchKeys + conv.groupKey
                            )
                        } else {
                            onSelectConversation(conv.groupKey, conv.latestMessage.id)
                            if (isCompact) compactShowDetail = true
                        }
                    }
                    .padding(if (settings.messageCardStyle == MessageCardStyle.COMPACT_LIST || isCompact) 10.dp else cardPad),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (batchSelectMode) {
                    Checkbox(
                        checked = isBatchChecked,
                        onCheckedChange = { checked ->
                            onSelectedBatchKeysChange(
                                if (checked) selectedBatchKeys + conv.groupKey else selectedBatchKeys - conv.groupKey
                            )
                        }
                    )
                }
                Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(avatarColor.copy(alpha = 0.24f))
                            .border(1.dp, avatarColor.copy(alpha = 0.65f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = DesktopAppLabels.avatarInitial(conv.sender, conv.appLabel),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = avatarColor
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(riskDotColor)
                            .border(1.5.dp, tokens.surface, CircleShape)
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${if (conv.hasStarred) "★ " else ""}${conv.sender}",
                                fontWeight = if (conv.unreadCount > 0) FontWeight.Bold else FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Text(
                                text = conv.appLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = avatarColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (conv.isGroup) {
                                Text(
                                    text = DesktopStrings.groupBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.accentText,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(accentColor.copy(alpha = 0.14f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = relTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (conv.highestRisk.equals("scam", ignoreCase = true)) tokens.error else tokens.textMuted
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = conv.latestMessage.preview.take(settings.previewLength),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (conv.unreadCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(accentColor)
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (conv.unreadCount > 99) "99+" else conv.unreadCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.onAccent
                                )
                            }
                        }
                    }
                }
            }
        }

        // Master List composable block
        val renderConversationList: @Composable (Modifier) -> Unit = { listMod ->
            Box(modifier = listMod) {
                if (visibleConversations.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(DesktopStrings.emptyMessages, color = tokens.textMuted)
                    }
                } else {
                    LazyColumn(
                        state = messagesListState,
                        verticalArrangement = Arrangement.spacedBy(
                            if (settings.messageCardStyle == MessageCardStyle.COMPACT_LIST || isCompact) 4.dp else listSpace
                        )
                    ) {
                        if (settings.desktopMessagesViewMode == DesktopMessagesViewMode.GROUP_BY_APP) {
                            items(appGroups, key = { "app::${it.packageName}" }) { group ->
                                val isExpanded = group.packageName in expandedAppPackages
                                val appColor = DesktopAppLabels.resolveColor(group.packageName)
                                val riskDotColor = riskSemanticColor(group.highestRisk, tokens)
                                val relTime = formatRelativeConvTime(group.latestMessage.timestamp, settings.use24HourTime)
                                val corner = settings.messageCardCornerDp.dp
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(corner))
                                            .background(if (isExpanded) tokens.selectedBg.copy(alpha = 0.7f) else tokens.surface)
                                            .border(
                                                1.dp,
                                                if (isExpanded) accentColor.copy(alpha = 0.5f) else tokens.border,
                                                RoundedCornerShape(corner)
                                            )
                                            .clickable {
                                                expandedAppPackages = if (isExpanded) {
                                                    expandedAppPackages - group.packageName
                                                } else {
                                                    expandedAppPackages + group.packageName
                                                }
                                            }
                                            .padding(if (isCompact) 10.dp else cardPad),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(appColor.copy(alpha = 0.26f))
                                                    .border(1.2.dp, appColor.copy(alpha = 0.7f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = DesktopAppLabels.avatarInitial(group.appLabel, group.packageName),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = appColor
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(riskDotColor)
                                                    .border(1.5.dp, tokens.surface, CircleShape)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    modifier = Modifier.weight(1f),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "${if (isExpanded) "▾ " else "▸ "}${group.appLabel}",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = tokens.textPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    Text(
                                                        text = DesktopStrings.appGroupConversationsCount(group.conversations.size),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = tokens.textMuted,
                                                        maxLines = 1
                                                    )
                                                }
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = relTime,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (group.highestRisk.equals("scam", ignoreCase = true)) tokens.error else tokens.textMuted
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = group.latestMessage.preview.take(settings.previewLength),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = tokens.textSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (group.unreadCount > 0) {
                                                    Spacer(Modifier.width(8.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(CircleShape)
                                                            .background(accentColor)
                                                            .padding(horizontal = 7.dp, vertical = 2.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = if (group.unreadCount > 99) "99+" else group.unreadCount.toString(),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = tokens.onAccent
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(
                                            modifier = Modifier.padding(start = 16.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            group.conversations.forEach { conv ->
                                                renderConversationRow(conv, Modifier)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            items(visibleConversations, key = { it.groupKey }) { conv ->
                                renderConversationRow(conv, Modifier)
                            }
                        }

                        // #531 Pagination footer (loading / honest error + manual retry / end)
                        item(key = "messages_pagination_footer") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    isLoadingMore -> {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = accentColor
                                            )
                                            Text(
                                                DesktopStrings.loadingMoreMessages,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = tokens.textMuted
                                            )
                                        }
                                    }
                                    loadMoreError != null -> {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                loadMoreError,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = tokens.error
                                            )
                                            TextButton(onClick = onLoadMoreMessages) {
                                                Text(DesktopStrings.retryLoadMore, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                    !hasMoreMessages && messages.isNotEmpty() -> {
                                        Text(
                                            DesktopStrings.allMessagesLoaded,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tokens.textMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Detail Pane composable block
        val renderDetailPane: @Composable (Modifier) -> Unit = { detailMod ->
            GlassCard(modifier = detailMod) {
                val detail = selectedMsg
                val conv = selectedConv
                if (detail == null) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (isCompact) {
                                TextButton(onClick = { compactShowDetail = false }) {
                                    Text(DesktopStrings.backToList, color = tokens.accentText)
                                }
                            }
                            Text(DesktopStrings.selectMessageHint, color = tokens.textMuted)
                        }
                    }
                } else {
                    val resolvedApp = conv?.appLabel ?: DesktopAppLabels.resolveLabel(detail.packageName, detail.appLabel)
                    val appColor = DesktopAppLabels.resolveColor(detail.packageName)
                    Column(
                        modifier = Modifier.fillMaxSize().padding(if (isCompact) 12.dp else 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isCompact) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { compactShowDetail = false }) {
                                    Text(DesktopStrings.backToList, color = tokens.accentText)
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(appColor.copy(alpha = 0.24f))
                                        .border(1.dp, appColor.copy(alpha = 0.65f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = DesktopAppLabels.avatarInitial(detail.sender, resolvedApp),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = appColor
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        resolveMessageTitle(detail.sender, detail.packageName, resolvedApp),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = tokens.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text("$resolvedApp · ${timeFmt.format(Date(detail.timestamp))}", style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                                }
                            }
                            Text(
                                text = DesktopStrings.riskLabelText(detail.riskLabel),
                                style = MaterialTheme.typography.labelMedium,
                                color = riskSemanticColor(detail.riskLabel, tokens)
                            )
                        }

                        // Action Row (horizontally scrollable for compact widths)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if ((conv?.unreadCount ?: 0) > 0 && detail.conversationKey.isNotBlank()) {
                                TextButton(onClick = {
                                    scope.launch {
                                        sendOrNotify(
                                            LinkMsgType.MESSAGES_MARK_READ,
                                            MarkReadReq(packageName = detail.packageName, conversationKey = detail.conversationKey)
                                        )
                                    }
                                }) { Text(DesktopStrings.conversationMarkRead) }
                            } else if (!detail.isRead) {
                                TextButton(onClick = { scope.launch { sendOrNotify(LinkMsgType.MESSAGES_MARK_READ, MarkReadReq(ids = listOf(detail.id))) } }) {
                                    Text(DesktopStrings.markRead)
                                }
                            }
                            TextButton(onClick = {
                                scope.launch { sendOrNotify(LinkMsgType.MESSAGES_STAR_TOGGLE, StarToggleReq(id = detail.id, starred = !detail.isStarred)) }
                            }) {
                                Text(
                                    if (detail.isStarred) "★ ${DesktopStrings.unstar}" else "☆ ${DesktopStrings.star}",
                                    color = if (detail.isStarred) tokens.warning else tokens.accentText
                                )
                            }
                            TextButton(
                                enabled = deepAnalyzingMsgId != detail.id,
                                onClick = {
                                    scope.launch {
                                        if (sendOrNotify(LinkMsgType.MESSAGE_DEEP_ANALYZE, DeepAnalyzeReq(id = detail.id))) {
                                            onSetDeepAnalyzingMsgId(detail.id)
                                        }
                                    }
                                }
                            ) { Text(if (deepAnalyzingMsgId == detail.id) DesktopStrings.deepAnalyzing else DesktopStrings.deepAnalyze) }
                            TextButton(onClick = {
                                val titleForNote = resolveMessageTitle(detail.sender, detail.packageName, resolvedApp)
                                scope.launch { sendOrNotify(LinkMsgType.NOTE_ADD, NoteAddReq(content = "[$titleForNote] ${detail.preview}", relatedMessageId = detail.id)) }
                            }) { Text(DesktopStrings.addNote) }
                        }

                        // Tags row
                        if (tags.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                tags.forEach { tg ->
                                    val applied = tg.id in detail.tagIds
                                    FilterChip(
                                        selected = applied,
                                        onClick = {
                                            scope.launch {
                                                val cmd = if (applied) LinkMsgType.MESSAGES_TAG_REMOVE else LinkMsgType.MESSAGES_TAG_APPLY
                                                sendOrNotify(cmd, MessageTagReq(messageId = detail.id, tagId = tg.id))
                                            }
                                        },
                                        label = { Text(tg.name) }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = tokens.border)
                        Text(DesktopStrings.conversationTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)

                        val threadItems = remember(conversationThread, messages, conv?.groupKey) {
                            val raw = conversationThread?.items?.takeIf { it.isNotEmpty() }
                                ?: if (conv != null) messages.filter { conversationGroupKey(it) == conv.groupKey } else emptyList()
                            raw.sortedBy { it.timestamp }
                        }
                        val firstUnreadIndex = remember(threadItems) { threadItems.indexOfFirst { !it.isRead } }
                        val showScrollToLatest by remember(threadItems.size) {
                            derivedStateOf {
                                val lastVisible = threadListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                threadItems.size > 4 && lastVisible < threadItems.lastIndex - 1
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (threadItems.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(DesktopStrings.conversationEmpty, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                                }
                            } else {
                                LazyColumn(
                                    state = threadListState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(threadItems, key = { _, it -> it.id }) { idx, item ->
                                        val curDay = MSG_DAY_FMT.get().format(Date(item.timestamp))
                                        val prevDay = if (idx > 0) MSG_DAY_FMT.get().format(Date(threadItems[idx - 1].timestamp)) else null
                                        if (idx == 0 || curDay != prevDay) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                HorizontalDivider(modifier = Modifier.weight(1f), color = tokens.border)
                                                Text(curDay, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                                                HorizontalDivider(modifier = Modifier.weight(1f), color = tokens.border)
                                            }
                                        }
                                        if (idx == firstUnreadIndex && firstUnreadIndex > 0) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                HorizontalDivider(modifier = Modifier.weight(1f), color = accentColor.copy(alpha = 0.5f))
                                                Text(
                                                    "── ${DesktopStrings.newMessagesDivider} ──",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = tokens.accentText,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                HorizontalDivider(modifier = Modifier.weight(1f), color = accentColor.copy(alpha = 0.5f))
                                            }
                                        }

                                        val itemSelected = item.id == detail.id
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (itemSelected) tokens.selectedBg else tokens.surfaceElevated.copy(alpha = 0.65f))
                                                .border(
                                                    width = 1.dp,
                                                    color = if (itemSelected) accentColor.copy(alpha = 0.45f) else Color.Transparent,
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .clickable { onSelectMessageId(item.id) }
                                                .padding(10.dp)
                                        ) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                val itemTitle = resolveMessageTitle(item.sender, item.packageName, item.appLabel.ifBlank { resolvedApp })
                                                Text(
                                                    text = "${if (item.isStarred) "★ " else ""}$itemTitle",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = tokens.accentText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = "${DesktopStrings.riskLabelText(item.riskLabel)} · ${formatRelativeConvTime(item.timestamp, settings.use24HourTime)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = when (item.riskLabel.lowercase()) {
                                                        "scam" -> tokens.error
                                                        "suspicious", "warning" -> tokens.warning
                                                        else -> tokens.textMuted
                                                    }
                                                )
                                            }
                                            Spacer(Modifier.height(3.dp))
                                            Text(item.preview.take(settings.previewLength), style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
                                        }
                                    }
                                }
                            }

                            // Floating Scroll-to-Latest Pill
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showScrollToLatest,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = accentColor,
                                    shadowElevation = if (tokens.isHighContrast) 0.dp else 4.dp,
                                    modifier = Modifier.clickable {
                                        if (threadItems.isNotEmpty()) {
                                            scope.launch { threadListState.animateScrollToItem(threadItems.lastIndex) }
                                        }
                                    }
                                ) {
                                    Text(
                                        text = DesktopStrings.scrollToLatest,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.onAccent,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        // Quick Reply Bar
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = replyText,
                                onValueChange = onReplyTextChange,
                                label = { Text(DesktopStrings.replyPlaceholder) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            SpringButton(
                                enabled = replyText.isNotBlank(),
                                animationsEnabled = settings.animationsEnabled,
                                onClick = {
                                    val txt = replyText.trim()
                                    if (txt.isNotBlank()) {
                                        scope.launch {
                                            if (sendOrNotify(
                                                    LinkMsgType.QUICK_REPLY_SEND,
                                                    QuickReplySendReq(
                                                        packageName = detail.packageName,
                                                        sender = detail.sender,
                                                        conversationKey = detail.conversationKey,
                                                        text = txt
                                                    )
                                                )
                                            ) {
                                                onReplyTextChange("")
                                            }
                                        }
                                    }
                                }
                            ) { Text(DesktopStrings.sendReply) }
                        }
                    }
                }
            }
        }

        // Right Info Pane (LARGE 3rd pane: Source App, Conversation Stats, Quick Note)
        val renderInfoPane: @Composable (Modifier) -> Unit = { infoMod ->
            GlassCard(modifier = infoMod) {
                val conv = selectedConv
                val detail = selectedMsg
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(DesktopStrings.infoPaneTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                    HorizontalDivider(color = tokens.border)
                    if (conv == null || detail == null) {
                        Text(DesktopStrings.selectMessageHint, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                    } else {
                        val appColor = DesktopAppLabels.resolveColor(conv.packageName)
                        Text(DesktopStrings.infoPaneSourceApp, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(appColor.copy(alpha = 0.22f))
                                    .border(1.dp, appColor.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    DesktopAppLabels.avatarInitial(conv.sender, conv.appLabel),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = appColor
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(conv.appLabel, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary, fontWeight = FontWeight.SemiBold)
                                Text(conv.packageName, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        HorizontalDivider(color = tokens.border)
                        val convMessages = remember(messages, conv.groupKey) { messages.filter { conversationGroupKey(it) == conv.groupKey } }
                        val starredCount = remember(convMessages) { convMessages.count { it.isStarred } }
                        InfoStatRow(DesktopStrings.infoPaneMsgCount, conv.messageCount.toString(), tokens.textPrimary)
                        InfoStatRow(DesktopStrings.infoPaneUnreadCount, conv.unreadCount.toString(), if (conv.unreadCount > 0) tokens.accentText else tokens.textSecondary)
                        InfoStatRow(DesktopStrings.infoPaneStarredCount, starredCount.toString(), if (starredCount > 0) tokens.warning else tokens.textSecondary)
                        InfoStatRow(DesktopStrings.infoPaneHighestRisk, DesktopStrings.riskLabelText(conv.highestRisk), riskSemanticColor(conv.highestRisk, tokens))

                        HorizontalDivider(color = tokens.border)
                        Text(DesktopStrings.addNote, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                        OutlinedTextField(
                            value = quickInfoNote,
                            onValueChange = { quickInfoNote = it },
                            placeholder = { Text(DesktopStrings.infoPaneQuickNoteHint, style = MaterialTheme.typography.bodySmall) },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        SpringButton(
                            enabled = quickInfoNote.isNotBlank(),
                            animationsEnabled = settings.animationsEnabled,
                            containerColor = accentColor,
                            onClick = {
                                val noteTxt = quickInfoNote.trim()
                                if (noteTxt.isNotBlank()) {
                                    scope.launch {
                                        if (sendOrNotify(LinkMsgType.NOTE_ADD, NoteAddReq(content = "[${conv.sender}] $noteTxt", relatedMessageId = detail.id))) {
                                            quickInfoNote = ""
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(DesktopStrings.addNote) }
                    }
                }
            }
        }

        // Responsive 1 / 2 / 3-Pane Layout
        when (windowClass) {
            DesktopWindowClass.COMPACT -> {
                if (compactShowDetail && selectedMsg != null) {
                    renderDetailPane(Modifier.weight(1f).fillMaxWidth())
                } else {
                    renderConversationList(Modifier.weight(1f).fillMaxWidth())
                }
            }
            DesktopWindowClass.MEDIUM -> {
                Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    renderConversationList(Modifier.width(320.dp).fillMaxHeight())
                    renderDetailPane(Modifier.weight(1f).fillMaxHeight())
                }
            }
            DesktopWindowClass.EXPANDED -> {
                Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    renderConversationList(Modifier.width(370.dp).fillMaxHeight())
                    renderDetailPane(Modifier.weight(1f).fillMaxHeight())
                }
            }
            DesktopWindowClass.LARGE -> {
                Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    renderConversationList(Modifier.width(370.dp).fillMaxHeight())
                    renderDetailPane(Modifier.weight(1f).fillMaxHeight())
                    if (showInfoPane) {
                        renderInfoPane(Modifier.width(300.dp).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoStatRow(label: String, value: String, valueColor: Color) {
    val tokens = LocalDesktopTokens.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}
