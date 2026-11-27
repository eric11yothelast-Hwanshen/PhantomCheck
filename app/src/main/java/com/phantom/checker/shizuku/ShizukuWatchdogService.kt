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
        return runProcess(listOf("sh", "-c", cmd))
    }

    private fun runProcess(args: List<String>): String {
        return try {
            val proc = ProcessBuilder(args)
                .redirectErrorStream(true)
                .start()
            val outBuf = java.io.ByteArrayOutputStream()
            var truncated = false
            val readerThread = Thread {
                try {
                    val buf = ByteArray(4096)
                    proc.inputStream.use { input ->
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            val remaining = MAX_OUTPUT_BYTES - outBuf.size()
                            if (remaining > 0) {
                                outBuf.write(buf, 0, minOf(read, remaining))
                            }
                            if (read > remaining) {
                                truncated = true
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }.apply {
                isDaemon = true
                start()
            }

            val finished = proc.waitFor(EXEC_TIMEOUT_SEC, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                readerThread.join(1000L)
                val partial = outBuf.toString(Charsets.UTF_8.name())
                val suffix = if (truncated) "\n...[truncated]" else ""
                return "EXIT:124\nCommand timed out after ${EXEC_TIMEOUT_SEC}s${if (partial.isNotEmpty()) "\n$partial$suffix" else ""}"
            }

            readerThread.join(2000L)
            val code = proc.exitValue()
            val out = outBuf.toString(Charsets.UTF_8.name()) + if (truncated) "\n...[truncated]" else ""
            "EXIT:$code\n$out"
        } catch (e: Exception) {
            "EXIT:1\n${e.message ?: "exec error"}"
        }
    }

    override fun startWatchdog(packageName: String?, serviceClassName: String?) {
        val pkg = packageName?.trim().orEmpty()
        val cls = serviceClassName?.trim().orEmpty()
        if (!isSafePackageName(pkg) || !isSafeClassName(cls)) return
        stopWatchdog()
        running = true
        intensiveUntilMs = 0L
        scheduleNext(pkg, cls, STEADY_INTERVAL_SEC)
    }

    private fun scheduleNext(pkg: String, cls: String, delaySec: Long) {
        if (!running) return
        if (!isSafePackageName(pkg) || !isSafeClassName(cls)) return
        watchdogTask = scheduler.schedule({
            if (!running) return@schedule
            try {
                val component = "$pkg/$cls"
                val check = runProcess(listOf("dumpsys", "activity", "services", component))
                val alive = check.contains("ServiceRecord{") && !check.contains("(nothing)")
                if (!alive) {
                    runProcess(listOf("am", "start-foreground-service", "-n", component))
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
        private const val STEADY_INTERVAL_SEC = 600L
        private const val INTENSIVE_INTERVAL_SEC = 60L
        private const val INTENSIVE_WINDOW_MS = 10L * 60L * 1000L
        private const val EXEC_TIMEOUT_SEC = 10L
        private const val MAX_OUTPUT_BYTES = 32 * 1024

        private val SAFE_PKG_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
        private val SAFE_CLS_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

        internal fun isSafePackageName(pkg: String): Boolean = SAFE_PKG_REGEX.matches(pkg)

        internal fun isSafeClassName(cls: String): Boolean = SAFE_CLS_REGEX.matches(cls)
    }
}
