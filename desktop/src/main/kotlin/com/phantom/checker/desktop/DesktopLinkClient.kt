package com.phantom.checker.desktop

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
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.InetAddress
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Properties
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.X509TrustManager
import kotlin.time.Duration.Companion.seconds

enum class LinkConnState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

data class DesktopLinkUiState(
    val connState: LinkConnState = LinkConnState.DISCONNECTED,
    val isPaired: Boolean = false,
    val pairedDeviceName: String = "",
    val savedWssUrl: String = "",
    val lastError: String? = null,
    val reconnectRemainingSec: Int = 0,
    val reconnectTotalSec: Int = 0
)

/**
 * #449 / #450 / #457 Desktop 端 Ktor CIO wss 客戶端：
 * - 自簽憑證 SHA-256 指紋驗證（配對後嚴格 pinning）
 * - HMAC challenge-response 重連
 * - 指數退避重連（1s..30s，含倒數秒數回報）
 */
class DesktopLinkClient {

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val configDir = File(System.getProperty("user.home"), ".phantomcheck-desktop")
    private val configFile = File(configDir, "link.properties")

    private val _state = MutableStateFlow(DesktopLinkUiState())
    val state: StateFlow<DesktopLinkUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<LinkEnvelope>(extraBufferCapacity = 64)
    val events: SharedFlow<LinkEnvelope> = _events.asSharedFlow()

    @Volatile
    private var activeWs: WebSocketSession? = null

    @Volatile
    private var connectJob: Job? = null

    @Volatile
    private var shouldKeepConnected = false

    @Volatile
    private var lastObservedCertFingerprint: String = ""

    private var instanceId: String = ""
    private var hmacSecret: String = ""
    private var savedWssUrl: String = ""
    private var savedFingerprint: String = ""
    private var savedDeviceName: String = ""

    init {
        loadConfig()
    }

    private fun loadConfig() {
        val props = Properties()
        if (configFile.exists()) {
            runCatching { configFile.inputStream().use { props.load(it) } }
        }
        instanceId = props.getProperty("instanceId").orEmpty().ifBlank {
            UUID.randomUUID().toString().also { saveProp("instanceId", it) }
        }
        hmacSecret = props.getProperty("hmacSecret").orEmpty()
        savedWssUrl = props.getProperty("wssUrl").orEmpty()
        savedFingerprint = props.getProperty("fingerprint").orEmpty()
        savedDeviceName = props.getProperty("deviceName").orEmpty()

        _state.value = DesktopLinkUiState(
            connState = LinkConnState.DISCONNECTED,
            isPaired = hmacSecret.isNotBlank() && savedWssUrl.isNotBlank(),
            pairedDeviceName = savedDeviceName,
            savedWssUrl = savedWssUrl
        )
    }

    private fun saveProp(key: String, value: String) {
        runCatching {
            if (!configDir.exists()) configDir.mkdirs()
            val props = Properties()
            if (configFile.exists()) configFile.inputStream().use { props.load(it) }
            props.setProperty(key, value)
            configFile.outputStream().use { props.store(it, "PhantomCheck Desktop Link") }
        }
    }

    private fun savePairing(wssUrl: String, fingerprint: String, secret: String, deviceName: String) {
        hmacSecret = secret
        savedWssUrl = wssUrl
        savedFingerprint = fingerprint
        savedDeviceName = deviceName
        runCatching {
            if (!configDir.exists()) configDir.mkdirs()
            val props = Properties()
            if (configFile.exists()) configFile.inputStream().use { props.load(it) }
            props.setProperty("instanceId", instanceId)
            props.setProperty("hmacSecret", secret)
            props.setProperty("wssUrl", wssUrl)
            props.setProperty("fingerprint", fingerprint)
            props.setProperty("deviceName", deviceName)
            configFile.outputStream().use { props.store(it, "PhantomCheck Desktop Link") }
        }
    }

    fun clearPairing() {
        disconnect()
        hmacSecret = ""
        savedWssUrl = ""
        savedFingerprint = ""
        savedDeviceName = ""
        runCatching {
            if (configFile.exists()) {
                val props = Properties()
                configFile.inputStream().use { props.load(it) }
                props.remove("hmacSecret")
                props.remove("wssUrl")
                props.remove("fingerprint")
                props.remove("deviceName")
                configFile.outputStream().use { props.store(it, "PhantomCheck Desktop Link") }
            }
        }
        _state.value = DesktopLinkUiState(connState = LinkConnState.DISCONNECTED, isPaired = false)
    }

    fun pairAndConnect(hostPort: String, code: String, expectedFingerprint: String = "") {
        val clean = hostPort.trim().removePrefix("wss://").removePrefix("ws://").substringBefore("/")
        val wssUrl = "wss://$clean${LinkProtocol.WS_PATH}"
        shouldKeepConnected = true
        connectJob?.cancel()
        connectJob = scope.launch {
            runSessionOnce(
                wssUrl = wssUrl,
                pairCode = code.trim(),
                expectedFingerprint = expectedFingerprint.trim()
            )
            if (shouldKeepConnected && hmacSecret.isNotBlank()) {
                reconnectLoop()
            }
        }
    }

    fun connectSaved() {
        if (hmacSecret.isBlank() || savedWssUrl.isBlank()) return
        shouldKeepConnected = true
        connectJob?.cancel()
        connectJob = scope.launch {
            reconnectLoop()
        }
    }

    fun disconnect() {
        shouldKeepConnected = false
        connectJob?.cancel()
        connectJob = null
        activeWs = null
        _state.value = _state.value.copy(
            connState = LinkConnState.DISCONNECTED,
            reconnectRemainingSec = 0,
            reconnectTotalSec = 0
        )
    }

    suspend fun sendCommand(type: String, reqId: String, payload: Any) {
        val ws = activeWs ?: return
        val env = LinkEnvelope(type = type, reqId = reqId, payloadJson = gson.toJson(payload))
        try {
            ws.send(Frame.Text(gson.toJson(env)))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // 斷線瞬間送出失敗交由重連迴圈處理，不拋出未捕捉例外造成桌面端閃退
        }
    }

    private suspend fun reconnectLoop() {
        var backoffMs = 1000L
        while (shouldKeepConnected && hmacSecret.isNotBlank() && savedWssUrl.isNotBlank()) {
            val ok = runSessionOnce(
                wssUrl = savedWssUrl,
                pairCode = null,
                expectedFingerprint = savedFingerprint
            )
            if (!shouldKeepConnected) break
            backoffMs = if (ok) 1000L else (backoffMs * 2).coerceAtMost(30_000L)
            val totalSec = (backoffMs / 1000L).toInt().coerceIn(1, 30)
            for (rem in totalSec downTo 1) {
                if (!shouldKeepConnected) break
                _state.value = _state.value.copy(
                    connState = LinkConnState.RECONNECTING,
                    reconnectRemainingSec = rem,
                    reconnectTotalSec = totalSec
                )
                delay(1000L)
            }
        }
    }

    private suspend fun runSessionOnce(
        wssUrl: String,
        pairCode: String?,
        expectedFingerprint: String
    ): Boolean {
        _state.value = _state.value.copy(
            connState = LinkConnState.CONNECTING,
            lastError = null,
            reconnectRemainingSec = 0,
            reconnectTotalSec = 0
        )
        lastObservedCertFingerprint = ""
        val client = buildPinningClient()
        var sessionSucceeded = false
        try {
            client.webSocket(urlString = wssUrl) {
                activeWs = this
                val desktopName = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("Desktop")
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val env = runCatching { gson.fromJson(frame.readText(), LinkEnvelope::class.java) }.getOrNull()
                        ?: continue

                    when (env.type) {
                        LinkMsgType.HELLO -> {
                            val hello = gson.fromJson(env.payloadJson, HelloPayload::class.java)
                            if (hello.protocolVersion != LinkProtocol.PROTOCOL_VERSION) {
                                _state.value = _state.value.copy(
                                    connState = LinkConnState.DISCONNECTED,
                                    lastError = "${LinkErrorCode.VERSION_MISMATCH}: server=${hello.protocolVersion}, desktop=${LinkProtocol.PROTOCOL_VERSION}"
                                )
                                shouldKeepConnected = false
                                return@webSocket
                            }
                            val actualFp = lastObservedCertFingerprint.ifBlank { hello.certFingerprint }
                            if (expectedFingerprint.isNotBlank() && !actualFp.startsWith(expectedFingerprint, ignoreCase = true) && !expectedFingerprint.equals(actualFp, ignoreCase = true)) {
                                _state.value = _state.value.copy(
                                    connState = LinkConnState.DISCONNECTED,
                                    lastError = "${LinkErrorCode.FINGERPRINT_MISMATCH}: TLS certificate changed"
                                )
                                shouldKeepConnected = false
                                return@webSocket
                            }
                            if (pairCode != null) {
                                sendEnvelope(
                                    this,
                                    LinkMsgType.PAIR_REQUEST,
                                    UUID.randomUUID().toString(),
                                    PairRequestPayload(
                                        instanceId = instanceId,
                                        desktopName = desktopName,
                                        code = pairCode,
                                        protocolVersion = LinkProtocol.PROTOCOL_VERSION,
                                        expectedFingerprint = actualFp
                                    )
                                )
                            } else {
                                val hmac = computeHmacSha256Hex(hmacSecret, hello.challenge)
                                sendEnvelope(
                                    this,
                                    LinkMsgType.AUTH_REQUEST,
                                    UUID.randomUUID().toString(),
                                    AuthRequestPayload(
                                        instanceId = instanceId,
                                        hmacResponse = hmac,
                                        protocolVersion = LinkProtocol.PROTOCOL_VERSION,
                                        expectedFingerprint = actualFp
                                    )
                                )
                            }
                        }

                        LinkMsgType.PAIR_RESPONSE -> {
                            val resp = gson.fromJson(env.payloadJson, PairResponsePayload::class.java)
                            if (resp.ok) {
                                savePairing(wssUrl, resp.certFingerprint, resp.hmacSecret, resp.deviceName)
                                sessionSucceeded = true
                                _state.value = _state.value.copy(
                                    connState = LinkConnState.CONNECTED,
                                    isPaired = true,
                                    pairedDeviceName = resp.deviceName,
                                    savedWssUrl = wssUrl,
                                    lastError = null
                                )
                            } else {
                                shouldKeepConnected = false
                                _state.value = _state.value.copy(
                                    connState = LinkConnState.DISCONNECTED,
                                    lastError = "${resp.errorCode}: ${resp.errorMessage}"
                                )
                                return@webSocket
                            }
                        }

                        LinkMsgType.AUTH_RESPONSE -> {
                            val resp = gson.fromJson(env.payloadJson, AuthResponsePayload::class.java)
                            if (resp.ok) {
                                sessionSucceeded = true
                                _state.value = _state.value.copy(
                                    connState = LinkConnState.CONNECTED,
                                    isPaired = true,
                                    pairedDeviceName = resp.deviceName,
                                    lastError = null
                                )
                            } else {
                                shouldKeepConnected = false
                                _state.value = _state.value.copy(
                                    connState = LinkConnState.DISCONNECTED,
                                    lastError = "${resp.errorCode}: ${resp.errorMessage}"
                                )
                                return@webSocket
                            }
                        }

                        LinkMsgType.UNPAIRED -> {
                            clearPairing()
                            return@webSocket
                        }

                        LinkMsgType.ERROR -> {
                            val err = gson.fromJson(env.payloadJson, ErrorPayload::class.java)
                            _state.value = _state.value.copy(lastError = "${err.errorCode}: ${err.errorMessage}")
                        }

                        else -> {
                            _events.emit(env)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                connState = LinkConnState.DISCONNECTED,
                lastError = e.message ?: e.javaClass.simpleName
            )
        } finally {
            activeWs = null
            runCatching { client.close() }
        }
        return sessionSucceeded
    }

    private suspend fun sendEnvelope(ws: WebSocketSession, type: String, reqId: String, payload: Any) {
        val env = LinkEnvelope(type = type, reqId = reqId, payloadJson = gson.toJson(payload))
        ws.send(Frame.Text(gson.toJson(env)))
    }

    private fun buildPinningClient(): HttpClient {
        return HttpClient(CIO) {
            install(WebSockets) {
                pingIntervalMillis = 15_000L
            }
            engine {
                https {
                    trustManager = object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                            val cert = chain?.firstOrNull() ?: return
                            val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                            lastObservedCertFingerprint = digest.joinToString("") { "%02x".format(it) }
                        }
                        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                    }
                }
            }
        }
    }

    private fun computeHmacSha256Hex(secretHex: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretHex.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
