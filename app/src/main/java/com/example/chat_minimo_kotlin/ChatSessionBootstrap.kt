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
     * Uso **carteiro** (após LOEC + idCorreios resolvido): `POST /chat/historico` e reutiliza só sessão **ativa**
     * (não `RESOLVIDO` / `ARQUIVADO` / `FECHADO`); se não houver, `POST /chat/sessoes`.
     * O app cidadão não deve depender deste fluxo para abrir atendimento.
     */
    fun ensureChatId(
        client: OkHttpClient,
        gson: Gson,
        baseUrl: String,
        idCorreios: String,
        codigosObjeto: List<String>,
        carteiroId: String?,
        historicoPorCarteiro: Boolean = false,
    ): String {
        val base = baseUrl.trimEnd('/')
        val historicoBody = mutableMapOf<String, Any>("codigosObjeto" to codigosObjeto)
        if (historicoPorCarteiro && !carteiroId.isNullOrBlank()) {
            historicoBody["carteiroId"] = carteiroId.trim()
        } else {
            historicoBody["idCorreios"] = idCorreios
        }
        val historicoJson = gson.toJson(historicoBody)
        val histRequest = Request.Builder()
            .url("$base/chat/historico")
            .post(historicoJson.toRequestBody(jsonMedia))
            .build()
        client.newCall(histRequest).execute().use { resp ->
            if (!resp.isSuccessful) {
                error("historico HTTP ${resp.code}: ${resp.body?.string()}")
            }
            val body = resp.body?.string().orEmpty()
            val openId = parseHistoricoFirstOpenChatId(body, gson)
            if (openId != null) return openId
        }

        val createPayload = mutableMapOf<String, Any?>(
            "idCorreios" to idCorreios,
            "codigosObjeto" to codigosObjeto,
        )
        if (!carteiroId.isNullOrBlank()) {
            createPayload["carteiroId"] = carteiroId.trim()
        }
        val createRequest = Request.Builder()
            .url("$base/chat/sessoes")
            .post(gson.toJson(createPayload).toRequestBody(jsonMedia))
            .build()
        client.newCall(createRequest).execute().use { resp ->
            if (!resp.isSuccessful) {
                error("create chat HTTP ${resp.code}: ${resp.body?.string()}")
            }
            val body = resp.body?.string().orEmpty()
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val map: Map<String, Any?> = gson.fromJson(body, mapType)
                ?: error("POST /chat/sessoes: corpo vazio")
            return map["id"]?.toString() ?: error("POST /chat/sessoes sem id")
        }
    }

    /**
     * Primeiro chat **ainda iterável** (aba Ativos): exclui `RESOLVIDO`, `ARQUIVADO`, `FECHADO`.
     * Se só existir histórico encerrado, retorna `null` e o fluxo cria sessão nova.
     */
    private fun parseHistoricoFirstOpenChatId(body: String, gson: Gson): String? {
        for (m in parseHistoricoRows(body, gson)) {
            if (!isOpenForNovaConversa(m)) continue
            val id = m["id"]?.toString()?.trim().orEmpty()
            if (id.isNotEmpty()) return id
        }
        return null
    }

    private fun parseHistoricoRows(body: String, gson: Gson): List<Map<String, Any?>> {
        val trimmed = body.trimStart()
        if (trimmed.startsWith("[")) {
            val listType = object : TypeToken<List<Map<String, Any?>>>() {}.type
            return gson.fromJson(body, listType) ?: emptyList()
        }
        if (trimmed.startsWith("{")) {
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val root: Map<String, Any?> = gson.fromJson(body, mapType) ?: return emptyList()
            val contentAny = root["content"] ?: return emptyList()
            if (contentAny !is List<*>) return emptyList()
            @Suppress("UNCHECKED_CAST")
            return contentAny.mapNotNull { it as? Map<String, Any?> }
        }
        return emptyList()
    }

    private fun isOpenForNovaConversa(row: Map<String, Any?>): Boolean {
        val st = row["status"]?.toString()?.trim().orEmpty()
        if (st.isEmpty()) return true
        return !ChatStatusBuckets.isHistorico(st)
    }
}
