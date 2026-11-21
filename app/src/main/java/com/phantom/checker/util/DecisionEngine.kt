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
        val hasUrlOrCrypto: Boolean = false,
        val hasAmountCue: Boolean = false,
        val hasKnownAvatar: Boolean = false
    ) {
        val hasLocalRiskSignal: Boolean
            get() = !filterRuleHit.isNullOrBlank() || hasUrlOrCrypto || hasAmountCue

        val hasLocalTrustSignal: Boolean
            get() = whitelisted || isKnownContactOrVip || hasKnownAvatar

        fun toSignalTags(): List<String> = buildList {
            if (whitelisted) add("whitelist")
            if (!filterRuleHit.isNullOrBlank()) add("rule:${filterRuleHit.take(16)}")
            if (!memoRuleAction.isNullOrBlank()) add("memo:$memoRuleAction")
            if (isKnownContactOrVip) add("known_contact")
            if (hasKnownAvatar) add("known_sender")
            if (hasUrlOrCrypto) add("url_or_crypto")
            if (hasAmountCue) add("amount")
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

        /** 低置信：僅 AI 語義且無本地風險訊號，或本地信任與 AI 語義衝突 → 降級進確認卡（不自動彈高危詐騙） */
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

        // 2. 自訂詐騙過濾規則命中：確定性高置信 scam
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

        // 4. 衝突：本地為通訊錄熟人/VIP，但 AI 判高風險 → 降級進確認卡，不自動彈詐騙警示
        if (local.isKnownContactOrVip) {
            val capped = semScore.coerceAtMost(soloCap)
            val reason = "本地訊號為通訊錄熟人／VIP，與 AI 語義衝突，改為待確認（點擊「此判斷有誤」可回饋修正）"
            val prov = formatProvenance(messageId, "${ai.riskReason}（$reason）", baseSignals + "conflict:known_vs_sem")
            return Decision.ConfirmCard(
                finalScore = capped,
                finalLabel = "warning",
                confirmReason = reason,
                provenanceReason = prov,
                signals = baseSignals + "conflict:known_vs_sem"
            )
        }

        // 5. 同向高置信：本地有確定性風險訊號（URL / 加密地址 / 金額詞）＋ AI 語義同向 → 自動執行 scam
        if (local.hasLocalRiskSignal && semScore >= threshold) {
            val prov = formatProvenance(messageId, ai.riskReason.ifBlank { "本地風險訊號與 AI 語義一致" }, baseSignals)
            return Decision.Auto(semScore, "scam", prov, baseSignals)
        }

        // 6. 僅 AI 語義且無任何本地確定性風險訊號 → 結構保證不可單獨觸發高危詐騙彈窗，上限壓在 threshold - 10 進確認卡
        val cappedScore = semScore.coerceAtMost(soloCap)
        val reason = "僅 AI 語義提醒（無本地規則／連結／金額訊號），依雙門檻降為待確認"
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

    /** 供 UI 唯讀擷取 provenance 中的訊號標記清單 */
    fun extractSignalBadges(riskReason: String): List<String> {
        if (riskReason.isBlank()) return emptyList()
        return Regex("\\[([^\\]]{1,28})]").findAll(riskReason)
            .map { it.groupValues[1] }
            .distinct()
            .take(6)
            .toList()
    }

    private val AMOUNT_CUE_REGEX = Regex(
        "(?:NT\\$|USD|\\$|新台幣|台幣|萬元|轉帳|匯款|提領|保證金|手續費|帳戶凍結|解除分期|獲利|投資群|飆股)",
        RegexOption.IGNORE_CASE
    )

    fun hasAmountOrFraudCue(content: String): Boolean = AMOUNT_CUE_REGEX.containsMatchIn(content)
}
