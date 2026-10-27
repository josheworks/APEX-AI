package com.apex.ai.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.ai.mobile.model.ModelHealth
import com.apex.ai.mobile.ui.theme.AccentCyan
import com.apex.ai.mobile.ui.theme.AccentSuccess
import com.apex.ai.mobile.ui.theme.AccentWarning
import com.apex.ai.mobile.ui.theme.BgDeep
import com.apex.ai.mobile.ui.theme.BorderColor
import com.apex.ai.mobile.ui.theme.PanelBg
import com.apex.ai.mobile.ui.theme.TextPrimary
import com.apex.ai.mobile.ui.theme.TextSecondary
import com.apex.ai.mobile.ui.viewmodel.ApexUiState

@Composable
fun SettingsScreen(
    uiState: ApexUiState,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(12.dp)
            .verticalScroll(scrollState)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = AccentCyan
                )
            }
            Text(
                text = "APEX SYSTEM CONFIGURATION",
                color = AccentCyan,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 1. Local AI Model Status
        SettingsSection(title = "LOCAL AI MODEL (Mobile Action 270M)") {
            Text(
                text = "Model Identifier: google/functiongemma-270m-it",
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            Text(
                text = "Packaging: INT8 LiteRT Language Model (.litertlm)",
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            Text(
                text = "Runtime Status: ${uiState.modelStatus.name}",
                color = AccentSuccess,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            when (val health = uiState.modelHealth) {
                is ModelHealth.Healthy -> {
                    Text(
                        text = "Device Engine: ${health.device} | RAM: ${health.memoryUsedMb}MB",
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
                is ModelHealth.Degraded -> {
                    Text(
                        text = "Degraded: ${health.message} (${health.memoryUsedMb}MB)",
                        color = AccentWarning,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
                is ModelHealth.Offline -> {
                    Text(
                        text = "Offline: ${health.reason}",
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Permission & Safety Policy Engine
        SettingsSection(title = "POLICY ENGINE & RISK MATRIX") {
            Text(
                text = "• LOW RISK: Auto-executes after validation (e.g. Battery, Open App)",
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
            Text(
                text = "• MEDIUM RISK: Validates permissions (e.g. Camera, Storage, Brightness)",
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
            Text(
                text = "• HIGH RISK: Action-specific modal confirmation required (e.g. Send WhatsApp, Install APK)",
                color = AccentWarning,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
            Text(
                text = "• RESTRICTED: Covert capture & silent installation are strictly rejected.",
                color = AccentCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Registered Tools Browser
        SettingsSection(title = "TYPED TOOL REGISTRY (${uiState.availableTools.size} Tools)") {
            for (tool in uiState.availableTools.take(8)) {
                Text(
                    text = "• ${tool.name} [${tool.risk}] - ${tool.category}",
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
            if (uiState.availableTools.size > 8) {
                Text(
                    text = "... and ${uiState.availableTools.size - 8} more verified native tools.",
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Privacy & Audit Controls
        SettingsSection(title = "PRIVACY & DATA AUDIT") {
            Text(
                text = "No private conversations or device telemetry are sent to cloud servers.",
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = {},
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan)
            ) {
                Text("CLEAR AUDIT HISTORY", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 5. About
        SettingsSection(title = "ABOUT APEX MOBILE") {
            Text(
                text = "APEX AI — Advanced Personal Executive v0.1.0 Mobile",
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            Text(
                text = "Designed for local on-device operation with Google FunctionGemma 270M.",
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBg)
            .border(1.dp, BorderColor)
            .padding(12.dp)
    ) {
        Text(
            text = "┌─ $title",
            color = AccentCyan,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        content()
    }
}
