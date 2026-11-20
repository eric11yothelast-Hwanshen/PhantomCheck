package com.phantom.checker.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 集中式 BYOK 機密儲存層（EncryptedSharedPreferences AES256_SIV / AES256_GCM）：
 * - 儲存外部搜尋端點金鑰（#337）、GitHub PAT 與 Discord Webhook URL（#339）。
 * - 嚴格遵守 SECURITY.md 紅線：絕不寫入 BuildConfig、repo 或 log。
 */
object EncryptedByokStore {

    private const val PREF_FILE = "phantom_byok_secrets"

    private const val KEY_EXT_SEARCH_URL = "ext_search_base_url"
    private const val KEY_EXT_SEARCH_MODEL = "ext_search_model"
    private const val KEY_EXT_SEARCH_API_KEY = "ext_search_api_key"

    private const val KEY_GITHUB_PAT = "github_pat"
    private const val KEY_GITHUB_REPOS = "github_repos"
    private const val KEY_DISCORD_WEBHOOK = "discord_webhook_url"

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun securePrefs(ctx: Context): SharedPreferences? {
        cachedPrefs?.let { return it }
        return synchronized(this) {
            cachedPrefs ?: try {
                val appCtx = ctx.applicationContext
                val masterKey = MasterKey.Builder(appCtx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    appCtx,
                    PREF_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ).also { cachedPrefs = it }
            } catch (_: Exception) {
                null
            }
        }
    }

    // ---- #337 外部情報搜尋（選配 OpenAI 相容搜尋端點 BYOK，如 Perplexity sonar） ----

    fun getExternalSearchBaseUrl(ctx: Context): String =
        securePrefs(ctx)?.getString(KEY_EXT_SEARCH_URL, "").orEmpty().trim()

    fun setExternalSearchBaseUrl(ctx: Context, url: String) {
        securePrefs(ctx)?.edit()?.putString(KEY_EXT_SEARCH_URL, url.trim())?.apply()
    }

    fun getExternalSearchModel(ctx: Context): String =
        securePrefs(ctx)?.getString(KEY_EXT_SEARCH_MODEL, "sonar").orEmpty().trim().ifEmpty { "sonar" }

    fun setExternalSearchModel(ctx: Context, model: String) {
        securePrefs(ctx)?.edit()?.putString(KEY_EXT_SEARCH_MODEL, model.trim())?.apply()
    }

    fun getExternalSearchApiKey(ctx: Context): String =
        securePrefs(ctx)?.getString(KEY_EXT_SEARCH_API_KEY, "").orEmpty().trim()

    fun setExternalSearchApiKey(ctx: Context, apiKey: String) {
        securePrefs(ctx)?.edit()?.putString(KEY_EXT_SEARCH_API_KEY, apiKey.trim())?.apply()
    }

    fun isExternalSearchConfigured(ctx: Context): Boolean =
        getExternalSearchBaseUrl(ctx).isNotEmpty() && getExternalSearchApiKey(ctx).isNotEmpty()

    // ---- #339 GitHub PAT / Discord Webhook BYOK ----

    fun getGitHubPat(ctx: Context): String =
        securePrefs(ctx)?.getString(KEY_GITHUB_PAT, "").orEmpty().trim()

    fun setGitHubPat(ctx: Context, pat: String) {
        securePrefs(ctx)?.edit()?.putString(KEY_GITHUB_PAT, pat.trim())?.apply()
    }

    fun getGitHubRepos(ctx: Context): String =
        securePrefs(ctx)?.getString(KEY_GITHUB_REPOS, "").orEmpty().trim()

    fun setGitHubRepos(ctx: Context, reposCsv: String) {
        securePrefs(ctx)?.edit()?.putString(KEY_GITHUB_REPOS, reposCsv.trim())?.apply()
    }

    fun isGitHubConfigured(ctx: Context): Boolean = getGitHubPat(ctx).isNotEmpty()

    fun getDiscordWebhookUrl(ctx: Context): String =
        securePrefs(ctx)?.getString(KEY_DISCORD_WEBHOOK, "").orEmpty().trim()

    fun setDiscordWebhookUrl(ctx: Context, url: String) {
        securePrefs(ctx)?.edit()?.putString(KEY_DISCORD_WEBHOOK, url.trim())?.apply()
    }

    fun isDiscordConfigured(ctx: Context): Boolean =
        getDiscordWebhookUrl(ctx).startsWith("https://")
}
