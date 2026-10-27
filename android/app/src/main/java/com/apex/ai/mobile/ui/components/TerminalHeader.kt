package com.apex.ai.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.ai.mobile.model.ModelRuntimeStatus
import com.apex.ai.mobile.ui.theme.AccentCyan
import com.apex.ai.mobile.ui.theme.AccentError
import com.apex.ai.mobile.ui.theme.AccentProcessing
import com.apex.ai.mobile.ui.theme.AccentSuccess
import com.apex.ai.mobile.ui.theme.BgDeep
import com.apex.ai.mobile.ui.theme.BorderColor
import com.apex.ai.mobile.ui.theme.PanelBg
import com.apex.ai.mobile.ui.theme.TextPrimary
import com.apex.ai.mobile.ui.theme.TextSecondary

@Composable
fun TerminalHeader(
    modelStatus: ModelRuntimeStatus,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PanelBg)
            .border(1.dp, BorderColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                ">",
                color = AccentCyan,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "APEX AI SYSTEM",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            val statusColor = when (modelStatus) {
                ModelRuntimeStatus.READY -> AccentSuccess
                ModelRuntimeStatus.INFERRING -> AccentProcessing
                ModelRuntimeStatus.LOADING -> AccentCyan
                ModelRuntimeStatus.ERROR -> AccentError
                else -> TextSecondary
            }

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "v0.1",
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
