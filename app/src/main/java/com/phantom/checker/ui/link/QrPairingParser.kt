package com.phantom.checker.ui.link

import com.google.gson.JsonParser
import com.phantom.checker.shared.LinkProtocol
import com.phantom.checker.shared.QrPairingPayloadV2

sealed interface QrParseResult {
    data class Valid(val payload: QrPairingPayloadV2) : QrParseResult
    data class VersionMismatch(val foundVersion: Int, val expectedVersion: Int) : QrParseResult
    data class Invalid(val reason: String) : QrParseResult
}

/**
 * #477 / #480 QR 配對內容防呆解析器（QR 屬未信任來源，任何缺欄、型別錯誤、非法 IP/埠/指紋/配對碼絕不 crash）：
 * 期望 JSON：{"v":2,"name":"<PC>","ip":"<IPv4>","port":49515,"fp":"<64hex>","code":"<6digit>"}
 */
object QrPairingParser {

    private val ipv4Regex = Regex(
        "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$"
    )
    private val fp64HexRegex = Regex("^[0-9a-fA-F]{64}$")
    private val code6DigitRegex = Regex("^\\d{6}$")

    fun parse(raw: String?): QrParseResult {
        val text = raw?.trim().orEmpty()
        if (text.isBlank() || text.length > 2048 || !text.startsWith("{")) {
            return QrParseResult.Invalid("not_json")
        }
        val obj = runCatching {
            val elem = JsonParser.parseString(text)
            if (elem.isJsonObject) elem.asJsonObject else null
        }.getOrNull() ?: return QrParseResult.Invalid("malformed_json")

        val v = runCatching { obj.get("v")?.takeIf { it.isJsonPrimitive }?.asInt }.getOrNull()
            ?: return QrParseResult.Invalid("missing_v")
        if (v != LinkProtocol.PROTOCOL_VERSION) {
            return QrParseResult.VersionMismatch(v, LinkProtocol.PROTOCOL_VERSION)
        }

        val name = runCatching { obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.trim() }.getOrNull()
            .orEmpty().take(64)
        if (name.isBlank()) return QrParseResult.Invalid("missing_name")

        val ip = runCatching { obj.get("ip")?.takeIf { it.isJsonPrimitive }?.asString?.trim() }.getOrNull().orEmpty()
        if (!ipv4Regex.matches(ip)) return QrParseResult.Invalid("invalid_ip")

        val port = runCatching { obj.get("port")?.takeIf { it.isJsonPrimitive }?.asInt }.getOrNull()
            ?: return QrParseResult.Invalid("missing_port")
        if (port !in 1024..65535) return QrParseResult.Invalid("invalid_port")

        val fp = runCatching { obj.get("fp")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.lowercase() }.getOrNull().orEmpty()
        if (!fp64HexRegex.matches(fp)) return QrParseResult.Invalid("invalid_fingerprint")

        val code = runCatching { obj.get("code")?.takeIf { it.isJsonPrimitive }?.asString?.trim() }.getOrNull().orEmpty()
        if (!code6DigitRegex.matches(code)) return QrParseResult.Invalid("invalid_code")

        return QrParseResult.Valid(
            QrPairingPayloadV2(
                v = v,
                name = name,
                ip = ip,
                port = port,
                fp = fp,
                code = code
            )
        )
    }
}
