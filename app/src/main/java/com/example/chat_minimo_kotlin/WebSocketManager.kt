package com.example.chat_minimo_kotlin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.ArrayDeque

/**
 * Singleton via [ChatApplication] — uma conexão WS por app; reconexão com delay.
 * Fila de envio quando offline (D-08 / checklist demo).
 */
class WebSocketManager(private val scope: CoroutineScope) {

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var lastUrl: String? = null
    private val pendingOutbound = ArrayDeque<String>(32)

    @Volatile
    var onText: ((String) -> Unit)? = null

    /** Chamado em thread OkHttp; quem consome deve postar na Main se for atualizar UI. */
    @Volatile
    var onTransportError: ((Throwable, Response?) -> Unit)? = null

    fun connect(wsUrl: String) {
        lastUrl = wsUrl
        reconnectJob?.cancel()
        try {
            webSocket?.close(1000, "reconnect")
        } catch (_: Exception) {
        }
        webSocket = null

        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    flushPendingOutbound()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    onText?.invoke(text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    onText?.invoke(bytes.utf8())
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    try {
                        onTransportError?.invoke(t, response)
                    } catch (_: Exception) {
                    }
                    scheduleReconnect()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    scheduleReconnect()
                }
            },
        )
    }

    private fun flushPendingOutbound() {
        val ws = webSocket ?: return
        while (pendingOutbound.isNotEmpty()) {
            val msg = pendingOutbound.first()
            try {
                if (!ws.send(msg)) break
                pendingOutbound.removeFirst()
            } catch (_: Exception) {
                break
            }
        }
    }

    private fun scheduleReconnect() {
        val url = lastUrl ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(3000)
            connect(url)
        }
    }

    fun send(json: String): Boolean {
        val ws = webSocket
        if (ws == null) {
            pendingOutbound.addLast(json)
            return false
        }
        return try {
            val ok = ws.send(json)
            if (!ok) pendingOutbound.addLast(json)
            ok
        } catch (_: Exception) {
            pendingOutbound.addLast(json)
            false
        }
    }

    fun shutdown() {
        reconnectJob?.cancel()
        reconnectJob = null
        try {
            webSocket?.close(1000, "shutdown")
        } catch (_: Exception) {
        }
        webSocket = null
        pendingOutbound.clear()
    }
}
