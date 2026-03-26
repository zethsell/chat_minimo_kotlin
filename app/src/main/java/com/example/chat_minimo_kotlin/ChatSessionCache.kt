package com.example.chat_minimo_kotlin

/**
 * Evita `POST /chats/historico` (e criação de chat) a cada [onCreate] no mesmo processo.
 */
object ChatSessionCache {

    @Volatile
    private var lastKey: String? = null

    @Volatile
    private var lastChatId: String? = null

    fun cacheKey(
        idCorreios: String,
        codigosObjeto: List<String>,
        carteiroId: String?,
    ): String {
        val sorted = codigosObjeto.sorted().joinToString(",")
        return "${idCorreios.trim()}|$sorted|${carteiroId?.trim().orEmpty()}"
    }

    fun getCachedChatId(key: String): String? {
        if (key == lastKey && !lastChatId.isNullOrBlank()) return lastChatId
        return null
    }

    fun remember(key: String, chatId: String) {
        if (chatId.isBlank()) return
        lastKey = key
        lastChatId = chatId
    }
}
