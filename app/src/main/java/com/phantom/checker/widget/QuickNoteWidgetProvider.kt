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
import com.phantom.checker.util.AppLocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * #439 新增小工具 3：快捷記事（QuickNote）
 * - 顯示最近 3 筆記事預覽＋一鍵「新增記事」deep link 進入 App 記事分頁。
 * - goAsync() 限時 8.5 秒讀取本機 Room noteDao，零新權限、零網路請求。
 */
class QuickNoteWidgetProvider : AppWidgetProvider() {

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
                withTimeout(8_500L) {
                    val notes = loadRecentNotes(appCtx)
                    lastSnapshot = computeSnapshotKey(appCtx, notes)
                    val views = buildRemoteViews(appCtx, notes)
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
        val remaining = mgr.getAppWidgetIds(ComponentName(context, QuickNoteWidgetProvider::class.java))
        if (remaining.isEmpty()) {
            lastSnapshot = null
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        lastSnapshot = null
    }

    companion object {
        const val EXTRA_QUICK_NEW_NOTE = "extra_quick_new_note"
        private const val MAX_ITEMS = 3
        private const val OPEN_NOTES_REQUEST_CODE = 2131
        private const val NEW_NOTE_REQUEST_CODE = 2132

        @Volatile
        private var lastSnapshot: String? = null

        private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd")

        private suspend fun loadRecentNotes(appCtx: Context): List<NoteEntity> =
            runCatching { AppDatabaseHolder.db(appCtx).noteDao().topRecent(MAX_ITEMS) }
                .getOrDefault(emptyList())

        private fun computeSnapshotKey(context: Context, notes: List<NoteEntity>): String {
            val localeTag = AppLocaleHelper.resolveEffectiveLocale(context).toLanguageTag()
            val dayBucket = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
            val itemsKey = notes.joinToString("|") { note ->
                "${note.id}:${note.pinned}:${note.updatedAt}:${note.content.take(40)}"
            }
            return "$localeTag|$dayBucket|$itemsKey"
        }

        suspend fun refreshNow(context: Context) {
            val appCtx = context.applicationContext
            val mgr = try {
                AppWidgetManager.getInstance(appCtx)
            } catch (_: Exception) {
                return
            }
            val ids = try {
                mgr.getAppWidgetIds(ComponentName(appCtx, QuickNoteWidgetProvider::class.java))
            } catch (_: Exception) {
                return
            }
            if (ids.isEmpty()) {
                lastSnapshot = null
                return
            }
            val notes = loadRecentNotes(appCtx)
            val sig = computeSnapshotKey(appCtx, notes)
            if (lastSnapshot == sig) return
            lastSnapshot = sig
            val views = buildRemoteViews(appCtx, notes)
            try {
                mgr.updateAppWidget(ids, views)
            } catch (_: Exception) {
            }
        }

        private fun buildRemoteViews(context: Context, notes: List<NoteEntity>): RemoteViews {
            val resCtx = AppLocaleHelper.localizedContext(context)
            val openNotesIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(DailySummaryPolicy.EXTRA_OPEN_TAB, ReminderWidgetProvider.TAB_NOTES)
            }
            val openNotesPending = PendingIntent.getActivity(
                context,
                OPEN_NOTES_REQUEST_CODE,
                openNotesIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val newNoteIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(DailySummaryPolicy.EXTRA_OPEN_TAB, ReminderWidgetProvider.TAB_NOTES)
                putExtra(EXTRA_QUICK_NEW_NOTE, true)
            }
            val newNotePending = PendingIntent.getActivity(
                context,
                NEW_NOTE_REQUEST_CODE,
                newNoteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val rowIds = intArrayOf(
                R.id.widget_quick_note_row1,
                R.id.widget_quick_note_row2,
                R.id.widget_quick_note_row3
            )
            val textIds = intArrayOf(
                R.id.widget_quick_note_text1,
                R.id.widget_quick_note_text2,
                R.id.widget_quick_note_text3
            )
            val dateIds = intArrayOf(
                R.id.widget_quick_note_date1,
                R.id.widget_quick_note_date2,
                R.id.widget_quick_note_date3
            )
            val zone = ZoneId.systemDefault()

            return RemoteViews(context.packageName, R.layout.widget_quick_note).apply {
                setTextViewText(R.id.widget_quick_note_header, resCtx.getString(R.string.widget_quick_note_title))
                setTextViewText(R.id.widget_quick_note_add_btn, resCtx.getString(R.string.widget_quick_note_add_btn))
                setOnClickPendingIntent(R.id.widget_root, openNotesPending)
                setOnClickPendingIntent(R.id.widget_quick_note_add_btn, newNotePending)

                if (notes.isEmpty()) {
                    setViewVisibility(R.id.widget_quick_note_empty, View.VISIBLE)
                    setTextViewText(R.id.widget_quick_note_empty, resCtx.getString(R.string.widget_quick_note_empty))
                    for (i in 0 until MAX_ITEMS) {
                        setViewVisibility(rowIds[i], View.GONE)
                    }
                } else {
                    setViewVisibility(R.id.widget_quick_note_empty, View.GONE)
                    for (i in 0 until MAX_ITEMS) {
                        val note = notes.getOrNull(i)
                        if (note != null) {
                            setViewVisibility(rowIds[i], View.VISIBLE)
                            val preview = note.content.replace('\n', ' ').trim()
                            setTextViewText(textIds[i], preview)
                            val dateStr = if (note.updatedAt > 0L) {
                                Instant.ofEpochMilli(note.updatedAt).atZone(zone).toLocalDate().format(dateFormatter)
                            } else {
                                ""
                            }
                            setTextViewText(dateIds[i], dateStr)
                        } else {
                            setViewVisibility(rowIds[i], View.GONE)
                        }
                    }
                }
            }
        }
    }
}
