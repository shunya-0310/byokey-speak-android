package com.byokeylab.speak

import android.content.Context
import android.media.MediaPlayer

/** 同梱済み固定見本の再生。Gemini APIや利用者のキーは使用しない。 */
object GeminiVoicePreviewPlayer {
    private var player: MediaPlayer? = null

    fun play(context: Context, voice: GeminiTtsVoice) {
        stop()
        val resourceId = previewResources[voice] ?: return
        player = MediaPlayer.create(context.applicationContext, resourceId)?.apply {
            setOnCompletionListener { stop() }
            start()
        }
    }

    fun stop() {
        val current = player ?: return
        player = null
        runCatching { current.stop() }
        current.release()
    }

    // 動的getIdentifierではreleaseのresource shrinkerが見本を未使用と誤認するため、
    // 全ファイルを静的参照する。見本はすべて同一の固定文で、通信・APIキーを使わない。
    private val previewResources = mapOf(
        GeminiTtsVoice.Zephyr to R.raw.voice_zephyr,
        GeminiTtsVoice.Puck to R.raw.voice_puck,
        GeminiTtsVoice.Charon to R.raw.voice_charon,
        GeminiTtsVoice.Kore to R.raw.voice_kore,
        GeminiTtsVoice.Fenrir to R.raw.voice_fenrir,
        GeminiTtsVoice.Leda to R.raw.voice_leda,
        GeminiTtsVoice.Orus to R.raw.voice_orus,
        GeminiTtsVoice.Aoede to R.raw.voice_aoede,
        GeminiTtsVoice.Callirrhoe to R.raw.voice_callirrhoe,
        GeminiTtsVoice.Autonoe to R.raw.voice_autonoe,
        GeminiTtsVoice.Enceladus to R.raw.voice_enceladus,
        GeminiTtsVoice.Iapetus to R.raw.voice_iapetus,
        GeminiTtsVoice.Umbriel to R.raw.voice_umbriel,
        GeminiTtsVoice.Algieba to R.raw.voice_algieba,
        GeminiTtsVoice.Despina to R.raw.voice_despina,
        GeminiTtsVoice.Erinome to R.raw.voice_erinome,
        GeminiTtsVoice.Algenib to R.raw.voice_algenib,
        GeminiTtsVoice.Rasalgethi to R.raw.voice_rasalgethi,
        GeminiTtsVoice.Laomedeia to R.raw.voice_laomedeia,
        GeminiTtsVoice.Achernar to R.raw.voice_achernar,
        GeminiTtsVoice.Alnilam to R.raw.voice_alnilam,
        GeminiTtsVoice.Schedar to R.raw.voice_schedar,
        GeminiTtsVoice.Gacrux to R.raw.voice_gacrux,
        GeminiTtsVoice.Pulcherrima to R.raw.voice_pulcherrima,
        GeminiTtsVoice.Achird to R.raw.voice_achird,
        GeminiTtsVoice.Zubenelgenubi to R.raw.voice_zubenelgenubi,
        GeminiTtsVoice.Vindemiatrix to R.raw.voice_vindemiatrix,
        GeminiTtsVoice.Sadachbia to R.raw.voice_sadachbia,
        GeminiTtsVoice.Sadaltager to R.raw.voice_sadaltager,
        GeminiTtsVoice.Sulafat to R.raw.voice_sulafat
    )
}
