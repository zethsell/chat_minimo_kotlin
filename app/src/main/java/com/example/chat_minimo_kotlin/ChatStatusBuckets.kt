package com.example.chat_minimo_kotlin

/**
 * Duas abas: **Ativos** (ex-antigo “aberto”) vs **Histórico** (ex-antigo “fechado”).
 */
enum class ChatInboxTab {
    /** `ABERTO`, `AGUARDANDO` e qualquer status que não seja encerrado. */
    ATIVOS,

    /** `RESOLVIDO`, `ARQUIVADO`, `FECHADO` (legado). */
    HISTORICO,
}

object ChatStatusBuckets {
    fun isHistorico(status: String): Boolean {
        val s = status.uppercase()
        return s == "RESOLVIDO" || s == "ARQUIVADO" || s == "FECHADO"
    }

    fun matchesTab(status: String, tab: ChatInboxTab): Boolean {
        val h = isHistorico(status)
        return if (tab == ChatInboxTab.HISTORICO) h else !h
    }
}
