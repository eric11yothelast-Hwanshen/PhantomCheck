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
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.phantom.checker.R
import com.phantom.checker.ui.MainActivity
import com.phantom.checker.ui.buildRecentAppsHorizontalStripBitmap
import com.phantom.checker.util.DesktopLinkStore
import com.phantom.checker.util.NotifClickBehavior
import com.phantom.checker.util.PersistentNotifStyle
import com.phantom.checker.util.PersistentNotifSubtitleMode
import com.phantom.checker.util.PhantomPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
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
        val subtitleMode: PersistentNotifSubtitleMode,
        val linkSubtitle: String?,
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
        // R5: 背景預熱 DesktopLinkStore 記憶體快取，避免主執行緒通知建構時同步解密
        scope.launch {
            runCatching { DesktopLinkStore.warmUp(applicationContext) }
        }
        scope.launch {
            DesktopLinkStore.pairedDevicesFlow
                .drop(1)
                .collect {
                    val mode = PhantomPrefs.getPersistentNotifSubtitleMode(applicationContext)
                    if (mode == PersistentNotifSubtitleMode.LINK_STATUS || mode == PersistentNotifSubtitleMode.BOTH) {
                        performRefresh(force = true)
                    }
                }
        }
        // #512 僅於桌面連線狀態或裝置名稱變化時觸發常駐通知刷新，零輪詢開銷
        scope.launch {
            DesktopLinkClient.status
                .map { it.connState to it.connectedDesktopName }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    val mode = PhantomPrefs.getPersistentNotifSubtitleMode(applicationContext)
                    if (mode == PersistentNotifSubtitleMode.LINK_STATUS || mode == PersistentNotifSubtitleMode.BOTH) {
                        performRefresh(force = true)
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val shouldStop = !PhantomPrefs.isPersistentStatsEnabled(applicationContext) ||
            !hasPostNotificationsPermission(applicationContext)
        if (shouldStop) {
            // P0 (#543): Android 8+ startForegroundService 啟動後即使要立即停止也須先 startForeground 一次，避免 FGS 逾時系統閃退
            if (!isForegroundStarted) {
                val minimalNotif = buildNotification(
                    ctx = applicationContext,
                    today = lastToday,
                    unread = lastUnread,
                    scam = lastScam,
                    largeIcon = cachedStripBitmap
                )
                startForegroundCompat(minimalNotif)
            }
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
            if (!startForegroundCompat(initialNotif)) {
                return START_NOT_STICKY
            }
            isForegroundStarted = true
        } else if (!force && !isScreenInteractive()) {
            // 熄屏時跳過主動刷新（亮屏由 ScreenStateReceiver 一次補齊）
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
        cachedStripBitmap = null
        cachedStripPackages = emptyList()
        lastStripBitmap = null
        if (PhantomPrefs.isPersistentStatsEnabled(applicationContext) &&
            hasPostNotificationsPermission(applicationContext)
        ) {
            KeepAlivePolicy.scheduleRescue(applicationContext, delayMs = nextRescueDelayMs())
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
            if (!force && !isScreenInteractive() && !isRefreshStale()) {
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
                com.phantom.checker.widget.RiskActionWidgetProvider.updateAllIfChanged(applicationContext)
                com.phantom.checker.widget.ReminderWidgetProvider.updateAllIfChanged(applicationContext)

                val subtitleMode = PhantomPrefs.getPersistentNotifSubtitleMode(applicationContext)
                val linkSubtitle = resolveLinkStatusSubtitle(applicationContext)
                val newSig = RefreshSignature(
                    today = snap.today,
                    unread = snap.unread,
                    scam = snap.scam,
                    recentPkgs = recentPkgs,
                    latestPkg = snap.latestPackageName,
                    showIcons = showIcons,
                    style = style,
                    subtitleMode = subtitleMode,
                    linkSubtitle = linkSubtitle,
                    clickBehavior = clickBehavior
                )

                val now = System.currentTimeMillis()
                val unchanged = !force &&
                    isForegroundStarted &&
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
                    lastStripBitmap = null
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
                if (startForegroundCompat(notif)) {
                    isForegroundStarted = true
                }
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }
    }

    private fun startForegroundCompat(notification: Notification): Boolean {
        return try {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            ServiceCompat.startForeground(this, STATS_NOTIF_ID, notification, fgsType)
            true
        } catch (e: IllegalStateException) {
            android.util.Log.w("StatsForegroundService", "startForeground failed (IllegalState/NotAllowed)", e)
            stopForegroundCompat()
            stopSelf()
            false
        } catch (e: SecurityException) {
            android.util.Log.w("StatsForegroundService", "startForeground failed (SecurityException)", e)
            stopForegroundCompat()
            stopSelf()
            false
        } catch (e: Exception) {
            android.util.Log.w("StatsForegroundService", "startForeground failed", e)
            stopForegroundCompat()
            stopSelf()
            false
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForegroundStarted = false
        lastStripBitmap = null
    }

    companion object {
        const val STATS_CHANNEL_ID = "stats_summary"
        const val STATS_NOTIF_ID = NotifIds.STATS_PERSISTENT
        const val EXTRA_FORCE_REFRESH = "extra_force_refresh"
        private const val THROTTLE_MS = 5000L
        private const val DEFAULT_STALE_MS = 30 * 60 * 1000L
        private const val RESCUE_WINDOW_MS = 60_000L
        private const val RESCUE_MAX_FAST_COUNT = 3
        private const val RESCUE_FAST_DELAY_MS = 1000L
        private const val RESCUE_BACKOFF_BASE_MS = 30_000L
        private const val RESCUE_BACKOFF_MAX_MS = 5 * 60 * 1000L

        private val rescueLock = Any()
        private var lastRescueAtMs = 0L
        private var consecutiveRescueCount = 0

        private fun nextRescueDelayMs(): Long = synchronized(rescueLock) {
            val now = System.currentTimeMillis()
            if (now - lastRescueAtMs > RESCUE_WINDOW_MS) {
                consecutiveRescueCount = 1
            } else {
                consecutiveRescueCount += 1
            }
            lastRescueAtMs = now
            if (consecutiveRescueCount <= RESCUE_MAX_FAST_COUNT) {
                RESCUE_FAST_DELAY_MS
            } else {
                val shift = (consecutiveRescueCount - RESCUE_MAX_FAST_COUNT - 1).coerceAtMost(4)
                (RESCUE_BACKOFF_BASE_MS shl shift).coerceAtMost(RESCUE_BACKOFF_MAX_MS)
            }
        }

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
                    com.phantom.checker.PhantomApplication.str(R.string.common_stats),
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

        internal fun resolveLinkStatusSubtitle(ctx: Context): String? {
            val paired = DesktopLinkStore.pairedDevicesFlow.value.ifEmpty {
                DesktopLinkStore.peekPairedDevices()
            }
            if (paired.isEmpty()) return null
            val st = DesktopLinkClient.status.value
            return when (st.connState) {
                PhoneLinkConnState.CONNECTED -> {
                    val name = st.connectedDesktopName.ifBlank {
                        paired.firstOrNull { it.instanceId == st.connectedDesktopId }?.name
                            ?.ifBlank { st.connectedHostPort }
                            ?: st.connectedHostPort
                    }.ifBlank { "PC" }
                    com.phantom.checker.PhantomApplication.str(R.string.notif_stats_link_connected, name)
                }
                PhoneLinkConnState.CONNECTING, PhoneLinkConnState.RECONNECTING ->
                    com.phantom.checker.PhantomApplication.str(R.string.notif_stats_link_connecting)
                PhoneLinkConnState.DISCONNECTED ->
                    com.phantom.checker.PhantomApplication.str(R.string.notif_stats_link_disconnected)
            }
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
            val subtitleMode = PhantomPrefs.getPersistentNotifSubtitleMode(appCtx)
            val showIcons = PhantomPrefs.isPersistentNotifShowIcons(appCtx)
            val title = com.phantom.checker.PhantomApplication.str(R.string.notif_stats_s_6d05d2, today)
            val unreadText = when (style) {
                com.phantom.checker.util.PersistentNotifStyle.DETAILED ->
                    com.phantom.checker.PhantomApplication.str(R.string.notif_stats_s_fe53e3, unread, scam)
                com.phantom.checker.util.PersistentNotifStyle.COMPACT ->
                    com.phantom.checker.PhantomApplication.str(R.string.notif_stats_s_7d33b6)
            }
            val linkText = resolveLinkStatusSubtitle(appCtx)
            val text = when (subtitleMode) {
                PersistentNotifSubtitleMode.LINK_STATUS -> linkText ?: unreadText
                PersistentNotifSubtitleMode.UNREAD_COUNT -> unreadText
                PersistentNotifSubtitleMode.BOTH -> if (linkText != null) "$linkText · $unreadText" else unreadText
                PersistentNotifSubtitleMode.HIDDEN -> ""
            }

            val builder = Notification.Builder(appCtx, STATS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_phantom)
                .setContentTitle(title)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
            if (text.isNotEmpty()) {
                builder.setContentText(text)
            }

            val appliedCustomView = try {
                val rv = RemoteViews(appCtx.packageName, R.layout.notif_stats_collapsed).apply {
                    setTextViewText(R.id.notif_title, title)
                    if (text.isNotEmpty()) {
                        setTextViewText(R.id.notif_subtitle, text)
                        setViewVisibility(R.id.notif_subtitle, View.VISIBLE)
                    } else {
                        setViewVisibility(R.id.notif_subtitle, View.GONE)
                    }
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
