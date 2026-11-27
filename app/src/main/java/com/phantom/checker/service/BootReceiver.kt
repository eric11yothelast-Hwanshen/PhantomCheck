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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 接收開機完成、快速開機、App 更新完成廣播，於背景非同步恢復所有排程與常駐通知，
 * 若通知存取權限遭系統重設則發出提醒通知。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action !in ALLOWED_ACTIONS) return
        val appCtx = ctx.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 14 項排程與服務初始化全部移至背景執行緒，避免主執行緒 ANR
                runCatching { AutoReplyConfig.load(appCtx) }
                runCatching { DailySummaryPolicy.schedule(appCtx) }
                runCatching { WeeklySummaryPolicy.schedule(appCtx) }
                runCatching { WeeklySummaryPolicy.maybeCatchUp(appCtx) }
                runCatching { BriefingScheduler.rescheduleAll(appCtx) }
                runCatching { BriefingScheduler.maybeCatchUp(appCtx) }
                runCatching { DiaryPolicy.schedule(appCtx) }
                runCatching { DiaryPolicy.maybeCatchUp(appCtx) }
                runCatching { CalendarInsightScheduler.scheduleOrUpdate(appCtx) }
                runCatching { WatchTopicEngine.syncSchedule(appCtx) }
                runCatching { ProactiveIntelScheduler.syncSchedule(appCtx) }
                runCatching { RetentionPolicy.scheduleOrUpdate(appCtx) }
                runCatching { KeepAlivePolicy.scheduleAll(appCtx) }
                runCatching { StatsForegroundService.startOrRefresh(appCtx, force = true) }

                val now = System.currentTimeMillis()
                val catchUpFloorMs = now - 24L * 60L * 60L * 1000L
                val db = AppDatabaseHolder.db(appCtx)
                val scheduled = db.messageDao().allScheduledReminders()
                scheduled.forEach { msg ->
                    val ts = msg.remindAt
                    if (ts != null) {
                        if (ts > now) {
                            ReminderScheduler.schedule(appCtx, msg.id, ts)
                        } else if (ts >= catchUpFloorMs) {
                            ReminderScheduler.deliverReminder(appCtx, msg.id)
                        }
                    }
                }
                val pendingTodos = db.todoDao().pendingTodos()
                val dayKey = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                pendingTodos.forEach { todo ->
                    if (todo.dueAt > now) {
                        ReminderScheduler.scheduleTodo(appCtx, todo.id, todo.dueAt)
                    } else if (todo.dueAt >= catchUpFloorMs) {
                        val bootKey = "boot_todo:${todo.id}:$dayKey"
                        if (!com.phantom.checker.util.PhantomPrefs.isProactiveMilestoneDelivered(appCtx, bootKey, dayKey)) {
                            com.phantom.checker.util.PhantomPrefs.markProactiveMilestonesDelivered(appCtx, listOf(bootKey), dayKey)
                            ReminderScheduler.deliverTodoReminder(appCtx, todo.id)
                        }
                    }
                }
                TaskTemplateScheduler.rescheduleAllEnabled(appCtx)

                notifyIfListenerDisabled(appCtx)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("BootReceiver", "boot init failed: ${e.javaClass.simpleName}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun notifyIfListenerDisabled(appCtx: Context) {
        val listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(appCtx)
            .contains(appCtx.packageName)
        if (listenerEnabled) return
        val nm = appCtx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                "system_alert",
                com.phantom.checker.PhantomApplication.str(R.string.notif_boot_s_7687ab),
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
            .setContentTitle(com.phantom.checker.PhantomApplication.str(R.string.notif_boot_s_12efcf))
            .setContentText(com.phantom.checker.PhantomApplication.str(R.string.notif_boot_s_8dca8c))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(BOOT_ALERT_NOTIF_ID, notif)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        private const val BOOT_ALERT_NOTIF_ID = NotifIds.BOOT_ALERT
        private val ALLOWED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
    }
}
