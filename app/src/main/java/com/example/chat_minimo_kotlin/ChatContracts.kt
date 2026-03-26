package com.example.chat_minimo_kotlin

/**
 * Contratos WS v2 (tipos e chaves alinhados à message-server-api / message-server).
 */
object ChatWs {
    const val TYPE_CHAT_UPDATE = "chatUpdate"
    const val TYPE_CHAT_STATUS_CHANGED = "chatStatusChanged"
    const val TYPE_MESSAGE_STATUS = "messageStatus"
    const val TYPE_PONG = "PONG"

    const val KEY_CANONICAL = "canonical"
    const val KEY_CHAT_ID = "chatId"
    const val KEY_LAST_MESSAGE_MILLIS = "lastMessageMillis"
    const val KEY_UNREAD_COUNT = "unreadCount"
    const val KEY_CHAT_STATUS = "chatStatus"
    const val KEY_NEW_STATUS = "newStatus"
}
