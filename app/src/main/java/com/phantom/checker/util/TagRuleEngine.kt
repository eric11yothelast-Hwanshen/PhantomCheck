package com.phantom.checker.util

import android.util.LruCache
import com.phantom.checker.data.UserTagRuleEntity
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * 確定性標籤規則比對引擎（零 LLM token）。
 *
 * 比對原則：
 * 1. 規則至少須具備一個非空條件，避免空條件誤傷全部訊息。
 * 2. 同規則的多個條件採 AND 語意，條件越具體越不易誤傷。
 * 3. App 條件僅比對 packageName 與 appLabel，不對內文做模糊 App 名稱比對。
 */
object TagRuleEngine {

    private const val REGEX_CACHE_MAX_SIZE = 64
    const val MAX_REGEX_PATTERN_LENGTH = 200
    const val MAX_REGEX_INPUT_LENGTH = 2000
    private const val MAX_REGEX_QUANTIFIERS = 20
    private const val MAX_REGEX_GROUP_DEPTH = 6
    private const val MAX_REGEX_MATCH_STEPS = 50_000

    private val KW_SPLIT_REGEX = Regex("[|｜,，]+")
    private val OTP_DIGITS_REGEX = Regex("(?<!\\d)\\d{4,8}(?!\\d)")

    private data class CompiledRegexEntry(
        val pattern: Pattern?,
        val error: String?
    )

    private class RegexStepLimitExceededException : RuntimeException()

    private class StepLimitedCharSequence(
        private val delegate: CharSequence,
        private val maxSteps: Int,
        private val counter: IntArray = IntArray(1)
    ) : CharSequence {
        override val length: Int get() = delegate.length

        override fun get(index: Int): Char {
            if (++counter[0] > maxSteps) throw RegexStepLimitExceededException()
            return delegate[index]
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            StepLimitedCharSequence(delegate.subSequence(startIndex, endIndex), maxSteps, counter)

        override fun toString(): String = delegate.toString()
    }

    private val compiledRegexCache = object : LruCache<String, CompiledRegexEntry>(REGEX_CACHE_MAX_SIZE) {}

    /**
     * 建置規則時檢核正則語法與 ReDoS 複雜度防呆；回傳 null 代表安全可用，否則回傳拒絕原因代碼。
     */
    fun validateRegexPattern(rawPattern: String): String? {
        val pattern = rawPattern.trim()
        if (pattern.isEmpty()) return "empty_pattern"
        if (pattern.length > MAX_REGEX_PATTERN_LENGTH) return "pattern_too_long"
        if (hasUnsafeRegexComplexity(pattern)) return "unsafe_complexity"
        return try {
            Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            null
        } catch (_: PatternSyntaxException) {
            "invalid_syntax"
        }
    }

    fun isSafeRegexPattern(rawPattern: String): Boolean = validateRegexPattern(rawPattern) == null

    /**
     * 靜態掃描正則結構，阻擋巢狀量詞（如 (a+)+$、(a*)*、(\d+){2,}）、過深群組、過多量詞與反向參照。
     */
    internal fun hasUnsafeRegexComplexity(pattern: String): Boolean {
        class GroupFrame(var hasRepeatingQuantifier: Boolean = false, var hasAlternation: Boolean = false)

        val stack = ArrayDeque<GroupFrame>()
        var inCharClass = false
        var escaped = false
        var quantifierCount = 0
        var i = 0

        fun readQuantifierRepeat(idx: Int): Pair<Int, Boolean>? {
            if (idx >= pattern.length) return null
            return when (pattern[idx]) {
                '+', '*' -> {
                    var next = idx + 1
                    if (next < pattern.length && (pattern[next] == '?' || pattern[next] == '+')) next++
                    next to true
                }
                '?' -> {
                    var next = idx + 1
                    if (next < pattern.length && (pattern[next] == '?' || pattern[next] == '+')) next++
                    next to false
                }
                '{' -> {
                    val close = pattern.indexOf('}', idx + 1)
                    if (close <= idx + 1) return null
                    val body = pattern.substring(idx + 1, close)
                    val parts = body.split(',')
                    if (parts.size > 2) return null
                    val min = parts[0].trim().toIntOrNull() ?: return null
                    val isRepeating = if (parts.size == 1) {
                        min > 1
                    } else {
                        val maxStr = parts[1].trim()
                        maxStr.isEmpty() || (maxStr.toIntOrNull()?.let { it > 1 } ?: return null)
                    }
                    var next = close + 1
                    if (next < pattern.length && (pattern[next] == '?' || pattern[next] == '+')) next++
                    next to isRepeating
                }
                else -> null
            }
        }

        while (i < pattern.length) {
            val ch = pattern[i]
            if (escaped) {
                // 反向參照（\1..\9）在回溯下易造成指數複雜度，直接阻擋
                if (!inCharClass && ch in '1'..'9') return true
                escaped = false
                i++
                continue
            }
            if (ch == '\\') {
                escaped = true
                i++
                continue
            }
            if (inCharClass) {
                if (ch == ']') inCharClass = false
                i++
                continue
            }
            when (ch) {
                '[' -> {
                    inCharClass = true
                    i++
                }
                '(' -> {
                    if (stack.size >= MAX_REGEX_GROUP_DEPTH) return true
                    stack.addLast(GroupFrame())
                    i++
                }
                '|' -> {
                    stack.lastOrNull()?.let { it.hasAlternation = true }
                    i++
                }
                ')' -> {
                    val closed = stack.removeLastOrNull()
                    val q = readQuantifierRepeat(i + 1)
                    if (q != null) {
                        quantifierCount++
                        if (quantifierCount > MAX_REGEX_QUANTIFIERS) return true
                        val (nextIdx, isRepeating) = q
                        if (isRepeating && closed != null) {
                            // 群組內含重複量詞，外層又套重複量詞（如 (a+)+、(a*)*、((a+)b)+）→ ReDoS
                            if (closed.hasRepeatingQuantifier) return true
                            // 群組含分支且外層套重複量詞（如 (a|aa)+）→ 保守視為高風險
                            if (closed.hasAlternation) return true
                        }
                        if (isRepeating) {
                            stack.lastOrNull()?.let { it.hasRepeatingQuantifier = true }
                        }
                        i = nextIdx
                    } else {
                        if (closed?.hasRepeatingQuantifier == true) {
                            stack.lastOrNull()?.let { it.hasRepeatingQuantifier = true }
                        }
                        i++
                    }
                }
                '+', '*', '?', '{' -> {
                    val q = readQuantifierRepeat(i)
                    if (q != null) {
                        quantifierCount++
                        if (quantifierCount > MAX_REGEX_QUANTIFIERS) return true
                        val (nextIdx, isRepeating) = q
                        if (isRepeating) {
                            stack.lastOrNull()?.let { it.hasRepeatingQuantifier = true }
                        }
                        i = nextIdx
                    } else {
                        i++
                    }
                }
                else -> i++
            }
        }
        return false
    }

    private fun getCompiledPattern(regexCond: String): Pattern? {
        synchronized(compiledRegexCache) {
            compiledRegexCache.get(regexCond)?.let { return it.pattern }
        }
        val err = validateRegexPattern(regexCond)
        val entry = if (err != null) {
            CompiledRegexEntry(pattern = null, error = err)
        } else {
            try {
                CompiledRegexEntry(
                    pattern = Pattern.compile(regexCond, Pattern.CASE_INSENSITIVE),
                    error = null
                )
            } catch (_: PatternSyntaxException) {
                CompiledRegexEntry(pattern = null, error = "invalid_syntax")
            }
        }
        synchronized(compiledRegexCache) {
            compiledRegexCache.put(regexCond, entry)
        }
        return entry.pattern
    }

    internal fun safeRegexContainsMatch(regexCond: String, content: String): Boolean {
        val pattern = getCompiledPattern(regexCond) ?: return false
        val bounded = if (content.length > MAX_REGEX_INPUT_LENGTH) {
            content.substring(0, MAX_REGEX_INPUT_LENGTH)
        } else {
            content
        }
        return try {
            pattern.matcher(StepLimitedCharSequence(bounded, MAX_REGEX_MATCH_STEPS)).find()
        } catch (_: RegexStepLimitExceededException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    val SEMANTIC_CATEGORIES: List<Pair<String, String>> = listOf(
        "otp" to "OTP／驗證碼",
        "system" to "系統公告／裝置管家",
        "bill" to "帳單／金融交易",
        "marketing" to "行銷／優惠促銷",
        "delivery" to "包裹／物流取貨"
    )

    fun semanticLabel(category: String?): String? {
        val norm = category?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return SEMANTIC_CATEGORIES.firstOrNull { it.first == norm }?.second ?: norm
    }

    fun normalizeSemanticCategory(raw: String?): String? {
        val v = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            v in setOf("otp", "驗證碼", "動態密碼", "一次性密碼", "verification") -> "otp"
            v in setOf("system", "系統", "系統公告", "系統通知", "手機管家", "裝置") -> "system"
            v in setOf("bill", "帳單", "繳費", "金融", "銀行", "發票") -> "bill"
            v in setOf("marketing", "行銷", "優惠", "促銷", "廣告") -> "marketing"
            v in setOf("delivery", "包裹", "物流", "取貨", "宅配") -> "delivery"
            else -> v
        }
    }

    fun matchesRule(
        rule: UserTagRuleEntity,
        packageName: String,
        appLabel: String,
        sender: String,
        content: String
    ): Boolean {
        if (!rule.isEnabled) return false

        val pkgCond = rule.appPackage?.trim()?.takeIf { it.isNotEmpty() }
        val labelCond = rule.appLabel?.trim()?.takeIf { it.isNotEmpty() }
        val senderCond = rule.senderPattern?.trim()?.takeIf { it.isNotEmpty() }
        val kwCond = rule.contentKeyword?.trim()?.takeIf { it.isNotEmpty() }
        val regexCond = rule.contentRegex?.trim()?.takeIf { it.isNotEmpty() }
        val semCond = normalizeSemanticCategory(rule.semanticCategory)

        if (pkgCond == null && labelCond == null && senderCond == null &&
            kwCond == null && regexCond == null && semCond == null
        ) {
            return false
        }

        if (pkgCond != null && !packageName.equals(pkgCond, ignoreCase = true)) {
            return false
        }

        if (labelCond != null) {
            val labelLower = appLabel.trim().lowercase()
            val pkgLower = packageName.trim().lowercase()
            val condLower = labelCond.lowercase()
            val matchedApp = labelLower == condLower ||
                (condLower.length >= 2 && labelLower.contains(condLower)) ||
                pkgLower == condLower
            if (!matchedApp) return false
        }

        if (senderCond != null && !sender.contains(senderCond, ignoreCase = true)) {
            return false
        }

        if (kwCond != null) {
            val tokens = kwCond.split(KW_SPLIT_REGEX).map { it.trim() }.filter { it.isNotEmpty() }
            val hitKw = tokens.any { tok ->
                content.contains(tok, ignoreCase = true) || sender.contains(tok, ignoreCase = true)
            }
            if (!hitKw) return false
        }

        if (regexCond != null) {
            if (!safeRegexContainsMatch(regexCond, content)) return false
        }

        if (semCond != null && !matchesSemanticCategory(semCond, appLabel, sender, content)) {
            return false
        }

        return true
    }

    fun evaluateTagRules(
        rules: List<UserTagRuleEntity>,
        packageName: String,
        appLabel: String,
        sender: String,
        content: String
    ): List<UserTagRuleEntity> {
        if (rules.isEmpty()) return emptyList()
        return rules
            .filter { matchesRule(it, packageName, appLabel, sender, content) }
            .sortedWith(compareByDescending<UserTagRuleEntity> { it.priority }.thenByDescending { it.createdAt })
    }

    fun formatRuleConditions(rule: UserTagRuleEntity): String {
        val parts = buildList {
            rule.appLabel?.trim()?.takeIf { it.isNotEmpty() }?.let { add("App=$it") }
            rule.appPackage?.trim()?.takeIf { it.isNotEmpty() }?.let { add("套件=$it") }
            rule.senderPattern?.trim()?.takeIf { it.isNotEmpty() }?.let { add("寄件者含「$it」") }
            rule.contentKeyword?.trim()?.takeIf { it.isNotEmpty() }?.let { add("關鍵字「$it」") }
            rule.contentRegex?.trim()?.takeIf { it.isNotEmpty() }?.let { add("正則 /$it/") }
            semanticLabel(rule.semanticCategory)?.let { add("語意=$it") }
        }
        return if (parts.isEmpty()) "（無條件）" else parts.joinToString(" ＋ ")
    }

    private fun matchesSemanticCategory(
        category: String,
        appLabel: String,
        sender: String,
        content: String
    ): Boolean {
        val combined = "$sender\n$content"
        return when (category) {
            "otp" -> {
                val hasOtpWord = listOf(
                    "驗證碼", "otp", "一次性密碼", "動態密碼", "安全碼", "認證碼", "verification code", "passcode"
                ).any { combined.contains(it, ignoreCase = true) }
                val hasDigits = OTP_DIGITS_REGEX.containsMatchIn(content)
                hasOtpWord || (hasDigits && (combined.contains("驗證", ignoreCase = true) || combined.contains("code", ignoreCase = true)))
            }
            "system" -> {
                val appOrText = "$appLabel\n$combined"
                listOf(
                    "系統通知", "系統更新", "手機管家", "安全更新", "儲存空間", "電池最佳化",
                    "權限管理", "系統維護", "韌體更新", "system update", "security patch"
                ).any { appOrText.contains(it, ignoreCase = true) }
            }
            "bill" -> listOf(
                "帳單", "應繳", "繳費", "扣款", "信用卡", "入帳", "轉帳", "發票", "電子發票", "月結"
            ).any { combined.contains(it, ignoreCase = true) }
            "marketing" -> listOf(
                "優惠", "折扣", "促銷", "折價券", "免運", "限時", "特價", "購物金", "滿額", "搶購"
            ).any { combined.contains(it, ignoreCase = true) }
            "delivery" -> listOf(
                "包裹", "取貨", "寄出", "配送", "物流", "宅配", "超商", "到貨", "貨運", "門市取件"
            ).any { combined.contains(it, ignoreCase = true) }
            else -> false
        }
    }
}
