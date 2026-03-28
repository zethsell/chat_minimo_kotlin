package com.example.chat_minimo_kotlin.domain.service

import com.example.chat_minimo_kotlin.domain.realtime.ChatSessionUpdate
import javax.inject.Inject

/**
 * Pré-visualização da última linha na inbox do **carteiro** (prioriza texto do cidadão).
 */
class ChatListPreviewFormatter @Inject constructor() {

    fun previewFromHistoricoRow(row: ChatSummarySourceRow, myUserId: String): String {
        if (row.lastInboundFromCitizen.isNotEmpty()) return row.lastInboundFromCitizen
        val lastSender = row.lastMessageSender
        val last = row.lastMessage
        return if (lastSender != null && lastSender != myUserId) last else ""
    }

    fun previewAfterChatUpdate(
        update: ChatSessionUpdate,
        myUserId: String,
        previousPreview: String,
    ): String {
        update.lastInboundFromCitizen?.takeIf { it.isNotEmpty() }?.let { return it }
        val lastSender = update.lastMessageSender
        val incomingLast = update.lastMessage.orEmpty()
        return when {
            lastSender != null && lastSender != myUserId -> incomingLast
            !update.canonical && previousPreview.isNotEmpty() -> previousPreview
            else -> ""
        }
    }
}

/**
 * Campos necessários para o subtítulo da lista, vindos do DTO de histórico (não expor JSON cru).
 */
data class ChatSummarySourceRow(
    val lastInboundFromCitizen: String,
    val lastMessageSender: String?,
    val lastMessage: String,
)
