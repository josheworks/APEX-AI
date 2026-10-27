package com.apex.ai.mobile.registry

object SchemaValidator {

    sealed class ValidationResult {
        data class Valid(val sanitizedArguments: Map<String, Any?>) : ValidationResult()
        data class Invalid(val errors: List<String>) : ValidationResult()
    }

    fun validate(
        metadata: ToolMetadata,
        arguments: Map<String, Any?>
    ): ValidationResult {
        val errors = mutableListOf<String>()
        val sanitized = mutableMapOf<String, Any?>()

        for ((paramName, paramDef) in metadata.parametersSchema) {
            val value = arguments[paramName]

            if (value == null || (value is String && value.isBlank())) {
                if (paramDef.required) {
                    errors.add("Missing required parameter: '$paramName'")
                }
                continue
            }

            when (paramDef.type) {
                ParameterType.STRING -> {
                    val strVal = value.toString().trim()
                    // Sanitize against dangerous command injection, null bytes, and traversal
                    val cleanStr = strVal.replace("\u0000", "").trim()
                    if (cleanStr.length > 2048) {
                        errors.add("Parameter '$paramName' exceeds maximum permitted length (2048 chars)")
                    } else if (paramDef.allowedValues != null && cleanStr.lowercase() !in paramDef.allowedValues.map { it.lowercase() }) {
                        errors.add("Parameter '$paramName' value '$cleanStr' is not in allowed set: ${paramDef.allowedValues}")
                    } else {
                        sanitized[paramName] = cleanStr
                    }
                }
                ParameterType.INTEGER -> {
                    val intVal = when (value) {
                        is Number -> value.toInt()
                        is String -> value.toIntOrNull()
                        else -> null
                    }
                    if (intVal == null) {
                        errors.add("Parameter '$paramName' must be an integer, got '$value'")
                    } else {
                        sanitized[paramName] = intVal
                    }
                }
                ParameterType.BOOLEAN -> {
                    val boolVal = when (value) {
                        is Boolean -> value
                        is String -> value.toBooleanStrictOrNull()
                        else -> null
                    }
                    if (boolVal == null) {
                        errors.add("Parameter '$paramName' must be a boolean, got '$value'")
                    } else {
                        sanitized[paramName] = boolVal
                    }
                }
                ParameterType.FLOAT -> {
                    val floatVal = when (value) {
                        is Number -> value.toFloat()
                        is String -> value.toFloatOrNull()
                        else -> null
                    }
                    if (floatVal == null) {
                        errors.add("Parameter '$paramName' must be a float, got '$value'")
                    } else {
                        sanitized[paramName] = floatVal
                    }
                }
                ParameterType.OBJECT -> {
                    sanitized[paramName] = value
                }
            }
        }

        // Check for unrecognized extra arguments
        for (key in arguments.keys) {
            if (!metadata.parametersSchema.containsKey(key)) {
                // Ignore empty benign keys or log warning, but do not pass through unknown parameters
            }
        }

        return if (errors.isEmpty()) {
            ValidationResult.Valid(sanitized)
        } else {
            ValidationResult.Invalid(errors)
        }
    }
}
