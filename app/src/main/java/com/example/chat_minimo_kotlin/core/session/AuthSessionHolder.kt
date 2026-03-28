package com.example.chat_minimo_kotlin.core.session

import com.example.chat_minimo_kotlin.ChatTokenStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sessão BFF em memória, espelhada em [ChatTokenStore] após login/logout.
 */
@Singleton
class AuthSessionHolder @Inject constructor() {

    @Volatile
    var sessionCookie: String? = ChatTokenStore.read()
        private set

    @Volatile
    var loggedUsuario: String? = ChatTokenStore.readUsuario()
        private set

    @Volatile
    var bffApiBaseUrl: String? = ChatTokenStore.readBffBaseUrl()
        private set

    fun updateSession(cookie: String?, usuario: String? = null, bffBaseUrl: String? = null) {
        val trimmed = cookie?.trim()?.takeIf { it.isNotEmpty() }
        sessionCookie = trimmed
        if (trimmed == null) {
            loggedUsuario = null
            bffApiBaseUrl = null
            ChatTokenStore.clear()
            return
        }
        if (usuario != null) {
            loggedUsuario = usuario.trim().takeIf { it.isNotEmpty() }
        }
        val base = bffBaseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
        if (base != null) {
            bffApiBaseUrl = base
        }
        ChatTokenStore.save(trimmed, loggedUsuario, bffApiBaseUrl)
    }
}
