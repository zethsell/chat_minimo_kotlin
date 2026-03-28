package com.example.chat_minimo_kotlin.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.chat_minimo_kotlin.domain.model.ChatInboxTab
import com.example.chat_minimo_kotlin.domain.model.ChatStatusBuckets
import com.example.chat_minimo_kotlin.domain.model.ChatSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val timeToday: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val timeOther: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    chats: List<ChatSummary>,
    onOpenChat: (ChatSummary) -> Unit,
    onRefresh: suspend () -> Unit,
    defaultIdCorreios: String = "",
    /**
     * Pós-LOEC: abre conversa com o cidadão [idCorreios].
     * Vários cidadãos distintos → várias conversas; se já existir linha **ativa** com o mesmo id, abre essa.
     */
    onNovaConversa: suspend (idCorreios: String) -> Unit,
    novaConversaBusy: Boolean = false,
    onLogout: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val tabs = ChatInboxTab.entries
    val tabTitles = listOf("Ativos", "Histórico")
    var tabIndex by remember { mutableIntStateOf(0) }
    val selectedTab = tabs[tabIndex]
    var showNovaConversaDialog by remember { mutableStateOf(false) }
    var draftIdCorreios by remember { mutableStateOf(defaultIdCorreios) }

    LaunchedEffect(Unit) {
        onRefresh()
    }

    if (showNovaConversaDialog) {
        AlertDialog(
            onDismissRequest = { if (!novaConversaBusy) showNovaConversaDialog = false },
            title = { Text("Nova conversa") },
            text = {
                OutlinedTextField(
                    value = draftIdCorreios,
                    onValueChange = { draftIdCorreios = it },
                    label = { Text("idCorreios") },
                    placeholder = { Text("Digite o idCorreios do cidadão") },
                    singleLine = true,
                    enabled = !novaConversaBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            onNovaConversa(draftIdCorreios.trim())
                            showNovaConversaDialog = false
                        }
                    },
                    enabled = !novaConversaBusy && draftIdCorreios.isNotBlank(),
                ) {
                    Text("Iniciar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNovaConversaDialog = false },
                    enabled = !novaConversaBusy,
                ) {
                    Text("Cancelar")
                }
            },
        )
    }

    fun openNovaConversaDialog() {
        draftIdCorreios = defaultIdCorreios
        showNovaConversaDialog = true
    }

    val filtered =
        chats
            .filter { ChatStatusBuckets.matchesTab(it.status, selectedTab) }
            .sortedByDescending { it.lastMillis }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Conversas") },
                    actions = {
                        TextButton(
                            onClick = { if (!novaConversaBusy) openNovaConversaDialog() },
                            enabled = !novaConversaBusy,
                        ) {
                            Text("Nova conversa")
                        }
                        TextButton(onClick = { scope.launch { onRefresh() } }) {
                            Text("Atualizar")
                        }
                        TextButton(onClick = onLogout) {
                            Text("Sair")
                        }
                    },
                )
                TabRow(selectedTabIndex = tabIndex) {
                    tabs.forEachIndexed { i, tab ->
                        val count = chats.count {
                            ChatStatusBuckets.matchesTab(it.status, tab)
                        }
                        Tab(
                            selected = tabIndex == i,
                            onClick = { tabIndex = i },
                            text = {
                                Text(
                                    "${tabTitles[i]} ($count)",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Nova conversa") },
                icon = {
                    Icon(Icons.Filled.AddComment, contentDescription = null)
                },
                onClick = {
                    if (!novaConversaBusy) {
                        openNovaConversaDialog()
                    }
                },
                expanded = true,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "Nenhuma conversa neste filtro.",
                            modifier = Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                items(filtered, key = { it.chatId }) { chat ->
                    ChatListRowWhatsApp(
                        chat = chat,
                        onClick = { onOpenChat(chat) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ChatListRowWhatsApp(chat: ChatSummary, onClick: () -> Unit) {
    val initial = chat.title.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val timeText = formatChatTime(chat.lastMillis)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(initial, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                chat.title,
                style = if (chat.unread > 0) {
                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                } else {
                    MaterialTheme.typography.titleMedium
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                chat.lastMessage.ifEmpty { "Sem mensagens" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (chat.unread > 0) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(timeText, style = MaterialTheme.typography.labelMedium)
            if (chat.unread > 0) {
                Spacer(modifier = Modifier.size(4.dp))
                Badge { Text("${chat.unread}") }
            }
        }
    }
}

private fun formatChatTime(millis: Long): String {
    if (millis <= 0L) return ""
    val z = ZoneId.systemDefault()
    val instant = Instant.ofEpochMilli(millis)
    val day = instant.atZone(z).toLocalDate()
    val today = LocalDate.now(z)
    return if (day == today) {
        timeToday.format(instant.atZone(z).toLocalTime())
    } else {
        timeOther.format(instant.atZone(z).toLocalDate())
    }
}
