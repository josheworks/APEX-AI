package com.apex.ai.mobile.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MockLocalModelAdapter(
    private val simulatedLatencyMs: Long = 150L,
    private val shouldFail: Boolean = false
) : LocalModelAdapter {

    private val _status = MutableStateFlow(ModelRuntimeStatus.UNINITIALIZED)
    override val status: StateFlow<ModelRuntimeStatus> = _status.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0f)
    override val loadingProgress: StateFlow<Float> = _loadingProgress.asStateFlow()

    @Volatile
    private var isCancelled = false

    override suspend fun initialize(config: ModelConfig): Result<Unit> {
        _status.value = ModelRuntimeStatus.LOADING
        for (i in 1..10) {
            delay(10)
            _loadingProgress.value = i / 10f
        }
        _status.value = ModelRuntimeStatus.READY
        return Result.success(Unit)
    }

    override suspend fun generate(request: GenerationRequest): Result<GenerationResult> {
        if (_status.value != ModelRuntimeStatus.READY) {
            return Result.failure(IllegalStateException("Model not initialized"))
        }
        if (shouldFail) {
            _status.value = ModelRuntimeStatus.ERROR
            return Result.failure(RuntimeException("Simulated inference failure"))
        }

        _status.value = ModelRuntimeStatus.INFERRING
        isCancelled = false

        try {
            val startTime = System.currentTimeMillis()
            delay(simulatedLatencyMs)

            if (isCancelled) {
                _status.value = ModelRuntimeStatus.CANCELLED
                return Result.failure(CancellationException("Generation cancelled by user"))
            }

            val promptLower = request.prompt.lowercase()
            val result = when {
                // Device Settings
                promptLower.contains("battery") -> {
                    GenerationResult(
                        text = "Checking battery status now.",
                        toolCalls = listOf(ToolCallProposal("get_battery_status", emptyMap())),
                        latencyMs = System.currentTimeMillis() - startTime,
                        tokenCount = 24
                    )
                }
                promptLower.contains("wifi") || promptLower.contains("wi-fi") -> {
                    GenerationResult(
                        text = "Opening Wi-Fi settings for you.",
                        toolCalls = listOf(ToolCallProposal("open_wifi_settings", emptyMap())),
                        latencyMs = System.currentTimeMillis() - startTime,
                        tokenCount = 20
                    )
                }
                promptLower.contains("device info") || promptLower.contains("system info") -> {
                    GenerationResult(
                        text = "Retrieving device hardware specifications.",
                        toolCalls = listOf(ToolCallProposal("get_device_info", emptyMap())),
                        latencyMs = System.currentTimeMillis() - startTime,
                        tokenCount = 22
                    )
                }
                // App Control
                promptLower.startsWith("open ") || promptLower.contains("launch ") -> {
                    val target = request.prompt.substringAfter("open ", "").substringAfter("launch ", "").trim()
                        .removeSuffix(".").trim()
                    val appName = if (target.isNotBlank()) target else "WhatsApp"
                    GenerationResult(
                        text = "Opening $appName.",
                        toolCalls = listOf(ToolCallProposal("open_app", mapOf("app_name" to appName))),
                        latencyMs = System.currentTimeMillis() - startTime,
                        tokenCount = 18
                    )
                }
                promptLower.contains("list apps") || promptLower.contains("available apps") -> {
                    GenerationResult(
                        text = "Scanning available applications.",
                        toolCalls = listOf(ToolCallProposal("list_available_apps", emptyMap())),
                        latencyMs = System.currentTimeMillis() - startTime,
                        tokenCount = 18
                    )
                }
                // WhatsApp / Communication
                promptLower.contains("message") || promptLower.contains("whatsapp") -> {
                    val recipient = extractRecipient(request.prompt)
                    val message = extractMessage(request.prompt)
                    GenerationResult(
                        text = "Preparing WhatsApp message for $recipient.",
                        toolCalls = listOf(
                            ToolCallProposal(
                                "send_whatsapp_message",
                                mapOf("recipient" to recipient, "message" to message)
                            )
                        ),
                        latencyMs = System.currentTimeMillis() - startTime,
                        tokenCount = 35
                    )
                }
                // Camera / Media
                promptLower.contains("photo") || promptLower.contains("picture") || promptLower.contains("camera") -> {
                    GenerationResult(
                        text = "Opening camera viewfinder to capture a photo.",
                        toolCalls = listOf(ToolCallProposal("take_photo", emptyMap())),
                        latencyMs = System.currentTimeMillis() - startTime,
                        tokenCount = 16
                    )
                }
                promptLower.contains("flashlight") || promptLower.contains("torch") -> {
                    val enabled = !promptLower.contains("off")
                    GenerationResult(
                        text = if (enabled) "Toggling flashlight on." else "Toggling flashlight off.",
                        toolCalls = listOf(ToolCallProposal("toggle_flashlight", mapOf("enabled" to enabled))),
                        latencyMs = System.currentTimeMillis() - startTime,
                        tokenCount = 18
                    )
                }
                // APK Management
                promptLower.contains("install") && promptLower.contains("apk") -> {
                    GenerationResult(
                        text = "Initiating APK package inspection and installation.",
                        toolCalls = listOf(ToolCallProposal("find_apk", emptyMap())),
                        latencyMs = System.currentTimeMillis() - startTime,
                        tokenCount = 20
                    )
                }
                // Restricted request test
                promptLower.contains("silent install") || promptLower.contains("covert record") -> {
                    GenerationResult(
                        text = "I cannot perform covert or silent installations as this violates security policies.",
                        toolCalls = listOf(ToolCallProposal("silent_install_apk", emptyMap())),
                        latencyMs = System.currentTimeMillis() - startTime,
                        tokenCount = 28
                    )
                }
                // Screen interaction
                promptLower.contains("screenshot") -> {
                    GenerationResult(
                        text = "Requesting screen capture permission.",
                        toolCalls = listOf(ToolCallProposal("take_screenshot", emptyMap())),
                        latencyMs = System.currentTimeMillis() - startTime,
                        tokenCount = 16
                    )
                }
                // Normal conversational reply
                else -> {
                    GenerationResult(
                        text = "Got it! APEX is here and ready. How can I assist you with your device today?",
                        toolCalls = emptyList(),
                        latencyMs = System.currentTimeMillis() - startTime,
                        tokenCount = 22
                    )
                }
            }

            _status.value = ModelRuntimeStatus.READY
            return Result.success(result)
        } finally {
            if (_status.value == ModelRuntimeStatus.INFERRING) {
                _status.value = ModelRuntimeStatus.READY
            }
        }
    }

    override fun cancelCurrentGeneration() {
        isCancelled = true
        _status.value = ModelRuntimeStatus.CANCELLED
    }

    override suspend fun unload(): Result<Unit> {
        _status.value = ModelRuntimeStatus.UNINITIALIZED
        _loadingProgress.value = 0f
        return Result.success(Unit)
    }

    override fun checkHealth(): ModelHealth {
        return ModelHealth.Healthy(
            ready = _status.value == ModelRuntimeStatus.READY,
            memoryUsedMb = 288L,
            device = "Mobile Action 270M (Simulated CPU/XNNPACK)"
        )
    }

    override fun getSupportedRuntimes(): List<String> = listOf("MockEngine", "LiteRT-CPU", "LiteRT-GPU")

    private fun extractRecipient(prompt: String): String {
        val parts = prompt.split(" ")
        for (i in parts.indices) {
            if (parts[i].equals("to", ignoreCase = true) || parts[i].equals("send", ignoreCase = true)) {
                if (i + 1 < parts.size && parts[i + 1].lowercase() !in setOf("a", "the", "message", "whatsapp")) {
                    return parts[i + 1].replace(Regex("[^a-zA-Z0-9]"), "")
                }
            }
        }
        return "Abi"
    }

    private fun extractMessage(prompt: String): String {
        return when {
            prompt.contains("saying", ignoreCase = true) -> prompt.substringAfter("saying").trim().removePrefix(":").trim()
            prompt.contains("that", ignoreCase = true) -> prompt.substringAfter("that").trim()
            else -> "I will call later"
        }
    }
}
