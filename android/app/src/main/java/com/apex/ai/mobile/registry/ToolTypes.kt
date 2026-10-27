package com.apex.ai.mobile.registry

import android.content.Context

enum class ToolRisk {
    LOW,
    MEDIUM,
    HIGH,
    RESTRICTED
}

enum class ConfirmationPolicy {
    NEVER,
    WHEN_NEEDED,
    ALWAYS,
    NOT_ALLOWED
}

data class ToolMetadata(
    val name: String,
    val description: String,
    val category: String,
    val risk: ToolRisk,
    val confirmation: ConfirmationPolicy,
    val timeoutMillis: Long = 10000L,
    val requiredCapabilities: Set<String> = emptySet(),
    val requiredPermissions: Set<String> = emptySet(),
    val reversible: Boolean = false,
    val parametersSchema: Map<String, ParameterDefinition> = emptyMap()
)

data class ParameterDefinition(
    val type: ParameterType,
    val description: String,
    val required: Boolean = true,
    val allowedValues: List<String>? = null
)

enum class ParameterType {
    STRING,
    INTEGER,
    BOOLEAN,
    FLOAT,
    OBJECT
}

data class ToolExecutionRequest(
    val toolName: String,
    val arguments: Map<String, Any?>,
    val callerTurnId: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

sealed class ToolExecutionResult {
    data class Success(
        val toolName: String,
        val output: Any?,
        val message: String = "Operation completed successfully"
    ) : ToolExecutionResult()

    data class Failed(
        val toolName: String,
        val errorCode: String,
        val errorMessage: String,
        val cause: Throwable? = null
    ) : ToolExecutionResult()

    data class ConfirmationRequired(
        val toolName: String,
        val pendingActionId: String,
        val arguments: Map<String, Any?>,
        val summary: String,
        val consequences: String
    ) : ToolExecutionResult()

    data class PermissionRequired(
        val toolName: String,
        val missingPermissions: Set<String>,
        val explanation: String
    ) : ToolExecutionResult()

    data class Unsupported(
        val toolName: String,
        val explanation: String
    ) : ToolExecutionResult()

    data class Rejected(
        val toolName: String,
        val reason: String
    ) : ToolExecutionResult()
}

fun interface ToolHandler {
    suspend fun execute(context: Context, arguments: Map<String, Any?>): ToolExecutionResult
}
