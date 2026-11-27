package com.phantom.checker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phantom.checker.R
import com.phantom.checker.data.GroupReportEntity
import com.phantom.checker.data.GroupReportTargetEntity
import com.phantom.checker.data.ObservedGroupAppRow
import com.phantom.checker.data.ObservedGroupRow
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.service.GroupReportEngine
import com.phantom.checker.util.PhantomPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * #509 群組報告設定與獨立報告檢視區塊：
 * - 兩層手動選擇（實測 DB 有群組訊息之 App → 該 App 內群組多選）
 * - 每目標獨立設定定時／滿 N 則（10..500）／並行觸發與雙輸出模式（獨立報告頁／存進記事，附詳細展開說明）
 * - 內建獨立報告檢視清單與全文對話框
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupReportSettingsSection(
    onShowMessage: (String) -> Unit
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val dao = remember(appCtx) { AppDatabaseHolder.db(appCtx).groupReportDao() }
    val targets by dao.observeTargets().collectAsState(initial = emptyList())
    val reports by dao.observeReports(limit = 50).collectAsState(initial = emptyList())

    var showPickerDialog by remember { mutableStateOf(false) }
    var editingTarget by remember { mutableStateOf<GroupReportTargetEntity?>(null) }
    var showReportsDialog by remember { mutableStateOf(false) }
    var showModeDetailInfo by remember { mutableStateOf(false) }
    var lastTriggeredAtMap by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.group_report_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.group_report_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showModeDetailInfo = !showModeDetailInfo }) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = stringResource(R.string.group_report_mode_info_btn),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        AnimatedVisibility(visible = showModeDetailInfo) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = phantomSurfaceAlpha(0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.group_report_mode_compare_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.group_report_mode_report_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.group_report_mode_note_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showPickerDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.group_report_add_target))
            }
            OutlinedButton(
                onClick = { showReportsDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.group_report_view_reports, reports.size))
            }
        }

        if (targets.isEmpty()) {
            Text(
                text = stringResource(R.string.group_report_empty_targets),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            targets.forEach { target ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = phantomSurfaceAlpha(0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingTarget = target }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${target.appLabel} · ${target.conversationKey}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val triggerParts = buildList {
                                    if (target.scheduleEnabled) {
                                        add(
                                            String.format(
                                                "%02d:%02d",
                                                target.scheduleHour,
                                                target.scheduleMinute
                                            )
                                        )
                                    }
                                    if (target.countEnabled) {
                                        add(ctx.getString(R.string.group_report_count_badge, target.countThreshold))
                                    }
                                }.joinToString(" / ")
                                val modeLabel = if (target.outputMode == GroupReportTargetEntity.OUTPUT_NOTE) {
                                    stringResource(R.string.group_report_mode_note)
                                } else {
                                    stringResource(R.string.group_report_mode_report)
                                }
                                Text(
                                    text = "$triggerParts · $modeLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    val nowMs = System.currentTimeMillis()
                                    val prevMs = lastTriggeredAtMap[target.id] ?: 0L
                                    if (nowMs - prevMs < 2_000L) return@IconButton
                                    lastTriggeredAtMap = lastTriggeredAtMap + (target.id to nowMs)
                                    GroupReportEngine.triggerImmediate(appCtx, target.id)
                                    onShowMessage(ctx.getString(R.string.group_report_triggered_toast))
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = stringResource(R.string.group_report_run_now),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Switch(
                                    checked = target.enabled,
                                    onCheckedChange = { checked ->
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                dao.updateTarget(target.copy(enabled = checked))
                                            }
                                            GroupReportEngine.scheduleOrCancel(appCtx)
                                        }
                                    }
                                )
                            }
                        }
                        if (target.lastStatus != GroupReportTargetEntity.STATUS_IDLE) {
                            val statusText = when (target.lastStatus) {
                                GroupReportTargetEntity.STATUS_OK ->
                                    ctx.getString(
                                        R.string.group_report_last_ok,
                                        PhantomPrefs.formatMessageTime(appCtx, target.lastReportAt, includeDate = true)
                                    )
                                GroupReportTargetEntity.STATUS_DEFERRED ->
                                    ctx.getString(R.string.group_report_last_deferred, target.lastError)
                                GroupReportTargetEntity.STATUS_FAILED ->
                                    ctx.getString(R.string.group_report_last_failed, target.lastError)
                                else -> ""
                            }
                            if (statusText.isNotBlank()) {
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (target.lastStatus == GroupReportTargetEntity.STATUS_FAILED) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPickerDialog) {
        GroupTargetPickerDialog(
            onDismiss = { showPickerDialog = false },
            onAdded = { count ->
                showPickerDialog = false
                GroupReportEngine.scheduleOrCancel(appCtx)
                onShowMessage(ctx.getString(R.string.group_report_added_toast, count))
            }
        )
    }

    editingTarget?.let { target ->
        GroupTargetEditDialog(
            target = target,
            onDismiss = { editingTarget = null },
            onSave = { updated ->
                scope.launch {
                    withContext(Dispatchers.IO) { dao.updateTarget(updated) }
                    GroupReportEngine.scheduleOrCancel(appCtx)
                    editingTarget = null
                    onShowMessage(ctx.getString(R.string.group_report_saved_toast))
                }
            },
            onDelete = {
                scope.launch {
                    withContext(Dispatchers.IO) { dao.deleteTargetById(target.id) }
                    GroupReportEngine.scheduleOrCancel(appCtx)
                    editingTarget = null
                    onShowMessage(ctx.getString(R.string.group_report_deleted_toast))
                }
            }
        )
    }

    if (showReportsDialog) {
        AlertDialog(
            onDismissRequest = { showReportsDialog = false },
            title = { Text(stringResource(R.string.group_report_viewer_title)) },
            text = {
                GroupReportsViewer(
                    reports = reports,
                    onDeleteReport = { reportId ->
                        scope.launch {
                            withContext(Dispatchers.IO) { dao.deleteReportById(reportId) }
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showReportsDialog = false }) {
                    Text(stringResource(R.string.common_close))
                }
            }
        )
    }
}

/**
 * #527 共用群組報告清單＋全文檢視 Composable（設定頁與訊息頁共用同一份，不得複製）。
 */
@Composable
fun GroupReportsViewer(
    reports: List<GroupReportEntity>,
    onDeleteReport: (Long) -> Unit,
    modifier: Modifier = Modifier,
    maxListHeight: androidx.compose.ui.unit.Dp = 380.dp
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    var viewingReport by remember { mutableStateOf<GroupReportEntity?>(null) }
    var pendingDeleteReport by remember { mutableStateOf<GroupReportEntity?>(null) }

    if (reports.isEmpty()) {
        Text(
            text = stringResource(R.string.group_report_viewer_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 12.dp)
        )
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = maxListHeight),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(reports, key = { it.id }) { report ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = phantomSurfaceAlpha(0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewingReport = report }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${report.appLabel} · ${report.conversationKey}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { pendingDeleteReport = report },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.common_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            text = "${PhantomPrefs.formatMessageTime(appCtx, report.createdAt, includeDate = true)} · ${ctx.getString(R.string.group_report_msg_count, report.messageCount)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = report.summaryText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    viewingReport?.let { report ->
        AlertDialog(
            onDismissRequest = { viewingReport = null },
            title = { Text("${report.appLabel} · ${report.conversationKey}") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${PhantomPrefs.formatMessageTime(appCtx, report.createdAt, includeDate = true)} · ${ctx.getString(R.string.group_report_msg_count, report.messageCount)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    PhantomMarkdownText(
                        markdown = report.summaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingReport = null }) {
                    Text(stringResource(R.string.common_close))
                }
            }
        )
    }

    pendingDeleteReport?.let { targetReport ->
        AlertDialog(
            onDismissRequest = { pendingDeleteReport = null },
            title = { Text(stringResource(R.string.common_confirm_delete)) },
            text = {
                Text(
                    text = "${targetReport.appLabel} · ${targetReport.conversationKey}" +
                        stringResource(R.string.memory_s_071ef4),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = targetReport.id
                        pendingDeleteReport = null
                        if (viewingReport?.id == id) {
                            viewingReport = null
                        }
                        onDeleteReport(id)
                    }
                ) {
                    Text(
                        text = stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteReport = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun GroupTargetPickerDialog(
    onDismiss: () -> Unit,
    onAdded: (Int) -> Unit
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val dao = remember(appCtx) { AppDatabaseHolder.db(appCtx).groupReportDao() }

    var apps by remember { mutableStateOf<List<ObservedGroupAppRow>>(emptyList()) }
    var selectedApp by remember { mutableStateOf<ObservedGroupAppRow?>(null) }
    var groups by remember { mutableStateOf<List<ObservedGroupRow>>(emptyList()) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var isAdding by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { dao.observedGroupApps() }
        loading = false
    }

    LaunchedEffect(selectedApp) {
        val app = selectedApp
        if (app != null) {
            groups = withContext(Dispatchers.IO) { dao.observedGroupsForApp(app.packageName) }
            selectedKeys = emptySet()
        } else {
            groups = emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (selectedApp == null) {
                    stringResource(R.string.group_report_pick_app_title)
                } else {
                    stringResource(R.string.group_report_pick_group_title, selectedApp!!.appLabel)
                }
            )
        },
        text = {
            when {
                loading -> {
                    Text(stringResource(R.string.group_report_loading))
                }
                apps.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.group_report_empty_db),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                selectedApp == null -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(apps, key = { it.packageName }) { app ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = phantomSurfaceAlpha(0.45f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedApp = app }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = app.appLabel.ifBlank { app.packageName },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = stringResource(R.string.group_report_group_count, app.groupCount),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { selectedApp = null }) {
                            Text(stringResource(R.string.group_report_back_to_apps))
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(groups, key = { it.conversationKey }) { grp ->
                                val checked = grp.conversationKey in selectedKeys
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = phantomSurfaceAlpha(0.45f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedKeys = if (checked) selectedKeys - grp.conversationKey else selectedKeys + grp.conversationKey
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { c ->
                                                selectedKeys = if (c) selectedKeys + grp.conversationKey else selectedKeys - grp.conversationKey
                                            }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = grp.conversationKey,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = stringResource(R.string.group_report_msg_count, grp.messageCount),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedApp != null && selectedKeys.isNotEmpty()) {
                Button(
                    enabled = !isAdding,
                    onClick = {
                        if (isAdding) return@Button
                        val app = selectedApp ?: return@Button
                        val keys = selectedKeys.toList()
                        isAdding = true
                        scope.launch {
                            try {
                                var added = 0
                                withContext(Dispatchers.IO) {
                                    keys.forEach { key ->
                                        val id = dao.insertTarget(
                                            GroupReportTargetEntity(
                                                packageName = app.packageName,
                                                appLabel = app.appLabel.ifBlank { app.packageName },
                                                conversationKey = key
                                            )
                                        )
                                        if (id > 0L) added++
                                    }
                                }
                                onAdded(added)
                            } finally {
                                isAdding = false
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.group_report_confirm_add, selectedKeys.size))
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !isAdding, onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupTargetEditDialog(
    target: GroupReportTargetEntity,
    onDismiss: () -> Unit,
    onSave: (GroupReportTargetEntity) -> Unit,
    onDelete: () -> Unit
) {
    var scheduleEnabled by remember(target.id) { mutableStateOf(target.scheduleEnabled) }
    var scheduleHour by remember(target.id) { mutableIntStateOf(target.scheduleHour) }
    var countEnabled by remember(target.id) { mutableStateOf(target.countEnabled) }
    var countThreshold by remember(target.id) { mutableIntStateOf(target.countThreshold) }
    var outputMode by remember(target.id) { mutableStateOf(target.outputMode) }
    var showModeExplain by remember(target.id) { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${target.appLabel} · ${target.conversationKey}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.group_report_trigger_schedule), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = scheduleEnabled, onCheckedChange = { scheduleEnabled = it })
                }
                if (scheduleEnabled) {
                    Text(
                        text = stringResource(R.string.group_report_schedule_hour, scheduleHour),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = scheduleHour.toFloat(),
                        onValueChange = { scheduleHour = it.roundToInt().coerceIn(0, 23) },
                        valueRange = 0f..23f,
                        steps = 22
                    )
                }

                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.group_report_trigger_count), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = countEnabled, onCheckedChange = { countEnabled = it })
                }
                if (countEnabled) {
                    Text(
                        text = stringResource(R.string.group_report_count_threshold, countThreshold),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = countThreshold.toFloat(),
                        onValueChange = { countThreshold = ((it / 10f).roundToInt() * 10).coerceIn(10, 500) },
                        valueRange = 10f..500f
                    )
                }
                if (scheduleEnabled && countEnabled) {
                    Text(
                        text = stringResource(R.string.group_report_both_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.group_report_output_mode), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    TextButton(onClick = { showModeExplain = !showModeExplain }) {
                        Text(stringResource(R.string.group_report_mode_info_btn))
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = outputMode == GroupReportTargetEntity.OUTPUT_REPORT_PAGE,
                        onClick = { outputMode = GroupReportTargetEntity.OUTPUT_REPORT_PAGE },
                        label = { Text(stringResource(R.string.group_report_mode_report)) }
                    )
                    FilterChip(
                        selected = outputMode == GroupReportTargetEntity.OUTPUT_NOTE,
                        onClick = { outputMode = GroupReportTargetEntity.OUTPUT_NOTE },
                        label = { Text(stringResource(R.string.group_report_mode_note)) }
                    )
                }
                AnimatedVisibility(visible = showModeExplain) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = phantomSurfaceAlpha(0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.group_report_mode_report_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.group_report_mode_note_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = scheduleEnabled || countEnabled,
                onClick = {
                    onSave(
                        target.copy(
                            scheduleEnabled = scheduleEnabled,
                            scheduleHour = scheduleHour,
                            scheduleMinute = target.scheduleMinute,
                            countEnabled = countEnabled,
                            countThreshold = countThreshold,
                            outputMode = outputMode
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        }
    )
}
