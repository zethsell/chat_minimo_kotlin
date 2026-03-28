package com.example.chat_minimo_kotlin

import android.content.Context
import android.content.SharedPreferences

/** Persiste cookie BFF, usuário e base URL após login. */
object ChatTokenStore {

    private const val PREF_NAME = "chat_minimo_auth"
    private const val KEY_SESSION_COOKIE = "bff_session_cookie"
    private const val KEY_USUARIO = "bff_usuario"
    private const val KEY_BFF_BASE = "bff_api_base_url"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        val app = context.applicationContext ?: context
        prefs = app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun read(): String? = prefs.getString(KEY_SESSION_COOKIE, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun readBffBaseUrl(): String? =
        prefs.getString(KEY_BFF_BASE, null)?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }

    fun save(sessionCookie: String, usuario: String? = null, bffBaseUrl: String? = null) {
        val e = prefs.edit().putString(KEY_SESSION_COOKIE, sessionCookie.trim())
        if (usuario != null) {
            e.putString(KEY_USUARIO, usuario.trim())
        }
        if (bffBaseUrl != null) {
            e.putString(KEY_BFF_BASE, bffBaseUrl.trim().trimEnd('/'))
        }
        e.apply()
    }

    fun readUsuario(): String? = prefs.getString(KEY_USUARIO, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun clear() {
        prefs.edit().remove(KEY_SESSION_COOKIE).remove(KEY_USUARIO).remove(KEY_BFF_BASE).apply()
    }
}
