package com.phantom.checker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.phantom.checker.R
import com.phantom.checker.service.DailySummaryPolicy
import com.phantom.checker.ui.MainActivity
import com.phantom.checker.ui.PermissionHubActivity
import com.phantom.checker.util.AppLocaleHelper
import com.phantom.checker.util.HealthConnectBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * #439 新增小工具 4：健康快照（HealthSnap）
 * - 透過既有 HealthConnectBridge 讀取今日步數與睡眠時數（零新權限、零新網路）。
 * - 未授權或裝置未安裝 Health Connect 時，誠實顯示教學文案並導向 PermissionHubActivity，絕不顯示 0 或例外。
 * - 本機 30 分鐘快取策略（SharedPreferences），禁止小工具每次互動都打 Health Connect。
 */
class HealthSnapWidgetProvider : AppWidgetProvider() {

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
                    val snapshot = loadOrRefreshSnapshot(appCtx, forceRefresh = false)
                    lastSnapshot = computeSnapshotKey(appCtx, snapshot)
                    val views = buildRemoteViews(appCtx, snapshot)
                    appWidgetManager.updateAppWidget(appWidgetIds, views)
                }
            } catch (_: Exception) {
                // 例外時優先回退既有快取，僅無快取時才降級為未授權教學態
                val fallbackSnap = readCache(appCtx) ?: CachedHealthSnap.unauthorized()
                lastSnapshot = computeSnapshotKey(appCtx, fallbackSnap)
                val fallback = buildRemoteViews(appCtx, fallbackSnap)
                runCatching { appWidgetManager.updateAppWidget(appWidgetIds, fallback) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val mgr = AppWidgetManager.getInstance(context)
        val remaining = mgr.getAppWidgetIds(ComponentName(context, HealthSnapWidgetProvider::class.java))
        if (remaining.isEmpty()) {
            lastSnapshot = null
            clearCache(context.applicationContext)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        lastSnapshot = null
        clearCache(context.applicationContext)
    }

    companion object {
        private const val CACHE_PREFS = "phantom_health_widget_cache"
        private const val KEY_AUTHORIZED = "authorized"
        private const val KEY_HAS_STEPS = "has_steps_perm"
        private const val KEY_HAS_SLEEP = "has_sleep_perm"
        private const val KEY_STEPS = "steps"
        private const val KEY_SLEEP_HOURS = "sleep_hours"
        private const val KEY_CACHED_AT_MS = "cached_at_ms"
        private const val CACHE_TTL_MS = 30L * 60L * 1000L

        private const val OPEN_STATS_REQUEST_CODE = 2141
        private const val OPEN_PERMS_REQUEST_CODE = 2142

        @Volatile
        private var lastSnapshot: String? = null

        private data class CachedHealthSnap(
            val authorized: Boolean,
            val hasStepsPerm: Boolean,
            val hasSleepPerm: Boolean,
            val steps: Long,
            val sleepHours: Float,
            val cachedAtMs: Long
        ) {
            companion object {
                fun unauthorized(nowMs: Long = System.currentTimeMillis()) = CachedHealthSnap(
                    authorized = false,
                    hasStepsPerm = false,
                    hasSleepPerm = false,
                    steps = -1L,
                    sleepHours = -1f,
                    cachedAtMs = nowMs
                )
            }
        }

        private fun computeSnapshotKey(appCtx: Context, snap: CachedHealthSnap): String {
            val localeTag = AppLocaleHelper.resolveEffectiveLocale(appCtx).toLanguageTag()
            return "$localeTag|${snap.authorized}|${snap.hasStepsPerm}|${snap.hasSleepPerm}|${snap.steps}|${snap.sleepHours}"
        }

        private fun readCache(appCtx: Context): CachedHealthSnap? {
            val prefs = appCtx.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
            val cachedAt = prefs.getLong(KEY_CACHED_AT_MS, 0L)
            if (cachedAt <= 0L) return null
            return CachedHealthSnap(
                authorized = prefs.getBoolean(KEY_AUTHORIZED, false),
                hasStepsPerm = prefs.getBoolean(KEY_HAS_STEPS, false),
                hasSleepPerm = prefs.getBoolean(KEY_HAS_SLEEP, false),
                steps = prefs.getLong(KEY_STEPS, -1L),
                sleepHours = prefs.getFloat(KEY_SLEEP_HOURS, -1f),
                cachedAtMs = cachedAt
            )
        }

        private fun writeCache(appCtx: Context, snap: CachedHealthSnap) {
            appCtx.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_AUTHORIZED, snap.authorized)
                .putBoolean(KEY_HAS_STEPS, snap.hasStepsPerm)
                .putBoolean(KEY_HAS_SLEEP, snap.hasSleepPerm)
                .putLong(KEY_STEPS, snap.steps)
                .putFloat(KEY_SLEEP_HOURS, snap.sleepHours)
                .putLong(KEY_CACHED_AT_MS, snap.cachedAtMs)
                .apply()
        }

        private fun clearCache(appCtx: Context) {
            runCatching {
                appCtx.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
            }
        }

        /**
         * 僅查詢今日步數與跨午夜睡眠的輕量聚合（不查心率／距離／熱量，避免多餘 Health Connect IPC）。
         */
        private suspend fun readTodayStepsAndSleep(
            appCtx: Context,
            hasStepsPerm: Boolean,
            hasSleepPerm: Boolean
        ): Pair<Long, Float> {
            val client = HealthConnectBridge.getClientSafely(appCtx) ?: return -1L to -1f
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)

            var steps = -1L
            if (hasStepsPerm) {
                steps = runCatching {
                    val res = client.aggregate(
                        AggregateRequest(
                            metrics = setOf(StepsRecord.COUNT_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(
                                today.atStartOfDay(),
                                today.plusDays(1).atStartOfDay()
                            )
                        )
                    )
                    res[StepsRecord.COUNT_TOTAL] ?: 0L
                }.getOrDefault(-1L)
            }

            var sleepHours = -1f
            if (hasSleepPerm) {
                sleepHours = runCatching {
                    val res = client.aggregate(
                        AggregateRequest(
                            metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(
                                today.minusDays(1).atTime(LocalTime.of(18, 0)),
                                today.atTime(LocalTime.of(18, 0))
                            )
                        )
                    )
                    val dur = res[SleepSessionRecord.SLEEP_DURATION_TOTAL]
                    if (dur != null) dur.toMinutes() / 60f else 0f
                }.getOrDefault(-1f)
            }

            return steps to sleepHours
        }

        private suspend fun loadOrRefreshSnapshot(
            appCtx: Context,
            forceRefresh: Boolean
        ): CachedHealthSnap {
            val nowMs = System.currentTimeMillis()
            val existingCache = readCache(appCtx)
            if (!forceRefresh && existingCache != null && (nowMs - existingCache.cachedAtMs) in 0L until CACHE_TTL_MS) {
                return existingCache
            }
            val perm = runCatching { HealthConnectBridge.getPermissionStatus(appCtx) }.getOrNull()
                ?: return existingCache ?: CachedHealthSnap.unauthorized(nowMs)
            if (!perm.steps && !perm.sleep) {
                val unauth = CachedHealthSnap.unauthorized(nowMs)
                writeCache(appCtx, unauth)
                return unauth
            }
            val (steps, sleepHours) = readTodayStepsAndSleep(
                appCtx = appCtx,
                hasStepsPerm = perm.steps,
                hasSleepPerm = perm.sleep
            )
            val snap = CachedHealthSnap(
                authorized = true,
                hasStepsPerm = perm.steps,
                hasSleepPerm = perm.sleep,
                steps = if (perm.steps) steps else -1L,
                sleepHours = if (perm.sleep) sleepHours else -1f,
                cachedAtMs = nowMs
            )
            writeCache(appCtx, snap)
            return snap
        }

        suspend fun refreshNow(context: Context, forceRefresh: Boolean = false) {
            val appCtx = context.applicationContext
            val mgr = try {
                AppWidgetManager.getInstance(appCtx)
            } catch (_: Exception) {
                return
            }
            val ids = try {
                mgr.getAppWidgetIds(ComponentName(appCtx, HealthSnapWidgetProvider::class.java))
            } catch (_: Exception) {
                return
            }
            if (ids.isEmpty()) {
                lastSnapshot = null
                clearCache(appCtx)
                return
            }
            val snap = loadOrRefreshSnapshot(appCtx, forceRefresh = forceRefresh)
            val sig = computeSnapshotKey(appCtx, snap)
            if (!forceRefresh && lastSnapshot == sig) return
            lastSnapshot = sig
            val views = buildRemoteViews(appCtx, snap)
            try {
                mgr.updateAppWidget(ids, views)
            } catch (_: Exception) {
            }
        }

        private fun buildRemoteViews(context: Context, snap: CachedHealthSnap): RemoteViews {
            val resCtx = AppLocaleHelper.localizedContext(context)
            val openStatsIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(DailySummaryPolicy.EXTRA_OPEN_TAB, DailySummaryPolicy.TAB_STATS)
            }
            val openStatsPending = PendingIntent.getActivity(
                context,
                OPEN_STATS_REQUEST_CODE,
                openStatsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val openPermsIntent = Intent(context, PermissionHubActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPermsPending = PendingIntent.getActivity(
                context,
                OPEN_PERMS_REQUEST_CODE,
                openPermsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return RemoteViews(context.packageName, R.layout.widget_health_snap).apply {
                setTextViewText(R.id.widget_health_header, resCtx.getString(R.string.widget_health_snap_title))
                if (!snap.authorized) {
                    setViewVisibility(R.id.widget_health_unauth, View.VISIBLE)
                    setViewVisibility(R.id.widget_health_metrics, View.GONE)
                    setTextViewText(
                        R.id.widget_health_unauth,
                        resCtx.getString(R.string.widget_health_snap_unauth_msg)
                    )
                    setOnClickPendingIntent(R.id.widget_root, openPermsPending)
                } else {
                    setViewVisibility(R.id.widget_health_unauth, View.GONE)
                    setViewVisibility(R.id.widget_health_metrics, View.VISIBLE)
                    setTextViewText(
                        R.id.widget_health_steps_label,
                        resCtx.getString(R.string.widget_health_snap_steps_label)
                    )
                    setTextViewText(
                        R.id.widget_health_sleep_label,
                        resCtx.getString(R.string.widget_health_snap_sleep_label)
                    )
                    val stepsDisplay = if (snap.hasStepsPerm && snap.steps >= 0L) {
                        NumberFormat.getIntegerInstance(AppLocaleHelper.resolveEffectiveLocale(context))
                            .format(snap.steps)
                    } else {
                        "—"
                    }
                    val sleepDisplay = if (snap.hasSleepPerm && snap.sleepHours > 0f) {
                        String.format(Locale.US, "%.1fh", snap.sleepHours)
                    } else {
                        "—"
                    }
                    setTextViewText(R.id.widget_health_steps_value, stepsDisplay)
                    setTextViewText(R.id.widget_health_sleep_value, sleepDisplay)
                    setOnClickPendingIntent(R.id.widget_root, openStatsPending)
                }
            }
        }
    }
}
