package com.example.chat_minimo_kotlin.data.remote.api

import com.example.chat_minimo_kotlin.data.dto.ChatUpsertResponseDto
import com.example.chat_minimo_kotlin.data.dto.CreateSessionBody
import com.example.chat_minimo_kotlin.data.dto.DeliveryStatusBody
import com.example.chat_minimo_kotlin.data.dto.HistoricoRequestBody
import com.example.chat_minimo_kotlin.data.dto.MarkAllReadBody
import com.example.chat_minimo_kotlin.data.dto.PostChatMessageBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Endpoints `/chat/...` no BFF. URLs absolutas via [@Url] (base dinâmica por sessão).
 */
interface BffChatApi {

    @POST
    @Headers("Accept: application/json")
    suspend fun postHistorico(
        @Url url: String,
        @Body body: HistoricoRequestBody,
    ): Response<ResponseBody>

    @GET
    @Headers("Accept: application/json")
    suspend fun getMessages(
        @Url url: String,
    ): Response<ResponseBody>

    @POST
    @Headers("Accept: application/json")
    suspend fun postMessage(
        @Url url: String,
        @Body body: PostChatMessageBody,
    ): Response<ResponseBody>

    @POST
    @Headers("Accept: application/json")
    suspend fun postDeliveryStatus(
        @Url url: String,
        @Body body: DeliveryStatusBody,
    ): Response<ResponseBody>

    @PATCH
    @Headers("Accept: application/json")
    suspend fun markAllRead(
        @Url url: String,
        @Body body: MarkAllReadBody,
    ): Response<ResponseBody>

    @POST
    @Headers("Accept: application/json")
    suspend fun createSession(
        @Url url: String,
        @Body body: CreateSessionBody,
    ): Response<ChatUpsertResponseDto>
}
