package com.phantom.checker.ui

import com.phantom.checker.ui.components.PhantomMotion
import com.phantom.checker.ui.components.PhantomChevron
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
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
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource

class WhitelistManagementActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.phantom.checker.util.AppLocaleHelper.wrapContext(newBase))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhantomCheckTheme {
                WhitelistManagementScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WhitelistManagementScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val glassState = remember { HazeState() }

    var allowlistExpanded by remember { mutableStateOf(PhantomPrefs.isFilterSectionExpanded(appCtx, "allowlist", true)) }
    var mutedExpanded by remember { mutableStateOf(PhantomPrefs.isFilterSectionExpanded(appCtx, "muted", true)) }
    var tagsExpanded by remember { mutableStateOf(PhantomPrefs.isFilterSectionExpanded(appCtx, "tags", false)) }

    com.phantom.checker.ui.components.PhantomPageScaffold(
        title = stringResource(R.string.catalog_whitelist_management_title),
        onBack = onBack,
        topBarStyle = com.phantom.checker.ui.components.PhantomTopBarStyle.Medium,
        containerColor = MaterialTheme.colorScheme.background,
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. 白名單規則（可收合＋記憶偏好 #501）
                PhantomGlassCard(
                    glassState = glassState,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    allowlistExpanded = !allowlistExpanded
                                    PhantomPrefs.setFilterSectionExpanded(appCtx, "allowlist", allowlistExpanded)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.filters_sec_allowlist),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            PhantomChevron(expanded = allowlistExpanded)
                        }
                        AnimatedVisibility(
                            visible = allowlistExpanded,
                            enter = PhantomMotion.ExpandEnter,
                            exit = PhantomMotion.ExpandExit
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

                // 2. 靜音應用與專注（臨時 1 小時快捷＋倒數顯示 #501）
                PhantomGlassCard(
                    glassState = glassState,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    mutedExpanded = !mutedExpanded
                                    PhantomPrefs.setFilterSectionExpanded(appCtx, "muted", mutedExpanded)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.filters_sec_muted_apps),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            PhantomChevron(expanded = mutedExpanded)
                        }
                        AnimatedVisibility(
                            visible = mutedExpanded,
                            enter = PhantomMotion.ExpandEnter,
                            exit = PhantomMotion.ExpandExit
                        ) {
                            FiltersMutedAppsSection(
                                appCtx = appCtx,
                                onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                            )
                        }
                    }
                }

                // 3. 標籤管理（改名、刪除提醒、色標、依風險自動建議 #501）
                PhantomGlassCard(
                    glassState = glassState,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    tagsExpanded = !tagsExpanded
                                    PhantomPrefs.setFilterSectionExpanded(appCtx, "tags", tagsExpanded)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.filters_sec_tags),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            PhantomChevron(expanded = tagsExpanded)
                        }
                        AnimatedVisibility(
                            visible = tagsExpanded,
                            enter = PhantomMotion.ExpandEnter,
                            exit = PhantomMotion.ExpandExit
                        ) {
                            FiltersTagManagementSection(
                                appCtx = appCtx,
                                onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FiltersMutedAppsSection(
    appCtx: Context,
    onShowMessage: (String) -> Unit
) {
    var mutedApps by remember { mutableStateOf(PhantomPrefs.getMutedApps(appCtx).toList().sorted()) }
    var focusMuteUntil by remember { mutableStateOf(PhantomPrefs.getFocusMuteUntilMs(appCtx)) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(focusMuteUntil) {
        while (focusMuteUntil > System.currentTimeMillis()) {
            nowMs = System.currentTimeMillis()
            kotlinx.coroutines.delay(15_000L)
        }
        nowMs = System.currentTimeMillis()
    }

    val remainMin = ((focusMuteUntil - nowMs) / 60_000L).toInt().coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (focusMuteUntil > nowMs) {
                Text(
                    text = stringResource(R.string.filters_focus_mute_active, remainMin.coerceAtLeast(1)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = {
                    PhantomPrefs.setFocusMuteUntilMs(appCtx, 0L)
                    focusMuteUntil = 0L
                }) {
                    Text(stringResource(R.string.filters_focus_mute_cancel))
                }
            } else {
                OutlinedButton(
                    onClick = {
                        val until = System.currentTimeMillis() + 60L * 60L * 1000L
                        PhantomPrefs.setFocusMuteUntilMs(appCtx, until)
                        focusMuteUntil = until
                        nowMs = System.currentTimeMillis()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.filters_focus_mute_1h))
                }
            }
        }
        if (mutedApps.isEmpty()) {
            Text(
                text = stringResource(R.string.filters_muted_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            mutedApps.forEach { pkg ->
                val label = remember(pkg) {
                    runCatching {
                        val pm = appCtx.packageManager
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    }.getOrDefault(pkg)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        AppIconImage(packageName = pkg, contentDescription = label, size = 24.dp)
                        Column {
                            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    TextButton(onClick = {
                        PhantomPrefs.setAppMuted(appCtx, pkg, false)
                        mutedApps = PhantomPrefs.getMutedApps(appCtx).toList().sorted()
                        onShowMessage(label)
                    }) {
                        Text(stringResource(R.string.filters_unmute))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FiltersTagManagementSection(
    appCtx: Context,
    onShowMessage: (String) -> Unit
) {
    val resCtx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tags by remember { mutableStateOf<List<com.phantom.checker.data.TagEntity>>(emptyList()) }
    var renameTarget by remember { mutableStateOf<com.phantom.checker.data.TagEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<com.phantom.checker.data.TagEntity?>(null) }

    fun reloadTags() {
        scope.launch {
            tags = withContext(Dispatchers.IO) {
                runCatching { AppDatabaseHolder.db(appCtx).tagDao().all() }.getOrDefault(emptyList())
            }
        }
    }
    LaunchedEffect(Unit) { reloadTags() }

    val riskSuggestions = remember(resCtx) {
        listOf(
            resCtx.getString(R.string.filters_suggest_high_risk) to "#EF4444",
            resCtx.getString(R.string.filters_suggest_finance) to "#F59E0B",
            resCtx.getString(R.string.filters_suggest_vip) to "#10B981",
            resCtx.getString(R.string.filters_suggest_work) to "#3B82F6"
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.filters_tag_suggest_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            riskSuggestions.forEach { (name, hex) ->
                val exists = tags.any { it.name.equals(name, ignoreCase = true) }
                FilterChip(
                    selected = exists,
                    onClick = {
                        if (!exists) {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    AppDatabaseHolder.db(appCtx).tagDao().insert(
                                        com.phantom.checker.data.TagEntity(name = name, colorHex = hex)
                                    )
                                    NotificationCollectorService.invalidateRuleCaches()
                                }
                                reloadTags()
                                onShowMessage(resCtx.getString(R.string.filters_tag_added, name))
                            }
                        }
                    },
                    label = { Text(name, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        tags.forEach { tag ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tag.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row {
                    TextButton(onClick = {
                        renameTarget = tag
                        renameText = tag.name
                    }) {
                        Text(stringResource(R.string.filters_tag_rename))
                    }
                    IconButton(onClick = { deleteTarget = tag }) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.filters_tag_rename)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = renameText.trim()
                    if (trimmed.isNotEmpty()) {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                AppDatabaseHolder.db(appCtx).tagDao().update(target.copy(name = trimmed))
                                NotificationCollectorService.invalidateRuleCaches()
                            }
                            renameTarget = null
                            reloadTags()
                        }
                    }
                }) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.filters_sec_tags)) },
            text = { Text(stringResource(R.string.filters_tag_delete_warn, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            // R1 (#545) 共用交易：同交易刪除標籤、關聯與規則孤兒並清理規則快取
                            AppDatabaseHolder.db(appCtx).tagDao().deleteTagWithOrphans(target.id)
                            NotificationCollectorService.invalidateRuleCaches()
                        }
                        deleteTarget = null
                        reloadTags()
                    }
                }) { Text(stringResource(R.string.common_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhitelistManagementSection(
    appCtx: Context,
    modifier: Modifier = Modifier,
    onShowMessage: (String) -> Unit
) {
    val resCtx = LocalContext.current
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
                    AppDatabaseHolder.db(appCtx).aiRuleDao().allWhitelist()
                }
                rules = loaded
            } catch (e: Exception) {
                Log.e("PhantomWhitelist", "reloadRules failed", e)
                onShowMessage(resCtx.getString(R.string.whitelist_s_f7b950, e.message ?: resCtx.getString(R.string.common_unknown_error)))
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
                    text = stringResource(R.string.whitelist_s_55e1b5, rules.size),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.whitelist_s_1e4f1a),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = { showAddForm = !showAddForm },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.whitelist_s_c96b03), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (showAddForm) stringResource(R.string.chat_s_5d5815) else stringResource(R.string.common_add))
            }
        }

        AnimatedVisibility(
 visible = showAddForm,
 enter = PhantomMotion.ExpandEnter,
 exit = PhantomMotion.ExpandExit
 ) {
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
                            label = { Text(stringResource(R.string.whitelist_s_94ce69)) }
                        )
                        FilterChip(
                            selected = !addModeSender,
                            onClick = { addModeSender = false },
                            label = { Text(stringResource(R.string.whitelist_s_b7014a)) }
                        )
                    }
                    OutlinedTextField(
                        value = inputTarget,
                        onValueChange = { inputTarget = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(if (addModeSender) stringResource(R.string.whitelist_s_4eb4b5) else stringResource(R.string.whitelist_s_fed0a9)) },
                        placeholder = { Text(if (addModeSender) stringResource(R.string.whitelist_s_028582) else stringResource(R.string.whitelist_s_a9728a)) }
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = inputAllApps,
                            onClick = { inputAllApps = true },
                            label = { Text(stringResource(R.string.whitelist_s_3d8628)) }
                        )
                        FilterChip(
                            selected = !inputAllApps,
                            onClick = { inputAllApps = false },
                            label = { Text(stringResource(R.string.settings_s_cff87d)) }
                        )
                    }
                    if (!inputAllApps) {
                        OutlinedTextField(
                            value = inputPackage,
                            onValueChange = { inputPackage = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(stringResource(R.string.settings_s_180245)) },
                            placeholder = { Text(stringResource(R.string.whitelist_s_0eaddb)) }
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
                            text = stringResource(R.string.whitelist_s_1f42ac),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (matchingExisting != null && !matchingExisting.isEnabled) {
                        Text(
                            text = stringResource(R.string.whitelist_s_afb50c),
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
                                            val db = AppDatabaseHolder.db(appCtx)
                                            val ruleText = if (addModeSender) {
                                                "白名單寄件者：$trimmedTarget"
                                            } else {
                                                trimmedTarget
                                            }
                                            val res = db.withTransaction {
                                                WhitelistRuleHelper.upsertWhitelist(
                                                    dao = db.aiRuleDao(),
                                                    ruleText = ruleText,
                                                    sender = if (addModeSender) trimmedTarget else null,
                                                    packageName = resolvedPkg
                                                )
                                            }
                                            if (res.status == WhitelistRuleHelper.Status.INSERTED ||
                                                res.status == WhitelistRuleHelper.Status.REACTIVATED
                                            ) {
                                                NotificationCollectorService.invalidateRuleCaches()
                                            }
                                            res
                                        }
                                        if (upsertRes.isSuccess) {
                                            inputTarget = ""
                                            inputPackage = ""
                                            showAddForm = false
                                            reloadRules()
                                            onShowMessage("${upsertRes.message}：$trimmedTarget")
                                        } else {
                                            onShowMessage(resCtx.getString(R.string.whitelist_s_2c74a0, upsertRes.message))
                                        }
                                    } catch (e: Exception) {
                                        Log.e("PhantomWhitelist", "insert whitelist failed", e)
                                        onShowMessage(resCtx.getString(R.string.whitelist_s_ebd2b5, e.message ?: resCtx.getString(R.string.common_unknown_error)))
                                    }
                                }
                            }
                        ) {
                            Text(stringResource(R.string.common_confirm_create))
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
                    Text(stringResource(R.string.whitelist_s_0dc7d6), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(R.string.whitelist_s_2eb320),
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
                        resCtx.getString(R.string.whitelist_s_56370c)
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
                            PhantomChevron(
 expanded = isGroupExpanded,
                                contentDescription = if (isGroupExpanded) resCtx.getString(R.string.settings_s_52c158) else resCtx.getString(R.string.settings_s_6ce777)
                            )
                        }
                        AnimatedVisibility(
 visible = isGroupExpanded,
 enter = PhantomMotion.ExpandEnter,
 exit = PhantomMotion.ExpandExit
 ) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                groupRules.forEach { rule ->
                                    val isSenderMode = !rule.sender.isNullOrBlank()
                                    val displayTitle = rule.sender?.takeIf { it.isNotBlank() } ?: rule.ruleText
                                    val modeBadge = if (isSenderMode) resCtx.getString(R.string.whitelist_s_94ce69) else resCtx.getString(R.string.whitelist_s_ab2265)
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
                                                    text = resCtx.getString(R.string.whitelist_s_53458a, modeBadge, timeStr),
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
                                                            onShowMessage(resCtx.getString(R.string.whitelist_s_3bcea4, e.message ?: resCtx.getString(R.string.common_unknown_error)))
                                                        }
                                                    }
                                                }
                                            )
                                            IconButton(onClick = { deleteTarget = rule }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = resCtx.getString(R.string.whitelist_s_ad3473),
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
            title = { Text(stringResource(R.string.whitelist_s_e13109)) },
            text = { Text(stringResource(R.string.whitelist_s_1273d2, titleDisp)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = target
                        deleteTarget = null
                        scope.launch {
                            try {
                                val deletedOk = withContext(Dispatchers.IO) {
                                    val db = AppDatabaseHolder.db(appCtx)
                                    val ok = db.withTransaction {
                                        val dao = db.aiRuleDao()
                                        dao.deleteById(toDelete.id)
                                        dao.byId(toDelete.id) == null
                                    }
                                    NotificationCollectorService.invalidateRuleCaches()
                                    ok
                                }
                                if (deletedOk) {
                                    rules = rules.filterNot { it.id == toDelete.id }
                                    onShowMessage(resCtx.getString(R.string.whitelist_s_e3955c, titleDisp))
                                } else {
                                    onShowMessage(resCtx.getString(R.string.whitelist_s_6b3212))
                                }
                            } catch (e: Exception) {
                                Log.e("PhantomWhitelist", "delete whitelist failed id=${toDelete.id}", e)
                                onShowMessage(resCtx.getString(R.string.whitelist_s_e635c2, e.message ?: resCtx.getString(R.string.common_unknown_error)))
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
