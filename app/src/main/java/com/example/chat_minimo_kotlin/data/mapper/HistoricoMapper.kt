package com.example.chat_minimo_kotlin.data.mapper

import com.example.chat_minimo_kotlin.data.dto.HistoricoChatRowDto
import com.example.chat_minimo_kotlin.domain.model.ChatSummary
import com.example.chat_minimo_kotlin.domain.service.ChatListPreviewFormatter
import com.example.chat_minimo_kotlin.domain.service.ChatSummarySourceRow
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.Gson
import javax.inject.Inject

class HistoricoMapper @Inject constructor(
    private val previewFormatter: ChatListPreviewFormatter,
) {

    fun parseHistoricoBody(body: String, gson: Gson): List<HistoricoChatRowDto> {
        val trimmed = body.trimStart()
        if (trimmed.startsWith("[")) {
            val arr = runCatching { gson.fromJson(body, JsonArray::class.java) }.getOrNull()
                ?: return emptyList()
            return arr.mapNotNull { el ->
                runCatching { gson.fromJson(el, HistoricoChatRowDto::class.java) }.getOrNull()
            }
        }
        if (trimmed.startsWith("{")) {
            val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull()
                ?: return emptyList()
            val content = root.get("content") ?: return emptyList()
            if (!content.isJsonArray) return emptyList()
            return content.asJsonArray.mapNotNull { el ->
                runCatching { gson.fromJson(el, HistoricoChatRowDto::class.java) }.getOrNull()
            }
        }
        return emptyList()
    }

    fun toChatSummary(row: HistoricoChatRowDto, myUserId: String): ChatSummary {
        val chatId = (row.id ?: row.chatId)?.trim().orEmpty()
        val status = row.status?.trim().orEmpty().ifEmpty { "ABERTO" }
        val lastMillis = row.lastMessageMillis?.toLong() ?: 0L
        val source =
            ChatSummarySourceRow(
                lastInboundFromCitizen = row.lastInboundFromCitizen.orEmpty(),
                lastMessageSender = row.lastMessageSender,
                lastMessage = row.lastMessage.orEmpty(),
            )
        val lastMsg = previewFormatter.previewFromHistoricoRow(source, myUserId)
        val unread = unreadForUser(row.unreadCount, myUserId)
        val idC = row.detalhes?.idCorreios?.trim()?.takeIf { it.isNotEmpty() }
            ?: row.idCorreiosRoot?.trim()?.takeIf { it.isNotEmpty() }
        val cart = row.detalhes?.carteiroId?.trim()?.takeIf { it.isNotEmpty() }
        val peerId =
            when {
                idC != null && idC != myUserId -> idC
                cart != null && cart != myUserId -> cart
                idC != null -> idC
                cart != null -> cart
                else -> ""
            }
        val title =
            when {
                idC != null && idC != myUserId -> idC
                cart != null && cart != myUserId -> cart
                idC != null -> idC
                cart != null -> cart
                else -> chatId.take(8)
            }
        return ChatSummary(
            chatId = chatId,
            peerId = peerId,
            title = title,
            lastMessage = lastMsg,
            lastMillis = lastMillis,
            unread = unread,
            status = status,
        )
    }

    fun citizenIdCorreios(row: HistoricoChatRowDto): String? {
        row.detalhes?.idCorreios?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        row.idCorreiosRoot?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return null
    }

    private fun unreadForUser(unread: JsonObject?, myUserId: String): Int {
        if (unread == null) return 0
        val e = unread.get(myUserId) ?: return 0
        return when {
            e.isJsonPrimitive && e.asJsonPrimitive.isNumber -> e.asInt
            e.isJsonPrimitive && e.asJsonPrimitive.isString -> e.asString.toIntOrNull() ?: 0
            else -> 0
        }
    }
}
