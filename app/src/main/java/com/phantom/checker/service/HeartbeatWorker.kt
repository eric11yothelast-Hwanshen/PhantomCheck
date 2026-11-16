package com.phantom.checker.service

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.phantom.checker.util.PhantomPrefs
import java.util.concurrent.TimeUnit

/**
 * 每 15 分鐘強制刷新常駐統計通知，並檢查 NotificationListenerService 與 AlarmManager 排程狀態。
 */
class HeartbeatWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!PhantomPrefs.isPersistentStatsEnabled(ctx) ||
            !StatsForegroundService.hasPostNotificationsPermission(ctx)
        ) {
            return Result.success()
        }
        StatsForegroundService.startOrRefresh(ctx, force = true)
        KeepAlivePolicy.ensureScheduled(ctx)

        val listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(ctx)
            .contains(ctx.packageName)
        if (!NotificationCollectorService.isConnected &&
            listenerEnabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            try {
                NotificationListenerService.requestRebind(
                    ComponentName(ctx, NotificationCollectorService::class.java)
                )
            } catch (_: Exception) {
            }
        }
        return Result.success()
    }
}

object HeartbeatPolicy {
    private const val UNIQUE_WORK_NAME = "phantom_stats_heartbeat"

    fun schedule(ctx: Context) {
        val appCtx = ctx.applicationContext
        val workManager = WorkManager.getInstance(appCtx)
        if (!PhantomPrefs.isPersistentStatsEnabled(appCtx)) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
