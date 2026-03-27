package com.example.chat_minimo_kotlin

import java.io.EOFException
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource

/**
 * Uma conexão SSE por app (OkHttp, sem limite de leitura). Reconexão com backoff + jitter.
 * Payloads JSON chegam nas mesmas linhas `data:` que a API emite após Redis.
 */
class SseManager(private val scope: CoroutineScope) {

    private val client =
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    @Volatile private var activeCall: Call? = null
    private var loopJob: Job? = null

    @Volatile private var lastUrl: String? = null
    private var reconnectAttempt: Int = 0

    @Volatile var onText: ((String) -> Unit)? = null

    @Volatile var onTransportError: ((Throwable) -> Unit)? = null

    /** Após HTTP 2xx, antes de ler linhas — útil para catch-up e fila outbound. */
    @Volatile var onStreamOpen: (() -> Unit)? = null

    fun connect(url: String) {
        lastUrl = url
        loopJob?.cancel()
        try {
            activeCall?.cancel()
        } catch (_: Exception) {
        }
        loopJob =
            scope.launch(Dispatchers.IO) {
                while (isActive && lastUrl != null) {
                    val u = lastUrl ?: break
                    try {
                        runSseOnce(u)
                        reconnectAttempt = 0
                        delay(1500L + Random.nextLong(0, 1000))
                    } catch (e: CancellationException) {
                        break
                    } catch (e: Exception) {
                        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(12)
                        val exp =
                            (2000L shl (reconnectAttempt - 1).coerceAtMost(6)).coerceAtMost(60_000L)
                        delay(exp + Random.nextLong(0, 2500))
                    }
                }
            }
    }

    private suspend fun runSseOnce(url: String) =
        withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url(url)
                    .header("Accept", "text/event-stream")
                    .build()
            val call = client.newCall(request)
            activeCall = call
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val msg = "SSE HTTP ${resp.code} ${resp.message}"
                    onTransportError?.invoke(IllegalStateException(msg))
                    throw IllegalStateException(msg)
                }
                reconnectAttempt = 0
                onStreamOpen?.invoke()
                val body = resp.body ?: return@use
                readSseEvents(body.source())
            }
        }

    private fun readSseEvents(source: BufferedSource) {
        val buf = StringBuilder()
        while (true) {
            val line =
                try {
                    source.readUtf8LineStrict()
                } catch (_: EOFException) {
                    break
                }
            when {
                line.isEmpty() -> {
                    if (buf.isNotEmpty()) {
                        val json = buf.toString()
                        buf.clear()
                        onText?.invoke(json)
                    }
                }
                line.startsWith(":") -> Unit
                line.startsWith("data:") -> {
                    val payload = line.substring(5).trimStart()
                    if (buf.isNotEmpty()) buf.append('\n')
                    buf.append(payload)
                }
            }
        }
    }

    fun shutdown() {
        lastUrl = null
        loopJob?.cancel()
        loopJob = null
        try {
            activeCall?.cancel()
        } catch (_: Exception) {
        }
        activeCall = null
        reconnectAttempt = 0
    }
}
