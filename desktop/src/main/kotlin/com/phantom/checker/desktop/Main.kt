package com.phantom.checker.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import java.awt.SystemTray

/**
 * #469 桌面系統匣常駐入口（Compose Multiplatform 官方 androidx.compose.ui.window.Tray API）：
 * - 預設關閉視窗縮至系統匣背景運行（WSS 連線、訊息鏡射、指數退避重連不中斷）
 * - 支援 --minimized / --tray 啟動參數（供開機自動啟動直接縮至系統匣、不搶焦點）
 * - SystemTray 不支援環境誠實降級為關閉即結束
 */
fun main(args: Array<String>) = application {
    val startMinimized = args.any { it.equals("--minimized", ignoreCase = true) || it.equals("--tray", ignoreCase = true) }
    val traySupported = remember { runCatching { SystemTray.isSupported() }.getOrDefault(false) }

    val settings by DesktopSettingsStore.state.collectAsState()
    val server = remember { DesktopLinkServer() }
    val serverState by server.state.collectAsState()
    val trayState = rememberTrayState()
    val trayIcon = remember { PhantomTrayIconPainter }

    var isWindowVisible by remember { mutableStateOf(!(startMinimized && traySupported)) }
    var unsupportedNoticeShown by remember { mutableStateOf(false) }

    val initialPos = remember {
        if (settings.windowX != Int.MIN_VALUE && settings.windowY != Int.MIN_VALUE) {
            WindowPosition(settings.windowX.dp, settings.windowY.dp)
        } else {
            WindowPosition(Alignment.Center)
        }
    }
    val windowState = rememberWindowState(
        position = initialPos,
        size = DpSize(settings.windowWidth.coerceAtLeast(1024).dp, settings.windowHeight.coerceAtLeast(680).dp)
    )

    // 生命週期拉升至 application 層，隱藏視窗至系統匣（含 --minimized）時 WSS 伺服器與 mDNS 廣播持續常駐
    LaunchedEffect(Unit) {
        DesktopDiagnostics.info("App", "Started (traySupported=$traySupported, startMinimized=$startMinimized)")
        server.start(
            onErrorNotify = { errMsg ->
                if (traySupported) {
                    runCatching {
                        trayState.sendNotification(
                            Notification(
                                title = DesktopStrings.statusServerError,
                                message = errMsg,
                                type = Notification.Type.Error
                            )
                        )
                    }
                }
            }
        )
        if (!traySupported && settings.closeBehavior == CloseBehavior.MINIMIZE_TO_TRAY && !unsupportedNoticeShown) {
            unsupportedNoticeShown = true
            DesktopDiagnostics.warn("Tray", DesktopStrings.trayUnsupportedNotice)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            server.stop()
        }
    }

    fun performFullExit() {
        saveWindowGeometry(windowState)
        server.stop()
        DesktopDiagnostics.info("App", "Exiting application cleanly")
        exitApplication()
    }

    val trayTooltip = remember(serverState.connState, serverState.port, serverState.connectedPhones.size, serverState.lastError) {
        when (serverState.connState) {
            LinkConnState.ERROR -> "${DesktopStrings.appTitle} — ${DesktopStrings.statusServerError}: ${serverState.lastError.orEmpty()}"
            LinkConnState.CONNECTED -> "${DesktopStrings.appTitle} — ${DesktopStrings.statusConnected}${serverState.pairedDeviceName}"
            LinkConnState.WAITING_FOR_PHONE -> "${DesktopStrings.appTitle} — ${DesktopStrings.serverListeningOn(serverState.port, 0)}"
            LinkConnState.STOPPED -> "${DesktopStrings.appTitle} — ${DesktopStrings.statusServerStopped}"
        }
    }

    if (traySupported) {
        Tray(
            state = trayState,
            icon = trayIcon,
            tooltip = trayTooltip,
            onAction = { isWindowVisible = true },
            menu = {
                Item(
                    text = DesktopStrings.trayOpen,
                    onClick = { isWindowVisible = true }
                )
                Separator()
                Item(
                    text = DesktopStrings.trayExit,
                    onClick = { performFullExit() }
                )
            }
        )
    }

    Window(
        visible = isWindowVisible,
        onCloseRequest = {
            saveWindowGeometry(windowState)
            if (traySupported && settings.closeBehavior == CloseBehavior.MINIMIZE_TO_TRAY) {
                isWindowVisible = false
                DesktopDiagnostics.info("Tray", "Window minimized to system tray")
            } else {
                performFullExit()
            }
        },
        state = windowState,
        title = DesktopStrings.appTitle,
        icon = trayIcon
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(1024, 680)
        }
        LaunchedEffect(isWindowVisible) {
            if (isWindowVisible) {
                runCatching {
                    window.toFront()
                    window.requestFocus()
                }
            }
        }
        DesktopApp(
            server = server,
            manageLifecycle = false,
            onMirrorNotification = { title, body ->
                if (traySupported) {
                    runCatching {
                        trayState.sendNotification(
                            Notification(
                                title = title.ifBlank { DesktopStrings.appTitle },
                                message = body,
                                type = Notification.Type.Info
                            )
                        )
                    }
                }
            }
        )
    }
}

private object PhantomTrayIconPainter : Painter() {
    override val intrinsicSize: Size = Size(32f, 32f)
    override fun DrawScope.onDraw() {
        drawRoundRect(
            color = Color(0xFF101726),
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(size.width * 0.24f, size.height * 0.24f)
        )
        drawCircle(
            color = Color(0xFF6EA8FE),
            radius = size.minDimension * 0.30f,
            center = Offset(size.width / 2f, size.height / 2f)
        )
        drawCircle(
            color = Color(0xFFEBF1F8),
            radius = size.minDimension * 0.12f,
            center = Offset(size.width / 2f, size.height / 2f)
        )
    }
}

internal fun saveWindowGeometry(state: WindowState) {
    val pos = state.position
    val size = state.size
    DesktopSettingsStore.update { cur ->
        cur.copy(
            windowX = if (pos is WindowPosition.Absolute) pos.x.value.toInt() else cur.windowX,
            windowY = if (pos is WindowPosition.Absolute) pos.y.value.toInt() else cur.windowY,
            windowWidth = size.width.value.toInt().coerceAtLeast(1180),
            windowHeight = size.height.value.toInt().coerceAtLeast(760)
        )
    }
}
