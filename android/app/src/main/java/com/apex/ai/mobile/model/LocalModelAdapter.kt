package com.apex.ai.mobile.model

import kotlinx.coroutines.flow.StateFlow

interface LocalModelAdapter {
    val status: StateFlow<ModelRuntimeStatus>
    val loadingProgress: StateFlow<Float>
    val lastError: StateFlow<ModelErrorInfo?>
    val isMock: Boolean

    suspend fun initialize(config: ModelConfig): Result<Unit>
    suspend fun generate(request: GenerationRequest): Result<GenerationResult>
    fun cancelCurrentGeneration()
    suspend fun unload(): Result<Unit>
    fun checkHealth(): ModelHealth
    fun getSupportedRuntimes(): List<String>
}
