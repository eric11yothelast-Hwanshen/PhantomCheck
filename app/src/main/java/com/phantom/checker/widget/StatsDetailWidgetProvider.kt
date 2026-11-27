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
import com.phantom.checker.service.NotificationCollectorService
import com.phantom.checker.ui.MainActivity
import com.phantom.checker.util.AppLocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 桌面 4x2 詳情統計小工具（跟隨系統淺/深色主題）：
 * 顯示今日訊息數、未讀數、詐騙警示數、近 7 日訊息趨勢條（RemoteViews LinearLayout + ImageView 比例條）
 * 與最近來源 App 圖示，點擊開啟幻訊主畫面。
 */
class StatsDetailWidgetProvider : AppWidgetProvider() {

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
                    val startOfDay = NotificationCollectorService.startOfTodayMillis()
                    val dao = AppDatabaseHolder.db(appCtx).messageDao()
                    val snap = dao.statsSnapshot(startOfDay)
                    val dayData = loadSevenDayCounts(appCtx)
                    val recentPkgs = snap.recentPackages().take(3)
                    lastDetailSig = DetailSignature(
                        today = snap.today,
                        unread = snap.unread,
                        scam = snap.scam,
                        recentPkgs = recentPkgs,
                        dayStartMs = startOfDay,
                        dayCountsHash = dayData.first.contentHashCode(),
                        localeTag = AppLocaleHelper.resolveEffectiveLocale(appCtx).toLanguageTag()
                    )
                    val views = buildRemoteViews(
                        context = appCtx,
                        today = snap.today,
                        unread = snap.unread,
                        scam = snap.scam,
                        dayCounts = dayData.first,
                        dayLabels = dayData.second,
                        recentPkgs = recentPkgs
                    )
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
        val remaining = mgr.getAppWidgetIds(ComponentName(context, StatsDetailWidgetProvider::class.java))
        if (remaining.isEmpty()) {
            lastDetailSig = null
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        lastDetailSig = null
    }

    private data class DetailSignature(
        val today: Int,
        val unread: Int,
        val scam: Int,
        val recentPkgs: List<String>,
        val dayStartMs: Long,
        val dayCountsHash: Int,
        val localeTag: String
    )

    companion object {
        private const val WIDGET_CLICK_REQUEST_CODE = 2003
        private const val BAR_MAX_HEIGHT_DP = 38f
        private const val BAR_MIN_HEIGHT_DP = 2f
        private val DAY_KEY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US)

        private val BAR_COL_IDS = intArrayOf(
            R.id.widget_bar_col_0,
            R.id.widget_bar_col_1,
            R.id.widget_bar_col_2,
            R.id.widget_bar_col_3,
            R.id.widget_bar_col_4,
            R.id.widget_bar_col_5,
            R.id.widget_bar_col_6
        )

        private val BAR_LABEL_IDS = intArrayOf(
            R.id.widget_bar_label_0,
            R.id.widget_bar_label_1,
            R.id.widget_bar_label_2,
            R.id.widget_bar_label_3,
            R.id.widget_bar_label_4,
            R.id.widget_bar_label_5,
            R.id.widget_bar_label_6
        )

        private val RECENT_ICON_IDS = intArrayOf(
            R.id.widget_recent_icon_0,
            R.id.widget_recent_icon_1,
            R.id.widget_recent_icon_2
        )

        @Volatile
        private var lastDetailSig: DetailSignature? = null

        suspend fun updateAllIfChanged(
            context: Context,
            today: Int,
            unread: Int,
            scam: Int,
            recentPkgs: List<String>
        ) {
            val appCtx = context.applicationContext
            val mgr = try {
                AppWidgetManager.getInstance(appCtx)
            } catch (_: Exception) {
                return
            }
            val ids = try {
                mgr.getAppWidgetIds(ComponentName(appCtx, StatsDetailWidgetProvider::class.java))
            } catch (_: Exception) {
                return
            }
            if (ids.isEmpty()) {
                lastDetailSig = null
                return
            }

            val dayStartMs = NotificationCollectorService.startOfTodayMillis()
            val trimmedPkgs = recentPkgs.take(3)
            val localeTag = AppLocaleHelper.resolveEffectiveLocale(appCtx).toLanguageTag()
            val dayData = withContext(Dispatchers.IO) {
                loadSevenDayCounts(appCtx)
            }
            val current = DetailSignature(
                today = today,
                unread = unread,
                scam = scam,
                recentPkgs = trimmedPkgs,
                dayStartMs = dayStartMs,
                dayCountsHash = dayData.first.contentHashCode(),
                localeTag = localeTag
            )
            if (lastDetailSig == current) return

            lastDetailSig = current
            val views = withContext(Dispatchers.IO) {
                buildRemoteViews(
                    context = appCtx,
                    today = today,
                    unread = unread,
                    scam = scam,
                    dayCounts = dayData.first,
                    dayLabels = dayData.second,
                    recentPkgs = trimmedPkgs
                )
            }
            try {
                mgr.updateAppWidget(ids, views)
            } catch (_: Exception) {
            }
        }

        private suspend fun loadSevenDayCounts(context: Context): Pair<IntArray, Array<String>> {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val startDate = today.minusDays(6L)
            val start7d = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val rows = try {
                AppDatabaseHolder.db(context).messageDao().countByDaySince(start7d)
            } catch (_: Exception) {
                emptyList()
            }
            val countMap = rows.associate { it.dayKey to it.cnt }

            val counts = IntArray(7)
            val labels = Array(7) { "" }
            val resCtx = AppLocaleHelper.localizedContext(context)
            val weekdayNames = arrayOf(
                resCtx.getString(R.string.stats_s_15917f),
                resCtx.getString(R.string.widget_stats_s_d274ee),
                resCtx.getString(R.string.widget_stats_s_1d5639),
                resCtx.getString(R.string.widget_stats_s_49ddb0),
                resCtx.getString(R.string.widget_stats_s_4f8874),
                resCtx.getString(R.string.widget_stats_s_8f07f5),
                resCtx.getString(R.string.widget_stats_s_3d72c7)
            )
            val todayLabel = resCtx.getString(R.string.widget_stats_s_d2130d)

            for (i in 0 until 7) {
                val date = today.minusDays((6 - i).toLong())
                labels[i] = if (i == 6) todayLabel else weekdayNames[date.dayOfWeek.value % 7]
                counts[i] = countMap[date.format(DAY_KEY_FORMATTER)] ?: 0
            }
            return counts to labels
        }

        private fun buildRemoteViews(
            context: Context,
            today: Int,
            unread: Int,
            scam: Int,
            dayCounts: IntArray,
            dayLabels: Array<String>,
            recentPkgs: List<String>
        ): RemoteViews {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                WIDGET_CLICK_REQUEST_CODE,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val density = context.resources.displayMetrics.density
            val maxPx = (BAR_MAX_HEIGHT_DP * density).roundToInt().coerceAtLeast(10)
            val minPx = (BAR_MIN_HEIGHT_DP * density).roundToInt().coerceAtLeast(2)
            val maxCount = (dayCounts.maxOrNull() ?: 0).coerceAtLeast(1)

            return RemoteViews(context.packageName, R.layout.widget_stats_detail).apply {
                setTextViewText(R.id.widget_detail_today_count, today.toString())
                setTextViewText(R.id.widget_detail_unread_count, unread.toString())
                setTextViewText(R.id.widget_detail_scam_count, scam.toString())
                setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                for (i in 0 until 7) {
                    val count = dayCounts.getOrElse(i) { 0 }
                    val barPx = if (count <= 0) {
                        minPx
                    } else {
                        ((count.toFloat() / maxCount) * maxPx).roundToInt().coerceIn(minPx, maxPx)
                    }
                    val topPad = (maxPx - barPx).coerceAtLeast(0)
                    setViewPadding(BAR_COL_IDS[i], 0, topPad, 0, 0)
                    setTextViewText(BAR_LABEL_IDS[i], dayLabels.getOrElse(i) { "" })
                }

                for (i in 0 until 3) {
                    val pkg = recentPkgs.getOrNull(i)
                    val iconBmp = pkg?.let {
                        com.phantom.checker.ui.loadAppIconAndroidBitmap(
                            context,
                            it,
                            (16 * density).roundToInt().coerceAtLeast(24)
                        )
                    }
                    if (iconBmp != null) {
                        setViewVisibility(RECENT_ICON_IDS[i], View.VISIBLE)
                        setImageViewBitmap(RECENT_ICON_IDS[i], iconBmp)
                    } else {
                        setViewVisibility(RECENT_ICON_IDS[i], View.GONE)
                    }
                }
            }
        }
    }
}
