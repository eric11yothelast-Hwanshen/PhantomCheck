package com.phantom.checker.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.phantom.checker.data.DiaryDayEntity
import com.phantom.checker.network.AiClient
import com.phantom.checker.util.PhantomPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * #378 AI 日記／每日回顧策略與本地時間軸聚合層：
 * 1. 時間軸事實層 100% 本機零 token SQL 聚合（訊息數 by app、記事新增、待辦完成、防詐警示數、簡報點閱），
 *    絕不讀敏感記憶，無 API key 亦 100% 可用。
 * 2. 每日 00:30 排程（獨立 unique work）＋ #254 maybeCatchUp 補跑（retries≤3、關機一天回開不斷篇）。
 * 3. AI 註記三重防護：僅引用 timelineJson 事實、展開生成一次快取、「不準」回饋降同類權重。
 */
object DiaryPolicy {

    private const val DAILY_WORK_NAME = "phantom_diary_daily_0030"
    private const val CATCHUP_WORK_NAME = "phantom_diary_catchup"
    private const val DISLIKE_SUPPRESS_THRESHOLD = 3

    data class TimelineItem(
        val timeLabel: String,
        val category: String, // messages | note | todo | scam | briefing
        val icon: String,
        val title: String,
        val detail: String
    )

    data class DayTimelineSummary(
        val dayKey: String,
        val items: List<TimelineItem>,
        val messageCount: Int,
        val noteCount: Int,
        val todoDoneCount: Int,
        val scamCount: Int,
        val annotation: String?,
        val annotationCategory: String?
    )

    fun schedule(ctx: Context) {
        val appCtx = ctx.applicationContext
        val wm = WorkManager.getInstance(appCtx)
        if (!PhantomPrefs.isDiaryDailyEnabled(appCtx)) {
            wm.cancelUniqueWork(DAILY_WORK_NAME)
            return
        }
        val now = LocalDateTime.now()
        var next0030 = now.toLocalDate().atTime(LocalTime.of(0, 30))
        if (!next0030.isAfter(now)) {
            next0030 = next0030.plusDays(1)
        }
        val initialDelayMin = Duration.between(now, next0030).toMinutes().coerceAtLeast(1L)
        val req = PeriodicWorkRequestBuilder<DiaryWorker>(24L, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMin, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(
            DAILY_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }

    /** #254 補跑機制：若昨日日記尚未生成（例如 00:30 關機），開機或開啟頁面即補跑（零 token 本地聚合，不強制網路） */
    fun maybeCatchUp(ctx: Context) {
        val appCtx = ctx.applicationContext
        if (!PhantomPrefs.isDiaryDailyEnabled(appCtx)) return
        val req = OneTimeWorkRequestBuilder<DiaryWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()
        WorkManager.getInstance(appCtx).enqueueUniqueWork(
            CATCHUP_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            req
        )
    }

    /** 零 token 本機 SQL 聚合指定日期之時間軸事實並寫入 diary_days */
    suspend fun buildAndSaveDayTimeline(
        ctx: Context,
        date: LocalDate = LocalDate.now().minusDays(1)
    ): DiaryDayEntity = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val db = AppDatabaseHolder.db(appCtx)
        val zone = ZoneId.systemDefault()
        val dayKey = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val dayStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEndMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

        val rawItems = mutableListOf<Pair<Long, TimelineItem>>()

        // 1. 當日訊息統計（依 appLabel 分組）＋ 防詐警示
        val allRecentMsgs = runCatching { db.messageDao().paged(600, 0) }.getOrDefault(emptyList())
        val dayMsgs = allRecentMsgs.filter { it.timestamp in dayStartMs until dayEndMs }
        val byApp = dayMsgs.groupBy { it.appLabel.ifBlank { it.packageName } }
            .entries.sortedByDescending { it.value.size }
        if (dayMsgs.isNotEmpty()) {
            val topAppsStr = byApp.take(4).joinToString("、") { "${it.key} ${it.value.size} 則" }
            val firstTs = dayMsgs.minOf { it.timestamp }
            rawItems.add(
                firstTs to TimelineItem(
                    timeLabel = Instant.ofEpochMilli(firstTs).atZone(zone).format(timeFmt),
                    category = "messages",
                    icon = "💬",
                    title = "收到 ${dayMsgs.size} 則通知訊息",
                    detail = topAppsStr
                )
            )
        }
        val scamMsgs = dayMsgs.filter { it.riskScore >= 60 || it.riskLabel == "scam" }
        if (scamMsgs.isNotEmpty()) {
            val latestScamTs = scamMsgs.maxOf { it.timestamp }
            rawItems.add(
                latestScamTs to TimelineItem(
                    timeLabel = Instant.ofEpochMilli(latestScamTs).atZone(zone).format(timeFmt),
                    category = "scam",
                    icon = "🛡️",
                    title = "攔截 ${scamMsgs.size} 則高風險／防詐警示",
                    detail = scamMsgs.take(2).joinToString("；") { "${it.appLabel}（風險 ${it.riskScore} 分）" }
                )
            )
        }

        // 2. 當日新增記事
        val dayNotes = runCatching { db.noteDao().all() }.getOrDefault(emptyList())
            .filter { it.createdAt in dayStartMs until dayEndMs }
        dayNotes.take(8).forEach { note ->
            val preview = note.content.replace("\n", " ").trim().take(42)
            rawItems.add(
                note.createdAt to TimelineItem(
                    timeLabel = Instant.ofEpochMilli(note.createdAt).atZone(zone).format(timeFmt),
                    category = "note",
                    icon = "📝",
                    title = "新增記事",
                    detail = preview
                )
            )
        }

        // 3. 當日完成待辦
        val doneTodos = runCatching { db.todoDao().all() }.getOrDefault(emptyList())
            .filter { it.isDone && it.completedAt != null && it.completedAt in dayStartMs until dayEndMs }
        doneTodos.take(10).forEach { todo ->
            val ts = todo.completedAt ?: todo.createdAt
            rawItems.add(
                ts to TimelineItem(
                    timeLabel = Instant.ofEpochMilli(ts).atZone(zone).format(timeFmt),
                    category = "todo",
                    icon = "✅",
                    title = "完成待辦",
                    detail = todo.title.take(48)
                )
            )
        }

        // 4. 當日簡報點閱
        val lastBriefingTs = PhantomPrefs.getLastBriefingTimestamp(appCtx)
        if (lastBriefingTs in dayStartMs until dayEndMs) {
            rawItems.add(
                lastBriefingTs to TimelineItem(
                    timeLabel = Instant.ofEpochMilli(lastBriefingTs).atZone(zone).format(timeFmt),
                    category = "briefing",
                    icon = "☀️",
                    title = "已產出並檢視個人簡報",
                    detail = "累計點閱 ${PhantomPrefs.getBriefingReadCount(appCtx)} 次"
                )
            )
        }

        val sorted = rawItems.sortedBy { it.first }.map { it.second }
        val arr = JSONArray()
        sorted.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("time", item.timeLabel)
                    put("category", item.category)
                    put("icon", item.icon)
                    put("title", item.title)
                    put("detail", item.detail)
                }
            )
        }
        val root = JSONObject().apply {
            put("messageCount", dayMsgs.size)
            put("noteCount", dayNotes.size)
            put("todoDoneCount", doneTodos.size)
            put("scamCount", scamMsgs.size)
            put("items", arr)
        }

        val existing = db.diaryDao().byDayKey(dayKey)
        val entity = DiaryDayEntity(
            dayKey = dayKey,
            timelineJson = root.toString(),
            generatedAt = System.currentTimeMillis(),
            annotation = existing?.annotation,
            annotatedAt = existing?.annotatedAt ?: 0L,
            annotationCategory = existing?.annotationCategory
        )
        db.diaryDao().upsert(entity)
        entity
    }

    fun parseSummary(entity: DiaryDayEntity): DayTimelineSummary {
        return try {
            val root = JSONObject(entity.timelineJson)
            val arr = root.optJSONArray("items") ?: JSONArray()
            val list = mutableListOf<TimelineItem>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(
                    TimelineItem(
                        timeLabel = o.optString("time", ""),
                        category = o.optString("category", "messages"),
                        icon = o.optString("icon", "•"),
                        title = o.optString("title", ""),
                        detail = o.optString("detail", "")
                    )
                )
            }
            DayTimelineSummary(
                dayKey = entity.dayKey,
                items = list,
                messageCount = root.optInt("messageCount", 0),
                noteCount = root.optInt("noteCount", 0),
                todoDoneCount = root.optInt("todoDoneCount", 0),
                scamCount = root.optInt("scamCount", 0),
                annotation = entity.annotation,
                annotationCategory = entity.annotationCategory
            )
        } catch (_: Exception) {
            DayTimelineSummary(entity.dayKey, emptyList(), 0, 0, 0, 0, entity.annotation, entity.annotationCategory)
        }
    }

    enum class AnnotationUnavailableReason {
        TOKEN_SAVER,
        NO_PROFILE,
        DISLIKED_SUPPRESSED,
        OTHER
    }

    fun dominantCategoryOf(summary: DayTimelineSummary): String = when {
        summary.scamCount > 0 -> "scam"
        summary.todoDoneCount > 0 -> "todo"
        summary.noteCount > 0 -> "note"
        else -> "messages"
    }

    /** #421 診斷 AI 回顧註記不可用的具體原因（供 DiarySection 顯示三態具體文案與捷徑） */
    fun diagnoseAnnotationUnavailable(ctx: Context, summary: DayTimelineSummary): AnnotationUnavailableReason {
        val appCtx = ctx.applicationContext
        if (PhantomPrefs.isTokenSaverEnabled(appCtx) && !PhantomPrefs.isDiaryAnnotationInSaverEnabled(appCtx)) {
            return AnnotationUnavailableReason.TOKEN_SAVER
        }
        val profile = com.phantom.checker.network.AiClient.getProfileForPurpose(
            appCtx,
            com.phantom.checker.util.AiPurpose.DIARY_ANNOTATION
        )
        if (profile == null || profile.apiKey.trim().isEmpty()) {
            return AnnotationUnavailableReason.NO_PROFILE
        }
        val dominantCategory = dominantCategoryOf(summary)
        if (PhantomPrefs.getDiaryAnnotationDislikeCount(appCtx, dominantCategory) >= DISLIKE_SUPPRESS_THRESHOLD) {
            return AnnotationUnavailableReason.DISLIKED_SUPPRESSED
        }
        return AnnotationUnavailableReason.OTHER
    }

    fun resetDislikeForCategory(ctx: Context, category: String?) {
        val cat = category?.ifBlank { "messages" } ?: "messages"
        PhantomPrefs.resetDiaryAnnotationDislike(ctx.applicationContext, cat)
    }

    /**
     * AI 註記三重防護：
     * 1. 已有快取直接回傳，絕不重複付費。
     * 2. 同類註記「不準」次數達門檻（≥3）則不再自動生成（可於 UI 重新啟用）。
     * 3. 僅送出 timelineJson 作為唯一事實輸入，prompt 嚴禁補無來源敘述；省 token 模式預設關閉（可由 diary_annotation_in_saver 放行）。
     */
    suspend fun getOrGenerateAnnotation(ctx: Context, dayKey: String): String? = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val db = AppDatabaseHolder.db(appCtx)
        val day = db.diaryDao().byDayKey(dayKey) ?: return@withContext null
        if (!day.annotation.isNullOrBlank()) return@withContext day.annotation
        if (PhantomPrefs.isTokenSaverEnabled(appCtx) && !PhantomPrefs.isDiaryAnnotationInSaverEnabled(appCtx)) {
            return@withContext null
        }

        val summary = parseSummary(day)
        if (summary.items.isEmpty()) return@withContext null

        val dominantCategory = dominantCategoryOf(summary)
        if (PhantomPrefs.getDiaryAnnotationDislikeCount(appCtx, dominantCategory) >= DISLIKE_SUPPRESS_THRESHOLD) {
            return@withContext null
        }

        val assistantName = PhantomPrefs.getAssistantName(appCtx)
        val diarySystemPrompt = if (AiClient.isZhTwLocale()) {
            "你是「$assistantName」。請僅依據下方 JSON 時間軸事實，用繁體中文寫 1–2 句簡短溫暖的回顧註記（以「我注意到今天…」開頭，≤80字）。嚴格禁止補充時間軸以外的任何事實、推測或情緒臆測。"
        } else {
            "You are \"$assistantName\". Based strictly on the JSON timeline facts below, write 1–2 brief, warm reflection sentences in English (locale: ${AiClient.resolveLocaleTag()}), starting with \"I noticed today...\" and within 60 words. Strictly forbid adding any facts, assumptions, or emotional speculation not present in the timeline."
        }
        val generated = runCatching {
            AiClient.generateBriefingText(
                context = appCtx,
                systemPrompt = diarySystemPrompt,
                userPrompt = day.timelineJson,
                maxTokens = 140,
                purpose = com.phantom.checker.util.AiPurpose.DIARY_ANNOTATION
            )
        }.getOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: return@withContext null

        db.diaryDao().updateAnnotation(dayKey, generated, System.currentTimeMillis(), dominantCategory)
        generated
    }

    suspend fun markAnnotationInaccurate(ctx: Context, dayKey: String, category: String?) = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val cat = category?.ifBlank { "messages" } ?: "messages"
        PhantomPrefs.incrementDiaryAnnotationDislike(appCtx, cat)
        AppDatabaseHolder.db(appCtx).diaryDao().updateAnnotation(dayKey, null, 0L, cat)
    }
}

/** #378 每日 00:30 本地時間軸整理 Worker（零 token，retries≤3，補跑不斷篇） */
class DiaryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!PhantomPrefs.isDiaryDailyEnabled(applicationContext)) return Result.success()
        return try {
            val db = AppDatabaseHolder.db(applicationContext)
            val today = LocalDate.now()
            // 補齊最近 3 天內缺漏的過去日期（關機一天回開不斷篇）
            for (offset in 1L..3L) {
                val targetDate = today.minusDays(offset)
                val key = targetDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                if (db.diaryDao().byDayKey(key) == null) {
                    DiaryPolicy.buildAndSaveDayTimeline(applicationContext, targetDate)
                }
            }
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}
