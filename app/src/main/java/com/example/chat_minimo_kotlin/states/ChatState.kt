package com.example.chat_minimo_kotlin.states

import androidx.compose.runtime.mutableStateListOf

object ChatState {
    val messages = mutableStateListOf<Map<String, Any?>>()
}