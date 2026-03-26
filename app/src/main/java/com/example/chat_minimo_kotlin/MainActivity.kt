package com.example.chat_minimo_kotlin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.chat_minimo_kotlin.states.ChatSessionsState
import com.example.chat_minimo_kotlin.states.ChatState
import com.example.chat_minimo_kotlin.states.ChatSummaryUi
import com.example.chat_minimo_kotlin.states.UiDiagnostics
import com.example.chat_minimo_kotlin.ui.components.ErrorLogBanner
import com.example.chat_minimo_kotlin.ui.pages.ChatListScreen
import com.example.chat_minimo_kotlin.ui.pages.ChatScreen
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Demo **carteiro** → BFF `:9090` (Flutter cidadão → proxyapp `:9091`).
 *
 * Fluxo alvo: **LOEC** → busca **idCorreios por objeto** → este app **inicia** o chat (`POST /chat/sessoes` quando
 * integrado) e a **primeira mensagem**; o app **cidadão não cria sessão**, só lista + conversa existente.
 *
 * [userId] = matrícula (WS, historico por carteiro, unread). [idCorreiosCidadao] = cidadão (peer ao enviar / demo fixo).
 */
class MainActivity : ComponentActivity() {

    private val gson = Gson()
    private val client = OkHttpClient()
    private val userId = "matricula_123"
    /** idCorreios do cidadão — chave da busca em `POST /chat/historico` e peer padrão na conversa. */
    private val idCorreiosCidadao = "idCorreios_123"
    /** Base HTTP do BFF — [R.string.chat_bff_base_url]; emulador típico `10.0.2.2:9090`, físico: IP LAN do PC. */
    private val apiBaseUrl: String
        get() = getString(R.string.chat_bff_base_url).trimEnd('/')

    private val wsBaseUrl: String
        get() = apiBaseUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
    private val codigosObjeto = listOf("AN123456789BR")
    private val visualizadaEnviada = mutableSetOf<String>()

    /** Chat aberto na tela de conversa (null = lista). */
    private var activeChatId: String? = null
    private val dedupeChatUpdateMillis = mutableMapOf<String, Long>()

    private val wsManager: WebSocketManager
        get() = (application as ChatApplication).wsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wsManager.connect("$wsBaseUrl/ws?userId=$userId")
        wsManager.onText = { handleWsPayload(it) }
        wsManager.onTransportError = { t, resp ->
            runOnUiThread {
                val http = resp?.let { "${it.code} ${it.message}" } ?: "sem handshake HTTP"
                UiDiagnostics.report(t, "WebSocket ($http)")
            }
        }

        setContent {
            Box(Modifier.fillMaxSize()) {
                var selected by remember { mutableStateOf<ChatSummaryUi?>(null) }
                var novaBusy by remember { mutableStateOf(false) }

                val openChatId = selected?.chatId
                LaunchedEffect(openChatId) {
                    val id = openChatId ?: return@LaunchedEffect
                    withContext(Dispatchers.IO) {
                        runCatching {
                            ChatMutationApi.markAllRead(client, gson, apiBaseUrl, id, userId)
                        }.onFailure { e ->
                            withContext(Dispatchers.Main) {
                                UiDiagnostics.report(e, "markAllRead → $apiBaseUrl")
                            }
                        }
                    }
                }

                if (selected == null) {
                    ChatListScreen(
                        onOpenChat = { summary ->
                            activeChatId = summary.chatId
                            selected = summary
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    val raw = ChatHistoryApi.fetchMensagens(
                                        client,
                                        gson,
                                        apiBaseUrl,
                                        summary.chatId,
                                    )
                                    val normalized = raw.map { ChatHistoryApi.normalizeRow(it) }
                                    runOnUiThread {
                                        ChatState.messages.clear()
                                        ChatState.messages.addAll(normalized)
                                    }
                                } catch (e: Exception) {
                                    runOnUiThread {
                                        UiDiagnostics.report(
                                            e,
                                            "GET mensagens → $apiBaseUrl/chat/sessoes/${summary.chatId}",
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
                        },
                        onRefresh = {
                            refreshChatList()
                        },
                        onNovaConversa = conv@{
                            if (novaBusy) return@conv
                            novaBusy = true
                            try {
                                val chatId = withContext(Dispatchers.IO) {
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
                                activeChatId = chatId
                                selected = ChatSummaryUi(
                                    chatId = chatId,
                                    peerId = idCorreiosCidadao,
                                    title = idCorreiosCidadao,
                                    lastMessage = "",
                                    lastMillis = System.currentTimeMillis(),
                                    unread = 0,
                                    status = "ABERTO",
                                )
                                withContext(Dispatchers.IO) {
                                    try {
                                        val raw = ChatHistoryApi.fetchMensagens(
                                            client,
                                            gson,
                                            apiBaseUrl,
                                            chatId,
                                        )
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
                                refreshChatList()
                            } catch (e: Exception) {
                                UiDiagnostics.report(
                                    e,
                                    "Nova conversa (pós-LOEC: historico + sessoes)",
                                )
                            } finally {
                                withContext(Dispatchers.Main) {
                                    novaBusy = false
                                }
                            }
                        },
                        novaConversaBusy = novaBusy,
                    )
                } else {
                    val sel = selected!!
                    ChatScreen(
                        userId = userId,
                        receiverId = if (sel.peerId.isNotEmpty()) sel.peerId else idCorreiosCidadao,
                        chatId = sel.chatId,
                        sessionLoading = false,
                        sessionError = null,
                        onSendMessage = { sendMessage(it) },
                        onBack = {
                            activeChatId = null
                            selected = null
                        },
                    )
                }

                ErrorLogBanner(Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    private suspend fun refreshChatList() {
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

    override fun onDestroy() {
        wsManager.onText = null
        wsManager.onTransportError = null
        super.onDestroy()
    }

    private fun handleWsPayload(text: String) {
        if (text.isBlank()) return
        val msg: Map<String, Any?>
        try {
            @Suppress("UNCHECKED_CAST")
            msg = gson.fromJson(text, Map::class.java) as Map<String, Any?>
        } catch (e: Exception) {
            runOnUiThread {
                UiDiagnostics.report(e, "WS JSON (${text.take(120)}…)")
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
                runOnUiThread { applyMessageStatus(msg) }
                return
            }

            val chatId = msg["chatId"]?.toString()
            if (chatId != null && chatId != activeChatId) {
                return
            }

            val senderRaw = msg["sender"] ?: return
            val sender = senderRaw.toString()
            if (sender == userId) return
            runOnUiThread {
                val incoming = msg.toMutableMap().apply { put("sender", sender) }
                val enriched = enrichIncoming(incoming)
                ChatState.messages.add(enriched)
                maybeSendVisualizada(enriched)
            }
        } catch (e: Exception) {
            runOnUiThread {
                UiDiagnostics.report(e, "WS handler (type=${msg["type"]})")
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

        runOnUiThread {
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
        }
    }

    private fun applyChatStatusChanged(msg: Map<String, Any?>) {
        val chatId = msg[ChatWs.KEY_CHAT_ID]?.toString() ?: return
        val ns = msg[ChatWs.KEY_NEW_STATUS]?.toString() ?: return
        runOnUiThread {
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
        val chatId = status["chatId"]?.toString() ?: return
        val msgId = status["msgId"]?.toString() ?: return
        val ds = status["deliveryStatus"]?.toString()?.trim()?.uppercase().orEmpty()
        val flagR = status["recebida"] == true
        val flagV = status["visualizada"] == true
        if (ds.isEmpty() && !flagR && !flagV) return
        for (i in ChatState.messages.indices) {
            val row = ChatState.messages[i]
            val rowMsg = row["msgId"]?.toString()
            val rowId = row["id"]?.toString()
            if (rowMsg != msgId && rowId != msgId) continue
            val rowChat = row["chatId"]?.toString()
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
            ChatState.messages.removeAt(i)
            ChatState.messages.add(i, next)
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
        val payload = mapOf(
            "type" to "messageStatus",
            "chatId" to chatId,
            "msgId" to msgId,
            "deliveryStatus" to "VISUALIZADA",
            "targetUserId" to from,
            "receiver" to from,
            "recebida" to true,
            "visualizada" to true,
        )
        wsManager.send(gson.toJson(payload))
    }

    private fun sendMessage(content: Map<String, Any?>) {
        val json = gson.toJson(content)
        if (!wsManager.send(json)) {
            runOnUiThread {
                UiDiagnostics.report(
                    IllegalStateException("send() retornou false (offline ou fila)"),
                    "Enviar mensagem WebSocket",
                )
                ChatState.messages.add(
                    mapOf(
                        "sender" to "system",
                        "content" to "Sem conexão WebSocket — mensagem não enviada.",
                    ),
                )
            }
            return
        }
        ChatState.messages.add(content)
    }
}
