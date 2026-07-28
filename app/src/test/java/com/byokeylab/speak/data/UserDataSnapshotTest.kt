package com.byokeylab.speak.data

import org.junit.Assert.assertThrows
import org.junit.Test

class UserDataSnapshotTest {
    @Test
    fun messageMustReferenceExistingChat() {
        val snapshot = UserDataSnapshot(
            chats = emptyList(),
            messages = listOf(
                MessageEntity(
                    id = 1,
                    chatId = 99,
                    role = "User",
                    text = "Hello",
                    createdAt = 1
                )
            ),
            notes = emptyList(),
            vocabCards = emptyList(),
            dailyStats = emptyList(),
            analyses = emptyList()
        )

        assertThrows(IllegalArgumentException::class.java) {
            snapshot.validate()
        }
    }

    @Test
    fun validSnapshotPassesValidation() {
        val snapshot = UserDataSnapshot(
            chats = listOf(
                ChatEntity(
                    id = 1,
                    title = "Chat",
                    mode = "Quick",
                    createdAt = 1,
                    updatedAt = 1
                )
            ),
            messages = listOf(
                MessageEntity(
                    id = 2,
                    chatId = 1,
                    role = "User",
                    text = "Hello",
                    createdAt = 2
                )
            ),
            notes = emptyList(),
            vocabCards = emptyList(),
            dailyStats = emptyList(),
            analyses = emptyList()
        )

        snapshot.validate()
    }
}
