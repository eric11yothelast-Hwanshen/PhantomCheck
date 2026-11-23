package com.phantom.checker.service

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.google.gson.Gson
import com.phantom.checker.shared.AuthRequestPayload
import com.phantom.checker.shared.AuthResponsePayload
import com.phantom.checker.shared.ErrorPayload
import com.phantom.checker.shared.HelloPayload
import com.phantom.checker.shared.LinkEnvelope
import com.phantom.checker.shared.LinkErrorCode
import com.phantom.checker.shared.LinkMsgType
import com.phantom.checker.shared.LinkProtocol
import com.phantom.checker.shared.PairRequestPayload
import com.phantom.checker.shared.PairResponsePayload
import com.phantom.checker.util.DesktopLinkStore
import com.phantom.checker.util.PairCodeCheckResult
import com.phantom.checker.util.PairedDesktop
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
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
import java.net.Inet4Address
import java.net.NetworkInterface
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
 * #449 / #450 手機端區域網路 WebSocket 伺服器（wss-only，CIO engine + 自簽 PKCS12 + NsdManager）。
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

    /** M2 可掛載的已驗證指令處理器 */
    @Volatile
    var commandHandler: (suspend (Context, String, LinkEnvelope, WebSocketSession) -> Unit)? = null

    /** M2 可掛載的斷線/連線事件回呼 */
    @Volatile
    var onSessionChanged: ((Context, Int) -> Unit)? = null

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

        val engine = embeddedServer(CIO, configure = {
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
                    handleClientConnection(appCtx, tls.certFingerprintSha256, this)
                }
            }
        }

        runCatching {
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
            runCatching { engine.stop(200, 500) }
            server = null
            _status.value = LinkServerStatus(
                isRunning = false,
                port = port,
                localIp = localIp,
                lastError = err.message ?: "Server start failed"
            )
        }
    }

    @Synchronized
    fun stop(ctx: Context? = null) {
        idleWatchJob?.cancel()
        idleWatchJob = null
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
        ws: WebSocketSession
    ) {
        val remoteIp = "LAN"
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
        val cmdTimestamps = ArrayDeque<Long>()

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
                            activeSessions[req.instanceId]?.ws?.let { old -> runCatching { old.close() } }
                            activeSessions[req.instanceId] = ClientSession(info, ws)
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
                            activeSessions[req.instanceId]?.ws?.let { old -> runCatching { old.close() } }
                            activeSessions[req.instanceId] = ClientSession(info, ws)
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

                // 已驗證階段：速率限制（≤5 指令/秒，超限斷線）
                val now = System.currentTimeMillis()
                while (cmdTimestamps.isNotEmpty() && (now - cmdTimestamps.first()) > 1000L) {
                    cmdTimestamps.removeFirst()
                }
                if (cmdTimestamps.size >= LinkProtocol.MAX_COMMANDS_PER_SEC) {
                    sendEnvelope(ws, LinkMsgType.ERROR, env.reqId, ErrorPayload(LinkErrorCode.RATE_LIMIT_EXCEEDED, "Rate limit exceeded"))
                    ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Rate limit exceeded"))
                    return
                }
                cmdTimestamps.addLast(now)

                if (env.type == LinkMsgType.PING) {
                    sendEnvelope(ws, LinkMsgType.PONG, env.reqId, emptyMap<String, String>())
                    continue
                }

                val handler = commandHandler
                if (handler != null) {
                    handler.invoke(appCtx, authedInstanceId, env, ws)
                } else {
                    sendEnvelope(
                        ws,
                        LinkMsgType.ERROR,
                        env.reqId,
                        ErrorPayload(LinkErrorCode.UNKNOWN_COMMAND, "Unknown command: ${env.type}")
                    )
                }
            }
        } catch (_: Exception) {
        } finally {
            val id = authedInstanceId
            if (id != null) {
                activeSessions.remove(id)
                refreshConnectedClients(appCtx)
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
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            }
            mgr.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            nsdManager = mgr
            nsdListener = listener
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
