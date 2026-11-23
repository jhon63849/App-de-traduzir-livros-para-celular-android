package com.example.model

import com.example.ui.theme.AmoledReaderPalette
import com.example.ui.theme.LightReaderPalette
import com.example.ui.theme.PaperReaderPalette
import com.example.ui.theme.ReaderThemePalette
import com.example.ui.theme.SepiaReaderPalette

enum class ReadingTheme(val labelPt: String, val subtitlePt: String) {
    PAPER("Papel", "Amarelado suave"),
    SEPIA("Sépia", "Conforto clássico"),
    LIGHT("Claro", "Alto contraste"),
    AMOLED("AMOLED", "Preto puro");

    fun palette(): ReaderThemePalette = when (this) {
        PAPER -> PaperReaderPalette
        SEPIA -> SepiaReaderPalette
        LIGHT -> LightReaderPalette
        AMOLED -> AmoledReaderPalette
    }
}

enum class TranslationDisplayMode(val labelPt: String, val shortLabelPt: String) {
    TRANSLATION_ONLY("Só Tradução", "Tradução"),
    ORIGINAL_ONLY("Só Original", "Original"),
    BILINGUAL("Bilíngue", "Bilíngue");

    fun next(): TranslationDisplayMode = when (this) {
        TRANSLATION_ONLY -> ORIGINAL_ONLY
        ORIGINAL_ONLY -> BILINGUAL
        BILINGUAL -> TRANSLATION_ONLY
    }
}

enum class ReaderFontOption(val labelPt: String) {
    SERIF_LITERATA("Literata (Serifa)"),
    SANS_JAKARTA("Jakarta (Sem Serifa)")
}

data class ReaderPreferences(
    val readingTheme: ReadingTheme = ReadingTheme.PAPER,
    val fontSizeSp: Float = 19f,
    val lineSpacingMultiplier: Float = 1.5f,
    val fontOption: ReaderFontOption = ReaderFontOption.SERIF_LITERATA,
    val displayMode: TranslationDisplayMode = TranslationDisplayMode.BILINGUAL,
    val requireWifiForDownloads: Boolean = true,
    val defaultTargetLanguage: SupportedLanguage = SupportedLanguage.PORTUGUESE
)
