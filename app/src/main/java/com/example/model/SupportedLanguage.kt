package com.example.model

import com.google.mlkit.nl.translate.TranslateLanguage

/**
 * Conjunto enxuto de 7 idiomas modernos leves (~30 MB por pacote no ML Kit On-Device),
 * otimizado para celulares com espaço interno reduzido.
 */
enum class SupportedLanguage(
    val mlKitCode: String,
    val codeLabel: String,
    val displayNamePt: String,
    val nativeName: String,
    val estimatedSizeMb: Double
) {
    ENGLISH(
        mlKitCode = TranslateLanguage.ENGLISH,
        codeLabel = "EN",
        displayNamePt = "Inglês",
        nativeName = "English",
        estimatedSizeMb = 29.8
    ),
    PORTUGUESE(
        mlKitCode = TranslateLanguage.PORTUGUESE,
        codeLabel = "PT",
        displayNamePt = "Português",
        nativeName = "Português",
        estimatedSizeMb = 30.4
    ),
    SPANISH(
        mlKitCode = TranslateLanguage.SPANISH,
        codeLabel = "ES",
        displayNamePt = "Espanhol",
        nativeName = "Español",
        estimatedSizeMb = 30.1
    ),
    GERMAN(
        mlKitCode = TranslateLanguage.GERMAN,
        codeLabel = "DE",
        displayNamePt = "Alemão",
        nativeName = "Deutsch",
        estimatedSizeMb = 31.2
    ),
    FRENCH(
        mlKitCode = TranslateLanguage.FRENCH,
        codeLabel = "FR",
        displayNamePt = "Francês",
        nativeName = "Français",
        estimatedSizeMb = 30.6
    ),
    ITALIAN(
        mlKitCode = TranslateLanguage.ITALIAN,
        codeLabel = "IT",
        displayNamePt = "Italiano",
        nativeName = "Italiano",
        estimatedSizeMb = 29.9
    ),
    CHINESE(
        mlKitCode = TranslateLanguage.CHINESE,
        codeLabel = "ZH",
        displayNamePt = "Chinês",
        nativeName = "中文",
        estimatedSizeMb = 32.5
    );

    companion object {
        fun fromCode(code: String): SupportedLanguage {
            return entries.firstOrNull { it.mlKitCode.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}

sealed interface LanguageModelStatus {
    data object NotDownloaded : LanguageModelStatus
    data object Downloading : LanguageModelStatus
    data class Downloaded(val sizeMb: Double, val measuredOnDisk: Boolean) : LanguageModelStatus
    data class Error(val message: String) : LanguageModelStatus
}

data class LanguagePackItem(
    val language: SupportedLanguage,
    val status: LanguageModelStatus
) {
    val isDownloaded: Boolean
        get() = status is LanguageModelStatus.Downloaded

    val displaySizeMb: Double
        get() = when (status) {
            is LanguageModelStatus.Downloaded -> status.sizeMb
            else -> language.estimatedSizeMb
        }
}
