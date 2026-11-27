package com.phantom.checker.desktop

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
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

/**
 * #495 桌面端單實例鎖定與本機回環 IPC（防雙開搶埠 + 第二實例喚醒既有視窗 + 安裝器優雅關閉）。
 * - 使用 NIO FileLock（%APPDATA%\PhantomCheckDesktop\instance.lock）保證跨進程互斥，崩潰後由 OS 自動釋放。
 * - 使用 127.0.0.1 臨時埠＋隨機 Token（原子寫入 owner-only instance.port）驗證 SHOW / EXIT 指令，不綁定區網介面亦不觸發防火牆彈窗。
 * - 所有回呼均派發至 AWT EDT 執行，避免背景 IPC 執行緒直接修改 Compose 狀態。
 */
class DesktopSingleInstance private constructor(
    private val lockChannel: FileChannel,
    private val fileLock: FileLock,
    private val ipcServer: ServerSocket?,
    private val portFile: File,
    private val ipcToken: String
) {
    private val released = AtomicBoolean(false)
    private val callbackLock = Any()

    private var onFocusRequested: (() -> Unit)? = null
    private var onGracefulExitRequested: (() -> Unit)? = null
    private var pendingFocus = false
    private var pendingExit = false

    init {
        val ss = ipcServer
        if (ss != null) {
            Thread({
                while (!released.get() && !ss.isClosed) {
                    val client = runCatching { ss.accept() }.getOrNull() ?: break
                    runCatching {
                        client.use { sock ->
                            sock.soTimeout = 1000
                            val line = readBoundedLine(sock, MAX_IPC_LINE_CHARS).trim()
                            val parts = line.split(' ', limit = 2)
                            if (parts.size != 2) {
                                DesktopDiagnostics.warn("SingleInstance", "Rejected malformed IPC payload")
                                return@use
                            }
                            val suppliedToken = parts[0].trim()
                            val cmd = parts[1].trim().uppercase(Locale.ROOT)
                            if (!isValidToken(suppliedToken)) {
                                DesktopDiagnostics.warn("SingleInstance", "Rejected IPC command with invalid token")
                                return@use
                            }
                            when (cmd) {
                                CMD_SHOW -> {
                                    DesktopDiagnostics.info("SingleInstance", "Received authenticated SHOW from second instance")
                                    dispatchFocus()
                                }
                                CMD_EXIT -> {
                                    DesktopDiagnostics.info("SingleInstance", "Received authenticated EXIT for graceful shutdown")
                                    dispatchGracefulExit()
                                }
                            }
                        }
                    }
                }
            }, "phantom-single-instance-ipc").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun isValidToken(suppliedToken: String): Boolean {
        if (ipcToken.isBlank() || suppliedToken.isBlank()) return false
        return MessageDigest.isEqual(
            ipcToken.toByteArray(Charsets.UTF_8),
            suppliedToken.toByteArray(Charsets.UTF_8)
        )
    }

    private fun dispatchOnEdt(action: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            runCatching { action() }
        } else {
            SwingUtilities.invokeLater {
                runCatching { action() }
            }
        }
    }

    private fun dispatchFocus() {
        val cb = synchronized(callbackLock) {
            val current = onFocusRequested
            if (current == null) {
                pendingFocus = true
            }
            current
        }
        if (cb != null) {
            dispatchOnEdt(cb)
        }
    }

    private fun dispatchGracefulExit() {
        val cb = synchronized(callbackLock) {
            val current = onGracefulExitRequested
            if (current == null) {
                pendingExit = true
            }
            current
        }
        if (cb != null) {
            dispatchOnEdt(cb)
        }
    }

    fun setCallbacks(onFocus: () -> Unit, onGracefulExit: () -> Unit) {
        val replayExit: Boolean
        val replayFocus: Boolean
        synchronized(callbackLock) {
            onFocusRequested = onFocus
            onGracefulExitRequested = onGracefulExit
            replayExit = pendingExit
            replayFocus = pendingFocus && !pendingExit
            pendingExit = false
            pendingFocus = false
        }
        if (replayExit) {
            dispatchOnEdt(onGracefulExit)
        } else if (replayFocus) {
            dispatchOnEdt(onFocus)
        }
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
        private const val MAX_IPC_LINE_CHARS = 256

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

            val token = DesktopTls.randomHex(32)
            val server = runCatching {
                ServerSocket(0, 4, InetAddress.getLoopbackAddress()).also { ss ->
                    runCatching {
                        DesktopTls.atomicWriteFile(portFile, restrictOwnerOnly = true) { tmp ->
                            tmp.writeText("${ss.localPort} $token", Charsets.UTF_8)
                        }
                    }
                }
            }.getOrNull()

            val instance = DesktopSingleInstance(channel, lock, server, portFile, token)
            runCatching {
                Runtime.getRuntime().addShutdownHook(Thread { instance.release() })
            }
            return instance
        }

        private fun readBoundedLine(socket: Socket, maxChars: Int): String {
            val reader = InputStreamReader(socket.getInputStream(), Charsets.UTF_8)
            val sb = StringBuilder()
            while (sb.length < maxChars) {
                val ch = reader.read()
                if (ch == -1 || ch == '\n'.code) break
                if (ch != '\r'.code) {
                    sb.append(ch.toChar())
                }
            }
            return sb.toString()
        }

        private fun sendCommandToExisting(portFile: File, command: String): Boolean = runCatching {
            if (!portFile.exists()) return@runCatching false
            val raw = portFile.readText(Charsets.UTF_8).trim()
            val parts = raw.split(Regex("\\s+"), limit = 2)
            if (parts.size != 2) return@runCatching false
            val port = parts[0].toIntOrNull() ?: return@runCatching false
            val token = parts[1].trim()
            if (port !in 1024..65535 || token.isBlank()) return@runCatching false
            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 600)
                socket.soTimeout = 600
                OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8).use { writer ->
                    writer.write(token)
                    writer.write(" ")
                    writer.write(command)
                    writer.write("\n")
                    writer.flush()
                }
            }
            true
        }.getOrDefault(false)
    }
}
