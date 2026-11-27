// #301 來源：MainActivity.kt（TodoFilterMode、NotesTodoRow、TodoEditorDialog）
package com.phantom.checker.ui.todo

import android.provider.Settings
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phantom.checker.data.TodoEntity
import com.phantom.checker.ui.components.pressableScale
import com.phantom.checker.util.PhantomPrefs
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import com.phantom.checker.ui.*
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes

internal enum class TodoFilterMode(@StringRes val labelRes: Int) {
    ALL(R.string.common_all),
    PENDING(R.string.todo_filter_pending),
    DONE(R.string.chat_s_e99b48),
    OVERDUE(R.string.todo_filter_overdue);

    val label: String get() = com.phantom.checker.PhantomApplication.str(labelRes)
}


/**
 * #242 待辦事項單列（展開清單與收合精簡顯示共用）。
 * 到期資訊依智慧排序結果渲染：未逾期近者在上、已逾期下移、已完成殿後。
 */
@Composable
internal fun NotesTodoRow(
    todo: TodoEntity,
    now: Long,
    startOfToday: Long,
    endOfToday: Long,
    dayMs: Long,
    onEdit: (TodoEntity) -> Unit,
    onDelete: (TodoEntity) -> Unit,
    onComplete: (TodoEntity, Boolean) -> Unit,
    onOpenMessageDetail: (Long) -> Unit
) {
    val ctx = LocalContext.current
    val isOverdue = !todo.isDone && todo.dueAt < now
    val isDueToday = !todo.isDone && todo.dueAt in startOfToday..endOfToday && !isOverdue
    val overdueDays = if (isOverdue) {
        val zone = java.time.ZoneId.systemDefault()
        val todayDate = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val dueDate = java.time.Instant.ofEpochMilli(todo.dueAt).atZone(zone).toLocalDate()
        java.time.temporal.ChronoUnit.DAYS.between(dueDate, todayDate).toInt().coerceAtLeast(0)
    } else 0
    val priorityColor = when (todo.priority) {
        TodoEntity.PRIORITY_HIGH -> MaterialTheme.colorScheme.error
        TodoEntity.PRIORITY_LOW -> Color(0xFF26A69A)
        else -> MaterialTheme.colorScheme.warning
    }
    val overdueColor = MaterialTheme.colorScheme.warning

    val doneAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (todo.isDone) 0.62f else 1f,
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "todoDoneAlpha"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressableScale(0.98f)
            .graphicsLayer { alpha = doneAlpha }
            .clickable { onEdit(todo) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = todo.isDone,
            onCheckedChange = { checked -> onComplete(todo, checked) }
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(priorityColor)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = todo.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isOverdue) FontWeight.SemiBold else FontWeight.Normal,
                textDecoration = if (todo.isDone) {
                    androidx.compose.ui.text.style.TextDecoration.LineThrough
                } else {
                    null
                },
                color = when {
                    todo.isDone -> MaterialTheme.colorScheme.onSurfaceVariant
                    isOverdue -> overdueColor
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            val statusBadge = when {
                isOverdue -> if (overdueDays >= 1) stringResource(R.string.todo_s_b4ce0c, overdueDays) else stringResource(R.string.todo_s_e55871)
                isDueToday -> stringResource(R.string.todo_s_4c4355)
                else -> ""
            }
            val repeatTag = when (todo.repeatRule) {
                TodoEntity.REPEAT_DAILY -> stringResource(R.string.todo_s_709832)
                TodoEntity.REPEAT_WEEKLY -> stringResource(R.string.todo_s_a66138)
                else -> ""
            }
            Text(
                text = stringResource(R.string.todo_s_12173a, statusBadge, PhantomPrefs.formatMessageTime(ctx, todo.dueAt, includeDate = true), repeatTag),
                style = MaterialTheme.typography.labelSmall,
                color = if (isOverdue) overdueColor else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            if (todo.isDone && todo.completedAt != null) {
                Text(
                    text = stringResource(R.string.todo_s_c232c8, PhantomPrefs.formatMessageTime(ctx, todo.completedAt, includeDate = true)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (todo.notes.isNotBlank()) {
                Text(
                    text = todo.notes,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (todo.relatedMessageId != null) {
            IconButton(
                onClick = { onOpenMessageDetail(todo.relatedMessageId) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = stringResource(R.string.notes_s_7f4858),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        IconButton(onClick = { onEdit(todo) }) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(R.string.todo_s_f2ddea),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { onDelete(todo) }) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.chat_s_ae9e5f),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)

@Composable
internal fun TodoEditorDialog(
    initial: TodoEntity?,
    onDismiss: () -> Unit,
    onSave: (title: String, dueAt: Long, priority: Int, repeatRule: String, notes: String) -> Unit
) {
    val ctx = LocalContext.current
    var title by remember(initial) { mutableStateOf(initial?.title ?: "") }
    var dueAt by remember(initial) {
        mutableLongStateOf(initial?.dueAt ?: (System.currentTimeMillis() + 60L * 60L * 1000L))
    }
    var priority by remember(initial) { mutableIntStateOf(initial?.priority ?: TodoEntity.PRIORITY_MEDIUM) }
    var repeatRule by remember(initial) { mutableStateOf(initial?.repeatRule ?: TodoEntity.REPEAT_NONE) }
    var notes by remember(initial) { mutableStateOf(initial?.notes ?: "") }
    val dateFmt = remember { SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()) }

    val priorityOptions = listOf(
        TodoEntity.PRIORITY_LOW to stringResource(R.string.todo_s_b9ee25),
        TodoEntity.PRIORITY_MEDIUM to stringResource(R.string.todo_s_086907),
        TodoEntity.PRIORITY_HIGH to stringResource(R.string.todo_s_b096b3)
    )
    val repeatOptions = listOf(
        TodoEntity.REPEAT_NONE to stringResource(R.string.todo_s_cc34e6),
        TodoEntity.REPEAT_DAILY to stringResource(R.string.assistant_hub_s_08d65b),
        TodoEntity.REPEAT_WEEKLY to stringResource(R.string.chat_s_d6847a)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) stringResource(R.string.notes_s_75ab7d) else stringResource(R.string.todo_s_6dd489)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.todo_s_9bc9a3)) },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val cal = java.util.Calendar.getInstance().apply { timeInMillis = dueAt }
                            android.app.DatePickerDialog(
                                ctx,
                                { _, y, m, d ->
                                    cal.set(java.util.Calendar.YEAR, y)
                                    cal.set(java.util.Calendar.MONTH, m)
                                    cal.set(java.util.Calendar.DAY_OF_MONTH, d)
                                    dueAt = cal.timeInMillis
                                },
                                cal.get(java.util.Calendar.YEAR),
                                cal.get(java.util.Calendar.MONTH),
                                cal.get(java.util.Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(dateFmt.format(Date(dueAt)), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            val cal = java.util.Calendar.getInstance().apply { timeInMillis = dueAt }
                            android.app.TimePickerDialog(
                                ctx,
                                { _, h, min ->
                                    cal.set(java.util.Calendar.HOUR_OF_DAY, h)
                                    cal.set(java.util.Calendar.MINUTE, min)
                                    cal.set(java.util.Calendar.SECOND, 0)
                                    dueAt = cal.timeInMillis
                                },
                                cal.get(java.util.Calendar.HOUR_OF_DAY),
                                cal.get(java.util.Calendar.MINUTE),
                                PhantomPrefs.is24HourFormat(ctx)
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(PhantomPrefs.formatMessageTime(ctx, dueAt, includeDate = false), maxLines = 1)
                    }
                }
                Text(stringResource(R.string.todo_s_e3c782), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    priorityOptions.forEach { (pVal, pLabel) ->
                        FilterChip(
                            selected = priority == pVal,
                            onClick = { priority = pVal },
                            label = { Text(pLabel) }
                        )
                    }
                }
                Text(stringResource(R.string.todo_s_4fea89), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeatOptions.forEach { (rVal, rLabel) ->
                        FilterChip(
                            selected = repeatRule == rVal,
                            onClick = { repeatRule = rVal },
                            label = { Text(rLabel) }
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.todo_s_9bd8a5)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.trim().isNotEmpty(),
                onClick = { onSave(title.trim(), dueAt, priority, repeatRule, notes.trim()) }
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
