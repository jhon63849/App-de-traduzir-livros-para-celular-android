package com.example.data

import android.net.Uri
import com.example.data.local.BookDao
import com.example.data.local.BookEntity
import com.example.data.local.BookParagraphEntity
import com.example.data.local.CacheStats
import com.example.data.local.TranslationCacheDao
import com.example.data.parser.BookImporter
import com.example.data.parser.ImportResult
import com.example.model.SupportedLanguage
import kotlinx.coroutines.flow.Flow

class BookRepository(
    private val bookDao: BookDao,
    private val translationCacheDao: TranslationCacheDao,
    private val bookImporter: BookImporter
) {
    val allBooks: Flow<List<BookEntity>> = bookDao.observeAllBooks()
    val cacheStats: Flow<CacheStats> = translationCacheDao.observeCacheStats()

    fun observeBook(bookId: Long): Flow<BookEntity?> = bookDao.observeBookById(bookId)

    suspend fun getBook(bookId: Long): BookEntity? = bookDao.getBookById(bookId)

    suspend fun importBook(uri: Uri, defaultTargetLanguage: SupportedLanguage): ImportResult =
        bookImporter.importFromUri(uri, defaultTargetLanguage)

    suspend fun importSampleBook(defaultTargetLanguage: SupportedLanguage): ImportResult =
        bookImporter.importPublicDomainSample(defaultTargetLanguage)

    suspend fun deleteBook(bookId: Long) {
        translationCacheDao.clearBookCache(bookId)
        bookDao.deleteBookById(bookId)
    }

    suspend fun updateReadingProgress(bookId: Long, paragraphIndex: Int, charOffset: Int) {
        bookDao.updateReadingProgress(bookId, paragraphIndex, charOffset)
    }

    suspend fun updateBookLanguages(
        bookId: Long,
        source: SupportedLanguage,
        target: SupportedLanguage
    ) {
        bookDao.updateLanguages(bookId, source.mlKitCode, target.mlKitCode)
    }

    suspend fun getParagraphWindow(
        bookId: Long,
        startIndex: Int,
        endExclusive: Int
    ): List<BookParagraphEntity> = bookDao.getParagraphWindow(bookId, startIndex, endExclusive)

    suspend fun getParagraphsByIndices(
        bookId: Long,
        indices: List<Int>
    ): List<BookParagraphEntity> = bookDao.getParagraphsByIndices(bookId, indices)

    suspend fun clearAllTranslationCache() {
        translationCacheDao.clearAllCache()
    }
}
