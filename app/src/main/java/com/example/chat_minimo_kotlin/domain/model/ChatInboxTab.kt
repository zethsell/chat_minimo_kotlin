package com.example.chat_minimo_kotlin.domain.model

/**
 * Abas da inbox (Ativos vs histórico encerrado).
 */
enum class ChatInboxTab {
    ATIVOS,
    HISTORICO,
}

/**
 * Classificação de `status` da sessão para filtro de abas.
 */
object ChatStatusBuckets {
    fun isHistorico(status: String): Boolean {
        val s = status.trim().uppercase()
        return s == "RESOLVIDO" || s == "ARQUIVADO" || s == "FECHADO"
    }

    fun matchesTab(status: String, tab: ChatInboxTab): Boolean {
        val h = isHistorico(status)
        return if (tab == ChatInboxTab.HISTORICO) h else !h
    }
}
