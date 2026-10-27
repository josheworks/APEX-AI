package com.apex.ai.mobile.workflow

import android.content.Context
import com.apex.ai.mobile.policy.AuditLogger
import com.apex.ai.mobile.policy.PolicyDecision
import com.apex.ai.mobile.policy.PolicyEngine
import com.apex.ai.mobile.registry.ToolExecutionResult
import com.apex.ai.mobile.registry.ToolRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class WorkflowEngine(
    private val toolRegistry: ToolRegistry,
    private val policyEngine: PolicyEngine,
    private val auditLogger: AuditLogger
) {
    private val _state = MutableStateFlow<WorkflowState>(WorkflowState.Idle)
    val state: StateFlow<WorkflowState> = _state.asStateFlow()

    @Volatile
    private var isCancelled = false

    suspend fun executeWorkflow(
        context: Context,
        definition: WorkflowDefinition,
        autoApproveCheckpoints: Boolean = false
    ): WorkflowState = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val reports = mutableListOf<StepExecutionReport>()
        isCancelled = false

        // 1. Permission & Tool Preflight Check
        for (step in definition.steps) {
            val tool = toolRegistry.getTool(step.toolName)
            if (tool == null) {
                val error = "Preflight Check Failed: Tool '${step.toolName}' is not registered."
                val failedState = WorkflowState.Failed(definition.workflowId, step.stepId, error, reports)
                _state.value = failedState
                return@withContext failedState
            }
        }

        // 2. Sequential Step Execution
        for ((index, step) in definition.steps.withIndex()) {
            if (isCancelled) {
                val cancelledState = WorkflowState.Cancelled(definition.workflowId, reports)
                _state.value = cancelledState
                return@withContext cancelledState
            }

            _state.value = WorkflowState.Running(index + 1, definition.steps.size, step.stepId)
            val stepStart = System.currentTimeMillis()

            val tool = toolRegistry.getTool(step.toolName)!!

            // Safety policy check
            val decision = policyEngine.evaluate(context, tool, step.arguments)
            when (decision) {
                is PolicyDecision.Reject -> {
                    val report = StepExecutionReport(
                        stepId = step.stepId,
                        toolName = step.toolName,
                        status = "REJECTED",
                        durationMs = System.currentTimeMillis() - stepStart,
                        error = decision.reason
                    )
                    reports.add(report)
                    val failedState = WorkflowState.Failed(definition.workflowId, step.stepId, decision.reason, reports)
                    _state.value = failedState
                    return@withContext failedState
                }
                is PolicyDecision.PermissionRequired -> {
                    val report = StepExecutionReport(
                        stepId = step.stepId,
                        toolName = step.toolName,
                        status = "PERMISSION_REQUIRED",
                        durationMs = System.currentTimeMillis() - stepStart,
                        error = "Missing: ${decision.missingPermissions}"
                    )
                    reports.add(report)
                    val failedState = WorkflowState.Failed(definition.workflowId, step.stepId, "Missing permissions: ${decision.missingPermissions}", reports)
                    _state.value = failedState
                    return@withContext failedState
                }
                is PolicyDecision.RequireConfirmation -> {
                    if (!autoApproveCheckpoints && step.requiresConfirmation) {
                        _state.value = WorkflowState.AwaitingConfirmation(step, decision.pendingConfirmation.targetSummary)
                        // In non-interactive mode or when awaiting user dialog, report checkpoint
                    }
                }
                is PolicyDecision.Allow -> {
                    // Proceed
                }
            }

            // Execute step with bounded timeout
            var stepResult: ToolExecutionResult? = null
            var stepError: String? = null
            try {
                withTimeout(step.timeoutMillis) {
                    stepResult = toolRegistry.executeTool(context, step.toolName, step.arguments)
                }
            } catch (e: CancellationException) {
                val cancelledState = WorkflowState.Cancelled(definition.workflowId, reports)
                _state.value = cancelledState
                return@withContext cancelledState
            } catch (e: Exception) {
                stepError = e.message ?: "Execution failed"
            }

            val stepDuration = System.currentTimeMillis() - stepStart
            val isSuccess = stepResult is ToolExecutionResult.Success

            reports.add(
                StepExecutionReport(
                    stepId = step.stepId,
                    toolName = step.toolName,
                    status = if (isSuccess) "SUCCESS" else "FAILED",
                    durationMs = stepDuration,
                    result = stepResult,
                    error = stepError
                )
            )

            if (!isSuccess) {
                // Execute compensation steps if defined
                executeCompensation(context, definition.compensationSteps)
                val failedState = WorkflowState.Failed(
                    workflowId = definition.workflowId,
                    failedStepId = step.stepId,
                    error = stepError ?: (stepResult as? ToolExecutionResult.Failed)?.errorMessage ?: "Step execution failed",
                    stepReports = reports
                )
                _state.value = failedState
                return@withContext failedState
            }
        }

        val completedState = WorkflowState.Completed(
            workflowId = definition.workflowId,
            stepReports = reports,
            totalDurationMs = System.currentTimeMillis() - startTime
        )
        _state.value = completedState
        return@withContext completedState
    }

    private suspend fun executeCompensation(context: Context, compensationSteps: List<WorkflowStep>) {
        for (compStep in compensationSteps) {
            try {
                toolRegistry.executeTool(context, compStep.toolName, compStep.arguments)
            } catch (_: Exception) {
            }
        }
    }

    fun cancel() {
        isCancelled = true
        _state.value = WorkflowState.Idle
    }

    companion object {
        fun createPhotoSharingWorkflow(recipient: String): WorkflowDefinition {
            return WorkflowDefinition(
                workflowId = "workflow_capture_and_share_photo",
                title = "Capture Photo & Share to WhatsApp",
                description = "Captures a new photo using the camera and opens WhatsApp to share it with $recipient.",
                steps = listOf(
                    WorkflowStep(
                        stepId = "step_1_camera",
                        toolName = "take_photo",
                        arguments = emptyMap(),
                        description = "Open camera and capture photo with user confirmation"
                    ),
                    WorkflowStep(
                        stepId = "step_2_share",
                        toolName = "prepare_whatsapp_message",
                        arguments = mapOf("recipient" to recipient, "message" to "Photo attached from APEX"),
                        description = "Prepare WhatsApp conversation with photo",
                        requiresConfirmation = true
                    )
                )
            )
        }
    }
}
