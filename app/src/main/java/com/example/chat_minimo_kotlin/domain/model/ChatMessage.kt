package com.example.chat_minimo_kotlin.domain.model

/**
 * Mensagem na thread, após normalização do JSON do BFF ou evento tempo real.
 */
data class ChatMessage(
    val msgId: String,
    val serverId: String?,
    val chatId: String?,
    val sender: String,
    val receiver: String?,
    val content: String,
    val timestampMillis: Long,
    val recebida: Boolean,
    val visualizada: Boolean,
) {
    val isSystem: Boolean get() = sender == "system"

    companion object {
        fun systemNotice(content: String, chatId: String? = null): ChatMessage =
            ChatMessage(
                msgId = "sys-${System.nanoTime()}",
                serverId = null,
                chatId = chatId,
                sender = "system",
                receiver = null,
                content = content,
                timestampMillis = System.currentTimeMillis(),
                recebida = false,
                visualizada = false,
            )
    }
}
