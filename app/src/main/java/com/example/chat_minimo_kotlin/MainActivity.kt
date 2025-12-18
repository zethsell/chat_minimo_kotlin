package com.example.chat_minimo_kotlin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.example.chat_minimo_kotlin.states.ChatState
import com.example.chat_minimo_kotlin.ui.pages.ChatScreen
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val gson = Gson()
    private val client = OkHttpClient()

    private var webSocket: WebSocket? = null
    private val userId = "kotlin_123"
    private val receiverId = "flutter_123"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ChatScreen(
                userId = userId,
                receiverId = receiverId,
                onSendMessage = { msg -> sendMessage(msg) }
            )
        }

        connect()
    }

    // -----------------------------
    // WEBSOCKET
    // -----------------------------
    private fun connect() {

        val request = Request.Builder()
            .url("ws://10.0.2.2:9090/ws/chat?userId=$userId")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                ChatState.messages.add(
                    mapOf(
                        "sender" to "system",
                        "content" to "🟢 Conectado!"
                    )
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = gson.fromJson(text, Map::class.java) as Map<String, Any>

                // NÃO adicionar mensagens enviadas por mim
                if (msg["sender"] != userId) {
                    ChatState.messages.add(msg)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val text = bytes.utf8()
                val msg = gson.fromJson(text, Map::class.java) as Map<String, Any>
                ChatState.messages.add(msg)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                ChatState.messages.add(
                    mapOf(
                        "sender" to "system",
                        "content" to "❌ Falha: ${t.localizedMessage}"
                    )
                )
                reconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                ChatState.messages.add(
                    mapOf(
                        "sender" to "system",
                        "content" to "🔴 Desconectado"
                    )
                )
                reconnect()
            }
        })
    }

    private fun reconnect() {
        lifecycleScope.launch {
            delay(3000)
            connect()
        }
    }

    // -----------------------------
    // ENVIO DE MENSAGENS
    // -----------------------------
    private fun sendMessage(content: Map<String, Any?>) {
        val json = gson.toJson(content)
        webSocket?.send(json)
        ChatState.messages.add(content)
    }
}