package com.example.chat_minimo_kotlin.domain.service

import com.example.chat_minimo_kotlin.domain.model.ChatMessage
import javax.inject.Inject

/**
 * Mescla mensagens locais com lote incremental (GET `since`); dedupe por identidade lógica e ordena por tempo.
 */
class MessageMergeService @Inject constructor() {

    fun merge(
        existing: List<ChatMessage>,
        incoming: List<ChatMessage>,
    ): List<ChatMessage> {
        fun rowKey(row: ChatMessage): String? {
            val k = row.msgId.ifBlank { null } ?: row.serverId?.ifBlank { null }
            return k?.takeIf { it.isNotEmpty() }
        }

        fun sameLogicalMessage(a: ChatMessage, b: ChatMessage): Boolean {
            if (a.msgId.isNotEmpty() && a.msgId == b.msgId) return true
            val ai = a.serverId?.takeIf { it.isNotEmpty() }
            val bi = b.serverId?.takeIf { it.isNotEmpty() }
            if (ai != null && ai == bi) return true
            if (a.msgId.isNotEmpty() && a.msgId == bi) return true
            if (b.msgId.isNotEmpty() && b.msgId == ai) return true
            return false
        }

        val byId = LinkedHashMap<String, ChatMessage>()
        for (e in existing) {
            val k = rowKey(e) ?: continue
            byId[k] = e
        }
        for (m in incoming) {
            val hit = byId.entries.firstOrNull { sameLogicalMessage(it.value, m) }
            if (hit == null) {
                val newKey = rowKey(m) ?: continue
                byId[newKey] = m
            } else {
                val prev = hit.value
                byId[hit.key] =
                    prev.copy(
                        recebida = prev.recebida || m.recebida,
                        visualizada = prev.visualizada || m.visualizada,
                        msgId = m.msgId.ifBlank { prev.msgId },
                        serverId = m.serverId ?: prev.serverId,
                    )
            }
        }
        return byId.values.sortedBy { it.timestampMillis }
    }

    fun maxTimestampMillis(rows: List<ChatMessage>): Long? =
        rows.map { it.timestampMillis }.maxOrNull()
}
