package com.phantom.checker.network

import android.util.Log
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * #532 主動情報 v3 Google News RSS 引擎（免費、無需 API Key、純 Android XmlPullParser 零新依賴）：
 * - 搜尋 feed：https://news.google.com/rss/search?q=<關鍵詞>&hl=zh-TW&gl=TW&ceid=TW:zh-Hant
 * - 地區 geo feed：https://news.google.com/rss/headlines/section/geo/<地區>?hl=zh-TW&gl=TW&ceid=TW:zh-Hant
 * - 新鮮度過濾 <48h、每主題 top 3–5、URL＋標題 SHA-256 去重、來源網域治理沿用 FreeWebSearchEngine.DOMAIN_QUALITY
 * - 429 / 離線 / 解析失敗誠實回傳錯誤並記錄 Log，絕不靜默吞例外
 */
object GoogleNewsRssEngine {

    private const val TAG = "GoogleNewsRssEngine"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 10_000
    private val MAX_AGE_MS = TimeUnit.HOURS.toMillis(48)
    private const val MAX_ITEMS_PER_FEED = 5
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    data class NewsRssItem(
        val title: String,
        val link: String,
        val sourceName: String,
        val sourceUrl: String,
        val sourceDomain: String,
        val publishedAtMs: Long,
        val domainScore: Int,
        val dedupHash: String
    )

    data class RssFetchResult(
        val items: List<NewsRssItem> = emptyList(),
        val errorMessage: String? = null,
        val isRateLimited: Boolean = false
    )

    suspend fun fetchSearchFeed(
        query: String,
        maxItems: Int = MAX_ITEMS_PER_FEED,
        nowMs: Long = System.currentTimeMillis()
    ): RssFetchResult {
        val cleanQ = query.trim().take(120)
        if (cleanQ.isBlank()) return RssFetchResult()
        val encoded = runCatching { URLEncoder.encode(cleanQ, "UTF-8") }.getOrDefault(cleanQ)
        val url = "https://news.google.com/rss/search?q=$encoded&hl=zh-TW&gl=TW&ceid=TW:zh-Hant"
        return fetchAndParse(url, maxItems, nowMs)
    }

    suspend fun fetchGeoFeed(
        region: String = "台灣",
        maxItems: Int = MAX_ITEMS_PER_FEED,
        nowMs: Long = System.currentTimeMillis()
    ): RssFetchResult {
        val cleanRegion = region.trim().ifBlank { "台灣" }.take(32)
        val encoded = runCatching { URLEncoder.encode(cleanRegion, "UTF-8") }.getOrDefault(cleanRegion)
        val url = "https://news.google.com/rss/headlines/section/geo/$encoded?hl=zh-TW&gl=TW&ceid=TW:zh-Hant"
        return fetchAndParse(url, maxItems, nowMs)
    }

    internal suspend fun fetchAndParse(
        feedUrl: String,
        maxItems: Int = MAX_ITEMS_PER_FEED,
        nowMs: Long = System.currentTimeMillis()
    ): RssFetchResult = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml;q=0.9, */*;q=0.8")
                setRequestProperty("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.7")
            }
            val code = conn.responseCode
            if (code == 429 || code == 503) {
                val msg = "Google News RSS HTTP $code (rate limited)"
                Log.w(TAG, msg)
                return@withContext RssFetchResult(errorMessage = msg, isRateLimited = true)
            }
            if (code !in 200..299) {
                val msg = "Google News RSS HTTP $code"
                Log.w(TAG, msg)
                return@withContext RssFetchResult(errorMessage = msg)
            }
            val xmlText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (xmlText.isBlank() || !xmlText.contains("<rss", ignoreCase = true)) {
                val msg = "Google News RSS returned non-RSS payload (possible rate limit or block)"
                Log.w(TAG, msg)
                return@withContext RssFetchResult(errorMessage = msg, isRateLimited = xmlText.contains("Sorry", ignoreCase = true))
            }
            val parsed = parseRssXml(xmlText, nowMs)
                .filter { it.domainScore >= 0 } // Drop content farms / negative quality domains
                .distinctBy { it.dedupHash }
                .sortedWith(
                    compareByDescending<NewsRssItem> { it.domainScore }
                        .thenByDescending { it.publishedAtMs }
                )
                .take(maxItems.coerceIn(1, 8))
            RssFetchResult(items = parsed)
        } catch (e: Exception) {
            val msg = "Google News RSS fetch error: ${e.javaClass.simpleName}: ${e.message}"
            Log.w(TAG, msg, e)
            RssFetchResult(errorMessage = msg)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    internal fun parseRssXml(xml: String, nowMs: Long = System.currentTimeMillis()): List<NewsRssItem> {
        val result = mutableListOf<NewsRssItem>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))

            var inItem = false
            var title = ""
            var link = ""
            var pubDateRaw = ""
            var sourceName = ""
            var sourceUrl = ""
            var currentTag = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name?.lowercase(Locale.US).orEmpty()
                        if (name == "item") {
                            inItem = true
                            title = ""
                            link = ""
                            pubDateRaw = ""
                            sourceName = ""
                            sourceUrl = ""
                        } else if (inItem) {
                            currentTag = name
                            if (name == "source") {
                                sourceUrl = parser.getAttributeValue(null, "url").orEmpty().trim()
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inItem) {
                            val txt = parser.text.orEmpty()
                            when (currentTag) {
                                "title" -> title += txt
                                "link" -> link += txt
                                "pubdate" -> pubDateRaw += txt
                                "source" -> sourceName += txt
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name?.lowercase(Locale.US).orEmpty()
                        if (name == "item" && inItem) {
                            inItem = false
                            val cleanTitle = AiClient.sanitizeForPrompt(FreeWebSearchEngine.sanitizeSnippetText(title)).take(140)
                            val cleanLink = link.trim()
                            val cleanSourceName = FreeWebSearchEngine.sanitizeSnippetText(sourceName).take(40)
                            val publishedMs = parseRfc822Date(pubDateRaw.trim())
                            val isFresh = publishedMs <= 0L || (nowMs - publishedMs) in 0L..MAX_AGE_MS
                            if (cleanTitle.isNotBlank() && isFresh) {
                                val domainSource = sourceUrl.ifBlank { cleanLink }
                                val domain = FreeWebSearchEngine.extractDisplayDomain(domainSource)
                                val score = FreeWebSearchEngine.domainQualityScore(domain)
                                val dedupHash = sha256Hex("${cleanLink.lowercase(Locale.US)}|${cleanTitle.lowercase(Locale.US)}")
                                result.add(
                                    NewsRssItem(
                                        title = cleanTitle,
                                        link = cleanLink,
                                        sourceName = cleanSourceName.ifBlank { domain },
                                        sourceUrl = sourceUrl.ifBlank { cleanLink },
                                        sourceDomain = domain,
                                        publishedAtMs = publishedMs,
                                        domainScore = score,
                                        dedupHash = dedupHash
                                    )
                                )
                            }
                        }
                        if (name == currentTag) currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "XML parse error: ${e.javaClass.simpleName}: ${e.message}", e)
        }
        return result
    }

    internal fun parseRfc822Date(raw: String): Long {
        if (raw.isBlank()) return 0L
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "dd MMM yyyy HH:mm:ss zzz"
        )
        for (p in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(p, Locale.US).parse(raw)?.time
            }.getOrNull()
            if (parsed != null && parsed > 0L) return parsed
        }
        return 0L
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
