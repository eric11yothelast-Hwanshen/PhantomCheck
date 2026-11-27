package com.phantom.checker.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.GeneralSecurityException
import java.util.Locale
import javax.crypto.AEADBadTagException

/**
 * 集中式 BYOK 機密儲存層（EncryptedSharedPreferences AES256_SIV / AES256_GCM）：
 * - 儲存外部搜尋端點金鑰（#337）、GitHub PAT 與 Discord Webhook URL（#339）。
 * - 嚴格遵守 SECURITY.md 紅線：絕不寫入 BuildConfig、repo 或 log。
 * - R1 (#545)：雙重檢查單例＋記憶體快取層＋AEADBadTagException 記錄與清除重建。
 */
object EncryptedByokStore {

    private const val TAG = "EncryptedByokStore"
    private const val PREF_FILE = "phantom_byok_secrets"

    private const val KEY_EXT_SEARCH_URL = "ext_search_base_url"
    private const val KEY_EXT_SEARCH_MODEL = "ext_search_model"
    private const val KEY_EXT_SEARCH_API_KEY = "ext_search_api_key"

    private const val KEY_GITHUB_PAT = "github_pat"
    private const val KEY_GITHUB_REPOS = "github_repos"
    private const val KEY_DISCORD_WEBHOOK = "discord_webhook_url"

    private val prefsLock = Any()

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    @Volatile
    var lastKeystoreRecoveryAtMs: Long = 0L
        private set

    @Volatile
    var keystoreRecoveryCount: Int = 0
        private set

    // 記憶體快取層（寫入時同步更新，避免高頻呼叫重複同步解密）
    @Volatile
    private var cachedExtSearchUrl: String? = null
    @Volatile
    private var cachedExtSearchModel: String? = null
    @Volatile
    private var cachedExtSearchApiKey: String? = null
    @Volatile
    private var cachedGitHubPat: String? = null
    @Volatile
    private var cachedGitHubRepos: String? = null
    @Volatile
    private var cachedDiscordWebhook: String? = null
    @Volatile
    private var cachedBenignUrlHostsList: List<String>? = null
    @Volatile
    private var cachedBenignUrlHostsSet: Set<String>? = null

    private fun isKeystoreCorruption(err: Throwable): Boolean {
        var cur: Throwable? = err
        var depth = 0
        while (cur != null && depth < 12) {
            if (cur is AEADBadTagException ||
                cur is GeneralSecurityException ||
                cur.javaClass.name.contains("AEADBadTagException") ||
                cur.javaClass.name.contains("InvalidProtocolBufferException")
            ) {
                return true
            }
            cur = cur.cause
            depth++
        }
        return false
    }

    private fun clearMemoryCaches() {
        cachedExtSearchUrl = null
        cachedExtSearchModel = null
        cachedExtSearchApiKey = null
        cachedGitHubPat = null
        cachedGitHubRepos = null
        cachedDiscordWebhook = null
        cachedBenignUrlHostsList = null
        cachedBenignUrlHostsSet = null
    }

    @Suppress("DEPRECATION")
    private fun createEncryptedPrefs(appCtx: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(appCtx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appCtx,
            PREF_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** 清除損毀之 EncryptedSharedPreferences 檔案並重建；同時記錄復原次數與時間戳。 */
    fun resetCorruptedStore(ctx: Context, reason: Throwable? = null): SharedPreferences? {
        val appCtx = ctx.applicationContext ?: ctx
        return synchronized(prefsLock) {
            Log.w(
                TAG,
                "EncryptedSharedPreferences corrupted (${reason?.javaClass?.simpleName ?: "manual"}), clearing & recreating $PREF_FILE",
                reason
            )
            cachedPrefs = null
            clearMemoryCaches()
            runCatching { appCtx.deleteSharedPreferences(PREF_FILE) }
            runCatching {
                appCtx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit().clear().commit()
            }
            keystoreRecoveryCount += 1
            lastKeystoreRecoveryAtMs = System.currentTimeMillis()
            try {
                createEncryptedPrefs(appCtx).also { cachedPrefs = it }
            } catch (recreateErr: Throwable) {
                Log.w(TAG, "EncryptedSharedPreferences recreate failed: ${recreateErr.javaClass.simpleName}", recreateErr)
                null
            }
        }
    }

    private fun securePrefs(ctx: Context): SharedPreferences? {
        cachedPrefs?.let { return it }
        return synchronized(prefsLock) {
            cachedPrefs ?: run {
                val appCtx = ctx.applicationContext ?: ctx
                try {
                    createEncryptedPrefs(appCtx).also { cachedPrefs = it }
                } catch (err: Throwable) {
                    if (isKeystoreCorruption(err)) {
                        resetCorruptedStore(appCtx, err)
                    } else {
                        Log.w(TAG, "EncryptedSharedPreferences init failed: ${err.javaClass.simpleName}")
                        null
                    }
                }
            }
        }
    }

    private fun readStringSafe(ctx: Context, key: String, default: String = ""): String {
        val prefs = securePrefs(ctx) ?: return default
        return try {
            prefs.getString(key, default) ?: default
        } catch (err: Throwable) {
            if (isKeystoreCorruption(err)) {
                resetCorruptedStore(ctx, err)
            } else {
                Log.w(TAG, "EncryptedSharedPreferences read failed ($key): ${err.javaClass.simpleName}")
            }
            default
        }
    }

    private fun writeStringSafe(ctx: Context, key: String, value: String) {
        val prefs = securePrefs(ctx) ?: return
        try {
            prefs.edit().putString(key, value).apply()
        } catch (err: Throwable) {
            if (isKeystoreCorruption(err)) {
                val recovered = resetCorruptedStore(ctx, err) ?: return
                runCatching { recovered.edit().putString(key, value).apply() }
            } else {
                Log.w(TAG, "EncryptedSharedPreferences write failed ($key): ${err.javaClass.simpleName}")
            }
        }
    }

    // ---- #337 外部情報搜尋（選配 OpenAI 相容搜尋端點 BYOK，如 Perplexity sonar） ----

    fun getExternalSearchBaseUrl(ctx: Context): String {
        cachedExtSearchUrl?.let { return it }
        val loaded = readStringSafe(ctx, KEY_EXT_SEARCH_URL, "").trim()
        cachedExtSearchUrl = loaded
        return loaded
    }

    fun setExternalSearchBaseUrl(ctx: Context, url: String) {
        val trimmed = url.trim()
        cachedExtSearchUrl = trimmed
        writeStringSafe(ctx, KEY_EXT_SEARCH_URL, trimmed)
    }

    fun getExternalSearchModel(ctx: Context): String {
        cachedExtSearchModel?.let { return it }
        val loaded = readStringSafe(ctx, KEY_EXT_SEARCH_MODEL, "sonar").trim().ifEmpty { "sonar" }
        cachedExtSearchModel = loaded
        return loaded
    }

    fun setExternalSearchModel(ctx: Context, model: String) {
        val trimmed = model.trim()
        cachedExtSearchModel = trimmed.ifEmpty { "sonar" }
        writeStringSafe(ctx, KEY_EXT_SEARCH_MODEL, trimmed)
    }

    fun getExternalSearchApiKey(ctx: Context): String {
        cachedExtSearchApiKey?.let { return it }
        val loaded = readStringSafe(ctx, KEY_EXT_SEARCH_API_KEY, "").trim()
        cachedExtSearchApiKey = loaded
        return loaded
    }

    fun setExternalSearchApiKey(ctx: Context, apiKey: String) {
        val trimmed = apiKey.trim()
        cachedExtSearchApiKey = trimmed
        writeStringSafe(ctx, KEY_EXT_SEARCH_API_KEY, trimmed)
    }

    fun isExternalSearchConfigured(ctx: Context): Boolean =
        getExternalSearchBaseUrl(ctx).isNotEmpty() && getExternalSearchApiKey(ctx).isNotEmpty()

    // ---- #339 GitHub PAT / Discord Webhook BYOK ----

    fun getGitHubPat(ctx: Context): String {
        cachedGitHubPat?.let { return it }
        val loaded = readStringSafe(ctx, KEY_GITHUB_PAT, "").trim()
        cachedGitHubPat = loaded
        return loaded
    }

    fun setGitHubPat(ctx: Context, pat: String) {
        val trimmed = pat.trim()
        cachedGitHubPat = trimmed
        writeStringSafe(ctx, KEY_GITHUB_PAT, trimmed)
    }

    fun getGitHubRepos(ctx: Context): String {
        cachedGitHubRepos?.let { return it }
        val loaded = readStringSafe(ctx, KEY_GITHUB_REPOS, "").trim()
        cachedGitHubRepos = loaded
        return loaded
    }

    fun setGitHubRepos(ctx: Context, reposCsv: String) {
        val trimmed = reposCsv.trim()
        cachedGitHubRepos = trimmed
        writeStringSafe(ctx, KEY_GITHUB_REPOS, trimmed)
    }

    fun isGitHubConfigured(ctx: Context): Boolean = getGitHubPat(ctx).isNotEmpty()

    fun getDiscordWebhookUrl(ctx: Context): String {
        cachedDiscordWebhook?.let { return it }
        val loaded = readStringSafe(ctx, KEY_DISCORD_WEBHOOK, "").trim()
        cachedDiscordWebhook = loaded
        return loaded
    }

    fun setDiscordWebhookUrl(ctx: Context, url: String) {
        val trimmed = url.trim()
        cachedDiscordWebhook = trimmed
        writeStringSafe(ctx, KEY_DISCORD_WEBHOOK, trimmed)
    }

    fun isDiscordConfigured(ctx: Context): Boolean =
        getDiscordWebhookUrl(ctx).startsWith("https://")

    // ---- #375 本地良性 URL Host 清單（EncryptedSharedPreferences，容量上限 100，LRU 淘汰） ----

    private const val KEY_BENIGN_URL_HOSTS = "benign_url_hosts_lru"
    private const val MAX_BENIGN_URL_HOSTS = 100

    private fun loadBenignUrlHostsListLocked(ctx: Context): List<String> {
        cachedBenignUrlHostsList?.let { return it }
        val raw = readStringSafe(ctx, KEY_BENIGN_URL_HOSTS, "")
        val list = if (raw.isBlank()) {
            emptyList()
        } else {
            raw.split('\n').mapNotNull { it.trim().lowercase(Locale.ROOT).takeIf { s -> s.isNotEmpty() } }
        }
        cachedBenignUrlHostsList = list
        cachedBenignUrlHostsSet = list.toSet()
        return list
    }

    fun getBenignUrlHosts(ctx: Context): Set<String> {
        cachedBenignUrlHostsSet?.let { return it }
        synchronized(this) {
            cachedBenignUrlHostsSet?.let { return it }
            loadBenignUrlHostsListLocked(ctx)
            return cachedBenignUrlHostsSet ?: emptySet()
        }
    }

    fun isBenignUrlHost(ctx: Context, host: String): Boolean {
        val norm = host.trim().lowercase(Locale.ROOT).removePrefix("www.")
        if (norm.isBlank()) return false
        return norm in getBenignUrlHosts(ctx)
    }

    @Synchronized
    fun addBenignUrlHost(ctx: Context, host: String) {
        val norm = host.trim().lowercase(Locale.ROOT).removePrefix("www.")
        if (norm.isBlank()) return
        val list = loadBenignUrlHostsListLocked(ctx)
            .filter { it != norm }
            .toMutableList()
        list.add(norm)
        val trimmed = if (list.size > MAX_BENIGN_URL_HOSTS) list.takeLast(MAX_BENIGN_URL_HOSTS) else list
        cachedBenignUrlHostsList = trimmed
        cachedBenignUrlHostsSet = trimmed.toSet()
        writeStringSafe(ctx, KEY_BENIGN_URL_HOSTS, trimmed.joinToString("\n"))
    }
}
