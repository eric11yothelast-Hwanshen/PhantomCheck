package com.phantom.checker.service

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.google.gson.Gson
import com.phantom.checker.data.AiRuleEntity
import com.phantom.checker.data.ChatMessageEntity
import com.phantom.checker.data.ChatSessionEntity
import com.phantom.checker.data.TagEntity
import com.phantom.checker.data.WhitelistRuleHelper
import com.phantom.checker.network.AiClient
import com.phantom.checker.network.ChatMessage
import com.phantom.checker.shared.AuthRequestPayload
import com.phantom.checker.shared.AuthResponsePayload
import com.phantom.checker.shared.ChatBubbleDto
import com.phantom.checker.shared.ChatHistoryReq
import com.phantom.checker.shared.ChatHistoryRes
import com.phantom.checker.shared.ChatReplyRes
import com.phantom.checker.shared.ChatSendReq
import com.phantom.checker.shared.ErrorPayload
import com.phantom.checker.shared.HelloPayload
import com.phantom.checker.shared.LinkEnvelope
import com.phantom.checker.shared.LinkErrorCode
import com.phantom.checker.shared.LinkMsgType
import com.phantom.checker.shared.LinkProtocol
import com.phantom.checker.shared.MarkReadReq
import com.phantom.checker.shared.MarkReadRes
import com.phantom.checker.shared.MessageNewPush
import com.phantom.checker.shared.MessageReadSyncPush
import com.phantom.checker.shared.MessageSummaryDto
import com.phantom.checker.shared.MessagesListReq
import com.phantom.checker.shared.MessagesListRes
import com.phantom.checker.shared.PairRequestPayload
import com.phantom.checker.shared.PairResponsePayload
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
import com.phantom.checker.R
import com.phantom.checker.util.DesktopLinkStore
import com.phantom.checker.util.PairCodeCheckResult
import com.phantom.checker.util.PairedDesktop
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.origin
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.BindException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

data class ConnectedClientInfo(
    val instanceId: String,
    val name: String,
    val remoteIp: String,
    val connectedAtMs: Long
)

data class LinkServerStatus(
    val isRunning: Boolean = false,
    val port: Int = LinkProtocol.DEFAULT_PORT,
    val localIp: String = "",
    val fingerprint: String = "",
    val connectedClients: List<ConnectedClientInfo> = emptyList(),
    val lastError: String? = null
)

/**
 * #449 / #450 / #465 手機端區域網路 WebSocket 伺服器（wss-only，Netty engine + 自簽 PKCS12 + NsdManager）。
 */
object DesktopLinkServer {

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(LinkServerStatus())
    val status: StateFlow<LinkServerStatus> = _status.asStateFlow()

    @Volatile
    private var server: EmbeddedServer<*, *>? = null

    @Volatile
    private var nsdManager: NsdManager? = null

    @Volatile
    private var nsdListener: NsdManager.RegistrationListener? = null

    @Volatile
    private var idleWatchJob: Job? = null

    @Volatile
    private var lastActivityMs: Long = System.currentTimeMillis()

    internal data class ClientSession(
        val info: ConnectedClientInfo,
        val ws: WebSocketSession
    )

    private val activeSessions = ConcurrentHashMap<String, ClientSession>()
    private val subscribedClients = ConcurrentHashMap.newKeySet<String>()
    private val clientCursors = ConcurrentHashMap<String, Long>()

    @Volatile
    private var messagePushJob: Job? = null

    /** M2 可掛載的斷線/連線事件回呼 */
    @Volatile
    var onSessionChanged: ((Context, Int) -> Unit)? = null

    /** #466 / #471 前景停留於連線頁時刷新活動時間，避免誤觸 30 分鐘閒置停置 */
    fun touchActivity() {
        lastActivityMs = System.currentTimeMillis()
    }

    @Synchronized
    fun start(ctx: Context) {
        val appCtx = ctx.applicationContext
        if (server != null) return

        val port = DesktopLinkStore.getServerPort(appCtx)
        val localIp = getLocalWifiIpAddress().orEmpty()
        val tls = runCatching { DesktopLinkTls.loadOrCreate(appCtx) }.getOrElse { err ->
            _status.value = LinkServerStatus(
                isRunning = false,
                port = port,
                localIp = localIp,
                lastError = err.message ?: "TLS init failed"
            )
            return
        }

        try {
            ServerSocket().use { sock ->
                sock.reuseAddress = true
                sock.bind(InetSocketAddress("0.0.0.0", port))
            }
        } catch (_: BindException) {
            _status.value = LinkServerStatus(
                isRunning = false,
                port = port,
                localIp = localIp,
                lastError = appCtx.getString(R.string.link_hub_port_in_use, port)
            )
            return
        } catch (err: Exception) {
            _status.value = LinkServerStatus(
                isRunning = false,
                port = port,
                localIp = localIp,
                lastError = err.message ?: err.javaClass.simpleName
            )
            return
        }

        var createdEngine: EmbeddedServer<*, *>? = null
        runCatching {
            val engine = embeddedServer(Netty, configure = {
                connectionGroupSize = 1
                workerGroupSize = 2
                callGroupSize = 4
                sslConnector(
                    keyStore = tls.keyStore,
                    keyAlias = DesktopLinkTls.KEY_ALIAS,
                    keyStorePassword = { tls.keyPassword.toCharArray() },
                    privateKeyPassword = { tls.keyPassword.toCharArray() }
                ) {
                    this.host = "0.0.0.0"
                    this.port = port
                }
            }) {
                install(WebSockets) {
                    pingPeriod = 15.seconds
                    timeout = 30.seconds
                    maxFrameSize = 256 * 1024L
                }
                routing {
                    webSocket(LinkProtocol.WS_PATH) {
                        val remoteIp = runCatching { call.request.origin.remoteAddress }
                            .getOrNull()
                            ?.takeIf { it.isNotBlank() }
                            ?: "unknown"
                        handleClientConnection(appCtx, tls.certFingerprintSha256, remoteIp, this)
                    }
                }
            }
            createdEngine = engine
            engine.start(wait = false)
            server = engine
            lastActivityMs = System.currentTimeMillis()
            _status.value = LinkServerStatus(
                isRunning = true,
                port = port,
                localIp = localIp,
                fingerprint = tls.certFingerprintSha256,
                connectedClients = emptyList(),
                lastError = null
            )
            registerNsd(appCtx, port, tls.certFingerprintSha256)
            startIdleWatcher(appCtx)
        }.onFailure { err ->
            runCatching { createdEngine?.stop(200, 500) }
            server = null
            val errMsg = if (err is BindException || err.cause is BindException) {
                appCtx.getString(R.string.link_hub_port_in_use, port)
            } else {
                err.message ?: err.javaClass.simpleName
            }
            _status.value = LinkServerStatus(
                isRunning = false,
                port = port,
                localIp = localIp,
                lastError = errMsg
            )
        }
    }

    @Synchronized
    fun stop(ctx: Context? = null) {
        idleWatchJob?.cancel()
        idleWatchJob = null
        messagePushJob?.cancel()
        messagePushJob = null
        subscribedClients.clear()
        clientCursors.clear()
        unregisterNsd()
        val sessions = activeSessions.values.toList()
        activeSessions.clear()
        scope.launch {
            sessions.forEach {
                runCatching { it.ws.close(CloseReason(CloseReason.Codes.NORMAL, "Server stopped")) }
            }
        }
        val srv = server
        server = null
        if (srv != null) {
            scope.launch {
                runCatching { srv.stop(300, 1000) }
            }
        }
        val cur = _status.value
        _status.value = cur.copy(isRunning = false, connectedClients = emptyList())
        if (ctx != null) {
            onSessionChanged?.invoke(ctx.applicationContext, 0)
        }
    }

    fun revokeDevice(ctx: Context, instanceId: String) {
        val appCtx = ctx.applicationContext
        DesktopLinkStore.removePairedDevice(appCtx, instanceId)
        subscribedClients.remove(instanceId)
        clientCursors.remove(instanceId)
        val session = activeSessions.remove(instanceId)
        if (session != null) {
            refreshConnectedClients(appCtx)
            scope.launch {
                runCatching {
                    sendEnvelope(session.ws, LinkMsgType.UNPAIRED, "", ErrorPayload(LinkErrorCode.AUTH_FAILED, "Unpaired by phone"))
                    session.ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unpaired"))
                }
            }
        }
    }

    suspend fun broadcastToAll(type: String, payload: Any) {
        val env = LinkEnvelope(type = type, reqId = "", payloadJson = gson.toJson(payload))
        val text = gson.toJson(env)
        activeSessions.values.toList().forEach { session ->
            runCatching { session.ws.send(Frame.Text(text)) }
        }
    }

    suspend fun sendEnvelope(ws: WebSocketSession, type: String, reqId: String, payload: Any) {
        val env = LinkEnvelope(
            type = type,
            reqId = reqId,
            payloadJson = gson.toJson(payload)
        )
        ws.send(Frame.Text(gson.toJson(env)))
    }

    private suspend fun handleClientConnection(
        appCtx: Context,
        certFingerprint: String,
        remoteIp: String,
        ws: WebSocketSession
    ) {
        val challenge = DesktopLinkStore.randomHex(24)
        val deviceName = Build.MODEL.ifBlank { "Android" }

        runCatching {
            sendEnvelope(
                ws,
                LinkMsgType.HELLO,
                "",
                HelloPayload(
                    protocolVersion = LinkProtocol.PROTOCOL_VERSION,
                    deviceName = deviceName,
                    certFingerprint = certFingerprint,
                    challenge = challenge
                )
            )
        }.onFailure { return }

        var authedInstanceId: String? = null
        var mySession: ClientSession? = null
        var bucketTokens = LinkProtocol.MAX_COMMAND_BURST.toDouble()
        var lastRefillMs = System.currentTimeMillis()

        try {
            for (frame in ws.incoming) {
                if (frame !is Frame.Text) continue
                lastActivityMs = System.currentTimeMillis()
                val env = runCatching { gson.fromJson(frame.readText(), LinkEnvelope::class.java) }.getOrNull()
                    ?: continue

                if (authedInstanceId == null) {
                    when (env.type) {
                        LinkMsgType.PAIR_REQUEST -> {
                            val req = runCatching {
                                gson.fromJson(env.payloadJson, PairRequestPayload::class.java)
                            }.getOrNull()
                            if (req == null || req.instanceId.isBlank()) {
                                DesktopLinkStore.recordUnknownAttempt(appCtx, remoteIp, "invalid_pair_request")
                                sendEnvelope(ws, LinkMsgType.PAIR_RESPONSE, env.reqId, PairResponsePayload(ok = false, errorCode = LinkErrorCode.INVALID_REQUEST, errorMessage = "Invalid pair request"))
                                ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid pair request"))
                                return
                            }
                            if (req.protocolVersion != LinkProtocol.PROTOCOL_VERSION) {
                                DesktopLinkStore.recordUnknownAttempt(appCtx, remoteIp, "version_mismatch:${req.protocolVersion}", req.instanceId)
                                sendEnvelope(ws, LinkMsgType.PAIR_RESPONSE, env.reqId, PairResponsePayload(ok = false, errorCode = LinkErrorCode.VERSION_MISMATCH, errorMessage = "Protocol version mismatch"))
                                ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Version mismatch"))
                                return
                            }
                            if (req.expectedFingerprint.isNotBlank() && !req.expectedFingerprint.equals(certFingerprint, ignoreCase = true)) {
                                DesktopLinkStore.recordUnknownAttempt(appCtx, remoteIp, "fingerprint_mismatch", req.instanceId)
                                sendEnvelope(ws, LinkMsgType.PAIR_RESPONSE, env.reqId, PairResponsePayload(ok = false, errorCode = LinkErrorCode.FINGERPRINT_MISMATCH, errorMessage = "Certificate fingerprint mismatch"))
                                ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Fingerprint mismatch"))
                                return
                            }
                            when (DesktopLinkStore.consumePairCode(req.code)) {
                                PairCodeCheckResult.EXPIRED -> {
                                    DesktopLinkStore.recordUnknownAttempt(appCtx, remoteIp, "pair_code_expired", req.instanceId)
                                    sendEnvelope(ws, LinkMsgType.PAIR_RESPONSE, env.reqId, PairResponsePayload(ok = false, errorCode = LinkErrorCode.PAIR_CODE_EXPIRED, errorMessage = "Pairing code expired"))
                                    ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Code expired"))
                                    return
                                }
                                PairCodeCheckResult.INVALID -> {
                                    DesktopLinkStore.recordUnknownAttempt(appCtx, remoteIp, "invalid_pair_code", req.instanceId)
                                    sendEnvelope(ws, LinkMsgType.PAIR_RESPONSE, env.reqId, PairResponsePayload(ok = false, errorCode = LinkErrorCode.INVALID_PAIR_CODE, errorMessage = "Invalid pairing code"))
                                    ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid code"))
                                    return
                                }
                                PairCodeCheckResult.VALID -> Unit
                            }
                            if (activeSessions.size >= LinkProtocol.MAX_PAIRED_CLIENTS && !activeSessions.containsKey(req.instanceId)) {
                                sendEnvelope(ws, LinkMsgType.PAIR_RESPONSE, env.reqId, PairResponsePayload(ok = false, errorCode = LinkErrorCode.TOO_MANY_CLIENTS, errorMessage = "Max 2 concurrent clients reached"))
                                ws.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Too many clients"))
                                return
                            }
                            val secret = DesktopLinkStore.randomHex(32)
                            val now = System.currentTimeMillis()
                            DesktopLinkStore.upsertPairedDevice(
                                appCtx,
                                PairedDesktop(
                                    instanceId = req.instanceId,
                                    name = req.desktopName.ifBlank { "Desktop" },
                                    hmacSecret = secret,
                                    pairedAt = now,
                                    lastSeenAt = now
                                )
                            )
                            authedInstanceId = req.instanceId
                            val info = ConnectedClientInfo(req.instanceId, req.desktopName.ifBlank { "Desktop" }, remoteIp, now)
                            val newSession = ClientSession(info, ws)
                            mySession = newSession
                            activeSessions.put(req.instanceId, newSession)?.ws?.let { old -> runCatching { old.close() } }
                            refreshConnectedClients(appCtx)
                            sendEnvelope(
                                ws,
                                LinkMsgType.PAIR_RESPONSE,
                                env.reqId,
                                PairResponsePayload(
                                    ok = true,
                                    hmacSecret = secret,
                                    deviceName = deviceName,
                                    certFingerprint = certFingerprint
                                )
                            )
                        }

                        LinkMsgType.AUTH_REQUEST -> {
                            val req = runCatching {
                                gson.fromJson(env.payloadJson, AuthRequestPayload::class.java)
                            }.getOrNull()
                            if (req == null || req.instanceId.isBlank()) {
                                DesktopLinkStore.recordUnknownAttempt(appCtx, remoteIp, "invalid_auth_request")
                                sendEnvelope(ws, LinkMsgType.AUTH_RESPONSE, env.reqId, AuthResponsePayload(ok = false, errorCode = LinkErrorCode.INVALID_REQUEST, errorMessage = "Invalid auth request"))
                                ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid auth"))
                                return
                            }
                            if (req.protocolVersion != LinkProtocol.PROTOCOL_VERSION) {
                                DesktopLinkStore.recordUnknownAttempt(appCtx, remoteIp, "version_mismatch:${req.protocolVersion}", req.instanceId)
                                sendEnvelope(ws, LinkMsgType.AUTH_RESPONSE, env.reqId, AuthResponsePayload(ok = false, errorCode = LinkErrorCode.VERSION_MISMATCH, errorMessage = "Protocol version mismatch"))
                                ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Version mismatch"))
                                return
                            }
                            if (req.expectedFingerprint.isNotBlank() && !req.expectedFingerprint.equals(certFingerprint, ignoreCase = true)) {
                                DesktopLinkStore.recordUnknownAttempt(appCtx, remoteIp, "fingerprint_mismatch", req.instanceId)
                                sendEnvelope(ws, LinkMsgType.AUTH_RESPONSE, env.reqId, AuthResponsePayload(ok = false, errorCode = LinkErrorCode.FINGERPRINT_MISMATCH, errorMessage = "Certificate fingerprint mismatch"))
                                ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Fingerprint mismatch"))
                                return
                            }
                            val paired = DesktopLinkStore.findPairedDevice(appCtx, req.instanceId)
                            if (paired == null) {
                                DesktopLinkStore.recordUnknownAttempt(appCtx, remoteIp, "unknown_instance", req.instanceId)
                                sendEnvelope(ws, LinkMsgType.AUTH_RESPONSE, env.reqId, AuthResponsePayload(ok = false, errorCode = LinkErrorCode.UNKNOWN_INSTANCE, errorMessage = "Unknown desktop instance"))
                                ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unknown instance"))
                                return
                            }
                            val expectedHmac = DesktopLinkStore.computeHmacSha256Hex(paired.hmacSecret, challenge)
                            if (!expectedHmac.equals(req.hmacResponse, ignoreCase = true)) {
                                DesktopLinkStore.recordUnknownAttempt(appCtx, remoteIp, "hmac_auth_failed", req.instanceId)
                                sendEnvelope(ws, LinkMsgType.AUTH_RESPONSE, env.reqId, AuthResponsePayload(ok = false, errorCode = LinkErrorCode.AUTH_FAILED, errorMessage = "HMAC challenge failed"))
                                ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Auth failed"))
                                return
                            }
                            if (activeSessions.size >= LinkProtocol.MAX_PAIRED_CLIENTS && !activeSessions.containsKey(req.instanceId)) {
                                sendEnvelope(ws, LinkMsgType.AUTH_RESPONSE, env.reqId, AuthResponsePayload(ok = false, errorCode = LinkErrorCode.TOO_MANY_CLIENTS, errorMessage = "Max 2 concurrent clients reached"))
                                ws.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Too many clients"))
                                return
                            }
                            val now = System.currentTimeMillis()
                            DesktopLinkStore.updateLastSeen(appCtx, req.instanceId, now)
                            authedInstanceId = req.instanceId
                            val info = ConnectedClientInfo(req.instanceId, paired.name, remoteIp, now)
                            val newSession = ClientSession(info, ws)
                            mySession = newSession
                            activeSessions.put(req.instanceId, newSession)?.ws?.let { old -> runCatching { old.close() } }
                            refreshConnectedClients(appCtx)
                            sendEnvelope(ws, LinkMsgType.AUTH_RESPONSE, env.reqId, AuthResponsePayload(ok = true, deviceName = deviceName))
                        }

                        else -> {
                            DesktopLinkStore.recordUnknownAttempt(appCtx, remoteIp, "unauthenticated_command:${env.type}")
                            sendEnvelope(ws, LinkMsgType.ERROR, env.reqId, ErrorPayload(LinkErrorCode.AUTH_FAILED, "Authentication required"))
                            ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthenticated"))
                            return
                        }
                    }
                    continue
                }

                // 已驗證階段：速率限制（持續 5 指令/秒＋突發桶 12，超限斷線）
                val now = System.currentTimeMillis()
                val elapsedSec = (now - lastRefillMs).coerceAtLeast(0L) / 1000.0
                if (elapsedSec > 0.0) {
                    bucketTokens = (bucketTokens + elapsedSec * LinkProtocol.MAX_COMMANDS_PER_SEC)
                        .coerceAtMost(LinkProtocol.MAX_COMMAND_BURST.toDouble())
                    lastRefillMs = now
                }
                if (bucketTokens < 1.0) {
                    DesktopLinkStore.recordAuditLog(appCtx, authedInstanceId, env.type, false, "rate_limit_exceeded")
                    sendEnvelope(ws, LinkMsgType.ERROR, env.reqId, ErrorPayload(LinkErrorCode.RATE_LIMIT_EXCEEDED, "Rate limit exceeded"))
                    ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Rate limit exceeded"))
                    return
                }
                bucketTokens -= 1.0

                if (env.type == LinkMsgType.PING) {
                    sendEnvelope(ws, LinkMsgType.PONG, env.reqId, emptyMap<String, String>())
                    continue
                }

                handleM2Command(appCtx, authedInstanceId, env, ws)
            }
        } catch (_: Exception) {
        } finally {
            val id = authedInstanceId
            val sess = mySession
            if (id != null && sess != null) {
                if (activeSessions.remove(id, sess)) {
                    subscribedClients.remove(id)
                    clientCursors.remove(id)
                    refreshConnectedClients(appCtx)
                }
            }
        }
    }

    private suspend fun handleM2Command(
        appCtx: Context,
        instanceId: String,
        env: LinkEnvelope,
        ws: WebSocketSession
    ) {
        val db = AppDatabaseHolder.db(appCtx)
        when (env.type) {
            LinkMsgType.MESSAGES_LIST -> {
                val req = runCatching { gson.fromJson(env.payloadJson, MessagesListReq::class.java) }.getOrNull() ?: MessagesListReq()
                val limit = req.limit.coerceIn(1, 60)
                val offset = req.offset.coerceAtLeast(0)
                val raw = when (req.filter) {
                    "unread" -> db.messageDao().unreadPaged(limit, offset)
                    "starred" -> db.messageDao().starredPaged(limit, offset)
                    "scam" -> db.messageDao().scamsPaged(limit, offset)
                    else -> db.messageDao().paged(limit, offset)
                }
                val filtered = if (req.sinceCursor > 0L) raw.filter { it.id > req.sinceCursor } else raw
                val items = filtered.map { m ->
                    MessageSummaryDto(
                        id = m.id,
                        packageName = m.packageName,
                        sender = m.sender,
                        preview = m.content.replace(Regex("\\s+"), " ").trim().take(90),
                        timestamp = m.timestamp,
                        riskLabel = m.riskLabel,
                        isStarred = m.isStarred,
                        isRead = m.isRead
                    )
                }
                val latestCursor = items.maxOfOrNull { it.id } ?: req.sinceCursor
                sendEnvelope(
                    ws,
                    LinkMsgType.MESSAGES_LIST_RES,
                    env.reqId,
                    MessagesListRes(items = items, latestCursor = latestCursor, hasMore = raw.size >= limit)
                )
            }

            LinkMsgType.MESSAGES_SUBSCRIBE -> {
                subscribedClients.add(instanceId)
                val latest = db.messageDao().paged(1, 0).firstOrNull()?.id ?: 0L
                val cursor = clientCursors.compute(instanceId) { _, prev ->
                    maxOf(prev ?: 0L, latest)
                } ?: latest
                ensureMessagePushLoop(appCtx)
                sendEnvelope(ws, LinkMsgType.MESSAGES_SUBSCRIBE_RES, env.reqId, mapOf("subscribed" to true, "cursor" to cursor))
            }

            LinkMsgType.MESSAGES_MARK_READ -> {
                val req = runCatching { gson.fromJson(env.payloadJson, MarkReadReq::class.java) }.getOrNull() ?: MarkReadReq()
                var updated = 0
                if (req.all) {
                    db.messageDao().markAllRead()
                    updated = -1
                    broadcastToAll(LinkMsgType.MESSAGE_READ_SYNC, MessageReadSyncPush(all = true))
                } else if (req.ids.isNotEmpty()) {
                    db.messageDao().markReadByIds(req.ids)
                    updated = req.ids.size
                    broadcastToAll(LinkMsgType.MESSAGE_READ_SYNC, MessageReadSyncPush(ids = req.ids))
                } else if (req.packageName.isNotBlank() && req.conversationKey.isNotBlank()) {
                    val ids = db.messageDao().unreadIdsByConversation(req.packageName, req.conversationKey)
                    updated = db.messageDao().markReadByConversation(req.packageName, req.conversationKey)
                    if (ids.isNotEmpty()) {
                        broadcastToAll(LinkMsgType.MESSAGE_READ_SYNC, MessageReadSyncPush(ids = ids))
                    }
                }
                DesktopLinkStore.recordAuditLog(appCtx, instanceId, LinkMsgType.MESSAGES_MARK_READ, true, "count=$updated")
                sendEnvelope(
                    ws,
                    LinkMsgType.MESSAGES_MARK_READ_RES,
                    env.reqId,
                    MarkReadRes(ok = true, updatedCount = updated, note = "Marked read in PhantomCheck list only")
                )
            }

            LinkMsgType.STATS_SNAPSHOT -> {
                val startOfDay = NotificationCollectorService.startOfTodayMillis()
                val snap = db.messageDao().statsSnapshot(startOfDay)
                sendEnvelope(
                    ws,
                    LinkMsgType.STATS_SNAPSHOT_RES,
                    env.reqId,
                    StatsSnapshotDto(
                        total = snap.total,
                        today = snap.today,
                        unread = snap.unread,
                        scam = snap.scam,
                        latestPackageName = snap.latestPackageName.orEmpty(),
                        recentPackages = snap.recentPackages()
                    )
                )
            }

            LinkMsgType.CHAT_HISTORY -> {
                val req = runCatching { gson.fromJson(env.payloadJson, ChatHistoryReq::class.java) }.getOrNull() ?: ChatHistoryReq()
                val sid = if (req.sessionId > 0L) {
                    req.sessionId
                } else {
                    db.chatDao().allSessions()
                        .firstOrNull { it.title != ProactiveEngine.PROACTIVE_SESSION_TITLE }?.id ?: 0L
                }
                val msgs = if (sid > 0L) {
                    db.chatDao().messagesForSession(sid).takeLast(50).map {
                        ChatBubbleDto(id = it.id, role = it.role, content = it.content, createdAt = it.createdAt)
                    }
                } else {
                    emptyList()
                }
                sendEnvelope(ws, LinkMsgType.CHAT_HISTORY_RES, env.reqId, ChatHistoryRes(sessionId = sid, messages = msgs))
            }

            LinkMsgType.CHAT_SEND -> {
                val req = runCatching { gson.fromJson(env.payloadJson, ChatSendReq::class.java) }.getOrNull() ?: ChatSendReq()
                val text = req.text.trim()
                if (text.isBlank()) {
                    DesktopLinkStore.recordAuditLog(appCtx, instanceId, LinkMsgType.CHAT_SEND, false, "empty_text")
                    sendEnvelope(ws, LinkMsgType.CHAT_REPLY, env.reqId, ChatReplyRes(ok = false, errorCode = LinkErrorCode.INVALID_REQUEST, errorMessage = "Empty message"))
                    return
                }
                DesktopLinkStore.recordAuditLog(appCtx, instanceId, LinkMsgType.CHAT_SEND, true, "len=${text.length}")
                scope.launch {
                    val sid = if (req.sessionId > 0L && db.chatDao().sessionById(req.sessionId) != null) {
                        req.sessionId
                    } else {
                        db.chatDao().allSessions()
                            .firstOrNull { it.title != ProactiveEngine.PROACTIVE_SESSION_TITLE }?.id
                            ?: db.chatDao().insertSession(ChatSessionEntity(title = text.take(20)))
                    }
                    val existing = db.chatDao().messagesForSession(sid).takeLast(12).map { ChatMessage(it.role, it.content) }
                    db.chatDao().insertMessage(ChatMessageEntity(sessionId = sid, role = ChatMessageEntity.ROLE_USER, content = text))
                    val result = AiClient.chatAssistant(
                        systemPrompt = AiClient.buildRagSummaryPrompt(),
                        history = existing,
                        userMessage = text,
                        context = appCtx
                    )
                    result.onSuccess { outcome ->
                        val reply = outcome.replyText.trim()
                        db.chatDao().insertMessage(ChatMessageEntity(sessionId = sid, role = ChatMessageEntity.ROLE_ASSISTANT, content = reply))
                        runCatching {
                            sendEnvelope(ws, LinkMsgType.CHAT_REPLY, env.reqId, ChatReplyRes(ok = true, sessionId = sid, role = "assistant", content = reply))
                        }
                    }.onFailure { err ->
                        runCatching {
                            sendEnvelope(ws, LinkMsgType.CHAT_REPLY, env.reqId, ChatReplyRes(ok = false, sessionId = sid, errorCode = LinkErrorCode.INTERNAL_ERROR, errorMessage = err.message ?: "Chat failed"))
                        }
                    }
                }
            }

            LinkMsgType.TODOS_LIST -> {
                val req = runCatching { gson.fromJson(env.payloadJson, TodosListReq::class.java) }.getOrNull() ?: TodosListReq()
                val all = db.todoDao().all()
                val filtered = if (req.done != null) all.filter { it.isDone == req.done } else all
                val items = filtered.map {
                    TodoItemDto(id = it.id, title = it.title, dueAt = it.dueAt, isDone = it.isDone, repeatRule = it.repeatRule)
                }
                sendEnvelope(ws, LinkMsgType.TODOS_LIST_RES, env.reqId, TodosListRes(items = items))
            }

            LinkMsgType.TODOS_TOGGLE -> {
                val req = runCatching { gson.fromJson(env.payloadJson, TodosToggleReq::class.java) }.getOrNull()
                val todo = if (req != null) db.todoDao().byId(req.id) else null
                if (req == null || todo == null) {
                    DesktopLinkStore.recordAuditLog(appCtx, instanceId, LinkMsgType.TODOS_TOGGLE, false, "not_found")
                    sendEnvelope(ws, LinkMsgType.TODOS_TOGGLE_RES, env.reqId, TodosToggleRes(ok = false, errorMessage = "Todo not found"))
                    return
                }
                // #440 / #450 嚴格複用 App 內單點入口，絕不複製邏輯
                val spawned = ReminderScheduler.completeTodoWithRecurrence(appCtx, todo, req.done)
                DesktopLinkStore.recordAuditLog(appCtx, instanceId, LinkMsgType.TODOS_TOGGLE, true, "id=${todo.id},done=${req.done}")
                sendEnvelope(
                    ws,
                    LinkMsgType.TODOS_TOGGLE_RES,
                    env.reqId,
                    TodosToggleRes(
                        ok = true,
                        id = todo.id,
                        isDone = req.done,
                        spawnedNextId = spawned?.id ?: 0L,
                        spawnedDueAt = spawned?.dueAt ?: 0L
                    )
                )
            }

            LinkMsgType.QUICK_REPLY_SEND -> {
                val req = runCatching { gson.fromJson(env.payloadJson, QuickReplySendReq::class.java) }.getOrNull()
                if (req == null || req.packageName.isBlank() || req.text.isBlank()) {
                    DesktopLinkStore.recordAuditLog(appCtx, instanceId, LinkMsgType.QUICK_REPLY_SEND, false, "invalid_request")
                    sendEnvelope(ws, LinkMsgType.QUICK_REPLY_SEND_RES, env.reqId, QuickReplySendRes(ok = false, errorCode = LinkErrorCode.INVALID_REQUEST, errorMessage = "Invalid quickReply request"))
                    return
                }
                val res = NotificationCollectorService.sendReplyToNotification(
                    context = appCtx,
                    packageName = req.packageName,
                    sender = req.sender,
                    contentSnippet = "",
                    replyText = req.text,
                    conversationKey = req.conversationKey.ifBlank { null }
                )
                when (res) {
                    is NotificationCollectorService.Companion.ReplySendResult.Success -> {
                        DesktopLinkStore.recordAuditLog(appCtx, instanceId, LinkMsgType.QUICK_REPLY_SEND, true, req.packageName)
                        sendEnvelope(ws, LinkMsgType.QUICK_REPLY_SEND_RES, env.reqId, QuickReplySendRes(ok = true))
                    }
                    is NotificationCollectorService.Companion.ReplySendResult.Unavailable -> {
                        DesktopLinkStore.recordAuditLog(appCtx, instanceId, LinkMsgType.QUICK_REPLY_SEND, false, "unsupported:${req.packageName}")
                        sendEnvelope(ws, LinkMsgType.QUICK_REPLY_SEND_RES, env.reqId, QuickReplySendRes(ok = false, errorCode = LinkErrorCode.UNSUPPORTED, errorMessage = res.reason))
                    }
                }
            }

            LinkMsgType.WHITELIST_LIST -> {
                val items = db.aiRuleDao().all()
                    .filter { it.kind == AiRuleEntity.KIND_WHITELIST }
                    .map { RuleItemDto(id = it.id, kind = it.kind, ruleText = it.ruleText, sender = it.sender.orEmpty(), packageName = it.packageName.orEmpty(), isEnabled = it.isEnabled) }
                sendEnvelope(ws, LinkMsgType.WHITELIST_LIST_RES, env.reqId, RulesListRes(items = items))
            }

            LinkMsgType.WHITELIST_ADD -> {
                val req = runCatching { gson.fromJson(env.payloadJson, WhitelistAddReq::class.java) }.getOrNull() ?: WhitelistAddReq()
                val text = req.ruleText.ifBlank { req.sender }.trim()
                if (text.isBlank()) {
                    DesktopLinkStore.recordAuditLog(appCtx, instanceId, LinkMsgType.WHITELIST_ADD, false, "empty")
                    sendEnvelope(ws, LinkMsgType.WHITELIST_ADD_RES, env.reqId, RuleMutateRes(ok = false, message = "Empty whitelist rule"))
                    return
                }
                val res = WhitelistRuleHelper.upsertWhitelist(
                    dao = db.aiRuleDao(),
                    ruleText = text,
                    sender = req.sender.trim().ifBlank { null },
                    packageName = req.packageName.trim().ifBlank { null }
                )
                DesktopLinkStore.recordAuditLog(appCtx, instanceId, LinkMsgType.WHITELIST_ADD, res.isSuccess, res.status.name)
                val dto = res.rule?.let { RuleItemDto(it.id, it.kind, it.ruleText, it.sender.orEmpty(), it.packageName.orEmpty(), it.isEnabled) }
                sendEnvelope(ws, LinkMsgType.WHITELIST_ADD_RES, env.reqId, RuleMutateRes(ok = res.isSuccess, message = res.message, item = dto))
            }

            LinkMsgType.WHITELIST_REMOVE -> {
                val req = runCatching { gson.fromJson(env.payloadJson, RuleRemoveReq::class.java) }.getOrNull()
                if (req == null || req.id <= 0L) {
                    DesktopLinkStore.recordAuditLog(appCtx, instanceId, LinkMsgType.WHITELIST_REMOVE, false, "invalid_id")
                    sendEnvelope(ws, LinkMsgType.WHITELIST_REMOVE_RES, env.reqId, RuleMutateRes(ok = false, message = "Invalid id"))
                    return
                }
                db.aiRuleDao().deleteById(req.id)
                DesktopLinkStore.recordAuditLog(appCtx, instanceId, LinkMsgType.WHITELIST_REMOVE, true, "id=${req.id}")
                sendEnvelope(ws, LinkMsgType.WHITELIST_REMOVE_RES, env.reqId, RuleMutateRes(ok = true, message = "Removed"))
            }

            LinkMsgType.TAGS_LIST -> {
                val items = db.tagDao().all().map { TagItemDto(id = it.id, name = it.name, colorHex = it.colorHex) }
                sendEnvelope(ws, LinkMsgType.TAGS_LIST_RES, env.reqId, TagsListRes(items = items))
            }

            LinkMsgType.TAGS_ADD -> {
                val req = runCatching { gson.fromJson(env.payloadJson, TagAddReq::class.java) }.getOrNull() ?: TagAddReq()
                val name = req.name.trim()
                if (name.isBlank()) {
                    DesktopLinkStore.recordAuditLog(appCtx, instanceId, LinkMsgType.TAGS_ADD, false, "empty_name")
                    sendEnvelope(ws, LinkMsgType.TAGS_ADD_RES, env.reqId, TagMutateRes(ok = false, message = "Empty tag name"))
                    return
                }
                val dup = db.tagDao().all().firstOrNull { it.name.equals(name, ignoreCase = true) }
                if (dup != null) {
                    DesktopLinkStore.recordAuditLog(appCtx, instanceId, LinkMsgType.TAGS_ADD, true, "duplicate:$name")
                    sendEnvelope(ws, LinkMsgType.TAGS_ADD_RES, env.reqId, TagMutateRes(ok = true, message = "Already exists", item = TagItemDto(dup.id, dup.name, dup.colorHex)))
                    return
                }
                val id = db.tagDao().insert(TagEntity(name = name, colorHex = req.colorHex.ifBlank { "#6EA8FE" }))
                DesktopLinkStore.recordAuditLog(appCtx, instanceId, LinkMsgType.TAGS_ADD, true, name)
                sendEnvelope(ws, LinkMsgType.TAGS_ADD_RES, env.reqId, TagMutateRes(ok = true, message = "Created", item = TagItemDto(id, name, req.colorHex.ifBlank { "#6EA8FE" })))
            }

            LinkMsgType.TAGS_REMOVE -> {
                val req = runCatching { gson.fromJson(env.payloadJson, TagRemoveReq::class.java) }.getOrNull()
                if (req == null || req.id <= 0L) {
                    DesktopLinkStore.recordAuditLog(appCtx, instanceId, LinkMsgType.TAGS_REMOVE, false, "invalid_id")
                    sendEnvelope(ws, LinkMsgType.TAGS_REMOVE_RES, env.reqId, TagMutateRes(ok = false, message = "Invalid id"))
                    return
                }
                db.tagDao().deleteCrossRefsByTag(req.id)
                db.tagDao().deleteById(req.id)
                DesktopLinkStore.recordAuditLog(appCtx, instanceId, LinkMsgType.TAGS_REMOVE, true, "id=${req.id}")
                sendEnvelope(ws, LinkMsgType.TAGS_REMOVE_RES, env.reqId, TagMutateRes(ok = true, message = "Removed"))
            }

            else -> {
                DesktopLinkStore.recordAuditLog(appCtx, instanceId, env.type, false, "unknown_command")
                sendEnvelope(
                    ws,
                    LinkMsgType.ERROR,
                    env.reqId,
                    ErrorPayload(LinkErrorCode.UNKNOWN_COMMAND, "Unknown command: ${env.type}")
                )
            }
        }
    }

    private fun ensureMessagePushLoop(appCtx: Context) {
        if (messagePushJob?.isActive == true) return
        messagePushJob = scope.launch {
            val db = AppDatabaseHolder.db(appCtx)
            while (server != null && subscribedClients.isNotEmpty()) {
                delay(3_000L)
                if (subscribedClients.isEmpty()) break
                val recent = runCatching { db.messageDao().paged(20, 0) }.getOrDefault(emptyList())
                if (recent.isEmpty()) continue
                subscribedClients.toList().forEach { id ->
                    val ws = activeSessions[id]?.ws ?: return@forEach
                    val prevCursor = clientCursors[id] ?: 0L
                    val newItems = recent.filter { it.id > prevCursor }.sortedBy { it.id }
                    if (newItems.isEmpty()) return@forEach
                    val nextCursor = newItems.maxOf { it.id }
                    val dtos = newItems.map { m ->
                        MessageSummaryDto(
                            id = m.id,
                            packageName = m.packageName,
                            sender = m.sender,
                            preview = m.content.replace(Regex("\\s+"), " ").trim().take(90),
                            timestamp = m.timestamp,
                            riskLabel = m.riskLabel,
                            isStarred = m.isStarred,
                            isRead = m.isRead
                        )
                    }
                    val env = LinkEnvelope(
                        type = LinkMsgType.MESSAGE_NEW,
                        reqId = "",
                        payloadJson = gson.toJson(MessageNewPush(items = dtos, latestCursor = nextCursor))
                    )
                    val sent = runCatching { ws.send(Frame.Text(gson.toJson(env))) }.isSuccess
                    if (sent) {
                        clientCursors[id] = nextCursor
                    }
                }
            }
        }
    }

    private fun refreshConnectedClients(appCtx: Context) {
        val list = activeSessions.values.map { it.info }
        _status.value = _status.value.copy(connectedClients = list)
        onSessionChanged?.invoke(appCtx, list.size)
    }

    private fun startIdleWatcher(appCtx: Context) {
        idleWatchJob?.cancel()
        idleWatchJob = scope.launch {
            while (server != null) {
                delay(60_000L)
                if (activeSessions.isEmpty()) {
                    val idleMs = System.currentTimeMillis() - lastActivityMs
                    if (idleMs >= LinkProtocol.IDLE_TIMEOUT_MS) {
                        stop(appCtx)
                        break
                    }
                } else {
                    lastActivityMs = System.currentTimeMillis()
                }
            }
        }
    }

    private fun registerNsd(appCtx: Context, port: Int, fingerprint: String) {
        unregisterNsd()
        runCatching {
            val mgr = appCtx.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            val info = NsdServiceInfo().apply {
                serviceName = "PhantomCheck-${Build.MODEL.replace(" ", "").take(12)}"
                serviceType = LinkProtocol.MDNS_SERVICE_TYPE
                setPort(port)
                setAttribute("fn", Build.MODEL.take(28))
                setAttribute("fp", fingerprint.take(8))
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    android.util.Log.w("DesktopLinkServer", "NSD registration failed: errorCode=$errorCode")
                    if (server != null) {
                        _status.value = _status.value.copy(
                            lastError = appCtx.getString(R.string.link_hub_nsd_failed, errorCode)
                        )
                    }
                }
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    android.util.Log.w("DesktopLinkServer", "NSD unregistration failed: errorCode=$errorCode")
                }
            }
            mgr.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            nsdManager = mgr
            nsdListener = listener
        }.onFailure { err ->
            android.util.Log.w("DesktopLinkServer", "NSD registerService threw: ${err.javaClass.simpleName}")
        }
    }

    private fun unregisterNsd() {
        val mgr = nsdManager
        val listener = nsdListener
        nsdManager = null
        nsdListener = null
        if (mgr != null && listener != null) {
            runCatching { mgr.unregisterService(listener) }
        }
    }

    fun getLocalWifiIpAddress(): String? {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            val candidates = mutableListOf<String>()
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (!nif.isUp || nif.isLoopback) continue
                val addrs = nif.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        if (addr.isSiteLocalAddress) {
                            if (nif.name.startsWith("wlan", ignoreCase = true)) return ip
                            candidates.add(ip)
                        }
                    }
                }
            }
            candidates.firstOrNull()
        }.getOrNull()
    }
}
