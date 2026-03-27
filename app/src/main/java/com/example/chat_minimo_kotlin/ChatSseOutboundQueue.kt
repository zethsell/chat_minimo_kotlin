package com.example.chat_minimo_kotlin

import java.util.ArrayDeque

/** Mensagens de texto pendentes quando o POST REST falha (modo SSE). */
data class PendingChatTextMessage(
    val chatId: String,
    val msgId: String,
    val sender: String,
    val receiver: String,
    val content: String,
    val timestampMillis: Long,
)

object ChatSseOutboundQueue {

    private val deque = ArrayDeque<PendingChatTextMessage>(32)
    private val lock = Any()

    fun enqueue(m: PendingChatTextMessage) {
        synchronized(lock) { deque.addLast(m) }
    }

    /** Remove todos para tentativa de envio; em falha parcial, reenfileire os não enviados. */
    fun drain(): List<PendingChatTextMessage> =
        synchronized(lock) {
            val copy = deque.toList()
            deque.clear()
            copy
        }
}
