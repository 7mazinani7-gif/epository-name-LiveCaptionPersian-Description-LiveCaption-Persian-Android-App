package com.livecaption.persian

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

/** Free on-device translation after the required language models have been downloaded. */
class CaptionTranslator(private val onResult: (String) -> Unit, private val onStatus: (String) -> Unit) {
    private val identifier = LanguageIdentification.getClient()
    private var translator: com.google.mlkit.nl.translate.Translator? = null
    private var activeSource: String? = null
    private var activeTarget: String? = null
    @Volatile private var requestGeneration: Long = 0L

    fun translate(text: String, targetLabel: String) {
        if (text.isBlank()) return
        val generation = ++requestGeneration
        fun current() = generation == requestGeneration
        val target = targetCode(targetLabel) ?: run { if (current()) onStatus("زبان مقصد فعلاً پشتیبانی نمی‌شود."); return }
        identifier.identifyLanguage(text).addOnSuccessListener { source ->
            if (!current()) return@addOnSuccessListener
            if (source == "und") { onStatus("زبان گفتار تشخیص داده نشد."); return@addOnSuccessListener }
            val sourceCode = TranslateLanguage.fromLanguageTag(source)
            if (sourceCode == null) { onStatus("مدل ترجمه این زبان در دسترس نیست."); return@addOnSuccessListener }
            if (sourceCode == target) { onResult(text); return@addOnSuccessListener }
            if (activeSource != sourceCode || activeTarget != target || translator == null) {
                translator?.close()
                activeSource = sourceCode
                activeTarget = target
                translator = Translation.getClient(TranslatorOptions.Builder()
                    .setSourceLanguage(sourceCode).setTargetLanguage(target).build())
            }
            val selectedTranslator = translator
            selectedTranslator?.downloadModelIfNeeded()
                ?.addOnSuccessListener {
                    if (!current()) return@addOnSuccessListener
                    selectedTranslator.translate(text)
                        .addOnSuccessListener { result -> if (current()) onResult(result) }
                        .addOnFailureListener { if (current()) onStatus("ترجمه ناموفق بود؛ اتصال اینترنت و فضای ذخیره‌سازی را بررسی کن.") }
                }
                ?.addOnFailureListener { if (current()) onStatus("برای دریافت مدل ترجمه، اینترنت لازم است.") }
        }.addOnFailureListener { if (current()) onStatus("تشخیص زبان ناموفق بود.") }
    }

    private fun targetCode(label: String): String? = when (label) {
        "فارسی" -> TranslateLanguage.PERSIAN
        "English" -> TranslateLanguage.ENGLISH
        "العربية" -> TranslateLanguage.ARABIC
        "Deutsch" -> TranslateLanguage.GERMAN
        "Français" -> TranslateLanguage.FRENCH
        "中文" -> TranslateLanguage.CHINESE
        "日本語" -> TranslateLanguage.JAPANESE
        "Español" -> TranslateLanguage.SPANISH
        "Русский" -> TranslateLanguage.RUSSIAN
        "Türkçe" -> TranslateLanguage.TURKISH
        "اردو" -> TranslateLanguage.URDU
        "हिन्दी" -> TranslateLanguage.HINDI
        else -> null
    }

    fun close() { requestGeneration++; translator?.close(); identifier.close() }
}
