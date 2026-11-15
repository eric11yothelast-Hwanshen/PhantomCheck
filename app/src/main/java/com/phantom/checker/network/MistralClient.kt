package com.phantom.checker.network

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.annotations.SerializedName
import com.phantom.checker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * 訊息風險分析 API 客戶端。
 *
 * API key 讀取順序：
 * 1. EncryptedSharedPreferences（App 內設定儲存）
 * 2. BuildConfig.MISTRAL_API_KEY（local.properties 注入）
 * 3. 空值（回傳 null，由 UI 提示未設定）
 */
data class ChatMessage(val role: String, val content: String)
data class ChatRequest(
    val model: String = "mistral-small-latest",
    val messages: List<ChatMessage>,
    @SerializedName("max_tokens") val maxTokens: Int = 200,
    val temperature: Double = 0.2
)
data class ChatChoice(val message: ChatMessage)
data class ChatResponse(val choices: List<ChatChoice>)

interface MistralApi {
    @POST("v1/chat/completions")
    suspend fun chat(
        @Header("Authorization") auth: String,
        @Body body: ChatRequest
    ): ChatResponse
}

enum class ApiKeySource {
    ENCRYPTED_PREFS,
    BUILD_CONFIG,
    NONE
}

object MistralClient {
    private const val FALLBACK_URL = "https://api.mistral.ai/"
    private const val PREFS_NAME = "phantom_secure_prefs"
    private const val KEY_MISTRAL_API_KEY = "mistral_api_key"

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun resolveContext(context: Context?): Context? {
        if (context != null) return context.applicationContext
        appContext?.let { return it }
        return try {
            val app = Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? Application
            app?.applicationContext?.also { appContext = it }
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun getEncryptedPrefs(context: Context): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            null
        }
    }

    fun saveApiKey(context: Context, key: String) {
        init(context)
        val trimmed = key.trim()
        val prefs = getEncryptedPrefs(context) ?: return
        if (trimmed.isEmpty()) {
            prefs.edit().remove(KEY_MISTRAL_API_KEY).apply()
        } else {
            prefs.edit().putString(KEY_MISTRAL_API_KEY, trimmed).apply()
        }
    }

    fun clearApiKey(context: Context) {
        init(context)
        getEncryptedPrefs(context)?.edit()?.remove(KEY_MISTRAL_API_KEY)?.apply()
    }

    fun getStoredApiKey(context: Context): String? {
        return getEncryptedPrefs(context)
            ?.getString(KEY_MISTRAL_API_KEY, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun getBuildConfigApiKey(): String? {
        return try {
            BuildConfig.MISTRAL_API_KEY.trim().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    fun getActiveApiKey(context: Context? = null): String? {
        val ctx = resolveContext(context)
        if (ctx != null) {
            getStoredApiKey(ctx)?.let { return it }
        }
        return getBuildConfigApiKey()
    }

    fun getKeySource(context: Context): ApiKeySource {
        if (getStoredApiKey(context) != null) return ApiKeySource.ENCRYPTED_PREFS
        if (getBuildConfigApiKey() != null) return ApiKeySource.BUILD_CONFIG
        return ApiKeySource.NONE
    }

    fun getMaskedKeyTail(key: String?): String? {
        val trimmed = key?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (trimmed.length > 4) "••••${trimmed.takeLast(4)}" else "••••"
    }

    private val api: MistralApi by lazy {
        val url = try {
            BuildConfig.MISTRAL_BASE_URL.ifEmpty { FALLBACK_URL }
        } catch (e: Exception) { FALLBACK_URL }
        Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MistralApi::class.java)
    }

    private val SYSTEM_PROMPT = """
        你是防詐分析引擎。分析使用者收到的訊息，判斷是否為詐騙/釣魚。
        只輸出 JSON：{"risk": 0-100, "label": "safe|suspicious|scam", "reason": "一句話說明"}
    """.trimIndent()

    /** 使用指定的 API key 發送 max_tokens=5 的最小請求以驗證連線 */
    suspend fun testConnection(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("請先輸入 API Key"))
        }
        try {
            val res = api.chat(
                auth = "Bearer $trimmed",
                body = ChatRequest(
                    messages = listOf(ChatMessage("user", "Hi")),
                    maxTokens = 5,
                    temperature = 0.0
                )
            )
            if (res.choices.isNotEmpty()) {
                Result.success("連線成功！API Key 驗證通過")
            } else {
                Result.failure(IllegalStateException("API 回應為空"))
            }
        } catch (e: HttpException) {
            Result.failure(Exception("HTTP ${e.code()}：驗證失敗，請確認 API Key 是否正確"))
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "連線失敗"))
        }
    }

    /** 回傳 (分數, 標籤, 原因)；未設定 key 或解析失敗回 null */
    suspend fun analyzeRisk(
        sender: String,
        content: String,
        context: Context? = null
    ): Triple<Int, String, String>? {
        return try {
            val key = getActiveApiKey(context) ?: return null
            val res = api.chat("Bearer $key", ChatRequest(messages = listOf(
                ChatMessage("system", SYSTEM_PROMPT),
                ChatMessage("user", "寄件者: $sender\n內容: $content")
            )))
            val text = res.choices.firstOrNull()?.message?.content ?: return null
            val json = text.substringAfter("{").substringBeforeLast("}")
            val risk = Regex("\"risk\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toInt() ?: 0
            val label = Regex("\"label\"\\s*:\\s*\"(\\w+)\"").find(json)?.groupValues?.get(1) ?: "safe"
            val reason = Regex("\"reason\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1) ?: ""
            Triple(risk, label, reason)
        } catch (e: Exception) { null }
    }
}
