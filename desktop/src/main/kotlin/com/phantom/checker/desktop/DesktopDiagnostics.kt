package com.phantom.checker.desktop

import java.awt.Desktop
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * #468 桌機端診斷紀錄（3 份旋轉日誌檔 desktop-0.log .. desktop-2.log，每份上限 512 KB）。
 * 僅記錄連線事件、錯誤碼與設定操作，絕不寫入訊息內容或 hmacSecret。
 */
object DesktopDiagnostics {

    private const val MAX_LOG_BYTES = 512L * 1024L
    private const val MAX_FILES = 3
    private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private fun logFile(index: Int): File = File(DesktopPaths.logsDir, "desktop-$index.log")

    private fun nowFormatted(): String = LocalDateTime.now().format(fmt)

    @Synchronized
    fun log(level: String, tag: String, message: String) {
        runCatching {
            val active = logFile(0)
            active.parentFile?.let { if (!it.exists()) it.mkdirs() }
            if (active.exists() && active.length() >= MAX_LOG_BYTES) {
                rotate()
            }
            val line = "${nowFormatted()} [$level] $tag: $message\n"
            active.appendText(line, Charsets.UTF_8)
        }
    }

    fun info(tag: String, message: String) = log("INFO", tag, message)
    fun warn(tag: String, message: String) = log("WARN", tag, message)
    fun error(tag: String, message: String) = log("ERROR", tag, message)

    private val perfStarted = AtomicBoolean(false)

    /** #520 選配效能取樣（僅在啟動參數加 -Dphantom.perf=1 時每 5 秒紀錄 JVM processCpuLoad，預設零開銷） */
    fun startPerfSamplerIfEnabled() {
        if (System.getProperty("phantom.perf") != "1") return
        if (!perfStarted.compareAndSet(false, true)) return
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
            if (src.exists()) {
                runCatching {
                    Files.move(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }.onFailure {
                    if (dst.exists()) dst.delete()
                    src.renameTo(dst)
                }
            }
        }
    }

    @Synchronized
    private fun snapshotLogFiles(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        for (i in 0 until MAX_FILES) {
            val f = logFile(i)
            if (f.exists()) {
                val tail = runCatching { f.readText(Charsets.UTF_8).takeLast(16_000) }.getOrDefault("")
                result.add(f.name to tail)
            }
        }
        return result
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
        sb.appendLine("Time: ${nowFormatted()}")
        sb.appendLine("OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})")
        sb.appendLine("Java: ${System.getProperty("java.version")}")
        sb.appendLine("ConfigDir: ${DesktopPaths.configDir.absolutePath}")
        sb.appendLine()
        for ((name, content) in snapshotLogFiles()) {
            sb.appendLine("--- $name ---")
            sb.appendLine(content)
        }
        DesktopTls.atomicWriteFile(out, restrictOwnerOnly = true) { tmp ->
            tmp.writeText(sb.toString(), Charsets.UTF_8)
        }
        out
    }
}
