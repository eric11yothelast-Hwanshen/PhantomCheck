package com.phantom.checker

import android.app.Application
import com.phantom.checker.shizuku.ShizukuManager
import com.phantom.checker.util.BackgroundImageHelper

class PhantomApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuManager.init(this)
        com.phantom.checker.service.BriefingScheduler.rescheduleAll(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        BackgroundImageHelper.trimMemory()
    }
}
