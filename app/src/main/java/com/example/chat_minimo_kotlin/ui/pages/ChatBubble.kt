package com.example.chat_minimo_kotlin.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.chat_minimo_kotlin.domain.model.ChatMessage

@Composable
fun ChatBubble(
    text: String,
    isMine: Boolean,
    msg: ChatMessage? = null,
) {
    val bgMine = Color(0xFF64B5F6)
    val bgOther = Color(0xFFE0E0E0)
    val textColor = Color(0xDD000000)

    Box(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .background(
                color = if (isMine) bgMine else bgOther,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = text,
                color = textColor,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.widthIn(max = 260.dp),
            )
            if (isMine && msg != null) {
                DeliveryTicksIcon(msg = msg)
            }
        }
    }
}

@Composable
private fun DeliveryTicksIcon(msg: ChatMessage) {
    val v = msg.visualizada
    val r = msg.recebida
    val color = when {
        v -> Color(0xFF0D47A1)
        r -> Color.White.copy(alpha = 0.85f)
        else -> Color.White.copy(alpha = 0.55f)
    }
    Icon(
        imageVector = if (v || r) Icons.Filled.DoneAll else Icons.Filled.Done,
        contentDescription = null,
        tint = color,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
    )
}
