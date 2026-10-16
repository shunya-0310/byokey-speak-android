package com.byokeylab.speak

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiTtsProtocolTest {
    @Test fun requestSeparatesTranscriptAndStyleAndSelectsPcm() {
        val body = GeminiTtsProtocol.requestBody(" Hello! ", "Puck")
        assertEquals("gemini-3.8-flash-lite-tts", body.getString("model"))
        val content = body.getJSONArray("input").getJSONObject(0).getJSONArray("content").getJSONObject(0)
        assertEquals("Hello!", content.getString("text"))
        assertEquals("speech_metadata", content.getJSONArray("annotations").getJSONObject(0).getString("type"))
        assertEquals("Puck", body.getJSONObject("generation_config").getJSONArray("speech_config").getJSONObject(0).getString("voice"))
        assertEquals("audio/l16", body.getJSONObject("response_format").getString("mime_type"))
        assertEquals(24_000, body.getJSONObject("response_format").getInt("sample_rate"))
        assertTrue(body.getBoolean("stream"))
        listOf("temperature", "topP", "topK", "thinkingConfig").forEach { assertFalse(body.has(it)) }
    }

    @Test fun streamAcceptsPcmAndCompletedEvent() {
        val event = GeminiTtsProtocol.parseEvent("""{"event_type":"step.delta","delta":{"type":"audio","data":"AAEAAg==","mime_type":"audio/l16","sample_rate":24000,"channels":1}}""")
        assertEquals(4, (event as GeminiTtsProtocol.Event.Audio).bytes.size)
        assertEquals(GeminiTtsProtocol.Event.Completed,
            GeminiTtsProtocol.parseEvent("""{"event_type":"interaction.completed","interaction":{"status":"completed"}}"""))
    }

    @Test fun streamRejectsWavFailureAndCorruptCache() {
        assertThrows(IllegalStateException::class.java) {
            GeminiTtsProtocol.parseEvent("""{"event_type":"step.delta","delta":{"type":"audio","data":"UklGRg==","mime_type":"audio/wav"}}""")
        }
        assertThrows(IllegalStateException::class.java) {
            GeminiTtsProtocol.parseEvent("""{"event_type":"error","error":{"message":"private provider detail"}}""")
        }
        assertFalse(GeminiTtsProtocol.isPcm("RIFF".toByteArray()))
        assertFalse(GeminiTtsProtocol.isPcm(byteArrayOf(0)))
    }
}
