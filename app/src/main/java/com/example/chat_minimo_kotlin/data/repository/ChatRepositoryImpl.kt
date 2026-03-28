package com.example.chat_minimo_kotlin.data.repository

import com.example.chat_minimo_kotlin.core.config.ChatAppConfig
import com.example.chat_minimo_kotlin.core.session.AuthSessionHolder
import com.example.chat_minimo_kotlin.data.mapper.HistoricoMapper
import com.example.chat_minimo_kotlin.data.remote.ChatRemoteDataSource
import com.example.chat_minimo_kotlin.domain.model.ChatMessage
import com.example.chat_minimo_kotlin.domain.model.ChatSummary
import com.example.chat_minimo_kotlin.domain.model.ChatStatusBuckets
import com.example.chat_minimo_kotlin.domain.repository.ChatRepository
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val remote: ChatRemoteDataSource,
    private val historicoMapper: HistoricoMapper,
    private val session: AuthSessionHolder,
    private val appConfig: ChatAppConfig,
    private val gson: Gson,
) : ChatRepository {

    private fun bffBase(): String {
        val u = session.bffApiBaseUrl?.trim()?.trimEnd('/')
        return if (!u.isNullOrEmpty()) u else appConfig.defaultBffBaseUrl
    }

    override suspend fun fetchHistoricoChats(
        codigosObjeto: List<String>,
        myUserId: String,
        idCorreios: String?,
        carteiroId: String?,
    ): List<ChatSummary> =
        withContext(Dispatchers.IO) {
            val rows =
                remote.fetchHistoricoRows(
                    bffBase(),
                    codigosObjeto,
                    idCorreios,
                    carteiroId,
                )
            rows.map { historicoMapper.toChatSummary(it, myUserId) }
        }

    override suspend fun fetchMessages(
        chatId: String,
        since: Long?,
        size: Int?,
    ): List<ChatMessage> =
        withContext(Dispatchers.IO) {
            remote.fetchMessages(bffBase(), chatId, since, size)
        }

    override suspend fun postChatMessage(
        chatId: String,
        msgId: String,
        sender: String,
        receiver: String,
        content: String,
        timestampMillis: Long,
    ): Boolean =
        withContext(Dispatchers.IO) {
            remote.postChatMessage(
                bffBase(),
                chatId,
                msgId,
                sender,
                receiver,
                content,
                timestampMillis,
            )
        }

    override suspend fun postDeliveryStatus(
        chatId: String,
        msgId: String,
        deliveryStatus: String,
        targetUserId: String,
    ) {
        withContext(Dispatchers.IO) {
            remote.postDeliveryStatus(
                bffBase(),
                chatId,
                msgId,
                deliveryStatus,
                targetUserId,
            )
        }
    }

    override suspend fun markAllRead(chatId: String, userId: String) {
        withContext(Dispatchers.IO) {
            remote.markAllRead(bffBase(), chatId, userId)
        }
    }

    override suspend fun ensureChatId(
        idCorreios: String,
        codigosObjeto: List<String>,
        carteiroId: String?,
    ): String =
        withContext(Dispatchers.IO) {
            val base = bffBase()
            val body =
                remote.postHistorico(
                    base,
                    codigosObjeto,
                    idCorreios.trim(),
                    carteiroId,
                )
            val rows = historicoMapper.parseHistoricoBody(body, gson)
            val want = idCorreios.trim()
            for (dto in rows) {
                val st = dto.status?.trim().orEmpty()
                if (st.isNotEmpty() && ChatStatusBuckets.isHistorico(st)) continue
                val citizen = historicoMapper.citizenIdCorreios(dto)?.trim().orEmpty()
                if (citizen != want) continue
                val id = dto.id?.trim().orEmpty()
                if (id.isNotEmpty()) return@withContext id
            }
            remote.createSession(base, idCorreios.trim(), codigosObjeto, carteiroId)
        }
}
