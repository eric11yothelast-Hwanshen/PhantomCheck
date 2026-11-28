package com.phantom.checker.e2e

import android.content.Intent
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import com.phantom.checker.R
import com.phantom.checker.ui.SettingsActivity
import com.phantom.checker.util.PhantomPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * #585 smoke 7：設定頁開關切換後重啟 Activity 仍生效。
 * 使用 Token Saver 開關（QUICK 類別第一張卡，免捲動、無系統權限副作用）。
 * 注意：此頁的 SettingSwitchRow 只有右側 FluidSwitch 可點，列本身不是 clickable。
 */
class SettingsPersistenceSmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<SettingsActivity>
    private var initialTokenSaver = false
    private val tokenSaverTitle by lazy { str(R.string.catalog_quick_token_saver_title) }

    private val tokenSaverSwitch
        get() = hasClickAction() and hasAnySibling(hasText(tokenSaverTitle))

    @Before
    fun setUp() {
        clearDatabase()
        seedBaselinePrefs()
        grantPostNotifications()
        initialTokenSaver = PhantomPrefs.isTokenSaverEnabled(appContext)
        val intent = Intent(appContext, SettingsActivity::class.java)
            .putExtra("highlight_setting_id", "quick_token_saver")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        scenario = ActivityScenario.launch(intent)
    }

    @After
    fun tearDown() {
        scenario.close()
        PhantomPrefs.setTokenSaverEnabled(appContext, initialTokenSaver)
        seedBaselinePrefs()
    }

    @Test
    fun smoke07_settingsSwitchPersistsAcrossActivityRecreate() {
        compose.waitForText(tokenSaverTitle, timeoutMs = 20_000L)
        compose.waitForIdle()

        // 快速設定卡同一個 parent 下有多顆開關，Token Saver 是第一個（由上而下）。
        compose.onAllNodes(tokenSaverSwitch, useUnmergedTree = true)[0].performClick()
        val expected = !initialTokenSaver
        compose.waitUntil(5_000L) { PhantomPrefs.isTokenSaverEnabled(appContext) == expected }
        assertEquals("點擊後偏好應立即翻轉", expected, PhantomPrefs.isTokenSaverEnabled(appContext))
        if (expected) {
            compose.onAllNodes(tokenSaverSwitch, useUnmergedTree = true)[0].assertIsOn()
        } else {
            compose.onAllNodes(tokenSaverSwitch, useUnmergedTree = true)[0].assertIsOff()
        }

        scenario.recreate()
        compose.waitForText(tokenSaverTitle, timeoutMs = 20_000L)
        assertTrue(
            "重啟 Activity 後開關仍應保持 $expected",
            PhantomPrefs.isTokenSaverEnabled(appContext) == expected
        )
        if (expected) {
            compose.onAllNodes(tokenSaverSwitch, useUnmergedTree = true)[0].assertIsOn()
        } else {
            compose.onAllNodes(tokenSaverSwitch, useUnmergedTree = true)[0].assertIsOff()
        }
    }
}
