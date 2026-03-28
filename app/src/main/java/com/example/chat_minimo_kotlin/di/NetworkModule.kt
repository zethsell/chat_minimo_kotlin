package com.example.chat_minimo_kotlin.di

import com.example.chat_minimo_kotlin.data.remote.api.BffAuthApi
import com.example.chat_minimo_kotlin.data.remote.api.BffChatApi
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** Base fixa exigida pelo Retrofit; chamadas usam [@Url] absoluto (host da sessão). */
    private const val UNUSED_RETROFIT_BASE = "https://unused.invalid/"

    @Provides
    @Singleton
    fun gson(): Gson = Gson()

    @Provides
    @Singleton
    @PlainOkHttpClient
    fun plainOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @ChatHttpClient
    fun chatOkHttpClient(interceptor: SessionCookieInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(interceptor)
            .build()

    @Provides
    @Singleton
    fun bffChatApi(
        @ChatHttpClient client: OkHttpClient,
        gson: Gson,
    ): BffChatApi =
        Retrofit.Builder()
            .baseUrl(UNUSED_RETROFIT_BASE)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(BffChatApi::class.java)

    @Provides
    @Singleton
    fun bffAuthApi(
        @PlainOkHttpClient client: OkHttpClient,
        gson: Gson,
    ): BffAuthApi =
        Retrofit.Builder()
            .baseUrl(UNUSED_RETROFIT_BASE)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(BffAuthApi::class.java)
}
