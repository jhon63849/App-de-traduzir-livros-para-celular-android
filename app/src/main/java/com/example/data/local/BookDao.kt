package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadTimestamp DESC")
    fun observeAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getBookById(bookId: Long): BookEntity?

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    fun observeBookById(bookId: Long): Flow<BookEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Query("UPDATE books SET totalParagraphs = :totalParagraphs WHERE id = :bookId")
    suspend fun updateTotalParagraphs(bookId: Long, totalParagraphs: Int)

    @Query(
        """
        UPDATE books 
        SET currentParagraphIndex = :paragraphIndex, 
            currentCharOffset = :charOffset, 
            lastReadTimestamp = :timestamp 
        WHERE id = :bookId
        """
    )
    suspend fun updateReadingProgress(
        bookId: Long,
        paragraphIndex: Int,
        charOffset: Int,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE books 
        SET sourceLanguage = :sourceLang, targetLanguage = :targetLang 
        WHERE id = :bookId
        """
    )
    suspend fun updateLanguages(bookId: Long, sourceLang: String, targetLang: String)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBookById(bookId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParagraphs(paragraphs: List<BookParagraphEntity>)

    /**
     * Carrega apenas uma janela pequena de parágrafos para evitar manter o livro inteiro na RAM.
     */
    @Query(
        """
        SELECT * FROM book_paragraphs 
        WHERE bookId = :bookId AND paragraphIndex >= :startIndex AND paragraphIndex < :endExclusive 
        ORDER BY paragraphIndex ASC
        """
    )
    suspend fun getParagraphWindow(
        bookId: Long,
        startIndex: Int,
        endExclusive: Int
    ): List<BookParagraphEntity>

    @Query(
        """
        SELECT * FROM book_paragraphs 
        WHERE bookId = :bookId AND paragraphIndex IN (:indices) 
        ORDER BY paragraphIndex ASC
        """
    )
    suspend fun getParagraphsByIndices(
        bookId: Long,
        indices: List<Int>
    ): List<BookParagraphEntity>
}
