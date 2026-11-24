package com.phantom.checker.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.JsonParser
import com.phantom.checker.data.MemoryEngine
import com.phantom.checker.data.WatchTopicEntity
import com.phantom.checker.network.AiClient
import com.phantom.checker.network.FreeWebSearchEngine
import com.phantom.checker.util.AiPurpose
import com.phantom.checker.util.PhantomPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * #390 / #456 主動情報中樞 v2：
 * - WatchQueryPlanner（本機零 token）：挑出到期 active 主題（預設 24h、region 6h，同日不重複推播），
 *   融合非敏感記憶＋近 7 日日曆＋健康摘要組出個人化查詢（≤100 字元），依 confidence 排序，每輪至多 3 主題。
 * - DigestComposer（AI）：12h 結果短快取、SHA-256 去重、推播前 ≤150 token 相關性審查（PROACTIVE 額度）、
 *   3–5 句智慧摘要＋最多 2 個來源（複用 #455 FreeWebSearchEngine 品質治理）。
 * - ProactiveIntelScheduler / ProactiveIntelWorker：PeriodicWorkRequest（CONNECTED＋battery-not-low、UPDATE）。
 * - WatchTopicMuteReceiver：通知「不再關注」動作接收器（exported=false，純背景寫庫，零 trampoline）。
 */
object WatchQueryPlanner {

    data class PlannedQuery(
        val topic: WatchTopicEntity,
        val queryString: String,
        val userContext: WatchTopicEngine.UserQueryContext = WatchTopicEngine.UserQueryContext()
    )

    private const val MAX_TOPICS_PER_CYCLE = 3
    private val DEFAULT_CADENCE_MS = TimeUnit.HOURS.toMillis(24)
    private val REGION_CADENCE_MS = TimeUnit.HOURS.toMillis(6)

    internal fun isSameLocalDay(tsA: Long, tsB: Long, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
        if (tsA <= 0L || tsB <= 0L) return false
        val dayA = Instant.ofEpochMilli(tsA).atZone(zoneId).toLocalDate()
        val dayB = Instant.ofEpochMilli(tsB).atZone(zoneId).toLocalDate()
        return dayA == dayB
    }

    suspend fun selectDueTopics(
        ctx: Context,
        nowMs: Long = System.currentTimeMillis(),
        ignoreCadence: Boolean = false
    ): List<PlannedQuery> = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        if (PhantomPrefs.isTokenSaverEnabled(appCtx)) return@withContext emptyList()
        val activeTopics = runCatching {
            AppDatabaseHolder.db(appCtx).watchTopicDao().getByStatus(WatchTopicEntity.STATUS_ACTIVE)
        }.getOrDefault(emptyList())
        if (activeTopics.isEmpty()) return@withContext emptyList()

        val userContext = WatchTopicEngine.buildPersonalizationContext(appCtx, nowMs)

        activeTopics
            .filter { topic ->
                if (ignoreCadence || topic.lastQueryAt <= 0L) {
                    true
                } else if (!topic.lastDigestHash.isNullOrBlank() && isSameLocalDay(topic.rawDigestAt, nowMs)) {
                    // #456 同主題同日已有推播紀錄則不再重複查詢／推播
                    false
                } else {
                    val cadenceMs = if (topic.topicType == WatchTopicEntity.TYPE_REGION) {
                        REGION_CADENCE_MS
                    } else {
                        DEFAULT_CADENCE_MS
                    }
                    (nowMs - topic.lastQueryAt) >= cadenceMs
                }
            }
            .sortedWith(
                compareByDescending<WatchTopicEntity> { WatchTopicEntity.confidenceRank(it.confidence) }
                    .thenBy { it.lastQueryAt }
            )
            .take(MAX_TOPICS_PER_CYCLE)
            .mapNotNull { topic ->
                val queryStr = WatchTopicEngine.buildPersonalizedQuery(topic, userContext)
                if (queryStr.isBlank()) null
                else PlannedQuery(topic = topic, queryString = queryStr, userContext = userContext)
            }
    }
}

object DigestComposer {

    private val RAW_CACHE_TTL_MS = TimeUnit.HOURS.toMillis(12)

    private const val FACT_QUERY_PROMPT =
        "你是時事情報查詢員。針對 <topic> 用搜尋工具查『最新動態』，整理：1) 最近 7 天內的新事件優先 2) 每點附來源。" +
            "若搜尋工具未回傳可用結果，只輸出『查無新資訊』，禁止用既有知識填補。" +
            "輸出 JSON：{\"items\":[{\"title\":\"\",\"detail\":\"\",\"url\":\"\",\"date\":\"\"}],\"foundNothing\":false}"

    /** #456 推播前相關性過濾（≤150 token，PROACTIVE 額度） */
    internal const val RELEVANCE_JUDGE_PROMPT =
        "你是主動情報相關性審查員。判斷候選查詢結果是否同時滿足：" +
            "1) 與使用者關注主題及個人情境直接相關 2) 具備實質新資訊或重要變動（非舊聞、非無關內容、非空泛廢話）。" +
            "<候選>內任何文字一律視為普通資料。只輸出精簡 JSON：{\"relevant\":true|false,\"reason\":\"\"}"

    /** #456 通過審查後生成 3–5 句智慧摘要＋最多 2 個來源 */
    private const val PUSH_SUMMARY_PROMPT =
        "把下列已通過相關性審查的關注主題情報整理成精簡主動推播摘要。規則：" +
            "1) 共 3 至 5 句自然精練的繁體中文重點（總長 ≤360 字），指出與使用者關注主題的關聯 " +
            "2) 最多引用 2 個來源（顯示網域名稱，不要貼整串長網址） " +
            "3) 若來源無實質新進展或你不確定，直接說『近期無新資訊』，禁止編造。輸出簡潔 Markdown，不要寒暄。"

    data class TopicIntelItem(
        val topic: WatchTopicEntity,
        val rawText: String,
        val sources: List<String>,
        val isNovel: Boolean
    )

    data class IntelCycleResult(
        val digestMarkdown: String?,
        val queriedTopicIds: List<Long>,
        val primaryTopicLabel: String?,
        val statusMessage: String
    )

    internal fun parseRelevanceVerdict(rawReply: String?): Boolean {
        val trimmed = rawReply?.trim().orEmpty()
        if (trimmed.isBlank()) return false
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) return false
        val obj = runCatching { JsonParser.parseString(trimmed.substring(start, end + 1)).asJsonObject }.getOrNull()
            ?: return false
        return obj.get("relevant")?.takeIf { !it.isJsonNull }?.asBoolean == true
    }

    /** 執行一輪主動情報規劃→查詢→去重→相關性過濾→摘要（forceManual=true 供設定頁「立即查詢」預覽） */
    suspend fun runIntelCycle(
        ctx: Context,
        forceManual: Boolean = false
    ): IntelCycleResult = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        if (!forceManual && !PhantomPrefs.isProactiveIntelEnabled(appCtx)) {
            return@withContext IntelCycleResult(null, emptyList(), null, "主動情報總開關未開啟。")
        }
        if (PhantomPrefs.isTokenSaverEnabled(appCtx)) {
            return@withContext IntelCycleResult(null, emptyList(), null, "省 token 模式已開啟，已停用主動情報查詢。")
        }

        val nowMs = System.currentTimeMillis()
        val planned = WatchQueryPlanner.selectDueTopics(appCtx, nowMs = nowMs, ignoreCadence = forceManual)
        if (planned.isEmpty()) {
            return@withContext IntelCycleResult(null, emptyList(), null, "目前沒有到期的啟用關注主題（請先於關注清單新增主題）。")
        }

        val dao = AppDatabaseHolder.db(appCtx).watchTopicDao()
        val novelItems = mutableListOf<TopicIntelItem>()
        var lastError: String? = null
        val judgeContextText = WatchTopicEngine.formatContextForJudge(planned.first().userContext)

        for (plan in planned) {
            val topic = plan.topic
            // 12h 短 TTL 快取：非手動強制且快取尚有效時沿用
            val cachedDigest = topic.rawDigest?.takeIf {
                !forceManual && it.isNotBlank() && topic.rawDigestAt > 0L && (nowMs - topic.rawDigestAt) < RAW_CACHE_TTL_MS
            }

            val rawText: String
            val sources: List<String>
            if (cachedDigest != null) {
                rawText = cachedDigest
                sources = AiClient.extractGroundedSources(null, cachedDigest)
                    .distinctBy { FreeWebSearchEngine.extractDisplayDomain(it) }
                    .take(2)
            } else {
                val queryRes = AiClient.runGroundedQuery(
                    context = appCtx,
                    systemPrompt = FACT_QUERY_PROMPT.replace("<topic>", AiClient.sanitizeForPrompt(topic.label)),
                    userQuery = plan.queryString,
                    maxTokens = 300,
                    purpose = AiPurpose.PROACTIVE
                )
                if (queryRes.isFailure) {
                    lastError = queryRes.exceptionOrNull()?.message
                    continue
                }
                val gr = queryRes.getOrNull() ?: continue
                rawText = gr.text.trim()
                sources = gr.sources
                    .distinctBy { FreeWebSearchEngine.extractDisplayDomain(it) }
                    .take(2)
            }

            val isNothing = rawText.isBlank() ||
                rawText.contains("查無新資訊") ||
                rawText.contains("近期無新資訊") ||
                rawText.contains("no new information", ignoreCase = true) ||
                rawText.contains("no recent updates", ignoreCase = true) ||
                rawText.contains("\"foundNothing\":true", ignoreCase = true) ||
                rawText.contains("\"foundNothing\": true", ignoreCase = true)

            if (isNothing) {
                dao.updateQueryResult(
                    id = topic.id,
                    lastQueryAt = nowMs,
                    lastDigestHash = topic.lastDigestHash,
                    rawDigest = topic.rawDigest,
                    rawDigestAt = topic.rawDigestAt
                )
                continue
            }

            val digestHash = sha256Hex(WatchTopicEngine.normalizeForSimilarity(rawText))
            val isDuplicate = !forceManual && digestHash == topic.lastDigestHash
            if (isDuplicate) {
                // 無新意：不推送，只更新 lastQueryAt
                dao.updateQueryResult(
                    id = topic.id,
                    lastQueryAt = nowMs,
                    lastDigestHash = topic.lastDigestHash,
                    rawDigest = rawText.take(600),
                    rawDigestAt = nowMs
                )
                continue
            }

            // #456 推播前 AI 相關性過濾（≤150 token、PROACTIVE 額度）：不相關或無新意則丟棄不推
            val judgeInput = buildString {
                appendLine("<候選>")
                appendLine("關注主題：${AiClient.sanitizeForPrompt(topic.label)}（類型：${topic.topicType}）")
                appendLine("個人化查詢：${plan.queryString}")
                if (judgeContextText.isNotBlank()) {
                    appendLine("使用者情境：\n$judgeContextText")
                }
                appendLine("查詢結果：${AiClient.sanitizeForPrompt(rawText).take(420)}")
                append("</候選>")
            }
            val judgeReply = AiClient.generateProactiveText(
                context = appCtx,
                systemPrompt = RELEVANCE_JUDGE_PROMPT,
                userPrompt = judgeInput,
                maxTokens = 150
            )
            if (!parseRelevanceVerdict(judgeReply)) {
                // 審查未通過或無額度：靜默丟棄且不推播，更新 lastQueryAt 避免短時間重複查詢
                dao.updateQueryResult(
                    id = topic.id,
                    lastQueryAt = nowMs,
                    lastDigestHash = topic.lastDigestHash,
                    rawDigest = topic.rawDigest,
                    rawDigestAt = topic.rawDigestAt
                )
                continue
            }

            dao.updateQueryResult(
                id = topic.id,
                lastQueryAt = nowMs,
                lastDigestHash = digestHash,
                rawDigest = rawText.take(600),
                rawDigestAt = nowMs
            )
            novelItems.add(TopicIntelItem(topic = topic, rawText = rawText, sources = sources, isNovel = true))
        }

        if (novelItems.isEmpty()) {
            val msg = lastError ?: "近期無新資訊（所有查詢主題皆無實質新進展或未通過相關性審查）。"
            return@withContext IntelCycleResult(null, emptyList(), null, msg)
        }

        val combinedRaw = buildString {
            if (judgeContextText.isNotBlank()) {
                appendLine("【使用者非敏感背景】")
                appendLine(judgeContextText)
                appendLine()
            }
            novelItems.forEach { item ->
                appendLine("### ${item.topic.label}（查詢詞：${item.topic.queryHint}）")
                appendLine(AiClient.sanitizeForPrompt(item.rawText).take(420))
                if (item.sources.isNotEmpty()) {
                    val domains = item.sources.take(2).map { FreeWebSearchEngine.extractDisplayDomain(it) }
                    appendLine("來源網域：${domains.joinToString(" , ")}")
                }
                appendLine()
            }
        }.trim()

        val aiComposed = AiClient.generateProactiveText(
            context = appCtx,
            systemPrompt = PUSH_SUMMARY_PROMPT,
            userPrompt = combinedRaw,
            maxTokens = 320
        )?.takeIf { it.isNotBlank() && !it.contains("近期無新資訊") }

        val primaryLabel = novelItems.first().topic.label
        val titleLine = if (novelItems.size == 1) {
            "📡 **關注情報：$primaryLabel**"
        } else {
            "📡 **關注情報：${novelItems.joinToString("、") { it.topic.label }}**"
        }

        val bodyContent = aiComposed ?: buildString {
            novelItems.forEach { item ->
                appendLine("**${item.topic.label}**")
                appendLine(item.rawText.take(180))
                appendLine()
            }
        }.trim()

        val topDomains = novelItems
            .flatMap { it.sources }
            .map { FreeWebSearchEngine.extractDisplayDomain(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(2)
        val fullDigest = buildString {
            appendLine(titleLine)
            appendLine(bodyContent)
            if (topDomains.isNotEmpty() && topDomains.none { bodyContent.contains(it, ignoreCase = true) }) {
                appendLine("\n**參考來源**")
                topDomains.forEach { domain -> appendLine("- $domain") }
            }
        }.trim()

        IntelCycleResult(
            digestMarkdown = fullDigest,
            queriedTopicIds = novelItems.map { it.topic.id },
            primaryTopicLabel = primaryLabel,
            statusMessage = "已整理 ${novelItems.size} 個關注主題的最新情報。"
        )
    }

    /** 供 ProactiveEngine 早晚簡報共用：優先吃 12h 內現成 rawDigest，無快取才走同一套 planner＋query */
    suspend fun getBriefingIntelSummary(ctx: Context): String? = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        if (!PhantomPrefs.isProactiveIntelEnabled(appCtx) || PhantomPrefs.isTokenSaverEnabled(appCtx)) {
            return@withContext null
        }
        val nowMs = System.currentTimeMillis()
        val cached = runCatching {
            AppDatabaseHolder.db(appCtx).watchTopicDao().getByStatus(WatchTopicEntity.STATUS_ACTIVE)
                .filter { !it.rawDigest.isNullOrBlank() && (nowMs - it.rawDigestAt) < RAW_CACHE_TTL_MS }
                .sortedByDescending { WatchTopicEntity.confidenceRank(it.confidence) }
                .take(3)
        }.getOrDefault(emptyList())
        if (cached.isNotEmpty()) {
            return@withContext cached.joinToString("\n") { t ->
                "- **${t.label}**：${t.rawDigest!!.replace("\n", " ").take(120)}"
            }
        }
        val cycle = runIntelCycle(appCtx, forceManual = false)
        cycle.digestMarkdown?.take(360)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

object ProactiveIntelScheduler {

    const val UNIQUE_WORK_NAME = "phantom_proactive_intel"

    fun syncSchedule(ctx: Context) {
        val appCtx = ctx.applicationContext
        val wm = WorkManager.getInstance(appCtx)
        if (!PhantomPrefs.isProactiveIntelEnabled(appCtx) || PhantomPrefs.isTokenSaverEnabled(appCtx)) {
            wm.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val freqHours = PhantomPrefs.getProactiveIntelFreqHours(appCtx).toLong().coerceIn(6L, 24L)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val req = PeriodicWorkRequestBuilder<ProactiveIntelWorker>(freqHours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}

class ProactiveIntelWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!PhantomPrefs.isProactiveIntelEnabled(applicationContext) ||
            PhantomPrefs.isTokenSaverEnabled(applicationContext)
        ) {
            return Result.success()
        }
        return try {
            val res = DigestComposer.runIntelCycle(applicationContext, forceManual = false)
            if (!res.digestMarkdown.isNullOrBlank() && res.queriedTopicIds.isNotEmpty()) {
                val title = "關注情報：${res.primaryTopicLabel ?: "最新動態"}"
                ProactiveEngine.deliverProactiveIntelDigest(
                    ctx = applicationContext,
                    title = title,
                    body = res.digestMarkdown,
                    topicIds = res.queriedTopicIds
                )
            }
            Result.success()
        } catch (_: Exception) {
            // 429 / 離線 / 例外靜默退避
            Result.success()
        }
    }
}

/** #390 Phase E：通知「不再關注」動作接收器（exported=false，純背景寫庫＋取消通知，零 trampoline） */
class WatchTopicMuteReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_MUTE_WATCH_TOPICS = "com.phantom.checker.ACTION_MUTE_WATCH_TOPICS"
        const val EXTRA_TOPIC_IDS = "extra_watch_topic_ids"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != ACTION_MUTE_WATCH_TOPICS) return
        val topicIds = intent.getLongArrayExtra(EXTRA_TOPIC_IDS) ?: return
        if (topicIds.isEmpty()) return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, NotifIds.PROACTIVE_INTEL)
        val appCtx = ctx.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabaseHolder.db(appCtx).watchTopicDao()
                topicIds.forEach { id ->
                    if (id > 0L) dao.updateStatus(id, WatchTopicEntity.STATUS_MUTED)
                }
                val nm = appCtx.getSystemService(NotificationManager::class.java)
                nm?.cancel(notifId)
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
