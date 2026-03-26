package com.example.chat_minimo_kotlin

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object ChatMutationApi {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun markAllRead(client: OkHttpClient, gson: Gson, baseUrl: String, chatId: String, userId: String) {
        val body = gson.toJson(
            mapOf(
                "action" to "markAllRead",
                "userId" to userId,
            ),
        )
        val req = Request.Builder()
            .url("$baseUrl/chat/sessoes/$chatId")
            .patch(body.toRequestBody(jsonMedia))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("markAllRead HTTP ${resp.code}")
            }
        }
    }
}
