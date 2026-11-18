package com.phantom.checker.util

import com.phantom.checker.data.MessageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class FoldedMessage(
    val representative: MessageEntity,
    val count: Int,
    val firstTs: Long,
    val lastTs: Long,
    val isFolded: Boolean
)

/**
 * AI 送出前重複通知折疊（純函式，不修改資料庫與既有 UI 清單）。
 *
 * 規則：
 * - 同 packageName + sender + conversationKey + riskLabel 才可比
 * - 任一筆含 scam / suspicious 風險標籤者不合併（高風險逐則保留）
 * - 內含疑似訂單編號／金額／OTP／電話／URL 等關鍵數字或網址序列集合不一致者不合併
 * - 正規化（\s+ 壓縮、trim）後完全相同，或 character bigram Dice 係數 ≥ 0.85
 * - 相鄰時間窗 ≤ 24 小時鏈式串接，跨窗不合併
 */
object NotificationFoldUtil {

    private const val CHAIN_WINDOW_MS = 24L * 60L * 60L * 1000L
    private const val DICE_THRESHOLD = 0.85
    private val KEY_TOKEN_REGEX = Regex("""https?://\S+|\d+(?:[.,]\d+)*""")

    private data class MutableGroup(
        val representative: MessageEntity,
        val senderKey: String,
        val convKey: String,
        val riskKey: String,
        val normalizedCombined: String,
        val keyTokens: List<String>,
        var count: Int,
        var firstTs: Long,
        var lastTs: Long,
        var lastAnchorTs: Long
    )

    fun fold(messages: List<MessageEntity>): List<FoldedMessage> {
        if (messages.isEmpty()) return emptyList()
        val groups = ArrayList<MutableGroup>(messages.size)
        for (msg in messages) {
            val riskKey = msg.riskLabel.trim().lowercase(Locale.ROOT)
            val isHighRisk = riskKey == "scam" || riskKey == "suspicious"
            val senderKey = msg.sender.trim()
            val convKey = msg.conversationKey.trim()
            val normCombined = normalize("${msg.sender} ${msg.content}")
            val tokens = extractKeyTokens(normCombined)

            if (isHighRisk) {
                groups.add(
                    MutableGroup(
                        representative = msg,
                        senderKey = senderKey,
                        convKey = convKey,
                        riskKey = riskKey,
                        normalizedCombined = normCombined,
                        keyTokens = tokens,
                        count = 1,
                        firstTs = msg.timestamp,
                        lastTs = msg.timestamp,
                        lastAnchorTs = msg.timestamp
                    )
                )
                continue
            }

            var matched: MutableGroup? = null
            for (i in groups.indices.reversed()) {
                val g = groups[i]
                if (g.riskKey == "scam" || g.riskKey == "suspicious") continue
                if (g.representative.packageName != msg.packageName) continue
                if (g.senderKey != senderKey) continue
                if (g.convKey != convKey) continue
                if (g.riskKey != riskKey) continue
                if (abs(msg.timestamp - g.lastAnchorTs) > CHAIN_WINDOW_MS) continue
                if (g.keyTokens != tokens) continue
                if (g.normalizedCombined == normCombined ||
                    diceBigramSimilarity(g.normalizedCombined, normCombined) >= DICE_THRESHOLD
                ) {
                    matched = g
                    break
                }
            }

            if (matched != null) {
                matched.count++
                if (msg.timestamp < matched.firstTs) matched.firstTs = msg.timestamp
                if (msg.timestamp > matched.lastTs) matched.lastTs = msg.timestamp
                matched.lastAnchorTs = msg.timestamp
            } else {
                groups.add(
                    MutableGroup(
                        representative = msg,
                        senderKey = senderKey,
                        convKey = convKey,
                        riskKey = riskKey,
                        normalizedCombined = normCombined,
                        keyTokens = tokens,
                        count = 1,
                        firstTs = msg.timestamp,
                        lastTs = msg.timestamp,
                        lastAnchorTs = msg.timestamp
                    )
                )
            }
        }

        return groups.map { g ->
            FoldedMessage(
                representative = g.representative,
                count = g.count,
                firstTs = g.firstTs,
                lastTs = g.lastTs,
                isFolded = g.count > 1
            )
        }
    }

    fun formatFoldedHeadline(entry: FoldedMessage): String {
        val m = entry.representative
        val appLabel = m.appLabel.ifBlank { m.packageName }
        val title = m.sender.trim().ifBlank {
            m.content.replace("\n", " ").trim().take(24)
        }
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val startStr = fmt.format(Date(entry.firstTs))
        val endStr = fmt.format(Date(entry.lastTs))
        return "[$appLabel] $title（$startStr–$endStr 共重複 ${entry.count} 次）"
    }

    internal fun normalize(text: String): String =
        text.trim().replace(Regex("\\s+"), " ")

    internal fun extractKeyTokens(normalizedText: String): List<String> =
        KEY_TOKEN_REGEX.findAll(normalizedText).map { it.value }.toList()

    internal fun diceBigramSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.length < 2 || b.length < 2) return 0.0
        val aBigrams = HashMap<String, Int>(a.length)
        for (i in 0 until a.length - 1) {
            val bg = a.substring(i, i + 2)
            aBigrams[bg] = (aBigrams[bg] ?: 0) + 1
        }
        var intersection = 0
        val bCount = b.length - 1
        for (i in 0 until bCount) {
            val bg = b.substring(i, i + 2)
            val rem = aBigrams[bg] ?: 0
            if (rem > 0) {
                intersection++
                aBigrams[bg] = rem - 1
            }
        }
        return (2.0 * intersection) / ((a.length - 1) + bCount)
    }
}
