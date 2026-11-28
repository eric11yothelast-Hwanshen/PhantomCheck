package com.phantom.checker.e2e

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import com.phantom.checker.R
import com.phantom.checker.ui.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * #585 smoke 4（記事 CRUD，含點擊可編輯 #499 不回歸）／5（待辦新增、打勾、刪除）。
 */
class NotesTodoSmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        clearDatabase()
        seedBaselinePrefs()
        grantPostNotifications()
        scenario = launchMainActivity()
        compose.waitForText(str(R.string.messages_s_d0ea34), timeoutMs = 15_000L)
        compose.onNodeWithContentDescription(str(R.string.common_notes)).performClick()
        compose.waitForText(str(R.string.notes_s_7dff4f))
    }

    @After
    fun tearDown() {
        scenario?.close()
        seedBaselinePrefs()
    }

    @Test
    fun smoke04_memoCreateTapToEditUpdateAndDelete() {
        val original = "E2E memo content 585"
        val edited = "E2E memo edited 585"

        compose.waitForText(str(R.string.notes_s_b288a3))
        compose.onNodeWithText(str(R.string.notes_s_7dff4f)).performClick()
        compose.waitForText(str(R.string.common_save))
        compose.onNode(hasSetTextAction()).performTextInput(original)
        compose.onNodeWithText(str(R.string.common_save)).performClick()
        compose.waitForTextGone(str(R.string.common_save))
        compose.waitForText(original)
        assertEquals("新增後資料庫應有 1 則記事", 1, noteCount())

        // #499：點卡片要直接開啟編輯器，且內容已預填（不是空白新增）。
        // Snackbar 會蓋住卡片中央，先等它消失；再收鍵盤避免 IME 擋住點擊。
        compose.waitForTextGone(str(R.string.notes_s_61d3ff), timeoutMs = 10_000L)
        Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithText(original).performScrollTo().performClick()
        compose.waitForText(str(R.string.notes_s_a5ab2c))
        compose.onNode(hasSetTextAction()).assertTextContains(original)

        compose.onNode(hasSetTextAction()).performTextReplacement(edited)
        compose.onNodeWithText(str(R.string.common_save)).performClick()
        compose.waitForTextGone(str(R.string.common_save))
        compose.waitForText(edited)
        compose.waitForTextGone(original)
        compose.waitForTextGone(str(R.string.notes_s_e7e844), timeoutMs = 10_000L)

        compose.onNodeWithText(str(R.string.common_delete)).performClick()
        compose.waitForText(str(R.string.common_confirm_delete))
        compose.onNode(
            hasText(str(R.string.common_confirm_delete)) and
                hasClickAction() and
                hasAnyAncestor(isDialog())
        ).performClick()
        compose.waitUntil(10_000L) { noteCount() == 0 }
        compose.waitForTextGone(edited)
    }

    @Test
    fun smoke05_todoCreateCompleteAndDelete() {
        val title = "E2E todo 585"

        compose.onNodeWithContentDescription(str(R.string.notes_s_75ab7d)).performClick()
        compose.waitForText(str(R.string.common_save))
        compose.onAllNodes(hasSetTextAction())[0].performTextInput(title)
        compose.onNodeWithText(str(R.string.common_save)).performClick()
        compose.waitForTextGone(str(R.string.common_save))
        compose.waitForText(title)
        assertTrue("新增後資料庫應有 1 筆待辦", todoCount() == 1)
        compose.waitForTextGone(str(R.string.notes_s_3398e6), timeoutMs = 10_000L)

        compose.onNode(isToggleable()).performClick()
        compose.waitUntil(10_000L) { todoDone(title) }

        compose.onNodeWithContentDescription(str(R.string.chat_s_ae9e5f)).performClick()
        compose.waitForText(str(R.string.common_confirm_delete))
        compose.onNode(
            hasText(str(R.string.common_confirm_delete)) and
                hasClickAction() and
                hasAnyAncestor(isDialog())
        ).performClick()
        compose.waitUntil(10_000L) { todoCount() == 0 }
    }
}
