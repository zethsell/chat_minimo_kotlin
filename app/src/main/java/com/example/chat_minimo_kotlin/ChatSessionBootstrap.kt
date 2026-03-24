package com.example.chat_minimo_kotlin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object ChatSessionBootstrap {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /**
     * Resolve sessão em `POST /chats/historico` ou cria com `POST /chats`.
     * [idCorreios] = identificação do cidadão na sessão; [carteiroId] opcional (matrícula).
     */
    fun ensureChatId(
        client: OkHttpClient,
        gson: Gson,
        baseUrl: String,
        idCorreios: String,
        codigosObjeto: List<String>,
        carteiroId: String?,
    ): String {
        val base = baseUrl.trimEnd('/')
        val historicoJson = gson.toJson(
            mapOf(
                "idCorreios" to idCorreios,
                "codigosObjeto" to codigosObjeto,
            ),
        )
        val histRequest = Request.Builder()
            .url("$base/chats/historico")
            .post(historicoJson.toRequestBody(jsonMedia))
            .build()
        client.newCall(histRequest).execute().use { resp ->
            if (!resp.isSuccessful) {
                error("historico HTTP ${resp.code}: ${resp.body?.string()}")
            }
            val body = resp.body?.string().orEmpty()
            val listType = object : TypeToken<List<Map<String, Any?>>>() {}.type
            val arr: List<Map<String, Any?>> = gson.fromJson(body, listType) ?: emptyList()
            val firstId = arr.firstOrNull()?.get("id")?.toString()
            if (firstId != null) return firstId
        }

        val createPayload = mutableMapOf<String, Any?>(
            "idCorreios" to idCorreios,
            "codigosObjeto" to codigosObjeto,
        )
        if (!carteiroId.isNullOrBlank()) {
            createPayload["carteiroId"] = carteiroId.trim()
        }
        val createRequest = Request.Builder()
            .url("$base/chats")
            .post(gson.toJson(createPayload).toRequestBody(jsonMedia))
            .build()
        client.newCall(createRequest).execute().use { resp ->
            if (!resp.isSuccessful) {
                error("create chat HTTP ${resp.code}: ${resp.body?.string()}")
            }
            val body = resp.body?.string().orEmpty()
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val map: Map<String, Any?> = gson.fromJson(body, mapType)
                ?: error("POST /chats: corpo vazio")
            return map["id"]?.toString() ?: error("POST /chats sem id")
        }
    }
}
