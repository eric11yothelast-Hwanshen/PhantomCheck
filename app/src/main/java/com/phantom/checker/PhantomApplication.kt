package com.phantom.checker

import android.app.Application
import com.phantom.checker.shizuku.ShizukuManager
import com.phantom.checker.util.BackgroundImageHelper

class PhantomApplication : Application() {
    companion object {
        lateinit var appContext: android.content.Context
            private set

        fun str(@androidx.annotation.StringRes resId: Int, vararg args: Any): String =
            if (args.isEmpty()) appContext.getString(resId) else appContext.getString(resId, *args)
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        ShizukuManager.init(this)
        com.phantom.checker.service.BriefingScheduler.rescheduleAll(this)
        com.phantom.checker.service.CalendarInsightScheduler.scheduleOrUpdate(this)
        com.phantom.checker.service.DiaryPolicy.schedule(this)
        com.phantom.checker.service.DiaryPolicy.maybeCatchUp(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        BackgroundImageHelper.trimMemory()
    }
}
