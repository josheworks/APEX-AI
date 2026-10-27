package com.apex.ai.mobile

import com.apex.ai.mobile.policy.AuditLogger
import com.apex.ai.mobile.registry.ToolRisk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuditLoggerTest {

    private lateinit var auditLogger: AuditLogger

    @Before
    fun setUp() {
        auditLogger = AuditLogger()
    }

    @Test
    fun testAuditRecordLogged() {
        auditLogger.log(
            toolName = "get_battery_status",
            risk = ToolRisk.LOW,
            outcome = "SUCCESS",
            durationMs = 12L,
            sanitizedDetails = "Battery 95%"
        )

        val records = auditLogger.records.value
        assertEquals(1, records.size)
        assertEquals("get_battery_status", records[0].toolName)
        assertEquals(ToolRisk.LOW, records[0].risk)
        assertEquals("SUCCESS", records[0].outcome)
    }

    @Test
    fun testSensitiveDataIsRedactedInLogs() {
        auditLogger.log(
            toolName = "login_action",
            risk = ToolRisk.HIGH,
            outcome = "EXECUTED",
            durationMs = 45L,
            sanitizedDetails = "Attempted with password=SecretPassword123 and token=xyz987654"
        )

        val record = auditLogger.records.value[0]
        assertFalse(record.sanitizedDetails.contains("SecretPassword123"))
        assertFalse(record.sanitizedDetails.contains("xyz987654"))
        assertTrue(record.sanitizedDetails.contains("password=[REDACTED]"))
        assertTrue(record.sanitizedDetails.contains("token=[REDACTED]"))
    }

    @Test
    fun testClearAuditLog() {
        auditLogger.log("tool1", ToolRisk.LOW, "SUCCESS", 5L, "ok")
        assertEquals(1, auditLogger.records.value.size)
        auditLogger.clear()
        assertEquals(0, auditLogger.records.value.size)
    }
}
