package com.example.chat_minimo_kotlin.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.chat_minimo_kotlin.states.ChatState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    userId: String,
    receiverId: String,
    onSendMessage: (Map<String, Any?>) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    fun scrollToBottom() {
        scope.launch {
            delay(100)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Chat - $receiverId") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(12.dp)
                .fillMaxSize()
        ) {

            // LISTA DE MENSAGENS
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                ChatState.messages.forEach { msg ->

                    val sender = msg["sender"] as? String ?: "system"
                    val text = msg["content"] as? String ?: ""
                    val isMine = sender == userId

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            if (isMine) Arrangement.End else Arrangement.Start
                    ) {
                        ChatBubble(text, isMine)
                    }
                }
            }

            // CAMPO DE TEXTO
            Row {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Digite a mensagem...") },
                    singleLine = true,
                    // se quiser tratar IME depois: keyboardOptions/keyboardActions
                )

                Spacer(Modifier.width(8.dp))

                Button(onClick = {
                    if (input.isNotBlank()) {

                        val msg: Map<String, Any?> = mapOf(
                            "msgId" to null,
                            "chatId" to "test_chat",
                            "sender" to userId,
                            "receiver" to receiverId,
                            "content" to input,
                            "timestamp" to System.currentTimeMillis(),
                            "ack" to false,
                        )

                        // envia para o websocket
                        onSendMessage(msg)

                        // adiciona localmente igual Flutter
//                        ChatState.messages.add(msg)

                        input = ""
                        scrollToBottom()
                    }
                }) {
                    Text("Enviar")
                }
            }
        }
    }
}
