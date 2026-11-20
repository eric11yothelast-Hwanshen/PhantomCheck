package com.phantom.checker.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.phantom.checker.ui.MainActivity
import com.phantom.checker.util.PhantomPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * #337 早晚簡報精準排程器：
 * - 主路徑：AlarmManager.setAlarmClock（AlarmClockInfo label=「幻訊•<助手名>簡報」），準點誤差 ≤2 分鐘。
 * - Fallback 鏈：SecurityException / 不可用時依序降級 setExactAndAllowWhileIdle → setAndAllowWhileIdle。
 * - 搭配 15 分鐘零 token 變動偵測 PeriodicWorkRequest（ExistingPeriodicWorkPolicy.UPDATE，無網路限制）。
 * - 錯過 <2h 補發一次，逾時跳過次日。
 */
object BriefingScheduler {

    const val ACTION_MORNING_BRIEFING = "com.phantom.checker.action.MORNING_BRIEFING"
    const val ACTION_EVENING_BRIEFING = "com.phantom.checker.action.EVENING_BRIEFING"
    const val EXTRA_BRIEFING_MSG_ID = "extra_briefing_msg_id"
    const val TAB_ASSISTANT = "assistant"

    private const val REQ_MORNING = 33701
    private const val REQ_EVENING = 33702
    private const val REQ_SHOW_APP = 33703
    private const val CATCHUP_WINDOW_MS = 2L * 60L * 60L * 1000L // 2 小時補跑窗口
    private const val MONITOR_WORK_NAME = "phantom_proactive_monitor_15m"

    fun rescheduleAll(ctx: Context) {
        val appCtx = ctx.applicationContext
        scheduleSingle(
            appCtx,
            isMorning = true,
            enabled = PhantomPrefs.isMorningBriefingEnabled(appCtx),
            hhmm = PhantomPrefs.getMorningBriefingTime(appCtx)
        )
        scheduleSingle(
            appCtx,
            isMorning = false,
            enabled = PhantomPrefs.isEveningBriefingEnabled(appCtx),
            hhmm = PhantomPrefs.getEveningBriefingTime(appCtx)
        )
        scheduleProactiveMonitorWorker(appCtx)
    }

    fun scheduleProactiveMonitorWorker(ctx: Context) {
        val appCtx = ctx.applicationContext
        val anyEnabled = PhantomPrefs.isMorningBriefingEnabled(appCtx) ||
            PhantomPrefs.isEveningBriefingEnabled(appCtx) ||
            PhantomPrefs.getProactiveTopics(appCtx).isNotEmpty()
        val wm = WorkManager.getInstance(appCtx)
        if (!anyEnabled) {
            wm.cancelUniqueWork(MONITOR_WORK_NAME)
            return
        }
        // 零 token 本地指紋比對，不設網路 constraint
        val req = PeriodicWorkRequestBuilder<ProactiveMonitorWorker>(15L, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(
            MONITOR_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }

    private fun scheduleSingle(ctx: Context, isMorning: Boolean, enabled: Boolean, hhmm: String) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = buildAlarmPendingIntent(ctx, isMorning)
        am.cancel(pi)
        if (!enabled) return

        val triggerMs = computeNextTriggerMs(hhmm)
        val showIntent = PendingIntent.getActivity(
            ctx,
            REQ_SHOW_APP,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(DailySummaryPolicy.EXTRA_OPEN_TAB, TAB_ASSISTANT)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 主路徑：setAlarmClock → fallback setExactAndAllowWhileIdle → fallback setAndAllowWhileIdle
        val scheduledWithClock = try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerMs, showIntent), pi)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
        if (scheduledWithClock) return

        val scheduledExact = try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
                true
            } else {
                false
            }
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
        if (scheduledExact) return

        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        } catch (_: Exception) {
        }
    }

    private fun buildAlarmPendingIntent(ctx: Context, isMorning: Boolean): PendingIntent {
        val intent = Intent(ctx, BriefingAlarmReceiver::class.java).apply {
            action = if (isMorning) ACTION_MORNING_BRIEFING else ACTION_EVENING_BRIEFING
        }
        return PendingIntent.getBroadcast(
            ctx,
            if (isMorning) REQ_MORNING else REQ_EVENING,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun computeNextTriggerMs(hhmm: String, nowMs: Long = System.currentTimeMillis()): Long {
        val zone = ZoneId.systemDefault()
        val nowZdt = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMs), zone)
        val targetTime = parseHHmm(hhmm)
        var next = nowZdt.toLocalDate().atTime(targetTime).atZone(zone)
        if (!next.toInstant().toEpochMilli().let { it > nowMs + 15_000L }) {
            next = next.plusDays(1)
        }
        return next.toInstant().toEpochMilli()
    }

    /** 重開機或恢復後補跑檢查：錯過 <2h 且今日尚未發送則立即補發一次，逾時跳過次日 */
    fun maybeCatchUp(ctx: Context) {
        val appCtx = ctx.applicationContext
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dayKey = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

        fun checkOne(isMorning: Boolean, enabled: Boolean, hhmm: String) {
            if (!enabled) return
            if (PhantomPrefs.getLastBriefingDay(appCtx, isMorning) == dayKey) return
            val scheduledMs = today.atTime(parseHHmm(hhmm)).atZone(zone).toInstant().toEpochMilli()
            val missedBy = now - scheduledMs
            if (missedBy in 1L..CATCHUP_WINDOW_MS) {
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        ProactiveEngine.deliverBriefing(appCtx, isMorning = isMorning, force = false)
                    }
                }
            }
        }

        checkOne(true, PhantomPrefs.isMorningBriefingEnabled(appCtx), PhantomPrefs.getMorningBriefingTime(appCtx))
        checkOne(false, PhantomPrefs.isEveningBriefingEnabled(appCtx), PhantomPrefs.getEveningBriefingTime(appCtx))
    }

    fun parseHHmm(raw: String, fallback: LocalTime = LocalTime.of(8, 45)): LocalTime {
        val parts = raw.trim().split(":")
        val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: return fallback
        val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: return fallback
        return LocalTime.of(h, m)
    }
}

class BriefingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appCtx = context.applicationContext
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                BriefingScheduler.rescheduleAll(appCtx)
            }
            BriefingScheduler.ACTION_MORNING_BRIEFING -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        ProactiveEngine.deliverBriefing(appCtx, isMorning = true, force = false)
                        BriefingScheduler.rescheduleAll(appCtx)
                    } catch (_: Exception) {
                    } finally {
                        pending.finish()
                    }
                }
            }
            BriefingScheduler.ACTION_EVENING_BRIEFING -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        ProactiveEngine.deliverBriefing(appCtx, isMorning = false, force = false)
                        BriefingScheduler.rescheduleAll(appCtx)
                    } catch (_: Exception) {
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
