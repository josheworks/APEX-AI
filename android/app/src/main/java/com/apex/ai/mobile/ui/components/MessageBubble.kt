package com.apex.ai.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.ai.mobile.ui.theme.AccentCyan
import com.apex.ai.mobile.ui.theme.AccentSuccess
import com.apex.ai.mobile.ui.theme.BorderColor
import com.apex.ai.mobile.ui.theme.PanelBg
import com.apex.ai.mobile.ui.theme.TextPrimary
import com.apex.ai.mobile.ui.theme.TextSecondary

data class ChatMessage(
    val id: String,
    val sender: String, // "user" or "apex"
    val content: String,
    val timestamp: String,
    val toolCallName: String? = null
)

@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.sender == "user"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row {
            Text(
                text = if (isUser) "> " else "APEX: ",
                color = if (isUser) AccentCyan else AccentSuccess,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
            Text(
                text = message.content,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun WelcomeCard(
    timeString: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(PanelBg)
            .border(1.dp, BorderColor)
            .padding(12.dp)
    ) {
        Column {
            Row {
                Text(
                    text = timeString,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "[SYSTEM ONLINE]",
                    color = AccentCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "> APEX is online",
                color = AccentSuccess,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                text = "> What can I do for you, Boss?",
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
    }
}
