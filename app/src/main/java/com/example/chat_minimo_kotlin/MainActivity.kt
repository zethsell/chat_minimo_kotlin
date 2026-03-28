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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chat_minimo_kotlin.core.session.AuthSessionHolder
import com.example.chat_minimo_kotlin.domain.model.ChatSummary
import com.example.chat_minimo_kotlin.domain.model.OutgoingChatText
import com.example.chat_minimo_kotlin.domain.repository.AuthRepository
import com.example.chat_minimo_kotlin.presentation.chat.ChatViewModel
import com.example.chat_minimo_kotlin.states.UiDiagnostics
import com.example.chat_minimo_kotlin.ui.components.ErrorLogBanner
import com.example.chat_minimo_kotlin.ui.pages.ChatListScreen
import com.example.chat_minimo_kotlin.ui.pages.ChatScreen
import com.example.chat_minimo_kotlin.ui.pages.LoginScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    @Inject
    lateinit var sessionHolder: AuthSessionHolder

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var loggedIn by remember {
                mutableStateOf(!sessionHolder.sessionCookie.isNullOrBlank())
            }
            val scope = rememberCoroutineScope()

            LaunchedEffect(loggedIn) {
                if (loggedIn) {
                    viewModel.startTransport()
                }
            }

            Box(Modifier.fillMaxSize()) {
                if (!loggedIn) {
                    LoginScreen(
                        defaultAuthBaseUrl = getString(R.string.chat_auth_base_url).trimEnd('/'),
                        authRepository = authRepository,
                        onLoggedIn = { cookie, usuario, authBaseUrl ->
                            sessionHolder.updateSession(cookie, usuario, authBaseUrl)
                            loggedIn = true
                        },
                    )
                    ErrorLogBanner(Modifier.align(Alignment.BottomCenter))
                    return@Box
                }

                var selected by remember { mutableStateOf<ChatSummary?>(null) }
                var novaBusy by remember { mutableStateOf(false) }

                val openChatId = selected?.chatId
                LaunchedEffect(openChatId) {
                    val id = openChatId ?: return@LaunchedEffect
                    viewModel.markAllRead(id)
                }

                fun doLogout() {
                    viewModel.prepareForLogout()
                    sessionHolder.updateSession(null)
                    loggedIn = false
                }

                if (selected == null) {
                    val chats by viewModel.chats.collectAsStateWithLifecycle()
                    ChatListScreen(
                        chats = chats,
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
                        onNovaConversa = conv@{ idCorreiosInformed ->
                            if (novaBusy) return@conv
                            novaBusy = true
                            try {
                                val peer = idCorreiosInformed.trim()
                                if (peer.isEmpty()) return@conv
                                val existing = viewModel.findActiveChatForCitizen(peer)
                                if (existing != null) {
                                    viewModel.activeChatId = existing.chatId
                                    selected = existing
                                    scope.launch {
                                        viewModel.loadMessagesForChat(existing.chatId)
                                    }
                                    return@conv
                                }
                                val chatId = viewModel.bootstrapNewChatId(peer)
                                viewModel.activeChatId = chatId
                                selected =
                                    ChatSummary(
                                        chatId = chatId,
                                        peerId = peer,
                                        title = peer,
                                        lastMessage = "",
                                        lastMillis = System.currentTimeMillis(),
                                        unread = 0,
                                        status = "ABERTO",
                                    )
                                scope.launch {
                                    viewModel.loadMessagesForChat(chatId)
                                    viewModel.refreshChatList()
                                }
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
                        onLogout = ::doLogout,
                    )
                } else {
                    val sel = selected!!
                    val messages by viewModel.messages.collectAsStateWithLifecycle()
                    ChatScreen(
                        userId = viewModel.userId,
                        receiverId = sel.peerId,
                        chatId = sel.chatId,
                        sessionLoading = false,
                        sessionError = null,
                        messages = messages,
                        onSendMessage = { content ->
                            viewModel.sendMessage(
                                OutgoingChatText(
                                    chatId = sel.chatId,
                                    receiverId = sel.peerId,
                                    content = content,
                                ),
                            )
                        },
                        onBack = {
                            viewModel.activeChatId = null
                            selected = null
                        },
                        onLogout = ::doLogout,
                    )
                }

                ErrorLogBanner(Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}
