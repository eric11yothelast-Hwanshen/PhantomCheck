// #301 來源：MainActivity.kt（NotesTab、NoteCardItem、NoteEditorDialog、ManualMemoRuleDialog、TuneIcon）
package com.phantom.checker.ui.notes

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.room.withTransaction
import com.phantom.checker.data.AiSummaryEntity
import com.phantom.checker.data.AiTagCountRow
import com.phantom.checker.data.AppGroupRow
import com.phantom.checker.data.AppRiskRow
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.data.MessageTagCrossRef
import com.phantom.checker.data.MessageTagRow
import com.phantom.checker.data.NoteEntity
import com.phantom.checker.data.TagEntity
import com.phantom.checker.data.TagTotalCountRow
import com.phantom.checker.data.TodoEntity
import com.phantom.checker.network.AiClient
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.service.DailySummaryPolicy
import com.phantom.checker.service.WeeklySummaryPolicy
import com.phantom.checker.service.HeartbeatPolicy
import com.phantom.checker.service.NotificationCollectorService
import com.phantom.checker.service.ReminderScheduler
import com.phantom.checker.service.RetentionPolicy
import com.phantom.checker.service.StatsForegroundService
import com.phantom.checker.shizuku.ShizukuManager
import com.phantom.checker.ui.components.LiquidGlassTabBar
import com.phantom.checker.ui.components.LiquidGlassTabBarItem
import com.phantom.checker.ui.components.pressableScale
import com.phantom.checker.util.AppUpdateHelper
import com.phantom.checker.util.MessageExporter
import com.phantom.checker.util.MuteMode
import com.phantom.checker.util.PhantomPrefs
import com.phantom.checker.util.PhantomSettingsBus
import com.phantom.checker.util.ShareCardReportData
import com.phantom.checker.util.StatsShareCardGenerator
import com.phantom.checker.util.TabBarDisplayMode
import com.phantom.checker.util.UpdateCheckResult
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import com.phantom.checker.ui.*
import com.phantom.checker.ui.todo.*

private val TuneIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Tune",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 17f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(6f)
            verticalLineToRelative(-2f)
            horizontalLineTo(3f)
            close()
            moveTo(3f, 5f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(10f)
            verticalLineTo(5f)
            horizontalLineTo(3f)
            close()
            moveTo(13f, 21f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(8f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(-8f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(2f)
            close()
            moveTo(7f, 9f)
            verticalLineToRelative(2f)
            horizontalLineTo(3f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(2f)
            verticalLineTo(9f)
            horizontalLineTo(7f)
            close()
            moveTo(21f, 13f)
            verticalLineToRelative(-2f)
            horizontalLineTo(11f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(10f)
            close()
            moveTo(15f, 9f)
            horizontalLineToRelative(2f)
            verticalLineTo(7f)
            horizontalLineToRelative(4f)
            verticalLineTo(5f)
            horizontalLineToRelative(-4f)
            verticalLineTo(3f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(6f)
            close()
        }
    }.build()
}

/**
 * #245 記事卡片內容（供一般條列 / 往下露頂部 / 中央堆疊三種排列模式共用）。
 * 堆疊模式（overlapOpaque = true）啟用不透明深色底防止後卡文字透出，
 * 並以 contentMaxLines 限制內文行數讓焦點卡保持易讀。
 */

@Composable
internal fun NoteCardItem(
    note: NoteEntity,
    glassState: HazeState,
    contentMaxLines: Int = Int.MAX_VALUE,
    overlapOpaque: Boolean = false,
    onOpenMessageDetail: (Long) -> Unit,
    onEdit: (NoteEntity) -> Unit,
    onDelete: (NoteEntity) -> Unit,
    onTogglePin: (NoteEntity) -> Unit,
    onAutoNoteLongPress: (NoteEntity) -> Unit
) {
    val ctx = LocalContext.current
    val accentColor = remember(note.colorHex) {
        note.colorHex?.let { hex ->
            runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
        }
    }
    val isAuto = note.source == NoteEntity.SOURCE_AUTO
    val timeText = remember(note.updatedAt) {
        PhantomPrefs.formatMessageTime(ctx, note.updatedAt, includeDate = true)
    }
    PhantomGlassCard(
        glassState = glassState,
        modifier = Modifier
            .then(if (overlapOpaque) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
            .then(
                if (isAuto) {
                    Modifier.combinedClickable(
                        onClick = {
                            if (note.relatedMessageId != null) {
                                onOpenMessageDetail(note.relatedMessageId)
                            }
                        },
                        onLongClick = { onAutoNoteLongPress(note) }
                    )
                } else {
                    Modifier
                }
            ),
        cornerRadius = if (overlapOpaque) 20.dp else 18.dp,
        contentPadding = if (overlapOpaque) 12.dp else 14.dp,
        overlapOpaqueBoost = overlapOpaque
    ) {
        if (overlapOpaque) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (accentColor != null) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accentColor)
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 第一段：標題列（時間＋精簡操作鈕）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onTogglePin(note) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = if (note.pinned) "取消置頂" else "置頂",
                                    tint = if (note.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onEdit(note) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "編輯",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onDelete(note) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "刪除",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    // 第二段：單行記事預覽
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 第三段（底部 40dp 露出帶）：置頂／自動／來源 App 標籤列
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (note.pinned) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "置頂",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (isAuto) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "自動",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        val appLabel = note.relatedAppLabel
                        if (!appLabel.isNullOrBlank()) {
                            Text(
                                text = appLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        } else if (!note.pinned && !isAuto) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "記事",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                if (accentColor != null) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accentColor)
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (note.pinned) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "置頂",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (isAuto) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "自動",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        val appLabel = note.relatedAppLabel
                        if (!appLabel.isNullOrBlank()) {
                            Text(
                                text = appLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (note.pinned || isAuto || !note.relatedAppLabel.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = contentMaxLines,
                        overflow = if (contentMaxLines == Int.MAX_VALUE) {
                            TextOverflow.Clip
                        } else {
                            TextOverflow.Ellipsis
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { onTogglePin(note) }) {
                        Text(if (note.pinned) "取消置頂" else "置頂")
                    }
                    Row {
                        TextButton(onClick = { onEdit(note) }) {
                            Text("編輯")
                        }
                        TextButton(onClick = { onDelete(note) }) {
                            Text("刪除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun NotesTab(
    glassState: HazeState,
    initialEditTodoId: Long? = null,
    onConsumedEditTodoId: () -> Unit = {},
    onOpenMessageDetail: (Long) -> Unit,
    onShowMessage: (String) -> Unit,
    onReportBounds: (String, Rect) -> Unit = { _, _ -> }
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val db = remember(appCtx) { AppDatabaseHolder.db(appCtx) }

    val notes by remember(db) { db.noteDao().observeAll() }.collectAsState(initial = emptyList())
    val rawTodos by remember(db) { db.todoDao().observeAll() }.collectAsState(initial = emptyList())
    val memoRules by remember(db) { db.memoRuleDao().observeAll() }.collectAsState(initial = emptyList())
    val todos = remember(rawTodos) { ReminderScheduler.sortTodosSmart(rawTodos) }
    var todoFilter by remember { mutableStateOf(TodoFilterMode.ALL) }
    var showEditor by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<NoteEntity?>(null) }
    var showTodoEditor by remember { mutableStateOf(false) }
    var editingTodo by remember { mutableStateOf<TodoEntity?>(null) }
    var showMemoRulesPanel by remember { mutableStateOf(false) }
    var showManualMemoRuleDialog by remember { mutableStateOf(false) }
    var nlMemoRuleInput by remember { mutableStateOf("") }
    var isParsingMemoRule by remember { mutableStateOf(false) }
    var pendingNlMemoRule by remember { mutableStateOf<AiClient.ParsedMemoRule?>(null) }
    var neverMemoTargetMsg by remember { mutableStateOf<MessageEntity?>(null) }
    var neverMemoFallbackNote by remember { mutableStateOf<NoteEntity?>(null) }

    LaunchedEffect(initialEditTodoId, todos) {
        val targetId = initialEditTodoId ?: return@LaunchedEffect
        val found = todos.firstOrNull { it.id == targetId }
            ?: withContext(Dispatchers.IO) { db.todoDao().byId(targetId) }
        if (found != null) {
            editingTodo = found
            showTodoEditor = true
            onConsumedEditTodoId()
        }
    }

    if (showTodoEditor) {
        TodoEditorDialog(
            initial = editingTodo,
            onDismiss = {
                showTodoEditor = false
                editingTodo = null
            },
            onSave = { title, dueAt, priority, repeatRule, notesText ->
                val target = editingTodo
                showTodoEditor = false
                editingTodo = null
                scope.launch {
                    val savedId = withContext(Dispatchers.IO) {
                        if (target == null) {
                            db.todoDao().insert(
                                TodoEntity(
                                    title = title,
                                    dueAt = dueAt,
                                    isDone = false,
                                    createdAt = System.currentTimeMillis(),
                                    priority = priority,
                                    notes = notesText,
                                    repeatRule = repeatRule
                                )
                            )
                        } else {
                            db.todoDao().update(
                                target.copy(
                                    title = title,
                                    dueAt = dueAt,
                                    priority = priority,
                                    notes = notesText,
                                    repeatRule = repeatRule
                                )
                            )
                            target.id
                        }
                    }
                    if (target?.isDone != true) {
                        if (dueAt > System.currentTimeMillis()) {
                            ReminderScheduler.scheduleTodo(appCtx, savedId, dueAt)
                        } else {
                            ReminderScheduler.cancelTodo(appCtx, savedId)
                        }
                    }
                    com.phantom.checker.widget.ReminderWidgetProvider.updateAllIfChanged(appCtx)
                    onShowMessage(if (target == null) "已新增待辦事項" else "已更新待辦事項")
                }
            }
        )
    }

    if (showEditor) {
        NoteEditorDialog(
            initial = editingNote,
            onDismiss = {
                showEditor = false
                editingNote = null
            },
            onSave = { content, colorHex ->
                val target = editingNote
                showEditor = false
                editingNote = null
                scope.launch {
                    val now = System.currentTimeMillis()
                    withContext(Dispatchers.IO) {
                        if (target == null) {
                            db.noteDao().insert(
                                NoteEntity(
                                    content = content,
                                    colorHex = colorHex,
                                    source = NoteEntity.SOURCE_MANUAL,
                                    createdAt = now,
                                    updatedAt = now
                                )
                            )
                        } else {
                            db.noteDao().update(
                                target.copy(
                                    content = content,
                                    colorHex = colorHex,
                                    updatedAt = now
                                )
                            )
                        }
                        com.phantom.checker.widget.NotesWidgetProvider.updateAllIfChanged(appCtx)
                    }
                    onShowMessage(if (target == null) "已新增記事" else "已更新記事")
                }
            }
        )
    }

    val now = System.currentTimeMillis()
    val startOfToday = NotificationCollectorService.startOfTodayMillis()
    val dayMs = 24L * 60L * 60L * 1000L
    val endOfToday = startOfToday + dayMs - 1L
    val filteredTodos = remember(todos, todoFilter, now) {
        val sorted = ReminderScheduler.sortTodosSmart(todos, now)
        when (todoFilter) {
            TodoFilterMode.ALL -> sorted
            TodoFilterMode.PENDING -> sorted.filter { !it.isDone }
            TodoFilterMode.DONE -> sorted.filter { it.isDone }
            TodoFilterMode.OVERDUE -> sorted.filter { !it.isDone && it.dueAt < now }
        }
    }

    var showNotesSearch by remember { mutableStateOf(false) }
    var notesSearchQuery by remember { mutableStateOf("") }

    // 待辦資料操作回呼（展開／收合兩種顯示狀態共用，避免重複邏輯）
    val onEditTodo: (TodoEntity) -> Unit = { t ->
        editingTodo = t
        showTodoEditor = true
    }
    val onDeleteTodo: (TodoEntity) -> Unit = { t ->
        scope.launch {
            ReminderScheduler.cancelTodo(appCtx, t.id)
            withContext(Dispatchers.IO) {
                db.todoDao().deleteById(t.id)
            }
            com.phantom.checker.widget.ReminderWidgetProvider.updateAllIfChanged(appCtx)
            onShowMessage("已刪除待辦事項")
        }
    }
    val onCompleteTodo: (TodoEntity, Boolean) -> Unit = { t, checked ->
        scope.launch {
            val spawned = withContext(Dispatchers.IO) {
                ReminderScheduler.completeTodoWithRecurrence(appCtx, t, checked)
            }
            if (spawned != null) {
                onShowMessage("已完成並自動建立下一次重複待辦")
            }
        }
    }
    // 記事卡片內容操作回呼（三種排列模式共用）
    val onEditNote: (NoteEntity) -> Unit = { n ->
        editingNote = n
        showEditor = true
    }
    val onDeleteNote: (NoteEntity) -> Unit = { n ->
        scope.launch {
            withContext(Dispatchers.IO) {
                db.noteDao().deleteById(n.id)
                com.phantom.checker.widget.NotesWidgetProvider.updateAllIfChanged(appCtx)
            }
            onShowMessage("已刪除記事")
        }
    }
    val onTogglePinNote: (NoteEntity) -> Unit = { n ->
        scope.launch {
            withContext(Dispatchers.IO) {
                db.noteDao().update(
                    n.copy(
                        pinned = !n.pinned
                    )
                )
                com.phantom.checker.widget.NotesWidgetProvider.updateAllIfChanged(appCtx)
            }
        }
    }
    val onAutoNoteLongPress: (NoteEntity) -> Unit = { n ->
        val relId = n.relatedMessageId
        if (relId != null) {
            scope.launch {
                val src = withContext(Dispatchers.IO) {
                    db.messageDao().byId(relId)
                }
                if (src != null) {
                    neverMemoTargetMsg = src
                } else {
                    neverMemoFallbackNote = n
                }
            }
        } else {
            neverMemoFallbackNote = n
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxTodoExpandedHeight = maxHeight * 2f / 3f
        // 記事卡片排列模式（獨立於首頁，於設定頁調整後即時同步）；待辦收合狀態持久化
        val notesSettingsVersion by PhantomSettingsBus.version.collectAsState()
        val noteLayout = remember(notesSettingsVersion) { PhantomPrefs.getNoteCardLayoutMode(ctx) }
        val todoSectionCollapsed = remember { mutableStateOf(PhantomPrefs.isNotesTodoCollapsed(ctx)) }

        Column(modifier = Modifier.fillMaxSize()) {
            // 0. 搜尋膠囊列（待辦與記事共用入口）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PhantomSearchBarPill(
                    glassState = glassState,
                    query = notesSearchQuery,
                    placeholder = "搜尋待辦或備忘錄…",
                    onClick = { showNotesSearch = true }
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = { showMemoRulesPanel = !showMemoRulesPanel },
                        modifier = Modifier
                            .size(48.dp)
                            .onGloballyPositioned { coords ->
                                onReportBounds(OnboardingTargets.MEMO_RULES_AREA, coords.boundsInRoot())
                            }
                    ) {
                        Icon(
                            imageVector = TuneIcon,
                            contentDescription = "自動記事規則",
                            tint = if (showMemoRulesPanel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (memoRules.isNotEmpty()) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 4.dp)
                        ) {
                            Text(
                                text = if (memoRules.size > 99) "99+" else "${memoRules.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 1. 待辦事項區（與 AI 助手、桌面小工具同步；可收合以節省空間）
            val pendingCount = todos.count { !it.isDone }
            val overdueCount = todos.count { !it.isDone && it.dueAt < now }
            // 收合時的精簡顯示：距離系統時間最近、尚未逾期的未完成待辦；全部逾期時順延最早逾期那則
            val nextUpcomingTodo = todos.filter { !it.isDone && it.dueAt >= now }.minByOrNull { it.dueAt }
            val overdueFallbackTodo = if (nextUpcomingTodo == null) {
                todos.filter { !it.isDone }.minByOrNull { it.dueAt }
            } else {
                null
            }

            PhantomGlassCard(
                glassState = glassState,
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 14.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            todoSectionCollapsed.value = !todoSectionCollapsed.value
                            PhantomPrefs.setNotesTodoCollapsed(ctx, todoSectionCollapsed.value)
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "待辦事項（$pendingCount 未完成" +
                                (if (overdueCount > 0) " · $overdueCount 逾期" else "") + "）",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "與 AI 助手及桌面小工具同步",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            editingTodo = null
                            showTodoEditor = true
                        },
                        modifier = Modifier.onGloballyPositioned { coords ->
                            onReportBounds(OnboardingTargets.TODO_ADD, coords.boundsInRoot())
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新增待辦事項",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            todoSectionCollapsed.value = !todoSectionCollapsed.value
                            PhantomPrefs.setNotesTodoCollapsed(ctx, todoSectionCollapsed.value)
                        }
                    ) {
                        Icon(
                            imageVector = if (todoSectionCollapsed.value) {
                                Icons.Default.KeyboardArrowDown
                            } else {
                                Icons.Default.KeyboardArrowUp
                            },
                            contentDescription = if (todoSectionCollapsed.value) "展開待辦事項" else "收起待辦事項",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                // 展開狀態：篩選膠囊列 + 完整待辦清單（依智慧排序：未逾期近者在上、已逾期下移、已完成殿後）
                AnimatedVisibility(
                    visible = !todoSectionCollapsed.value,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    val todoListScrollState = key(todoFilter) { rememberScrollState() }
                    Column(modifier = Modifier.heightIn(max = maxTodoExpandedHeight)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TodoFilterMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = todoFilter == mode,
                                    onClick = { todoFilter = mode },
                                    label = {
                                        val badge = when (mode) {
                                            TodoFilterMode.ALL -> todos.size
                                            TodoFilterMode.PENDING -> pendingCount
                                            TodoFilterMode.DONE -> todos.count { it.isDone }
                                            TodoFilterMode.OVERDUE -> overdueCount
                                        }
                                        Text("${mode.label}($badge)", style = MaterialTheme.typography.labelSmall)
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(todoListScrollState)
                        ) {
                            if (filteredTodos.isEmpty()) {
                                Text(
                                    text = if (todos.isEmpty()) {
                                        "尚無待辦事項（點右上角 + 手動新增，或在 AI 助手對話中建立）"
                                    } else {
                                        "此篩選條件下無待辦事項"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                filteredTodos.forEachIndexed { idx, todo ->
                                    if (idx > 0) {
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                    NotesTodoRow(
                                        todo = todo,
                                        now = now,
                                        startOfToday = startOfToday,
                                        endOfToday = endOfToday,
                                        dayMs = dayMs,
                                        onEdit = onEditTodo,
                                        onDelete = onDeleteTodo,
                                        onComplete = onCompleteTodo,
                                        onOpenMessageDetail = onOpenMessageDetail
                                    )
                                }
                            }
                        }
                    }
                }
                // 收合狀態：只顯示最快到期的未完成待辦（或最少量的提示文字）
                AnimatedVisibility(
                    visible = todoSectionCollapsed.value,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    when {
                        nextUpcomingTodo != null -> NotesTodoRow(
                            todo = nextUpcomingTodo,
                            now = now,
                            startOfToday = startOfToday,
                            endOfToday = endOfToday,
                            dayMs = dayMs,
                            onEdit = onEditTodo,
                            onDelete = onDeleteTodo,
                            onComplete = onCompleteTodo,
                            onOpenMessageDetail = onOpenMessageDetail
                        )
                        overdueFallbackTodo != null -> NotesTodoRow(
                            todo = overdueFallbackTodo,
                            now = now,
                            startOfToday = startOfToday,
                            endOfToday = endOfToday,
                            dayMs = dayMs,
                            onEdit = onEditTodo,
                            onDelete = onDeleteTodo,
                            onComplete = onCompleteTodo,
                            onOpenMessageDetail = onOpenMessageDetail
                        )
                        pendingCount == 0 && todos.isNotEmpty() -> Text(
                            text = "所有待辦事項皆已完成",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> Text(
                            text = "尚無待辦事項（點上方 + 新增）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 2. 記事／備忘錄標題列 + 新增按鈕
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "記事／備忘錄（${notes.size}）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = {
                        editingNote = null
                        showEditor = true
                    },
                    modifier = Modifier.pressableScale()
                ) {
                    Text("新增記事")
                }
            }

            Spacer(Modifier.height(if (noteLayout == PhantomPrefs.NoteCardLayoutMode.CENTER) 4.dp else 8.dp))

            // 3. 記事內容：依排列模式（一般條列 / 往下露頂部 / 中央堆疊）填滿剩餘高度；
            //    待辦收起時此容器(weight(1f))自動向上延伸，釋放滑動視野
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (notes.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.Edit,
                        title = "尚無記事",
                        description = "點擊「新增記事」記錄備忘事項，或至設定頁開啟「自動備忘錄」。"
                    )
                } else {
                    when (noteLayout) {
                        PhantomPrefs.NoteCardLayoutMode.LIST -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 120.dp)
                            ) {
                                items(notes, key = { it.id }) { note ->
                                    NoteCardItem(
                                        note = note,
                                        glassState = glassState,
                                        onOpenMessageDetail = onOpenMessageDetail,
                                        onEdit = onEditNote,
                                        onDelete = onDeleteNote,
                                        onTogglePin = onTogglePinNote,
                                        onAutoNoteLongPress = onAutoNoteLongPress
                                    )
                                }
                            }
                        }
                        PhantomPrefs.NoteCardLayoutMode.CENTER -> {
                            // #270 中央堆疊：共用 CenterFocusCardStack（92dp 緊湊卡高、40dp 露出帶、單行預覽）
                            CenterFocusCardStack(
                                items = notes,
                                key = { it.id },
                                modifier = Modifier.fillMaxSize(),
                                edgeGap = 6.dp
                            ) { note ->
                                NoteCardItem(
                                    note = note,
                                    glassState = glassState,
                                    contentMaxLines = 1,
                                    overlapOpaque = true,
                                    onOpenMessageDetail = onOpenMessageDetail,
                                    onEdit = onEditNote,
                                    onDelete = onDeleteNote,
                                    onTogglePin = onTogglePinNote,
                                    onAutoNoteLongPress = onAutoNoteLongPress
                                )
                            }
                        }
                    }
                }
            }
        }

    // 自動記事規則浮動面板：位於 BoxWithConstraints 作用域內，以取得 maxWidth/maxHeight 與 BoxScope.align


        BackHandler(enabled = showMemoRulesPanel) {
            showMemoRulesPanel = false
        }

        if (showMemoRulesPanel) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showMemoRulesPanel = false }
                    )
            )
        }

        val panelWidth = minOf(340.dp, maxWidth * 0.9f)
        val panelMaxHeight = maxHeight * 0.6f
        AnimatedVisibility(
            visible = showMemoRulesPanel,
            enter = expandHorizontally(
                expandFrom = Alignment.End,
                animationSpec = spring(stiffness = 380f, dampingRatio = 0.85f)
            ) + expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = spring(stiffness = 380f, dampingRatio = 0.85f)
            ) + fadeIn(animationSpec = tween(250)),
            exit = shrinkHorizontally(
                shrinkTowards = Alignment.End,
                animationSpec = tween(200)
            ) + shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(200)
            ) + fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 52.dp)
        ) {
            PhantomFloatingSurface(
                modifier = Modifier
                    .width(panelWidth)
                    .heightIn(max = panelMaxHeight),
                cornerRadius = 20.dp,
                contentPadding = 14.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "自動記事規則 (${memoRules.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = { showManualMemoRuleDialog = true },
                            modifier = Modifier.pressableScale()
                        ) {
                            Text("手動新增")
                        }
                    }
                    Text(
                        text = "AI 啟用時以語意判斷相似訊息（含關鍵字條件）；AI 未啟用時不產生自動記事",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "自然語言新增規則",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = nlMemoRuleInput,
                                onValueChange = { nlMemoRuleInput = it },
                                placeholder = { Text("例如：Gogolook 驗證碼不要加入待辦") },
                                singleLine = true,
                                enabled = !isParsingMemoRule,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val text = nlMemoRuleInput.trim()
                                    if (text.isEmpty() || isParsingMemoRule) return@Button
                                    isParsingMemoRule = true
                                    scope.launch {
                                        val result = AiClient.parseMemoRule(appCtx, text)
                                        isParsingMemoRule = false
                                        result.fold(
                                            onSuccess = { parsed ->
                                                pendingNlMemoRule = parsed
                                            },
                                            onFailure = { err ->
                                                onShowMessage(err.message ?: "解析規則失敗")
                                            }
                                        )
                                    }
                                },
                                enabled = nlMemoRuleInput.isNotBlank() && !isParsingMemoRule
                            ) {
                                if (isParsingMemoRule) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("送出")
                                }
                            }
                        }
                    }
                    if (memoRules.isEmpty()) {
                        Text(
                            text = "尚無自動記事規則。可於上方用自然語言或「手動新增」設定哪些 App、寄件者或關鍵字要一律記事、一律待辦或永不加入。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(memoRules, key = { "memo_rule_${it.id}" }) { rule ->
                                val (badgeContainer, badgeContent, badgeText) = when (rule.action) {
                                    com.phantom.checker.data.MemoRuleEntity.ACTION_NOTE -> Triple(
                                        MaterialTheme.colorScheme.tertiaryContainer,
                                        MaterialTheme.colorScheme.onTertiaryContainer,
                                        "一律記事"
                                    )
                                    com.phantom.checker.data.MemoRuleEntity.ACTION_TODO -> Triple(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.onPrimaryContainer,
                                        "一律待辦"
                                    )
                                    else -> Triple(
                                        MaterialTheme.colorScheme.errorContainer,
                                        MaterialTheme.colorScheme.onErrorContainer,
                                        "永不加入"
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Surface(color = badgeContainer, shape = RoundedCornerShape(6.dp)) {
                                                    Text(
                                                        text = badgeText,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = badgeContent,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Text(
                                                    text = com.phantom.checker.util.MemoRuleEngine.formatConditionSummary(rule),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = rule.isEnabled,
                                            onCheckedChange = { enabled ->
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        db.memoRuleDao().update(rule.copy(isEnabled = enabled))
                                                        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                                                    }
                                                }
                                            }
                                        )
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        db.memoRuleDao().deleteById(rule.id)
                                                        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                                                    }
                                                    onShowMessage("已刪除自動記事規則")
                                                }
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "刪除規則",
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

    if (showManualMemoRuleDialog) {
        ManualMemoRuleDialog(
            onDismiss = { showManualMemoRuleDialog = false },
            onSave = { pkg, sender, keyword, action ->
                showManualMemoRuleDialog = false
                scope.launch {
                    withContext(Dispatchers.IO) {
                        db.memoRuleDao().insert(
                            com.phantom.checker.data.MemoRuleEntity(
                                packageName = pkg,
                                sender = sender,
                                keyword = keyword,
                                action = action,
                                isEnabled = true,
                                createdAt = System.currentTimeMillis(),
                                source = com.phantom.checker.data.MemoRuleEntity.SOURCE_MANUAL
                            )
                        )
                        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                    }
                    onShowMessage("已新增自動記事規則")
                }
            }
        )
    }

    pendingNlMemoRule?.let { parsed ->
        val summary = buildString {
            val parts = listOfNotNull(
                parsed.app?.let { "App $it" },
                parsed.sender?.let { "寄件者 $it" },
                parsed.keyword?.let { "含「$it」" }
            )
            append(parts.joinToString("／"))
            append(" → ")
            append(com.phantom.checker.util.MemoRuleEngine.actionLabel(parsed.memoAction))
        }
        AlertDialog(
            onDismissRequest = { pendingNlMemoRule = null },
            title = { Text("確認新增自動記事規則") },
            text = { Text(summary) },
            confirmButton = {
                Button(onClick = {
                    val rule = parsed
                    pendingNlMemoRule = null
                    nlMemoRuleInput = ""
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            db.memoRuleDao().insert(
                                com.phantom.checker.data.MemoRuleEntity(
                                    packageName = rule.app,
                                    sender = rule.sender,
                                    keyword = rule.keyword,
                                    action = rule.memoAction,
                                    isEnabled = true,
                                    createdAt = System.currentTimeMillis(),
                                    source = com.phantom.checker.data.MemoRuleEntity.SOURCE_CHAT
                                )
                            )
                            com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                        }
                        onShowMessage("已新增自動記事規則")
                    }
                }) {
                    Text("確認新增")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingNlMemoRule = null }) {
                    Text("取消")
                }
            }
        )
    }

    neverMemoTargetMsg?.let { srcMsg ->
        NeverAutoMemoConfirmDialog(
            packageName = srcMsg.packageName,
            appLabel = srcMsg.appLabel,
            sender = srcMsg.sender,
            defaultKeyword = srcMsg.content,
            onDismiss = { neverMemoTargetMsg = null },
            onConfirm = { pkg, snd, kw ->
                neverMemoTargetMsg = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        db.memoRuleDao().insert(
                            com.phantom.checker.data.MemoRuleEntity(
                                packageName = pkg,
                                sender = snd,
                                keyword = kw,
                                action = com.phantom.checker.data.MemoRuleEntity.ACTION_NEVER,
                                isEnabled = true,
                                createdAt = System.currentTimeMillis(),
                                source = com.phantom.checker.data.MemoRuleEntity.SOURCE_DETAIL
                            )
                        )
                        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                    }
                    onShowMessage("已設定不再自動加入記事／待辦")
                }
            }
        )
    }

    neverMemoFallbackNote?.let { targetNote ->
        NeverAutoMemoConfirmDialog(
            packageName = null,
            appLabel = targetNote.relatedAppLabel.orEmpty(),
            sender = targetNote.relatedAppLabel,
            defaultKeyword = targetNote.content,
            onDismiss = { neverMemoFallbackNote = null },
            onConfirm = { pkg, snd, kw ->
                neverMemoFallbackNote = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        db.memoRuleDao().insert(
                            com.phantom.checker.data.MemoRuleEntity(
                                packageName = pkg,
                                sender = snd,
                                keyword = kw,
                                action = com.phantom.checker.data.MemoRuleEntity.ACTION_NEVER,
                                isEnabled = true,
                                createdAt = System.currentTimeMillis(),
                                source = com.phantom.checker.data.MemoRuleEntity.SOURCE_DETAIL
                            )
                        )
                        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                    }
                    onShowMessage("已設定不再自動加入記事／待辦")
                }
            }
        )
    }

    val nq = notesSearchQuery.trim()
    val matchedTodos = remember(todos, nq) {
        if (nq.isEmpty()) emptyList()
        else todos.filter {
            it.title.contains(nq, ignoreCase = true) || it.notes.contains(nq, ignoreCase = true)
        }
    }
    val matchedNotes = remember(notes, nq) {
        if (nq.isEmpty()) emptyList()
        else notes.filter {
            (it.relatedAppLabel?.contains(nq, ignoreCase = true) == true) || it.content.contains(nq, ignoreCase = true)
        }
    }
    PhantomFullScreenSearchOverlay(
        visible = showNotesSearch,
        query = notesSearchQuery,
        onQueryChange = { notesSearchQuery = it },
        onDismiss = { showNotesSearch = false },
        placeholder = "搜尋待辦事項或備忘錄…",
        resultsContent = {
            if (nq.isEmpty()) {
                item(key = "notes_search_hint") {
                    Text(
                        text = "輸入關鍵字即時搜尋待辦事項與備忘錄。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else if (matchedTodos.isEmpty() && matchedNotes.isEmpty()) {
                item(key = "notes_search_empty") {
                    Text(
                        text = "找不到符合「$nq」的待辦或記事。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else {
                if (matchedTodos.isNotEmpty()) {
                    item(key = "notes_search_todos_hdr") {
                        Text(
                            text = "待辦事項（${matchedTodos.size}）",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(matchedTodos, key = { "todo_${it.id}" }) { todo ->
                        PhantomGlassCard(
                            glassState = glassState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showNotesSearch = false
                                    editingTodo = todo
                                    showTodoEditor = true
                                },
                            cornerRadius = 14.dp,
                            contentPadding = 12.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = todo.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        textDecoration = if (todo.isDone) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                    Text(
                                        text = PhantomPrefs.formatMessageTime(appCtx, todo.dueAt, includeDate = true),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (todo.notes.isNotBlank()) {
                                        Text(
                                            text = todo.notes,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (todo.relatedMessageId != null) {
                                    IconButton(
                                        onClick = {
                                            showNotesSearch = false
                                            onOpenMessageDetail(todo.relatedMessageId)
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "查看來源訊息",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (matchedNotes.isNotEmpty()) {
                    item(key = "notes_search_notes_hdr") {
                        Text(
                            text = "備忘錄（${matchedNotes.size}）",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(matchedNotes, key = { "note_${it.id}" }) { note ->
                        PhantomGlassCard(
                            glassState = glassState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showNotesSearch = false
                                    editingNote = note
                                    showEditor = true
                                },
                            cornerRadius = 14.dp,
                            contentPadding = 12.dp
                        ) {
                            val appLabel = note.relatedAppLabel
                            if (!appLabel.isNullOrBlank()) {
                                Text(
                                    text = appLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = note.content,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    )
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoteEditorDialog(
    initial: NoteEntity?,
    onDismiss: () -> Unit,
    onSave: (content: String, colorHex: String?) -> Unit
) {
    var content by remember(initial) { mutableStateOf(initial?.content ?: "") }
    var selectedColor by remember(initial) { mutableStateOf(initial?.colorHex) }
    val colorOptions = listOf<Pair<String?, String>>(
        null to "預設",
        "#EF5350" to "紅",
        "#FFA726" to "橘",
        "#66BB6A" to "綠",
        "#42A5F5" to "藍",
        "#AB47BC" to "紫"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增記事" else "編輯記事") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("記事內容") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "標記顏色（選填）",
                    style = MaterialTheme.typography.labelMedium
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    colorOptions.forEach { (hex, label) ->
                        FilterChip(
                            selected = selectedColor == hex,
                            onClick = { selectedColor = hex },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = content.trim().isNotEmpty(),
                onClick = { onSave(content.trim(), selectedColor) }
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


@Composable
internal fun ManualMemoRuleDialog(
    onDismiss: () -> Unit,
    onSave: (packageName: String?, sender: String?, keyword: String?, action: String) -> Unit
) {
    val ctx = LocalContext.current
    var pkgText by remember { mutableStateOf("") }
    var senderText by remember { mutableStateOf("") }
    var keywordText by remember { mutableStateOf("") }
    var selectedAction by remember { mutableStateOf(com.phantom.checker.data.MemoRuleEntity.ACTION_NEVER) }
    var showAppDropdown by remember { mutableStateOf(false) }
    var appOptions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(Unit) {
        appOptions = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val fromDb = runCatching {
                AppDatabaseHolder.db(ctx.applicationContext).messageDao().groupByApp()
                    .map { it.packageName to it.appLabel.ifBlank { it.packageName } }
            }.getOrDefault(emptyList())
            val installed = runCatching {
                pm.getInstalledApplications(0)
                    .filter { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
                    .map { it.packageName to pm.getApplicationLabel(it).toString() }
            }.getOrDefault(emptyList())
            (fromDb + installed)
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
        }
    }

    val isValid = pkgText.isNotBlank() || senderText.isNotBlank() || keywordText.isNotBlank()

    PhantomDialog(onDismissRequest = onDismiss) {
        Text(
            text = "手動新增自動記事規則",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
                Text(
                    text = "以下三項條件至少填寫一項（同時填寫時須全部符合）：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box {
                    OutlinedTextField(
                        value = pkgText,
                        onValueChange = { pkgText = it },
                        label = { Text("App 套件名（可選）") },
                        placeholder = { Text("例如 com.gogolook.whoscall") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showAppDropdown = true }) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "選擇已安裝 App"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = showAppDropdown,
                        onDismissRequest = { showAppDropdown = false }
                    ) {
                        appOptions.take(40).forEach { (pkg, label) ->
                            DropdownMenuItem(
                                text = { Text("$label ($pkg)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = {
                                    pkgText = pkg
                                    showAppDropdown = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = senderText,
                    onValueChange = { senderText = it },
                    label = { Text("寄件者（可選）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = keywordText,
                    onValueChange = { keywordText = it },
                    label = { Text("關鍵字（可選）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "規則動作：",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                listOf(
                    com.phantom.checker.data.MemoRuleEntity.ACTION_NEVER to "永不加入記事／待辦",
                    com.phantom.checker.data.MemoRuleEntity.ACTION_TODO to "一律加入待辦",
                    com.phantom.checker.data.MemoRuleEntity.ACTION_NOTE to "一律加入記事"
                ).forEach { (act, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedAction = act },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedAction == act,
                            onClick = { selectedAction = act }
                        )
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
            Button(
                onClick = {
                    onSave(
                        pkgText.trim().takeIf { it.isNotEmpty() },
                        senderText.trim().takeIf { it.isNotEmpty() },
                        keywordText.trim().takeIf { it.isNotEmpty() },
                        selectedAction
                    )
                },
                enabled = isValid
            ) {
                Text("儲存")
            }
        }
    }

}
