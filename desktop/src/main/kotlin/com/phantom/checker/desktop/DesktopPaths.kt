package com.phantom.checker.desktop

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * #471 / #468 桌機端配置目錄解析（per-user ACL 目錄）與舊版 ~/.phantomcheck-desktop 自動遷移：
 * - Windows: %APPDATA%/PhantomCheckDesktop
 * - macOS: ~/Library/Application Support/PhantomCheckDesktop
 * - Linux/其他: $XDG_CONFIG_HOME/PhantomCheckDesktop 或 ~/.config/PhantomCheckDesktop
 */
object DesktopPaths {

    val configDir: File by lazy {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val userHome = System.getProperty("user.home").orEmpty()
        val dir = when {
            os.contains("win") -> {
                val appData = System.getenv("APPDATA").orEmpty().trim()
                if (appData.isNotBlank()) File(appData, "PhantomCheckDesktop")
                else File(userHome, "AppData/Roaming/PhantomCheckDesktop")
            }
            os.contains("mac") -> {
                File(userHome, "Library/Application Support/PhantomCheckDesktop")
            }
            else -> {
                val xdg = System.getenv("XDG_CONFIG_HOME").orEmpty().trim()
                if (xdg.isNotBlank()) File(xdg, "PhantomCheckDesktop")
                else File(userHome, ".config/PhantomCheckDesktop")
            }
        }
        runCatching { if (!dir.exists()) dir.mkdirs() }
        dir
    }

    val linkPropertiesFile: File by lazy {
        val target = File(configDir, "link.properties")
        migrateLegacyLinkPropertiesIfNeeded(target)
        target
    }

    val settingsPropertiesFile: File by lazy {
        File(configDir, "settings.properties")
    }

    val serverKeystoreFile: File by lazy {
        File(configDir, "desktop_server.p12")
    }

    val serverTlsPropsFile: File by lazy {
        File(configDir, "tls.properties")
    }

    val pairedPhonesFile: File by lazy {
        File(configDir, "paired_phones.json")
    }

    val logsDir: File by lazy {
        File(configDir, "logs").also { runCatching { if (!it.exists()) it.mkdirs() } }
    }

    val backgroundsDir: File by lazy {
        File(configDir, "backgrounds").also { runCatching { if (!it.exists()) it.mkdirs() } }
    }

    private fun migrateLegacyLinkPropertiesIfNeeded(target: File) {
        if (target.exists()) return
        val legacyDir = File(System.getProperty("user.home").orEmpty(), ".phantomcheck-desktop")
        val legacyFile = File(legacyDir, "link.properties")
        if (!legacyFile.exists()) return
        runCatching {
            if (!target.parentFile.exists()) target.parentFile.mkdirs()
            legacyFile.copyTo(target, overwrite = false)
            restrictOwnerOnlyPermissions(target)
            legacyFile.delete()
            if (legacyDir.listFiles()?.isEmpty() == true) {
                legacyDir.delete()
            }
        }
    }

    fun restrictOwnerOnlyPermissions(file: File) {
        runCatching {
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
        }
        runCatching {
            val perms = setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE
            )
            Files.setPosixFilePermissions(file.toPath(), perms)
        }
    }
}
