package com.example.chat_minimo_kotlin.domain.repository

/**
 * Login BFF (`POST /v1/autenticacao`); retorna o valor do header `Cookie` a usar no REST/SSE.
 */
interface AuthRepository {
    suspend fun login(
        authBaseUrl: String,
        usuario: String,
        senha: String,
        nomeVersao: String,
        numeroCompilacao: String,
    ): String
}
