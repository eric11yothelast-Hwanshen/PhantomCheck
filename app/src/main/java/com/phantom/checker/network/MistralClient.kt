package com.phantom.checker.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import com.google.gson.annotations.SerializedName

/**
 * 訊息風險分析 API 客戶端。
 *
 * 安全規則（公開倉庫！）：
 * - API key 一律從 BuildConfig.MISTRAL_API_KEY 讀取，來源為 local.properties
 * - local.properties 在 .gitignore 內，永遠不進倉庫
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

object MistralClient {
    private const val FALLBACK_URL = "https://api.mistral.ai/"

    private val api: MistralApi by lazy {
        val url = try {
            com.phantom.checker.BuildConfig.MISTRAL_BASE_URL.ifEmpty { FALLBACK_URL }
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

    /** 回傳 (分數, 標籤, 原因)；解析失敗回 null */
    suspend fun analyzeRisk(sender: String, content: String): Triple<Int, String, String>? {
        return try {
            val key = com.phantom.checker.BuildConfig.MISTRAL_API_KEY
            if (key.isEmpty()) return null
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
