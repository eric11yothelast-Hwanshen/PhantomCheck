package com.phantom.checker

import android.app.Application
import com.phantom.checker.shizuku.ShizukuManager

class PhantomApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuManager.init(this)
    }
}
