package com.phantom.checker.network

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
 * 2. fraud.tw（台灣 165 通報，GET /api/check?d=<host>）
 * 3. Google Safe Browsing Lookup API v4（僅 App 加密儲存 key／設定頁輸入）
 * 4. PhishTank（可選，預設關閉，支援可選 app_key）
 * 5. Chainabuse（可選，預設關閉，Basic 認證，僅查 crypto 地址）
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

    private const val PREFS_NAME = "phantom_secure_prefs"
    private const val KEY_SAFE_BROWSING_API = "intel_safe_browsing_key"
    private const val KEY_PHISHTANK_APP = "intel_phishtank_app_key"
    private const val KEY_CHAINABUSE_API = "intel_chainabuse_api_key"

    private const val CACHE_TTL_MS = 72L * 60L * 60L * 1000L
    private const val CONNECT_TIMEOUT_MS = 4000
    private const val READ_TIMEOUT_MS = 5000

    private val memoryCache = object : LruCache<String, CachedIntel>(512) {}
    private val domainTokenRegex = Regex("""\b([a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)+)\b""")

    private val descriptiveUserAgent: String
        get() = "PhantomCheck/${BuildConfig.VERSION_NAME} (Android anti-fraud)"

    @Suppress("DEPRECATION")
    private fun getEncryptedPrefs(context: Context): SharedPreferences? {
        return try {
            val appCtx = context.applicationContext
            val masterKey = MasterKey.Builder(appCtx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appCtx,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            null
        }
    }

    fun getSafeBrowsingApiKey(context: Context? = null): String? =
        context?.let { getEncryptedPrefs(it)?.getString(KEY_SAFE_BROWSING_API, null)?.trim()?.takeIf { k -> k.isNotEmpty() } }

    fun getSafeBrowsingKeySource(context: Context): ApiKeySource {
        val stored = getEncryptedPrefs(context)?.getString(KEY_SAFE_BROWSING_API, null)?.trim()
        return if (!stored.isNullOrEmpty()) ApiKeySource.ENCRYPTED_PREFS else ApiKeySource.NONE
    }

    fun setSafeBrowsingApiKey(context: Context, key: String) {
        val prefs = getEncryptedPrefs(context) ?: return
        val trimmed = key.trim()
        prefs.edit().apply {
            if (trimmed.isEmpty()) remove(KEY_SAFE_BROWSING_API) else putString(KEY_SAFE_BROWSING_API, trimmed)
        }.apply()
    }

    fun isSafeBrowsingKeyConfigured(context: Context? = null): Boolean = getSafeBrowsingApiKey(context) != null

    fun getPhishTankAppKey(context: Context): String? =
        getEncryptedPrefs(context)?.getString(KEY_PHISHTANK_APP, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setPhishTankAppKey(context: Context, key: String) {
        val prefs = getEncryptedPrefs(context) ?: return
        val trimmed = key.trim()
        prefs.edit().apply {
            if (trimmed.isEmpty()) remove(KEY_PHISHTANK_APP) else putString(KEY_PHISHTANK_APP, trimmed)
        }.apply()
    }

    fun getChainabuseApiKey(context: Context): String? =
        getEncryptedPrefs(context)?.getString(KEY_CHAINABUSE_API, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setChainabuseApiKey(context: Context, key: String) {
        val prefs = getEncryptedPrefs(context) ?: return
        val trimmed = key.trim()
        prefs.edit().apply {
            if (trimmed.isEmpty()) remove(KEY_CHAINABUSE_API) else putString(KEY_CHAINABUSE_API, trimmed)
        }.apply()
    }

    fun isChainabuseKeyConfigured(context: Context): Boolean = getChainabuseApiKey(context) != null

    fun matchesWhitelistRule(
        rule: AiRuleEntity,
        sender: String,
        packageName: String,
        appLabel: String,
        content: String,
        indicators: IndicatorExtractor.ExtractedIndicators? = null
    ): Boolean {
        if (!rule.isEnabled || rule.kind != AiRuleEntity.KIND_WHITELIST) return false

        // 1. 結構化寄件者精確比對（packageName+sender 或 appLabel+sender）
        val ruleSender = rule.sender?.trim().orEmpty()
        if (ruleSender.isNotEmpty()) {
            val senderMatch = sender.trim().equals(ruleSender, ignoreCase = true)
            if (!senderMatch) return false
            val rulePkg = rule.packageName?.trim().orEmpty()
            if (rulePkg.isEmpty()) return true
            return packageName.equals(rulePkg, ignoreCase = true) ||
                appLabel.equals(rulePkg, ignoreCase = true)
        }

        // 2. 既有自然語言 / 內容 / 網域比對
        val ruleText = rule.ruleText.trim()
        if (ruleText.isEmpty()) return false
        val rulePkg = rule.packageName?.trim().orEmpty()
        if (rulePkg.isNotEmpty() &&
            !packageName.equals(rulePkg, ignoreCase = true) &&
            !appLabel.equals(rulePkg, ignoreCase = true)
        ) {
            return false
        }
        if (content.contains(ruleText, ignoreCase = true)) return true

        val hosts = indicators?.urls?.mapNotNull { rawUrl ->
            try {
                Uri.parse(rawUrl).host?.lowercase()?.removePrefix("www.")
            } catch (_: Exception) {
                null
            }
        }.orEmpty()

        val ruleDomains = domainTokenRegex.findAll(ruleText)
            .map { it.groupValues[1].lowercase().removePrefix("www.") }
            .toList()
        if (ruleDomains.isNotEmpty() && hosts.any { host ->
                ruleDomains.any { rd -> host == rd || host.endsWith(".$rd") }
            }
        ) {
            return true
        }
        return false
    }

    fun isMessageWhitelisted(
        sender: String,
        packageName: String,
        appLabel: String,
        content: String,
        aiRules: List<AiRuleEntity>,
        indicators: IndicatorExtractor.ExtractedIndicators? = null
    ): Boolean {
        val activeWhitelist = aiRules.filter { it.isEnabled && it.kind == AiRuleEntity.KIND_WHITELIST }
        if (activeWhitelist.isEmpty()) return false
        return activeWhitelist.any { rule ->
            matchesWhitelistRule(rule, sender, packageName, appLabel, content, indicators)
        }
    }

    fun isWhitelisted(
        indicators: IndicatorExtractor.ExtractedIndicators,
        content: String,
        aiRules: List<AiRuleEntity>,
        sender: String = "",
        packageName: String = "",
        appLabel: String = ""
    ): Boolean = isMessageWhitelisted(
        sender = sender,
        packageName = packageName,
        appLabel = appLabel,
        content = content,
        aiRules = aiRules,
        indicators = indicators
    )

    suspend fun checkIndicators(
        context: Context,
        db: AppDatabase,
        indicators: IndicatorExtractor.ExtractedIndicators
    ): IntelCheckResult? = withContext(Dispatchers.IO) {
        if (indicators.isEmpty()) return@withContext null

        val appCtx = context.applicationContext
        val useFraudTw = PhantomPrefs.isIntelFraudTwEnabled(appCtx) && indicators.urls.isNotEmpty()
        val useSafeBrowsing = PhantomPrefs.isIntelSafeBrowsingEnabled(appCtx) &&
            isSafeBrowsingKeyConfigured(appCtx) &&
            indicators.urls.isNotEmpty()
        val usePhishTank = PhantomPrefs.isIntelPhishTankEnabled(appCtx) && indicators.urls.isNotEmpty()
        val useChainabuse = PhantomPrefs.isIntelChainabuseEnabled(appCtx) &&
            isChainabuseKeyConfigured(appCtx) &&
            indicators.cryptoAddresses.isNotEmpty()

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
            when (val outcome = querySafeBrowsing(appCtx, indicators.urls.take(20))) {
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
                when (val outcome = queryPhishTank(appCtx, url)) {
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
                when (val outcome = queryChainabuse(appCtx, addr)) {
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
            val appCtx = context.applicationContext
            when (provider) {
                IntelProvider.FRAUD_TW -> {
                    when (val outcome = queryFraudTw("https://example.com/")) {
                        is ProviderOutcome.Hit, is ProviderOutcome.Clean ->
                            ProviderTestResult(provider, true, "連線正常（fraud.tw 官方端點可用，免金鑰）")
                        is ProviderOutcome.Unavailable ->
                            ProviderTestResult(provider, false, "不可用：${outcome.reason}（管線將靜默降級）")
                    }
                }
                IntelProvider.SAFE_BROWSING -> {
                    if (!isSafeBrowsingKeyConfigured(appCtx)) {
                        return@withContext ProviderTestResult(
                            provider,
                            false,
                            "未設定 API key（請於上方輸入，或於 local.properties 設定 safebrowsing.apiKey）"
                        )
                    }
                    when (val outcome = querySafeBrowsing(appCtx, listOf("https://example.com/"))) {
                        is ProviderOutcome.Hit, is ProviderOutcome.Clean ->
                            ProviderTestResult(provider, true, "連線正常（Google Safe Browsing v4）")
                        is ProviderOutcome.Unavailable ->
                            ProviderTestResult(provider, false, "不可用：${outcome.reason}")
                    }
                }
                IntelProvider.PHISHTANK -> {
                    val hasKey = getPhishTankAppKey(appCtx) != null
                    when (val outcome = queryPhishTank(appCtx, "https://example.com/")) {
                        is ProviderOutcome.Hit, is ProviderOutcome.Clean -> {
                            val note = if (hasKey) {
                                "連線正常（PhishTank API，已帶 app_key）"
                            } else {
                                "連線正常（PhishTank API；未填 app_key，可能受較嚴格限流）"
                            }
                            ProviderTestResult(provider, true, note)
                        }
                        is ProviderOutcome.Unavailable ->
                            ProviderTestResult(provider, false, "不可用：${outcome.reason}")
                    }
                }
                IntelProvider.CHAINABUSE -> {
                    if (!isChainabuseKeyConfigured(appCtx)) {
                        return@withContext ProviderTestResult(
                            provider,
                            false,
                            "未設定 API key（需於 chainabuse.com 產生，標準 key 每月限 10 次呼叫）"
                        )
                    }
                    val dummy = IndicatorExtractor.CryptoIndicator(
                        IndicatorExtractor.CryptoChain.ETH,
                        "0x0000000000000000000000000000000000000000"
                    )
                    when (val outcome = queryChainabuse(appCtx, dummy)) {
                        is ProviderOutcome.Hit, is ProviderOutcome.Clean ->
                            ProviderTestResult(provider, true, "連線正常（Chainabuse Public API；標準 key 每月限 10 次）")
                        is ProviderOutcome.Unavailable ->
                            ProviderTestResult(provider, false, "不可用：${outcome.reason}")
                    }
                }
            }
        }

    private fun extractDomainHost(targetUrl: String): String? {
        return try {
            val withScheme = if (targetUrl.startsWith("http://", true) || targetUrl.startsWith("https://", true)) {
                targetUrl
            } else {
                "https://$targetUrl"
            }
            Uri.parse(withScheme).host?.lowercase()?.removePrefix("www.")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun queryFraudTw(targetUrl: String): ProviderOutcome {
        val host = extractDomainHost(targetUrl) ?: return ProviderOutcome.Unavailable("無法解析網域")
        return try {
            val encoded = URLEncoder.encode(host, "UTF-8")
            val resp = httpGet("https://fraud.tw/api/check?d=$encoded")
            if (!resp.ok) {
                return ProviderOutcome.Unavailable("HTTP ${resp.code}")
            }
            val root = JsonParser.parseString(resp.body)
            if (!root.isJsonObject) {
                return ProviderOutcome.Unavailable("回應格式非 JSON 物件")
            }
            val obj = root.asJsonObject
            val listed = obj.readBool("listed") ?: false
            val risk = obj.readString("risk")?.lowercase()
            if (listed || risk == "listed") {
                val category = obj.readString("category")
                val totalReports = obj.get("total_reports")
                    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                    ?.asInt
                val detail = buildString {
                    append(category ?: "165 通報涉詐網域")
                    if (totalReports != null && totalReports > 0) {
                        append("・累計通報 $totalReports 筆")
                    }
                    append("（$host）")
                }
                return ProviderOutcome.Hit("165 通報", detail)
            }
            if (obj.has("listed") || risk != null) {
                return ProviderOutcome.Clean
            }
            ProviderOutcome.Unavailable("欄位格式不相容")
        } catch (e: Exception) {
            ProviderOutcome.Unavailable(e.message ?: "連線失敗")
        }
    }

    private fun querySafeBrowsing(context: Context, urls: List<String>): ProviderOutcome {
        val apiKey = getSafeBrowsingApiKey(context) ?: return ProviderOutcome.Unavailable("未設定 API key")
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

    private fun queryPhishTank(context: Context, targetUrl: String): ProviderOutcome {
        return try {
            val appKey = getPhishTankAppKey(context)
            val form = buildString {
                append("url=").append(URLEncoder.encode(targetUrl, "UTF-8"))
                append("&format=json")
                if (!appKey.isNullOrBlank()) {
                    append("&app_key=").append(URLEncoder.encode(appKey, "UTF-8"))
                }
            }
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

    private fun queryChainabuse(context: Context, indicator: IndicatorExtractor.CryptoIndicator): ProviderOutcome {
        val apiKey = getChainabuseApiKey(context) ?: return ProviderOutcome.Unavailable("未設定 API key")
        return try {
            val encodedAddr = URLEncoder.encode(indicator.address, "UTF-8")
            val encodedChain = URLEncoder.encode(indicator.chain.name, "UTF-8")
            val creds = "$apiKey:$apiKey"
            val authHeader = "Basic " + Base64.encodeToString(creds.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val resp = httpGet(
                urlStr = "https://api.chainabuse.com/v0/reports?address=$encodedAddr&chain=$encodedChain",
                headers = mapOf("Authorization" to authHeader)
            )
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

    private fun httpGet(urlStr: String, headers: Map<String, String> = emptyMap()): HttpResponse {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", descriptiveUserAgent)
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
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
            setRequestProperty("User-Agent", descriptiveUserAgent)
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
            setRequestProperty("User-Agent", descriptiveUserAgent)
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
