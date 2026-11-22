package com.phantom.checker.ui.partner

import com.phantom.checker.ui.components.pressableScale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.phantom.checker.data.GoalEntity
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.ui.PhantomGlassCard
import com.phantom.checker.util.GoalEngine
import com.phantom.checker.util.PhantomPrefs
import com.phantom.checker.util.PhantomSettingsBus
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * #379 夥伴分頁長程目標卡（進度條＋連續天數＋下一個里程碑＋手動建立／管理）：
 * - 無 API key 時手動建立與全部追蹤流程 100% 可用。
 * - 升頻僅能由使用者於此詳情對話框手動調整，絕不自動加密。
 */
@Composable
internal fun PartnerGoalsCard(
    glassState: HazeState,
    settingsVersion: Long,
    onGoalsChanged: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<GoalEngine.GoalDisplayItem>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var managingItem by remember { mutableStateOf<GoalEngine.GoalDisplayItem?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(settingsVersion, reloadKey) {
        if (!PhantomPrefs.isGoalTrackingEnabled(appCtx)) {
            items = emptyList()
            return@LaunchedEffect
        }
        items = GoalEngine.loadGoalDisplayItems(appCtx)
    }

    PhantomGlassCard(
        glassState = glassState,
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        contentPadding = 14.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎯 長程目標追蹤（${items.count { it.goal.status == GoalEntity.STATUS_ACTIVE }} 進行中）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { showCreateDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("+ 建立目標", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (items.isEmpty()) {
                Text(
                    text = "尚未建立長程目標。可點右上角手動建立（無需 API Key），或在對話中提及想養成的習慣時由確認卡建立。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                items.take(5).forEach { item ->
                    val g = item.goal
                    val targetProgress = (item.completedCount.toFloat() / g.targetCount.coerceAtLeast(1).toFloat())
                        .coerceIn(0f, 1f)
                    val progressFraction by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = targetProgress,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = 0.85f,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                        ),
                        label = "goalProgress"
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressableScale(0.98f, RoundedCornerShape(14.dp))
                            .clickable { managingItem = item }
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = g.title + if (g.status == GoalEntity.STATUS_PAUSED) "（已暫停）" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = GoalEngine.cadenceLabel(g.cadenceDays),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                            Text(
                                text = "進度 ${item.completedCount}/${g.targetCount} · 連續 ${item.streakDays} 期 · 下個里程碑 ${item.nextMilestoneDate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var titleInput by remember { mutableStateOf("") }
        var cadenceInput by remember { mutableStateOf(7) }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("建立長程目標") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("目標名稱（例如：跑步 30 分鐘）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("提醒頻率", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1 to "每天", 7 to "每週", 14 to "雙週", 30 to "每月").forEach { (days, label) ->
                            FilterChip(
                                selected = cadenceInput == days,
                                onClick = { cadenceInput = days },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = titleInput.trim()
                        if (clean.isNotEmpty()) {
                            scope.launch {
                                GoalEngine.createGoalAndScheduleNextTodo(appCtx, clean, cadenceInput)
                                reloadKey++
                                onGoalsChanged()
                            }
                            showCreateDialog = false
                        }
                    },
                    enabled = titleInput.trim().isNotEmpty()
                ) {
                    Text("建立並排程子待辦")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("取消") }
            }
        )
    }

    managingItem?.let { item ->
        val g = item.goal
        var selectedCadence by remember(g.id) { mutableStateOf(g.cadenceDays) }
        AlertDialog(
            onDismissRequest = { managingItem = null },
            title = { Text("管理目標：${g.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "已完成 ${item.completedCount}/${g.targetCount} 次（連續 ${item.streakDays} 期）。手動調整頻率（系統只會自動降頻，不會自動升頻）：",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1 to "每天", 7 to "每週", 14 to "雙週", 30 to "每月").forEach { (days, label) ->
                            FilterChip(
                                selected = selectedCadence == days,
                                onClick = { selectedCadence = days },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        val nextStatus = if (g.status == GoalEntity.STATUS_PAUSED) {
                                            GoalEntity.STATUS_ACTIVE
                                        } else {
                                            GoalEntity.STATUS_PAUSED
                                        }
                                        AppDatabaseHolder.db(appCtx).goalDao().updateGoal(g.copy(status = nextStatus))
                                    }
                                    PhantomSettingsBus.notifyChanged()
                                    reloadKey++
                                    onGoalsChanged()
                                }
                                managingItem = null
                            }
                        ) {
                            Text(if (g.status == GoalEntity.STATUS_PAUSED) "恢復追蹤" else "暫停追蹤")
                        }
                        TextButton(
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        val dao = AppDatabaseHolder.db(appCtx).goalDao()
                                        dao.deleteProgressForGoal(g.id)
                                        dao.deleteGoal(g.id)
                                    }
                                    PhantomSettingsBus.notifyChanged()
                                    reloadKey++
                                    onGoalsChanged()
                                }
                                managingItem = null
                            }
                        ) {
                            Text("刪除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                AppDatabaseHolder.db(appCtx).goalDao().updateGoal(
                                    g.copy(cadenceDays = selectedCadence, missedPushStreak = 0)
                                )
                            }
                            PhantomSettingsBus.notifyChanged()
                            reloadKey++
                            onGoalsChanged()
                        }
                        managingItem = null
                    }
                ) {
                    Text("儲存頻率")
                }
            },
            dismissButton = {
                TextButton(onClick = { managingItem = null }) { Text("關閉") }
            }
        )
    }
}
