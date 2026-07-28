package com.byokeylab.speak

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.concurrent.ConcurrentHashMap

enum class AppSound {
    Decision,
    Select,
    Pages,
    MessageSend,
    MessageReceive
}

class AppSoundEffects(context: Context) {
    var enabled: Boolean = true

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val loaded = ConcurrentHashMap.newKeySet<Int>()
    private val activeStreams = ConcurrentHashMap<AppSound, Int>()
    private val soundIds: Map<AppSound, Int>

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loaded += sampleId
        }
        soundIds = mapOf(
            AppSound.Decision to soundPool.load(context, R.raw.decision, 1),
            AppSound.Select to soundPool.load(context, R.raw.select, 1),
            AppSound.Pages to soundPool.load(context, R.raw.pages, 1),
            AppSound.MessageSend to soundPool.load(context, R.raw.message_send, 1),
            AppSound.MessageReceive to soundPool.load(context, R.raw.message_receive, 1)
        )
    }

    fun play(sound: AppSound) {
        if (!enabled) return
        val soundId = soundIds[sound] ?: return
        if (soundId !in loaded) return
        val volume = when (sound) {
            AppSound.Decision -> 0.58f
            AppSound.Select -> 0.62f
            AppSound.Pages -> 0.95f
            AppSound.MessageSend -> 0.52f
            AppSound.MessageReceive -> 0.52f
        }
        activeStreams.remove(sound)?.let(soundPool::stop)
        val streamId = soundPool.play(soundId, volume, volume, 1, 0, 1f)
        if (streamId != 0) activeStreams[sound] = streamId
    }

    fun release() {
        soundPool.release()
        loaded.clear()
        activeStreams.clear()
    }
}

val LocalAppSoundEffects = staticCompositionLocalOf<AppSoundEffects?> { null }
