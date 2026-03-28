package com.example.chat_minimo_kotlin.domain.model

/**
 * Texto a enviar; o ViewModel gera [ChatMessage.msgId] e timestamps otimistas.
 */
data class OutgoingChatText(
    val chatId: String,
    val receiverId: String,
    val content: String,
)
