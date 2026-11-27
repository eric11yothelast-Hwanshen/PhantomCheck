package com.phantom.checker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.phantom.checker.R
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.service.DailySummaryPolicy
import com.phantom.checker.service.ReminderScheduler
import com.phantom.checker.ui.MainActivity
import com.phantom.checker.ui.MessageDetailActivity
import com.phantom.checker.util.AppLocaleHelper
import com.phantom.checker.util.PhantomPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 桌面 2x1「待辦提醒」小工具：
 * 顯示最近 1-2 則待辦提醒（支援逾期標示與智能排序），並提供逐列完成／編輯／刪除互動按鈕。
 */
class ReminderWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action != ACTION_COMPLETE_ITEM && action != ACTION_DELETE_ITEM) return
        val itemType = intent.getStringExtra(EXTRA_ITEM_TYPE) ?: return
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
        if (itemId <= 0L) return

        val appCtx = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                kotlinx.coroutines.withTimeout(8_500L) {
                    val db = AppDatabaseHolder.db(appCtx)
                    when (action) {
                        ACTION_COMPLETE_ITEM -> {
                            if (itemType == TYPE_TODO) {
                                val todo = db.todoDao().byId(itemId)
                                if (todo != null && !todo.isDone) {
                                    ReminderScheduler.completeTodoWithRecurrence(appCtx, todo, true)
                                }
                            }
                        }
                        ACTION_DELETE_ITEM -> {
                            if (itemType == TYPE_TODO) {
                                db.todoDao().deleteById(itemId)
                                ReminderScheduler.cancelTodo(appCtx, itemId)
                            } else if (itemType == TYPE_MSG) {
                                db.messageDao().setRemindAt(itemId, null)
                                ReminderScheduler.cancel(appCtx, itemId)
                            }
                        }
                    }
                    updateAllIfChanged(appCtx)
                    if (itemType == TYPE_TODO) {
                        runCatching { TodoTodayWidgetProvider.refreshNow(appCtx) }
                        runCatching { GoalProgressWidgetProvider.refreshNow(appCtx) }
                    }
                }
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        val appCtx = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                kotlinx.coroutines.withTimeout(8_500L) {
                    val now = System.currentTimeMillis()
                    val items = loadUpcomingItems(appCtx, now)
                    lastSnapshot = computeSnapshotKey(appCtx, items)
                    val views = buildRemoteViews(appCtx, items)
                    appWidgetManager.updateAppWidget(appWidgetIds, views)
                }
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val mgr = AppWidgetManager.getInstance(context)
        val remaining = mgr.getAppWidgetIds(ComponentName(context, ReminderWidgetProvider::class.java))
        if (remaining.isEmpty()) {
            lastSnapshot = null
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        lastSnapshot = null
    }

    companion object {
        const val ACTION_COMPLETE_ITEM = "com.phantom.checker.widget.ACTION_REMINDER_COMPLETE"
        const val ACTION_DELETE_ITEM = "com.phantom.checker.widget.ACTION_REMINDER_DELETE"
        const val EXTRA_ITEM_TYPE = "extra_item_type"
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_EDIT_TODO_ID = "extra_edit_todo_id"
        const val TAB_NOTES = "notes"
        private const val TYPE_TODO = "todo"
        private const val TYPE_MSG = "msg"
        private const val WIDGET_CLICK_REQUEST_CODE = 2008

        private data class UpcomingReminderItem(
            val snapshotKey: String,
            val dueAt: Long,
            val summary: String,
            val itemType: String,
            val itemId: Long,
            val todoId: Long? = null,
            val messageId: Long? = null
        )

        @Volatile
        private var lastSnapshot: String? = null

        private suspend fun loadUpcomingItems(ctx: Context, now: Long): List<UpcomingReminderItem> {
            val db = AppDatabaseHolder.db(ctx)
            val msgItems = db.messageDao().pendingReminders(now)
                .filter { (it.remindAt ?: 0L) >= now }
                .map { m ->
                    val title = m.sender.ifBlank { m.appLabel }
                    val snippet = m.content.replace("\n", " ").trim().take(18)
                    val summary = if (snippet.isNotEmpty()) "$title：$snippet" else title
                    UpcomingReminderItem(
                        snapshotKey = "m:${m.id}:${m.remindAt}",
                        dueAt = m.remindAt ?: Long.MAX_VALUE,
                        summary = summary,
                        itemType = TYPE_MSG,
                        itemId = m.id,
                        messageId = m.id
                    )
                }
            val todoItems = ReminderScheduler.sortTodosSmart(
                db.todoDao().pendingTodos().filter { !it.isDone },
                now
            ).map { t ->
                val overdueTag = if (t.dueAt < now) com.phantom.checker.PhantomApplication.str(R.string.widget_reminder_s_af6faa) else ""
                UpcomingReminderItem(
                    snapshotKey = "t:${t.id}:${t.dueAt}:${t.priority}:${t.isDone}",
                    dueAt = t.dueAt,
                    summary = "$overdueTag${t.title.replace("\n", " ").trim().take(20)}",
                    itemType = TYPE_TODO,
                    itemId = t.id,
                    todoId = t.id,
                    messageId = t.relatedMessageId
                )
            }
            return (todoItems + msgItems)
                .sortedWith(
                    compareBy<UpcomingReminderItem> { if (it.dueAt < now) 0 else 1 }
                        .thenBy { it.dueAt }
                )
                .take(2)
        }

        private fun computeSnapshotKey(context: Context, items: List<UpcomingReminderItem>): String {
            val localeTag = AppLocaleHelper.resolveEffectiveLocale(context).toLanguageTag()
            val use24h = PhantomPrefs.is24HourFormat(context)
            val prefix = "$localeTag|$use24h|"
            if (items.isEmpty()) return "${prefix}empty"
            return prefix + items.joinToString("|") { "${it.snapshotKey}:${it.summary}" }
        }

        private fun formatLine(context: Context, item: UpcomingReminderItem): String {
            val timeStr = PhantomPrefs.formatMessageTime(context, item.dueAt, includeDate = true)
            return if (timeStr.isNotEmpty()) "$timeStr · ${item.summary}" else item.summary
        }

        suspend fun updateAllIfChanged(context: Context) {
            val appCtx = context.applicationContext
            val mgr = try {
                AppWidgetManager.getInstance(appCtx)
            } catch (_: Exception) {
                return
            }
            val ids = try {
                mgr.getAppWidgetIds(ComponentName(appCtx, ReminderWidgetProvider::class.java))
            } catch (_: Exception) {
                return
            }
            if (ids.isEmpty()) {
                lastSnapshot = null
                return
            }
            val now = System.currentTimeMillis()
            val items = loadUpcomingItems(appCtx, now)
            val snapshot = computeSnapshotKey(appCtx, items)
            if (lastSnapshot == snapshot) return
            lastSnapshot = snapshot
            val views = buildRemoteViews(appCtx, items)
            try {
                mgr.updateAppWidget(ids, views)
            } catch (_: Exception) {
            }
        }

        private fun uniqueReqCode(itemId: Long, salt: Int): Int {
            return ((itemId and 0xFFFFL).toInt() * 31) + salt
        }

        private fun buildActionBroadcast(
            context: Context,
            action: String,
            item: UpcomingReminderItem,
            salt: Int
        ): PendingIntent {
            val intent = Intent(context, ReminderWidgetProvider::class.java).apply {
                this.action = action
                putExtra(EXTRA_ITEM_TYPE, item.itemType)
                putExtra(EXTRA_ITEM_ID, item.itemId)
            }
            return PendingIntent.getBroadcast(
                context,
                uniqueReqCode(item.itemId, salt),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun buildOpenItemIntent(
            context: Context,
            item: UpcomingReminderItem?,
            salt: Int
        ): PendingIntent {
            val intent = when {
                item?.messageId != null -> MessageDetailActivity.createIntent(context, item.messageId).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                item?.todoId != null -> Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(DailySummaryPolicy.EXTRA_OPEN_TAB, TAB_NOTES)
                }
                else -> Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(DailySummaryPolicy.EXTRA_OPEN_TAB, TAB_NOTES)
                }
            }
            val reqCode = item?.let { uniqueReqCode(it.itemId, salt) } ?: WIDGET_CLICK_REQUEST_CODE
            return PendingIntent.getActivity(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun buildEditTodoIntent(
            context: Context,
            todoId: Long,
            salt: Int
        ): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(DailySummaryPolicy.EXTRA_OPEN_TAB, TAB_NOTES)
                putExtra(EXTRA_EDIT_TODO_ID, todoId)
            }
            return PendingIntent.getActivity(
                context,
                uniqueReqCode(todoId, salt),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun bindRow(
            context: Context,
            views: RemoteViews,
            item: UpcomingReminderItem?,
            rowId: Int,
            textId: Int,
            doneId: Int,
            editId: Int,
            deleteId: Int,
            rowOffset: Int
        ) {
            if (item == null) {
                views.setViewVisibility(rowId, View.GONE)
                return
            }
            views.setViewVisibility(rowId, View.VISIBLE)
            views.setTextViewText(textId, formatLine(context, item))
            views.setOnClickPendingIntent(textId, buildOpenItemIntent(context, item, 100 + rowOffset))

            if (item.todoId != null) {
                views.setViewVisibility(doneId, View.VISIBLE)
                views.setOnClickPendingIntent(
                    doneId,
                    buildActionBroadcast(context, ACTION_COMPLETE_ITEM, item, 200 + rowOffset)
                )
                views.setViewVisibility(editId, View.VISIBLE)
                views.setOnClickPendingIntent(
                    editId,
                    buildEditTodoIntent(context, item.todoId, 300 + rowOffset)
                )
            } else {
                views.setViewVisibility(doneId, View.GONE)
                views.setViewVisibility(editId, View.GONE)
            }

            views.setViewVisibility(deleteId, View.VISIBLE)
            views.setOnClickPendingIntent(
                deleteId,
                buildActionBroadcast(context, ACTION_DELETE_ITEM, item, 400 + rowOffset)
            )
        }

        private fun buildRemoteViews(
            context: Context,
            items: List<UpcomingReminderItem>
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_reminder)
            val rootPending = buildOpenItemIntent(context, items.firstOrNull(), 0)
            views.setOnClickPendingIntent(R.id.widget_root, rootPending)

            if (items.isEmpty()) {
                views.setViewVisibility(R.id.widget_reminder_empty, View.VISIBLE)
                views.setViewVisibility(R.id.widget_reminder_row1, View.GONE)
                views.setViewVisibility(R.id.widget_reminder_row2, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_reminder_empty, View.GONE)
                bindRow(
                    context = context,
                    views = views,
                    item = items.getOrNull(0),
                    rowId = R.id.widget_reminder_row1,
                    textId = R.id.widget_reminder_line1,
                    doneId = R.id.widget_reminder_row1_done,
                    editId = R.id.widget_reminder_row1_edit,
                    deleteId = R.id.widget_reminder_row1_delete,
                    rowOffset = 1
                )
                bindRow(
                    context = context,
                    views = views,
                    item = items.getOrNull(1),
                    rowId = R.id.widget_reminder_row2,
                    textId = R.id.widget_reminder_line2,
                    doneId = R.id.widget_reminder_row2_done,
                    editId = R.id.widget_reminder_row2_edit,
                    deleteId = R.id.widget_reminder_row2_delete,
                    rowOffset = 2
                )
            }
            return views
        }
    }
}
