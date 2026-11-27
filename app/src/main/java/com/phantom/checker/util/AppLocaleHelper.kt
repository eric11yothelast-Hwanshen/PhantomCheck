package com.phantom.checker.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * #438 App 內語言切換共用 Helper（單一來源，供所有 Activity、Application 與 AI 層共用）：
 * - 三態：跟隨系統（system，預設）／繁體中文（zh-TW）／English（en）。
 * - API 33+（Android 13+）：透過 LocaleManager.setApplicationLocales 與系統 per-app 語言設定雙向同步。
 * - API 26–32（Android 8–12）：透過 attachBaseContext + createConfigurationContext 套用 override，
 *   切換時以 decorView.post { recreate() } 平滑重建，避免黑屏或卡死。
 * - 切回「跟隨系統」時清空 override（API 33+ 設為 LocaleList.getEmptyLocaleList()），
 *   與系統 zh-TW 下 v4.2.0 行為逐 byte 一致。
 */
object AppLocaleHelper {

    fun getEffectiveMode(ctx: Context): String {
        val appCtx = ctx.applicationContext ?: ctx
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val lm = runCatching { appCtx.getSystemService(LocaleManager::class.java) }.getOrNull()
            val appLocales = runCatching { lm?.applicationLocales }.getOrNull()
            if (appLocales != null) {
                val sysMode = if (appLocales.isEmpty) {
                    PhantomPrefs.APP_LOCALE_SYSTEM
                } else {
                    val tag = appLocales.get(0)?.toLanguageTag()?.lowercase(Locale.ROOT).orEmpty()
                    if (tag.startsWith("zh")) PhantomPrefs.APP_LOCALE_ZH_TW else PhantomPrefs.APP_LOCALE_EN
                }
                if (PhantomPrefs.getAppLanguageMode(appCtx) != sysMode) {
                    PhantomPrefs.setAppLanguageMode(appCtx, sysMode, notify = false)
                }
                return sysMode
            }
        }
        return PhantomPrefs.getAppLanguageMode(appCtx)
    }

    fun resolveSystemLocale(): Locale {
        return try {
            val sysLocales = Resources.getSystem().configuration.locales
            if (!sysLocales.isEmpty) sysLocales.get(0) else Locale.getDefault()
        } catch (_: Exception) {
            Locale.getDefault()
        }
    }

    /** 解析目前 App 生效的 Locale（App 設定優先於系統語系；跟隨系統時回傳系統語系）。 */
    fun resolveEffectiveLocale(ctx: Context? = null): Locale {
        if (ctx == null) return Locale.getDefault()
        val appCtx = ctx.applicationContext ?: ctx
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val lm = runCatching { appCtx.getSystemService(LocaleManager::class.java) }.getOrNull()
            val appLocales = runCatching { lm?.applicationLocales }.getOrNull()
            if (appLocales != null && !appLocales.isEmpty) {
                appLocales.get(0)?.let { return it }
            }
        }
        return when (PhantomPrefs.getAppLanguageMode(appCtx)) {
            PhantomPrefs.APP_LOCALE_ZH_TW -> Locale.forLanguageTag("zh-TW")
            PhantomPrefs.APP_LOCALE_EN -> Locale.ENGLISH
            else -> resolveSystemLocale()
        }
    }

    /** 供所有 Activity.attachBaseContext 與 Application.attachBaseContext 統一呼叫。 */
    fun wrapContext(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 由系統 LocaleManager 原生管理 per-app locale，不重複覆寫
            return base
        }
        val mode = runCatching { PhantomPrefs.getAppLanguageMode(base) }
            .getOrDefault(PhantomPrefs.APP_LOCALE_SYSTEM)
        if (mode == PhantomPrefs.APP_LOCALE_SYSTEM) {
            return base
        }
        val targetLocale = when (mode) {
            PhantomPrefs.APP_LOCALE_ZH_TW -> Locale.forLanguageTag("zh-TW")
            PhantomPrefs.APP_LOCALE_EN -> Locale.ENGLISH
            else -> return base
        }
        Locale.setDefault(targetLocale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(targetLocale))
        return base.createConfigurationContext(config)
    }

    private val localizedCacheLock = Any()
    @Volatile
    private var cachedLocaleAppCtx: Context? = null
    @Volatile
    private var cachedLocaleMode: String? = null
    @Volatile
    private var cachedLocalizedCtx: Context? = null

    /** 供 PhantomApplication.str()、通知與 Widget/RemoteViews 取得套用 App 語系後的 Context。 */
    fun localizedContext(ctx: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ctx
        }
        val appCtx = ctx.applicationContext ?: ctx
        val mode = runCatching { PhantomPrefs.getAppLanguageMode(appCtx) }
            .getOrDefault(PhantomPrefs.APP_LOCALE_SYSTEM)
        if (mode == PhantomPrefs.APP_LOCALE_SYSTEM) {
            return ctx
        }
        if (cachedLocaleAppCtx === appCtx && cachedLocaleMode == mode) {
            cachedLocalizedCtx?.let { return it }
        }
        return synchronized(localizedCacheLock) {
            if (cachedLocaleAppCtx === appCtx && cachedLocaleMode == mode) {
                cachedLocalizedCtx?.let { return@synchronized it }
            }
            val created = wrapContext(appCtx)
            cachedLocaleAppCtx = appCtx
            cachedLocaleMode = mode
            cachedLocalizedCtx = created
            created
        }
    }

    /** 清除 API 26–32 localizedContext 快取（語系切換時自動呼叫）。 */
    fun invalidateLocalizedContextCache() {
        synchronized(localizedCacheLock) {
            cachedLocaleAppCtx = null
            cachedLocaleMode = null
            cachedLocalizedCtx = null
        }
    }

    /** 套用使用者於設定頁選擇的語言模式，並安全觸發介面更新。 */
    fun applyLanguageMode(activity: Activity, mode: String) {
        val normalized = PhantomPrefs.normalizeAppLanguageMode(mode)
        val appCtx = activity.applicationContext
        val currentMode = getEffectiveMode(appCtx)
        PhantomPrefs.setAppLanguageMode(appCtx, normalized)
        invalidateLocalizedContextCache()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val lm = runCatching { appCtx.getSystemService(LocaleManager::class.java) }.getOrNull()
            if (lm != null) {
                val targetList = if (normalized == PhantomPrefs.APP_LOCALE_SYSTEM) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    LocaleList.forLanguageTags(normalized)
                }
                lm.applicationLocales = targetList
                return
            }
        }

        // API 26–32：同步 JVM default locale（供格式化與 AI 層讀取），並平滑重建 Activity
        val targetLocale = when (normalized) {
            PhantomPrefs.APP_LOCALE_ZH_TW -> Locale.forLanguageTag("zh-TW")
            PhantomPrefs.APP_LOCALE_EN -> Locale.ENGLISH
            else -> resolveSystemLocale()
        }
        Locale.setDefault(targetLocale)
        if (currentMode != normalized) {
            activity.window?.decorView?.post {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.recreate()
                }
            }
        }
    }
}
