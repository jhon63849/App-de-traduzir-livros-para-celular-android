package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class CacheStats(
    val entryCount: Int,
    val totalBytes: Long
)

@Dao
interface TranslationCacheDao {
    @Query("SELECT * FROM translation_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getByKey(cacheKey: String): TranslationCacheEntity?

    @Query("SELECT * FROM translation_cache WHERE cacheKey IN (:keys)")
    suspend fun getByKeys(keys: List<String>): List<TranslationCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<TranslationCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TranslationCacheEntity)

    @Query("SELECT COUNT(*) AS entryCount, COALESCE(SUM(byteSize), 0) AS totalBytes FROM translation_cache")
    fun observeCacheStats(): Flow<CacheStats>

    @Query("DELETE FROM translation_cache WHERE bookId = :bookId")
    suspend fun clearBookCache(bookId: Long)

    @Query("DELETE FROM translation_cache")
    suspend fun clearAllCache()
}
