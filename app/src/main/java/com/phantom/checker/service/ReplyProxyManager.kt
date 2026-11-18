package com.phantom.checker.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.phantom.checker.R
import com.phantom.checker.util.PhantomPrefs

/**
 * 通知欄連續快速回覆代理管理器（Multi-turn Inline Reply）。
 *
 * 注意：Session 狀態採 in-memory LRU（上限 8 組），App 被系統回收或重開機後即清空，
 * 不寫入 Room 資料庫（避免膨脹與額外 migration）。
 */
object ReplyProxyManager {

    private const val TAG = "PhantomReply"
    const val CHANNEL_ID = "phantom_reply_proxy"
    const val REMOTE_INPUT_KEY = "proxy_reply"
    const val ACTION_REPLY = "com.phantom.checker.ACTION_PROXY_REPLY"
    const val ACTION_CLOSE = "com.phantom.checker.ACTION_PROXY_CLOSE"
    const val ACTION_DISMISSED = "com.phantom.checker.ACTION_PROXY_DISMISSED"
    const val ACTION_TIMEOUT = "com.phantom.checker.ACTION_PROXY_TIMEOUT"
    const val EXTRA_SESSION_KEY = "extra_proxy_session_key"

    private const val MAX_SESSIONS = 8
    private const val MAX_HISTORY_MESSAGES = 8

    data class ReplyHistoryEntry(
        val isSelf: Boolean,
        val text: String,
        val timestamp: Long
    )

    data class ProxySession(
        val sessionKey: String,
        val packageName: String,
        val appLabel: String,
        val sender: String,
        val originalSnippet: String,
        val history: MutableList<ReplyHistoryEntry>,
        var lastActivityAt: Long,
        val notificationId: Int
    )

    private val lock = Any()
    private val sessions = object : LinkedHashMap<String, ProxySession>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ProxySession>?): Boolean {
            return size > MAX_SESSIONS
        }
    }

    fun buildSessionKey(packageName: String, sender: String): String =
        "$packageName|${sender.trim()}"

    fun getSession(sessionKey: String): ProxySession? = synchronized(lock) {
        sessions[sessionKey]
    }

    fun startOrRefreshSession(
        ctx: Context,
        packageName: String,
        appLabel: String,
        sender: String,
        originalSnippet: String,
        initialReplyText: String? = null
    ) {
        val appCtx = ctx.applicationContext
        if (!PhantomPrefs.isReplyProxyEnabled(appCtx)) return
        val key = buildSessionKey(packageName, sender)
        val now = System.currentTimeMillis()
        val displaySender = sender.trim().ifBlank { appLabel.ifBlank { packageName } }
        val displayApp = appLabel.ifBlank { packageName }

        var evicted: ProxySession? = null
        val session: ProxySession = synchronized(lock) {
            val existing = sessions[key]
            if (existing != null) {
                if (!initialReplyText.isNullOrBlank()) {
                    appendEntryLocked(existing, isSelf = true, text = initialReplyText.trim(), ts = now)
                }
                existing.lastActivityAt = now
                existing
            } else {
                if (sessions.size >= MAX_SESSIONS) {
                    val eldestKey = sessions.keys.firstOrNull()
                    if (eldestKey != null) {
                        evicted = sessions.remove(eldestKey)
                    }
                }
                val history = mutableListOf(
                    ReplyHistoryEntry(
                        isSelf = false,
                        text = originalSnippet.trim().take(200).ifBlank { "（無訊息預覽）" },
                        timestamp = now
                    )
                )
                if (!initialReplyText.isNullOrBlank()) {
                    history.add(
                        ReplyHistoryEntry(
                            isSelf = true,
                            text = initialReplyText.trim(),
                            timestamp = now + 1L
                        )
                    )
                }
                val created = ProxySession(
                    sessionKey = key,
                    packageName = packageName,
                    appLabel = displayApp,
                    sender = displaySender,
                    originalSnippet = originalSnippet.trim().take(200),
                    history = history,
                    lastActivityAt = now,
                    notificationId = NotifIds.replyProxy(key.hashCode())
                )
                sessions[key] = created
                created
            }
        }

        evicted?.let { old ->
            cancelTimeoutAlarm(appCtx, old.sessionKey)
            try {
                appCtx.getSystemService(NotificationManager::class.java)?.cancel(old.notificationId)
            } catch (_: Exception) {
            }
        }

        postOrUpdateNotification(appCtx, session)
        scheduleIdleTimeout(appCtx, session)
    }

    fun appendMessageAndNotify(
        ctx: Context,
        sessionKey: String,
        isSelf: Boolean,
        text: String
    ) {
        val appCtx = ctx.applicationContext
        val now = System.currentTimeMillis()
        val session = synchronized(lock) {
            val s = sessions[sessionKey] ?: return
            appendEntryLocked(s, isSelf = isSelf, text = text, ts = now)
            s.lastActivityAt = now
            s
        }
        postOrUpdateNotification(appCtx, session)
        scheduleIdleTimeout(appCtx, session)
    }

    fun closeSession(
        ctx: Context,
        sessionKey: String,
        cancelNotification: Boolean = true
    ) {
        val appCtx = ctx.applicationContext
        val removed = synchronized(lock) { sessions.remove(sessionKey) }
        cancelTimeoutAlarm(appCtx, sessionKey)
        if (cancelNotification) {
            val notifId = removed?.notificationId ?: NotifIds.replyProxy(sessionKey.hashCode())
            try {
                appCtx.getSystemService(NotificationManager::class.java)?.cancel(notifId)
            } catch (_: Exception) {
            }
        }
    }

    private fun appendEntryLocked(session: ProxySession, isSelf: Boolean, text: String, ts: Long) {
        session.history.add(ReplyHistoryEntry(isSelf = isSelf, text = text, timestamp = ts))
        while (session.history.size > MAX_HISTORY_MESSAGES) {
            session.history.removeAt(0)
        }
    }

    private fun ensureChannel(ctx: Context) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "快速回覆對話",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "透過 AI 助手回覆後保留於通知欄，方便連續回覆同一對話"
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "ensureChannel failed", e)
        }
    }

    private fun postOrUpdateNotification(ctx: Context, session: ProxySession) {
        try {
            ensureChannel(ctx)
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return

            val selfPerson = Person.Builder().setName("我").build()
            val senderPerson = Person.Builder().setName(session.sender).build()
            val style = NotificationCompat.MessagingStyle(selfPerson)
                .setConversationTitle("${session.sender}（${session.appLabel}）")
                .setGroupConversation(false)

            val snapshotHistory = synchronized(lock) { session.history.toList() }
            snapshotHistory.forEach { entry ->
                style.addMessage(
                    entry.text,
                    entry.timestamp,
                    if (entry.isSelf) selfPerson else senderPerson
                )
            }

            val replyIntent = Intent(ctx, ReplyProxyReceiver::class.java).apply {
                action = ACTION_REPLY
                putExtra(EXTRA_SESSION_KEY, session.sessionKey)
            }
            val replyPi = PendingIntent.getBroadcast(
                ctx,
                reqCode(session.sessionKey, 1),
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY)
                .setLabel("繼續回覆 ${session.sender}…")
                .build()
            val replyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_stat_phantom,
                "回覆",
                replyPi
            ).addRemoteInput(remoteInput).build()

            val closeIntent = Intent(ctx, ReplyProxyReceiver::class.java).apply {
                action = ACTION_CLOSE
                putExtra(EXTRA_SESSION_KEY, session.sessionKey)
            }
            val closePi = PendingIntent.getBroadcast(
                ctx,
                reqCode(session.sessionKey, 2),
                closeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val closeAction = NotificationCompat.Action.Builder(
                0,
                "關閉",
                closePi
            ).build()

            val dismissIntent = Intent(ctx, ReplyProxyReceiver::class.java).apply {
                action = ACTION_DISMISSED
                putExtra(EXTRA_SESSION_KEY, session.sessionKey)
            }
            val dismissPi = PendingIntent.getBroadcast(
                ctx,
                reqCode(session.sessionKey, 3),
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_phantom)
                .setStyle(style)
                .setContentTitle("${session.sender}（${session.appLabel}）")
                .setContentText(snapshotHistory.lastOrNull()?.text.orEmpty())
                .addAction(replyAction)
                .addAction(closeAction)
                .setDeleteIntent(dismissPi)
                .setAutoCancel(false)
                .setOngoing(false)
                .setOnlyAlertOnce(true)
                .build()

            try {
                nm.notify(session.notificationId, notif)
                Log.d(TAG, "Posted proxy notification id=${session.notificationId} key=${session.sessionKey}")
            } catch (e: SecurityException) {
                Log.w(TAG, "postOrUpdateNotification SecurityException (missing POST_NOTIFICATIONS?)", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "postOrUpdateNotification failed key=${session.sessionKey}", e)
        }
    }

    private fun scheduleIdleTimeout(ctx: Context, session: ProxySession) {
        try {
            val minutes = PhantomPrefs.getReplyProxyTimeoutMinutes(ctx)
            cancelTimeoutAlarm(ctx, session.sessionKey)
            if (minutes <= 0) return

            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val triggerAt = System.currentTimeMillis() + minutes * 60L * 1000L
            val pi = buildTimeoutPendingIntent(ctx, session.sessionKey)
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } catch (_: SecurityException) {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: Exception) {
            Log.e(TAG, "scheduleIdleTimeout failed key=${session.sessionKey}", e)
        }
    }

    private fun cancelTimeoutAlarm(ctx: Context, sessionKey: String) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pi = buildTimeoutPendingIntent(ctx, sessionKey)
            am.cancel(pi)
            pi.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "cancelTimeoutAlarm failed key=$sessionKey", e)
        }
    }

    private fun buildTimeoutPendingIntent(ctx: Context, sessionKey: String): PendingIntent {
        val intent = Intent(ctx, ReplyProxyReceiver::class.java).apply {
            action = ACTION_TIMEOUT
            putExtra(EXTRA_SESSION_KEY, sessionKey)
        }
        return PendingIntent.getBroadcast(
            ctx,
            reqCode(sessionKey, 4),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun reqCode(sessionKey: String, salt: Int): Int {
        return (sessionKey.hashCode() * 31) + salt
    }
}
