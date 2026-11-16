package com.phantom.checker.service

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import com.phantom.checker.util.PhantomPrefs

/**
 * 接收 AlarmManager Watchdog、8 小時安全網、服務自救、與精確鬧鐘權限變更廣播，
 * 檢查常駐統計通知與 NotificationListenerService 存活狀態並自動自癒。
 */
class WatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val appCtx = ctx.applicationContext
        val action = intent.action

        if (action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            if (PhantomPrefs.isPersistentStatsEnabled(appCtx)) {
                KeepAlivePolicy.scheduleWatchdog(appCtx)
                KeepAlivePolicy.scheduleAlarmClockSafetyNet(appCtx)
            }
            return
        }

        if (action == KeepAlivePolicy.ACTION_REBIND_FALLBACK) {
            tryRequestListenerRebind(appCtx)
        }

        if (!PhantomPrefs.isPersistentStatsEnabled(appCtx) ||
            !StatsForegroundService.hasPostNotificationsPermission(appCtx)
        ) {
            KeepAlivePolicy.cancelAll(appCtx)
            return
        }

        val nm = appCtx.getSystemService(NotificationManager::class.java)
        val hasStatsNotif = nm?.activeNotifications?.any {
            it.id == StatsForegroundService.STATS_NOTIF_ID
        } == true

        val shouldRefresh = !hasStatsNotif ||
            action == KeepAlivePolicy.ACTION_RESCUE ||
            action == KeepAlivePolicy.ACTION_SAFETY_NET ||
            StatsForegroundService.isRefreshStale()

        if (shouldRefresh) {
            StatsForegroundService.startOrRefresh(appCtx, force = true)
        }

        tryRequestListenerRebind(appCtx)
        HeartbeatPolicy.schedule(appCtx)

        when (action) {
            KeepAlivePolicy.ACTION_SAFETY_NET -> KeepAlivePolicy.scheduleAlarmClockSafetyNet(appCtx)
            else -> KeepAlivePolicy.scheduleWatchdog(appCtx)
        }
    }

    private fun tryRequestListenerRebind(ctx: Context) {
        if (NotificationCollectorService.isConnected) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        if (!enabled) return
        try {
            NotificationListenerService.requestRebind(
                ComponentName(ctx, NotificationCollectorService::class.java)
            )
        } catch (_: Exception) {
        }
    }
}
