package com.example.chat_minimo_kotlin.domain.model

/**
 * Envio pendente quando o POST REST falha (reenfileirado após reconexão SSE).
 */
data class PendingOutboundMessage(
    val chatId: String,
    val msgId: String,
    val sender: String,
    val receiver: String,
    val content: String,
    val timestampMillis: Long,
)
