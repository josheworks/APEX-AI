package com.apex.ai.mobile

import android.app.Application
import com.apex.ai.mobile.model.LiteRTLocalModelAdapter
import com.apex.ai.mobile.model.LocalModelAdapter
import com.apex.ai.mobile.model.MockLocalModelAdapter
import com.apex.ai.mobile.policy.AuditLogger
import com.apex.ai.mobile.policy.PolicyEngine
import com.apex.ai.mobile.registry.ToolRegistry
import com.apex.ai.mobile.voice.VoiceAssistantController
import com.apex.ai.mobile.workflow.WorkflowEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ApexApplication : Application() {

    lateinit var toolRegistry: ToolRegistry
        private set

    lateinit var auditLogger: AuditLogger
        private set

    lateinit var policyEngine: PolicyEngine
        private set

    lateinit var localModelAdapter: LocalModelAdapter
        private set

    lateinit var modelImporter: com.apex.ai.mobile.model.ModelImporter
        private set

    lateinit var workflowEngine: WorkflowEngine
        private set

    lateinit var voiceController: VoiceAssistantController
        private set

    private val applicationScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this

        auditLogger = AuditLogger()
        toolRegistry = ToolRegistry()
        policyEngine = PolicyEngine(auditLogger)

        // Initialize production on-device local model adapter powered by LiteRT / MediaPipe GenAI
        localModelAdapter = LiteRTLocalModelAdapter(this)
        modelImporter = com.apex.ai.mobile.model.ModelImporter(this)

        workflowEngine = WorkflowEngine(toolRegistry, policyEngine, auditLogger)

        // Initialize production offline-first voice pipeline
        voiceController = VoiceAssistantController(this, applicationScope)
        applicationScope.launch {
            voiceController.initialize()
        }

        auditLogger.log(
            toolName = "system_startup",
            risk = com.apex.ai.mobile.registry.ToolRisk.LOW,
            outcome = "INITIALIZED",
            durationMs = 0L,
            sanitizedDetails = "APEX Mobile Core, Local Model Adapter, and Voice Assistant Engine initialized."
        )
    }

    override fun onTerminate() {
        super.onTerminate()
        if (::voiceController.isInitialized) {
            voiceController.shutdown()
        }
    }

    companion object {
        lateinit var instance: ApexApplication
            private set
    }
}
