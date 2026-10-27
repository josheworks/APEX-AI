package com.apex.ai.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.ai.mobile.registry.ToolExecutionResult
import com.apex.ai.mobile.ui.theme.AccentCyan
import com.apex.ai.mobile.ui.theme.AccentError
import com.apex.ai.mobile.ui.theme.AccentProcessing
import com.apex.ai.mobile.ui.theme.AccentSuccess
import com.apex.ai.mobile.ui.theme.BorderColor
import com.apex.ai.mobile.ui.theme.PanelBg
import com.apex.ai.mobile.ui.theme.TextPrimary
import com.apex.ai.mobile.ui.theme.TextSecondary

@Composable
fun ToolStatusCard(
    toolName: String,
    isRunning: Boolean,
    result: ToolExecutionResult?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(PanelBg)
            .border(1.dp, BorderColor)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "⚙ ",
                    color = AccentProcessing,
                    fontSize = 12.sp
                )
                Text(
                    text = toolName,
                    color = AccentCyan,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }

            if (isRunning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(12.dp).height(12.dp),
                        color = AccentProcessing,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "EXECUTING",
                        color = AccentProcessing,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            } else if (result != null) {
                val (statusText, statusColor) = when (result) {
                    is ToolExecutionResult.Success -> Pair("SUCCESS", AccentSuccess)
                    is ToolExecutionResult.ConfirmationRequired -> Pair("AWAITING CONFIRMATION", AccentCyan)
                    is ToolExecutionResult.PermissionRequired -> Pair("PERMISSION NEEDED", AccentError)
                    is ToolExecutionResult.Rejected -> Pair("REJECTED", AccentError)
                    is ToolExecutionResult.Unsupported -> Pair("UNSUPPORTED", TextSecondary)
                    is ToolExecutionResult.Failed -> Pair("FAILED", AccentError)
                }
                Text(
                    text = statusText,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
        }

        if (result is ToolExecutionResult.Success) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = result.message,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        } else if (result is ToolExecutionResult.Failed) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = result.errorMessage,
                color = AccentError,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
    }
}
