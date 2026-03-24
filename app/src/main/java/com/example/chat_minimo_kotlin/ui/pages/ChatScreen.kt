package com.example.chat_minimo_kotlin.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.chat_minimo_kotlin.states.ChatState
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    userId: String,
    receiverId: String,
    chatId: String? = null,
    sessionLoading: Boolean = false,
    sessionError: String? = null,
    onSendMessage: (Map<String, Any?>) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val messages = ChatState.messages

    val subtitle = when {
        sessionLoading -> "Abrindo sessão…"
        chatId != null -> "chatId: $chatId"
        sessionError != null -> sessionError
        else -> receiverId
    }

    fun scrollToBottom() {
        scope.launch {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Chat")
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                itemsIndexed(
                    items = messages,
                    key = { index, msg ->
                        msg["msgId"]?.toString() ?: "row-$index-${msg["content"]}"
                    },
                ) { _, msg ->
                    val sender = msg["sender"] as? String ?: "system"
                    val text = msg["content"] as? String ?: ""
                    val isMine = sender == userId

                    key(msg["msgId"], msg["recebida"], msg["visualizada"]) {
                        if (sender == "system") {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    if (isMine) Arrangement.End else Arrangement.Start,
                            ) {
                                ChatBubble(text = text, isMine = isMine, msg = msg)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Digite a mensagem...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                IconButton(
                    onClick = {
                        if (input.isNotBlank() && chatId != null) {
                            val msg: Map<String, Any?> = buildMap {
                                put("msgId", UUID.randomUUID().toString())
                                put("chatId", chatId)
                                put("sender", userId)
                                put("receiver", receiverId)
                                put("content", input)
                                put("timestamp", System.currentTimeMillis())
                                put("recebida", false)
                                put("visualizada", false)
                            }
                            onSendMessage(msg)
                            input = ""
                            scrollToBottom()
                        }
                    },
                    enabled = chatId != null && !sessionLoading,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Enviar",
                        tint = Color(0xFF2196F3),
                    )
                }
            }
        }
    }
}
