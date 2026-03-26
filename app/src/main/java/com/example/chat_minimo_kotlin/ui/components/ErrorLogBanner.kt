package com.example.chat_minimo_kotlin.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.chat_minimo_kotlin.states.UiDiagnostics

@Composable
fun ErrorLogBanner(modifier: Modifier = Modifier) {
    val msg = UiDiagnostics.lastError ?: return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFEBEE),
            contentColor = Color(0xFFB71C1C),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "Erro (toque em Limpar para fechar)",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .verticalScroll(rememberScrollState()),
            )
            TextButton(
                onClick = { UiDiagnostics.clear() },
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text("Limpar")
            }
        }
    }
}
