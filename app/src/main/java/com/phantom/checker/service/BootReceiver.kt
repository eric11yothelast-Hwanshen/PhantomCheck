package com.phantom.checker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.phantom.checker.R

/**
 * 接收開機完成、快速開機、App 更新完成廣播，恢復所有排程與常駐通知，
 * 若通知存取權限遭系統重設則發出提醒通知。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val appCtx = ctx.applicationContext
        AutoReplyConfig.load(appCtx)
        DailySummaryPolicy.schedule(appCtx)
        RetentionPolicy.scheduleOrUpdate(appCtx)
        KeepAlivePolicy.scheduleAll(appCtx)
        StatsForegroundService.startOrRefresh(appCtx, force = true)

        val listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(appCtx)
            .contains(appCtx.packageName)
        if (!listenerEnabled) {
            val nm = appCtx.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(
                    "system_alert",
                    "系統提醒",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
            val settingsIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                appCtx,
                BOOT_ALERT_NOTIF_ID,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = Notification.Builder(appCtx, "system_alert")
                .setSmallIcon(R.drawable.ic_stat_phantom)
                .setContentTitle("幻訊通知權限需要重新開啟")
                .setContentText("開機後系統可能已重設通知存取權限，點擊前往重新授權。")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            try {
                nm.notify(BOOT_ALERT_NOTIF_ID, notif)
            } catch (_: SecurityException) {
            }
        }
    }

    companion object {
        private const val BOOT_ALERT_NOTIF_ID = 1002
    }
}
