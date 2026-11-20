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
 * 定期檢查 NotificationListenerService 存活與重連保護；
 * 開啟常駐統計時（15 分鐘週期）同步刷新常駐通知與 AlarmManager 排程，
 * 關閉常駐統計時（30 分鐘低頻週期）僅做 Listener 重連檢查，不啟動前景服務或精確鬧鐘。
 */
class HeartbeatWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (PhantomPrefs.isPersistentStatsEnabled(ctx) &&
            StatsForegroundService.hasPostNotificationsPermission(ctx)
        ) {
            StatsForegroundService.startOrRefresh(ctx, force = true)
            KeepAlivePolicy.ensureScheduled(ctx)
        }

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
    private const val PREFS_NAME = "heartbeat_policy_prefs"
    private const val KEY_LAST_PERSISTENT_MODE = "last_persistent_mode"

    fun schedule(ctx: Context) {
        val appCtx = ctx.applicationContext
        val workManager = WorkManager.getInstance(appCtx)
        val persistentEnabled = PhantomPrefs.isPersistentStatsEnabled(appCtx)
        val intervalMinutes = if (persistentEnabled) 15L else 30L
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasLastMode = prefs.contains(KEY_LAST_PERSISTENT_MODE)
        val lastMode = prefs.getBoolean(KEY_LAST_PERSISTENT_MODE, persistentEnabled)
        val policy = if (hasLastMode && lastMode != persistentEnabled) {
            ExistingPeriodicWorkPolicy.UPDATE
        } else {
            ExistingPeriodicWorkPolicy.KEEP
        }
        if (!hasLastMode || lastMode != persistentEnabled) {
            prefs.edit().putBoolean(KEY_LAST_PERSISTENT_MODE, persistentEnabled).apply()
        }
        val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(intervalMinutes, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            policy,
            request
        )
    }
}
