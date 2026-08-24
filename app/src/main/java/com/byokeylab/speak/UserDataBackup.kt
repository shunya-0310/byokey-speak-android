package com.byokeylab.speak

import android.content.Context
import android.net.Uri
import com.byokeylab.speak.data.ChatEntity
import com.byokeylab.speak.data.ChatRepository
import com.byokeylab.speak.data.ConversationAnalysisEntity
import com.byokeylab.speak.data.DailyStatEntity
import com.byokeylab.speak.data.LearningNoteEntity
import com.byokeylab.speak.data.MessageEntity
import com.byokeylab.speak.data.UserDataSnapshot
import com.byokeylab.speak.data.VocabCardEntity
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class PortableSettings(
    val model: String,
    val dailyReminderEnabled: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int,
    val englishLevel: String,
    val coachInstructions: String,
    val theme: String,
    val voiceGender: String,
    val speechOutputProvider: String = SpeechOutputProvider.Device.name,
    val geminiTtsVoice: String = GeminiTtsVoice.Kore.name,
    val soundEffectsEnabled: Boolean
)

data class BackupResult(
    val recordCount: Int,
    val createdAt: String
)

class UserDataBackupManager(
    private val context: Context,
    private val repository: ChatRepository,
    private val storage: AppStorage
) {
    suspend fun exportTo(uri: Uri, passphrase: CharArray): BackupResult = withContext(Dispatchers.IO) {
        requirePassphrase(passphrase)
        try {
            val snapshot = repository.createBackupSnapshot().also(UserDataSnapshot::validate)
            val createdAt = Instant.now().toString()
            val plainJson = BackupJson.encode(
                snapshot = snapshot,
                settings = storage.exportPortableSettings(),
                createdAt = createdAt
            )
            val encrypted = BackupCrypto.encrypt(plainJson.toByteArray(Charsets.UTF_8), passphrase)
            context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
                it.write(encrypted)
            } ?: error("バックアップ先を開けませんでした。")
            BackupResult(snapshot.recordCount, createdAt)
        } finally {
            passphrase.fill('\u0000')
        }
    }

    suspend fun restoreFrom(uri: Uri, passphrase: CharArray): BackupResult = withContext(Dispatchers.IO) {
        requirePassphrase(passphrase)
        try {
            val encrypted = context.contentResolver.openInputStream(uri)?.use(::readLimited)
                ?: error("バックアップファイルを開けませんでした。")
            val plain = BackupCrypto.decrypt(encrypted, passphrase).toString(Charsets.UTF_8)
            val decoded = BackupJson.decode(plain)
            decoded.snapshot.validate()
            val restoredSettings = storage.validatePortableSettings(decoded.settings)
            repository.restoreBackupSnapshot(decoded.snapshot)
            storage.saveSettings(restoredSettings)
            BackupResult(decoded.snapshot.recordCount, decoded.createdAt)
        } finally {
            passphrase.fill('\u0000')
        }
    }

    private fun requirePassphrase(passphrase: CharArray) {
        require(passphrase.size >= MIN_PASSPHRASE_LENGTH) {
            "パスフレーズは${MIN_PASSPHRASE_LENGTH}文字以上で入力してください。"
        }
    }

    private fun readLimited(input: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_BACKUP_BYTES) { "バックアップファイルが大きすぎます。" }
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    companion object {
        const val MIN_PASSPHRASE_LENGTH = 8
        private const val MAX_BACKUP_BYTES = 25 * 1024 * 1024
    }
}

internal object BackupCrypto {
    private const val FORMAT_VERSION = 1
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12

    fun encrypt(plain: ByteArray, passphrase: CharArray): String {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val key = deriveKey(passphrase, salt, ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plain)
        return JSONObject()
            .put("formatVersion", FORMAT_VERSION)
            .put("cipher", "AES-256-GCM")
            .put("kdf", "PBKDF2-HMAC-SHA256")
            .put("iterations", ITERATIONS)
            .put("salt", Base64.getEncoder().encodeToString(salt))
            .put("iv", Base64.getEncoder().encodeToString(iv))
            .put("ciphertext", Base64.getEncoder().encodeToString(encrypted))
            .toString()
    }

    fun decrypt(envelope: String, passphrase: CharArray): ByteArray {
        val json = JSONObject(envelope)
        require(json.getInt("formatVersion") == FORMAT_VERSION) {
            "このバックアップ形式には対応していません。"
        }
        require(json.getString("cipher") == "AES-256-GCM") { "暗号方式が不正です。" }
        require(json.getString("kdf") == "PBKDF2-HMAC-SHA256") { "鍵導出方式が不正です。" }
        val iterations = json.getInt("iterations")
        require(iterations in 100_000..1_000_000) { "暗号化パラメータが不正です。" }
        val salt = Base64.getDecoder().decode(json.getString("salt"))
        val iv = Base64.getDecoder().decode(json.getString("iv"))
        val encrypted = Base64.getDecoder().decode(json.getString("ciphertext"))
        require(salt.size == SALT_BYTES && iv.size == IV_BYTES) { "暗号化データが破損しています。" }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            deriveKey(passphrase, salt, iterations),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        return runCatching { cipher.doFinal(encrypted) }
            .getOrElse { throw IllegalArgumentException("パスフレーズが違うか、ファイルが破損しています。") }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
            SecretKeySpec(encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}

private data class DecodedBackup(
    val snapshot: UserDataSnapshot,
    val settings: PortableSettings,
    val createdAt: String
)

private object BackupJson {
    private const val CONTENT_VERSION = 1

    fun encode(snapshot: UserDataSnapshot, settings: PortableSettings, createdAt: String): String {
        return JSONObject()
            .put("contentVersion", CONTENT_VERSION)
            .put("createdAt", createdAt)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("settings", settings.toJson())
            .put("chats", snapshot.chats.toJsonArray { it.toJson() })
            .put("messages", snapshot.messages.toJsonArray { it.toJson() })
            .put("notes", snapshot.notes.toJsonArray { it.toJson() })
            .put("vocabCards", snapshot.vocabCards.toJsonArray { it.toJson() })
            .put("dailyStats", snapshot.dailyStats.toJsonArray { it.toJson() })
            .put("analyses", snapshot.analyses.toJsonArray { it.toJson() })
            .toString()
    }

    fun decode(raw: String): DecodedBackup {
        val root = JSONObject(raw)
        require(root.getInt("contentVersion") == CONTENT_VERSION) {
            "このバックアップの内容形式には対応していません。"
        }
        val createdAt = root.getString("createdAt")
        Instant.parse(createdAt)
        return DecodedBackup(
            snapshot = UserDataSnapshot(
                chats = root.getJSONArray("chats").mapObjects { it.toChat() },
                messages = root.getJSONArray("messages").mapObjects { it.toMessage() },
                notes = root.getJSONArray("notes").mapObjects { it.toNote() },
                vocabCards = root.getJSONArray("vocabCards").mapObjects { it.toVocabCard() },
                dailyStats = root.getJSONArray("dailyStats").mapObjects { it.toDailyStat() },
                analyses = root.getJSONArray("analyses").mapObjects { it.toAnalysis() }
            ),
            settings = root.getJSONObject("settings").toPortableSettings(),
            createdAt = createdAt
        )
    }
}

private fun PortableSettings.toJson() = JSONObject()
    .put("model", model)
    .put("dailyReminderEnabled", dailyReminderEnabled)
    .put("reminderHour", reminderHour)
    .put("reminderMinute", reminderMinute)
    .put("englishLevel", englishLevel)
    .put("coachInstructions", coachInstructions)
    .put("theme", theme)
    .put("voiceGender", voiceGender)
    .put("speechOutputProvider", speechOutputProvider)
    .put("geminiTtsVoice", geminiTtsVoice)
    .put("soundEffectsEnabled", soundEffectsEnabled)

private fun JSONObject.toPortableSettings() = PortableSettings(
    model = getString("model"),
    dailyReminderEnabled = getBoolean("dailyReminderEnabled"),
    reminderHour = getInt("reminderHour"),
    reminderMinute = getInt("reminderMinute"),
    englishLevel = getString("englishLevel"),
    coachInstructions = getString("coachInstructions"),
    theme = getString("theme"),
    voiceGender = getString("voiceGender"),
    speechOutputProvider = optString("speechOutputProvider", SpeechOutputProvider.Device.name),
    geminiTtsVoice = optString("geminiTtsVoice", GeminiTtsVoice.Kore.name),
    soundEffectsEnabled = getBoolean("soundEffectsEnabled")
)

private fun ChatEntity.toJson() = JSONObject()
    .put("id", id).put("title", title).put("mode", mode).put("origin", origin)
    .put("pinned", pinned).put("createdAt", createdAt).put("updatedAt", updatedAt)

private fun JSONObject.toChat() = ChatEntity(
    id = getLong("id"), title = getString("title"), mode = getString("mode"),
    origin = getString("origin"), pinned = getBoolean("pinned"),
    createdAt = getLong("createdAt"), updatedAt = getLong("updatedAt")
)

private fun MessageEntity.toJson() = JSONObject()
    .put("id", id).put("chatId", chatId).put("role", role).put("text", text)
    .put("inputSource", inputSource).put("usedQuickAssist", usedQuickAssist).put("createdAt", createdAt)

private fun JSONObject.toMessage() = MessageEntity(
    id = getLong("id"), chatId = getLong("chatId"), role = getString("role"),
    text = getString("text"), inputSource = getString("inputSource"),
    usedQuickAssist = getBoolean("usedQuickAssist"), createdAt = getLong("createdAt")
)

private fun LearningNoteEntity.toJson() = JSONObject()
    .put("id", id).put("chatId", chatId ?: JSONObject.NULL).put("sourceMessage", sourceMessage)
    .put("coachNotes", coachNotes).put("betterOptions", betterOptions)
    .put("japaneseNote", japaneseNote).put("reviewed", reviewed).put("createdAt", createdAt)

private fun JSONObject.toNote() = LearningNoteEntity(
    id = getLong("id"), chatId = nullableLong("chatId"), sourceMessage = getString("sourceMessage"),
    coachNotes = getString("coachNotes"), betterOptions = getString("betterOptions"),
    japaneseNote = getString("japaneseNote"), reviewed = getBoolean("reviewed"),
    createdAt = getLong("createdAt")
)

private fun VocabCardEntity.toJson() = JSONObject()
    .put("id", id).put("expression", expression).put("meaning", meaning).put("source", source)
    .put("chatId", chatId ?: JSONObject.NULL).put("favorite", favorite).put("createdAt", createdAt)

private fun JSONObject.toVocabCard() = VocabCardEntity(
    id = getLong("id"), expression = getString("expression"), meaning = getString("meaning"),
    source = getString("source"), chatId = nullableLong("chatId"),
    favorite = getBoolean("favorite"), createdAt = getLong("createdAt")
)

private fun DailyStatEntity.toJson() = JSONObject()
    .put("epochDay", epochDay).put("turns", turns).put("assistUses", assistUses)
    .put("notesSaved", notesSaved).put("reviewsDone", reviewsDone)

private fun JSONObject.toDailyStat() = DailyStatEntity(
    epochDay = getLong("epochDay"), turns = getInt("turns"), assistUses = getInt("assistUses"),
    notesSaved = getInt("notesSaved"), reviewsDone = getInt("reviewsDone")
)

private fun ConversationAnalysisEntity.toJson() = JSONObject()
    .put("id", id).put("createdAt", createdAt).put("periodStart", periodStart)
    .put("periodEnd", periodEnd).put("userMessageCount", userMessageCount)
    .put("provider", provider).put("model", model).put("resultJson", resultJson)

private fun JSONObject.toAnalysis() = ConversationAnalysisEntity(
    id = getLong("id"), createdAt = getLong("createdAt"), periodStart = getLong("periodStart"),
    periodEnd = getLong("periodEnd"), userMessageCount = getInt("userMessageCount"),
    provider = getString("provider"), model = getString("model"), resultJson = getString("resultJson")
)

private fun JSONObject.nullableLong(name: String): Long? =
    if (isNull(name)) null else getLong(name)

private fun <T> List<T>.toJsonArray(transform: (T) -> JSONObject): JSONArray =
    JSONArray().also { array -> forEach { array.put(transform(it)) } }

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    buildList { for (index in 0 until length()) add(transform(getJSONObject(index))) }
