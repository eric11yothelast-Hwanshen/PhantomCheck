package com.phantom.checker.shizuku

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import com.phantom.checker.service.NotificationCollectorService
import com.phantom.checker.util.PhantomPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                label = "Doze 電池白名單",
                ok = idleRes.isSuccess,
                detail = idleRes.output.ifBlank { if (idleRes.isSuccess) "已加入" else "失敗" }
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
                label = "AppOps 背景與鬧鐘解限",
                ok = allOpsOk,
                detail = if (allOpsOk) "4 項權限已設為 allow" else "部分項目設定失敗"
            )

            val bucketRes = ShizukuManager.execCommand(appCtx, "am set-standby-bucket $pkg active")
            results += ShizukuDiagItem(
                label = "App Standby Bucket",
                ok = bucketRes.isSuccess,
                detail = if (bucketRes.isSuccess) "已設為 active" else bucketRes.output
            )

            val listenerHealed = healNotificationListener(appCtx)
            results += ShizukuDiagItem(
                label = "通知存取權限自癒",
                ok = listenerHealed.isSuccess,
                detail = listenerHealed.output
            )

            val permRes = grantSelfRuntimePermissions(appCtx)
            results += ShizukuDiagItem(
                label = "執行期權限自動補授",
                ok = permRes.isSuccess,
                detail = permRes.output
            )

            if (PhantomPrefs.isShizukuWatchdogEnabled(appCtx) &&
                PhantomPrefs.isPersistentStatsEnabled(appCtx)
            ) {
                val wdOk = ShizukuManager.startWatchdog(appCtx)
                results += ShizukuDiagItem(
                    label = "Shizuku 跨行程看門狗",
                    ok = wdOk,
                    detail = if (wdOk) "運作中（每 60 秒檢查前景服務）" else "啟動失敗"
                )
            }

            results
        }

    suspend fun healNotificationListener(ctx: Context): ShellResult = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val component = "${appCtx.packageName}/${NotificationCollectorService::class.java.name}"
        val getRes = ShizukuManager.execCommand(appCtx, "settings get secure enabled_notification_listeners")
        if (!getRes.isSuccess) return@withContext getRes

        val current = getRes.output.trim().let { if (it == "null") "" else it }
        val entries = current.split(':').map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (entries.none { it == component || it == "${appCtx.packageName}/.service.NotificationCollectorService" }) {
            entries.add(component)
            val updated = entries.joinToString(":")
            val putRes = ShizukuManager.execCommand(
                appCtx,
                "settings put secure enabled_notification_listeners \"$updated\""
            )
            if (!putRes.isSuccess) return@withContext putRes
        }
        try {
            NotificationListenerService.requestRebind(
                ComponentName(appCtx, NotificationCollectorService::class.java)
            )
        } catch (_: Exception) {
        }
        ShellResult(0, "已啟用並觸發 rebind")
    }

    suspend fun grantSelfRuntimePermissions(ctx: Context): ShellResult = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val pkg = appCtx.packageName
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add("android.permission.POST_NOTIFICATIONS")
            }
            add("android.permission.RECEIVE_SMS")
            add("android.permission.SEND_SMS")
        }
        var okCount = 0
        perms.forEach { perm ->
            val r = ShizukuManager.execCommand(appCtx, "pm grant $pkg $perm")
            if (r.isSuccess) okCount++
        }
        ShellResult(
            if (okCount == perms.size) 0 else 1,
            "已授予 $okCount/${perms.size} 項權限"
        )
    }

    suspend fun runDiagnostics(ctx: Context): List<ShizukuDiagItem> = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val pkg = appCtx.packageName
        val items = mutableListOf<ShizukuDiagItem>()

        val idleRes = ShizukuManager.execCommand(appCtx, "dumpsys deviceidle whitelist")
        val inWhitelist = idleRes.isSuccess && idleRes.output.contains(pkg)
        items += ShizukuDiagItem(
            label = "Doze 白名單",
            ok = inWhitelist,
            detail = if (inWhitelist) "已在系統白名單" else "未在白名單"
        )

        val appOpsRes = ShizukuManager.execCommand(appCtx, "cmd appops get $pkg RUN_ANY_IN_BACKGROUND")
        val bgAllowed = appOpsRes.isSuccess && appOpsRes.output.contains("allow", ignoreCase = true)
        items += ShizukuDiagItem(
            label = "AppOps 背景限制",
            ok = bgAllowed,
            detail = appOpsRes.output.ifBlank { if (bgAllowed) "allow" else "受限" }
        )

        val bucketRes = ShizukuManager.execCommand(appCtx, "am get-standby-bucket $pkg")
        val bucketText = bucketRes.output.trim().ifBlank { "未知" }
        items += ShizukuDiagItem(
            label = "Standby Bucket",
            ok = bucketRes.isSuccess && (bucketText.contains("active", true) || bucketText == "10" || bucketText == "5"),
            detail = bucketText
        )

        val listenerRes = ShizukuManager.execCommand(appCtx, "settings get secure enabled_notification_listeners")
        val listenerOk = listenerRes.isSuccess && listenerRes.output.contains(pkg)
        items += ShizukuDiagItem(
            label = "系統通知監聽器",
            ok = listenerOk,
            detail = if (listenerOk) "已登錄於系統設定" else "未登錄"
        )

        ShizukuManager.refreshState(appCtx)
        val wdActive = ShizukuManager.state.value.isWatchdogActive
        items += ShizukuDiagItem(
            label = "Shizuku 跨行程看門狗",
            ok = wdActive,
            detail = if (wdActive) "運作中" else "未啟動"
        )

        items
    }

    suspend fun forceStopTargetApp(ctx: Context, targetPackage: String): ShellResult =
        withContext(Dispatchers.IO) {
            val safePkg = sanitizePackage(targetPackage)
                ?: return@withContext ShellResult(1, "無效的套件名稱")
            if (safePkg == ctx.packageName) {
                return@withContext ShellResult(1, "不可對幻訊本身執行強行停止")
            }
            ShizukuManager.execCommand(ctx, "am force-stop $safePkg")
        }

    suspend fun suspendTargetApp(ctx: Context, targetPackage: String): ShellResult =
        withContext(Dispatchers.IO) {
            val safePkg = sanitizePackage(targetPackage)
                ?: return@withContext ShellResult(1, "無效的套件名稱")
            if (safePkg == ctx.packageName) {
                return@withContext ShellResult(1, "不可凍結幻訊本身")
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
                ?: return@withContext ShellResult(1, "無效的套件名稱")
            val res = ShizukuManager.execCommand(ctx, "pm unsuspend $safePkg")
            if (res.isSuccess) {
                PhantomPrefs.removeShizukuSuspendedApp(ctx, safePkg)
            }
            res
        }
}
