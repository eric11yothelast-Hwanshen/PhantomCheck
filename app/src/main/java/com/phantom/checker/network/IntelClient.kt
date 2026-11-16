package com.phantom.checker.network

import android.content.Context
import android.net.Uri
import android.util.LruCache
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.phantom.checker.BuildConfig
import com.phantom.checker.data.AiRuleEntity
import com.phantom.checker.data.AppDatabase
import com.phantom.checker.data.IntelCacheEntity
import com.phantom.checker.util.PhantomPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * 公開防詐情報查詢客戶端（零 LLM token、僅送 URL / 加密貨幣地址，不送訊息全文）。
 *
 * 查詢順序：
 * 1. 本地快取（LruCache + Room intel_cache，TTL 72 小時）
 * 2. fraud.tw（台灣 165 通報）
 * 3. Google Safe Browsing Lookup API v4（需 local.properties 注入 safebrowsing.apiKey）
 * 4. PhishTank（可選，預設關閉）
 * 5. Chainabuse（可選，預設關閉，僅查 crypto 地址）
 *
 * 任一外部來源失敗時靜默降級，絕不阻斷通知管線或造假命中。
 */
object IntelClient {

    enum class IntelProvider(val id: String, val displayName: String) {
        FRAUD_TW("fraud_tw", "165 通報（fraud.tw）"),
        SAFE_BROWSING("safe_browsing", "Google Safe Browsing v4"),
        PHISHTANK("phishtank", "PhishTank"),
        CHAINABUSE("chainabuse", "Chainabuse（鏈上地址）")
    }

    data class IntelCheckResult(
        val isHit: Boolean,
        val sourceLabel: String,
        val detail: String,
        val checkedAt: Long,
        val fromCache: Boolean = false
    )

    data class ProviderTestResult(
        val provider: IntelProvider,
        val ok: Boolean,
        val message: String
    )

    private sealed class ProviderOutcome {
        data class Hit(val sourceLabel: String, val detail: String) : ProviderOutcome()
        object Clean : ProviderOutcome()
        data class Unavailable(val reason: String) : ProviderOutcome()
    }

    private data class CachedIntel(
        val isHit: Boolean,
        val sourceLabel: String,
        val detail: String,
        val checkedAt: Long
    )

    private const val CACHE_TTL_MS = 72L * 60L * 60L * 1000L
    private const val CONNECT_TIMEOUT_MS = 4000
    private const val READ_TIMEOUT_MS = 5000

    private val memoryCache = object : LruCache<String, CachedIntel>(512) {}
    private val domainTokenRegex = Regex("""\b([a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)+)\b""")

    fun isSafeBrowsingKeyConfigured(): Boolean = BuildConfig.SAFE_BROWSING_API_KEY.isNotBlank()

    fun isWhitelisted(
        indicators: IndicatorExtractor.ExtractedIndicators,
        content: String,
        aiRules: List<AiRuleEntity>
    ): Boolean {
        val activeWhitelist = aiRules.filter { it.isEnabled && it.kind == AiRuleEntity.KIND_WHITELIST }
        if (activeWhitelist.isEmpty()) return false

        val hosts = indicators.urls.mapNotNull { rawUrl ->
            try {
                Uri.parse(rawUrl).host?.lowercase()?.removePrefix("www.")
            } catch (_: Exception) {
                null
            }
        }

        return activeWhitelist.any { rule ->
            val ruleText = rule.ruleText.trim()
            if (ruleText.isEmpty()) return@any false
            if (content.contains(ruleText, ignoreCase = true)) return@any true

            val ruleDomains = domainTokenRegex.findAll(ruleText)
                .map { it.groupValues[1].lowercase().removePrefix("www.") }
                .toList()
            if (ruleDomains.isNotEmpty() && hosts.any { host ->
                    ruleDomains.any { rd -> host == rd || host.endsWith(".$rd") }
                }
            ) {
                return@any true
            }
            false
        }
    }

    suspend fun checkIndicators(
        context: Context,
        db: AppDatabase,
        indicators: IndicatorExtractor.ExtractedIndicators
    ): IntelCheckResult? = withContext(Dispatchers.IO) {
        if (indicators.isEmpty()) return@withContext null

        val appCtx = context.applicationContext
        val useFraudTw = PhantomPrefs.isIntelFraudTwEnabled(appCtx) && indicators.urls.isNotEmpty()
        val useSafeBrowsing = PhantomPrefs.isIntelSafeBrowsingEnabled(appCtx) &&
            isSafeBrowsingKeyConfigured() &&
            indicators.urls.isNotEmpty()
        val usePhishTank = PhantomPrefs.isIntelPhishTankEnabled(appCtx) && indicators.urls.isNotEmpty()
        val useChainabuse = PhantomPrefs.isIntelChainabuseEnabled(appCtx) && indicators.cryptoAddresses.isNotEmpty()

        if (!useFraudTw && !useSafeBrowsing && !usePhishTank && !useChainabuse) {
            return@withContext null
        }

        val providerSig = buildString {
            append(if (useFraudTw) "ft1" else "ft0")
            append(if (useSafeBrowsing) "|sb1" else "|sb0")
            append(if (usePhishTank) "|pt1" else "|pt0")
            append(if (useChainabuse) "|ca1" else "|ca0")
        }
        val cacheKey = sha256("${indicators.canonicalKey()}|$providerSig")
        val now = System.currentTimeMillis()

        // 1. 記憶體快取
        synchronized(memoryCache) {
            memoryCache.get(cacheKey)?.let { cached ->
                if (now - cached.checkedAt <= CACHE_TTL_MS) {
                    return@withContext IntelCheckResult(
                        isHit = cached.isHit,
                        sourceLabel = cached.sourceLabel,
                        detail = cached.detail,
                        checkedAt = cached.checkedAt,
                        fromCache = true
                    )
                }
            }
        }

        // 2. Room 72h 快取
        try {
            db.intelDao().getCache(cacheKey)?.let { row ->
                if (now - row.checkedAt <= CACHE_TTL_MS) {
                    synchronized(memoryCache) {
                        memoryCache.put(
                            cacheKey,
                            CachedIntel(row.isHit, row.sourceLabel, row.detail, row.checkedAt)
                        )
                    }
                    return@withContext IntelCheckResult(
                        isHit = row.isHit,
                        sourceLabel = row.sourceLabel,
                        detail = row.detail,
                        checkedAt = row.checkedAt,
                        fromCache = true
                    )
                }
            }
        } catch (_: Exception) {
        }

        var anyClean = false

        // 3. fraud.tw (165 通報)
        if (useFraudTw) {
            for (url in indicators.urls.take(5)) {
                when (val outcome = queryFraudTw(url)) {
                    is ProviderOutcome.Hit -> {
                        return@withContext cacheAndReturn(db, cacheKey, now, true, outcome.sourceLabel, outcome.detail)
                    }
                    is ProviderOutcome.Clean -> anyClean = true
                    is ProviderOutcome.Unavailable -> Unit
                }
            }
        }

        // 4. Google Safe Browsing Lookup API v4
        if (useSafeBrowsing) {
            when (val outcome = querySafeBrowsing(indicators.urls.take(20))) {
                is ProviderOutcome.Hit -> {
                    return@withContext cacheAndReturn(db, cacheKey, now, true, outcome.sourceLabel, outcome.detail)
                }
                is ProviderOutcome.Clean -> anyClean = true
                is ProviderOutcome.Unavailable -> Unit
            }
        }

        // 5. PhishTank（可選）
        if (usePhishTank) {
            for (url in indicators.urls.take(3)) {
                when (val outcome = queryPhishTank(url)) {
                    is ProviderOutcome.Hit -> {
                        return@withContext cacheAndReturn(db, cacheKey, now, true, outcome.sourceLabel, outcome.detail)
                    }
                    is ProviderOutcome.Clean -> anyClean = true
                    is ProviderOutcome.Unavailable -> Unit
                }
            }
        }

        // 6. Chainabuse（可選，僅查加密貨幣地址）
        if (useChainabuse) {
            for (addr in indicators.cryptoAddresses.take(3)) {
                when (val outcome = queryChainabuse(addr)) {
                    is ProviderOutcome.Hit -> {
                        return@withContext cacheAndReturn(db, cacheKey, now, true, outcome.sourceLabel, outcome.detail)
                    }
                    is ProviderOutcome.Clean -> anyClean = true
                    is ProviderOutcome.Unavailable -> Unit
                }
            }
        }

        // 僅當至少有一個外部來源明確回應「未命中」時才快取與回傳已查證結果；
        // 若全部來源皆離線/不可用，靜默降級回 null，交由既有 AI 流程處理。
        if (!anyClean) return@withContext null

        return@withContext cacheAndReturn(
            db = db,
            cacheKey = cacheKey,
            checkedAt = now,
            isHit = false,
            sourceLabel = "已查證",
            detail = "已查證，無命中紀錄"
        )
    }

    private suspend fun cacheAndReturn(
        db: AppDatabase,
        cacheKey: String,
        checkedAt: Long,
        isHit: Boolean,
        sourceLabel: String,
        detail: String
    ): IntelCheckResult {
        synchronized(memoryCache) {
            memoryCache.put(cacheKey, CachedIntel(isHit, sourceLabel, detail, checkedAt))
        }
        try {
            db.intelDao().putCache(
                IntelCacheEntity(
                    cacheKey = cacheKey,
                    isHit = isHit,
                    sourceLabel = sourceLabel,
                    detail = detail,
                    checkedAt = checkedAt
                )
            )
        } catch (_: Exception) {
        }
        return IntelCheckResult(
            isHit = isHit,
            sourceLabel = sourceLabel,
            detail = detail,
            checkedAt = checkedAt,
            fromCache = false
        )
    }

    suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
        synchronized(memoryCache) {
            memoryCache.evictAll()
        }
        try {
            com.phantom.checker.service.AppDatabaseHolder.db(context).intelDao().clearCache()
        } catch (_: Exception) {
        }
    }

    suspend fun testProvider(context: Context, provider: IntelProvider): ProviderTestResult =
        withContext(Dispatchers.IO) {
            when (provider) {
                IntelProvider.FRAUD_TW -> {
                    when (val outcome = queryFraudTw("https://example.com/")) {
                        is ProviderOutcome.Hit, is ProviderOutcome.Clean ->
                            ProviderTestResult(provider, true, "連線正常（fraud.tw API 可用）")
                        is ProviderOutcome.Unavailable ->
                            ProviderTestResult(provider, false, "不可用：${outcome.reason}（管線將靜默降級）")
                    }
                }
                IntelProvider.SAFE_BROWSING -> {
                    if (!isSafeBrowsingKeyConfigured()) {
                        return@withContext ProviderTestResult(
                            provider,
                            false,
                            "未注入 safebrowsing.apiKey（請於 local.properties 設定）"
                        )
                    }
                    when (val outcome = querySafeBrowsing(listOf("https://example.com/"))) {
                        is ProviderOutcome.Hit, is ProviderOutcome.Clean ->
                            ProviderTestResult(provider, true, "連線正常（Google Safe Browsing v4）")
                        is ProviderOutcome.Unavailable ->
                            ProviderTestResult(provider, false, "不可用：${outcome.reason}")
                    }
                }
                IntelProvider.PHISHTANK -> {
                    when (val outcome = queryPhishTank("https://example.com/")) {
                        is ProviderOutcome.Hit, is ProviderOutcome.Clean ->
                            ProviderTestResult(provider, true, "連線正常（PhishTank API）")
                        is ProviderOutcome.Unavailable ->
                            ProviderTestResult(provider, false, "不可用：${outcome.reason}")
                    }
                }
                IntelProvider.CHAINABUSE -> {
                    val dummy = IndicatorExtractor.CryptoIndicator(
                        IndicatorExtractor.CryptoChain.ETH,
                        "0x0000000000000000000000000000000000000000"
                    )
                    when (val outcome = queryChainabuse(dummy)) {
                        is ProviderOutcome.Hit, is ProviderOutcome.Clean ->
                            ProviderTestResult(provider, true, "連線正常（Chainabuse Public API）")
                        is ProviderOutcome.Unavailable ->
                            ProviderTestResult(provider, false, "不可用：${outcome.reason}")
                    }
                }
            }
        }

    private fun queryFraudTw(targetUrl: String): ProviderOutcome {
        return try {
            val encoded = URLEncoder.encode(targetUrl, "UTF-8")
            val resp = httpGet("https://fraud.tw/api/v1/check?url=$encoded")
            if (!resp.ok) {
                return ProviderOutcome.Unavailable("HTTP ${resp.code}")
            }
            val root = JsonParser.parseString(resp.body)
            if (!root.isJsonObject) {
                return ProviderOutcome.Unavailable("回應格式非 JSON 物件")
            }
            val obj = root.asJsonObject
            val hitFlag = obj.readBool("isFraud")
                ?: obj.readBool("is_fraud")
                ?: obj.readBool("scam")
                ?: obj.readBool("hit")
                ?: obj.readBool("found")
                ?: obj.readBool("matched")
            if (hitFlag == true) {
                val reason = obj.readString("reason") ?: obj.readString("category") ?: "165 通報涉詐網站"
                return ProviderOutcome.Hit("165 通報", "$reason（$targetUrl）")
            }
            val status = obj.readString("status")?.lowercase()
            if (status in listOf("fraud", "scam", "danger", "malicious", "blocked")) {
                return ProviderOutcome.Hit("165 通報", "165 通報涉詐網站（$targetUrl）")
            }
            if (hitFlag == false || status in listOf("safe", "clean", "ok", "normal")) {
                return ProviderOutcome.Clean
            }
            ProviderOutcome.Unavailable("欄位格式不相容")
        } catch (e: Exception) {
            ProviderOutcome.Unavailable(e.message ?: "連線失敗")
        }
    }

    private fun querySafeBrowsing(urls: List<String>): ProviderOutcome {
        val apiKey = BuildConfig.SAFE_BROWSING_API_KEY
        if (apiKey.isBlank()) return ProviderOutcome.Unavailable("未設定 API key")
        return try {
            val payload = JsonObject().apply {
                add("client", JsonObject().apply {
                    addProperty("clientId", "phantomcheck")
                    addProperty("clientVersion", BuildConfig.VERSION_NAME)
                })
                add("threatInfo", JsonObject().apply {
                    add("threatTypes", JsonArray().apply {
                        add("MALWARE")
                        add("SOCIAL_ENGINEERING")
                        add("UNWANTED_SOFTWARE")
                        add("POTENTIALLY_HARMFUL_APPLICATION")
                    })
                    add("platformTypes", JsonArray().apply { add("ANY_PLATFORM") })
                    add("threatEntryTypes", JsonArray().apply { add("URL") })
                    add("threatEntries", JsonArray().apply {
                        urls.forEach { u ->
                            add(JsonObject().apply { addProperty("url", u) })
                        }
                    })
                })
            }
            val endpoint = "https://safebrowsing.googleapis.com/v4/threatMatches:find?key=${URLEncoder.encode(apiKey, "UTF-8")}"
            val resp = httpPostJson(endpoint, payload.toString())
            if (!resp.ok) {
                return ProviderOutcome.Unavailable("HTTP ${resp.code}")
            }
            val obj = JsonParser.parseString(resp.body).asJsonObject
            val matches = obj.getAsJsonArray("matches")
            if (matches != null && matches.size() > 0) {
                val first = matches[0].asJsonObject
                val threatType = first.readString("threatType") ?: "SOCIAL_ENGINEERING"
                val matchedUrl = first.getAsJsonObject("threat")?.readString("url") ?: urls.firstOrNull().orEmpty()
                ProviderOutcome.Hit(
                    sourceLabel = "Google Safe Browsing",
                    detail = "威脅類型：$threatType（$matchedUrl）"
                )
            } else {
                ProviderOutcome.Clean
            }
        } catch (e: Exception) {
            ProviderOutcome.Unavailable(e.message ?: "連線失敗")
        }
    }

    private fun queryPhishTank(targetUrl: String): ProviderOutcome {
        return try {
            val form = "url=${URLEncoder.encode(targetUrl, "UTF-8")}&format=json"
            val resp = httpPostForm("https://checkurl.phishtank.com/checkurl/", form)
            if (!resp.ok) {
                return ProviderOutcome.Unavailable("HTTP ${resp.code}")
            }
            val obj = JsonParser.parseString(resp.body).asJsonObject
            val results = obj.getAsJsonObject("results")
                ?: return ProviderOutcome.Unavailable("缺少 results 欄位")
            val inDb = results.readBool("in_database") ?: false
            val valid = results.readBool("valid") ?: false
            if (inDb && valid) {
                ProviderOutcome.Hit("PhishTank", "已驗證釣魚網址（$targetUrl）")
            } else {
                ProviderOutcome.Clean
            }
        } catch (e: Exception) {
            ProviderOutcome.Unavailable(e.message ?: "連線失敗")
        }
    }

    private fun queryChainabuse(indicator: IndicatorExtractor.CryptoIndicator): ProviderOutcome {
        return try {
            val encoded = URLEncoder.encode(indicator.address, "UTF-8")
            val resp = httpGet("https://api.chainabuse.com/v0/reports?address=$encoded")
            if (!resp.ok) {
                return ProviderOutcome.Unavailable("HTTP ${resp.code}")
            }
            val elem = JsonParser.parseString(resp.body)
            val count = when {
                elem.isJsonArray -> elem.asJsonArray.size()
                elem.isJsonObject -> {
                    val obj = elem.asJsonObject
                    when {
                        obj.has("reports") && obj.get("reports").isJsonArray -> obj.getAsJsonArray("reports").size()
                        obj.has("data") && obj.get("data").isJsonArray -> obj.getAsJsonArray("data").size()
                        else -> 0
                    }
                }
                else -> 0
            }
            if (count > 0) {
                ProviderOutcome.Hit(
                    sourceLabel = "Chainabuse",
                    detail = "${indicator.chain.label} 地址被檢舉 $count 次（${indicator.address.take(12)}…）"
                )
            } else {
                ProviderOutcome.Clean
            }
        } catch (e: Exception) {
            ProviderOutcome.Unavailable(e.message ?: "連線失敗")
        }
    }

    private data class HttpResponse(val ok: Boolean, val code: Int, val body: String)

    private fun httpGet(urlStr: String): HttpResponse {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "PhantomCheck/${BuildConfig.VERSION_NAME}")
        }
        return readHttpResponse(conn)
    }

    private fun httpPostJson(urlStr: String, jsonBody: String): HttpResponse {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "PhantomCheck/${BuildConfig.VERSION_NAME}")
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(jsonBody) }
        return readHttpResponse(conn)
    }

    private fun httpPostForm(urlStr: String, formBody: String): HttpResponse {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "PhantomCheck/${BuildConfig.VERSION_NAME}")
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(formBody) }
        return readHttpResponse(conn)
    }

    private fun readHttpResponse(conn: HttpURLConnection): HttpResponse {
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = if (stream != null) {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            } else {
                ""
            }
            HttpResponse(code in 200..299, code, body)
        } finally {
            conn.disconnect()
        }
    }

    private fun JsonObject.readBool(key: String): Boolean? {
        val el = get(key) ?: return null
        if (!el.isJsonPrimitive) return null
        val prim = el.asJsonPrimitive
        return when {
            prim.isBoolean -> prim.asBoolean
            prim.isNumber -> prim.asInt != 0
            prim.isString -> prim.asString.equals("true", ignoreCase = true)
            else -> null
        }
    }

    private fun JsonObject.readString(key: String): String? {
        val el = get(key) ?: return null
        if (!el.isJsonPrimitive) return null
        return el.asString?.takeIf { it.isNotBlank() }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
