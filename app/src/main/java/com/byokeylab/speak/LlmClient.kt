package com.byokeylab.speak

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class LlmClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generate(provider: LlmProvider, apiKey: String, model: String, prompt: String): String {
        return withContext(Dispatchers.IO) {
            when (provider) {
                LlmProvider.Gemini -> generateWithGemini(apiKey, model, prompt)
                LlmProvider.OpenAI -> generateWithOpenAi(apiKey, model, prompt)
                LlmProvider.Anthropic -> generateWithAnthropic(apiKey, model, prompt)
            }
        }
    }

    private fun generateWithGemini(apiKey: String, model: String, prompt: String): String {
        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
            .put(
                "generationConfig",
                JSONObject().put("temperature", 0.7).put("topP", 0.95)
            )
            .toString()
            .toRequestBody(JSON)

        val modelName = model.trim().removePrefix("models/").ifBlank { LlmProvider.Gemini.defaultModel }
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent")
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val response = execute(request, LlmProvider.Gemini)
        return collectTexts(response.optJSONArray("candidates"))
            .ifBlank { throw IOException("Gemini returned no text.") }
    }

    private fun generateWithOpenAi(apiKey: String, model: String, prompt: String): String {
        val body = JSONObject()
            .put("model", model.trim().ifBlank { LlmProvider.OpenAI.defaultModel })
            .put("input", prompt)
            .put("store", false)
            .toString()
            .toRequestBody(JSON)

        val request = Request.Builder()
            .url("https://api.openai.com/v1/responses")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val response = execute(request, LlmProvider.OpenAI)
        response.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }
        return collectTexts(response.optJSONArray("output"))
            .ifBlank { throw IOException("OpenAI returned no text.") }
    }

    private fun generateWithAnthropic(apiKey: String, model: String, prompt: String): String {
        val body = JSONObject()
            .put("model", model.trim().ifBlank { LlmProvider.Anthropic.defaultModel })
            .put("max_tokens", 1200)
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", prompt))
            )
            .toString()
            .toRequestBody(JSON)

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val response = execute(request, LlmProvider.Anthropic)
        return collectTexts(response.optJSONArray("content"))
            .ifBlank { throw IOException("Claude returned no text.") }
    }

    private fun execute(request: Request, provider: LlmProvider): JSONObject {
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("${provider.label} API error ${response.code}: ${parseError(raw)}")
            }
            return runCatching { JSONObject(raw) }
                .getOrElse { throw IOException("${provider.label} returned an invalid response.") }
        }
    }

    private fun parseError(raw: String): String {
        return runCatching {
            val error = JSONObject(raw).opt("error")
            when (error) {
                is JSONObject -> error.optString("message").ifBlank { raw }
                is String -> error.ifBlank { raw }
                else -> raw
            }
        }.getOrDefault(raw.ifBlank { "Unknown error" })
    }

    private fun collectTexts(array: JSONArray?): String {
        if (array == null) return ""
        val output = mutableListOf<String>()
        for (index in 0 until array.length()) {
            appendTexts(array.opt(index), output)
        }
        return output.distinct().joinToString("\n").trim()
    }

    private fun appendTexts(value: Any?, output: MutableList<String>) {
        when (value) {
            is JSONObject -> {
                val type = value.optString("type")
                val text = value.optString("text")
                if (text.isNotBlank() && (type.isBlank() || type == "text" || type == "output_text")) {
                    output.add(text)
                }
                value.keys().forEach { key ->
                    if (key != "text") appendTexts(value.opt(key), output)
                }
            }
            is JSONArray -> {
                for (index in 0 until value.length()) appendTexts(value.opt(index), output)
            }
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
