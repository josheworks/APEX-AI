package com.apex.ai.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.apex.ai.mobile.ui.components.CommandBar
import com.apex.ai.mobile.ui.components.ConfirmationDialog
import com.apex.ai.mobile.ui.components.LiveActivityPanel
import com.apex.ai.mobile.ui.components.MessageBubble
import com.apex.ai.mobile.ui.components.SystemStatusDashboard
import com.apex.ai.mobile.ui.components.TerminalHeader
import com.apex.ai.mobile.ui.components.ToolStatusCard
import com.apex.ai.mobile.ui.components.WelcomeCard
import com.apex.ai.mobile.ui.theme.BgDeep
import com.apex.ai.mobile.ui.viewmodel.ApexUiState
import com.apex.ai.mobile.ui.viewmodel.ApexViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainTerminalScreen(
    viewModel: ApexViewModel,
    uiState: ApexUiState,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new messages
    LaunchedEffect(uiState.messages.size, uiState.activeToolName, uiState.lastToolResult) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Terminal Header
            TerminalHeader(
                modelStatus = uiState.modelStatus,
                onOpenSettings = onOpenSettings
            )

            // 2. System Status Dashboard (collapsible)
            SystemStatusDashboard(
                modelStatus = uiState.modelStatus,
                activeToolsCount = uiState.availableTools.size
            )

            // 3. Live Activity Feed (collapsible)
            LiveActivityPanel(records = uiState.auditRecords)

            // 4. Conversation Scrollable Area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    WelcomeCard(
                        timeString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                items(uiState.messages) { message ->
                    MessageBubble(message = message)
                }

                // Active Tool Status Card
                if (uiState.activeToolName != null) {
                    item {
                        ToolStatusCard(
                            toolName = uiState.activeToolName,
                            isRunning = uiState.isToolExecuting,
                            result = uiState.lastToolResult
                        )
                    }
                }
            }

            // 5. Command Bar
            CommandBar(
                input = uiState.currentInput,
                onInputChange = viewModel::onInputChange,
                onSend = { viewModel.sendMessage(context) },
                onMicClick = viewModel::toggleVoiceInput,
                isListening = uiState.isListening,
                isGenerating = uiState.isGenerating,
                onCancelGeneration = viewModel::cancelGeneration
            )
        }

        // Security Confirmation Dialog Modal
        uiState.pendingConfirmation?.let { pending ->
            ConfirmationDialog(
                pendingConfirmation = pending,
                onConfirm = { actionId -> viewModel.confirmPendingAction(context, actionId) },
                onCancel = { actionId -> viewModel.cancelPendingAction(actionId) }
            )
        }
    }
}
