package com.phantom.checker.service

import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.phantom.checker.util.PhantomPrefs
import java.util.concurrent.TimeUnit

/**
 * 訊息保留政策設定（SharedPreferences）。
 * 預設保留 30 天；設為 RETENTION_FOREVER (-1) 表示永久保留。
 */
object RetentionConfig {
    private const val PREFS_NAME = "retention_prefs"
    private const val KEY_RETENTION_DAYS = "retention_days"

    const val DEFAULT_RETENTION_DAYS = 30
    const val RETENTION_FOREVER = -1

    val OPTIONS = listOf(7, 30, 90, 365, RETENTION_FOREVER)
    val MAX_COUNT_OPTIONS = listOf(1000, 5000, -1)

    fun getRetentionDays(ctx: Context): Int {
        val prefs = ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS)
    }

    fun setRetentionDays(ctx: Context, days: Int) {
        ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_RETENTION_DAYS, days)
            .apply()
    }

    fun labelForDays(days: Int): String = when {
        days <= 0 -> "永久"
        else -> "$days 天"
    }

    fun labelForMaxCount(count: Int): String = when {
        count <= 0 -> "不限"
        else -> "$count 則"
    }
}

/**
 * 每日定期清理超過保留天數或則數上限的舊訊息，並保留 riskLabel = 'scam' 的詐騙證據。
 */
class RetentionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val days = RetentionConfig.getRetentionDays(applicationContext)
        val maxCount = PhantomPrefs.getRetentionMaxCount(applicationContext)
        if (days <= 0 && maxCount <= 0) {
            return Result.success()
        }

        val db = AppDatabaseHolder.db(applicationContext)
        val dao = db.messageDao()
        val tagDao = db.tagDao()

        db.withTransaction {
            if (days > 0) {
                val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
                dao.prune(cutoff)
            }
            if (maxCount > 0) {
                dao.deleteOlderThanCount(maxCount)
            }
            tagDao.deleteOrphanCrossRefs()
        }

        return Result.success()
    }
}

/**
 * 排程或更新每日訊息保留清理任務。
 */
object RetentionPolicy {
    private const val UNIQUE_WORK_NAME = "phantom_message_retention"

    fun scheduleOrUpdate(ctx: Context) {
        val appCtx = ctx.applicationContext
        val workManager = WorkManager.getInstance(appCtx)
        val days = RetentionConfig.getRetentionDays(appCtx)
        val maxCount = PhantomPrefs.getRetentionMaxCount(appCtx)

        if (days <= 0 && maxCount <= 0) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val request = PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
