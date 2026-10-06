package com.example.tkuschedule.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// 保留原類別名稱，讓既有 ViewModel 不需要更換。
// 此版本只連本機 Ollama，不呼叫 Gemini。
class GeminiRepository {
    companion object {
        // 電腦的 Tailscale IPv4；手機與電腦需保持 Tailscale 連線。
        private const val SERVER_URL = "http://100.108.198.109:11434"
        // 先在電腦執行 ollama pull gemma3:1b。若回答品質不足，可改回 gemma3:4b。
        private const val MODEL = "gemma3:1b"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .callTimeout(300, TimeUnit.SECONDS)
        .build()

    suspend fun sendMessage(message: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("model", MODEL)
                .put("stream", false)
                .put("messages", JSONArray().put(JSONObject()
                    .put("role", "user").put("content", message)))
                .put("options", JSONObject()
                    .put("temperature", 0.2)
                    .put("num_gpu", 0) // 沿用先前避開 CUDA 錯誤的 CPU 設定。
                    .put("num_ctx", 4096)
                    .put("num_predict", 256))
            val request = Request.Builder()
                .url("$SERVER_URL/api/chat")
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                val json = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = runCatching { JSONObject(json).optString("error") }.getOrDefault("")
                    error("Gemma 連線失敗（HTTP ${response.code}）：$detail")
                }
                val answer = JSONObject(json).optJSONObject("message")
                    ?.optString("content")?.trim().orEmpty()
                check(answer.isNotBlank()) { "Gemma 沒有回傳內容" }
                Result.success(answer)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
