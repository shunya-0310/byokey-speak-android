package com.byokeylab.speak

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCryptoTest {
    @Test
    fun encryptedBackupRoundTrips() {
        val source = "会話とVocabularyを安全に保存".toByteArray()
        val encrypted = BackupCrypto.encrypt(source, "correct horse".toCharArray())
        val restored = BackupCrypto.decrypt(encrypted, "correct horse".toCharArray())

        assertArrayEquals(source, restored)
    }

    @Test
    fun wrongPassphraseCannotDecrypt() {
        val encrypted = BackupCrypto.encrypt("private".toByteArray(), "correct horse".toCharArray())

        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(encrypted, "wrong password".toCharArray())
        }
    }
}
