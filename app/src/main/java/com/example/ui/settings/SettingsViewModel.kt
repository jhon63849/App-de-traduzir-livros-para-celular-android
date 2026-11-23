package com.example.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.BookRepository
import com.example.data.local.CacheStats
import com.example.data.prefs.ReaderPreferencesRepository
import com.example.model.ReaderFontOption
import com.example.model.ReaderPreferences
import com.example.model.ReadingTheme
import com.example.model.SupportedLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: ReaderPreferences = ReaderPreferences(),
    val cacheStats: CacheStats = CacheStats(0, 0L),
    val feedbackMessage: String? = null
)

class SettingsViewModel(
    private val bookRepository: BookRepository,
    private val preferencesRepository: ReaderPreferencesRepository
) : ViewModel() {

    private val feedbackMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.preferencesFlow,
        bookRepository.cacheStats,
        feedbackMessage
    ) { prefs, stats, msg ->
        SettingsUiState(
            preferences = prefs,
            cacheStats = stats,
            feedbackMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setReadingTheme(theme: ReadingTheme) {
        viewModelScope.launch { preferencesRepository.setReadingTheme(theme) }
    }

    fun setFontOption(option: ReaderFontOption) {
        viewModelScope.launch { preferencesRepository.setFontOption(option) }
    }

    fun setDefaultTargetLanguage(language: SupportedLanguage) {
        viewModelScope.launch { preferencesRepository.setDefaultTargetLanguage(language) }
    }

    fun setRequireWifi(requireWifi: Boolean) {
        viewModelScope.launch { preferencesRepository.setRequireWifiForDownloads(requireWifi) }
    }

    fun clearTranslationCache() {
        viewModelScope.launch {
            bookRepository.clearAllTranslationCache()
            feedbackMessage.value = "Cache local de traduções limpo com sucesso."
        }
    }

    fun clearFeedback() {
        feedbackMessage.value = null
    }

    companion object {
        fun factory(
            bookRepository: BookRepository,
            preferencesRepository: ReaderPreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(bookRepository, preferencesRepository) as T
            }
        }
    }
}
