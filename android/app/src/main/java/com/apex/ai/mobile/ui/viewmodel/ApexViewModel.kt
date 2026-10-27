package com.apex.ai.mobile.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.ai.mobile.model.GenerationRequest
import com.apex.ai.mobile.model.LocalModelAdapter
import com.apex.ai.mobile.model.ModelConfig
import com.apex.ai.mobile.model.ModelRuntimeStatus
import com.apex.ai.mobile.model.ToolCallProposal
import com.apex.ai.mobile.policy.AuditLogger
import com.apex.ai.mobile.policy.PolicyDecision
import com.apex.ai.mobile.policy.PolicyEngine
import com.apex.ai.mobile.registry.ToolExecutionResult
import com.apex.ai.mobile.registry.ToolRegistry
import com.apex.ai.mobile.ui.components.ChatMessage
import com.apex.ai.mobile.workflow.WorkflowEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ApexViewModel(
    private val modelAdapter: LocalModelAdapter,
    private val toolRegistry: ToolRegistry,
    private val policyEngine: PolicyEngine,
    private val auditLogger: AuditLogger,
    private val workflowEngine: WorkflowEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ApexUiState(
            availableTools = toolRegistry.getAllTools()
        )
    )
    val uiState: StateFlow<ApexUiState> = _uiState.asStateFlow()

    init {
        // Collect model status
        viewModelScope.launch {
            modelAdapter.status.collect { status ->
                _uiState.update { it.copy(modelStatus = status, modelHealth = modelAdapter.checkHealth()) }
            }
        }
        // Collect audit records
        viewModelScope.launch {
            auditLogger.records.collect { records ->
                _uiState.update { it.copy(auditRecords = records) }
            }
        }
        // Collect workflow state
        viewModelScope.launch {
            workflowEngine.state.collect { wfState ->
                _uiState.update { it.copy(workflowState = wfState) }
            }
        }
        // Initialize model
        viewModelScope.launch {
            modelAdapter.initialize(ModelConfig())
        }
    }

    fun onInputChange(input: String) {
        _uiState.update { it.copy(currentInput = input) }
    }

    fun sendMessage(context: Context) {
        val text = _uiState.value.currentInput.trim()
        if (text.isBlank()) return

        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = "user",
            content = text,
            timestamp = currentTimeString()
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                currentInput = "",
                isGenerating = true
            )
        }

        viewModelScope.launch {
            val history = _uiState.value.messages.takeLast(10).map { Pair(it.sender, it.content) }
            val toolsDeclarations = toolRegistry.getAllTools().map {
                mapOf("name" to it.name, "description" to it.description)
            }

            val request = GenerationRequest(
                prompt = text,
                conversationHistory = history,
                availableTools = toolsDeclarations
            )

            val genResult = modelAdapter.generate(request)
            _uiState.update { it.copy(isGenerating = false) }

            genResult.onSuccess { result ->
                if (result.text.isNotBlank()) {
                    val apexMsg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        sender = "apex",
                        content = result.text,
                        timestamp = currentTimeString()
                    )
                    _uiState.update { it.copy(messages = it.messages + apexMsg) }
                }

                // If model proposed tool calls, process them through the Policy Engine
                for (proposal in result.toolCalls) {
                    processToolProposal(context, proposal)
                }
            }.onFailure { err ->
                val errorMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = "apex",
                    content = "System notice: ${err.message ?: "Unable to complete request"}",
                    timestamp = currentTimeString()
                )
                _uiState.update { it.copy(messages = it.messages + errorMsg) }
            }
        }
    }

    private suspend fun processToolProposal(context: Context, proposal: ToolCallProposal) {
        val tool = toolRegistry.getTool(proposal.toolName)
        if (tool == null) {
            val failedResult = ToolExecutionResult.Failed(
                proposal.toolName,
                "NOT_FOUND",
                "Tool '${proposal.toolName}' is not registered."
            )
            _uiState.update {
                it.copy(
                    activeToolName = proposal.toolName,
                    isToolExecuting = false,
                    lastToolResult = failedResult
                )
            }
            return
        }

        val decision = policyEngine.evaluate(context, tool, proposal.arguments)
        when (decision) {
            is PolicyDecision.Allow -> {
                executeVerifiedTool(context, tool.name, decision.sanitizedArguments)
            }
            is PolicyDecision.RequireConfirmation -> {
                _uiState.update {
                    it.copy(
                        pendingConfirmation = decision.pendingConfirmation,
                        activeToolName = tool.name,
                        lastToolResult = ToolExecutionResult.ConfirmationRequired(
                            toolName = tool.name,
                            pendingActionId = decision.pendingConfirmation.actionId,
                            arguments = decision.pendingConfirmation.arguments,
                            summary = decision.pendingConfirmation.targetSummary,
                            consequences = decision.pendingConfirmation.consequences
                        )
                    )
                }
            }
            is PolicyDecision.PermissionRequired -> {
                val permResult = ToolExecutionResult.PermissionRequired(
                    toolName = tool.name,
                    missingPermissions = decision.missingPermissions,
                    explanation = decision.reason
                )
                _uiState.update {
                    it.copy(
                        activeToolName = tool.name,
                        isToolExecuting = false,
                        lastToolResult = permResult
                    )
                }
            }
            is PolicyDecision.Reject -> {
                val rejectResult = ToolExecutionResult.Rejected(tool.name, decision.reason)
                _uiState.update {
                    it.copy(
                        activeToolName = tool.name,
                        isToolExecuting = false,
                        lastToolResult = rejectResult
                    )
                }
            }
        }
    }

    fun confirmPendingAction(context: Context, actionId: String) {
        val pending = _uiState.value.pendingConfirmation ?: return
        if (pending.actionId != actionId) return

        _uiState.update { it.copy(pendingConfirmation = null) }

        viewModelScope.launch {
            val tool = toolRegistry.getTool(pending.toolName) ?: return@launch
            val decision = policyEngine.evaluate(
                context = context,
                toolMetadata = tool,
                rawArguments = pending.arguments,
                confirmedActionId = actionId
            )

            if (decision is PolicyDecision.Allow) {
                executeVerifiedTool(context, tool.name, decision.sanitizedArguments)
            } else {
                _uiState.update {
                    it.copy(
                        lastToolResult = ToolExecutionResult.Failed(
                            tool.name,
                            "CONFIRMATION_INVALID",
                            "Confirmation evaluation failed"
                        )
                    )
                }
            }
        }
    }

    fun cancelPendingAction(actionId: String) {
        policyEngine.cancelConfirmation(actionId)
        _uiState.update {
            it.copy(
                pendingConfirmation = null,
                lastToolResult = ToolExecutionResult.Failed(
                    toolName = it.activeToolName ?: "action",
                    errorCode = "USER_CANCELLED",
                    errorMessage = "Action was cancelled by user."
                )
            )
        }
    }

    private suspend fun executeVerifiedTool(
        context: Context,
        toolName: String,
        arguments: Map<String, Any?>
    ) {
        _uiState.update { it.copy(activeToolName = toolName, isToolExecuting = true) }
        val startTime = System.currentTimeMillis()

        val result = toolRegistry.executeTool(context, toolName, arguments)
        val duration = System.currentTimeMillis() - startTime

        auditLogger.log(
            toolName = toolName,
            risk = toolRegistry.getTool(toolName)?.risk ?: com.apex.ai.mobile.registry.ToolRisk.LOW,
            outcome = if (result is ToolExecutionResult.Success) "SUCCESS" else "FAILED",
            durationMs = duration,
            sanitizedDetails = if (result is ToolExecutionResult.Success) result.message else (result as? ToolExecutionResult.Failed)?.errorMessage ?: ""
        )

        _uiState.update {
            it.copy(
                isToolExecuting = false,
                lastToolResult = result
            )
        }

        // Add follow-up assistant message if successful
        if (result is ToolExecutionResult.Success) {
            val feedbackMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = "apex",
                content = result.message,
                timestamp = currentTimeString(),
                toolCallName = toolName
            )
            _uiState.update { it.copy(messages = it.messages + feedbackMsg) }
        }
    }

    fun cancelGeneration() {
        modelAdapter.cancelCurrentGeneration()
        _uiState.update { it.copy(isGenerating = false) }
    }

    fun toggleVoiceInput() {
        _uiState.update { it.copy(isListening = !it.isListening) }
    }

    private fun currentTimeString(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }
}
