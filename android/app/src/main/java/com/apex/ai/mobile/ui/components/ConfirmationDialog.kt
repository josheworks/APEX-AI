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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.apex.ai.mobile.policy.PendingConfirmation
import com.apex.ai.mobile.ui.theme.AccentCyan
import com.apex.ai.mobile.ui.theme.AccentError
import com.apex.ai.mobile.ui.theme.AccentWarning
import com.apex.ai.mobile.ui.theme.BgDeep
import com.apex.ai.mobile.ui.theme.BorderColor
import com.apex.ai.mobile.ui.theme.PanelBg
import com.apex.ai.mobile.ui.theme.TextPrimary
import com.apex.ai.mobile.ui.theme.TextSecondary

@Composable
fun ConfirmationDialog(
    pendingConfirmation: PendingConfirmation,
    onConfirm: (String) -> Unit,
    onCancel: (String) -> Unit
) {
    Dialog(onDismissRequest = { onCancel(pendingConfirmation.actionId) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelBg)
                .border(1.dp, AccentWarning)
                .padding(16.dp)
        ) {
            Text(
                text = "┌─ SECURITY CONFIRMATION ─┐",
                color = AccentWarning,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "ACTION: ${pendingConfirmation.toolName}",
                color = AccentCyan,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "TARGET: ${pendingConfirmation.targetSummary}",
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )

            if (pendingConfirmation.arguments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "PARAMETERS:",
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
                for ((k, v) in pendingConfirmation.arguments) {
                    Text(
                        text = " • $k: $v",
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "CONSEQUENCES:",
                color = AccentWarning,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
            Text(
                text = pendingConfirmation.consequences,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = { onCancel(pendingConfirmation.actionId) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentError)
                ) {
                    Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(pendingConfirmation.actionId) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentWarning, contentColor = BgDeep)
                ) {
                    Text("CONFIRM ACTION", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}
