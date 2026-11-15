package com.phantom.checker.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.phantom.checker.data.FilterRule
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.service.RetentionConfig
import com.phantom.checker.service.RetentionPolicy
import com.phantom.checker.util.MessageExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhantomCheckTheme {
                FilterSettingsScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var rules by remember { mutableStateOf(listOf<FilterRule>()) }
    var newKeyword by remember { mutableStateOf("") }
    var isAllApps by remember { mutableStateOf(true) }
    var targetPackageName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }
    var retentionDays by remember { mutableIntStateOf(RetentionConfig.getRetentionDays(appCtx)) }
    val currentThemeMode = ThemePreferences.getThemeMode(appCtx)

    LaunchedEffect(Unit) {
        isLoading = true
        rules = withContext(Dispatchers.IO) {
            AppDatabaseHolder.db(appCtx).filterDao().all()
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("過濾關鍵字管理") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    ctx.startActivity(Intent(ctx, AutoReplySettingsActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("SMS 自動回覆設定")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    if (isExporting) return@OutlinedButton
                    scope.launch {
                        isExporting = true
                        val result = MessageExporter.exportToDownloads(appCtx)
                        isExporting = false
                        result.onSuccess { fileName ->
                            snackbarHostState.showSnackbar("匯出成功：$fileName")
                        }.onFailure { err ->
                            val reason = err.localizedMessage?.takeIf { it.isNotBlank() }
                                ?: err.javaClass.simpleName
                            snackbarHostState.showSnackbar("匯出失敗：$reason")
                        }
                    }
                },
                enabled = !isExporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isExporting) "匯出中…" else "匯出訊息 CSV")
            }

            Spacer(Modifier.height(10.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "外觀主題",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = currentThemeMode == mode,
                                onClick = { ThemePreferences.setThemeMode(appCtx, mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "訊息保留天數",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "超過保留天數的舊訊息將每日自動清理（詐騙警示記錄將永久保留）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RetentionConfig.OPTIONS.forEach { days ->
                            FilterChip(
                                selected = retentionDays == days,
                                onClick = {
                                    retentionDays = days
                                    RetentionConfig.setRetentionDays(appCtx, days)
                                    RetentionPolicy.scheduleOrUpdate(appCtx)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "已設定訊息保留：${RetentionConfig.labelForDays(days)}"
                                        )
                                    }
                                },
                                label = { Text(RetentionConfig.labelForDays(days)) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "符合以下啟用關鍵字的通知將直接標記為已過濾，不送交 AI 分析。",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newKeyword,
                    onValueChange = { newKeyword = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("輸入過濾關鍵字…") }
                )
                Spacer(Modifier.width(8.dp))
                val trimmedKeyword = newKeyword.trim()
                val trimmedPkg = targetPackageName.trim()
                val resolvedPkg = if (isAllApps) null else trimmedPkg
                val hasValidScope = isAllApps || trimmedPkg.isNotEmpty()
                val canAdd = trimmedKeyword.isNotEmpty() &&
                    hasValidScope &&
                    rules.none {
                        it.keyword.equals(trimmedKeyword, ignoreCase = true) &&
                            it.packageName == resolvedPkg
                    }
                Button(
                    onClick = {
                        scope.launch {
                            rules = withContext(Dispatchers.IO) {
                                val dao = AppDatabaseHolder.db(appCtx).filterDao()
                                dao.insert(
                                    FilterRule(
                                        keyword = trimmedKeyword,
                                        isActive = true,
                                        packageName = resolvedPkg
                                    )
                                )
                                dao.all()
                            }
                            newKeyword = ""
                            targetPackageName = ""
                        }
                    },
                    enabled = canAdd
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "新增"
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("新增")
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = isAllApps,
                    onClick = { isAllApps = true },
                    label = { Text("全部 App") }
                )
                FilterChip(
                    selected = !isAllApps,
                    onClick = { isAllApps = false },
                    label = { Text("指定 App") }
                )
            }

            if (!isAllApps) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = targetPackageName,
                    onValueChange = { targetPackageName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("來源 App Package 名稱") },
                    placeholder = { Text("例如：com.android.mms") }
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "查找方式：手機「設定 → 應用程式 → 選擇 App → 應用程式資訊」即可查看套件名稱（如 com.android.mms）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Spacer(Modifier.height(12.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                rules.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "目前尚無過濾關鍵字",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            rules,
                            key = { "${it.id}_${it.keyword}_${it.packageName.orEmpty()}" }
                        ) { rule ->
                            FilterRuleCard(
                                rule = rule,
                                onToggleActive = { checked ->
                                    scope.launch {
                                        val updated = rule.copy(isActive = checked)
                                        withContext(Dispatchers.IO) {
                                            AppDatabaseHolder.db(appCtx).filterDao().update(updated)
                                        }
                                        rules = rules.map {
                                            if (it.id == rule.id) updated else it
                                        }
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            AppDatabaseHolder.db(appCtx).filterDao().delete(rule)
                                        }
                                        rules = rules.filterNot {
                                            it.id == rule.id
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRuleCard(
    rule: FilterRule,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.keyword,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (rule.isActive) MaterialTheme.colorScheme.onSurface else Color.Gray
                )
                Text(
                    text = if (rule.packageName.isNullOrBlank()) {
                        "適用範圍：全部 App"
                    } else {
                        "適用範圍：${rule.packageName}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Switch(
                checked = rule.isActive,
                onCheckedChange = onToggleActive
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "刪除關鍵字",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
