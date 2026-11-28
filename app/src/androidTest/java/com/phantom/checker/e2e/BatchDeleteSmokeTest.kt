package com.phantom.checker.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import com.phantom.checker.R
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.ui.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * #585 smoke 9：長按 App 卡片 → 進入多選 → 批次刪除。
 * 只刪被選取 App 的非 scam 訊息，另一個 App 必須原封不動。
 */
class BatchDeleteSmokeTest {

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
                packageName = "com.fake.one",
                appLabel = "FakeOne",
                sender = "One",
                content = "First message from one",
                timestamp = now - 1_000L
            ),
            MessageEntity(
                packageName = "com.fake.one",
                appLabel = "FakeOne",
                sender = "One",
                content = "Second message from one",
                timestamp = now - 2_000L
            ),
            MessageEntity(
                packageName = "com.fake.two",
                appLabel = "FakeTwo",
                sender = "Two",
                content = "Message from two must survive",
                timestamp = now - 3_000L
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
    fun smoke09_longPressAppEntersMultiSelectAndBatchDeletesSelectedApp() {
        compose.waitForText("FakeOne", timeoutMs = 15_000L)
        compose.waitForText("FakeTwo")

        compose.onNodeWithText("FakeOne").performTouchInput { longClick() }
        compose.waitForText(str(R.string.batch_app_multi_select_btn))
        compose.onNodeWithText(str(R.string.batch_app_multi_select_btn)).performClick()

        // 多選列出現：選了 1 個 App，準備刪除。
        compose.waitForText(str(R.string.common_cancel))
        compose.onNodeWithText(str(R.string.common_delete)).performClick()
        compose.waitForText(str(R.string.batch_app_delete_confirm_title))
        compose.onNode(
            hasText(str(R.string.common_delete)) and
                hasClickAction() and
                hasAnyAncestor(isDialog())
        ).performClick()

        compose.waitUntil(10_000L) { messageCount("com.fake.one") == 0 }
        assertEquals(
            "批次刪除不可波及未被選取的 App",
            1,
            messageCount("com.fake.two")
        )
        compose.waitForTextGone("FakeOne")
        compose.onNodeWithText("FakeTwo").assertExists()
    }
}
