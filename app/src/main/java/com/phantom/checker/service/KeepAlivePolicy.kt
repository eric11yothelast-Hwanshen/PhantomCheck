package com.phantom.checker.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.phantom.checker.ui.MainActivity
import com.phantom.checker.util.PhantomPrefs

/**
 * 多層保活排程核心：
 * 1. AlarmManager 45 分鐘自鏈 Watchdog（支援精確鬧鐘與 setAndAllowWhileIdle 降級）
 * 2. 8 小時 setAlarmClock() 安全網（最高優先權喚醒）
 * 3. onDestroy / onListenerDisconnected 1 秒自救重啟鬧鐘
 */
object KeepAlivePolicy {

    const val ACTION_WATCHDOG = "com.phantom.checker.action.KEEP_ALIVE_WATCHDOG"
    const val ACTION_SAFETY_NET = "com.phantom.checker.action.KEEP_ALIVE_SAFETY_NET"
    const val ACTION_RESCUE = "com.phantom.checker.action.KEEP_ALIVE_RESCUE"
    const val ACTION_REBIND_FALLBACK = "com.phantom.checker.action.REBIND_FALLBACK"

    private const val REQ_WATCHDOG = 2001
    private const val REQ_SAFETY_NET = 2002
    private const val REQ_RESCUE = 2003
    private const val REQ_REBIND = 2004
    private const val REQ_ALARM_SHOW = 2005

    private const val WATCHDOG_INTERVAL_MS = 45 * 60 * 1000L
    private const val SAFETY_NET_INTERVAL_MS = 8 * 60 * 60 * 1000L

    fun scheduleAll(ctx: Context) {
        val appCtx = ctx.applicationContext
        HeartbeatPolicy.schedule(appCtx)
        if (!PhantomPrefs.isPersistentStatsEnabled(appCtx)) {
            cancelAll(appCtx)
            return
        }
        scheduleWatchdog(appCtx)
        scheduleAlarmClockSafetyNet(appCtx)
    }

    fun ensureScheduled(ctx: Context) {
        val appCtx = ctx.applicationContext
        if (!PhantomPrefs.isPersistentStatsEnabled(appCtx)) {
            cancelAll(appCtx)
            HeartbeatPolicy.schedule(appCtx)
            return
        }
        val am = appCtx.getSystemService(AlarmManager::class.java) ?: return
        val existingWatchdog = PendingIntent.getBroadcast(
            appCtx,
            REQ_WATCHDOG,
            Intent(appCtx, WatchdogReceiver::class.java).setAction(ACTION_WATCHDOG),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (existingWatchdog == null) {
            scheduleWatchdog(appCtx, am)
        }
        val existingSafetyNet = PendingIntent.getBroadcast(
            appCtx,
            REQ_SAFETY_NET,
            Intent(appCtx, WatchdogReceiver::class.java).setAction(ACTION_SAFETY_NET),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (existingSafetyNet == null) {
            scheduleAlarmClockSafetyNet(appCtx, am)
        }
    }

    fun scheduleWatchdog(ctx: Context, alarmManager: AlarmManager? = null) {
        val appCtx = ctx.applicationContext
        if (!PhantomPrefs.isPersistentStatsEnabled(appCtx)) {
            cancelWatchdog(appCtx)
            return
        }
        val am = alarmManager ?: appCtx.getSystemService(AlarmManager::class.java) ?: return
        val pi = watchdogPendingIntent(appCtx, ACTION_WATCHDOG, REQ_WATCHDOG)
        val triggerAt = SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS
        scheduleElapsedAlarm(am, triggerAt, pi)
    }

    fun scheduleAlarmClockSafetyNet(ctx: Context, alarmManager: AlarmManager? = null) {
        val appCtx = ctx.applicationContext
        if (!PhantomPrefs.isPersistentStatsEnabled(appCtx)) {
            cancelSafetyNet(appCtx)
            return
        }
        val am = alarmManager ?: appCtx.getSystemService(AlarmManager::class.java) ?: return
        val fireIntent = watchdogPendingIntent(appCtx, ACTION_SAFETY_NET, REQ_SAFETY_NET)
        val showIntent = PendingIntent.getActivity(
            appCtx,
            REQ_ALARM_SHOW,
            Intent(appCtx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerWallClock = System.currentTimeMillis() + SAFETY_NET_INTERVAL_MS
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerWallClock, showIntent), fireIntent)
                return
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }
        try {
            am.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerWallClock,
                fireIntent
            )
        } catch (_: Exception) {
        }
    }

    fun scheduleRescue(ctx: Context, delayMs: Long = 1000L) {
        val appCtx = ctx.applicationContext
        if (!PhantomPrefs.isPersistentStatsEnabled(appCtx)) return
        val am = appCtx.getSystemService(AlarmManager::class.java) ?: return
        val pi = watchdogPendingIntent(appCtx, ACTION_RESCUE, REQ_RESCUE)
        val triggerAt = SystemClock.elapsedRealtime() + delayMs.coerceAtLeast(500L)
        scheduleElapsedAlarm(am, triggerAt, pi)
    }

    fun scheduleRebindFallback(ctx: Context, delayMs: Long = 5000L) {
        val appCtx = ctx.applicationContext
        val am = appCtx.getSystemService(AlarmManager::class.java) ?: return
        val pi = watchdogPendingIntent(appCtx, ACTION_REBIND_FALLBACK, REQ_REBIND)
        val triggerAt = SystemClock.elapsedRealtime() + delayMs.coerceAtLeast(1000L)
        scheduleElapsedAlarm(am, triggerAt, pi)
    }

    fun cancelAll(ctx: Context) {
        val appCtx = ctx.applicationContext
        cancelWatchdog(appCtx)
        cancelSafetyNet(appCtx)
    }

    private fun cancelWatchdog(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(watchdogPendingIntent(ctx, ACTION_WATCHDOG, REQ_WATCHDOG))
    }

    private fun cancelSafetyNet(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(watchdogPendingIntent(ctx, ACTION_SAFETY_NET, REQ_SAFETY_NET))
    }

    private fun scheduleElapsedAlarm(am: AlarmManager, triggerAtElapsed: Long, pi: PendingIntent) {
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, pi)
            }
        } catch (_: SecurityException) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, pi)
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }
    }

    private fun watchdogPendingIntent(ctx: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(ctx, WatchdogReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            ctx,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
