package com.phantom.checker.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phantom.checker.data.AiRuleEntity
import com.phantom.checker.data.WhitelistRuleHelper
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.service.NotificationCollectorService
import com.phantom.checker.util.BackgroundLayer
import com.phantom.checker.util.PhantomPrefs
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WhitelistManagementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhantomCheckTheme {
                WhitelistManagementScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhitelistManagementScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val glassState = remember { HazeState() }

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 52.dp,
                title = {
                    Text(
                        text = "白名單管理",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        PhantomBackgroundBox(
            layer = BackgroundLayer.GLOBAL,
            glassState = glassState,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                PhantomGlassCard(
                    glassState = glassState,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WhitelistManagementSection(
                        appCtx = appCtx,
                        modifier = Modifier.fillMaxWidth(),
                        onShowMessage = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhitelistManagementSection(
    appCtx: Context,
    modifier: Modifier = Modifier,
    onShowMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var rules by remember { mutableStateOf<List<AiRuleEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var collapsedGroups by remember {
        mutableStateOf(PhantomPrefs.getWhitelistCollapsedGroups(appCtx).toSet())
    }
    var showAddForm by remember { mutableStateOf(false) }
    var addModeSender by remember { mutableStateOf(true) }
    var inputTarget by remember { mutableStateOf("") }
    var inputAllApps by remember { mutableStateOf(true) }
    var inputPackage by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<AiRuleEntity?>(null) }

    val timeFmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    fun reloadRules() {
        scope.launch {
            isLoading = true
            try {
                val loaded = withContext(Dispatchers.IO) {
                    AppDatabaseHolder.db(appCtx).aiRuleDao().all()
                        .filter { it.kind == AiRuleEntity.KIND_WHITELIST }
                }
                rules = loaded
            } catch (e: Exception) {
                Log.e("PhantomWhitelist", "reloadRules failed", e)
                onShowMessage("讀取白名單失敗：${e.message ?: "未知錯誤"}")
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { reloadRules() }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "白名單管理（${rules.size} 筆）",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "白名單內的寄件者或內容會跳過詐騙警示與情報查詢",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = { showAddForm = !showAddForm },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "新增白名單", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (showAddForm) "收起" else "新增")
            }
        }

        AnimatedVisibility(visible = showAddForm) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = addModeSender,
                            onClick = { addModeSender = true },
                            label = { Text("寄件者白名單") }
                        )
                        FilterChip(
                            selected = !addModeSender,
                            onClick = { addModeSender = false },
                            label = { Text("內容規則白名單") }
                        )
                    }
                    OutlinedTextField(
                        value = inputTarget,
                        onValueChange = { inputTarget = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(if (addModeSender) "寄件者名稱" else "白名單規則描述／關鍵字") },
                        placeholder = { Text(if (addModeSender) "例如：電信公司、自己的銀行" else "例如：官方帳單通知不是詐騙") }
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = inputAllApps,
                            onClick = { inputAllApps = true },
                            label = { Text("全部 App（全域）") }
                        )
                        FilterChip(
                            selected = !inputAllApps,
                            onClick = { inputAllApps = false },
                            label = { Text("指定 App") }
                        )
                    }
                    if (!inputAllApps) {
                        OutlinedTextField(
                            value = inputPackage,
                            onValueChange = { inputPackage = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("來源 App Package 名稱") },
                            placeholder = { Text("例如：jp.naver.line.android") }
                        )
                    }
                    val trimmedTarget = inputTarget.trim()
                    val resolvedPkg = if (inputAllApps) null else inputPackage.trim().takeIf { it.isNotEmpty() }
                    val matchingExisting = if (trimmedTarget.isNotEmpty()) {
                        rules.firstOrNull { r ->
                            val samePkg = (r.packageName?.trim() ?: "") == (resolvedPkg ?: "")
                            if (!samePkg) false
                            else if (addModeSender) r.sender?.trim()?.equals(trimmedTarget, ignoreCase = true) == true
                            else r.sender.isNullOrBlank() && r.ruleText.trim().equals(trimmedTarget, ignoreCase = true)
                        }
                    } else null
                    val isEnabledDuplicate = matchingExisting != null && matchingExisting.isEnabled
                    if (isEnabledDuplicate) {
                        Text(
                            text = "已存在相同的啟用白名單規則，請勿重複新增。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (matchingExisting != null && !matchingExisting.isEnabled) {
                        Text(
                            text = "已存在相同但已停用的規則，確認後將直接重新啟用。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    val canSubmit = trimmedTarget.isNotEmpty() &&
                        (inputAllApps || resolvedPkg != null) &&
                        !isEnabledDuplicate
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            enabled = canSubmit,
                            onClick = {
                                scope.launch {
                                    try {
                                        val upsertRes = withContext(Dispatchers.IO) {
                                            val dao = AppDatabaseHolder.db(appCtx).aiRuleDao()
                                            val ruleText = if (addModeSender) {
                                                "白名單寄件者：$trimmedTarget"
                                            } else {
                                                trimmedTarget
                                            }
                                            WhitelistRuleHelper.upsertWhitelist(
                                                dao = dao,
                                                ruleText = ruleText,
                                                sender = if (addModeSender) trimmedTarget else null,
                                                packageName = resolvedPkg,
                                                onCacheInvalidate = { NotificationCollectorService.invalidateRuleCaches() }
                                            )
                                        }
                                        if (upsertRes.isSuccess) {
                                            inputTarget = ""
                                            inputPackage = ""
                                            showAddForm = false
                                            reloadRules()
                                            onShowMessage("${upsertRes.message}：$trimmedTarget")
                                        } else {
                                            onShowMessage("${upsertRes.message}，請重試。")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("PhantomWhitelist", "insert whitelist failed", e)
                                        onShowMessage("新增白名單失敗：${e.message ?: "未知錯誤"}")
                                    }
                                }
                            }
                        ) {
                            Text("確認新增")
                        }
                    }
                }
            }
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else if (rules.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp)
                    )
                    Text("目前尚無白名單規則", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "可點擊右上角「新增」，或在訊息詳情／AI 助手中將信任的寄件者加入白名單。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val grouped = remember(rules) {
                rules.groupBy { it.packageName?.takeIf { p -> p.isNotBlank() } ?: "" }
                    .toSortedMap()
            }
            val expandedGroups = remember(grouped, collapsedGroups) {
                grouped.keys - collapsedGroups
            }
            grouped.forEach { (pkg, groupRules) ->
                val isGroupExpanded = pkg in expandedGroups
                val groupLabel = remember(pkg) {
                    if (pkg.isBlank()) {
                        "全域規則（全部 App）"
                    } else {
                        runCatching {
                            val pm = appCtx.packageManager
                            val ai = pm.getApplicationInfo(pkg, 0)
                            pm.getApplicationLabel(ai).toString()
                        }.getOrDefault(pkg)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val nextCollapsed = if (isGroupExpanded) {
                                        collapsedGroups + pkg
                                    } else {
                                        collapsedGroups - pkg
                                    }
                                    collapsedGroups = nextCollapsed
                                    PhantomPrefs.setWhitelistCollapsedGroups(appCtx, nextCollapsed)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (pkg.isNotBlank()) {
                                AppIconImage(packageName = pkg, contentDescription = groupLabel, size = 24.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Text(
                                text = "$groupLabel（${groupRules.size}）",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = if (isGroupExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isGroupExpanded) "收合" else "展開"
                            )
                        }
                        AnimatedVisibility(visible = isGroupExpanded) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                groupRules.forEach { rule ->
                                    val isSenderMode = !rule.sender.isNullOrBlank()
                                    val displayTitle = rule.sender?.takeIf { it.isNotBlank() } ?: rule.ruleText
                                    val modeBadge = if (isSenderMode) "寄件者白名單" else "內容規則"
                                    val timeStr = timeFmt.format(Date(rule.createdAt))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = displayTitle,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "$modeBadge · 建立於 $timeStr",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = rule.isEnabled,
                                                onCheckedChange = { checked ->
                                                    scope.launch {
                                                        try {
                                                            val updated = rule.copy(isEnabled = checked)
                                                            withContext(Dispatchers.IO) {
                                                                AppDatabaseHolder.db(appCtx).aiRuleDao().update(updated)
                                                                NotificationCollectorService.invalidateRuleCaches()
                                                            }
                                                            rules = rules.map { if (it.id == rule.id) updated else it }
                                                        } catch (e: Exception) {
                                                            Log.e("PhantomWhitelist", "toggle whitelist failed id=${rule.id}", e)
                                                            onShowMessage("更新狀態失敗：${e.message ?: "未知錯誤"}")
                                                        }
                                                    }
                                                }
                                            )
                                            IconButton(onClick = { deleteTarget = rule }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "刪除白名單",
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

    deleteTarget?.let { target ->
        val titleDisp = target.sender?.takeIf { it.isNotBlank() } ?: target.ruleText
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("移出白名單") },
            text = { Text("確定要刪除白名單規則「$titleDisp」嗎？刪除後將恢復正常詐騙風險檢查。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = target
                        deleteTarget = null
                        scope.launch {
                            try {
                                val deletedOk = withContext(Dispatchers.IO) {
                                    val dao = AppDatabaseHolder.db(appCtx).aiRuleDao()
                                    dao.deleteById(toDelete.id)
                                    val stillExists = dao.byId(toDelete.id) != null
                                    NotificationCollectorService.invalidateRuleCaches()
                                    !stillExists
                                }
                                if (deletedOk) {
                                    rules = rules.filterNot { it.id == toDelete.id }
                                    onShowMessage("已移出白名單：$titleDisp")
                                } else {
                                    onShowMessage("刪除失敗，請重試。")
                                }
                            } catch (e: Exception) {
                                Log.e("PhantomWhitelist", "delete whitelist failed id=${toDelete.id}", e)
                                onShowMessage("刪除失敗：${e.message ?: "未知錯誤"}")
                            }
                        }
                    }
                ) {
                    Text("刪除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消")
                }
            }
        )
    }
}
