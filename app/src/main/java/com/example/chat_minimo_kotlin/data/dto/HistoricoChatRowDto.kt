package com.example.chat_minimo_kotlin.data.dto

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class HistoricoDetalhesDto(
    @SerializedName("idCorreios") val idCorreios: String? = null,
    @SerializedName("carteiroId") val carteiroId: String? = null,
    @SerializedName("lastMessageMillis") val lastMessageMillisDet: Number? = null,
)

data class HistoricoChatRowDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("chatId") val chatId: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("lastMessageMillis") val lastMessageMillis: Number? = null,
    @SerializedName("lastInboundFromCitizen") val lastInboundFromCitizen: String? = null,
    @SerializedName("lastMessageSender") val lastMessageSender: String? = null,
    @SerializedName("lastMessage") val lastMessage: String? = null,
    @SerializedName("detalhes") val detalhes: HistoricoDetalhesDto? = null,
    @SerializedName("idCorreios") val idCorreiosRoot: String? = null,
    @SerializedName("unreadCount") val unreadCount: JsonObject? = null,
)
