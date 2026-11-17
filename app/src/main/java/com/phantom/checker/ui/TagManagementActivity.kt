package com.phantom.checker.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import com.phantom.checker.data.AiRuleEntity
import com.phantom.checker.data.AppCategoryRuleEntity
import com.phantom.checker.data.TagEntity
import com.phantom.checker.data.UserTagRuleEntity
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.ui.components.FluidSwitch
import com.phantom.checker.util.TagRuleEngine
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TAG_COLOR_PALETTE = listOf(
    "#7C3AED", // 品牌紫
    "#2563EB", // 藍
    "#059669", // 綠
    "#D97706", // 琥珀
    "#DC2626", // 紅
    "#DB2777", // 粉紅
    "#0891B2", // 青
    "#4B5563"  // 灰
)

private data class InstalledAppChoice(
    val packageName: String,
    val label: String
)

class TagManagementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhantomCheckTheme {
                TagManagementScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManagementScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var tags by remember { mutableStateOf(listOf<TagEntity>()) }
    var appCatRules by remember { mutableStateOf(listOf<AppCategoryRuleEntity>()) }
    var aiRules by remember { mutableStateOf(listOf<AiRuleEntity>()) }
    var userTagRules by remember { mutableStateOf(listOf<UserTagRuleEntity>()) }

    var showEditor by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf<TagEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<TagEntity?>(null) }

    var showAddAppCatDialog by remember { mutableStateOf(false) }
    var deleteAppCatCandidate by remember { mutableStateOf<AppCategoryRuleEntity?>(null) }

    var showAddAiRuleDialog by remember { mutableStateOf(false) }
    var deleteAiRuleCandidate by remember { mutableStateOf<AiRuleEntity?>(null) }

    var showTestTagRulesDialog by remember { mutableStateOf(false) }
    var deleteUserTagRuleCandidate by remember { mutableStateOf<UserTagRuleEntity?>(null) }

    suspend fun reloadAll() {
        withContext(Dispatchers.IO) {
            val db = AppDatabaseHolder.db(appCtx)
            tags = db.tagDao().all()
            appCatRules = db.appCategoryRuleDao().all()
            aiRules = db.aiRuleDao().all()
            userTagRules = db.userTagRuleDao().all()
        }
    }

    LaunchedEffect(Unit) {
        reloadAll()
    }

    val tagById = remember(tags) { tags.associateBy { it.id } }

    if (showEditor) {
        TagEditorDialog(
            initial = editingTag,
            onDismiss = {
                showEditor = false
                editingTag = null
            },
            onSave = { name, colorHex, aiHint, isAiManaged, isAppCategory ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val dao = AppDatabaseHolder.db(appCtx).tagDao()
                        val existing = editingTag
                        if (existing == null) {
                            val nextOrder = (tags.maxOfOrNull { it.sortOrder } ?: -1) + 1
                            dao.insert(
                                TagEntity(
                                    name = name,
                                    colorHex = colorHex,
                                    sortOrder = nextOrder,
                                    aiHint = aiHint,
                                    isAiManaged = isAiManaged,
                                    isAppCategory = isAppCategory
                                )
                            )
                        } else {
                            dao.update(
                                existing.copy(
                                    name = name,
                                    colorHex = colorHex,
                                    aiHint = aiHint,
                                    isAiManaged = isAiManaged,
                                    isAppCategory = isAppCategory
                                )
                            )
                        }
                    }
                    showEditor = false
                    editingTag = null
                    reloadAll()
                }
            }
        )
    }

    deleteCandidate?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("刪除標籤") },
            text = { Text("確定要刪除標籤「${target.name}」嗎？這將一併移除所有訊息與分類規則上的此標籤關聯。") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                db.withTransaction {
                                    db.tagDao().deleteCrossRefsByTag(target.id)
                                    db.appCategoryRuleDao().deleteByTagId(target.id)
                                    db.tagDao().deleteById(target.id)
                                }
                                com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                            }
                            deleteCandidate = null
                            reloadAll()
                            snackbarHostState.showSnackbar("已刪除標籤「${target.name}」")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確認刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showAddAppCatDialog) {
        AddAppCategoryRuleDialog(
            tags = tags,
            onDismiss = { showAddAppCatDialog = false },
            onSave = { pkg, tagId ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val dao = AppDatabaseHolder.db(appCtx).appCategoryRuleDao()
                        dao.deleteByPackage(pkg)
                        dao.insert(
                            AppCategoryRuleEntity(
                                packageName = pkg,
                                tagId = tagId,
                                isEnabled = true
                            )
                        )
                        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                    }
                    showAddAppCatDialog = false
                    reloadAll()
                    snackbarHostState.showSnackbar("已新增 App 分類規則")
                }
            }
        )
    }

    deleteAppCatCandidate?.let { rule ->
        AlertDialog(
            onDismissRequest = { deleteAppCatCandidate = null },
            title = { Text("刪除 App 分類規則") },
            text = { Text("確定要移除 ${rule.packageName} 的分類規則嗎？") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                AppDatabaseHolder.db(appCtx).appCategoryRuleDao().deleteById(rule.id)
                                com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                            }
                            deleteAppCatCandidate = null
                            reloadAll()
                            snackbarHostState.showSnackbar("已刪除 App 分類規則")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確認刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteAppCatCandidate = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showAddAiRuleDialog) {
        AddAiRuleDialog(
            tags = tags,
            onDismiss = { showAddAiRuleDialog = false },
            onSave = { ruleText, kind, targetTagId ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        AppDatabaseHolder.db(appCtx).aiRuleDao().insert(
                            AiRuleEntity(
                                ruleText = ruleText,
                                targetTagId = targetTagId,
                                kind = kind,
                                isEnabled = true,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }
                    showAddAiRuleDialog = false
                    reloadAll()
                    snackbarHostState.showSnackbar("已新增 AI 規則（將套用於新訊息）")
                }
            }
        )
    }

    deleteAiRuleCandidate?.let { rule ->
        AlertDialog(
            onDismissRequest = { deleteAiRuleCandidate = null },
            title = { Text("刪除 AI 規則") },
            text = { Text("確定要刪除這條自然語言規則嗎？\n「${rule.ruleText}」") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                AppDatabaseHolder.db(appCtx).aiRuleDao().deleteById(rule.id)
                            }
                            deleteAiRuleCandidate = null
                            reloadAll()
                            snackbarHostState.showSnackbar("已刪除 AI 規則")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確認刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteAiRuleCandidate = null }) {
                    Text("取消")
                }
            }
        )
    }

    deleteUserTagRuleCandidate?.let { rule ->
        AlertDialog(
            onDismissRequest = { deleteUserTagRuleCandidate = null },
            title = { Text("刪除標籤記憶規則") },
            text = { Text("確定要刪除這條規則嗎？刪除後新訊息將恢復原本的 AI 判斷。\n「${rule.description}」") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                AppDatabaseHolder.db(appCtx).userTagRuleDao().deleteById(rule.id)
                            }
                            deleteUserTagRuleCandidate = null
                            reloadAll()
                            snackbarHostState.showSnackbar("已刪除標籤記憶規則")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確認刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteUserTagRuleCandidate = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showTestTagRulesDialog) {
        TestUserTagRulesDialog(
            rules = userTagRules,
            tagById = tagById,
            onDismiss = { showTestTagRulesDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 52.dp,
                title = {
                    Text(
                        text = "標籤與 AI 規則管理",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingTag = null
                    showEditor = true
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "新增標籤")
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        val glassState = remember { HazeState() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .phantomGlassSource(glassState)
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "標籤清單",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (tags.isEmpty()) {
                    item {
                        PhantomGlassCard(
                            glassState = glassState,
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 18.dp,
                            contentPadding = 14.dp
                        ) {
                            Text(
                                text = "尚無自訂標籤，請點擊右下角「+」新增。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    itemsIndexed(tags, key = { _, item -> "tag_${item.id}" }) { idx, tag ->
                        PhantomGlassCard(
                            glassState = glassState,
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 20.dp,
                            contentPadding = 12.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(parseTagColor(tag.colorHex))
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = tag.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (tag.isAiManaged) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        text = "AI",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            if (tag.isAppCategory) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Text(
                                                        text = "App分類",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (tag.aiHint.isNotBlank()) {
                                            Text(
                                                text = tag.aiHint,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        enabled = idx > 0,
                                        onClick = {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    val prev = tags[idx - 1]
                                                    val dao = AppDatabaseHolder.db(appCtx).tagDao()
                                                    dao.update(tag.copy(sortOrder = prev.sortOrder))
                                                    dao.update(prev.copy(sortOrder = tag.sortOrder))
                                                }
                                                reloadAll()
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移")
                                    }
                                    IconButton(
                                        enabled = idx < tags.lastIndex,
                                        onClick = {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    val next = tags[idx + 1]
                                                    val dao = AppDatabaseHolder.db(appCtx).tagDao()
                                                    dao.update(tag.copy(sortOrder = next.sortOrder))
                                                    dao.update(next.copy(sortOrder = tag.sortOrder))
                                                }
                                                reloadAll()
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移")
                                    }
                                    IconButton(onClick = {
                                        editingTag = tag
                                        showEditor = true
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "編輯")
                                    }
                                    IconButton(onClick = { deleteCandidate = tag }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "刪除",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 區塊二：App 分類規則（零 token）
                item {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "App 分類規則（零 token）",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "收到指定 App 通知時直接自動貼上分類標籤，不消耗 AI 額度。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { showAddAppCatDialog = true },
                            enabled = tags.isNotEmpty()
                        ) {
                            Text("新增規則")
                        }
                    }
                }

                if (appCatRules.isEmpty()) {
                    item {
                        PhantomGlassCard(
                            glassState = glassState,
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 18.dp,
                            contentPadding = 12.dp
                        ) {
                            Text(
                                text = "尚無 App 分類規則。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(appCatRules, key = { "appcat_${it.id}" }) { rule ->
                        val assignedTag = tagById[rule.tagId]
                        val appLabel = remember(rule.packageName) {
                            try {
                                ctx.packageManager.getApplicationLabel(
                                    ctx.packageManager.getApplicationInfo(rule.packageName, 0)
                                ).toString()
                            } catch (_: Exception) {
                                rule.packageName
                            }
                        }
                        PhantomGlassCard(
                            glassState = glassState,
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 18.dp,
                            contentPadding = 12.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIconImage(
                                        packageName = rule.packageName,
                                        contentDescription = appLabel,
                                        size = 32.dp
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = appLabel,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "→ ${assignedTag?.name ?: "未知標籤"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FluidSwitch(
                                        checked = rule.isEnabled,
                                        onCheckedChange = { enabled ->
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    AppDatabaseHolder.db(appCtx).appCategoryRuleDao()
                                                        .update(rule.copy(isEnabled = enabled))
                                                }
                                                reloadAll()
                                            }
                                        }
                                    )
                                    IconButton(onClick = { deleteAppCatCandidate = rule }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "刪除規則",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 區塊三：自然語言 AI 規則
                item {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "自然語言 AI 規則",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "用自然語言教 AI 辨識白名單或自動貼標（僅套用於新收到的訊息）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = { showAddAiRuleDialog = true }) {
                            Text("新增規則")
                        }
                    }
                }

                if (aiRules.count { it.isEnabled } > 20) {
                    item {
                        PhantomGlassCard(
                            glassState = glassState,
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 16.dp,
                            contentPadding = 12.dp
                        ) {
                            Text(
                                text = "目前已啟用超過 20 條 AI 規則，分析時僅會帶入前 20 條，建議精簡規則以節省 token。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                if (aiRules.isEmpty()) {
                    item {
                        PhantomGlassCard(
                            glassState = glassState,
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 18.dp,
                            contentPadding = 12.dp
                        ) {
                            Text(
                                text = "尚無自然語言 AI 規則。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(aiRules, key = { "airule_${it.id}" }) { rule ->
                        val targetTag = rule.targetTagId?.let { tagById[it] }
                        val kindLabel = if (rule.kind == AiRuleEntity.KIND_WHITELIST) {
                            "非詐騙白名單"
                        } else {
                            "套用標籤：${targetTag?.name ?: "自動"}"
                        }
                        PhantomGlassCard(
                            glassState = glassState,
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 18.dp,
                            contentPadding = 12.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (rule.kind == AiRuleEntity.KIND_WHITELIST) {
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.primaryContainer
                                        }
                                    ) {
                                        Text(
                                            text = kindLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = rule.ruleText,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (!rule.sender.isNullOrBlank() || !rule.packageName.isNullOrBlank()) {
                                        val meta = listOfNotNull(
                                            rule.sender?.takeIf { it.isNotBlank() }?.let { "寄件者：$it" },
                                            rule.packageName?.takeIf { it.isNotBlank() }?.let { "App：$it" }
                                        ).joinToString(" · ")
                                        Text(
                                            text = meta,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FluidSwitch(
                                        checked = rule.isEnabled,
                                        onCheckedChange = { enabled ->
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    AppDatabaseHolder.db(appCtx).aiRuleDao()
                                                        .update(rule.copy(isEnabled = enabled))
                                                }
                                                reloadAll()
                                            }
                                        }
                                    )
                                    IconButton(onClick = { deleteAiRuleCandidate = rule }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "刪除 AI 規則",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "使用者標籤記憶規則（確定性・零 Token）",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "優先序高於 AI 自動標籤，可在 AI 助手對話教學建立（如「手機管家歸類為系統通知」）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = { showTestTagRulesDialog = true }) {
                            Text("測試命中")
                        }
                    }
                }

                if (userTagRules.isEmpty()) {
                    item {
                        PhantomGlassCard(
                            glassState = glassState,
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 18.dp,
                            contentPadding = 12.dp
                        ) {
                            Text(
                                text = "尚無使用者標籤記憶規則。可至「AI 助手」用自然語言教學建立。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(userTagRules, key = { "utagrule_${it.id}" }) { rule ->
                        val targetTag = tagById[rule.targetTagId]
                        val sourceLabel = if (rule.source == "chat") "對話教學" else "手動建立"
                        val condSummary = TagRuleEngine.formatRuleConditions(rule)
                        PhantomGlassCard(
                            glassState = glassState,
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 18.dp,
                            contentPadding = 12.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                text = sourceLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "標籤：${targetTag?.name ?: "未知(#${rule.targetTagId})"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = rule.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "條件：$condSummary",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FluidSwitch(
                                        checked = rule.isEnabled,
                                        onCheckedChange = { enabled ->
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    AppDatabaseHolder.db(appCtx).userTagRuleDao()
                                                        .update(rule.copy(isEnabled = enabled))
                                                }
                                                reloadAll()
                                            }
                                        }
                                    )
                                    IconButton(onClick = { deleteUserTagRuleCandidate = rule }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "刪除標籤記憶規則",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(64.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagEditorDialog(
    initial: TagEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Boolean, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var selectedColor by remember { mutableStateOf(initial?.colorHex ?: TAG_COLOR_PALETTE.first()) }
    var aiHint by remember { mutableStateOf(initial?.aiHint ?: "") }
    var isAiManaged by remember { mutableStateOf(initial?.isAiManaged ?: false) }
    var isAppCategory by remember { mutableStateOf(initial?.isAppCategory ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增標籤" else "編輯標籤") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("標籤名稱") },
                    placeholder = { Text("例如：工作、帳單、物流") }
                )
                OutlinedTextField(
                    value = aiHint,
                    onValueChange = { aiHint = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    label = { Text("AI 判斷依據（選填）") },
                    placeholder = { Text("例如：銀行扣款、信用卡消費通知") }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "允許 AI 自動指派此標籤",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    FluidSwitch(
                        checked = isAiManaged,
                        onCheckedChange = { isAiManaged = it }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "作為 App 分類標籤",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    FluidSwitch(
                        checked = isAppCategory,
                        onCheckedChange = { isAppCategory = it }
                    )
                }
                Text("選擇顏色", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TAG_COLOR_PALETTE.forEach { hex ->
                        val isSelected = selectedColor.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(parseTagColor(hex))
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            width = 3.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), selectedColor, aiHint.trim(), isAiManaged, isAppCategory) },
                enabled = name.isNotBlank()
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddAppCategoryRuleDialog(
    tags: List<TagEntity>,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit
) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf(listOf<InstalledAppChoice>()) }
    var search by remember { mutableStateOf("") }
    var selectedPkg by remember { mutableStateOf<String?>(null) }
    var selectedTagId by remember(tags) {
        mutableStateOf(tags.firstOrNull { it.isAppCategory }?.id ?: tags.firstOrNull()?.id)
    }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .map { ri ->
                    InstalledAppChoice(
                        packageName = ri.activityInfo.packageName,
                        label = ri.loadLabel(pm).toString()
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }
    }

    val filteredApps = remember(apps, search) {
        val q = search.trim()
        if (q.isEmpty()) apps
        else apps.filter { it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增 App 分類規則") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. 選擇分類標籤", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tags.forEach { tag ->
                        FilterChip(
                            selected = selectedTagId == tag.id,
                            onClick = { selectedTagId = tag.id },
                            label = { Text(tag.name) }
                        )
                    }
                }
                Text("2. 選擇 App", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("搜尋 App 名稱…") }
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isSel = selectedPkg == app.packageName
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSel) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .clickable { selectedPkg = app.packageName }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconImage(
                                packageName = app.packageName,
                                contentDescription = app.label,
                                size = 28.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pkg = selectedPkg ?: return@Button
                    val tid = selectedTagId ?: return@Button
                    onSave(pkg, tid)
                },
                enabled = selectedPkg != null && selectedTagId != null
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddAiRuleDialog(
    tags: List<TagEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, Long?) -> Unit
) {
    var ruleText by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(AiRuleEntity.KIND_WHITELIST) }
    var targetTagId by remember(tags) { mutableStateOf(tags.firstOrNull()?.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增自然語言 AI 規則") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = kind == AiRuleEntity.KIND_WHITELIST,
                        onClick = { kind = AiRuleEntity.KIND_WHITELIST },
                        label = { Text("非詐騙白名單") }
                    )
                    FilterChip(
                        selected = kind == AiRuleEntity.KIND_TAG,
                        onClick = { kind = AiRuleEntity.KIND_TAG },
                        label = { Text("套用標籤") }
                    )
                }
                OutlinedTextField(
                    value = ruleText,
                    onValueChange = { ruleText = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                    label = { Text("規則描述") },
                    placeholder = {
                        Text(
                            if (kind == AiRuleEntity.KIND_WHITELIST) {
                                "例如：台灣大哥大 twm5g.co 官方短連結通知不是詐騙"
                            } else {
                                "例如：PChome 含 P 幣或折扣碼的訊息標記必看"
                            }
                        )
                    }
                )
                if (kind == AiRuleEntity.KIND_TAG && tags.isNotEmpty()) {
                    Text("指定標籤", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tags.forEach { tag ->
                            FilterChip(
                                selected = targetTagId == tag.id,
                                onClick = { targetTagId = tag.id },
                                label = { Text(tag.name) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val tid = if (kind == AiRuleEntity.KIND_TAG) targetTagId else null
                    onSave(ruleText.trim(), kind, tid)
                },
                enabled = ruleText.isNotBlank() && (kind == AiRuleEntity.KIND_WHITELIST || targetTagId != null)
            ) {
                Text("新增")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun TestUserTagRulesDialog(
    rules: List<UserTagRuleEntity>,
    tagById: Map<Long, TagEntity>,
    onDismiss: () -> Unit
) {
    var sampleAppLabel by remember { mutableStateOf("手機管家") }
    var samplePackage by remember { mutableStateOf("") }
    var sampleSender by remember { mutableStateOf("") }
    var sampleContent by remember { mutableStateOf("") }

    val matched = remember(rules, sampleAppLabel, samplePackage, sampleSender, sampleContent) {
        TagRuleEngine.evaluateTagRules(
            rules = rules,
            packageName = samplePackage.trim(),
            appLabel = sampleAppLabel.trim(),
            sender = sampleSender.trim(),
            content = sampleContent.trim()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("測試標籤記憶規則命中") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = sampleAppLabel,
                    onValueChange = { sampleAppLabel = it },
                    label = { Text("App 名稱（例：手機管家）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = samplePackage,
                    onValueChange = { samplePackage = it },
                    label = { Text("套件名稱（選填，例：com.miui.securitycenter）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sampleSender,
                    onValueChange = { sampleSender = it },
                    label = { Text("寄件者（選填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sampleContent,
                    onValueChange = { sampleContent = it },
                    label = { Text("訊息內容（選填，例：您的驗證碼為 123456）") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                if (rules.none { it.isEnabled }) {
                    Text(
                        text = "目前沒有已啟用的標籤記憶規則。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (matched.isEmpty()) {
                    Text(
                        text = "未命中任何標籤記憶規則（將交由 AI／App 分類處理）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "命中 ${matched.size} 條規則：",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    matched.forEach { r ->
                        val tName = tagById[r.targetTagId]?.name ?: "未知(#${r.targetTagId})"
                        Text(
                            text = "• ${r.description} → [$tName]",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}

