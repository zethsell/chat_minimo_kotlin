package com.example.chat_minimo_kotlin.presentation.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat_minimo_kotlin.core.config.ChatAppConfig
import com.example.chat_minimo_kotlin.core.session.AuthSessionHolder
import com.example.chat_minimo_kotlin.data.queue.ChatSseOutboundQueue
import com.example.chat_minimo_kotlin.data.sse.RealtimeSseParser
import com.example.chat_minimo_kotlin.data.sse.SseManager
import com.example.chat_minimo_kotlin.domain.model.ChatMessage
import com.example.chat_minimo_kotlin.domain.model.ChatStatusBuckets
import com.example.chat_minimo_kotlin.domain.model.ChatDetail
import com.example.chat_minimo_kotlin.domain.model.OutgoingChatText
import com.example.chat_minimo_kotlin.domain.model.PendingOutboundMessage
import com.example.chat_minimo_kotlin.domain.realtime.ChatSessionUpdate
import com.example.chat_minimo_kotlin.domain.realtime.MessageDeliveryStatusEvent
import com.example.chat_minimo_kotlin.domain.realtime.ParsedRealtimeEvent
import com.example.chat_minimo_kotlin.domain.repository.ChatRepository
import com.example.chat_minimo_kotlin.domain.service.ChatListPreviewFormatter
import com.example.chat_minimo_kotlin.domain.service.MessageMergeService
import com.example.chat_minimo_kotlin.states.UiDiagnostics
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ChatViewModel @Inject constructor(
    application: Application,
    private val chatRepository: ChatRepository,
    private val session: AuthSessionHolder,
    private val appConfig: ChatAppConfig,
    private val messageMergeService: MessageMergeService,
    private val listPreviewFormatter: ChatListPreviewFormatter,
    private val sseManager: SseManager,
    private val realtimeParser: RealtimeSseParser,
) : AndroidViewModel(application) {

    private val _chats = MutableStateFlow<List<ChatDetail>>(emptyList())
    val chats: StateFlow<List<ChatDetail>> = _chats.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    @Volatile
    var activeChatId: String? = null

    private val visualizadaEnviada = mutableSetOf<String>()
    private val dedupeChatUpdateMillis = mutableMapOf<String, Long>()

    private var transportStarted = false

    val userId: String
        get() =
            session.loggedUsuario?.trim()?.takeIf { it.isNotEmpty() }
                ?: appConfig.demoFallbackUserId

    private fun codigosObjeto(): List<String> = appConfig.demoCodigosObjeto

    /** Códigos de objeto demo (ex.: nova conversa até o próximo refresh do histórico). */
    val demoCodigosObjeto: List<String> get() = appConfig.demoCodigosObjeto

    private fun bffBase(): String {
        val u = session.bffApiBaseUrl?.trim()?.trimEnd('/')
        return if (!u.isNullOrEmpty()) u else appConfig.defaultBffBaseUrl
    }

    fun findActiveChatForCitizen(idCorreios: String): ChatDetail? {
        val want = idCorreios.trim()
        if (want.isEmpty()) return null
        return _chats.value.find { s ->
            !ChatStatusBuckets.isHistorico(s.status) && peerMatchesCitizen(s, want)
        }
    }

    private fun peerMatchesCitizen(s: ChatDetail, idCorreios: String): Boolean {
        val id = s.idCorreios.trim()
        return id.isNotEmpty() &&
            (id == idCorreios || id.equals(idCorreios, ignoreCase = true))
    }

    fun prepareForLogout() {
        transportStarted = false
        sseManager.onText = null
        sseManager.onTransportError = null
        sseManager.onStreamOpen = null
        sseManager.shutdown()
        activeChatId = null
        visualizadaEnviada.clear()
        dedupeChatUpdateMillis.clear()
        _chats.value = emptyList()
        _messages.value = emptyList()
    }

    fun startTransport() {
        if (transportStarted) return
        if (session.sessionCookie.isNullOrBlank()) return
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
        sseManager.connect("${bffBase()}/sse/stream?userId=$userId")
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
                val list =
                    chatRepository.fetchHistoricoChats(
                        codigosObjeto = codigosObjeto(),
                        myUserId = userId,
                        idCorreios = null,
                        carteiroId = userId,
                    )
                withContext(Dispatchers.Main) {
                    _chats.value = list.sortedByDescending { it.lastMillis }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                UiDiagnostics.report(e, "Lista: POST /chat/historico → ${bffBase()}")
            }
        }
    }

    suspend fun markAllRead(chatId: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                chatRepository.markAllRead(chatId, userId)
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    UiDiagnostics.report(e, "markAllRead → ${bffBase()}")
                }
            }
        }
    }

    suspend fun loadMessagesForChat(chatId: String) {
        try {
            val raw =
                withContext(Dispatchers.IO) {
                    chatRepository.fetchMessages(chatId)
                }
            withContext(Dispatchers.Main) {
                _messages.value = raw
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                UiDiagnostics.report(
                    e,
                    "GET mensagens → ${bffBase()}/chat/sessoes/$chatId",
                )
                _messages.value =
                    listOf(ChatMessage.systemNotice(e.message ?: e.toString(), chatId))
            }
        }
    }

    suspend fun bootstrapNewChatId(idCorreiosPeer: String): String =
        withContext(Dispatchers.IO) {
            val id = idCorreiosPeer.trim()
            require(id.isNotEmpty()) { "Informe idCorreios no diálogo para iniciar a conversa." }
            chatRepository.ensureChatId(
                idCorreios = id,
                codigosObjeto = codigosObjeto(),
                carteiroId = userId,
            )
        }

    fun sendMessage(outgoing: OutgoingChatText) {
        val chatId = outgoing.chatId
        val receiver = outgoing.receiverId
        val text = outgoing.content
        val msgId = java.util.UUID.randomUUID().toString()
        val ts = System.currentTimeMillis()
        val optimistic =
            ChatMessage(
                msgId = msgId,
                serverId = null,
                chatId = chatId,
                sender = userId,
                receiver = receiver,
                content = text,
                timestampMillis = ts,
                recebida = false,
                visualizada = false,
            )
        viewModelScope.launch(Dispatchers.Main.immediate) {
            _messages.update { it + optimistic }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val ok =
                chatRepository.postChatMessage(
                    chatId,
                    msgId,
                    userId,
                    receiver,
                    text,
                    ts,
                )
            if (!ok) {
                ChatSseOutboundQueue.enqueue(
                    PendingOutboundMessage(chatId, msgId, userId, receiver, text, ts),
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

    private suspend fun mergeIncrementalThread(expectedChatId: String) {
        if (activeChatId != expectedChatId) return
        val localMessages = withContext(Dispatchers.Main) { _messages.value }
        val since = messageMergeService.maxTimestampMillis(localMessages)
        val raw =
            withContext(Dispatchers.IO) {
                when {
                    since != null ->
                        chatRepository.fetchMessages(
                            expectedChatId,
                            since = since,
                            size = 200,
                        )
                    localMessages.isEmpty() ->
                        chatRepository.fetchMessages(expectedChatId, size = 200)
                    else -> emptyList()
                }
            }
        if (raw.isEmpty()) return
        withContext(Dispatchers.Main) {
            if (activeChatId != expectedChatId) return@withContext
            val merged = messageMergeService.merge(localMessages, raw)
            _messages.value = merged
        }
    }

    private suspend fun drainPendingOutbound() {
        val batch = ChatSseOutboundQueue.drain()
        var failedFrom = -1
        for ((i, p) in batch.withIndex()) {
            val ok =
                chatRepository.postChatMessage(
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
        try {
            val event = realtimeParser.parse(text, userId) ?: return
            when (event) {
                ParsedRealtimeEvent.Pong -> Unit
                is ParsedRealtimeEvent.ChatUpdate ->
                    applyChatUpdate(event.update)
                is ParsedRealtimeEvent.ChatStatusChanged ->
                    applyChatStatusChanged(event.chatId, event.newStatus)
                is ParsedRealtimeEvent.MessageStatus ->
                    viewModelScope.launch(Dispatchers.Main.immediate) {
                        applyMessageStatus(event.event)
                    }
                is ParsedRealtimeEvent.ThreadMessage ->
                    viewModelScope.launch(Dispatchers.Main.immediate) {
                        handleThreadMessage(event.message)
                    }
                is ParsedRealtimeEvent.Unhandled -> Unit
            }
        } catch (e: Exception) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                UiDiagnostics.report(e, "Realtime handler")
            }
        }
    }

    private fun applyChatUpdate(update: ChatSessionUpdate) {
        val chatId = update.chatId
        val canonical = update.canonical
        val lm = update.lastMessageMillis
        if (!canonical) {
            val last = dedupeChatUpdateMillis[chatId] ?: 0L
            if (lm <= last + 5_000L) {
                return
            }
        }
        dedupeChatUpdateMillis[chatId] = lm

        viewModelScope.launch(Dispatchers.Main.immediate) {
            val current = _chats.value.toMutableList()
            val idx = current.indexOfFirst { it.chatId == chatId }
            val previousPreview = if (idx >= 0) current[idx].lastMessage else ""
            val listPreview =
                listPreviewFormatter.previewAfterChatUpdate(
                    update,
                    userId,
                    previousPreview,
                )
            val urResolved = update.unreadForUser
            val stOpt = update.chatStatus
            if (idx >= 0) {
                val old = current[idx]
                current[idx] =
                    old.copy(
                        lastMessage = listPreview,
                        lastMillis = lm,
                        unread = urResolved ?: old.unread,
                        status = stOpt ?: old.status,
                    )
            } else {
                current.add(
                    ChatDetail(
                        chatId = chatId,
                        idCorreios = "",
                        nomeCliente = chatId.take(8),
                        nomeCarteiro = "",
                        clientAvatar = null,
                        codigosObjetos = emptyList(),
                        lastMessage = listPreview,
                        lastMillis = lm,
                        unread = urResolved ?: 0,
                        status = stOpt ?: "ABERTO",
                    ),
                )
            }
            _chats.value = current.sortedByDescending { it.lastMillis }

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

    private fun applyChatStatusChanged(chatId: String, newStatus: String) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            _chats.update { list ->
                list.map { s ->
                    if (s.chatId == chatId) s.copy(status = newStatus) else s
                }
            }
        }
    }

    private fun enrichIncoming(msg: ChatMessage): ChatMessage {
        var m = msg
        if (m.chatId.isNullOrBlank() && !activeChatId.isNullOrBlank()) {
            m = m.copy(chatId = activeChatId)
        }
        if (m.msgId.isEmpty() && !m.serverId.isNullOrBlank()) {
            m = m.copy(msgId = m.serverId!!)
        }
        return m
    }

    private fun handleThreadMessage(raw: ChatMessage) {
        val chatId = raw.chatId
        if (chatId != null && chatId != activeChatId) {
            return
        }
        if (raw.sender == userId) return
        val enriched = enrichIncoming(raw)
        _messages.update { it + enriched }
        maybeSendVisualizada(enriched)
    }

    private fun applyMessageStatus(status: MessageDeliveryStatusEvent) {
        val ds = status.deliveryStatus?.trim()?.uppercase().orEmpty()
        if (ds.isEmpty() && !status.recebida && !status.visualizada) return
        val chatId = status.chatId
        val eventMsgId = status.msgId
        _messages.update { rows ->
            val i =
                rows.indexOfFirst { row ->
                    val rowMsg = row.msgId
                    val rowId = row.serverId
                    val matchId = eventMsgId == rowMsg || eventMsgId == rowId
                    if (!matchId) return@indexOfFirst false
                    val rowChat = row.chatId
                    val chatOk =
                        if (!rowChat.isNullOrBlank()) {
                            rowChat == chatId
                        } else {
                            activeChatId == chatId
                        }
                    chatOk
                }
            if (i < 0) return@update rows
            val row = rows[i]
            var next = row
            when (ds) {
                "RECEBIDA" -> next = next.copy(recebida = true)
                "VISUALIZADA" -> next = next.copy(recebida = true, visualizada = true)
            }
            if (status.recebida) next = next.copy(recebida = true)
            if (status.visualizada) {
                next = next.copy(visualizada = true, recebida = true)
            }
            rows.toMutableList().apply { this[i] = next }
        }
    }

    private fun maybeSendVisualizada(incoming: ChatMessage) {
        val msgId = incoming.msgId.ifBlank { return }
        val chatId =
            incoming.chatId?.trim()?.takeIf { it.isNotEmpty() }
                ?: activeChatId?.trim()?.takeIf { it.isNotEmpty() }
                ?: return
        val from = incoming.sender.ifBlank { return }
        if (!visualizadaEnviada.add(msgId)) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                chatRepository.postDeliveryStatus(
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
