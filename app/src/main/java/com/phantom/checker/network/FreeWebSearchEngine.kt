package com.phantom.checker.network

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private val CONTROL_WS_REGEX = Regex("[\\r\\n\\t]+")
private val MULTI_SPACE_REGEX = Regex("\\s{2,}")


data class FreeSearchSnippet(
    val title: String,
    val snippet: String,
    val url: String,
    val source: String
)

/**
 * #447 / #455 零金鑰免費搜尋引擎後端（純 OkHttp + Gson，無新增第三方依賴）：
 * 1) DuckDuckGo Instant Answer API（優先，輕量官方 JSON）
 * 2) DuckDuckGo HTML 端點（IA 不足時最多打 1 次；202/429/限速頁視為暫不可用並進入 5 分鐘負快取）
 * 3) Wikipedia OpenSearch / Query API（依語系 zh/en 併行嘗試）
 * 4) #455 品質治理（引擎層，供聊天智慧聯網與主動情報 v2 共用）：
 *    - 剝除 script/style/noscript/meta refresh、HTML 實體與 percent-encoding 解碼
 *    - 可讀字元（CJK+英數+標點）占比 <60% 丟棄；長度 <40 或 >600 字元丟棄
 *    - DOMAIN_QUALITY 網域分層（官方/政府/教育/百科優先、內容農場與低信譽降權/丟棄）＋同網域去重，取前 4–6 筆
 */
object FreeWebSearchEngine {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    private const val NEGATIVE_CACHE_TTL_MS = 5 * 60_000L
    private const val MAX_RETRY_ON_IO = 1 // 總計至多 2 次（初次 + 1 次 backoff 重試）
    private const val MIN_SNIPPET_LEN = 40
    private const val MAX_SNIPPET_LEN = 600
    private const val MIN_READABLE_RATIO = 0.60
    private const val BLOCKED_DOMAIN_THRESHOLD = -100

    /**
     * #455 網域品質分層表：
     * - >= +60：官方 / 政府 / 教育 / 權威百科與公信力媒體
     * - -20..-50：低信譽或影音社群平台（降權）
     * - <= -100：內容農場（直接丟棄）
     */
    internal val DOMAIN_QUALITY: Map<String, Int> = mapOf(
        // 官方 / 政府 / 教育 / 權威來源
        "cwa.gov.tw" to 100,
        "cdc.gov.tw" to 100,
        "moe.gov.tw" to 100,
        "ey.gov.tw" to 100,
        "president.gov.tw" to 100,
        "gov.tw" to 95,
        "edu.tw" to 90,
        "who.int" to 95,
        "nasa.gov" to 95,
        "nih.gov" to 95,
        "zh.wikipedia.org" to 85,
        "en.wikipedia.org" to 85,
        "wikipedia.org" to 85,
        "cna.com.tw" to 75,
        "pts.org.tw" to 75,
        "reuters.com" to 75,
        "apnews.com" to 75,
        "bbc.com" to 70,
        "bbc.co.uk" to 70,
        "developer.android.com" to 80,
        "kotlinlang.org" to 80,
        "github.com" to 65,
        // 社群 / 影音（降權）
        "youtube.com" to -30,
        "youtu.be" to -30,
        "tiktok.com" to -40,
        "pinterest.com" to -40,
        "instagram.com" to -35,
        "facebook.com" to -35,
        "x.com" to -25,
        "twitter.com" to -25,
        "quora.com" to -25,
        // 內容農場（直接丟棄）
        "teepr.com" to -100,
        "kknews.cc" to -100,
        "read01.com" to -100,
        "bomb01.com" to -100,
        "dailyview.tw" to -100,
        "coco01.net" to -100,
        "buzzhand.com" to -100,
        "gigacircle.com" to -100,
        "ptt01.cc" to -100,
        "life.tw" to -100,
        "twgreatdaily.com" to -100,
        "ezp9.com" to -100,
        "toutiao.com" to -100,
        "baijiahao.baidu.com" to -100,
        "xuehua.us" to -100,
        "inf.news" to -100,
        "min.news" to -100,
        "daydaynews.cc" to -100,
        "kuaibao.qq.com" to -100
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val ddgHtmlCooldownUntilMs = java.util.concurrent.atomic.AtomicLong(0L)
    private val negativeQueryCache = ConcurrentHashMap<String, Long>()

    private val SCRIPT_STYLE_META_REGEX = Regex(
        """<(script|style|noscript)\b[^>]*>.*?</\1>|<meta\b[^>]*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val HTML_TAG_REGEX = Regex("<[^>]+>")
    private val PERCENT_SEQ_REGEX = Regex("(?:%[0-9A-Fa-f]{2})+")
    private val HEX_ENTITY_REGEX = Regex("&#x([0-9A-Fa-f]+);")
    private val DEC_ENTITY_REGEX = Regex("&#(\\d+);")

    suspend fun search(
        query: String,
        preferZh: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): List<FreeSearchSnippet> = withContext(Dispatchers.IO) {
        val clean = query.trim().take(200)
        if (clean.isBlank()) return@withContext emptyList()

        val cacheKey = "${if (preferZh) "zh" else "en"}:${clean.lowercase()}"
        val cachedNegAt = negativeQueryCache[cacheKey]
        if (cachedNegAt != null && (nowMs - cachedNegAt) < NEGATIVE_CACHE_TTL_MS) {
            return@withContext emptyList()
        }

        val rawCandidates = mutableListOf<FreeSearchSnippet>()

        // 1. DuckDuckGo Instant Answer API（至多 1 次查詢，含 IO 指數 backoff）
        rawCandidates.addAll(fetchDdgInstantAnswer(clean))

        // 2. 品質候選不足 2 筆再嘗試 DuckDuckGo HTML 端點（若未處於 5 分鐘限速冷卻）
        if (rankAndFilterSnippets(rawCandidates).size < 2 && nowMs >= ddgHtmlCooldownUntilMs.get()) {
            rawCandidates.addAll(fetchDdgHtml(clean, nowMs))
        }

        // 3. 品質候選仍不足 4 筆時併行查詢 Wikipedia（zh / en 依語系排序）
        if (rankAndFilterSnippets(rawCandidates).size < 4) {
            rawCandidates.addAll(fetchWikipediaParallel(clean, preferZh))
        }

        val result = rankAndFilterSnippets(rawCandidates)
        if (result.isEmpty()) {
            if (negativeQueryCache.size > 64) {
                negativeQueryCache.clear()
            }
            negativeQueryCache[cacheKey] = nowMs
        }
        result
    }

    /** #455 萃取乾淨顯示網域（不含 www. / path / query），供來源清單呈現與同網域去重 */
    fun extractDisplayDomain(url: String): String {
        val trimmed = url.trim()
        val host = runCatching { URI(trimmed).host }.getOrNull()
            ?.lowercase()
            ?.removePrefix("www.")
            ?.removePrefix("m.")
            ?.takeIf { it.isNotBlank() }
        if (host != null) return host
        return trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .substringBefore('?')
            .lowercase()
            .removePrefix("www.")
            .removePrefix("m.")
    }

    internal fun domainQualityScore(domain: String): Int {
        val d = domain.lowercase().trim()
        if (d.isBlank()) return -50
        DOMAIN_QUALITY[d]?.let { return it }
        // 檢查父網域精準命中（例如 news.cwa.gov.tw -> cwa.gov.tw）
        for ((key, score) in DOMAIN_QUALITY) {
            if (d.endsWith(".$key")) return score
        }
        // 通用政府與教育 TLD 加權
        if (d.endsWith(".gov") || d.endsWith(".gov.tw") || d.endsWith(".go.jp") || d.endsWith(".gov.uk")) {
            return 95
        }
        if (d.endsWith(".edu") || d.endsWith(".edu.tw") || d.endsWith(".ac.uk") || d.endsWith(".ac.jp")) {
            return 90
        }
        return 0
    }

    internal fun sanitizeSnippetText(raw: String): String {
        val noScripts = SCRIPT_STYLE_META_REGEX.replace(raw, " ")
        val noTags = HTML_TAG_REGEX.replace(noScripts, " ")
        val decodedEntities = decodeHtmlEntities(noTags)
        val decodedPercent = PERCENT_SEQ_REGEX.replace(decodedEntities) { m ->
            runCatching { URLDecoder.decode(m.value, "UTF-8") }.getOrDefault(m.value)
        }
        return decodedPercent
            .replace(CONTROL_WS_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }

    internal fun readableCharRatio(text: String): Double {
        if (text.isEmpty()) return 0.0
        var readable = 0
        for (ch in text) {
            if (isReadableChar(ch)) readable++
        }
        return readable.toDouble() / text.length.toDouble()
    }

    private fun isReadableChar(ch: Char): Boolean {
        val code = ch.code
        return ch.isWhitespace() ||
            ch in 'a'..'z' ||
            ch in 'A'..'Z' ||
            ch in '0'..'9' ||
            code in 0x4E00..0x9FFF || // CJK Unified Ideographs
            code in 0x3400..0x4DBF || // CJK Ext A
            code in 0x3000..0x303F || // CJK Punctuation
            code in 0xFF00..0xFFEF || // Fullwidth forms
            ch in ".,!?;:'\"()[]{}-_/&@#%+=~·•—–…°℃℉$￥€£"
    }

    internal fun rankAndFilterSnippets(candidates: List<FreeSearchSnippet>): List<FreeSearchSnippet> {
        data class Scored(val snippet: FreeSearchSnippet, val domain: String, val score: Int, val order: Int)

        val scored = mutableListOf<Scored>()
        candidates.forEachIndexed { index, item ->
            val normUrl = item.url.trim()
            if (!normUrl.startsWith("http://", ignoreCase = true) &&
                !normUrl.startsWith("https://", ignoreCase = true)
            ) {
                return@forEachIndexed
            }
            val domain = extractDisplayDomain(normUrl)
            val score = domainQualityScore(domain)
            if (score <= BLOCKED_DOMAIN_THRESHOLD) return@forEachIndexed

            val cleanTitle = sanitizeSnippetText(item.title).take(120)
            val cleanSnippet = sanitizeSnippetText(item.snippet)
            if (cleanSnippet.length !in MIN_SNIPPET_LEN..MAX_SNIPPET_LEN) return@forEachIndexed
            if (readableCharRatio(cleanSnippet) < MIN_READABLE_RATIO) return@forEachIndexed

            scored.add(
                Scored(
                    snippet = item.copy(
                        title = cleanTitle.ifBlank { domain },
                        snippet = cleanSnippet,
                        url = normUrl
                    ),
                    domain = domain,
                    score = score,
                    order = index
                )
            )
        }

        // 先依品質分數降冪、再依原始順序，最後同網域去重，取前 6 筆
        val sorted = scored.sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.order })
        val seenDomains = HashSet<String>()
        val result = mutableListOf<FreeSearchSnippet>()
        for (entry in sorted) {
            if (result.size >= 6) break
            if (seenDomains.add(entry.domain)) {
                result.add(entry.snippet)
            }
        }
        return result
    }

    private suspend fun fetchDdgInstantAnswer(query: String): List<FreeSearchSnippet> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        val body = executeWithLightRetry(req) ?: return emptyList()
        return parseDdgInstantAnswerJson(body)
    }

    internal fun parseDdgInstantAnswerJson(rawJson: String): List<FreeSearchSnippet> {
        val out = mutableListOf<FreeSearchSnippet>()
        val root = runCatching { JsonParser.parseString(rawJson).asJsonObject }.getOrNull() ?: return out
        val abstractText = root.get("AbstractText")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
        val abstractUrl = root.get("AbstractURL")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
        val heading = root.get("Heading")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
        if (abstractText.isNotBlank() && abstractUrl.startsWith("http")) {
            out.add(
                FreeSearchSnippet(
                    title = heading.ifBlank { "DuckDuckGo" },
                    snippet = sanitizeSnippetText(abstractText).take(560),
                    url = abstractUrl,
                    source = "ddg_ia"
                )
            )
        }
        fun collectTopics(arr: com.google.gson.JsonArray?) {
            if (arr == null) return
            for (el in arr) {
                if (out.size >= 6) break
                val obj = runCatching { el.asJsonObject }.getOrNull() ?: continue
                val nested = runCatching { obj.getAsJsonArray("Topics") }.getOrNull()
                if (nested != null) {
                    collectTopics(nested)
                    continue
                }
                val text = obj.get("Text")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
                val firstUrl = obj.get("FirstURL")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
                if (text.isNotBlank() && firstUrl.startsWith("http")) {
                    out.add(
                        FreeSearchSnippet(
                            title = text.substringBefore(" - ").take(80).ifBlank { "DuckDuckGo" },
                            snippet = sanitizeSnippetText(text).take(560),
                            url = firstUrl,
                            source = "ddg_ia"
                        )
                    )
                }
            }
        }
        collectTopics(runCatching { root.getAsJsonArray("RelatedTopics") }.getOrNull())
        return out
    }

    private suspend fun fetchDdgHtml(query: String, nowMs: Long): List<FreeSearchSnippet> {
        val formBody = FormBody.Builder()
            .add("q", query)
            .build()
        val req = Request.Builder()
            .url("https://html.duckduckgo.com/html/")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .post(formBody)
            .build()
        val respBody = executeWithLightRetry(req, onRateLimited = {
            ddgHtmlCooldownUntilMs.set(nowMs + NEGATIVE_CACHE_TTL_MS)
        }) ?: return emptyList()

        if (looksLikeRateLimitPage(respBody)) {
            ddgHtmlCooldownUntilMs.set(nowMs + NEGATIVE_CACHE_TTL_MS)
            return emptyList()
        }
        return parseDdgHtmlResults(respBody)
    }

    internal fun looksLikeRateLimitPage(html: String): Boolean {
        val lower = html.lowercase()
        return lower.contains("anomaly-modal") ||
            lower.contains("bots use duckduckgo") ||
            lower.contains("please complete the following challenge") ||
            (lower.contains("captcha") && !lower.contains("result__a"))
    }

    private val DDG_LINK_REGEX = Regex(
        """<a[^>]*class=["'][^"']*result__a[^"']*["'][^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val DDG_SNIPPET_REGEX = Regex(
        """<(?:a|div|span)[^>]*class=["'][^"']*result__snippet[^"']*["'][^>]*>(.*?)</(?:a|div|span)>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    internal fun parseDdgHtmlResults(html: String): List<FreeSearchSnippet> {
        val cleanedHtml = SCRIPT_STYLE_META_REGEX.replace(html, " ")
        val out = mutableListOf<FreeSearchSnippet>()
        val links = DDG_LINK_REGEX.findAll(cleanedHtml).toList()
        val snippets = DDG_SNIPPET_REGEX.findAll(cleanedHtml).toList()
        for (i in links.indices) {
            if (out.size >= 10) break
            val rawHref = decodeHtmlEntities(links[i].groupValues[1].trim())
            val resolvedUrl = unwrapDdgRedirect(rawHref) ?: continue
            val title = sanitizeSnippetText(links[i].groupValues[2]).take(120)
            val snippet = if (i < snippets.size) sanitizeSnippetText(snippets[i].groupValues[1]).take(560) else ""
            if (title.isNotBlank() && snippet.isNotBlank()) {
                out.add(
                    FreeSearchSnippet(
                        title = title,
                        snippet = snippet,
                        url = resolvedUrl,
                        source = "ddg_html"
                    )
                )
            }
        }
        return out
    }

    private fun unwrapDdgRedirect(href: String): String? {
        val normalized = when {
            href.startsWith("//") -> "https:$href"
            else -> href
        }
        if (normalized.contains("duckduckgo.com/l/", ignoreCase = true)) {
            val uddgIdx = normalized.indexOf("uddg=")
            if (uddgIdx >= 0) {
                val encoded = normalized.substring(uddgIdx + 5).substringBefore('&')
                val decoded = runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull()
                if (!decoded.isNullOrBlank() && decoded.startsWith("http")) return decoded
            }
        }
        return normalized.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
    }

    private suspend fun fetchWikipediaParallel(query: String, preferZh: Boolean): List<FreeSearchSnippet> =
        coroutineScope {
            val langs = if (preferZh) listOf("zh", "en") else listOf("en", "zh")
            val jobs = langs.map { lang ->
                async { fetchWikipediaForLang(query, lang) }
            }
            jobs.flatMap { it.await() }.take(4)
        }

    private suspend fun fetchWikipediaForLang(query: String, lang: String): List<FreeSearchSnippet> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://$lang.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&srlimit=2&utf8=1&format=json"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        val body = executeWithLightRetry(req) ?: return emptyList()
        return parseWikipediaSearchJson(body, lang)
    }

    internal fun parseWikipediaSearchJson(rawJson: String, lang: String): List<FreeSearchSnippet> {
        val out = mutableListOf<FreeSearchSnippet>()
        val root = runCatching { JsonParser.parseString(rawJson).asJsonObject }.getOrNull() ?: return out
        val searchArr = runCatching { root.getAsJsonObject("query")?.getAsJsonArray("search") }.getOrNull() ?: return out
        for (el in searchArr) {
            if (out.size >= 2) break
            val obj = runCatching { el.asJsonObject }.getOrNull() ?: continue
            val title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
            val rawSnippet = obj.get("snippet")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            val snippet = sanitizeSnippetText(rawSnippet).take(560)
            if (title.isBlank()) continue
            val pageSlug = URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "%20")
            out.add(
                FreeSearchSnippet(
                    title = title,
                    snippet = snippet.ifBlank { title },
                    url = "https://$lang.wikipedia.org/wiki/$pageSlug",
                    source = "wikipedia"
                )
            )
        }
        return out
    }

    private suspend fun executeWithLightRetry(
        request: Request,
        onRateLimited: (() -> Unit)? = null
    ): String? {
        var attempt = 0
        var backoffMs = 400L
        while (attempt <= MAX_RETRY_ON_IO) {
            try {
                client.newCall(request).execute().use { resp ->
                    if (resp.code == 202 || resp.code == 429) {
                        onRateLimited?.invoke()
                        return null
                    }
                    if (!resp.isSuccessful) {
                        return null
                    }
                    return resp.body?.string()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                if (attempt >= MAX_RETRY_ON_IO) return null
                delay(backoffMs)
                backoffMs *= 2
                attempt++
            }
        }
        return null
    }

    private fun decodeHtmlEntities(s: String): String =
        s.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .let { str ->
                HEX_ENTITY_REGEX.replace(str) { m ->
                    val cp = m.groupValues[1].toIntOrNull(16)
                    if (cp != null && cp in 32..0x10FFFF) String(Character.toChars(cp)) else m.value
                }
            }
            .let { str ->
                DEC_ENTITY_REGEX.replace(str) { m ->
                    val cp = m.groupValues[1].toIntOrNull()
                    if (cp != null && cp in 32..0x10FFFF) String(Character.toChars(cp)) else m.value
                }
            }
}
