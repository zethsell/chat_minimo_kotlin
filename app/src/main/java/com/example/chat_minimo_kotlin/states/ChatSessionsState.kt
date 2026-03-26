package com.example.chat_minimo_kotlin.states

import androidx.compose.runtime.mutableStateListOf

data class ChatSummaryUi(
    val chatId: String,
    /** Destinatário da mensagem ao abrir o chat (par). */
    val peerId: String,
    val title: String,
    val lastMessage: String,
    val lastMillis: Long,
    val unread: Int,
    val status: String,
)

object ChatSessionsState {
    val chats = mutableStateListOf<ChatSummaryUi>()
}
