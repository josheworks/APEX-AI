package com.apex.ai.mobile

import android.app.Application
import com.apex.ai.mobile.model.LiteRTLocalModelAdapter
import com.apex.ai.mobile.model.LocalModelAdapter
import com.apex.ai.mobile.model.MockLocalModelAdapter
import com.apex.ai.mobile.policy.AuditLogger
import com.apex.ai.mobile.policy.PolicyEngine
import com.apex.ai.mobile.registry.ToolRegistry
import com.apex.ai.mobile.workflow.WorkflowEngine

class ApexApplication : Application() {

    lateinit var toolRegistry: ToolRegistry
        private set

    lateinit var auditLogger: AuditLogger
        private set

    lateinit var policyEngine: PolicyEngine
        private set

    lateinit var localModelAdapter: LocalModelAdapter
        private set

    lateinit var workflowEngine: WorkflowEngine
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        auditLogger = AuditLogger()
        toolRegistry = ToolRegistry()
        policyEngine = PolicyEngine(auditLogger)

        // Initialize local model adapter.
        // Falls back seamlessly to MockLocalModelAdapter when weights are not downloaded during compilation/test.
        localModelAdapter = MockLocalModelAdapter()

        workflowEngine = WorkflowEngine(toolRegistry, policyEngine, auditLogger)

        auditLogger.log(
            toolName = "system_startup",
            risk = com.apex.ai.mobile.registry.ToolRisk.LOW,
            outcome = "INITIALIZED",
            durationMs = 0L,
            sanitizedDetails = "APEX Mobile Core and Local Model Adapter initialized."
        )
    }

    companion object {
        lateinit var instance: ApexApplication
            private set
    }
}
