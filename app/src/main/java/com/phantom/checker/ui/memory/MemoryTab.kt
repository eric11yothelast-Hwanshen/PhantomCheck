package com.phantom.checker.ui.memory

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phantom.checker.data.AiRuleEntity
import com.phantom.checker.data.MemoRuleEntity
import com.phantom.checker.data.MemoryEngine
import com.phantom.checker.data.MemoryEntity
import com.phantom.checker.data.NoteEntity
import com.phantom.checker.data.TagEntity
import com.phantom.checker.data.UserTagRuleEntity
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.ui.EmptyStateView
import com.phantom.checker.ui.MemoryIcon
import com.phantom.checker.ui.PhantomGlassCard
import com.phantom.checker.ui.TagManagementActivity
import com.phantom.checker.ui.WhitelistManagementActivity
import com.phantom.checker.ui.components.FluidSwitch
import com.phantom.checker.ui.components.SwipeToRevealRow
import com.phantom.checker.ui.components.pressableScale
import com.phantom.checker.util.MemoRuleEngine
import com.phantom.checker.util.PhantomPrefs
import com.phantom.checker.util.TagRuleEngine
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private enum class MemoryFilterChip(val label: String) {
    ALL("全部"),
    LONG_TERM("長期"),
    SHORT_TERM("短期"),
    SCAM_IMMUNITY("防詐免疫"),
    CONFLICT("衝突待確認"),
    ARCHIVED("已封存"),
    AUTO_MEMO("自動備忘錄"),
    WHITELIST("白名單"),
    TAG_RULES("標籤規則"),
    MEMO_RULES("備忘規則")
}

private data class MemoryStatTile(
    val emoji: String,
    val label: String,
    val count: Int,
    val filter: MemoryFilterChip,
    val highlightError: Boolean = false
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun MemoryTab(
    glassState: HazeState,
    onOpenMessageDetail: (Long) -> Unit,
    onOpenMemorySettings: () -> Unit,
    onShowMessage: (String) -> Unit,
    initialFilterKey: String? = null,
    onConsumedInitialFilter: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val db = remember(appCtx) { AppDatabaseHolder.db(appCtx) }

    // 觀察 memories 表與既有 notes / memo_rules 表，並同步載入 ai_rules 與 user_tag_rules
    val rawMemories by remember(db) { db.memoryDao().observeAll() }.collectAsState(initial = emptyList())
    val allNotes by remember(db) { db.noteDao().observeAll() }.collectAsState(initial = emptyList())
    val allMemoRules by remember(db) { db.memoRuleDao().observeAll() }.collectAsState(initial = emptyList())

    var whitelistRules by remember { mutableStateOf<List<AiRuleEntity>>(emptyList()) }
    var aiTagRules by remember { mutableStateOf<List<AiRuleEntity>>(emptyList()) }
    var userTagRules by remember { mutableStateOf<List<UserTagRuleEntity>>(emptyList()) }
    var tagsById by remember { mutableStateOf<Map<Long, TagEntity>>(emptyMap()) }
    var reloadRulesTick by remember { mutableStateOf(0) }

    LaunchedEffect(rawMemories.size, allNotes.size, allMemoRules.size, reloadRulesTick) {
        withContext(Dispatchers.IO) {
            MemoryEngine.lazyArchiveExpiredShortTerm(db, System.currentTimeMillis())
            val snap = MemoryEngine.loadIntegratedSnapshot(db)
            whitelistRules = snap.whitelistRules
            aiTagRules = snap.aiTagRules
            userTagRules = snap.userTagRules
            tagsById = snap.tagsById
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(MemoryFilterChip.ALL) }

    // 各區塊展開/收合狀態（沿用 settings_collapsed_cards 持久化風格）
    var expandConflict by remember { mutableStateOf(PhantomPrefs.isMemorySectionExpanded(appCtx, "mem_conflict")) }
    var expandScamImmunity by remember { mutableStateOf(PhantomPrefs.isMemorySectionExpanded(appCtx, "mem_scam_immunity")) }
    var expandLongTerm by remember { mutableStateOf(PhantomPrefs.isMemorySectionExpanded(appCtx, "mem_long_term")) }
    var expandShortTerm by remember { mutableStateOf(PhantomPrefs.isMemorySectionExpanded(appCtx, "mem_short_term")) }
    var expandArchived by remember { mutableStateOf(PhantomPrefs.isMemorySectionExpanded(appCtx, "mem_archived")) }
    var expandAutoMemo by remember { mutableStateOf(PhantomPrefs.isMemorySectionExpanded(appCtx, "mem_auto_memo")) }
    var expandWhitelist by remember { mutableStateOf(PhantomPrefs.isMemorySectionExpanded(appCtx, "mem_whitelist")) }
    var expandTagRules by remember { mutableStateOf(PhantomPrefs.isMemorySectionExpanded(appCtx, "mem_tag_rules")) }
    var expandMemoRules by remember { mutableStateOf(PhantomPrefs.isMemorySectionExpanded(appCtx, "mem_memo_rules")) }

    LaunchedEffect(initialFilterKey) {
        when (initialFilterKey?.trim()?.lowercase()) {
            MemoryEntity.CATEGORY_SCAM_IMMUNITY -> {
                selectedFilter = MemoryFilterChip.SCAM_IMMUNITY
                expandScamImmunity = true
                onConsumedInitialFilter()
            }
            "conflict" -> {
                selectedFilter = MemoryFilterChip.CONFLICT
                expandConflict = true
                onConsumedInitialFilter()
            }
            "long_term" -> {
                selectedFilter = MemoryFilterChip.LONG_TERM
                expandLongTerm = true
                onConsumedInitialFilter()
            }
            "short_term" -> {
                selectedFilter = MemoryFilterChip.SHORT_TERM
                expandShortTerm = true
                onConsumedInitialFilter()
            }
        }
    }

    // 滑動展開列控制（單一展開）
    var revealedMemoryId by remember { mutableStateOf<Long?>(null) }

    // 編輯 / 新增對話框狀態
    var showEditorDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<MemoryEntity?>(null) }
    var showWatchTopicDialog by remember { mutableStateOf(false) }

    // 單筆刪除確認
    var pendingDeleteMemory by remember { mutableStateOf<MemoryEntity?>(null) }

    // 批次刪除模式狀態
    var isBatchMode by remember { mutableStateOf(false) }
    var selectedBatchIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    val nowMs = System.currentTimeMillis()
    val qTrim = searchQuery.trim()

    fun matchesMemoryQuery(m: MemoryEntity): Boolean {
        if (qTrim.isEmpty()) return true
        return m.subject.contains(qTrim, ignoreCase = true) ||
            m.content.contains(qTrim, ignoreCase = true) ||
            MemoryEngine.categoryLabel(m.category).contains(qTrim, ignoreCase = true) ||
            (m.sourceRef?.contains(qTrim, ignoreCase = true) == true)
    }

    val filteredMemories = remember(rawMemories, qTrim) {
        rawMemories.filter { matchesMemoryQuery(it) }
    }

    val conflictMemories = remember(filteredMemories) {
        filteredMemories.filter { it.status == MemoryEntity.STATUS_CONFLICT }
    }
    val scamImmunityMemories = remember(filteredMemories) {
        filteredMemories.filter {
            it.category == MemoryEntity.CATEGORY_SCAM_IMMUNITY &&
                it.status != MemoryEntity.STATUS_ARCHIVED
        }
    }
    val longTermMemories = remember(filteredMemories) {
        filteredMemories.filter {
            it.status == MemoryEntity.STATUS_ACTIVE &&
                it.layer == MemoryEntity.LAYER_LONG_TERM &&
                it.category != MemoryEntity.CATEGORY_SCAM_IMMUNITY
        }
    }
    val shortTermMemories = remember(filteredMemories, nowMs) {
        filteredMemories.filter {
            it.status == MemoryEntity.STATUS_ACTIVE &&
                it.layer == MemoryEntity.LAYER_SHORT_TERM &&
                (it.expiresAt == null || it.expiresAt > nowMs)
        }
    }
    val archivedMemories = remember(filteredMemories, nowMs) {
        filteredMemories.filter {
            it.status == MemoryEntity.STATUS_ARCHIVED ||
                (it.status == MemoryEntity.STATUS_ACTIVE &&
                    it.layer == MemoryEntity.LAYER_SHORT_TERM &&
                    it.expiresAt != null &&
                    it.expiresAt <= nowMs)
        }
    }

    val autoNotes = remember(allNotes, qTrim) {
        allNotes.filter { it.source == NoteEntity.SOURCE_AUTO }.filter { n ->
            qTrim.isEmpty() ||
                n.content.contains(qTrim, ignoreCase = true) ||
                (n.relatedAppLabel?.contains(qTrim, ignoreCase = true) == true)
        }
    }
    val filteredWhitelists = remember(whitelistRules, qTrim) {
        whitelistRules.filter { r ->
            qTrim.isEmpty() ||
                r.ruleText.contains(qTrim, ignoreCase = true) ||
                (r.sender?.contains(qTrim, ignoreCase = true) == true) ||
                (r.packageName?.contains(qTrim, ignoreCase = true) == true)
        }
    }
    val filteredAiTagRules = remember(aiTagRules, tagsById, qTrim) {
        aiTagRules.filter { r ->
            val tagName = r.targetTagId?.let { tagsById[it]?.name }.orEmpty()
            qTrim.isEmpty() ||
                r.ruleText.contains(qTrim, ignoreCase = true) ||
                tagName.contains(qTrim, ignoreCase = true)
        }
    }
    val filteredUserTagRules = remember(userTagRules, tagsById, qTrim) {
        userTagRules.filter { r ->
            val tagName = tagsById[r.targetTagId]?.name.orEmpty()
            qTrim.isEmpty() ||
                r.description.contains(qTrim, ignoreCase = true) ||
                tagName.contains(qTrim, ignoreCase = true) ||
                (r.senderPattern?.contains(qTrim, ignoreCase = true) == true) ||
                (r.contentKeyword?.contains(qTrim, ignoreCase = true) == true)
        }
    }
    val filteredMemoRules = remember(allMemoRules, qTrim) {
        allMemoRules.filter { r ->
            qTrim.isEmpty() ||
                (r.packageName?.contains(qTrim, ignoreCase = true) == true) ||
                (r.sender?.contains(qTrim, ignoreCase = true) == true) ||
                (r.keyword?.contains(qTrim, ignoreCase = true) == true)
        }
    }

    // 計算目前在畫面上可被批次選取的記憶 ID 清單（依目前篩選與搜尋詞）
    val visibleSelectableMemories = remember(
        selectedFilter,
        conflictMemories,
        scamImmunityMemories,
        longTermMemories,
        shortTermMemories,
        archivedMemories
    ) {
        when (selectedFilter) {
            MemoryFilterChip.ALL -> conflictMemories + scamImmunityMemories + longTermMemories + shortTermMemories + archivedMemories
            MemoryFilterChip.LONG_TERM -> longTermMemories
            MemoryFilterChip.SHORT_TERM -> shortTermMemories
            MemoryFilterChip.SCAM_IMMUNITY -> scamImmunityMemories
            MemoryFilterChip.CONFLICT -> conflictMemories
            MemoryFilterChip.ARCHIVED -> archivedMemories
            else -> emptyList()
        }.distinctBy { it.id }
    }

    // 新增 / 編輯記憶對話框
    if (showEditorDialog) {
        MemoryEditorDialog(
            initial = editingMemory,
            onDismiss = {
                showEditorDialog = false
                editingMemory = null
            },
            onSave = { cat, subj, content, layer, expAt, priv, conf ->
                val target = editingMemory
                showEditorDialog = false
                editingMemory = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val now = System.currentTimeMillis()
                        if (target == null) {
                            MemoryEngine.writeMemoryWithConflictDetection(
                                db = db,
                                category = cat,
                                subject = subj,
                                content = content,
                                layer = layer,
                                expiresAt = expAt,
                                privacyTier = priv,
                                confidence = conf,
                                sourceType = MemoryEntity.SOURCE_MANUAL,
                                sourceRef = "手動新增（${PhantomPrefs.formatMessageTime(appCtx, now, includeDate = true)}）",
                                forceReplaceConflict = false,
                                nowMs = now
                            )
                        } else {
                            db.memoryDao().update(
                                target.copy(
                                    category = cat,
                                    subject = subj,
                                    content = content,
                                    layer = layer,
                                    expiresAt = if (layer == MemoryEntity.LAYER_SHORT_TERM) {
                                        expAt ?: (now + TimeUnit.DAYS.toMillis(7))
                                    } else null,
                                    privacyTier = priv,
                                    confidence = conf,
                                    updatedAt = now
                                )
                            )
                        }
                    }
                    onShowMessage(if (target == null) "已新增記憶" else "已更新記憶")
                }
            }
        )
    }

    // 單筆刪除確認對話框
    pendingDeleteMemory?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteMemory = null },
            title = { Text("確認刪除記憶") },
            text = {
                Text("確定要永久刪除記憶「${target.subject}：${target.content.take(50)}」嗎？此操作無法復原。")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        val id = target.id
                        pendingDeleteMemory = null
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                db.memoryDao().deleteById(id)
                            }
                            selectedBatchIds = selectedBatchIds - id
                            onShowMessage("已刪除記憶")
                        }
                    }
                ) {
                    Text("確認刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteMemory = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 批次刪除確認對話框
    if (showBatchDeleteConfirm && selectedBatchIds.isNotEmpty()) {
        val count = selectedBatchIds.size
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("確認批次刪除記憶") },
            text = {
                Text(
                    buildString {
                        append("確定要永久刪除已勾選的 $count 筆記憶嗎？")
                        if (qTrim.isNotEmpty()) {
                            append("\n（目前搜尋篩選：「$qTrim」）")
                        }
                        append("\n此操作無法復原。")
                    }
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        val idsToDelete = selectedBatchIds.toList()
                        showBatchDeleteConfirm = false
                        isBatchMode = false
                        selectedBatchIds = emptySet()
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                db.memoryDao().deleteByIds(idsToDelete)
                            }
                            onShowMessage("已批次刪除 ${idsToDelete.size} 筆記憶")
                        }
                    }
                ) {
                    Text("確認刪除（$count）")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchHasFocused by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val isSearchVisible = isSearchExpanded || searchQuery.isNotEmpty()
    var filterChipsCollapsed by remember { mutableStateOf(PhantomPrefs.isMemoryFilterChipsCollapsed(ctx)) }
    val chipShape = RoundedCornerShape(8.dp)
    var pendingImmunityDraft by remember { mutableStateOf(PhantomPrefs.getPendingScamImmunityDraft(appCtx)) }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            runCatching { searchFocusRequester.requestFocus() }
        }
    }

    BackHandler(enabled = isSearchVisible) {
        if (searchQuery.isNotEmpty()) {
            searchQuery = ""
        } else {
            searchHasFocused = false
            isSearchExpanded = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 頂部標題列：標題＋統計摘要 ｜ 搜尋鈕＋新增＋齒輪（保持 48dp 觸控目標，不擠壓）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "個人記憶庫",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "長期 ${longTermMemories.size} · 短期 ${shortTermMemories.size}" +
                        (if (conflictMemories.isNotEmpty()) " · 衝突 ${conflictMemories.size}" else "") +
                        " · 封存 ${archivedMemories.size}",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (conflictMemories.isNotEmpty()) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = {
                        if (isSearchVisible && searchQuery.isEmpty()) {
                            searchHasFocused = false
                            isSearchExpanded = false
                        } else {
                            isSearchExpanded = true
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = if (isSearchVisible) "收合搜尋框" else "搜尋記憶",
                        tint = if (isSearchVisible) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                TextButton(
                    onClick = { showWatchTopicDialog = true }
                ) {
                    Text("關注清單", style = MaterialTheme.typography.labelMedium)
                }
                IconButton(
                    onClick = {
                        editingMemory = null
                        showEditorDialog = true
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "新增記憶")
                }
                IconButton(
                    onClick = onOpenMemorySettings,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "記憶庫與 AI 助手設定")
                }
            }
        }

        if (showWatchTopicDialog) {
            WatchTopicManagerDialog(
                onDismiss = { showWatchTopicDialog = false },
                onShowMessage = onShowMessage
            )
        }

        // 可收合搜尋框（點擊頂部搜尋鈕展開並自動聚焦；有字維持展開；清空失焦收合；返回鍵先清字再收合）
        AnimatedVisibility(
            visible = isSearchVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .focusRequester(searchFocusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            searchHasFocused = true
                        } else if (searchHasFocused && searchQuery.isEmpty()) {
                            searchHasFocused = false
                            isSearchExpanded = false
                        }
                    },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                placeholder = {
                    Text(
                        text = "搜尋記憶內容、人物主題、來源或規則…",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "搜尋記憶")
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (searchQuery.isNotEmpty()) {
                                searchQuery = ""
                            } else {
                                searchHasFocused = false
                                isSearchExpanded = false
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = if (searchQuery.isNotEmpty()) "清除搜尋" else "收合搜尋"
                        )
                    }
                }
            )
        }

        // 搜尋結果狀態條（有輸入關鍵字且非批次模式時，顯示符合筆數與快速進入批次管理）
        AnimatedVisibility(
            visible = qTrim.isNotEmpty() && !isBatchMode,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "符合「$qTrim」共 ${visibleSelectableMemories.size} 筆可選記憶",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { isBatchMode = true },
                        modifier = Modifier.heightIn(min = 36.dp)
                    ) {
                        Text("批次選取", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // 批次模式操作條（支援依搜尋結果全選→確認刪除）
        AnimatedVisibility(
            visible = isBatchMode,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            val allVisibleIds = remember(visibleSelectableMemories) {
                visibleSelectableMemories.map { it.id }.toSet()
            }
            val allSelected = allVisibleIds.isNotEmpty() && allVisibleIds.all { it in selectedBatchIds }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (qTrim.isNotEmpty()) {
                            "符合「$qTrim」共 ${allVisibleIds.size} 筆 · 已選 ${selectedBatchIds.size} 筆"
                        } else {
                            "已選 ${selectedBatchIds.size} / ${allVisibleIds.size} 筆"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            enabled = allVisibleIds.isNotEmpty(),
                            onClick = {
                                selectedBatchIds = if (allSelected) {
                                    selectedBatchIds - allVisibleIds
                                } else {
                                    selectedBatchIds + allVisibleIds
                                }
                            }
                        ) {
                            Text(if (allSelected) "取消全選" else "全選結果")
                        }
                        Button(
                            enabled = selectedBatchIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            onClick = { showBatchDeleteConfirm = true }
                        ) {
                            Text("刪除(${selectedBatchIds.size})")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // #357 圖像化記憶統計四格總覽（點擊直達對應篩選）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val statTiles = listOf(
                MemoryStatTile("🧠", "長期", longTermMemories.size, MemoryFilterChip.LONG_TERM),
                MemoryStatTile("⏳", "短期", shortTermMemories.size, MemoryFilterChip.SHORT_TERM),
                MemoryStatTile("⚖️", "衝突", conflictMemories.size, MemoryFilterChip.CONFLICT, conflictMemories.isNotEmpty()),
                MemoryStatTile("🛡️", "免疫", scamImmunityMemories.size, MemoryFilterChip.SCAM_IMMUNITY)
            )
            statTiles.forEach { tile ->
                val isSelected = selectedFilter == tile.filter
                val containerColor = when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                    tile.highlightError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                }
                val valueColor = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    tile.highlightError -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = containerColor,
                    border = BorderStroke(
                        width = if (isSelected) 1.2.dp else 0.75.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            selectedFilter = if (selectedFilter == tile.filter) MemoryFilterChip.ALL else tile.filter
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "${tile.emoji} ${tile.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${tile.count}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = valueColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // #357 分層等高篩選列（展開分「記憶」／「來源與規則」兩排水平滑動，收合單排顯示全部＋目前選中）
        val badgeCountOf: (MemoryFilterChip) -> Int = { chip ->
            when (chip) {
                MemoryFilterChip.ALL -> filteredMemories.size
                MemoryFilterChip.LONG_TERM -> longTermMemories.size
                MemoryFilterChip.SHORT_TERM -> shortTermMemories.size
                MemoryFilterChip.SCAM_IMMUNITY -> scamImmunityMemories.size
                MemoryFilterChip.CONFLICT -> conflictMemories.size
                MemoryFilterChip.ARCHIVED -> archivedMemories.size
                MemoryFilterChip.AUTO_MEMO -> autoNotes.size
                MemoryFilterChip.WHITELIST -> filteredWhitelists.size
                MemoryFilterChip.TAG_RULES -> filteredAiTagRules.size + filteredUserTagRules.size
                MemoryFilterChip.MEMO_RULES -> filteredMemoRules.size
            }
        }
        val memoryTierChips = listOf(
            MemoryFilterChip.ALL,
            MemoryFilterChip.CONFLICT,
            MemoryFilterChip.LONG_TERM,
            MemoryFilterChip.SHORT_TERM,
            MemoryFilterChip.ARCHIVED
        )
        val ruleTierChips = listOf(
            MemoryFilterChip.SCAM_IMMUNITY,
            MemoryFilterChip.AUTO_MEMO,
            MemoryFilterChip.WHITELIST,
            MemoryFilterChip.TAG_RULES,
            MemoryFilterChip.MEMO_RULES
        )

        // #363 修復篩選 chips 被「批次管理」與收合鈕遮擋：
        // - 展開模式：將批次管理／收合鈕移至獨立頂部控制列，兩排分層 LazyRow 獨佔全寬並加 trailing contentPadding + clipToBounds
        // - 收合模式：僅 1–2 顆 chip，保留同列並加 clipToBounds + 8dp 明確間距 + trailing contentPadding
        // - 所有 chip 與膠囊改用 heightIn(min = 34.dp)，確保大字體 fontScale=1.3 與窄螢幕 360dp 不截字、不重疊
        val batchAndCollapseControls: @Composable () -> Unit = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isBatchMode) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    border = BorderStroke(
                        width = 0.75.dp,
                        color = if (isBatchMode) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        }
                    ),
                    modifier = Modifier
                        .heightIn(min = 34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            isBatchMode = !isBatchMode
                            if (!isBatchMode) selectedBatchIds = emptySet()
                        }
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isBatchMode) "結束批次" else "批次管理",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isBatchMode) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        .clickable {
                            filterChipsCollapsed = !filterChipsCollapsed
                            PhantomPrefs.setMemoryFilterChipsCollapsed(ctx, filterChipsCollapsed)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (filterChipsCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (filterChipsCollapsed) "展開篩選標籤" else "收合篩選標籤",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (filterChipsCollapsed) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val compactList = buildList {
                    add(MemoryFilterChip.ALL)
                    if (selectedFilter != MemoryFilterChip.ALL) add(selectedFilter)
                }
                LazyRow(
                    modifier = Modifier
                        .weight(1f)
                        .clipToBounds(),
                    contentPadding = PaddingValues(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(compactList, key = { it.name }) { chip ->
                        val count = badgeCountOf(chip)
                        FilterChip(
                            selected = selectedFilter == chip,
                            onClick = { selectedFilter = chip },
                            shape = chipShape,
                            modifier = Modifier.heightIn(min = 34.dp),
                            label = {
                                Text(
                                    text = if (count > 0) "${chip.label} $count" else chip.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                batchAndCollapseControls()
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "分類篩選",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    batchAndCollapseControls()
                }
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds(),
                    contentPadding = PaddingValues(end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item(key = "tier_label_mem") {
                        Text(
                            text = "記憶",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = 2.dp)
                        )
                    }
                    items(memoryTierChips, key = { it.name }) { chip ->
                        val count = badgeCountOf(chip)
                        FilterChip(
                            selected = selectedFilter == chip,
                            onClick = { selectedFilter = chip },
                            shape = chipShape,
                            modifier = Modifier.heightIn(min = 34.dp),
                            label = {
                                Text(
                                    text = if (count > 0) "${chip.label} $count" else chip.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds(),
                    contentPadding = PaddingValues(end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item(key = "tier_label_rules") {
                        Text(
                            text = "規則",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = 2.dp)
                        )
                    }
                    items(ruleTierChips, key = { it.name }) { chip ->
                        val count = badgeCountOf(chip)
                        FilterChip(
                            selected = selectedFilter == chip,
                            onClick = { selectedFilter = chip },
                            shape = chipShape,
                            modifier = Modifier.heightIn(min = 34.dp),
                            label = {
                                Text(
                                    text = if (count > 0) "${chip.label} $count" else chip.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // #328 每週詐騙免疫自動彙總待確認卡（一律須使用者確認才寫入 memories）
        pendingImmunityDraft?.let { draft ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "🛡️ 每週防詐免疫彙總建議（待確認）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = draft.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                PhantomPrefs.clearPendingScamImmunityDraft(appCtx)
                                pendingImmunityDraft = null
                                onShowMessage("已略過本次防詐免疫彙總")
                            }
                        ) {
                            Text("略過")
                        }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    MemoryEngine.writeMemoryWithConflictDetection(
                                        db = db,
                                        category = MemoryEntity.CATEGORY_SCAM_IMMUNITY,
                                        subject = draft.subject,
                                        content = draft.content,
                                        layer = MemoryEntity.LAYER_LONG_TERM,
                                        privacyTier = MemoryEntity.PRIVACY_NORMAL,
                                        confidence = MemoryEntity.CONFIDENCE_INFERRED,
                                        sourceType = MemoryEntity.SOURCE_NOTIFICATION,
                                        sourceRef = "每週詐騙手法彙總"
                                    )
                                    PhantomPrefs.clearPendingScamImmunityDraft(appCtx)
                                    pendingImmunityDraft = null
                                    onShowMessage("已寫入「防詐免疫」長期記憶")
                                }
                            }
                        ) {
                            Text("確認寫入免疫記憶")
                        }
                    }
                }
            }
        }

        val showConflicts = selectedFilter == MemoryFilterChip.ALL || selectedFilter == MemoryFilterChip.CONFLICT
        val showScamImmunity = selectedFilter == MemoryFilterChip.ALL || selectedFilter == MemoryFilterChip.SCAM_IMMUNITY
        val showLongTerm = selectedFilter == MemoryFilterChip.ALL || selectedFilter == MemoryFilterChip.LONG_TERM
        val showShortTerm = selectedFilter == MemoryFilterChip.ALL || selectedFilter == MemoryFilterChip.SHORT_TERM
        val showArchived = selectedFilter == MemoryFilterChip.ALL || selectedFilter == MemoryFilterChip.ARCHIVED
        val showAutoMemo = selectedFilter == MemoryFilterChip.ALL || selectedFilter == MemoryFilterChip.AUTO_MEMO
        val showWhitelist = selectedFilter == MemoryFilterChip.ALL || selectedFilter == MemoryFilterChip.WHITELIST
        val showTagRules = selectedFilter == MemoryFilterChip.ALL || selectedFilter == MemoryFilterChip.TAG_RULES
        val showMemoRules = selectedFilter == MemoryFilterChip.ALL || selectedFilter == MemoryFilterChip.MEMO_RULES

        val isCompletelyEmpty = filteredMemories.isEmpty() &&
            autoNotes.isEmpty() &&
            filteredWhitelists.isEmpty() &&
            filteredAiTagRules.isEmpty() &&
            filteredUserTagRules.isEmpty() &&
            filteredMemoRules.isEmpty()

        if (isCompletelyEmpty) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateView(
                    icon = MemoryIcon,
                    title = if (qTrim.isNotEmpty()) "找不到符合「$qTrim」的記憶" else "記憶庫目前為空",
                    description = if (qTrim.isNotEmpty()) {
                        "請嘗試其他關鍵字或切換上方篩選標籤。"
                    } else {
                        "你可以在 AI 助手對話中請 AI 記住偏好、人物、行程，或點擊右上角「＋」手動新增長期與短期記憶。"
                    },
                    actionLabel = if (qTrim.isNotEmpty()) "清除搜尋" else "新增第一筆記憶",
                    onAction = {
                        if (qTrim.isNotEmpty()) {
                            searchQuery = ""
                        } else {
                            editingMemory = null
                            showEditorDialog = true
                        }
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // 0. #337 「我對你的瞭解」畫像卡（≤200 tokens，敏感記憶絕不入卡）
                if (selectedFilter == MemoryFilterChip.ALL && qTrim.isEmpty()) {
                    item(key = "section_portrait_card") {
                        com.phantom.checker.ui.components.PortraitCardSection(
                            glassState = glassState,
                            onShowMessage = onShowMessage
                        )
                    }
                }

                // 1. 衝突待確認區塊（醒目提示＋兩按鈕裁定）
                if (showConflicts && (conflictMemories.isNotEmpty() || selectedFilter == MemoryFilterChip.CONFLICT)) {
                    item(key = "section_conflict") {
                        val conflictGroups = remember(conflictMemories) {
                            conflictMemories.groupBy { m ->
                                "${m.category}|${MemoryEngine.normalizeSubjectKey(m.subject)}"
                            }
                        }
                        CollapsibleMemorySectionCard(
                            title = "⚠️ 衝突待確認（${conflictMemories.size} 筆）",
                            subtitle = "同主題偵測到不同內容，系統未覆蓋原值，請裁定要保留哪一個版本",
                            expanded = expandConflict,
                            highlightWarning = conflictMemories.isNotEmpty(),
                            glassState = glassState,
                            onToggle = {
                                expandConflict = !expandConflict
                                PhantomPrefs.setMemorySectionExpanded(appCtx, "mem_conflict", expandConflict)
                            }
                        ) {
                            if (conflictMemories.isEmpty()) {
                                Text(
                                    text = "目前沒有待裁定的記憶衝突。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    conflictGroups.values.forEach { groupItems ->
                                        val sortedByTime = groupItems.sortedBy { it.createdAt }
                                        val firstSubject = sortedByTime.first().subject
                                        val catLbl = MemoryEngine.categoryLabel(sortedByTime.first().category)
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Warning,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.tertiary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Text(
                                                        text = "[$catLbl] 主題：$firstSubject（共 ${sortedByTime.size} 個候選版本）",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                sortedByTime.forEachIndexed { idx, cand ->
                                                    val isOldest = idx == 0 && sortedByTime.size > 1
                                                    val versionTag = if (isOldest) "原記憶 (#${cand.id})" else "新記憶 (#${cand.id})"
                                                    val btnLabel = if (isOldest) "保留原樣" else "更新記憶（採用此版）"
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(10.dp),
                                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(
                                                                    text = "$versionTag · ${MemoryEngine.confidenceLabel(cand.confidence)}",
                                                                    style = MaterialTheme.typography.labelMedium,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isOldest) {
                                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                                    } else {
                                                                        MaterialTheme.colorScheme.primary
                                                                    }
                                                                )
                                                                Text(
                                                                    text = PhantomPrefs.formatMessageTime(appCtx, cand.updatedAt, includeDate = true),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                            Text(
                                                                text = cand.content,
                                                                style = MaterialTheme.typography.bodyMedium
                                                            )
                                                            if (!cand.sourceRef.isNullOrBlank()) {
                                                                Text(
                                                                    text = "來源：${cand.sourceRef}",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.End,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                TextButton(
                                                                    onClick = {
                                                                        editingMemory = cand
                                                                        showEditorDialog = true
                                                                    }
                                                                ) {
                                                                    Text("編輯")
                                                                }
                                                                Button(
                                                                    onClick = {
                                                                        scope.launch {
                                                                            MemoryEngine.resolveConflictGroup(db, cand.id)
                                                                            onShowMessage("已裁定採用 #$${cand.id} 並將衝突舊版封存".replace("#$", "#"))
                                                                        }
                                                                    }
                                                                ) {
                                                                    Text(btnLabel)
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

                // 1.5 #335 防詐免疫記憶專區（category=scam_immunity，附來源訊息跳轉）
                if (showScamImmunity && (scamImmunityMemories.isNotEmpty() || selectedFilter == MemoryFilterChip.SCAM_IMMUNITY)) {
                    item(key = "section_scam_immunity") {
                        CollapsibleMemorySectionCard(
                            title = "🛡️ 防詐免疫（${scamImmunityMemories.size} 筆）",
                            subtitle = "曾遇過的詐騙話術與免疫特徵；收到金錢、催促或驗證碼訊息時自動交叉比對",
                            expanded = expandScamImmunity,
                            glassState = glassState,
                            onToggle = {
                                expandScamImmunity = !expandScamImmunity
                                PhantomPrefs.setMemorySectionExpanded(appCtx, "mem_scam_immunity", expandScamImmunity)
                            }
                        ) {
                            if (scamImmunityMemories.isEmpty()) {
                                Text(
                                    text = "尚無防詐免疫記憶。可在詐騙中心點擊「加入防詐免疫」或於 AI 助手對話中新增。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    scamImmunityMemories.forEach { mem ->
                                        MemoryItemCard(
                                            memory = mem,
                                            isBatchMode = isBatchMode,
                                            isSelected = mem.id in selectedBatchIds,
                                            isRevealed = revealedMemoryId == mem.id,
                                            onReveal = { revealedMemoryId = mem.id },
                                            onCollapse = { if (revealedMemoryId == mem.id) revealedMemoryId = null },
                                            onToggleSelect = {
                                                selectedBatchIds = if (mem.id in selectedBatchIds) {
                                                    selectedBatchIds - mem.id
                                                } else {
                                                    selectedBatchIds + mem.id
                                                }
                                            },
                                            onEdit = {
                                                editingMemory = mem
                                                showEditorDialog = true
                                            },
                                            onDelete = { pendingDeleteMemory = mem },
                                            onTogglePrivacy = {
                                                val nextTier = if (mem.privacyTier == MemoryEntity.PRIVACY_SENSITIVE) {
                                                    MemoryEntity.PRIVACY_NORMAL
                                                } else {
                                                    MemoryEntity.PRIVACY_SENSITIVE
                                                }
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        db.memoryDao().update(
                                                            mem.copy(
                                                                privacyTier = nextTier,
                                                                updatedAt = System.currentTimeMillis()
                                                            )
                                                        )
                                                    }
                                                    onShowMessage(
                                                        if (nextTier == MemoryEntity.PRIVACY_SENSITIVE) {
                                                            "已設為「敏感記憶」（僅本機保存，絕不加入 AI prompt）"
                                                        } else {
                                                            "已設為「一般記憶」（允許檢索注入 AI 對話）"
                                                        }
                                                    )
                                                }
                                            },
                                            onArchiveOrRestore = {
                                                scope.launch {
                                                    MemoryEngine.archiveMemory(db, mem.id)
                                                    onShowMessage("已封存該筆防詐免疫記憶")
                                                }
                                            },
                                            onOpenMessageDetail = onOpenMessageDetail
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. 長期記憶區塊
                if (showLongTerm) {
                    item(key = "section_long_term") {
                        CollapsibleMemorySectionCard(
                            title = "長期記憶（${longTermMemories.size} 筆）",
                            subtitle = "持久保存的人物、地點、偏好、習慣與關係（敏感記憶僅本機顯示不外送）",
                            expanded = expandLongTerm,
                            glassState = glassState,
                            onToggle = {
                                expandLongTerm = !expandLongTerm
                                PhantomPrefs.setMemorySectionExpanded(appCtx, "mem_long_term", expandLongTerm)
                            }
                        ) {
                            if (longTermMemories.isEmpty()) {
                                Text(
                                    text = "尚無長期記憶。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    longTermMemories.forEach { mem ->
                                        MemoryItemCard(
                                            memory = mem,
                                            isBatchMode = isBatchMode,
                                            isSelected = mem.id in selectedBatchIds,
                                            isRevealed = revealedMemoryId == mem.id,
                                            onReveal = { revealedMemoryId = mem.id },
                                            onCollapse = { if (revealedMemoryId == mem.id) revealedMemoryId = null },
                                            onToggleSelect = {
                                                selectedBatchIds = if (mem.id in selectedBatchIds) {
                                                    selectedBatchIds - mem.id
                                                } else {
                                                    selectedBatchIds + mem.id
                                                }
                                            },
                                            onEdit = {
                                                editingMemory = mem
                                                showEditorDialog = true
                                            },
                                            onDelete = { pendingDeleteMemory = mem },
                                            onTogglePrivacy = {
                                                val nextTier = if (mem.privacyTier == MemoryEntity.PRIVACY_SENSITIVE) {
                                                    MemoryEntity.PRIVACY_NORMAL
                                                } else {
                                                    MemoryEntity.PRIVACY_SENSITIVE
                                                }
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        db.memoryDao().update(
                                                            mem.copy(
                                                                privacyTier = nextTier,
                                                                updatedAt = System.currentTimeMillis()
                                                            )
                                                        )
                                                    }
                                                    onShowMessage(
                                                        if (nextTier == MemoryEntity.PRIVACY_SENSITIVE) {
                                                            "已設為「敏感記憶」（僅本機保存，絕不加入 AI prompt）"
                                                        } else {
                                                            "已設為「一般記憶」（允許檢索注入 AI 對話）"
                                                        }
                                                    )
                                                }
                                            },
                                            onArchiveOrRestore = {
                                                scope.launch {
                                                    MemoryEngine.archiveMemory(db, mem.id)
                                                    onShowMessage("已封存記憶 #${mem.id}")
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. 短期記憶區塊（顯示到期日）
                if (showShortTerm) {
                    item(key = "section_short_term") {
                        CollapsibleMemorySectionCard(
                            title = "短期記憶（${shortTermMemories.size} 筆）",
                            subtitle = "具時效性的近期狀態或臨時行程，到期後自動轉入「已封存」",
                            expanded = expandShortTerm,
                            glassState = glassState,
                            onToggle = {
                                expandShortTerm = !expandShortTerm
                                PhantomPrefs.setMemorySectionExpanded(appCtx, "mem_short_term", expandShortTerm)
                            }
                        ) {
                            if (shortTermMemories.isEmpty()) {
                                Text(
                                    text = "尚無使用中的短期記憶。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    shortTermMemories.forEach { mem ->
                                        MemoryItemCard(
                                            memory = mem,
                                            isBatchMode = isBatchMode,
                                            isSelected = mem.id in selectedBatchIds,
                                            isRevealed = revealedMemoryId == mem.id,
                                            onReveal = { revealedMemoryId = mem.id },
                                            onCollapse = { if (revealedMemoryId == mem.id) revealedMemoryId = null },
                                            onToggleSelect = {
                                                selectedBatchIds = if (mem.id in selectedBatchIds) {
                                                    selectedBatchIds - mem.id
                                                } else {
                                                    selectedBatchIds + mem.id
                                                }
                                            },
                                            onEdit = {
                                                editingMemory = mem
                                                showEditorDialog = true
                                            },
                                            onDelete = { pendingDeleteMemory = mem },
                                            onTogglePrivacy = {
                                                val nextTier = if (mem.privacyTier == MemoryEntity.PRIVACY_SENSITIVE) {
                                                    MemoryEntity.PRIVACY_NORMAL
                                                } else {
                                                    MemoryEntity.PRIVACY_SENSITIVE
                                                }
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        db.memoryDao().update(
                                                            mem.copy(
                                                                privacyTier = nextTier,
                                                                updatedAt = System.currentTimeMillis()
                                                            )
                                                        )
                                                    }
                                                    onShowMessage(
                                                        if (nextTier == MemoryEntity.PRIVACY_SENSITIVE) {
                                                            "已設為「敏感記憶」（僅本機保存，絕不外送）"
                                                        } else {
                                                            "已設為「一般記憶」"
                                                        }
                                                    )
                                                }
                                            },
                                            onArchiveOrRestore = {
                                                scope.launch {
                                                    MemoryEngine.archiveMemory(db, mem.id)
                                                    onShowMessage("已封存短期記憶 #${mem.id}")
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. 已封存區塊（過期短期記憶或被取代的衝突版本，可一鍵恢復）
                if (showArchived) {
                    item(key = "section_archived") {
                        CollapsibleMemorySectionCard(
                            title = "已封存（${archivedMemories.size} 筆）",
                            subtitle = "已過期的短期記憶或衝突裁定後封存的歷史紀錄，不會注入 AI 對話，可隨時恢復",
                            expanded = expandArchived,
                            glassState = glassState,
                            onToggle = {
                                expandArchived = !expandArchived
                                PhantomPrefs.setMemorySectionExpanded(appCtx, "mem_archived", expandArchived)
                            }
                        ) {
                            if (archivedMemories.isEmpty()) {
                                Text(
                                    text = "目前沒有已封存的記憶。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    archivedMemories.forEach { mem ->
                                        MemoryItemCard(
                                            memory = mem,
                                            isBatchMode = isBatchMode,
                                            isSelected = mem.id in selectedBatchIds,
                                            isRevealed = revealedMemoryId == mem.id,
                                            onReveal = { revealedMemoryId = mem.id },
                                            onCollapse = { if (revealedMemoryId == mem.id) revealedMemoryId = null },
                                            onToggleSelect = {
                                                selectedBatchIds = if (mem.id in selectedBatchIds) {
                                                    selectedBatchIds - mem.id
                                                } else {
                                                    selectedBatchIds + mem.id
                                                }
                                            },
                                            onEdit = {
                                                editingMemory = mem
                                                showEditorDialog = true
                                            },
                                            onDelete = { pendingDeleteMemory = mem },
                                            onTogglePrivacy = {
                                                val nextTier = if (mem.privacyTier == MemoryEntity.PRIVACY_SENSITIVE) {
                                                    MemoryEntity.PRIVACY_NORMAL
                                                } else {
                                                    MemoryEntity.PRIVACY_SENSITIVE
                                                }
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        db.memoryDao().update(
                                                            mem.copy(
                                                                privacyTier = nextTier,
                                                                updatedAt = System.currentTimeMillis()
                                                            )
                                                        )
                                                    }
                                                    onShowMessage("已更新隱私分級")
                                                }
                                            },
                                            onArchiveOrRestore = {
                                                scope.launch {
                                                    MemoryEngine.restoreArchivedMemory(db, mem.id)
                                                    onShowMessage("已恢復記憶 #${mem.id} 至使用中")
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. 整合區塊：自動備忘錄（notes source=auto）
                if (showAutoMemo) {
                    item(key = "section_auto_memo") {
                        CollapsibleMemorySectionCard(
                            title = "自動備忘錄（${autoNotes.size} 筆）",
                            subtitle = "AI 分析新通知時自動擷取的備忘紀錄（與「記事」分頁同步）",
                            expanded = expandAutoMemo,
                            glassState = glassState,
                            onToggle = {
                                expandAutoMemo = !expandAutoMemo
                                PhantomPrefs.setMemorySectionExpanded(appCtx, "mem_auto_memo", expandAutoMemo)
                            }
                        ) {
                            if (autoNotes.isEmpty()) {
                                Text(
                                    text = "尚無自動備忘錄。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    autoNotes.take(20).forEach { note ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    note.relatedMessageId?.let { onOpenMessageDetail(it) }
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        MemoryBadge(
                                                            text = note.relatedAppLabel?.takeIf { it.isNotBlank() } ?: "自動備忘",
                                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                        Text(
                                                            text = PhantomPrefs.formatMessageTime(appCtx, note.updatedAt, includeDate = true),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(
                                                        text = note.content,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            MemoryEngine.deleteAutoNote(appCtx, db, note.id)
                                                            onShowMessage("已刪除自動備忘錄")
                                                        }
                                                    },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "刪除自動備忘錄",
                                                        tint = MaterialTheme.colorScheme.error
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

                // 6. 整合區塊：白名單規則（ai_rules kind=whitelist）
                if (showWhitelist) {
                    item(key = "section_whitelist") {
                        CollapsibleMemorySectionCard(
                            title = "白名單規則（${filteredWhitelists.size} 筆）",
                            subtitle = "信任的寄件者或通知規則（命中時優先標為安全並跳過防詐情報預查）",
                            expanded = expandWhitelist,
                            glassState = glassState,
                            onToggle = {
                                expandWhitelist = !expandWhitelist
                                PhantomPrefs.setMemorySectionExpanded(appCtx, "mem_whitelist", expandWhitelist)
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        ctx.startActivity(Intent(ctx, WhitelistManagementActivity::class.java))
                                    },
                                    modifier = Modifier.pressableScale()
                                ) {
                                    Text("開啟完整白名單管理頁")
                                }
                            }
                            if (filteredWhitelists.isEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "尚無白名單規則。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Spacer(Modifier.height(6.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    filteredWhitelists.take(20).forEach { rule ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    val scopeText = listOfNotNull(
                                                        rule.packageName?.takeIf { it.isNotBlank() },
                                                        rule.sender?.takeIf { it.isNotBlank() }?.let { "寄件者：$it" }
                                                    ).joinToString(" · ").ifBlank { "全域白名單" }
                                                    Text(
                                                        text = scopeText,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = rule.ruleText,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                FluidSwitch(
                                                    checked = rule.isEnabled,
                                                    onCheckedChange = { enabled ->
                                                        scope.launch {
                                                            MemoryEngine.toggleAiRuleEnabled(db, rule, enabled)
                                                            reloadRulesTick++
                                                        }
                                                    }
                                                )
                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            MemoryEngine.deleteAiRule(db, rule.id)
                                                            reloadRulesTick++
                                                            onShowMessage("已刪除白名單規則")
                                                        }
                                                    },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "刪除白名單規則",
                                                        tint = MaterialTheme.colorScheme.error
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

                // 7. 整合區塊：標籤規則（user_tag_rules ＋ ai_rules kind=tag）
                if (showTagRules) {
                    item(key = "section_tag_rules") {
                        val totalTagRules = filteredUserTagRules.size + filteredAiTagRules.size
                        CollapsibleMemorySectionCard(
                            title = "標籤規則（$totalTagRules 筆）",
                            subtitle = "使用者標籤記憶規則與 AI 自動標籤規則",
                            expanded = expandTagRules,
                            glassState = glassState,
                            onToggle = {
                                expandTagRules = !expandTagRules
                                PhantomPrefs.setMemorySectionExpanded(appCtx, "mem_tag_rules", expandTagRules)
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        ctx.startActivity(Intent(ctx, TagManagementActivity::class.java))
                                    },
                                    modifier = Modifier.pressableScale()
                                ) {
                                    Text("開啟完整標籤與規則管理頁")
                                }
                            }
                            if (totalTagRules == 0) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "尚無自訂標籤規則。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Spacer(Modifier.height(6.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    filteredUserTagRules.take(15).forEach { ur ->
                                        val tagName = tagsById[ur.targetTagId]?.name ?: "未知標籤"
                                        val condStr = TagRuleEngine.formatRuleConditions(ur)
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "[$tagName] ${ur.description}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = "確定性條件：$condStr",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                FluidSwitch(
                                                    checked = ur.isEnabled,
                                                    onCheckedChange = { enabled ->
                                                        scope.launch {
                                                            MemoryEngine.toggleUserTagRuleEnabled(db, ur, enabled)
                                                            reloadRulesTick++
                                                        }
                                                    }
                                                )
                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            MemoryEngine.deleteUserTagRule(db, ur.id)
                                                            reloadRulesTick++
                                                            onShowMessage("已刪除標籤記憶規則")
                                                        }
                                                    },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "刪除標籤規則",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    filteredAiTagRules.take(15).forEach { ar ->
                                        val tagName = ar.targetTagId?.let { tagsById[it]?.name } ?: "AI 標籤"
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "[$tagName · AI 語意] ${ar.ruleText}",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                                FluidSwitch(
                                                    checked = ar.isEnabled,
                                                    onCheckedChange = { enabled ->
                                                        scope.launch {
                                                            MemoryEngine.toggleAiRuleEnabled(db, ar, enabled)
                                                            reloadRulesTick++
                                                        }
                                                    }
                                                )
                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            MemoryEngine.deleteAiRule(db, ar.id)
                                                            reloadRulesTick++
                                                            onShowMessage("已刪除 AI 標籤規則")
                                                        }
                                                    },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "刪除 AI 標籤規則",
                                                        tint = MaterialTheme.colorScheme.error
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

                // 8. 整合區塊：備忘規則（memo_rules）
                if (showMemoRules) {
                    item(key = "section_memo_rules") {
                        CollapsibleMemorySectionCard(
                            title = "備忘規則（${filteredMemoRules.size} 筆）",
                            subtitle = "控制特定 App／寄件者／關鍵字「一律記事、一律待辦或永不記事」",
                            expanded = expandMemoRules,
                            glassState = glassState,
                            onToggle = {
                                expandMemoRules = !expandMemoRules
                                PhantomPrefs.setMemorySectionExpanded(appCtx, "mem_memo_rules", expandMemoRules)
                            }
                        ) {
                            if (filteredMemoRules.isEmpty()) {
                                Text(
                                    text = "尚無備忘規則。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    filteredMemoRules.take(20).forEach { mr ->
                                        val condParts = listOfNotNull(
                                            mr.packageName?.takeIf { it.isNotBlank() }?.let { "App: $it" },
                                            mr.sender?.takeIf { it.isNotBlank() }?.let { "寄件者: $it" },
                                            mr.keyword?.takeIf { it.isNotBlank() }?.let { "關鍵字: $it" }
                                        ).joinToString(" · ").ifBlank { "未指定條件" }
                                        val actLbl = MemoRuleEngine.actionLabel(mr.action)
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = actLbl,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = condParts,
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                                FluidSwitch(
                                                    checked = mr.isEnabled,
                                                    onCheckedChange = { enabled ->
                                                        scope.launch {
                                                            MemoryEngine.toggleMemoRuleEnabled(db, mr, enabled)
                                                        }
                                                    }
                                                )
                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            MemoryEngine.deleteMemoRule(db, mr.id)
                                                            onShowMessage("已刪除備忘規則")
                                                        }
                                                    },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "刪除備忘規則",
                                                        tint = MaterialTheme.colorScheme.error
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

@Composable
private fun CollapsibleMemorySectionCard(
    title: String,
    subtitle: String,
    expanded: Boolean,
    glassState: HazeState,
    highlightWarning: Boolean = false,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    PhantomGlassCard(
        glassState = glassState,
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        contentPadding = 14.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { onToggle() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (highlightWarning) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "收合區塊" else "展開區塊",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                content()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun MemoryItemCard(
    memory: MemoryEntity,
    isBatchMode: Boolean,
    isSelected: Boolean,
    isRevealed: Boolean,
    onReveal: () -> Unit,
    onCollapse: () -> Unit,
    onToggleSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTogglePrivacy: () -> Unit,
    onArchiveOrRestore: () -> Unit,
    onOpenMessageDetail: ((Long) -> Unit)? = null
) {
    val ctx = LocalContext.current
    val catLabel = MemoryEngine.categoryLabel(memory.category)
    val isConfirmed = memory.confidence == MemoryEntity.CONFIDENCE_CONFIRMED
    val isSensitive = memory.privacyTier == MemoryEntity.PRIVACY_SENSITIVE
    val isArchived = memory.status == MemoryEntity.STATUS_ARCHIVED
    val isShortTerm = memory.layer == MemoryEntity.LAYER_SHORT_TERM

    SwipeToRevealRow(
        revealed = isRevealed && !isBatchMode,
        onReveal = onReveal,
        onCollapse = onCollapse,
        revealDistance = 148.dp,
        actions = {
            OutlinedButton(
                onClick = {
                    onCollapse()
                    onArchiveOrRestore()
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.heightIn(min = 40.dp)
            ) {
                Text(if (isArchived) "恢復" else "封存", style = MaterialTheme.typography.labelSmall)
            }
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                onClick = {
                    onCollapse()
                    onDelete()
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.heightIn(min = 40.dp)
            ) {
                Text("刪除", style = MaterialTheme.typography.labelSmall)
            }
        }
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(
                    onClick = {
                        if (isBatchMode) {
                            onToggleSelect()
                        } else {
                            onEdit()
                        }
                    },
                    onLongClick = {
                        onToggleSelect()
                    }
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 頂列：徽章列（category、AI 推測/已確認、長期/短期、隱私分級）＋ 操作鈕
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MemoryBadge(
                            text = catLabel,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        MemoryBadge(
                            text = if (isConfirmed) "✓ 已確認" else "✨ AI 推測",
                            containerColor = if (isConfirmed) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            },
                            contentColor = if (isConfirmed) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            }
                        )
                        MemoryBadge(
                            text = if (isShortTerm) "短期" else "長期",
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isSensitive) {
                            MemoryBadge(
                                text = "🔒 敏感（僅本機）",
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    if (isBatchMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect() }
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onEdit,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "編輯記憶",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "刪除記憶",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // 主題與內容
                Text(
                    text = memory.subject,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = memory.content,
                    style = MaterialTheme.typography.bodyMedium
                )

                // 短期到期日與來源（sourceRef）與引用次數
                val metaLines = buildList {
                    if (isShortTerm && memory.expiresAt != null) {
                        val expText = PhantomPrefs.formatMessageTime(ctx, memory.expiresAt, includeDate = true)
                        add("⏰ 到期日：$expText")
                    }
                    if (!memory.sourceRef.isNullOrBlank()) {
                        add("來源：${memory.sourceRef}")
                    } else {
                        add("來源：${MemoryEngine.sourceTypeLabel(memory.sourceType)}")
                    }
                    val updatedStr = PhantomPrefs.formatMessageTime(ctx, memory.updatedAt, includeDate = true)
                    val usagePart = if (memory.useCount > 0) " · 已注入引用 ${memory.useCount} 次" else ""
                    add("更新於 $updatedStr$usagePart")
                }
                metaLines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // #335 若 provenance 對應特定通知訊息，提供直達 MessageDetailActivity 的「來源訊息」連結
                val relatedMsgId = remember(memory.sourceRef) {
                    MemoryEngine.extractRelatedMessageId(memory.sourceRef)
                }
                if (relatedMsgId != null && onOpenMessageDetail != null && !isBatchMode) {
                    TextButton(
                        onClick = { onOpenMessageDetail(relatedMsgId) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.heightIn(min = 32.dp)
                    ) {
                        Text(
                            text = "查看來源訊息 #$relatedMsgId →",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 底部快捷動作列（切換隱私分級、封存/恢復）
                if (!isBatchMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onTogglePrivacy,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isSensitive) "改為一般（允許注入 AI）" else "設為敏感（禁止外送 AI）",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = onArchiveOrRestore,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isArchived) "恢復至使用中" else "封存",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryBadge(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryEditorDialog(
    initial: MemoryEntity?,
    onDismiss: () -> Unit,
    onSave: (
        category: String,
        subject: String,
        content: String,
        layer: String,
        expiresAt: Long?,
        privacyTier: String,
        confidence: String
    ) -> Unit
) {
    val nowMs = remember { System.currentTimeMillis() }
    var category by remember(initial) {
        mutableStateOf(initial?.category ?: MemoryEntity.CATEGORY_PREFERENCE)
    }
    var subject by remember(initial) {
        mutableStateOf(initial?.subject.orEmpty())
    }
    var content by remember(initial) {
        mutableStateOf(initial?.content.orEmpty())
    }
    var layer by remember(initial) {
        mutableStateOf(initial?.layer ?: MemoryEntity.LAYER_LONG_TERM)
    }
    val initialDays = remember(initial) {
        val exp = initial?.expiresAt
        if (exp != null && exp > nowMs) {
            ((exp - nowMs) / TimeUnit.DAYS.toMillis(1)).toInt().coerceIn(1, 90)
        } else {
            7
        }
    }
    var expireDays by remember(initial) { mutableStateOf(initialDays) }
    var privacyTier by remember(initial) {
        mutableStateOf(initial?.privacyTier ?: MemoryEntity.PRIVACY_NORMAL)
    }
    var confidence by remember(initial) {
        mutableStateOf(initial?.confidence ?: MemoryEntity.CONFIDENCE_CONFIRMED)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增記憶" else "編輯記憶 #${initial.id}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("分類", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MemoryEngine.ALLOWED_CATEGORIES.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(MemoryEngine.categoryLabel(cat)) }
                        )
                    }
                }

                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("主題鍵（如人物名、地點、事項）") },
                    placeholder = { Text("例如：媽媽、常去健身房、飲食偏好") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("記憶內容") },
                    placeholder = { Text("例如：不吃香菜，咖啡習慣喝無糖冰美式") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("記憶層級", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = layer == MemoryEntity.LAYER_LONG_TERM,
                        onClick = { layer = MemoryEntity.LAYER_LONG_TERM },
                        label = { Text("長期記憶（永久有效）") }
                    )
                    FilterChip(
                        selected = layer == MemoryEntity.LAYER_SHORT_TERM,
                        onClick = { layer = MemoryEntity.LAYER_SHORT_TERM },
                        label = { Text("短期記憶（定期自動封存）") }
                    )
                }

                if (layer == MemoryEntity.LAYER_SHORT_TERM) {
                    Text("短期記憶有效天數", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 3, 7, 14, 30).forEach { d ->
                            FilterChip(
                                selected = expireDays == d,
                                onClick = { expireDays = d },
                                label = { Text("$d 天") }
                            )
                        }
                    }
                }

                Text("隱私分級", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = privacyTier == MemoryEntity.PRIVACY_NORMAL,
                        onClick = { privacyTier = MemoryEntity.PRIVACY_NORMAL },
                        label = { Text("一般（可檢索注入 AI）") }
                    )
                    FilterChip(
                        selected = privacyTier == MemoryEntity.PRIVACY_SENSITIVE,
                        onClick = { privacyTier = MemoryEntity.PRIVACY_SENSITIVE },
                        label = { Text("🔒 敏感（僅本機、絕不外送）") }
                    )
                }

                Text("確認狀態", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = confidence == MemoryEntity.CONFIDENCE_CONFIRMED,
                        onClick = { confidence = MemoryEntity.CONFIDENCE_CONFIRMED },
                        label = { Text("✓ 已確認（檢索加權）") }
                    )
                    FilterChip(
                        selected = confidence == MemoryEntity.CONFIDENCE_INFERRED,
                        onClick = { confidence = MemoryEntity.CONFIDENCE_INFERRED },
                        label = { Text("✨ AI 推測") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = content.isNotBlank(),
                onClick = {
                    val cleanContent = content.trim()
                    val cleanSubj = subject.trim().ifEmpty { cleanContent.take(16) }
                    val expiresAt = if (layer == MemoryEntity.LAYER_SHORT_TERM) {
                        System.currentTimeMillis() + TimeUnit.DAYS.toMillis(expireDays.toLong())
                    } else null
                    onSave(category, cleanSubj, cleanContent, layer, expiresAt, privacyTier, confidence)
                }
            ) {
                Text("儲存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
