package com.phantom.checker.util

/**
 * #367 雙門檻自動決策層（純函式、零 AI token）：
 * - 確定性訊號優先（白名單、filter rule、memo rule、熟人/VIP、結構化時間/OTP/URL/金額、已知頭像）
 * - AI 語義僅作輔助加分；結構保證 AI 語義單獨最高不超過 CONFIRM_BELOW_SCORE - 10，
 *   且絕不單獨觸發高置信詐騙彈窗或不可逆動作
 * - 可逆/不可逆動作矩陣集中編列，供全碼稽核與 UI 共用
 * - 統一 provenance 格式：[signal1] [signal2] msg#id · reason
 */
object DecisionEngine {

    /** 低於此置信門檻一律進確認卡（可透過 PhantomPrefs 調整，預設 60） */
    const val DEFAULT_CONFIRM_BELOW_SCORE = 60

    /** 結構保證：無本地確定性風險訊號時，AI 語義單獨給分上限 = threshold - 10 */
    fun aiSemanticSoloCap(confirmThreshold: Int = DEFAULT_CONFIRM_BELOW_SCORE): Int =
        (confirmThreshold - 10).coerceAtLeast(30)

    enum class ActionType(val reversible: Boolean, val label: String) {
        // 可逆（自動執行）
        TAG_APPLY(true, "分類／標籤套用"),
        ARCHIVE(true, "歸檔"),
        SUMMARY(true, "摘要彙整"),
        SCHEDULE_SUGGESTION_CARD(true, "排程建議卡"),
        MEMORY_AUTO_EXTRACT_NORMAL(true, "非敏感自動記憶擷取"),

        // 不可逆（程式層必確認）
        DELETE_DATA(false, "刪除任何資料"),
        EXTERNAL_SEND_OR_EXPORT(false, "對外發送／回覆／匯出分享"),
        CALENDAR_WRITE(false, "寫入本機行事曆"),
        AI_TOKEN_DEEP_OR_SEARCH(false, "動用 AI 深度分析／外部搜尋"),
        RULE_MUTATION(false, "新增／修改／刪除規則庫")
    }

    val REVERSIBLE_ACTIONS: Set<ActionType> = ActionType.values().filter { it.reversible }.toSet()
    val IRREVERSIBLE_ACTIONS: Set<ActionType> = ActionType.values().filter { !it.reversible }.toSet()

    fun requiresConfirmation(action: ActionType): Boolean = !action.reversible

    data class DeterministicSignals(
        val whitelisted: Boolean = false,
        val filterRuleHit: String? = null,
        val memoRuleAction: String? = null,
        val isKnownContactOrVip: Boolean = false,
        val hasStructuredTime: Boolean = false,
        val hasOtp: Boolean = false,
        /** #375 弱訊號：僅含 URL，不可單獨觸發規則 5 自動 scam */
        val urlPresent: Boolean = false,
        /** #375 強訊號：含加密貨幣錢包地址 */
        val cryptoAddressPresent: Boolean = false,
        /** #375 強訊號：IntelClient 命中 Safe Browsing / PhishTank / fraud.tw / Chainabuse 任一 */
        val intelHit: Boolean = false,
        /** 相容欄位：若外部仍傳入 hasUrlOrCrypto，僅視為 urlPresent 弱訊號（不升級為 intelHit） */
        val hasUrlOrCrypto: Boolean = false,
        val hasAmountCue: Boolean = false,
        val hasKnownAvatar: Boolean = false,
        /** #375 發送者 30 天信譽（≥10 筆低風險或有頭像或誤判回饋提升） */
        val trustedSenderHistory: Boolean = false
    ) {
        val hasStrongRiskSignal: Boolean
            get() = !filterRuleHit.isNullOrBlank() || intelHit || cryptoAddressPresent || hasAmountCue

        val hasLocalRiskSignal: Boolean
            get() = hasStrongRiskSignal

        val hasLocalTrustSignal: Boolean
            get() = whitelisted || isKnownContactOrVip || trustedSenderHistory || hasKnownAvatar

        fun toSignalTags(): List<String> = buildList {
            if (whitelisted) add("whitelist")
            if (!filterRuleHit.isNullOrBlank()) add("rule:${filterRuleHit.take(16)}")
            if (!memoRuleAction.isNullOrBlank()) add("memo:$memoRuleAction")
            if (isKnownContactOrVip) add("known_contact")
            if (trustedSenderHistory) add("trusted_sender")
            if (hasKnownAvatar) add("known_sender")
            if (intelHit) add("intel_hit")
            if (cryptoAddressPresent) add("crypto_addr")
            if (urlPresent || hasUrlOrCrypto) add("url_present")
            if (hasAmountCue) add("fraud_cue")
            if (hasOtp) add("otp")
            if (hasStructuredTime) add("time_parsed")
        }
    }

    data class AiSemanticSignal(
        val riskScore: Int,
        val riskLabel: String,
        val riskSemanticScore: Int? = null,
        val riskReason: String
    )

    sealed class Decision {
        abstract val finalScore: Int
        abstract val finalLabel: String
        abstract val provenanceReason: String
        abstract val signals: List<String>

        /** 高置信：確定性訊號命中，或確定性訊號與 AI 語義同向 → 可自動執行 */
        data class Auto(
            override val finalScore: Int,
            override val finalLabel: String,
            override val provenanceReason: String,
            override val signals: List<String>
        ) : Decision()

        /** 低置信：僅 AI 語義且無本地強風險訊號，或本地信任與 AI 語義衝突 → 降級進確認卡（不自動彈高危詐騙） */
        data class ConfirmCard(
            override val finalScore: Int,
            override val finalLabel: String,
            val confirmReason: String,
            override val provenanceReason: String,
            override val signals: List<String>
        ) : Decision()
    }

    fun evaluateRisk(
        messageId: Long?,
        local: DeterministicSignals,
        ai: AiSemanticSignal,
        confirmThreshold: Int = DEFAULT_CONFIRM_BELOW_SCORE
    ): Decision {
        val threshold = confirmThreshold.coerceIn(40, 85)
        val soloCap = aiSemanticSoloCap(threshold)
        val semScore = (ai.riskSemanticScore ?: ai.riskScore).coerceIn(0, 100)
        val baseSignals = local.toSignalTags().toMutableList()
        baseSignals.add("sem:$semScore")

        // 1. 白名單最高優先：一律 safe（不可回退鐵律）
        if (local.whitelisted) {
            val prov = formatProvenance(messageId, "命中白名單規則", baseSignals)
            return Decision.Auto(10, "safe", prov, baseSignals)
        }

        // 2. 自訂詐騙過濾規則命中：確定性高置信 scam（不可回退鐵律）
        if (!local.filterRuleHit.isNullOrBlank()) {
            val score = maxOf(85, semScore)
            val prov = formatProvenance(messageId, "命中自訂詐騙過濾規則：${local.filterRuleHit}", baseSignals)
            return Decision.Auto(score, "scam", prov, baseSignals)
        }

        // 3. 安全判定（低風險）：直接自動放行
        val isAiRisky = ai.riskLabel == "scam" || semScore >= threshold
        if (!isAiRisky) {
            val prov = formatProvenance(messageId, ai.riskReason.ifBlank { "未見明顯風險" }, baseSignals)
            return Decision.Auto(semScore, ai.riskLabel.ifBlank { "safe" }, prov, baseSignals)
        }

        // 4. 衝突降級：通訊錄熟人/VIP 或發送者 30 天信任信譽，與 AI 語義衝突 → 降級進確認卡
        //    （但信譽不得壓過 intelHit 情報命中）
        if ((local.isKnownContactOrVip || local.trustedSenderHistory) && !local.intelHit) {
            val capped = semScore.coerceAtMost(soloCap)
            val trustLabel = if (local.isKnownContactOrVip) "通訊錄熟人／VIP" else "歷史高信任發送者"
            val reason = "本地訊號為$trustLabel，與 AI 語義衝突，改為待確認（點擊「此判斷有誤」可回饋修正）"
            val conflictTag = if (local.isKnownContactOrVip) "conflict:known_vs_sem" else "conflict:reputation_vs_sem"
            val prov = formatProvenance(messageId, "${ai.riskReason}（$reason）", baseSignals + conflictTag)
            return Decision.ConfirmCard(
                finalScore = capped,
                finalLabel = "warning",
                confirmReason = reason,
                provenanceReason = prov,
                signals = baseSignals + conflictTag
            )
        }

        // 5. 同向高置信：僅「intelHit / 加密貨幣地址 / 明確詐騙操作訊號」＋ AI 語義同向 → 自動執行 scam
        //    僅 urlPresent（無 intelHit）一律不進規則 5，落入規則 6 確認卡
        if (local.hasStrongRiskSignal && semScore >= threshold) {
            val prov = formatProvenance(messageId, ai.riskReason.ifBlank { "本地強風險訊號與 AI 語義一致" }, baseSignals)
            return Decision.Auto(semScore, "scam", prov, baseSignals)
        }

        // 6. 僅 AI 語義或僅弱訊號（urlPresent）→ 結構保證不可單獨觸發高危詐騙彈窗，上限壓在 threshold - 10 進確認卡
        val cappedScore = semScore.coerceAtMost(soloCap)
        val reason = if (local.urlPresent || local.hasUrlOrCrypto) {
            "含一般連結但未命中防詐情報庫，依雙門檻降為待確認（不自動標記詐騙）"
        } else {
            "僅 AI 語義提醒（無本地規則／情報命中／強風險訊號），依雙門檻降為待確認"
        }
        val prov = formatProvenance(messageId, "${ai.riskReason}（$reason）", baseSignals + "sem_only_capped")
        return Decision.ConfirmCard(
            finalScore = cappedScore,
            finalLabel = "warning",
            confirmReason = reason,
            provenanceReason = prov,
            signals = baseSignals + "sem_only_capped"
        )
    }

    /** 統一 provenance 格式：[sig1] [sig2] msg#id · reason */
    fun formatProvenance(
        messageId: Long?,
        reason: String,
        signals: List<String>
    ): String {
        val tagPart = signals.distinct().take(6).joinToString(" ") { "[$it]" }
        val msgPart = messageId?.let { "msg#$it" }.orEmpty()
        val head = listOf(tagPart, msgPart).filter { it.isNotBlank() }.joinToString(" ")
        val cleanReason = reason.trim()
        return if (head.isNotEmpty() && cleanReason.isNotEmpty()) {
            "$head · $cleanReason"
        } else {
            cleanReason.ifEmpty { head }
        }
    }

    private val SIGNAL_BADGE_REGEX = Regex("\\[([^\\]]{1,28})]")

    /** 供 UI 唯讀擷取 provenance 中的訊號標記清單 */
    fun extractSignalBadges(riskReason: String): List<String> {
        if (riskReason.isBlank()) return emptyList()
        return SIGNAL_BADGE_REGEX.findAll(riskReason)
            .map { it.groupValues[1] }
            .distinct()
            .take(6)
            .toList()
    }

    /**
     * #375 收斂高風險詐騙操作訊號：移除裸「$」「元」「NT$」「台幣」「獲利」等日常詞，
     * 保留具體詐騙操作（ATM／解除分期／投資群／飆股／保證金／帳戶凍結／匯款轉帳催促／中獎+連結／客服索取驗證碼）。
     */
    private val AMOUNT_CUE_REGEX = Regex(
        "(?:解除分期|操作\\s*ATM|ATM\\s*操作|帳戶凍結|保證金|手續費|投資群|飆股|帶進帶出|老師帶單|急需轉帳|馬上匯款|立即匯款|儘速匯款|匯款.{0,8}帳號|轉帳.{0,8}帳號|(?:提供|告知|給我|回傳).{0,6}(?:驗證碼|OTP)|(?:中獎|得獎).{0,24}(?:https?://|www\\.|bit\\.ly|reurl\\.cc|tinyurl\\.com|點擊|領獎))",
        RegexOption.IGNORE_CASE
    )

    fun hasAmountOrFraudCue(content: String): Boolean = AMOUNT_CUE_REGEX.containsMatchIn(content)
}
