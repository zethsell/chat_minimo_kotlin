package com.example.chat_minimo_kotlin.data.remote

import com.example.chat_minimo_kotlin.data.dto.CreateSessionBody
import com.example.chat_minimo_kotlin.data.dto.DeliveryStatusBody
import com.example.chat_minimo_kotlin.data.dto.HistoricoRequestBody
import com.example.chat_minimo_kotlin.data.dto.MarkAllReadBody
import com.example.chat_minimo_kotlin.data.dto.PostChatMessageBody
import com.example.chat_minimo_kotlin.data.mapper.ChatMessageMapper
import com.example.chat_minimo_kotlin.data.mapper.HistoricoMapper
import com.example.chat_minimo_kotlin.data.remote.api.BffChatApi
import com.example.chat_minimo_kotlin.domain.model.ChatMessage
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

/**
 * REST `/chat/...` via Retrofit ([BffChatApi]); cookie no [OkHttp] injetado.
 */
@Singleton
class ChatRemoteDataSource @Inject constructor(
    private val api: BffChatApi,
    private val gson: Gson,
    private val historicoMapper: HistoricoMapper,
    private val messageMapper: ChatMessageMapper,
) {

    private fun historicoRequestBody(
        codigosObjeto: List<String>,
        idCorreios: String?,
        carteiroId: String?,
    ): HistoricoRequestBody {
        val idC = idCorreios?.trim().orEmpty()
        val cart = carteiroId?.trim().orEmpty()
        require(idC.isNotEmpty() || cart.isNotEmpty()) {
            "historico: informe idCorreios ou carteiroId"
        }
        return HistoricoRequestBody(
            codigosObjeto = codigosObjeto,
            idCorreios = idC.takeIf { it.isNotEmpty() },
            carteiroId = cart.takeIf { it.isNotEmpty() },
        )
    }

    suspend fun postHistorico(
        baseUrl: String,
        codigosObjeto: List<String>,
        idCorreios: String?,
        carteiroId: String?,
    ): String {
        val url = "${baseUrl.trimEnd('/')}/chat/historico"
        val resp = api.postHistorico(url, historicoRequestBody(codigosObjeto, idCorreios, carteiroId))
        val text = resp.body()?.string().orEmpty()
        if (!resp.isSuccessful) {
            throw IllegalStateException(
                "historico HTTP ${resp.code()}: ${resp.errorBody()?.string()?.take(900) ?: text.take(900)}",
            )
        }
        return text
    }

    suspend fun fetchHistoricoRows(
        baseUrl: String,
        codigosObjeto: List<String>,
        idCorreios: String?,
        carteiroId: String?,
    ) = historicoMapper.parseHistoricoBody(
        postHistorico(baseUrl, codigosObjeto, idCorreios, carteiroId),
        gson,
    )

    suspend fun fetchMessages(
        baseUrl: String,
        chatId: String,
        since: Long? = null,
        size: Int? = null,
    ): List<ChatMessage> {
        val b = baseUrl.trimEnd('/')
        val url = buildString {
            append(b).append("/chat/sessoes/").append(chatId).append("/messages")
            val q = mutableListOf<String>()
            if (since != null) q.add("since=$since")
            if (size != null) q.add("size=$size")
            if (q.isNotEmpty()) append("?").append(q.joinToString("&"))
        }
        val resp = api.getMessages(url)
        if (!resp.isSuccessful) {
            error("messages HTTP ${resp.code()}: ${resp.errorBody()?.string()}")
        }
        val body = resp.body()?.string().orEmpty()
        return messageMapper.parseMessagesBody(gson, body)
    }

    suspend fun postChatMessage(
        baseUrl: String,
        chatId: String,
        msgId: String,
        sender: String,
        receiver: String,
        content: String,
        timestampMillis: Long,
    ): Boolean {
        val url = "${baseUrl.trimEnd('/')}/chat/sessoes/$chatId/messages"
        val resp =
            api.postMessage(
                url,
                PostChatMessageBody(msgId, sender, receiver, content, timestampMillis),
            )
        return resp.isSuccessful
    }

    suspend fun postDeliveryStatus(
        baseUrl: String,
        chatId: String,
        msgId: String,
        deliveryStatus: String,
        targetUserId: String,
    ) {
        val url = "${baseUrl.trimEnd('/')}/chat/sessoes/$chatId/delivery-status"
        val resp =
            api.postDeliveryStatus(
                url,
                DeliveryStatusBody(msgId, deliveryStatus, targetUserId),
            )
        if (!resp.isSuccessful) {
            error("delivery-status HTTP ${resp.code()}: ${resp.errorBody()?.string()}")
        }
    }

    suspend fun markAllRead(baseUrl: String, chatId: String, userId: String) {
        val url = "${baseUrl.trimEnd('/')}/chat/sessoes/$chatId"
        val resp = api.markAllRead(url, MarkAllReadBody(userId = userId))
        if (!resp.isSuccessful) {
            val detail = resp.errorBody()?.string()?.trim()?.take(500) ?: ""
            throw IllegalStateException(
                "markAllRead HTTP ${resp.code()}${if (detail.isNotEmpty()) ": $detail" else ""}",
            )
        }
    }

    suspend fun createSession(
        baseUrl: String,
        idCorreios: String,
        codigosObjeto: List<String>,
        carteiroId: String?,
    ): String {
        val url = "${baseUrl.trimEnd('/')}/chat/sessoes"
        val body =
            CreateSessionBody(
                idCorreios = idCorreios.trim(),
                codigosObjeto = codigosObjeto,
                carteiroId = carteiroId?.trim()?.takeIf { it.isNotEmpty() },
            )
        val resp = api.createSession(url, body)
        if (!resp.isSuccessful) {
            error("create chat HTTP ${resp.code()}: ${resp.errorBody()?.string()}")
        }
        val dto = resp.body() ?: error("POST /chat/sessoes: corpo vazio")
        return dto.id?.takeIf { it.isNotBlank() }
            ?: dto.chatId?.takeIf { it.isNotBlank() }
            ?: error("POST /chat/sessoes sem id/chatId")
    }
}
