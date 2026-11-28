package com.phantom.checker.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * #585 smoke 12：桌面端 jvmTest。
 * - DesktopUpdater.compareSemVer 行為表（避免更新判斷把舊版當新版）。
 * - DesktopStrings 每個公開字串 key 都必須中英雙語齊全（不得只寫一種語言）。
 */
class DesktopE2eSupportTest {

    @Test
    fun compareSemVerBehaviorTable() {
        assertTrue(
            DesktopUpdater.compareSemVer("0.8.0", "0.9.0") < 0,
            "0.8.0 應小於 0.9.0"
        )
        assertEquals(
            0,
            DesktopUpdater.compareSemVer("0.8.0", "0.8.0"),
            "相同版本應回傳 0"
        )
        assertTrue(
            DesktopUpdater.compareSemVer("1.0.0", "0.9.9") > 0,
            "1.0.0 應大於 0.9.9"
        )
        assertTrue(
            DesktopUpdater.compareSemVer("v0.9", "0.8.9") > 0,
            "v 前綴與缺號段（0.9 -> 0.9.0）需正常比較"
        )
        assertEquals(
            0,
            DesktopUpdater.compareSemVer("v1.0.0", "1.0.0"),
            "v 前綴不影響相等判斷"
        )
    }

    @Test
    fun desktopStringsHaveBothLanguagesForEveryKey() {
        val getters = DesktopStrings::class.java.methods.filter { method ->
            method.parameterCount == 0 &&
                method.returnType == String::class.java &&
                method.name.startsWith("get") &&
                method.name != "getClass"
        }
        assertTrue(
            getters.size >= 50,
            "DesktopStrings 應有足夠的公開字串 key（實際 ${getters.size}）"
        )

        DesktopStrings.syncLanguage("zh-TW")
        val zh = getters.associateWith { it.invoke(DesktopStrings) as String }

        DesktopStrings.syncLanguage("en")
        val en = getters.associateWith { it.invoke(DesktopStrings) as String }

        val missing = getters.filter { getter ->
            zh[getter].isNullOrBlank() || en[getter].isNullOrBlank()
        }
        assertTrue(
            missing.isEmpty(),
            "以下 key 缺少中文或英文：" + missing.joinToString { it.name.removePrefix("get") }
        )

        val bilingualCount = getters.count { getter -> zh[getter] != en[getter] }
        assertTrue(
            bilingualCount >= getters.size / 2,
            "多數字串 key 應有實際雙語差異（$bilingualCount/${getters.size}）"
        )

        DesktopStrings.syncLanguage("en")
        assertEquals("Messages", DesktopStrings.navMessages)
        DesktopStrings.syncLanguage("zh-TW")
        assertNotEquals("Messages", DesktopStrings.navMessages)
    }
}
