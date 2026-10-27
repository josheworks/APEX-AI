package com.apex.ai.mobile.workflow

import com.apex.ai.mobile.registry.ToolExecutionResult

data class WorkflowStep(
    val stepId: String,
    val toolName: String,
    val arguments: Map<String, Any?>,
    val description: String,
    val requiresConfirmation: Boolean = false,
    val timeoutMillis: Long = 10000L,
    val allowRetry: Boolean = false,
    val maxRetries: Int = 1
)

data class WorkflowDefinition(
    val workflowId: String,
    val title: String,
    val description: String,
    val steps: List<WorkflowStep>,
    val compensationSteps: List<WorkflowStep> = emptyList()
)

data class StepExecutionReport(
    val stepId: String,
    val toolName: String,
    val status: String,
    val durationMs: Long,
    val result: ToolExecutionResult? = null,
    val error: String? = null
)

sealed class WorkflowState {
    object Idle : WorkflowState()
    data class Running(val currentStepIndex: Int, val totalSteps: Int, val currentStepId: String) : WorkflowState()
    data class AwaitingConfirmation(
        val step: WorkflowStep,
        val confirmationPrompt: String
    ) : WorkflowState()
    data class Completed(
        val workflowId: String,
        val stepReports: List<StepExecutionReport>,
        val totalDurationMs: Long
    ) : WorkflowState()
    data class Failed(
        val workflowId: String,
        val failedStepId: String,
        val error: String,
        val stepReports: List<StepExecutionReport>
    ) : WorkflowState()
    data class Cancelled(
        val workflowId: String,
        val stepReports: List<StepExecutionReport>
    ) : WorkflowState()
}
