package com.phantom.checker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.phantom.checker.data.AppDatabase
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.network.MistralClient
import com.phantom.checker.ui.ScamCenterActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 核心：通知攔截服務。
 * 讀取通知純粹是「被動接收系統廣播」，不會開啟原 App，因此不觸發已讀。
 */
class NotificationCollectorService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: return
        val text = extras.getCharSequence("android.text")?.toString() ?: return
        val pkg = sbn.packageName

        scope.launch {
            val db = AppDatabaseHolder.db(applicationContext)
            val rules = db.filterDao().active()
            val filtered = rules.any { rule ->
                if (rule.packageName != null && rule.packageName != pkg) return@any false
                text.contains(rule.keyword, ignoreCase = true)
            }

            val id = db.messageDao().insert(
                MessageEntity(
                    packageName = pkg,
                    appLabel = appLabel(pkg),
                    sender = title,
                    content = text,
                    timestamp = sbn.postTime,
                    isFiltered = filtered
                )
            )

            if (!filtered) {
                MistralClient.analyzeRisk(title, text)?.let { (risk, label, reason) ->
                    val msg = db.messageDao().recent().firstOrNull { it.id == id }
                    if (msg != null) db.messageDao().update(
                        msg.copy(riskScore = risk, riskLabel = label, riskReason = reason)
                    )
                    if (label == "scam") alertScam(applicationContext, title, reason)
                }
            }
        }
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(pkg, 0)
        ).toString()
    } catch (e: Exception) { pkg }

    private fun alertScam(ctx: Context, sender: String, reason: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("scam_alert", "詐騙警示", NotificationManager.IMPORTANCE_HIGH)
        )
        val intent = Intent(ctx, ScamCenterActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            ctx,
            sender.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = android.app.Notification.Builder(ctx, "scam_alert")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ 偵測到高風險訊息：" + sender)
            .setContentText(reason)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        nm.notify(sender.hashCode(), notif)
    }
}

/** Database 單例 */
object AppDatabaseHolder {
    @Volatile private var instance: AppDatabase? = null
    fun db(ctx: Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: androidx.room.Room.databaseBuilder(
                ctx, AppDatabase::class.java, "phantomcheck.db"
            ).addMigrations(AppDatabase.MIGRATION_1_2).build().also { instance = it }
        }
}
