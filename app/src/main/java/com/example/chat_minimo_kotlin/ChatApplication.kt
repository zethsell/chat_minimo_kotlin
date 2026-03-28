package com.example.chat_minimo_kotlin

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ChatApplication : Application() {

    override fun onCreate() {
        ChatTokenStore.init(this)
        super.onCreate()
    }
}
