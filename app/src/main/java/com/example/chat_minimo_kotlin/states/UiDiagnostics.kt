package com.example.chat_minimo_kotlin.states

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Último erro visível na UI (debug / dispositivo real sem depender só do Logcat).
 */
object UiDiagnostics {
    private const val TAG = "chat_minimo_diag"

    var lastError: String? by mutableStateOf(null)
        private set

    fun report(throwable: Throwable, context: String = "") {
        Log.e(TAG, context.ifEmpty { "erro" }, throwable)
        lastError = buildString {
            if (context.isNotEmpty()) {
                append(context)
                append('\n')
            }
            append(throwable.javaClass.simpleName)
            append(": ")
            append(throwable.message ?: "(sem mensagem)")
            throwable.cause?.let { c ->
                append("\n→ ")
                append(c.javaClass.simpleName)
                append(": ")
                append(c.message ?: "")
            }
        }
    }

    fun reportMessage(msg: String) {
        Log.e(TAG, msg)
        lastError = msg
    }

    fun clear() {
        lastError = null
    }
}
