package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ReaderFontOption
import com.example.model.ReaderPreferences
import com.example.model.ReadingTheme
import com.example.model.SupportedLanguage
import com.example.model.TranslationDisplayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "kindle_translate_prefs"
)

class ReaderPreferencesRepository(private val context: Context) {

    private object Keys {
        val READING_THEME = stringPreferencesKey("reading_theme")
        val FONT_SIZE_SP = floatPreferencesKey("font_size_sp")
        val LINE_SPACING = floatPreferencesKey("line_spacing")
        val FONT_OPTION = stringPreferencesKey("font_option")
        val DISPLAY_MODE = stringPreferencesKey("display_mode")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only_downloads")
        val DEFAULT_TARGET_LANG = stringPreferencesKey("default_target_lang")
    }

    val preferencesFlow: Flow<ReaderPreferences> = context.readerDataStore.data.map { prefs ->
        ReaderPreferences(
            readingTheme = prefs[Keys.READING_THEME]?.let { name ->
                ReadingTheme.entries.firstOrNull { it.name == name }
            } ?: ReadingTheme.PAPER,
            fontSizeSp = (prefs[Keys.FONT_SIZE_SP] ?: 19f).coerceIn(14f, 28f),
            lineSpacingMultiplier = (prefs[Keys.LINE_SPACING] ?: 1.5f).coerceIn(1.2f, 2.0f),
            fontOption = prefs[Keys.FONT_OPTION]?.let { name ->
                ReaderFontOption.entries.firstOrNull { it.name == name }
            } ?: ReaderFontOption.SERIF_LITERATA,
            displayMode = prefs[Keys.DISPLAY_MODE]?.let { name ->
                TranslationDisplayMode.entries.firstOrNull { it.name == name }
            } ?: TranslationDisplayMode.BILINGUAL,
            requireWifiForDownloads = prefs[Keys.WIFI_ONLY] ?: true,
            defaultTargetLanguage = prefs[Keys.DEFAULT_TARGET_LANG]?.let {
                SupportedLanguage.fromCode(it)
            } ?: SupportedLanguage.PORTUGUESE
        )
    }

    suspend fun setReadingTheme(theme: ReadingTheme) {
        context.readerDataStore.edit { it[Keys.READING_THEME] = theme.name }
    }

    suspend fun setFontSize(sizeSp: Float) {
        context.readerDataStore.edit { it[Keys.FONT_SIZE_SP] = sizeSp.coerceIn(14f, 28f) }
    }

    suspend fun setLineSpacing(multiplier: Float) {
        context.readerDataStore.edit { it[Keys.LINE_SPACING] = multiplier.coerceIn(1.2f, 2.0f) }
    }

    suspend fun setFontOption(option: ReaderFontOption) {
        context.readerDataStore.edit { it[Keys.FONT_OPTION] = option.name }
    }

    suspend fun setDisplayMode(mode: TranslationDisplayMode) {
        context.readerDataStore.edit { it[Keys.DISPLAY_MODE] = mode.name }
    }

    suspend fun setRequireWifiForDownloads(requireWifi: Boolean) {
        context.readerDataStore.edit { it[Keys.WIFI_ONLY] = requireWifi }
    }

    suspend fun setDefaultTargetLanguage(language: SupportedLanguage) {
        context.readerDataStore.edit { it[Keys.DEFAULT_TARGET_LANG] = language.mlKitCode }
    }
}
