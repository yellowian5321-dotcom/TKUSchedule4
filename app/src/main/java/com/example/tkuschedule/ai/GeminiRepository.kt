package com.example.tkuschedule.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend

class GeminiRepository {

    private val model = Firebase
        .ai(
            backend =
                GenerativeBackend.googleAI()
        )
        .generativeModel(
            modelName = "gemini-2.5-flash"
        )

    suspend fun sendMessage(
        message: String
    ): Result<String> {

        return runCatching {
            val response =
                model.generateContent(message)

            response.text
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: "Gemini 沒有回傳內容"
        }
    }
}