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

data class LlmSource(val title: String, val url: String)

data class LlmResult(
    val text: String,
    val sources: List<LlmSource> = emptyList(),
    val searchAttempted: Boolean = false,
    val searchUsed: Boolean = false
) {
    fun textWithSources(): String {
        if (sources.isEmpty()) return text
        val sourceText = sources.joinToString("\n") { source ->
            "${source.title.ifBlank { source.url }}\n${source.url}"
        }
        return "$text\n\nSources:\n$sourceText"
    }
}

class LlmClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generate(provider: LlmProvider, apiKey: String, model: String, prompt: String): String {
        return generateDetailed(provider, apiKey, model, prompt).text
    }

    suspend fun generateDetailed(
        provider: LlmProvider,
        apiKey: String,
        model: String,
        prompt: String,
        enableWebSearch: Boolean = false
    ): LlmResult {
        if (apiKey.isBlank()) {
            throw LlmException(LlmErrorKind.MissingApiKey, LlmErrorKind.MissingApiKey.userMessage)
        }
        return withContext(Dispatchers.IO) {
            // Gemini専用版。保存済み設定はAppStorageでGeminiへ正規化済み。
            generateWithGemini(apiKey, model, prompt, enableWebSearch)
        }
    }

    /** FR-002: 接続テスト。成功ならnull、失敗なら分類済み例外を返す。 */
    suspend fun testConnection(provider: LlmProvider, apiKey: String, model: String): LlmException? {
        return try {
            generate(provider, apiKey, model, "Reply with exactly: OK")
            null
        } catch (error: LlmException) {
            error
        } catch (error: Exception) {
            LlmException(LlmErrorKind.Unknown, LlmErrorKind.Unknown.userMessage)
        }
    }

    private fun generateWithGemini(
        apiKey: String,
        model: String,
        prompt: String,
        enableWebSearch: Boolean
    ): LlmResult {
        val bodyJson = JSONObject()
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
        if (enableWebSearch) {
            bodyJson.put(
                "tools",
                JSONArray().put(JSONObject().put("google_search", JSONObject()))
            )
        }
        val body = bodyJson
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
        val text = collectTexts(response.optJSONArray("candidates"))
            .ifBlank { throw LlmException(LlmErrorKind.Provider, "Gemini returned no text.") }
        val sources = collectSources(response)
        return LlmResult(text, sources, enableWebSearch, sources.isNotEmpty())
    }


    private fun execute(request: Request, provider: LlmProvider): JSONObject {
        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            throw LlmException(LlmErrorKind.Network, LlmErrorKind.Network.userMessage)
        }
        response.use {
            val raw = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val kind = LlmErrorClassifier.classifyHttp(it.code, raw)
                // NOTE: rawをそのまま出さない(キー混入防止)。分類済みメッセージ+短い要約のみ。
                throw LlmException(kind, "${provider.label}: ${kind.userMessage}")
            }
            return runCatching { JSONObject(raw) }
                .getOrElse {
                    throw LlmException(LlmErrorKind.Provider, "${provider.label} returned an invalid response.")
                }
        }
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

    private fun collectSources(root: JSONObject): List<LlmSource> {
        val output = linkedMapOf<String, LlmSource>()
        appendSources(root, output)
        return output.values.take(6)
    }

    private fun appendSources(value: Any?, output: MutableMap<String, LlmSource>) {
        when (value) {
            is JSONObject -> {
                val web = value.optJSONObject("web")
                if (web != null) addSource(web, output)
                addSource(value, output)
                value.keys().forEach { key -> appendSources(value.opt(key), output) }
            }
            is JSONArray -> {
                for (index in 0 until value.length()) appendSources(value.opt(index), output)
            }
        }
    }

    private fun addSource(value: JSONObject, output: MutableMap<String, LlmSource>) {
        val url = sequenceOf("url", "uri")
            .map(value::optString)
            .firstOrNull { it.startsWith("https://") }
            ?: return
        val title = sequenceOf("title", "name")
            .map(value::optString)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        output.putIfAbsent(url, LlmSource(title, url))
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/** HTTPステータスとエラー本文からエラー種別を分類する。純粋関数(テスト対象)。 */
object LlmErrorClassifier {
    fun classifyHttp(code: Int, body: String): LlmErrorKind {
        val lower = body.lowercase()
        return when {
            code == 401 || code == 403 -> LlmErrorKind.InvalidApiKey
            code == 404 -> LlmErrorKind.InvalidModel
            code == 402 -> LlmErrorKind.Billing
            code == 429 && (
                lower.contains("insufficient_quota") ||
                    lower.contains("billing") ||
                    lower.contains("credit balance")
                ) -> LlmErrorKind.Billing
            code == 429 -> LlmErrorKind.RateLimit
            code == 400 && (
                lower.contains("model") &&
                    (lower.contains("not found") || lower.contains("not_found") || lower.contains("invalid") || lower.contains("does not exist"))
                ) -> LlmErrorKind.InvalidModel
            code == 400 && lower.contains("api key") -> LlmErrorKind.InvalidApiKey
            code == 400 && lower.contains("api_key") -> LlmErrorKind.InvalidApiKey
            code >= 500 -> LlmErrorKind.Provider
            else -> LlmErrorKind.Unknown
        }
    }
}
