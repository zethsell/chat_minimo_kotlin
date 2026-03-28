package com.example.chat_minimo_kotlin.domain.repository

import com.example.chat_minimo_kotlin.domain.model.ChatMessage
import com.example.chat_minimo_kotlin.domain.model.ChatSummary

/**
 * Acesso REST ao domínio de chat no BFF (histórico, mensagens, mutações, bootstrap de sessão).
 */
interface ChatRepository {
    suspend fun fetchHistoricoChats(
        codigosObjeto: List<String>,
        myUserId: String,
        idCorreios: String? = null,
        carteiroId: String? = null,
    ): List<ChatSummary>

    suspend fun fetchMessages(
        chatId: String,
        since: Long? = null,
        size: Int? = null,
    ): List<ChatMessage>

    suspend fun postChatMessage(
        chatId: String,
        msgId: String,
        sender: String,
        receiver: String,
        content: String,
        timestampMillis: Long,
    ): Boolean

    suspend fun postDeliveryStatus(
        chatId: String,
        msgId: String,
        deliveryStatus: String,
        targetUserId: String,
    )

    suspend fun markAllRead(chatId: String, userId: String)

    suspend fun ensureChatId(
        idCorreios: String,
        codigosObjeto: List<String>,
        carteiroId: String?,
    ): String
}
