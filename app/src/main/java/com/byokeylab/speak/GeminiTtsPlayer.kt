package com.byokeylab.speak

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit

/** Gemini 3.1 Flash TTS PreviewのPCMストリームを直接再生する。 */
class GeminiTtsPlayer(context: Context) {
    interface Callback {
        fun onStart()
        fun onComplete()
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
    private val cacheDir = File(appContext.filesDir, "generated_tts").apply { mkdirs() }
    private var activeJob: Job? = null
    private val playbackSequence = AtomicLong(0)
    @Volatile private var activeCall: okhttp3.Call? = null
    @Volatile private var activeTrack: AudioTrack? = null

    fun play(apiKey: String, text: String, voice: GeminiTtsVoice, callback: Callback) {
        stop()
        val playbackId = playbackSequence.incrementAndGet()
        if (apiKey.isBlank()) {
            mainHandler.post { if (isCurrent(playbackId)) callback.onError("Gemini APIキーが未入力です。") }
            return
        }
        if (text.isBlank()) return
        activeJob = scope.launch {
            val cache = cacheFile(text, voice)
            try {
                if (cache.isFile && cache.length() > 0) {
                    playPcm(cache.readBytes(), playbackId, callback)
                } else {
                    streamToCacheAndPlay(apiKey, text, voice, cache, playbackId, callback)
                }
            } catch (error: TtsCancelledException) {
                // 利用者が停止した場合はエラーも完了も通知しない。
            } catch (error: IOException) {
                mainHandler.post {
                    if (isCurrent(playbackId)) callback.onError("通信に失敗しました。接続を確認してください。")
                }
            } catch (error: Exception) {
                mainHandler.post {
                    if (isCurrent(playbackId)) callback.onError(error.message ?: "Gemini音声を再生できませんでした。")
                }
            } finally {
                if (isCurrent(playbackId)) {
                    releaseTrack()
                    activeCall = null
                    activeJob = null
                }
            }
        }
    }

    fun stop() {
        playbackSequence.incrementAndGet()
        activeJob?.cancel()
        activeJob = null
        activeCall?.cancel()
        activeCall = null
        releaseTrack()
    }

    fun close() {
        stop()
        scope.cancel()
    }

    private fun streamToCacheAndPlay(
        apiKey: String,
        text: String,
        voice: GeminiTtsVoice,
        cache: File,
        playbackId: Long,
        callback: Callback
    ) {
        val body = JSONObject()
            .put("model", MODEL)
            .put("input", text)
            .put("response_format", JSONObject().put("type", "audio"))
            .put("generation_config", JSONObject().put(
                "speech_config", JSONArray().put(JSONObject().put("voice", voice.apiName))
            ))
            .put("stream", true)
            .toString()
            .toRequestBody(JSON)
        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .addHeader("Api-Revision", API_REVISION)
            .post(body)
            .build()
        val call = client.newCall(request)
        activeCall = call
        call.execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Gemini音声を生成できませんでした（${response.code}）。")
            }
            val source = response.body?.source() ?: throw IOException("音声応答がありません。")
            val temp = File(cache.parentFile, "${cache.name}.tmp")
            var started = false
            var received = false
            FileOutputStream(temp).use { output ->
                while (!source.exhausted()) {
                    ensureActive(playbackId)
                    val line = source.readUtf8LineStrictOrNull() ?: continue
                    if (!line.startsWith("data:")) continue
                    val event = line.removePrefix("data:").trim()
                    if (event.isBlank() || event == "[DONE]") continue
                    val chunk = extractAudioChunk(event) ?: continue
                    if (chunk.isEmpty()) continue
                    if (!started) {
                        started = true
                        mainHandler.post { if (isCurrent(playbackId)) callback.onStart() }
                        activeTrack = createTrack().also { it.play() }
                    }
                    received = true
                    output.write(chunk)
                    activeTrack?.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                }
            }
            if (!received) {
                temp.delete()
                throw IllegalStateException("Gemini音声データを取得できませんでした。")
            }
            if (!temp.renameTo(cache)) {
                temp.copyTo(cache, overwrite = true)
                temp.delete()
            }
            trimCache()
            mainHandler.post { if (isCurrent(playbackId)) callback.onComplete() }
        }
    }

    private fun playPcm(bytes: ByteArray, playbackId: Long, callback: Callback) {
        ensureActive(playbackId)
        mainHandler.post { if (isCurrent(playbackId)) callback.onStart() }
        activeTrack = createTrack().also { it.play() }
        var offset = 0
        while (offset < bytes.size) {
            ensureActive(playbackId)
            val size = minOf(8_192, bytes.size - offset)
            val wrote = activeTrack?.write(bytes, offset, size, AudioTrack.WRITE_BLOCKING) ?: 0
            if (wrote <= 0) throw IOException("音声を再生できませんでした。")
            offset += wrote
        }
        mainHandler.post { if (isCurrent(playbackId)) callback.onComplete() }
    }

    private fun extractAudioChunk(raw: String): ByteArray? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val delta = root.optJSONObject("delta") ?: return null
        if (delta.optString("type") != "audio") return null
        return delta.optString("data").takeIf { it.isNotBlank() }
            ?.let { Base64.decode(it, Base64.DEFAULT) }
    }

    private fun createTrack(): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(8_192)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE_HZ)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun ensureActive(playbackId: Long) {
        if (Thread.currentThread().isInterrupted || !isCurrent(playbackId) || activeJob?.isCancelled == true) {
            throw TtsCancelledException()
        }
    }

    private fun isCurrent(playbackId: Long): Boolean = playbackSequence.get() == playbackId

    private fun releaseTrack() {
        val track = activeTrack ?: return
        activeTrack = null
        runCatching { track.pause() }
        runCatching { track.flush() }
        runCatching { track.release() }
    }

    private fun cacheFile(text: String, voice: GeminiTtsVoice): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$MODEL|${voice.apiName}|$text".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$digest.pcm")
    }

    private fun trimCache() {
        val files = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= MAX_CACHE_BYTES) break
            total -= file.length()
            file.delete()
        }
    }

    private class TtsCancelledException : RuntimeException()

    private fun BufferedSource.readUtf8LineStrictOrNull(): String? =
        if (exhausted()) null else readUtf8Line()

    companion object {
        const val MODEL = "gemini-3.1-flash-tts-preview"
        private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions"
        private const val API_REVISION = "2026-05-20"
        private const val SAMPLE_RATE_HZ = 24_000
        private const val MAX_CACHE_BYTES = 20L * 1024L * 1024L
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun clearCache(context: Context) {
            val directory = File(context.applicationContext.filesDir, "generated_tts")
            directory.listFiles()?.forEach { file -> runCatching { file.delete() } }
            runCatching { directory.delete() }
        }
    }
}
