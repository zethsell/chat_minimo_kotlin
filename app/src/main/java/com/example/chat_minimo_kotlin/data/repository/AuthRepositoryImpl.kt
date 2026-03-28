package com.example.chat_minimo_kotlin.data.repository

import com.example.chat_minimo_kotlin.data.remote.BffAuthRemoteDataSource
import com.example.chat_minimo_kotlin.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val remote: BffAuthRemoteDataSource,
) : AuthRepository {

    override suspend fun login(
        authBaseUrl: String,
        usuario: String,
        senha: String,
        nomeVersao: String,
        numeroCompilacao: String,
    ): String =
        withContext(Dispatchers.IO) {
            remote.login(authBaseUrl, usuario, senha, nomeVersao, numeroCompilacao)
        }
}
