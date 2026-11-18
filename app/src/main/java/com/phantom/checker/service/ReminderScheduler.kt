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
        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (am != null) {
            val pi = buildPendingIntent(appCtx, messageId)
            am.cancel(pi)
            pi.cancel()
        }
        try {
            appCtx.getSystemService(NotificationManager::class.java)
                ?.cancel(NotifIds.reminder(messageId))
        } catch (_: Exception) {
        }
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
        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (am != null) {
            val pi = buildTodoPendingIntent(appCtx, todoId)
            am.cancel(pi)
            pi.cancel()
        }
        try {
            appCtx.getSystemService(NotificationManager::class.java)
                ?.cancel(NotifIds.todoReminder(todoId))
        } catch (_: Exception) {
        }
    }

    /**
     * 切換待辦完成狀態：
     * - 寫入 isDone 與 completedAt（取消勾選時清空）
     * - 若勾選完成且設為每日/每週重複提醒，自動派生下一筆待辦並排程提醒
     * - 同步刷新桌面小工具
     */
    suspend fun completeTodoWithRecurrence(
        ctx: Context,
        todo: com.phantom.checker.data.TodoEntity,
        done: Boolean
    ): com.phantom.checker.data.TodoEntity? {
        val appCtx = ctx.applicationContext
        val db = AppDatabaseHolder.db(appCtx)
        val now = System.currentTimeMillis()
        db.todoDao().setDone(todo.id, done, if (done) now else null)
        var spawned: com.phantom.checker.data.TodoEntity? = null
        if (done) {
            cancelTodo(appCtx, todo.id)
            val stepMs = when (todo.repeatRule) {
                com.phantom.checker.data.TodoEntity.REPEAT_DAILY -> 24L * 60L * 60L * 1000L
                com.phantom.checker.data.TodoEntity.REPEAT_WEEKLY -> 7L * 24L * 60L * 60L * 1000L
                else -> 0L
            }
            if (stepMs > 0L) {
                var nextDue = todo.dueAt + stepMs
                while (nextDue <= now) {
                    nextDue += stepMs
                }
                val nextTodo = com.phantom.checker.data.TodoEntity(
                    title = todo.title,
                    dueAt = nextDue,
                    isDone = false,
                    createdAt = now,
                    relatedMessageId = todo.relatedMessageId,
                    sourceSessionId = todo.sourceSessionId,
                    priority = todo.priority,
                    notes = todo.notes,
                    repeatRule = todo.repeatRule
                )
                val newId = db.todoDao().insert(nextTodo)
                spawned = nextTodo.copy(id = newId)
                scheduleTodo(appCtx, newId, nextDue)
            }
        } else if (todo.dueAt > now) {
            scheduleTodo(appCtx, todo.id, todo.dueAt)
        }
        ReminderWidgetProvider.updateAllIfChanged(appCtx)
        return spawned
    }

    /** 智慧排序：未完成優先（逾期 > 到期日早→晚 > 優先級高→低），已完成依完成時間新→舊 */
    fun sortTodosSmart(
        list: List<com.phantom.checker.data.TodoEntity>,
        now: Long = System.currentTimeMillis()
    ): List<com.phantom.checker.data.TodoEntity> = list.sortedWith(
        compareBy<com.phantom.checker.data.TodoEntity> { it.isDone }
            .thenByDescending { !it.isDone && it.dueAt < now }
            .thenBy { if (!it.isDone) it.dueAt else Long.MAX_VALUE }
            .thenByDescending { if (!it.isDone) it.priority else 0 }
            .thenByDescending { it.completedAt ?: 0L }
            .thenByDescending { it.id }
    )

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
