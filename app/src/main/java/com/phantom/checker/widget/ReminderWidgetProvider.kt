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
import com.phantom.checker.ui.MainActivity
import com.phantom.checker.ui.MessageDetailActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 桌面 2x1「待辦提醒」小工具：
 * 顯示最近 1-2 則尚未觸發的待辦提醒事項，點擊開啟訊息詳情或主畫面。
 */
class ReminderWidgetProvider : AppWidgetProvider() {

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
                val now = System.currentTimeMillis()
                val items = loadUpcomingItems(appCtx, now)
                val firstMsgId = items.firstOrNull()?.messageId
                val firstKey = items.firstOrNull()?.snapshotKey
                val lines = formatLines(items)
                lastSnapshot = Triple(firstKey, lines.first, lines.second)
                val views = buildRemoteViews(appCtx, firstMsgId, lines.first, lines.second)
                appWidgetManager.updateAppWidget(appWidgetIds, views)
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

    companion object {
        private const val WIDGET_CLICK_REQUEST_CODE = 2008

        private data class UpcomingReminderItem(
            val snapshotKey: String,
            val dueAt: Long,
            val summary: String,
            val messageId: Long? = null
        )

        @Volatile
        private var lastSnapshot: Triple<String?, String, String>? = null

        private suspend fun loadUpcomingItems(ctx: Context, now: Long): List<UpcomingReminderItem> {
            val db = AppDatabaseHolder.db(ctx)
            val msgItems = db.messageDao().pendingReminders(now)
                .filter { (it.remindAt ?: 0L) >= now }
                .map { m ->
                    val title = m.sender.ifBlank { m.appLabel }
                    val snippet = m.content.replace("\n", " ").trim().take(20)
                    val summary = if (snippet.isNotEmpty()) "$title：$snippet" else title
                    UpcomingReminderItem(
                        snapshotKey = "m:${m.id}:${m.remindAt}",
                        dueAt = m.remindAt ?: Long.MAX_VALUE,
                        summary = summary,
                        messageId = m.id
                    )
                }
            val todoItems = db.todoDao().pendingTodos()
                .filter { !it.isDone && it.dueAt >= now }
                .map { t ->
                    UpcomingReminderItem(
                        snapshotKey = "t:${t.id}:${t.dueAt}",
                        dueAt = t.dueAt,
                        summary = t.title.replace("\n", " ").trim().take(28),
                        messageId = t.relatedMessageId
                    )
                }
            return (msgItems + todoItems)
                .sortedBy { it.dueAt }
                .take(2)
        }

        private fun formatLines(items: List<UpcomingReminderItem>): Pair<String, String> {
            if (items.isEmpty()) return "目前無待辦提醒" to ""
            val fmt = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            fun lineFor(item: UpcomingReminderItem): String {
                val timeStr = fmt.format(Date(item.dueAt))
                return if (timeStr.isNotEmpty()) "$timeStr · ${item.summary}" else item.summary
            }
            val l1 = lineFor(items[0])
            val l2 = items.getOrNull(1)?.let { lineFor(it) } ?: ""
            return l1 to l2
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
            val firstMsgId = items.firstOrNull()?.messageId
            val firstKey = items.firstOrNull()?.snapshotKey
            val lines = formatLines(items)
            val snapshot = Triple(firstKey, lines.first, lines.second)
            if (lastSnapshot == snapshot) return
            lastSnapshot = snapshot
            val views = buildRemoteViews(appCtx, firstMsgId, lines.first, lines.second)
            try {
                mgr.updateAppWidget(ids, views)
            } catch (_: Exception) {
            }
        }

        private fun buildRemoteViews(
            context: Context,
            firstMessageId: Long?,
            line1: String,
            line2: String
        ): RemoteViews {
            val launchIntent = if (firstMessageId != null) {
                MessageDetailActivity.createIntent(context, firstMessageId).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            } else {
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                WIDGET_CLICK_REQUEST_CODE,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return RemoteViews(context.packageName, R.layout.widget_reminder).apply {
                setTextViewText(R.id.widget_reminder_line1, line1)
                setTextViewText(R.id.widget_reminder_line2, line2)
                setViewVisibility(R.id.widget_reminder_line2, if (line2.isBlank()) View.GONE else View.VISIBLE)
                setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            }
        }
    }
}
