package com.phantom.checker.ui.partner

import com.phantom.checker.ui.components.pressableScale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.phantom.checker.data.GoalEntity
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.ui.PhantomGlassCard
import com.phantom.checker.util.GoalEngine
import com.phantom.checker.util.PhantomPrefs
import com.phantom.checker.util.PhantomSettingsBus
import com.phantom.checker.widget.GoalProgressWidgetProvider
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource

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
    val resCtx = LocalContext.current
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<GoalEngine.GoalDisplayItem>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var managingItem by remember { mutableStateOf<GoalEngine.GoalDisplayItem?>(null) }
    var pendingDeleteGoal by remember { mutableStateOf<GoalEntity?>(null) }
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
                    text = stringResource(R.string.goal_s_ccea0f, items.count { it.goal.status == GoalEntity.STATUS_ACTIVE }),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { showCreateDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(stringResource(R.string.goal_s_959895), style = MaterialTheme.typography.labelMedium)
                }
            }

            if (items.isEmpty()) {
                Text(
                    text = stringResource(R.string.goal_s_94865d),
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
                                    text = g.title + if (g.status == GoalEntity.STATUS_PAUSED) resCtx.getString(R.string.goal_s_2ee68c) else "",
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
                            val milestoneText = item.nextMilestoneDate.ifBlank {
                                resCtx.getString(R.string.goal_milestone_reached)
                            }
                            val summaryText = if (item.nextDueDate.isNotBlank()) {
                                resCtx.getString(
                                    R.string.goal_progress_summary_periods,
                                    item.completedCount,
                                    g.targetCount,
                                    item.streakDays,
                                    item.nextDueDate,
                                    milestoneText
                                )
                            } else {
                                resCtx.getString(
                                    R.string.goal_progress_summary_periods_no_due,
                                    item.completedCount,
                                    g.targetCount,
                                    item.streakDays,
                                    milestoneText
                                )
                            }
                            Text(
                                text = summaryText,
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
            title = { Text(stringResource(R.string.goal_s_6081ee)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text(stringResource(R.string.goal_s_71746f)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.goal_s_4f5f34), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1 to stringResource(R.string.chat_s_5e4715), 7 to stringResource(R.string.chat_s_d6847a), 14 to stringResource(R.string.chat_s_7d6acc), 30 to stringResource(R.string.chat_s_24aedc)).forEach { (days, label) ->
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
                    Text(stringResource(R.string.goal_s_cdf108))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    managingItem?.let { item ->
        val g = item.goal
        var selectedCadence by remember(g.id) { mutableStateOf(g.cadenceDays) }
        AlertDialog(
            onDismissRequest = { managingItem = null },
            title = { Text(stringResource(R.string.goal_s_62c91b, g.title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.goal_manage_summary_periods, item.completedCount, g.targetCount, item.streakDays),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1 to stringResource(R.string.chat_s_5e4715), 7 to stringResource(R.string.chat_s_d6847a), 14 to stringResource(R.string.chat_s_7d6acc), 30 to stringResource(R.string.chat_s_24aedc)).forEach { (days, label) ->
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
                                        val db = AppDatabaseHolder.db(appCtx)
                                        db.withTransaction {
                                            db.goalDao().updateGoal(g.copy(status = nextStatus))
                                        }
                                        runCatching { GoalProgressWidgetProvider.refreshNow(appCtx) }
                                    }
                                    PhantomSettingsBus.notifyChanged()
                                    reloadKey++
                                    onGoalsChanged()
                                }
                                managingItem = null
                            }
                        ) {
                            Text(if (g.status == GoalEntity.STATUS_PAUSED) stringResource(R.string.goal_s_70b03b) else stringResource(R.string.goal_s_61ac1b))
                        }
                        TextButton(
                            onClick = {
                                managingItem = null
                                pendingDeleteGoal = g
                            }
                        ) {
                            Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                db.withTransaction {
                                    db.goalDao().updateGoal(
                                        g.copy(cadenceDays = selectedCadence, missedPushStreak = 0)
                                    )
                                }
                                runCatching { GoalProgressWidgetProvider.refreshNow(appCtx) }
                            }
                            PhantomSettingsBus.notifyChanged()
                            reloadKey++
                            onGoalsChanged()
                        }
                        managingItem = null
                    }
                ) {
                    Text(stringResource(R.string.goal_s_c1cb88))
                }
            },
            dismissButton = {
                TextButton(onClick = { managingItem = null }) { Text(stringResource(R.string.common_close)) }
            }
        )
    }

    pendingDeleteGoal?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteGoal = null },
            title = { Text(stringResource(R.string.common_confirm_delete)) },
            text = { Text("${target.title}${stringResource(R.string.memory_s_071ef4)}") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        pendingDeleteGoal = null
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                db.withTransaction {
                                    val dao = db.goalDao()
                                    dao.deleteProgressForGoal(target.id)
                                    dao.deleteGoal(target.id)
                                }
                                runCatching { GoalProgressWidgetProvider.refreshNow(appCtx) }
                            }
                            PhantomSettingsBus.notifyChanged()
                            reloadKey++
                            onGoalsChanged()
                        }
                    }
                ) {
                    Text(stringResource(R.string.common_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteGoal = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
