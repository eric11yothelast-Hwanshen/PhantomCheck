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
import com.phantom.checker.data.MemoryEngine
import com.phantom.checker.data.WatchTopicEntity
import com.phantom.checker.network.AiClient
import com.phantom.checker.util.AiPurpose
import com.phantom.checker.util.PhantomPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * #390 主動情報中樞 Phase C：
 * - WatchQueryPlanner（純本機零 token）：挑出到期 active 主題（預設 24h、region 6h），依 confidence 排序，每輪至多 3 主題。
 * - DigestComposer（AI）：12h 結果短快取、SHA-256 去重、誠實「近期無新資訊」鐵句、附來源 URL。
 * - ProactiveIntelScheduler / ProactiveIntelWorker：PeriodicWorkRequest（CONNECTED＋battery-not-low、UPDATE）。
 * - WatchTopicMuteReceiver：通知「不再關注」動作接收器（exported=false，純背景寫庫，零 trampoline）。
 */
object WatchQueryPlanner {

    data class PlannedQuery(
        val topic: WatchTopicEntity,
        val queryString: String
    )

    private const val MAX_TOPICS_PER_CYCLE = 3
    private val DEFAULT_CADENCE_MS = TimeUnit.HOURS.toMillis(24)
    private val REGION_CADENCE_MS = TimeUnit.HOURS.toMillis(6)

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

        activeTopics
            .filter { topic ->
                if (ignoreCadence || topic.lastQueryAt <= 0L) {
                    true
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
            .map { topic ->
                val hint = AiClient.sanitizeForPrompt(topic.queryHint.ifBlank { topic.label }).trim()
                val queryStr = "$hint 最新動態".take(200)
                PlannedQuery(topic = topic, queryString = queryStr)
            }
    }
}

object DigestComposer {

    private val RAW_CACHE_TTL_MS = TimeUnit.HOURS.toMillis(12)

    private const val FACT_QUERY_PROMPT =
        "你是時事情報查詢員。針對 <topic> 用搜尋工具查『最新動態』，整理：1) 最近 7 天內的新事件優先 2) 每點附來源 URL。" +
            "若搜尋工具未回傳可用結果，只輸出『查無新資訊』，禁止用既有知識填補。" +
            "輸出 JSON：{\"items\":[{\"title\":\"\",\"detail\":\"\",\"url\":\"\",\"date\":\"\"}],\"foundNothing\":false}"

    private const val PUSH_SUMMARY_PROMPT =
        "把下列各主題的查詢結果整理成一則關注情報。規則：1) 每主題 ≤3 點、全部合計 ≤500 字 2) 保留各點來源 URL " +
            "3) 展示為使用者加上下文，例如指出『你關注的 X 有新進展』 4) foundNothing 的主題以一句帶過或省略。" +
            "若來源無實質新進展或你不確定，直接說『近期無新資訊』，禁止編造。輸出 Telegram-friendly Markdown，不要寒暄。"

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

    /** 執行一輪主動情報規劃→查詢→去重→摘要（forceManual=true 供設定頁「立即查詢」預覽） */
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
            }

            val isNothing = rawText.isBlank() ||
                rawText.contains("查無新資訊") ||
                rawText.contains("近期無新資訊") ||
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

            val digestHash = sha256Hex(MemoryEngine.normalizeForSimilarity(rawText))
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
            val msg = lastError ?: "近期無新資訊（所有查詢主題皆無實質新進展或無變動）。"
            return@withContext IntelCycleResult(null, emptyList(), null, msg)
        }

        val combinedRaw = buildString {
            novelItems.forEach { item ->
                appendLine("### ${item.topic.label}（查詢詞：${item.topic.queryHint}）")
                appendLine(AiClient.sanitizeForPrompt(item.rawText).take(450))
                if (item.sources.isNotEmpty()) {
                    appendLine("來源：${item.sources.take(3).joinToString(" , ")}")
                }
                appendLine()
            }
        }.trim()

        val aiComposed = AiClient.generateProactiveText(
            context = appCtx,
            systemPrompt = PUSH_SUMMARY_PROMPT,
            userPrompt = combinedRaw,
            maxTokens = 360
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
                if (item.sources.isNotEmpty()) {
                    item.sources.take(2).forEach { url -> appendLine("- 來源：$url") }
                }
                appendLine()
            }
        }.trim()

        val allSources = novelItems.flatMap { it.sources }.distinct().take(6)
        val fullDigest = buildString {
            appendLine(titleLine)
            appendLine(bodyContent)
            if (allSources.isNotEmpty() && allSources.none { bodyContent.contains(it) }) {
                appendLine("\n**參考來源**")
                allSources.forEach { url -> appendLine("- $url") }
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
