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

    @Volatile
    private var intensiveUntilMs = 0L

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
        intensiveUntilMs = 0L
        scheduleNext(pkg, cls, STEADY_INTERVAL_SEC)
    }

    private fun scheduleNext(pkg: String, cls: String, delaySec: Long) {
        if (!running) return
        watchdogTask = scheduler.schedule({
            if (!running) return@schedule
            try {
                val check = exec("dumpsys activity services $pkg/$cls")
                val alive = check.contains("ServiceRecord{") && !check.contains("(nothing)")
                if (!alive) {
                    exec("am start-foreground-service -n $pkg/$cls")
                    intensiveUntilMs = System.currentTimeMillis() + INTENSIVE_WINDOW_MS
                }
            } catch (_: Exception) {
            }
            if (running) {
                val nextDelay = if (System.currentTimeMillis() < intensiveUntilMs) {
                    INTENSIVE_INTERVAL_SEC
                } else {
                    STEADY_INTERVAL_SEC
                }
                scheduleNext(pkg, cls, nextDelay)
            }
        }, delaySec, TimeUnit.SECONDS)
    }

    override fun stopWatchdog() {
        running = false
        intensiveUntilMs = 0L
        watchdogTask?.cancel(true)
        watchdogTask = null
    }

    override fun isWatchdogRunning(): Boolean = running

    companion object {
        private const val STEADY_INTERVAL_SEC = 300L
        private const val INTENSIVE_INTERVAL_SEC = 60L
        private const val INTENSIVE_WINDOW_MS = 10L * 60L * 1000L
    }
}
