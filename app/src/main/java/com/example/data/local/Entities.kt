package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val author: String,
    val format: String,
    val sourceUri: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val totalParagraphs: Int,
    val currentParagraphIndex: Int = 0,
    val currentCharOffset: Int = 0,
    val fileSizeBytes: Long = 0L,
    val coverColorIndex: Int = 0,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val lastReadTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "book_paragraphs",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bookId", "paragraphIndex"], unique = true)
    ]
)
data class BookParagraphEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val bookId: Long,
    val paragraphIndex: Int,
    val chapterTitle: String,
    val text: String
)

@Entity(
    tableName = "translation_cache",
    indices = [
        Index(value = ["bookId"])
    ]
)
data class TranslationCacheEntity(
    @PrimaryKey val cacheKey: String,
    val bookId: Long,
    val sourceLanguage: String,
    val targetLanguage: String,
    val translatedText: String,
    val byteSize: Long,
    val timestamp: Long = System.currentTimeMillis()
)
