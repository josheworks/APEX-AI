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
import com.apex.ai.mobile.voice.DetectedInputLanguage
import com.apex.ai.mobile.voice.TranscriptionNormalizer
import com.apex.ai.mobile.voice.VoiceAssistantConfig
import com.apex.ai.mobile.voice.VoiceAssistantController
import com.apex.ai.mobile.voice.VoiceState
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
    private val workflowEngine: WorkflowEngine,
    private val modelImporter: com.apex.ai.mobile.model.ModelImporter,
    private val voiceController: VoiceAssistantController? = null,
    private val applicationContext: Context? = null
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
                val health = modelAdapter.checkHealth()
                val activePath = (modelAdapter as? com.apex.ai.mobile.model.LiteRTLocalModelAdapter)?.getActiveModelFile()?.absolutePath
                _uiState.update { it.copy(modelStatus = status, modelHealth = health, activeModelPath = activePath) }
            }
        }
        // Collect model errors & diagnostics
        viewModelScope.launch {
            modelAdapter.lastError.collect { errInfo ->
                _uiState.update { it.copy(modelErrorInfo = errInfo) }
            }
        }
        // Collect model import status
        viewModelScope.launch {
            modelImporter.importStatus.collect { importStatus ->
                _uiState.update { it.copy(modelImportStatus = importStatus) }
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

        // Collect voice pipeline states
        voiceController?.let { vc ->
            viewModelScope.launch {
                vc.voiceState.collect { state ->
                    _uiState.update {
                        it.copy(
                            voiceState = state,
                            isListening = (state == VoiceState.LISTENING)
                        )
                    }
                }
            }
            viewModelScope.launch {
                vc.audioRecorder.audioLevel.collect { level: Float ->
                    _uiState.update { it.copy(audioLevel = level) }
                }
            }
            viewModelScope.launch {
                vc.currentTranscription.collect { text ->
                    _uiState.update { it.copy(transcribedVoiceText = text) }
                }
            }
            viewModelScope.launch {
                vc.ttsManager.isSpeaking.collect { speaking ->
                    _uiState.update { it.copy(isSpeaking = speaking) }
                }
            }
            viewModelScope.launch {
                vc.ttsManager.availableVoices.collect { voices ->
                    _uiState.update { it.copy(availableVoices = voices) }
                }
            }
            viewModelScope.launch {
                vc.config.collect { cfg ->
                    _uiState.update { it.copy(voiceConfig = cfg) }
                }
            }

            // Wire callback for voice command recognition
            vc.onCommandRecognized = { normalizedText, detectedLang ->
                handleVoiceTurn(normalizedText, detectedLang)
            }
        }
        // Initialize model at startup
        viewModelScope.launch {
            val initResult = modelAdapter.initialize(ModelConfig())
            initResult.onSuccess {
                val startupNotice = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = "apex",
                    content = if (modelAdapter.isMock) {
                        "[MOCK MODE] Running with test keywords adapter."
                    } else {
                        "APEX Mobile On-Device AI Engine is ONLINE. FunctionGemma 270M loaded via LiteRT."
                    },
                    timestamp = currentTimeString()
                )
                _uiState.update { it.copy(messages = it.messages + startupNotice) }
            }.onFailure {
                val errInfo = _uiState.value.modelErrorInfo
                val notice = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = "apex",
                    content = "APEX Mobile Real Local AI initialized.\n\n" +
                            "[!] FunctionGemma on-device model is not yet imported.\n" +
                            "Target filename: mobile_actions_q8_ekv1024.litertlm\n" +
                            "Expected path:\n${errInfo?.expectedPath ?: "files/models/mobile_actions_q8_ekv1024.litertlm"}\n\n" +
                            "Go to Settings -> 'IMPORT MODEL FROM DOWNLOADS' to select it with the file picker, or transfer it to app storage.",
                    timestamp = currentTimeString()
                )
                _uiState.update { it.copy(messages = it.messages + notice) }
            }
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
                val errorInfo = _uiState.value.modelErrorInfo
                val contentText = when (_uiState.value.modelStatus) {
                    ModelRuntimeStatus.MODEL_MISSING ->
                        "[MODEL NOT FOUND] FunctionGemma 270M weights file is missing.\n\n" +
                        "Expected path:\n${errorInfo?.expectedPath ?: "models/functiongemma-270m-it-q8_ekv1024.litertlm"}\n\n" +
                        "Transfer the model file into that folder on your device and tap 'CHECK / RELOAD LOCAL MODEL' in Settings."
                    ModelRuntimeStatus.ERROR ->
                        "[INFERENCE ERROR] ${errorInfo?.code ?: "ENGINE_ERROR"}: ${err.message}\n" +
                        (errorInfo?.resolutionGuide ?: "Please inspect device logs or check model file compatibility.")
                    ModelRuntimeStatus.LOADING ->
                        "[MODEL LOADING] Model weights are currently being loaded into memory. Please wait a moment."
                    else ->
                        "System notice: ${err.message ?: "Unable to complete request"}"
                }
                val errorMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = "apex",
                    content = contentText,
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
    ): ToolExecutionResult {
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

        return result
    }

    /**
     * Handles an incoming voice turn recognized from English, Tamil or Tanglish speech.
     * Normalized canonical text is sent through FunctionGemma 270M and Safe Tool Registry.
     * Crucially, the response returned and spoken by TTS is ALWAYS in natural English.
     */
    private suspend fun handleVoiceTurn(
        normalizedText: String,
        detectedLang: DetectedInputLanguage
    ): String {
        val context = applicationContext ?: com.apex.ai.mobile.ApexApplication.instance
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = "user",
            content = normalizedText,
            timestamp = currentTimeString()
        )
        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                isGenerating = true
            )
        }

        val history = _uiState.value.messages.takeLast(10).map { Pair(it.sender, it.content) }
        val toolsDeclarations = toolRegistry.getAllTools().map {
            mapOf("name" to it.name, "description" to it.description)
        }

        val request = GenerationRequest(
            prompt = normalizedText,
            conversationHistory = history,
            availableTools = toolsDeclarations
        )

        val genResult = modelAdapter.generate(request)
        _uiState.update { it.copy(isGenerating = false) }

        var spokenResponse = ""

        genResult.onSuccess { result ->
            if (result.text.isNotBlank()) {
                val apexMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = "apex",
                    content = result.text,
                    timestamp = currentTimeString()
                )
                _uiState.update { it.copy(messages = it.messages + apexMsg) }
                spokenResponse = result.text
            }

            // Execute proposed tools through policy engine
            for (proposal in result.toolCalls) {
                val tool = toolRegistry.getTool(proposal.toolName)
                if (tool == null) {
                    spokenResponse = "Tool ${proposal.toolName} was not found."
                    continue
                }
                val decision = policyEngine.evaluate(context, tool, proposal.arguments)
                when (decision) {
                    is PolicyDecision.Allow -> {
                        val execResult = executeVerifiedTool(context, tool.name, decision.sanitizedArguments)
                        spokenResponse = TranscriptionNormalizer.formatSpokenResponse(execResult)
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
                        spokenResponse = "This action requires your confirmation on screen."
                    }
                    is PolicyDecision.PermissionRequired -> {
                        spokenResponse = "Permission is required to execute ${tool.name}."
                    }
                    is PolicyDecision.Reject -> {
                        spokenResponse = "Action was blocked: ${decision.reason}."
                    }
                }
            }
        }.onFailure { err ->
            spokenResponse = "Sorry, I couldn't complete that command."
        }

        if (spokenResponse.isBlank()) {
            spokenResponse = "Command processed."
        }

        return spokenResponse
    }

    fun cancelGeneration() {
        modelAdapter.cancelCurrentGeneration()
        _uiState.update { it.copy(isGenerating = false) }
    }

    fun toggleVoiceInput() {
        if (voiceController != null) {
            voiceController.toggleListening()
        } else {
            _uiState.update { it.copy(isListening = !it.isListening) }
        }
    }

    fun stopSpeaking() {
        voiceController?.stopSpeaking()
    }

    fun updateVoiceConfig(config: VoiceAssistantConfig) {
        voiceController?.updateConfig(config)
    }

    fun reloadModel() {
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true) }
            val result = modelAdapter.initialize(ModelConfig())
            _uiState.update { it.copy(isGenerating = false) }

            result.onSuccess {
                val feedback = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = "apex",
                    content = if (modelAdapter.isMock) {
                        "[MOCK ENGINE] Test adapter reloaded."
                    } else {
                        "SUCCESS: Real LiteRT engine initialized with FunctionGemma 270M weights."
                    },
                    timestamp = currentTimeString()
                )
                _uiState.update { it.copy(messages = it.messages + feedback) }
            }.onFailure { err ->
                val errInfo = _uiState.value.modelErrorInfo
                val feedback = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = "apex",
                    content = "RELOAD FAILED: ${err.message}\nExpected path:\n${errInfo?.expectedPath ?: ""}",
                    timestamp = currentTimeString()
                )
                _uiState.update { it.copy(messages = it.messages + feedback) }
            }
        }
    }

    fun importModel(uri: android.net.Uri) {
        viewModelScope.launch {
            val result = modelImporter.importModelFromUri(
                sourceUri = uri,
                targetFileName = "mobile_actions_q8_ekv1024.litertlm"
            )

            result.onSuccess { importedFile ->
                val sizeMb = importedFile.length() / (1024 * 1024)
                val successMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = "apex",
                    content = "SUCCESS: Model imported ($sizeMb MB).\nFile: ${importedFile.name}\nPath: ${importedFile.absolutePath}\n\nInitializing on-device LiteRT inference engine...",
                    timestamp = currentTimeString()
                )
                _uiState.update { it.copy(messages = it.messages + successMsg) }

                // Automatically reload model to initialize the real LiteRT engine with the imported weights
                reloadModel()
            }.onFailure { err ->
                val failMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = "apex",
                    content = "MODEL IMPORT FAILED: ${err.message}",
                    timestamp = currentTimeString()
                )
                _uiState.update { it.copy(messages = it.messages + failMsg) }
            }
        }
    }

    fun resetImportStatus() {
        modelImporter.resetStatus()
    }

    private fun currentTimeString(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }
}
