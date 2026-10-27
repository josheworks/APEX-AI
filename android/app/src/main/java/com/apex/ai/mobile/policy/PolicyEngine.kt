package com.apex.ai.mobile.policy

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.apex.ai.mobile.registry.ConfirmationPolicy
import com.apex.ai.mobile.registry.SchemaValidator
import com.apex.ai.mobile.registry.ToolMetadata
import com.apex.ai.mobile.registry.ToolRisk
import java.security.MessageDigest
import java.util.UUID

sealed class PolicyDecision {
    data class Allow(val sanitizedArguments: Map<String, Any?>) : PolicyDecision()
    data class RequireConfirmation(
        val pendingConfirmation: PendingConfirmation
    ) : PolicyDecision()
    data class PermissionRequired(
        val missingPermissions: Set<String>,
        val reason: String
    ) : PolicyDecision()
    data class Reject(
        val reason: String,
        val isRestricted: Boolean = false
    ) : PolicyDecision()
}

data class PendingConfirmation(
    val actionId: String = UUID.randomUUID().toString(),
    val toolName: String,
    val argumentsHash: String,
    val arguments: Map<String, Any?>,
    val targetSummary: String,
    val consequences: String,
    val risk: ToolRisk,
    val timestamp: Long = System.currentTimeMillis()
)

class PolicyEngine(
    private val auditLogger: AuditLogger
) {
    // Map of actionId -> PendingConfirmation
    private val pendingConfirmations = mutableMapOf<String, PendingConfirmation>()

    fun evaluate(
        context: Context,
        toolMetadata: ToolMetadata,
        rawArguments: Map<String, Any?>,
        confirmedActionId: String? = null
    ): PolicyDecision {
        val startTime = System.currentTimeMillis()

        // 1. Check RESTRICTED operations
        if (toolMetadata.risk == ToolRisk.RESTRICTED || toolMetadata.confirmation == ConfirmationPolicy.NOT_ALLOWED) {
            val reason = "Security Policy Violation: Tool '${toolMetadata.name}' is strictly restricted on this system."
            auditLogger.log(
                toolName = toolMetadata.name,
                risk = toolMetadata.risk,
                outcome = "REJECTED_RESTRICTED",
                durationMs = System.currentTimeMillis() - startTime,
                sanitizedDetails = reason
            )
            return PolicyDecision.Reject(reason, isRestricted = true)
        }

        // 2. Schema Validation & Input Sanitization
        val validationResult = SchemaValidator.validate(toolMetadata, rawArguments)
        val sanitizedArgs = when (validationResult) {
            is SchemaValidator.ValidationResult.Valid -> validationResult.sanitizedArguments
            is SchemaValidator.ValidationResult.Invalid -> {
                val reason = "Schema validation failed: ${validationResult.errors.joinToString("; ")}"
                auditLogger.log(
                    toolName = toolMetadata.name,
                    risk = toolMetadata.risk,
                    outcome = "INVALID_SCHEMA",
                    durationMs = System.currentTimeMillis() - startTime,
                    sanitizedDetails = reason
                )
                return PolicyDecision.Reject(reason)
            }
        }

        // 3. Permission Checks
        val missingPermissions = mutableSetOf<String>()
        for (permission in toolMetadata.requiredPermissions) {
            val status = ContextCompat.checkSelfPermission(context, permission)
            if (status != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission)
            }
        }

        if (missingPermissions.isNotEmpty()) {
            val reason = "Missing required Android permissions: $missingPermissions"
            auditLogger.log(
                toolName = toolMetadata.name,
                risk = toolMetadata.risk,
                outcome = "PERMISSION_REQUIRED",
                durationMs = System.currentTimeMillis() - startTime,
                sanitizedDetails = reason
            )
            return PolicyDecision.PermissionRequired(
                missingPermissions = missingPermissions,
                reason = "Please grant the necessary permissions to execute ${toolMetadata.name}."
            )
        }

        // 4. Confirmation Policy
        val currentArgsHash = computeArgumentsHash(toolMetadata.name, sanitizedArgs)
        val needsConfirmation = when (toolMetadata.confirmation) {
            ConfirmationPolicy.ALWAYS -> true
            ConfirmationPolicy.WHEN_NEEDED -> toolMetadata.risk == ToolRisk.HIGH || toolMetadata.risk == ToolRisk.MEDIUM
            ConfirmationPolicy.NEVER -> false
            ConfirmationPolicy.NOT_ALLOWED -> true
        }

        if (needsConfirmation) {
            if (confirmedActionId != null) {
                val pending = pendingConfirmations[confirmedActionId]
                if (pending == null) {
                    return PolicyDecision.Reject("Expired or unknown confirmation token.")
                }
                // Verify arguments have NOT mutated between confirmation prompt and execution
                if (pending.argumentsHash != currentArgsHash) {
                    pendingConfirmations.remove(confirmedActionId)
                    val reason = "Security Alert: Action parameters changed after confirmation was requested. Re-confirmation required."
                    auditLogger.log(
                        toolName = toolMetadata.name,
                        risk = toolMetadata.risk,
                        outcome = "CONFIRMATION_ARG_MUTATION",
                        durationMs = System.currentTimeMillis() - startTime,
                        sanitizedDetails = reason
                    )
                    return createPendingConfirmation(toolMetadata, sanitizedArgs, currentArgsHash)
                }
                // Validated confirmation
                pendingConfirmations.remove(confirmedActionId)
                return PolicyDecision.Allow(sanitizedArgs)
            } else {
                // Must request confirmation from user
                return createPendingConfirmation(toolMetadata, sanitizedArgs, currentArgsHash)
            }
        }

        // 5. Allowed
        return PolicyDecision.Allow(sanitizedArgs)
    }

    private fun createPendingConfirmation(
        tool: ToolMetadata,
        args: Map<String, Any?>,
        argsHash: String
    ): PolicyDecision.RequireConfirmation {
        val target = when (tool.name) {
            "send_whatsapp_message" -> "WhatsApp Recipient: ${args["recipient"] ?: "Unknown"}"
            "request_app_install" -> "APK: ${args["apk_path"] ?: args["uri"] ?: "Selected file"}"
            "request_app_uninstall" -> "Package: ${args["package_name"] ?: "Unknown"}"
            else -> tool.name
        }

        val consequences = when (tool.risk) {
            ToolRisk.HIGH -> "High Risk: This will send external data or modify device application state."
            ToolRisk.MEDIUM -> "Medium Risk: This will access sensor hardware or shared storage."
            else -> "Standard action execution."
        }

        val pending = PendingConfirmation(
            toolName = tool.name,
            argumentsHash = argsHash,
            arguments = args,
            targetSummary = target,
            consequences = consequences,
            risk = tool.risk
        )
        pendingConfirmations[pending.actionId] = pending
        return PolicyDecision.RequireConfirmation(pending)
    }

    fun cancelConfirmation(actionId: String) {
        pendingConfirmations.remove(actionId)
    }

    private fun computeArgumentsHash(toolName: String, args: Map<String, Any?>): String {
        val canonical = "$toolName|" + args.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(canonical.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
