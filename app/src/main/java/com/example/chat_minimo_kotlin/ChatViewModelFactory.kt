package com.example.chat_minimo_kotlin

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ChatViewModelFactory(
    private val application: Application,
    private val apiBaseUrl: String,
    private val userId: String,
    private val idCorreiosCidadao: String,
    private val codigosObjeto: List<String>,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(
                application,
                apiBaseUrl,
                userId,
                idCorreiosCidadao,
                codigosObjeto,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
