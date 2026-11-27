package com.phantom.checker.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.phantom.checker.R
import com.phantom.checker.data.AppDatabase
import com.phantom.checker.network.AiClient
import com.phantom.checker.ui.MainActivity
import com.phantom.checker.util.PhantomPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class PendingAiAnalysisWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val appCtx = applicationContext
        if (!PhantomPrefs.isAiOfflineRetryEnabled(appCtx) ||
            PhantomPrefs.isAiOfflineRetryPaused(appCtx) ||
            !PhantomPrefs.isAiRiskEnabled(appCtx)
        ) {
            return Result.success()
        }

        AiClient.init(appCtx)
        val db = AppDatabaseHolder.db(appCtx)
        val dao = db.pendingAiAnalysisDao()
        val queued = dao.nextBatch(limit = 20)
        if (queued.isEmpty()) {
            return Result.success()
        }

        val messages = queued.mapNotNull { entry ->
            val msg = db.messageDao().byId(entry.messageId)
            if (msg == null) {
                dao.deleteByIds(listOf(entry.messageId))
            }
            msg
        }
        if (messages.isEmpty()) {
            if (dao.pendingCount() == 0) {
                PendingAiAnalysisPolicy.notifyCompleted(appCtx)
            }
            return Result.success()
        }

        val enableAutoTag = PhantomPrefs.isEffectiveAiAutoTagEnabled(appCtx)
        val aiTags = if (enableAutoTag) db.tagDao().aiManagedTags() else emptyList()
        val aiRules = db.aiRuleDao().active()
        val allWhitelistRules = db.aiRuleDao().activeWhitelistAll()
        val memoRules = db.memoRuleDao().active().take(10)

        val toAnalyze = mutableListOf<com.phantom.checker.data.MessageEntity>()
        messages.forEach { msg ->
            if (com.phantom.checker.network.IntelClient.isMessageWhitelisted(
                    sender = msg.sender,
                    packageName = msg.packageName,
                    appLabel = msg.appLabel,
                    content = msg.content,
                    aiRules = allWhitelistRules,
                    indicators = com.phantom.checker.network.IndicatorExtractor.extract(msg.content)
                )
            ) {
                db.messageDao().update(
                    msg.copy(riskScore = 10, riskLabel = "safe", riskReason = "命中白名單規則")
                )
                dao.deleteByIds(listOf(msg.id))
            } else {
                toAnalyze.add(msg)
            }
        }
        if (toAnalyze.isEmpty()) {
            if (dao.pendingCount() == 0) {
                PendingAiAnalysisPolicy.notifyCompleted(appCtx)
            }
            return Result.success()
        }

        var anyTransientFailure = false
        var anyScamAlert = false
        var anyResultsApplied = false

        for (chunk in toAnalyze.chunked(5)) {
            if (!PhantomPrefs.canUseAiTodayAndIncrement(appCtx)) {
                break
            }
            var shouldRefundQuota = true
            try {
                val batchItems = chunk.map { msg ->
                    AiClient.BatchAnalyzeItem(
                        messageId = msg.id,
                        sender = msg.sender,
                        content = msg.content,
                        packageName = msg.packageName,
                        appLabel = msg.appLabel,
                        timestampMs = msg.timestamp
                    )
                }
                when (
                    val outcome = AiClient.analyzeMessageBatchDetailed(
                        items = batchItems,
                        tags = aiTags,
                        enableAutoTag = enableAutoTag,
                        context = appCtx,
                        rules = aiRules,
                        memoRules = memoRules
                    )
                ) {
                    is AiClient.BatchAnalysisOutcome.Success -> {
                        val ids = chunk.map { it.id }
                        dao.deleteByIds(ids)
                        if (outcome.results.isNotEmpty()) {
                            shouldRefundQuota = false
                            anyResultsApplied = true
                            val hadScam = applyAnalysisResults(
                                appCtx = appCtx,
                                db = db,
                                results = outcome.results,
                                enableAutoTag = enableAutoTag
                            )
                            if (hadScam) anyScamAlert = true
                        }
                        // outcome.results 為空時維持 shouldRefundQuota = true，於 finally 退還今日額度
                    }
                    is AiClient.BatchAnalysisOutcome.TransientFailure -> {
                        val ids = chunk.map { it.id }
                        dao.incrementAttempts(ids)
                        anyTransientFailure = true
                    }
                    is AiClient.BatchAnalysisOutcome.PermanentFailure -> {
                        val ids = chunk.map { it.id }
                        dao.deleteByIds(ids)
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e("PendingAiAnalysis", "Chunk analysis failed size=${chunk.size}", e)
                val ids = chunk.map { it.id }
                runCatching { dao.incrementAttempts(ids) }
                anyTransientFailure = true
            } finally {
                if (shouldRefundQuota) {
                    withContext(NonCancellable) {
                        PhantomPrefs.refundTodayAiUsage(appCtx)
                    }
                }
            }
        }

        val exhausted = dao.exhaustedItems()
        if (exhausted.isNotEmpty()) {
            dao.deleteExhausted()
            PendingAiAnalysisPolicy.notifyExhausted(appCtx, exhausted.size)
        }

        if (anyScamAlert) {
            StatsForegroundService.startOrRefresh(appCtx, force = true)
        }
        if (anyResultsApplied || anyScamAlert) {
            com.phantom.checker.widget.WidgetUpdater.scheduleRefresh(appCtx)
        }

        val remaining = dao.pendingCount()
        if (remaining == 0 && !anyTransientFailure) {
            PendingAiAnalysisPolicy.notifyCompleted(appCtx)
            return Result.success()
        }

        if (anyTransientFailure && remaining > 0) {
            return Result.retry()
        }

        if (remaining > 0) {
            PendingAiAnalysisPolicy.enqueue(appCtx)
        }
        return Result.success()
    }

    private suspend fun applyAnalysisResults(
        appCtx: Context,
        db: AppDatabase,
        results: Map<Long, AiClient.AiAnalysisResult>,
        enableAutoTag: Boolean
    ): Boolean = NotificationCollectorService.applyAiAnalysisResults(
        appCtx = appCtx,
        db = db,
        results = results,
        enableAutoTag = enableAutoTag,
        alertScamCallback = NotificationCollectorService::alertScamFromWorker
    )
}

object PendingAiAnalysisPolicy {
    const val UNIQUE_WORK_NAME = "ai_pending_analysis_worker"
    private const val CHANNEL_ID = "ai_offline_queue"

    fun enqueue(ctx: Context) {
        val appCtx = ctx.applicationContext
        if (!PhantomPrefs.isAiOfflineRetryEnabled(appCtx) ||
            PhantomPrefs.isAiOfflineRetryPaused(appCtx)
        ) {
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<PendingAiAnalysisWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appCtx).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun retryNow(ctx: Context) {
        val appCtx = ctx.applicationContext
        if (!PhantomPrefs.isAiOfflineRetryEnabled(appCtx)) return
        PhantomPrefs.setAiOfflineRetryPaused(appCtx, false)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<PendingAiAnalysisWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appCtx).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun pause(ctx: Context) {
        val appCtx = ctx.applicationContext
        PhantomPrefs.setAiOfflineRetryPaused(appCtx, true)
        WorkManager.getInstance(appCtx).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    fun resume(ctx: Context) {
        val appCtx = ctx.applicationContext
        PhantomPrefs.setAiOfflineRetryPaused(appCtx, false)
        enqueue(appCtx)
    }

    suspend fun disableAndClear(ctx: Context) {
        val appCtx = ctx.applicationContext
        PhantomPrefs.setAiOfflineRetryPaused(appCtx, false)
        WorkManager.getInstance(appCtx).cancelUniqueWork(UNIQUE_WORK_NAME)
        withContext(Dispatchers.IO) {
            AppDatabaseHolder.db(appCtx).pendingAiAnalysisDao().clearAll()
        }
    }

    fun notifyQueued(ctx: Context, count: Int) {
        if (count <= 0 || PhantomPrefs.isInQuietHoursNow(ctx)) return
        postLowPriorityNotif(
            ctx = ctx,
            title = com.phantom.checker.PhantomApplication.str(R.string.notif_pending_ai_s_89e940),
            text = com.phantom.checker.PhantomApplication.str(R.string.notif_pending_ai_s_2ff01b, count)
        )
    }

    fun notifyCompleted(ctx: Context) {
        if (PhantomPrefs.isInQuietHoursNow(ctx)) return
        postLowPriorityNotif(
            ctx = ctx,
            title = com.phantom.checker.PhantomApplication.str(R.string.notif_pending_ai_s_decac3),
            text = com.phantom.checker.PhantomApplication.str(R.string.notif_pending_ai_s_692a1b)
        )
    }

    fun notifyExhausted(ctx: Context, count: Int) {
        if (count <= 0 || PhantomPrefs.isInQuietHoursNow(ctx)) return
        postLowPriorityNotif(
            ctx = ctx,
            title = com.phantom.checker.PhantomApplication.str(R.string.notif_pending_ai_s_573d85),
            text = com.phantom.checker.PhantomApplication.str(R.string.notif_pending_ai_s_dc60b7, count)
        )
    }

    private fun postLowPriorityNotif(ctx: Context, title: String, text: String) {
        val appCtx = ctx.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                appCtx,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, com.phantom.checker.PhantomApplication.str(R.string.notif_pending_ai_s_fd8b2e), NotificationManager.IMPORTANCE_LOW)
        )
        val intent = Intent(appCtx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            appCtx,
            NotifIds.AI_OFFLINE_QUEUE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(appCtx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_phantom)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            nm.notify(NotifIds.AI_OFFLINE_QUEUE, notif)
        } catch (e: SecurityException) {
            Log.w("PendingAiAnalysis", "postLowPriorityNotif SecurityException", e)
        }
    }
}
