package com.byokeylab.speak

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** Interactions REST contract verified against Gemini 3.8 Flash-Lite TTS. */
internal object GeminiTtsProtocol {
    const val MODEL = "gemini-3.8-flash-lite-tts"
    private const val STYLE = "Warm conversational delivery; articulate clearly with natural pacing."

    fun requestBody(text: String, voice: String): JSONObject {
        val annotation = JSONObject().put("type", "speech_metadata").put("style", STYLE)
        val content = JSONObject().put("type", "text").put("text", text.trim())
            .put("annotations", JSONArray().put(annotation))
        val input = JSONObject().put("type", "user_input").put("content", JSONArray().put(content))
        return JSONObject()
            .put("model", MODEL)
            .put("input", JSONArray().put(input))
            .put("response_format", JSONObject()
                .put("type", "audio")
                .put("mime_type", "audio/l16")
                .put("sample_rate", 24_000))
            .put("generation_config", JSONObject().put(
                "speech_config", JSONArray().put(JSONObject().put("voice", voice))
            ))
            .put("stream", true)
    }

    sealed interface Event {
        data class Audio(val bytes: ByteArray) : Event
        data object Completed : Event
        data object Ignore : Event
    }

    fun parseEvent(raw: String): Event {
        val root = try { JSONObject(raw) } catch (_: Exception) {
            throw IllegalStateException("Gemini音声の応答を解析できませんでした。")
        }
        return when (root.optString("event_type")) {
            "error", "interaction.failed" -> throw IllegalStateException("Gemini音声の生成が失敗しました。")
            "interaction.completed" -> {
                if (root.optJSONObject("interaction")?.optString("status") != "completed") {
                    throw IllegalStateException("Gemini音声が正常終了しませんでした。")
                }
                Event.Completed
            }
            "step.delta" -> {
                val delta = root.optJSONObject("delta") ?: return Event.Ignore
                if (delta.optString("type") != "audio") return Event.Ignore
                val mime = delta.optString("mime_type", delta.optString("mimeType")).lowercase()
                val rateFromMime = Regex("(?:^|;)\\s*rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull()
                val channelsFromMime = Regex("(?:^|;)\\s*channels=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull()
                if (!Regex("^audio/l16(?:;|$)").containsMatchIn(mime) ||
                    (rateFromMime != null && rateFromMime != 24_000) ||
                    (channelsFromMime != null && channelsFromMime != 1) ||
                    (delta.has("sample_rate") && delta.optInt("sample_rate") != 24_000) ||
                    (delta.has("channels") && delta.optInt("channels") != 1)) {
                    throw IllegalStateException("Gemini音声の形式が想定と異なります。")
                }
                val encoded = delta.optString("data")
                if (encoded.isBlank()) return Event.Ignore
                val bytes = try { Base64.getDecoder().decode(encoded) } catch (_: IllegalArgumentException) {
                    throw IllegalStateException("Gemini音声のデータを解析できませんでした。")
                }
                Event.Audio(bytes)
            }
            else -> Event.Ignore
        }
    }

    fun isPcm(bytes: ByteArray): Boolean = bytes.isNotEmpty() && bytes.size % 2 == 0 &&
        !(bytes.size >= 4 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte())
}
