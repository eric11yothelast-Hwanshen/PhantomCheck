package com.phantom.checker.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.phantom.checker.shared.ChatBubbleDto
import com.phantom.checker.shared.ChatHistoryReq
import com.phantom.checker.shared.ChatHistoryRes
import com.phantom.checker.shared.ChatReplyRes
import com.phantom.checker.shared.ChatSendReq
import com.phantom.checker.shared.LinkMsgType
import com.phantom.checker.shared.MarkReadReq
import com.phantom.checker.shared.MessageNewPush
import com.phantom.checker.shared.MessageReadSyncPush
import com.phantom.checker.shared.MessageSummaryDto
import com.phantom.checker.shared.MessagesListReq
import com.phantom.checker.shared.MessagesListRes
import com.phantom.checker.shared.QuickReplySendReq
import com.phantom.checker.shared.QuickReplySendRes
import com.phantom.checker.shared.RuleItemDto
import com.phantom.checker.shared.RuleMutateRes
import com.phantom.checker.shared.RuleRemoveReq
import com.phantom.checker.shared.RulesListRes
import com.phantom.checker.shared.StatsSnapshotDto
import com.phantom.checker.shared.TagAddReq
import com.phantom.checker.shared.TagItemDto
import com.phantom.checker.shared.TagMutateRes
import com.phantom.checker.shared.TagRemoveReq
import com.phantom.checker.shared.TagsListRes
import com.phantom.checker.shared.TodoItemDto
import com.phantom.checker.shared.TodosListReq
import com.phantom.checker.shared.TodosListRes
import com.phantom.checker.shared.TodosToggleReq
import com.phantom.checker.shared.TodosToggleRes
import com.phantom.checker.shared.WhitelistAddReq
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private enum class DesktopTab {
    MESSAGES, STATS, TODOS, CHAT, RULES, SETTINGS
}

@Composable
fun DesktopApp() {
    val gson = remember { Gson() }
    val scanner = remember { MdnsScanner() }
    val client = remember { DesktopLinkClient() }
    val scope = rememberCoroutineScope()

    val devices by scanner.devices.collectAsState()
    val isScanning by scanner.isScanning.collectAsState()
    val uiState by client.state.collectAsState()

    var activeTab by remember { mutableStateOf(DesktopTab.MESSAGES) }

    // M1 Pairing inputs
    var manualHostPort by remember { mutableStateOf("") }
    var manualCode by remember { mutableStateOf("") }
    var selectedFingerprintPrefix by remember { mutableStateOf("") }

    // M2 Data states
    var msgFilter by remember { mutableStateOf("all") }
    var messages by remember { mutableStateOf<List<MessageSummaryDto>>(emptyList()) }
    var latestCursor by remember { mutableLongStateOf(0L) }
    var mirrorToast by remember { mutableStateOf<String?>(null) }
    var replyTargetId by remember { mutableLongStateOf(-1L) }
    var replyText by remember { mutableStateOf("") }
    var actionFeedback by remember { mutableStateOf<String?>(null) }

    var stats by remember { mutableStateOf(StatsSnapshotDto()) }
    var todos by remember { mutableStateOf<List<TodoItemDto>>(emptyList()) }

    var chatSessionId by remember { mutableLongStateOf(0L) }
    var chatBubbles by remember { mutableStateOf<List<ChatBubbleDto>>(emptyList()) }
    var chatInput by remember { mutableStateOf("") }
    var chatSending by remember { mutableStateOf(false) }

    var whitelistRules by remember { mutableStateOf<List<RuleItemDto>>(emptyList()) }
    var newRuleSender by remember { mutableStateOf("") }
    var newRulePkg by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf<List<TagItemDto>>(emptyList()) }
    var newTagName by remember { mutableStateOf("") }

    fun reqId() = UUID.randomUUID().toString()

    fun refreshMessages(useCursor: Boolean = false) {
        scope.launch {
            val cursor = if (useCursor) latestCursor else 0L
            client.sendCommand(LinkMsgType.MESSAGES_LIST, reqId(), MessagesListReq(filter = msgFilter, offset = 0, limit = 40, sinceCursor = cursor))
        }
    }

    fun refreshAllOnConnect() {
        scope.launch {
            client.sendCommand(LinkMsgType.MESSAGES_SUBSCRIBE, reqId(), emptyMap<String, String>())
            client.sendCommand(LinkMsgType.MESSAGES_LIST, reqId(), MessagesListReq(filter = msgFilter, offset = 0, limit = 40, sinceCursor = latestCursor))
            client.sendCommand(LinkMsgType.STATS_SNAPSHOT, reqId(), emptyMap<String, String>())
            client.sendCommand(LinkMsgType.TODOS_LIST, reqId(), TodosListReq())
            client.sendCommand(LinkMsgType.CHAT_HISTORY, reqId(), ChatHistoryReq(chatSessionId))
            client.sendCommand(LinkMsgType.WHITELIST_LIST, reqId(), emptyMap<String, String>())
            client.sendCommand(LinkMsgType.TAGS_LIST, reqId(), emptyMap<String, String>())
        }
    }

    LaunchedEffect(Unit) {
        scanner.start()
        if (uiState.isPaired) {
            client.connectSaved()
        } else {
            activeTab = DesktopTab.SETTINGS
        }
    }

    LaunchedEffect(uiState.connState) {
        if (uiState.connState == LinkConnState.CONNECTED) {
            refreshAllOnConnect()
        }
    }

    LaunchedEffect(Unit) {
        client.events.collect { env ->
            when (env.type) {
                LinkMsgType.MESSAGES_LIST_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, MessagesListRes::class.java) }.getOrNull() ?: return@collect
                    if (res.latestCursor > latestCursor) latestCursor = res.latestCursor
                    messages = if (messages.isEmpty()) res.items else (res.items + messages).distinctBy { it.id }.sortedByDescending { it.timestamp }
                }
                LinkMsgType.MESSAGE_NEW -> {
                    val push = runCatching { gson.fromJson(env.payloadJson, MessageNewPush::class.java) }.getOrNull() ?: return@collect
                    if (push.latestCursor > latestCursor) latestCursor = push.latestCursor
                    messages = (push.items + messages).distinctBy { it.id }.sortedByDescending { it.timestamp }
                    push.items.firstOrNull()?.let { newest ->
                        mirrorToast = "${DesktopStrings.newMessageMirror}${newest.sender}: ${newest.preview}"
                    }
                }
                LinkMsgType.MESSAGE_READ_SYNC -> {
                    val sync = runCatching { gson.fromJson(env.payloadJson, MessageReadSyncPush::class.java) }.getOrNull() ?: return@collect
                    messages = if (sync.all) {
                        messages.map { it.copy(isRead = true) }
                    } else {
                        val idSet = sync.ids.toSet()
                        messages.map { if (it.id in idSet) it.copy(isRead = true) else it }
                    }
                }
                LinkMsgType.STATS_SNAPSHOT_RES -> {
                    stats = runCatching { gson.fromJson(env.payloadJson, StatsSnapshotDto::class.java) }.getOrNull() ?: stats
                }
                LinkMsgType.TODOS_LIST_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, TodosListRes::class.java) }.getOrNull() ?: return@collect
                    todos = res.items
                }
                LinkMsgType.TODOS_TOGGLE_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, TodosToggleRes::class.java) }.getOrNull() ?: return@collect
                    if (res.ok) {
                        scope.launch { client.sendCommand(LinkMsgType.TODOS_LIST, reqId(), TodosListReq()) }
                    } else {
                        actionFeedback = res.errorMessage
                    }
                }
                LinkMsgType.CHAT_HISTORY_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, ChatHistoryRes::class.java) }.getOrNull() ?: return@collect
                    if (res.sessionId > 0L) chatSessionId = res.sessionId
                    chatBubbles = res.messages
                }
                LinkMsgType.CHAT_REPLY -> {
                    chatSending = false
                    val res = runCatching { gson.fromJson(env.payloadJson, ChatReplyRes::class.java) }.getOrNull() ?: return@collect
                    if (res.sessionId > 0L) chatSessionId = res.sessionId
                    if (res.ok) {
                        chatBubbles = chatBubbles + ChatBubbleDto(System.currentTimeMillis(), "assistant", res.content, System.currentTimeMillis())
                    } else {
                        actionFeedback = "${res.errorCode}: ${res.errorMessage}"
                    }
                }
                LinkMsgType.QUICK_REPLY_SEND_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, QuickReplySendRes::class.java) }.getOrNull() ?: return@collect
                    actionFeedback = if (res.ok) "Reply sent" else "${res.errorCode}: ${res.errorMessage}"
                }
                LinkMsgType.WHITELIST_LIST_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, RulesListRes::class.java) }.getOrNull() ?: return@collect
                    whitelistRules = res.items
                }
                LinkMsgType.WHITELIST_ADD_RES, LinkMsgType.WHITELIST_REMOVE_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, RuleMutateRes::class.java) }.getOrNull() ?: return@collect
                    actionFeedback = res.message
                    scope.launch { client.sendCommand(LinkMsgType.WHITELIST_LIST, reqId(), emptyMap<String, String>()) }
                }
                LinkMsgType.TAGS_LIST_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, TagsListRes::class.java) }.getOrNull() ?: return@collect
                    tags = res.items
                }
                LinkMsgType.TAGS_ADD_RES, LinkMsgType.TAGS_REMOVE_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, TagMutateRes::class.java) }.getOrNull() ?: return@collect
                    actionFeedback = res.message
                    scope.launch { client.sendCommand(LinkMsgType.TAGS_LIST, reqId(), emptyMap<String, String>()) }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            scanner.stop()
            client.disconnect()
        }
    }

    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF0E1117),
            surface = Color(0xFF171C26),
            surfaceVariant = Color(0xFF1F2633),
            primary = Color(0xFF6EA8FE),
            onBackground = Color(0xFFE6EDF3),
            onSurface = Color(0xFFE6EDF3)
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Row(modifier = Modifier.fillMaxSize()) {
                // 側邊導覽列
                Column(
                    modifier = Modifier
                        .width(200.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF131822))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = DesktopStrings.appTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)
                    )
                    val navItems = listOf(
                        DesktopTab.MESSAGES to DesktopStrings.navMessages,
                        DesktopTab.STATS to DesktopStrings.navStats,
                        DesktopTab.TODOS to DesktopStrings.navTodos,
                        DesktopTab.CHAT to DesktopStrings.navChat,
                        DesktopTab.RULES to DesktopStrings.navRules,
                        DesktopTab.SETTINGS to DesktopStrings.navSettings
                    )
                    navItems.forEach { (tab, label) ->
                        val selected = activeTab == tab
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Color(0xFF253248) else Color.Transparent)
                                .clickable { activeTab = tab }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    // 常駐連線狀態列
                    val statusText = when (uiState.connState) {
                        LinkConnState.CONNECTED -> DesktopStrings.statusConnected + uiState.pairedDeviceName
                        LinkConnState.RECONNECTING -> DesktopStrings.statusReconnecting
                        LinkConnState.CONNECTING -> DesktopStrings.statusScanning
                        LinkConnState.DISCONNECTED -> if (uiState.isPaired) DesktopStrings.statusReconnecting else DesktopStrings.statusUnpaired
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(10.dp)
                    ) {
                        Text(statusText, style = MaterialTheme.typography.labelSmall)
                    }
                }

                // 主內容區
                Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(20.dp)) {
                    if (!uiState.lastError.isNullOrBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF3A1D24)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(uiState.lastError.orEmpty(), color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodySmall)
                                Text(DesktopStrings.firewallHint, style = MaterialTheme.typography.labelSmall, color = Color(0xFFD0D7DE))
                            }
                        }
                    }

                    if (!mirrorToast.isNullOrBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1D3557)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(mirrorToast.orEmpty(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                TextButton(onClick = { mirrorToast = null }) { Text("✕") }
                            }
                        }
                    }

                    if (!actionFeedback.isNullOrBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(actionFeedback.orEmpty(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                TextButton(onClick = { actionFeedback = null }) { Text("OK") }
                            }
                        }
                    }

                    if (uiState.connState != LinkConnState.CONNECTED && activeTab != DesktopTab.SETTINGS) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(DesktopStrings.notConnectedHint, color = Color(0xFF9BA3B4))
                        }
                    } else when (activeTab) {
                        DesktopTab.MESSAGES -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(
                                    "all" to DesktopStrings.filterAll,
                                    "unread" to DesktopStrings.filterUnread,
                                    "starred" to DesktopStrings.filterStarred,
                                    "scam" to DesktopStrings.filterScam
                                ).forEach { (key, label) ->
                                    FilterChip(
                                        selected = msgFilter == key,
                                        onClick = {
                                            msgFilter = key
                                            messages = emptyList()
                                            refreshMessages(useCursor = false)
                                        },
                                        label = { Text(label) }
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        client.sendCommand(LinkMsgType.MESSAGES_MARK_READ, reqId(), MarkReadReq(all = true))
                                    }
                                }) {
                                    Text(DesktopStrings.markAllRead)
                                }
                                OutlinedButton(onClick = {
                                    messages = emptyList()
                                    refreshMessages(useCursor = false)
                                }) {
                                    Text(DesktopStrings.refresh)
                                }
                            }
                            Text(DesktopStrings.markReadNote, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9BA3B4), modifier = Modifier.padding(vertical = 4.dp))
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                                items(messages, key = { it.id }) { m ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${if (m.isRead) "" else "● "}${m.sender} (${m.packageName})",
                                                    fontWeight = if (m.isRead) FontWeight.Normal else FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Text(m.riskLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (m.riskLabel == "scam") Color(0xFFFF6B6B) else Color(0xFF9BA3B4))
                                                    Text(timeFmt.format(Date(m.timestamp)), style = MaterialTheme.typography.labelSmall, color = Color(0xFF9BA3B4))
                                                }
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(m.preview, style = MaterialTheme.typography.bodySmall, color = Color(0xFFD0D7DE))
                                            Spacer(Modifier.height(6.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                if (!m.isRead) {
                                                    TextButton(onClick = {
                                                        scope.launch {
                                                            client.sendCommand(LinkMsgType.MESSAGES_MARK_READ, reqId(), MarkReadReq(ids = listOf(m.id)))
                                                        }
                                                    }) { Text(DesktopStrings.markRead) }
                                                }
                                                TextButton(onClick = {
                                                    replyTargetId = if (replyTargetId == m.id) -1L else m.id
                                                    replyText = ""
                                                }) { Text(DesktopStrings.sendReply) }
                                            }
                                            if (replyTargetId == m.id) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    OutlinedTextField(
                                                        value = replyText,
                                                        onValueChange = { replyText = it },
                                                        label = { Text(DesktopStrings.replyPlaceholder) },
                                                        singleLine = true,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Button(onClick = {
                                                        if (replyText.isNotBlank()) {
                                                            scope.launch {
                                                                client.sendCommand(
                                                                    LinkMsgType.QUICK_REPLY_SEND,
                                                                    reqId(),
                                                                    QuickReplySendReq(packageName = m.packageName, sender = m.sender, text = replyText)
                                                                )
                                                                replyTargetId = -1L
                                                                replyText = ""
                                                            }
                                                        }
                                                    }) { Text(DesktopStrings.sendReply) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        DesktopTab.STATS -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                    StatBox(DesktopStrings.statsTotal, stats.total.toString(), Modifier.weight(1f))
                                    StatBox(DesktopStrings.statsToday, stats.today.toString(), Modifier.weight(1f))
                                    StatBox(DesktopStrings.statsUnread, stats.unread.toString(), Modifier.weight(1f))
                                    StatBox(DesktopStrings.statsScam, stats.scam.toString(), Modifier.weight(1f))
                                }
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                        Text(DesktopStrings.statsRecentApps, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.height(8.dp))
                                        Text(stats.recentPackages.joinToString(" · ").ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }

                        DesktopTab.TODOS -> {
                            if (todos.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(DesktopStrings.todosEmpty, color = Color(0xFF9BA3B4))
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(todos, key = { it.id }) { t ->
                                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = t.isDone,
                                                    onCheckedChange = { checked ->
                                                        scope.launch {
                                                            client.sendCommand(LinkMsgType.TODOS_TOGGLE, reqId(), TodosToggleReq(id = t.id, done = checked))
                                                        }
                                                    }
                                                )
                                                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                                    Text(t.title, style = MaterialTheme.typography.bodyMedium)
                                                    if (t.repeatRule.isNotBlank()) {
                                                        Text(t.repeatRule, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9BA3B4))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        DesktopTab.CHAT -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(chatBubbles) { b ->
                                        val isUser = b.role == "user"
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (isUser) Color(0xFF253248) else MaterialTheme.colorScheme.surface)
                                                    .padding(12.dp)
                                            ) {
                                                Text(b.content, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                }
                                if (chatSending) {
                                    Text(DesktopStrings.chatThinking, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9BA3B4), modifier = Modifier.padding(vertical = 4.dp))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = chatInput,
                                        onValueChange = { chatInput = it },
                                        label = { Text(DesktopStrings.chatInputPlaceholder) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = {
                                            val msg = chatInput.trim()
                                            if (msg.isNotBlank()) {
                                                chatInput = ""
                                                chatSending = true
                                                chatBubbles = chatBubbles + ChatBubbleDto(System.currentTimeMillis(), "user", msg, System.currentTimeMillis())
                                                scope.launch {
                                                    client.sendCommand(LinkMsgType.CHAT_SEND, reqId(), ChatSendReq(text = msg, sessionId = chatSessionId))
                                                }
                                            }
                                        },
                                        enabled = !chatSending && chatInput.isNotBlank()
                                    ) {
                                        Text(DesktopStrings.chatSend)
                                    }
                                }
                            }
                        }

                        DesktopTab.RULES -> {
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp)) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(DesktopStrings.whitelistTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedTextField(value = newRuleSender, onValueChange = { newRuleSender = it }, label = { Text(DesktopStrings.whitelistSenderHint) }, modifier = Modifier.weight(1f), singleLine = true)
                                            OutlinedTextField(value = newRulePkg, onValueChange = { newRulePkg = it }, label = { Text(DesktopStrings.whitelistPkgHint) }, modifier = Modifier.weight(1f), singleLine = true)
                                            Button(onClick = {
                                                if (newRuleSender.isNotBlank()) {
                                                    scope.launch {
                                                        client.sendCommand(LinkMsgType.WHITELIST_ADD, reqId(), WhitelistAddReq(sender = newRuleSender, packageName = newRulePkg, ruleText = newRuleSender))
                                                        newRuleSender = ""
                                                        newRulePkg = ""
                                                    }
                                                }
                                            }) { Text(DesktopStrings.add) }
                                        }
                                        whitelistRules.forEach { r ->
                                            HorizontalDivider()
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text("${r.ruleText} ${if (r.packageName.isNotBlank()) "(${r.packageName})" else ""}", style = MaterialTheme.typography.bodySmall)
                                                TextButton(onClick = {
                                                    scope.launch { client.sendCommand(LinkMsgType.WHITELIST_REMOVE, reqId(), RuleRemoveReq(r.id)) }
                                                }) { Text(DesktopStrings.remove, color = MaterialTheme.colorScheme.error) }
                                            }
                                        }
                                    }
                                }

                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp)) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(DesktopStrings.tagsTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedTextField(value = newTagName, onValueChange = { newTagName = it }, label = { Text(DesktopStrings.tagNameHint) }, modifier = Modifier.weight(1f), singleLine = true)
                                            Button(onClick = {
                                                if (newTagName.isNotBlank()) {
                                                    scope.launch {
                                                        client.sendCommand(LinkMsgType.TAGS_ADD, reqId(), TagAddReq(name = newTagName))
                                                        newTagName = ""
                                                    }
                                                }
                                            }) { Text(DesktopStrings.add) }
                                        }
                                        tags.forEach { t ->
                                            HorizontalDivider()
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text(t.name, style = MaterialTheme.typography.bodySmall)
                                                TextButton(onClick = {
                                                    scope.launch { client.sendCommand(LinkMsgType.TAGS_REMOVE, reqId(), TagRemoveReq(t.id)) }
                                                }) { Text(DesktopStrings.remove, color = MaterialTheme.colorScheme.error) }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        DesktopTab.SETTINGS -> {
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (uiState.isPaired) {
                                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(14.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("${uiState.pairedDeviceName} (${uiState.savedWssUrl})", style = MaterialTheme.typography.bodyMedium)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(onClick = { client.disconnect() }) { Text(DesktopStrings.disconnectBtn) }
                                                TextButton(onClick = { client.clearPairing() }) { Text(DesktopStrings.unpairBtn, color = MaterialTheme.colorScheme.error) }
                                            }
                                        }
                                    }
                                }

                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp)) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = if (isScanning) DesktopStrings.statusScanning else DesktopStrings.discoveredDevices,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (devices.isEmpty()) {
                                            Text(DesktopStrings.noDevicesFound, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9BA3B4))
                                        } else {
                                            devices.forEach { dev ->
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Column {
                                                        Text(dev.friendlyName, fontWeight = FontWeight.Medium)
                                                        Text("${dev.host}:${dev.port} · fp:${dev.fingerprintPrefix}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = Color(0xFF9BA3B4))
                                                    }
                                                    OutlinedButton(onClick = {
                                                        manualHostPort = "${dev.host}:${dev.port}"
                                                        selectedFingerprintPrefix = dev.fingerprintPrefix
                                                    }) { Text("Select") }
                                                }
                                            }
                                        }
                                    }
                                }

                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp)) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(DesktopStrings.manualPairTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        OutlinedTextField(value = manualHostPort, onValueChange = { manualHostPort = it }, label = { Text(DesktopStrings.hostPortLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                        OutlinedTextField(value = manualCode, onValueChange = { manualCode = it.filter { c -> c.isDigit() }.take(6) }, label = { Text(DesktopStrings.pairCodeLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                        Button(
                                            onClick = {
                                                if (manualHostPort.isNotBlank() && manualCode.length == 6) {
                                                    client.pairAndConnect(manualHostPort, manualCode, selectedFingerprintPrefix)
                                                }
                                            },
                                            enabled = manualHostPort.isNotBlank() && manualCode.length == 6
                                        ) { Text(DesktopStrings.pairBtn) }
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

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9BA3B4))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}
