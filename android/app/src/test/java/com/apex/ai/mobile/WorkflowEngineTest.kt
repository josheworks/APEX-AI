package com.apex.ai.mobile

import android.content.Context
import com.apex.ai.mobile.policy.AuditLogger
import com.apex.ai.mobile.policy.PolicyEngine
import com.apex.ai.mobile.registry.ToolRegistry
import com.apex.ai.mobile.workflow.WorkflowDefinition
import com.apex.ai.mobile.workflow.WorkflowEngine
import com.apex.ai.mobile.workflow.WorkflowState
import com.apex.ai.mobile.workflow.WorkflowStep
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class WorkflowEngineTest {

    private lateinit var toolRegistry: ToolRegistry
    private lateinit var auditLogger: AuditLogger
    private lateinit var policyEngine: PolicyEngine
    private lateinit var workflowEngine: WorkflowEngine
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        toolRegistry = ToolRegistry()
        auditLogger = AuditLogger()
        policyEngine = PolicyEngine(auditLogger)
        workflowEngine = WorkflowEngine(toolRegistry, policyEngine, auditLogger)
        mockContext = Mockito.mock(Context::class.java)
    }

    @Test
    fun testUnregisteredToolPreflightFailsWorkflow() = runBlocking {
        val workflow = WorkflowDefinition(
            workflowId = "invalid_wf",
            title = "Invalid Workflow",
            description = "Calls unknown tool",
            steps = listOf(
                WorkflowStep("step1", "unknown_missing_tool", emptyMap(), "Missing")
            )
        )

        val result = workflowEngine.executeWorkflow(mockContext, workflow)
        assertTrue("Workflow must fail preflight when tool is missing", result is WorkflowState.Failed)
        val failed = result as WorkflowState.Failed
        assertTrue(failed.error.contains("not registered"))
    }

    @Test
    fun testRestrictedStepRejectsWorkflow() = runBlocking {
        val workflow = WorkflowDefinition(
            workflowId = "restricted_wf",
            title = "Restricted Workflow",
            description = "Calls restricted tool",
            steps = listOf(
                WorkflowStep("step1", "silent_install_apk", emptyMap(), "Silent install")
            )
        )

        val result = workflowEngine.executeWorkflow(mockContext, workflow)
        assertTrue("Workflow must fail when a step is restricted", result is WorkflowState.Failed)
    }

    @Test
    fun testPhotoSharingWorkflowCreation() {
        val wf = WorkflowEngine.createPhotoSharingWorkflow("Abi")
        assertEquals(2, wf.steps.size)
        assertEquals("take_photo", wf.steps[0].toolName)
        assertEquals("prepare_whatsapp_message", wf.steps[1].toolName)
        assertEquals("Abi", wf.steps[1].arguments["recipient"])
    }
}
