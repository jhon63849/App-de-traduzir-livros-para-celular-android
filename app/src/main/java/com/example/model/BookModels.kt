package com.example.model

enum class BookFormat(val label: String) {
    EPUB("EPUB"),
    PDF("PDF");

    companion object {
        fun fromString(value: String): BookFormat =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: EPUB
    }
}

/**
 * Ponteiro ultraleve de paginação em RAM (apenas índices inteiros, sem reter o texto do livro inteiro).
 */
data class PageSegmentPointer(
    val paragraphIndex: Int,
    val startChar: Int,
    val endChar: Int,
    val isContinuation: Boolean = false
)

data class PagePointer(
    val pageIndex: Int,
    val chapterTitle: String,
    val segments: List<PageSegmentPointer>
)

/**
 * Bloco renderizável da página ativa (carregado sob demanda para a janela [página-1, página, página+1]).
 */
data class PageTextBlock(
    val paragraphIndex: Int,
    val startChar: Int,
    val endChar: Int,
    val originalText: String,
    val translatedText: String? = null,
    val isContinuation: Boolean = false
)

enum class PageTranslationStatus {
    IDLE,
    TRANSLATING,
    READY,
    MODEL_MISSING,
    ERROR
}

data class LoadedPage(
    val pageIndex: Int,
    val chapterTitle: String,
    val blocks: List<PageTextBlock>,
    val translationStatus: PageTranslationStatus = PageTranslationStatus.IDLE,
    val errorMessage: String? = null
)
