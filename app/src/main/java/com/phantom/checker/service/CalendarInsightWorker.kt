package com.phantom.checker.service

import android.content.Context
import android.util.LruCache
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.phantom.checker.network.AiClient
import com.phantom.checker.util.CalendarBridge
import com.phantom.checker.util.EncryptedByokStore
import com.phantom.checker.util.PhantomPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * #365 CalendarInsightEngine 排程器：
 * - WorkManager PeriodicWork（30/60/120/180 分鐘，預設 60 分鐘）
 * - 切換頻率一律使用 ExistingPeriodicWorkPolicy.UPDATE 確保即時更新且冪等
 * - 關閉開關即 cancelUniqueWork
 */
object CalendarInsightScheduler {
    const val WORK_NAME = "phantom_calendar_insight_periodic"

    fun scheduleOrUpdate(ctx: Context) {
        val appCtx = ctx.applicationContext
        val wm = WorkManager.getInstance(appCtx)
        if (!PhantomPrefs.isProactiveCalendarInsightEnabled(appCtx)) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val intervalMinutes = PhantomPrefs.getCalendarInsightIntervalMinutes(appCtx).coerceIn(30, 180)
        val req = PeriodicWorkRequestBuilder<CalendarInsightWorker>(
            intervalMinutes.toLong(),
            TimeUnit.MINUTES
        ).build()
        wm.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }
}

/**
 * #365 CalendarInsightWorker：
 * 1. 僅讀使用者勾選的本機日曆未來 7 天（CalendarBridge.queryInstances，全程 Dispatchers.IO）；未授權或未勾選誠實跳過。
 * 2. 篩選未來 48 小時即將到來的事件，以「事件標題＋日期」（≤60 字、sanitizeForPrompt）查外部情報（嚴禁參與人、備註、位置）。
 * 3. 去重鍵 = eventId|beginMs|intelHash（LruCache + PhantomPrefs 持久化），無新情報零 AI 呼叫。
 * 4. 有新情報才以 AiClient.generateBriefingText 潤飾（失敗降級為零 token 模板），寫入專屬主動訊息聊天室並依開關發通知。
 * 5. 遵守每日主動上限、獨立勿擾時段、areNotificationsEnabled、零 trampoline，永遠回傳 Result.success() 不阻塞。
 */
class CalendarInsightWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val appCtx = applicationContext
        try {
            if (!PhantomPrefs.isProactiveCalendarInsightEnabled(appCtx)) {
                return@withContext Result.success()
            }
            if (!CalendarBridge.isCalendarEnabledForAi(appCtx)) {
                return@withContext Result.success()
            }

            val nowMs = System.currentTimeMillis()
            val sevenDaysEndMs = nowMs + 7L * 24L * 60L * 60L * 1000L
            val allEvents = CalendarBridge.queryInstances(appCtx, nowMs, sevenDaysEndMs)
            if (allEvents.isEmpty()) {
                return@withContext Result.success()
            }

            val upcomingCutoffMs = nowMs + UPCOMING_WINDOW_MS
            val upcomingEvents = allEvents
                .filter { it.beginMs in nowMs..upcomingCutoffMs }
                .sortedBy { it.beginMs }
                .take(MAX_EVENTS_PER_CYCLE)
            if (upcomingEvents.isEmpty()) {
                return@withContext Result.success()
            }

            val webSearchAllowed = PhantomPrefs.isProactiveCalendarWebSearchEnabled(appCtx) &&
                !PhantomPrefs.isTokenSaverEnabled(appCtx) &&
                EncryptedByokStore.isExternalSearchConfigured(appCtx)
            if (!webSearchAllowed) {
                return@withContext Result.success()
            }

            val zone = ZoneId.systemDefault()
            val dateOnlyFmt = DateTimeFormatter.ofPattern("MM/dd", Locale.US)
            val timeFmt = DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.US)

            data class EventInsight(
                val event: CalendarBridge.CalendarEventInstance,
                val intelText: String,
                val alertKey: String
            )

            val freshInsights = mutableListOf<EventInsight>()
            for (ev in upcomingEvents) {
                val dateStr = Instant.ofEpochMilli(ev.beginMs).atZone(zone).format(dateOnlyFmt)
                // 嚴守機密紅線：外部查詢字串僅含「事件標題＋日期」（≤60 字、sanitize），嚴禁參與人、備註、位置
                val cleanTitle = AiClient.sanitizeForPrompt(ev.title).trim()
                if (cleanTitle.isBlank()) continue
                val safeQuery = "$cleanTitle $dateStr".trim().take(MAX_EXTERNAL_QUERY_CHARS)
                val intel = AiClient.queryExternalSearchEndpoint(appCtx, safeQuery)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: continue
                val intelHash = sha256Hex(intel).take(16)
                val alertKey = "${ev.eventId}|${ev.beginMs}|$intelHash"
                if (isSeen(appCtx, alertKey)) continue
                freshInsights.add(EventInsight(ev, intel, alertKey))
            }

            if (freshInsights.isEmpty()) {
                return@withContext Result.success()
            }

            val dayKey = LocalDate.now(zone).format(DateTimeFormatter.ISO_LOCAL_DATE)
            if (PhantomPrefs.getProactiveDailySentCount(appCtx, dayKey) >= PhantomPrefs.getProactiveDailyCap(appCtx)) {
                return@withContext Result.success()
            }

            val assistantName = PhantomPrefs.getAssistantName(appCtx)
            val templateBody = buildString {
                appendLine("**📅 $assistantName 日曆情報提醒**")
                freshInsights.forEach { item ->
                    val whenStr = Instant.ofEpochMilli(item.event.beginMs).atZone(zone).format(timeFmt)
                    appendLine("- **${item.event.title}**（$whenStr）")
                    appendLine("  ${item.intelText}")
                }
            }.trim()

            val finalBody = if (PhantomPrefs.isTokenSaverEnabled(appCtx)) {
                templateBody
            } else {
                val polished = AiClient.generateBriefingText(
                    context = appCtx,
                    systemPrompt = "你是「$assistantName」，請將以下即將到來的日曆事件與最新外部情報潤飾為簡潔實用的繁體中文提醒（≤220字，保留所有事件名稱與時間，不可編造）。",
                    userPrompt = templateBody,
                    maxTokens = 300
                )
                if (!polished.isNullOrBlank()) "**📅 $assistantName 日曆情報提醒**\n$polished" else templateBody
            }

            val sendNotif = PhantomPrefs.isProactiveCalendarInsightNotifEnabled(appCtx)
            val notifId = NotifIds.calendarInsight(freshInsights.first().alertKey.hashCode())
            val msgId = ProactiveEngine.deliverProactiveMessage(
                ctx = appCtx,
                title = "📅 日曆情報：${freshInsights.first().event.title}",
                body = finalBody,
                notifId = notifId,
                sendNotif = sendNotif,
                respectDailyCap = true,
                nowMs = nowMs
            )
            if (msgId != null) {
                freshInsights.forEach { markSeen(appCtx, it.alertKey) }
            }
            Result.success()
        } catch (e: Exception) {
            android.util.Log.w("PhantomCalInsight", "CalendarInsightWorker failed: ${e.javaClass.simpleName}")
            Result.success()
        }
    }

    companion object {
        private const val UPCOMING_WINDOW_MS = 48L * 60L * 60L * 1000L
        private const val MAX_EVENTS_PER_CYCLE = 3
        private const val MAX_EXTERNAL_QUERY_CHARS = 60

        private val seenCache = object : LruCache<String, Boolean>(200) {}

        private fun isSeen(ctx: Context, key: String): Boolean {
            synchronized(seenCache) {
                if (seenCache.get(key) == true) return true
            }
            val persisted = PhantomPrefs.hasCalendarInsightSeenKey(ctx, key)
            if (persisted) {
                synchronized(seenCache) { seenCache.put(key, true) }
            }
            return persisted
        }

        private fun markSeen(ctx: Context, key: String) {
            synchronized(seenCache) { seenCache.put(key, true) }
            PhantomPrefs.markCalendarInsightSeenKey(ctx, key)
        }

        private fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
