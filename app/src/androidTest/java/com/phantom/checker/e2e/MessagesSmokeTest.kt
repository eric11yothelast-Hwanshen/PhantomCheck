package com.phantom.checker.e2e

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import com.phantom.checker.R
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.ui.MainActivity
import com.phantom.checker.util.PhantomPrefs
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * #585 smoke 2（fake 資料清單）／3（篩選列展開收合＋篩選切換）。
 * 資料直接注入 Room（等同「已收到通知」的結果），不啟用通知監聽權限。
 */
class MessagesSmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        clearDatabase()
        seedBaselinePrefs()
        grantPostNotifications()
        val now = System.currentTimeMillis()
        insertMessages(
            MessageEntity(
                packageName = "com.fake.chat",
                appLabel = "FakeChat",
                sender = "Alice",
                content = "Hello from fake data",
                timestamp = now - 60_000L
            ),
            MessageEntity(
                packageName = "com.fake.scam",
                appLabel = "FakeScam",
                sender = "Prize Bot",
                content = "You won a prize",
                timestamp = now - 120_000L,
                riskLabel = "scam",
                riskScore = 95
            )
        )
        scenario = launchMainActivity()
    }

    @After
    fun tearDown() {
        scenario?.close()
        seedBaselinePrefs()
    }

    @Test
    fun smoke02_messageListShowsInjectedFakeData() {
        compose.waitForText("FakeChat", timeoutMs = 15_000L)
        compose.onNodeWithText("FakeChat").assertExists()
        compose.onNodeWithText("Hello from fake data").assertExists()
        compose.onNodeWithContentDescription("FakeChat").assertExists()
    }

    @Test
    fun smoke03_filterBarExpandsCollapsesAndSwitchesFilters() {
        compose.waitForText("FakeChat", timeoutMs = 15_000L)

        // 篩選切換：High Risk 只留 scam 訊息。
        compose.onNodeWithText(str(R.string.common_high_risk)).performClick()
        compose.waitForText("You won a prize")
        compose.onNodeWithText(str(R.string.messages_s_f0a3ca, 1)).assertExists()
        compose.onNodeWithText(str(R.string.common_all)).performClick()
        compose.waitForText("FakeChat")

        // 篩選列收合：More Filters 面板內的開關即時生效並寫入偏好。
        // 水平 chip 列在手機寬度下會把「More」推到畫面外，先左滑讓它曝光。
        compose.onNodeWithText(str(R.string.common_high_risk)).performTouchInput { swipeLeft() }
        compose.waitForText(str(R.string.common_more))
        compose.onNodeWithText(str(R.string.common_more)).performClick()
        compose.waitForText(str(R.string.messages_s_4f346f))
        compose.onNode(isToggleable()).assertIsOff().performClick().assertIsOn()
        Espresso.pressBack()
        assertTrue(
            "收合篩選列後偏好應為 true",
            PhantomPrefs.isHomeFilterChipsCollapsed(appContext)
        )

        // 重開面板確認狀態仍保留（同一 Activity 內的偏好記憶）。
        compose.onNodeWithText(str(R.string.common_more)).performClick()
        compose.waitForText(str(R.string.messages_s_4f346f))
        compose.onNode(isToggleable()).assertIsOn()
        Espresso.pressBack()
    }
}
