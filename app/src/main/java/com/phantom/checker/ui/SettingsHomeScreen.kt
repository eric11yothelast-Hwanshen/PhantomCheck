package com.phantom.checker.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phantom.checker.network.AiClient
import com.phantom.checker.ui.components.AuroraCenteredContent
import com.phantom.checker.ui.components.AuroraTokens
import com.phantom.checker.ui.components.FluidSwitch
import com.phantom.checker.ui.components.PhantomMotion
import com.phantom.checker.ui.components.PhantomPageScaffold
import com.phantom.checker.ui.components.PhantomTopBarStyle
import com.phantom.checker.ui.components.SettingsNavigationRow
import com.phantom.checker.ui.components.SettingsSectionHeader
import com.phantom.checker.ui.components.SettingsSegmentedGroup
import com.phantom.checker.ui.components.auroraHorizontalPadding
import com.phantom.checker.ui.components.phantomPopIn
import com.phantom.checker.util.PhantomSettingsBus
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SettingsHomeSummaries(
    val quickSummary: String,
    val aiSummary: String,
    val notifSummary: String,
    val assistantSummary: String,
    val permissionsSummary: String,
    val permissionsBadge: String? = null,
    val appearanceSummary: String,
    val dataSummary: String,
    val aboutSummary: String,
    val versionDisplay: String,
)

/**
 * §4.6 設定中心首頁（Aurora II）：
 * - Large 收合應用列（N4）
 * - 搜尋列（高 56dp，圓角 28dp，即時本地 searchByKeywords ＋ 右側 ✨ AI 語意搜尋）
 * - 釘選區（≤6，2 欄磁磚，開關型直接內嵌 FluidSwitch，可編輯）
 * - 最近使用（≤5 水平 chips，可清除）
 * - 8 大分類分段清單（36dp 圓形圖示＋標題＋一行狀態摘要＋chevron）
 * - 搜尋結果態：整頁切換為結果清單（分類麵包屑＋命中項目＋點入直達）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun SettingsHomeScreen(
    onBack: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isAiSearching: Boolean,
    aiMatchedIds: List<String>,
    aiSearchNote: String?,
    onRunAiSearch: () -> Unit,
    onClearAiSearch: () -> Unit,
    summaries: SettingsHomeSummaries,
    snackbarHostState: SnackbarHostState,
    onOpenCategory: (SettingsCategory) -> Unit,
    onSelectSettingItem: (SettingSearchItem) -> Unit,
    onReplayTour: () -> Unit,
    onReportTourBounds: (String, Rect) -> Unit = { _, _ -> },
    onShowMessage: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val resCtx = ctx
    val appCtx = ctx.applicationContext
    val horizPad = auroraHorizontalPadding()
    val layoutVer = UiLayoutPrefs.version
    val settingsBusVer by PhantomSettingsBus.version.collectAsState()

    val pinnedIds = remember(layoutVer) { UiLayoutPrefs.getPinnedIds(appCtx) }
    val pinnedItems = remember(pinnedIds) { pinnedIds.mapNotNull { SettingsCatalog.findById(it) } }
    val recentIds = remember(layoutVer) { UiLayoutPrefs.getRecentSettingIds(appCtx) }
    val recentItems = remember(recentIds) { recentIds.mapNotNull { SettingsCatalog.findById(it) } }

    var showPinEditorSheet by remember { mutableStateOf(false) }

    val trimmedQuery = searchQuery.trim()
    val localMatchedItems = remember(trimmedQuery) {
        if (trimmedQuery.isEmpty()) emptyList() else SettingsCatalog.searchByKeywords(trimmedQuery)
    }
    val combinedMatches = remember(localMatchedItems, aiMatchedIds) {
        if (aiMatchedIds.isEmpty()) {
            localMatchedItems
        } else {
            val aiItems = aiMatchedIds.mapNotNull { SettingsCatalog.findById(it) }
            val aiSet = aiItems.map { it.id }.toSet()
            aiItems + localMatchedItems.filterNot { it.id in aiSet }
        }
    }
    var isAiConfigured by remember { mutableStateOf(false) }
    LaunchedEffect(settingsBusVer) {
        isAiConfigured = withContext(Dispatchers.IO) {
            AiClient.getActiveProfile(appCtx) != null
        }
    }

    PhantomPageScaffold(
        title = stringResource(R.string.common_settings),
        onBack = onBack,
        topBarStyle = PhantomTopBarStyle.Large,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        AuroraCenteredContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = horizPad, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ── 搜尋列（56dp、圓角 28dp） ──
                item(key = "settings_search_bar") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { onReportTourBounds("search", it.boundsInRoot()) },
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                                unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.settings_home_s_5d457a),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.settings_home_s_677b49))
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = {
                                            onSearchQueryChange("")
                                            onClearAiSearch()
                                        }) {
                                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.common_s_59b34b))
                                        }
                                    }
                                    IconButton(
                                        onClick = onRunAiSearch,
                                        enabled = trimmedQuery.isNotEmpty() && !isAiSearching && isAiConfigured,
                                    ) {
                                        if (isAiSearching) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            Icon(
                                                imageVector = AutoAwesomeIcon,
                                                contentDescription = if (isAiConfigured) {
                                                    stringResource(R.string.settings_home_s_86436a)
                                                } else {
                                                    stringResource(R.string.settings_home_s_a97d90)
                                                },
                                                tint = if (trimmedQuery.isNotEmpty() && isAiConfigured) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                                },
                                            )
                                        }
                                    }
                                }
                            },
                        )
                        if (!isAiConfigured && trimmedQuery.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_home_s_9f991e),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                        if (!aiSearchNote.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = aiSearchNote,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = onClearAiSearch) {
                                    Text(stringResource(R.string.common_clear))
                                }
                            }
                        }
                    }
                }

                if (trimmedQuery.isNotEmpty()) {
                    // ── 搜尋結果態 ──
                    if (combinedMatches.isEmpty()) {
                        item(key = "settings_search_empty") {
                            EmptyStateView(
                                icon = Icons.Default.Search,
                                title = stringResource(R.string.search_s_4de5af),
                                description = if (isAiConfigured) {
                                    stringResource(R.string.settings_home_s_284fe0, trimmedQuery)
                                } else {
                                    stringResource(R.string.settings_home_s_a005c6, trimmedQuery)
                                },
                                actionLabel = if (isAiConfigured) stringResource(R.string.settings_home_s_5db5f0) else null,
                                onAction = if (isAiConfigured) onRunAiSearch else null,
                            )
                        }
                    } else {
                        item(key = "settings_search_count") {
                            Text(
                                text = stringResource(R.string.settings_home_s_de4d96, combinedMatches.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                        items(combinedMatches, key = { "sr_${it.id}" }) { item ->
                            SettingsSearchResultRow(
                                item = item,
                                settingsBusVer = settingsBusVer,
                                onClick = {
                                    UiLayoutPrefs.recordRecentSetting(appCtx, item.id)
                                    onSelectSettingItem(item)
                                },
                            )
                        }
                    }
                } else {
                    // ── 釘選區（≤6，2 欄磁磚） ──
                    item(key = "settings_pinned_section") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { onReportTourBounds("pinned", it.boundsInRoot()) },
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SettingsSectionHeader(title = stringResource(R.string.search_s_71bbd8))
                                TextButton(onClick = { showPinEditorSheet = true }) {
                                    Text(stringResource(R.string.common_edit))
                                }
                            }
                            if (pinnedItems.isEmpty()) {
                                Surface(
                                    onClick = { showPinEditorSheet = true },
                                    shape = RoundedCornerShape(AuroraTokens.CornerCard),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_home_s_16fe9f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pinnedItems.chunked(2).forEach { rowItems ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            rowItems.forEach { item ->
                                                PinnedSettingTile(
                                                    item = item,
                                                    settingsBusVer = settingsBusVer,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .phantomPopIn(initialScale = PhantomMotion.PinPopInInitialScale),
                                                    onClick = {
                                                        UiLayoutPrefs.recordRecentSetting(appCtx, item.id)
                                                        onSelectSettingItem(item)
                                                    },
                                                )
                                            }
                                            if (rowItems.size == 1) {
                                                Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 最近使用（≤5 水平 chips） ──
                    if (recentItems.isNotEmpty()) {
                        item(key = "settings_recent_section") {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SettingsSectionHeader(title = stringResource(R.string.settings_home_s_71265f))
                                    TextButton(onClick = { UiLayoutPrefs.clearRecentSettings(appCtx) }) {
                                        Text(stringResource(R.string.common_clear))
                                    }
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(recentItems, key = { "rec_${it.id}" }) { item ->
                                        AssistChip(
                                            onClick = {
                                                UiLayoutPrefs.recordRecentSetting(appCtx, item.id)
                                                onSelectSettingItem(item)
                                            },
                                            label = {
                                                Text(
                                                    text = item.title,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── 主要設定分類（常用一鍵可及 #501） ──
                    item(key = "settings_categories_section") {
                        var advancedExpanded by remember {
                            mutableStateOf(com.phantom.checker.util.PhantomPrefs.isSettingsAdvancedExpanded(appCtx))
                        }
                        val primaryCategories = remember {
                            listOf(
                                SettingsCategory.QUICK,
                                SettingsCategory.AI,
                                SettingsCategory.NOTIF,
                                SettingsCategory.ASSISTANT,
                                SettingsCategory.APPEARANCE
                            )
                        }
                        val advancedCategories = remember {
                            listOf(
                                SettingsCategory.PERMISSIONS,
                                SettingsCategory.DATA,
                                SettingsCategory.ABOUT
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { onReportTourBounds("categories", it.boundsInRoot()) },
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                SettingsSectionHeader(title = stringResource(R.string.settings_home_s_d72af3))
                                SettingsSegmentedGroup {
                                    primaryCategories.forEachIndexed { idx, category ->
                                        val (summary, badge) = when (category) {
                                            SettingsCategory.QUICK -> summaries.quickSummary to null
                                            SettingsCategory.AI -> summaries.aiSummary to null
                                            SettingsCategory.NOTIF -> summaries.notifSummary to null
                                            SettingsCategory.ASSISTANT -> summaries.assistantSummary to null
                                            SettingsCategory.APPEARANCE -> summaries.appearanceSummary to null
                                            else -> "" to null
                                        }
                                        SettingsNavigationRow(
                                            title = category.title,
                                            subtitle = summary,
                                            icon = categoryIcon(category),
                                            badgeText = badge,
                                            shape = AuroraTokens.segmentedItemShape(idx, primaryCategories.size),
                                            onClick = { onOpenCategory(category) },
                                        )
                                    }
                                }
                            }

                            // #501 低頻設定收納為預設收合之「進階與系統」群組（搜尋仍可一鍵命中）
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = phantomSurfaceAlpha(0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                advancedExpanded = !advancedExpanded
                                                com.phantom.checker.util.PhantomPrefs.setSettingsAdvancedExpanded(appCtx, advancedExpanded)
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.settings_advanced_group_title),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = stringResource(R.string.settings_advanced_group_desc),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        com.phantom.checker.ui.components.PhantomChevron(expanded = advancedExpanded)
                                    }
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = advancedExpanded,
                                        enter = PhantomMotion.ExpandEnter,
                                        exit = PhantomMotion.ExpandExit
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                                            SettingsSegmentedGroup {
                                                advancedCategories.forEachIndexed { idx, category ->
                                                    val (summary, badge) = when (category) {
                                                        SettingsCategory.PERMISSIONS -> summaries.permissionsSummary to summaries.permissionsBadge
                                                        SettingsCategory.DATA -> summaries.dataSummary to null
                                                        SettingsCategory.ABOUT -> summaries.aboutSummary to null
                                                        else -> "" to null
                                                    }
                                                    SettingsNavigationRow(
                                                        title = category.title,
                                                        subtitle = summary,
                                                        icon = categoryIcon(category),
                                                        badgeText = badge,
                                                        shape = AuroraTokens.segmentedItemShape(idx, advancedCategories.size),
                                                        onClick = { onOpenCategory(category) },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 尾段：版本與重播教學 ──
                    item(key = "settings_footer_section") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .onGloballyPositioned { onReportTourBounds("replay", it.boundsInRoot()) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.settings_home_s_8527b3, summaries.versionDisplay),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onReplayTour) {
                                Text(stringResource(R.string.settings_home_s_f7754d))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPinEditorSheet) {
        PinEditorBottomSheet(
            pinnedIds = pinnedIds,
            onDismiss = { showPinEditorSheet = false },
            onTogglePin = { id ->
                if (id in pinnedIds) {
                    UiLayoutPrefs.removePinned(appCtx, id)
                } else {
                    val ok = UiLayoutPrefs.addPinned(appCtx, id)
                    if (!ok) {
                        onShowMessage(resCtx.getString(R.string.settings_home_s_73c93d, UiLayoutPrefs.MAX_PINNED))
                    }
                }
            },
            onResetDefaults = {
                UiLayoutPrefs.resetPinnedToDefaults(appCtx)
                onShowMessage(resCtx.getString(R.string.settings_home_s_69894d))
            },
        )
    }
}

private fun categoryIcon(category: SettingsCategory): ImageVector = when (category) {
    SettingsCategory.QUICK -> Icons.Default.Star
    SettingsCategory.AI -> AutoAwesomeIcon
    SettingsCategory.NOTIF -> Icons.Default.Notifications
    SettingsCategory.ASSISTANT -> Icons.Default.Person
    SettingsCategory.PERMISSIONS -> Icons.Default.Lock
    SettingsCategory.APPEARANCE -> Icons.Default.Edit
    SettingsCategory.DATA -> Icons.Default.Build
    SettingsCategory.ABOUT -> Icons.Default.Info
}

@Composable
private fun PinnedSettingTile(
    item: SettingSearchItem,
    settingsBusVer: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    val resCtx = ctx
    val appCtx = ctx.applicationContext
    val isToggle = remember(item.id) { SettingsCatalog.isToggleable(item.id) }
    var checked by remember(item.id, settingsBusVer) {
        mutableStateOf(if (isToggle) SettingsCatalog.getBooleanState(appCtx, item.id) == true else false)
    }
    Surface(
        onClick = {
            if (isToggle) {
                val next = !checked
                checked = next
                SettingsCatalog.applyBooleanSetting(appCtx, item.id, next)
                UiLayoutPrefs.recordRecentSetting(appCtx, item.id)
            } else {
                onClick()
            }
        },
        shape = RoundedCornerShape(AuroraTokens.CornerCard),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .heightIn(min = 84.dp)
            .semantics {
                contentDescription = if (isToggle) {
                    resCtx.getString(R.string.settings_home_s_170746, item.title, if (checked) resCtx.getString(R.string.settings_home_s_a36463) else resCtx.getString(R.string.chat_s_e8a92f))
                } else {
                    resCtx.getString(R.string.settings_home_s_66babc, item.title)
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isToggle) {
                        if (checked) stringResource(R.string.settings_home_s_a36463) else stringResource(R.string.chat_s_e8a92f)
                    } else {
                        stringResource(R.string.settings_home_s_d3995e)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToggle && checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isToggle) {
                    FluidSwitch(
                        checked = checked,
                        onCheckedChange = { next ->
                            checked = next
                            SettingsCatalog.applyBooleanSetting(appCtx, item.id, next)
                            UiLayoutPrefs.recordRecentSetting(appCtx, item.id)
                        },
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSearchResultRow(
    item: SettingSearchItem,
    settingsBusVer: Long,
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    val resCtx = ctx
    val appCtx = ctx.applicationContext
    val category = remember(item.id) { SettingsCategoryRegistry.categoryFor(item.id) }
    val isFolded = remember(item.id) { SettingsCategoryRegistry.isFoldedByDefault(item.id) }
    val isToggle = remember(item.id) { SettingsCatalog.isToggleable(item.id) }
    var checked by remember(item.id, settingsBusVer) {
        mutableStateOf(if (isToggle) SettingsCatalog.getBooleanState(appCtx, item.id) == true else false)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(AuroraTokens.CornerCard),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = resCtx.getString(R.string.search_s_e60c1c, item.title, category?.title ?: resCtx.getString(R.string.common_settings))
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AuroraTokens.MinRowHeight)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = buildString {
                        append(category?.title ?: resCtx.getString(R.string.common_settings))
                        if (isFolded) append(resCtx.getString(R.string.settings_home_s_0bafaf))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (item.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isToggle) {
                Spacer(Modifier.width(12.dp))
                FluidSwitch(
                    checked = checked,
                    onCheckedChange = { next ->
                        checked = next
                        SettingsCatalog.applyBooleanSetting(appCtx, item.id, next)
                        UiLayoutPrefs.recordRecentSetting(appCtx, item.id)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinEditorBottomSheet(
    pinnedIds: List<String>,
    onDismiss: () -> Unit,
    onTogglePin: (String) -> Unit,
    onResetDefaults: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filterQuery by remember { mutableStateOf("") }
    val filteredItems = remember(filterQuery) {
        val q = filterQuery.trim()
        if (q.isEmpty()) SettingsCatalog.ITEMS else SettingsCatalog.searchByKeywords(q)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_home_s_bc9899, pinnedIds.size, UiLayoutPrefs.MAX_PINNED),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onResetDefaults) {
                    Text(stringResource(R.string.settings_home_s_3cbc2a))
                }
            }
            OutlinedTextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text(stringResource(R.string.settings_home_s_428fe3)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filteredItems, key = { "pe_${it.id}" }) { item ->
                    val isPinned = item.id in pinnedIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTogglePin(item.id) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = isPinned,
                            onCheckedChange = { onTogglePin(item.id) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = SettingsCategoryRegistry.categoryFor(item.id)?.title ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
