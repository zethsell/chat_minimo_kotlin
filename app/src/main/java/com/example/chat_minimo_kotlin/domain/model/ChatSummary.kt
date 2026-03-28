package com.example.chat_minimo_kotlin.domain.model

/**
 * Resumo de sessão na inbox (carteiro), após mapeamento do histórico BFF.
 */
data class ChatSummary(
    val chatId: String,
    val peerId: String,
    val title: String,
    val lastMessage: String,
    val lastMillis: Long,
    val unread: Int,
    val status: String,
)
