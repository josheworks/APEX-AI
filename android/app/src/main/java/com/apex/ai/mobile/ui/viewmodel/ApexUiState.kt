package com.apex.ai.mobile.ui.viewmodel

import com.apex.ai.mobile.model.ModelHealth
import com.apex.ai.mobile.model.ModelRuntimeStatus
import com.apex.ai.mobile.policy.AuditRecord
import com.apex.ai.mobile.policy.PendingConfirmation
import com.apex.ai.mobile.registry.ToolExecutionResult
import com.apex.ai.mobile.registry.ToolMetadata
import com.apex.ai.mobile.ui.components.ChatMessage
import com.apex.ai.mobile.workflow.WorkflowState

data class ApexUiState(
    val messages: List<ChatMessage> = emptyList(),
    val currentInput: String = "",
    val modelStatus: ModelRuntimeStatus = ModelRuntimeStatus.UNINITIALIZED,
    val modelHealth: ModelHealth = ModelHealth.Offline("Initializing..."),
    val activeToolName: String? = null,
    val isToolExecuting: Boolean = false,
    val lastToolResult: ToolExecutionResult? = null,
    val isListening: Boolean = false,
    val isGenerating: Boolean = false,
    val pendingConfirmation: PendingConfirmation? = null,
    val auditRecords: List<AuditRecord> = emptyList(),
    val availableTools: List<ToolMetadata> = emptyList(),
    val workflowState: WorkflowState = WorkflowState.Idle
)
