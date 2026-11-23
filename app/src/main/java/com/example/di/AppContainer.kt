package com.example.di

import android.content.Context
import com.example.data.BookRepository
import com.example.data.local.AppDatabase
import com.example.data.parser.BookImporter
import com.example.data.prefs.ReaderPreferencesRepository
import com.example.data.translation.MlKitTranslationManager

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase = AppDatabase.getInstance(appContext)
    val preferencesRepository: ReaderPreferencesRepository = ReaderPreferencesRepository(appContext)
    val bookImporter: BookImporter = BookImporter(appContext, database.bookDao())
    val bookRepository: BookRepository = BookRepository(
        bookDao = database.bookDao(),
        translationCacheDao = database.translationCacheDao(),
        bookImporter = bookImporter
    )
    val translationManager: MlKitTranslationManager = MlKitTranslationManager(
        context = appContext,
        cacheDao = database.translationCacheDao()
    )
}
