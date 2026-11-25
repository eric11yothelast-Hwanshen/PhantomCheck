package com.phantom.checker.desktop

import java.awt.Desktop
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * #468 桌機端診斷紀錄（3 份旋轉日誌檔 desktop-0.log .. desktop-2.log，每份上限 512 KB）。
 * 僅記錄連線事件、錯誤碼與設定操作，絕不寫入訊息內容或 hmacSecret。
 */
object DesktopDiagnostics {

    private const val MAX_LOG_BYTES = 512L * 1024L
    private const val MAX_FILES = 3
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private fun logFile(index: Int): File = File(DesktopPaths.logsDir, "desktop-$index.log")

    @Synchronized
    fun log(level: String, tag: String, message: String) {
        runCatching {
            val active = logFile(0)
            if (active.exists() && active.length() >= MAX_LOG_BYTES) {
                rotate()
            }
            val line = "${fmt.format(Date())} [$level] $tag: $message\n"
            active.appendText(line, Charsets.UTF_8)
        }
    }

    fun info(tag: String, message: String) = log("INFO", tag, message)
    fun warn(tag: String, message: String) = log("WARN", tag, message)
    fun error(tag: String, message: String) = log("ERROR", tag, message)

    @Volatile
    private var perfStarted = false

    /** #520 選配效能取樣（僅在啟動參數加 -Dphantom.perf=1 時每 5 秒紀錄 JVM processCpuLoad，預設零開銷） */
    fun startPerfSamplerIfEnabled() {
        if (perfStarted || System.getProperty("phantom.perf") != "1") return
        perfStarted = true
        val t = Thread({
            val bean = runCatching {
                java.lang.management.ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean
            }.getOrNull()
            while (true) {
                runCatching { Thread.sleep(5_000L) }.onFailure { break }
                val load = bean?.processCpuLoad ?: -1.0
                if (load >= 0.0) {
                    info("Perf", "processCpuLoad=%.2f%%".format(Locale.US, load * 100.0))
                }
            }
        }, "PhantomPerfSampler")
        t.isDaemon = true
        t.start()
    }

    private fun rotate() {
        for (i in (MAX_FILES - 1) downTo 1) {
            val src = logFile(i - 1)
            val dst = logFile(i)
            if (dst.exists()) dst.delete()
            if (src.exists()) src.renameTo(dst)
        }
    }

    fun openLogsFolder(): Result<String> = runCatching {
        val dir = DesktopPaths.logsDir
        if (!dir.exists()) dir.mkdirs()
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(dir)
            dir.absolutePath
        } else {
            throw UnsupportedOperationException("Desktop.open() not supported on this platform")
        }
    }

    fun openConfigFolder(): Result<String> = runCatching {
        val dir = DesktopPaths.configDir
        if (!dir.exists()) dir.mkdirs()
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(dir)
            dir.absolutePath
        } else {
            throw UnsupportedOperationException("Desktop.open() not supported on this platform")
        }
    }

    fun exportDiagnosticBundle(): Result<File> = runCatching {
        val out = File(DesktopPaths.logsDir, "diagnostics-export-${System.currentTimeMillis()}.txt")
        val sb = StringBuilder()
        sb.appendLine("=== PhantomCheck Desktop Diagnostics ===")
        sb.appendLine("Time: ${fmt.format(Date())}")
        sb.appendLine("OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})")
        sb.appendLine("Java: ${System.getProperty("java.version")}")
        sb.appendLine("ConfigDir: ${DesktopPaths.configDir.absolutePath}")
        sb.appendLine()
        for (i in 0 until MAX_FILES) {
            val f = logFile(i)
            if (f.exists()) {
                sb.appendLine("--- ${f.name} ---")
                sb.appendLine(f.readText(Charsets.UTF_8).takeLast(16_000))
            }
        }
        out.writeText(sb.toString(), Charsets.UTF_8)
        out
    }
}
