package com.apex.ai.mobile

import com.apex.ai.mobile.model.FunctionGemmaPromptFormatter
import com.apex.ai.mobile.model.GenerationRequest
import com.apex.ai.mobile.model.MockLocalModelAdapter
import com.apex.ai.mobile.model.ModelConfig
import com.apex.ai.mobile.model.ModelRuntimeStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelAdapterTest {

    @Test
    fun testFunctionGemmaPromptFormatting() {
        val tools = listOf(
            mapOf("name" to "get_battery_status", "description" to "Check battery")
        )
        val history = listOf(Pair("user", "Hello"), Pair("model", "Hi!"))
        val prompt = FunctionGemmaPromptFormatter.formatPrompt(
            systemInstruction = "APEX Assistant",
            toolsDeclarations = tools,
            history = history,
            userPrompt = "What is my battery level?"
        )

        assertTrue(prompt.startsWith("<bos><start_of_turn>developer"))
        assertTrue(prompt.contains("<start_function_declaration>declaration:get_battery_status"))
        assertTrue(prompt.contains("<start_of_turn>user\nWhat is my battery level?<end_of_turn>"))
        assertTrue(prompt.endsWith("<start_of_turn>model\n"))
    }

    @Test
    fun testFunctionGemmaToolCallParsing() {
        val rawModelOutput = "<start_function_call>call:open_app{app_name:<escape>WhatsApp<escape>}<end_function_call>"
        val (text, calls) = FunctionGemmaPromptFormatter.parseOutput(rawModelOutput)

        assertEquals(1, calls.size)
        assertEquals("open_app", calls[0].toolName)
        assertEquals("WhatsApp", calls[0].arguments["app_name"])
    }

    @Test
    fun testFunctionGemmaJsonCallParsing() {
        val rawModelOutput = "<start_function_call>call:send_whatsapp_message{\"recipient\":\"Abi\",\"message\":\"I will call later\"}<end_function_call>"
        val (text, calls) = FunctionGemmaPromptFormatter.parseOutput(rawModelOutput)

        assertEquals(1, calls.size)
        assertEquals("send_whatsapp_message", calls[0].toolName)
        assertEquals("Abi", calls[0].arguments["recipient"])
        assertEquals("I will call later", calls[0].arguments["message"])
    }

    @Test
    fun testFunctionGemmaMalformedRecovery() {
        val rawModelOutput = "I will check your battery now. call:get_battery_status{}"
        val (text, calls) = FunctionGemmaPromptFormatter.parseOutput(rawModelOutput)

        assertEquals(1, calls.size)
        assertEquals("get_battery_status", calls[0].toolName)
    }

    @Test
    fun testMockModelInferenceAndCancellation() = runBlocking {
        val adapter = MockLocalModelAdapter(simulatedLatencyMs = 50L)
        adapter.initialize(ModelConfig())
        assertEquals(ModelRuntimeStatus.READY, adapter.status.value)

        val result = adapter.generate(GenerationRequest(prompt = "Check battery status"))
        assertTrue(result.isSuccess)
        val gen = result.getOrNull()
        assertTrue(gen != null)
        assertEquals(1, gen?.toolCalls?.size)
        assertEquals("get_battery_status", gen?.toolCalls?.get(0)?.toolName)
    }
}
