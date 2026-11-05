package com.apex.ai.mobile

import android.content.Context
import com.apex.ai.mobile.model.FunctionGemmaPromptFormatter
import com.apex.ai.mobile.model.GenerationRequest
import com.apex.ai.mobile.model.LiteRTLocalModelAdapter
import com.apex.ai.mobile.model.MockLocalModelAdapter
import com.apex.ai.mobile.model.ModelConfig
import com.apex.ai.mobile.model.ModelFormatValidator
import com.apex.ai.mobile.model.ModelHealth
import com.apex.ai.mobile.model.ModelImportStatus
import com.apex.ai.mobile.model.ModelImporter
import com.apex.ai.mobile.model.ModelRuntimeStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException

class LocalModelAdapterTest {

    @Test
    fun testFunctionGemmaPromptFormatting() {
        val tools = listOf(
            mapOf("name" to "get_battery_status", "description" to "Check battery")
        )
        val history = listOf(Pair("user", "Hello"), Pair("model", "Hi!"))

        // Test LiteRT-LM format (without <bos> because LiteRT-LM tokenizer prepends it automatically)
        val prompt = FunctionGemmaPromptFormatter.formatPrompt(
            systemInstruction = "APEX Assistant",
            toolsDeclarations = tools,
            history = history,
            userPrompt = "What is my battery level?",
            includeBos = false
        )
        assertTrue(prompt.startsWith("<start_of_turn>developer"))
        assertTrue(prompt.contains("<start_function_declaration>declaration:get_battery_status"))
        assertTrue(prompt.contains("<start_of_turn>user\nWhat is my battery level?<end_of_turn>"))
        assertTrue(prompt.endsWith("<start_of_turn>model\n"))

        // Test standard format with BOS
        val promptWithBos = FunctionGemmaPromptFormatter.formatPrompt(
            systemInstruction = "APEX Assistant",
            toolsDeclarations = tools,
            history = history,
            userPrompt = "What is my battery level?",
            includeBos = true
        )
        assertTrue(promptWithBos.startsWith("<bos><start_of_turn>developer"))
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

    @Test
    fun testMockModeExplicitFlag() {
        val mockAdapter = MockLocalModelAdapter()
        assertTrue("Mock adapter must declare isMock = true", mockAdapter.isMock)

        val mockContext = Mockito.mock(Context::class.java)
        val realAdapter = LiteRTLocalModelAdapter(mockContext)
        assertFalse("Production LiteRT adapter must declare isMock = false", realAdapter.isMock)
    }

    @Test
    fun testLiteRTAdapterMissingModelFailsHonestly() = runBlocking {
        val mockContext = Mockito.mock(Context::class.java)
        val tempEmptyDir = File(System.getProperty("java.io.tmpdir"), "empty_models_dir_" + System.currentTimeMillis())
        tempEmptyDir.mkdirs()
        tempEmptyDir.deleteOnExit()

        Mockito.`when`(mockContext.filesDir).thenReturn(tempEmptyDir)
        Mockito.`when`(mockContext.getExternalFilesDir(null)).thenReturn(tempEmptyDir)
        Mockito.`when`(mockContext.getExternalFilesDir("models")).thenReturn(File(tempEmptyDir, "models"))

        val adapter = LiteRTLocalModelAdapter(mockContext)
        val initResult = adapter.initialize(ModelConfig(modelPath = "models/nonexistent.litertlm"))

        assertTrue("Initialize must fail when model file is missing", initResult.isFailure)
        assertEquals(ModelRuntimeStatus.MODEL_MISSING, adapter.status.value)

        val lastError = adapter.lastError.value
        assertTrue("Must provide error info", lastError != null)
        assertEquals("MODEL_NOT_FOUND", lastError?.code)

        val health = adapter.checkHealth()
        assertTrue("Health must be Offline when model is missing", health is ModelHealth.Offline)

        val genResult = adapter.generate(GenerationRequest(prompt = "Check battery"))
        assertTrue("Generate must fail when model is missing", genResult.isFailure)
        assertTrue(
            "Must not produce simulated response when model missing",
            genResult.exceptionOrNull()?.message?.contains("not found", ignoreCase = true) == true
        )
    }

    @Test
    fun testCancellationAndTimeoutHandling() = runBlocking {
        val adapter = MockLocalModelAdapter(simulatedLatencyMs = 2000L)
        adapter.initialize(ModelConfig(timeoutMillis = 100L))

        adapter.cancelCurrentGeneration()
        val result = adapter.generate(GenerationRequest(prompt = "Take a photo"))
        assertTrue("Cancelled generation must fail with cancellation", result.isFailure)
    }

    @Test
    fun testModelPathResolutionSuccess() {
        val mockContext = Mockito.mock(Context::class.java)
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_model_resolve_" + System.currentTimeMillis())
        val modelsDir = File(tempDir, "models")
        modelsDir.mkdirs()
        tempDir.deleteOnExit()

        val dummyModel = File(modelsDir, "mobile_actions_q8_ekv1024.litertlm")
        dummyModel.writeBytes(ByteArray(1024 * 1024 + 10)) // > 1MB
        dummyModel.deleteOnExit()

        Mockito.`when`(mockContext.filesDir).thenReturn(tempDir)
        Mockito.`when`(mockContext.getExternalFilesDir(null)).thenReturn(tempDir)
        Mockito.`when`(mockContext.getExternalFilesDir("models")).thenReturn(modelsDir)

        val adapter = LiteRTLocalModelAdapter(mockContext)
        val resolved = adapter.resolveModelFile(ModelConfig(modelPath = "models/mobile_actions_q8_ekv1024.litertlm"))

        assertTrue("Should resolve existing model file", resolved != null)
        assertEquals(dummyModel.absolutePath, resolved?.absolutePath)
        assertTrue((resolved?.length() ?: 0) > 0)
    }

    @Test
    fun testModelImportValidationSuccess() = runBlocking {
        val mockContext = Mockito.mock(Context::class.java)
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_import_success_" + System.currentTimeMillis())
        tempDir.mkdirs()
        tempDir.deleteOnExit()

        Mockito.`when`(mockContext.filesDir).thenReturn(tempDir)

        val importer = ModelImporter(mockContext)
        val testDataSize = 1024 * 1024 + 500 // > 1MB
        val dummyData = ByteArray(testDataSize) { 0x42 }
        val stream = ByteArrayInputStream(dummyData)

        val result = importer.importModelFromStream(
            inputStream = stream,
            totalBytes = testDataSize.toLong(),
            targetFileName = "mobile_actions_q8_ekv1024.litertlm",
            targetDir = File(tempDir, "models"),
            minimumSizeBytes = 1024L * 1024L
        )

        assertTrue("Import must succeed with valid data", result.isSuccess)
        val imported = result.getOrNull()
        assertTrue("Imported file must exist", imported?.exists() == true)
        assertEquals("mobile_actions_q8_ekv1024.litertlm", imported?.name)
        assertEquals(testDataSize.toLong(), imported?.length())
        assertTrue("Status must be Success", importer.importStatus.value is ModelImportStatus.Success)
    }

    @Test
    fun testModelImportEmptyFileRejection() = runBlocking {
        val mockContext = Mockito.mock(Context::class.java)
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_import_empty_" + System.currentTimeMillis())
        tempDir.mkdirs()
        tempDir.deleteOnExit()

        Mockito.`when`(mockContext.filesDir).thenReturn(tempDir)

        val importer = ModelImporter(mockContext)
        val emptyStream = ByteArrayInputStream(ByteArray(0))

        val result = importer.importModelFromStream(
            inputStream = emptyStream,
            totalBytes = 0L,
            targetFileName = "mobile_actions_q8_ekv1024.litertlm",
            targetDir = File(tempDir, "models")
        )

        assertTrue("Import must fail for empty stream", result.isFailure)
        assertTrue("Status must be Error", importer.importStatus.value is ModelImportStatus.Error)
        val target = File(tempDir, "models/mobile_actions_q8_ekv1024.litertlm")
        assertFalse("Target file must not be left behind on empty failure", target.exists())
    }

    @Test
    fun testModelImportTinyFileRejection() = runBlocking {
        val mockContext = Mockito.mock(Context::class.java)
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_import_tiny_" + System.currentTimeMillis())
        tempDir.mkdirs()
        tempDir.deleteOnExit()

        Mockito.`when`(mockContext.filesDir).thenReturn(tempDir)

        val importer = ModelImporter(mockContext)
        val tinyData = ByteArray(1024) // 1KB only (less than 1MB minimum)
        val tinyStream = ByteArrayInputStream(tinyData)

        val result = importer.importModelFromStream(
            inputStream = tinyStream,
            totalBytes = tinyData.size.toLong(),
            targetFileName = "mobile_actions_q8_ekv1024.litertlm",
            targetDir = File(tempDir, "models"),
            minimumSizeBytes = 1024L * 1024L
        )

        assertTrue("Import must fail for tiny file < 1MB", result.isFailure)
        assertTrue(
            "Error message should mention file is too small",
            (importer.importStatus.value as? ModelImportStatus.Error)?.message?.contains("too small") == true
        )
    }

    @Test
    fun testLiteRtLmModelNotPassedToStandardTfLiteInterpreter() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_rtlm_guard_" + System.currentTimeMillis())
        tempDir.mkdirs()
        tempDir.deleteOnExit()

        // Create a model file with LiteRT-LM FlatBuffer header: identifier 'RTLM' at bytes 4..7
        val litertLmFile = File(tempDir, "test_actions_model.litertlm")
        val litertLmBytes = ByteArray(64)
        litertLmBytes[0] = 0x20
        litertLmBytes[4] = 'R'.code.toByte()
        litertLmBytes[5] = 'T'.code.toByte()
        litertLmBytes[6] = 'L'.code.toByte()
        litertLmBytes[7] = 'M'.code.toByte()
        litertLmFile.writeBytes(litertLmBytes)
        litertLmFile.deleteOnExit()

        assertTrue(ModelFormatValidator.isLiteRtLm(litertLmFile))
        assertFalse(ModelFormatValidator.isStandardTfLite(litertLmFile))
        assertEquals("RTLM", ModelFormatValidator.readModelIdentifier(litertLmFile))

        // Verify that passing .litertlm file to standard TFLite interpreter validator throws the expected error
        try {
            ModelFormatValidator.validateForStandardTfLiteInterpreter(litertLmFile)
            fail("Expected IllegalArgumentException when passing .litertlm ('RTLM') to standard TFLite interpreter")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "Error message must contain: Model provided has model identifier 'RTLM', should be 'TFL3'",
                e.message?.contains("Model provided has model identifier 'RTLM', should be 'TFL3'") == true
            )
        }

        // Verify that a standard TFLite model has 'TFL3' and passes standard validator
        val tfliteFile = File(tempDir, "test_legacy_model.tflite")
        val tfliteBytes = ByteArray(64)
        tfliteBytes[0] = 0x20
        tfliteBytes[4] = 'T'.code.toByte()
        tfliteBytes[5] = 'F'.code.toByte()
        tfliteBytes[6] = 'L'.code.toByte()
        tfliteBytes[7] = '3'.code.toByte()
        tfliteFile.writeBytes(tfliteBytes)
        tfliteFile.deleteOnExit()

        assertTrue(ModelFormatValidator.isStandardTfLite(tfliteFile))
        assertFalse(ModelFormatValidator.isLiteRtLm(tfliteFile))
        // Should succeed for standard TFLite
        ModelFormatValidator.validateForStandardTfLiteInterpreter(tfliteFile)

        // But should fail LiteRT-LM engine validation
        try {
            ModelFormatValidator.validateForLiteRtLmEngine(tfliteFile)
            fail("Expected IllegalArgumentException when passing .tflite ('TFL3') to LiteRT-LM engine")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "Error message must report TFL3 not compatible with RTLM",
                e.message?.contains("Model provided has model identifier 'TFL3', should be 'RTLM'") == true
            )
        }
    }

    @Test
    fun testRealModelFileIdentifierIsRtlm() {
        val candidates = listOf(
            File("../../models/mobile_actions_q8_ekv1024.litertlm"),
            File("../models/mobile_actions_q8_ekv1024.litertlm"),
            File("models/mobile_actions_q8_ekv1024.litertlm"),
            File("D:/APEX-MOBILE/models/mobile_actions_q8_ekv1024.litertlm")
        )
        val realModel = candidates.firstOrNull { it.exists() && it.isFile }
        if (realModel != null) {
            val id = ModelFormatValidator.readModelIdentifier(realModel)
            assertEquals("Real model file must carry 'RTLM' identifier", "RTLM", id)
            assertTrue("Must be identified as LiteRT-LM", ModelFormatValidator.isLiteRtLm(realModel))
            assertFalse("Must NOT be identified as standard TFLite", ModelFormatValidator.isStandardTfLite(realModel))
        }
    }

    @Test
    fun testLiteRTAdapterRejectsTfliteFileDirectly() = runBlocking {
        val mockContext = Mockito.mock(Context::class.java)
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_reject_tflite_" + System.currentTimeMillis())
        val modelsDir = File(tempDir, "models")
        modelsDir.mkdirs()
        tempDir.deleteOnExit()

        // Create a fake TFL3 model file named mobile_actions_q8_ekv1024.litertlm
        val fakeTfl3Model = File(modelsDir, "mobile_actions_q8_ekv1024.litertlm")
        val bytes = ByteArray(64)
        bytes[0] = 0x20
        bytes[4] = 'T'.code.toByte()
        bytes[5] = 'F'.code.toByte()
        bytes[6] = 'L'.code.toByte()
        bytes[7] = '3'.code.toByte()
        fakeTfl3Model.writeBytes(bytes)
        fakeTfl3Model.deleteOnExit()

        Mockito.`when`(mockContext.filesDir).thenReturn(tempDir)
        Mockito.`when`(mockContext.getExternalFilesDir(null)).thenReturn(tempDir)
        Mockito.`when`(mockContext.getExternalFilesDir("models")).thenReturn(modelsDir)

        val adapter = LiteRTLocalModelAdapter(mockContext)
        val initResult = adapter.initialize(ModelConfig(modelPath = "models/mobile_actions_q8_ekv1024.litertlm"))

        assertTrue("Initialize must fail when file is TFL3 instead of RTLM", initResult.isFailure)
        assertEquals(ModelRuntimeStatus.ERROR, adapter.status.value)
        assertEquals("INCOMPATIBLE_MODEL_FORMAT", adapter.lastError.value?.code)
    }
}
