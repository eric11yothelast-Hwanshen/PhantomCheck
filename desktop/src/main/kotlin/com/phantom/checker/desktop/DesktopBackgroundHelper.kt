package com.phantom.checker.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities

/**
 * #488 桌面端自訂背景圖片輔助（原生 FileDialog 選擇、複製進 DesktopPaths.backgroundsDir、移除還原）。
 */
object DesktopBackgroundHelper {

    private val allowedExts = setOf("png", "jpg", "jpeg", "webp", "bmp")
    private const val MAX_IMAGE_BYTES = 25L * 1024L * 1024L // 25 MB safety guard

    /**
     * 開啟系統原生檔案選擇器挑選背景圖片，驗證後複製進 DesktopPaths.backgroundsDir。
     * 若使用者取消則回傳 Result.success(null)。
     */
    suspend fun pickAndCopyBackgroundImage(): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val selectedFile = openNativeImageDialog() ?: return@runCatching null
            copyAndValidateBackground(selectedFile)
        }
    }

    fun copyAndValidateBackground(source: File): String {
        if (!source.exists() || !source.isFile) {
            throw IllegalArgumentException("File does not exist: ${source.absolutePath}")
        }
        if (source.length() <= 0L || source.length() > MAX_IMAGE_BYTES) {
            throw IllegalArgumentException("Image file size invalid (${source.length()} bytes)")
        }
        val ext = source.extension.lowercase().ifBlank { "png" }
        if (ext !in allowedExts) {
            throw IllegalArgumentException("Unsupported image format: .$ext")
        }
        val bytes = source.readBytes()
        // Validate image decodes cleanly before copying
        SkiaImage.makeFromEncoded(bytes).use { /* decoded successfully */ }

        val dir = DesktopPaths.backgroundsDir
        if (!dir.exists()) dir.mkdirs()

        val target = File(dir, "custom_bg_${System.currentTimeMillis()}.$ext")
        DesktopTls.atomicWriteFile(target, restrictOwnerOnly = false) { tmp ->
            tmp.writeBytes(bytes)
        }
        // Remove previous managed background copies (both legacy custom_bg.* and timestamped custom_bg_*) after atomic write succeeds
        dir.listFiles()
            ?.filter { it != target && (it.name.startsWith("custom_bg.") || it.name.startsWith("custom_bg_")) }
            ?.forEach { runCatching { it.delete() } }

        DesktopDiagnostics.info("Background", "Copied custom background (${bytes.size} bytes) to ${target.name}")
        return target.absolutePath
    }

    fun removeManagedBackground(currentPath: String) {
        if (currentPath.isBlank()) return
        runCatching {
            val file = File(currentPath)
            val bgDir = DesktopPaths.backgroundsDir.canonicalFile
            if (file.exists() && file.canonicalFile.parentFile == bgDir) {
                file.delete()
                DesktopDiagnostics.info("Background", "Removed managed background ${file.name}")
            }
        }
    }

    private fun openNativeImageDialog(): File? {
        val resultRef = AtomicReference<File?>(null)
        val showDialog = Runnable {
            val dialog = FileDialog(null as Frame?, DesktopStrings.bgPickerTitle, FileDialog.LOAD)
            try {
                dialog.filenameFilter = FilenameFilter { _, name ->
                    val ext = name.substringAfterLast('.', "").lowercase()
                    ext in allowedExts
                }
                dialog.isVisible = true
                val dir = dialog.directory
                val file = dialog.file
                if (!dir.isNullOrBlank() && !file.isNullOrBlank()) {
                    resultRef.set(File(dir, file))
                }
            } finally {
                dialog.dispose()
            }
        }
        if (SwingUtilities.isEventDispatchThread()) {
            showDialog.run()
        } else {
            SwingUtilities.invokeAndWait(showDialog)
        }
        return resultRef.get()
    }
}
