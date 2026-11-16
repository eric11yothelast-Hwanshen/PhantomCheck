package com.phantom.checker.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.phantom.checker.util.PhantomPrefs

/**
 * 機會式喚醒接收器（動態註冊於 StatsForegroundService）：
 * 監聽 SCREEN_ON / USER_PRESENT / CONNECTIVITY_CHANGE，60 秒節流，
 * 當通知消失或超過 30 分鐘未刷新時主動喚起 StatsForegroundService 與排程檢查。
 */
class ScreenStateReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val appCtx = ctx.applicationContext
        val now = System.currentTimeMillis()
        val isScreenOn = intent.action == Intent.ACTION_SCREEN_ON

        if (isScreenOn) {
            if (now - lastScreenOnTriggerMs < SCREEN_ON_DEBOUNCE_MS) return
            lastScreenOnTriggerMs = now
        } else {
            if (now - lastTriggerMs < THROTTLE_MS) return
            lastTriggerMs = now
        }

        if (!PhantomPrefs.isPersistentStatsEnabled(appCtx) ||
            !StatsForegroundService.hasPostNotificationsPermission(appCtx)
        ) {
            return
        }

        if (isScreenOn) {
            // 亮屏立即刷新一次（performRefresh 內有差異比對，數據未變會跳過重建）
            StatsForegroundService.startOrRefresh(appCtx, force = true)
            KeepAlivePolicy.ensureScheduled(appCtx)
            return
        }

        val nm = appCtx.getSystemService(NotificationManager::class.java)
        val hasStatsNotif = nm?.activeNotifications?.any {
            it.id == StatsForegroundService.STATS_NOTIF_ID
        } == true

        if (!hasStatsNotif || StatsForegroundService.isRefreshStale()) {
            StatsForegroundService.startOrRefresh(appCtx, force = true)
        }
        KeepAlivePolicy.ensureScheduled(appCtx)
    }

    companion object {
        private const val THROTTLE_MS = 60_000L
        private const val SCREEN_ON_DEBOUNCE_MS = 3_000L

        @Volatile
        private var lastTriggerMs = 0L

        @Volatile
        private var lastScreenOnTriggerMs = 0L
    }
}
