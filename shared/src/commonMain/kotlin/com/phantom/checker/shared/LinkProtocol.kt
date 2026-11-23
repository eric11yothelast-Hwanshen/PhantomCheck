package com.phantom.checker.shared

/**
 * #449 / #450 手機端 ↔ 桌面端 區網連線協定常數與純 Kotlin DTO（零第三方依賴，雙端統一以 Gson 序列化）。
 */
object LinkProtocol {
    const val PROTOCOL_VERSION = 1
    const val DEFAULT_PORT = 49515
    const val MDNS_SERVICE_TYPE = "_phantomcheck._tcp."
    const val WS_PATH = "/link"
    const val IDLE_TIMEOUT_MS = 30L * 60L * 1000L
    const val PAIR_CODE_TTL_MS = 5L * 60L * 1000L
    const val MAX_PAIRED_CLIENTS = 2
    const val MAX_COMMANDS_PER_SEC = 5
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

data class HelloPayload(
    val protocolVersion: Int = LinkProtocol.PROTOCOL_VERSION,
    val deviceName: String = "",
    val certFingerprint: String = "",
    val challenge: String = ""
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
