package com.example.ui.reader

import androidx.compose.ui.text.TextMeasurer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.BookRepository
import com.example.data.local.BookEntity
import com.example.data.prefs.ReaderPreferencesRepository
import com.example.data.translation.MlKitTranslationManager
import com.example.data.translation.TranslationOutcome
import com.example.model.LoadedPage
import com.example.model.PagePointer
import com.example.model.PageTextBlock
import com.example.model.PageTranslationStatus
import com.example.model.ReaderFontOption
import com.example.model.ReaderPreferences
import com.example.model.ReadingTheme
import com.example.model.SupportedLanguage
import com.example.model.TranslationDisplayMode
import com.example.reader.KindlePaginator
import com.example.reader.PaginationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class ReaderUiState(
    val book: BookEntity? = null,
    val isPaginating: Boolean = true,
    val currentPageIndex: Int = 0,
    val totalPages: Int = 0,
    val currentPage: LoadedPage? = null,
    val nextPagePreTranslated: Boolean = false,
    val missingLanguages: List<SupportedLanguage> = emptyList(),
    val isDownloadingModels: Boolean = false,
    val activeRamPagesCount: Int = 0,
    val statusNotice: String? = null
) {
    val progressPercent: Int
        get() = if (totalPages <= 1) 100 else (((currentPageIndex + 1).toFloat() / totalPages) * 100f).toInt().coerceIn(0, 100)
}

/**
 * Gerencia leitura paginada com baixo consumo de RAM:
 * - Mantém em memória SOMENTE a janela [página - 1, página atual, página + 1].
 * - Traduz sob demanda apenas a página atual em Dispatchers.IO.
 * - Pré-traduz silenciosamente apenas a página + 1 em segundo plano e libera páginas distantes da RAM.
 */
class ReaderViewModel(
    private val bookId: Long,
    private val bookRepository: BookRepository,
    private val preferencesRepository: ReaderPreferencesRepository,
    private val translationManager: MlKitTranslationManager
) : ViewModel() {

    val preferences: StateFlow<ReaderPreferences> = preferencesRepository.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReaderPreferences()
    )

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var pagePointers: List<PagePointer> = emptyList()
    private val ramCacheMutex = Mutex()
    private val ramPageCache = LinkedHashMap<Int, LoadedPage>()

    private var paginationJob: Job? = null
    private var currentPageJob: Job? = null
    private var preTranslateJob: Job? = null

    private var lastPaginationSignature: String? = null
    private var anchorParagraphIndex: Int = 0
    private var anchorCharOffset: Int = 0

    init {
        viewModelScope.launch {
            val loadedBook = bookRepository.getBook(bookId)
            if (loadedBook != null) {
                anchorParagraphIndex = loadedBook.currentParagraphIndex
                anchorCharOffset = loadedBook.currentCharOffset
                _uiState.update { it.copy(book = loadedBook) }
            }
            translationManager.refreshDownloadedModels()
        }
    }

    fun requestPagination(
        textMeasurer: TextMeasurer,
        config: PaginationConfig,
        signature: String
    ) {
        val book = _uiState.value.book ?: return
        if (signature == lastPaginationSignature && pagePointers.isNotEmpty()) return
        lastPaginationSignature = signature

        paginationJob?.cancel()
        currentPageJob?.cancel()
        preTranslateJob?.cancel()

        paginationJob = viewModelScope.launch {
            _uiState.update { it.copy(isPaginating = true) }
            val pointers = KindlePaginator.paginateBook(
                bookId = book.id,
                totalParagraphs = book.totalParagraphs,
                repository = bookRepository,
                textMeasurer = textMeasurer,
                config = config
            )
            pagePointers = pointers
            ramCacheMutex.withLock { ramPageCache.clear() }

            val restoredIndex = findPageIndexForAnchor(pointers, anchorParagraphIndex, anchorCharOffset)
            _uiState.update {
                it.copy(
                    isPaginating = false,
                    totalPages = pointers.size,
                    currentPageIndex = restoredIndex,
                    activeRamPagesCount = 0
                )
            }
            if (pointers.isNotEmpty()) {
                loadPageAndPrefetchNext(restoredIndex)
            }
        }
    }

    fun nextPage() {
        val state = _uiState.value
        if (state.isPaginating || state.currentPageIndex >= state.totalPages - 1) return
        goToPage(state.currentPageIndex + 1)
    }

    fun previousPage() {
        val state = _uiState.value
        if (state.isPaginating || state.currentPageIndex <= 0) return
        goToPage(state.currentPageIndex - 1)
    }

    fun goToPage(targetPageIndex: Int) {
        if (pagePointers.isEmpty()) return
        val clamped = targetPageIndex.coerceIn(0, pagePointers.lastIndex)
        if (clamped == _uiState.value.currentPageIndex && _uiState.value.currentPage != null) return

        val pointer = pagePointers[clamped]
        pointer.segments.firstOrNull()?.let { firstSeg ->
            anchorParagraphIndex = firstSeg.paragraphIndex
            anchorCharOffset = firstSeg.startChar
        }

        _uiState.update { it.copy(currentPageIndex = clamped, nextPagePreTranslated = false) }
        loadPageAndPrefetchNext(clamped)
    }

    fun setDisplayMode(mode: TranslationDisplayMode) {
        viewModelScope.launch {
            preferencesRepository.setDisplayMode(mode)
            if (mode != TranslationDisplayMode.ORIGINAL_ONLY && pagePointers.isNotEmpty()) {
                loadPageAndPrefetchNext(_uiState.value.currentPageIndex, forceRetranslate = false)
            }
        }
    }

    fun cycleDisplayMode() {
        val next = preferences.value.displayMode.next()
        setDisplayMode(next)
    }

    fun setReadingTheme(theme: ReadingTheme) {
        viewModelScope.launch { preferencesRepository.setReadingTheme(theme) }
    }

    fun setFontSize(sizeSp: Float) {
        viewModelScope.launch { preferencesRepository.setFontSize(sizeSp) }
    }

    fun setLineSpacing(multiplier: Float) {
        viewModelScope.launch { preferencesRepository.setLineSpacing(multiplier) }
    }

    fun setFontOption(option: ReaderFontOption) {
        viewModelScope.launch { preferencesRepository.setFontOption(option) }
    }

    fun updateLanguagePair(source: SupportedLanguage, target: SupportedLanguage) {
        val currentBook = _uiState.value.book ?: return
        viewModelScope.launch {
            bookRepository.updateBookLanguages(currentBook.id, source, target)
            val updated = currentBook.copy(
                sourceLanguage = source.mlKitCode,
                targetLanguage = target.mlKitCode
            )
            _uiState.update { it.copy(book = updated, missingLanguages = emptyList()) }
            ramCacheMutex.withLock { ramPageCache.clear() }
            if (pagePointers.isNotEmpty()) {
                loadPageAndPrefetchNext(_uiState.value.currentPageIndex, forceRetranslate = true)
            }
        }
    }

    fun downloadMissingModelsNow() {
        val missing = _uiState.value.missingLanguages
        if (missing.isEmpty() || _uiState.value.isDownloadingModels) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloadingModels = true,
                    statusNotice = "Baixando pacote(s) offline do ML Kit..."
                )
            }
            val requireWifi = preferencesRepository.preferencesFlow.first().requireWifiForDownloads
            var allSucceeded = true
            for (lang in missing) {
                val res = translationManager.downloadLanguage(lang, requireWifi)
                if (res.isFailure) allSucceeded = false
            }
            _uiState.update {
                it.copy(
                    isDownloadingModels = false,
                    statusNotice = if (allSucceeded) {
                        "Pacotes baixados! Traduzindo página atual..."
                    } else {
                        "Falha ao baixar pacote. Verifique Wi-Fi ou permita dados móveis em Idiomas."
                    }
                )
            }
            if (allSucceeded && pagePointers.isNotEmpty()) {
                ramCacheMutex.withLock { ramPageCache.clear() }
                loadPageAndPrefetchNext(_uiState.value.currentPageIndex, forceRetranslate = true)
            }
        }
    }

    fun clearStatusNotice() {
        _uiState.update { it.copy(statusNotice = null) }
    }

    private fun loadPageAndPrefetchNext(pageIndex: Int, forceRetranslate: Boolean = false) {
        val book = _uiState.value.book ?: return
        if (pageIndex !in pagePointers.indices) return

        currentPageJob?.cancel()
        preTranslateJob?.cancel()

        currentPageJob = viewModelScope.launch(Dispatchers.IO) {
            // 1) Evicção ativa de RAM: mantém somente [pageIndex - 1, pageIndex, pageIndex + 1]
            evictDistantPages(pageIndex)

            // 2) Salva progresso real da leitura no Room
            val pointer = pagePointers[pageIndex]
            pointer.segments.firstOrNull()?.let { seg ->
                bookRepository.updateReadingProgress(book.id, seg.paragraphIndex, seg.startChar)
            }

            // 3) Carrega texto original instantaneamente (hit de RAM ou Room)
            val cached = if (!forceRetranslate) {
                ramCacheMutex.withLock { ramPageCache[pageIndex] }
            } else {
                null
            }

            val basePage = cached ?: buildBasePageFromRoom(book.id, pointer)
            val mode = preferencesRepository.preferencesFlow.first().displayMode

            if (mode == TranslationDisplayMode.ORIGINAL_ONLY) {
                storeInRamCache(pageIndex, basePage)
                _uiState.update {
                    it.copy(
                        currentPage = basePage,
                        missingLanguages = emptyList(),
                        activeRamPagesCount = ramPageCache.size
                    )
                }
                return@launch
            }

            // Se já estiver traduzida na janela de RAM (ex: veio da pré-tradução de página + 1!), exibição é 0ms
            if (basePage.translationStatus == PageTranslationStatus.READY && !forceRetranslate) {
                _uiState.update {
                    it.copy(
                        currentPage = basePage,
                        missingLanguages = emptyList(),
                        activeRamPagesCount = ramPageCache.size
                    )
                }
            } else {
                // Exibe original imediatamente enquanto traduz os parágrafos desta página
                val loadingPage = basePage.copy(translationStatus = PageTranslationStatus.TRANSLATING)
                _uiState.update {
                    it.copy(
                        currentPage = loadingPage,
                        activeRamPagesCount = ramPageCache.size
                    )
                }
                val translatedPage = translatePageBlocks(book, basePage)
                storeInRamCache(pageIndex, translatedPage)
                val missing = if (translatedPage.translationStatus == PageTranslationStatus.MODEL_MISSING) {
                    translationManager.getMissingLanguagesForPair(
                        SupportedLanguage.fromCode(book.sourceLanguage),
                        SupportedLanguage.fromCode(book.targetLanguage)
                    )
                } else {
                    emptyList()
                }
                _uiState.update {
                    it.copy(
                        currentPage = translatedPage,
                        missingLanguages = missing,
                        activeRamPagesCount = ramPageCache.size
                    )
                }
                if (translatedPage.translationStatus != PageTranslationStatus.READY) {
                    return@launch
                }
            }

            // 4) Pré-traduz silenciosamente APENAS a página seguinte (página + 1) em Dispatchers.IO
            val nextIndex = pageIndex + 1
            if (nextIndex in pagePointers.indices) {
                preTranslateJob = launch(Dispatchers.IO) {
                    val alreadyPrepared = ramCacheMutex.withLock {
                        ramPageCache[nextIndex]?.translationStatus == PageTranslationStatus.READY
                    }
                    if (!alreadyPrepared) {
                        val nextBase = buildBasePageFromRoom(book.id, pagePointers[nextIndex])
                        val nextTranslated = translatePageBlocks(book, nextBase)
                        if (nextTranslated.translationStatus == PageTranslationStatus.READY) {
                            storeInRamCache(nextIndex, nextTranslated)
                            _uiState.update {
                                it.copy(
                                    nextPagePreTranslated = true,
                                    activeRamPagesCount = ramPageCache.size
                                )
                            }
                        }
                    } else {
                        _uiState.update { it.copy(nextPagePreTranslated = true) }
                    }
                }
            }
        }
    }

    private suspend fun buildBasePageFromRoom(bookId: Long, pointer: PagePointer): LoadedPage {
        val indices = pointer.segments.map { it.paragraphIndex }.distinct()
        val entities = bookRepository.getParagraphsByIndices(bookId, indices)
            .associateBy { it.paragraphIndex }

        val blocks = pointer.segments.mapNotNull { seg ->
            val entity = entities[seg.paragraphIndex] ?: return@mapNotNull null
            val raw = entity.text.trim()
            if (raw.isEmpty()) return@mapNotNull null
            val safeStart = seg.startChar.coerceIn(0, raw.length)
            val safeEnd = seg.endChar.coerceIn(safeStart, raw.length)
            val text = raw.substring(safeStart, safeEnd).trim()
            if (text.isEmpty()) return@mapNotNull null
            PageTextBlock(
                paragraphIndex = seg.paragraphIndex,
                startChar = safeStart,
                endChar = safeEnd,
                originalText = text,
                isContinuation = seg.isContinuation
            )
        }
        return LoadedPage(
            pageIndex = pointer.pageIndex,
            chapterTitle = pointer.chapterTitle,
            blocks = blocks,
            translationStatus = PageTranslationStatus.IDLE
        )
    }

    private suspend fun translatePageBlocks(book: BookEntity, page: LoadedPage): LoadedPage =
        withContext(Dispatchers.IO) {
            val source = SupportedLanguage.fromCode(book.sourceLanguage)
            val target = SupportedLanguage.fromCode(book.targetLanguage)

            val updatedBlocks = ArrayList<PageTextBlock>(page.blocks.size)
            for (block in page.blocks) {
                when (
                    val outcome = translationManager.translateBlock(
                        bookId = book.id,
                        text = block.originalText,
                        source = source,
                        target = target
                    )
                ) {
                    is TranslationOutcome.Success -> {
                        updatedBlocks.add(block.copy(translatedText = outcome.translatedText))
                    }

                    is TranslationOutcome.ModelMissing -> {
                        return@withContext page.copy(
                            translationStatus = PageTranslationStatus.MODEL_MISSING,
                            errorMessage = "Baixe os pacotes offline necessários para traduzir."
                        )
                    }

                    is TranslationOutcome.Failure -> {
                        return@withContext page.copy(
                            translationStatus = PageTranslationStatus.ERROR,
                            errorMessage = outcome.reason
                        )
                    }
                }
            }
            page.copy(
                blocks = updatedBlocks,
                translationStatus = PageTranslationStatus.READY,
                errorMessage = null
            )
        }

    private suspend fun evictDistantPages(centerPageIndex: Int) {
        ramCacheMutex.withLock {
            val iterator = ramPageCache.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (abs(entry.key - centerPageIndex) > 1) {
                    iterator.remove()
                }
            }
        }
    }

    private suspend fun storeInRamCache(pageIndex: Int, page: LoadedPage) {
        ramCacheMutex.withLock {
            ramPageCache[pageIndex] = page
        }
    }

    private fun findPageIndexForAnchor(
        pointers: List<PagePointer>,
        paragraphIndex: Int,
        charOffset: Int
    ): Int {
        if (pointers.isEmpty()) return 0
        val match = pointers.indexOfFirst { page ->
            page.segments.any { seg ->
                seg.paragraphIndex == paragraphIndex && charOffset in seg.startChar..seg.endChar
            }
        }
        if (match >= 0) return match
        val fallback = pointers.indexOfFirst { page ->
            page.segments.any { it.paragraphIndex >= paragraphIndex }
        }
        return if (fallback >= 0) fallback else 0
    }

    override fun onCleared() {
        super.onCleared()
        paginationJob?.cancel()
        currentPageJob?.cancel()
        preTranslateJob?.cancel()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO).launch {
            translationManager.releaseTranslatorMemory()
        }
    }

    companion object {
        fun factory(
            bookId: Long,
            bookRepository: BookRepository,
            preferencesRepository: ReaderPreferencesRepository,
            translationManager: MlKitTranslationManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReaderViewModel(
                    bookId = bookId,
                    bookRepository = bookRepository,
                    preferencesRepository = preferencesRepository,
                    translationManager = translationManager
                ) as T
            }
        }
    }
}
