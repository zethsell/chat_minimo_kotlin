package com.example.chat_minimo_kotlin.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ChatBubble(text: String, isMine: Boolean) {

    val bg = if (isMine) Color(0xFF64B5F6) else Color(0xFFEEEEEE)
    val color = if (isMine) Color.White else Color.Black

    Box(
        modifier = Modifier
            .padding(4.dp)
            .background(
                bg,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
            .widthIn(max = 260.dp)
    ) {
        Text(text, color = color)
    }
}
