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
import com.phantom.checker.ui.MainActivity
import com.phantom.checker.ui.MessageDetailActivity
import com.phantom.checker.widget.ReminderWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 訊息與待辦提醒排程器（使用 AlarmManager.setAndAllowWhileIdle）。
 */
object ReminderScheduler {
    const val CHANNEL_ID = "phantom_message_reminders"
    const val EXTRA_MESSAGE_ID = "extra_reminder_message_id"
    const val EXTRA_TODO_ID = "extra_reminder_todo_id"
    private const val ACTION_TODO_REMINDER = "com.phantom.checker.ACTION_TODO_REMINDER"

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

    fun scheduleTodo(ctx: Context, todoId: Long, triggerAtMillis: Long) {
        val appCtx = ctx.applicationContext
        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = buildTodoPendingIntent(appCtx, todoId)
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    fun cancelTodo(ctx: Context, todoId: Long) {
        val appCtx = ctx.applicationContext
        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = buildTodoPendingIntent(appCtx, todoId)
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

    private fun buildTodoPendingIntent(ctx: Context, todoId: Long): PendingIntent {
        val intent = Intent(ctx, ReminderReceiver::class.java).apply {
            action = ACTION_TODO_REMINDER
            putExtra(EXTRA_TODO_ID, todoId)
        }
        val reqCode = 0x40000000 xor (todoId xor (todoId ushr 32)).toInt()
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
                    description = "「稍後處理」與 AI 待辦排程的提醒通知"
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
        ReminderWidgetProvider.updateAllIfChanged(appCtx)
    }

    suspend fun deliverTodoReminder(ctx: Context, todoId: Long) {
        val appCtx = ctx.applicationContext
        val todoDao = AppDatabaseHolder.db(appCtx).todoDao()
        val todo = todoDao.byId(todoId) ?: return
        if (todo.isDone) return
        ensureChannel(appCtx)
        val openIntent = if (todo.relatedMessageId != null) {
            MessageDetailActivity.createIntent(appCtx, todo.relatedMessageId).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            Intent(appCtx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
        val reqCode = 0x40000000 xor (todoId xor (todoId ushr 32)).toInt()
        val pi = PendingIntent.getActivity(
            appCtx,
            reqCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = Notification.Builder(appCtx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_phantom)
            .setContentTitle("⏰ 待辦提醒")
            .setContentText(todo.title.take(120))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        val nm = appCtx.getSystemService(NotificationManager::class.java)
        try {
            nm?.notify(NotifIds.todoReminder(todoId), notif)
        } catch (_: SecurityException) {
        }
        ReminderWidgetProvider.updateAllIfChanged(appCtx)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val todoId = intent.getLongExtra(ReminderScheduler.EXTRA_TODO_ID, -1L)
        val messageId = intent.getLongExtra(ReminderScheduler.EXTRA_MESSAGE_ID, -1L)
        if (todoId <= 0L && messageId <= 0L) return
        val appCtx = ctx.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (todoId > 0L) {
                    ReminderScheduler.deliverTodoReminder(appCtx, todoId)
                } else {
                    ReminderScheduler.deliverReminder(appCtx, messageId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
