package com.phantom.checker.network

/**
 * 可重用指標抽取層：
 * 1. 保留既有省 token 模式特徵判斷（hasSuspiciousFeature）行為不變。
 * 2. 抽取完整 URL 與加密貨幣地址（BTC / ETH / TRON），供防詐情報庫查詢使用（不送全文）。
 */
object IndicatorExtractor {

    enum class CryptoChain(val label: String) {
        BTC("BTC"),
        ETH("ETH"),
        TRON("TRON")
    }

    data class CryptoIndicator(
        val chain: CryptoChain,
        val address: String
    )

    data class ExtractedIndicators(
        val urls: List<String>,
        val cryptoAddresses: List<CryptoIndicator>
    ) {
        fun isEmpty(): Boolean = urls.isEmpty() && cryptoAddresses.isEmpty()

        fun isNotEmpty(): Boolean = !isEmpty()

        fun canonicalKey(): String {
            val u = urls.sorted().joinToString(",")
            val c = cryptoAddresses.map { "${it.chain.name}:${it.address}" }.sorted().joinToString(",")
            return "u=[$u]|c=[$c]"
        }

        fun summaryText(): String {
            val parts = mutableListOf<String>()
            if (urls.isNotEmpty()) parts.add("URL x${urls.size}")
            if (cryptoAddresses.isNotEmpty()) {
                parts.add(cryptoAddresses.joinToString("、") { "${it.chain.label}:${it.address.take(8)}…" })
            }
            return parts.joinToString(" / ")
        }
    }

    val SUSPICIOUS_URL_REGEX = Regex(
        "(https?://|www\\.|\\b(bit\\.ly|reurl\\.cc|tinyurl\\.com|pse\\.is|is\\.gd|t\\.co|goo\\.gl|shorturl\\.at|ppt\\.cc|risu\\.io|lihi1\\.com|lihi\\.cc|twm5g\\.co)/)",
        RegexOption.IGNORE_CASE
    )

    /**
     * #375 強可疑關鍵字（可單獨觸發省 token 模式之 AI 分析）：
     * 已移除「$」「元」「客服」「投資」「獲利」「領取」「扣款」「刷卡」「中獎」「帳號」「急件」等日常高頻單字，
     * 避免朋友玩笑、家人領取包裹、銀行扣款、購物促銷誤觸發 AI 分析。
     * #431 同步擴充 2025–2026 高頻英文詐騙話術（概念意譯，以高精準度片語為主，避免日常單字誤觸發）。
     */
    val SUSPICIOUS_KEYWORDS = listOf(
        // 中文既有詞庫（不動）
        "轉帳", "匯款", "密碼", "驗證碼", "OTP",
        "凍結", "超商代碼", "ATM", "解除分期", "投資群", "飆股", "保證金", "帳戶異常",
        // Phishing / Bank account compromise / ATO (FTC, FBI IC3 PSA251125, UK Report Fraud, APWG)
        "account suspended", "account locked", "account compromised", "account frozen",
        "account restricted", "unusual activity", "unauthorized access", "unauthorized charge",
        "fraud department", "safe account", "verify your account", "confirm your identity",
        "wire transfer",
        // OTP / Credential & wallet theft
        "verification code", "one-time code", "one-time passcode", "security code",
        "seed phrase", "recovery phrase",
        // Parcel / Delivery / Unpaid toll smishing (FTC Top Text Scams, FCC 2025, UK Report Fraud)
        "unpaid toll", "outstanding toll", "toll balance", "package could not be delivered",
        "incomplete address", "redelivery fee", "customs fee", "shipment on hold", "delivery failed",
        // Pig butchering / Romance & Investment pump-and-dump (FinCEN, Chainalysis, APWG)
        "crypto platform", "investment platform", "liquidity mining", "unlock fee",
        "withdrawal fee", "investment group", "trading group", "signal group", "vip trading",
        "stock tip", "insider tip", "guaranteed profit", "guaranteed return", "margin deposit",
        // Fake customer service refund / Gift card / Remote access (FTC, FBI IC3)
        "overpayment refund", "claim your refund", "refund department", "processing fee",
        "gift card", "apple gift card", "google play card", "western union",
        "remote access", "anydesk", "teamviewer",
        // Identity / Law enforcement / Government impersonation (FBI IC3 PSA260917 & PSA260720, HMRC)
        "arrest warrant", "social security suspended", "tax refund", "fund recovery", "legal action"
    )

    private val FULL_URL_REGEX = Regex(
        """(https?://[^\s\u4e00-\u9fff<>"'()\[\]{}]+|www\.[^\s\u4e00-\u9fff<>"'()\[\]{}]+|\b(?:bit\.ly|reurl\.cc|tinyurl\.com|pse\.is|is\.gd|t\.co|goo\.gl|shorturl\.at|ppt\.cc|risu\.io|lihi1\.com|lihi\.cc|twm5g\.co)/[^\s\u4e00-\u9fff<>"'()\[\]{}]+)""",
        RegexOption.IGNORE_CASE
    )

    private val BTC_REGEX = Regex("""\b(bc1[a-z0-9]{20,60}|[13][a-km-zA-HJ-NP-Z1-9]{25,34})\b""")
    private val ETH_REGEX = Regex("""\b0x[a-fA-F0-9]{40}\b""")
    private val TRON_REGEX = Regex("""\bT[1-9A-HJ-NP-Za-km-z]{33}\b""")

    private val TRAILING_PUNCTUATION = charArrayOf('.', ',', ')', ']', '}', '!', '?', ';', ':', '。', '，', '！', '？', '；', '：', '」', '』', '）')

    fun hasSuspiciousFeature(
        content: String,
        isRuleFiltered: Boolean,
        benignHosts: Set<String> = emptySet()
    ): Boolean {
        if (isRuleFiltered) return true
        val extracted = extract(content)
        if (extracted.cryptoAddresses.isNotEmpty()) return true
        if (extracted.urls.isNotEmpty()) {
            val hasNonBenignUrl = extracted.urls.any { url ->
                val host = extractHost(url)
                host == null || host !in benignHosts
            }
            if (hasNonBenignUrl) return true
        }
        return SUSPICIOUS_KEYWORDS.any { content.contains(it, ignoreCase = true) }
    }

    fun extractHost(url: String): String? {
        return try {
            val normalized = if (url.startsWith("http://", true) || url.startsWith("https://", true)) url else "https://$url"
            java.net.URI(normalized).host?.lowercase(java.util.Locale.ROOT)?.removePrefix("www.")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun extract(content: String): ExtractedIndicators {
        if (content.isBlank()) return ExtractedIndicators(emptyList(), emptyList())

        val urls = LinkedHashSet<String>()
        FULL_URL_REGEX.findAll(content).forEach { match ->
            var raw = match.value.trimEnd(*TRAILING_PUNCTUATION)
            if (raw.isBlank()) return@forEach
            if (!raw.startsWith("http://", ignoreCase = true) && !raw.startsWith("https://", ignoreCase = true)) {
                raw = "https://$raw"
            }
            if (raw.length >= 10) {
                urls.add(raw)
            }
        }

        val cryptos = LinkedHashMap<String, CryptoIndicator>()
        BTC_REGEX.findAll(content).forEach { match ->
            val addr = match.value
            cryptos.putIfAbsent("BTC:$addr", CryptoIndicator(CryptoChain.BTC, addr))
        }
        ETH_REGEX.findAll(content).forEach { match ->
            val addr = match.value
            cryptos.putIfAbsent("ETH:$addr", CryptoIndicator(CryptoChain.ETH, addr))
        }
        TRON_REGEX.findAll(content).forEach { match ->
            val addr = match.value
            cryptos.putIfAbsent("TRON:$addr", CryptoIndicator(CryptoChain.TRON, addr))
        }

        return ExtractedIndicators(
            urls = urls.toList(),
            cryptoAddresses = cryptos.values.toList()
        )
    }
}
