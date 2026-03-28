package com.example.chat_minimo_kotlin.data.queue

import com.example.chat_minimo_kotlin.domain.model.PendingOutboundMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatSseOutboundQueueTest {

    @Before
    fun clearQueue() {
        ChatSseOutboundQueue.drain()
    }

    @Test
    fun drain_returnsFifoOrder() {
        ChatSseOutboundQueue.enqueue(msg("1"))
        ChatSseOutboundQueue.enqueue(msg("2"))
        val batch = ChatSseOutboundQueue.drain()
        assertEquals(listOf("1", "2"), batch.map { it.msgId })
    }

    @Test
    fun drain_emptiesQueue() {
        ChatSseOutboundQueue.enqueue(msg("a"))
        assertEquals(1, ChatSseOutboundQueue.drain().size)
        assertTrue(ChatSseOutboundQueue.drain().isEmpty())
    }

    private fun msg(id: String) =
        PendingOutboundMessage(
            chatId = "c",
            msgId = id,
            sender = "s",
            receiver = "r",
            content = "x",
            timestampMillis = 0L,
        )
}
