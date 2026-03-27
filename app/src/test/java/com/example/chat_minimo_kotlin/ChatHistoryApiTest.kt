package com.example.chat_minimo_kotlin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatHistoryApiTest {

    @Test
    fun normalizeRow_derivesMsgIdAndTimestampMillis() {
        val out = ChatHistoryApi.normalizeRow(
            mapOf(
                "id" to "x1",
                "timestamp" to 42L,
            ),
        )
        assertEquals("x1", out["msgId"])
        assertEquals(42L, out["timestampMillis"])
        assertEquals(false, out["recebida"])
        assertEquals(false, out["visualizada"])
    }

    @Test
    fun maxTimestampMillis_empty_returnsNull() {
        assertNull(ChatHistoryApi.maxTimestampMillis(emptyList()))
    }

    @Test
    fun mergeCatchUp_addsNew_dedupesByMsgId_sortsByTime() {
        val existing =
            listOf(
                mapOf("msgId" to "a", "timestampMillis" to 10L),
            )
        val incoming =
            listOf(
                mapOf("msgId" to "a", "timestampMillis" to 99L),
                mapOf("msgId" to "b", "timestampMillis" to 5L),
            ).map { ChatHistoryApi.normalizeRow(it) }
        val merged = ChatHistoryApi.mergeCatchUp(existing, incoming)
        assertEquals(listOf("b", "a"), merged.map { it["msgId"] })
        assertEquals(10L, merged.find { it["msgId"] == "a" }!!["timestampMillis"])
    }

    @Test
    fun mergeCatchUp_emptyExisting_returnsIncomingSorted() {
        val incoming =
            listOf(
                mapOf("msgId" to "z", "timestampMillis" to 20L),
                mapOf("msgId" to "y", "timestampMillis" to 10L),
            ).map { ChatHistoryApi.normalizeRow(it) }
        val merged = ChatHistoryApi.mergeCatchUp(emptyList(), incoming)
        assertEquals(listOf("y", "z"), merged.map { it["msgId"] })
    }

    @Test
    fun mergeCatchUp_duplicateIncoming_sameBatch_keepsFirstTimestamp() {
        val incoming =
            listOf(
                mapOf("msgId" to "x", "timestampMillis" to 1L),
                mapOf("msgId" to "x", "timestampMillis" to 999L),
            ).map { ChatHistoryApi.normalizeRow(it) }
        val merged = ChatHistoryApi.mergeCatchUp(emptyList(), incoming)
        assertEquals(1, merged.size)
        assertEquals(1L, merged[0]["timestampMillis"])
    }
}
