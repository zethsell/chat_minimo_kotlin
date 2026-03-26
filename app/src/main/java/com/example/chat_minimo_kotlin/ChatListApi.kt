package com.example.chat_minimo_kotlin

import com.example.chat_minimo_kotlin.states.ChatSummaryUi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object ChatListApi {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /**
     * Subtítulo na **inbox do carteiro**: última mensagem **do cidadão**, não a última da thread
     * (que pode ser o próprio envio do carteiro).
     */
    fun listPreviewForCarteiro(row: Map<String, Any?>, myUserId: String): String {
        val inbound = row["lastInboundFromCitizen"]?.toString().orEmpty()
        if (inbound.isNotEmpty()) return inbound
        val lastSender = row["lastMessageSender"]?.toString()
        val last = row["lastMessage"]?.toString().orEmpty()
        return if (lastSender != null && lastSender != myUserId) last else ""
    }

    /**
     * Merge de `chatUpdate` (otimista pode não trazer `lastInboundFromCitizen` ainda).
     */
    fun listPreviewForCarteiroFromChatUpdate(
        msg: Map<String, Any?>,
        myUserId: String,
        canonical: Boolean,
        previousPreview: String,
    ): String {
        val inbound = msg["lastInboundFromCitizen"]?.toString().orEmpty()
        if (inbound.isNotEmpty()) return inbound
        val lastSender = msg["lastMessageSender"]?.toString()
        val incomingLast = msg["lastMessage"]?.toString().orEmpty()
        return when {
            lastSender != null && lastSender != myUserId -> incomingLast
            !canonical && previousPreview.isNotEmpty() -> previousPreview
            else -> ""
        }
    }

    /**
     * Lista conversas via `POST /chat/historico`.
     * Informe **carteiroId** (app carteiro / matrícula) **ou** **idCorreios** (app cidadão), nunca os dois vazios.
     * Se ambos forem enviados, a API prioriza a busca por **carteiroId**.
     */
    fun fetchHistoricoChats(
        client: OkHttpClient,
        gson: Gson,
        baseUrl: String,
        codigosObjeto: List<String>,
        myUserId: String,
        idCorreios: String? = null,
        carteiroId: String? = null,
    ): List<ChatSummaryUi> {
        val idC = idCorreios?.trim().orEmpty()
        val cart = carteiroId?.trim().orEmpty()
        require(idC.isNotEmpty() || cart.isNotEmpty()) {
            "fetchHistoricoChats: informe idCorreios ou carteiroId"
        }
        val body = mutableMapOf<String, Any>("codigosObjeto" to codigosObjeto)
        if (idC.isNotEmpty()) {
            body["idCorreios"] = idC
        }
        if (cart.isNotEmpty()) {
            body["carteiroId"] = cart
        }
        val json = gson.toJson(body)
        val req = Request.Builder()
            .url("$baseUrl/chat/historico")
            .post(json.toRequestBody(jsonMedia))
            .build()
        client.newCall(req).execute().use { resp ->
            val responseBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException(
                    "historico HTTP ${resp.code}: ${responseBody.take(900)}",
                )
            }
            val list = parseHistoricoBody(responseBody, gson)
            return list.map { row -> toSummary(row, myUserId) }
        }
    }

    /** Resposta em array JSON ou envelope paginado `{ "content": [ ... ] }`. */
    private fun parseHistoricoBody(body: String, gson: Gson): List<Map<String, Any?>> {
        val trimmed = body.trimStart()
        if (trimmed.startsWith("[")) {
            val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
            return gson.fromJson(body, type) ?: emptyList()
        }
        if (trimmed.startsWith("{")) {
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val root: Map<String, Any?> = gson.fromJson(body, mapType) ?: return emptyList()
            val contentAny = root["content"] ?: return emptyList()
            if (contentAny !is List<*>) {
                return emptyList()
            }
            return contentAny.mapNotNull { row ->
                @Suppress("UNCHECKED_CAST")
                row as? Map<String, Any?>
            }
        }
        return emptyList()
    }

    @Suppress("UNCHECKED_CAST")
    private fun toSummary(row: Map<String, Any?>, myUserId: String): ChatSummaryUi {
        val chatId = (row["id"] ?: row["chatId"])?.toString().orEmpty()
        val status = row["status"]?.toString() ?: "ABERTO"
        val lastMsg = listPreviewForCarteiro(row, myUserId)
        val lastMillis = when (val v = row["lastMessageMillis"]) {
            is Number -> v.toLong()
            else -> 0L
        }
        val unreadMap = row["unreadCount"] as? Map<*, *>
        val unread = when {
            unreadMap == null -> 0
            else -> (unreadMap[myUserId] as? Number)?.toInt() ?: 0
        }
        val det = row["detalhes"] as? Map<*, *>
        val idC = det?.get("idCorreios")?.toString()
        val cart = det?.get("carteiroId")?.toString()
        val peerId = when {
            idC != null && idC != myUserId -> idC
            cart != null && cart != myUserId -> cart
            idC != null -> idC
            cart != null -> cart
            else -> ""
        }
        val title = when {
            idC != null && idC != myUserId -> idC
            cart != null && cart != myUserId -> cart
            idC != null -> idC
            cart != null -> cart
            else -> chatId.take(8)
        }
        return ChatSummaryUi(
            chatId = chatId,
            peerId = peerId,
            title = title,
            lastMessage = lastMsg,
            lastMillis = lastMillis,
            unread = unread,
            status = status,
        )
    }
}
