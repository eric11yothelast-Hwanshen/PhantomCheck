package com.phantom.checker.util

import com.phantom.checker.data.UserTagRuleEntity

/**
 * 確定性標籤規則比對引擎（零 LLM token）。
 *
 * 比對原則：
 * 1. 規則至少須具備一個非空條件，避免空條件誤傷全部訊息。
 * 2. 同規則的多個條件採 AND 語意，條件越具體越不易誤傷。
 * 3. App 條件僅比對 packageName 與 appLabel，不對內文做模糊 App 名稱比對。
 */
object TagRuleEngine {

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
            val tokens = kwCond.split(Regex("[|｜,，]+")).map { it.trim() }.filter { it.isNotEmpty() }
            val hitKw = tokens.any { tok ->
                content.contains(tok, ignoreCase = true) || sender.contains(tok, ignoreCase = true)
            }
            if (!hitKw) return false
        }

        if (regexCond != null) {
            val hitRegex = runCatching {
                Regex(regexCond, RegexOption.IGNORE_CASE).containsMatchIn(content)
            }.getOrDefault(false)
            if (!hitRegex) return false
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
                val hasDigits = Regex("(?<!\\d)\\d{4,8}(?!\\d)").containsMatchIn(content)
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
