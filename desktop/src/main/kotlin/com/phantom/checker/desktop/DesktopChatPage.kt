package com.phantom.checker.desktop

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phantom.checker.shared.ChatBubbleDto
import com.phantom.checker.shared.ChatHistoryReq
import com.phantom.checker.shared.ChatSessionSummaryDto
import com.phantom.checker.shared.ChatSessionNewReq
import com.phantom.checker.shared.LinkMsgType
import com.phantom.checker.shared.MessageSummaryDto
import kotlinx.coroutines.launch

/**
 * #520 §7 commit 11：桌面 AI 助手頁響應式重編
 * - COMPACT / MEDIUM：會話欄改為覆蓋式抽屜（〔☰ 會話〕鈕開關，不推擠對話區）
 * - EXPANDED / LARGE：常駐 220dp 左側會話欄
 * - 訊息欄與輸入區最大寬 760dp 置中（防止 4K 超寬行長）
 * - 快速指令 chips 橫向捲動＋引用最新通知＋Markdown＋複製＋Ctrl+Enter 全保留
 */
@Composable
internal fun DesktopChatPage(
    windowClass: DesktopWindowClass,
    settings: DesktopSettings,
    tokens: DesktopThemeTokens,
    accentColor: Color,
    isWindowVisible: Boolean,
    chatSessions: List<ChatSessionSummaryDto>,
    chatSessionId: Long,
    onSelectSession: (Long) -> Unit,
    chatBubbles: List<ChatBubbleDto>,
    chatSending: Boolean,
    chatInput: String,
    onChatInputChange: (String) -> Unit,
    messages: List<MessageSummaryDto>,
    chatListState: LazyListState,
    onSendChatMessage: (String) -> Unit,
    onCopyToClipboard: (String) -> Unit,
    proactiveEvents: List<com.phantom.checker.shared.ProactiveEventPush> = emptyList(),
    onMuteProactiveTopics: (com.phantom.checker.shared.ProactiveEventPush) -> Unit = {},
    sendOrNotify: suspend (String, Any) -> Boolean
) {
    val scope = rememberCoroutineScope()
    val motion = LocalDesktopMotion.current
    val useDrawer = windowClass == DesktopWindowClass.COMPACT || windowClass == DesktopWindowClass.MEDIUM
    var drawerOpen by remember(useDrawer) { mutableStateOf(false) }

    val renderSessionListCard: @Composable (Modifier) -> Unit = { cardMod ->
        GlassCard(modifier = cardMod, containerColor = if (useDrawer) tokens.popupBg else tokens.surface) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(DesktopStrings.chatSessionsTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                    TextButton(onClick = {
                        scope.launch { sendOrNotify(LinkMsgType.CHAT_SESSION_NEW, ChatSessionNewReq(title = "Desktop Chat")) }
                    }) { Text(DesktopStrings.chatNewSession, color = tokens.accentText) }
                }
                HorizontalDivider(color = tokens.border)
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(chatSessions, key = { it.id }) { s ->
                        val sel = s.id == chatSessionId
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) tokens.selectedBg else Color.Transparent)
                                .clickable {
                                    onSelectSession(s.id)
                                    scope.launch { sendOrNotify(LinkMsgType.CHAT_HISTORY, ChatHistoryReq(s.id)) }
                                    if (useDrawer) drawerOpen = false
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                s.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (sel) tokens.accentText else tokens.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    val renderChatMain: @Composable (Modifier) -> Unit = { mainMod ->
        Box(modifier = mainMod, contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
            ) {
                // Quick Commands Bar + Session Drawer Toggle (COMPACT/MEDIUM) + Quote Latest Notification
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (useDrawer) {
                        FilterChip(
                            selected = drawerOpen,
                            onClick = { drawerOpen = !drawerOpen },
                            label = { Text(DesktopStrings.chatSessionsDrawerBtn) }
                        )
                    }
                    listOf(
                        DesktopStrings.quickPromptSummary,
                        DesktopStrings.quickPromptUnread,
                        DesktopStrings.quickPromptScam,
                        DesktopStrings.quickPromptTodos
                    ).forEach { prompt ->
                        FilterChip(selected = false, onClick = { onSendChatMessage(prompt) }, label = { Text(prompt) })
                    }
                    val latestMsg = remember(messages) { messages.maxByOrNull { it.timestamp } }
                    if (latestMsg != null) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                val app = DesktopAppLabels.resolveLabel(latestMsg.packageName, latestMsg.appLabel)
                                val quote = "[$app · ${latestMsg.sender}] ${latestMsg.preview.take(120)}\n"
                                onChatInputChange(if (chatInput.isBlank()) quote else "$quote$chatInput")
                            },
                            label = { Text(DesktopStrings.chatQuoteLatest, color = tokens.accentText) }
                        )
                    }
                }

                // #532 Mirrored Proactive Intel Alerts section (with Mute Topic writeback to phone)
                if (proactiveEvents.isNotEmpty()) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        containerColor = accentColor.copy(alpha = 0.10f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${DesktopStrings.proactiveIntelMirrorTitle} (${proactiveEvents.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.accentText
                            )
                            proactiveEvents.take(5).forEach { ev ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(tokens.surfaceElevated.copy(alpha = 0.7f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = ev.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tokens.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = ev.summary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = tokens.textSecondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    TextButton(onClick = { onMuteProactiveTopics(ev) }) {
                                        Text(DesktopStrings.proactiveIntelMuteBtn, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                if (chatBubbles.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(DesktopStrings.emptyChat, color = tokens.textMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(state = chatListState, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(
                            items = chatBubbles,
                            key = { idx, b -> "${idx}_${b.id}_${b.role}_${b.createdAt}" }
                        ) { _, b ->
                            val isUser = b.role == "user"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                            ) {
                                val chatCorner = settings.messageCardCornerDp.dp
                                val chatTextStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * settings.chatFontSize.multiplier
                                )
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(chatCorner))
                                        .background(if (isUser) tokens.selectedBg else tokens.surface)
                                        .border(1.dp, if (isUser) accentColor.copy(alpha = 0.35f) else tokens.border, RoundedCornerShape(chatCorner))
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    if (isUser) {
                                        Text(b.content, style = chatTextStyle, color = tokens.textPrimary)
                                    } else {
                                        ProvideTextStyle(chatTextStyle) {
                                            DesktopMarkdownText(markdown = b.content, accentColor = accentColor)
                                        }
                                        Row(modifier = Modifier.align(Alignment.End)) {
                                            TextButton(onClick = { onCopyToClipboard(b.content) }) {
                                                Text(DesktopStrings.chatCopy, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = chatSending) {
                    PulsingThinkingLabel(
                        text = DesktopStrings.chatThinking,
                        color = tokens.accentText,
                        animationsEnabled = motion.allowInfiniteLoops && settings.animationsEnabled && isWindowVisible
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = chatInput,
                        onValueChange = onChatInputChange,
                        label = { Text(DesktopStrings.chatInputPlaceholder) },
                        maxLines = 4,
                        modifier = Modifier
                            .weight(1f)
                            .onPreviewKeyEvent { ev ->
                                if (ev.type == KeyEventType.KeyDown && ev.isCtrlPressed && ev.key == Key.Enter) {
                                    onSendChatMessage(chatInput)
                                    true
                                } else false
                            }
                    )
                    SpringButton(
                        onClick = { onSendChatMessage(chatInput) },
                        enabled = !chatSending && chatInput.isNotBlank(),
                        animationsEnabled = settings.animationsEnabled
                    ) { Text(DesktopStrings.chatSend) }
                }
            }
        }
    }

    if (useDrawer) {
        Box(modifier = Modifier.fillMaxSize()) {
            renderChatMain(Modifier.fillMaxSize())
            if (drawerOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable { drawerOpen = false }
                )
                renderSessionListCard(
                    Modifier
                        .width(240.dp)
                        .fillMaxHeight()
                        .align(Alignment.CenterStart)
                )
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            renderSessionListCard(Modifier.width(220.dp).fillMaxHeight())
            renderChatMain(Modifier.weight(1f).fillMaxHeight())
        }
    }
}
