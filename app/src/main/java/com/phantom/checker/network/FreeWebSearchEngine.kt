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
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class FreeSearchSnippet(
    val title: String,
    val snippet: String,
    val url: String,
    val source: String
)

/**
 * #447 零金鑰免費搜尋引擎後端（純 OkHttp + Gson，無新增第三方依賴）：
 * 1) DuckDuckGo Instant Answer API（優先，輕量官方 JSON）
 * 2) DuckDuckGo HTML 端點（IA 不足時最多打 1 次；202/429/限速頁視為暫不可用並進入 5 分鐘負快取）
 * 3) Wikipedia OpenSearch / Query API（依語系 zh/en 併行嘗試）
 */
object FreeWebSearchEngine {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    private const val NEGATIVE_CACHE_TTL_MS = 5 * 60_000L
    private const val MAX_RETRY_ON_IO = 1 // 總計至多 2 次（初次 + 1 次 backoff 重試）

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

        val collected = LinkedHashMap<String, FreeSearchSnippet>()
        fun addAllUnique(items: List<FreeSearchSnippet>) {
            for (item in items) {
                if (collected.size >= 6) break
                val normUrl = item.url.trim()
                if (normUrl.startsWith("http://", ignoreCase = true) ||
                    normUrl.startsWith("https://", ignoreCase = true)
                ) {
                    collected.putIfAbsent(normUrl, item)
                }
            }
        }

        // 1. DuckDuckGo Instant Answer API（至多 1 次查詢，含 IO 指數 backoff）
        addAllUnique(fetchDdgInstantAnswer(clean))

        // 2. 結果不足 2 筆再嘗試 DuckDuckGo HTML 端點（若未處於 5 分鐘限速冷卻）
        if (collected.size < 2 && nowMs >= ddgHtmlCooldownUntilMs.get()) {
            addAllUnique(fetchDdgHtml(clean, nowMs))
        }

        // 3. 結果仍不足 3 筆時併行查詢 Wikipedia（zh / en 依語系排序）
        if (collected.size < 3) {
            addAllUnique(fetchWikipediaParallel(clean, preferZh))
        }

        val result = collected.values.toList()
        if (result.isEmpty()) {
            if (negativeQueryCache.size > 64) {
                negativeQueryCache.clear()
            }
            negativeQueryCache[cacheKey] = nowMs
        }
        result
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
                    snippet = abstractText.take(360),
                    url = abstractUrl,
                    source = "ddg_ia"
                )
            )
        }
        fun collectTopics(arr: com.google.gson.JsonArray?) {
            if (arr == null) return
            for (el in arr) {
                if (out.size >= 5) break
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
                            snippet = text.take(280),
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
    private val HTML_TAG_REGEX = Regex("<[^>]+>")

    internal fun parseDdgHtmlResults(html: String): List<FreeSearchSnippet> {
        val out = mutableListOf<FreeSearchSnippet>()
        val links = DDG_LINK_REGEX.findAll(html).toList()
        val snippets = DDG_SNIPPET_REGEX.findAll(html).toList()
        for (i in links.indices) {
            if (out.size >= 5) break
            val rawHref = decodeHtmlEntities(links[i].groupValues[1].trim())
            val resolvedUrl = unwrapDdgRedirect(rawHref) ?: continue
            val title = stripHtml(links[i].groupValues[2]).take(120)
            val snippet = if (i < snippets.size) stripHtml(snippets[i].groupValues[1]).take(320) else ""
            if (title.isNotBlank() && (snippet.isNotBlank() || resolvedUrl.isNotBlank())) {
                out.add(
                    FreeSearchSnippet(
                        title = title,
                        snippet = snippet.ifBlank { title },
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
            val snippet = stripHtml(rawSnippet).take(320)
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

    private fun stripHtml(raw: String): String =
        decodeHtmlEntities(HTML_TAG_REGEX.replace(raw, " "))
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun decodeHtmlEntities(s: String): String =
        s.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("&#(\\d+);")) { m ->
                val cp = m.groupValues[1].toIntOrNull()
                if (cp != null && cp in 32..0x10FFFF) String(Character.toChars(cp)) else m.value
            }
}
