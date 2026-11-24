package com.phantom.checker.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

fun main() = application {
    val saved = DesktopSettingsStore.state.value
    val initialPos = if (saved.windowX != Int.MIN_VALUE && saved.windowY != Int.MIN_VALUE) {
        WindowPosition(saved.windowX.dp, saved.windowY.dp)
    } else {
        WindowPosition(Alignment.Center)
    }
    val windowState = rememberWindowState(
        position = initialPos,
        size = DpSize(saved.windowWidth.coerceAtLeast(1180).dp, saved.windowHeight.coerceAtLeast(760).dp)
    )

    Window(
        onCloseRequest = {
            saveWindowGeometry(windowState)
            exitApplication()
        },
        state = windowState,
        title = "PhantomCheck Desktop"
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(1180, 760)
        }
        DesktopApp()
    }
}

internal fun saveWindowGeometry(state: androidx.compose.ui.window.WindowState) {
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
