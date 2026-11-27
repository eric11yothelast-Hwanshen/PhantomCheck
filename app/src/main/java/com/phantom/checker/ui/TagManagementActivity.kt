package com.phantom.checker.ui

import com.phantom.checker.ui.components.phantomEnter
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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

private data class TagManagementSnapshot(
    val tags: List<TagEntity>,
    val appCatRules: List<AppCategoryRuleEntity>,
    val aiRules: List<AiRuleEntity>,
    val userTagRules: List<UserTagRuleEntity>
)

class TagManagementActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.phantom.checker.util.AppLocaleHelper.wrapContext(newBase))
    }
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
    val resCtx = LocalContext.current
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
    var showUserTagRuleEditor by remember { mutableStateOf(false) }
    var editingUserTagRule by remember { mutableStateOf<UserTagRuleEntity?>(null) }
    var deleteUserTagRuleCandidate by remember { mutableStateOf<UserTagRuleEntity?>(null) }

    suspend fun reloadAll() {
        val snapshot = withContext(Dispatchers.IO) {
            val db = AppDatabaseHolder.db(appCtx)
            TagManagementSnapshot(
                tags = db.tagDao().all(),
                appCatRules = db.appCategoryRuleDao().all(),
                aiRules = db.aiRuleDao().all(),
                userTagRules = db.userTagRuleDao().all()
            )
        }
        tags = snapshot.tags
        appCatRules = snapshot.appCatRules
        aiRules = snapshot.aiRules
        userTagRules = snapshot.userTagRules
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
                val existing = editingTag
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val dao = AppDatabaseHolder.db(appCtx).tagDao()
                        if (existing == null) {
                            val nextOrder = (dao.all().maxOfOrNull { it.sortOrder } ?: -1) + 1
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
                        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
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
            title = { Text(stringResource(R.string.tag_mgmt_s_66923b)) },
            text = { Text(stringResource(R.string.tag_mgmt_s_cd8e2b, target.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                // R1 (#545) 共用交易：同交易刪除標籤、關聯與 user_tag_rules / app_category_rules / ai_rules 孤兒
                                AppDatabaseHolder.db(appCtx).tagDao().deleteTagWithOrphans(target.id)
                                com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                            }
                            deleteCandidate = null
                            reloadAll()
                            snackbarHostState.showSnackbar(resCtx.getString(R.string.tag_mgmt_s_9ef04e, target.name))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.common_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(R.string.common_cancel))
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
                        val db = AppDatabaseHolder.db(appCtx)
                        db.withTransaction {
                            val dao = db.appCategoryRuleDao()
                            dao.deleteByPackage(pkg)
                            dao.insert(
                                AppCategoryRuleEntity(
                                    packageName = pkg,
                                    tagId = tagId,
                                    isEnabled = true
                                )
                            )
                        }
                        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                    }
                    showAddAppCatDialog = false
                    reloadAll()
                    snackbarHostState.showSnackbar(resCtx.getString(R.string.tag_mgmt_s_8acb54))
                }
            }
        )
    }

    deleteAppCatCandidate?.let { rule ->
        AlertDialog(
            onDismissRequest = { deleteAppCatCandidate = null },
            title = { Text(stringResource(R.string.tag_mgmt_s_41fae7)) },
            text = { Text(stringResource(R.string.tag_mgmt_s_3c913a, rule.packageName)) },
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
                            snackbarHostState.showSnackbar(resCtx.getString(R.string.tag_mgmt_s_af5c7d))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.common_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteAppCatCandidate = null }) {
                    Text(stringResource(R.string.common_cancel))
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
                        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                    }
                    showAddAiRuleDialog = false
                    reloadAll()
                    snackbarHostState.showSnackbar(resCtx.getString(R.string.tag_mgmt_s_00c5dc))
                }
            }
        )
    }

    deleteAiRuleCandidate?.let { rule ->
        AlertDialog(
            onDismissRequest = { deleteAiRuleCandidate = null },
            title = { Text(stringResource(R.string.tag_mgmt_s_1a5c2f)) },
            text = { Text(stringResource(R.string.tag_mgmt_s_d31863, rule.ruleText)) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                AppDatabaseHolder.db(appCtx).aiRuleDao().deleteById(rule.id)
                                com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                            }
                            deleteAiRuleCandidate = null
                            reloadAll()
                            snackbarHostState.showSnackbar(resCtx.getString(R.string.tag_mgmt_s_74ac4a))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.common_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteAiRuleCandidate = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    deleteUserTagRuleCandidate?.let { rule ->
        AlertDialog(
            onDismissRequest = { deleteUserTagRuleCandidate = null },
            title = { Text(stringResource(R.string.tag_mgmt_s_bdf1ab)) },
            text = { Text(stringResource(R.string.tag_mgmt_s_9bc5f7, rule.description)) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                AppDatabaseHolder.db(appCtx).userTagRuleDao().deleteById(rule.id)
                                com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                            }
                            deleteUserTagRuleCandidate = null
                            reloadAll()
                            snackbarHostState.showSnackbar(resCtx.getString(R.string.tag_mgmt_s_412fe2))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.common_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteUserTagRuleCandidate = null }) {
                    Text(stringResource(R.string.common_cancel))
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

    if (showUserTagRuleEditor) {
        UserTagRuleEditorDialog(
            initial = editingUserTagRule,
            tags = tags,
            onDismiss = {
                showUserTagRuleEditor = false
                editingUserTagRule = null
            },
            onSave = { rule ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val dao = AppDatabaseHolder.db(appCtx).userTagRuleDao()
                        if (rule.id == 0L) {
                            dao.insert(rule)
                        } else {
                            dao.update(rule)
                        }
                        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                    }
                    showUserTagRuleEditor = false
                    editingUserTagRule = null
                    reloadAll()
                }
            }
        )
    }

    com.phantom.checker.ui.components.PhantomPageScaffold(
        title = stringResource(R.string.tag_mgmt_s_d5cbb6),
        onBack = onBack,
        topBarStyle = com.phantom.checker.ui.components.PhantomTopBarStyle.Medium,
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingTag = null
                    showEditor = true
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.tag_mgmt_s_a5b7d1))
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
                        text = stringResource(R.string.tag_mgmt_s_38f6a2),
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
                                text = stringResource(R.string.tag_mgmt_s_b49e1e),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    itemsIndexed(tags, key = { _, item -> "tag_${item.id}" }) { idx, tag ->
                        PhantomGlassCard(
                            glassState = glassState,
                            modifier = Modifier
                                .animateItem()
                                .phantomEnter(idx, key = "tag_${tag.id}")
                                .fillMaxWidth(),
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
                                                        text = stringResource(R.string.tag_mgmt_s_7183f3),
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
                                            val prev = tags.getOrNull(idx - 1) ?: return@IconButton
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    val db = AppDatabaseHolder.db(appCtx)
                                                    db.withTransaction {
                                                        val dao = db.tagDao()
                                                        dao.update(tag.copy(sortOrder = prev.sortOrder))
                                                        dao.update(prev.copy(sortOrder = tag.sortOrder))
                                                    }
                                                }
                                                reloadAll()
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.tag_mgmt_s_8a0c83))
                                    }
                                    IconButton(
                                        enabled = idx < tags.lastIndex,
                                        onClick = {
                                            val next = tags.getOrNull(idx + 1) ?: return@IconButton
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    val db = AppDatabaseHolder.db(appCtx)
                                                    db.withTransaction {
                                                        val dao = db.tagDao()
                                                        dao.update(tag.copy(sortOrder = next.sortOrder))
                                                        dao.update(next.copy(sortOrder = tag.sortOrder))
                                                    }
                                                }
                                                reloadAll()
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.tag_mgmt_s_05c46f))
                                    }
                                    IconButton(onClick = {
                                        editingTag = tag
                                        showEditor = true
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.common_edit))
                                    }
                                    IconButton(onClick = { deleteCandidate = tag }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.common_delete),
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
                                text = stringResource(R.string.tag_mgmt_s_46cd20),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.tag_mgmt_s_374659),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { showAddAppCatDialog = true },
                            enabled = tags.isNotEmpty()
                        ) {
                            Text(stringResource(R.string.tag_mgmt_s_606c09))
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
                                text = stringResource(R.string.tag_mgmt_s_d1759a),
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
                                            text = stringResource(R.string.tag_mgmt_s_e036fd, assignedTag?.name ?: resCtx.getString(R.string.tag_mgmt_s_1a24b4)),
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
                                                    com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                                                }
                                                reloadAll()
                                            }
                                        }
                                    )
                                    IconButton(onClick = { deleteAppCatCandidate = rule }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.notes_s_a287b5),
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
                                text = stringResource(R.string.tag_mgmt_s_1b4963),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.tag_mgmt_s_caddc0),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = { showAddAiRuleDialog = true }) {
                            Text(stringResource(R.string.tag_mgmt_s_606c09))
                        }
                    }
                }

                if (aiRules.count { it.isEnabled } > 20) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.warningContainer
                        ) {
                            Text(
                                text = stringResource(R.string.tag_mgmt_s_1e07ca),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onWarningContainer,
                                modifier = Modifier.padding(12.dp)
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
                                text = stringResource(R.string.tag_mgmt_s_1f7412),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(aiRules, key = { "airule_${it.id}" }) { rule ->
                        val targetTag = rule.targetTagId?.let { tagById[it] }
                        val kindLabel = if (rule.kind == AiRuleEntity.KIND_WHITELIST) {
                            stringResource(R.string.tag_mgmt_s_9bac9f)
                        } else {
                            stringResource(R.string.tag_mgmt_s_ee0f66, targetTag?.name ?: resCtx.getString(R.string.common_auto))
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
                                            rule.sender?.takeIf { it.isNotBlank() }?.let { resCtx.getString(R.string.common_s_c9cc80, it) },
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
                                                    com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                                                }
                                                reloadAll()
                                            }
                                        }
                                    )
                                    IconButton(onClick = { deleteAiRuleCandidate = rule }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.tag_mgmt_s_1a5c2f),
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
                                text = stringResource(R.string.tag_mgmt_s_2f137b),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.tag_mgmt_s_1bbe95),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    editingUserTagRule = null
                                    showUserTagRuleEditor = true
                                },
                                enabled = tags.isNotEmpty()
                            ) {
                                Text(stringResource(R.string.tag_mgmt_s_606c09))
                            }
                            OutlinedButton(onClick = { showTestTagRulesDialog = true }) {
                                Text(stringResource(R.string.tag_mgmt_s_9a0c6d))
                            }
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
                                text = stringResource(R.string.tag_mgmt_s_66e18a),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(userTagRules, key = { "utagrule_${it.id}" }) { rule ->
                        val targetTag = tagById[rule.targetTagId]
                        val sourceLabel = if (rule.source == "chat") stringResource(R.string.tag_mgmt_s_033df7) else stringResource(R.string.tag_mgmt_s_816d67)
                        val condSummary = TagRuleEngine.formatRuleConditions(rule)
                        val regexErr = remember(rule.contentRegex) {
                            rule.contentRegex?.trim()?.takeIf { it.isNotEmpty() }?.let {
                                TagRuleEngine.validateRegexPattern(it)
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
                                                text = stringResource(R.string.tag_mgmt_s_8d5a4e, targetTag?.name ?: resCtx.getString(R.string.tag_mgmt_s_7359b3, rule.targetTagId)),
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
                                        text = stringResource(R.string.tag_mgmt_s_2c4da9, condSummary),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (regexErr != null) {
                                        Text(
                                            text = "⚠ " + stringResource(
                                                R.string.tag_regex_error_line,
                                                when (regexErr) {
                                                    "pattern_too_long" -> stringResource(R.string.tag_regex_err_too_long)
                                                    "unsafe_complexity" -> stringResource(R.string.tag_regex_err_unsafe)
                                                    else -> stringResource(R.string.tag_regex_err_invalid)
                                                }
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.warning
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FluidSwitch(
                                        checked = rule.isEnabled,
                                        onCheckedChange = { enabled ->
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    AppDatabaseHolder.db(appCtx).userTagRuleDao()
                                                        .update(rule.copy(isEnabled = enabled))
                                                    com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                                                }
                                                reloadAll()
                                            }
                                        }
                                    )
                                    IconButton(onClick = {
                                        editingUserTagRule = rule
                                        showUserTagRuleEditor = true
                                    }) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = stringResource(R.string.common_edit)
                                        )
                                    }
                                    IconButton(onClick = { deleteUserTagRuleCandidate = rule }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.tag_mgmt_s_bdf1ab),
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
        title = { Text(if (initial == null) stringResource(R.string.tag_mgmt_s_a5b7d1) else stringResource(R.string.tag_mgmt_s_f71e52)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.tag_mgmt_s_640e5f)) },
                    placeholder = { Text(stringResource(R.string.tag_mgmt_s_08abb9)) }
                )
                OutlinedTextField(
                    value = aiHint,
                    onValueChange = { aiHint = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    label = { Text(stringResource(R.string.tag_mgmt_s_644824)) },
                    placeholder = { Text(stringResource(R.string.tag_mgmt_s_3be582)) }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tag_mgmt_s_19dc94),
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
                        text = stringResource(R.string.tag_mgmt_s_0c7cc2),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    FluidSwitch(
                        checked = isAppCategory,
                        onCheckedChange = { isAppCategory = it }
                    )
                }
                Text(stringResource(R.string.tag_mgmt_s_24f7c7), style = MaterialTheme.typography.labelMedium)
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
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
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
        title = { Text(stringResource(R.string.tag_mgmt_s_44ac50)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.tag_mgmt_s_7a1193), style = MaterialTheme.typography.labelMedium)
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
                Text(stringResource(R.string.tag_mgmt_s_0ff951), style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.tag_mgmt_s_670721)) }
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
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
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
        title = { Text(stringResource(R.string.tag_mgmt_s_399ccd)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = kind == AiRuleEntity.KIND_WHITELIST,
                        onClick = { kind = AiRuleEntity.KIND_WHITELIST },
                        label = { Text(stringResource(R.string.tag_mgmt_s_9bac9f)) }
                    )
                    FilterChip(
                        selected = kind == AiRuleEntity.KIND_TAG,
                        onClick = { kind = AiRuleEntity.KIND_TAG },
                        label = { Text(stringResource(R.string.tag_mgmt_s_089bf9)) }
                    )
                }
                OutlinedTextField(
                    value = ruleText,
                    onValueChange = { ruleText = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                    label = { Text(stringResource(R.string.tag_mgmt_s_b76d64)) },
                    placeholder = {
                        Text(
                            if (kind == AiRuleEntity.KIND_WHITELIST) {
                                stringResource(R.string.tag_mgmt_s_82c27d)
                            } else {
                                stringResource(R.string.tag_mgmt_s_8c75f6)
                            }
                        )
                    }
                )
                if (kind == AiRuleEntity.KIND_TAG && tags.isNotEmpty()) {
                    Text(stringResource(R.string.tag_mgmt_s_b62d5d), style = MaterialTheme.typography.labelMedium)
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
                Text(stringResource(R.string.common_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
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
    val resCtx = LocalContext.current
    var sampleAppLabel by remember { mutableStateOf(resCtx.getString(R.string.tag_mgmt_s_b2a7d8)) }
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
        title = { Text(stringResource(R.string.tag_mgmt_s_b2c72d)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = sampleAppLabel,
                    onValueChange = { sampleAppLabel = it },
                    label = { Text(stringResource(R.string.tag_mgmt_s_b120d8)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = samplePackage,
                    onValueChange = { samplePackage = it },
                    label = { Text(stringResource(R.string.tag_mgmt_s_b9ab43)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sampleSender,
                    onValueChange = { sampleSender = it },
                    label = { Text(stringResource(R.string.tag_mgmt_s_f12817)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sampleContent,
                    onValueChange = { sampleContent = it },
                    label = { Text(stringResource(R.string.tag_mgmt_s_e47aa1)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                if (rules.none { it.isEnabled }) {
                    Text(
                        text = stringResource(R.string.tag_mgmt_s_a4aa2c),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (matched.isEmpty()) {
                    Text(
                        text = stringResource(R.string.tag_mgmt_s_17bec2),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = stringResource(R.string.tag_mgmt_s_a9d5c1, matched.size),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    matched.forEach { r ->
                        val tName = tagById[r.targetTagId]?.name ?: resCtx.getString(R.string.tag_mgmt_s_7359b3, r.targetTagId)
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
                Text(stringResource(R.string.common_close))
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserTagRuleEditorDialog(
    initial: UserTagRuleEntity?,
    tags: List<TagEntity>,
    onDismiss: () -> Unit,
    onSave: (UserTagRuleEntity) -> Unit
) {
    var description by remember(initial) { mutableStateOf(initial?.description ?: "") }
    var targetTagId by remember(initial, tags) {
        mutableStateOf(initial?.targetTagId ?: tags.firstOrNull()?.id)
    }
    var appLabel by remember(initial) { mutableStateOf(initial?.appLabel ?: "") }
    var appPackage by remember(initial) { mutableStateOf(initial?.appPackage ?: "") }
    var senderPattern by remember(initial) { mutableStateOf(initial?.senderPattern ?: "") }
    var contentKeyword by remember(initial) { mutableStateOf(initial?.contentKeyword ?: "") }
    var contentRegex by remember(initial) { mutableStateOf(initial?.contentRegex ?: "") }

    val trimmedRegex = contentRegex.trim()
    val regexValidationError = remember(trimmedRegex) {
        if (trimmedRegex.isEmpty()) null else TagRuleEngine.validateRegexPattern(trimmedRegex)
    }
    val hasAnyCondition = appLabel.isNotBlank() ||
        appPackage.isNotBlank() ||
        senderPattern.isNotBlank() ||
        contentKeyword.isNotBlank() ||
        trimmedRegex.isNotEmpty() ||
        !initial?.semanticCategory.isNullOrBlank()

    val canSave = description.isNotBlank() &&
        targetTagId != null &&
        hasAnyCondition &&
        regexValidationError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) {
                    stringResource(R.string.tag_mgmt_s_606c09)
                } else {
                    stringResource(R.string.common_edit)
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.tag_mgmt_s_b76d64)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (tags.isNotEmpty()) {
                    Text(stringResource(R.string.tag_mgmt_s_b62d5d), style = MaterialTheme.typography.labelMedium)
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
                OutlinedTextField(
                    value = appLabel,
                    onValueChange = { appLabel = it },
                    label = { Text(stringResource(R.string.tag_mgmt_s_b120d8)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = appPackage,
                    onValueChange = { appPackage = it },
                    label = { Text(stringResource(R.string.tag_mgmt_s_b9ab43)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = senderPattern,
                    onValueChange = { senderPattern = it },
                    label = { Text(stringResource(R.string.tag_mgmt_s_f12817)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contentKeyword,
                    onValueChange = { contentKeyword = it },
                    label = { Text(stringResource(R.string.tag_mgmt_s_e47aa1)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contentRegex,
                    onValueChange = { contentRegex = it },
                    label = { Text(stringResource(R.string.chat_s_3af783, "Regex")) },
                    singleLine = true,
                    isError = regexValidationError != null,
                    supportingText = if (regexValidationError != null) {
                        {
                            Text(
                                text = "⚠ " + stringResource(
                                    R.string.tag_regex_error_line,
                                    when (regexValidationError) {
                                        "pattern_too_long" -> stringResource(R.string.tag_regex_err_too_long)
                                        "unsafe_complexity" -> stringResource(R.string.tag_regex_err_unsafe)
                                        else -> stringResource(R.string.tag_regex_err_invalid)
                                    }
                                ),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val tid = targetTagId ?: return@Button
                    val safeRegex = trimmedRegex.takeIf { it.isNotEmpty() }
                    if (safeRegex != null && TagRuleEngine.validateRegexPattern(safeRegex) != null) {
                        return@Button
                    }
                    onSave(
                        UserTagRuleEntity(
                            id = initial?.id ?: 0L,
                            description = description.trim(),
                            appPackage = appPackage.trim().takeIf { it.isNotEmpty() },
                            appLabel = appLabel.trim().takeIf { it.isNotEmpty() },
                            senderPattern = senderPattern.trim().takeIf { it.isNotEmpty() },
                            contentKeyword = contentKeyword.trim().takeIf { it.isNotEmpty() },
                            contentRegex = safeRegex,
                            semanticCategory = initial?.semanticCategory,
                            targetTagId = tid,
                            isEnabled = initial?.isEnabled ?: true,
                            priority = initial?.priority ?: 100,
                            source = initial?.source ?: "manual",
                            createdAt = initial?.createdAt ?: System.currentTimeMillis()
                        )
                    )
                },
                enabled = canSave
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

