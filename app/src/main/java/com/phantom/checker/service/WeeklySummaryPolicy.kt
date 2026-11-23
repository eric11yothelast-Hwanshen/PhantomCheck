package com.phantom.checker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.phantom.checker.R
import com.phantom.checker.data.AiSummaryEntity
import com.phantom.checker.network.AiClient
import com.phantom.checker.ui.MainActivity
import com.phantom.checker.ui.stripMarkdown
import com.phantom.checker.util.PhantomPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 每週日 22:00 自動生成當週訊息統計與風險趨勢 AI 摘要的排程 Worker。
 * 不受省 token 模式限制，並以排程錨定週計算區間避免延遲跨週失真。
 */
class WeeklySummaryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val anchorMs = inputData.getLong(WeeklySummaryPolicy.KEY_ANCHOR_MS, 0L)
            .takeIf { it > 0L }
            ?: WeeklySummaryPolicy.latestScheduledSundayMillis()
        try {
            if (AiClient.getActiveProfile(applicationContext) != null) {
                WeeklySummaryPolicy.generateWeeklySummary(
                    ctx = applicationContext,
                    fromSchedule = true,
                    anchorMs = anchorMs
                )
            }
        } finally {
            // #246 OneTimeWork 自我重新排程：錨定下一個週日 22:00，避免排程漂移
            WeeklySummaryPolicy.schedule(applicationContext)
        }
        return Result.success()
    }
}

object WeeklySummaryPolicy {
    private const val UNIQUE_WORK_NAME = "phantom_weekly_ai_summary"
    private const val WEEKLY_SUMMARY_NOTIF_ID = NotifIds.WEEKLY_SUMMARY
    const val WEEKLY_KEY_OFFSET = 1_000_000_000L
    internal const val KEY_ANCHOR_MS = "weekly_anchor_ms"

    fun schedule(ctx: Context) {
        val appCtx = ctx.applicationContext
        val workManager = WorkManager.getInstance(appCtx)

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }
        val initialDelayMs = (target.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // OneTimeWorkRequest 指向下一個週日 22:00，執行後由 Worker 自行排下一次
        val request = OneTimeWorkRequestBuilder<WeeklySummaryWorker>()
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setInputData(workDataOf(KEY_ANCHOR_MS to target.timeInMillis))
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** 最近一個已過（或正值）的週日 22:00 排程時刻 */
    fun latestScheduledSundayMillis(nowMs: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis > nowMs) {
                add(Calendar.WEEK_OF_YEAR, -1)
            }
        }
        return cal.timeInMillis
    }

    /**
     * #254 錯過補跑：若最近一次週日 22:00 已過，且該排程錨定週尚無週摘要，立即補跑一次。
     */
    fun maybeCatchUp(ctx: Context) {
        val appCtx = ctx.applicationContext
        val anchorMs = latestScheduledSundayMillis()
        val anchorWeekKey = weekKeyFor(anchorMs)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (AiClient.getActiveProfile(appCtx) == null) return@launch
                val db = AppDatabaseHolder.db(appCtx)
                if (db.aiSummaryDao().byDate(anchorWeekKey) != null) return@launch
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val request = OneTimeWorkRequestBuilder<WeeklySummaryWorker>()
                    .setInitialDelay(3, TimeUnit.SECONDS)
                    .setConstraints(constraints)
                    .setInputData(workDataOf(KEY_ANCHOR_MS to anchorMs))
                    .build()
                WorkManager.getInstance(appCtx).enqueueUniqueWork(
                    UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            } catch (_: Exception) {
            }
        }
    }

    fun startOfWeekMillis(anchorMs: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance().apply {
            timeInMillis = anchorMs
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis > anchorMs) {
                add(Calendar.WEEK_OF_YEAR, -1)
            }
        }.timeInMillis
    }

    fun startOfCurrentWeekMillis(): Long = startOfWeekMillis(System.currentTimeMillis())

    fun weekKeyFor(anchorMs: Long): Long {
        val startMs = startOfWeekMillis(anchorMs)
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        val ymd = fmt.format(Date(startMs)).toLongOrNull() ?: 0L
        return WEEKLY_KEY_OFFSET + ymd
    }

    fun currentWeekKey(): Long = weekKeyFor(System.currentTimeMillis())

    fun formatWeekLabel(dateKey: Long): String {
        val raw = (dateKey - WEEKLY_KEY_OFFSET).coerceAtLeast(0L).toString()
        return if (raw.length == 8) {
            "${raw.substring(0, 4)}/${raw.substring(4, 6)}/${raw.substring(6, 8)} 週"
        } else {
            "週報 #$dateKey"
        }
    }

    suspend fun generateWeeklySummary(
        ctx: Context,
        fromSchedule: Boolean = false,
        anchorMs: Long = System.currentTimeMillis()
    ): Result<AiSummaryEntity> {
        val appCtx = ctx.applicationContext
        AiClient.init(appCtx)
        if (AiClient.getActiveProfile(appCtx) == null) {
            return Result.failure(IllegalStateException("尚未設定 AI Provider API Key"))
        }

        val weekStartMs = startOfWeekMillis(anchorMs)
        val weekEndMs = weekStartMs + TimeUnit.DAYS.toMillis(7)
        val prevWeekStartMs = weekStartMs - TimeUnit.DAYS.toMillis(7)
        val db = AppDatabaseHolder.db(appCtx)
        val weekMessages = db.messageDao().between(weekStartMs, weekEndMs)

        if (weekMessages.isEmpty()) {
            return Result.failure(IllegalStateException("本週尚無訊息可供摘要"))
        }

        val prevWeekScam = db.messageDao().scamCountBetween(prevWeekStartMs, weekStartMs)
        val scamCount = weekMessages.count { it.riskLabel == "scam" }
        val suspiciousCount = weekMessages.count { it.riskLabel == "suspicious" }
        val safeCount = weekMessages.size - scamCount - suspiciousCount
        val unreadCount = weekMessages.count { !it.isRead }

        val topApps = weekMessages
            .groupBy { it.appLabel.ifBlank { it.packageName } }
            .entries
            .sortedByDescending { it.value.size }
            .take(5)
            .joinToString("、") { "${it.key} ${it.value.size} 則" }

        val excerptsBlock = DailySummaryPolicy.buildSummaryExcerpts(weekMessages)

        val scamDelta = scamCount - prevWeekScam
        val trendText = when {
            scamDelta > 0 -> "較上週增加 $scamDelta 則（上週 $prevWeekScam 則）"
            scamDelta < 0 -> "較上週減少 ${-scamDelta} 則（上週 $prevWeekScam 則）"
            else -> "與上週持平（上週 $prevWeekScam 則）"
        }

        val prompt = buildString {
            appendLine("請整理「本週至今」訊息週報，包含：1. 本週訊息量與趨勢、2. 風險分布變化、3. 最活躍 App、4. 高風險訊息重點、5. 詐騙提醒與防範建議。")
            appendLine("本週總訊息數：${weekMessages.size} 則（未讀 $unreadCount 則）")
            appendLine("風險分布：安全 $safeCount 則、疑似 $suspiciousCount 則、詐騙 $scamCount 則（$trendText）")
            appendLine("最活躍 App：$topApps")
            append(excerptsBlock)
        }

        val summaryResult = AiClient.summarizeWeekly(prompt, appCtx)
        val summaryText = summaryResult.getOrElse { err ->
            return Result.failure(err)
        }

        val entity = AiSummaryEntity(
            date = weekKeyFor(anchorMs),
            summary = summaryText,
            generatedAt = System.currentTimeMillis()
        )
        db.aiSummaryDao().insertOrReplace(entity)
        com.phantom.checker.widget.AiSummaryWidgetProvider.updateAllIfChanged(appCtx)

        if (fromSchedule &&
            !PhantomPrefs.isTokenSaverEnabled(appCtx) &&
            StatsForegroundService.hasPostNotificationsPermission(appCtx)
        ) {
            postWeeklyNotification(appCtx, summaryText)
        }

        return Result.success(entity)
    }

    private fun postWeeklyNotification(ctx: Context, summaryText: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                "daily_summary",
                com.phantom.checker.PhantomApplication.str(R.string.notif_weekly_s_1a5e7e),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(DailySummaryPolicy.EXTRA_OPEN_TAB, DailySummaryPolicy.TAB_STATS)
        }
        val pi = PendingIntent.getActivity(
            ctx,
            WEEKLY_SUMMARY_NOTIF_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val plainPreview = stripMarkdown(summaryText)
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(120)
            .ifBlank { com.phantom.checker.PhantomApplication.str(R.string.notif_weekly_s_2ffc1d) }
        val notif = android.app.Notification.Builder(ctx, "daily_summary")
            .setSmallIcon(R.drawable.ic_stat_phantom)
            .setContentTitle(com.phantom.checker.PhantomApplication.str(R.string.notif_weekly_s_55374c))
            .setContentText(plainPreview)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(WEEKLY_SUMMARY_NOTIF_ID, notif)
        } catch (_: SecurityException) {
        }
    }
}
