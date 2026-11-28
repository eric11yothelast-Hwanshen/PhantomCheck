package com.phantom.checker.e2e

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import com.phantom.checker.R
import com.phantom.checker.ui.MainActivity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * #585 smoke 1／2（空狀態）／8：啟動、底部導覽切換、全域搜尋開關。
 * 全部使用 App 內建字串（getString）比對，CI 換語系也不會壞。
 */
class AppNavigationSmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        clearDatabase()
        seedBaselinePrefs()
        grantPostNotifications()
        scenario = launchMainActivity()
    }

    @After
    fun tearDown() {
        scenario?.close()
        seedBaselinePrefs()
    }

    @Test
    fun smoke01_appLaunchesAndBottomTabsSwitch() {
        compose.waitForText(str(R.string.messages_s_d0ea34), timeoutMs = 15_000L)

        compose.onNodeWithContentDescription(str(R.string.common_notes)).performClick()
        compose.waitForText(str(R.string.notes_s_7dff4f))

        compose.onNodeWithContentDescription(str(R.string.common_memory)).performClick()
        compose.waitForText(str(R.string.memory_s_c425e2))

        compose.onNodeWithContentDescription(str(R.string.common_insight)).performClick()
        compose.waitForText(str(R.string.common_partner))
        compose.onNodeWithText(str(R.string.common_stats)).assertExists()

        compose.onNodeWithContentDescription(str(R.string.common_link)).performClick()
        compose.waitForText(str(R.string.link_hub_status_title))

        compose.onNodeWithContentDescription(str(R.string.common_assistant)).performClick()
        compose.waitForTextGone(str(R.string.link_hub_status_title))
    }

    @Test
    fun smoke02_messagesEmptyStateIsShownWithInjectedEmptyDatabase() {
        compose.waitForText(str(R.string.messages_s_d0ea34), timeoutMs = 15_000L)
        compose.onNodeWithText(str(R.string.messages_s_d0ea34)).assertExists()
        assertTrue("空資料庫不該有訊息", messageCount() == 0)
    }

    @Test
    fun smoke08_globalSearchOpensAcceptsInputAndCloses() {
        compose.waitForText(str(R.string.messages_s_d0ea34), timeoutMs = 15_000L)

        compose.onNodeWithContentDescription(str(R.string.common_open_global_search)).performClick()
        compose.waitForText(str(R.string.search_s_c3aa57))

        compose.onNode(hasSetTextAction()).performTextInput("phantom")
        compose.onNode(hasSetTextAction()).assertTextContains("phantom")

        compose.onNodeWithContentDescription(str(R.string.common_s_27c39c)).performClick()
        compose.waitForTextGone(str(R.string.search_s_c3aa57))
    }
}
