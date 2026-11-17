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
                val items = AppDatabaseHolder.db(appCtx).messageDao()
                    .pendingReminders(now)
                    .filter { (it.reminderAt ?: 0L) >= now }
                    .sortedBy { it.reminderAt ?: Long.MAX_VALUE }
                    .take(2)
                val firstId = items.firstOrNull()?.id
                val lines = formatLines(items)
                lastSnapshot = Triple(firstId, lines.first, lines.second)
                val views = buildRemoteViews(appCtx, firstId, lines.first, lines.second)
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

        @Volatile
        private var lastSnapshot: Triple<Long?, String, String>? = null

        private fun formatLines(items: List<com.phantom.checker.data.MessageEntity>): Pair<String, String> {
            if (items.isEmpty()) return "目前無待辦提醒" to ""
            val fmt = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            fun lineFor(m: com.phantom.checker.data.MessageEntity): String {
                val timeStr = m.reminderAt?.let { fmt.format(Date(it)) } ?: ""
                val title = m.reminderNote?.takeIf { it.isNotBlank() }
                    ?: m.sender.ifBlank { m.appLabel }
                return if (timeStr.isNotEmpty()) "$timeStr · $title" else title
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
            val items = AppDatabaseHolder.db(appCtx).messageDao()
                .pendingReminders(now)
                .filter { (it.reminderAt ?: 0L) >= now }
                .sortedBy { it.reminderAt ?: Long.MAX_VALUE }
                .take(2)
            val firstId = items.firstOrNull()?.id
            val lines = formatLines(items)
            val snapshot = Triple(firstId, lines.first, lines.second)
            if (lastSnapshot == snapshot) return
            lastSnapshot = snapshot
            val views = buildRemoteViews(appCtx, firstId, lines.first, lines.second)
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
