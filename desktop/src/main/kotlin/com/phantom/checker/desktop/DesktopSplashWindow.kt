package com.phantom.checker.desktop

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JWindow
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * #530 桌面端無邊框置中啟動載入畫面（純 JDK AWT/Swing JWindow，零額外依賴）：
 * - main() 進入即顯示（--minimized / --tray 靜默啟動時不顯示）
 * - 主視窗首次可見時呼叫 dismiss() 關閉；內建 8 秒保險 Timer 自動關閉
 * - 任何建立失敗（如 headless 環境）皆 runCatching 降級，絕不阻擋啟動
 * - 查證依據：JetBrains/compose-multiplatform#3233 & StackOverflow 78973852
 *   （jpackage 原生啟動器與開發模式對 -splash 支援不一，以獨立 AWT JWindow 為跨模式穩定解）
 */
internal class DesktopSplashWindow private constructor(
    private val window: JWindow,
    private val safetyTimer: Timer
) {
    private val dismissed = AtomicBoolean(false)

    fun dismiss() {
        if (!dismissed.compareAndSet(false, true)) return
        runCatching {
            safetyTimer.stop()
            val closeAction = Runnable {
                runCatching {
                    window.isVisible = false
                    window.dispose()
                }
            }
            if (SwingUtilities.isEventDispatchThread()) {
                closeAction.run()
            } else {
                SwingUtilities.invokeLater(closeAction)
            }
        }.onFailure { err ->
            DesktopDiagnostics.warn("Splash", "Failed disposing splash window: ${err.message}")
        }
    }

    companion object {
        private const val SAFETY_TIMEOUT_MS = 8_000

        fun showIfEligible(startMinimized: Boolean): DesktopSplashWindow? {
            if (startMinimized) return null
            return runCatching {
                val createdRef = java.util.concurrent.atomic.AtomicReference<DesktopSplashWindow?>(null)
                val initTask = Runnable {
                    val jw = JWindow().apply {
                        isAlwaysOnTop = true
                    }
                    val root = JPanel(BorderLayout()).apply {
                        background = Color(0x10, 0x17, 0x26)
                        border = BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(Color(0x6E, 0xA8, 0xFE, 120), 1),
                            BorderFactory.createEmptyBorder(24, 28, 22, 28)
                        )
                        preferredSize = Dimension(360, 196)
                    }

                    val centerBox = JPanel().apply {
                        layout = BoxLayout(this, BoxLayout.Y_AXIS)
                        isOpaque = false
                    }

                    val iconPanel = object : JPanel() {
                        override fun paintComponent(g: Graphics) {
                            super.paintComponent(g)
                            val g2 = g as? Graphics2D ?: return
                            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                            val cx = width / 2
                            val cy = height / 2
                            g2.color = Color(0x1C, 0x27, 0x3E)
                            g2.fillRoundRect(cx - 24, cy - 24, 48, 48, 14, 14)
                            g2.color = Color(0x6E, 0xA8, 0xFE)
                            g2.fillOval(cx - 14, cy - 14, 28, 28)
                            g2.color = Color(0xEB, 0xF1, 0xF8)
                            g2.fillOval(cx - 6, cy - 6, 12, 12)
                        }
                    }.apply {
                        isOpaque = false
                        preferredSize = Dimension(56, 56)
                        maximumSize = Dimension(56, 56)
                        alignmentX = JPanel.CENTER_ALIGNMENT
                    }

                    val titleLabel = JLabel("幻訊 PhantomCheck Desktop").apply {
                        foreground = Color(0xEB, 0xF1, 0xF8)
                        font = Font(Font.SANS_SERIF, Font.BOLD, 16)
                        alignmentX = JLabel.CENTER_ALIGNMENT
                    }

                    val versionLabel = JLabel("v${DesktopSettingsStore.CURRENT_DESKTOP_VERSION}").apply {
                        foreground = Color(0x94, 0xA3, 0xB8)
                        font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
                        alignmentX = JLabel.CENTER_ALIGNMENT
                    }

                    val progressBar = JProgressBar().apply {
                        isIndeterminate = true
                        isBorderPainted = false
                        background = Color(0x1E, 0x29, 0x3B)
                        foreground = Color(0x6E, 0xA8, 0xFE)
                        preferredSize = Dimension(280, 6)
                        maximumSize = Dimension(280, 6)
                        alignmentX = JProgressBar.CENTER_ALIGNMENT
                    }

                    centerBox.add(iconPanel)
                    centerBox.add(Box.createVerticalStrut(10))
                    centerBox.add(titleLabel)
                    centerBox.add(Box.createVerticalStrut(4))
                    centerBox.add(versionLabel)
                    centerBox.add(Box.createVerticalStrut(18))
                    centerBox.add(progressBar)

                    root.add(centerBox, BorderLayout.CENTER)
                    jw.contentPane = root
                    jw.pack()
                    jw.setLocationRelativeTo(null)
                    jw.isVisible = true

                    val timer = Timer(SAFETY_TIMEOUT_MS) {
                        createdRef.get()?.dismiss()
                    }.apply {
                        isRepeats = false
                    }
                    createdRef.set(DesktopSplashWindow(jw, timer))
                    timer.start()
                }

                if (SwingUtilities.isEventDispatchThread()) {
                    initTask.run()
                } else {
                    SwingUtilities.invokeAndWait(initTask)
                }
                createdRef.get()
            }.onFailure { err ->
                DesktopDiagnostics.warn("Splash", "Splash creation failed (graceful fallback): ${err.javaClass.simpleName}: ${err.message}")
            }.getOrNull()
        }
    }
}
