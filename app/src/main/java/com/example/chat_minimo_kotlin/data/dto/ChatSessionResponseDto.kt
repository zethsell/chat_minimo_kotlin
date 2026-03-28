package com.example.chat_minimo_kotlin.data.dto

import com.google.gson.annotations.SerializedName

data class ChatUpsertResponseDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("chatId") val chatId: String? = null,
)
