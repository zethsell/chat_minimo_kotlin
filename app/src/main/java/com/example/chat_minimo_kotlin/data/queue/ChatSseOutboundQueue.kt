package com.example.chat_minimo_kotlin.data.queue

import com.example.chat_minimo_kotlin.domain.model.PendingOutboundMessage
import java.util.ArrayDeque

object ChatSseOutboundQueue {

    private val deque = ArrayDeque<PendingOutboundMessage>(32)
    private val lock = Any()

    fun enqueue(m: PendingOutboundMessage) {
        synchronized(lock) { deque.addLast(m) }
    }

    fun drain(): List<PendingOutboundMessage> =
        synchronized(lock) {
            val copy = deque.toList()
            deque.clear()
            copy
        }
}
