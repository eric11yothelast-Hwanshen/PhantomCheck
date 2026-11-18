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
import com.phantom.checker.data.NoteEntity
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.service.DailySummaryPolicy
import com.phantom.checker.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 桌面 2x2「幻訊記事」小工具：
 * 顯示總記事筆數與最近 5 則記事（置頂優先），點擊開啟 App 記事分頁。
 */
class NotesWidgetProvider : AppWidgetProvider() {

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
                val allNotes = AppDatabaseHolder.db(appCtx).noteDao().all()
                val topNotes = allNotes.take(MAX_ITEMS)
                lastSnapshot = computeSnapshotKey(allNotes.size, topNotes)
                val views = buildRemoteViews(appCtx, allNotes.size, topNotes, System.currentTimeMillis())
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
        val remaining = mgr.getAppWidgetIds(ComponentName(context, NotesWidgetProvider::class.java))
        if (remaining.isEmpty()) {
            lastSnapshot = null
        }
    }

    companion object {
        private const val MAX_ITEMS = 5
        private const val SNIPPET_MAX_LEN = 36
        private const val WIDGET_CLICK_REQUEST_CODE = 2019

        @Volatile
        private var lastSnapshot: String? = null

        suspend fun updateAllIfChanged(context: Context?) {
            val appCtx = context?.applicationContext ?: return
            val mgr = try {
                AppWidgetManager.getInstance(appCtx)
            } catch (_: Exception) {
                return
            }
            val ids = try {
                mgr.getAppWidgetIds(ComponentName(appCtx, NotesWidgetProvider::class.java))
            } catch (_: Exception) {
                return
            }
            if (ids.isEmpty()) {
                lastSnapshot = null
                return
            }
            val allNotes = try {
                AppDatabaseHolder.db(appCtx).noteDao().all()
            } catch (_: Exception) {
                return
            }
            val topNotes = allNotes.take(MAX_ITEMS)
            val snapshot = computeSnapshotKey(allNotes.size, topNotes)
            if (lastSnapshot == snapshot) return
            lastSnapshot = snapshot
            val views = buildRemoteViews(appCtx, allNotes.size, topNotes, System.currentTimeMillis())
            try {
                mgr.updateAppWidget(ids, views)
            } catch (_: Exception) {
            }
        }

        private fun computeSnapshotKey(totalCount: Int, topNotes: List<NoteEntity>): String {
            if (totalCount == 0 || topNotes.isEmpty()) return "0:empty"
            return "$totalCount|" + topNotes.joinToString("|") { note ->
                "${note.id}:${note.pinned}:${note.updatedAt}:${note.content.take(SNIPPET_MAX_LEN)}"
            }
        }

        private fun formatSnippet(note: NoteEntity): String {
            val prefix = if (note.pinned) "📌 " else ""
            val clean = note.content.replace("\n", " ").trim()
            val truncated = if (clean.length > SNIPPET_MAX_LEN) {
                clean.take(SNIPPET_MAX_LEN) + "…"
            } else {
                clean
            }
            return "$prefix$truncated"
        }

        private fun formatRelativeTime(now: Long, ts: Long): String {
            val diffMs = (now - ts).coerceAtLeast(0L)
            val diffMin = diffMs / 60_000L
            return when {
                diffMin < 1L -> "剛剛"
                diffMin < 60L -> "${diffMin}分鐘前"
                diffMin < 24L * 60L -> "${diffMin / 60L}小時前"
                diffMin < 7L * 24L * 60L -> "${diffMin / (24L * 60L)}天前"
                else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(ts))
            }
        }

        private fun buildOpenNotesIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(DailySummaryPolicy.EXTRA_OPEN_TAB, ReminderWidgetProvider.TAB_NOTES)
            }
            return PendingIntent.getActivity(
                context,
                WIDGET_CLICK_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun buildRemoteViews(
            context: Context,
            totalCount: Int,
            notes: List<NoteEntity>,
            now: Long
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_notes)
            views.setOnClickPendingIntent(R.id.widget_root, buildOpenNotesIntent(context))
            views.setTextViewText(R.id.widget_notes_count, if (totalCount > 0) "${totalCount} 筆" else "")

            val rowIds = intArrayOf(
                R.id.widget_notes_row1,
                R.id.widget_notes_row2,
                R.id.widget_notes_row3,
                R.id.widget_notes_row4,
                R.id.widget_notes_row5
            )
            val textIds = intArrayOf(
                R.id.widget_notes_text1,
                R.id.widget_notes_text2,
                R.id.widget_notes_text3,
                R.id.widget_notes_text4,
                R.id.widget_notes_text5
            )
            val timeIds = intArrayOf(
                R.id.widget_notes_time1,
                R.id.widget_notes_time2,
                R.id.widget_notes_time3,
                R.id.widget_notes_time4,
                R.id.widget_notes_time5
            )

            if (notes.isEmpty()) {
                views.setViewVisibility(R.id.widget_notes_empty, View.VISIBLE)
                for (i in rowIds.indices) {
                    views.setViewVisibility(rowIds[i], View.GONE)
                }
            } else {
                views.setViewVisibility(R.id.widget_notes_empty, View.GONE)
                for (i in rowIds.indices) {
                    val note = notes.getOrNull(i)
                    if (note == null) {
                        views.setViewVisibility(rowIds[i], View.GONE)
                    } else {
                        views.setViewVisibility(rowIds[i], View.VISIBLE)
                        views.setTextViewText(textIds[i], formatSnippet(note))
                        views.setTextViewText(timeIds[i], formatRelativeTime(now, note.updatedAt))
                    }
                }
            }
            return views
        }
    }
}
