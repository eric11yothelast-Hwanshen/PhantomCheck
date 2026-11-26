package com.phantom.checker.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.google.gson.Gson
import com.phantom.checker.shared.ChatBubbleDto
import com.phantom.checker.shared.ChatHistoryReq
import com.phantom.checker.shared.ChatHistoryRes
import com.phantom.checker.shared.ChatReplyRes
import com.phantom.checker.shared.ChatSendReq
import com.phantom.checker.shared.ChatSessionSummaryDto
import com.phantom.checker.shared.ChatSessionsListRes
import com.phantom.checker.shared.LinkMsgType
import com.phantom.checker.shared.MessageSummaryDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * #497 桌面通知事件負載（供頂部橫幅、右下 Toast、懸浮助手氣泡共用，僅含既有鏡射欄位、嚴守隱私紅線）。
 */
data class MirrorNotificationEvent(
    val id: Long,
    val packageName: String,
    val appLabel: String,
    val sender: String,
    val preview: String,
    val riskLabel: String,
    val conversationKey: String,
    val timestamp: Long = System.currentTimeMillis(),
    val seq: Long = System.nanoTime()
)

/**
 * #517 手機端即時裝置狀態持有者（主視窗側欄／總覽／懸浮球 Tooltip／Tray 共用；斷線保留最後已知時間）。
 */
object DesktopDeviceStatusHolder {
    private val _status = MutableStateFlow<com.phantom.checker.shared.DeviceStatusPush?>(null)
    val status: StateFlow<com.phantom.checker.shared.DeviceStatusPush?> = _status.asStateFlow()

    fun update(push: com.phantom.checker.shared.DeviceStatusPush) {
        _status.value = push.copy(
            updatedAtMs = if (push.updatedAtMs > 0L) push.updatedAtMs else System.currentTimeMillis()
        )
    }

    fun updateFromLegacy(ps: com.phantom.checker.shared.PhoneStatusDto) {
        val cur = _status.value
        _status.value = (cur ?: com.phantom.checker.shared.DeviceStatusPush()).copy(
            batteryLevel = ps.batteryPercent,
            isCharging = ps.isCharging,
            deviceName = ps.deviceName.ifBlank { cur?.deviceName.orEmpty() },
            updatedAtMs = System.currentTimeMillis()
        )
    }
}

/**
 * #497 桌面通知匯流排（application scope 單例，不干擾主視窗生命週期）。
 */
object DesktopNotificationBus {
    private val _latestBanner = MutableStateFlow<MirrorNotificationEvent?>(null)
    val latestBanner: StateFlow<MirrorNotificationEvent?> = _latestBanner.asStateFlow()

    private val _latestToast = MutableStateFlow<MirrorNotificationEvent?>(null)
    val latestToast: StateFlow<MirrorNotificationEvent?> = _latestToast.asStateFlow()

    private val _latestFloatingBubble = MutableStateFlow<MirrorNotificationEvent?>(null)
    val latestFloatingBubble: StateFlow<MirrorNotificationEvent?> = _latestFloatingBubble.asStateFlow()

    private val _floatingUnreadBadge = MutableStateFlow(0)
    val floatingUnreadBadge: StateFlow<Int> = _floatingUnreadBadge.asStateFlow()

    private val _pausedUntilMs = MutableStateFlow(0L)
    val pausedUntilMs: StateFlow<Long> = _pausedUntilMs.asStateFlow()

    private val _navigateToConversation = MutableStateFlow<MirrorNotificationEvent?>(null)
    val navigateToConversation: StateFlow<MirrorNotificationEvent?> = _navigateToConversation.asStateFlow()

    fun isPausedNow(nowMs: Long = System.currentTimeMillis()): Boolean = _pausedUntilMs.value > nowMs

    fun pauseForOneHour() {
        _pausedUntilMs.value = System.currentTimeMillis() + 60L * 60L * 1000L
    }

    fun resumeNotifications() {
        _pausedUntilMs.value = 0L
    }

    fun requestNavigateTo(event: MirrorNotificationEvent) {
        _navigateToConversation.value = event
    }

    fun consumeNavigation() {
        _navigateToConversation.value = null
    }

    fun clearBanner() {
        _latestBanner.value = null
    }

    fun clearToast() {
        _latestToast.value = null
    }

    fun clearFloatingBubble() {
        _latestFloatingBubble.value = null
    }

    fun clearFloatingBadge() {
        _floatingUnreadBadge.value = 0
    }

    private val toastMinuteWindow = ArrayDeque<Long>()
    private val floatingMinuteWindow = ArrayDeque<Long>()
    private var aggWindowStartMs: Long = 0L
    private var aggCount: Int = 0
    private var aggLastEvent: MirrorNotificationEvent? = null

    private fun allowUnderPerMinuteCap(window: ArrayDeque<Long>, maxPerMin: Int, nowMs: Long): Boolean {
        if (maxPerMin <= 0) return true
        while (window.isNotEmpty() && nowMs - window.first() > 60_000L) {
            window.removeFirst()
        }
        if (window.size >= maxPerMin) return false
        window.addLast(nowMs)
        return true
    }

    fun dispatchNewMessage(
        msg: MessageSummaryDto,
        settings: DesktopSettings,
        onNativeTrayNotify: (title: String, body: String) -> Unit
    ) {
        val nowMs = System.currentTimeMillis()
        val appName = DesktopAppLabels.resolveLabel(msg.packageName, msg.appLabel)
        val preview = msg.preview.take(settings.previewLength)
        val event = MirrorNotificationEvent(
            id = msg.id,
            packageName = msg.packageName,
            appLabel = appName,
            sender = msg.sender.ifBlank { appName },
            preview = preview,
            riskLabel = msg.riskLabel,
            conversationKey = msg.conversationKey,
            timestamp = msg.timestamp
        )

        val dndOrPaused = settings.isEffectiveDndNow() || isPausedNow(nowMs)

        // A. In-app top banner
        if (!dndOrPaused && settings.bannerEnabled && (settings.mirrorMode == MirrorDisplayMode.BANNER || settings.mirrorMode == MirrorDisplayMode.BOTH || settings.mirrorMode == MirrorDisplayMode.COMBO)) {
            _latestBanner.value = event
        }

        // B. Bottom-right desktop toast (suppressed in DND/paused or fullscreen, respects #513 frequency & aggregation)
        val toastAllowedByStar = !settings.toastOnlyStarred || msg.isStarred
        if (!dndOrPaused && toastAllowedByStar && settings.toastEnabled && (settings.mirrorMode == MirrorDisplayMode.TOAST || settings.mirrorMode == MirrorDisplayMode.BOTH || settings.mirrorMode == MirrorDisplayMode.COMBO)) {
            if (!DesktopFullscreenDetector.isFullscreenAppActive()) {
                val aggMin = settings.toastAggregateMinutes.coerceIn(0, 15)
                if (aggMin > 0) {
                    val aggMs = aggMin * 60_000L
                    if (aggWindowStartMs == 0L) {
                        aggWindowStartMs = nowMs
                        aggCount = 1
                        aggLastEvent = event
                        if (allowUnderPerMinuteCap(toastMinuteWindow, settings.toastMaxPerMinute, nowMs)) {
                            _latestToast.value = event
                            if (settings.notificationSoundEnabled) DesktopSoundPlayer.play(settings)
                        }
                    } else if (nowMs - aggWindowStartMs >= aggMs) {
                        val total = aggCount + 1
                        aggWindowStartMs = nowMs
                        aggCount = 0
                        aggLastEvent = null
                        val summaryEvent = event.copy(
                            sender = DesktopStrings.toastAggregatedTitle,
                            preview = DesktopStrings.toastAggregatedBody(total)
                        )
                        if (allowUnderPerMinuteCap(toastMinuteWindow, settings.toastMaxPerMinute, nowMs)) {
                            _latestToast.value = summaryEvent
                            if (settings.notificationSoundEnabled) DesktopSoundPlayer.play(settings)
                        }
                    } else {
                        aggCount++
                        aggLastEvent = event
                    }
                } else if (allowUnderPerMinuteCap(toastMinuteWindow, settings.toastMaxPerMinute, nowMs)) {
                    _latestToast.value = event
                    if (settings.notificationSoundEnabled) DesktopSoundPlayer.play(settings)
                }
            }
        }

        // C. Floating assistant bubble or badge-only in DND/paused (separate #513 frequency controls)
        val floatingAllowedByStar = !settings.floatingOnlyStarred || msg.isStarred
        if (settings.floatingAssistantEnabled) {
            if (dndOrPaused) {
                _floatingUnreadBadge.value = (_floatingUnreadBadge.value + 1).coerceAtMost(99)
            } else if (floatingAllowedByStar && allowUnderPerMinuteCap(floatingMinuteWindow, settings.floatingMaxPerMinute, nowMs)) {
                _latestFloatingBubble.value = event
                _floatingUnreadBadge.value = (_floatingUnreadBadge.value + 1).coerceAtMost(99)
            } else {
                _floatingUnreadBadge.value = (_floatingUnreadBadge.value + 1).coerceAtMost(99)
            }
        }

        // Native OS tray notification
        if (!dndOrPaused && toastAllowedByStar && settings.nativeNotifications &&
            (settings.mirrorMode == MirrorDisplayMode.NATIVE || settings.mirrorMode == MirrorDisplayMode.BOTH || settings.mirrorMode == MirrorDisplayMode.COMBO)
        ) {
            onNativeTrayNotify("$appName · ${event.sender}", preview)
        }
    }

    /** #513 測試通知：勿擾開啟時靜默記錄並回傳 false（不彈出），勿擾關閉時立即彈出測試 Toast 並播音。 */
    fun dispatchTestNotification(settings: DesktopSettings): Boolean {
        val nowMs = System.currentTimeMillis()
        if (settings.isEffectiveDndNow() || isPausedNow(nowMs)) {
            DesktopDiagnostics.info("Notification", "Test notification suppressed (DND/paused active)")
            return false
        }
        val testEvent = MirrorNotificationEvent(
            id = -1L,
            packageName = "com.phantom.checker",
            appLabel = "PhantomCheck",
            sender = DesktopStrings.sendTestNotifBtn,
            preview = DesktopStrings.testNotifSent,
            riskLabel = "safe",
            conversationKey = "",
            timestamp = nowMs
        )
        _latestToast.value = testEvent
        if (settings.notificationSoundEnabled) {
            DesktopSoundPlayer.play(settings)
        }
        DesktopDiagnostics.info("Notification", "Test notification dispatched")
        return true
    }
}

/**
 * #513 桌面端通知音效播放器（支援自訂 WAV + javax.sound MASTER_GAIN 音量，無自訂檔時退回系統提示音）。
 */
object DesktopSoundPlayer {
    fun play(settings: DesktopSettings) {
        if (!settings.notificationSoundEnabled || settings.toastSoundVolume <= 0.01f) return
        Thread {
            val wavPath = settings.toastSoundCustomWavPath.trim()
            val playedCustom = if (wavPath.isNotBlank()) {
                runCatching {
                    val file = java.io.File(wavPath)
                    if (!file.exists() || !file.isFile) return@runCatching false
                    val audioIn = javax.sound.sampled.AudioSystem.getAudioInputStream(file)
                    val clip = javax.sound.sampled.AudioSystem.getClip()
                    clip.open(audioIn)
                    if (clip.isControlSupported(javax.sound.sampled.FloatControl.Type.MASTER_GAIN)) {
                        val gain = clip.getControl(javax.sound.sampled.FloatControl.Type.MASTER_GAIN) as javax.sound.sampled.FloatControl
                        val vol = settings.toastSoundVolume.coerceIn(0.01f, 1f)
                        val db = (20.0 * kotlin.math.log10(vol.toDouble())).toFloat().coerceIn(gain.minimum, gain.maximum)
                        gain.value = db
                    }
                    clip.addLineListener { ev ->
                        if (ev.type == javax.sound.sampled.LineEvent.Type.STOP) {
                            runCatching { clip.close() }
                            runCatching { audioIn.close() }
                        }
                    }
                    clip.start()
                    true
                }.getOrDefault(false)
            } else false
            if (!playedCustom) {
                runCatching { java.awt.Toolkit.getDefaultToolkit().beep() }
            }
        }.apply {
            isDaemon = true
            name = "phantom-sound"
            start()
        }
    }
}

/**
 * #497 / #515 前景全螢幕偵測器（Windows 使用 SHQueryUserNotificationState 並快取 2 秒；非 Windows 退回 GraphicsDevice；
 * Toast 路徑於彈出前按需檢查；懸浮球自動隱藏路徑於啟用時每 15 秒低頻檢查一次）。
 */
object DesktopFullscreenDetector {
    @Volatile
    private var lastCheckMs: Long = 0L

    @Volatile
    private var lastResult: Boolean = false

    private const val CACHE_MS = 2_000L

    fun isFullscreenAppActive(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastCheckMs < CACHE_MS) return lastResult
        val detected = runCatching {
            if (DesktopSettingsStore.isWindowsOs()) {
                queryWindowsNotificationStateBusy()
            } else {
                val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
                ge.screenDevices.any { it.fullScreenWindow != null }
            }
        }.getOrDefault(false)
        lastCheckMs = now
        lastResult = detected
        return detected
    }

    private fun queryWindowsNotificationStateBusy(): Boolean {
        // SHQueryUserNotificationState: QUNS_BUSY(2), QUNS_RUNNING_D3D_FULL_SCREEN(3), QUNS_PRESENTATION_MODE(4)
        val psScript = """
            Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public class QunsHelper { [DllImport("shell32.dll")] public static extern int SHQueryUserNotificationState(out int state); }' -ErrorAction SilentlyContinue
            ${'$'}s = 0; [void][QunsHelper]::SHQueryUserNotificationState([ref]${'$'}s); Write-Output ${'$'}s
        """.trimIndent()
        val proc = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", psScript)
            .redirectErrorStream(true)
            .start()
        val out = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor()
        val state = out.lineSequence().lastOrNull()?.trim()?.toIntOrNull() ?: return false
        return state in setOf(2, 3, 4)
    }
}

internal fun primaryWorkAreaBounds(): Rectangle = runCatching {
    GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
}.getOrDefault(Rectangle(0, 0, 1920, 1040))

/**
 * #528 唯一 AWT 螢幕像素 ↔ Compose dp 換算層（依螢幕 GraphicsConfiguration.defaultTransform.scaleX 換算，
 * 解決 Windows 125%/150% 及多螢幕不同 DPI 下懸浮球拖曳漂移與彈窗錯位）。
 */
internal object DesktopScreenMetrics {
    fun displayDensityAt(xPx: Int, yPx: Int): Float = runCatching {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val devices = ge.screenDevices
        val matched = devices.firstOrNull { dev ->
            runCatching { dev.defaultConfiguration.bounds.contains(xPx, yPx) }.getOrDefault(false)
        } ?: devices.minByOrNull { dev ->
            val b = dev.defaultConfiguration.bounds
            val cx = b.x + b.width / 2
            val cy = b.y + b.height / 2
            val dx = (xPx - cx).toLong()
            val dy = (yPx - cy).toLong()
            dx * dx + dy * dy
        } ?: ge.defaultScreenDevice
        val sx = matched.defaultConfiguration.defaultTransform.scaleX.toFloat()
        if (sx.isFinite() && sx > 0f) sx.coerceIn(0.75f, 4.0f)
        else (Toolkit.getDefaultToolkit().screenResolution / 96f).coerceIn(0.75f, 4.0f)
    }.getOrElse { err ->
        DesktopDiagnostics.warn("ScreenMetrics", "Fallback density 1.0f: ${err.message}")
        1.0f
    }

    fun pxToDp(px: Int, density: Float): androidx.compose.ui.unit.Dp =
        (px.toFloat() / density.coerceAtLeast(0.5f)).dp

    fun dpToPx(dpVal: Int, density: Float): Int =
        (dpVal.toFloat() * density.coerceAtLeast(0.5f)).roundToInt()

    fun toWindowPosition(xPx: Int, yPx: Int, density: Float = displayDensityAt(xPx, yPx)): WindowPosition =
        WindowPosition(pxToDp(xPx, density), pxToDp(yPx, density))
}

/**
 * #528 懸浮球自訂懸停動作資料快取（由 DesktopApp 與 DesktopNotificationBus 更新，零輪詢）。
 */
object DesktopHoverDataHolder {
    private val _latestMessage = MutableStateFlow<MessageSummaryDto?>(null)
    val latestMessage: StateFlow<MessageSummaryDto?> = _latestMessage.asStateFlow()

    private val _stats = MutableStateFlow<com.phantom.checker.shared.StatsSnapshotDto?>(null)
    val stats: StateFlow<com.phantom.checker.shared.StatsSnapshotDto?> = _stats.asStateFlow()

    private val _latestIntelSummary = MutableStateFlow<String?>(null)
    val latestIntelSummary: StateFlow<String?> = _latestIntelSummary.asStateFlow()

    fun updateLatestMessage(msg: MessageSummaryDto?) {
        if (msg != null) _latestMessage.value = msg
    }

    fun updateStats(snapshot: com.phantom.checker.shared.StatsSnapshotDto) {
        _stats.value = snapshot
    }

    fun updateLatestIntelOrReport(summary: String?) {
        if (!summary.isNullOrBlank()) _latestIntelSummary.value = summary
    }
}

private val TOAST_TIME_24_FMT = ThreadLocal.withInitial { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
private val TOAST_TIME_12_FMT = ThreadLocal.withInitial { java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()) }
private val HOVER_DATE_FMT = ThreadLocal.withInitial { java.text.SimpleDateFormat("MM/dd EEE", java.util.Locale.getDefault()) }

/**
 * #497 B：獨立 always-on-top 無邊框右下 Toast 視窗（application scope）。
 */
@Composable
fun DesktopToastOverlayWindow(
    settings: DesktopSettings,
    onOpenConversation: (MirrorNotificationEvent) -> Unit
) {
    val toastEvent by DesktopNotificationBus.latestToast.collectAsState()
    val current = toastEvent ?: return

    val tokens = remember(settings.surfaceOpacity, settings.bgScrimAlpha, settings.highContrastText, settings.bgTheme) {
        DesktopThemeTokens.from(settings)
    }
    val accentColor = remember(settings.accentPreset, settings.customAccentHex) {
        DesktopPalette.resolveAccentColor(settings.accentPreset, settings.customAccentHex)
    }

    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()

    val bounds = remember(current.seq) { primaryWorkAreaBounds() }
    val toastDensity = remember(bounds) { DesktopScreenMetrics.displayDensityAt(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2) }
    val toastW = settings.toastWidthDp.coerceIn(280, 480)
    val toastH = settings.toastHeightDp.coerceIn(84, 180)
    val toastWPx = DesktopScreenMetrics.dpToPx(toastW, toastDensity)
    val toastHPx = DesktopScreenMetrics.dpToPx(toastH, toastDensity)
    val marginPx = DesktopScreenMetrics.dpToPx(18, toastDensity)
    val toastXPx = (bounds.x + bounds.width - toastWPx - marginPx).coerceAtLeast(bounds.x)
    val toastYPx = (bounds.y + bounds.height - toastHPx - marginPx).coerceAtLeast(bounds.y)

    var visible by remember(current.seq) { mutableStateOf(false) }

    LaunchedEffect(current.seq) {
        visible = true
    }

    LaunchedEffect(current.seq, isHovered, settings.toastDurationSec) {
        val sec = settings.toastDurationSec
        if (sec <= 0 || isHovered) return@LaunchedEffect
        delay(sec * 1000L)
        visible = false
        delay(180L)
        DesktopNotificationBus.clearToast()
    }

    val toastWindowState = rememberWindowState(
        position = DesktopScreenMetrics.toWindowPosition(toastXPx, toastYPx, toastDensity),
        size = DpSize(toastW.dp, toastH.dp)
    )
    LaunchedEffect(toastW, toastH, toastXPx, toastYPx, toastDensity) {
        toastWindowState.size = DpSize(toastW.dp, toastH.dp)
        toastWindowState.position = DesktopScreenMetrics.toWindowPosition(toastXPx, toastYPx, toastDensity)
    }

    val customToastBgBitmap = remember(settings.toastBgImagePath) {
        val path = settings.toastBgImagePath.trim()
        if (path.isBlank()) null else runCatching {
            val file = java.io.File(path)
            if (file.exists() && file.isFile) {
                org.jetbrains.skia.Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
            } else null
        }.getOrNull()
    }
    val customTextColor = remember(settings.toastTextColorHex, tokens.textPrimary) {
        val hex = settings.toastTextColorHex.trim().removePrefix("#")
        if (hex.length == 6) {
            runCatching { Color(0xFF000000.toInt() or hex.toInt(16)) }.getOrNull() ?: tokens.textPrimary
        } else tokens.textPrimary
    }
    val toastBgColor = tokens.popupBg

    Window(
        onCloseRequest = { DesktopNotificationBus.clearToast() },
        state = toastWindowState,
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = false,
        alwaysOnTop = true,
        title = DesktopStrings.appTitle
    ) {
        ProvideDesktopTheme(settings) {
            val motion = LocalDesktopMotion.current
            MaterialTheme(
                colorScheme = darkColorScheme(
                    surface = tokens.surface,
                    primary = accentColor,
                    onSurface = tokens.textPrimary
                )
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                    val enterAnim = if (motion.isBalanced) {
                        fadeIn(tween(180)) + slideInHorizontally(tween(200)) { it / 2 }
                    } else fadeIn(tween(80))
                    val exitAnim = if (motion.isBalanced) {
                        fadeOut(tween(150)) + slideOutHorizontally(tween(160)) { it / 2 }
                    } else fadeOut(tween(80))
                    AnimatedVisibility(
                        visible = visible,
                        enter = enterAnim,
                        exit = exitAnim
                    ) {
                        val appColor = DesktopAppLabels.resolveColor(current.packageName)
                        val riskColor = when (current.riskLabel.lowercase()) {
                            "scam" -> tokens.error
                            "suspicious" -> tokens.warning
                            else -> tokens.success
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                                .background(toastBgColor)
                                .border(if (tokens.isHighContrast) 2.dp else 1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                                .hoverable(hoverSource)
                                .clickable {
                                    DesktopNotificationBus.clearToast()
                                    onOpenConversation(current)
                                }
                        ) {
                        if (customToastBgBitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = customToastBgBitmap,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(if (settings.lightTheme) Color.White.copy(alpha = 0.72f) else Color.Black.copy(alpha = 0.58f))
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(appColor.copy(alpha = 0.24f))
                                    .border(1.dp, appColor.copy(alpha = 0.7f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = DesktopAppLabels.avatarInitial(current.sender, current.appLabel),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = appColor
                                )
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                val headerText = buildList {
                                    if (settings.toastShowAppName && current.appLabel.isNotBlank()) add(current.appLabel)
                                    if (settings.toastShowTitle && current.sender.isNotBlank()) add(current.sender)
                                }.joinToString(" · ")
                                if (headerText.isNotBlank() || settings.toastShowTime) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = headerText,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = customTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        val rightMeta = if (settings.toastShowTime) {
                                            val sdf = if (settings.use24HourTime) TOAST_TIME_24_FMT.get() else TOAST_TIME_12_FMT.get()
                                            "${sdf.format(java.util.Date(current.timestamp))} · ${DesktopStrings.riskLabelText(current.riskLabel)}"
                                        } else {
                                            DesktopStrings.riskLabelText(current.riskLabel)
                                        }
                                        Text(
                                            text = rightMeta,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = riskColor
                                        )
                                    }
                                }
                                Text(
                                    text = current.preview.take(settings.previewLength),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = customTextColor.copy(alpha = 0.85f),
                                    maxLines = settings.toastMaxLines.coerceIn(1, 4),
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

internal fun allMonitorWorkAreas(): List<Rectangle> = runCatching {
    val toolkit = java.awt.Toolkit.getDefaultToolkit()
    val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
    ge.screenDevices.map { dev ->
        val gc = dev.defaultConfiguration
        val b = gc.bounds
        val insets = runCatching { toolkit.getScreenInsets(gc) }.getOrNull()
        if (insets != null) {
            Rectangle(
                b.x + insets.left,
                b.y + insets.top,
                (b.width - insets.left - insets.right).coerceAtLeast(320),
                (b.height - insets.top - insets.bottom).coerceAtLeast(240)
            )
        } else b
    }.ifEmpty { listOf(primaryWorkAreaBounds()) }
}.getOrDefault(listOf(primaryWorkAreaBounds()))

internal fun workAreaContaining(x: Int, y: Int): Rectangle {
    val monitors = allMonitorWorkAreas()
    monitors.firstOrNull { it.contains(x, y) }?.let { return it }
    return monitors.minByOrNull { r ->
        val cx = r.x + r.width / 2
        val cy = r.y + r.height / 2
        val dx = (x - cx).toLong()
        val dy = (y - cy).toLong()
        dx * dx + dy * dy
    } ?: primaryWorkAreaBounds()
}

internal fun clampToAllMonitors(x: Int, y: Int, w: Int, h: Int): Pair<Int, Int> {
    val target = workAreaContaining(x + w / 2, y + h / 2)
    val cx = x.coerceIn(target.x, (target.x + target.width - w).coerceAtLeast(target.x))
    val cy = y.coerceIn(target.y, (target.y + target.height - h).coerceAtLeast(target.y))
    return cx to cy
}

private fun mergeChatBubblesDedup(existing: List<ChatBubbleDto>, incoming: List<ChatBubbleDto>): List<ChatBubbleDto> {
    val result = existing.toMutableList()
    for (b in incoming) {
        val dup = result.any {
            (b.id > 0L && it.id == b.id && it.role == b.role) ||
                (it.role == b.role && it.content == b.content && kotlin.math.abs(it.createdAt - b.createdAt) < 5_000L)
        }
        if (!dup) result.add(b)
    }
    return result.sortedBy { it.createdAt }.takeLast(60)
}

/**
 * #497 C / #514 / #515：懸浮助手球（AWT MouseInfo 固定抓取點螢幕座標拖曳、多螢幕 DPI 零漂移、鬆手 8px 邊距 spring 吸附、
 * 半圓 180° 快捷扇形選單、與手機雙向同步 AI 對話、Enter 送出 / Shift+Enter 換行、全螢幕自動隱藏）。
 */
@Composable
fun DesktopFloatingAssistantWindow(
    settings: DesktopSettings,
    server: DesktopLinkServer,
    onOpenMainWindow: () -> Unit,
    onOpenConversation: (MirrorNotificationEvent) -> Unit,
    onOpenTab: (String) -> Unit = { onOpenMainWindow() }
) {
    if (!settings.floatingAssistantEnabled) return

    val gson = remember { Gson() }
    val scope = rememberCoroutineScope()
    val tokens = remember(settings.surfaceOpacity, settings.bgScrimAlpha, settings.highContrastText, settings.bgTheme, settings.lightTheme) {
        DesktopThemeTokens.from(settings)
    }
    val accentColor = remember(settings.accentPreset, settings.customAccentHex) {
        DesktopPalette.resolveAccentColor(settings.accentPreset, settings.customAccentHex)
    }

    val serverState by server.state.collectAsState()
    val phoneStatus by DesktopDeviceStatusHolder.status.collectAsState()

    // #515 Fullscreen auto-hide (15s low-frequency check only when enabled & not in NEVER_HIDE mode)
    var isFullscreenHidden by remember { mutableStateOf(false) }
    LaunchedEffect(settings.floatingFullscreenMode) {
        if (settings.floatingFullscreenMode == FullscreenHideMode.NEVER_HIDE) {
            isFullscreenHidden = false
            return@LaunchedEffect
        }
        while (true) {
            val fs = withContext(Dispatchers.IO) { DesktopFullscreenDetector.isFullscreenAppActive() }
            if (fs != isFullscreenHidden) {
                isFullscreenHidden = fs
                if (fs && settings.floatingFullscreenMode == FullscreenHideMode.HIDE_WITH_HINT) {
                    DesktopDiagnostics.info("FloatingOrb", "Hidden while fullscreen app is active")
                }
            }
            delay(15_000L)
        }
    }
    if (isFullscreenHidden) return

    val initialBounds = remember { primaryWorkAreaBounds() }
    val initialDensity = remember(initialBounds) {
        DesktopScreenMetrics.displayDensityAt(initialBounds.x + initialBounds.width / 2, initialBounds.y + initialBounds.height / 2)
    }
    val orbSize = settings.floatingOrbSizeDp.coerceIn(28, 56)
    val initialOrbWinPx = DesktopScreenMetrics.dpToPx(orbSize + 18, initialDensity)

    var orbX by remember {
        mutableIntStateOf(
            if (settings.floatingOrbX != Int.MIN_VALUE) {
                clampToAllMonitors(
                    settings.floatingOrbX,
                    settings.floatingOrbY.takeIf { it != Int.MIN_VALUE } ?: initialBounds.y,
                    initialOrbWinPx,
                    initialOrbWinPx
                ).first
            } else initialBounds.x + initialBounds.width - initialOrbWinPx - DesktopScreenMetrics.dpToPx(8, initialDensity)
        )
    }
    var orbY by remember {
        mutableIntStateOf(
            if (settings.floatingOrbY != Int.MIN_VALUE) {
                clampToAllMonitors(
                    settings.floatingOrbX.takeIf { it != Int.MIN_VALUE } ?: initialBounds.x,
                    settings.floatingOrbY,
                    initialOrbWinPx,
                    initialOrbWinPx
                ).second
            } else initialBounds.y + initialBounds.height - initialOrbWinPx - DesktopScreenMetrics.dpToPx(102, initialDensity)
        )
    }
    val orbDensity = remember(orbX, orbY) { DesktopScreenMetrics.displayDensityAt(orbX, orbY) }
    val orbWinPx = DesktopScreenMetrics.dpToPx(orbSize + 18, orbDensity)
    val activeMonitor = remember(orbX, orbY, orbWinPx) { workAreaContaining(orbX + orbWinPx / 2, orbY + orbWinPx / 2) }

    var aiExpanded by remember { mutableStateOf(false) }
    var radialOpen by remember { mutableStateOf(false) }
    var miniChatInput by remember { mutableStateOf("") }
    var miniChatSending by remember { mutableStateOf(false) }
    var miniChatSessionId by remember { mutableLongStateOf(0L) }
    var miniChatSessions by remember { mutableStateOf<List<ChatSessionSummaryDto>>(emptyList()) }
    var miniChatBubbles by remember { mutableStateOf<List<ChatBubbleDto>>(emptyList()) }
    var miniFontDeltaSp by remember { mutableIntStateOf(0) }
    var copiedBubbleId by remember { mutableLongStateOf(-1L) }

    val bubbleEvent by DesktopNotificationBus.latestFloatingBubble.collectAsState()
    val unreadBadge by DesktopNotificationBus.floatingUnreadBadge.collectAsState()
    val hoverLatestMsg by DesktopHoverDataHolder.latestMessage.collectAsState()
    val hoverStats by DesktopHoverDataHolder.stats.collectAsState()
    val hoverIntelSummary by DesktopHoverDataHolder.latestIntelSummary.collectAsState()

    val orbHoverSource = remember { MutableInteractionSource() }
    val orbHovered by orbHoverSource.collectIsHoveredAsState()
    val bubbleHoverSource = remember { MutableInteractionSource() }
    val bubbleHovered by bubbleHoverSource.collectIsHoveredAsState()

    // #515 Sync sessions & history with phone whenever AI popup expands or reconnects
    LaunchedEffect(aiExpanded, serverState.connectedPhones.size) {
        if (aiExpanded && serverState.connectedPhones.isNotEmpty()) {
            server.sendCommand(LinkMsgType.CHAT_SESSIONS_LIST, UUID.randomUUID().toString(), emptyMap<String, String>())
            if (miniChatSessionId > 0L) {
                server.sendCommand(LinkMsgType.CHAT_HISTORY, UUID.randomUUID().toString(), ChatHistoryReq(sessionId = miniChatSessionId))
            }
        }
    }

    // Safety timeout for miniChatSending (130s)
    LaunchedEffect(miniChatSending) {
        if (miniChatSending) {
            delay(130_000L)
            if (miniChatSending) {
                miniChatSending = false
            }
        }
    }

    // Collect CHAT_SESSIONS_LIST_RES, CHAT_HISTORY_RES, CHAT_REPLY for bidirectional sync
    LaunchedEffect(server) {
        server.events.collect { env ->
            when (env.type) {
                LinkMsgType.CHAT_SESSIONS_LIST_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, ChatSessionsListRes::class.java) }.getOrNull() ?: return@collect
                    miniChatSessions = res.sessions
                    if (miniChatSessionId == 0L && res.sessions.isNotEmpty()) {
                        miniChatSessionId = res.sessions.first().id
                        if (aiExpanded) {
                            server.sendCommand(LinkMsgType.CHAT_HISTORY, UUID.randomUUID().toString(), ChatHistoryReq(sessionId = miniChatSessionId))
                        }
                    }
                }
                LinkMsgType.CHAT_HISTORY_RES -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, ChatHistoryRes::class.java) }.getOrNull() ?: return@collect
                    if (res.sessionId == miniChatSessionId || miniChatSessionId == 0L) {
                        miniChatSessionId = res.sessionId
                        miniChatBubbles = mergeChatBubblesDedup(emptyList(), res.messages)
                    }
                }
                LinkMsgType.CHAT_REPLY -> {
                    val res = runCatching { gson.fromJson(env.payloadJson, ChatReplyRes::class.java) }.getOrNull() ?: return@collect
                    if (miniChatSending || (res.sessionId > 0L && res.sessionId == miniChatSessionId)) {
                        miniChatSending = false
                        if (res.sessionId > 0L) miniChatSessionId = res.sessionId
                        val content = if (res.ok) res.content else "${res.errorCode}: ${res.errorMessage}"
                        val now = System.currentTimeMillis()
                        miniChatBubbles = mergeChatBubblesDedup(
                            miniChatBubbles,
                            listOf(ChatBubbleDto(now, "assistant", content, now))
                        )
                    }
                }
            }
        }
    }

    // #528 Bubble auto-dismiss with resume-on-hover-exit remainingMs state machine
    var bubbleRemainingMs by remember(bubbleEvent?.seq, settings.floatingBubbleSec) {
        mutableLongStateOf(settings.floatingBubbleSec.coerceIn(3, 12) * 1000L)
    }
    LaunchedEffect(bubbleEvent?.seq, bubbleHovered) {
        if (bubbleEvent == null || bubbleHovered) return@LaunchedEffect
        while (bubbleRemainingMs > 0L) {
            val step = minOf(250L, bubbleRemainingMs)
            delay(step)
            bubbleRemainingMs -= step
        }
        DesktopNotificationBus.clearFloatingBubble()
    }

    val isOnRightHalf = orbX + orbWinPx / 2 > activeMonitor.x + activeMonitor.width / 2
    val isOnBottomHalf = orbY + orbWinPx / 2 > activeMonitor.y + activeMonitor.height / 2

    val radialItems = remember(settings.floatingMenuOrder) {
        val parsed = settings.floatingMenuOrder.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (parsed.isEmpty()) listOf("ai", "main", "search", "todos", "dnd", "note", "clipboard", "settings") else parsed.take(8)
    }

    fun sendMiniChat(rawText: String) {
        val text = rawText.trim()
        if (text.isBlank() || miniChatSending) return
        miniChatInput = ""
        miniChatSending = true
        val now = System.currentTimeMillis()
        miniChatBubbles = mergeChatBubblesDedup(miniChatBubbles, listOf(ChatBubbleDto(now, "user", text, now)))
        scope.launch {
            val ok = server.sendCommand(LinkMsgType.CHAT_SEND, UUID.randomUUID().toString(), ChatSendReq(text = text, sessionId = miniChatSessionId))
            if (!ok) {
                miniChatSending = false
                miniChatBubbles = mergeChatBubblesDedup(
                    miniChatBubbles,
                    listOf(ChatBubbleDto(System.currentTimeMillis(), "assistant", DesktopStrings.commandNotSentDisconnected, System.currentTimeMillis()))
                )
            }
        }
    }

    fun executeRadialAction(actionKey: String) {
        radialOpen = false
        when (actionKey) {
            "ai" -> aiExpanded = true
            "main" -> onOpenMainWindow()
            "search" -> onOpenTab("MESSAGES")
            "todos" -> onOpenTab("TODOS")
            "dnd" -> DesktopSettingsStore.update { it.copy(quickDndActive = !it.quickDndActive) }
            "note" -> onOpenTab("TODOS")
            "clipboard" -> onOpenTab("TOOLS")
            "settings" -> onOpenTab("SETTINGS")
            else -> onOpenMainWindow()
        }
    }

    // #528 Primary Orb Window geometry: strictly invariant during hover & bubble!
    // Only click-driven aiExpanded / radialOpen resize this window.
    val winWDp: Int
    val winHDp: Int
    val winXPx: Int
    val winYPx: Int
    when {
        aiExpanded -> {
            winWDp = 400
            winHDp = 500
            val wPx = DesktopScreenMetrics.dpToPx(winWDp, orbDensity)
            val hPx = DesktopScreenMetrics.dpToPx(winHDp, orbDensity)
            winXPx = (if (isOnRightHalf) orbX + orbWinPx - wPx else orbX)
                .coerceIn(activeMonitor.x, (activeMonitor.x + activeMonitor.width - wPx).coerceAtLeast(activeMonitor.x))
            winYPx = (if (isOnBottomHalf) orbY + orbWinPx - hPx else orbY)
                .coerceIn(activeMonitor.y, (activeMonitor.y + activeMonitor.height - hPx).coerceAtLeast(activeMonitor.y))
        }
        radialOpen -> {
            winWDp = 240
            winHDp = 260
            val wPx = DesktopScreenMetrics.dpToPx(winWDp, orbDensity)
            val hPx = DesktopScreenMetrics.dpToPx(winHDp, orbDensity)
            winXPx = (if (isOnRightHalf) orbX + orbWinPx - wPx else orbX)
                .coerceIn(activeMonitor.x, (activeMonitor.x + activeMonitor.width - wPx).coerceAtLeast(activeMonitor.x))
            winYPx = (orbY + orbWinPx / 2 - hPx / 2)
                .coerceIn(activeMonitor.y, (activeMonitor.y + activeMonitor.height - hPx).coerceAtLeast(activeMonitor.y))
        }
        else -> {
            winWDp = orbSize + 18
            winHDp = orbSize + 18
            val clamped = clampToAllMonitors(orbX, orbY, orbWinPx, orbWinPx)
            winXPx = clamped.first
            winYPx = clamped.second
        }
    }

    val floatingState = rememberWindowState(
        position = DesktopScreenMetrics.toWindowPosition(winXPx, winYPx, orbDensity),
        size = DpSize(winWDp.dp, winHDp.dp)
    )
    LaunchedEffect(winXPx, winYPx, winWDp, winHDp, orbDensity) {
        floatingState.position = DesktopScreenMetrics.toWindowPosition(winXPx, winYPx, orbDensity)
        floatingState.size = DpSize(winWDp.dp, winHDp.dp)
    }

    var snapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun finishDragAndSnap() {
        val curDensity = DesktopScreenMetrics.displayDensityAt(orbX, orbY)
        val curOrbPx = DesktopScreenMetrics.dpToPx(orbSize + 18, curDensity)
        val mon = workAreaContaining(orbX + curOrbPx / 2, orbY + curOrbPx / 2)
        val monDensity = DesktopScreenMetrics.displayDensityAt(mon.x + mon.width / 2, mon.y + mon.height / 2)
        val monOrbPx = DesktopScreenMetrics.dpToPx(orbSize + 18, monDensity)
        val marginPx = DesktopScreenMetrics.dpToPx(8, monDensity)
        val maxX = (mon.x + mon.width - monOrbPx - marginPx).coerceAtLeast(mon.x + marginPx)
        val maxY = (mon.y + mon.height - monOrbPx - marginPx).coerceAtLeast(mon.y + marginPx)
        if (!settings.floatingSnapToEdge) {
            orbX = orbX.coerceIn(mon.x + marginPx, maxX)
            orbY = orbY.coerceIn(mon.y + marginPx, maxY)
            floatingState.position = DesktopScreenMetrics.toWindowPosition(orbX, orbY, monDensity)
            DesktopSettingsStore.update { it.copy(floatingOrbX = orbX, floatingOrbY = orbY) }
            return
        }
        val leftDist = (orbX - (mon.x + marginPx)).coerceAtLeast(0)
        val rightDist = (maxX - orbX).coerceAtLeast(0)
        val topDist = (orbY - (mon.y + marginPx)).coerceAtLeast(0)
        val bottomDist = (maxY - orbY).coerceAtLeast(0)
        val minDist = minOf(leftDist, rightDist, topDist, bottomDist)
        val targetX = when (minDist) {
            leftDist -> mon.x + marginPx
            rightDist -> maxX
            else -> orbX.coerceIn(mon.x + marginPx, maxX)
        }
        val targetY = when (minDist) {
            topDist -> mon.y + marginPx
            bottomDist -> maxY
            else -> orbY.coerceIn(mon.y + marginPx, maxY)
        }
        snapJob?.cancel()
        if (!settings.animationsEnabled) {
            orbX = targetX
            orbY = targetY
            floatingState.position = DesktopScreenMetrics.toWindowPosition(orbX, orbY, monDensity)
            DesktopSettingsStore.update { it.copy(floatingOrbX = orbX, floatingOrbY = orbY) }
        } else {
            val startX = orbX.toFloat()
            val startY = orbY.toFloat()
            snapJob = scope.launch {
                val anim = androidx.compose.animation.core.Animatable(0f)
                anim.animateTo(
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = 0.75f,
                        stiffness = 380f
                    )
                ) {
                    val p = value
                    val nx = (startX + (targetX - startX) * p).toInt()
                    val ny = (startY + (targetY - startY) * p).toInt()
                    orbX = nx
                    orbY = ny
                    floatingState.position = DesktopScreenMetrics.toWindowPosition(nx, ny, monDensity)
                }
                orbX = targetX
                orbY = targetY
                floatingState.position = DesktopScreenMetrics.toWindowPosition(targetX, targetY, monDensity)
                DesktopSettingsStore.update { it.copy(floatingOrbX = orbX, floatingOrbY = orbY) }
            }
        }
    }

    val popupBgColor = tokens.popupBg

    Window(
        onCloseRequest = { aiExpanded = false; radialOpen = false },
        state = floatingState,
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = aiExpanded || radialOpen,
        alwaysOnTop = true,
        title = DesktopStrings.floatingAssistantTitle
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                surface = tokens.surface,
                primary = accentColor,
                onSurface = tokens.textPrimary
            )
        ) {
            if (aiExpanded) {
                // #515 Upgraded AI Chat Popup (bidirectional sync, Enter/Shift+Enter, quick commands, copy, font size)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(18.dp))
                        .background(popupBgColor)
                        .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(DesktopStrings.floatingAssistantTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, fontWeight = FontWeight.SemiBold)
                            if (serverState.connectedPhones.isNotEmpty()) {
                                Text(
                                    text = DesktopStrings.syncedWithPhoneChip,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DesktopPalette.GlassSuccess,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DesktopPalette.GlassSuccess.copy(alpha = 0.14f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { miniFontDeltaSp = (miniFontDeltaSp - 1).coerceAtLeast(-2) }) { Text("A-") }
                            TextButton(onClick = { miniFontDeltaSp = (miniFontDeltaSp + 1).coerceAtMost(4) }) { Text("A+") }
                            TextButton(onClick = { aiExpanded = false; onOpenMainWindow() }) { Text(DesktopStrings.trayOpen) }
                            TextButton(onClick = { aiExpanded = false }) { Text("✕") }
                        }
                    }

                    // Session switcher row (synced with phone AI Assistant)
                    if (miniChatSessions.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(miniChatSessions.take(6), key = { it.id }) { sess ->
                                FilterChip(
                                    selected = sess.id == miniChatSessionId,
                                    onClick = {
                                        miniChatSessionId = sess.id
                                        scope.launch {
                                            server.sendCommand(LinkMsgType.CHAT_HISTORY, UUID.randomUUID().toString(), ChatHistoryReq(sessionId = sess.id))
                                        }
                                    },
                                    label = { Text(sess.title.ifBlank { "#${sess.id}" }.take(12), maxLines = 1) }
                                )
                            }
                        }
                    }

                    // Quick command chips
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val quickPrompts = listOf(
                            DesktopStrings.t("今日訊息摘要", "Today's summary") to DesktopStrings.t("請摘要我今天的訊息重點與未讀數", "Summarize my messages today and unread count"),
                            DesktopStrings.t("可疑/詐騙檢查", "Scam check") to DesktopStrings.t("今天有可疑或詐騙訊息嗎？", "Are there any suspicious or scam messages today?"),
                            DesktopStrings.t("重要待辦", "Action items") to DesktopStrings.t("幫我整理今天訊息中的待辦事項", "Extract action items from today's messages")
                        )
                        items(quickPrompts) { (label, prompt) ->
                            FilterChip(
                                selected = false,
                                enabled = !miniChatSending,
                                onClick = { sendMiniChat(prompt) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (miniChatBubbles.isEmpty()) {
                            item {
                                Text(DesktopStrings.emptyChat, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                            }
                        } else {
                            items(miniChatBubbles, key = { "${it.role}_${it.id}_${it.createdAt}" }) { b ->
                                val isUser = b.role == "user"
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isUser) accentColor.copy(alpha = 0.18f) else tokens.surfaceElevated)
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (isUser) {
                                        Text(
                                            text = b.content,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = (13 + miniFontDeltaSp).sp),
                                            color = tokens.textPrimary
                                        )
                                    } else {
                                        DesktopMarkdownText(markdown = b.content, textColor = tokens.textPrimary, accentColor = accentColor)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            Text(
                                                text = if (copiedBubbleId == b.id) DesktopStrings.copiedFeedback else DesktopStrings.copyBtn,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = accentColor,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        runCatching {
                                                            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(b.content), null)
                                                            copiedBubbleId = b.id
                                                        }
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (miniChatSending) {
                            item {
                                Text(DesktopStrings.chatThinking, style = MaterialTheme.typography.labelSmall, color = accentColor)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                        OutlinedTextField(
                            value = miniChatInput,
                            onValueChange = { miniChatInput = it },
                            placeholder = { Text(DesktopStrings.enterSendHint, style = MaterialTheme.typography.labelSmall) },
                            maxLines = 4,
                            modifier = Modifier
                                .weight(1f)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown &&
                                        (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) &&
                                        !keyEvent.isShiftPressed
                                    ) {
                                        sendMiniChat(miniChatInput)
                                        true
                                    } else false
                                }
                        )
                        SpringButton(
                            enabled = miniChatInput.isNotBlank() && !miniChatSending,
                            animationsEnabled = settings.animationsEnabled,
                            onClick = { sendMiniChat(miniChatInput) }
                        ) { Text(DesktopStrings.chatSend) }
                    }
                }
            } else if (radialOpen) {
                // #515 Semi-circular 180-deg radial fan-out menu with spring(0.62f, 380f) & i*24ms stagger
                val radiusPx = 86f
                val centerX = if (isOnRightHalf) (winWDp - orbSize / 2 - 14) else (orbSize / 2 + 14)
                val centerY = winHDp / 2
                Box(modifier = Modifier.fillMaxSize()) {
                    radialItems.forEachIndexed { idx, actionKey ->
                        val progress = remember(radialOpen, idx) { androidx.compose.animation.core.Animatable(if (settings.animationsEnabled) 0f else 1f) }
                        LaunchedEffect(radialOpen, settings.animationsEnabled) {
                            if (settings.animationsEnabled) {
                                delay(idx * 24L)
                                progress.animateTo(
                                    1f,
                                    androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 380f)
                                )
                            } else {
                                progress.snapTo(1f)
                            }
                        }
                        val count = radialItems.size.coerceAtLeast(1)
                        val frac = if (count == 1) 0.5f else idx.toFloat() / (count - 1).toFloat()
                        // Left semicircle [90°, 270°] if orb is on right half; right semicircle [-90°, 90°] if on left half
                        val angleDeg = if (isOnRightHalf) (105.0 + frac * 150.0) else (-75.0 + frac * 150.0)
                        val rad = angleDeg * PI / 180.0
                        val p = progress.value
                        val dx = (cos(rad) * radiusPx * p).roundToInt()
                        val dy = (sin(rad) * radiusPx * p).roundToInt()
                        val label = when (actionKey) {
                            "ai" -> DesktopStrings.radialAi
                            "main" -> DesktopStrings.radialMain
                            "search" -> DesktopStrings.radialSearch
                            "todos" -> DesktopStrings.radialTodos
                            "dnd" -> if (settings.quickDndActive) "${DesktopStrings.radialDnd}✓" else DesktopStrings.radialDnd
                            "note" -> DesktopStrings.radialNote
                            "clipboard" -> DesktopStrings.radialClipboard
                            "settings" -> DesktopStrings.radialSettings
                            else -> actionKey
                        }
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(centerX + dx - 30, centerY + dy - 16) }
                                .graphicsLayer {
                                    alpha = p.coerceIn(0f, 1f)
                                    scaleX = 0.7f + 0.3f * p
                                    scaleY = 0.7f + 0.3f * p
                                }
                                .clip(RoundedCornerShape(14.dp))
                                .background(popupBgColor)
                                .border(1.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                                .clickable { executeRadialAction(actionKey) }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = tokens.textPrimary, maxLines = 1)
                        }
                    }

                    // Center close orb
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(centerX - orbSize / 2, centerY - orbSize / 2) }
                            .size(orbSize.dp)
                            .clip(CircleShape)
                            .background(popupBgColor)
                            .border(2.dp, accentColor, CircleShape)
                            .clickable { radialOpen = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accentColor)
                    }
                }
            } else {
                // #528 Orb-only fixed-size window (geometry NEVER changes on hover/bubble -> zero jitter)
                val idleAlpha = if (orbHovered || bubbleEvent != null) 1f else settings.floatingOrbOpacity.coerceIn(0.35f, 1f)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size((orbSize + 8).dp)
                            .graphicsLayer { alpha = idleAlpha }
                            .hoverable(orbHoverSource)
                            .pointerInput(orbSize, settings.floatingSnapToEdge) {
                                var dragStartPointerX = 0
                                var dragStartPointerY = 0
                                var dragStartOrbX = 0
                                var dragStartOrbY = 0
                                detectDragGestures(
                                    onDragStart = {
                                        snapJob?.cancel()
                                        val pt = runCatching { java.awt.MouseInfo.getPointerInfo()?.location }.getOrNull()
                                        if (pt != null) {
                                            dragStartPointerX = pt.x
                                            dragStartPointerY = pt.y
                                        }
                                        dragStartOrbX = orbX
                                        dragStartOrbY = orbY
                                    },
                                    onDragEnd = { finishDragAndSnap() },
                                    onDragCancel = { finishDragAndSnap() },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val pt = runCatching { java.awt.MouseInfo.getPointerInfo()?.location }.getOrNull() ?: return@detectDragGestures
                                        val rawX = dragStartOrbX + (pt.x - dragStartPointerX)
                                        val rawY = dragStartOrbY + (pt.y - dragStartPointerY)
                                        val dragDensity = DesktopScreenMetrics.displayDensityAt(rawX, rawY)
                                        val dragOrbPx = DesktopScreenMetrics.dpToPx(orbSize + 18, dragDensity)
                                        val clamped = clampToAllMonitors(rawX, rawY, dragOrbPx, dragOrbPx)
                                        orbX = clamped.first
                                        orbY = clamped.second
                                        // #528 Synchronous position update (zero 1-frame LaunchedEffect lag, zero 14ms throttle)
                                        floatingState.position = DesktopScreenMetrics.toWindowPosition(clamped.first, clamped.second, dragDensity)
                                    }
                                )
                            }
                            .clickable {
                                DesktopNotificationBus.clearFloatingBadge()
                                radialOpen = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(orbSize.dp)
                                .clip(CircleShape)
                                .background(popupBgColor)
                                .border(2.dp, accentColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "P",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }
                        if (unreadBadge > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(DesktopPalette.GlassError)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = if (unreadBadge > 99) "99+" else unreadBadge.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // #528 Independent second Window for Hover Card (6 customizable actions) & Notification Bubble.
    // Rationale: JetBrains/compose-multiplatform#1401 transparent windows intercept mouse events, so
    // keeping a permanently enlarged orb window is invalid; a separate companion window keeps the orb
    // window geometry 100% invariant during hover and eliminates the resize->unhover->shrink loop.
    val currentBubble = bubbleEvent
    if (!aiExpanded && !radialOpen && (currentBubble != null || orbHovered)) {
        val isBubbleMode = currentBubble != null
        val auxWDp = if (isBubbleMode) 280 else 192
        val auxHDp = if (isBubbleMode) maxOf(orbSize + 24, 108) else 68
        val auxWPx = DesktopScreenMetrics.dpToPx(auxWDp, orbDensity)
        val auxHPx = DesktopScreenMetrics.dpToPx(auxHDp, orbDensity)
        val gapPx = DesktopScreenMetrics.dpToPx(8, orbDensity)
        val auxXPx = (if (isOnRightHalf) orbX - auxWPx - gapPx else orbX + orbWinPx + gapPx)
            .coerceIn(activeMonitor.x, (activeMonitor.x + activeMonitor.width - auxWPx).coerceAtLeast(activeMonitor.x))
        val auxYPx = (if (isOnBottomHalf) orbY + orbWinPx - auxHPx else orbY)
            .coerceIn(activeMonitor.y, (activeMonitor.y + activeMonitor.height - auxHPx).coerceAtLeast(activeMonitor.y))

        val auxState = rememberWindowState(
            position = DesktopScreenMetrics.toWindowPosition(auxXPx, auxYPx, orbDensity),
            size = DpSize(auxWDp.dp, auxHDp.dp)
        )
        LaunchedEffect(auxXPx, auxYPx, auxWDp, auxHDp, orbDensity) {
            auxState.position = DesktopScreenMetrics.toWindowPosition(auxXPx, auxYPx, orbDensity)
            auxState.size = DpSize(auxWDp.dp, auxHDp.dp)
        }

        Window(
            onCloseRequest = { DesktopNotificationBus.clearFloatingBubble() },
            state = auxState,
            undecorated = true,
            transparent = true,
            resizable = false,
            focusable = false,
            alwaysOnTop = true,
            title = DesktopStrings.floatingAssistantTitle
        ) {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    surface = tokens.surface,
                    primary = accentColor,
                    onSurface = tokens.textPrimary
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    contentAlignment = if (isOnBottomHalf) Alignment.BottomCenter else Alignment.TopCenter
                ) {
                    if (currentBubble != null) {
                        val displayText = when (settings.floatingBubbleContent) {
                            FloatingBubbleContent.APP_ONLY -> currentBubble.appLabel
                            FloatingBubbleContent.SENDER_PREVIEW -> "${currentBubble.sender}: ${currentBubble.preview.take(48)}"
                            FloatingBubbleContent.FULL_PREVIEW -> "${currentBubble.appLabel} · ${currentBubble.sender}\n${currentBubble.preview.take(settings.previewLength)}"
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(popupBgColor)
                                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                .hoverable(bubbleHoverSource)
                                .clickable {
                                    DesktopNotificationBus.clearFloatingBubble()
                                    DesktopNotificationBus.clearFloatingBadge()
                                    onOpenConversation(currentBubble)
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textPrimary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        // #528 6 Customizable Hover Actions (184dp glass card)
                        val (hoverTitle, hoverSub) = when (settings.floatingHoverAction) {
                            FloatingHoverAction.CONNECTION_BATTERY -> {
                                val battStr = phoneStatus?.let { st ->
                                    val ch = if (st.isCharging) " ⚡" else ""
                                    "${st.batteryLevel}%$ch"
                                } ?: "--"
                                val connStr = if (serverState.connectedPhones.isNotEmpty()) {
                                    "${serverState.pairedDeviceName.ifBlank { "Phone" }} · $battStr"
                                } else {
                                    DesktopStrings.t("未連線", "Disconnected")
                                }
                                connStr to ""
                            }
                            FloatingHoverAction.LATEST_MESSAGE -> {
                                val msg = hoverLatestMsg
                                if (msg != null) {
                                    val app = DesktopAppLabels.resolveLabel(msg.packageName, msg.appLabel)
                                    val sender = resolveMessageTitle(msg.sender, msg.packageName, app)
                                    "$app · $sender" to msg.preview.take(48)
                                } else {
                                    DesktopStrings.floatingHoverNoMessage to ""
                                }
                            }
                            FloatingHoverAction.TODAY_STATS -> {
                                val st = hoverStats
                                DesktopStrings.floatingHoverActionName(FloatingHoverAction.TODAY_STATS) to
                                    DesktopStrings.floatingHoverStatsLine(st?.today ?: 0, st?.unread ?: 0, st?.scam ?: 0)
                            }
                            FloatingHoverAction.LATEST_INTEL_REPORT -> {
                                DesktopStrings.floatingHoverActionName(FloatingHoverAction.LATEST_INTEL_REPORT) to
                                    (hoverIntelSummary?.take(56) ?: DesktopStrings.floatingHoverNoIntel)
                            }
                            FloatingHoverAction.CLOCK_DATE -> {
                                val now = java.util.Date()
                                val timeSdf = if (settings.use24HourTime) TOAST_TIME_24_FMT.get() else TOAST_TIME_12_FMT.get()
                                timeSdf.format(now) to HOVER_DATE_FMT.get().format(now)
                            }
                            FloatingHoverAction.CUSTOM_TEXT -> {
                                settings.floatingHoverCustomText.trim().take(40).ifBlank {
                                    DesktopStrings.appTitle
                                } to ""
                            }
                        }
                        Column(
                            modifier = Modifier
                                .width(184.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(popupBgColor)
                                .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = hoverTitle,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (hoverSub.isNotBlank()) {
                                Text(
                                    text = hoverSub,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.textSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
