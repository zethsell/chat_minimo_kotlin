package com.example.chat_minimo_kotlin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.chat_minimo_kotlin.states.ChatSummaryUi
import com.example.chat_minimo_kotlin.states.UiDiagnostics
import com.example.chat_minimo_kotlin.ui.components.ErrorLogBanner
import com.example.chat_minimo_kotlin.ui.pages.ChatListScreen
import com.example.chat_minimo_kotlin.ui.pages.ChatScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Demo **carteiro** → BFF `:9090` (Flutter cidadão → proxyapp `:9091`).
 *
 * Fluxo alvo: **LOEC** → busca **idCorreios por objeto** → este app **inicia** o chat (`POST /chat/sessoes` quando
 * integrado) e a **primeira mensagem**; o app **cidadão não cria sessão**, só lista + conversa existente.
 *
 * [userId] = matrícula (WS, historico por carteiro, unread). [idCorreiosCidadao] = cidadão (peer ao enviar / demo fixo).
 */
class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(
            application,
            getString(R.string.chat_bff_base_url).trimEnd('/'),
            "matricula_123",
            "idCorreios_123",
            listOf("AN123456789BR"),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.startTransport()

        setContent {
            val scope = rememberCoroutineScope()
            Box(Modifier.fillMaxSize()) {
                var selected by remember { mutableStateOf<ChatSummaryUi?>(null) }
                var novaBusy by remember { mutableStateOf(false) }

                val openChatId = selected?.chatId
                LaunchedEffect(openChatId) {
                    val id = openChatId ?: return@LaunchedEffect
                    viewModel.markAllRead(id)
                }

                if (selected == null) {
                    ChatListScreen(
                        onOpenChat = { summary ->
                            viewModel.activeChatId = summary.chatId
                            selected = summary
                            scope.launch {
                                viewModel.loadMessagesForChat(summary.chatId)
                            }
                        },
                        onRefresh = {
                            scope.launch {
                                viewModel.refreshChatList()
                            }
                        },
                        onNovaConversa = conv@{
                            if (novaBusy) return@conv
                            novaBusy = true
                            try {
                                val chatId = viewModel.bootstrapNewChatId()
                                viewModel.activeChatId = chatId
                                selected = ChatSummaryUi(
                                    chatId = chatId,
                                    peerId = viewModel.idCorreiosCidadao,
                                    title = viewModel.idCorreiosCidadao,
                                    lastMessage = "",
                                    lastMillis = System.currentTimeMillis(),
                                    unread = 0,
                                    status = "ABERTO",
                                )
                                viewModel.loadMessagesForChat(chatId)
                                viewModel.refreshChatList()
                            } catch (e: Exception) {
                                UiDiagnostics.report(
                                    e,
                                    "Nova conversa (pós-LOEC: historico + sessoes)",
                                )
                            } finally {
                                withContext(Dispatchers.Main.immediate) {
                                    novaBusy = false
                                }
                            }
                        },
                        novaConversaBusy = novaBusy,
                    )
                } else {
                    val sel = selected!!
                    ChatScreen(
                        userId = viewModel.userId,
                        receiverId = if (sel.peerId.isNotEmpty()) sel.peerId else viewModel.idCorreiosCidadao,
                        chatId = sel.chatId,
                        sessionLoading = false,
                        sessionError = null,
                        onSendMessage = { viewModel.sendMessage(it) },
                        onBack = {
                            viewModel.activeChatId = null
                            selected = null
                        },
                    )
                }

                ErrorLogBanner(Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}
