package com.example.chat_minimo_kotlin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request

object ChatHistoryApi {

    fun fetchMensagens(
        client: OkHttpClient,
        gson: Gson,
        baseUrl: String,
        chatId: String,
    ): List<Map<String, Any?>> {
        val base = baseUrl.trimEnd('/')
        val req = Request.Builder()
            .url("$base/chats/$chatId/messages")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                error("messages HTTP ${resp.code}: ${resp.body?.string()}")
            }
            val body = resp.body?.string().orEmpty()
            val listType = object : TypeToken<List<Map<String, Any?>>>() {}.type
            @Suppress("UNCHECKED_CAST")
            return gson.fromJson<Any>(body, listType) as? List<Map<String, Any?>> ?: emptyList()
        }
    }

    fun normalizeRow(m: Map<String, Any?>): Map<String, Any?> {
        val row = m.toMutableMap()
        row["recebida"] = row["recebida"] == true
        row["visualizada"] = row["visualizada"] == true
        if (row["msgId"] == null && row["id"] != null) {
            row["msgId"] = row["id"].toString()
        }
        return row
    }
}
