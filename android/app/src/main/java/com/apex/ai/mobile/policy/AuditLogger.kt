package com.apex.ai.mobile.policy

import com.apex.ai.mobile.registry.ToolRisk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AuditRecord(
    val id: Long = System.currentTimeMillis(),
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
    val toolName: String,
    val risk: ToolRisk,
    val outcome: String,
    val durationMs: Long,
    val sanitizedDetails: String
)

class AuditLogger {

    private val _records = MutableStateFlow<List<AuditRecord>>(emptyList())
    val records: StateFlow<List<AuditRecord>> = _records.asStateFlow()

    fun log(
        toolName: String,
        risk: ToolRisk,
        outcome: String,
        durationMs: Long,
        sanitizedDetails: String
    ) {
        // Redact any accidental sensitive content (e.g. passwords, tokens)
        val cleanDetails = sanitizedDetails
            .replace(Regex("(?i)password[=:]\\s*\\S+"), "password=[REDACTED]")
            .replace(Regex("(?i)token[=:]\\s*\\S+"), "token=[REDACTED]")
            .replace(Regex("(?i)api[-_]?key[=:]\\s*\\S+"), "api_key=[REDACTED]")

        val record = AuditRecord(
            toolName = toolName,
            risk = risk,
            outcome = outcome,
            durationMs = durationMs,
            sanitizedDetails = cleanDetails
        )

        val updated = (_records.value + record).takeLast(100) // Keep bounded in-memory log
        _records.value = updated
    }

    fun clear() {
        _records.value = emptyList()
    }
}
