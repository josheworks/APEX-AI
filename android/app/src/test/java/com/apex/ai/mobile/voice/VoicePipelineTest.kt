package com.apex.ai.mobile.voice

import com.apex.ai.mobile.registry.ToolExecutionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePipelineTest {

    @Test
    fun testLanguageDetection_English() {
        val input = "Open WhatsApp and check my messages."
        val detected = LanguageDetector.detectLanguage(input)
        assertEquals(DetectedInputLanguage.ENGLISH, detected)
    }

    @Test
    fun testLanguageDetection_Tamil() {
        val input = "வாட்ஸ்அப் திற"
        val detected = LanguageDetector.detectLanguage(input)
        assertEquals(DetectedInputLanguage.TAMIL, detected)
    }

    @Test
    fun testLanguageDetection_Tanglish() {
        val input1 = "WhatsApp open pannu"
        val detected1 = LanguageDetector.detectLanguage(input1)
        assertEquals(DetectedInputLanguage.TANGLISH, detected1)

        val input2 = "En battery evlo irukku?"
        val detected2 = LanguageDetector.detectLanguage(input2)
        assertEquals(DetectedInputLanguage.TANGLISH, detected2)

        val input3 = "Torch on pannu"
        val detected3 = LanguageDetector.detectLanguage(input3)
        assertEquals(DetectedInputLanguage.TANGLISH, detected3)
    }

    @Test
    fun testTranscriptionNormalizer_TanglishToEnglishIntent() {
        val (normalized1, lang1) = TranscriptionNormalizer.normalizeInput("WhatsApp open pannu")
        assertEquals(DetectedInputLanguage.TANGLISH, lang1)
        assertEquals("open WhatsApp", normalized1)

        val (normalized2, lang2) = TranscriptionNormalizer.normalizeInput("En battery evlo irukku")
        assertEquals(DetectedInputLanguage.TANGLISH, lang2)
        assertEquals("check battery status", normalized2)

        val (normalized3, lang3) = TranscriptionNormalizer.normalizeInput("torch on pannu")
        assertEquals(DetectedInputLanguage.TANGLISH, lang3)
        assertEquals("turn on flashlight", normalized3)

        val (normalized4, lang4) = TranscriptionNormalizer.normalizeInput("wifi on pannu")
        assertEquals(DetectedInputLanguage.TANGLISH, lang4)
        assertEquals("enable Wi-Fi", normalized4)
    }

    @Test
    fun testTranscriptionNormalizer_TamilToEnglishIntent() {
        val (normalized1, lang1) = TranscriptionNormalizer.normalizeInput("வாட்ஸ்அப் திற")
        assertEquals(DetectedInputLanguage.TAMIL, lang1)
        assertEquals("open WhatsApp", normalized1)

        val (normalized2, lang2) = TranscriptionNormalizer.normalizeInput("பேட்டரி எவ்வளவு")
        assertEquals(DetectedInputLanguage.TAMIL, lang2)
        assertEquals("check battery status", normalized2)
    }

    @Test
    fun testSpokenResponse_AlwaysInEnglish() {
        // Battery status result
        val batteryResult = ToolExecutionResult.Success(
            toolName = "get_battery_status",
            output = mapOf("percentage" to 82, "is_charging" to false),
            message = "Battery status retrieved"
        )
        val batterySpoken = TranscriptionNormalizer.formatSpokenResponse(batteryResult)
        assertEquals("Your battery is at 82 percent.", batterySpoken)

        // Open app result
        val openAppResult = ToolExecutionResult.Success(
            toolName = "open_app",
            output = mapOf("app_name" to "WhatsApp", "package_name" to "com.whatsapp"),
            message = "WhatsApp opened"
        )
        val openAppSpoken = TranscriptionNormalizer.formatSpokenResponse(openAppResult)
        assertEquals("Opening WhatsApp.", openAppSpoken)

        // Flashlight toggle
        val torchResult = ToolExecutionResult.Success(
            toolName = "toggle_flashlight",
            output = mapOf("state" to true),
            message = "Flashlight toggled"
        )
        val torchSpoken = TranscriptionNormalizer.formatSpokenResponse(torchResult)
        assertEquals("Flashlight is now turned on.", torchSpoken)

        // Failed action error sanitation
        val failedResult = ToolExecutionResult.Failed(
            toolName = "open_app",
            errorCode = "NOT_INSTALLED",
            errorMessage = "Package com.example.fakeapp is not installed on device"
        )
        val failedSpoken = TranscriptionNormalizer.formatSpokenResponse(failedResult)
        assertTrue(failedSpoken.startsWith("I couldn't complete that action."))
        assertFalse("Spoken response must not contain raw package name", failedSpoken.contains("com.example.fakeapp"))
    }

    @Test
    fun testVoiceActivityDetector_EnergyCalculation() {
        val vad = VoiceActivityDetector(speechThresholdRms = 0.05f)

        // Silent audio chunk (zeros)
        val silentChunk = ShortArray(1600) { 0 }
        assertFalse(vad.isSpeechFrame(silentChunk))

        // Audio chunk with loud speech signal
        val speechChunk = ShortArray(1600) { (it % 100 * 300).toShort() }
        assertTrue(vad.isSpeechFrame(speechChunk))
    }
}
