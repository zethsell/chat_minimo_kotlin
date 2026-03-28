package com.example.chat_minimo_kotlin.data.mapper

import com.example.chat_minimo_kotlin.data.dto.ChatMessageDto
import com.example.chat_minimo_kotlin.data.json.JsonIds
import com.example.chat_minimo_kotlin.domain.model.ChatMessage
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.Gson
import javax.inject.Inject

class ChatMessageMapper @Inject constructor() {

    fun fromDto(dto: ChatMessageDto): ChatMessage {
        val mid = JsonIds.stringId(dto.msgId) ?: JsonIds.stringId(dto.serverId).orEmpty()
        val sid = JsonIds.stringId(dto.serverId)
        val ts =
            dto.timestampMillis?.toLong()
                ?: dto.timestamp?.toLong()
                ?: 0L
        return ChatMessage(
            msgId = mid.ifEmpty { sid.orEmpty().ifEmpty { "local-${System.nanoTime()}" } },
            serverId = sid,
            chatId = dto.chatId?.trim()?.takeIf { it.isNotEmpty() },
            sender = dto.sender?.trim().orEmpty().ifEmpty { "system" },
            receiver = dto.receiver?.trim()?.takeIf { it.isNotEmpty() },
            content = dto.content.orEmpty(),
            timestampMillis = ts,
            recebida = JsonIds.deliveryTruthy(dto.recebida),
            visualizada = JsonIds.deliveryTruthy(dto.visualizada),
        )
    }

    fun parseMessagesBody(gson: Gson, body: String): List<ChatMessage> {
        if (body.isBlank()) return emptyList()
        val root =
            runCatching { com.google.gson.JsonParser.parseString(body) }.getOrNull()
                ?: return emptyList()
        val array: JsonArray =
            when {
                root.isJsonArray -> root.asJsonArray
                root.isJsonObject -> {
                    val c = root.asJsonObject.get("content")
                    if (c != null && c.isJsonArray) c.asJsonArray else return emptyList()
                }
                else -> return emptyList()
            }
        return array.mapNotNull { el ->
            runCatching {
                fromDto(gson.fromJson(el, ChatMessageDto::class.java))
            }.getOrNull()
        }
    }

    fun fromJsonObject(obj: JsonObject, gson: Gson): ChatMessage? =
        runCatching {
            fromDto(gson.fromJson(obj, ChatMessageDto::class.java))
        }.getOrNull()

    fun fromJsonElement(el: JsonElement, gson: Gson): ChatMessage? =
        if (el.isJsonObject) fromJsonObject(el.asJsonObject, gson) else null
}
