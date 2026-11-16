package com.phantom.checker.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.phantom.checker.R
import com.phantom.checker.ui.MainActivity
import com.phantom.checker.ui.buildRecentAppsHorizontalStripBitmap
import com.phantom.checker.util.NotifClickBehavior
import com.phantom.checker.util.PersistentNotifStyle
import com.phantom.checker.util.PhantomPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 獨立的前景服務（START_STICKY），負責維護通知欄常駐統計通知，
 * 避免 NotificationListenerService 在滑掉 App 卡片、重開機或 OEM 省電凍結時中斷統計更新。
 */
class StatsForegroundService : Service() {

    private data class RefreshSignature(
        val today: Int,
        val unread: Int,
        val scam: Int,
        val recentPkgs: List<String>,
        val latestPkg: String?,
        val showIcons: Boolean,
        val style: PersistentNotifStyle,
        val clickBehavior: NotifClickBehavior
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var screenStateReceiver: ScreenStateReceiver? = null

    @Volatile
    private var lastUpdateMs = 0L

    @Volatile
    private var pendingScheduled = false

    @Volatile
    private var pendingForceRefresh = false

    @Volatile
    private var isForegroundStarted = false

    @Volatile
    private var cachedStripPackages: List<String> = emptyList()

    @Volatile
    private var cachedStripBitmap: Bitmap? = null

    @Volatile
    private var lastSignature: RefreshSignature? = null

    private val delayedRefreshRunnable = Runnable {
        val force = pendingForceRefresh
        pendingScheduled = false
        pendingForceRefresh = false
        performRefresh(force = force)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(applicationContext)
        KeepAlivePolicy.scheduleAll(applicationContext)
        registerScreenStateReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!PhantomPrefs.isPersistentStatsEnabled(applicationContext)) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!hasPostNotificationsPermission(applicationContext)) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        val force = intent?.getBooleanExtra(EXTRA_FORCE_REFRESH, false) == true

        // 首次啟動必須立即呼叫 startForeground 以符合 Android 8+ 規範
        if (!isForegroundStarted) {
            val initialNotif = buildNotification(
                ctx = applicationContext,
                today = lastToday,
                unread = lastUnread,
                scam = lastScam,
                largeIcon = cachedStripBitmap
            )
            startForegroundCompat(initialNotif)
            isForegroundStarted = true
        } else if (!force && !isScreenInteractive()) {
            // 熄屏時跳過主動刷新（被動事件如新通知到達、KeepAlive 心跳、亮屏仍會傳 force=true）
            return START_STICKY
        }

        scheduleThrottledRefresh(force = force)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (PhantomPrefs.isPersistentStatsEnabled(applicationContext) &&
            hasPostNotificationsPermission(applicationContext)
        ) {
            startOrRefresh(applicationContext)
        }
    }

    override fun onDestroy() {
        unregisterScreenStateReceiver()
        mainHandler.removeCallbacks(delayedRefreshRunnable)
        scope.cancel()
        if (PhantomPrefs.isPersistentStatsEnabled(applicationContext) &&
            hasPostNotificationsPermission(applicationContext)
        ) {
            KeepAlivePolicy.scheduleRescue(applicationContext, delayMs = 1000L)
        }
        super.onDestroy()
    }

    private fun isScreenInteractive(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        return pm == null || pm.isInteractive
    }

    private fun hasActiveStatsNotif(): Boolean {
        val nm = getSystemService(NotificationManager::class.java) ?: return false
        return try {
            nm.activeNotifications.any { it.id == STATS_NOTIF_ID }
        } catch (_: Exception) {
            false
        }
    }

    private fun registerScreenStateReceiver() {
        if (screenStateReceiver != null) return
        val receiver = ScreenStateReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        try {
            ContextCompat.registerReceiver(
                this,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            screenStateReceiver = receiver
        } catch (_: Exception) {
        }
    }

    private fun unregisterScreenStateReceiver() {
        val receiver = screenStateReceiver ?: return
        screenStateReceiver = null
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
    }

    private fun scheduleThrottledRefresh(force: Boolean = false) {
        val now = System.currentTimeMillis()
        val elapsed = now - lastUpdateMs
        if (elapsed >= THROTTLE_MS) {
            mainHandler.removeCallbacks(delayedRefreshRunnable)
            pendingScheduled = false
            pendingForceRefresh = false
            performRefresh(force = force)
        } else {
            if (force) pendingForceRefresh = true
            if (!pendingScheduled) {
                pendingScheduled = true
                mainHandler.postDelayed(delayedRefreshRunnable, THROTTLE_MS - elapsed)
            }
        }
    }

    private fun performRefresh(force: Boolean = false) {
        lastUpdateMs = System.currentTimeMillis()
        scope.launch {
            if (!PhantomPrefs.isPersistentStatsEnabled(applicationContext) ||
                !hasPostNotificationsPermission(applicationContext)
            ) {
                stopForegroundCompat()
                stopSelf()
                return@launch
            }
            if (!force && isForegroundStarted && !isScreenInteractive()) {
                return@launch
            }
            try {
                val startOfDay = NotificationCollectorService.startOfTodayMillis()
                val dao = AppDatabaseHolder.db(applicationContext).messageDao()
                val snap = dao.statsSnapshot(startOfDay)
                val showIcons = PhantomPrefs.isPersistentNotifShowIcons(applicationContext)
                val style = PhantomPrefs.getPersistentNotifStyle(applicationContext)
                val clickBehavior = PhantomPrefs.getNotifClickBehavior(applicationContext)
                val recentPkgs = if (showIcons) snap.recentPackages() else emptyList()
                com.phantom.checker.widget.StatsWidgetProvider.updateAllIfChanged(
                    applicationContext,
                    snap.today,
                    snap.unread,
                    snap.scam
                )
                com.phantom.checker.widget.StatsMiniWidgetProvider.updateAllIfChanged(
                    applicationContext,
                    snap.today,
                    snap.scam
                )
                com.phantom.checker.widget.StatsDetailWidgetProvider.updateAllIfChanged(
                    applicationContext,
                    snap.today,
                    snap.unread,
                    snap.scam,
                    snap.recentPackages()
                )

                val newSig = RefreshSignature(
                    today = snap.today,
                    unread = snap.unread,
                    scam = snap.scam,
                    recentPkgs = recentPkgs,
                    latestPkg = snap.latestPackageName,
                    showIcons = showIcons,
                    style = style,
                    clickBehavior = clickBehavior
                )

                val now = System.currentTimeMillis()
                val unchanged = isForegroundStarted &&
                    hasActiveStatsNotif() &&
                    lastSignature == newSig &&
                    !isRefreshStale()

                if (unchanged) {
                    // 數據簽名未變且未逾 staleness，僅更新時間戳並跳過重建通知
                    lastRefreshMs = now
                    return@launch
                }

                if (showIcons) {
                    if (recentPkgs != cachedStripPackages || cachedStripBitmap == null) {
                        cachedStripPackages = recentPkgs
                        cachedStripBitmap = buildRecentAppsHorizontalStripBitmap(applicationContext, recentPkgs)
                    }
                } else {
                    cachedStripPackages = emptyList()
                    cachedStripBitmap = null
                }

                lastToday = snap.today
                lastUnread = snap.unread
                lastScam = snap.scam
                lastStripBitmap = cachedStripBitmap
                lastLatestPkg = snap.latestPackageName
                lastSignature = newSig
                lastRefreshMs = now

                val notif = buildNotification(
                    ctx = applicationContext,
                    today = snap.today,
                    unread = snap.unread,
                    scam = snap.scam,
                    largeIcon = cachedStripBitmap,
                    latestPackageName = snap.latestPackageName
                )
                // 有變化、逾時或通知遭滑掉時呼叫 startForeground 重建
                startForegroundCompat(notif)
                isForegroundStarted = true
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                STATS_NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(STATS_NOTIF_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForegroundStarted = false
    }

    companion object {
        const val STATS_CHANNEL_ID = "stats_summary"
        const val STATS_NOTIF_ID = 1001
        const val EXTRA_FORCE_REFRESH = "extra_force_refresh"
        private const val THROTTLE_MS = 5000L
        private const val DEFAULT_STALE_MS = 30 * 60 * 1000L

        @Volatile private var lastToday = 0
        @Volatile private var lastUnread = 0
        @Volatile private var lastScam = 0
        @Volatile private var lastStripBitmap: Bitmap? = null
        @Volatile private var lastLatestPkg: String? = null
        @Volatile var lastRefreshMs: Long = 0L

        fun isRefreshStale(maxAgeMs: Long = DEFAULT_STALE_MS): Boolean {
            val last = lastRefreshMs
            return last == 0L || (System.currentTimeMillis() - last) >= maxAgeMs
        }

        fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(
                    STATS_CHANNEL_ID,
                    "統計",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        private fun buildContentIntent(ctx: Context, latestPackageName: String?): PendingIntent {
            val behavior = PhantomPrefs.getNotifClickBehavior(ctx)
            val targetIntent = if (behavior == NotifClickBehavior.OPEN_ORIGINAL_APP && !latestPackageName.isNullOrBlank()) {
                ctx.packageManager.getLaunchIntentForPackage(latestPackageName)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } else {
                null
            } ?: Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                ctx,
                STATS_NOTIF_ID,
                targetIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun buildNotification(
            ctx: Context,
            today: Int,
            unread: Int,
            scam: Int,
            largeIcon: Bitmap? = null,
            latestPackageName: String? = lastLatestPkg
        ): Notification {
            val appCtx = ctx.applicationContext
            ensureChannel(appCtx)
            val pendingIntent = buildContentIntent(appCtx, latestPackageName)
            val style = PhantomPrefs.getPersistentNotifStyle(appCtx)
            val showIcons = PhantomPrefs.isPersistentNotifShowIcons(appCtx)
            val title = "幻訊・今日 $today 則訊息"
            val text = when (style) {
                com.phantom.checker.util.PersistentNotifStyle.DETAILED ->
                    "未讀 $unread 則・詐騙警示 $scam 則"
                com.phantom.checker.util.PersistentNotifStyle.COMPACT ->
                    "常駐監控中"
            }

            val builder = Notification.Builder(appCtx, STATS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_phantom)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)

            val appliedCustomView = try {
                val rv = RemoteViews(appCtx.packageName, R.layout.notif_stats_collapsed).apply {
                    setTextViewText(R.id.notif_title, title)
                    setTextViewText(R.id.notif_subtitle, text)
                    if (showIcons && largeIcon != null) {
                        setImageViewBitmap(R.id.notif_recent_strip, largeIcon)
                        setViewVisibility(R.id.notif_recent_strip, View.VISIBLE)
                    } else {
                        setViewVisibility(R.id.notif_recent_strip, View.GONE)
                    }
                }
                builder.setCustomContentView(rv)
                builder.setStyle(Notification.DecoratedCustomViewStyle())
                true
            } catch (_: Exception) {
                false
            }

            if (!appliedCustomView && showIcons && largeIcon != null) {
                builder.setLargeIcon(largeIcon)
            }

            val notif = builder.build()
            notif.flags = notif.flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT
            return notif
        }

        /** 由 NotificationCollectorService.onNotificationRemoved 直接重發，不經 startForegroundService */
        fun repostDirectly(ctx: Context) {
            val appCtx = ctx.applicationContext
            if (!PhantomPrefs.isPersistentStatsEnabled(appCtx) ||
                !hasPostNotificationsPermission(appCtx)
            ) {
                return
            }
            val nm = appCtx.getSystemService(NotificationManager::class.java) ?: return
            val notif = buildNotification(
                ctx = appCtx,
                today = lastToday,
                unread = lastUnread,
                scam = lastScam,
                largeIcon = lastStripBitmap,
                latestPackageName = lastLatestPkg
            )
            try {
                nm.notify(STATS_NOTIF_ID, notif)
            } catch (_: SecurityException) {
            }
        }

        fun hasPostNotificationsPermission(ctx: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    ctx,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }

        fun startOrRefresh(ctx: Context, force: Boolean = false) {
            val appCtx = ctx.applicationContext
            if (!PhantomPrefs.isPersistentStatsEnabled(appCtx)) {
                stop(appCtx)
                return
            }
            if (!hasPostNotificationsPermission(appCtx)) {
                return
            }
            val intent = Intent(appCtx, StatsForegroundService::class.java).apply {
                putExtra(EXTRA_FORCE_REFRESH, force)
            }
            try {
                ContextCompat.startForegroundService(appCtx, intent)
            } catch (_: Exception) {
            }
        }

        fun stop(ctx: Context) {
            val appCtx = ctx.applicationContext
            try {
                appCtx.stopService(Intent(appCtx, StatsForegroundService::class.java))
                appCtx.getSystemService(NotificationManager::class.java)?.cancel(STATS_NOTIF_ID)
            } catch (_: Exception) {
            }
        }
    }
}
