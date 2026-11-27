package com.phantom.checker.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.awt.SystemTray
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * #469 桌面系統匣常駐入口（Compose Multiplatform 官方 androidx.compose.ui.window.Tray API）：
 * - 預設關閉視窗縮至系統匣背景運行（WSS 連線、訊息鏡射、指數退避重連不中斷）
 * - 支援 --minimized / --tray 啟動參數（供開機自動啟動直接縮至系統匣、不搶焦點）
 * - SystemTray 不支援環境誠實降級為關閉即結束
 */
fun main(args: Array<String>) {
    // #495 Single-instance mutex before Compose/server startup to prevent duplicate processes & port drift
    val singleInstance = DesktopSingleInstance.acquireOrNotifyExisting(args) ?: return
    val startMinimized = args.any { it.equals("--minimized", ignoreCase = true) || it.equals("--tray", ignoreCase = true) }
    // #530 Instant borderless splash on normal launch (never shown on --minimized / --tray)
    val splash = DesktopSplashWindow.showIfEligible(startMinimized = startMinimized)

    application {
        val traySupported = remember { runCatching { SystemTray.isSupported() }.getOrDefault(false) }

        val settings by DesktopSettingsStore.state.collectAsState()
        val server = remember { DesktopLinkServer() }
        val serverState by server.state.collectAsState()
        val trayState = rememberTrayState()
        val trayIcon = remember { PhantomTrayIconPainter }
        val appScope = rememberCoroutineScope()
        val exiting = remember { AtomicBoolean(false) }

        var isWindowVisible by remember { mutableStateOf(!(startMinimized && traySupported)) }
        var focusRequestTick by remember { mutableStateOf(0) }
        var requestedTabKey by remember { mutableStateOf<String?>(null) }
        var triggerUpdateCheckTick by remember { mutableStateOf(0) }
        var unsupportedNoticeShown by remember { mutableStateOf(false) }
        val pausedUntilMs by DesktopNotificationBus.pausedUntilMs.collectAsState()
        val isNotifPaused = pausedUntilMs > System.currentTimeMillis()

        val initialPos = remember {
            if (settings.windowX != Int.MIN_VALUE && settings.windowY != Int.MIN_VALUE) {
                WindowPosition(settings.windowX.dp, settings.windowY.dp)
            } else {
                WindowPosition(Alignment.Center)
            }
        }
        val workArea = remember { primaryWorkAreaBounds() }
        val initW = settings.windowWidth.coerceAtLeast(880).coerceAtMost((workArea.width * 0.92).toInt().coerceAtLeast(880))
        val initH = settings.windowHeight.coerceAtLeast(560).coerceAtMost((workArea.height * 0.92).toInt().coerceAtLeast(560))
        val windowState = rememberWindowState(
            position = initialPos,
            size = DpSize(initW.dp, initH.dp)
        )

        fun showAndFocusMainWindow(tabKey: String? = null) {
            val apply = {
                if (tabKey != null) requestedTabKey = tabKey
                isWindowVisible = true
                focusRequestTick += 1
            }
            if (SwingUtilities.isEventDispatchThread()) {
                apply()
            } else {
                SwingUtilities.invokeLater(apply)
            }
        }

        fun performFullExit() {
            if (!exiting.compareAndSet(false, true)) return
            val runExit = {
                splash?.dismiss()
                saveWindowGeometry(windowState)
                server.stop()
                singleInstance.release()
                DesktopDiagnostics.info("App", "Exiting application cleanly")
                exitApplication()
            }
            if (SwingUtilities.isEventDispatchThread()) {
                runExit()
            } else {
                SwingUtilities.invokeLater(runExit)
            }
        }

        // 生命週期拉升至 application 層，隱藏視窗至系統匣（含 --minimized）時 WSS 伺服器與 mDNS 廣播持續常駐
        LaunchedEffect(Unit) {
            DesktopDiagnostics.startPerfSamplerIfEnabled()
            singleInstance.setCallbacks(
                onFocus = { showAndFocusMainWindow() },
                onGracefulExit = { performFullExit() }
            )
            DesktopDiagnostics.info("App", "Started (traySupported=$traySupported, startMinimized=$startMinimized, autoConnect=${settings.autoConnectOnStartup})")
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching { DesktopSettingsStore.verifyAndRepairAutoStartOnLaunch() }
            }
            if (settings.autoConnectOnStartup) {
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
            }
            if (!traySupported && settings.closeBehavior == CloseBehavior.MINIMIZE_TO_TRAY && !unsupportedNoticeShown) {
                unsupportedNoticeShown = true
                DesktopDiagnostics.warn("Tray", DesktopStrings.trayUnsupportedNotice)
            }
            // #497 D: Single left-click on tray icon directly opens main window (in addition to double-click onAction)
            if (traySupported) {
                kotlinx.coroutines.delay(400L)
                runCatching {
                    val icons = SystemTray.getSystemTray().trayIcons
                    icons.lastOrNull()?.addMouseListener(object : java.awt.event.MouseAdapter() {
                        override fun mouseClicked(e: java.awt.event.MouseEvent) {
                            if (e.button == java.awt.event.MouseEvent.BUTTON1 && e.clickCount == 1) {
                                showAndFocusMainWindow()
                            }
                        }
                    })
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                server.stop()
                singleInstance.release()
            }
        }

        val phoneStatus by DesktopDeviceStatusHolder.status.collectAsState()
        val batterySuffix = remember(phoneStatus, serverState.connState) {
            val st = phoneStatus
            if (serverState.connState == LinkConnState.CONNECTED && st != null && st.batteryLevel >= 0) {
                " · 📱 ${st.batteryLevel}%${if (st.isCharging) "⚡" else ""}"
            } else ""
        }

        val trayTooltip = remember(settings.language, serverState.connState, serverState.port, serverState.connectedPhones.size, serverState.lastError, batterySuffix) {
            when (serverState.connState) {
                LinkConnState.ERROR -> "${DesktopStrings.appTitle} — ${DesktopStrings.statusServerError}: ${serverState.lastError.orEmpty()}"
                LinkConnState.CONNECTED -> "${DesktopStrings.appTitle} — ${DesktopStrings.statusConnected}${serverState.pairedDeviceName}$batterySuffix"
                LinkConnState.WAITING_FOR_PHONE -> "${DesktopStrings.appTitle} — ${DesktopStrings.serverListeningOn(serverState.port, 0)}"
                LinkConnState.STOPPED -> "${DesktopStrings.appTitle} — ${DesktopStrings.statusServerStopped}"
            }
        }

        val isServerRunning = serverState.connState == LinkConnState.CONNECTED || serverState.connState == LinkConnState.WAITING_FOR_PHONE

        if (traySupported) {
            Tray(
                state = trayState,
                icon = trayIcon,
                tooltip = trayTooltip,
                onAction = { showAndFocusMainWindow() },
                menu = {
                    // 1. Live Connection Status (+ #517 battery suffix)
                    Item(
                        text = DesktopStrings.trayConnStatus(serverState.connectedPhones.size, serverState.port, isServerRunning) + batterySuffix,
                        onClick = { showAndFocusMainWindow("HOME") }
                    )
                    // 2. Open PhantomCheck
                    Item(
                        text = DesktopStrings.trayOpen,
                        onClick = { showAndFocusMainWindow() }
                    )
                    Separator()
                    // 3. Toggle DND (#513 one-click quickDndActive synced with main window)
                    Item(
                        text = DesktopStrings.trayToggleDnd(settings.quickDndActive),
                        onClick = {
                            DesktopSettingsStore.update { it.copy(quickDndActive = !it.quickDndActive) }
                        }
                    )
                    // 4. Pause Notifications 1 hr / Resume
                    Item(
                        text = if (isNotifPaused) DesktopStrings.trayResumeNotif else DesktopStrings.trayPauseNotif1h,
                        onClick = {
                            if (isNotifPaused) DesktopNotificationBus.resumeNotifications()
                            else DesktopNotificationBus.pauseForOneHour()
                        }
                    )
                    // 5. #520 Floating Orb Toggle
                    Item(
                        text = DesktopStrings.trayFloatingOrb(settings.floatingAssistantEnabled),
                        onClick = {
                            DesktopSettingsStore.update { it.copy(floatingAssistantEnabled = !it.floatingAssistantEnabled) }
                        }
                    )
                    Separator()
                    // 6. #520 Quick Go Submenu
                    Menu(DesktopStrings.trayQuickGo) {
                        Item(DesktopStrings.tabHome, onClick = { showAndFocusMainWindow("HOME") })
                        Item(DesktopStrings.navMessages, onClick = { showAndFocusMainWindow("MESSAGES") })
                        Item(DesktopStrings.tabReports, onClick = { showAndFocusMainWindow("REPORTS") })
                        Item(DesktopStrings.navTodos, onClick = { showAndFocusMainWindow("TODOS") })
                        Item(DesktopStrings.navChat, onClick = { showAndFocusMainWindow("CHAT") })
                        Item(DesktopStrings.tabTools, onClick = { showAndFocusMainWindow("TOOLS") })
                        Item(DesktopStrings.navSettings, onClick = { showAndFocusMainWindow("SETTINGS") })
                    }
                    // 7. Start / Stop WSS Server
                    Item(
                        text = if (isServerRunning) DesktopStrings.trayStopServer else DesktopStrings.trayStartServer,
                        onClick = {
                            appScope.launch(Dispatchers.IO) {
                                if (isServerRunning) server.stop() else server.start()
                            }
                        }
                    )
                    // 8. Check for Updates
                    Item(
                        text = DesktopStrings.trayCheckUpdate,
                        onClick = {
                            showAndFocusMainWindow("SETTINGS")
                            triggerUpdateCheckTick += 1
                        }
                    )
                    // 9. Open Settings
                    Item(
                        text = DesktopStrings.trayOpenSettings,
                        onClick = { showAndFocusMainWindow("SETTINGS") }
                    )
                    Separator()
                    // 10. Exit
                    Item(
                        text = DesktopStrings.trayExit,
                        onClick = { performFullExit() }
                    )
                }
            )
        }

        // #497 B: Bottom-right always-on-top toast overlay window (application scope)
        DesktopToastOverlayWindow(
            settings = settings,
            onOpenConversation = { ev ->
                DesktopNotificationBus.requestNavigateTo(ev)
                showAndFocusMainWindow("MESSAGES")
            }
        )

        // #497 C: Floating assistant orb + mini AI chat + adaptive bubble (application scope)
        DesktopFloatingAssistantWindow(
            settings = settings,
            server = server,
            onOpenMainWindow = { showAndFocusMainWindow() },
            onOpenConversation = { ev ->
                DesktopNotificationBus.requestNavigateTo(ev)
                showAndFocusMainWindow("MESSAGES")
            },
            onOpenTab = { tabKey -> showAndFocusMainWindow(tabKey) }
        )

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
                window.minimumSize = Dimension(880, 560)
            }
            LaunchedEffect(isWindowVisible, focusRequestTick) {
                if (isWindowVisible) {
                    splash?.dismiss()
                    runCatching {
                        windowState.isMinimized = false
                        window.toFront()
                        window.requestFocus()
                    }
                }
            }
            ProvideDesktopTheme(settings) {
                DesktopApp(
                    server = server,
                    manageLifecycle = false,
                    isWindowVisible = isWindowVisible,
                    requestedTabKey = requestedTabKey,
                    onRequestedTabConsumed = { requestedTabKey = null },
                    triggerUpdateCheckTick = triggerUpdateCheckTick,
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
            windowWidth = size.width.value.toInt().coerceAtLeast(1024),
            windowHeight = size.height.value.toInt().coerceAtLeast(680)
        )
    }
}
