package com.example.chat_minimo_kotlin.data.dto

import com.google.gson.annotations.SerializedName

data class HistoricoRequestBody(
    @SerializedName("codigosObjeto") val codigosObjeto: List<String>,
    @SerializedName("idCorreios") val idCorreios: String? = null,
    @SerializedName("carteiroId") val carteiroId: String? = null,
)

data class PostChatMessageBody(
    @SerializedName("msgId") val msgId: String,
    @SerializedName("sender") val sender: String,
    @SerializedName("receiver") val receiver: String,
    @SerializedName("content") val content: String,
    @SerializedName("timestampMillis") val timestampMillis: Long,
)

data class DeliveryStatusBody(
    @SerializedName("msgId") val msgId: String,
    @SerializedName("deliveryStatus") val deliveryStatus: String,
    @SerializedName("targetUserId") val targetUserId: String,
)

data class MarkAllReadBody(
    @SerializedName("action") val action: String = "markAllRead",
    @SerializedName("userId") val userId: String,
)

data class CreateSessionBody(
    @SerializedName("idCorreios") val idCorreios: String,
    @SerializedName("codigosObjeto") val codigosObjeto: List<String>,
    @SerializedName("carteiroId") val carteiroId: String? = null,
)
