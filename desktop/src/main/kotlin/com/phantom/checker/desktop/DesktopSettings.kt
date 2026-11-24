package com.phantom.checker.desktop

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Properties

enum class CloseBehavior { MINIMIZE_TO_TRAY, EXIT_APP }
enum class AccentPreset(val key: String) { PURPLE("purple"), BLUE("blue"), GREEN("green"), ORANGE("orange"), PINK("pink"), CYAN("cyan") }
enum class UiDensity(val key: String) { COMPACT("compact"), STANDARD("standard"), COMFORTABLE("comfortable") }
enum class MessageCardStyle(val key: String) { CARD("card"), COMPACT_LIST("compact_list") }
enum class MirrorDisplayMode(val key: String) { BANNER("banner"), NATIVE("native"), BOTH("both") }
enum class BgThemePreset(val key: String) { MIDNIGHT("midnight"), AURORA("aurora"), OBSIDIAN("obsidian"), DEEP_OCEAN("deep_ocean") }
enum class GlassBlurLevel(val key: String) { LOW("low"), MEDIUM("medium"), HIGH("high") }
enum class ChatFontSize(val key: String, val multiplier: Float) { SMALL("small", 0.9f), STANDARD("standard", 1.0f), LARGE("large", 1.18f) }

/**
 * #468 / #479 桌機端設定資料模型（10 系統設定＋16 自訂設定＋視窗位置記憶）。
 */
data class DesktopSettings(
    // 10 System settings
    val language: String = "auto", // auto | zh-TW | en
    val autoStart: Boolean = false,
    val closeBehavior: CloseBehavior = CloseBehavior.MINIMIZE_TO_TRAY,
    val nativeNotifications: Boolean = true,
    val dndEnabled: Boolean = true,
    val dndStartHour: Int = 0,
    val dndEndHour: Int = 7,
    val maxReconnectBackoffSec: Int = 30, // 15 | 30 | 60
    val pingIntervalSec: Int = 15, // 10 | 15 | 30
    val previewLength: Int = 200, // 90 | 200 | 500
    val serverPort: Int = com.phantom.checker.shared.LinkProtocol.DEFAULT_PORT,
    // 16 Custom settings (original 8 + #479 new 8)
    val accentPreset: AccentPreset = AccentPreset.BLUE,
    val customAccentHex: String = "", // #RRGGBB (optional override)
    val density: UiDensity = UiDensity.STANDARD,
    val fontScale: Float = 1.0f, // 0.9 | 1.0 | 1.1 | 1.25
    val sidebarWidthDp: Int = 244, // 210 | 244 | 280
    val sidebarCollapsed: Boolean = false,
    val messageCardStyle: MessageCardStyle = MessageCardStyle.CARD,
    val mirrorMode: MirrorDisplayMode = MirrorDisplayMode.BOTH,
    val bgTheme: BgThemePreset = BgThemePreset.MIDNIGHT,
    val customBgImagePath: String = "",
    val animationsEnabled: Boolean = true,
    val use24HourTime: Boolean = true,
    val messageCardCornerDp: Int = 16, // 12 | 16 | 20 | 24
    val glassBlurLevel: GlassBlurLevel = GlassBlurLevel.MEDIUM,
    val chatFontSize: ChatFontSize = ChatFontSize.STANDARD,
    val notificationSoundEnabled: Boolean = true,
    val autoConnectOnStartup: Boolean = true,
    val defaultStartupTab: String = "MESSAGES", // MESSAGES | STATS | TODOS | CHAT | RULES | SETTINGS
    // Window geometry persistence
    val windowX: Int = Int.MIN_VALUE,
    val windowY: Int = Int.MIN_VALUE,
    val windowWidth: Int = 1280,
    val windowHeight: Int = 820
) {
    fun isInDndWindow(cal: Calendar = Calendar.getInstance()): Boolean {
        if (!dndEnabled) return false
        val h = cal.get(Calendar.HOUR_OF_DAY)
        return if (dndStartHour <= dndEndHour) {
            h in dndStartHour until dndEndHour
        } else {
            h >= dndStartHour || h < dndEndHour
        }
    }
}

data class DesktopUpdateCheckResult(
    val ok: Boolean,
    val latestTag: String = "",
    val htmlUrl: String = "",
    val errorMessage: String = ""
)

object DesktopSettingsStore {

    const val CURRENT_DESKTOP_VERSION = "0.4.0"
    private const val WIN_RUN_REG_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val WIN_RUN_VALUE_NAME = "PhantomCheckDesktop"
    private val validStartupTabs = setOf("MESSAGES", "STATS", "TODOS", "CHAT", "RULES", "SETTINGS")

    private val _state = MutableStateFlow(loadFromDisk())
    val state: StateFlow<DesktopSettings> = _state.asStateFlow()

    private fun loadFromDisk(): DesktopSettings {
        val file = DesktopPaths.settingsPropertiesFile
        if (!file.exists()) return DesktopSettings()
        val p = Properties()
        runCatching { file.bufferedReader(Charsets.UTF_8).use { p.load(it) } }
            .onFailure {
                runCatching { file.inputStream().use { p.load(it) } }
            }
        return DesktopSettings(
            language = p.getProperty("language", "auto"),
            autoStart = p.getProperty("autoStart", "false").toBoolean(),
            closeBehavior = runCatching { CloseBehavior.valueOf(p.getProperty("closeBehavior", "MINIMIZE_TO_TRAY")) }.getOrDefault(CloseBehavior.MINIMIZE_TO_TRAY),
            nativeNotifications = p.getProperty("nativeNotifications", "true").toBoolean(),
            dndEnabled = p.getProperty("dndEnabled", "true").toBoolean(),
            dndStartHour = p.getProperty("dndStartHour", "0").toIntOrNull()?.coerceIn(0, 23) ?: 0,
            dndEndHour = p.getProperty("dndEndHour", "7").toIntOrNull()?.coerceIn(0, 23) ?: 7,
            maxReconnectBackoffSec = p.getProperty("maxReconnectBackoffSec", "30").toIntOrNull()?.takeIf { it in listOf(15, 30, 60) } ?: 30,
            pingIntervalSec = p.getProperty("pingIntervalSec", "15").toIntOrNull()?.takeIf { it in listOf(10, 15, 30) } ?: 15,
            previewLength = p.getProperty("previewLength", "200").toIntOrNull()?.takeIf { it in listOf(90, 200, 500) } ?: 200,
            serverPort = p.getProperty("serverPort", com.phantom.checker.shared.LinkProtocol.DEFAULT_PORT.toString()).toIntOrNull()?.coerceIn(1024, 65535) ?: com.phantom.checker.shared.LinkProtocol.DEFAULT_PORT,
            accentPreset = AccentPreset.entries.firstOrNull { it.key == p.getProperty("accentPreset", "blue") } ?: AccentPreset.BLUE,
            customAccentHex = p.getProperty("customAccentHex", "").trim(),
            density = UiDensity.entries.firstOrNull { it.key == p.getProperty("density", "standard") } ?: UiDensity.STANDARD,
            fontScale = p.getProperty("fontScale", "1.0").toFloatOrNull()?.coerceIn(0.9f, 1.25f) ?: 1.0f,
            sidebarWidthDp = p.getProperty("sidebarWidthDp", "244").toIntOrNull()?.coerceIn(190, 320) ?: 244,
            sidebarCollapsed = p.getProperty("sidebarCollapsed", "false").toBoolean(),
            messageCardStyle = MessageCardStyle.entries.firstOrNull { it.key == p.getProperty("messageCardStyle", "card") } ?: MessageCardStyle.CARD,
            mirrorMode = MirrorDisplayMode.entries.firstOrNull { it.key == p.getProperty("mirrorMode", "both") } ?: MirrorDisplayMode.BOTH,
            bgTheme = BgThemePreset.entries.firstOrNull { it.key == p.getProperty("bgTheme", "midnight") } ?: BgThemePreset.MIDNIGHT,
            customBgImagePath = p.getProperty("customBgImagePath", ""),
            animationsEnabled = p.getProperty("animationsEnabled", "true").toBoolean(),
            use24HourTime = p.getProperty("use24HourTime", "true").toBoolean(),
            messageCardCornerDp = p.getProperty("messageCardCornerDp", "16").toIntOrNull()?.takeIf { it in listOf(12, 16, 20, 24) } ?: 16,
            glassBlurLevel = GlassBlurLevel.entries.firstOrNull { it.key == p.getProperty("glassBlurLevel", "medium") } ?: GlassBlurLevel.MEDIUM,
            chatFontSize = ChatFontSize.entries.firstOrNull { it.key == p.getProperty("chatFontSize", "standard") } ?: ChatFontSize.STANDARD,
            notificationSoundEnabled = p.getProperty("notificationSoundEnabled", "true").toBoolean(),
            autoConnectOnStartup = p.getProperty("autoConnectOnStartup", "true").toBoolean(),
            defaultStartupTab = p.getProperty("defaultStartupTab", "MESSAGES").takeIf { it in validStartupTabs } ?: "MESSAGES",
            windowX = p.getProperty("windowX", Int.MIN_VALUE.toString()).toIntOrNull() ?: Int.MIN_VALUE,
            windowY = p.getProperty("windowY", Int.MIN_VALUE.toString()).toIntOrNull() ?: Int.MIN_VALUE,
            windowWidth = p.getProperty("windowWidth", "1280").toIntOrNull()?.coerceAtLeast(1024) ?: 1280,
            windowHeight = p.getProperty("windowHeight", "820").toIntOrNull()?.coerceAtLeast(680) ?: 820
        )
    }

    @Synchronized
    fun update(transform: (DesktopSettings) -> DesktopSettings) {
        val next = transform(_state.value)
        _state.value = next
        DesktopStrings.syncLanguage(next.language)
        saveToDisk(next)
    }

    private fun saveToDisk(s: DesktopSettings) {
        runCatching {
            val file = DesktopPaths.settingsPropertiesFile
            if (!file.parentFile.exists()) file.parentFile.mkdirs()
            val p = Properties()
            p.setProperty("language", s.language)
            p.setProperty("autoStart", s.autoStart.toString())
            p.setProperty("closeBehavior", s.closeBehavior.name)
            p.setProperty("nativeNotifications", s.nativeNotifications.toString())
            p.setProperty("dndEnabled", s.dndEnabled.toString())
            p.setProperty("dndStartHour", s.dndStartHour.toString())
            p.setProperty("dndEndHour", s.dndEndHour.toString())
            p.setProperty("maxReconnectBackoffSec", s.maxReconnectBackoffSec.toString())
            p.setProperty("pingIntervalSec", s.pingIntervalSec.toString())
            p.setProperty("previewLength", s.previewLength.toString())
            p.setProperty("serverPort", s.serverPort.toString())
            p.setProperty("accentPreset", s.accentPreset.key)
            p.setProperty("customAccentHex", s.customAccentHex)
            p.setProperty("density", s.density.key)
            p.setProperty("fontScale", s.fontScale.toString())
            p.setProperty("sidebarWidthDp", s.sidebarWidthDp.toString())
            p.setProperty("sidebarCollapsed", s.sidebarCollapsed.toString())
            p.setProperty("messageCardStyle", s.messageCardStyle.key)
            p.setProperty("mirrorMode", s.mirrorMode.key)
            p.setProperty("bgTheme", s.bgTheme.key)
            p.setProperty("customBgImagePath", s.customBgImagePath)
            p.setProperty("animationsEnabled", s.animationsEnabled.toString())
            p.setProperty("use24HourTime", s.use24HourTime.toString())
            p.setProperty("messageCardCornerDp", s.messageCardCornerDp.toString())
            p.setProperty("glassBlurLevel", s.glassBlurLevel.key)
            p.setProperty("chatFontSize", s.chatFontSize.key)
            p.setProperty("notificationSoundEnabled", s.notificationSoundEnabled.toString())
            p.setProperty("autoConnectOnStartup", s.autoConnectOnStartup.toString())
            p.setProperty("defaultStartupTab", s.defaultStartupTab)
            p.setProperty("windowX", s.windowX.toString())
            p.setProperty("windowY", s.windowY.toString())
            p.setProperty("windowWidth", s.windowWidth.toString())
            p.setProperty("windowHeight", s.windowHeight.toString())
            file.bufferedWriter(Charsets.UTF_8).use { p.store(it, "PhantomCheck Desktop Settings") }
        }.onFailure { err ->
            DesktopDiagnostics.error("Settings", "Failed saving settings: ${err.javaClass.simpleName}: ${err.message}")
        }
    }

    fun resetSettingsToDefaults() {
        val defaults = DesktopSettings()
        _state.value = defaults
        DesktopStrings.syncLanguage(defaults.language)
        saveToDisk(defaults)
        DesktopDiagnostics.info("Settings", "Settings reset to defaults")
    }

    /**
     * #468 系統設定 2：Windows 開機自動啟動（HKCU\...\Run 機碼，reg.exe 實作，開關可互逆，啟動參數 --minimized 進系統匣）。
     */
    fun applyWindowsAutoStart(enable: Boolean): Result<String> = runCatching {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        if (!os.contains("win")) {
            throw UnsupportedOperationException("Windows HKCU Run auto-start is only supported on Windows")
        }
        val cmd = if (enable) {
            val exePath = resolveCurrentWindowsExecutable()
            listOf("reg.exe", "add", WIN_RUN_REG_KEY, "/v", WIN_RUN_VALUE_NAME, "/t", "REG_SZ", "/d", "\"$exePath\" --minimized", "/f")
        } else {
            listOf("reg.exe", "delete", WIN_RUN_REG_KEY, "/v", WIN_RUN_VALUE_NAME, "/f")
        }
        val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText().trim()
        val exit = proc.waitFor()
        // delete 若機碼本就不存在會回傳非 0，視為成功關閉
        if (exit != 0 && enable) {
            DesktopDiagnostics.error("AutoStart", "reg.exe failed (exit=$exit): $output")
            throw IllegalStateException("reg.exe failed (exit=$exit): $output")
        }
        update { it.copy(autoStart = enable) }
        DesktopDiagnostics.info("AutoStart", "Windows autoStart set to $enable")
        if (enable) "Enabled" else "Disabled"
    }

    internal fun resolveCurrentWindowsExecutable(): String {
        val jpackagePath = System.getProperty("jpackage.app-path").orEmpty().trim()
        if (jpackagePath.isNotBlank() && File(jpackagePath).exists()) return jpackagePath
        val localAppData = System.getenv("LOCALAPPDATA").orEmpty().trim()
        val defaultExe = File(localAppData, "PhantomCheckDesktop\\PhantomCheckDesktop.exe")
        if (defaultExe.exists()) return defaultExe.absolutePath
        return ProcessHandle.current().info().command().orElse("PhantomCheckDesktop.exe")
    }

    /**
     * #468 系統設定 9：手動檢查更新（GitHub Releases latest，絕不背景輪詢，錯誤誠實回報）。
     */
    suspend fun checkLatestRelease(): DesktopUpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://api.github.com/repos/eric11yothelast-Hwanshen/PhantomCheck/releases/latest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "PhantomCheckDesktop/$CURRENT_DESKTOP_VERSION")
            }
            val code = conn.responseCode
            if (code != 200) {
                val err = runCatching { conn.errorStream?.bufferedReader()?.readText()?.take(200) }.getOrNull().orEmpty()
                DesktopDiagnostics.warn("UpdateCheck", "HTTP $code: $err")
                return@runCatching DesktopUpdateCheckResult(ok = false, errorMessage = "HTTP $code")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JsonParser.parseString(body).asJsonObject
            val tag = json.get("tag_name")?.asString.orEmpty()
            val htmlUrl = json.get("html_url")?.asString.orEmpty()
            DesktopDiagnostics.info("UpdateCheck", "Latest release tag=$tag")
            DesktopUpdateCheckResult(ok = true, latestTag = tag, htmlUrl = htmlUrl)
        }.getOrElse { err ->
            DesktopDiagnostics.error("UpdateCheck", err.message ?: err.javaClass.simpleName)
            DesktopUpdateCheckResult(ok = false, errorMessage = err.message ?: err.javaClass.simpleName)
        }
    }
}
