package com.example.chat_minimo_kotlin.di

import com.example.chat_minimo_kotlin.core.session.AuthSessionHolder
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

class SessionCookieInterceptor @Inject constructor(
    private val session: AuthSessionHolder,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val cookie = session.sessionCookie?.trim()?.takeIf { it.isNotEmpty() }
        val req =
            if (cookie != null) {
                chain.request().newBuilder().header("Cookie", cookie).build()
            } else {
                chain.request()
            }
        return chain.proceed(req)
    }
}
