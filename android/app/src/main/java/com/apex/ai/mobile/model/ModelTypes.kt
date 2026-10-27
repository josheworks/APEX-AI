package com.apex.ai.mobile.model

data class ModelConfig(
    val modelId: String = "google/functiongemma-270m-it",
    val modelPath: String = "models/functiongemma-270m-it-q8_ekv1024.litertlm",
    val maxContextLength: Int = 2048,
    val maxOutputTokens: Int = 256,
    val temperature: Float = 0.1f,
    val topK: Int = 40,
    val useGpuDelegate: Boolean = false,
    val numThreads: Int = 4,
    val timeoutMillis: Long = 10000L
)

enum class ModelRuntimeStatus {
    UNINITIALIZED,
    LOADING,
    READY,
    INFERRING,
    CANCELLED,
    ERROR
}

sealed class ModelHealth {
    data class Healthy(val ready: Boolean, val memoryUsedMb: Long, val device: String) : ModelHealth()
    data class Degraded(val message: String, val memoryUsedMb: Long) : ModelHealth()
    data class Offline(val reason: String) : ModelHealth()
}

data class ToolCallProposal(
    val toolName: String,
    val arguments: Map<String, Any?>,
    val rawCallText: String = "",
    val confidence: Float = 1.0f
)

data class GenerationRequest(
    val prompt: String,
    val conversationHistory: List<Pair<String, String>> = emptyList(),
    val availableTools: List<Map<String, Any?>> = emptyList(),
    val systemInstruction: String? = null,
    val maxOutputTokens: Int? = null,
    val temperature: Float? = null
)

data class GenerationResult(
    val text: String,
    val toolCalls: List<ToolCallProposal> = emptyList(),
    val isToolCall: Boolean = toolCalls.isNotEmpty(),
    val latencyMs: Long = 0L,
    val tokenCount: Int = 0,
    val finishReason: String = "complete"
)
