package com.apex.ai.mobile.voice

/**
 * Intelligent detector for English, Tamil, Tanglish, and Mixed speech input.
 *
 * Distinguishes:
 * 1. TAMIL: Written in native Tamil script (Unicode block U+0B80 - U+0BFF).
 * 2. TANGLISH: Colloquial Tamil vocabulary and verb suffixes transliterated into Latin characters.
 * 3. ENGLISH: Standard English syntax and vocabulary.
 * 4. MIXED: Interleaved Tamil/Tanglish and English phrases.
 */
object LanguageDetector {

    // Regex matching native Tamil Unicode range
    private val TAMIL_SCRIPT_REGEX = Regex("[\\u0B80-\\u0BFF]")

    // High-frequency Tanglish verbs, particles, pronouns, and question words
    private val TANGLISH_KEYWORDS = setOf(
        // Verbs & Imperatives
        "pannu", "pannunga", "podu", "podunga", "thira", "thiravum", "therandhu", "theranthu",
        "eduthu", "edu", "edunga", "kaattu", "kaatunga", "sollu", "sollunga", "anupu", "anupunga",
        "vai", "vainga", "kodu", "kodunga", "paaru", "paarunga", "off pannu", "on pannu", "anachu",
        "adichi", "adi", "kammi", "athigam", "yethu", "kura",

        // Pronouns & Interrogatives
        "en", "ennoda", "enna", "enakku", "evlo", "evvalavu", "epdi", "eppadi", "enga",
        "engada", "yaar", "yaaru", "idhu", "adhu", "ippo", "appo",

        // Auxiliaries & Particles
        "irukku", "irukka", "illai", "illa", "kooda", "mattum", "da", "di", "machi",
        "mudiyuma", "mudiyum", "tharen", "vendaam", "venam", "seri", "aama", "appadiye"
    )

    /**
     * Classifies the input string into one of the supported language categories.
     */
    fun detectLanguage(text: String): DetectedInputLanguage {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return DetectedInputLanguage.ENGLISH

        // 1. Check for native Tamil script
        val hasTamilScript = TAMIL_SCRIPT_REGEX.containsMatchIn(trimmed)
        val hasLatinScript = trimmed.any { it in 'a'..'z' || it in 'A'..'Z' }

        if (hasTamilScript) {
            return if (hasLatinScript) {
                DetectedInputLanguage.MIXED
            } else {
                DetectedInputLanguage.TAMIL
            }
        }

        // 2. Analyze Latin-script tokens for Tanglish markers
        val tokens = trimmed.lowercase().split(Regex("[\\s,?.!]+"))
        var tanglishMatches = 0

        for (token in tokens) {
            if (token.isBlank()) continue
            if (isTanglishToken(token)) {
                tanglishMatches++
            }
        }

        if (tanglishMatches > 0) {
            return DetectedInputLanguage.TANGLISH
        }

        return DetectedInputLanguage.ENGLISH
    }

    private fun isTanglishToken(token: String): Boolean {
        if (token in TANGLISH_KEYWORDS) return true

        // Suffix heuristics for Tanglish conjugated verbs (e.g. panniten, panreengala)
        val suffixes = listOf("pannu", "podu", "irukku", "kittu", "aakku")
        return suffixes.any { token.endsWith(it) && token.length > it.length }
    }
}
