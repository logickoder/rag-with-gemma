package dev.logickoder.ragwithgemma.data.model

import java.util.UUID

enum class ChatRole { USER, ASSISTANT }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val content: String,
    val timestampMs: Long = System.currentTimeMillis(),
)
