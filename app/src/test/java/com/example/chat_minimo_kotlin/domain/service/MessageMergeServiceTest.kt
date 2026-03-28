package com.example.chat_minimo_kotlin.domain.service

import com.example.chat_minimo_kotlin.domain.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageMergeServiceTest {

    private val service = MessageMergeService()

    private fun row(
        msgId: String,
        ts: Long,
        serverId: String? = null,
    ): ChatMessage =
        ChatMessage(
            msgId = msgId,
            serverId = serverId,
            chatId = null,
            sender = "u",
            receiver = null,
            content = "",
            timestampMillis = ts,
            recebida = false,
            visualizada = false,
        )

    @Test
    fun maxTimestampMillis_empty_returnsNull() {
        assertNull(service.maxTimestampMillis(emptyList()))
    }

    @Test
    fun merge_addsNew_dedupesByMsgId_sortsByTime() {
        val existing = listOf(row("a", 10L))
        val incoming = listOf(row("a", 99L), row("b", 5L))
        val merged = service.merge(existing, incoming)
        assertEquals(listOf("b", "a"), merged.map { it.msgId })
        assertEquals(10L, merged.find { it.msgId == "a" }!!.timestampMillis)
    }

    @Test
    fun merge_emptyExisting_returnsIncomingSorted() {
        val incoming = listOf(row("z", 20L), row("y", 10L))
        val merged = service.merge(emptyList(), incoming)
        assertEquals(listOf("y", "z"), merged.map { it.msgId })
    }

    @Test
    fun merge_duplicateIncoming_sameBatch_keepsFirstTimestamp() {
        val incoming = listOf(row("x", 1L), row("x", 999L))
        val merged = service.merge(emptyList(), incoming)
        assertEquals(1, merged.size)
        assertEquals(1L, merged[0].timestampMillis)
    }
}
