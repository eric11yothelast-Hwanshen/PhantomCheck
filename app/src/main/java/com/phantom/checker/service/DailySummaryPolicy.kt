package com.phantom.checker.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.phantom.checker.data.AiSummaryEntity
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.network.AiClient
import com.phantom.checker.util.PhantomPrefs
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 每日自動生成當日訊息 AI 摘要的排程 Worker。
 */
class DailySummaryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (PhantomPrefs.isEffectiveDailySummaryEnabled(applicationContext)) {
            DailySummaryPolicy.generateTodaySummary(applicationContext)
        }
        return Result.success()
    }
}

object DailySummaryPolicy {
    private const val UNIQUE_WORK_NAME = "phantom_daily_ai_summary"

    fun schedule(ctx: Context) {
        val appCtx = ctx.applicationContext
        val workManager = WorkManager.getInstance(appCtx)
        if (!PhantomPrefs.isEffectiveDailySummaryEnabled(appCtx)) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val hour = PhantomPrefs.getDailySummaryHour(appCtx)
        val minute = PhantomPrefs.getDailySummaryMinute(appCtx)

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val initialDelayMs = (target.timeInMillis - now.timeInMillis).coerceAtLeast(0L)

        val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun todayDateKey(): Long {
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        return fmt.format(Date()).toLongOrNull() ?: 0L
    }

    fun dateKeyDaysAgo(daysAgo: Int): Long {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo)
        }
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        return fmt.format(cal.time).toLongOrNull() ?: 0L
    }

    suspend fun generateTodaySummary(ctx: Context): Result<AiSummaryEntity> {
        val appCtx = ctx.applicationContext
        AiClient.init(appCtx)
        if (AiClient.getActiveProfile(appCtx) == null) {
            return Result.failure(IllegalStateException("尚未設定 AI Provider API Key"))
        }

        val startOfDay = NotificationCollectorService.startOfTodayMillis()
        val db = AppDatabaseHolder.db(appCtx)
        val todayMessages = db.messageDao().since(startOfDay)

        if (todayMessages.isEmpty()) {
            return Result.failure(IllegalStateException("今日尚無訊息可供摘要"))
        }

        val appCounts = todayMessages
            .groupBy { it.appLabel.ifBlank { it.packageName } }
            .entries
            .sortedByDescending { it.value.size }
            .joinToString("、") { "${it.key} ${it.value.size} 則" }

        val excerptsBlock = buildSummaryExcerpts(todayMessages)

        val prompt = buildString {
            appendLine("請用繁體中文 5 句內摘要今日訊息重點與風險：")
            appendLine("今日總訊息數：${todayMessages.size} 則")
            appendLine("各 App 統計：$appCounts")
            append(excerptsBlock)
        }

        val summaryText = AiClient.summarizeDaily(prompt, appCtx)
            ?: return Result.failure(IllegalStateException("AI 摘要生成失敗，請確認網路與 API 設定"))

        val entity = AiSummaryEntity(
            date = todayDateKey(),
            summary = summaryText,
            generatedAt = System.currentTimeMillis()
        )
        db.aiSummaryDao().insertOrReplace(entity)
        com.phantom.checker.widget.AiSummaryWidgetProvider.updateAllIfChanged(appCtx)

        if (PhantomPrefs.isEffectiveDailySummaryEnabled(appCtx) &&
            PhantomPrefs.isEffectiveDailySummaryNotifEnabled(appCtx) &&
            StatsForegroundService.hasPostNotificationsPermission(appCtx)
        ) {
            postSummaryNotification(appCtx, summaryText)
        }

        return Result.success(entity)
    }

    internal fun buildSummaryExcerpts(
        messages: List<MessageEntity>,
        maxChars: Int = 2400
    ): String {
        data class DedupBucket(val msg: MessageEntity, var count: Int = 1)

        fun dedup(list: List<MessageEntity>, maxItems: Int): List<DedupBucket> {
            val map = LinkedHashMap<String, DedupBucket>()
            for (m in list) {
                val normPrefix = m.content.replace(Regex("\\s+"), " ").trim().take(40)
                val key = "${m.packageName}|${m.sender.trim()}|$normPrefix"
                val existing = map[key]
                if (existing == null) {
                    if (map.size >= maxItems) continue
                    map[key] = DedupBucket(m, 1)
                } else {
                    existing.count++
                }
            }
            return map.values.toList()
        }

        val riskyBuckets = dedup(
            messages.filter { it.riskLabel == "scam" || it.riskLabel == "suspicious" },
            maxItems = 20
        )
        val unanalyzedBuckets = dedup(
            messages.filter { it.riskLabel.isBlank() },
            maxItems = 10
        )

        return buildString {
            var remaining = maxChars
            var truncated = false

            fun appendSection(header: String, buckets: List<DedupBucket>, tagResolver: (MessageEntity) -> String) {
                if (buckets.isEmpty() || remaining <= header.length + 16) return
                appendLine(header)
                remaining -= header.length + 1
                buckets.forEachIndexed { idx, bucket ->
                    if (truncated) return@forEachIndexed
                    val m = bucket.msg
                    val tag = tagResolver(m)
                    val repeat = if (bucket.count > 1) " (×${bucket.count})" else ""
                    val snippet = m.content.replace("\n", " ").trim().take(90)
                    val line = "${idx + 1}. [$tag][${m.appLabel}] ${m.sender}$repeat: $snippet"
                    if (line.length + 1 <= remaining) {
                        appendLine(line)
                        remaining -= line.length + 1
                    } else {
                        truncated = true
                    }
                }
            }

            if (riskyBuckets.isNotEmpty()) {
                appendSection("高風險訊息節錄（已去重）：", riskyBuckets) {
                    if (it.riskLabel == "scam") "詐騙" else "疑似"
                }
            } else {
                appendLine("無高風險（scam/suspicious）訊息。")
            }

            if (unanalyzedBuckets.isNotEmpty() && !truncated) {
                appendSection("未分析訊息節錄（供參考）：", unanalyzedBuckets) { "未分析" }
            }

            if (truncated) {
                appendLine("（部分節錄已依 token 上限截斷）")
            }
        }
    }

    private fun postSummaryNotification(ctx: Context, summaryText: String = "") {
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            android.app.NotificationChannel(
                "daily_summary",
                "每日摘要",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
        )
        val intent = android.content.Intent(ctx, com.phantom.checker.ui.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_TAB, TAB_STATS)
        }
        val pi = android.app.PendingIntent.getActivity(
            ctx,
            DAILY_SUMMARY_NOTIF_ID,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val plainPreview = com.phantom.checker.ui.stripMarkdown(summaryText)
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(120)
            .ifBlank { "點擊查看今日訊息重點與風險統計" }
        val notif = android.app.Notification.Builder(ctx, "daily_summary")
            .setSmallIcon(com.phantom.checker.R.drawable.ic_stat_phantom)
            .setContentTitle("今日訊息摘要已生成")
            .setContentText(plainPreview)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(DAILY_SUMMARY_NOTIF_ID, notif)
        } catch (_: SecurityException) {
        }
    }

    const val EXTRA_OPEN_TAB = "extra_open_tab"
    const val TAB_STATS = "stats"
    private const val DAILY_SUMMARY_NOTIF_ID = NotifIds.DAILY_SUMMARY
}
