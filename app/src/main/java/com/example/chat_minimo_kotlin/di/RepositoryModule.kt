package com.example.chat_minimo_kotlin.di

import com.example.chat_minimo_kotlin.data.repository.AuthRepositoryImpl
import com.example.chat_minimo_kotlin.data.repository.ChatRepositoryImpl
import com.example.chat_minimo_kotlin.domain.repository.AuthRepository
import com.example.chat_minimo_kotlin.domain.repository.ChatRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun chatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun authRepository(impl: AuthRepositoryImpl): AuthRepository
}
