package com.apex.ai.mobile.model

import org.json.JSONObject
import java.util.regex.Pattern

object FunctionGemmaPromptFormatter {

    const val BOS_TOKEN = "<bos>"
    const val START_OF_TURN = "<start_of_turn>"
    const val END_OF_TURN = "<end_of_turn>"
    const val START_DECLARATION = "<start_function_declaration>"
    const val END_DECLARATION = "<end_function_declaration>"
    const val START_CALL = "<start_function_call>"
    const val END_CALL = "<end_function_call>"
    const val START_RESPONSE = "<start_function_response>"
    const val END_RESPONSE = "<end_function_response>"
    const val ESCAPE_TOKEN = "<escape>"

    private val CALL_REGEX = Pattern.compile(
        "<start_function_call>\\s*call:([a-zA-Z0-9_]+)\\s*\\{(.*?)\\}\\s*<end_function_call>",
        Pattern.DOTALL
    )

    private val SIMPLE_CALL_REGEX = Pattern.compile(
        "call:([a-zA-Z0-9_]+)\\s*\\{(.*?)\\}",
        Pattern.DOTALL
    )

    fun formatPrompt(
        systemInstruction: String,
        toolsDeclarations: List<Map<String, Any?>>,
        history: List<Pair<String, String>>,
        userPrompt: String
    ): String {
        val sb = StringBuilder()
        sb.append(BOS_TOKEN)

        // Developer / System turn
        sb.append(START_OF_TURN).append("developer\n")
        sb.append(systemInstruction.trim())
        if (toolsDeclarations.isNotEmpty()) {
            sb.append("\nYou are a model that can do function calling with the following functions:")
            for (tool in toolsDeclarations) {
                sb.append(START_DECLARATION)
                val name = tool["name"] as? String ?: "tool"
                val description = tool["description"] as? String ?: ""
                sb.append("declaration:").append(name)
                sb.append("{description:").append(ESCAPE_TOKEN).append(description).append(ESCAPE_TOKEN)
                sb.append(",parameters:{type:").append(ESCAPE_TOKEN).append("OBJECT").append(ESCAPE_TOKEN).append("}}")
                sb.append(END_DECLARATION)
            }
        }
        sb.append(END_OF_TURN).append("\n")

        // Conversation history (bounded to last 10 turns to respect 1024-2048 token budget)
        val boundedHistory = if (history.size > 10) history.takeLast(10) else history
        for ((role, content) in boundedHistory) {
            val normalizedRole = if (role.equals("user", ignoreCase = true)) "user" else "model"
            sb.append(START_OF_TURN).append(normalizedRole).append("\n")
            sb.append(content.trim())
            sb.append(END_OF_TURN).append("\n")
        }

        // Current turn
        sb.append(START_OF_TURN).append("user\n")
        sb.append(userPrompt.trim())
        sb.append(END_OF_TURN).append("\n")

        // Cue model turn
        sb.append(START_OF_TURN).append("model\n")
        return sb.toString()
    }

    fun parseOutput(rawOutput: String): Pair<String, List<ToolCallProposal>> {
        val trimmed = rawOutput.trim()
        val toolCalls = mutableListOf<ToolCallProposal>()

        var matcher = CALL_REGEX.matcher(trimmed)
        var foundMatches = false

        while (matcher.find()) {
            foundMatches = true
            val toolName = matcher.group(1).trim()
            val argsBody = matcher.group(2).trim()
            val arguments = parseArguments(argsBody)
            toolCalls.add(
                ToolCallProposal(
                    toolName = toolName,
                    arguments = arguments,
                    rawCallText = matcher.group(0)
                )
            )
        }

        if (!foundMatches) {
            // Check fallback without control tokens if raw call:name{} occurred
            matcher = SIMPLE_CALL_REGEX.matcher(trimmed)
            while (matcher.find()) {
                val toolName = matcher.group(1).trim()
                val argsBody = matcher.group(2).trim()
                val arguments = parseArguments(argsBody)
                toolCalls.add(
                    ToolCallProposal(
                        toolName = toolName,
                        arguments = arguments,
                        rawCallText = matcher.group(0)
                    )
                )
            }
        }

        // Extract clean text if there's conversational text along with tool call
        val cleanText = trimmed
            .replace(CALL_REGEX.toRegex(), "")
            .replace(SIMPLE_CALL_REGEX.toRegex(), "")
            .replace(START_OF_TURN, "")
            .replace(END_OF_TURN, "")
            .replace(ESCAPE_TOKEN, "")
            .trim()

        return Pair(cleanText, toolCalls)
    }

    private fun parseArguments(rawArgs: String): Map<String, Any?> {
        if (rawArgs.isBlank()) return emptyMap()

        // 1. Try JSON parsing
        try {
            val jsonCandidate = if (!rawArgs.startsWith("{")) "{$rawArgs}" else rawArgs
            val obj = JSONObject(jsonCandidate)
            val map = mutableMapOf<String, Any?>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.get(key)
                map[key] = if (value is String) {
                    value.replace(ESCAPE_TOKEN, "").trim()
                } else {
                    value
                }
            }
            return map
        } catch (_: Exception) {
            // Fallback: parse key: <escape>val<escape> or key: "val" or key: val
            val result = mutableMapOf<String, Any?>()
            val pairs = rawArgs.split(",")
            for (pair in pairs) {
                val parts = pair.split(":", limit = 2)
                if (parts.size == 2) {
                    val k = parts[0].trim().replace("\"", "").replace("'", "")
                    var v = parts[1].trim()
                    v = v.replace(ESCAPE_TOKEN, "").replace("\"", "").replace("'", "").trim()
                    if (k.isNotBlank()) {
                        result[k] = v
                    }
                }
            }
            return result
        }
    }
}
