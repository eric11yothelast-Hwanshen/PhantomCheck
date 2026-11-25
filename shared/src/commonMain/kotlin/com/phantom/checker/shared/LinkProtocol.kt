package com.phantom.checker.shared

/**
 * #449 / #450 手機端 ↔ 桌面端 區網連線協定常數與純 Kotlin DTO（零第三方依賴，雙端統一以 Gson 序列化）。
 */
object LinkProtocol {
    const val PROTOCOL_VERSION = 2
    const val DEFAULT_PORT = 49515
    const val MDNS_SERVICE_TYPE = "_phantomcheck._tcp."
    const val WS_PATH = "/link"
    const val IDLE_TIMEOUT_MS = 30L * 60L * 1000L
    const val PAIR_CODE_TTL_MS = 3L * 60L * 1000L
    const val MAX_PAIRED_CLIENTS = 2
    const val MAX_COMMANDS_PER_SEC = 5
    const val MAX_COMMAND_BURST = 12
    const val DEFAULT_COMMAND_TIMEOUT_MS = 8_500L
    const val LONG_AI_COMMAND_TIMEOUT_MS = 130_000L
}

object LinkErrorCode {
    const val VERSION_MISMATCH = "VERSION_MISMATCH"
    const val INVALID_PAIR_CODE = "INVALID_PAIR_CODE"
    const val PAIR_CODE_EXPIRED = "PAIR_CODE_EXPIRED"
    const val FINGERPRINT_MISMATCH = "FINGERPRINT_MISMATCH"
    const val UNKNOWN_INSTANCE = "UNKNOWN_INSTANCE"
    const val AUTH_FAILED = "AUTH_FAILED"
    const val TOO_MANY_CLIENTS = "TOO_MANY_CLIENTS"
    const val RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED"
    const val UNKNOWN_COMMAND = "UNKNOWN_COMMAND"
    const val UNSUPPORTED = "UNSUPPORTED"
    const val INVALID_REQUEST = "INVALID_REQUEST"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
    const val TIMEOUT = "TIMEOUT"
    const val TCP_UNREACHABLE = "TCP_UNREACHABLE"
    const val TLS_ERROR = "TLS_ERROR"
    const val HANDSHAKE_ERROR = "HANDSHAKE_ERROR"
    const val PORT_IN_USE = "PORT_IN_USE"
}

object LinkMsgType {
    const val HELLO = "hello"
    const val PAIR_REQUEST = "pair.request"
    const val PAIR_RESPONSE = "pair.response"
    const val AUTH_REQUEST = "auth.request"
    const val AUTH_RESPONSE = "auth.response"
    const val PING = "ping"
    const val PONG = "pong"
    const val ERROR = "error"
    const val UNPAIRED = "unpaired"

    // M2 Read channels
    const val MESSAGES_LIST = "messages.list"
    const val MESSAGES_LIST_RES = "messages.list.res"
    const val MESSAGES_SUBSCRIBE = "messages.subscribe"
    const val MESSAGES_SUBSCRIBE_RES = "messages.subscribe.res"
    const val MESSAGE_NEW = "message.new"
    const val MESSAGE_READ_SYNC = "message.read-sync"
    const val STATS_SNAPSHOT = "stats.snapshot"
    const val STATS_SNAPSHOT_RES = "stats.snapshot.res"
    const val CHAT_SEND = "chat.send"
    const val CHAT_REPLY = "chat.reply"
    const val CHAT_HISTORY = "chat.history"
    const val CHAT_HISTORY_RES = "chat.history.res"

    // M2 Write channels
    const val MESSAGES_MARK_READ = "messages.markRead"
    const val MESSAGES_MARK_READ_RES = "messages.markRead.res"
    const val TODOS_LIST = "todos.list"
    const val TODOS_LIST_RES = "todos.list.res"
    const val TODOS_TOGGLE = "todos.toggle"
    const val TODOS_TOGGLE_RES = "todos.toggle.res"
    const val QUICK_REPLY_SEND = "quickReply.send"
    const val QUICK_REPLY_SEND_RES = "quickReply.send.res"
    const val WHITELIST_LIST = "whitelist.list"
    const val WHITELIST_LIST_RES = "whitelist.list.res"
    const val WHITELIST_ADD = "whitelist.add"
    const val WHITELIST_ADD_RES = "whitelist.add.res"
    const val WHITELIST_REMOVE = "whitelist.remove"
    const val WHITELIST_REMOVE_RES = "whitelist.remove.res"
    const val TAGS_LIST = "tags.list"
    const val TAGS_LIST_RES = "tags.list.res"
    const val TAGS_ADD = "tags.add"
    const val TAGS_ADD_RES = "tags.add.res"
    const val TAGS_REMOVE = "tags.remove"
    const val TAGS_REMOVE_RES = "tags.remove.res"

    // #467 Bidirectional sync extensions (PROTOCOL_VERSION = 1, backwards-compatible)
    const val MESSAGES_STAR_TOGGLE = "messages.star.toggle"
    const val MESSAGES_STAR_TOGGLE_RES = "messages.star.toggle.res"
    const val MESSAGE_STAR_SYNC = "message.star.sync"
    const val MESSAGES_TAG_APPLY = "messages.tag.apply"
    const val MESSAGES_TAG_APPLY_RES = "messages.tag.apply.res"
    const val MESSAGES_TAG_REMOVE = "messages.tag.remove"
    const val MESSAGES_TAG_REMOVE_RES = "messages.tag.remove.res"
    const val TODOS_ADD = "todos.add"
    const val TODOS_ADD_RES = "todos.add.res"
    const val TODOS_EDIT = "todos.edit"
    const val TODOS_EDIT_RES = "todos.edit.res"
    const val TODOS_DELETE = "todos.delete"
    const val TODOS_DELETE_RES = "todos.delete.res"
    const val NOTES_LIST = "notes.list"
    const val NOTES_LIST_RES = "notes.list.res"
    const val NOTE_ADD = "note.add"
    const val NOTE_ADD_RES = "note.add.res"
    const val MESSAGE_DEEP_ANALYZE = "message.deepAnalyze"
    const val MESSAGE_DEEP_ANALYZE_RES = "message.deepAnalyze.res"
    const val MUTED_APPS_LIST = "muted.apps.list"
    const val MUTED_APPS_LIST_RES = "muted.apps.list.res"
    const val MUTED_APPS_TOGGLE = "muted.apps.toggle"
    const val MUTED_APPS_TOGGLE_RES = "muted.apps.toggle.res"
    const val MESSAGES_CONVERSATION = "messages.conversation"
    const val MESSAGES_CONVERSATION_RES = "messages.conversation.res"
    const val PHONE_STATUS_GET = "phone.status.get"
    const val PHONE_STATUS_RES = "phone.status.res"
    const val CHAT_SESSIONS_LIST = "chat.sessions.list"
    const val CHAT_SESSIONS_LIST_RES = "chat.sessions.list.res"
    const val CHAT_SESSION_NEW = "chat.session.new"
    const val CHAT_SESSION_NEW_RES = "chat.session.new.res"
}

data class LinkEnvelope(
    val type: String = "",
    val reqId: String = "",
    val payloadJson: String = ""
)

data class QrPairingPayload(
    val wssUrl: String = "",
    val fingerprint: String = "",
    val code: String = "",
    val deviceName: String = "",
    val protocolVersion: Int = LinkProtocol.PROTOCOL_VERSION
)

/**
 * #476 / #477 桌面伺服器產出之配對 QR JSON（手機掃描連線用）。
 */
data class QrPairingPayloadV2(
    val v: Int = LinkProtocol.PROTOCOL_VERSION,
    val name: String = "",
    val ip: String = "",
    val port: Int = LinkProtocol.DEFAULT_PORT,
    val fp: String = "",
    val code: String = ""
)

data class HelloPayload(
    val protocolVersion: Int = LinkProtocol.PROTOCOL_VERSION,
    val deviceName: String = "",
    val certFingerprint: String = "",
    val challenge: String = "",
    val serverInstanceId: String = ""
)

data class PairRequestPayload(
    val instanceId: String = "",
    val desktopName: String = "",
    val code: String = "",
    val protocolVersion: Int = LinkProtocol.PROTOCOL_VERSION,
    val expectedFingerprint: String = ""
)

data class PairResponsePayload(
    val ok: Boolean = false,
    val hmacSecret: String = "",
    val deviceName: String = "",
    val certFingerprint: String = "",
    val errorCode: String = "",
    val errorMessage: String = ""
)

data class AuthRequestPayload(
    val instanceId: String = "",
    val hmacResponse: String = "",
    val protocolVersion: Int = LinkProtocol.PROTOCOL_VERSION,
    val expectedFingerprint: String = ""
)

data class AuthResponsePayload(
    val ok: Boolean = false,
    val deviceName: String = "",
    val errorCode: String = "",
    val errorMessage: String = ""
)

data class ErrorPayload(
    val errorCode: String = "",
    val errorMessage: String = ""
)

// ---- M2 DTOs (嚴格欄位白名單：不含全文、敏感記憶、通訊錄或日曆原始內容) ----

data class MessageSummaryDto(
    val id: Long = 0L,
    val packageName: String = "",
    val sender: String = "",
    val preview: String = "",
    val timestamp: Long = 0L,
    val riskLabel: String = "safe",
    val isStarred: Boolean = false,
    val isRead: Boolean = false,
    val conversationKey: String = "",
    val tagIds: List<Long> = emptyList(),
    val appLabel: String = "",
    val isGroup: Boolean = false
)

data class MessagesListReq(
    val filter: String = "all",
    val offset: Int = 0,
    val limit: Int = 40,
    val sinceCursor: Long = 0L
)

data class MessagesListRes(
    val items: List<MessageSummaryDto> = emptyList(),
    val latestCursor: Long = 0L,
    val hasMore: Boolean = false
)

data class MessageNewPush(
    val items: List<MessageSummaryDto> = emptyList(),
    val latestCursor: Long = 0L
)

data class MessageReadSyncPush(
    val ids: List<Long> = emptyList(),
    val all: Boolean = false
)

data class MarkReadReq(
    val ids: List<Long> = emptyList(),
    val all: Boolean = false,
    val packageName: String = "",
    val conversationKey: String = ""
)

data class MarkReadRes(
    val ok: Boolean = false,
    val updatedCount: Int = 0,
    val note: String = ""
)

data class DayCountDto(
    val label: String = "",
    val count: Int = 0
)

data class HourCountDto(
    val hour: Int = 0,
    val count: Int = 0
)

data class ActiveAppStatDto(
    val packageName: String = "",
    val appLabel: String = "",
    val count: Int = 0,
    val colorHex: String = ""
)

data class StatsSnapshotDto(
    val total: Int = 0,
    val today: Int = 0,
    val unread: Int = 0,
    val scam: Int = 0,
    val latestPackageName: String = "",
    val recentPackages: List<String> = emptyList(),
    val suspicious: Int = 0,
    val dailyCounts: List<DayCountDto> = emptyList(),
    val hourlyCounts: List<HourCountDto> = emptyList(),
    val dailyCounts30: List<DayCountDto> = emptyList(),
    val activeAppsToday: List<ActiveAppStatDto> = emptyList(),
    val weeklyTopApps: List<ActiveAppStatDto> = emptyList()
)

data class ChatSendReq(
    val text: String = "",
    val sessionId: Long = 0L
)

data class ChatReplyRes(
    val ok: Boolean = false,
    val sessionId: Long = 0L,
    val role: String = "assistant",
    val content: String = "",
    val errorCode: String = "",
    val errorMessage: String = ""
)

data class ChatHistoryReq(
    val sessionId: Long = 0L
)

data class ChatBubbleDto(
    val id: Long = 0L,
    val role: String = "",
    val content: String = "",
    val createdAt: Long = 0L
)

data class ChatHistoryRes(
    val sessionId: Long = 0L,
    val messages: List<ChatBubbleDto> = emptyList()
)

data class TodoItemDto(
    val id: Long = 0L,
    val title: String = "",
    val dueAt: Long = 0L,
    val isDone: Boolean = false,
    val repeatRule: String = ""
)

data class TodosListReq(
    val done: Boolean? = null
)

data class TodosListRes(
    val items: List<TodoItemDto> = emptyList()
)

data class TodosToggleReq(
    val id: Long = 0L,
    val done: Boolean = true
)

data class TodosToggleRes(
    val ok: Boolean = false,
    val id: Long = 0L,
    val isDone: Boolean = false,
    val spawnedNextId: Long = 0L,
    val spawnedDueAt: Long = 0L,
    val errorMessage: String = ""
)

data class QuickReplySendReq(
    val packageName: String = "",
    val sender: String = "",
    val conversationKey: String = "",
    val text: String = ""
)

data class QuickReplySendRes(
    val ok: Boolean = false,
    val errorCode: String = "",
    val errorMessage: String = ""
)

data class RuleItemDto(
    val id: Long = 0L,
    val kind: String = "",
    val ruleText: String = "",
    val sender: String = "",
    val packageName: String = "",
    val isEnabled: Boolean = true
)

data class WhitelistAddReq(
    val sender: String = "",
    val packageName: String = "",
    val ruleText: String = ""
)

data class RuleRemoveReq(
    val id: Long = 0L
)

data class RulesListRes(
    val items: List<RuleItemDto> = emptyList()
)

data class RuleMutateRes(
    val ok: Boolean = false,
    val message: String = "",
    val item: RuleItemDto? = null
)

data class TagItemDto(
    val id: Long = 0L,
    val name: String = "",
    val colorHex: String = "#6EA8FE"
)

data class TagsListRes(
    val items: List<TagItemDto> = emptyList()
)

data class TagAddReq(
    val name: String = "",
    val colorHex: String = "#6EA8FE"
)

data class TagRemoveReq(
    val id: Long = 0L
)

data class TagMutateRes(
    val ok: Boolean = false,
    val message: String = "",
    val item: TagItemDto? = null
)

// ---- #467 Bidirectional Sync DTOs ----

data class StarToggleReq(
    val id: Long = 0L,
    val starred: Boolean = true
)

data class StarToggleRes(
    val ok: Boolean = false,
    val id: Long = 0L,
    val isStarred: Boolean = false,
    val errorMessage: String = ""
)

data class MessageStarSyncPush(
    val id: Long = 0L,
    val isStarred: Boolean = false
)

data class MessageTagReq(
    val messageId: Long = 0L,
    val tagId: Long = 0L
)

data class MessageTagRes(
    val ok: Boolean = false,
    val messageId: Long = 0L,
    val tagIds: List<Long> = emptyList(),
    val errorMessage: String = ""
)

data class TodoAddReq(
    val title: String = "",
    val dueAt: Long = 0L,
    val repeatRule: String = "",
    val notes: String = ""
)

data class TodoEditReq(
    val id: Long = 0L,
    val title: String = "",
    val dueAt: Long = 0L,
    val repeatRule: String = "",
    val notes: String = ""
)

data class TodoDeleteReq(
    val id: Long = 0L
)

data class TodoMutateRes(
    val ok: Boolean = false,
    val item: TodoItemDto? = null,
    val deletedId: Long = 0L,
    val errorMessage: String = ""
)

data class NoteItemDto(
    val id: Long = 0L,
    val content: String = "",
    val pinned: Boolean = false,
    val source: String = "manual",
    val relatedMessageId: Long = 0L,
    val relatedAppLabel: String = "",
    val updatedAt: Long = 0L
)

data class NotesListReq(
    val limit: Int = 50
)

data class NotesListRes(
    val items: List<NoteItemDto> = emptyList()
)

data class NoteAddReq(
    val content: String = "",
    val relatedMessageId: Long = 0L
)

data class NoteMutateRes(
    val ok: Boolean = false,
    val item: NoteItemDto? = null,
    val errorMessage: String = ""
)

data class DeepAnalyzeReq(
    val id: Long = 0L
)

data class DeepAnalyzeRes(
    val ok: Boolean = false,
    val messageId: Long = 0L,
    val markdown: String = "",
    val errorCode: String = "",
    val errorMessage: String = ""
)

data class MutedAppDto(
    val packageName: String = "",
    val appLabel: String = "",
    val muted: Boolean = false
)

data class MutedAppsListRes(
    val items: List<MutedAppDto> = emptyList()
)

data class MutedAppToggleReq(
    val packageName: String = "",
    val muted: Boolean = true
)

data class MutedAppToggleRes(
    val ok: Boolean = false,
    val packageName: String = "",
    val muted: Boolean = false,
    val errorMessage: String = ""
)

data class ConversationReq(
    val packageName: String = "",
    val conversationKey: String = "",
    val offset: Int = 0,
    val limit: Int = 50
)

data class ConversationRes(
    val packageName: String = "",
    val conversationKey: String = "",
    val items: List<MessageSummaryDto> = emptyList(),
    val hasMore: Boolean = false
)

data class PhoneStatusDto(
    val batteryPercent: Int = -1,
    val isCharging: Boolean = false,
    val serverUptimeMs: Long = 0L,
    val deviceName: String = ""
)

data class ChatSessionSummaryDto(
    val id: Long = 0L,
    val title: String = "",
    val updatedAt: Long = 0L
)

data class ChatSessionsListRes(
    val sessions: List<ChatSessionSummaryDto> = emptyList()
)

data class ChatSessionNewReq(
    val title: String = ""
)

data class ChatSessionNewRes(
    val ok: Boolean = false,
    val session: ChatSessionSummaryDto? = null,
    val errorMessage: String = ""
)

