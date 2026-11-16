package com.phantom.checker.shizuku

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * 執行於 Shizuku 特權獨立行程（UserService）中的 Shell 與跨行程看門狗服務。
 * 即使 App 主行程遭到系統省電清理，此看門狗仍可透過 am start-foreground-service 喚醒前景服務。
 */
class ShizukuWatchdogService : IShizukuShellService.Stub() {

    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    @Volatile
    private var watchdogTask: ScheduledFuture<*>? = null

    @Volatile
    private var running = false

    override fun destroy() {
        stopWatchdog()
        scheduler.shutdownNow()
        exitProcess(0)
    }

    override fun exec(command: String?): String {
        val cmd = command?.trim().orEmpty()
        if (cmd.isEmpty()) return "EXIT:1\nEmpty command"
        return try {
            val proc = ProcessBuilder("sh", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            val code = proc.waitFor()
            "EXIT:$code\n$out"
        } catch (e: Exception) {
            "EXIT:1\n${e.message ?: "exec error"}"
        }
    }

    override fun startWatchdog(packageName: String?, serviceClassName: String?) {
        val pkg = packageName?.trim().orEmpty()
        val cls = serviceClassName?.trim().orEmpty()
        if (pkg.isEmpty() || cls.isEmpty()) return
        stopWatchdog()
        running = true
        watchdogTask = scheduler.scheduleAtFixedRate({
            try {
                val check = exec("dumpsys activity services $pkg/$cls")
                val alive = check.contains("ServiceRecord{") && !check.contains("(nothing)")
                if (!alive) {
                    exec("am start-foreground-service -n $pkg/$cls")
                }
            } catch (_: Exception) {
            }
        }, 60L, 60L, TimeUnit.SECONDS)
    }

    override fun stopWatchdog() {
        running = false
        watchdogTask?.cancel(true)
        watchdogTask = null
    }

    override fun isWatchdogRunning(): Boolean = running
}
