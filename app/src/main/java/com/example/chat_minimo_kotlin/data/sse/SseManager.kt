package com.example.chat_minimo_kotlin.data.sse

import com.example.chat_minimo_kotlin.core.session.AuthSessionHolder
import com.example.chat_minimo_kotlin.di.ApplicationScope
import java.io.EOFException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
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
 * Uma conexão SSE por app (OkHttp). Reconexão com backoff + jitter; cookie via [AuthSessionHolder].
 */
@Singleton
class SseManager @Inject constructor(
    @param:ApplicationScope private val scope: CoroutineScope,
    private val session: AuthSessionHolder,
) {

    private val client =
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(0, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .connectTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    @Volatile private var activeCall: Call? = null
    private var loopJob: Job? = null
    private var renewalJob: Job? = null

    @Volatile private var lastUrl: String? = null
    private var reconnectAttempt: Int = 0

    @Volatile private var renewRequested: Boolean = false

    @Volatile var onText: ((String) -> Unit)? = null

    @Volatile var onTransportError: ((Throwable) -> Unit)? = null

    @Volatile var onStreamOpen: (() -> Unit)? = null

    fun connect(url: String) {
        lastUrl = url
        loopJob?.cancel()
        renewalJob?.cancel()
        renewalJob = null
        try {
            activeCall?.cancel()
        } catch (_: Exception) {
        }
        renewalJob =
            scope.launch(Dispatchers.IO) {
                while (isActive && lastUrl != null) {
                    delay(TimeUnit.MINUTES.toMillis(30))
                    if (!isActive || lastUrl == null) break
                    try {
                        if (activeCall != null) {
                            renewRequested = true
                            activeCall?.cancel()
                        }
                    } catch (_: Exception) {
                    }
                }
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
                        val proactive = renewRequested
                        renewRequested = false
                        if (proactive) {
                            reconnectAttempt = 0
                            delay(300L + Random.nextLong(0, 500))
                            continue
                        }
                        val exp = reconnectAttempt.coerceAtMost(8)
                        val baseMs = min(60_000L, 500L * (1L shl exp))
                        val jitterCap = max(1L, min(2000L, baseMs / 2 + 1))
                        delay(baseMs + Random.nextLong(0, jitterCap))
                        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(20)
                    }
                }
            }
    }

    private suspend fun runSseOnce(url: String) =
        withContext(Dispatchers.IO) {
            val cookie = session.sessionCookie?.trim()?.takeIf { it.isNotEmpty() }
            val b =
                Request.Builder()
                    .url(url)
                    .header("Accept", "text/event-stream")
            if (cookie != null) {
                b.header("Cookie", cookie)
            }
            val request = b.build()
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
        renewalJob?.cancel()
        renewalJob = null
        loopJob?.cancel()
        loopJob = null
        try {
            activeCall?.cancel()
        } catch (_: Exception) {
        }
        activeCall = null
        reconnectAttempt = 0
        renewRequested = false
    }
}
