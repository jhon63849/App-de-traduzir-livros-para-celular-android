package com.example.data.parser

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Html
import com.example.data.local.BookDao
import com.example.data.local.BookEntity
import com.example.data.local.BookParagraphEntity
import com.example.model.BookFormat
import com.example.model.SupportedLanguage
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipFile
import kotlin.math.abs

sealed interface ImportResult {
    data class Success(val bookId: Long, val title: String) : ImportResult
    data class Failure(val message: String) : ImportResult
}

/**
 * Importador de livros EPUB e PDF otimizado para pouca RAM:
 * extrai capítulos/páginas em fluxo contínuo e grava parágrafos em lotes de 80 no SQLite (Room),
 * evitando carregar o arquivo completo na memória.
 */
class BookImporter(
    private val context: Context,
    private val bookDao: BookDao
) {
    companion object {
        private const val BATCH_SIZE = 80
        private val BLOCK_SPLIT_REGEX = Regex(
            pattern = "</(?:p|div|h[1-6]|li|blockquote|section|article|tr)>|<br\\s*/?>",
            option = RegexOption.IGNORE_CASE
        )
        private val SCRIPT_STYLE_REGEX = Regex(
            pattern = "<(script|style|head|nav)[^>]*>[\\s\\S]*?</\\1>",
            option = RegexOption.IGNORE_CASE
        )
        private val HTML_TAG_REGEX = Regex("<[^>]+>")
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val TITLE_TAG_REGEX = Regex("<h[1-3][^>]*>(.*?)</h[1-3]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    }

    suspend fun importFromUri(
        uri: Uri,
        defaultTargetLanguage: SupportedLanguage
    ): ImportResult = withContext(Dispatchers.IO) {
        val meta = queryFileMeta(uri)
        val fileName = meta.first
        val fileSize = meta.second
        val mime = context.contentResolver.getType(uri).orEmpty()

        val format = when {
            fileName.endsWith(".pdf", ignoreCase = true) || mime.contains("pdf", ignoreCase = true) -> BookFormat.PDF
            fileName.endsWith(".epub", ignoreCase = true) || mime.contains("epub", ignoreCase = true) -> BookFormat.EPUB
            else -> return@withContext ImportResult.Failure(
                "Formato não suportado. Selecione um arquivo .epub ou .pdf."
            )
        }

        val tempFile = File.createTempFile("import_", ".${format.name.lowercase()}", context.cacheDir)
        try {
            val copied = copyUriToFile(uri, tempFile)
            if (!copied || tempFile.length() == 0L) {
                return@withContext ImportResult.Failure("Não foi possível ler o arquivo selecionado.")
            }

            when (format) {
                BookFormat.EPUB -> importEpub(
                    tempFile = tempFile,
                    sourceUri = uri.toString(),
                    fallbackTitle = stripExtension(fileName),
                    fileSize = if (fileSize > 0) fileSize else tempFile.length(),
                    defaultTarget = defaultTargetLanguage
                )

                BookFormat.PDF -> importPdf(
                    tempFile = tempFile,
                    sourceUri = uri.toString(),
                    fallbackTitle = stripExtension(fileName),
                    fileSize = if (fileSize > 0) fileSize else tempFile.length(),
                    defaultTarget = defaultTargetLanguage
                )
            }
        } catch (e: OutOfMemoryError) {
            ImportResult.Failure("Arquivo muito grande para a memória disponível.")
        } catch (e: Exception) {
            ImportResult.Failure("Erro ao importar livro: ${e.localizedMessage ?: "arquivo corrompido"}")
        } finally {
            tempFile.delete()
        }
    }

    private suspend fun importEpub(
        tempFile: File,
        sourceUri: String,
        fallbackTitle: String,
        fileSize: Long,
        defaultTarget: SupportedLanguage
    ): ImportResult {
        ZipFile(tempFile).use { zip ->
            val containerEntry = zip.getEntry("META-INF/container.xml")
                ?: return ImportResult.Failure("EPUB inválido: arquivo container.xml ausente.")

            val opfPath = zip.getInputStream(containerEntry).use { parseContainerOpfPath(it) }
                ?: return ImportResult.Failure("EPUB inválido: pacote OPF não encontrado.")

            val opfEntry = zip.getEntry(opfPath)
                ?: return ImportResult.Failure("EPUB inválido: manifesto OPF inacessível.")

            val opfDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
            val opfData = zip.getInputStream(opfEntry).use { parseOpf(it) }

            val title = opfData.title.ifBlank { fallbackTitle }
            val author = opfData.author.ifBlank { "Autor desconhecido" }
            val sourceLang = detectSupportedLanguage(opfData.language)
            val targetLang = if (sourceLang == defaultTarget) {
                if (sourceLang == SupportedLanguage.PORTUGUESE) SupportedLanguage.ENGLISH else SupportedLanguage.PORTUGUESE
            } else {
                defaultTarget
            }

            val bookId = bookDao.insertBook(
                BookEntity(
                    title = title,
                    author = author,
                    format = BookFormat.EPUB.name,
                    sourceUri = sourceUri,
                    sourceLanguage = sourceLang.mlKitCode,
                    targetLanguage = targetLang.mlKitCode,
                    totalParagraphs = 0,
                    fileSizeBytes = fileSize,
                    coverColorIndex = abs(title.hashCode()) % 6
                )
            )

            var paragraphIndex = 0
            val batch = ArrayList<BookParagraphEntity>(BATCH_SIZE)

            val chapterPaths = opfData.spineHrefs.ifEmpty {
                zip.entries().asSequence()
                    .map { it.name }
                    .filter { it.endsWith(".xhtml", true) || it.endsWith(".html", true) || it.endsWith(".htm", true) }
                    .sorted()
                    .toList()
            }

            chapterPaths.forEachIndexed { idx, href ->
                val resolvedPath = resolveZipPath(opfDir, href)
                val chapterEntry = zip.getEntry(resolvedPath) ?: zip.getEntry(href) ?: return@forEachIndexed
                val rawHtml = zip.getInputStream(chapterEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }

                val chapterHeading = extractChapterHeading(rawHtml) ?: "Capítulo ${idx + 1}"
                val paragraphs = extractParagraphsFromHtml(rawHtml)

                for (p in paragraphs) {
                    batch.add(
                        BookParagraphEntity(
                            bookId = bookId,
                            paragraphIndex = paragraphIndex++,
                            chapterTitle = chapterHeading,
                            text = p
                        )
                    )
                    if (batch.size >= BATCH_SIZE) {
                        bookDao.insertParagraphs(batch)
                        batch.clear()
                    }
                }
            }

            if (batch.isNotEmpty()) {
                bookDao.insertParagraphs(batch)
                batch.clear()
            }

            if (paragraphIndex == 0) {
                bookDao.deleteBookById(bookId)
                return ImportResult.Failure("Nenhum texto legível foi encontrado neste EPUB.")
            }

            bookDao.updateTotalParagraphs(bookId, paragraphIndex)
            return ImportResult.Success(bookId, title)
        }
    }

    private suspend fun importPdf(
        tempFile: File,
        sourceUri: String,
        fallbackTitle: String,
        fileSize: Long,
        defaultTarget: SupportedLanguage
    ): ImportResult {
        PDFBoxResourceLoader.init(context.applicationContext)

        PDDocument.load(tempFile, MemoryUsageSetting.setupTempFileOnly()).use { document ->
            val totalPages = document.numberOfPages
            if (totalPages <= 0) {
                return ImportResult.Failure("PDF vazio ou protegido.")
            }

            val info = document.documentInformation
            val title = info?.title?.takeIf { it.isNotBlank() } ?: fallbackTitle
            val author = info?.author?.takeIf { it.isNotBlank() } ?: "Documento PDF"
            val sourceLang = SupportedLanguage.ENGLISH
            val targetLang = if (sourceLang == defaultTarget) SupportedLanguage.PORTUGUESE else defaultTarget

            val bookId = bookDao.insertBook(
                BookEntity(
                    title = title,
                    author = author,
                    format = BookFormat.PDF.name,
                    sourceUri = sourceUri,
                    sourceLanguage = sourceLang.mlKitCode,
                    targetLanguage = targetLang.mlKitCode,
                    totalParagraphs = 0,
                    fileSizeBytes = fileSize,
                    coverColorIndex = abs(title.hashCode()) % 6
                )
            )

            val stripper = PDFTextStripper().apply {
                sortByPosition = true
            }

            var paragraphIndex = 0
            val batch = ArrayList<BookParagraphEntity>(BATCH_SIZE)

            for (pageNum in 1..totalPages) {
                stripper.startPage = pageNum
                stripper.endPage = pageNum
                val pageText = stripper.getText(document) ?: continue
                val pageParagraphs = extractParagraphsFromPdfPage(pageText)
                val chapterLabel = "PDF • Pág. $pageNum/$totalPages"

                for (p in pageParagraphs) {
                    batch.add(
                        BookParagraphEntity(
                            bookId = bookId,
                            paragraphIndex = paragraphIndex++,
                            chapterTitle = chapterLabel,
                            text = p
                        )
                    )
                    if (batch.size >= BATCH_SIZE) {
                        bookDao.insertParagraphs(batch)
                        batch.clear()
                    }
                }
            }

            if (batch.isNotEmpty()) {
                bookDao.insertParagraphs(batch)
                batch.clear()
            }

            if (paragraphIndex == 0) {
                bookDao.deleteBookById(bookId)
                return ImportResult.Failure(
                    "Este PDF não possui camada de texto selecionável (pode ser apenas imagem escaneada)."
                )
            }

            bookDao.updateTotalParagraphs(bookId, paragraphIndex)
            return ImportResult.Success(bookId, title)
        }
    }

    /**
     * Carrega um clássico em domínio público (Inglês) quando o usuário toca no botão de demonstração,
     * permitindo testar a paginação estilo Kindle e a tradução offline do ML Kit imediatamente.
     */
    suspend fun importPublicDomainSample(defaultTarget: SupportedLanguage): ImportResult =
        withContext(Dispatchers.IO) {
            val title = "Alice's Adventures in Wonderland"
            val author = "Lewis Carroll (Domínio Público)"
            val targetLang = if (defaultTarget == SupportedLanguage.ENGLISH) {
                SupportedLanguage.PORTUGUESE
            } else {
                defaultTarget
            }

            val sampleChapters = listOf(
                "Chapter I • Down the Rabbit-Hole" to listOf(
                    "Alice was beginning to get very tired of sitting by her sister on the bank, and of having nothing to do: once or twice she had peeped into the book her sister was reading, but it had no pictures or conversations in it, \"and what is the use of a book,\" thought Alice, \"without pictures or conversations?\"",
                    "So she was considering in her own mind, as well as she could, for the hot day made her feel very sleepy and stupid, whether the pleasure of making a daisy-chain would be worth the trouble of getting up and picking the daisies, when suddenly a White Rabbit with pink eyes ran close by her.",
                    "There was nothing so very remarkable in that; nor did Alice think it so very much out of the way to hear the Rabbit say to itself, \"Oh dear! Oh dear! I shall be late!\" But when the Rabbit actually took a watch out of its waistcoat-pocket, and looked at it, and then hurried on, Alice started to her feet.",
                    "Burning with curiosity, she ran across the field after it, and fortunately was just in time to see it pop down a large rabbit-hole under the hedge. In another moment down went Alice after it, never once considering how in the world she was to get out again.",
                    "The rabbit-hole went straight on like a tunnel for some way, and then dipped suddenly down, so suddenly that Alice had not a moment to think about stopping herself before she found herself falling down a very deep well.",
                    "Either the well was very deep, or she fell very slowly, for she had plenty of time as she went down to look about her and to wonder what was going to happen next. First, she tried to look down and make out what she was coming to, but it was too dark to see anything.",
                    "Then she looked at the sides of the well, and noticed that they were filled with cupboards and book-shelves; here and there she saw maps and pictures hung upon pegs. She took down a jar from one of the shelves as she passed; it was labelled \"ORANGE MARMALADE\", but to her great disappointment it was empty.",
                    "Down, down, down. Would the fall never come to an end? \"I wonder how many miles I've fallen by this time?\" she said aloud. \"I must be getting somewhere near the centre of the earth. Let me see: that would be four thousand miles down, I think.\"",
                    "Suddenly, thump! thump! Down she came upon a heap of sticks and dry leaves, and the fall was over. Alice was not a bit hurt, and she jumped up on to her feet in a moment. Before her was another long passage, and the White Rabbit was still in sight, hurrying down it."
                ),
                "Chapter II • The Pool of Tears" to listOf(
                    "\"Curiouser and curiouser!\" cried Alice. She was so much surprised, that for the moment she quite forgot how to speak good English. \"Now I'm opening out like the largest telescope that ever was! Good-bye, feet!\"",
                    "Just then her head struck against the roof of the hall: in fact she was now more than nine feet high, and she at once took up the little golden key and hurried off to the garden door.",
                    "Poor Alice! It was as much as she could do, lying down on one side, to look through into the garden with one eye; but to get through was more hopeless than ever: she sat down and began to cry again.",
                    "\"You ought to be ashamed of yourself,\" said Alice, \"a great girl like you, to go on crying in this way! Stop this moment, I tell you!\" But she went on all the same, shedding gallons of tears, until there was a large pool all round her, about four inches deep and reaching half down the hall.",
                    "After a time she heard a little pattering of feet in the distance, and she hastily dried her eyes to see what was coming. It was the White Rabbit returning, splendidly dressed, with a pair of white kid gloves in one hand and a large fan in the other."
                )
            )

            val bookId = bookDao.insertBook(
                BookEntity(
                    title = title,
                    author = author,
                    format = BookFormat.EPUB.name,
                    sourceUri = "sample://alice_wonderland",
                    sourceLanguage = SupportedLanguage.ENGLISH.mlKitCode,
                    targetLanguage = targetLang.mlKitCode,
                    totalParagraphs = 0,
                    fileSizeBytes = 18_400L,
                    coverColorIndex = 0
                )
            )

            var idx = 0
            val entities = buildList {
                sampleChapters.forEach { (chapter, paragraphs) ->
                    paragraphs.forEach { text ->
                        add(
                            BookParagraphEntity(
                                bookId = bookId,
                                paragraphIndex = idx++,
                                chapterTitle = chapter,
                                text = text
                            )
                        )
                    }
                }
            }
            bookDao.insertParagraphs(entities)
            bookDao.updateTotalParagraphs(bookId, idx)
            ImportResult.Success(bookId, title)
        }

    private fun copyUriToFile(uri: Uri, destination: File): Boolean {
        val input = context.contentResolver.openInputStream(uri) ?: return false
        input.use { stream ->
            FileOutputStream(destination).use { out ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                }
                out.flush()
            }
        }
        return true
    }

    private fun queryFileMeta(uri: Uri): Pair<String, Long> {
        var name = "Livro"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex) ?: name
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
        return name to size
    }

    private fun stripExtension(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) fileName.substring(0, dot) else fileName
    }

    private fun parseContainerOpfPath(input: InputStream): String? {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newPullParser()
        parser.setInput(input, "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name.equals("rootfile", ignoreCase = true)) {
                val fullPath = parser.getAttributeValue(null, "full-path")
                if (!fullPath.isNullOrBlank()) return fullPath
            }
            event = parser.next()
        }
        return null
    }

    private data class OpfMetadata(
        val title: String,
        val author: String,
        val language: String,
        val spineHrefs: List<String>
    )

    private fun parseOpf(input: InputStream): OpfMetadata {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newPullParser()
        parser.setInput(input, "UTF-8")

        var title = ""
        var author = ""
        var language = ""
        val manifest = LinkedHashMap<String, String>()
        val spineIds = ArrayList<String>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.lowercase()) {
                    "title" -> if (title.isBlank()) title = parser.nextText().trim()
                    "creator" -> if (author.isBlank()) author = parser.nextText().trim()
                    "language" -> if (language.isBlank()) language = parser.nextText().trim()
                    "item" -> {
                        val id = parser.getAttributeValue(null, "id")
                        val href = parser.getAttributeValue(null, "href")
                        val mediaType = parser.getAttributeValue(null, "media-type").orEmpty()
                        if (!id.isNullOrBlank() && !href.isNullOrBlank()) {
                            if (mediaType.contains("html", ignoreCase = true) ||
                                href.endsWith(".xhtml", true) ||
                                href.endsWith(".html", true) ||
                                href.endsWith(".htm", true)
                            ) {
                                manifest[id] = Uri.decode(href)
                            }
                        }
                    }

                    "itemref" -> {
                        val idRef = parser.getAttributeValue(null, "idref")
                        if (!idRef.isNullOrBlank()) spineIds.add(idRef)
                    }
                }
            }
            event = parser.next()
        }

        val orderedHrefs = spineIds.mapNotNull { manifest[it] }
        return OpfMetadata(
            title = title,
            author = author,
            language = language,
            spineHrefs = orderedHrefs
        )
    }

    private fun resolveZipPath(baseDir: String, href: String): String {
        val cleanHref = href.substringBefore('#').trimStart('/')
        return if (baseDir.isBlank()) cleanHref else "$baseDir/$cleanHref"
    }

    private fun extractChapterHeading(rawHtml: String): String? {
        val match = TITLE_TAG_REGEX.find(rawHtml) ?: return null
        val cleaned = decodeHtml(match.groupValues[1].replace(HTML_TAG_REGEX, " "))
            .replace(WHITESPACE_REGEX, " ")
            .trim()
        return cleaned.takeIf { it.length in 2..90 }
    }

    private fun extractParagraphsFromHtml(rawHtml: String): List<String> {
        val withoutNonBody = rawHtml.replace(SCRIPT_STYLE_REGEX, " ")
        val blocks = BLOCK_SPLIT_REGEX.split(withoutNonBody)
        val result = ArrayList<String>()
        for (block in blocks) {
            val stripped = block.replace(HTML_TAG_REGEX, " ")
            val decoded = decodeHtml(stripped).replace(WHITESPACE_REGEX, " ").trim()
            if (decoded.length >= 2) {
                result.add(decoded)
            }
        }
        return result
    }

    private fun extractParagraphsFromPdfPage(pageText: String): List<String> {
        val normalized = pageText.replace("\r\n", "\n").replace("\r", "\n")
        val lines = normalized.split("\n")
        val paragraphs = ArrayList<String>()
        val current = StringBuilder()

        fun flush() {
            val text = current.toString().replace(WHITESPACE_REGEX, " ").trim()
            if (text.length >= 2) {
                paragraphs.add(text)
            }
            current.setLength(0)
        }

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                flush()
                continue
            }
            if (current.isEmpty()) {
                current.append(line)
            } else {
                val prevEndsHyphen = current.endsWith("-") && line.firstOrNull()?.isLowerCase() == true
                if (prevEndsHyphen) {
                    current.deleteCharAt(current.length - 1)
                    current.append(line)
                } else {
                    current.append(' ').append(line)
                }
            }
            // Quebra parágrafos muito extensos de PDF em blocos confortáveis para paginação
            if (current.length > 650 && (line.endsWith(".") || line.endsWith("!") || line.endsWith("?"))) {
                flush()
            }
        }
        flush()
        return paragraphs
    }

    private fun decodeHtml(input: String): String {
        return Html.fromHtml(input, Html.FROM_HTML_MODE_LEGACY).toString()
    }

    private fun detectSupportedLanguage(rawLang: String): SupportedLanguage {
        val prefix = rawLang.trim().lowercase().substringBefore('-').substringBefore('_')
        return SupportedLanguage.entries.firstOrNull { it.mlKitCode == prefix }
            ?: SupportedLanguage.ENGLISH
    }
}
