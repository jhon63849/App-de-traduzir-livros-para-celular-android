package com.example.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import com.example.data.BookRepository
import com.example.model.PagePointer
import com.example.model.PageSegmentPointer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.math.ceil

data class PaginationConfig(
    val contentWidthPx: Int,
    val contentHeightPx: Int,
    val paragraphSpacingPx: Int,
    val bilingualMode: Boolean,
    val textStyle: TextStyle
)

/**
 * Paginador real estilo Kindle:
 * Mede exatamente quantas linhas cabem na altura útil da tela do celular usando TextMeasurer,
 * processando os parágrafos do Room em lotes pequenos (64 por vez) e retendo em RAM apenas
 * ponteiros leves de índices (PagePointer), sem nunca carregar o texto completo do livro na memória.
 */
object KindlePaginator {

    private const val STREAM_CHUNK_SIZE = 64

    suspend fun paginateBook(
        bookId: Long,
        totalParagraphs: Int,
        repository: BookRepository,
        textMeasurer: TextMeasurer,
        config: PaginationConfig
    ): List<PagePointer> = withContext(Dispatchers.Default) {
        if (totalParagraphs <= 0 || config.contentWidthPx <= 40 || config.contentHeightPx <= 80) {
            return@withContext emptyList()
        }

        val pages = ArrayList<PagePointer>()
        val currentSegments = ArrayList<PageSegmentPointer>()
        var currentChapter = ""
        var remainingHeight = config.contentHeightPx

        // No modo bilíngue, reservamos espaço para o texto original + tradução abaixo + margem interna.
        val heightFactor = if (config.bilingualMode) 2.25f else 1.08f
        val extraPerBlockPx = if (config.bilingualMode) {
            (config.paragraphSpacingPx * 1.4f).toInt()
        } else {
            config.paragraphSpacingPx
        }
        val constraints = Constraints(maxWidth = config.contentWidthPx)

        fun commitPage() {
            if (currentSegments.isNotEmpty()) {
                pages.add(
                    PagePointer(
                        pageIndex = pages.size,
                        chapterTitle = currentChapter,
                        segments = ArrayList(currentSegments)
                    )
                )
                currentSegments.clear()
                remainingHeight = config.contentHeightPx
            }
        }

        var cursor = 0
        while (cursor < totalParagraphs) {
            yield()
            val chunk = repository.getParagraphWindow(
                bookId = bookId,
                startIndex = cursor,
                endExclusive = (cursor + STREAM_CHUNK_SIZE).coerceAtMost(totalParagraphs)
            )
            if (chunk.isEmpty()) break

            for (paragraph in chunk) {
                val rawText = paragraph.text.trim()
                if (rawText.isEmpty()) continue

                if (currentSegments.isEmpty()) {
                    currentChapter = paragraph.chapterTitle
                } else if (paragraph.chapterTitle != currentChapter &&
                    paragraph.chapterTitle.isNotBlank() &&
                    !paragraph.chapterTitle.startsWith("PDF")
                ) {
                    // Novo capítulo em EPUB começa em página limpa estilo Kindle.
                    commitPage()
                    currentChapter = paragraph.chapterTitle
                }

                var startOffset = 0
                var isContinuation = false

                while (startOffset < rawText.length) {
                    val slice = rawText.substring(startOffset)
                    val layout = textMeasurer.measure(
                        text = AnnotatedString(slice),
                        style = config.textStyle,
                        constraints = constraints
                    )

                    val measuredHeight = ceil(layout.size.height * heightFactor).toInt()
                    val spacingNeeded = if (currentSegments.isEmpty()) 0 else extraPerBlockPx

                    if (measuredHeight + spacingNeeded <= remainingHeight) {
                        currentSegments.add(
                            PageSegmentPointer(
                                paragraphIndex = paragraph.paragraphIndex,
                                startChar = startOffset,
                                endChar = rawText.length,
                                isContinuation = isContinuation
                            )
                        )
                        remainingHeight -= (measuredHeight + spacingNeeded)
                        startOffset = rawText.length
                    } else {
                        val availableForText = ((remainingHeight - spacingNeeded) / heightFactor).toInt()
                        var fittingLine = -1
                        for (lineIdx in 0 until layout.lineCount) {
                            if (layout.getLineBottom(lineIdx) <= availableForText) {
                                fittingLine = lineIdx
                            } else {
                                break
                            }
                        }

                        if (fittingLine < 0 && currentSegments.isNotEmpty()) {
                            // Não coube nenhuma linha: fecha esta página e tenta na próxima.
                            commitPage()
                            currentChapter = paragraph.chapterTitle
                            continue
                        }

                        // Garante avanço de ao menos 1 linha se a página estiver vazia (parágrafo gigante).
                        val chosenLine = fittingLine.coerceAtLeast(0)
                        val lineEndInSlice = layout.getLineEnd(chosenLine, visibleEnd = true)
                            .coerceIn(1, slice.length)

                        val safeSplit = findCleanBreak(slice, lineEndInSlice)
                        val absoluteEnd = (startOffset + safeSplit).coerceIn(startOffset + 1, rawText.length)

                        currentSegments.add(
                            PageSegmentPointer(
                                paragraphIndex = paragraph.paragraphIndex,
                                startChar = startOffset,
                                endChar = absoluteEnd,
                                isContinuation = isContinuation
                            )
                        )
                        commitPage()
                        currentChapter = paragraph.chapterTitle

                        startOffset = skipLeadingSpaces(rawText, absoluteEnd)
                        isContinuation = true
                    }
                }
            }

            cursor += chunk.size
        }

        commitPage()
        pages
    }

    private fun findCleanBreak(text: String, proposedEnd: Int): Int {
        if (proposedEnd >= text.length) return text.length
        if (text[proposedEnd].isWhitespace() || text[proposedEnd - 1].isWhitespace()) {
            return proposedEnd
        }
        val searchBackLimit = (proposedEnd - 28).coerceAtLeast(1)
        for (i in proposedEnd - 1 downTo searchBackLimit) {
            if (text[i].isWhitespace()) {
                return i
            }
        }
        return proposedEnd
    }

    private fun skipLeadingSpaces(text: String, fromIndex: Int): Int {
        var idx = fromIndex
        while (idx < text.length && text[idx].isWhitespace()) {
            idx++
        }
        return idx
    }
}
