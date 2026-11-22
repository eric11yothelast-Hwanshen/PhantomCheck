package com.phantom.checker.ui.memory

import com.phantom.checker.ui.components.phantomEnter
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.phantom.checker.data.WatchTopicEntity
import com.phantom.checker.service.AppDatabaseHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * #388 主動情報中樞 Phase A：關注主題清單最小管理對話框（記憶頁與設定頁共用）。
 * 支援：列表檢視、手動新增、編輯 queryHint、靜音/啟用、刪除、以及一鍵接受全部 ai_extracted 候選。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatchTopicManagerDialog(
    onDismiss: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val db = remember(appCtx) { AppDatabaseHolder.db(appCtx) }
    val scope = rememberCoroutineScope()
    val topics by remember(db) { db.watchTopicDao().observeAll() }.collectAsState(initial = emptyList())

    var showAddForm by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf("") }
    var newQueryHint by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf(WatchTopicEntity.TYPE_KEYWORD) }
    var editingTopic by remember { mutableStateOf<WatchTopicEntity?>(null) }
    var editHintText by remember { mutableStateOf("") }

    val pendingCandidatesCount = remember(topics) {
        topics.count {
            it.source == WatchTopicEntity.SOURCE_AI_EXTRACTED &&
                it.confidence == WatchTopicEntity.CONFIDENCE_LOW &&
                it.status == WatchTopicEntity.STATUS_ACTIVE
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("關注主題清單（${topics.size}）")
                TextButton(onClick = { showAddForm = !showAddForm }) {
                    Text(if (showAddForm) "收合新增" else "+ 新增")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (pendingCandidatesCount > 0) {
                    Button(
                        onClick = {
                            scope.launch {
                                val count = withContext(Dispatchers.IO) {
                                    db.watchTopicDao().acceptAllExtracted()
                                }
                                onShowMessage("已確認 $count 筆 AI 萃取候選主題")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("一鍵接受全部候選（$pendingCandidatesCount 筆）")
                    }
                }

                if (showAddForm) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = newLabel,
                                onValueChange = { newLabel = it.take(40) },
                                label = { Text("主題名稱（例如：Switch 2、某款遊戲）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = newQueryHint,
                                onValueChange = { newQueryHint = it.take(80) },
                                label = { Text("查詢關鍵字提示（可留空同主題名）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    WatchTopicEntity.TYPE_APP,
                                    WatchTopicEntity.TYPE_GAME,
                                    WatchTopicEntity.TYPE_WORK,
                                    WatchTopicEntity.TYPE_PERSON,
                                    WatchTopicEntity.TYPE_PROJECT,
                                    WatchTopicEntity.TYPE_REGION,
                                    WatchTopicEntity.TYPE_KEYWORD
                                ).forEach { t ->
                                    FilterChip(
                                        selected = newType == t,
                                        onClick = { newType = t },
                                        label = { Text(WatchTopicEntity.typeLabel(t), style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    val lbl = newLabel.trim()
                                    if (lbl.length < 2) {
                                        onShowMessage("主題名稱至少需 2 個字")
                                        return@Button
                                    }
                                    val hint = newQueryHint.trim().ifBlank { lbl }
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            db.watchTopicDao().insert(
                                                WatchTopicEntity(
                                                    topicType = newType,
                                                    label = lbl,
                                                    queryHint = hint,
                                                    source = WatchTopicEntity.SOURCE_MANUAL,
                                                    confidence = WatchTopicEntity.CONFIDENCE_MANUAL,
                                                    status = WatchTopicEntity.STATUS_ACTIVE
                                                )
                                            )
                                        }
                                        newLabel = ""
                                        newQueryHint = ""
                                        showAddForm = false
                                        onShowMessage("已新增關注主題「$lbl」")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("儲存關注主題")
                            }
                        }
                    }
                }

                if (topics.isEmpty()) {
                    Text(
                        text = "尚未建立關注主題。可手動新增，或開啟「主動情報」後由系統每日從近期非敏感素材自動萃取候選。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(topics, key = { _, it -> it.id }) { idx, topic ->
                            val isCandidate = topic.source == WatchTopicEntity.SOURCE_AI_EXTRACTED &&
                                topic.confidence == WatchTopicEntity.CONFIDENCE_LOW
                            val isMuted = topic.status == WatchTopicEntity.STATUS_MUTED
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .animateItem()
                                    .phantomEnter(idx, key = topic.id)
                                    .fillMaxWidth()
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
                                            text = "${topic.label} [${WatchTopicEntity.typeLabel(topic.topicType)}]",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = when {
                                                isMuted -> "已靜音"
                                                isCandidate -> "AI 候選"
                                                topic.confidence == WatchTopicEntity.CONFIDENCE_MANUAL -> "手動"
                                                else -> "已確認"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isMuted) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else if (isCandidate) {
                                                MaterialTheme.colorScheme.tertiary
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            }
                                        )
                                    }
                                    Text(
                                        text = "查詢詞：${topic.queryHint}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        if (isCandidate) {
                                            TextButton(
                                                onClick = {
                                                    scope.launch {
                                                        withContext(Dispatchers.IO) {
                                                            db.watchTopicDao().updateConfidence(topic.id, WatchTopicEntity.CONFIDENCE_CONFIRMED)
                                                        }
                                                        onShowMessage("已確認「${topic.label}」")
                                                    }
                                                }
                                            ) { Text("確認") }
                                        }
                                        TextButton(
                                            onClick = {
                                                editingTopic = topic
                                                editHintText = topic.queryHint
                                            }
                                        ) { Text("編輯查詢詞") }
                                        TextButton(
                                            onClick = {
                                                val nextStatus = if (isMuted) WatchTopicEntity.STATUS_ACTIVE else WatchTopicEntity.STATUS_MUTED
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        db.watchTopicDao().updateStatus(topic.id, nextStatus)
                                                    }
                                                    onShowMessage(if (isMuted) "已恢復關注「${topic.label}」" else "已靜音「${topic.label}」")
                                                }
                                            }
                                        ) { Text(if (isMuted) "恢復" else "靜音") }
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        db.watchTopicDao().deleteById(topic.id)
                                                    }
                                                    onShowMessage("已刪除「${topic.label}」")
                                                }
                                            }
                                        ) {
                                            Text("刪除", color = MaterialTheme.colorScheme.error)
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
            OutlinedButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )

    editingTopic?.let { target ->
        AlertDialog(
            onDismissRequest = { editingTopic = null },
            title = { Text("編輯查詢詞：${target.label}") },
            text = {
                OutlinedTextField(
                    value = editHintText,
                    onValueChange = { editHintText = it.take(80) },
                    label = { Text("查詢關鍵字提示（外送最小化：僅送此字串）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleaned = editHintText.trim().ifBlank { target.label }
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                db.watchTopicDao().updateQueryHint(target.id, cleaned)
                            }
                            editingTopic = null
                            onShowMessage("已更新查詢詞")
                        }
                    }
                ) { Text("儲存") }
            },
            dismissButton = {
                TextButton(onClick = { editingTopic = null }) { Text("取消") }
            }
        )
    }
}
