package com.example.chat_minimo_kotlin.data.dto

import com.google.gson.annotations.SerializedName

data class ChatMessageDto(
    @SerializedName("msgId") val msgId: String? = null,
    @SerializedName("id") val serverId: String? = null,
    @SerializedName("chatId") val chatId: String? = null,
    @SerializedName("sender") val sender: String? = null,
    @SerializedName("receiver") val receiver: String? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("timestampMillis") val timestampMillis: Number? = null,
    @SerializedName("timestamp") val timestamp: Number? = null,
    @SerializedName("recebida") val recebida: Any? = null,
    @SerializedName("visualizada") val visualizada: Any? = null,
)
