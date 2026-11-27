package com.phantom.checker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phantom.checker.data.MemoryEntity
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.data.NoteEntity
import com.phantom.checker.network.AiClient
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.ui.components.AuroraTokens
import com.phantom.checker.ui.components.FluidSwitch
import com.phantom.checker.ui.home.SearchRiskFilter
import com.phantom.checker.util.PhantomSettingsBus
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** §6.2 全域搜尋範圍 */
enum class GlobalSearchScope(@StringRes val labelRes: Int) {
    ALL(R.string.common_all),
    MESSAGES(R.string.common_messages),
    SETTINGS(R.string.common_settings),
    MEMORY(R.string.common_memory),
    NOTES(R.string.common_notes),
}

/**
 * §6.2 全域統一搜尋覆蓋層：
 * - 範圍 chips：全部／訊息／設定／記憶／記事
 * - 無輸入時：最近搜尋（≤8，可逐條刪與全部清除）＋常用動作＋釘選設定（開關型直接內嵌 FluidSwitch）
 * - 有輸入時：分區結果（每區最多 5 筆＋「查看全部」）：設定（本地 searchByKeywords 100% 離線可用）、訊息、記憶（敏感記憶不顯示內容）、記事
 * - AI 語意找設定（#405 鏈保留）：無 API Key 時灰顯並說明原因，絕不阻擋本地結果；查無結果誠實顯示
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhantomGlobalSearchOverlay(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    initialScope: GlobalSearchScope,
    glassState: HazeState,
    messageResults: List<MessageEntity>,
    searchRiskFilter: SearchRiskFilter,
    onSearchRiskFilterChange: (SearchRiskFilter) -> Unit,
    activeFilterKeywords: List<String>,
    isLoadingMessages: Boolean,
    onOpenMessageDetail: (Long) -> Unit,
    onOpenSettings: (settingId: String?, legacyCardKey: String?) -> Unit,
    onOpenPermissionHub: () -> Unit,
    onReplayTour: () -> Unit,
    onOpenMemoryTab: () -> Unit,
    onOpenNotesTab: () -> Unit,
    onAiMessageSearch: (String) -> Unit,
    onShowSnackbar: (String) -> Unit,
) {
    val resCtx = LocalContext.current
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()

    var selectedScope by remember { mutableStateOf(initialScope) }
    var expandAllSettings by remember { mutableStateOf(false) }
    var expandAllMessages by remember { mutableStateOf(false) }
    var expandAllMemories by remember { mutableStateOf(false) }
    var expandAllNotes by remember { mutableStateOf(false) }

    var memoryMatches by remember { mutableStateOf<List<MemoryEntity>>(emptyList()) }
    var noteMatches by remember { mutableStateOf<List<NoteEntity>>(emptyList()) }

    var aiSettingsMatchedIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var aiSettingsNote by remember { mutableStateOf<String?>(null) }
    var isAiSearchingSettings by remember { mutableStateOf(false) }

    val layoutPrefsVersion = UiLayoutPrefs.version
    val settingsBusVer by PhantomSettingsBus.version.collectAsState()

    val recentSearches = remember(layoutPrefsVersion, visible) {
        UiLayoutPrefs.getRecentSearches(appCtx)
    }
    val pinnedItems = remember(layoutPrefsVersion, visible) {
        UiLayoutPrefs.getPinnedIds(appCtx).mapNotNull { SettingsCatalog.findById(it) }
    }

    LaunchedEffect(visible, initialScope) {
        if (visible) {
            selectedScope = initialScope
            expandAllSettings = false
            expandAllMessages = false
            expandAllMemories = false
            expandAllNotes = false
            aiSettingsMatchedIds = emptyList()
            aiSettingsNote = null
        }
    }

    val trimmedQuery = query.trim().take(120)

    val localSettingMatches = remember(trimmedQuery) {
        if (trimmedQuery.isEmpty()) emptyList() else SettingsCatalog.searchByKeywords(trimmedQuery)
    }

    LaunchedEffect(trimmedQuery, visible, selectedScope) {
        aiSettingsMatchedIds = emptyList()
        aiSettingsNote = null
        if (!visible || trimmedQuery.isEmpty()) {
            memoryMatches = emptyList()
            noteMatches = emptyList()
            return@LaunchedEffect
        }
        delay(200L)
        val qLower = trimmedQuery.lowercase()
        val (nextMemMatches, nextNoteMatches) = withContext(Dispatchers.IO) {
            val db = AppDatabaseHolder.db(appCtx)
            val mems = if (selectedScope == GlobalSearchScope.ALL || selectedScope == GlobalSearchScope.MEMORY) {
                db.memoryDao().search(trimmedQuery).filter { mem ->
                    val isSensitive = mem.privacyTier != MemoryEntity.PRIVACY_NORMAL
                    !isSensitive ||
                        mem.subject.lowercase().contains(qLower) ||
                        mem.category.lowercase().contains(qLower)
                }
            } else {
                emptyList()
            }
            val notes = if (selectedScope == GlobalSearchScope.ALL || selectedScope == GlobalSearchScope.NOTES) {
                db.noteDao().topRecent(500).filter { note ->
                    note.content.lowercase().contains(qLower) ||
                        (note.relatedAppLabel?.lowercase()?.contains(qLower) == true)
                }
            } else {
                emptyList()
            }
            mems to notes
        }
        memoryMatches = nextMemMatches
        noteMatches = nextNoteMatches
    }

    val isAiConfigured = remember(visible, settingsBusVer) {
        AiClient.getProfileForPurpose(appCtx, com.phantom.checker.util.AiPurpose.SETTINGS_SEARCH) != null
    }

    fun runAiSettingsSearch() {
        if (trimmedQuery.isEmpty() || isAiSearchingSettings || !isAiConfigured) return
        UiLayoutPrefs.recordRecentSearch(appCtx, trimmedQuery)
        scope.launch {
            isAiSearchingSettings = true
            aiSettingsNote = null
            val catalogText = SettingsCatalog.buildAiSearchCatalogText()
            val allowedIds = SettingsCatalog.ITEMS.map { it.id }.toSet()
            val result = AiClient.searchSettings(
                query = trimmedQuery,
                catalogText = catalogText,
                allowedIds = allowedIds,
                context = appCtx,
            )
            isAiSearchingSettings = false
            result.onSuccess { ids ->
                val valid = ids.filter { SettingsCatalog.findById(it) != null }
                aiSettingsMatchedIds = valid
                if (valid.isEmpty()) {
                    aiSettingsNote = resCtx.getString(R.string.search_s_4de5af)
                    onShowSnackbar(resCtx.getString(R.string.search_s_4de5af))
                } else {
                    aiSettingsNote = resCtx.getString(R.string.search_s_9e3e7b, valid.size)
                }
            }.onFailure { err ->
                aiSettingsNote = err.message ?: resCtx.getString(R.string.search_s_8b9a4e)
            }
        }
    }

    val combinedSettingResults = remember(localSettingMatches, aiSettingsMatchedIds) {
        if (aiSettingsMatchedIds.isEmpty()) {
            localSettingMatches
        } else {
            val aiItems = aiSettingsMatchedIds.mapNotNull { SettingsCatalog.findById(it) }
            val aiIdSet = aiItems.map { it.id }.toSet()
            aiItems + localSettingMatches.filterNot { it.id in aiIdSet }
        }
    }

    val showSettingsSection = selectedScope == GlobalSearchScope.ALL || selectedScope == GlobalSearchScope.SETTINGS
    val showMessagesSection = selectedScope == GlobalSearchScope.ALL || selectedScope == GlobalSearchScope.MESSAGES
    val showMemorySection = selectedScope == GlobalSearchScope.ALL || selectedScope == GlobalSearchScope.MEMORY
    val showNotesSection = selectedScope == GlobalSearchScope.ALL || selectedScope == GlobalSearchScope.NOTES

    PhantomFullScreenSearchOverlay(
        visible = visible,
        query = query,
        onQueryChange = onQueryChange,
        onDismiss = onDismiss,
        placeholder = stringResource(R.string.search_s_c3aa57),
        onAiSemanticSearch = if (selectedScope == GlobalSearchScope.MESSAGES && trimmedQuery.isNotEmpty()) {
            {
                UiLayoutPrefs.recordRecentSearch(appCtx, trimmedQuery)
                onAiMessageSearch(trimmedQuery)
            }
        } else null,
        aiQuotaHint = stringResource(R.string.search_s_306bf8),
        filterRow = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(GlobalSearchScope.entries, key = { it.name }) { scopeItem ->
                        FilterChip(
                            selected = selectedScope == scopeItem,
                            onClick = { selectedScope = scopeItem },
                            label = { Text(stringResource(scopeItem.labelRes)) },
                        )
                    }
                }
                if (showMessagesSection && trimmedQuery.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(SearchRiskFilter.entries, key = { it.name }) { filter ->
                            FilterChip(
                                selected = searchRiskFilter == filter,
                                onClick = { onSearchRiskFilterChange(filter) },
                                label = { Text(stringResource(filter.labelRes)) },
                            )
                        }
                    }
                }
            }
        },
        resultsContent = {
            if (trimmedQuery.isEmpty()) {
                // ── 無輸入態：最近搜尋 ＋ 常用動作 ＋ 釘選設定 ──
                if (recentSearches.isNotEmpty()) {
                    item(key = "recent_search_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.search_s_38d63d),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            TextButton(onClick = { UiLayoutPrefs.clearRecentSearches(appCtx) }) {
                                Text(stringResource(R.string.common_clear_all))
                            }
                        }
                    }
                    item(key = "recent_search_chips") {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            recentSearches.forEach { term ->
                                Surface(
                                    shape = RoundedCornerShape(AuroraTokens.CornerChip),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    ) {
                                        Text(
                                            text = term,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier
                                                .clickable { onQueryChange(term) }
                                                .padding(vertical = 4.dp),
                                        )
                                        IconButton(
                                            onClick = { UiLayoutPrefs.removeRecentSearch(appCtx, term) },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = resCtx.getString(R.string.search_s_0b142a, term),
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "quick_actions_header") {
                    Text(
                        text = stringResource(R.string.search_s_36940d),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                }
                item(key = "quick_actions_row") {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AssistChip(
                            onClick = {
                                onDismiss()
                                onOpenSettings(null, null)
                            },
                            label = { Text(stringResource(R.string.search_s_40b5c0)) },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                        AssistChip(
                            onClick = {
                                onDismiss()
                                onOpenPermissionHub()
                            },
                            label = { Text(stringResource(R.string.search_s_1579f9)) },
                        )
                        AssistChip(
                            onClick = {
                                onDismiss()
                                onReplayTour()
                            },
                            label = { Text(stringResource(R.string.search_s_1314ad)) },
                        )
                    }
                }

                if (pinnedItems.isNotEmpty()) {
                    item(key = "pinned_settings_header") {
                        Text(
                            text = stringResource(R.string.search_s_71bbd8),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    }
                    items(pinnedItems, key = { "pin_${it.id}" }) { item ->
                        GlobalSearchSettingRow(
                            item = item,
                            settingsBusVer = settingsBusVer,
                            onClick = {
                                onDismiss()
                                onOpenSettings(item.id, item.cardKey)
                            },
                        )
                    }
                }
            } else {
                // ── 有輸入態：分區結果 ──
                if (showSettingsSection) {
                    item(key = "section_settings_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.search_s_e0d0b8, combinedSettingResults.size),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (combinedSettingResults.size > 5 && !expandAllSettings) {
                                TextButton(onClick = { expandAllSettings = true }) {
                                    Text(stringResource(R.string.common_view_all))
                                }
                            }
                        }
                    }

                    // §6.2 AI 語意找設定（不阻擋本地結果，無 key 時灰顯並說明原因）
                    item(key = "section_settings_ai_btn") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = { runAiSettingsSearch() },
                                enabled = isAiConfigured && !isAiSearchingSettings,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp),
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        isAiSearchingSettings -> stringResource(R.string.search_s_fef37a)
                                        !isAiConfigured -> stringResource(R.string.search_s_c9acff)
                                        else -> stringResource(R.string.search_s_4877e2)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (!aiSettingsNote.isNullOrBlank()) {
                                Text(
                                    text = aiSettingsNote!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                        }
                    }

                    val displayedSettings = if (expandAllSettings) combinedSettingResults else combinedSettingResults.take(5)
                    if (displayedSettings.isEmpty() && selectedScope == GlobalSearchScope.SETTINGS) {
                        item(key = "settings_empty") {
                            Text(
                                text = stringResource(R.string.search_s_4de5af),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    } else {
                        items(displayedSettings, key = { "set_${it.id}" }) { item ->
                            GlobalSearchSettingRow(
                                item = item,
                                settingsBusVer = settingsBusVer,
                                onClick = {
                                    UiLayoutPrefs.recordRecentSearch(appCtx, trimmedQuery)
                                    UiLayoutPrefs.recordRecentSetting(appCtx, item.id)
                                    onDismiss()
                                    onOpenSettings(item.id, item.cardKey)
                                },
                            )
                        }
                    }
                }

                if (showMessagesSection) {
                    item(key = "section_messages_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.search_s_55cc00, messageResults.size),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (messageResults.size > 5 && !expandAllMessages) {
                                TextButton(onClick = { expandAllMessages = true }) {
                                    Text(stringResource(R.string.common_view_all))
                                }
                            }
                        }
                    }
                    val displayedMessages = if (expandAllMessages) messageResults else messageResults.take(5)
                    if (displayedMessages.isEmpty() && selectedScope == GlobalSearchScope.MESSAGES) {
                        item(key = "messages_empty") {
                            Text(
                                text = if (isLoadingMessages) stringResource(R.string.common_searching) else stringResource(R.string.search_s_3df885, trimmedQuery),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    } else {
                        items(displayedMessages, key = { "msg_${it.id}" }) { msg ->
                            MessageCard(
                                message = msg,
                                glassState = glassState,
                                highlightAsScam = msg.riskLabel == "scam",
                                highlightQuery = trimmedQuery,
                                matchedKeywords = activeFilterKeywords.filter { kw ->
                                    msg.content.contains(kw, ignoreCase = true) || msg.sender.contains(kw, ignoreCase = true)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        UiLayoutPrefs.recordRecentSearch(appCtx, trimmedQuery)
                                        onDismiss()
                                        onOpenMessageDetail(msg.id)
                                    },
                            )
                        }
                    }
                }

                if (showMemorySection && (memoryMatches.isNotEmpty() || selectedScope == GlobalSearchScope.MEMORY)) {
                    item(key = "section_memory_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.search_s_ad3196, memoryMatches.size),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (memoryMatches.size > 5 && !expandAllMemories) {
                                TextButton(onClick = { expandAllMemories = true }) {
                                    Text(stringResource(R.string.common_view_all))
                                }
                            }
                        }
                    }
                    val displayedMemories = if (expandAllMemories) memoryMatches else memoryMatches.take(5)
                    if (displayedMemories.isEmpty()) {
                        item(key = "memory_empty") {
                            Text(
                                text = stringResource(R.string.search_s_e39b37, trimmedQuery),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    } else {
                        items(displayedMemories, key = { "mem_${it.id}" }) { mem ->
                            val isSensitive = mem.privacyTier != MemoryEntity.PRIVACY_NORMAL
                            Surface(
                                onClick = {
                                    UiLayoutPrefs.recordRecentSearch(appCtx, trimmedQuery)
                                    onDismiss()
                                    onOpenMemoryTab()
                                },
                                shape = RoundedCornerShape(AuroraTokens.CornerCard),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = if (isSensitive) "🔒 ${mem.subject}" else mem.subject,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = if (isSensitive) stringResource(R.string.search_s_edbd60) else mem.content,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                if (showNotesSection && (noteMatches.isNotEmpty() || selectedScope == GlobalSearchScope.NOTES)) {
                    item(key = "section_notes_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.search_s_e4dd0f, noteMatches.size),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (noteMatches.size > 5 && !expandAllNotes) {
                                TextButton(onClick = { expandAllNotes = true }) {
                                    Text(stringResource(R.string.common_view_all))
                                }
                            }
                        }
                    }
                    val displayedNotes = if (expandAllNotes) noteMatches else noteMatches.take(5)
                    if (displayedNotes.isEmpty()) {
                        item(key = "notes_empty") {
                            Text(
                                text = stringResource(R.string.search_s_1ace63, trimmedQuery),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    } else {
                        items(displayedNotes, key = { "note_${it.id}" }) { note ->
                            Surface(
                                onClick = {
                                    UiLayoutPrefs.recordRecentSearch(appCtx, trimmedQuery)
                                    onDismiss()
                                    onOpenNotesTab()
                                },
                                shape = RoundedCornerShape(AuroraTokens.CornerCard),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    if (!note.relatedAppLabel.isNullOrBlank()) {
                                        Text(
                                            text = note.relatedAppLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Text(
                                        text = note.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun GlobalSearchSettingRow(
    item: SettingSearchItem,
    settingsBusVer: Long,
    onClick: () -> Unit,
) {
    val resCtx = LocalContext.current
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val categoryTitle = remember(item.id) {
        SettingsCategoryRegistry.categoryFor(item.id)?.title ?: resCtx.getString(R.string.common_settings)
    }
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
                contentDescription = resCtx.getString(R.string.search_s_e60c1c, item.title, categoryTitle)
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AuroraTokens.MinRowHeight)
                .padding(horizontal = 14.dp, vertical = 10.dp),
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
                    text = categoryTitle + if (SettingsCategoryRegistry.isFoldedByDefault(item.id)) stringResource(R.string.search_s_7e015d) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (item.description.isNotBlank()) {
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
