package com.phantom.checker

import android.app.Application
import com.phantom.checker.shizuku.ShizukuManager
import com.phantom.checker.util.BackgroundImageHelper

class PhantomApplication : Application() {
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
        com.phantom.checker.service.BriefingScheduler.rescheduleAll(this)
        com.phantom.checker.service.CalendarInsightScheduler.scheduleOrUpdate(this)
        com.phantom.checker.service.DiaryPolicy.schedule(this)
        com.phantom.checker.service.DiaryPolicy.maybeCatchUp(this)
        com.phantom.checker.widget.WidgetUpdater.ensurePeriodicBackupScheduled(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        BackgroundImageHelper.trimMemory()
    }
}
