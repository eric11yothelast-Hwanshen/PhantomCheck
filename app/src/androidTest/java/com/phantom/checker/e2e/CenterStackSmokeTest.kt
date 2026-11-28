package com.phantom.checker.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ActivityScenario
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.ui.MainActivity
import com.phantom.checker.util.PhantomPrefs
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/**
 * #585 smoke 6：CENTER 堆疊模式。
 * 驗證首卡貼齊堆疊頂端（不是置中），且往上滑可把焦點換到下一張、新焦點卡同樣貼頂。
 * 這是 v3–v6 幾何最容易回退的地方，因此用 boundsInRoot 實測。
 */
class CenterStackSmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        clearDatabase()
        seedBaselinePrefs()
        grantPostNotifications()
        PhantomPrefs.setHomeCardLayoutMode(appContext, PhantomPrefs.HomeCardLayoutMode.CENTER)
        val now = System.currentTimeMillis()
        insertMessages(
            MessageEntity(
                packageName = "com.fake.alpha",
                appLabel = "FakeAlpha",
                sender = "Alpha",
                content = "Alpha newest message",
                timestamp = now - 1_000L
            ),
            MessageEntity(
                packageName = "com.fake.beta",
                appLabel = "FakeBeta",
                sender = "Beta",
                content = "Beta second message",
                timestamp = now - 60_000L
            ),
            MessageEntity(
                packageName = "com.fake.gamma",
                appLabel = "FakeGamma",
                sender = "Gamma",
                content = "Gamma third message",
                timestamp = now - 120_000L
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
    fun smoke06_centerStackFirstCardAnchorsTopAndSwipeMovesFocus() {
        compose.waitForText("FakeAlpha", timeoutMs = 15_000L)
        compose.waitForText("FakeBeta")

        val alphaTop = compose.requireTopOf("FakeAlpha")
        val betaTop = compose.requireTopOf("FakeBeta")
        assertTrue(
            "CENTER 首卡應是堆疊最頂端的一張（FakeAlpha top=$alphaTop 應小於 FakeBeta top=$betaTop）",
            alphaTop < betaTop
        )

        val tolerance = compose.dpToPx(16f)
        val dragPx = compose.dpToPx(60f)
        compose.onRoot().performTouchInput {
            swipe(
                start = Offset(centerX, centerY + dragPx),
                end = Offset(centerX, centerY - dragPx),
                durationMillis = 500L
            )
        }

        // 等 snap 動畫結束：下一張卡被錨定到與原首卡相同的高度。
        compose.waitUntil(8_000L) {
            listOf("FakeBeta", "FakeGamma").any { label ->
                val top = compose.topOf(label)
                top != null && abs(top - alphaTop) <= tolerance
            }
        }

        val nextFocusTop = listOf("FakeBeta", "FakeGamma")
            .mapNotNull { label -> compose.topOf(label)?.let { label to it } }
            .minByOrNull { abs(it.second - alphaTop) }
        assertTrue("滑動後應有一張新焦點卡貼齊堆疊頂端", nextFocusTop != null)
        assertTrue(
            "新焦點卡 ${nextFocusTop!!.first} 的 top=${nextFocusTop.second} 應貼近原首卡 top=$alphaTop",
            abs(nextFocusTop.second - alphaTop) <= tolerance
        )
        val alphaAfter = compose.topOf("FakeAlpha")
        assertTrue(
            "原首卡 FakeAlpha 滑動後不應仍貼在頂端（目前 top=$alphaAfter）",
            alphaAfter == null || abs(alphaAfter - alphaTop) > compose.dpToPx(24f)
        )
    }
}
