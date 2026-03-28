package com.example.chat_minimo_kotlin.data.remote

import com.example.chat_minimo_kotlin.data.dto.AutenticacaoRequestDto
import com.example.chat_minimo_kotlin.data.dto.TokenResponseDto
import com.example.chat_minimo_kotlin.data.remote.api.BffAuthApi
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.Response
import okhttp3.ResponseBody

/**
 * Login BFF via Retrofit ([BffAuthApi]); mescla `token` JSON com `Set-Cookie`.
 */
@Singleton
class BffAuthRemoteDataSource @Inject constructor(
    private val api: BffAuthApi,
    private val gson: Gson,
) {

    suspend fun login(
        authBaseUrl: String,
        usuario: String,
        senha: String,
        nomeVersao: String,
        numeroCompilacao: String,
    ): String {
        val url = "${authBaseUrl.trimEnd('/')}/v1/autenticacao"
        val resp =
            api.login(
                url,
                AutenticacaoRequestDto(
                    usuario = usuario.trim(),
                    senha = senha,
                    nomeVersao = nomeVersao,
                    numeroCompilacao = numeroCompilacao,
                ),
            )
        val raw = resp.body()?.string().orEmpty()
        if (!resp.isSuccessful) {
            throw IllegalStateException("login HTTP ${resp.code()}: ${raw.take(900)}")
        }
        val token =
            runCatching { gson.fromJson(raw, TokenResponseDto::class.java)?.token?.trim() }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException("login: resposta sem token (corpo: ${raw.take(400)})")
        return mergeSessionCookies(resp, token)
    }

    private fun mergeSessionCookies(response: Response<ResponseBody>, sessionPairFromJson: String): String {
        val parts = mutableListOf<String>()
        parts.add(sessionPairFromJson.trim())
        val names = mutableSetOf<String>()
        names.add(sessionPairFromJson.substringBefore('=', "").trim().lowercase())
        for (h in response.headers().values("Set-Cookie")) {
            val nv = h.substringBefore(';').trim()
            if (!nv.contains('=')) continue
            val name = nv.substringBefore('=', "").trim()
            val nameLc = name.lowercase()
            if (nameLc == "session") continue
            if (nameLc in names) continue
            names.add(nameLc)
            parts.add(nv)
        }
        return parts.joinToString("; ")
    }
}
