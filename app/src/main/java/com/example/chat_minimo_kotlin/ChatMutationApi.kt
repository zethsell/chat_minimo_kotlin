package com.example.chat_minimo_kotlin

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object ChatMutationApi {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun postChatMessage(
        client: OkHttpClient,
        gson: Gson,
        baseUrl: String,
        chatId: String,
        msgId: String,
        sender: String,
        receiver: String,
        content: String,
        timestampMillis: Long,
    ): Boolean {
        val body =
            gson.toJson(
                mapOf(
                    "msgId" to msgId,
                    "sender" to sender,
                    "receiver" to receiver,
                    "content" to content,
                    "timestampMillis" to timestampMillis,
                ),
            )
        val req =
            Request.Builder()
                .url("${baseUrl.trimEnd('/')}/chat/sessoes/$chatId/messages")
                .header("Accept", "application/json")
                .post(body.toRequestBody(jsonMedia))
                .build()
        client.newCall(req).execute().use { return it.isSuccessful }
    }

    fun postDeliveryStatus(
        client: OkHttpClient,
        gson: Gson,
        baseUrl: String,
        chatId: String,
        msgId: String,
        deliveryStatus: String,
        targetUserId: String,
    ) {
        val body =
            gson.toJson(
                mapOf(
                    "msgId" to msgId,
                    "deliveryStatus" to deliveryStatus,
                    "targetUserId" to targetUserId,
                ),
            )
        val req =
            Request.Builder()
                .url("${baseUrl.trimEnd('/')}/chat/sessoes/$chatId/delivery-status")
                .header("Accept", "application/json")
                .post(body.toRequestBody(jsonMedia))
                .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                error("delivery-status HTTP ${resp.code}: ${resp.body?.string()}")
            }
        }
    }

    fun markAllRead(client: OkHttpClient, gson: Gson, baseUrl: String, chatId: String, userId: String) {
        val body = gson.toJson(
            mapOf(
                "action" to "markAllRead",
                "userId" to userId,
            ),
        )
        val base = baseUrl.trimEnd('/')
        val req = Request.Builder()
            .url("$base/chat/sessoes/$chatId")
            .header("Accept", "application/json")
            .patch(body.toRequestBody(jsonMedia))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("markAllRead HTTP ${resp.code}")
            }
        }
    }
}
