package com.example.chat_minimo_kotlin.data.dto

import com.google.gson.annotations.SerializedName

data class AutenticacaoRequestDto(
    @SerializedName("usuario") val usuario: String,
    @SerializedName("senha") val senha: String,
    @SerializedName("nomeVersao") val nomeVersao: String,
    @SerializedName("numeroCompilacao") val numeroCompilacao: String,
)

data class TokenResponseDto(
    @SerializedName("token") val token: String? = null,
)
