package com.example.chat_minimo_kotlin.di

import javax.inject.Qualifier
import kotlin.annotation.AnnotationRetention
import kotlin.annotation.Retention

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ChatHttpClient

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class PlainOkHttpClient

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
