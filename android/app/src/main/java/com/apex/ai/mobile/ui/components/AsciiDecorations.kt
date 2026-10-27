package com.apex.ai.mobile.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.ai.mobile.ui.theme.AccentCyan
import com.apex.ai.mobile.ui.theme.BorderColor
import com.apex.ai.mobile.ui.theme.TextSecondary

@Composable
fun PanelHeaderAscii(
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("┌─ ", color = BorderColor, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Text(title, color = AccentCyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Text(" ─────────────────────────┐", color = BorderColor, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    }
}

@Composable
fun PanelFooterAscii(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text("└───────────────────────────────┘", color = BorderColor, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    }
}
