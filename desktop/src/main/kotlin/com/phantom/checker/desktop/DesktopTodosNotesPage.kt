package com.phantom.checker.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.phantom.checker.shared.LinkMsgType
import com.phantom.checker.shared.NoteAddReq
import com.phantom.checker.shared.NoteDeleteReq
import com.phantom.checker.shared.NoteItemDto
import com.phantom.checker.shared.NotePinToggleReq
import com.phantom.checker.shared.TodoAddReq
import com.phantom.checker.shared.TodoDeleteReq
import com.phantom.checker.shared.TodoItemDto
import com.phantom.checker.shared.TodosToggleReq
import kotlinx.coroutines.launch

/**
 * #520 §7 commit 11：桌面待辦與記事頁響應式重編
 * - COMPACT / MEDIUM：頂部分段切換〔待辦事項｜隨手記事〕單欄
 * - EXPANDED / LARGE：左右並排 0.58 / 0.42（LARGE 記事採雙欄卡片流）
 * - 待辦行內新增列追加快速到期 chips（1 小時／今晚／明天）；優先度改符號＋語意 token 色（色盲友善）
 */
@Composable
internal fun DesktopTodosNotesPage(
    windowClass: DesktopWindowClass,
    settings: DesktopSettings,
    tokens: DesktopThemeTokens,
    accentColor: Color,
    cardPad: Dp,
    listSpace: Dp,
    todos: List<TodoItemDto>,
    notes: List<NoteItemDto>,
    newTodoTitle: String,
    onNewTodoTitleChange: (String) -> Unit,
    onOpenNewTodoDialog: () -> Unit,
    onOpenEditTodoDialog: (TodoItemDto) -> Unit,
    newNoteContent: String,
    onNewNoteContentChange: (String) -> Unit,
    newNotePinned: Boolean,
    onNewNotePinnedChange: (Boolean) -> Unit,
    newNoteColor: String,
    onNewNoteColorChange: (String) -> Unit,
    onOpenEditNoteDialog: (NoteItemDto) -> Unit,
    todosListState: LazyListState,
    notesListState: LazyListState,
    sendOrNotify: suspend (String, Any) -> Boolean
) {
    val scope = rememberCoroutineScope()
    val nowMs = System.currentTimeMillis()
    val noteColorChoices = remember { listOf("", "#A78BFA", "#6EA8FE", "#34D399", "#FBBF24", "#FB7185") }
    var activeSegment by remember { mutableStateOf(0) } // 0 = Todos, 1 = Notes (used on COMPACT/MEDIUM)
    var quickDuePreset by remember { mutableStateOf("") } // "", "1h", "tonight", "tomorrow"
    var quickDueAt by remember { mutableLongStateOf(0L) }
    var pendingTodoDelete by remember { mutableStateOf<TodoItemDto?>(null) }
    var pendingNoteDelete by remember { mutableStateOf<NoteItemDto?>(null) }

    val submitTodo: () -> Unit = {
        val title = newTodoTitle.trim()
        if (title.isNotBlank()) {
            scope.launch {
                if (sendOrNotify(LinkMsgType.TODOS_ADD, TodoAddReq(title = title, dueAt = quickDueAt))) {
                    onNewTodoTitleChange("")
                    quickDuePreset = ""
                    quickDueAt = 0L
                }
            }
        }
    }

    val submitNote: () -> Unit = {
        val c = newNoteContent.trim()
        if (c.isNotBlank()) {
            scope.launch {
                if (sendOrNotify(LinkMsgType.NOTE_ADD, NoteAddReq(content = c, pinned = newNotePinned, colorHex = newNoteColor))) {
                    onNewNoteContentChange("")
                    onNewNotePinnedChange(false)
                }
            }
        }
    }

    val renderTodosPane: @Composable (Modifier) -> Unit = { paneMod ->
        Column(modifier = paneMod, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newTodoTitle,
                    onValueChange = onNewTodoTitleChange,
                    label = { Text(DesktopStrings.todoAddHint) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown &&
                            (ev.key == Key.Enter || ev.key == Key.NumPadEnter)
                        ) {
                            submitTodo()
                            true
                        } else {
                            false
                        }
                    }
                )
                SpringButton(
                    onClick = submitTodo,
                    animationsEnabled = settings.animationsEnabled
                ) { Text(DesktopStrings.add) }
                SpringOutlinedButton(
                    onClick = onOpenNewTodoDialog,
                    animationsEnabled = settings.animationsEnabled
                ) { Text(DesktopStrings.todoAddFull) }
            }

            // Quick Due Preset Chips (1h / Tonight / Tomorrow)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(DesktopStrings.todoDueLabel, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                FilterChip(
                    selected = quickDuePreset == "1h",
                    onClick = {
                        if (quickDuePreset == "1h") {
                            quickDuePreset = ""
                            quickDueAt = 0L
                        } else {
                            quickDuePreset = "1h"
                            quickDueAt = System.currentTimeMillis() + 60L * 60L * 1000L
                        }
                    },
                    label = { Text(DesktopStrings.todoDue1h, style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = quickDuePreset == "tonight",
                    onClick = {
                        if (quickDuePreset == "tonight") {
                            quickDuePreset = ""
                            quickDueAt = 0L
                        } else {
                            val cal = java.util.Calendar.getInstance().apply {
                                set(java.util.Calendar.HOUR_OF_DAY, 20)
                                set(java.util.Calendar.MINUTE, 0)
                                set(java.util.Calendar.SECOND, 0)
                                if (timeInMillis <= System.currentTimeMillis()) add(java.util.Calendar.DAY_OF_YEAR, 1)
                            }
                            quickDuePreset = "tonight"
                            quickDueAt = cal.timeInMillis
                        }
                    },
                    label = { Text(DesktopStrings.todoDueTonight, style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = quickDuePreset == "tomorrow",
                    onClick = {
                        if (quickDuePreset == "tomorrow") {
                            quickDuePreset = ""
                            quickDueAt = 0L
                        } else {
                            val cal = java.util.Calendar.getInstance().apply {
                                add(java.util.Calendar.DAY_OF_YEAR, 1)
                                set(java.util.Calendar.HOUR_OF_DAY, 9)
                                set(java.util.Calendar.MINUTE, 0)
                                set(java.util.Calendar.SECOND, 0)
                            }
                            quickDuePreset = "tomorrow"
                            quickDueAt = cal.timeInMillis
                        }
                    },
                    label = { Text(DesktopStrings.todoDueTomorrow, style = MaterialTheme.typography.labelSmall) }
                )
            }

            if (todos.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(DesktopStrings.todosEmpty, color = tokens.textMuted)
                }
            } else {
                LazyColumn(state = todosListState, verticalArrangement = Arrangement.spacedBy(listSpace), modifier = Modifier.weight(1f)) {
                    items(todos, key = { it.id }) { t ->
                        val isOverdue = !t.isDone && t.dueAt > 0L && t.dueAt < nowMs
                        val (prioSymbol, prioColor) = when (t.priority) {
                            2 -> "▲" to tokens.error
                            0 -> "▼" to tokens.success
                            else -> "●" to tokens.warning
                        }
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth().padding(cardPad), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = t.isDone,
                                    onCheckedChange = { checked ->
                                        scope.launch { sendOrNotify(LinkMsgType.TODOS_TOGGLE, TodosToggleReq(id = t.id, done = checked)) }
                                    }
                                )
                                Text(
                                    text = prioSymbol,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = prioColor
                                )
                                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(
                                        text = t.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textDecoration = if (t.isDone) TextDecoration.LineThrough else null,
                                        color = if (t.isDone) tokens.textMuted else if (isOverdue) tokens.error else tokens.textPrimary
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (t.dueAt > 0L) {
                                            val duePrefix = if (isOverdue) "${DesktopStrings.todoOverdue} · " else ""
                                            Text(
                                                text = "$duePrefix${formatDueTime(t.dueAt)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isOverdue) tokens.error else tokens.textMuted
                                            )
                                        }
                                        if (t.repeatRule.isNotBlank()) {
                                            Text(
                                                text = when (t.repeatRule) {
                                                    "daily" -> DesktopStrings.todoRepeatDaily
                                                    "weekly" -> DesktopStrings.todoRepeatWeekly
                                                    else -> t.repeatRule
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = tokens.accentText
                                            )
                                        }
                                    }
                                    if (t.notes.isNotBlank()) {
                                        Text(
                                            text = t.notes,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tokens.textSecondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                TextButton(onClick = { onOpenEditTodoDialog(t) }) { Text(DesktopStrings.todoEdit) }
                                TextButton(onClick = { pendingTodoDelete = t }) {
                                    Text(DesktopStrings.todoDelete, color = tokens.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val renderNoteItem: @Composable (NoteItemDto, Modifier) -> Unit = { n, itemMod ->
        val noteAccent = DesktopPalette.parseHexColor(n.colorHex)
        Row(
            modifier = itemMod
                .clip(RoundedCornerShape(10.dp))
                .background(tokens.surfaceElevated.copy(alpha = 0.6f))
                .border(1.dp, if (n.pinned) accentColor.copy(alpha = 0.4f) else tokens.border, RoundedCornerShape(10.dp))
                .clickable { onOpenEditNoteDialog(n) }
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (noteAccent != null) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(noteAccent)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (n.relatedAppLabel.isNotBlank() || n.pinned) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (n.pinned) {
                            Icon(DesktopIcons.Pin, contentDescription = null, tint = tokens.accentText, modifier = Modifier.size(13.dp))
                        }
                        if (n.relatedAppLabel.isNotBlank()) {
                            Text(n.relatedAppLabel, style = MaterialTheme.typography.labelSmall, color = tokens.accentText)
                        }
                    }
                }
                Text(n.content, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = {
                    scope.launch {
                        sendOrNotify(LinkMsgType.NOTE_PIN_TOGGLE, NotePinToggleReq(id = n.id, pinned = !n.pinned))
                    }
                }) {
                    Text(if (n.pinned) DesktopStrings.noteUnpin else DesktopStrings.notePin, style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = {
                    pendingNoteDelete = n
                }) {
                    Text(DesktopStrings.noteDelete, style = MaterialTheme.typography.labelSmall, color = tokens.error)
                }
            }
        }
    }

    val renderNotesPane: @Composable (Modifier) -> Unit = { paneMod ->
        GlassCard(modifier = paneMod) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(DesktopIcons.Note, contentDescription = null, tint = tokens.accentText, modifier = Modifier.size(18.dp))
                        Text(DesktopStrings.notesTitle, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                    }
                    FilterChip(
                        selected = newNotePinned,
                        onClick = { onNewNotePinnedChange(!newNotePinned) },
                        label = { Text(DesktopStrings.notePin, style = MaterialTheme.typography.labelSmall) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newNoteContent,
                        onValueChange = onNewNoteContentChange,
                        label = { Text(DesktopStrings.noteInputHint) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown &&
                                (ev.key == Key.Enter || ev.key == Key.NumPadEnter)
                            ) {
                                submitNote()
                                true
                            } else {
                                false
                            }
                        }
                    )
                    SpringButton(
                        onClick = submitNote,
                        animationsEnabled = settings.animationsEnabled
                    ) { Text(DesktopStrings.add) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(DesktopStrings.noteColorLabel, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                    noteColorChoices.forEach { hex ->
                        val selected = newNoteColor == hex
                        val swatch = DesktopPalette.parseHexColor(hex) ?: tokens.border
                        Box(
                            modifier = Modifier
                                .size(if (selected) 20.dp else 16.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(if (selected) 2.dp else 1.dp, if (selected) tokens.textPrimary else tokens.border, CircleShape)
                                .clickable { onNewNoteColorChange(hex) }
                        )
                    }
                }
                if (notes.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(DesktopStrings.notesEmpty, color = tokens.textMuted)
                    }
                } else {
                    val sortedNotes = remember(notes) {
                        notes.sortedWith(compareByDescending<NoteItemDto> { it.pinned }.thenByDescending { it.updatedAt })
                    }
                    // R16a: 雙欄以 chunked(2)+複合鍵會隨增刪整欄重組（鍵值連鎖變動）。
                    // 改為單欄、以固定 id 作 key，增刪只影響自身項目，不再重組整欄。
                    LazyColumn(state = notesListState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sortedNotes, key = { it.id }) { n ->
                            renderNoteItem(n, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }

    if (windowClass == DesktopWindowClass.COMPACT || windowClass == DesktopWindowClass.MEDIUM) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = activeSegment == 0,
                    onClick = { activeSegment = 0 },
                    label = { Text("${DesktopStrings.todosSegmentLabel} (${todos.size})") }
                )
                FilterChip(
                    selected = activeSegment == 1,
                    onClick = { activeSegment = 1 },
                    label = { Text("${DesktopStrings.notesSegmentLabel} (${notes.size})") }
                )
            }
            if (activeSegment == 0) {
                renderTodosPane(Modifier.weight(1f).fillMaxWidth())
            } else {
                renderNotesPane(Modifier.weight(1f).fillMaxWidth())
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            renderTodosPane(Modifier.weight(0.58f).fillMaxHeight())
            renderNotesPane(Modifier.weight(0.42f).fillMaxHeight())
        }
    }

    // R16a: 刪除一律先經確認彈窗，避免誤刪待辦／記事。
    pendingTodoDelete?.let { todo ->
        AlertDialog(
            onDismissRequest = { pendingTodoDelete = null },
            title = { Text(DesktopStrings.todoDelete) },
            text = { Text(todo.title, maxLines = 3, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = {
                    pendingTodoDelete = null
                    scope.launch { sendOrNotify(LinkMsgType.TODOS_DELETE, TodoDeleteReq(id = todo.id)) }
                }) { Text(DesktopStrings.remove, color = tokens.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingTodoDelete = null }) { Text(DesktopStrings.ok) }
            }
        )
    }

    pendingNoteDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingNoteDelete = null },
            title = { Text(DesktopStrings.noteDelete) },
            text = { Text(note.content, maxLines = 3, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = {
                    pendingNoteDelete = null
                    scope.launch { sendOrNotify(LinkMsgType.NOTE_DELETE, NoteDeleteReq(id = note.id)) }
                }) { Text(DesktopStrings.remove, color = tokens.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingNoteDelete = null }) { Text(DesktopStrings.ok) }
            }
        )
    }
}
