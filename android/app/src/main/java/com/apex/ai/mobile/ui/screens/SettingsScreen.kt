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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.material3.LinearProgressIndicator
import com.apex.ai.mobile.model.ModelImportStatus
import androidx.compose.material3.Slider
import com.apex.ai.mobile.ui.theme.TextSecondary
import com.apex.ai.mobile.ui.viewmodel.ApexUiState
import com.apex.ai.mobile.voice.VoiceAssistantConfig
import com.apex.ai.mobile.voice.VoiceInteractionMode
import java.util.Locale

@Composable
fun SettingsScreen(
    uiState: ApexUiState,
    onReloadModel: () -> Unit = {},
    onImportModel: (Uri) -> Unit = {},
    onUpdateVoiceConfig: (VoiceAssistantConfig) -> Unit = {},
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    // SAF OpenDocument launcher for selecting .litertlm model from Downloads
    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportModel(uri)
        }
    }

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
        SettingsSection(title = "LOCAL AI MODEL (Mobile Actions FunctionGemma 270M)") {
            Text(
                text = "Model Identifier: litert-community/functiongemma-270m-ft-mobile-actions",
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            Text(
                text = "Target Artifact: mobile_actions_q8_ekv1024.litertlm",
                color = AccentCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            Text(
                text = "Runtime Engine: Google LiteRT / MediaPipe GenAI",
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )

            val statusColor = when (uiState.modelStatus) {
                com.apex.ai.mobile.model.ModelRuntimeStatus.READY -> AccentSuccess
                com.apex.ai.mobile.model.ModelRuntimeStatus.MODEL_MISSING -> AccentWarning
                com.apex.ai.mobile.model.ModelRuntimeStatus.LOADING -> AccentCyan
                com.apex.ai.mobile.model.ModelRuntimeStatus.INFERRING -> AccentCyan
                else -> AccentWarning
            }
            Text(
                text = "Runtime Status: ${uiState.modelStatus.name}",
                color = statusColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )

            when (val health = uiState.modelHealth) {
                is ModelHealth.Healthy -> {
                    Text(
                        text = "Engine: ${health.device} | RAM: ${health.memoryUsedMb}MB",
                        color = AccentSuccess,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Inference Mode: Genuine Offline AI Active",
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
                is ModelHealth.Degraded -> {
                    Text(
                        text = "Status: ${health.message} (${health.memoryUsedMb}MB)",
                        color = AccentWarning,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
                is ModelHealth.Offline -> {
                    Text(
                        text = "Engine State: Offline (${health.reason})",
                        color = AccentWarning,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }

            uiState.modelErrorInfo?.let { err ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Expected Location:\n${err.expectedPath}",
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
            }

            uiState.activeModelPath?.let { activePath ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Active Model Path:\n$activePath",
                    color = AccentCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
            }

            // Model Import Progress / Status Display
            when (val importState = uiState.modelImportStatus) {
                is ModelImportStatus.Importing -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "IMPORTING MODEL: ${(importState.progress * 100).toInt()}% (${importState.bytesCopied / (1024 * 1024)}MB / ${if (importState.totalBytes > 0) "${importState.totalBytes / (1024 * 1024)}MB" else "..."})",
                        color = AccentCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { importState.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = AccentCyan,
                        trackColor = BorderColor
                    )
                }
                is ModelImportStatus.Success -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "IMPORT SUCCESSFUL: ${importState.sizeBytes / (1024 * 1024)}MB copied to files/models/. Reloading engine...",
                        color = AccentSuccess,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                is ModelImportStatus.Error -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "IMPORT ERROR: ${importState.message}",
                        color = AccentWarning,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (importState.resolutionGuide.isNotBlank()) {
                        Text(
                            text = importState.resolutionGuide,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                    }
                }
                is ModelImportStatus.Idle -> { /* Idle */ }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button 1: SAF Document Picker for model import
                Button(
                    onClick = {
                        modelPickerLauncher.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = "IMPORT MODEL FROM DOWNLOADS",
                        color = AccentCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Button 2: Manual Check / Reload
                OutlinedButton(
                    onClick = onReloadModel,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan)
                ) {
                    Text(
                        text = "RELOAD ENGINE",
                        color = AccentCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Setup: Tap 'IMPORT MODEL FROM DOWNLOADS' to select mobile_actions_q8_ekv1024.litertlm from your Vivo phone's Downloads. APEX will safely copy it to private storage (files/models/) and start local AI.",
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Voice & Multilingual Assistant Configuration
        SettingsSection(title = "VOICE ASSISTANT (OFFLINE-FIRST)") {
            Text(
                text = "Language Input: English, Tamil, Tanglish (Mixed)",
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            Text(
                text = "Language Output Policy: ALWAYS ENGLISH (Spoken + Display)",
                color = AccentSuccess,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            Text(
                text = "STT Engines: Whisper.cpp Multilingual / Android Native Offline STT",
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
            Text(
                text = "TTS Engine: Android Native English TextToSpeech",
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Interaction mode toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Interaction Mode: ${uiState.voiceConfig.interactionMode.name}",
                    color = AccentCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = {
                        val nextMode = if (uiState.voiceConfig.interactionMode == VoiceInteractionMode.PUSH_TO_TALK)
                            VoiceInteractionMode.CONTINUOUS else VoiceInteractionMode.PUSH_TO_TALK
                        onUpdateVoiceConfig(uiState.voiceConfig.copy(interactionMode = nextMode))
                    }
                ) {
                    Text(
                        text = "TOGGLE MODE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Speech Rate Slider
            Text(
                text = "Speech Rate: ${String.format(Locale.US, "%.1fx", uiState.voiceConfig.speechRate)}",
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
            Slider(
                value = uiState.voiceConfig.speechRate,
                onValueChange = { onUpdateVoiceConfig(uiState.voiceConfig.copy(speechRate = it)) },
                valueRange = 0.5f..2.0f,
                steps = 15
            )

            // Speech Pitch Slider
            Text(
                text = "Speech Pitch: ${String.format(Locale.US, "%.1fx", uiState.voiceConfig.pitch)}",
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
            Slider(
                value = uiState.voiceConfig.pitch,
                onValueChange = { onUpdateVoiceConfig(uiState.voiceConfig.copy(pitch = it)) },
                valueRange = 0.5f..2.0f,
                steps = 15
            )

            // Available Voices List
            if (uiState.availableVoices.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Installed English Voices (${uiState.availableVoices.size}):",
                    color = AccentCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
                for (voice in uiState.availableVoices.take(4)) {
                    Text(
                        text = "• ${voice.name} (${voice.locale}) - ${if (voice.isOfflineAvailable) "Offline" else "Online"}",
                        color = if (voice.name == uiState.voiceConfig.selectedVoiceName) AccentSuccess else TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Permission & Safety Policy Engine
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
