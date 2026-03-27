package com.example.chat_minimo_kotlin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat_minimo_kotlin.states.ChatSessionsState
import com.example.chat_minimo_kotlin.states.ChatState
import com.example.chat_minimo_kotlin.states.ChatSummaryUi
import com.example.chat_minimo_kotlin.states.UiDiagnostics
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Lógica de lista, histórico, envio e eventos tempo real (**SSE** + REST) do demo carteiro.
 * Estado de UI imediato continua em [ChatState] / [ChatSessionsState] (singletons do demo).
 */
class ChatViewModel(
    application: Application,
    private val apiBaseUrl: String,
    val userId: String,
    val idCorreiosCidadao: String,
    private val codigosObjeto: List<String>,
) : AndroidViewModel(application) {

    private val gson = Gson()
    private val client = OkHttpClient()

    @Volatile
    var activeChatId: String? = null

    private val visualizadaEnviada = mutableSetOf<String>()
    private val dedupeChatUpdateMillis = mutableMapOf<String, Long>()

    private val sseManager: SseManager
        get() = getApplication<ChatApplication>().sseManager

    private var transportStarted = false

    fun startTransport() {
        if (transportStarted) return
        transportStarted = true
        sseManager.onText = { handleRealtimePayload(it) }
        sseManager.onTransportError = { t ->
            viewModelScope.launch(Dispatchers.Main.immediate) {
                UiDiagnostics.report(t, "SSE")
            }
        }
        sseManager.onStreamOpen = {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { performCatchUp() }
                    .onFailure { e ->
                        viewModelScope.launch(Dispatchers.Main.immediate) {
                            UiDiagnostics.report(e, "SSE catch-up")
                        }
                    }
                runCatching { drainPendingOutbound() }
                    .onFailure { e ->
                        viewModelScope.launch(Dispatchers.Main.immediate) {
                            UiDiagnostics.report(e, "SSE fila outbound")
                        }
                    }
            }
        }
        sseManager.connect("$apiBaseUrl/sse/stream?userId=$userId")
    }

    override fun onCleared() {
        sseManager.onText = null
        sseManager.onTransportError = null
        sseManager.onStreamOpen = null
        sseManager.shutdown()
        super.onCleared()
    }

    suspend fun refreshChatList() {
        try {
            withContext(Dispatchers.IO) {
                val list = ChatListApi.fetchHistoricoChats(
                    client = client,
                    gson = gson,
                    baseUrl = apiBaseUrl,
                    codigosObjeto = codigosObjeto,
                    myUserId = userId,
                    carteiroId = userId,
                )
                withContext(Dispatchers.Main) {
                    ChatSessionsState.chats.clear()
                    ChatSessionsState.chats.addAll(list.sortedByDescending { it.lastMillis })
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                UiDiagnostics.report(e, "Lista: POST /chat/historico → $apiBaseUrl")
            }
        }
    }

    suspend fun markAllRead(chatId: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                ChatMutationApi.markAllRead(client, gson, apiBaseUrl, chatId, userId)
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    UiDiagnostics.report(e, "markAllRead → $apiBaseUrl")
                }
            }
        }
    }

    suspend fun loadMessagesForChat(chatId: String) {
        try {
            val raw =
                withContext(Dispatchers.IO) {
                    ChatHistoryApi.fetchMensagens(client, gson, apiBaseUrl, chatId)
                }
            val normalized = raw.map { ChatHistoryApi.normalizeRow(it) }
            withContext(Dispatchers.Main) {
                ChatState.messages.clear()
                ChatState.messages.addAll(normalized)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                UiDiagnostics.report(
                    e,
                    "GET mensagens → $apiBaseUrl/chat/sessoes/$chatId",
                )
                ChatState.messages.clear()
                ChatState.messages.add(
                    mapOf(
                        "sender" to "system",
                        "content" to (e.message ?: e.toString()),
                    ),
                )
            }
        }
    }

    suspend fun bootstrapNewChatId(): String =
        withContext(Dispatchers.IO) {
            ChatSessionBootstrap.ensureChatId(
                client = client,
                gson = gson,
                baseUrl = apiBaseUrl,
                idCorreios = idCorreiosCidadao,
                codigosObjeto = codigosObjeto,
                carteiroId = userId,
                historicoPorCarteiro = false,
            )
        }

    fun sendMessage(content: Map<String, Any?>) {
        val chatId = content["chatId"]?.toString() ?: return
        val msgId = content["msgId"]?.toString() ?: return
        val sender = content["sender"]?.toString() ?: return
        val receiver = content["receiver"]?.toString() ?: return
        val text = content["content"]?.toString() ?: return
        val ts =
            (content["timestampMillis"] as? Number)?.toLong()
                ?: (content["timestamp"] as? Number)?.toLong()
                ?: System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.Main.immediate) {
            ChatState.messages.add(content)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val ok =
                ChatMutationApi.postChatMessage(
                    client,
                    gson,
                    apiBaseUrl,
                    chatId,
                    msgId,
                    sender,
                    receiver,
                    text,
                    ts,
                )
            if (!ok) {
                ChatSseOutboundQueue.enqueue(
                    PendingChatTextMessage(chatId, msgId, sender, receiver, text, ts),
                )
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    UiDiagnostics.report(
                        IllegalStateException("POST falhou — mensagem na fila para reenvio."),
                        "REST /chat/sessoes/.../messages",
                    )
                }
            }
        }
    }

    private suspend fun performCatchUp() {
        refreshChatList()
        val cid = activeChatId ?: return
        mergeIncrementalThread(cid)
    }

    /**
     * API publica novas mensagens no SSE como [ChatWs.TYPE_CHAT_UPDATE] (só metadados da sessão).
     * A thread da conversa precisa de GET incremental (`since` > último timestamp), como no catch-up.
     */
    private suspend fun mergeIncrementalThread(expectedChatId: String) {
        if (activeChatId != expectedChatId) return
        val localMessages = withContext(Dispatchers.Main) { ChatState.messages.toList() }
        val since = ChatHistoryApi.maxTimestampMillis(localMessages)
        val raw =
            withContext(Dispatchers.IO) {
                when {
                    since != null ->
                        ChatHistoryApi.fetchMensagens(
                            client,
                            gson,
                            apiBaseUrl,
                            expectedChatId,
                            since = since,
                            size = 200,
                        )
                    localMessages.isEmpty() ->
                        ChatHistoryApi.fetchMensagens(
                            client,
                            gson,
                            apiBaseUrl,
                            expectedChatId,
                            size = 200,
                        )
                    else -> emptyList()
                }
            }
        if (raw.isEmpty()) return
        withContext(Dispatchers.Main) {
            if (activeChatId != expectedChatId) return@withContext
            val normalized = raw.map { ChatHistoryApi.normalizeRow(it) }
            val merged =
                ChatHistoryApi.mergeCatchUp(ChatState.messages.toList(), normalized)
            ChatState.messages.clear()
            ChatState.messages.addAll(merged)
        }
    }

    private suspend fun drainPendingOutbound() {
        val batch = ChatSseOutboundQueue.drain()
        var failedFrom = -1
        for ((i, p) in batch.withIndex()) {
            val ok =
                ChatMutationApi.postChatMessage(
                    client,
                    gson,
                    apiBaseUrl,
                    p.chatId,
                    p.msgId,
                    p.sender,
                    p.receiver,
                    p.content,
                    p.timestampMillis,
                )
            if (!ok) {
                failedFrom = i
                break
            }
        }
        if (failedFrom >= 0) {
            for (j in failedFrom until batch.size) {
                ChatSseOutboundQueue.enqueue(batch[j])
            }
        }
    }

    private fun handleRealtimePayload(text: String) {
        if (text.isBlank()) return
        val msg: Map<String, Any?>
        try {
            @Suppress("UNCHECKED_CAST")
            msg = gson.fromJson(text, Map::class.java) as Map<String, Any?>
        } catch (e: Exception) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                UiDiagnostics.report(e, "Realtime JSON (${text.take(120)}…)")
            }
            return
        }
        try {
            val type = msg["type"] as? String
            if (type?.equals(ChatWs.TYPE_PONG, ignoreCase = true) == true) return

            if (type?.equals(ChatWs.TYPE_CHAT_UPDATE, ignoreCase = true) == true) {
                applyChatUpdate(msg)
                return
            }
            if (type?.equals(ChatWs.TYPE_CHAT_STATUS_CHANGED, ignoreCase = true) == true) {
                applyChatStatusChanged(msg)
                return
            }
            if (type?.equals(ChatWs.TYPE_MESSAGE_STATUS, ignoreCase = true) == true) {
                viewModelScope.launch(Dispatchers.Main.immediate) { applyMessageStatus(msg) }
                return
            }

            val chatId = msg["chatId"]?.toString()
            if (chatId != null && chatId != activeChatId) {
                return
            }

            val senderRaw = msg["sender"] ?: return
            val sender = senderRaw.toString()
            if (sender == userId) return
            viewModelScope.launch(Dispatchers.Main.immediate) {
                val incoming = msg.toMutableMap().apply { put("sender", sender) }
                val enriched = enrichIncoming(incoming)
                ChatState.messages.add(enriched)
                maybeSendVisualizada(enriched)
            }
        } catch (e: Exception) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                UiDiagnostics.report(e, "Realtime handler (type=${msg["type"]})")
            }
        }
    }

    private fun applyChatUpdate(msg: Map<String, Any?>) {
        val chatId = msg[ChatWs.KEY_CHAT_ID]?.toString() ?: return
        val canonical = msg[ChatWs.KEY_CANONICAL] as? Boolean ?: false
        val lm = (msg[ChatWs.KEY_LAST_MESSAGE_MILLIS] as? Number)?.toLong() ?: 0L
        if (!canonical) {
            val last = dedupeChatUpdateMillis[chatId] ?: 0L
            if (lm <= last + 5_000L) {
                return
            }
        }
        dedupeChatUpdateMillis[chatId] = lm

        @Suppress("UNCHECKED_CAST")
        val unreadMap = msg[ChatWs.KEY_UNREAD_COUNT] as? Map<*, *>
        val skipUnreadReplace =
            !canonical && (unreadMap == null || unreadMap.isEmpty())
        val urResolved =
            if (skipUnreadReplace) {
                null
            } else {
                (unreadMap?.get(userId) as? Number)?.toInt() ?: 0
            }
        val stOpt = msg[ChatWs.KEY_CHAT_STATUS]?.toString()

        viewModelScope.launch(Dispatchers.Main.immediate) {
            val idx = ChatSessionsState.chats.indexOfFirst { it.chatId == chatId }
            val previousPreview = if (idx >= 0) ChatSessionsState.chats[idx].lastMessage else ""
            val listPreview = ChatListApi.listPreviewForCarteiroFromChatUpdate(
                msg,
                userId,
                canonical,
                previousPreview,
            )
            if (idx >= 0) {
                val old = ChatSessionsState.chats[idx]
                ChatSessionsState.chats[idx] = old.copy(
                    lastMessage = listPreview,
                    lastMillis = lm,
                    unread = urResolved ?: old.unread,
                    status = stOpt ?: old.status,
                )
            } else {
                ChatSessionsState.chats.add(
                    ChatSummaryUi(
                        chatId = chatId,
                        peerId = "",
                        title = chatId.take(8),
                        lastMessage = listPreview,
                        lastMillis = lm,
                        unread = urResolved ?: 0,
                        status = stOpt ?: "ABERTO",
                    ),
                )
            }
            val sorted = ChatSessionsState.chats.sortedByDescending { it.lastMillis }
            ChatSessionsState.chats.clear()
            ChatSessionsState.chats.addAll(sorted)

            if (chatId == activeChatId) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching { mergeIncrementalThread(chatId) }
                        .onFailure { e ->
                            viewModelScope.launch(Dispatchers.Main.immediate) {
                                UiDiagnostics.report(e, "Realtime → merge thread ($chatId)")
                            }
                        }
                }
            }
        }
    }

    private fun applyChatStatusChanged(msg: Map<String, Any?>) {
        val chatId = msg[ChatWs.KEY_CHAT_ID]?.toString() ?: return
        val ns = msg[ChatWs.KEY_NEW_STATUS]?.toString() ?: return
        viewModelScope.launch(Dispatchers.Main.immediate) {
            val idx = ChatSessionsState.chats.indexOfFirst { it.chatId == chatId }
            if (idx >= 0) {
                val old = ChatSessionsState.chats[idx]
                ChatSessionsState.chats[idx] = old.copy(status = ns)
            }
        }
    }

    private fun enrichIncoming(msg: Map<String, Any?>): Map<String, Any?> {
        val m = msg.toMutableMap()
        if (m["chatId"] == null && !activeChatId.isNullOrBlank()) {
            m["chatId"] = activeChatId
        }
        if (m["msgId"] == null && m["id"] != null) {
            m["msgId"] = m["id"].toString()
        }
        if (m["recebida"] == null) m["recebida"] = false
        if (m["visualizada"] == null) m["visualizada"] = false
        return m
    }

    private fun applyMessageStatus(status: Map<String, Any?>) {
        val chatId = ChatJson.stringId(status["chatId"]) ?: return
        val eventMsgId = ChatJson.stringId(status["msgId"]) ?: return
        val ds = status["deliveryStatus"]?.toString()?.trim()?.uppercase().orEmpty()
        val flagR = ChatJson.deliveryTruthy(status["recebida"])
        val flagV = ChatJson.deliveryTruthy(status["visualizada"])
        if (ds.isEmpty() && !flagR && !flagV) return
        for (i in ChatState.messages.indices) {
            val row = ChatState.messages[i]
            val rowMsg = ChatJson.stringId(row["msgId"])
            val rowId = ChatJson.stringId(row["id"])
            if (eventMsgId != rowMsg && eventMsgId != rowId) continue
            val rowChat = ChatJson.stringId(row["chatId"])
            val chatOk =
                if (!rowChat.isNullOrBlank()) {
                    rowChat == chatId
                } else {
                    activeChatId == chatId
                }
            if (!chatOk) continue
            val next = row.toMutableMap()
            when (ds) {
                "RECEBIDA" -> next["recebida"] = true
                "VISUALIZADA" -> {
                    next["recebida"] = true
                    next["visualizada"] = true
                }
            }
            if (flagR) next["recebida"] = true
            if (flagV) {
                next["visualizada"] = true
                next["recebida"] = true
            }
            ChatState.messages[i] = next
            break
        }
    }

    private fun maybeSendVisualizada(incoming: Map<String, Any?>) {
        val msgId = incoming["msgId"]?.toString() ?: return
        val chatId =
            incoming["chatId"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: activeChatId?.trim()?.takeIf { it.isNotEmpty() }
                ?: return
        val from = incoming["sender"]?.toString() ?: return
        if (!visualizadaEnviada.add(msgId)) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                ChatMutationApi.postDeliveryStatus(
                    client,
                    gson,
                    apiBaseUrl,
                    chatId,
                    msgId,
                    "VISUALIZADA",
                    from,
                )
            }.onFailure { e ->
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    UiDiagnostics.report(e, "POST delivery-status")
                }
            }
        }
    }
}
