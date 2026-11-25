package com.phantom.checker.desktop

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean

/**
 * #495 桌面端單實例鎖定與本機回環 IPC（防雙開搶埠 + 第二實例喚醒既有視窗 + 安裝器優雅關閉）。
 * - 使用 NIO FileLock（%APPDATA%\PhantomCheckDesktop\instance.lock）保證跨進程互斥，崩潰後由 OS 自動釋放。
 * - 使用 127.0.0.1 臨時埠（寫入 instance.port）接收 SHOW / EXIT 指令，不綁定區網介面亦不觸發防火牆彈窗。
 */
class DesktopSingleInstance private constructor(
    private val lockChannel: FileChannel,
    private val fileLock: FileLock,
    private val ipcServer: ServerSocket?,
    private val portFile: File
) {
    private val released = AtomicBoolean(false)

    @Volatile
    private var onFocusRequested: (() -> Unit)? = null

    @Volatile
    private var onGracefulExitRequested: (() -> Unit)? = null

    init {
        val ss = ipcServer
        if (ss != null) {
            Thread({
                while (!released.get() && !ss.isClosed) {
                    val client = runCatching { ss.accept() }.getOrNull() ?: break
                    runCatching {
                        client.soTimeout = 1000
                        val line = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
                            .readLine()
                            ?.trim()
                            .orEmpty()
                        when (line.uppercase()) {
                            CMD_SHOW -> {
                                DesktopDiagnostics.info("SingleInstance", "Received SHOW from second instance")
                                onFocusRequested?.invoke()
                            }
                            CMD_EXIT -> {
                                DesktopDiagnostics.info("SingleInstance", "Received EXIT for graceful shutdown")
                                onGracefulExitRequested?.invoke()
                            }
                        }
                    }
                    runCatching { client.close() }
                }
            }, "phantom-single-instance-ipc").apply {
                isDaemon = true
                start()
            }
        }
    }

    fun setCallbacks(onFocus: () -> Unit, onGracefulExit: () -> Unit) {
        onFocusRequested = onFocus
        onGracefulExitRequested = onGracefulExit
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        runCatching { ipcServer?.close() }
        runCatching { if (portFile.exists()) portFile.delete() }
        runCatching { fileLock.release() }
        runCatching { lockChannel.close() }
    }

    companion object {
        private const val LOCK_FILE_NAME = "instance.lock"
        private const val PORT_FILE_NAME = "instance.port"
        private const val CMD_SHOW = "SHOW"
        private const val CMD_EXIT = "EXIT"

        /**
         * 嘗試取得單實例鎖。若已有實例執行中，通知其顯示視窗（或於 --shutdown-existing 時要求優雅關閉）並回傳 null。
         */
        fun acquireOrNotifyExisting(args: Array<String>): DesktopSingleInstance? {
            val shutdownOnly = args.any { it.equals("--shutdown-existing", ignoreCase = true) }
            val dir = DesktopPaths.configDir
            if (!dir.exists()) runCatching { dir.mkdirs() }
            val lockFile = File(dir, LOCK_FILE_NAME)
            val portFile = File(dir, PORT_FILE_NAME)

            if (shutdownOnly) {
                sendCommandToExisting(portFile, CMD_EXIT)
                return null
            }

            val channel = runCatching {
                FileChannel.open(
                    lockFile.toPath(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE
                )
            }.getOrNull() ?: return null

            val lock = runCatching { channel.tryLock() }.getOrNull()
            if (lock == null) {
                runCatching { channel.close() }
                val notified = sendCommandToExisting(portFile, CMD_SHOW)
                DesktopDiagnostics.info("SingleInstance", "Existing instance detected (notified=$notified); exiting duplicate launch")
                return null
            }

            val server = runCatching {
                ServerSocket(0, 4, InetAddress.getLoopbackAddress()).also { ss ->
                    runCatching { portFile.writeText(ss.localPort.toString(), Charsets.UTF_8) }
                }
            }.getOrNull()

            val instance = DesktopSingleInstance(channel, lock, server, portFile)
            runCatching {
                Runtime.getRuntime().addShutdownHook(Thread { instance.release() })
            }
            return instance
        }

        private fun sendCommandToExisting(portFile: File, command: String): Boolean = runCatching {
            if (!portFile.exists()) return@runCatching false
            val port = portFile.readText(Charsets.UTF_8).trim().toIntOrNull() ?: return@runCatching false
            if (port !in 1024..65535) return@runCatching false
            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 600)
                socket.soTimeout = 600
                OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8).use { writer ->
                    writer.write(command)
                    writer.write("\n")
                    writer.flush()
                }
            }
            true
        }.getOrDefault(false)
    }
}
