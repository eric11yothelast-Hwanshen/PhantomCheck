package com.phantom.checker.e2e

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.data.NoteEntity
import com.phantom.checker.data.TodoEntity
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.ui.MainActivity
import com.phantom.checker.ui.ThemeMode
import com.phantom.checker.ui.ThemePreferences
import com.phantom.checker.util.PhantomPrefs
import com.phantom.checker.util.TabBarDisplayMode
import kotlinx.coroutines.runBlocking

internal val appContext: Context
    get() = InstrumentationRegistry.getInstrumentation().targetContext

internal fun str(@StringRes id: Int, vararg args: Any): String =
    if (args.isEmpty()) appContext.getString(id) else appContext.getString(id, *args)

/**
 * #585 每條 smoke 測試上線前都把 App 帶回可預期的乾淨狀態：
 * 引導頁已看完、不自動檢查更新、標準分頁列、清單模式、篩選列展開、待辦不收合。
 */
internal fun seedBaselinePrefs(ctx: Context = appContext) {
    PhantomPrefs.setOnboardingCompleted(ctx, true)
    PhantomPrefs.setSettingsTourCompleted(ctx, true)
    PhantomPrefs.setUpdateAutoCheckEnabled(ctx, false)
    PhantomPrefs.setTabBarDisplayMode(ctx, TabBarDisplayMode.STANDARD)
    PhantomPrefs.setHomeCardLayoutMode(ctx, PhantomPrefs.HomeCardLayoutMode.LIST)
    PhantomPrefs.setNoteCardLayoutMode(ctx, PhantomPrefs.NoteCardLayoutMode.LIST)
    PhantomPrefs.setNotesTodoCollapsed(ctx, false)
    PhantomPrefs.setHomeFilterChipsCollapsed(ctx, false)
    PhantomPrefs.setVisibleHomeFilters(ctx, setOf("starred", "filtered"))
    PhantomPrefs.setFocusMuteUntilMs(ctx, 0L)
    PhantomPrefs.setFilterSectionExpanded(ctx, "allowlist", true)
    PhantomPrefs.setFilterSectionExpanded(ctx, "muted", true)
    PhantomPrefs.setFilterSectionExpanded(ctx, "tags", false)
    ThemePreferences.setThemeMode(ctx, ThemeMode.LIGHT)
    ThemePreferences.setHighContrast(ctx, false)
}

internal fun grantPostNotifications() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    runCatching {
        instrumentation.uiAutomation.grantRuntimePermission(
            appContext.packageName,
            Manifest.permission.POST_NOTIFICATIONS
        )
    }
}

internal fun clearDatabase(ctx: Context = appContext) {
    runBlocking { AppDatabaseHolder.db(ctx).clearAllTables() }
}

internal fun insertMessages(vararg messages: MessageEntity) {
    runBlocking {
        val dao = AppDatabaseHolder.db(appContext).messageDao()
        messages.forEach { dao.insert(it) }
    }
}

internal fun messageCount(packageName: String? = null): Int = runBlocking {
    val all = AppDatabaseHolder.db(appContext).messageDao().all()
    if (packageName == null) all.size else all.count { it.packageName == packageName }
}

internal fun noteCount(): Int = runBlocking { AppDatabaseHolder.db(appContext).noteDao().count() }

internal fun todoCount(): Int = runBlocking { AppDatabaseHolder.db(appContext).todoDao().all().size }

internal fun todoDone(title: String): Boolean = runBlocking {
    AppDatabaseHolder.db(appContext).todoDao().all().firstOrNull { it.title == title }?.isDone == true
}

internal fun launchMainActivity(): ActivityScenario<MainActivity> {
    val intent = Intent(appContext, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return ActivityScenario.launch(intent)
}

internal fun ComposeTestRule.waitForText(text: String, timeoutMs: Long = 8_000L) {
    waitUntil(timeoutMs) {
        onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
}

internal fun ComposeTestRule.waitForTextGone(text: String, timeoutMs: Long = 8_000L) {
    waitUntil(timeoutMs) {
        onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
    }
}

internal fun ComposeTestRule.topOf(text: String): Float? =
    onAllNodesWithText(text, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .firstOrNull()
        ?.boundsInRoot
        ?.top

internal fun ComposeTestRule.requireTopOf(text: String): Float =
    topOf(text) ?: error("找不到測試節點：$text（畫面可能尚未載入或文字已變更）")

internal fun ComposeTestRule.dpToPx(value: Float): Float =
    with(InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics) {
        value * density
    }
