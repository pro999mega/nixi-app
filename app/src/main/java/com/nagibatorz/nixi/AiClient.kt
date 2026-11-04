package com.nagibatorz.nixi

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object AiClient {

    private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"

    fun chat(apiKey: String, model: String, messages: JSONArray): String {
        return try {
            val payload = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("max_tokens", 700)
                put("temperature", 0.6)
            }.toString()

            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20000
                readTimeout = 60000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("HTTP-Referer", "https://github.com/pro999mega")
                setRequestProperty("X-Title", "Nixi Mobile")
            }

            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }

            if (code !in 200..299) {
                "Ошибка $code. ${body.take(200)}"
            } else {
                val json = JSONObject(body)
                val content = json.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                if (content.isBlank()) "Модель ответила пусто." else content
            }
        } catch (t: Throwable) {
            "Не получилось связаться с моделью: ${t.message ?: "нет сети"}"
        }
    }
}
