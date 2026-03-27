package com.example.chat_minimo_kotlin

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request

object ChatHistoryApi {

    /**
     * Paridade com Flutter [ChatHistoryApi.fetchMensagens]: `Accept: application/json` e array JSON
     * no message-server-api; corpo raiz lista ou envelope `{ "content": [ ... ] }`.
     */
    fun fetchMensagens(
        client: OkHttpClient,
        gson: Gson,
        baseUrl: String,
        chatId: String,
        since: Long? = null,
        size: Int? = null,
    ): List<Map<String, Any?>> {
        val base = baseUrl.trimEnd('/')
        val url = buildString {
            append(base).append("/chat/sessoes/").append(chatId).append("/messages")
            val q = mutableListOf<String>()
            if (since != null) q.add("since=$since")
            if (size != null) q.add("size=$size")
            if (q.isNotEmpty()) append("?").append(q.joinToString("&"))
        }
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                error("messages HTTP ${resp.code}: ${resp.body?.string()}")
            }
            val body = resp.body?.string().orEmpty()
            return parseMessagesBody(gson, body)
        }
    }

    private fun parseMessagesBody(gson: Gson, body: String): List<Map<String, Any?>> {
        if (body.isBlank()) {
            return emptyList()
        }
        val listType = object : TypeToken<List<Map<String, Any?>>>() {}.type
        val root = runCatching { JsonParser.parseString(body) }.getOrElse { return emptyList() }
        val array =
            when {
                root.isJsonArray -> root.asJsonArray
                root.isJsonObject -> {
                    val c = root.asJsonObject.get("content")
                    if (c != null && c.isJsonArray) c.asJsonArray else return emptyList()
                }
                else -> return emptyList()
            }
        @Suppress("UNCHECKED_CAST")
        return gson.fromJson<Any>(array, listType) as? List<Map<String, Any?>> ?: emptyList()
    }

    fun normalizeRow(m: Map<String, Any?>): Map<String, Any?> {
        val row = m.toMutableMap()
        row["recebida"] = ChatJson.deliveryTruthy(row["recebida"])
        row["visualizada"] = ChatJson.deliveryTruthy(row["visualizada"])
        val mid = ChatJson.stringId(row["msgId"])
        val oid = ChatJson.stringId(row["id"])
        if (mid != null) {
            row["msgId"] = mid
        } else if (oid != null) {
            row["msgId"] = oid
        }
        if (oid != null) {
            row["id"] = oid
        }
        if (row["timestampMillis"] == null && row["timestamp"] is Number) {
            row["timestampMillis"] = (row["timestamp"] as Number).toLong()
        }
        return row
    }

    fun rowTimestampMillis(row: Map<String, Any?>): Long? {
        (row["timestampMillis"] as? Number)?.toLong()?.let { return it }
        return (row["timestamp"] as? Number)?.toLong()
    }

    fun maxTimestampMillis(rows: List<Map<String, Any?>>): Long? =
        rows.mapNotNull { rowTimestampMillis(it) }.maxOrNull()

    /**
     * Mescla mensagens já exibidas com lote incremental (`since`); [incomingNormalized] deve vir de [normalizeRow].
     * Ordena por timestamp; dedupe por `msgId` ou `id`.
     */
    fun mergeCatchUp(
        existing: List<Map<String, Any?>>,
        incomingNormalized: List<Map<String, Any?>>,
    ): List<Map<String, Any?>> {
        fun rowKey(row: Map<String, Any?>): String? {
            val k = ChatJson.stringId(row["msgId"]) ?: ChatJson.stringId(row["id"])
            return k?.takeIf { it.isNotEmpty() }
        }

        fun sameLogicalMessage(a: Map<String, Any?>, b: Map<String, Any?>): Boolean {
            val am = ChatJson.stringId(a["msgId"])
            val bm = ChatJson.stringId(b["msgId"])
            val ai = ChatJson.stringId(a["id"])
            val bi = ChatJson.stringId(b["id"])
            if (am != null && am == bm) return true
            if (ai != null && ai == bi) return true
            if (am != null && am == bi) return true
            if (bm != null && bm == ai) return true
            return false
        }

        val byId = mutableMapOf<String, Map<String, Any?>>()
        for (e in existing) {
            val k = rowKey(e) ?: continue
            byId[k] = e
        }
        for (m in incomingNormalized) {
            val hit = byId.entries.firstOrNull { sameLogicalMessage(it.value, m) }
            if (hit == null) {
                val newKey = rowKey(m) ?: continue
                byId[newKey] = m
            } else {
                val prev = hit.value.toMutableMap()
                prev["recebida"] = ChatJson.deliveryTruthy(prev["recebida"]) || ChatJson.deliveryTruthy(m["recebida"])
                prev["visualizada"] = ChatJson.deliveryTruthy(prev["visualizada"]) || ChatJson.deliveryTruthy(m["visualizada"])
                ChatJson.stringId(m["msgId"])?.let { prev["msgId"] = it }
                ChatJson.stringId(m["id"])?.let { prev["id"] = it }
                byId[hit.key] = prev
            }
        }
        return byId.values.sortedBy { row -> rowTimestampMillis(row) ?: 0L }
    }
}
