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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalDensity
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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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

    private val busLock = Any()
    private val toastMinuteWindow = ArrayDeque<Long>()
    private val floatingMinuteWindow = ArrayDeque<Long>()
    private var aggWindowStartMs: Long = 0L
    private var aggCount: Int = 0
    private var aggLastEvent: MirrorNotificationEvent? = null

    fun clearFloatingBadge() {
        synchronized(busLock) {
            _floatingUnreadBadge.value = 0
        }
    }

    private fun allowUnderPerMinuteCapLocked(window: ArrayDeque<Long>, maxPerMin: Int, nowMs: Long): Boolean {
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
        val toastAllowedByStar = !settings.toastOnlyStarred || msg.isStarred
        val floatingAllowedByStar = !settings.floatingOnlyStarred || msg.isStarred

        // Non-blocking fullscreen check (returns cached state and refreshes asynchronously if stale)
        val isFullscreen = if (!dndOrPaused && toastAllowedByStar && settings.toastEnabled &&
            (settings.mirrorMode == MirrorDisplayMode.TOAST || settings.mirrorMode == MirrorDisplayMode.BOTH || settings.mirrorMode == MirrorDisplayMode.COMBO)
        ) {
            DesktopFullscreenDetector.isFullscreenAppActive()
        } else {
            false
        }

        var shouldPlaySound = false

        synchronized(busLock) {
            // A. In-app top banner
            if (!dndOrPaused && settings.bannerEnabled &&
                (settings.mirrorMode == MirrorDisplayMode.BANNER || settings.mirrorMode == MirrorDisplayMode.BOTH || settings.mirrorMode == MirrorDisplayMode.COMBO)
            ) {
                _latestBanner.value = event
            }

            // B. Bottom-right desktop toast (suppressed in DND/paused or fullscreen, respects #513 frequency & aggregation)
            if (!dndOrPaused && !isFullscreen && toastAllowedByStar && settings.toastEnabled &&
                (settings.mirrorMode == MirrorDisplayMode.TOAST || settings.mirrorMode == MirrorDisplayMode.BOTH || settings.mirrorMode == MirrorDisplayMode.COMBO)
            ) {
                val aggMin = settings.toastAggregateMinutes.coerceIn(0, 15)
                if (aggMin > 0) {
                    val aggMs = aggMin * 60_000L
                    if (aggWindowStartMs == 0L) {
                        aggWindowStartMs = nowMs
                        aggCount = 1
                        aggLastEvent = event
                        if (allowUnderPerMinuteCapLocked(toastMinuteWindow, settings.toastMaxPerMinute, nowMs)) {
                            _latestToast.value = event
                            shouldPlaySound = settings.notificationSoundEnabled
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
                        if (allowUnderPerMinuteCapLocked(toastMinuteWindow, settings.toastMaxPerMinute, nowMs)) {
                            _latestToast.value = summaryEvent
                            shouldPlaySound = settings.notificationSoundEnabled
                        }
                    } else {
                        aggCount++
                        aggLastEvent = event
                    }
                } else if (allowUnderPerMinuteCapLocked(toastMinuteWindow, settings.toastMaxPerMinute, nowMs)) {
                    _latestToast.value = event
                    shouldPlaySound = settings.notificationSoundEnabled
                }
            }

            // C. Floating assistant bubble or badge-only in DND/paused (separate #513 frequency controls)
            if (settings.floatingAssistantEnabled) {
                if (dndOrPaused) {
                    _floatingUnreadBadge.value = (_floatingUnreadBadge.value + 1).coerceAtMost(99)
                } else if (floatingAllowedByStar && allowUnderPerMinuteCapLocked(floatingMinuteWindow, settings.floatingMaxPerMinute, nowMs)) {
                    _latestFloatingBubble.value = event
                    _floatingUnreadBadge.value = (_floatingUnreadBadge.value + 1).coerceAtMost(99)
                } else {
                    _floatingUnreadBadge.value = (_floatingUnreadBadge.value + 1).coerceAtMost(99)
                }
            }
        }

        // Perform external side effects outside busLock
        if (shouldPlaySound) {
            DesktopSoundPlayer.play(settings)
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
        synchronized(busLock) {
            _latestToast.value = testEvent
        }
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
                    try {
                        clip.open(audioIn)
                    } catch (e: Throwable) {
                        runCatching { clip.close() }
                        runCatching { audioIn.close() }
                        throw e
                    }
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
 * #497 / #515 / #550 前景全螢幕偵測器：
 * - Windows 透過 PowerShell SHQueryUserNotificationState 查詢，採用非同步管線（關閉 stdin、daemon 讀取執行緒有界排空、3 秒逾時強制終止）
 * - isFullscreenAppActive() 永不阻塞呼叫端：立即回傳快取結果，若超過快取效期則於背景單執行緒非同步刷新
 * - 懸浮球自動隱藏迴圈透過 refreshFullscreenState() 於 Dispatchers.IO 執行有界更新
 */
object DesktopFullscreenDetector {
    @Volatile
    private var lastCheckMs: Long = 0L

    @Volatile
    private var lastResult: Boolean = false

    private val refreshInFlight = AtomicBoolean(false)

    private val detectorExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "phantom-fullscreen-detector").apply { isDaemon = true }
    }

    private const val CACHE_MS = 2_000L
    private const val PS_TIMEOUT_SECONDS = 3L
    private const val MAX_OUTPUT_CHARS = 16 * 1024

    private val WINDOWS_QUNS_SCRIPT = """
        Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public class QunsHelper { [DllImport("shell32.dll")] public static extern int SHQueryUserNotificationState(out int state); }' -ErrorAction SilentlyContinue
        ${'$'}s = 0; [void][QunsHelper]::SHQueryUserNotificationState([ref]${'$'}s); Write-Output ${'$'}s
    """.trimIndent()

    /**
     * Non-blocking check: returns cached fullscreen state immediately and schedules an async refresh
     * on a background daemon thread if the cache has expired.
     */
    fun isFullscreenAppActive(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastCheckMs >= CACHE_MS && refreshInFlight.compareAndSet(false, true)) {
            runCatching {
                detectorExecutor.execute {
                    try {
                        refreshFullscreenStateInternal()
                    } finally {
                        refreshInFlight.set(false)
                    }
                }
            }.onFailure {
                refreshInFlight.set(false)
            }
        }
        return lastResult
    }

    /**
     * Bounded blocking check intended for Dispatchers.IO callers (e.g. 15s floating orb poll).
     */
    fun refreshFullscreenState(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastCheckMs < CACHE_MS) return lastResult
        if (!refreshInFlight.compareAndSet(false, true)) return lastResult
        return try {
            refreshFullscreenStateInternal()
        } finally {
            refreshInFlight.set(false)
        }
    }

    private fun refreshFullscreenStateInternal(): Boolean {
        val detected = runCatching {
            if (DesktopSettingsStore.isWindowsOs()) {
                queryWindowsNotificationStateBusy()
            } else {
                val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
                ge.screenDevices.any { it.fullScreenWindow != null }
            }
        }.getOrDefault(false)
        lastResult = detected
        lastCheckMs = System.currentTimeMillis()
        return detected
    }

    private fun queryWindowsNotificationStateBusy(): Boolean {
        // SHQueryUserNotificationState: QUNS_BUSY(2), QUNS_RUNNING_D3D_FULL_SCREEN(3), QUNS_PRESENTATION_MODE(4)
        val proc = ProcessBuilder(
            "powershell.exe",
            "-NoProfile",
            "-NonInteractive",
            "-WindowStyle", "Hidden",
            "-Command", WINDOWS_QUNS_SCRIPT
        )
            .redirectErrorStream(true)
            .start()
        // Close stdin immediately so PowerShell never waits on input
        runCatching { proc.outputStream.close() }
        val outputRef = AtomicReference("")
        val readerThread = Thread({
            val sb = StringBuilder()
            runCatching {
                proc.inputStream.bufferedReader().use { reader ->
                    val buf = CharArray(1024)
                    while (true) {
                        val read = reader.read(buf)
                        if (read < 0) break
                        if (sb.length < MAX_OUTPUT_CHARS) {
                            val remain = MAX_OUTPUT_CHARS - sb.length
                            sb.append(buf, 0, minOf(read, remain))
                        }
                    }
                }
            }
            outputRef.set(sb.toString().trim())
        }, "phantom-ps-quns-reader").apply {
            isDaemon = true
            start()
        }
        val finished = proc.waitFor(PS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            runCatching { proc.waitFor(1, TimeUnit.SECONDS) }
            runCatching { proc.inputStream.close() }
            runCatching { readerThread.join(500L) }
            return false
        }
        runCatching { readerThread.join(1_000L) }
        val state = outputRef.get().lineSequence().lastOrNull()?.trim()?.toIntOrNull() ?: return false
        return state in setOf(2, 3, 4)
    }
}

internal fun primaryWorkAreaBounds(): Rectangle = runCatching {
    GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
}.getOrDefault(Rectangle(0, 0, 1920, 1040))

/**
 * #528 / #550 AWT 邏輯座標 ↔ Compose Desktop WindowPosition 轉換層：
 * 在 Java 9+ / Compose Desktop (HiDPI) 環境下，AWT 的 GraphicsConfiguration.bounds、
 * GraphicsEnvironment.maximumWindowBounds、Toolkit.getScreenInsets、MouseInfo.getPointerInfo().location
 * 以及 Compose WindowPosition(x.dp, y.dp) / DpSize(w.dp, h.dp) 皆已統一採用 AWT 使用者空間邏輯座標（1:1 對應 Compose dp）。
 * 不可再重複乘除以 GraphicsConfiguration.defaultTransform.scaleX，否則在 Windows 125%/150% DPI 下會導致懸浮球與彈窗錯位。
 */
internal object DesktopScreenMetrics {
    fun displayDensityAt(xLogical: Int, yLogical: Int): Float = runCatching {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val devices = ge.screenDevices
        val matched = devices.firstOrNull { dev ->
            runCatching { dev.defaultConfiguration.bounds.contains(xLogical, yLogical) }.getOrDefault(false)
        } ?: devices.minByOrNull { dev ->
            val b = dev.defaultConfiguration.bounds
            val cx = b.x + b.width / 2
            val cy = b.y + b.height / 2
            val dx = (xLogical - cx).toLong()
            val dy = (yLogical - cy).toLong()
            dx * dx + dy * dy
        } ?: ge.defaultScreenDevice
        val sx = matched.defaultConfiguration.defaultTransform.scaleX.toFloat()
        if (sx.isFinite() && sx > 0f) sx.coerceIn(0.75f, 4.0f)
        else (Toolkit.getDefaultToolkit().screenResolution / 96f).coerceIn(0.75f, 4.0f)
    }.getOrElse { err ->
        DesktopDiagnostics.warn("ScreenMetrics", "Fallback density 1.0f: ${err.message}")
        1.0f
    }

    fun toWindowPosition(xLogical: Int, yLogical: Int): WindowPosition =
        WindowPosition(xLogical.dp, yLogical.dp)
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

    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()

    val bounds = remember(current.seq) { primaryWorkAreaBounds() }
    val toastW = settings.toastWidthDp.coerceIn(280, 480)
    val toastH = settings.toastHeightDp.coerceIn(84, 180)
    val marginDp = 18
    val toastX = (bounds.x + bounds.width - toastW - marginDp).coerceAtLeast(bounds.x)
    val toastY = (bounds.y + bounds.height - toastH - marginDp).coerceAtLeast(bounds.y)

    var visible by remember(current.seq) { mutableStateOf(false) }

    LaunchedEffect(current.seq) {
        visible = true
    }

    // #529 Hover pause = resume remainingMs state machine (1s tick, never resets on hover exit; resets only on new seq)
    val initialDurationMs = settings.toastDurationSec.coerceIn(3, 20) * 1000L
    var remainingMs by remember(current.seq, initialDurationMs) { mutableLongStateOf(initialDurationMs) }
    LaunchedEffect(current.seq, isHovered) {
        if (isHovered) return@LaunchedEffect
        while (remainingMs > 0L) {
            val step = minOf(1000L, remainingMs)
            delay(step)
            remainingMs = (remainingMs - step).coerceAtLeast(0L)
        }
        visible = false
        delay(180L)
        DesktopNotificationBus.clearToast()
    }

    val toastWindowState = rememberWindowState(
        position = DesktopScreenMetrics.toWindowPosition(toastX, toastY),
        size = DpSize(toastW.dp, toastH.dp)
    )
    LaunchedEffect(toastW, toastH, toastX, toastY) {
        toastWindowState.size = DpSize(toastW.dp, toastH.dp)
        toastWindowState.position = DesktopScreenMetrics.toWindowPosition(toastX, toastY)
    }

    val customToastBgBitmap = remember(settings.toastBgImagePath) {
        val path = settings.toastBgImagePath.trim()
        if (path.isBlank()) null else runCatching {
            val file = java.io.File(path)
            if (!file.exists() || !file.isFile) {
                DesktopDiagnostics.warn("ToastBg", "Background image not found, falling back to solid color: $path")
                null
            } else if (file.length() > 5L * 1024L * 1024L) {
                DesktopDiagnostics.warn("ToastBg", "Background image exceeds 5MB, falling back to solid color: $path")
                null
            } else {
                org.jetbrains.skia.Image.makeFromEncoded(file.readBytes()).use { it.toComposeImageBitmap() }
            }
        }.onFailure { err ->
            DesktopDiagnostics.warn("ToastBg", "Failed decoding toast background image ($path): ${err.javaClass.simpleName}: ${err.message}")
        }.getOrNull()
    }
    val scrimAlpha = settings.toastBgScrimAlpha.coerceIn(0.30f, 0.90f)

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
            val tokens = LocalDesktopTokens.current
            val motion = LocalDesktopMotion.current
            val accentColor = tokens.accent
            val customTextColor = remember(settings.toastTextColorHex, tokens.textPrimary) {
                DesktopPalette.parseHexColor(settings.toastTextColorHex) ?: tokens.textPrimary
            }
            val toastBgColor = tokens.popupBg
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
                            "suspicious", "warning" -> tokens.warning
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
                                    .background(if (settings.lightTheme) Color.White.copy(alpha = scrimAlpha) else Color.Black.copy(alpha = scrimAlpha))
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
                        if (settings.toastShowCountdown) {
                            val remSec = ((remainingMs + 999L) / 1000L).coerceAtLeast(0L)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 8.dp, bottom = 5.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.42f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "${remSec}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = customTextColor.copy(alpha = 0.9f)
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

private class OrbDragState(
    var x: Int,
    var y: Int,
    var isDragging: Boolean = false
)

/**
 * #497 C / #514 / #515 / #550：懸浮助手球（AWT 邏輯座標固定抓取點拖曳、125%/150% 高 DPI 精準定位、拖曳零重組、鬆手 8dp 邊距 spring 吸附、
 * 半圓 180° 快捷扇形選單、與手機雙向同步 AI 對話、Enter 送出 / Shift+Enter 換行、非同步全螢幕自動隱藏）。
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
    val tokens = remember(
        settings.surfaceOpacity,
        settings.bgScrimAlpha,
        settings.highContrastText,
        settings.bgTheme,
        settings.lightTheme,
        settings.accentPreset,
        settings.customAccentHex,
        settings.messageCardCornerDp
    ) {
        DesktopThemeTokens.from(settings)
    }
    val accentColor = tokens.accent

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
            val fs = withContext(Dispatchers.IO) { DesktopFullscreenDetector.refreshFullscreenState() }
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
    val orbSize = settings.floatingOrbSizeDp.coerceIn(28, 56)
    val orbWinDp = orbSize + 18

    var orbX by remember {
        mutableIntStateOf(
            if (settings.floatingOrbX != Int.MIN_VALUE) {
                clampToAllMonitors(
                    settings.floatingOrbX,
                    settings.floatingOrbY.takeIf { it != Int.MIN_VALUE } ?: initialBounds.y,
                    orbWinDp,
                    orbWinDp
                ).first
            } else initialBounds.x + initialBounds.width - orbWinDp - 8
        )
    }
    var orbY by remember {
        mutableIntStateOf(
            if (settings.floatingOrbY != Int.MIN_VALUE) {
                clampToAllMonitors(
                    settings.floatingOrbX.takeIf { it != Int.MIN_VALUE } ?: initialBounds.x,
                    settings.floatingOrbY,
                    orbWinDp,
                    orbWinDp
                ).second
            } else initialBounds.y + initialBounds.height - orbWinDp - 102
        )
    }
    // Non-observable drag coordinate holder so dragging/snapping updates WindowPosition directly with zero recomposition
    val dragState = remember { OrbDragState(orbX, orbY) }
    if (!dragState.isDragging) {
        dragState.x = orbX
        dragState.y = orbY
    }
    val activeMonitor = remember(orbX, orbY, orbWinDp) { workAreaContaining(orbX + orbWinDp / 2, orbY + orbWinDp / 2) }

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

    val isOnRightHalf = orbX + orbWinDp / 2 > activeMonitor.x + activeMonitor.width / 2
    val isOnBottomHalf = orbY + orbWinDp / 2 > activeMonitor.y + activeMonitor.height / 2

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

    // #528 / #550 Primary Orb Window geometry in AWT logical coordinates (= Compose dp):
    // Strictly invariant during hover & bubble; only click-driven aiExpanded / radialOpen resize this window.
    val winWDp: Int
    val winHDp: Int
    val winX: Int
    val winY: Int
    when {
        aiExpanded -> {
            winWDp = 400
            winHDp = 500
            winX = (if (isOnRightHalf) orbX + orbWinDp - winWDp else orbX)
                .coerceIn(activeMonitor.x, (activeMonitor.x + activeMonitor.width - winWDp).coerceAtLeast(activeMonitor.x))
            winY = (if (isOnBottomHalf) orbY + orbWinDp - winHDp else orbY)
                .coerceIn(activeMonitor.y, (activeMonitor.y + activeMonitor.height - winHDp).coerceAtLeast(activeMonitor.y))
        }
        radialOpen -> {
            winWDp = 240
            winHDp = 260
            winX = (if (isOnRightHalf) orbX + orbWinDp - winWDp else orbX)
                .coerceIn(activeMonitor.x, (activeMonitor.x + activeMonitor.width - winWDp).coerceAtLeast(activeMonitor.x))
            winY = (orbY + orbWinDp / 2 - winHDp / 2)
                .coerceIn(activeMonitor.y, (activeMonitor.y + activeMonitor.height - winHDp).coerceAtLeast(activeMonitor.y))
        }
        else -> {
            winWDp = orbWinDp
            winHDp = orbWinDp
            val clamped = clampToAllMonitors(orbX, orbY, orbWinDp, orbWinDp)
            winX = clamped.first
            winY = clamped.second
        }
    }

    val floatingState = rememberWindowState(
        position = DesktopScreenMetrics.toWindowPosition(winX, winY),
        size = DpSize(winWDp.dp, winHDp.dp)
    )
    LaunchedEffect(winX, winY, winWDp, winHDp) {
        if (!dragState.isDragging) {
            floatingState.position = DesktopScreenMetrics.toWindowPosition(winX, winY)
        }
        floatingState.size = DpSize(winWDp.dp, winHDp.dp)
    }

    var snapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun finishDragAndSnap() {
        val curX = dragState.x
        val curY = dragState.y
        val mon = workAreaContaining(curX + orbWinDp / 2, curY + orbWinDp / 2)
        val marginDp = 8
        val maxX = (mon.x + mon.width - orbWinDp - marginDp).coerceAtLeast(mon.x + marginDp)
        val maxY = (mon.y + mon.height - orbWinDp - marginDp).coerceAtLeast(mon.y + marginDp)
        if (!settings.floatingSnapToEdge) {
            val finalX = curX.coerceIn(mon.x + marginDp, maxX)
            val finalY = curY.coerceIn(mon.y + marginDp, maxY)
            dragState.x = finalX
            dragState.y = finalY
            dragState.isDragging = false
            floatingState.position = DesktopScreenMetrics.toWindowPosition(finalX, finalY)
            orbX = finalX
            orbY = finalY
            DesktopSettingsStore.update { it.copy(floatingOrbX = finalX, floatingOrbY = finalY) }
            return
        }
        val leftDist = (curX - (mon.x + marginDp)).coerceAtLeast(0)
        val rightDist = (maxX - curX).coerceAtLeast(0)
        val topDist = (curY - (mon.y + marginDp)).coerceAtLeast(0)
        val bottomDist = (maxY - curY).coerceAtLeast(0)
        val minDist = minOf(leftDist, rightDist, topDist, bottomDist)
        val targetX = when (minDist) {
            leftDist -> mon.x + marginDp
            rightDist -> maxX
            else -> curX.coerceIn(mon.x + marginDp, maxX)
        }
        val targetY = when (minDist) {
            topDist -> mon.y + marginDp
            bottomDist -> maxY
            else -> curY.coerceIn(mon.y + marginDp, maxY)
        }
        snapJob?.cancel()
        if (!settings.animationsEnabled || settings.animationMode == AnimationMode.POWER_SAVER) {
            dragState.x = targetX
            dragState.y = targetY
            dragState.isDragging = false
            floatingState.position = DesktopScreenMetrics.toWindowPosition(targetX, targetY)
            orbX = targetX
            orbY = targetY
            DesktopSettingsStore.update { it.copy(floatingOrbX = targetX, floatingOrbY = targetY) }
        } else {
            val startX = curX.toFloat()
            val startY = curY.toFloat()
            snapJob = scope.launch {
                try {
                    val anim = androidx.compose.animation.core.Animatable(0f)
                    anim.animateTo(
                        targetValue = 1f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = 0.75f,
                            stiffness = 380f
                        )
                    ) {
                        val p = value
                        val nx = (startX + (targetX - startX) * p).roundToInt()
                        val ny = (startY + (targetY - startY) * p).roundToInt()
                        dragState.x = nx
                        dragState.y = ny
                        floatingState.position = DesktopScreenMetrics.toWindowPosition(nx, ny)
                    }
                    dragState.x = targetX
                    dragState.y = targetY
                    floatingState.position = DesktopScreenMetrics.toWindowPosition(targetX, targetY)
                    orbX = targetX
                    orbY = targetY
                    DesktopSettingsStore.update { it.copy(floatingOrbX = targetX, floatingOrbY = targetY) }
                } finally {
                    dragState.isDragging = false
                }
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
        ProvideDesktopTheme(settings) {
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
                                    color = tokens.success,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(tokens.success.copy(alpha = 0.14f))
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
                // #515 / #550 Semi-circular 180-deg radial fan-out menu (DPI-aware IntOffset scaling)
                val composeDensity = LocalDensity.current.density
                val radiusDp = 86f
                val centerXDp = if (isOnRightHalf) (winWDp - orbSize / 2f - 14f) else (orbSize / 2f + 14f)
                val centerYDp = winHDp / 2f
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
                                .offset {
                                    val p = progress.value
                                    val dxDp = (cos(rad) * radiusDp * p).toFloat()
                                    val dyDp = (sin(rad) * radiusDp * p).toFloat()
                                    IntOffset(
                                        ((centerXDp + dxDp - 30f) * composeDensity).roundToInt(),
                                        ((centerYDp + dyDp - 16f) * composeDensity).roundToInt()
                                    )
                                }
                                .graphicsLayer {
                                    val p = progress.value
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
                            .offset {
                                IntOffset(
                                    ((centerXDp - orbSize / 2f) * composeDensity).roundToInt(),
                                    ((centerYDp - orbSize / 2f) * composeDensity).roundToInt()
                                )
                            }
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
                // #528 / #550 Orb-only fixed-size window (geometry invariant during hover/bubble; zero-recomposition drag)
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
                                        dragState.isDragging = true
                                        val pt = runCatching { java.awt.MouseInfo.getPointerInfo()?.location }.getOrNull()
                                        if (pt != null) {
                                            dragStartPointerX = pt.x
                                            dragStartPointerY = pt.y
                                        }
                                        dragStartOrbX = dragState.x
                                        dragStartOrbY = dragState.y
                                    },
                                    onDragEnd = { finishDragAndSnap() },
                                    onDragCancel = { finishDragAndSnap() },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val pt = runCatching { java.awt.MouseInfo.getPointerInfo()?.location }.getOrNull() ?: return@detectDragGestures
                                        val rawX = dragStartOrbX + (pt.x - dragStartPointerX)
                                        val rawY = dragStartOrbY + (pt.y - dragStartPointerY)
                                        val clamped = clampToAllMonitors(rawX, rawY, orbWinDp, orbWinDp)
                                        dragState.x = clamped.first
                                        dragState.y = clamped.second
                                        // Direct WindowPosition update without mutating Compose state -> zero recomposition while dragging
                                        floatingState.position = DesktopScreenMetrics.toWindowPosition(clamped.first, clamped.second)
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
                                    .background(tokens.error)
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
    }

    // #528 / #550 Independent second Window for Hover Card (6 customizable actions) & Notification Bubble.
    val currentBubble = bubbleEvent
    if (!aiExpanded && !radialOpen && !dragState.isDragging && (currentBubble != null || orbHovered)) {
        val isBubbleMode = currentBubble != null
        val auxWDp = if (isBubbleMode) 280 else 192
        val auxHDp = if (isBubbleMode) maxOf(orbSize + 24, 108) else 68
        val gapDp = 8
        val auxX = (if (isOnRightHalf) orbX - auxWDp - gapDp else orbX + orbWinDp + gapDp)
            .coerceIn(activeMonitor.x, (activeMonitor.x + activeMonitor.width - auxWDp).coerceAtLeast(activeMonitor.x))
        val auxY = (if (isOnBottomHalf) orbY + orbWinDp - auxHDp else orbY)
            .coerceIn(activeMonitor.y, (activeMonitor.y + activeMonitor.height - auxHDp).coerceAtLeast(activeMonitor.y))

        val auxState = rememberWindowState(
            position = DesktopScreenMetrics.toWindowPosition(auxX, auxY),
            size = DpSize(auxWDp.dp, auxHDp.dp)
        )
        LaunchedEffect(auxX, auxY, auxWDp, auxHDp) {
            auxState.position = DesktopScreenMetrics.toWindowPosition(auxX, auxY)
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
            ProvideDesktopTheme(settings) {
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
                                    val totalRisk = (st?.scam ?: 0) + (st?.suspicious ?: 0) + (st?.warning ?: 0)
                                    DesktopStrings.floatingHoverActionName(FloatingHoverAction.TODAY_STATS) to
                                        DesktopStrings.floatingHoverStatsLine(st?.today ?: 0, st?.unread ?: 0, totalRisk)
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
}
