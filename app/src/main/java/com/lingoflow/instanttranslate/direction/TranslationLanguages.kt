package com.lingoflow.instanttranslate.direction

/** Wire IDs are shared with the backend catalog; Auto is a source option only. */
data class TranslationLanguage(val id: String, val label: String, val romanized: Boolean = false)

object TranslationLanguages {
    val auto = TranslationLanguage("auto", "Detect language")
    val available = listOf(
        TranslationLanguage("en", "English", false),
        TranslationLanguage("as", "Assamese", false),
        TranslationLanguage("as-Latn", "Assamese (Roman)", true),
        TranslationLanguage("bn", "Bengali", false),
        TranslationLanguage("bn-Latn", "Bengali (Roman)", true),
        TranslationLanguage("brx", "Bodo", false),
        TranslationLanguage("brx-Latn", "Bodo (Roman)", true),
        TranslationLanguage("doi", "Dogri", false),
        TranslationLanguage("doi-Latn", "Dogri (Roman)", true),
        TranslationLanguage("gu", "Gujarati", false),
        TranslationLanguage("gu-Latn", "Gujarati (Roman)", true),
        TranslationLanguage("hi", "Hindi", false),
        TranslationLanguage("hi-Latn", "Hindi (Roman)", true),
        TranslationLanguage("kn", "Kannada", false),
        TranslationLanguage("kn-Latn", "Kannada (Roman)", true),
        TranslationLanguage("ks", "Kashmiri", false),
        TranslationLanguage("ks-Latn", "Kashmiri (Roman)", true),
        TranslationLanguage("kok", "Konkani", false),
        TranslationLanguage("kok-Latn", "Konkani (Roman)", true),
        TranslationLanguage("mai", "Maithili", false),
        TranslationLanguage("mai-Latn", "Maithili (Roman)", true),
        TranslationLanguage("ml", "Malayalam", false),
        TranslationLanguage("ml-Latn", "Malayalam (Roman)", true),
        TranslationLanguage("mni", "Manipuri", false),
        TranslationLanguage("mni-Latn", "Manipuri (Roman)", true),
        TranslationLanguage("mr", "Marathi", false),
        TranslationLanguage("mr-Latn", "Marathi (Roman)", true),
        TranslationLanguage("ne", "Nepali", false),
        TranslationLanguage("ne-Latn", "Nepali (Roman)", true),
        TranslationLanguage("or", "Odia", false),
        TranslationLanguage("or-Latn", "Odia (Roman)", true),
        TranslationLanguage("pa", "Punjabi", false),
        TranslationLanguage("pa-Latn", "Punjabi (Roman)", true),
        TranslationLanguage("sa", "Sanskrit", false),
        TranslationLanguage("sa-Latn", "Sanskrit (Roman)", true),
        TranslationLanguage("sat", "Santali", false),
        TranslationLanguage("sat-Latn", "Santali (Roman)", true),
        TranslationLanguage("sd", "Sindhi", false),
        TranslationLanguage("sd-Latn", "Sindhi (Roman)", true),
        TranslationLanguage("ta", "Tamil", false),
        TranslationLanguage("ta-Latn", "Tamil (Roman)", true),
        TranslationLanguage("te", "Telugu", false),
        TranslationLanguage("te-Latn", "Telugu (Roman)", true),
        TranslationLanguage("ur", "Urdu", false),
        TranslationLanguage("ur-Latn", "Urdu (Roman)", true),
        TranslationLanguage("ko", "Korean", false),
        TranslationLanguage("id", "Indonesian", false),
        TranslationLanguage("zh", "Chinese (Simplified)", false),
        TranslationLanguage("ja", "Japanese", false),
        TranslationLanguage("ru", "Russian", false),
        TranslationLanguage("fr", "French", false),
        TranslationLanguage("es", "Spanish", false),
        TranslationLanguage("de", "German", false),
        TranslationLanguage("ar", "Arabic", false),
        TranslationLanguage("pt", "Portuguese", false),
        TranslationLanguage("it", "Italian", false),
        TranslationLanguage("tr", "Turkish", false),
        TranslationLanguage("vi", "Vietnamese", false),
        TranslationLanguage("th", "Thai", false),
        TranslationLanguage("nl", "Dutch", false),
        TranslationLanguage("pl", "Polish", false),
        TranslationLanguage("uk", "Ukrainian", false),
        TranslationLanguage("ms", "Malay", false),
        TranslationLanguage("fil", "Filipino", false),
        TranslationLanguage("fa", "Persian", false),
        TranslationLanguage("he", "Hebrew", false),
        TranslationLanguage("sv", "Swedish", false),
    )
    fun find(id: String): TranslationLanguage? = if (id == auto.id) auto else available.find { it.id == id }
}

data class TranslationLanguagePair(val source: String = "auto", val target: String = "en") {
    fun isValid() = TranslationLanguages.find(source) != null &&
        target != "auto" && TranslationLanguages.find(target) != null
    fun swapped() = TranslationLanguagePair(target, if (source == "auto") "hi-Latn" else source)
}
