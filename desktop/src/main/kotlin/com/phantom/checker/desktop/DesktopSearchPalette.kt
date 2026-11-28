package com.phantom.checker.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phantom.checker.shared.NoteItemDto
import com.phantom.checker.shared.TodoItemDto

/** #589 A2 / A7 global search result kinds. */
internal enum class DesktopSearchKind { CONVERSATION, NOTE, TODO, SETTING }

internal data class DesktopSearchResult(
    val key: String,
    val kind: DesktopSearchKind,
    val title: String,
    val subtitle: String,
    val tabKey: String,
    val conversationKey: String = "",
    val messageId: Long = -1L,
    val settingSubTab: String = ""
)

/**
 * #589 A2：搜尋對話（已載入清單）、記事、待辦與設定鍵。
 * 對話的資料庫查詢仍由既有 MESSAGES_LIST 流程負責，此處只做即時本地過濾；
 * 使用者按下 Enter 後會跳到既有頁面與項目，不新增任何協定命令。
 */
internal fun buildDesktopSearchResults(
    query: String,
    conversations: List<ConversationSummary>,
    notes: List<NoteItemDto>,
    todos: List<TodoItemDto>
): List<DesktopSearchResult> {
    val q = query.trim()
    if (q.isBlank()) return emptyList()
    val out = mutableListOf<DesktopSearchResult>()

    conversations.asSequence()
        .filter { conv ->
            conv.sender.contains(q, ignoreCase = true) ||
                conv.appLabel.contains(q, ignoreCase = true) ||
                conv.packageName.contains(q, ignoreCase = true) ||
                conv.latestMessage.preview.contains(q, ignoreCase = true)
        }
        .take(8)
        .forEach { conv ->
            out += DesktopSearchResult(
                key = "conv:${conv.groupKey}",
                kind = DesktopSearchKind.CONVERSATION,
                title = conv.sender.ifBlank { DesktopAppLabels.resolveLabel(conv.packageName, conv.appLabel) },
                subtitle = "${conv.appLabel} · ${conv.latestMessage.preview.take(80)}",
                tabKey = "MESSAGES",
                conversationKey = conv.groupKey,
                messageId = conv.latestMessage.id
            )
        }

    notes.asSequence()
        .filter { it.content.contains(q, ignoreCase = true) }
        .take(5)
        .forEach { note ->
            out += DesktopSearchResult(
                key = "note:${note.id}",
                kind = DesktopSearchKind.NOTE,
                title = note.content.lineSequence().firstOrNull().orEmpty().take(60),
                subtitle = DesktopStrings.globalSearchNotes,
                tabKey = "TODOS"
            )
        }

    todos.asSequence()
        .filter { it.title.contains(q, ignoreCase = true) || it.notes.contains(q, ignoreCase = true) }
        .take(5)
        .forEach { todo ->
            out += DesktopSearchResult(
                key = "todo:${todo.id}",
                kind = DesktopSearchKind.TODO,
                title = todo.title,
                subtitle = todo.notes.take(60).ifBlank { DesktopStrings.globalSearchTodos },
                tabKey = "TODOS"
            )
        }

    DesktopSettingsCatalog.search(q).take(8).forEach { entry ->
        out += DesktopSearchResult(
            key = "setting:${entry.id}",
            kind = DesktopSearchKind.SETTING,
            title = DesktopStrings.t(entry.titleZh, entry.titleEn),
            subtitle = entry.keywords,
            tabKey = "SETTINGS",
            settingSubTab = entry.subTab
        )
    }
    return out
}

/** #589 A2 global command palette (Ctrl+K). Also exposes a persistent sidebar entry point. */
@Composable
internal fun DesktopSearchPalette(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<DesktopSearchResult>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    focusRequester: FocusRequester,
    onDismiss: () -> Unit,
    onOpen: (DesktopSearchResult) -> Unit
) {
    val tokens = LocalDesktopTokens.current
    val motion = LocalDesktopMotion.current

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (tokens.isLight) 0.22f else 0.42f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .padding(top = 72.dp)
                .widthIn(max = 680.dp)
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(tokens.radiusLg))
                .background(tokens.popupBg)
                .border(1.dp, tokens.border, RoundedCornerShape(tokens.radiusLg))
                .clickable { }
                .padding(tokens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(tokens.spacingMd)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(tokens.spacingSm)) {
                Icon(DesktopIcons.Search, contentDescription = null, tint = tokens.accentText, modifier = Modifier.size(20.dp))
                Text(
                    text = DesktopStrings.globalSearchHint,
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Ctrl+K",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(tokens.radiusSm))
                        .background(tokens.surfaceElevated)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(DesktopStrings.globalSearchHint, style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                leadingIcon = { Icon(DesktopIcons.Search, contentDescription = null, tint = tokens.textMuted, modifier = Modifier.size(18.dp)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (ev.key) {
                            Key.Escape -> { onDismiss(); true }
                            Key.DirectionDown -> {
                                if (results.isNotEmpty()) onSelectedIndexChange((selectedIndex + 1).coerceAtMost(results.lastIndex))
                                true
                            }
                            Key.DirectionUp -> {
                                if (results.isNotEmpty()) onSelectedIndexChange((selectedIndex - 1).coerceAtLeast(0))
                                true
                            }
                            Key.Enter, Key.NumPadEnter -> {
                                results.getOrNull(selectedIndex)?.let(onOpen)
                                true
                            }
                            else -> false
                        }
                    }
            )
            Text(DesktopStrings.globalSearchNavigateHint, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)

            when {
                query.isBlank() -> {
                    Text(DesktopStrings.globalSearchEmptyQuery, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                }
                results.isEmpty() -> {
                    DesktopEmptyState(
                        title = DesktopStrings.globalSearchNoResults,
                        body = DesktopStrings.globalSearchNoResultsHint,
                        icon = DesktopIcons.Search
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(tokens.spacingXs)
                    ) {
                        itemsIndexed(results, key = { _, it -> it.key }) { idx, result ->
                            val selected = idx == selectedIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(tokens.radiusMd))
                                    .background(if (selected) tokens.selectedBg else Color.Transparent)
                                    .clickable { onOpen(result) }
                                    .padding(horizontal = tokens.spacingMd, vertical = tokens.spacingSm),
                                horizontalArrangement = Arrangement.spacedBy(tokens.spacingSm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (result.kind) {
                                        DesktopSearchKind.CONVERSATION -> "💬"
                                        DesktopSearchKind.NOTE -> "📝"
                                        DesktopSearchKind.TODO -> "✅"
                                        DesktopSearchKind.SETTING -> "⚙"
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(
                                        text = result.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = tokens.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = result.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = when (result.kind) {
                                        DesktopSearchKind.CONVERSATION -> DesktopStrings.globalSearchConversations
                                        DesktopSearchKind.NOTE -> DesktopStrings.globalSearchNotes
                                        DesktopSearchKind.TODO -> DesktopStrings.globalSearchTodos
                                        DesktopSearchKind.SETTING -> DesktopStrings.globalSearchSettings
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.accentText
                                )
                            }
                        }
                    }
                }
            }
            if (motion.isBalanced) {
                Spacer(Modifier.size(1.dp))
            }
        }
    }
}

/** #589 A8 consistent illustrated empty state for desktop pages. */
@Composable
internal fun DesktopEmptyState(
    title: String,
    body: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val tokens = LocalDesktopTokens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radiusLg))
            .background(tokens.surfaceElevated.copy(alpha = 0.55f))
            .padding(tokens.spacingXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(tokens.spacingSm)
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(tokens.radiusMd))
                    .background(tokens.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tokens.accentText, modifier = Modifier.size(24.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
        Text(body, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
    }
}

/** #589 A3 semantic status chip (icon + label + value). */
@Composable
internal fun DesktopStatusChip(
    icon: String,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val tokens = LocalDesktopTokens.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = if (tokens.isLight) 0.12f else 0.16f))
            .border(1.dp, tint.copy(alpha = 0.40f), RoundedCornerShape(50))
            .padding(horizontal = tokens.spacingMd, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, style = MaterialTheme.typography.labelMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = tint, maxLines = 1)
    }
}

/** #589 A7 shortcut help panel. */
@Composable
internal fun DesktopShortcutHelpDialog(onDismiss: () -> Unit) {
    val tokens = LocalDesktopTokens.current
    PhantomDialog(
        onDismissRequest = onDismiss,
        title = DesktopStrings.shortcutsHelpTitle,
        maxWidth = 620.dp,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(DesktopStrings.ok) }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacingMd)) {
            listOf(
                "Ctrl+1..8" to DesktopStrings.shortcutNavHint,
                "Ctrl+K / Ctrl+F" to DesktopStrings.shortcutSearchHint,
                "Ctrl+B" to DesktopStrings.shortcutSidebarHint,
                "Esc" to DesktopStrings.shortcutEscHint,
                "?" to DesktopStrings.shortcutHelpHint
            ).forEach { (keys, desc) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacingMd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = keys,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.accentText,
                        modifier = Modifier
                            .widthIn(min = 128.dp)
                            .clip(RoundedCornerShape(tokens.radiusSm))
                            .background(tokens.surfaceElevated)
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, modifier = Modifier.weight(1f))
                }
            }
            Text(DesktopStrings.shortcutCloseHint, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
        }
    }
}
