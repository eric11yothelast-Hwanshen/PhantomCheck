package com.phantom.checker.desktop

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.phantom.checker.shared.*
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.BindException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.Properties
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration.Companion.seconds

enum class LinkConnState {
    STOPPED,
    WAITING_FOR_PHONE,
    CONNECTED,
    ERROR
}

data class PairedPhoneInfo(
    val instanceId: String = "",
    val name: String = "",
    val hmacSecret: String = "",
    val pairedAt: Long = 0L,
    val lastSeenAt: Long = 0L
)

data class ConnectedPhoneSessionInfo(
    val instanceId: String,
    val name: String,
    val remoteIp: String,
    val connectedAtMs: Long
)

data class DesktopAuditEntry(
    val timestamp: Long,
    val instanceId: String,
    val event: String,
    val ok: Boolean,
    val detail: String = ""
)

data class DesktopLinkUiState(
    val connState: LinkConnState = LinkConnState.STOPPED,
    val isServerRunning: Boolean = false,
    val port: Int = LinkProtocol.DEFAULT_PORT,
    val desktopName: String = "",
    val certFingerprint: String = "",
    val localIps: List<String> = emptyList(),
    val connectedPhones: List<ConnectedPhoneSessionInfo> = emptyList(),
    val pairedPhones: List<PairedPhoneInfo> = emptyList(),
    val auditLogs: List<DesktopAuditEntry> = emptyList(),
    val lastErrorCode: String? = null,
    val lastError: String? = null
) {
    val isPaired: Boolean get() = pairedPhones.isNotEmpty()
    val pairedDeviceName: String get() = connectedPhones.firstOrNull()?.name
        ?: pairedPhones.firstOrNull()?.name.orEmpty()
}

enum class PairCodeStatus { VALID, EXPIRED, INVALID }

/**
 * #476 桌面端內建 WSS 伺服器（Ktor Netty + sslConnector 自簽 TLS）：
 * - JVM 為 Ktor server 原生支援平台，解決 Android 端內嵌 TLS server 不穩定的根本問題
 * - 協定沿用 shared/LinkProtocol DTO，僅反轉 socket 連線方向：
 *   桌面監聽 wss://0.0.0.0:<port>/link → 送出 HELLO(v=2, challenge, certFingerprint) →
 *   手機驗證指紋後送 PAIR_REQUEST / AUTH_REQUEST → 驗證通過後桌面發送既有 7+ 項資料指令、手機回應
 * - 維持 MAX_PAIRED_CLIENTS=2、Token Bucket 5/s + burst 12、審計紀錄（絕不記訊息內容與金鑰）
 */
class DesktopLinkServer(
    private val mdns: DesktopMdnsBroadcaster = DesktopMdnsBroadcaster()
) {

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val secureRandom = SecureRandom()

    private val _state = MutableStateFlow(DesktopLinkUiState())
    val state: StateFlow<DesktopLinkUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<LinkEnvelope>(extraBufferCapacity = 128)
    val events: SharedFlow<LinkEnvelope> = _events.asSharedFlow()

    val mdnsStatus: StateFlow<MdnsBroadcastStatus> = mdns.status

    @Volatile
    private var server: EmbeddedServer<*, *>? = null

    @Volatile
    private var serverInstanceId: String = ""

    private data class ActivePairCode(val code: String, val expiresAtMs: Long)
    private val activePairCode = AtomicReference<ActivePairCode?>(null)

    private data class PhoneSession(
        val info: ConnectedPhoneSessionInfo,
        val ws: WebSocketSession
    )

    private val activeSessions = ConcurrentHashMap<String, PhoneSession>()
    private val pendingTimeouts = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val auditLock = Any()
    private val auditList = mutableListOf<DesktopAuditEntry>()

    companion object {
        private const val MAX_AUDIT_LOGS = 200
    }

    init {
        serverInstanceId = loadOrCreateServerInstanceId()
        val paired = loadPairedPhones()
        val pcName = resolveDesktopName()
        val ips = DesktopMdnsBroadcaster.listSiteLocalIpv4Addresses()
        _state.value = DesktopLinkUiState(
            connState = LinkConnState.STOPPED,
            isServerRunning = false,
            port = DesktopSettingsStore.state.value.serverPort,
            desktopName = pcName,
            localIps = ips,
            pairedPhones = paired
        )
    }

    private fun resolveDesktopName(): String =
        runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("PhantomCheck-PC").ifBlank { "PhantomCheck-PC" }

    private fun loadOrCreateServerInstanceId(): String {
        val file = DesktopPaths.linkPropertiesFile
        val props = Properties()
        if (file.exists()) {
            runCatching { file.bufferedReader(Charsets.UTF_8).use { props.load(it) } }
        }
        val existing = props.getProperty("serverInstanceId").orEmpty().trim()
        if (existing.isNotBlank()) return existing
        val created = UUID.randomUUID().toString()
        props.setProperty("serverInstanceId", created)
        runCatching {
            if (!file.parentFile.exists()) file.parentFile.mkdirs()
            file.bufferedWriter(Charsets.UTF_8).use { props.store(it, "PhantomCheck Desktop Server") }
            DesktopPaths.restrictOwnerOnlyPermissions(file)
        }
        return created
    }

    @Synchronized
    fun start(portOverride: Int? = null, onErrorNotify: ((String) -> Unit)? = null) {
        if (server != null) return
        val bindPort = (portOverride ?: DesktopSettingsStore.state.value.serverPort).coerceIn(1024, 65535)
        val pcName = resolveDesktopName()
        val ips = DesktopMdnsBroadcaster.listSiteLocalIpv4Addresses()

        val tls = runCatching { DesktopTls.loadOrCreate() }.getOrElse { err ->
            val msg = err.message ?: err.javaClass.simpleName
            DesktopDiagnostics.error("Server", "TLS init failed: $msg")
            _state.value = _state.value.copy(
                connState = LinkConnState.ERROR,
                isServerRunning = false,
                port = bindPort,
                desktopName = pcName,
                localIps = ips,
                lastErrorCode = LinkErrorCode.TLS_ERROR,
                lastError = msg
            )
            onErrorNotify?.invoke(msg)
            return
        }

        // Pre-flight TCP port check so bind conflicts are immediately visible
        try {
            ServerSocket().use { sock ->
                sock.reuseAddress = true
                sock.bind(InetSocketAddress("0.0.0.0", bindPort))
            }
        } catch (_: BindException) {
            val msg = DesktopStrings.serverPortInUse(bindPort)
            DesktopDiagnostics.error("Server", "Port $bindPort already in use")
            _state.value = _state.value.copy(
                connState = LinkConnState.ERROR,
                isServerRunning = false,
                port = bindPort,
                desktopName = pcName,
                certFingerprint = tls.certFingerprintSha256,
                localIps = ips,
                lastErrorCode = LinkErrorCode.PORT_IN_USE,
                lastError = msg
            )
            onErrorNotify?.invoke(msg)
            return
        } catch (err: Exception) {
            val msg = err.message ?: err.javaClass.simpleName
            DesktopDiagnostics.error("Server", "Port pre-check failed on $bindPort: $msg")
            _state.value = _state.value.copy(
                connState = LinkConnState.ERROR,
                isServerRunning = false,
                port = bindPort,
                desktopName = pcName,
                certFingerprint = tls.certFingerprintSha256,
                localIps = ips,
                lastErrorCode = LinkErrorCode.INTERNAL_ERROR,
                lastError = msg
            )
            onErrorNotify?.invoke(msg)
            return
        }

        val pingSec = DesktopSettingsStore.state.value.pingIntervalSec.coerceIn(10, 60)
        var createdEngine: EmbeddedServer<*, *>? = null
        runCatching {
            val engine = embeddedServer(Netty, configure = {
                connectionGroupSize = 1
                workerGroupSize = 2
                callGroupSize = 4
                sslConnector(
                    keyStore = tls.keyStore,
                    keyAlias = DesktopTls.KEY_ALIAS,
                    keyStorePassword = { tls.keyPassword.toCharArray() },
                    privateKeyPassword = { tls.keyPassword.toCharArray() }
                ) {
                    this.host = "0.0.0.0"
                    this.port = bindPort
                }
            }) {
                install(WebSockets) {
                    pingPeriod = pingSec.seconds
                    timeout = (pingSec * 2).coerceAtLeast(30).seconds
                    maxFrameSize = 512 * 1024L
                }
                routing {
                    webSocket(LinkProtocol.WS_PATH) {
                        val remoteIp = runCatching { call.request.origin.remoteAddress }
                            .getOrNull()
                            ?.takeIf { it.isNotBlank() }
                            ?: "unknown"
                        handlePhoneConnection(
                            certFingerprint = tls.certFingerprintSha256,
                            desktopName = pcName,
                            remoteIp = remoteIp,
                            ws = this
                        )
                    }
                }
            }
            createdEngine = engine
            engine.start(wait = false)
            server = engine
            _state.value = _state.value.copy(
                connState = if (activeSessions.isEmpty()) LinkConnState.WAITING_FOR_PHONE else LinkConnState.CONNECTED,
                isServerRunning = true,
                port = bindPort,
                desktopName = pcName,
                certFingerprint = tls.certFingerprintSha256,
                localIps = ips,
                lastErrorCode = null,
                lastError = null
            )
            DesktopDiagnostics.info("Server", "WSS Netty server listening on 0.0.0.0:$bindPort (fp=${tls.certFingerprintSha256.take(8)}…)")
            mdns.start(bindPort, tls.certFingerprintSha256, pcName)
        }.onFailure { err ->
            runCatching { createdEngine?.stop(200, 500) }
            server = null
            val isBind = err is BindException || err.cause is BindException
            val code = if (isBind) LinkErrorCode.PORT_IN_USE else LinkErrorCode.INTERNAL_ERROR
            val msg = if (isBind) DesktopStrings.serverPortInUse(bindPort) else (err.message ?: err.javaClass.simpleName)
            DesktopDiagnostics.error("Server", "Failed to start WSS server: $code $msg")
            _state.value = _state.value.copy(
                connState = LinkConnState.ERROR,
                isServerRunning = false,
                port = bindPort,
                desktopName = pcName,
                certFingerprint = tls.certFingerprintSha256,
                localIps = ips,
                lastErrorCode = code,
                lastError = msg
            )
            onErrorNotify?.invoke(msg)
        }
    }

    @Synchronized
    fun restart(newPort: Int? = null, onErrorNotify: ((String) -> Unit)? = null) {
        stop()
        start(portOverride = newPort, onErrorNotify = onErrorNotify)
    }

    private fun cancelAllPendingTimeouts() {
        val jobs = pendingTimeouts.values.toList()
        pendingTimeouts.clear()
        jobs.forEach { it.cancel() }
    }

    @Synchronized
    fun stop() {
        cancelAllPendingTimeouts()
        mdns.stop()
        val sessions = activeSessions.values.toList()
        activeSessions.clear()
        scope.launch {
            sessions.forEach {
                runCatching { it.ws.close(CloseReason(CloseReason.Codes.NORMAL, "Desktop server stopped")) }
            }
        }
        val srv = server
        server = null
        if (srv != null) {
            scope.launch {
                runCatching { srv.stop(300, 1000) }
            }
        }
        _state.value = _state.value.copy(
            connState = LinkConnState.STOPPED,
            isServerRunning = false,
            connectedPhones = emptyList()
        )
        DesktopDiagnostics.info("Server", "WSS server stopped")
    }

    fun disconnectPhones() {
        cancelAllPendingTimeouts()
        val sessions = activeSessions.values.toList()
        activeSessions.clear()
        scope.launch {
            sessions.forEach {
                runCatching { it.ws.close(CloseReason(CloseReason.Codes.NORMAL, "Disconnected by desktop user")) }
            }
        }
        refreshSessionsState()
    }

    fun refreshLocalIps() {
        _state.value = _state.value.copy(localIps = DesktopMdnsBroadcaster.listSiteLocalIpv4Addresses())
    }

    fun generatePairCode(nowMs: Long = System.currentTimeMillis()): Pair<String, Long> {
        val code = "%06d".format(secureRandom.nextInt(1_000_000))
        val expiresAt = nowMs + LinkProtocol.PAIR_CODE_TTL_MS
        activePairCode.set(ActivePairCode(code, expiresAt))
        recordAudit("desktop", "pair.code.generated", true, "ttl=${LinkProtocol.PAIR_CODE_TTL_MS / 1000}s")
        return code to expiresAt
    }

    fun getCurrentPairCode(nowMs: Long = System.currentTimeMillis()): Pair<String, Long>? {
        val cur = activePairCode.get() ?: return null
        if (nowMs > cur.expiresAtMs) return null
        return cur.code to cur.expiresAtMs
    }

    fun getOrGeneratePairCode(nowMs: Long = System.currentTimeMillis()): Pair<String, Long> =
        getCurrentPairCode(nowMs) ?: generatePairCode(nowMs)

    private fun consumePairCode(inputCode: String, nowMs: Long = System.currentTimeMillis()): PairCodeStatus {
        val cur = activePairCode.get() ?: return PairCodeStatus.INVALID
        if (nowMs > cur.expiresAtMs) {
            activePairCode.compareAndSet(cur, null)
            return PairCodeStatus.EXPIRED
        }
        if (inputCode.trim() != cur.code) {
            return PairCodeStatus.INVALID
        }
        activePairCode.compareAndSet(cur, null)
        return PairCodeStatus.VALID
    }

    fun revokePhone(instanceId: String) {
        removePairedPhone(instanceId)
        val sess = activeSessions.remove(instanceId)
        if (sess != null) {
            scope.launch {
                runCatching {
                    sendEnvelope(sess.ws, LinkMsgType.UNPAIRED, "", ErrorPayload(LinkErrorCode.AUTH_FAILED, "Unpaired by desktop"))
                    sess.ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unpaired"))
                }
            }
        }
        recordAudit(instanceId, "phone.unpaired", true, "revoked_by_desktop")
        refreshSessionsState()
    }

    fun clearAllPairings() {
        val allIds = _state.value.pairedPhones.map { it.instanceId }
        allIds.forEach { revokePhone(it) }
    }

    suspend fun sendCommand(type: String, reqId: String, payload: Any): Boolean {
        val target = activeSessions.values.firstOrNull() ?: return false
        val env = LinkEnvelope(type = type, reqId = reqId, payloadJson = gson.toJson(payload))
        return try {
            target.ws.send(Frame.Text(gson.toJson(env)))
            recordAudit(target.info.instanceId, type, true, "sent")
            if (reqId.isNotBlank()) {
                val timeoutMs = if (type == LinkMsgType.CHAT_SEND || type == LinkMsgType.MESSAGE_DEEP_ANALYZE) {
                    LinkProtocol.LONG_AI_COMMAND_TIMEOUT_MS
                } else {
                    LinkProtocol.DEFAULT_COMMAND_TIMEOUT_MS
                }
                pendingTimeouts.remove(reqId)?.cancel()
                pendingTimeouts[reqId] = scope.launch {
                    kotlinx.coroutines.delay(timeoutMs)
                    if (pendingTimeouts.remove(reqId) != null) {
                        DesktopDiagnostics.warn("Timeout", "Command $type ($reqId) timed out after ${timeoutMs}ms")
                        recordAudit(target.info.instanceId, type, false, "timeout_${timeoutMs}ms")
                        _events.emit(
                            LinkEnvelope(
                                type = LinkMsgType.ERROR,
                                reqId = reqId,
                                payloadJson = gson.toJson(ErrorPayload(LinkErrorCode.TIMEOUT, type))
                            )
                        )
                    }
                }
            }
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            DesktopDiagnostics.warn("Link", "sendCommand($type) failed: ${e.javaClass.simpleName}: ${e.message}")
            recordAudit(target.info.instanceId, type, false, e.javaClass.simpleName)
            false
        }
    }

    private suspend fun sendEnvelope(ws: WebSocketSession, type: String, reqId: String, payload: Any) {
        val env = LinkEnvelope(type = type, reqId = reqId, payloadJson = gson.toJson(payload))
        ws.send(Frame.Text(gson.toJson(env)))
    }

    private suspend fun handlePhoneConnection(
        certFingerprint: String,
        desktopName: String,
        remoteIp: String,
        ws: WebSocketSession
    ) {
        val challenge = DesktopTls.randomHex(24)
        DesktopDiagnostics.info("Handshake", "Incoming WSS connection from $remoteIp; sending HELLO v${LinkProtocol.PROTOCOL_VERSION}")
        runCatching {
            sendEnvelope(
                ws,
                LinkMsgType.HELLO,
                "",
                HelloPayload(
                    protocolVersion = LinkProtocol.PROTOCOL_VERSION,
                    deviceName = desktopName,
                    certFingerprint = certFingerprint,
                    challenge = challenge,
                    serverInstanceId = serverInstanceId
                )
            )
        }.onFailure { err ->
            DesktopDiagnostics.warn("Handshake", "Failed sending HELLO to $remoteIp: ${err.javaClass.simpleName}")
            return
        }

        var authedInstanceId: String? = null
        var mySession: PhoneSession? = null
        var bucketTokens = LinkProtocol.MAX_COMMAND_BURST.toDouble()
        var lastRefillMs = System.currentTimeMillis()
        var consecutiveRateLimitViolations = 0

        try {
            for (frame in ws.incoming) {
                if (frame !is Frame.Text) continue
                val env = runCatching { gson.fromJson(frame.readText(), LinkEnvelope::class.java) }.getOrNull()
                    ?: continue

                if (authedInstanceId == null) {
                    when (env.type) {
                        LinkMsgType.PAIR_REQUEST -> {
                            val req = runCatching { gson.fromJson(env.payloadJson, PairRequestPayload::class.java) }.getOrNull()
                            if (req == null || req.instanceId.isBlank()) {
                                recordAudit("unknown", "pair.request", false, "invalid_request:$remoteIp")
                                sendEnvelope(ws, LinkMsgType.PAIR_RESPONSE, env.reqId, PairResponsePayload(ok = false, errorCode = LinkErrorCode.INVALID_REQUEST, errorMessage = "Invalid pair request"))
                                ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid pair request"))
                                return
                            }
                            if (req.protocolVersion != LinkProtocol.PROTOCOL_VERSION) {
                                recordAudit(req.instanceId, "pair.request", false, "version_mismatch:${req.protocolVersion}")
                                _state.value = _state.value.copy(
                                    lastErrorCode = LinkErrorCode.VERSION_MISMATCH,
                                    lastError = DesktopStrings.classifiedError(LinkErrorCode.VERSION_MISMATCH)
                                )
                                sendEnvelope(ws, LinkMsgType.PAIR_RESPONSE, env.reqId, PairResponsePayload(ok = false, errorCode = LinkErrorCode.VERSION_MISMATCH, errorMessage = "Protocol version mismatch (desktop=${LinkProtocol.PROTOCOL_VERSION}, phone=${req.protocolVersion})"))
                                ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Version mismatch"))
                                return
                            }
                            if (req.expectedFingerprint.isNotBlank() && !req.expectedFingerprint.equals(certFingerprint, ignoreCase = true)) {
                                recordAudit(req.instanceId, "pair.request", false, "fingerprint_mismatch")
                                _state.value = _state.value.copy(
                                    lastErrorCode = LinkErrorCode.FINGERPRINT_MISMATCH,
                                    lastError = DesktopStrings.classifiedError(LinkErrorCode.FINGERPRINT_MISMATCH)
                                )
                                sendEnvelope(ws, LinkMsgType.PAIR_RESPONSE, env.reqId, PairResponsePayload(ok = false, errorCode = LinkErrorCode.FINGERPRINT_MISMATCH, errorMessage = "Certificate fingerprint mismatch"))
                                ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Fingerprint mismatch"))
                                return
                            }
                            when (consumePairCode(req.code)) {
                                PairCodeStatus.EXPIRED -> {
                                    recordAudit(req.instanceId, "pair.request", false, "code_expired")
                                    _state.value = _state.value.copy(
                                        lastErrorCode = LinkErrorCode.PAIR_CODE_EXPIRED,
                                        lastError = DesktopStrings.classifiedError(LinkErrorCode.PAIR_CODE_EXPIRED)
                                    )
                                    sendEnvelope(ws, LinkMsgType.PAIR_RESPONSE, env.reqId, PairResponsePayload(ok = false, errorCode = LinkErrorCode.PAIR_CODE_EXPIRED, errorMessage = "Pairing code expired"))
                                    ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Code expired"))
                                    return
                                }
                                PairCodeStatus.INVALID -> {
                                    recordAudit(req.instanceId, "pair.request", false, "invalid_code")
                                    _state.value = _state.value.copy(
                                        lastErrorCode = LinkErrorCode.INVALID_PAIR_CODE,
                                        lastError = DesktopStrings.classifiedError(LinkErrorCode.INVALID_PAIR_CODE)
                                    )
                                    sendEnvelope(ws, LinkMsgType.PAIR_RESPONSE, env.reqId, PairResponsePayload(ok = false, errorCode = LinkErrorCode.INVALID_PAIR_CODE, errorMessage = "Invalid pairing code"))
                                    ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid code"))
                                    return
                                }
                                PairCodeStatus.VALID -> Unit
                            }
                            if (activeSessions.size >= LinkProtocol.MAX_PAIRED_CLIENTS && !activeSessions.containsKey(req.instanceId)) {
                                recordAudit(req.instanceId, "pair.request", false, "too_many_clients")
                                sendEnvelope(ws, LinkMsgType.PAIR_RESPONSE, env.reqId, PairResponsePayload(ok = false, errorCode = LinkErrorCode.TOO_MANY_CLIENTS, errorMessage = "Max ${LinkProtocol.MAX_PAIRED_CLIENTS} concurrent phones reached"))
                                ws.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Too many clients"))
                                return
                            }
                            val secret = DesktopTls.randomHex(32)
                            val now = System.currentTimeMillis()
                            val phoneName = req.desktopName.ifBlank { "Android Phone" }
                            upsertPairedPhone(
                                PairedPhoneInfo(
                                    instanceId = req.instanceId,
                                    name = phoneName,
                                    hmacSecret = secret,
                                    pairedAt = now,
                                    lastSeenAt = now
                                )
                            )
                            authedInstanceId = req.instanceId
                            val info = ConnectedPhoneSessionInfo(req.instanceId, phoneName, remoteIp, now)
                            val newSession = PhoneSession(info, ws)
                            mySession = newSession
                            activeSessions.put(req.instanceId, newSession)?.ws?.let { old -> runCatching { old.close() } }
                            recordAudit(req.instanceId, "pair.success", true, "remote=$remoteIp")
                            DesktopDiagnostics.info("Handshake", "Paired phone '$phoneName' from $remoteIp")
                            sendEnvelope(
                                ws,
                                LinkMsgType.PAIR_RESPONSE,
                                env.reqId,
                                PairResponsePayload(
                                    ok = true,
                                    hmacSecret = secret,
                                    deviceName = desktopName,
                                    certFingerprint = certFingerprint
                                )
                            )
                            refreshSessionsState(clearError = true)
                        }

                        LinkMsgType.AUTH_REQUEST -> {
                            val req = runCatching { gson.fromJson(env.payloadJson, AuthRequestPayload::class.java) }.getOrNull()
                            if (req == null || req.instanceId.isBlank()) {
                                recordAudit("unknown", "auth.request", false, "invalid_request:$remoteIp")
                                sendEnvelope(ws, LinkMsgType.AUTH_RESPONSE, env.reqId, AuthResponsePayload(ok = false, errorCode = LinkErrorCode.INVALID_REQUEST, errorMessage = "Invalid auth request"))
                                ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid auth"))
                                return
                            }
                            if (req.protocolVersion != LinkProtocol.PROTOCOL_VERSION) {
                                recordAudit(req.instanceId, "auth.request", false, "version_mismatch:${req.protocolVersion}")
                                _state.value = _state.value.copy(
                                    lastErrorCode = LinkErrorCode.VERSION_MISMATCH,
                                    lastError = DesktopStrings.classifiedError(LinkErrorCode.VERSION_MISMATCH)
                                )
                                sendEnvelope(ws, LinkMsgType.AUTH_RESPONSE, env.reqId, AuthResponsePayload(ok = false, errorCode = LinkErrorCode.VERSION_MISMATCH, errorMessage = "Protocol version mismatch"))
                                ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Version mismatch"))
                                return
                            }
                            if (req.expectedFingerprint.isNotBlank() && !req.expectedFingerprint.equals(certFingerprint, ignoreCase = true)) {
                                recordAudit(req.instanceId, "auth.request", false, "fingerprint_mismatch")
                                sendEnvelope(ws, LinkMsgType.AUTH_RESPONSE, env.reqId, AuthResponsePayload(ok = false, errorCode = LinkErrorCode.FINGERPRINT_MISMATCH, errorMessage = "Certificate fingerprint mismatch"))
                                ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Fingerprint mismatch"))
                                return
                            }
                            val paired = findPairedPhone(req.instanceId)
                            if (paired == null) {
                                recordAudit(req.instanceId, "auth.request", false, "unknown_instance:$remoteIp")
                                sendEnvelope(ws, LinkMsgType.AUTH_RESPONSE, env.reqId, AuthResponsePayload(ok = false, errorCode = LinkErrorCode.UNKNOWN_INSTANCE, errorMessage = "Unknown phone instance"))
                                ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unknown instance"))
                                return
                            }
                            val expectedHmac = computeHmacSha256Hex(paired.hmacSecret, challenge)
                            if (!expectedHmac.equals(req.hmacResponse, ignoreCase = true)) {
                                recordAudit(req.instanceId, "auth.request", false, "hmac_failed:$remoteIp")
                                _state.value = _state.value.copy(
                                    lastErrorCode = LinkErrorCode.AUTH_FAILED,
                                    lastError = DesktopStrings.classifiedError(LinkErrorCode.AUTH_FAILED)
                                )
                                sendEnvelope(ws, LinkMsgType.AUTH_RESPONSE, env.reqId, AuthResponsePayload(ok = false, errorCode = LinkErrorCode.AUTH_FAILED, errorMessage = "HMAC challenge failed"))
                                ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Auth failed"))
                                return
                            }
                            if (activeSessions.size >= LinkProtocol.MAX_PAIRED_CLIENTS && !activeSessions.containsKey(req.instanceId)) {
                                sendEnvelope(ws, LinkMsgType.AUTH_RESPONSE, env.reqId, AuthResponsePayload(ok = false, errorCode = LinkErrorCode.TOO_MANY_CLIENTS, errorMessage = "Max ${LinkProtocol.MAX_PAIRED_CLIENTS} concurrent phones reached"))
                                ws.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Too many clients"))
                                return
                            }
                            val now = System.currentTimeMillis()
                            updatePhoneLastSeen(req.instanceId, now)
                            authedInstanceId = req.instanceId
                            val info = ConnectedPhoneSessionInfo(req.instanceId, paired.name, remoteIp, now)
                            val newSession = PhoneSession(info, ws)
                            mySession = newSession
                            activeSessions.put(req.instanceId, newSession)?.ws?.let { old -> runCatching { old.close() } }
                            recordAudit(req.instanceId, "auth.success", true, "remote=$remoteIp")
                            DesktopDiagnostics.info("Handshake", "Authenticated phone '${paired.name}' from $remoteIp")
                            sendEnvelope(ws, LinkMsgType.AUTH_RESPONSE, env.reqId, AuthResponsePayload(ok = true, deviceName = desktopName))
                            refreshSessionsState(clearError = true)
                        }

                        else -> {
                            recordAudit("unknown", env.type, false, "unauthenticated:$remoteIp")
                            sendEnvelope(ws, LinkMsgType.ERROR, env.reqId, ErrorPayload(LinkErrorCode.AUTH_FAILED, "Authentication required"))
                            ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthenticated"))
                            return
                        }
                    }
                    continue
                }

                // Authenticated traffic: Token Bucket 5/s + burst 12 (drop + ERROR, disconnect after 3 consecutive)
                val now = System.currentTimeMillis()
                val elapsedSec = (now - lastRefillMs).coerceAtLeast(0L) / 1000.0
                if (elapsedSec > 0.0) {
                    bucketTokens = (bucketTokens + elapsedSec * LinkProtocol.MAX_COMMANDS_PER_SEC)
                        .coerceAtMost(LinkProtocol.MAX_COMMAND_BURST.toDouble())
                    lastRefillMs = now
                }
                if (bucketTokens < 1.0) {
                    consecutiveRateLimitViolations++
                    recordAudit(authedInstanceId, env.type, false, "rate_limit_exceeded#$consecutiveRateLimitViolations")
                    sendEnvelope(ws, LinkMsgType.ERROR, env.reqId, ErrorPayload(LinkErrorCode.RATE_LIMIT_EXCEEDED, "Rate limit exceeded"))
                    if (consecutiveRateLimitViolations >= 3) {
                        DesktopDiagnostics.warn("RateLimit", "Disconnecting $authedInstanceId after 3 consecutive rate limit violations")
                        ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Rate limit exceeded"))
                        return
                    }
                    continue
                }
                consecutiveRateLimitViolations = 0
                bucketTokens -= 1.0

                if (env.reqId.isNotBlank()) {
                    pendingTimeouts.remove(env.reqId)?.cancel()
                }

                when (env.type) {
                    LinkMsgType.PING -> {
                        sendEnvelope(ws, LinkMsgType.PONG, env.reqId, emptyMap<String, String>())
                    }
                    LinkMsgType.UNPAIRED -> {
                        removePairedPhone(authedInstanceId)
                        recordAudit(authedInstanceId, "phone.unpaired", true, "by_phone")
                        ws.close(CloseReason(CloseReason.Codes.NORMAL, "Unpaired"))
                        return
                    }
                    LinkMsgType.ERROR -> {
                        val err = runCatching { gson.fromJson(env.payloadJson, ErrorPayload::class.java) }.getOrNull()
                        if (err != null) {
                            DesktopDiagnostics.warn("Protocol", "Phone error ${err.errorCode}: ${err.errorMessage}")
                            _state.value = _state.value.copy(
                                lastErrorCode = err.errorCode,
                                lastError = "${err.errorCode}: ${err.errorMessage}"
                            )
                        }
                        _events.emit(env)
                    }
                    else -> {
                        _events.emit(env)
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            DesktopDiagnostics.warn("Link", "Phone session ($remoteIp) ended with ${e.javaClass.simpleName}: ${e.message}")
            recordAudit(authedInstanceId ?: "unknown", "session.error", false, e.javaClass.simpleName)
        } finally {
            val id = authedInstanceId
            val sess = mySession
            if (id != null && sess != null) {
                if (activeSessions.remove(id, sess)) {
                    if (activeSessions.isEmpty()) {
                        cancelAllPendingTimeouts()
                    }
                    DesktopDiagnostics.info("Link", "Phone '${sess.info.name}' disconnected")
                    refreshSessionsState()
                }
            }
        }
    }

    private fun refreshSessionsState(clearError: Boolean = false) {
        val connected = activeSessions.values.map { it.info }
        val paired = loadPairedPhones()
        val running = server != null
        val nextConnState = when {
            !running -> LinkConnState.STOPPED
            connected.isNotEmpty() -> LinkConnState.CONNECTED
            else -> LinkConnState.WAITING_FOR_PHONE
        }
        _state.value = _state.value.copy(
            connState = nextConnState,
            isServerRunning = running,
            connectedPhones = connected,
            pairedPhones = paired,
            lastErrorCode = if (clearError) null else _state.value.lastErrorCode,
            lastError = if (clearError) null else _state.value.lastError
        )
    }

    private fun recordAudit(instanceId: String, event: String, ok: Boolean, detail: String = "") {
        val entry = DesktopAuditEntry(
            timestamp = System.currentTimeMillis(),
            instanceId = instanceId,
            event = event,
            ok = ok,
            detail = detail.take(80)
        )
        val snapshot = synchronized(auditLock) {
            auditList.add(0, entry)
            if (auditList.size > MAX_AUDIT_LOGS) {
                auditList.removeAt(auditList.lastIndex)
            }
            auditList.toList()
        }
        _state.value = _state.value.copy(auditLogs = snapshot)
    }

    fun clearAuditLogs() {
        synchronized(auditLock) { auditList.clear() }
        _state.value = _state.value.copy(auditLogs = emptyList())
    }

    @Synchronized
    private fun loadPairedPhones(): List<PairedPhoneInfo> {
        val file = DesktopPaths.pairedPhonesFile
        if (!file.exists()) return emptyList()
        return runCatching {
            val raw = file.readText(Charsets.UTF_8)
            val type = object : TypeToken<List<PairedPhoneInfo?>>() {}.type
            gson.fromJson<List<PairedPhoneInfo?>>(raw, type)
                .orEmpty()
                .filterNotNull()
                .filter { it.instanceId.isNotBlank() && it.hmacSecret.isNotBlank() }
        }.onFailure { err ->
            DesktopDiagnostics.warn("Store", "Failed loading paired phones: ${err.javaClass.simpleName}")
        }.getOrDefault(emptyList())
    }

    @Synchronized
    private fun savePairedPhones(list: List<PairedPhoneInfo>) {
        runCatching {
            val file = DesktopPaths.pairedPhonesFile
            if (!file.parentFile.exists()) file.parentFile.mkdirs()
            file.writeText(gson.toJson(list), Charsets.UTF_8)
            DesktopPaths.restrictOwnerOnlyPermissions(file)
        }.onFailure { err ->
            DesktopDiagnostics.error("Store", "Failed saving paired phones: ${err.javaClass.simpleName}")
        }
    }

    private fun findPairedPhone(instanceId: String): PairedPhoneInfo? =
        loadPairedPhones().firstOrNull { it.instanceId == instanceId }

    private fun upsertPairedPhone(phone: PairedPhoneInfo) {
        val list = loadPairedPhones().toMutableList()
        val idx = list.indexOfFirst { it.instanceId == phone.instanceId }
        if (idx >= 0) list[idx] = phone else list.add(0, phone)
        savePairedPhones(list)
    }

    private fun updatePhoneLastSeen(instanceId: String, nowMs: Long) {
        val updated = loadPairedPhones().map {
            if (it.instanceId == instanceId) it.copy(lastSeenAt = nowMs) else it
        }
        savePairedPhones(updated)
    }

    private fun removePairedPhone(instanceId: String) {
        val updated = loadPairedPhones().filterNot { it.instanceId == instanceId }
        savePairedPhones(updated)
    }

    private fun computeHmacSha256Hex(secretHex: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretHex.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
