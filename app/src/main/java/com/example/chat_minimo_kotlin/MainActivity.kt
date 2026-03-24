package com.example.chat_minimo_kotlin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.example.chat_minimo_kotlin.states.ChatState
import com.example.chat_minimo_kotlin.ui.pages.ChatScreen
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class MainActivity : ComponentActivity() {

    private val gson = Gson()
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val userId = "matricula_123"
    private val receiverId = "idCorreios_123"
    /** message-server-api no host (emulador → 10.0.2.2). */
    private val apiBaseUrl = "http://10.0.2.2:9641"
    private val codigosObjeto = listOf("AN123456789BR")
    private val visualizadaEnviada = mutableSetOf<String>()
    private var reconnectJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val chatSessionState = mutableStateOf<String?>(null)
        val sessionLoadingState = mutableStateOf(true)
        val sessionErrorState = mutableStateOf<String?>(null)

        setContent {
            val chatId by chatSessionState
            val sessionLoading by sessionLoadingState
            val sessionError by sessionErrorState
            ChatScreen(
                userId = userId,
                receiverId = receiverId,
                chatId = chatId,
                sessionLoading = sessionLoading,
                sessionError = sessionError,
                onSendMessage = { msg -> sendMessage(msg) },
            )
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val id = ChatSessionBootstrap.ensureChatId(
                    client = client,
                    gson = gson,
                    baseUrl = apiBaseUrl,
                    idCorreios = receiverId,
                    codigosObjeto = codigosObjeto,
                    carteiroId = userId,
                )
                val raw = ChatHistoryApi.fetchMensagens(client, gson, apiBaseUrl, id)
                val normalized = raw.map { ChatHistoryApi.normalizeRow(it) }
                runOnUiThread {
                    chatSessionState.value = id
                    sessionErrorState.value = null
                    ChatState.messages.clear()
                    ChatState.messages.addAll(normalized)
                    sessionLoadingState.value = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    sessionLoadingState.value = false
                    sessionErrorState.value = e.message ?: e.toString()
                    ChatState.messages.clear()
                    ChatState.messages.add(
                        mapOf(
                            "sender" to "system",
                            "content" to "Falha ao abrir sessão ou histórico: ${e.message}",
                        ),
                    )
                }
            }
        }

        connect()
    }

    override fun onDestroy() {
        reconnectJob?.cancel()
        reconnectJob = null
        try {
            webSocket?.close(1000, "activity destroyed")
        } catch (_: Exception) {
        }
        webSocket = null
        super.onDestroy()
    }

    private fun connect() {
        reconnectJob?.cancel()
        try {
            webSocket?.close(1000, "reconnect")
        } catch (_: Exception) {
        }
        webSocket = null

        val request = Request.Builder()
            .url("ws://10.0.2.2:9090/ws?userId=$userId")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleWsPayload(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleWsPayload(bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                reconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                reconnect()
            }
        })
    }

    private fun handleWsPayload(text: String) {
        if (text.isBlank()) return
        val msg = gson.fromJson(text, Map::class.java) as Map<String, Any?>
        val type = msg["type"] as? String
        if (type?.equals("PONG", ignoreCase = true) == true) return
        if (type?.equals("messageStatus", ignoreCase = true) == true) {
            runOnUiThread { applyMessageStatus(msg) }
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
    }

    private fun enrichIncoming(msg: Map<String, Any?>): Map<String, Any?> {
        val m = msg.toMutableMap()
        if (m["recebida"] == null) m["recebida"] = false
        if (m["visualizada"] == null) m["visualizada"] = false
        return m
    }

    private fun applyMessageStatus(status: Map<String, Any?>) {
        val chatId = status["chatId"]?.toString() ?: return
        val msgId = status["msgId"]?.toString() ?: return
        val ds = status["deliveryStatus"]?.toString()?.uppercase().orEmpty()
        val flagR = status["recebida"] == true
        val flagV = status["visualizada"] == true
        if (ds.isEmpty() && !flagR && !flagV) return
        for (i in ChatState.messages.indices) {
            val row = ChatState.messages[i]
            if (row["msgId"]?.toString() != msgId || row["chatId"]?.toString() != chatId) continue
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
        val chatId = incoming["chatId"]?.toString() ?: return
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
        webSocket?.send(gson.toJson(payload))
    }

    private fun reconnect() {
        reconnectJob?.cancel()
        reconnectJob = lifecycleScope.launch {
            delay(3000)
            connect()
        }
    }

    private fun sendMessage(content: Map<String, Any?>) {
        val ws = webSocket
        val json = gson.toJson(content)
        if (ws == null) {
            runOnUiThread {
                ChatState.messages.add(
                    mapOf(
                        "sender" to "system",
                        "content" to "Sem conexão WebSocket — mensagem não enviada.",
                    ),
                )
            }
            return
        }
        val ok = ws.send(json)
        if (!ok) {
            runOnUiThread {
                ChatState.messages.add(
                    mapOf(
                        "sender" to "system",
                        "content" to "Fila WS cheia — tente de novo.",
                    ),
                )
            }
            return
        }
        ChatState.messages.add(content)
    }
}
