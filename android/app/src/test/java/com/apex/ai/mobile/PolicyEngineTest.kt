package com.apex.ai.mobile

import android.content.Context
import com.apex.ai.mobile.policy.AuditLogger
import com.apex.ai.mobile.policy.PolicyDecision
import com.apex.ai.mobile.policy.PolicyEngine
import com.apex.ai.mobile.registry.ConfirmationPolicy
import com.apex.ai.mobile.registry.ParameterDefinition
import com.apex.ai.mobile.registry.ParameterType
import com.apex.ai.mobile.registry.ToolMetadata
import com.apex.ai.mobile.registry.ToolRisk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class PolicyEngineTest {

    private lateinit var auditLogger: AuditLogger
    private lateinit var policyEngine: PolicyEngine
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        auditLogger = AuditLogger()
        policyEngine = PolicyEngine(auditLogger)
        mockContext = Mockito.mock(Context::class.java)
    }

    @Test
    fun testRestrictedOperationIsAlwaysRejected() {
        val restrictedTool = ToolMetadata(
            name = "silent_install_apk",
            description = "Silent install",
            category = "Restricted",
            risk = ToolRisk.RESTRICTED,
            confirmation = ConfirmationPolicy.NOT_ALLOWED
        )

        val decision = policyEngine.evaluate(mockContext, restrictedTool, emptyMap())
        assertTrue("Restricted tool must be rejected", decision is PolicyDecision.Reject)
        val reject = decision as PolicyDecision.Reject
        assertTrue(reject.isRestricted)
    }

    @Test
    fun testLowRiskToolAllowedDirectly() {
        val lowRiskTool = ToolMetadata(
            name = "get_battery_status",
            description = "Battery level",
            category = "Device",
            risk = ToolRisk.LOW,
            confirmation = ConfirmationPolicy.NEVER
        )

        val decision = policyEngine.evaluate(mockContext, lowRiskTool, emptyMap())
        assertTrue("Low risk tool must be allowed without confirmation", decision is PolicyDecision.Allow)
    }

    @Test
    fun testHighRiskToolRequiresConfirmation() {
        val highRiskTool = ToolMetadata(
            name = "send_whatsapp_message",
            description = "Send message",
            category = "Communication",
            risk = ToolRisk.HIGH,
            confirmation = ConfirmationPolicy.ALWAYS,
            parametersSchema = mapOf(
                "recipient" to ParameterDefinition(ParameterType.STRING, "Recipient", required = true),
                "message" to ParameterDefinition(ParameterType.STRING, "Message text", required = true)
            )
        )

        val args = mapOf("recipient" to "Abi", "message" to "I will call later")
        val decision = policyEngine.evaluate(mockContext, highRiskTool, args)

        assertTrue("High risk tool must trigger RequireConfirmation", decision is PolicyDecision.RequireConfirmation)
        val req = decision as PolicyDecision.RequireConfirmation
        assertNotNull(req.pendingConfirmation.actionId)
        assertEquals("send_whatsapp_message", req.pendingConfirmation.toolName)

        // Validating with token allows execution
        val confirmedDecision = policyEngine.evaluate(
            mockContext,
            highRiskTool,
            args,
            confirmedActionId = req.pendingConfirmation.actionId
        )
        assertTrue("Confirmed action must be allowed", confirmedDecision is PolicyDecision.Allow)
    }

    @Test
    fun testConfirmationInvalidatedIfParametersMutated() {
        val highRiskTool = ToolMetadata(
            name = "send_whatsapp_message",
            description = "Send message",
            category = "Communication",
            risk = ToolRisk.HIGH,
            confirmation = ConfirmationPolicy.ALWAYS,
            parametersSchema = mapOf(
                "recipient" to ParameterDefinition(ParameterType.STRING, "Recipient", required = true),
                "message" to ParameterDefinition(ParameterType.STRING, "Message text", required = true)
            )
        )

        val originalArgs = mapOf("recipient" to "Abi", "message" to "I will call later")
        val decision = policyEngine.evaluate(mockContext, highRiskTool, originalArgs)
        val req = decision as PolicyDecision.RequireConfirmation

        // Mutate recipient to Mallory!
        val mutatedArgs = mapOf("recipient" to "Mallory", "message" to "I will call later")
        val mutatedDecision = policyEngine.evaluate(
            mockContext,
            highRiskTool,
            mutatedArgs,
            confirmedActionId = req.pendingConfirmation.actionId
        )

        // Must reject or require fresh confirmation, NEVER allow mutated payload
        assertTrue(
            "Mutated arguments must require fresh confirmation",
            mutatedDecision is PolicyDecision.RequireConfirmation
        )
    }
}
