package com.phantom.checker.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.widget.Toast
import com.phantom.checker.R

/**
 * #339 「確認後執行」跨 App 動作執行器：
 * - 絕不隱式執行、絕不背景執行：僅於使用者明確點擊確認卡「執行」按鈕時呼叫。
 * - 三種裝置情境皆安全降級：Gmail 缺席回落系統郵件 / chooser；Google Maps 缺席回落 geo: chooser；失敗 toast「找不到可用的 App」。
 * - 鬧鐘永遠不設 EXTRA_SKIP_UI，保留系統確認畫面。
 */
sealed class CrossAppActionProposal {
    data class GmailCompose(
        val to: String,
        val subject: String,
        val body: String
    ) : CrossAppActionProposal()

    data class MapsNavigate(
        val destination: String
    ) : CrossAppActionProposal()

    data class SetAlarm(
        val hhmm: String,
        val label: String
    ) : CrossAppActionProposal()

    data class OpenApp(
        val packageName: String
    ) : CrossAppActionProposal()

    fun title(): String = when (this) {
        is GmailCompose -> com.phantom.checker.PhantomApplication.str(R.string.cross_app_s_7dd521)
        is MapsNavigate -> com.phantom.checker.PhantomApplication.str(R.string.cross_app_s_a965fe)
        is SetAlarm -> com.phantom.checker.PhantomApplication.str(R.string.cross_app_s_a4b30e)
        is OpenApp -> com.phantom.checker.PhantomApplication.str(R.string.cross_app_s_174375)
    }

    fun summary(): String = when (this) {
        is GmailCompose -> buildString {
            if (to.isNotBlank()) appendLine(com.phantom.checker.PhantomApplication.str(R.string.cross_app_s_ed986d, to))
            if (subject.isNotBlank()) appendLine(com.phantom.checker.PhantomApplication.str(R.string.cross_app_s_2ec93e, subject))
            if (body.isNotBlank()) append(com.phantom.checker.PhantomApplication.str(R.string.cross_app_s_e64ee6, body.take(120)))
        }.trim().ifBlank { com.phantom.checker.PhantomApplication.str(R.string.cross_app_s_2ac21e) }
        is MapsNavigate -> com.phantom.checker.PhantomApplication.str(R.string.cross_app_s_bc4b0c, destination)
        is SetAlarm -> com.phantom.checker.PhantomApplication.str(R.string.cross_app_s_17ab77, hhmm) + if (label.isNotBlank()) "（$label）" else ""
        is OpenApp -> com.phantom.checker.PhantomApplication.str(R.string.cross_app_s_008270, packageName)
    }
}

object CrossAppActionExecutor {

    fun execute(ctx: Context, action: CrossAppActionProposal): Boolean {
        return when (action) {
            is CrossAppActionProposal.GmailCompose -> executeEmail(ctx, action)
            is CrossAppActionProposal.MapsNavigate -> executeMaps(ctx, action)
            is CrossAppActionProposal.SetAlarm -> executeSetAlarm(ctx, action)
            is CrossAppActionProposal.OpenApp -> executeOpenApp(ctx, action)
        }
    }

    private fun executeEmail(ctx: Context, action: CrossAppActionProposal.GmailCompose): Boolean {
        val toClean = action.to.trim()
        val mailtoUri = Uri.parse("mailto:${Uri.encode(toClean)}")
        val baseIntent = Intent(Intent.ACTION_SENDTO, mailtoUri).apply {
            if (toClean.isNotEmpty()) {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(toClean))
            }
            if (action.subject.isNotBlank()) {
                putExtra(Intent.EXTRA_SUBJECT, action.subject)
            }
            if (action.body.isNotBlank()) {
                putExtra(Intent.EXTRA_TEXT, action.body)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // 優先嘗試 Gmail（若有收件者且已安裝），否則 fallback 至系統郵件 chooser
        if (toClean.isNotEmpty()) {
            val gmailIntent = Intent(baseIntent).setPackage("com.google.android.gm")
            if (tryStart(ctx, gmailIntent)) return true
        }
        if (tryStart(ctx, Intent.createChooser(baseIntent, com.phantom.checker.PhantomApplication.str(R.string.cross_app_s_c61ded)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            return true
        }
        Toast.makeText(ctx, com.phantom.checker.PhantomApplication.str(R.string.cross_app_s_551677), Toast.LENGTH_SHORT).show()
        return false
    }

    private fun executeMaps(ctx: Context, action: CrossAppActionProposal.MapsNavigate): Boolean {
        val dest = action.destination.trim()
        if (dest.isEmpty()) {
            Toast.makeText(ctx, com.phantom.checker.PhantomApplication.str(R.string.cross_app_s_551677), Toast.LENGTH_SHORT).show()
            return false
        }
        val navIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(dest)}")).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStart(ctx, navIntent)) return true

        val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(dest)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStart(ctx, Intent.createChooser(geoIntent, com.phantom.checker.PhantomApplication.str(R.string.cross_app_s_e179a4)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            return true
        }
        Toast.makeText(ctx, com.phantom.checker.PhantomApplication.str(R.string.cross_app_s_551677), Toast.LENGTH_SHORT).show()
        return false
    }

    private fun executeSetAlarm(ctx: Context, action: CrossAppActionProposal.SetAlarm): Boolean {
        val parts = action.hhmm.trim().split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        // 嚴守鐵律：不設 EXTRA_SKIP_UI，一律由系統鬧鐘介面讓使用者確認
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            if (action.label.isNotBlank()) {
                putExtra(AlarmClock.EXTRA_MESSAGE, action.label.take(60))
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStart(ctx, intent)) return true
        Toast.makeText(ctx, com.phantom.checker.PhantomApplication.str(R.string.cross_app_s_551677), Toast.LENGTH_SHORT).show()
        return false
    }

    private fun executeOpenApp(ctx: Context, action: CrossAppActionProposal.OpenApp): Boolean {
        val pkg = action.packageName.trim()
        if (pkg.isEmpty()) {
            Toast.makeText(ctx, com.phantom.checker.PhantomApplication.str(R.string.cross_app_s_551677), Toast.LENGTH_SHORT).show()
            return false
        }
        val launch = try {
            ctx.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (_: Exception) {
            null
        }
        if (launch != null && tryStart(ctx, launch)) return true
        Toast.makeText(ctx, com.phantom.checker.PhantomApplication.str(R.string.cross_app_s_551677), Toast.LENGTH_SHORT).show()
        return false
    }

    private fun tryStart(ctx: Context, intent: Intent): Boolean {
        return try {
            ctx.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
