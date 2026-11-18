package com.phantom.checker.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 接收代理回覆通知的「回覆」、「關閉」、滑掉（deleteIntent）與閒置逾時事件。
 */
class ReplyProxyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appCtx = context.applicationContext
        val sessionKey = intent.getStringExtra(ReplyProxyManager.EXTRA_SESSION_KEY) ?: return
        when (intent.action) {
            ReplyProxyManager.ACTION_CLOSE,
            ReplyProxyManager.ACTION_TIMEOUT -> {
                ReplyProxyManager.closeSession(appCtx, sessionKey, cancelNotification = true)
            }
            ReplyProxyManager.ACTION_DISMISSED -> {
                ReplyProxyManager.closeSession(appCtx, sessionKey, cancelNotification = false)
            }
            ReplyProxyManager.ACTION_REPLY -> {
                val replyText = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(ReplyProxyManager.REMOTE_INPUT_KEY)
                    ?.toString()
                    ?.trim()
                    .orEmpty()
                if (replyText.isEmpty()) return

                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val session = ReplyProxyManager.getSession(sessionKey)
                        if (session == null) {
                            val notifId = NotifIds.replyProxy(sessionKey.hashCode())
                            try {
                                appCtx.getSystemService(NotificationManager::class.java)
                                    ?.cancel(notifId)
                            } catch (_: Exception) {
                            }
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    appCtx,
                                    "對話已結束，請開啟原 App 回覆",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            return@launch
                        }
                        val res = NotificationCollectorService.sendReplyToNotification(
                            context = appCtx,
                            packageName = session.packageName,
                            sender = session.sender,
                            contentSnippet = session.originalSnippet,
                            replyText = replyText
                        )
                        when (res) {
                            is NotificationCollectorService.Companion.ReplySendResult.Success -> {
                                ReplyProxyManager.appendMessageAndNotify(
                                    ctx = appCtx,
                                    sessionKey = sessionKey,
                                    isSelf = true,
                                    text = replyText
                                )
                            }
                            is NotificationCollectorService.Companion.ReplySendResult.Unavailable -> {
                                val isSmsFallback = session.packageName == "sms" ||
                                    TW_PHONE_REGEX.matches(session.sender.trim())
                                if (isSmsFallback) {
                                    try {
                                        @Suppress("DEPRECATION")
                                        SmsManager.getDefault().sendTextMessage(
                                            session.sender.trim(),
                                            null,
                                            replyText,
                                            null,
                                            null
                                        )
                                        ReplyProxyManager.appendMessageAndNotify(
                                            ctx = appCtx,
                                            sessionKey = sessionKey,
                                            isSelf = true,
                                            text = replyText
                                        )
                                    } catch (e: Exception) {
                                        ReplyProxyManager.appendMessageAndNotify(
                                            ctx = appCtx,
                                            sessionKey = sessionKey,
                                            isSelf = false,
                                            text = "⚠️ 無法送出：${e.message ?: "簡訊發送失敗"}，請開啟原 App 回覆"
                                        )
                                    }
                                } else {
                                    ReplyProxyManager.appendMessageAndNotify(
                                        ctx = appCtx,
                                        sessionKey = sessionKey,
                                        isSelf = false,
                                        text = "⚠️ 無法送出：原始通知已移除，請開啟原 App 回覆"
                                    )
                                }
                            }
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        private val TW_PHONE_REGEX = Regex("^09\\d{8}$")
    }
}
