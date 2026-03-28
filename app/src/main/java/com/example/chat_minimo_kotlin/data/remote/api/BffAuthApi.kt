package com.example.chat_minimo_kotlin.data.remote.api

import com.example.chat_minimo_kotlin.data.dto.AutenticacaoRequestDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

/** `POST /v1/autenticacao` (URL absoluta via [@Url]). */
interface BffAuthApi {

    @POST
    @Headers(
        "Accept: application/json",
        "Content-Type: application/json; charset=utf-8",
    )
    suspend fun login(
        @Url url: String,
        @Body body: AutenticacaoRequestDto,
    ): Response<ResponseBody>
}
