package com.phantom.checker.e2e

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import com.phantom.checker.R
import com.phantom.checker.ui.WhitelistManagementActivity
import com.phantom.checker.util.PhantomPrefs
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * #585 smoke 10：「過濾與專注」三區（白名單／靜音＋專注／標籤）展開收合，
 * 收起狀態要寫入偏好，重啟 Activity 後仍記住。
 */
class FiltersFocusSmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<WhitelistManagementActivity>

    @Before
    fun setUp() {
        clearDatabase()
        seedBaselinePrefs()
        grantPostNotifications()
        val intent = Intent(appContext, WhitelistManagementActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        scenario = ActivityScenario.launch(intent)
    }

    @After
    fun tearDown() {
        scenario.close()
        seedBaselinePrefs()
    }

    @Test
    fun smoke10_filtersFocusSectionsCollapseExpandAndRememberPreference() {
        compose.waitForText(str(R.string.catalog_whitelist_management_title), timeoutMs = 15_000L)
        compose.waitForText(str(R.string.filters_sec_allowlist))
        compose.waitForText(str(R.string.filters_sec_muted_apps))
        compose.waitForText(str(R.string.filters_sec_tags))

        // 預設：白名單展開、標籤收合。
        compose.onNodeWithText(str(R.string.whitelist_s_1e4f1a)).assertExists()
        compose.onNodeWithText(str(R.string.filters_tag_suggest_title)).assertDoesNotExist()

        // 展開標籤。
        compose.onNodeWithText(str(R.string.filters_sec_tags)).performScrollTo().performClick()
        compose.waitForText(str(R.string.filters_tag_suggest_title))
        assertTrue(
            "展開標籤後偏好應為 true",
            PhantomPrefs.isFilterSectionExpanded(appContext, "tags", false)
        )

        // 收合白名單。
        compose.onNodeWithText(str(R.string.filters_sec_allowlist)).performScrollTo().performClick()
        compose.waitForTextGone(str(R.string.whitelist_s_1e4f1a))
        assertFalse(
            "收合白名單後偏好應為 false",
            PhantomPrefs.isFilterSectionExpanded(appContext, "allowlist", true)
        )

        // 重啟 Activity：兩區狀態都要沿用偏好。
        scenario.recreate()
        compose.waitForText(str(R.string.filters_sec_allowlist), timeoutMs = 15_000L)
        assertTrue(
            "重啟後標籤仍應展開",
            PhantomPrefs.isFilterSectionExpanded(appContext, "tags", false)
        )
        assertFalse(
            "重啟後白名單仍應收合",
            PhantomPrefs.isFilterSectionExpanded(appContext, "allowlist", true)
        )
        compose.onNodeWithText(str(R.string.filters_tag_suggest_title)).assertExists()
        compose.onNodeWithText(str(R.string.whitelist_s_1e4f1a)).assertDoesNotExist()
    }
}
