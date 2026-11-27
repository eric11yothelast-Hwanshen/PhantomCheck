package com.phantom.checker.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.phantom.checker.data.AiSummaryEntity
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.network.AiClient
import com.phantom.checker.util.PhantomPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.phantom.checker.R

/**
 * 每日自動生成當日訊息 AI 摘要的排程 Worker。
 */
class DailySummaryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // #349 每日 AI 摘要已由早晚簡報取代，取消殘留排程
        WorkManager.getInstance(applicationContext).cancelUniqueWork("phantom_daily_ai_summary")
        return Result.success()
    }
}

object DailySummaryPolicy {
    private const val UNIQUE_WORK_NAME = "phantom_daily_ai_summary"

    fun schedule(ctx: Context) {
        WorkManager.getInstance(ctx.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    fun maybeCatchUp(ctx: Context) {
        // #349 每日 AI 摘要已移除，不再補跑
    }

    private fun isPastScheduledTimeToday(appCtx: Context): Boolean {
        val hour = PhantomPrefs.getDailySummaryHour(appCtx)
        val minute = PhantomPrefs.getDailySummaryMinute(appCtx)
        val now = Calendar.getInstance()
        val todayTarget = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return !now.before(todayTarget)
    }

    /** 計算距離下一個排程時刻（今日 HH:mm，已過則明日同時刻）的毫秒數 */
    fun delayMillisUntilNext(appCtx: Context): Long {
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
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)
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
        return Result.failure(IllegalStateException("每日 AI 摘要已由早晚簡報取代"))
    }

    internal fun buildSummaryExcerpts(
        messages: List<MessageEntity>,
        maxChars: Int = 2400
    ): String {
        val riskyBuckets = com.phantom.checker.util.NotificationFoldUtil.fold(
            messages.filter {
                it.riskLabel == "scam" || it.riskLabel == "warning" || it.riskLabel == "suspicious"
            }
        ).take(20)
        val unanalyzedBuckets = com.phantom.checker.util.NotificationFoldUtil.fold(
            messages.filter { it.riskLabel.isBlank() }
        ).take(10)

        return buildString {
            var remaining = maxChars
            var truncated = false

            fun appendSection(
                header: String,
                buckets: List<com.phantom.checker.util.FoldedMessage>,
                tagResolver: (MessageEntity) -> String
            ) {
                if (buckets.isEmpty() || remaining <= header.length + 16) return
                appendLine(header)
                remaining -= header.length + 1
                buckets.forEachIndexed { idx, bucket ->
                    if (truncated) return@forEachIndexed
                    val m = bucket.representative
                    val tag = tagResolver(m)
                    val line = if (bucket.isFolded) {
                        "${idx + 1}. [$tag]${com.phantom.checker.util.NotificationFoldUtil.formatFoldedHeadline(bucket)}"
                    } else {
                        val snippet = m.content.replace("\n", " ").trim().take(90)
                        "${idx + 1}. [$tag][${m.appLabel}] ${m.sender}: $snippet"
                    }
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
                    when (it.riskLabel) {
                        "scam" -> "詐騙"
                        "warning" -> "警示"
                        else -> "疑似"
                    }
                }
            } else {
                appendLine("無高風險（scam/warning/suspicious）訊息。")
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
                com.phantom.checker.PhantomApplication.str(R.string.notif_daily_s_a3d0ed),
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
            .ifBlank { com.phantom.checker.PhantomApplication.str(R.string.notif_daily_s_88f2a5) }
        val notif = android.app.Notification.Builder(ctx, "daily_summary")
            .setSmallIcon(com.phantom.checker.R.drawable.ic_stat_phantom)
            .setContentTitle(com.phantom.checker.PhantomApplication.str(R.string.notif_daily_s_bd12ae))
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
