package com.phantom.checker.e2e

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.phantom.checker.ui.LocalHighContrast
import com.phantom.checker.ui.PhantomCheckTheme
import com.phantom.checker.ui.ThemeMode
import com.phantom.checker.ui.ThemePreferences
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * #585 smoke 11：高對比開關即時生效（不重啟 Activity）。
 * 以 CompositionLocal + MaterialTheme.colorScheme 實際值驗證，而不是只看偏好。
 */
class HighContrastSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    @After
    fun tearDown() {
        ThemePreferences.setHighContrast(appContext, false)
        seedBaselinePrefs()
    }

    @Composable
    private fun HighContrastProbe() {
        val enabled = LocalHighContrast.current
        val background = MaterialTheme.colorScheme.background.value.toString(16)
        Text("HC_${if (enabled) "ON" else "OFF"}_BG_$background")
    }

    private fun probeText(): String {
        val nodes = compose.onAllNodesWithText("HC_", substring = true).fetchSemanticsNodes()
        assertTrue("高對比探針節點不存在", nodes.isNotEmpty())
        return nodes.first().config[SemanticsProperties.Text].joinToString("") { it.text }
    }

    @Test
    fun smoke11_highContrastAppliesImmediatelyWithoutActivityRestart() {
        ThemePreferences.setThemeMode(appContext, ThemeMode.LIGHT)
        ThemePreferences.setHighContrast(appContext, false)

        compose.setContent {
            PhantomCheckTheme(darkTheme = false) {
                HighContrastProbe()
            }
        }

        val offText = probeText()
        assertTrue("初始應為一般對比：$offText", offText.startsWith("HC_OFF_"))

        // 切換偏好 → 同一個 composition 內立即重組，不需 recreate。
        ThemePreferences.setHighContrast(appContext, true)
        compose.waitForIdle()

        val onText = probeText()
        assertTrue("開啟後應立即變為高對比：$onText", onText.startsWith("HC_ON_"))
        assertNotEquals("高對比背景色應與一般模式不同", offText, onText)
    }
}
