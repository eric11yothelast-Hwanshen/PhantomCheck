package com.phantom.checker.shizuku

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import com.phantom.checker.service.NotificationCollectorService
import com.phantom.checker.util.PhantomPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.phantom.checker.R

data class ShizukuDiagItem(
    val label: String,
    val ok: Boolean,
    val detail: String
)

/**
 * 透過 Shizuku 執行本 App 保活強化、權限自癒、與使用者明確指定的可疑 App 處置（force-stop / suspend）。
 * 所有指令僅針對 com.phantom.checker 本身或使用者確認後指定的目標套件，絕不擅自異動其他 App。
 */
object ShizukuKeepAlive {

    private val SAFE_PKG_REGEX = Regex("^[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+$")

    private fun sanitizePackage(pkg: String): String? {
        val trimmed = pkg.trim()
        return if (SAFE_PKG_REGEX.matches(trimmed)) trimmed else null
    }

    suspend fun applyKeepAliveEnhancements(ctx: Context): List<ShizukuDiagItem> =
        withContext(Dispatchers.IO) {
            val appCtx = ctx.applicationContext
            val pkg = appCtx.packageName
            val results = mutableListOf<ShizukuDiagItem>()

            val idleRes = ShizukuManager.execCommand(appCtx, "dumpsys deviceidle whitelist +$pkg")
            results += ShizukuDiagItem(
                label = com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_d30212),
                ok = idleRes.isSuccess,
                detail = idleRes.output.ifBlank { if (idleRes.isSuccess) com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_7def19) else com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_a1d778) }
            )

            val ops = listOf(
                "RUN_IN_BACKGROUND",
                "RUN_ANY_IN_BACKGROUND",
                "WAKE_LOCK",
                "SCHEDULE_EXACT_ALARM"
            )
            var allOpsOk = true
            ops.forEach { op ->
                val r = ShizukuManager.execCommand(appCtx, "cmd appops set $pkg $op allow")
                if (!r.isSuccess) allOpsOk = false
            }
            results += ShizukuDiagItem(
                label = com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_7145fc),
                ok = allOpsOk,
                detail = if (allOpsOk) com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_a64090) else com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_ce31f3)
            )

            val bucketRes = ShizukuManager.execCommand(appCtx, "am set-standby-bucket $pkg active")
            results += ShizukuDiagItem(
                label = "App Standby Bucket",
                ok = bucketRes.isSuccess,
                detail = if (bucketRes.isSuccess) com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_6bd996) else bucketRes.output
            )

            val listenerHealed = healNotificationListener(appCtx)
            results += ShizukuDiagItem(
                label = com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_dacf5c),
                ok = listenerHealed.isSuccess,
                detail = listenerHealed.output
            )

            val permRes = grantSelfRuntimePermissions(appCtx)
            results += ShizukuDiagItem(
                label = com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_a629b6),
                ok = permRes.isSuccess,
                detail = permRes.output
            )

            if (PhantomPrefs.isShizukuWatchdogEnabled(appCtx) &&
                PhantomPrefs.isPersistentStatsEnabled(appCtx)
            ) {
                val wdOk = ShizukuManager.startWatchdog(appCtx)
                results += ShizukuDiagItem(
                    label = com.phantom.checker.PhantomApplication.str(R.string.settings_s_bde005),
                    ok = wdOk,
                    detail = if (wdOk) com.phantom.checker.PhantomApplication.str(R.string.settings_s_239685) else com.phantom.checker.PhantomApplication.str(R.string.chat_s_94c54a)
                )
            }

            results
        }

    suspend fun healNotificationListener(ctx: Context): ShellResult = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val safePkg = sanitizePackage(appCtx.packageName)
            ?: return@withContext ShellResult(1, com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_99cbc2))
        val component = "$safePkg/${NotificationCollectorService::class.java.name}"
        val allowRes = ShizukuManager.execCommand(appCtx, "cmd notification allow_listener $component")
        if (!allowRes.isSuccess) return@withContext allowRes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                NotificationListenerService.requestRebind(
                    ComponentName(appCtx, NotificationCollectorService::class.java)
                )
            } catch (_: Exception) {
            }
        }
        ShellResult(0, com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_1f3300))
    }

    suspend fun grantSelfRuntimePermissions(ctx: Context): ShellResult = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val pkg = appCtx.packageName
        com.phantom.checker.service.AutoReplyConfig.ensureLoaded(appCtx)
        val smsAutoReplyEnabled = com.phantom.checker.service.AutoReplyConfig.enabled
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add("android.permission.POST_NOTIFICATIONS")
            }
            if (smsAutoReplyEnabled) {
                add("android.permission.RECEIVE_SMS")
                add("android.permission.SEND_SMS")
            }
        }
        var okCount = 0
        perms.forEach { perm ->
            val r = ShizukuManager.execCommand(appCtx, "pm grant $pkg $perm")
            if (r.isSuccess) okCount++
        }
        ShellResult(
            if (okCount == perms.size) 0 else 1,
            com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_956ab1, okCount, perms.size)
        )
    }

    suspend fun runDiagnostics(ctx: Context): List<ShizukuDiagItem> = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val pkg = appCtx.packageName
        val items = mutableListOf<ShizukuDiagItem>()

        val idleRes = ShizukuManager.execCommand(appCtx, "dumpsys deviceidle whitelist")
        val inWhitelist = idleRes.isSuccess && idleRes.output.lineSequence().any { line ->
            line.split(',', ' ', '\t').any { token -> token.trim() == pkg }
        }
        items += ShizukuDiagItem(
            label = com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_f1578c),
            ok = inWhitelist,
            detail = if (inWhitelist) com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_3c5e91) else com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_3436c3)
        )

        val appOpsRes = ShizukuManager.execCommand(appCtx, "cmd appops get $pkg RUN_ANY_IN_BACKGROUND")
        val bgAllowed = appOpsRes.isSuccess && appOpsRes.output.contains("allow", ignoreCase = true)
        items += ShizukuDiagItem(
            label = com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_3d8723),
            ok = bgAllowed,
            detail = appOpsRes.output.ifBlank { if (bgAllowed) "allow" else com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_936333) }
        )

        val bucketRes = ShizukuManager.execCommand(appCtx, "am get-standby-bucket $pkg")
        val bucketText = bucketRes.output.trim().ifBlank { com.phantom.checker.PhantomApplication.str(R.string.common_unknown) }
        items += ShizukuDiagItem(
            label = "Standby Bucket",
            ok = bucketRes.isSuccess && (bucketText.contains("active", true) || bucketText == "10" || bucketText == "5"),
            detail = bucketText
        )

        val listenerRes = ShizukuManager.execCommand(appCtx, "settings get secure enabled_notification_listeners")
        val fullComponent = "$pkg/${NotificationCollectorService::class.java.name}"
        val shortComponent = "$pkg/.service.NotificationCollectorService"
        val listenerOk = listenerRes.isSuccess && listenerRes.output.trim()
            .takeIf { it.isNotEmpty() && it != "null" }
            ?.split(':')
            ?.map { it.trim() }
            ?.any { entry -> entry == fullComponent || entry == shortComponent } == true
        items += ShizukuDiagItem(
            label = com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_a1fe64),
            ok = listenerOk,
            detail = if (listenerOk) com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_9270a9) else com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_7d0d75)
        )

        ShizukuManager.refreshState(appCtx)
        val wdActive = ShizukuManager.state.value.isWatchdogActive
        items += ShizukuDiagItem(
            label = com.phantom.checker.PhantomApplication.str(R.string.settings_s_bde005),
            ok = wdActive,
            detail = if (wdActive) com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_41215a) else com.phantom.checker.PhantomApplication.str(R.string.settings_s_a38205)
        )

        items
    }

    suspend fun forceStopTargetApp(ctx: Context, targetPackage: String): ShellResult =
        withContext(Dispatchers.IO) {
            val safePkg = sanitizePackage(targetPackage)
                ?: return@withContext ShellResult(1, com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_99cbc2))
            if (safePkg == ctx.packageName) {
                return@withContext ShellResult(1, com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_b97c00))
            }
            ShizukuManager.execCommand(ctx, "am force-stop $safePkg")
        }

    suspend fun suspendTargetApp(ctx: Context, targetPackage: String): ShellResult =
        withContext(Dispatchers.IO) {
            val safePkg = sanitizePackage(targetPackage)
                ?: return@withContext ShellResult(1, com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_99cbc2))
            if (safePkg == ctx.packageName) {
                return@withContext ShellResult(1, com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_46d51b))
            }
            val res = ShizukuManager.execCommand(ctx, "pm suspend $safePkg")
            if (res.isSuccess) {
                PhantomPrefs.addShizukuSuspendedApp(ctx, safePkg)
            }
            res
        }

    suspend fun unsuspendTargetApp(ctx: Context, targetPackage: String): ShellResult =
        withContext(Dispatchers.IO) {
            val safePkg = sanitizePackage(targetPackage)
                ?: return@withContext ShellResult(1, com.phantom.checker.PhantomApplication.str(R.string.shizuku_s_99cbc2))
            val res = ShizukuManager.execCommand(ctx, "pm unsuspend $safePkg")
            if (res.isSuccess) {
                PhantomPrefs.removeShizukuSuspendedApp(ctx, safePkg)
            }
            res
        }
}
