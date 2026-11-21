package com.phantom.checker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
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
import com.phantom.checker.data.MessageTagCrossRef
import com.phantom.checker.network.AiClient
import com.phantom.checker.ui.MainActivity
import com.phantom.checker.util.PhantomPrefs
import kotlinx.coroutines.Dispatchers
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

        toAnalyze.chunked(5).forEach { chunk ->
            if (!PhantomPrefs.canUseAiTodayAndIncrement(appCtx)) {
                return Result.success()
            }
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
                    val hadScam = applyAnalysisResults(
                        appCtx = appCtx,
                        db = db,
                        results = outcome.results,
                        enableAutoTag = enableAutoTag
                    )
                    if (hadScam) anyScamAlert = true
                }
                is AiClient.BatchAnalysisOutcome.TransientFailure -> {
                    PhantomPrefs.refundTodayAiUsage(appCtx)
                    val ids = chunk.map { it.id }
                    dao.incrementAttempts(ids)
                    anyTransientFailure = true
                }
                is AiClient.BatchAnalysisOutcome.PermanentFailure -> {
                    PhantomPrefs.refundTodayAiUsage(appCtx)
                    val ids = chunk.map { it.id }
                    dao.deleteByIds(ids)
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
    ): Boolean {
        var hasNewScam = false
        results.forEach { (msgId, result) ->
            val msg = db.messageDao().byId(msgId) ?: return@forEach
            db.messageDao().update(
                msg.copy(
                    riskScore = result.riskScore,
                    riskLabel = result.riskLabel,
                    riskReason = result.riskReason
                )
            )
            if (enableAutoTag && result.tagIds.isNotEmpty()) {
                val existingRefs = db.tagDao().crossRefsForMessage(msgId)
                val hasHigherPriorityTag = existingRefs.any {
                    it.source == MessageTagCrossRef.SOURCE_MANUAL ||
                        it.source == MessageTagCrossRef.SOURCE_USER_RULE
                }
                if (!hasHigherPriorityTag) {
                    val refs = result.tagIds.map { tagId ->
                        MessageTagCrossRef(
                            messageId = msgId,
                            tagId = tagId,
                            source = MessageTagCrossRef.SOURCE_AUTO
                        )
                    }
                    db.tagDao().addTagsToMessageBatch(refs)
                }
            }
            if (PhantomPrefs.isAiAutoMemoEnabled(appCtx) && result.memo.isNotBlank()) {
                NotificationCollectorService.saveAutoMemoIfPresent(
                    db = db,
                    msg = msg,
                    rawMemo = result.memo,
                    memoType = result.memoType,
                    todoDue = result.todoDue,
                    ctx = appCtx
                )
            }
            if (result.riskLabel == "scam") {
                hasNewScam = true
                NotificationCollectorService.alertScamFromWorker(
                    ctx = appCtx,
                    msgId = msgId,
                    pkg = msg.packageName,
                    sender = msg.sender,
                    reason = result.riskReason
                )
            }
        }
        return hasNewScam
    }
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
            title = "離線 AI 分析排程中",
            text = "有 $count 則訊息待網路恢復後自動分析"
        )
    }

    fun notifyCompleted(ctx: Context) {
        if (PhantomPrefs.isInQuietHoursNow(ctx)) return
        postLowPriorityNotif(
            ctx = ctx,
            title = "離線 AI 分析已完成",
            text = "待補分析佇列已全數處理完成"
        )
    }

    fun notifyExhausted(ctx: Context, count: Int) {
        if (count <= 0 || PhantomPrefs.isInQuietHoursNow(ctx)) return
        postLowPriorityNotif(
            ctx = ctx,
            title = "部分訊息補分析已略過",
            text = "有 $count 則訊息重試達 3 次上限，已誠實略過"
        )
    }

    private fun postLowPriorityNotif(ctx: Context, title: String, text: String) {
        val appCtx = ctx.applicationContext
        val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "離線 AI 補分析通知", NotificationManager.IMPORTANCE_LOW)
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
        nm.notify(NotifIds.AI_OFFLINE_QUEUE, notif)
    }
}
