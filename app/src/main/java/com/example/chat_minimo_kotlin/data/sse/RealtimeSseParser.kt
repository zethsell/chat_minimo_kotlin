package com.example.chat_minimo_kotlin.data.sse

import com.example.chat_minimo_kotlin.data.json.JsonIds
import com.example.chat_minimo_kotlin.data.mapper.ChatMessageMapper
import com.example.chat_minimo_kotlin.domain.realtime.ChatSessionUpdate
import com.example.chat_minimo_kotlin.domain.realtime.ChatWsProtocol
import com.example.chat_minimo_kotlin.domain.realtime.MessageDeliveryStatusEvent
import com.example.chat_minimo_kotlin.domain.realtime.ParsedRealtimeEvent
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converte cada payload JSON do SSE em [ParsedRealtimeEvent] tipado (sem `Map` na UI).
 */
@Singleton
class RealtimeSseParser @Inject constructor(
    private val gson: Gson,
    private val messageMapper: ChatMessageMapper,
) {

    fun parse(text: String, myUserId: String): ParsedRealtimeEvent? {
        if (text.isBlank()) return null
        val obj =
            runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                ?: return ParsedRealtimeEvent.Unhandled(null)
        val type: String? =
            obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString

        when {
            type.equals(ChatWsProtocol.TYPE_PONG, ignoreCase = true) ->
                return ParsedRealtimeEvent.Pong
            type.equals(ChatWsProtocol.TYPE_CHAT_UPDATE, ignoreCase = true) ->
                return parseChatUpdate(obj, myUserId)
            type.equals(ChatWsProtocol.TYPE_CHAT_STATUS_CHANGED, ignoreCase = true) ->
                return parseChatStatusChanged(obj)
            type.equals(ChatWsProtocol.TYPE_MESSAGE_STATUS, ignoreCase = true) ->
                return ParsedRealtimeEvent.MessageStatus(parseMessageStatus(obj))
        }

        if (obj.has("sender")) {
            val msg = messageMapper.fromJsonObject(obj, gson) ?: return ParsedRealtimeEvent.Unhandled(type)
            return ParsedRealtimeEvent.ThreadMessage(msg)
        }
        return ParsedRealtimeEvent.Unhandled(type)
    }

    private fun parseChatUpdate(obj: JsonObject, myUserId: String): ParsedRealtimeEvent {
        val chatId =
            JsonIds.stringId(obj.get(ChatWsProtocol.KEY_CHAT_ID)) ?: return ParsedRealtimeEvent.Unhandled(
                ChatWsProtocol.TYPE_CHAT_UPDATE,
            )
        val canonical =
            when (val c = obj.get(ChatWsProtocol.KEY_CANONICAL)) {
                null, is JsonNull -> false
                else ->
                    c.isJsonPrimitive && c.asJsonPrimitive.isBoolean && c.asBoolean
            }
        val lm = jsonLong(obj.get(ChatWsProtocol.KEY_LAST_MESSAGE_MILLIS))
        val unreadEl = obj.get(ChatWsProtocol.KEY_UNREAD_COUNT)
        val skipUnreadReplace =
            !canonical &&
                (unreadEl == null || unreadEl is JsonNull || isEmptyObject(unreadEl))
        val unreadForUser: Int? =
            if (skipUnreadReplace) {
                null
            } else {
                unreadIntForUser(unreadEl, myUserId)
            }
        val stOpt =
            obj.get(ChatWsProtocol.KEY_CHAT_STATUS)?.takeIf { it.isJsonPrimitive }?.asString
        val inbound =
            obj.get("lastInboundFromCitizen")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val lastSender =
            obj.get("lastMessageSender")?.takeIf { it.isJsonPrimitive }?.asString
        val lastMsg =
            obj.get("lastMessage")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        return ParsedRealtimeEvent.ChatUpdate(
            ChatSessionUpdate(
                chatId = chatId,
                canonical = canonical,
                lastMessageMillis = lm,
                unreadForUser = unreadForUser,
                chatStatus = stOpt,
                lastInboundFromCitizen = inbound,
                lastMessageSender = lastSender,
                lastMessage = lastMsg,
            ),
        )
    }

    private fun parseChatStatusChanged(obj: JsonObject): ParsedRealtimeEvent {
        val chatId =
            JsonIds.stringId(obj.get(ChatWsProtocol.KEY_CHAT_ID)) ?: return ParsedRealtimeEvent.Unhandled(
                ChatWsProtocol.TYPE_CHAT_STATUS_CHANGED,
            )
        val ns =
            obj.get(ChatWsProtocol.KEY_NEW_STATUS)?.takeIf { it.isJsonPrimitive }?.asString
                ?: return ParsedRealtimeEvent.Unhandled(ChatWsProtocol.TYPE_CHAT_STATUS_CHANGED)
        return ParsedRealtimeEvent.ChatStatusChanged(chatId, ns)
    }

    private fun parseMessageStatus(obj: JsonObject): MessageDeliveryStatusEvent {
        val chatId = JsonIds.stringId(obj.get("chatId")).orEmpty()
        val msgId = JsonIds.stringId(obj.get("msgId")).orEmpty()
        val ds =
            obj.get("deliveryStatus")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.uppercase().orEmpty()
        val flagR = JsonIds.deliveryTruthy(obj.get("recebida"))
        val flagV = JsonIds.deliveryTruthy(obj.get("visualizada"))
        return MessageDeliveryStatusEvent(
            chatId = chatId,
            msgId = msgId,
            deliveryStatus = ds.takeIf { it.isNotEmpty() },
            recebida = flagR,
            visualizada = flagV,
        )
    }

    private fun isEmptyObject(el: JsonElement): Boolean =
        el.isJsonObject && el.asJsonObject.size() == 0

    private fun jsonLong(el: JsonElement?, default: Long = 0L): Long {
        if (el == null || el is JsonNull) return default
        if (!el.isJsonPrimitive) return default
        val p = el.asJsonPrimitive
        return when {
            p.isNumber -> p.asLong
            p.isString -> p.asString.toLongOrNull() ?: default
            else -> default
        }
    }

    private fun unreadIntForUser(unreadEl: JsonElement?, myUserId: String): Int {
        if (unreadEl == null || unreadEl is JsonNull) return 0
        if (!unreadEl.isJsonObject) return 0
        val o = unreadEl.asJsonObject
        val e = o.get(myUserId) ?: return 0
        return when {
            e.isJsonPrimitive && e.asJsonPrimitive.isNumber -> e.asInt
            e.isJsonPrimitive && e.asJsonPrimitive.isString -> e.asString.toIntOrNull() ?: 0
            else -> 0
        }
    }
}
