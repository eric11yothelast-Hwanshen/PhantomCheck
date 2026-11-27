package com.phantom.checker.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.BatteryManager
import android.os.Build
import com.google.gson.Gson
import com.phantom.checker.data.AiRuleEntity
import com.phantom.checker.data.ChatMessageEntity
import com.phantom.checker.data.ChatSessionEntity
import com.phantom.checker.data.MessageTagCrossRef
import com.phantom.checker.data.NoteEntity
import com.phantom.checker.data.TagEntity
import com.phantom.checker.data.TodoEntity
import com.phantom.checker.data.WhitelistRuleHelper
import com.phantom.checker.network.AiClient
import com.phantom.checker.network.ChatMessage
import com.phantom.checker.shared.*
import com.phantom.checker.util.AiPurpose
import com.phantom.checker.util.DesktopLinkStore
import com.phantom.checker.util.PairedDesktop
import com.phantom.checker.util.PhantomPrefs
import com.phantom.checker.util.PhantomSettingsBus
import com.phantom.checker.widget.QuickNoteWidgetProvider
import com.phantom.checker.widget.TodoTodayWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

enum class PhoneLinkConnState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

data class DiscoveredDesktop(
    val serviceName: String,
    val friendlyName: String,
    val host: String,
    val port: Int,
    val fingerprintPrefix: String,
    val protocolVersion: Int,
    val discoveredAtMs: Long = System.currentTimeMillis()
)

data class PhoneLinkStatus(
    val connState: PhoneLinkConnState = PhoneLinkConnState.DISCONNECTED,
    val connectedDesktopId: String = "",
    val connectedDesktopName: String = "",
    val connectedHostPort: String = "",
    val lastErrorCode: String? = null,
    val lastErrorDetail: String? = null,
    val reconnectRemainingSec: Int = 0,
    val reconnectTotalSec: Int = 0,
    val isProbing: Boolean = false,
    val probeCooldownSec: Int = 0
)

/**
 * #476 手機端 WSS 用戶端（OkHttp WebSocket + TLS 憑證 SHA-256 指紋 pinning + 指數退避重連 1s→30s）：
 * - 資料主體仍在手機：所有 Room / AI / 通知回覆 / 待辦 / 記事資料留存手機，僅反轉 socket 連線方向
 * - 嚴守隱私紅線：敏感記憶（PRIVACY_SENSITIVE）絕不外送、訊息預覽上限 200 字、審計與診斷不記訊息與金鑰
 */
object DesktopLinkClient {

    private const val PROBE_COOLDOWN_MS = 15_000L
    private const val TCP_PROBE_TIMEOUT_MS = 8_000

    // 預留通知 ID 區段 1151..1153（避開 NotifIds 1001..1139 與 2000+；NotifIds.kt 不在 R6 單檔範圍內，後續可集中收斂）
    private const val NOTIF_ID_FIND_PHONE = 1151
    private const val NOTIF_ID_SEND_PHONE = 1152
    private const val NOTIF_ID_OPEN_URL = 1153

    private val PURE_OTP_DIGITS_REGEX = Regex("^\\d{4,8}$")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val HEX_COLOR_REGEX = Regex("^#[0-9A-Fa-f]{6}$")

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(PhoneLinkStatus())
    val status: StateFlow<PhoneLinkStatus> = _status.asStateFlow()

    private val _discoveredDesktops = MutableStateFlow<List<DiscoveredDesktop>>(emptyList())
    val discoveredDesktops: StateFlow<List<DiscoveredDesktop>> = _discoveredDesktops.asStateFlow()

    private val _isScanningLan = MutableStateFlow(false)
    val isScanningLan: StateFlow<Boolean> = _isScanningLan.asStateFlow()

    @Volatile
    private var multicastLock: android.net.wifi.WifiManager.MulticastLock? = null
    @Volatile
    private var mdnsRestartJob: Job? = null

    private val wsLock = Any()
    private val sessionSeq = AtomicLong(0L)

    @Volatile
    private var activeWs: WebSocket? = null

    @Volatile
    private var activeClient: OkHttpClient? = null

    @Volatile
    private var reconnectJob: Job? = null

    @Volatile
    private var messagePushJob: Job? = null

    @Volatile
    private var probeJob: Job? = null

    @Volatile
    private var lastTimeoutTargetKey: String? = null

    @Volatile
    private var lastTimeoutAtMs: Long = 0L

    @Volatile
    private var shouldKeepConnected: Boolean = false

    @Volatile
    private var connectedAtMs: Long = 0L

    @Volatile
    private var isSubscribedToMessages: Boolean = false

    @Volatile
    private var lastPushedCursor: Long = 0L

    @Volatile
    private var currentTargetDesktopId: String? = null

    @Volatile
    private var nsdManager: NsdManager? = null

    @Volatile
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    @Volatile
    private var discoveryRequested: Boolean = false

    // #517 事件驅動電量與裝置狀態監聽（僅連線時動態註冊，斷線立即反註冊，零秒級輪詢）
    @Volatile
    private var batteryStatusReceiver: android.content.BroadcastReceiver? = null
    @Volatile
    private var batteryStatusAppCtx: Context? = null
    @Volatile
    private var lastPushedBatteryLevel: Int = -1
    @Volatile
    private var lastPushedCharging: Boolean? = null
    @Volatile
    private var lastPushedChargeType: String = ""

    @Volatile
    private var discoveryAppCtx: Context? = null

    @Volatile
    private var isScreenOff: Boolean = false

    @Volatile
    private var screenReceiverRegistered: Boolean = false

    private const val SCREEN_OFF_BACKOFF_CAP_SEC = 60
    private const val SCREEN_ON_BACKOFF_CAP_SEC = 30
    private const val MSG_PUSH_INTERVAL_INTERACTIVE_MS = 3_000L
    private const val MSG_PUSH_INTERVAL_SCREEN_OFF_MS = 10_000L

    private fun ensureScreenReceiverRegistered(appCtx: Context) {
        if (screenReceiverRegistered) return
        synchronized(this) {
            if (screenReceiverRegistered) return
            runCatching {
                val pm = appCtx.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                isScreenOff = pm != null && !pm.isInteractive
                val receiver = object : android.content.BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        when (intent?.action) {
                            Intent.ACTION_SCREEN_OFF -> {
                                isScreenOff = true
                                pauseDiscoveryForScreenOff()
                            }
                            Intent.ACTION_SCREEN_ON -> {
                                isScreenOff = false
                                val savedCtx = discoveryAppCtx ?: appCtx
                                if (discoveryRequested && discoveryListener == null) {
                                    startDiscoveryInternal(savedCtx)
                                }
                                val target = activeTarget
                                if (shouldKeepConnected && target != null &&
                                    _status.value.connState == PhoneLinkConnState.RECONNECTING
                                ) {
                                    reconnectJob?.cancel()
                                    reconnectJob = null
                                    openSocket(appCtx, target, backoffSec = 1)
                                }
                            }
                        }
                    }
                }
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appCtx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    appCtx.registerReceiver(receiver, filter)
                }
                screenReceiverRegistered = true
            }
        }
    }

    private data class ConnectTarget(
        val host: String,
        val port: Int,
        val expectedFingerprint: String,
        val desktopId: String?,
        val desktopName: String,
        val hmacSecret: String?,
        val pairCode: String?
    )

    @Volatile
    private var activeTarget: ConnectTarget? = null

    fun autoConnectIfPaired(ctx: Context) {
        val appCtx = ctx.applicationContext
        if (!DesktopLinkStore.isLinkEnabled(appCtx)) return
        if (_status.value.connState == PhoneLinkConnState.CONNECTED || _status.value.connState == PhoneLinkConnState.CONNECTING) return
        val paired = DesktopLinkStore.getPairedDevices(appCtx)
        if (paired.isEmpty()) return
        val lastId = DesktopLinkStore.getLastConnectedDesktopId(appCtx)
        val target = paired.firstOrNull { it.instanceId == lastId && it.host.isNotBlank() }
            ?: paired.firstOrNull { it.host.isNotBlank() }
            ?: return
        connectToPaired(appCtx, target)
    }

    fun pairAndConnect(
        ctx: Context,
        host: String,
        port: Int,
        code: String,
        expectedFingerprint: String = "",
        desktopName: String = ""
    ) {
        val appCtx = ctx.applicationContext
        val cleanHost = host.trim().removePrefix("wss://").removePrefix("ws://").substringBefore("/").substringBefore(":")
        val cleanPort = port.coerceIn(1024, 65535)
        if (cleanHost.isBlank()) {
            _status.update {
                it.copy(
                    connState = PhoneLinkConnState.DISCONNECTED,
                    lastErrorCode = LinkErrorCode.INVALID_REQUEST,
                    lastErrorDetail = "Empty host"
                )
            }
            return
        }
        DesktopLinkStore.setLinkEnabled(appCtx, true)
        val target = ConnectTarget(
            host = cleanHost,
            port = cleanPort,
            expectedFingerprint = expectedFingerprint.trim().lowercase(),
            desktopId = null,
            desktopName = desktopName.ifBlank { cleanHost },
            hmacSecret = null,
            pairCode = code.trim()
        )
        startConnectionFlow(appCtx, target)
    }

    /**
     * #500 依憑證指紋前綴或服務/裝置名稱從即時 mDNS 清單解析已配對電腦之最新 IP:port（解決埠漂移 49515→49513）。
     */
    fun findDiscoveredForPaired(desktop: PairedDesktop): DiscoveredDesktop? {
        val now = System.currentTimeMillis()
        val live = _discoveredDesktops.value.filter { now - it.discoveredAtMs <= MDNS_TTL_MS }
        val fpLower = desktop.fingerprint.trim().lowercase()
        if (fpLower.length >= 6) {
            live.firstOrNull { d ->
                val prefix = d.fingerprintPrefix.trim().lowercase()
                prefix.length >= 6 && fpLower.startsWith(prefix)
            }?.let { return it }
        }
        return live.firstOrNull { d ->
            (desktop.instanceId.isNotBlank() && d.serviceName.equals(desktop.instanceId, ignoreCase = true)) ||
                (desktop.name.isNotBlank() && d.friendlyName.equals(desktop.name, ignoreCase = true))
        }
    }

    fun isOnWifi(ctx: Context): Boolean = runCatching {
        val cm = ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }.getOrDefault(false)

    fun connectToPaired(ctx: Context, desktop: PairedDesktop) {
        val appCtx = ctx.applicationContext
        val liveMatch = findDiscoveredForPaired(desktop)
        val resolvedHost = liveMatch?.host?.takeIf { it.isNotBlank() } ?: desktop.host
        val resolvedPort = (liveMatch?.port ?: desktop.port).coerceIn(1024, 65535)
        if (resolvedHost.isBlank() || desktop.hmacSecret.isBlank()) {
            _status.update {
                it.copy(
                    connState = PhoneLinkConnState.DISCONNECTED,
                    lastErrorCode = LinkErrorCode.INVALID_REQUEST,
                    lastErrorDetail = "Missing desktop address or credentials"
                )
            }
            return
        }
        if (liveMatch != null && (liveMatch.host != desktop.host || liveMatch.port != desktop.port)) {
            DesktopLinkStore.recordDiagnostic(
                appCtx,
                "mDNS",
                "ENDPOINT_UPDATED",
                "${desktop.host}:${desktop.port} -> ${liveMatch.host}:${liveMatch.port}"
            )
            DesktopLinkStore.upsertPairedDevice(appCtx, desktop.copy(host = resolvedHost, port = resolvedPort))
        }
        DesktopLinkStore.setLinkEnabled(appCtx, true)
        DesktopLinkStore.setLastConnectedDesktopId(appCtx, desktop.instanceId)
        val target = ConnectTarget(
            host = resolvedHost,
            port = resolvedPort,
            expectedFingerprint = desktop.fingerprint.trim().lowercase(),
            desktopId = desktop.instanceId,
            desktopName = desktop.name.ifBlank { resolvedHost },
            hmacSecret = desktop.hmacSecret,
            pairCode = null
        )
        startConnectionFlow(appCtx, target)
    }

    fun disconnect(clearError: Boolean = false) {
        shouldKeepConnected = false
        reconnectJob?.cancel()
        reconnectJob = null
        messagePushJob?.cancel()
        messagePushJob = null
        isSubscribedToMessages = false
        stopBatteryStatusMonitor()
        closeActiveSocket("Disconnected by user")
        _status.update { cur ->
            cur.copy(
                connState = PhoneLinkConnState.DISCONNECTED,
                reconnectRemainingSec = 0,
                reconnectTotalSec = 0,
                lastErrorCode = if (clearError) null else cur.lastErrorCode,
                lastErrorDetail = if (clearError) null else cur.lastErrorDetail
            )
        }
    }

    /**
     * #503 手機端設定或 AI 語意改設定後即時同步桌面端通知中心設定（鏡射模式、勿擾、懸浮助手、橫幅秒數）。
     */
    fun pushDesktopSettingsSync(
        mirrorMode: String = "",
        dndEnabled: Boolean? = null,
        floatingAssistantEnabled: Boolean? = null,
        bannerDurationSec: Int? = null
    ): Boolean {
        val ws = activeWs ?: return false
        return sendEnvelope(
            ws,
            LinkMsgType.DESKTOP_SETTINGS_SYNC,
            "",
            DesktopSettingsSyncPush(
                mirrorMode = mirrorMode,
                dndEnabled = dndEnabled,
                floatingAssistantEnabled = floatingAssistantEnabled,
                bannerDurationSec = bannerDurationSec
            )
        )
    }

    @Volatile
    private var lastReceivedClipHash: Int = 0

    /**
     * #518 手機端手動/前景推送剪貼簿至已配對電腦（預設關、僅 TLS 配對連線、5000 字上限、OtpDetector 驗證碼保護）。
     * 回傳狀態碼：sent | disabled | disconnected | empty | too_long | otp_blocked | echo_skipped
     */
    fun pushClipboardToDesktop(ctx: Context, rawText: String): String {
        val appCtx = ctx.applicationContext
        if (!PhantomPrefs.isClipboardSyncEnabled(appCtx)) return "disabled"
        val ws = activeWs ?: return "disconnected"
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return "empty"
        val maxChars = PhantomPrefs.getClipboardMaxChars(appCtx)
        if (trimmed.length > maxChars) return "too_long"
        if (PhantomPrefs.isClipboardOtpGuardEnabled(appCtx)) {
            if (com.phantom.checker.util.OtpDetector.extractOtp(trimmed) != null ||
                PURE_OTP_DIGITS_REGEX.matches(trimmed)
            ) {
                return "otp_blocked"
            }
        }
        val hash = trimmed.hashCode()
        if (hash == lastReceivedClipHash) return "echo_skipped"
        lastReceivedClipHash = hash
        val ok = sendEnvelope(
            ws,
            LinkMsgType.CLIPBOARD_SYNC,
            "",
            ClipboardSyncPayload(text = trimmed, source = "phone", timestampMs = System.currentTimeMillis())
        )
        return if (ok) "sent" else "disconnected"
    }

    /** #518 手機端推送網址至桌面瀏覽器開啟（嚴格限制 http:// 或 https://） */
    fun sendOpenUrlToDesktop(rawUrl: String): Boolean {
        val url = rawUrl.trim()
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return false
        }
        val ws = activeWs ?: return false
        return sendEnvelope(ws, LinkMsgType.OPEN_URL_ON_PEER, "", OpenUrlReq(url = url.take(2048)))
    }

    /** #532 主動情報鏡射至已連線桌面（標題＋≤60字節錄，遵守桌面 toast 頻率／聚合） */
    fun pushProactiveEventToDesktop(
        title: String,
        summary: String,
        topicIds: List<Long>,
        timestamp: Long = System.currentTimeMillis()
    ): Boolean {
        val ws = activeWs ?: return false
        return sendEnvelope(
            ws,
            LinkMsgType.PROACTIVE_EVENT_PUSH,
            "",
            ProactiveEventPush(
                id = timestamp,
                title = title.take(80),
                summary = summary.take(60),
                topicIds = topicIds,
                timestamp = timestamp
            )
        )
    }

    fun unpairDesktop(ctx: Context, instanceId: String) {
        val appCtx = ctx.applicationContext
        val ws = activeWs
        if (currentTargetDesktopId == instanceId && ws != null) {
            runCatching {
                sendEnvelope(ws, LinkMsgType.UNPAIRED, "", ErrorPayload(LinkErrorCode.AUTH_FAILED, "Unpaired by phone"))
            }
            disconnect(clearError = true)
        }
        DesktopLinkStore.removePairedDevice(appCtx, instanceId)
    }

    /**
     * #487 手動「立即診斷」：繞過 15s 探測冷卻，直接對目標 host:port 執行一次 TCP 連通探測並記錄結果。
     */
    fun probeDesktopNow(ctx: Context, host: String, port: Int) {
        val appCtx = ctx.applicationContext
        val cleanHost = host.trim().removePrefix("wss://").removePrefix("ws://").substringBefore("/").substringBefore(":")
        val cleanPort = port.coerceIn(1024, 65535)
        if (cleanHost.isBlank()) return

        probeJob?.cancel()
        // 手動診斷繞過同目標冷卻
        lastTimeoutTargetKey = null
        lastTimeoutAtMs = 0L
        _status.update { it.copy(isProbing = true, probeCooldownSec = 0) }

        probeJob = scope.launch {
            val startedAt = System.currentTimeMillis()
            DesktopLinkStore.recordDiagnostic(appCtx, "PROBE", "START", "$cleanHost:$cleanPort")
            try {
                java.net.Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(cleanHost, cleanPort), TCP_PROBE_TIMEOUT_MS)
                }
                val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
                DesktopLinkStore.recordDiagnostic(appCtx, "PROBE", "TCP_OK", "$cleanHost:$cleanPort (${elapsed}ms)")
                _status.update { cur ->
                    cur.copy(
                        isProbing = false,
                        probeCooldownSec = 0,
                        lastErrorCode = if (cur.lastErrorCode == LinkErrorCode.TIMEOUT) null else cur.lastErrorCode,
                        lastErrorDetail = if (cur.lastErrorCode == LinkErrorCode.TIMEOUT) null else cur.lastErrorDetail
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: java.net.SocketTimeoutException) {
                lastTimeoutTargetKey = "$cleanHost:$cleanPort"
                lastTimeoutAtMs = System.currentTimeMillis()
                val detail = e.message ?: "failed to connect to $cleanHost:$cleanPort after ${TCP_PROBE_TIMEOUT_MS}ms"
                DesktopLinkStore.recordDiagnostic(appCtx, "TCP", LinkErrorCode.TIMEOUT, detail)
                _status.update {
                    it.copy(
                        isProbing = false,
                        lastErrorCode = LinkErrorCode.TIMEOUT,
                        lastErrorDetail = detail,
                        probeCooldownSec = (PROBE_COOLDOWN_MS / 1000L).toInt()
                    )
                }
            } catch (e: ConnectException) {
                val detail = e.message ?: "Connection refused"
                DesktopLinkStore.recordDiagnostic(appCtx, "TCP", "REFUSED", detail)
                _status.update {
                    it.copy(
                        isProbing = false,
                        lastErrorCode = LinkErrorCode.TCP_UNREACHABLE,
                        lastErrorDetail = detail,
                        probeCooldownSec = 0
                    )
                }
            } catch (e: Throwable) {
                val (stage, code) = classifyThrowable(e)
                val detail = e.message ?: e.javaClass.simpleName
                DesktopLinkStore.recordDiagnostic(appCtx, stage, code, detail)
                _status.update {
                    it.copy(
                        isProbing = false,
                        lastErrorCode = code,
                        lastErrorDetail = detail,
                        probeCooldownSec = 0
                    )
                }
            }
        }
    }

    private fun startConnectionFlow(appCtx: Context, target: ConnectTarget) {
        ensureScreenReceiverRegistered(appCtx)
        reconnectJob?.cancel()
        reconnectJob = null
        closeActiveSocket("Switching target")
        activeTarget = target
        shouldKeepConnected = true
        openSocket(appCtx, target, backoffSec = 1)
    }

    private fun openSocket(appCtx: Context, target: ConnectTarget, backoffSec: Int) {
        if (!shouldKeepConnected) return
        closeActiveSocket("Opening new socket")
        val mySeq = sessionSeq.incrementAndGet()
        val wssUrl = "wss://${target.host}:${target.port}${LinkProtocol.WS_PATH}"
        _status.update {
            it.copy(
                connState = PhoneLinkConnState.CONNECTING,
                connectedHostPort = "${target.host}:${target.port}",
                lastErrorCode = null,
                lastErrorDetail = null,
                reconnectRemainingSec = 0,
                reconnectTotalSec = 0
            )
        }
        DesktopLinkStore.recordDiagnostic(appCtx, "TCP", "CONNECTING", "${target.host}:${target.port}")

        val observedCertFp = AtomicReference("")
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val cert = chain?.firstOrNull()
                    ?: throw CertificateException("Server presented empty certificate chain")
                val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                observedCertFp.set(digest.joinToString("") { "%02x".format(it) })
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        val client = runCatching {
            val sslCtx = SSLContext.getInstance("TLS")
            sslCtx.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
            OkHttpClient.Builder()
                .sslSocketFactory(sslCtx.socketFactory, trustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(15, TimeUnit.SECONDS)
                .build()
        }.getOrElse { err ->
            val msg = err.message ?: err.javaClass.simpleName
            DesktopLinkStore.recordDiagnostic(appCtx, "TLS", LinkErrorCode.TLS_ERROR, msg)
            _status.update {
                it.copy(
                    connState = PhoneLinkConnState.DISCONNECTED,
                    lastErrorCode = LinkErrorCode.TLS_ERROR,
                    lastErrorDetail = msg
                )
            }
            shouldKeepConnected = false
            return
        }

        val request = Request.Builder().url(wssUrl).build()
        var sessionAuthed = false
        var activeDesktopId = target.desktopId.orEmpty()
        var activeDesktopName = target.desktopName
        val terminalHandled = AtomicBoolean(false)

        val listener = object : WebSocketListener() {
            private fun isCurrentSession(webSocket: WebSocket): Boolean {
                if (sessionSeq.get() != mySeq) return false
                val cur = activeWs
                return cur == null || cur === webSocket
            }

            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!isCurrentSession(webSocket)) {
                    runCatching { webSocket.close(1000, "Superseded") }
                    return
                }
                lastTimeoutTargetKey = null
                lastTimeoutAtMs = 0L
                DesktopLinkStore.recordDiagnostic(appCtx, "TLS", "OPEN", "fp=${observedCertFp.get().take(8)}…")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrentSession(webSocket)) return
                val env = runCatching { gson.fromJson(text, LinkEnvelope::class.java) }.getOrNull() ?: return
                scope.launch {
                    if (!isCurrentSession(webSocket)) return@launch
                    try {
                        when (env.type) {
                            LinkMsgType.HELLO -> {
                                val hello = runCatching { gson.fromJson(env.payloadJson, HelloPayload::class.java) }.getOrNull()
                                    ?: return@launch
                                if (hello.protocolVersion != LinkProtocol.PROTOCOL_VERSION) {
                                    DesktopLinkStore.recordDiagnostic(
                                        appCtx,
                                        "HANDSHAKE",
                                        LinkErrorCode.VERSION_MISMATCH,
                                        "desktop=${hello.protocolVersion}, phone=${LinkProtocol.PROTOCOL_VERSION}"
                                    )
                                    // Clear stale pairing if connecting to saved desktop so user can cleanly re-pair
                                    target.desktopId?.takeIf { it.isNotBlank() }?.let {
                                        DesktopLinkStore.removePairedDevice(appCtx, it)
                                    }
                                    shouldKeepConnected = false
                                    _status.update {
                                        it.copy(
                                            connState = PhoneLinkConnState.DISCONNECTED,
                                            lastErrorCode = LinkErrorCode.VERSION_MISMATCH,
                                            lastErrorDetail = "desktop=${hello.protocolVersion}, phone=${LinkProtocol.PROTOCOL_VERSION}"
                                        )
                                    }
                                    webSocket.close(1008, "Version mismatch")
                                    return@launch
                                }

                                // 僅採信 TLS 實測憑證 SHA-256 指紋，移除 hello.certFingerprint 回退；未取得實測憑證即斷線
                                val actualFp = observedCertFp.get().trim().lowercase()
                                if (actualFp.length != 64) {
                                    DesktopLinkStore.recordDiagnostic(
                                        appCtx,
                                        "TLS",
                                        LinkErrorCode.TLS_ERROR,
                                        "missing_observed_cert_fp"
                                    )
                                    shouldKeepConnected = false
                                    _status.update {
                                        it.copy(
                                            connState = PhoneLinkConnState.DISCONNECTED,
                                            lastErrorCode = LinkErrorCode.TLS_ERROR,
                                            lastErrorDetail = "Missing observed TLS certificate fingerprint"
                                        )
                                    }
                                    webSocket.close(1008, "Missing TLS certificate fingerprint")
                                    return@launch
                                }

                                val expected = target.expectedFingerprint.trim().lowercase()
                                val isPairedConnection = target.pairCode == null
                                // 已配對連線強制完整 64 字元 SHA-256 比對（移除 8 字元前綴匹配 MITM 風險）
                                val fpMatches = if (isPairedConnection) {
                                    expected.length == 64 && actualFp == expected
                                } else {
                                    expected.isBlank() || (expected.length == 64 && actualFp == expected)
                                }
                                if (!fpMatches) {
                                    DesktopLinkStore.recordDiagnostic(
                                        appCtx,
                                        "TLS",
                                        LinkErrorCode.FINGERPRINT_MISMATCH,
                                        "expected=${expected.take(8)}, actual=${actualFp.take(8)}"
                                    )
                                    DesktopLinkStore.recordUnknownAttempt(appCtx, target.host, "fingerprint_mismatch")
                                    shouldKeepConnected = false
                                    _status.update {
                                        it.copy(
                                            connState = PhoneLinkConnState.DISCONNECTED,
                                            lastErrorCode = LinkErrorCode.FINGERPRINT_MISMATCH,
                                            lastErrorDetail = "expected=${expected.take(8)}…, actual=${actualFp.take(8)}…"
                                        )
                                    }
                                    webSocket.close(1008, "Fingerprint mismatch")
                                    return@launch
                                }

                                if (hello.serverInstanceId.isNotBlank()) {
                                    activeDesktopId = hello.serverInstanceId
                                }
                                if (hello.deviceName.isNotBlank()) {
                                    activeDesktopName = hello.deviceName
                                }
                                val phoneId = DesktopLinkStore.getOrCreatePhoneInstanceId(appCtx)
                                val phoneName = Build.MODEL.ifBlank { "Android" }

                                if (target.pairCode != null) {
                                    sendEnvelope(
                                        webSocket,
                                        LinkMsgType.PAIR_REQUEST,
                                        UUID.randomUUID().toString(),
                                        PairRequestPayload(
                                            instanceId = phoneId,
                                            desktopName = phoneName,
                                            code = target.pairCode,
                                            protocolVersion = LinkProtocol.PROTOCOL_VERSION,
                                            expectedFingerprint = actualFp
                                        )
                                    )
                                } else {
                                    val secret = target.hmacSecret.orEmpty()
                                    val hmac = DesktopLinkStore.computeHmacSha256Hex(secret, hello.challenge)
                                    sendEnvelope(
                                        webSocket,
                                        LinkMsgType.AUTH_REQUEST,
                                        UUID.randomUUID().toString(),
                                        AuthRequestPayload(
                                            instanceId = phoneId,
                                            hmacResponse = hmac,
                                            protocolVersion = LinkProtocol.PROTOCOL_VERSION,
                                            expectedFingerprint = actualFp
                                        )
                                    )
                                }
                            }

                            LinkMsgType.PAIR_RESPONSE -> {
                                val resp = runCatching { gson.fromJson(env.payloadJson, PairResponsePayload::class.java) }.getOrNull()
                                    ?: return@launch
                                if (resp.ok) {
                                    val finalFp = observedCertFp.get().trim().lowercase()
                                    if (finalFp.length != 64) {
                                        DesktopLinkStore.recordDiagnostic(
                                            appCtx,
                                            "TLS",
                                            LinkErrorCode.TLS_ERROR,
                                            "missing_observed_cert_fp_on_pair"
                                        )
                                        shouldKeepConnected = false
                                        _status.update {
                                            it.copy(
                                                connState = PhoneLinkConnState.DISCONNECTED,
                                                lastErrorCode = LinkErrorCode.TLS_ERROR,
                                                lastErrorDetail = "Missing observed TLS certificate fingerprint"
                                            )
                                        }
                                        webSocket.close(1008, "Missing TLS certificate fingerprint")
                                        return@launch
                                    }
                                    val now = System.currentTimeMillis()
                                    val finalDesktopId = activeDesktopId.ifBlank { UUID.randomUUID().toString() }
                                    val finalName = resp.deviceName.ifBlank { activeDesktopName }
                                    val paired = PairedDesktop(
                                        instanceId = finalDesktopId,
                                        name = finalName,
                                        hmacSecret = resp.hmacSecret,
                                        pairedAt = now,
                                        lastSeenAt = now,
                                        host = target.host,
                                        port = target.port,
                                        fingerprint = finalFp
                                    )
                                    DesktopLinkStore.upsertPairedDevice(appCtx, paired)
                                    DesktopLinkStore.setLastConnectedDesktopId(appCtx, finalDesktopId)
                                    DesktopLinkStore.recordDiagnostic(appCtx, "HANDSHAKE", "PAIR_OK", finalName)
                                    sessionAuthed = true
                                    connectedAtMs = now
                                    currentTargetDesktopId = finalDesktopId
                                    activeTarget = ConnectTarget(
                                        host = target.host,
                                        port = target.port,
                                        expectedFingerprint = finalFp,
                                        desktopId = finalDesktopId,
                                        desktopName = finalName,
                                        hmacSecret = resp.hmacSecret,
                                        pairCode = null
                                    )
                                    _status.update {
                                        PhoneLinkStatus(
                                            connState = PhoneLinkConnState.CONNECTED,
                                            connectedDesktopId = finalDesktopId,
                                            connectedDesktopName = finalName,
                                            connectedHostPort = "${target.host}:${target.port}"
                                        )
                                    }
                                    startBatteryStatusMonitor(appCtx, webSocket)
                                } else {
                                    val errCode = resp.errorCode.ifBlank { LinkErrorCode.AUTH_FAILED }
                                    DesktopLinkStore.recordDiagnostic(appCtx, "HANDSHAKE", errCode, resp.errorMessage)
                                    shouldKeepConnected = false
                                    _status.update {
                                        it.copy(
                                            connState = PhoneLinkConnState.DISCONNECTED,
                                            lastErrorCode = errCode,
                                            lastErrorDetail = resp.errorMessage
                                        )
                                    }
                                    webSocket.close(1008, errCode)
                                }
                            }

                            LinkMsgType.AUTH_RESPONSE -> {
                                val resp = runCatching { gson.fromJson(env.payloadJson, AuthResponsePayload::class.java) }.getOrNull()
                                    ?: return@launch
                                if (resp.ok) {
                                    val now = System.currentTimeMillis()
                                    val finalDesktopId = activeDesktopId.ifBlank { target.desktopId.orEmpty() }
                                    val finalName = resp.deviceName.ifBlank { activeDesktopName }
                                    if (finalDesktopId.isNotBlank()) {
                                        DesktopLinkStore.updateLastSeen(appCtx, finalDesktopId, now)
                                        DesktopLinkStore.setLastConnectedDesktopId(appCtx, finalDesktopId)
                                    }
                                    DesktopLinkStore.recordDiagnostic(appCtx, "HANDSHAKE", "AUTH_OK", finalName)
                                    sessionAuthed = true
                                    connectedAtMs = now
                                    currentTargetDesktopId = finalDesktopId
                                    _status.update {
                                        PhoneLinkStatus(
                                            connState = PhoneLinkConnState.CONNECTED,
                                            connectedDesktopId = finalDesktopId,
                                            connectedDesktopName = finalName,
                                            connectedHostPort = "${target.host}:${target.port}"
                                        )
                                    }
                                    startBatteryStatusMonitor(appCtx, webSocket)
                                } else {
                                    val errCode = resp.errorCode.ifBlank { LinkErrorCode.AUTH_FAILED }
                                    DesktopLinkStore.recordDiagnostic(appCtx, "HANDSHAKE", errCode, resp.errorMessage)
                                    if (errCode == LinkErrorCode.UNKNOWN_INSTANCE || errCode == LinkErrorCode.VERSION_MISMATCH) {
                                        target.desktopId?.takeIf { it.isNotBlank() }?.let {
                                            DesktopLinkStore.removePairedDevice(appCtx, it)
                                        }
                                    }
                                    shouldKeepConnected = false
                                    _status.update {
                                        it.copy(
                                            connState = PhoneLinkConnState.DISCONNECTED,
                                            lastErrorCode = errCode,
                                            lastErrorDetail = resp.errorMessage
                                        )
                                    }
                                    webSocket.close(1008, errCode)
                                }
                            }

                            LinkMsgType.UNPAIRED -> {
                                val id = currentTargetDesktopId ?: target.desktopId
                                if (!id.isNullOrBlank()) {
                                    DesktopLinkStore.removePairedDevice(appCtx, id)
                                }
                                DesktopLinkStore.recordDiagnostic(appCtx, "PROTOCOL", "UNPAIRED", "Removed by desktop")
                                disconnect(clearError = false)
                                _status.update {
                                    it.copy(
                                        lastErrorCode = LinkErrorCode.AUTH_FAILED,
                                        lastErrorDetail = "Unpaired by desktop"
                                    )
                                }
                            }

                            LinkMsgType.PING -> {
                                sendEnvelope(webSocket, LinkMsgType.PONG, env.reqId, emptyMap<String, String>())
                            }

                            LinkMsgType.ERROR -> {
                                val err = runCatching { gson.fromJson(env.payloadJson, ErrorPayload::class.java) }.getOrNull()
                                if (err != null) {
                                    DesktopLinkStore.recordDiagnostic(appCtx, "PROTOCOL", err.errorCode, err.errorMessage)
                                }
                            }

                            else -> {
                                if (!sessionAuthed) {
                                    DesktopLinkStore.recordUnknownAttempt(appCtx, target.host, "unauthenticated_command:${env.type}")
                                    return@launch
                                }
                                handleDesktopCommand(appCtx, activeDesktopId.ifBlank { "desktop" }, env, webSocket)
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        DesktopLinkStore.recordDiagnostic(appCtx, "PROTOCOL", LinkErrorCode.INTERNAL_ERROR, "${env.type}: ${e.javaClass.simpleName}")
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!isCurrentSession(webSocket) || !terminalHandled.compareAndSet(false, true)) return
                synchronized(wsLock) {
                    if (sessionSeq.get() != mySeq) return
                    if (activeWs === webSocket) {
                        activeWs = null
                    }
                }
                messagePushJob?.cancel()
                messagePushJob = null
                isSubscribedToMessages = false
                stopBatteryStatusMonitor()
                DesktopLinkStore.recordDiagnostic(appCtx, "TCP", "CLOSED", "code=$code $reason")
                val maxCap = if (isScreenOff) SCREEN_OFF_BACKOFF_CAP_SEC else SCREEN_ON_BACKOFF_CAP_SEC
                val nextTarget = activeTarget
                if (shouldKeepConnected && nextTarget != null && nextTarget.hmacSecret != null) {
                    scheduleReconnect(
                        appCtx,
                        nextTarget,
                        if (sessionAuthed) 1 else (backoffSec * 2).coerceAtMost(maxCap),
                        expectedSeq = mySeq
                    )
                } else {
                    _status.update { it.copy(connState = PhoneLinkConnState.DISCONNECTED) }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!isCurrentSession(webSocket) || !terminalHandled.compareAndSet(false, true)) return
                synchronized(wsLock) {
                    if (sessionSeq.get() != mySeq) return
                    if (activeWs === webSocket) {
                        activeWs = null
                    }
                }
                messagePushJob?.cancel()
                messagePushJob = null
                isSubscribedToMessages = false
                stopBatteryStatusMonitor()
                val (stage, code) = classifyThrowable(t)
                val diagCode = if (t is ConnectException || t.cause is ConnectException) "REFUSED" else code
                val detail = t.message ?: t.javaClass.simpleName
                if (code == LinkErrorCode.TIMEOUT) {
                    lastTimeoutTargetKey = "${target.host}:${target.port}"
                    lastTimeoutAtMs = System.currentTimeMillis()
                }
                DesktopLinkStore.recordDiagnostic(appCtx, stage, diagCode, detail)

                // Do not auto-retry pairing failures or TLS/fingerprint failures
                val nextTarget = activeTarget
                val canAutoRetry = shouldKeepConnected &&
                    nextTarget != null &&
                    nextTarget.hmacSecret != null &&
                    code != LinkErrorCode.FINGERPRINT_MISMATCH &&
                    code != LinkErrorCode.VERSION_MISMATCH

                _status.update {
                    it.copy(
                        connState = if (canAutoRetry) PhoneLinkConnState.RECONNECTING else PhoneLinkConnState.DISCONNECTED,
                        lastErrorCode = code,
                        lastErrorDetail = detail,
                        probeCooldownSec = if (code == LinkErrorCode.TIMEOUT) (PROBE_COOLDOWN_MS / 1000L).toInt() else 0
                    )
                }

                if (canAutoRetry && nextTarget != null) {
                    val maxCap = if (isScreenOff) SCREEN_OFF_BACKOFF_CAP_SEC else SCREEN_ON_BACKOFF_CAP_SEC
                    scheduleReconnect(
                        appCtx,
                        nextTarget,
                        if (sessionAuthed) 1 else (backoffSec * 2).coerceAtMost(maxCap),
                        expectedSeq = mySeq
                    )
                } else {
                    shouldKeepConnected = false
                }
            }
        }

        synchronized(wsLock) {
            if (sessionSeq.get() != mySeq) {
                runCatching { client.dispatcher.executorService.shutdown() }
                return
            }
            activeClient = client
            activeWs = client.newWebSocket(request, listener)
        }
    }

    private fun scheduleReconnect(
        appCtx: Context,
        target: ConnectTarget,
        delaySec: Int,
        expectedSeq: Long = sessionSeq.get()
    ) {
        reconnectJob?.cancel()
        val maxCap = if (isScreenOff) SCREEN_OFF_BACKOFF_CAP_SEC else SCREEN_ON_BACKOFF_CAP_SEC
        val targetKey = "${target.host}:${target.port}"
        val cooldownRemainSec = if (lastTimeoutTargetKey == targetKey) {
            val remainMs = PROBE_COOLDOWN_MS - (System.currentTimeMillis() - lastTimeoutAtMs)
            if (remainMs > 0L) ((remainMs + 999L) / 1000L).toInt() else 0
        } else {
            0
        }
        val capped = maxOf(delaySec, cooldownRemainSec).coerceIn(1, maxCap)
        reconnectJob = scope.launch {
            if (isScreenOff) {
                // #489 熄屏時不做每秒 UI 倒數喚醒，整段退避睡眠（亮屏由 ACTION_SCREEN_ON 立即中斷並重連）
                if (!shouldKeepConnected || sessionSeq.get() != expectedSeq) return@launch
                _status.update {
                    it.copy(
                        connState = PhoneLinkConnState.RECONNECTING,
                        reconnectRemainingSec = capped,
                        reconnectTotalSec = capped
                    )
                }
                delay(capped * 1000L)
            } else {
                for (rem in capped downTo 1) {
                    if (!shouldKeepConnected || sessionSeq.get() != expectedSeq) return@launch
                    if (isScreenOff) {
                        delay(rem * 1000L)
                        break
                    }
                    _status.update {
                        it.copy(
                            connState = PhoneLinkConnState.RECONNECTING,
                            reconnectRemainingSec = rem,
                            reconnectTotalSec = capped
                        )
                    }
                    delay(1000L)
                }
            }
            if (shouldKeepConnected && sessionSeq.get() == expectedSeq) {
                openSocket(appCtx, target, capped)
            }
        }
    }

    private fun closeActiveSocket(reason: String) {
        sessionSeq.incrementAndGet()
        val ws: WebSocket?
        val cli: OkHttpClient?
        synchronized(wsLock) {
            ws = activeWs
            activeWs = null
            cli = activeClient
            activeClient = null
        }
        runCatching { ws?.close(1000, reason) }
        runCatching { cli?.dispatcher?.executorService?.shutdown() }
    }

    private fun classifyThrowable(t: Throwable): Pair<String, String> = when (t) {
        is SocketTimeoutException -> "TCP" to LinkErrorCode.TIMEOUT
        is ConnectException, is NoRouteToHostException, is UnknownHostException -> "TCP" to LinkErrorCode.TCP_UNREACHABLE
        is SSLException, is CertificateException -> "TLS" to LinkErrorCode.TLS_ERROR
        else -> {
            val cause = t.cause
            when (cause) {
                is SocketTimeoutException -> "TCP" to LinkErrorCode.TIMEOUT
                is ConnectException, is NoRouteToHostException, is UnknownHostException -> "TCP" to LinkErrorCode.TCP_UNREACHABLE
                is SSLException, is CertificateException -> "TLS" to LinkErrorCode.TLS_ERROR
                else -> "HANDSHAKE" to LinkErrorCode.HANDSHAKE_ERROR
            }
        }
    }

    private fun sendEnvelope(ws: WebSocket, type: String, reqId: String, payload: Any): Boolean {
        val env = LinkEnvelope(type = type, reqId = reqId, payloadJson = gson.toJson(payload))
        return runCatching { ws.send(gson.toJson(env)) }.getOrDefault(false)
    }

    // ---- All 7+ bidirectional M2/#467 command handlers (Data sovereignty stays on phone) ----

    private suspend fun handleDesktopCommand(
        appCtx: Context,
        desktopId: String,
        env: LinkEnvelope,
        ws: WebSocket
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
                val tagMap = if (filtered.isNotEmpty()) {
                    runCatching {
                        db.tagDao().crossRefsForMessages(filtered.map { it.id })
                            .groupBy({ it.messageId }, { it.tagId })
                    }.getOrDefault(emptyMap())
                } else emptyMap()
                val items = filtered.map { m ->
                    MessageSummaryDto(
                        id = m.id,
                        packageName = m.packageName,
                        sender = m.sender,
                        preview = m.content.replace(WHITESPACE_REGEX, " ").trim().take(200),
                        timestamp = m.timestamp,
                        riskLabel = m.riskLabel,
                        isStarred = m.isStarred,
                        isRead = m.isRead,
                        conversationKey = m.conversationKey,
                        tagIds = tagMap[m.id].orEmpty(),
                        appLabel = resolveAppDisplayLabel(appCtx, m.packageName, m.appLabel),
                        isGroup = m.conversationKey.isNotBlank() && m.conversationKey != m.sender
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
                isSubscribedToMessages = true
                val latest = db.messageDao().paged(1, 0).firstOrNull()?.id ?: 0L
                lastPushedCursor = maxOf(lastPushedCursor, latest)
                ensureMessagePushLoop(appCtx)
                sendEnvelope(ws, LinkMsgType.MESSAGES_SUBSCRIBE_RES, env.reqId, mapOf("subscribed" to true, "cursor" to lastPushedCursor))
            }

            LinkMsgType.MESSAGES_MARK_READ -> {
                val req = runCatching { gson.fromJson(env.payloadJson, MarkReadReq::class.java) }.getOrNull() ?: MarkReadReq()
                var updated = 0
                if (req.all) {
                    db.messageDao().markAllRead()
                    updated = -1
                    sendEnvelope(ws, LinkMsgType.MESSAGE_READ_SYNC, "", MessageReadSyncPush(all = true))
                } else if (req.ids.isNotEmpty()) {
                    db.messageDao().markReadByIds(req.ids)
                    updated = req.ids.size
                    sendEnvelope(ws, LinkMsgType.MESSAGE_READ_SYNC, "", MessageReadSyncPush(ids = req.ids))
                } else if (req.packageName.isNotBlank() && req.conversationKey.isNotBlank()) {
                    val ids = db.messageDao().unreadIdsByConversation(req.packageName, req.conversationKey)
                    updated = db.messageDao().markReadByConversation(req.packageName, req.conversationKey)
                    if (ids.isNotEmpty()) {
                        sendEnvelope(ws, LinkMsgType.MESSAGE_READ_SYNC, "", MessageReadSyncPush(ids = ids))
                    }
                }
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.MESSAGES_MARK_READ, true, "count=$updated")
                com.phantom.checker.widget.WidgetUpdater.scheduleRefresh(appCtx)
                sendEnvelope(ws, LinkMsgType.MESSAGES_MARK_READ_RES, env.reqId, MarkReadRes(ok = true, updatedCount = updated, note = "Marked read in PhantomCheck list only"))
            }

            LinkMsgType.STATS_SNAPSHOT -> {
                val startOfDay = NotificationCollectorService.startOfTodayMillis()
                val nowEnd = System.currentTimeMillis() + 1L
                val snap = db.messageDao().statsSnapshot(startOfDay)
                val suspicious = runCatching { db.messageDao().suspiciousCount() }.getOrDefault(0)
                val warning = runCatching { db.messageDao().warningCount() }.getOrDefault(0)
                val cal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                    add(java.util.Calendar.DAY_OF_YEAR, -6)
                }
                val sevenDaysAgo = cal.timeInMillis
                val cal30 = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                    add(java.util.Calendar.DAY_OF_YEAR, -29)
                }
                val thirtyDaysAgo = cal30.timeInMillis
                val dayMap30 = runCatching { db.messageDao().countByDaySince(thirtyDaysAgo).associate { it.dayKey to it.cnt } }.getOrDefault(emptyMap())
                val keyFmt = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                val labelFmt = java.text.SimpleDateFormat("M/d", java.util.Locale.getDefault())
                val dailyCounts = (0..6).map { offset ->
                    val c = (cal.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, offset) }
                    val key = keyFmt.format(c.time)
                    DayCountDto(label = labelFmt.format(c.time), count = dayMap30[key] ?: 0)
                }
                val dailyCounts30 = (0..29).map { offset ->
                    val c = (cal30.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, offset) }
                    val key = keyFmt.format(c.time)
                    DayCountDto(label = labelFmt.format(c.time), count = dayMap30[key] ?: 0)
                }
                val hourMap = runCatching { db.messageDao().countByHourSince(startOfDay).associate { it.hour to it.cnt } }.getOrDefault(emptyMap())
                val hourlyCounts = (0..23).map { h -> HourCountDto(hour = h, count = hourMap[h] ?: 0) }
                // #498 / R6: 改由 Room 區間聚合查詢今日活躍 App 與週排行，避免 paged(600, 0) 截斷失真
                val activeAppsToday = runCatching {
                    db.messageDao().topAppsBetween(startOfDay, nowEnd, limit = 12)
                }.getOrDefault(emptyList())
                    .filter { it.packageName.isNotBlank() }
                    .map { row ->
                        ActiveAppStatDto(
                            packageName = row.packageName,
                            appLabel = resolveAppDisplayLabel(appCtx, row.packageName, row.appLabel),
                            count = row.cnt
                        )
                    }
                val weeklyTopApps = runCatching {
                    db.messageDao().topAppsBetween(sevenDaysAgo, nowEnd, limit = 5)
                }.getOrDefault(emptyList())
                    .filter { it.packageName.isNotBlank() }
                    .map { row ->
                        ActiveAppStatDto(
                            packageName = row.packageName,
                            appLabel = resolveAppDisplayLabel(appCtx, row.packageName, row.appLabel),
                            count = row.cnt
                        )
                    }
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
                        recentPackages = snap.recentPackages(),
                        suspicious = suspicious,
                        dailyCounts = dailyCounts,
                        hourlyCounts = hourlyCounts,
                        dailyCounts30 = dailyCounts30,
                        activeAppsToday = activeAppsToday,
                        weeklyTopApps = weeklyTopApps,
                        warning = warning
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
                } else emptyList()
                sendEnvelope(ws, LinkMsgType.CHAT_HISTORY_RES, env.reqId, ChatHistoryRes(sessionId = sid, messages = msgs))
            }

            LinkMsgType.CHAT_SEND -> {
                val req = runCatching { gson.fromJson(env.payloadJson, ChatSendReq::class.java) }.getOrNull() ?: ChatSendReq()
                val text = req.text.trim()
                if (text.isBlank()) {
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.CHAT_SEND, false, "empty_text")
                    sendEnvelope(ws, LinkMsgType.CHAT_REPLY, env.reqId, ChatReplyRes(ok = false, errorCode = LinkErrorCode.INVALID_REQUEST, errorMessage = "Empty message"))
                    return
                }
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.CHAT_SEND, true, "len=${text.length}")
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
                    // #515 修正懸浮球與桌面端 AI 訊息計數失真：直接自手機 Room 注入精確統計與近 30 則淨化摘要
                    val calToday = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val totalMsgs = runCatching { db.messageDao().todayCount(0L) }.getOrDefault(0)
                    val todayMsgs = runCatching { db.messageDao().todayCount(calToday) }.getOrDefault(0)
                    val unreadMsgs = runCatching { db.messageDao().unreadCount() }.getOrDefault(0)
                    val recentMsgs = runCatching { db.messageDao().recent().take(30) }.getOrDefault(emptyList())
                    val contextBlock = buildString {
                        append(AiClient.buildRagSummaryPrompt())
                        append("\n\n<本機訊息快照>\n")
                        append("總訊息數：$totalMsgs 則；今日訊息數：$todayMsgs 則；未讀訊息數：$unreadMsgs 則。\n")
                        if (recentMsgs.isNotEmpty()) {
                            append("最近 ${recentMsgs.size} 則訊息（最新在前）：\n")
                            recentMsgs.forEach { m ->
                                val app = AiClient.sanitizeForPrompt(m.appLabel.ifBlank { m.packageName }).take(20)
                                val sender = AiClient.sanitizeForPrompt(m.sender).take(24)
                                val body = AiClient.sanitizeForPrompt(m.content).replace("\n", " ").take(120)
                                val readTag = if (m.isRead) "已讀" else "未讀"
                                append("- [$app] $sender ($readTag/${m.riskLabel}): $body\n")
                            }
                        }
                        append("</本機訊息快照>")
                    }
                    val result = AiClient.chatAssistant(
                        systemPrompt = contextBlock,
                        history = existing,
                        userMessage = text,
                        context = appCtx
                    )
                    result.onSuccess { outcome ->
                        val reply = outcome.replyText.trim()
                        db.chatDao().insertMessage(ChatMessageEntity(sessionId = sid, role = ChatMessageEntity.ROLE_ASSISTANT, content = reply))
                        sendEnvelope(ws, LinkMsgType.CHAT_REPLY, env.reqId, ChatReplyRes(ok = true, sessionId = sid, role = "assistant", content = reply))
                    }.onFailure { err ->
                        sendEnvelope(ws, LinkMsgType.CHAT_REPLY, env.reqId, ChatReplyRes(ok = false, sessionId = sid, errorCode = LinkErrorCode.INTERNAL_ERROR, errorMessage = err.message ?: "Chat failed"))
                    }
                }
            }

            LinkMsgType.TODOS_LIST -> {
                val req = runCatching { gson.fromJson(env.payloadJson, TodosListReq::class.java) }.getOrNull() ?: TodosListReq()
                val all = db.todoDao().all()
                val filtered = if (req.done != null) all.filter { it.isDone == req.done } else all
                val items = filtered.map {
                    TodoItemDto(
                        id = it.id,
                        title = it.title,
                        dueAt = it.dueAt,
                        isDone = it.isDone,
                        repeatRule = it.repeatRule,
                        priority = it.priority,
                        notes = it.notes,
                        createdAt = it.createdAt,
                        completedAt = it.completedAt ?: 0L
                    )
                }
                sendEnvelope(ws, LinkMsgType.TODOS_LIST_RES, env.reqId, TodosListRes(items = items))
            }

            LinkMsgType.TODOS_TOGGLE -> {
                val req = runCatching { gson.fromJson(env.payloadJson, TodosToggleReq::class.java) }.getOrNull()
                val todo = if (req != null) db.todoDao().byId(req.id) else null
                if (req == null || todo == null) {
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.TODOS_TOGGLE, false, "not_found")
                    sendEnvelope(ws, LinkMsgType.TODOS_TOGGLE_RES, env.reqId, TodosToggleRes(ok = false, errorMessage = "Todo not found"))
                    return
                }
                val spawned = ReminderScheduler.completeTodoWithRecurrence(appCtx, todo, req.done)
                runCatching { TodoTodayWidgetProvider.refreshNow(appCtx) }
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.TODOS_TOGGLE, true, "id=${todo.id},done=${req.done}")
                sendEnvelope(
                    ws,
                    LinkMsgType.TODOS_TOGGLE_RES,
                    env.reqId,
                    TodosToggleRes(ok = true, id = todo.id, isDone = req.done, spawnedNextId = spawned?.id ?: 0L, spawnedDueAt = spawned?.dueAt ?: 0L)
                )
            }

            LinkMsgType.QUICK_REPLY_SEND -> {
                val req = runCatching { gson.fromJson(env.payloadJson, QuickReplySendReq::class.java) }.getOrNull()
                if (req == null || req.packageName.isBlank() || req.text.isBlank()) {
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.QUICK_REPLY_SEND, false, "invalid_request")
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
                        DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.QUICK_REPLY_SEND, true, req.packageName)
                        sendEnvelope(ws, LinkMsgType.QUICK_REPLY_SEND_RES, env.reqId, QuickReplySendRes(ok = true))
                    }
                    is NotificationCollectorService.Companion.ReplySendResult.Unavailable -> {
                        DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.QUICK_REPLY_SEND, false, "unsupported:${req.packageName}")
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
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.WHITELIST_ADD, false, "empty")
                    sendEnvelope(ws, LinkMsgType.WHITELIST_ADD_RES, env.reqId, RuleMutateRes(ok = false, message = "Empty whitelist rule"))
                    return
                }
                val res = WhitelistRuleHelper.upsertWhitelist(
                    dao = db.aiRuleDao(),
                    ruleText = text,
                    sender = req.sender.trim().ifBlank { null },
                    packageName = req.packageName.trim().ifBlank { null },
                    onCacheInvalidate = { NotificationCollectorService.invalidateRuleCaches() }
                )
                if (res.status == WhitelistRuleHelper.Status.INSERTED || res.status == WhitelistRuleHelper.Status.REACTIVATED) {
                    PhantomSettingsBus.notifyChanged()
                }
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.WHITELIST_ADD, res.isSuccess, res.status.name)
                val dto = res.rule?.let { RuleItemDto(it.id, it.kind, it.ruleText, it.sender.orEmpty(), it.packageName.orEmpty(), it.isEnabled) }
                sendEnvelope(ws, LinkMsgType.WHITELIST_ADD_RES, env.reqId, RuleMutateRes(ok = res.isSuccess, message = res.message, item = dto))
            }

            LinkMsgType.WHITELIST_REMOVE -> {
                val req = runCatching { gson.fromJson(env.payloadJson, RuleRemoveReq::class.java) }.getOrNull()
                if (req == null || req.id <= 0L) {
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.WHITELIST_REMOVE, false, "invalid_id")
                    sendEnvelope(ws, LinkMsgType.WHITELIST_REMOVE_RES, env.reqId, RuleMutateRes(ok = false, message = "Invalid id"))
                    return
                }
                db.aiRuleDao().deleteById(req.id)
                NotificationCollectorService.invalidateRuleCaches()
                PhantomSettingsBus.notifyChanged()
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.WHITELIST_REMOVE, true, "id=${req.id}")
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
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.TAGS_ADD, false, "empty_name")
                    sendEnvelope(ws, LinkMsgType.TAGS_ADD_RES, env.reqId, TagMutateRes(ok = false, message = "Empty tag name"))
                    return
                }
                val existingTags = db.tagDao().all()
                val dup = existingTags.firstOrNull { it.name.equals(name, ignoreCase = true) }
                if (dup != null) {
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.TAGS_ADD, true, "duplicate:$name")
                    sendEnvelope(ws, LinkMsgType.TAGS_ADD_RES, env.reqId, TagMutateRes(ok = true, message = "Already exists", item = TagItemDto(dup.id, dup.name, dup.colorHex)))
                    return
                }
                val nextSortOrder = (existingTags.maxOfOrNull { it.sortOrder } ?: -1) + 1
                val color = req.colorHex.ifBlank { "#6EA8FE" }
                val id = db.tagDao().insert(TagEntity(name = name, colorHex = color, sortOrder = nextSortOrder))
                NotificationCollectorService.invalidateRuleCaches()
                PhantomSettingsBus.notifyChanged()
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.TAGS_ADD, true, name)
                sendEnvelope(ws, LinkMsgType.TAGS_ADD_RES, env.reqId, TagMutateRes(ok = true, message = "Created", item = TagItemDto(id, name, color)))
            }

            LinkMsgType.TAGS_REMOVE -> {
                val req = runCatching { gson.fromJson(env.payloadJson, TagRemoveReq::class.java) }.getOrNull()
                if (req == null || req.id <= 0L) {
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.TAGS_REMOVE, false, "invalid_id")
                    sendEnvelope(ws, LinkMsgType.TAGS_REMOVE_RES, env.reqId, TagMutateRes(ok = false, message = "Invalid id"))
                    return
                }
                db.tagDao().deleteTagWithOrphans(req.id)
                NotificationCollectorService.invalidateRuleCaches()
                PhantomSettingsBus.notifyChanged()
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.TAGS_REMOVE, true, "id=${req.id}")
                sendEnvelope(ws, LinkMsgType.TAGS_REMOVE_RES, env.reqId, TagMutateRes(ok = true, message = "Removed"))
            }

            LinkMsgType.MESSAGES_STAR_TOGGLE -> {
                val req = runCatching { gson.fromJson(env.payloadJson, StarToggleReq::class.java) }.getOrNull()
                if (req == null || req.id <= 0L) {
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.MESSAGES_STAR_TOGGLE, false, "invalid_id")
                    sendEnvelope(ws, LinkMsgType.MESSAGES_STAR_TOGGLE_RES, env.reqId, StarToggleRes(ok = false, errorMessage = "Invalid message id"))
                    return
                }
                val msg = db.messageDao().byId(req.id)
                if (msg == null) {
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.MESSAGES_STAR_TOGGLE, false, "not_found:${req.id}")
                    sendEnvelope(ws, LinkMsgType.MESSAGES_STAR_TOGGLE_RES, env.reqId, StarToggleRes(ok = false, id = req.id, errorMessage = "Message not found"))
                    return
                }
                db.messageDao().setStarred(req.id, req.starred)
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.MESSAGES_STAR_TOGGLE, true, "id=${req.id},starred=${req.starred}")
                sendEnvelope(ws, LinkMsgType.MESSAGE_STAR_SYNC, "", MessageStarSyncPush(id = req.id, isStarred = req.starred))
                sendEnvelope(ws, LinkMsgType.MESSAGES_STAR_TOGGLE_RES, env.reqId, StarToggleRes(ok = true, id = req.id, isStarred = req.starred))
            }

            LinkMsgType.MESSAGES_TAG_APPLY -> {
                val req = runCatching { gson.fromJson(env.payloadJson, MessageTagReq::class.java) }.getOrNull()
                if (req == null || req.messageId <= 0L || req.tagId <= 0L) {
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.MESSAGES_TAG_APPLY, false, "invalid_request")
                    sendEnvelope(ws, LinkMsgType.MESSAGES_TAG_APPLY_RES, env.reqId, MessageTagRes(ok = false, errorMessage = "Invalid messageId or tagId"))
                    return
                }
                db.tagDao().addTagToMessage(MessageTagCrossRef(messageId = req.messageId, tagId = req.tagId, source = "desktop"))
                val tagIds = db.tagDao().tagIdsForMessage(req.messageId)
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.MESSAGES_TAG_APPLY, true, "msg=${req.messageId},tag=${req.tagId}")
                sendEnvelope(ws, LinkMsgType.MESSAGES_TAG_APPLY_RES, env.reqId, MessageTagRes(ok = true, messageId = req.messageId, tagIds = tagIds))
            }

            LinkMsgType.MESSAGES_TAG_REMOVE -> {
                val req = runCatching { gson.fromJson(env.payloadJson, MessageTagReq::class.java) }.getOrNull()
                if (req == null || req.messageId <= 0L || req.tagId <= 0L) {
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.MESSAGES_TAG_REMOVE, false, "invalid_request")
                    sendEnvelope(ws, LinkMsgType.MESSAGES_TAG_REMOVE_RES, env.reqId, MessageTagRes(ok = false, errorMessage = "Invalid messageId or tagId"))
                    return
                }
                db.tagDao().removeTagFromMessage(req.messageId, req.tagId)
                val tagIds = db.tagDao().tagIdsForMessage(req.messageId)
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.MESSAGES_TAG_REMOVE, true, "msg=${req.messageId},tag=${req.tagId}")
                sendEnvelope(ws, LinkMsgType.MESSAGES_TAG_REMOVE_RES, env.reqId, MessageTagRes(ok = true, messageId = req.messageId, tagIds = tagIds))
            }

            LinkMsgType.TODOS_ADD -> {
                val req = runCatching { gson.fromJson(env.payloadJson, TodoAddReq::class.java) }.getOrNull() ?: TodoAddReq()
                val title = req.title.trim()
                if (title.isBlank()) {
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.TODOS_ADD, false, "empty_title")
                    sendEnvelope(ws, LinkMsgType.TODOS_ADD_RES, env.reqId, TodoMutateRes(ok = false, errorMessage = "Empty todo title"))
                    return
                }
                val now = System.currentTimeMillis()
                val dueAt = if (req.dueAt > 0L) req.dueAt else now + 60L * 60L * 1000L
                val repeat = req.repeatRule.takeIf { it == TodoEntity.REPEAT_DAILY || it == TodoEntity.REPEAT_WEEKLY } ?: TodoEntity.REPEAT_NONE
                val priority = req.priority.coerceIn(TodoEntity.PRIORITY_LOW, TodoEntity.PRIORITY_HIGH)
                val notesStr = req.notes.trim()
                val entity = TodoEntity(title = title, dueAt = dueAt, repeatRule = repeat, notes = notesStr, priority = priority)
                val id = db.todoDao().insert(entity)
                if (dueAt > now) {
                    ReminderScheduler.scheduleTodo(appCtx, id, dueAt)
                }
                runCatching { TodoTodayWidgetProvider.refreshNow(appCtx) }
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.TODOS_ADD, true, "id=$id")
                sendEnvelope(
                    ws,
                    LinkMsgType.TODOS_ADD_RES,
                    env.reqId,
                    TodoMutateRes(
                        ok = true,
                        item = TodoItemDto(
                            id = id,
                            title = title,
                            dueAt = dueAt,
                            isDone = false,
                            repeatRule = repeat,
                            priority = priority,
                            notes = notesStr,
                            createdAt = entity.createdAt
                        )
                    )
                )
            }

            LinkMsgType.TODOS_EDIT -> {
                val req = runCatching { gson.fromJson(env.payloadJson, TodoEditReq::class.java) }.getOrNull()
                val existing = if (req != null && req.id > 0L) db.todoDao().byId(req.id) else null
                if (req == null || existing == null) {
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.TODOS_EDIT, false, "not_found")
                    sendEnvelope(ws, LinkMsgType.TODOS_EDIT_RES, env.reqId, TodoMutateRes(ok = false, errorMessage = "Todo not found"))
                    return
                }
                val title = req.title.trim().ifBlank { existing.title }
                val dueAt = if (req.dueAt > 0L) req.dueAt else existing.dueAt
                val repeat = req.repeatRule.takeIf { it == TodoEntity.REPEAT_DAILY || it == TodoEntity.REPEAT_WEEKLY || it.isEmpty() } ?: existing.repeatRule
                val priority = req.priority.coerceIn(TodoEntity.PRIORITY_LOW, TodoEntity.PRIORITY_HIGH)
                val updated = existing.copy(title = title, dueAt = dueAt, repeatRule = repeat, notes = req.notes.trim(), priority = priority)
                db.todoDao().update(updated)
                ReminderScheduler.cancelTodo(appCtx, updated.id)
                if (!updated.isDone && updated.dueAt > System.currentTimeMillis()) {
                    ReminderScheduler.scheduleTodo(appCtx, updated.id, updated.dueAt)
                }
                runCatching { TodoTodayWidgetProvider.refreshNow(appCtx) }
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.TODOS_EDIT, true, "id=${updated.id}")
                sendEnvelope(
                    ws,
                    LinkMsgType.TODOS_EDIT_RES,
                    env.reqId,
                    TodoMutateRes(
                        ok = true,
                        item = TodoItemDto(
                            id = updated.id,
                            title = updated.title,
                            dueAt = updated.dueAt,
                            isDone = updated.isDone,
                            repeatRule = updated.repeatRule,
                            priority = updated.priority,
                            notes = updated.notes,
                            createdAt = updated.createdAt,
                            completedAt = updated.completedAt ?: 0L
                        )
                    )
                )
            }

            LinkMsgType.TODOS_DELETE -> {
                val req = runCatching { gson.fromJson(env.payloadJson, TodoDeleteReq::class.java) }.getOrNull()
                if (req == null || req.id <= 0L) {
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.TODOS_DELETE, false, "invalid_id")
                    sendEnvelope(ws, LinkMsgType.TODOS_DELETE_RES, env.reqId, TodoMutateRes(ok = false, errorMessage = "Invalid todo id"))
                    return
                }
                ReminderScheduler.cancelTodo(appCtx, req.id)
                db.todoDao().deleteById(req.id)
                runCatching { TodoTodayWidgetProvider.refreshNow(appCtx) }
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.TODOS_DELETE, true, "id=${req.id}")
                sendEnvelope(ws, LinkMsgType.TODOS_DELETE_RES, env.reqId, TodoMutateRes(ok = true, deletedId = req.id))
            }

            LinkMsgType.NOTES_LIST -> {
                val req = runCatching { gson.fromJson(env.payloadJson, NotesListReq::class.java) }.getOrNull() ?: NotesListReq()
                val limit = req.limit.coerceIn(1, 100)
                val items = db.noteDao().all().take(limit).map { n ->
                    NoteItemDto(
                        id = n.id,
                        content = n.content,
                        pinned = n.pinned,
                        source = n.source,
                        relatedMessageId = n.relatedMessageId ?: 0L,
                        relatedAppLabel = n.relatedAppLabel.orEmpty(),
                        updatedAt = n.updatedAt,
                        colorHex = n.colorHex.orEmpty()
                    )
                }
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.NOTES_LIST, true, "count=${items.size}")
                sendEnvelope(ws, LinkMsgType.NOTES_LIST_RES, env.reqId, NotesListRes(items = items))
            }

            LinkMsgType.NOTE_ADD -> {
                val req = runCatching { gson.fromJson(env.payloadJson, NoteAddReq::class.java) }.getOrNull() ?: NoteAddReq()
                val content = req.content.trim()
                if (content.isBlank()) {
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.NOTE_ADD, false, "empty_content")
                    sendEnvelope(ws, LinkMsgType.NOTE_ADD_RES, env.reqId, NoteMutateRes(ok = false, errorMessage = "Empty note content"))
                    return
                }
                val relatedId = req.relatedMessageId.takeIf { it > 0L }
                val relatedMsg = relatedId?.let { db.messageDao().byId(it) }
                val existing = db.noteDao().all().firstOrNull {
                    it.content.trim() == content && it.relatedMessageId == relatedId
                }
                if (existing != null) {
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.NOTE_ADD, true, "dedup:${existing.id}")
                    sendEnvelope(
                        ws,
                        LinkMsgType.NOTE_ADD_RES,
                        env.reqId,
                        NoteMutateRes(
                            ok = true,
                            item = NoteItemDto(
                                id = existing.id,
                                content = existing.content,
                                pinned = existing.pinned,
                                source = existing.source,
                                relatedMessageId = existing.relatedMessageId ?: 0L,
                                relatedAppLabel = existing.relatedAppLabel.orEmpty(),
                                updatedAt = existing.updatedAt,
                                colorHex = existing.colorHex.orEmpty()
                            )
                        )
                    )
                    return
                }
                val now = System.currentTimeMillis()
                val colorHex = req.colorHex.trim().takeIf { HEX_COLOR_REGEX.matches(it) }
                val note = NoteEntity(
                    content = content,
                    colorHex = colorHex,
                    pinned = req.pinned,
                    source = NoteEntity.SOURCE_MANUAL,
                    relatedMessageId = relatedId,
                    relatedAppLabel = relatedMsg?.appLabel?.ifBlank { relatedMsg.packageName },
                    createdAt = now,
                    updatedAt = now
                )
                val id = db.noteDao().insert(note)
                runCatching { QuickNoteWidgetProvider.refreshNow(appCtx) }
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.NOTE_ADD, true, "id=$id")
                sendEnvelope(
                    ws,
                    LinkMsgType.NOTE_ADD_RES,
                    env.reqId,
                    NoteMutateRes(
                        ok = true,
                        item = NoteItemDto(
                            id = id,
                            content = note.content,
                            pinned = note.pinned,
                            source = note.source,
                            relatedMessageId = note.relatedMessageId ?: 0L,
                            relatedAppLabel = note.relatedAppLabel.orEmpty(),
                            updatedAt = note.updatedAt,
                            colorHex = note.colorHex.orEmpty()
                        )
                    )
                )
            }

            LinkMsgType.NOTE_EDIT -> {
                val req = runCatching { gson.fromJson(env.payloadJson, NoteEditReq::class.java) }.getOrNull()
                val existing = if (req != null && req.id > 0L) db.noteDao().byId(req.id) else null
                if (req == null || existing == null) {
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.NOTE_EDIT, false, "not_found")
                    sendEnvelope(ws, LinkMsgType.NOTE_EDIT_RES, env.reqId, NoteMutateRes(ok = false, errorMessage = "Note not found"))
                    return
                }
                val newContent = req.content.trim().ifBlank { existing.content }
                val newColor = req.colorHex.trim().takeIf { HEX_COLOR_REGEX.matches(it) }
                val updated = existing.copy(
                    content = newContent,
                    pinned = req.pinned,
                    colorHex = newColor,
                    updatedAt = System.currentTimeMillis()
                )
                db.noteDao().update(updated)
                runCatching { QuickNoteWidgetProvider.refreshNow(appCtx) }
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.NOTE_EDIT, true, "id=${updated.id}")
                sendEnvelope(
                    ws,
                    LinkMsgType.NOTE_EDIT_RES,
                    env.reqId,
                    NoteMutateRes(
                        ok = true,
                        item = NoteItemDto(
                            id = updated.id,
                            content = updated.content,
                            pinned = updated.pinned,
                            source = updated.source,
                            relatedMessageId = updated.relatedMessageId ?: 0L,
                            relatedAppLabel = updated.relatedAppLabel.orEmpty(),
                            updatedAt = updated.updatedAt,
                            colorHex = updated.colorHex.orEmpty()
                        )
                    )
                )
            }

            LinkMsgType.NOTE_DELETE -> {
                val req = runCatching { gson.fromJson(env.payloadJson, NoteDeleteReq::class.java) }.getOrNull()
                if (req == null || req.id <= 0L) {
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.NOTE_DELETE, false, "invalid_id")
                    sendEnvelope(ws, LinkMsgType.NOTE_DELETE_RES, env.reqId, NoteMutateRes(ok = false, errorMessage = "Invalid note id"))
                    return
                }
                db.noteDao().deleteById(req.id)
                runCatching { QuickNoteWidgetProvider.refreshNow(appCtx) }
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.NOTE_DELETE, true, "id=${req.id}")
                sendEnvelope(ws, LinkMsgType.NOTE_DELETE_RES, env.reqId, NoteMutateRes(ok = true, deletedId = req.id))
            }

            LinkMsgType.NOTE_PIN_TOGGLE -> {
                val req = runCatching { gson.fromJson(env.payloadJson, NotePinToggleReq::class.java) }.getOrNull()
                val existing = if (req != null && req.id > 0L) db.noteDao().byId(req.id) else null
                if (req == null || existing == null) {
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.NOTE_PIN_TOGGLE, false, "not_found")
                    sendEnvelope(ws, LinkMsgType.NOTE_PIN_TOGGLE_RES, env.reqId, NoteMutateRes(ok = false, errorMessage = "Note not found"))
                    return
                }
                val updated = existing.copy(pinned = req.pinned, updatedAt = System.currentTimeMillis())
                db.noteDao().update(updated)
                runCatching { QuickNoteWidgetProvider.refreshNow(appCtx) }
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.NOTE_PIN_TOGGLE, true, "id=${updated.id},pin=${updated.pinned}")
                sendEnvelope(
                    ws,
                    LinkMsgType.NOTE_PIN_TOGGLE_RES,
                    env.reqId,
                    NoteMutateRes(
                        ok = true,
                        item = NoteItemDto(
                            id = updated.id,
                            content = updated.content,
                            pinned = updated.pinned,
                            source = updated.source,
                            relatedMessageId = updated.relatedMessageId ?: 0L,
                            relatedAppLabel = updated.relatedAppLabel.orEmpty(),
                            updatedAt = updated.updatedAt,
                            colorHex = updated.colorHex.orEmpty()
                        )
                    )
                )
            }

            LinkMsgType.MESSAGE_DEEP_ANALYZE -> {
                val req = runCatching { gson.fromJson(env.payloadJson, DeepAnalyzeReq::class.java) }.getOrNull()
                val msg = if (req != null && req.id > 0L) db.messageDao().byId(req.id) else null
                if (req == null || msg == null) {
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.MESSAGE_DEEP_ANALYZE, false, "not_found")
                    sendEnvelope(ws, LinkMsgType.MESSAGE_DEEP_ANALYZE_RES, env.reqId, DeepAnalyzeRes(ok = false, messageId = req?.id ?: 0L, errorCode = LinkErrorCode.INVALID_REQUEST, errorMessage = "Message not found"))
                    return
                }
                if (AiClient.getProfileForPurpose(appCtx, AiPurpose.DEEP_ANALYSIS) == null) {
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.MESSAGE_DEEP_ANALYZE, false, "no_api_key")
                    sendEnvelope(ws, LinkMsgType.MESSAGE_DEEP_ANALYZE_RES, env.reqId, DeepAnalyzeRes(ok = false, messageId = msg.id, errorCode = LinkErrorCode.UNSUPPORTED, errorMessage = "AI Provider API Key not configured on phone"))
                    return
                }
                if (!PhantomPrefs.canUseAiTodayAndIncrement(appCtx)) {
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.MESSAGE_DEEP_ANALYZE, false, "quota_exceeded")
                    sendEnvelope(ws, LinkMsgType.MESSAGE_DEEP_ANALYZE_RES, env.reqId, DeepAnalyzeRes(ok = false, messageId = msg.id, errorCode = LinkErrorCode.RATE_LIMIT_EXCEEDED, errorMessage = "Daily AI quota reached"))
                    return
                }
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.MESSAGE_DEEP_ANALYZE, true, "id=${msg.id}")
                scope.launch {
                    val result = AiClient.deepAnalyzeMessage(
                        sender = msg.sender,
                        content = msg.content,
                        appLabel = msg.appLabel.ifBlank { msg.packageName },
                        context = appCtx
                    )
                    result.onSuccess { md ->
                        sendEnvelope(ws, LinkMsgType.MESSAGE_DEEP_ANALYZE_RES, env.reqId, DeepAnalyzeRes(ok = true, messageId = msg.id, markdown = md))
                    }.onFailure { err ->
                        PhantomPrefs.refundTodayAiUsage(appCtx)
                        sendEnvelope(ws, LinkMsgType.MESSAGE_DEEP_ANALYZE_RES, env.reqId, DeepAnalyzeRes(ok = false, messageId = msg.id, errorCode = LinkErrorCode.INTERNAL_ERROR, errorMessage = err.message ?: "Deep analysis failed"))
                    }
                }
            }

            LinkMsgType.MUTED_APPS_LIST -> {
                val mutedSet = PhantomPrefs.getMutedApps(appCtx)
                val recentApps = runCatching { db.messageDao().paged(200, 0) }
                    .getOrDefault(emptyList())
                    .filter { it.packageName.isNotBlank() }
                    .associate { it.packageName to it.appLabel.ifBlank { it.packageName } }
                val allPkgs = (mutedSet + recentApps.keys).sorted()
                val items = allPkgs.map { pkg ->
                    MutedAppDto(packageName = pkg, appLabel = recentApps[pkg] ?: pkg, muted = pkg in mutedSet)
                }
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.MUTED_APPS_LIST, true, "count=${items.size}")
                sendEnvelope(ws, LinkMsgType.MUTED_APPS_LIST_RES, env.reqId, MutedAppsListRes(items = items))
            }

            LinkMsgType.MUTED_APPS_TOGGLE -> {
                val req = runCatching { gson.fromJson(env.payloadJson, MutedAppToggleReq::class.java) }.getOrNull()
                if (req == null || req.packageName.isBlank()) {
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.MUTED_APPS_TOGGLE, false, "invalid_pkg")
                    sendEnvelope(ws, LinkMsgType.MUTED_APPS_TOGGLE_RES, env.reqId, MutedAppToggleRes(ok = false, errorMessage = "Invalid packageName"))
                    return
                }
                val pkg = req.packageName.trim()
                PhantomPrefs.setAppMuted(appCtx, pkg, req.muted)
                NotificationCollectorService.invalidateRuleCaches()
                PhantomSettingsBus.notifyChanged()
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.MUTED_APPS_TOGGLE, true, "$pkg=${req.muted}")
                sendEnvelope(ws, LinkMsgType.MUTED_APPS_TOGGLE_RES, env.reqId, MutedAppToggleRes(ok = true, packageName = pkg, muted = req.muted))
            }

            LinkMsgType.MESSAGES_CONVERSATION -> {
                val req = runCatching { gson.fromJson(env.payloadJson, ConversationReq::class.java) }.getOrNull() ?: ConversationReq()
                if (req.packageName.isBlank() || req.conversationKey.isBlank()) {
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.MESSAGES_CONVERSATION, false, "invalid_request")
                    sendEnvelope(ws, LinkMsgType.MESSAGES_CONVERSATION_RES, env.reqId, ConversationRes())
                    return
                }
                val limit = req.limit.coerceIn(1, 100)
                val offset = req.offset.coerceAtLeast(0)
                val raw = db.messageDao().byConversationPaged(req.packageName, req.conversationKey, limit, offset)
                val tagMap = if (raw.isNotEmpty()) {
                    runCatching {
                        db.tagDao().crossRefsForMessages(raw.map { it.id })
                            .groupBy({ it.messageId }, { it.tagId })
                    }.getOrDefault(emptyMap())
                } else emptyMap()
                // 嚴守 PRIVACY.md 區網紅線：單則訊息預覽上限 200 字，不傳全文
                val items = raw.map { m ->
                    MessageSummaryDto(
                        id = m.id,
                        packageName = m.packageName,
                        sender = m.sender,
                        preview = m.content.replace(WHITESPACE_REGEX, " ").trim().take(200),
                        timestamp = m.timestamp,
                        riskLabel = m.riskLabel,
                        isStarred = m.isStarred,
                        isRead = m.isRead,
                        conversationKey = m.conversationKey,
                        tagIds = tagMap[m.id].orEmpty(),
                        appLabel = resolveAppDisplayLabel(appCtx, m.packageName, m.appLabel),
                        isGroup = m.conversationKey.isNotBlank() && m.conversationKey != m.sender
                    )
                }
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.MESSAGES_CONVERSATION, true, "count=${items.size}")
                sendEnvelope(
                    ws,
                    LinkMsgType.MESSAGES_CONVERSATION_RES,
                    env.reqId,
                    ConversationRes(packageName = req.packageName, conversationKey = req.conversationKey, items = items, hasMore = raw.size >= limit)
                )
            }

            LinkMsgType.PHONE_STATUS_GET -> {
                val ds = collectDeviceStatus(appCtx)
                val uptime = if (connectedAtMs > 0L) (System.currentTimeMillis() - connectedAtMs).coerceAtLeast(0L) else 0L
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.PHONE_STATUS_GET, true, "bat=${ds.batteryLevel}% chg=${ds.chargeType}")
                sendEnvelope(
                    ws,
                    LinkMsgType.PHONE_STATUS_RES,
                    env.reqId,
                    PhoneStatusDto(
                        batteryPercent = ds.batteryLevel,
                        isCharging = ds.isCharging,
                        serverUptimeMs = uptime,
                        deviceName = ds.deviceName
                    )
                )
                sendEnvelope(ws, LinkMsgType.DEVICE_STATUS, "", ds)
            }

            LinkMsgType.CHAT_SESSIONS_LIST -> {
                val sessions = db.chatDao().allSessions()
                    .filter { it.title != ProactiveEngine.PROACTIVE_SESSION_TITLE }
                    .take(30)
                    .map { ChatSessionSummaryDto(id = it.id, title = it.title.ifBlank { "Chat #${it.id}" }, updatedAt = it.createdAt) }
                sendEnvelope(ws, LinkMsgType.CHAT_SESSIONS_LIST_RES, env.reqId, ChatSessionsListRes(sessions = sessions))
            }

            LinkMsgType.CHAT_SESSION_NEW -> {
                val req = runCatching { gson.fromJson(env.payloadJson, ChatSessionNewReq::class.java) }.getOrNull() ?: ChatSessionNewReq()
                val title = req.title.trim().ifBlank { "Desktop Chat" }
                val id = db.chatDao().insertSession(ChatSessionEntity(title = title.take(30)))
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.CHAT_SESSION_NEW, true, "id=$id")
                sendEnvelope(
                    ws,
                    LinkMsgType.CHAT_SESSION_NEW_RES,
                    env.reqId,
                    ChatSessionNewRes(ok = true, session = ChatSessionSummaryDto(id = id, title = title.take(30), updatedAt = System.currentTimeMillis()))
                )
            }

            LinkMsgType.CLIPBOARD_SYNC -> {
                val payload = runCatching { gson.fromJson(env.payloadJson, ClipboardSyncPayload::class.java) }.getOrNull()
                if (payload != null && PhantomPrefs.isClipboardSyncEnabled(appCtx)) {
                    val maxChars = PhantomPrefs.getClipboardMaxChars(appCtx)
                    val text = payload.text.take(maxChars)
                    if (text.isNotBlank()) {
                        lastReceivedClipHash = text.hashCode()
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            runCatching {
                                val cm = appCtx.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                cm?.setPrimaryClip(android.content.ClipData.newPlainText("PhantomCheck", text))
                            }
                        }
                        DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.CLIPBOARD_SYNC, true, "len=${text.length}")
                    }
                } else {
                    DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.CLIPBOARD_SYNC, false, "disabled_or_empty")
                }
            }

            LinkMsgType.FIND_PHONE_RING -> {
                val ok = runCatching {
                    val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
                        ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                    val ringtone = android.media.RingtoneManager.getRingtone(appCtx, uri)
                    ringtone?.play()
                    scope.launch {
                        delay(3_000L)
                        runCatching { ringtone?.stop() }
                    }
                    val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                    val notif = androidx.core.app.NotificationCompat.Builder(appCtx, ProactiveEngine.CHANNEL_ASSISTANT_BRIEFING)
                        .setSmallIcon(com.phantom.checker.R.drawable.ic_stat_phantom)
                        .setContentTitle("PhantomCheck")
                        .setContentText(com.phantom.checker.PhantomApplication.str(com.phantom.checker.R.string.notif_find_my_phone))
                        .setAutoCancel(true)
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                        .build()
                    nm?.notify(NOTIF_ID_FIND_PHONE, notif)
                    true
                }.getOrDefault(false)
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.FIND_PHONE_RING, ok, "")
                sendEnvelope(ws, LinkMsgType.FIND_PHONE_RING_RES, env.reqId, CrossEndActionRes(ok = ok, message = if (ok) "ringing" else "failed"))
            }

            LinkMsgType.SEND_PHONE_NOTIF -> {
                val req = runCatching { gson.fromJson(env.payloadJson, SendPhoneNotifReq::class.java) }.getOrNull() ?: SendPhoneNotifReq()
                val title = req.title.trim().take(120).ifBlank { "PhantomCheck Desktop" }
                val body = req.body.trim().take(400)
                val ok = if (body.isBlank()) false else runCatching {
                    val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                    val notif = androidx.core.app.NotificationCompat.Builder(appCtx, ProactiveEngine.CHANNEL_ASSISTANT_BRIEFING)
                        .setSmallIcon(com.phantom.checker.R.drawable.ic_stat_phantom)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(body))
                        .setAutoCancel(true)
                        .build()
                    nm?.notify(NOTIF_ID_SEND_PHONE, notif)
                    true
                }.getOrDefault(false)
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.SEND_PHONE_NOTIF, ok, "len=${body.length}")
                sendEnvelope(ws, LinkMsgType.SEND_PHONE_NOTIF_RES, env.reqId, CrossEndActionRes(ok = ok, message = if (ok) "sent" else "empty_or_failed"))
            }

            LinkMsgType.OPEN_URL_ON_PEER -> {
                val req = runCatching { gson.fromJson(env.payloadJson, OpenUrlReq::class.java) }.getOrNull() ?: OpenUrlReq()
                val url = req.url.trim().take(2048)
                val validScheme = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
                var openMode = "invalid_url"
                val ok = if (!validScheme) false else runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (isAppInForeground(appCtx)) {
                        appCtx.startActivity(intent)
                        openMode = "opened"
                        true
                    } else {
                        val pi = android.app.PendingIntent.getActivity(
                            appCtx,
                            NOTIF_ID_OPEN_URL,
                            intent,
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                        )
                        val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                        val notif = androidx.core.app.NotificationCompat.Builder(appCtx, ProactiveEngine.CHANNEL_ASSISTANT_BRIEFING)
                            .setSmallIcon(com.phantom.checker.R.drawable.ic_stat_phantom)
                            .setContentTitle(appCtx.getString(com.phantom.checker.R.string.link_hub_title))
                            .setContentText(url)
                            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(url))
                            .setContentIntent(pi)
                            .setAutoCancel(true)
                            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                            .build()
                        nm?.notify(NOTIF_ID_OPEN_URL, notif)
                        openMode = "notified"
                        nm != null
                    }
                }.getOrDefault(false)
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.OPEN_URL_ON_PEER, ok, if (validScheme) "http_$openMode" else "blocked_scheme")
                sendEnvelope(ws, LinkMsgType.OPEN_URL_ON_PEER_RES, env.reqId, CrossEndActionRes(ok = ok, message = if (ok) openMode else "invalid_url"))
            }

            LinkMsgType.GROUP_REPORTS_LIST -> {
                val reports = runCatching { db.groupReportDao().recentReports(30) }.getOrDefault(emptyList())
                val dtos = reports.map { r ->
                    GroupReportSummaryDto(
                        id = r.id,
                        appLabel = r.appLabel,
                        conversationTitle = r.conversationKey,
                        summaryMarkdown = r.summaryText,
                        messageCount = r.messageCount,
                        triggerType = "group_report",
                        createdAt = r.createdAt
                    )
                }
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.GROUP_REPORTS_LIST, true, "count=${dtos.size}")
                sendEnvelope(ws, LinkMsgType.GROUP_REPORTS_LIST_RES, env.reqId, GroupReportsListRes(items = dtos))
            }

            LinkMsgType.WATCH_TOPIC_MUTE -> {
                val req = runCatching { gson.fromJson(env.payloadJson, WatchTopicMuteReq::class.java) }.getOrNull()
                val ids = req?.topicIds.orEmpty().filter { it > 0L }
                var mutedCount = 0
                runCatching {
                    val dao = db.watchTopicDao()
                    ids.forEach { id ->
                        dao.updateStatus(id, com.phantom.checker.data.WatchTopicEntity.STATUS_MUTED)
                        mutedCount++
                    }
                    if (mutedCount > 0) {
                        WatchTopicEngine.syncSchedule(appCtx)
                    }
                }.onFailure { err ->
                    android.util.Log.w("DesktopLinkClient", "WATCH_TOPIC_MUTE failed: ${err.message}", err)
                }
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, LinkMsgType.WATCH_TOPIC_MUTE, true, "muted=$mutedCount")
                sendEnvelope(ws, LinkMsgType.WATCH_TOPIC_MUTE_RES, env.reqId, WatchTopicMuteRes(ok = true, mutedCount = mutedCount))
            }

            else -> {
                DesktopLinkStore.recordAuditLog(appCtx, desktopId, env.type, false, "unknown_command")
                sendEnvelope(ws, LinkMsgType.ERROR, env.reqId, ErrorPayload(LinkErrorCode.UNKNOWN_COMMAND, "Unknown command: ${env.type}"))
            }
        }
    }

    private fun isAppInForeground(appCtx: Context): Boolean = runCatching {
        val am = appCtx.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return false
        val myPid = android.os.Process.myPid()
        am.runningAppProcesses?.any { proc ->
            proc.pid == myPid &&
                proc.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        } == true
    }.getOrDefault(false)

    private fun ensureMessagePushLoop(appCtx: Context) {
        if (messagePushJob?.isActive == true) return
        messagePushJob = scope.launch {
            val db = AppDatabaseHolder.db(appCtx)
            while (activeWs != null && isSubscribedToMessages) {
                delay(if (isScreenOff) MSG_PUSH_INTERVAL_SCREEN_OFF_MS else MSG_PUSH_INTERVAL_INTERACTIVE_MS)
                val ws = activeWs ?: break
                if (!isSubscribedToMessages) break
                val newItems = runCatching { db.messageDao().afterIdCursor(lastPushedCursor, 50) }.getOrDefault(emptyList())
                if (newItems.isEmpty()) continue
                val nextCursor = newItems.last().id
                val dtos = newItems.map { m ->
                    MessageSummaryDto(
                        id = m.id,
                        packageName = m.packageName,
                        sender = m.sender,
                        preview = m.content.replace(WHITESPACE_REGEX, " ").trim().take(200),
                        timestamp = m.timestamp,
                        riskLabel = m.riskLabel,
                        isStarred = m.isStarred,
                        isRead = m.isRead,
                        conversationKey = m.conversationKey,
                        appLabel = resolveAppDisplayLabel(appCtx, m.packageName, m.appLabel),
                        isGroup = m.conversationKey.isNotBlank() && m.conversationKey != m.sender
                    )
                }
                if (sendEnvelope(ws, LinkMsgType.MESSAGE_NEW, "", MessageNewPush(items = dtos, latestCursor = nextCursor))) {
                    lastPushedCursor = nextCursor
                }
            }
        }
    }

    private val appLabelCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun resolveAppDisplayLabel(appCtx: Context, packageName: String, dbLabel: String): String {
        val cleanDb = dbLabel.trim()
        if (cleanDb.isNotEmpty() && cleanDb != packageName) return cleanDb
        if (packageName.isBlank()) return ""
        appLabelCache[packageName]?.let { return it }
        val resolved = runCatching {
            val pm = appCtx.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo)?.toString()?.trim().orEmpty()
        }.getOrDefault("").ifBlank { packageName }
        if (appLabelCache.size < 256) {
            appLabelCache[packageName] = resolved
        }
        return resolved
    }

    // ---- Android NsdManager LAN Discovery for Desktop WSS Servers (#490 E1 serialized resolve queue + #500 TTL & NetworkCallback) ----

    private const val MDNS_TTL_MS = 60_000L
    private const val MDNS_SWEEP_INTERVAL_MS = 15_000L

    private val resolveLock = Any()
    private val resolveQueue = ArrayDeque<Pair<NsdServiceInfo, Int>>()

    @Volatile
    private var isResolvingService: Boolean = false

    private var mdnsTtlSweepJob: Job? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private fun pruneExpiredDiscovered(nowMs: Long = System.currentTimeMillis()) {
        _discoveredDesktops.update { current ->
            val fresh = current.filter { nowMs - it.discoveredAtMs <= MDNS_TTL_MS }
            if (fresh.size != current.size) fresh else current
        }
    }

    @Synchronized
    private fun ensureNetworkCallbackRegistered(appCtx: Context) {
        if (networkCallback != null) return
        runCatching {
            val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    handleNetworkChanged(appCtx, "AVAILABLE")
                }

                override fun onLost(network: Network) {
                    handleNetworkChanged(appCtx, "LOST")
                }
            }
            cm.registerDefaultNetworkCallback(cb)
            connectivityManager = cm
            networkCallback = cb
        }.onFailure { err ->
            DesktopLinkStore.recordDiagnostic(appCtx, "NET", "CALLBACK_REG_ERR", err.javaClass.simpleName)
        }
    }

    @Synchronized
    private fun unregisterNetworkCallback() {
        val cm = connectivityManager
        val cb = networkCallback
        connectivityManager = null
        networkCallback = null
        if (cm != null && cb != null) {
            runCatching { cm.unregisterNetworkCallback(cb) }
        }
    }

    private fun handleNetworkChanged(appCtx: Context, event: String) {
        DesktopLinkStore.recordDiagnostic(appCtx, "NET", "NETWORK_$event", "Clearing stale mDNS cache")
        _discoveredDesktops.update { emptyList() }
        synchronized(this) {
            if (discoveryRequested && !isScreenOff) {
                pauseDiscoveryForScreenOff()
                startDiscoveryInternal(appCtx)
            }
        }
    }

    @Synchronized
    fun rescanNow(ctx: Context) {
        val appCtx = ctx.applicationContext
        _discoveredDesktops.update { emptyList() }
        discoveryRequested = true
        discoveryAppCtx = appCtx
        ensureScreenReceiverRegistered(appCtx)
        ensureNetworkCallbackRegistered(appCtx)
        pauseDiscoveryForScreenOff()
        if (!isScreenOff) {
            startDiscoveryInternal(appCtx)
        }
    }

    @Synchronized
    fun startDiscovery(ctx: Context) {
        val appCtx = ctx.applicationContext
        ensureScreenReceiverRegistered(appCtx)
        ensureNetworkCallbackRegistered(appCtx)
        discoveryRequested = true
        discoveryAppCtx = appCtx
        pruneExpiredDiscovered()
        if (isScreenOff) return
        startDiscoveryInternal(appCtx)
    }

    private fun enqueueServiceResolve(appCtx: Context, mgr: NsdManager, serviceInfo: NsdServiceInfo, attempt: Int = 0) {
        synchronized(resolveLock) {
            if (resolveQueue.size >= 16) return
            if (resolveQueue.none { it.first.serviceName == serviceInfo.serviceName }) {
                resolveQueue.addLast(serviceInfo to attempt)
            }
        }
        drainResolveQueue(appCtx, mgr)
    }

    private fun drainResolveQueue(appCtx: Context, mgr: NsdManager) {
        val next: Pair<NsdServiceInfo, Int> = synchronized(resolveLock) {
            if (isResolvingService || resolveQueue.isEmpty() || discoveryListener == null) return
            isResolvingService = true
            resolveQueue.removeFirst()
        }
        val (serviceInfo, attempt) = next
        @Suppress("DEPRECATION")
        runCatching {
            mgr.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    DesktopLinkStore.recordDiagnostic(appCtx, "mDNS", "RESOLVE_FAILED", "code=$errorCode attempt=$attempt")
                    synchronized(resolveLock) { isResolvingService = false }
                    val retryable = errorCode == NsdManager.FAILURE_ALREADY_ACTIVE || errorCode == NsdManager.FAILURE_INTERNAL_ERROR
                    if (retryable && attempt < 3 && discoveryListener != null) {
                        val backoffMs = 500L * (1L shl attempt)
                        scope.launch {
                            delay(backoffMs)
                            val curMgr = nsdManager ?: return@launch
                            if (discoveryListener != null) {
                                enqueueServiceResolve(appCtx, curMgr, info, attempt + 1)
                            }
                        }
                    } else {
                        drainResolveQueue(appCtx, mgr)
                    }
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    val host = info.host?.hostAddress
                    if (!host.isNullOrBlank()) {
                        val now = System.currentTimeMillis()
                        val port = info.port.takeIf { it in 1024..65535 } ?: LinkProtocol.DEFAULT_PORT
                        val attrs = info.attributes
                        val fn = attrs["fn"]?.toString(Charsets.UTF_8).orEmpty().ifBlank { info.serviceName }
                        val fp = attrs["fp"]?.toString(Charsets.UTF_8).orEmpty()
                        val ver = attrs["v"]?.toString(Charsets.UTF_8)?.toIntOrNull() ?: 1
                        val item = DiscoveredDesktop(
                            serviceName = info.serviceName,
                            friendlyName = fn,
                            host = host,
                            port = port,
                            fingerprintPrefix = fp,
                            protocolVersion = ver,
                            discoveredAtMs = now
                        )
                        _discoveredDesktops.update { list ->
                            list.filter { now - it.discoveredAtMs <= MDNS_TTL_MS && it.serviceName != item.serviceName } + item
                        }
                    }
                    synchronized(resolveLock) { isResolvingService = false }
                    drainResolveQueue(appCtx, mgr)
                }
            })
        }.onFailure { err ->
            android.util.Log.w("DesktopLinkClient", "NsdManager.resolveService threw: ${err.javaClass.simpleName}")
            synchronized(resolveLock) { isResolvingService = false }
            drainResolveQueue(appCtx, mgr)
        }
    }

    @Synchronized
    private fun acquireMulticastLock(appCtx: Context) {
        if (multicastLock?.isHeld == true) return
        runCatching {
            val wm = appCtx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager ?: return
            val lock = multicastLock ?: wm.createMulticastLock("phantomcheck:mdns").apply {
                setReferenceCounted(false)
                multicastLock = this
            }
            if (!lock.isHeld) lock.acquire()
        }.onFailure { err ->
            DesktopLinkStore.recordDiagnostic(appCtx, "mDNS", "MULTICAST_LOCK_WARN", err.javaClass.simpleName)
        }
    }

    @Synchronized
    private fun releaseMulticastLock() {
        val lock = multicastLock
        multicastLock = null
        if (lock != null && lock.isHeld) {
            runCatching { lock.release() }
        }
    }

    @Synchronized
    private fun startDiscoveryInternal(appCtx: Context, startRestartLoop: Boolean = true) {
        if (discoveryListener != null) return
        acquireMulticastLock(appCtx)
        runCatching {
            val mgr = appCtx.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {
                    _isScanningLan.value = true
                }
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (!serviceInfo.serviceType.contains("_phantomcheck", ignoreCase = true)) return
                    enqueueServiceResolve(appCtx, mgr, serviceInfo, attempt = 0)
                }
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    _discoveredDesktops.update { list ->
                        list.filterNot { it.serviceName == serviceInfo.serviceName }
                    }
                }
                override fun onDiscoveryStopped(serviceType: String) {
                    _isScanningLan.value = false
                }
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    _isScanningLan.value = false
                    DesktopLinkStore.recordDiagnostic(appCtx, "mDNS", "DISCOVERY_START_FAILED", "code=$errorCode")
                    stopDiscoveryListenerOnly()
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    _isScanningLan.value = false
                    DesktopLinkStore.recordDiagnostic(appCtx, "mDNS", "DISCOVERY_STOP_FAILED", "code=$errorCode")
                }
            }
            mgr.discoverServices(LinkProtocol.MDNS_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            nsdManager = mgr
            discoveryListener = listener
            _isScanningLan.value = true
            mdnsTtlSweepJob?.cancel()
            mdnsTtlSweepJob = scope.launch {
                while (discoveryListener != null) {
                    delay(MDNS_SWEEP_INTERVAL_MS)
                    pruneExpiredDiscovered()
                }
            }
            // #516 / R6 OEM 省電防卡死：每 35 秒若仍未發現任何電腦且螢幕開啟，僅停止監聽器後重啟（不取消自身 mdnsRestartJob）
            if (startRestartLoop) {
                mdnsRestartJob?.cancel()
                mdnsRestartJob = scope.launch {
                    while (discoveryRequested && !isScreenOff) {
                        delay(35_000L)
                        if (discoveryRequested && !isScreenOff && _discoveredDesktops.value.isEmpty()) {
                            DesktopLinkStore.recordDiagnostic(appCtx, "mDNS", "PERIODIC_RESTART", "refreshing listener")
                            stopDiscoveryListenerOnly()
                            delay(350L)
                            if (discoveryRequested && !isScreenOff) {
                                startDiscoveryInternal(appCtx, startRestartLoop = false)
                            }
                        }
                    }
                }
            }
        }.onFailure { err ->
            _isScanningLan.value = false
            releaseMulticastLock()
            DesktopLinkStore.recordDiagnostic(appCtx, "mDNS", "DISCOVERY_ERR", err.javaClass.simpleName)
        }
    }

    @Synchronized
    private fun stopDiscoveryListenerOnly() {
        mdnsTtlSweepJob?.cancel()
        mdnsTtlSweepJob = null
        _isScanningLan.value = false
        val mgr = nsdManager
        val listener = discoveryListener
        nsdManager = null
        discoveryListener = null
        synchronized(resolveLock) {
            resolveQueue.clear()
            isResolvingService = false
        }
        if (mgr != null && listener != null) {
            runCatching { mgr.stopServiceDiscovery(listener) }
                .onFailure { err ->
                    android.util.Log.w("DesktopLinkClient", "stopServiceDiscovery failed: ${err.javaClass.simpleName}")
                }
        }
        releaseMulticastLock()
    }

    @Synchronized
    private fun pauseDiscoveryForScreenOff() {
        mdnsRestartJob?.cancel()
        mdnsRestartJob = null
        stopDiscoveryListenerOnly()
    }

    @Synchronized
    fun stopDiscovery() {
        discoveryRequested = false
        pauseDiscoveryForScreenOff()
        unregisterNetworkCallback()
    }

    // ---- #517 Phone Device Status (event-driven ACTION_BATTERY_CHANGED, zero second-level polling) ----

    internal suspend fun collectDeviceStatus(appCtx: Context, batteryIntentOverride: Intent? = null): DeviceStatusPush {
        val batteryIntent = batteryIntentOverride
            ?: runCatching { appCtx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) ((level * 100) / scale).coerceIn(0, 100) else -1
        val statusExtra = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val charging = statusExtra == BatteryManager.BATTERY_STATUS_CHARGING ||
            statusExtra == BatteryManager.BATTERY_STATUS_FULL ||
            plugged > 0
        val chargeType = when {
            (plugged and BatteryManager.BATTERY_PLUGGED_AC) != 0 -> "ac"
            (plugged and BatteryManager.BATTERY_PLUGGED_USB) != 0 -> "usb"
            (plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0 -> "wireless"
            charging -> "ac"
            else -> "none"
        }
        val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = runCatching { cm?.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
        val netType = when {
            caps == null -> "none"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            else -> "unknown"
        }
        val availMb = runCatching {
            android.os.StatFs(appCtx.filesDir.absolutePath).availableBytes / (1024L * 1024L)
        }.getOrDefault(-1L)
        val (totalMsgs, unreadMsgs) = runCatching {
            val db = AppDatabaseHolder.db(appCtx)
            db.messageDao().todayCount(0L) to db.messageDao().unreadCount()
        }.getOrDefault(-1 to -1)
        val isDnd = runCatching {
            val nm = appCtx.getSystemService(android.app.NotificationManager::class.java)
            val sysDnd = nm != null && nm.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL
            sysDnd || PhantomPrefs.isInQuietHoursNow(appCtx)
        }.getOrDefault(false)
        val appVer = runCatching {
            appCtx.packageManager.getPackageInfo(appCtx.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")
        return DeviceStatusPush(
            batteryLevel = pct,
            isCharging = charging,
            chargeType = chargeType,
            networkType = netType,
            wifiSsid = "",
            storageAvailMb = availMb,
            totalMessages = totalMsgs,
            unreadMessages = unreadMsgs,
            isDndActive = isDnd,
            appVersion = appVer,
            deviceName = Build.MODEL.ifBlank { "Android" },
            updatedAtMs = System.currentTimeMillis()
        )
    }

    @Synchronized
    private fun startBatteryStatusMonitor(appCtx: Context, ws: WebSocket) {
        stopBatteryStatusMonitor()
        lastPushedBatteryLevel = -1
        lastPushedCharging = null
        lastPushedChargeType = ""
        // 連線/重連後立即推送一次（保證 3 秒內刷新）
        scope.launch {
            if (activeWs !== ws) return@launch
            val ds = collectDeviceStatus(appCtx)
            lastPushedBatteryLevel = ds.batteryLevel
            lastPushedCharging = ds.isCharging
            lastPushedChargeType = ds.chargeType
            sendEnvelope(ws, LinkMsgType.DEVICE_STATUS, "", ds)
        }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val curWs = activeWs ?: return
                if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val pct = if (level >= 0 && scale > 0) ((level * 100) / scale).coerceIn(0, 100) else -1
                val statusExtra = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                val charging = statusExtra == BatteryManager.BATTERY_STATUS_CHARGING ||
                    statusExtra == BatteryManager.BATTERY_STATUS_FULL ||
                    plugged > 0
                val chargeType = when {
                    (plugged and BatteryManager.BATTERY_PLUGGED_AC) != 0 -> "ac"
                    (plugged and BatteryManager.BATTERY_PLUGGED_USB) != 0 -> "usb"
                    (plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0 -> "wireless"
                    charging -> "ac"
                    else -> "none"
                }
                val levelChanged = lastPushedBatteryLevel < 0 || kotlin.math.abs(pct - lastPushedBatteryLevel) >= 2
                val chargeChanged = lastPushedCharging != charging || lastPushedChargeType != chargeType
                if (!levelChanged && !chargeChanged) return
                lastPushedBatteryLevel = pct
                lastPushedCharging = charging
                lastPushedChargeType = chargeType
                scope.launch {
                    if (activeWs !== curWs) return@launch
                    val ds = collectDeviceStatus(appCtx, batteryIntentOverride = intent)
                    sendEnvelope(curWs, LinkMsgType.DEVICE_STATUS, "", ds)
                }
            }
        }
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                appCtx,
                receiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
            batteryStatusReceiver = receiver
            batteryStatusAppCtx = appCtx
        }
    }

    @Synchronized
    private fun stopBatteryStatusMonitor() {
        val r = batteryStatusReceiver
        val c = batteryStatusAppCtx
        batteryStatusReceiver = null
        batteryStatusAppCtx = null
        if (r != null && c != null) {
            runCatching { c.unregisterReceiver(r) }
        }
    }
}
