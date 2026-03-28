package com.example.chat_minimo_kotlin.domain.realtime

import com.example.chat_minimo_kotlin.domain.model.ChatMessage

/**
 * Payload de [ChatWsProtocol.TYPE_CHAT_UPDATE] já normalizado para a camada de apresentação.
 */
data class ChatSessionUpdate(
    val chatId: String,
    val canonical: Boolean,
    val lastMessageMillis: Long,
    /** `null` quando a UI deve preservar o unread anterior (dedupe otimista). */
    val unreadForUser: Int?,
    val chatStatus: String?,
    val lastInboundFromCitizen: String?,
    val lastMessageSender: String?,
    val lastMessage: String?,
)

/**
 * Payload de [ChatWsProtocol.TYPE_MESSAGE_STATUS].
 */
data class MessageDeliveryStatusEvent(
    val chatId: String,
    val msgId: String,
    val deliveryStatus: String?,
    val recebida: Boolean,
    val visualizada: Boolean,
)

/**
 * Resultado do parse de uma linha `data:` do SSE.
 */
sealed class ParsedRealtimeEvent {
    data object Pong : ParsedRealtimeEvent()

    data class ChatUpdate(val update: ChatSessionUpdate) : ParsedRealtimeEvent()

    data class ChatStatusChanged(val chatId: String, val newStatus: String) : ParsedRealtimeEvent()

    data class MessageStatus(val event: MessageDeliveryStatusEvent) : ParsedRealtimeEvent()

    /**
     * Mensagem na thread (payload genérico com sender/content), já enriquecida pelo parser.
     */
    data class ThreadMessage(val message: ChatMessage) : ParsedRealtimeEvent()

    /** JSON inválido ou tipo não suportado (ignorar ou logar). */
    data class Unhandled(val rawType: String?) : ParsedRealtimeEvent()
}
