package com.phantom.checker.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.phantom.checker.R
import com.phantom.checker.ui.MessageDetailActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 訊息待辦提醒排程器（使用 AlarmManager.setAndAllowWhileIdle）。
 */
object ReminderScheduler {
    const val CHANNEL_ID = "phantom_message_reminders"
    const val EXTRA_MESSAGE_ID = "extra_reminder_message_id"

    fun schedule(ctx: Context, messageId: Long, triggerAtMillis: Long) {
        val appCtx = ctx.applicationContext
        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = buildPendingIntent(appCtx, messageId)
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    fun cancel(ctx: Context, messageId: Long) {
        val appCtx = ctx.applicationContext
        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = buildPendingIntent(appCtx, messageId)
        am.cancel(pi)
        pi.cancel()
    }

    private fun buildPendingIntent(ctx: Context, messageId: Long): PendingIntent {
        val intent = Intent(ctx, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_MESSAGE_ID, messageId)
        }
        val reqCode = (messageId xor (messageId ushr 32)).toInt()
        return PendingIntent.getBroadcast(
            ctx,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "訊息待辦提醒",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "「稍後處理」排程的訊息提醒通知"
                }
            )
        }
    }

    suspend fun deliverReminder(ctx: Context, messageId: Long) {
        val appCtx = ctx.applicationContext
        val dao = AppDatabaseHolder.db(appCtx).messageDao()
        val msg = dao.byId(messageId) ?: return
        dao.setRemindAt(messageId, null)
        ensureChannel(appCtx)
        val openIntent = MessageDetailActivity.createIntent(appCtx, messageId).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val reqCode = (messageId xor (messageId ushr 32)).toInt()
        val pi = PendingIntent.getActivity(
            appCtx,
            reqCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = "待辦提醒：${msg.sender}（${msg.appLabel}）"
        val notif = Notification.Builder(appCtx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_phantom)
            .setContentTitle(title)
            .setContentText(msg.content.take(120))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        val nm = appCtx.getSystemService(NotificationManager::class.java)
        try {
            nm?.notify(NotifIds.reminder(messageId), notif)
        } catch (_: SecurityException) {
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val messageId = intent.getLongExtra(ReminderScheduler.EXTRA_MESSAGE_ID, -1L)
        if (messageId <= 0L) return
        val appCtx = ctx.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.deliverReminder(appCtx, messageId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
