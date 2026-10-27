package com.apex.ai.mobile

import com.apex.ai.mobile.registry.ParameterDefinition
import com.apex.ai.mobile.registry.ParameterType
import com.apex.ai.mobile.registry.SchemaValidator
import com.apex.ai.mobile.registry.ToolMetadata
import com.apex.ai.mobile.registry.ToolRisk
import com.apex.ai.mobile.registry.ConfirmationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaValidatorTest {

    private val testTool = ToolMetadata(
        name = "test_tool",
        description = "Test Tool",
        category = "Testing",
        risk = ToolRisk.LOW,
        confirmation = ConfirmationPolicy.NEVER,
        parametersSchema = mapOf(
            "app_name" to ParameterDefinition(ParameterType.STRING, "App name", required = true),
            "count" to ParameterDefinition(ParameterType.INTEGER, "Count", required = false),
            "enabled" to ParameterDefinition(ParameterType.BOOLEAN, "Flag", required = true),
            "mode" to ParameterDefinition(ParameterType.STRING, "Mode", required = false, allowedValues = listOf("fast", "slow"))
        )
    )

    @Test
    fun testValidArgumentsPass() {
        val args = mapOf("app_name" to "WhatsApp", "count" to 5, "enabled" to true, "mode" to "fast")
        val result = SchemaValidator.validate(testTool, args)
        assertTrue(result is SchemaValidator.ValidationResult.Valid)
        val valid = result as SchemaValidator.ValidationResult.Valid
        assertEquals("WhatsApp", valid.sanitizedArguments["app_name"])
        assertEquals(5, valid.sanitizedArguments["count"])
        assertEquals(true, valid.sanitizedArguments["enabled"])
    }

    @Test
    fun testMissingRequiredParameterFails() {
        val args = mapOf("count" to 5) // Missing 'app_name' and 'enabled'
        val result = SchemaValidator.validate(testTool, args)
        assertTrue(result is SchemaValidator.ValidationResult.Invalid)
        val invalid = result as SchemaValidator.ValidationResult.Invalid
        assertTrue(invalid.errors.any { it.contains("app_name") })
    }

    @Test
    fun testInvalidTypeFails() {
        val args = mapOf("app_name" to "WhatsApp", "enabled" to "not_a_boolean")
        val result = SchemaValidator.validate(testTool, args)
        assertTrue(result is SchemaValidator.ValidationResult.Invalid)
    }

    @Test
    fun testDisallowedValueFails() {
        val args = mapOf("app_name" to "WhatsApp", "enabled" to true, "mode" to "unsupported_mode")
        val result = SchemaValidator.validate(testTool, args)
        assertTrue(result is SchemaValidator.ValidationResult.Invalid)
        val invalid = result as SchemaValidator.ValidationResult.Invalid
        assertTrue(invalid.errors.any { it.contains("unsupported_mode") })
    }

    @Test
    fun testInputSanitizationRemovesNullBytes() {
        val args = mapOf("app_name" to "WhatsApp\u0000Injected", "enabled" to true)
        val result = SchemaValidator.validate(testTool, args)
        assertTrue(result is SchemaValidator.ValidationResult.Valid)
        val valid = result as SchemaValidator.ValidationResult.Valid
        assertEquals("WhatsAppInjected", valid.sanitizedArguments["app_name"])
    }
}
