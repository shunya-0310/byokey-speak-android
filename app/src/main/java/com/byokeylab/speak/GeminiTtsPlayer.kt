package com.byokeylab.speak

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit

/** Gemini 3.8 Flash-Lite TTSのPCMストリームを直接再生する。 */
class GeminiTtsPlayer(context: Context) {
    interface Callback {
        fun onGenerating()
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
                val cachedBytes = cache.takeIf { it.isFile }?.readBytes()
                if (cachedBytes != null && GeminiTtsProtocol.isPcm(cachedBytes)) {
                    playPcm(cachedBytes, playbackId, callback)
                } else {
                    if (cachedBytes != null) cache.delete()
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
        ensureActive(playbackId)
        mainHandler.post { if (isCurrent(playbackId)) callback.onGenerating() }
        val body = GeminiTtsProtocol.requestBody(text, voice.apiName)
            .toString()
            .toRequestBody(JSON)
        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
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
            var completed = false
            var pendingByte: Byte? = null
            var initialBytes = ByteArray(0)
            var writtenFrames = 0
            try {
                FileOutputStream(temp).use { output ->
                    while (!source.exhausted()) {
                        ensureActive(playbackId)
                        val line = source.readUtf8LineStrictOrNull() ?: continue
                        if (!line.startsWith("data:")) continue
                        val event = line.removePrefix("data:").trim()
                        if (event.isBlank() || event == "[DONE]") continue
                        when (val parsed = GeminiTtsProtocol.parseEvent(event)) {
                            GeminiTtsProtocol.Event.Completed -> completed = true
                            GeminiTtsProtocol.Event.Ignore -> Unit
                            is GeminiTtsProtocol.Event.Audio -> {
                                if (completed) throw IllegalStateException("Gemini音声の完了後にデータを受信しました。")
                                val bytes = if (pendingByte == null) parsed.bytes else byteArrayOf(pendingByte!!) + parsed.bytes
                                pendingByte = if (bytes.size % 2 == 1) bytes.last() else null
                                var chunk = if (pendingByte == null) bytes else bytes.copyOf(bytes.size - 1)
                                if (chunk.isEmpty()) continue
                                if (!started) {
                                    chunk = initialBytes + chunk
                                    if (chunk.size < 4) {
                                        initialBytes = chunk
                                        continue
                                    }
                                    if (!GeminiTtsProtocol.isPcm(chunk)) throw IllegalStateException("Gemini音声の形式が想定と異なります。")
                                    initialBytes = ByteArray(0)
                                    started = true
                                    mainHandler.post { if (isCurrent(playbackId)) callback.onStart() }
                                    activeTrack = createTrack().also { it.play() }
                                }
                                received = true
                                output.write(chunk)
                                val track = activeTrack ?: throw IOException("音声を再生できませんでした。")
                                writtenFrames += writePcmFully(track, chunk, playbackId) / BYTES_PER_FRAME
                            }
                        }
                    }
                }
                ensureActive(playbackId)
                if (!received || !completed || pendingByte != null) {
                    throw IllegalStateException("Gemini音声のストリームが正常終了しませんでした。")
                }
                if (!temp.renameTo(cache)) temp.copyTo(cache, overwrite = true)
                trimCache()
                awaitPlaybackFinished(activeTrack, writtenFrames, playbackId)
                mainHandler.post { if (isCurrent(playbackId)) callback.onComplete() }
            } finally {
                temp.delete()
            }
        }
    }

    private fun playPcm(bytes: ByteArray, playbackId: Long, callback: Callback) {
        ensureActive(playbackId)
        mainHandler.post { if (isCurrent(playbackId)) callback.onStart() }
        val track = createTrack().also {
            activeTrack = it
            it.play()
        }
        val writtenFrames = writePcmFully(track, bytes, playbackId) / BYTES_PER_FRAME
        awaitPlaybackFinished(track, writtenFrames, playbackId)
        mainHandler.post { if (isCurrent(playbackId)) callback.onComplete() }
    }

    /**
     * AudioTrack.write() returns when PCM is queued, not when speakers have played it.
     * The automatic microphone must wait for the playback head, otherwise it can interrupt
     * the tail of a coach reply.
     */
    private fun awaitPlaybackFinished(track: AudioTrack?, writtenFrames: Int, playbackId: Long) {
        val playbackTrack = track ?: throw IOException("音声を再生できませんでした。")
        if (writtenFrames <= 0) return
        val expectedDurationMs = writtenFrames * 1_000L / SAMPLE_RATE_HZ
        val deadline = SystemClock.elapsedRealtime() + expectedDurationMs + PLAYBACK_DRAIN_GRACE_MS
        while (playbackTrack.playbackHeadPosition < writtenFrames) {
            ensureActive(playbackId)
            if (SystemClock.elapsedRealtime() > deadline) {
                throw IOException("音声の再生完了を確認できませんでした。")
            }
            Thread.sleep(PLAYBACK_HEAD_POLL_MS)
        }
    }

    private fun writePcmFully(track: AudioTrack, bytes: ByteArray, playbackId: Long): Int {
        var offset = 0
        while (offset < bytes.size) {
            ensureActive(playbackId)
            val size = minOf(8_192, bytes.size - offset)
            val wrote = track.write(bytes, offset, size, AudioTrack.WRITE_BLOCKING)
            if (wrote <= 0) throw IOException("音声を再生できませんでした。")
            offset += wrote
        }
        return offset
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
        const val MODEL = GeminiTtsProtocol.MODEL
        private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions"
        private const val SAMPLE_RATE_HZ = 24_000
        private const val BYTES_PER_FRAME = 2
        private const val PLAYBACK_HEAD_POLL_MS = 20L
        private const val PLAYBACK_DRAIN_GRACE_MS = 2_000L
        private const val MAX_CACHE_BYTES = 20L * 1024L * 1024L
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun clearCache(context: Context) {
            val directory = File(context.applicationContext.filesDir, "generated_tts")
            directory.listFiles()?.forEach { file -> runCatching { file.delete() } }
            runCatching { directory.delete() }
        }
    }
}
