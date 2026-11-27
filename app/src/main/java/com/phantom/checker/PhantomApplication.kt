package com.phantom.checker

import android.app.Application
import com.phantom.checker.shizuku.ShizukuManager
import com.phantom.checker.util.BackgroundImageHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PhantomApplication : Application() {
    private val appInitScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        lateinit var appContext: android.content.Context
            private set

        fun str(@androidx.annotation.StringRes resId: Int, vararg args: Any): String {
            val localized = com.phantom.checker.util.AppLocaleHelper.localizedContext(appContext)
            return if (args.isEmpty()) localized.getString(resId) else localized.getString(resId, *args)
        }
    }

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(com.phantom.checker.util.AppLocaleHelper.wrapContext(base))
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        com.phantom.checker.util.AppLocaleHelper.getEffectiveMode(this)
        ShizukuManager.init(this)
        val ctx = applicationContext
        appInitScope.launch {
            try {
                runCatching { com.phantom.checker.service.BriefingScheduler.rescheduleAll(ctx) }
                runCatching { com.phantom.checker.service.CalendarInsightScheduler.scheduleOrUpdate(ctx) }
                runCatching { com.phantom.checker.service.DiaryPolicy.schedule(ctx) }
                runCatching { com.phantom.checker.service.DiaryPolicy.maybeCatchUp(ctx) }
                runCatching { com.phantom.checker.widget.WidgetUpdater.ensurePeriodicBackupScheduled(ctx) }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        BackgroundImageHelper.trimMemory()
        invokeAppIconHelperTrimMemory()
    }

    /**
     * 對接 R9 (#548) 將於 AppIconHelper 補齊的 trimMemory()：
     * 因 R4 與 R9 平行開發且 R4 範圍不得改動 AppIconHelper.kt，以反射安全呼叫（支援 object 或頂層函式）。
     */
    private fun invokeAppIconHelperTrimMemory() {
        runCatching {
            val helperCls = Class.forName("com.phantom.checker.ui.AppIconHelper")
            val instance = helperCls.getField("INSTANCE").get(null)
            helperCls.getMethod("trimMemory").invoke(instance)
            return
        }
        runCatching {
            val ktCls = Class.forName("com.phantom.checker.ui.AppIconHelperKt")
            ktCls.getMethod("trimMemory").invoke(null)
        }
    }
}
