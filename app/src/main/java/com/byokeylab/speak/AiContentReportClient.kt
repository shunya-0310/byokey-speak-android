package com.byokeylab.speak

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

enum class AiReportReason(val label: String) {
    Inappropriate("不適切・不快な内容"),
    Harmful("危険・有害な内容"),
    Incorrect("明らかな誤情報"),
    Other("その他")
}

class AiReportEndpointUnavailableException : IOException("通報受付先が設定されていません。")

class AiContentReportClient(
    private val endpoint: String = BuildConfig.AI_REPORT_ENDPOINT,
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun submit(
        coachMessage: String,
        reason: AiReportReason,
        comment: String,
        precedingUserMessage: String?,
        provider: String,
        model: String
    ) = withContext(Dispatchers.IO) {
        if (endpoint.isBlank()) throw AiReportEndpointUnavailableException()
        val payload = JSONObject()
            .put("schemaVersion", 1)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("reason", reason.name)
            .put("provider", provider.take(MAX_PROVIDER_LENGTH))
            .put("model", model.take(MAX_PROVIDER_LENGTH))
            .put("coachMessage", coachMessage.take(MAX_TEXT_LENGTH))
            .put("comment", comment.trim().take(MAX_COMMENT_LENGTH))
            .put(
                "precedingUserMessage",
                precedingUserMessage?.take(MAX_TEXT_LENGTH) ?: JSONObject.NULL
            )
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(endpoint)
            .post(payload)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("通報を送信できませんでした（${response.code}）。")
            }
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MAX_TEXT_LENGTH = 8_000
        const val MAX_COMMENT_LENGTH = 1_000
        const val MAX_PROVIDER_LENGTH = 120
    }
}
