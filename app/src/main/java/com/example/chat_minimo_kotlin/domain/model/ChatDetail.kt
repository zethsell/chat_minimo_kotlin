package com.example.chat_minimo_kotlin.domain.model

data class ChatDetail(
    val chatId: String,
    val idCorreios: String,
    val nomeCliente: String,
    val nomeCarteiro: String,
    val clientAvatar: String?,
    val codigosObjetos: List<String>,
    val lastMessage: String,
    val lastMillis: Long,
    val unread: Int,
    val status: String,
)
