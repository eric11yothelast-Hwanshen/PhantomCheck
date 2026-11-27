package com.phantom.checker.util

/**
 * 強訊號 OTP 驗證碼偵測器（避免誤判金額、時間、電話或訂單編號）。
 */
object OtpDetector {
    private val STRONG_KEYWORD_REGEX = Regex(
        pattern = "(?:驗證碼|安全碼|動態碼|動態密碼|一次性密碼|認證碼|確認碼|授權碼|提款碼|取件碼|verification\\s*code|security\\s*code|auth(?:entication)?\\s*code|passcode|one[-\\s]?time\\s*password|\\botp\\b|\\bpin\\b|\\b2fa\\b)",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    private val KEYWORD_CODE_REGEX = Regex(
        pattern = "(?:驗證碼|安全碼|動態碼|動態密碼|一次性密碼|認證碼|確認碼|授權碼|提款碼|取件碼|verification\\s*code|security\\s*code|auth(?:entication)?\\s*code|passcode|one[-\\s]?time\\s*password|\\botp\\b|\\bpin\\b|\\b2fa\\b)[^0-9\\n]{0,20}?(?:G-)?(?<!\\d)(\\d{4,8})(?!\\d)",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    private val CODE_BEFORE_KEYWORD_REGEX = Regex(
        pattern = "(?<!\\d)(\\d{4,8})(?!\\d)[^0-9\\n]{0,16}?(?:為您的|為您此次|是您的)?(?:驗證碼|安全碼|動態碼|動態密碼|一次性密碼|認證碼|確認碼|\\botp\\b|verification\\s*code|security\\s*code|auth(?:entication)?\\s*code|passcode|one[-\\s]?time\\s*password|\\b2fa\\b)",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    private val BRACKET_CODE_REGEX = Regex("【(\\d{4,8})】")

    private val EXCLUDED_PREFIX_REGEX = Regex("(?:NT\\$|\\$|USD|TWD|RMB|¥|€|£)\\s*$")

    fun extractOtp(text: String): String? {
        if (text.isBlank()) return null
        if (!STRONG_KEYWORD_REGEX.containsMatchIn(text)) return null

        KEYWORD_CODE_REGEX.findAll(text).forEach { match ->
            val candidate = match.groupValues[1]
            val start = match.range.first
            val codeStart = match.groups[1]?.range?.first ?: start
            if (isValidOtpCandidate(text, candidate, codeStart)) {
                return candidate
            }
        }

        CODE_BEFORE_KEYWORD_REGEX.findAll(text).forEach { match ->
            val candidate = match.groupValues[1]
            val codeStart = match.groups[1]?.range?.first ?: match.range.first
            if (isValidOtpCandidate(text, candidate, codeStart)) {
                return candidate
            }
        }

        BRACKET_CODE_REGEX.find(text)?.let { match ->
            val candidate = match.groupValues[1]
            val codeStart = match.groups[1]?.range?.first ?: match.range.first
            if (isValidOtpCandidate(text, candidate, codeStart)) {
                return candidate
            }
        }

        return null
    }

    private fun isValidOtpCandidate(text: String, code: String, codeStart: Int): Boolean {
        if (code.length !in 4..8) return false
        val before = text.substring(0, codeStart)
        if (EXCLUDED_PREFIX_REGEX.containsMatchIn(before)) return false
        val prevChar = if (codeStart > 0) text[codeStart - 1] else ' '
        val nextIdx = codeStart + code.length
        val nextChar = if (nextIdx < text.length) text[nextIdx] else ' '
        if (prevChar == '/' || nextChar == '/' || prevChar == ':' || nextChar == ':') return false
        if (code.length == 4 && (code.startsWith("19") || code.startsWith("20")) && nextChar == '年') return false
        return true
    }
}
