package com.example.ui.languages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.prefs.ReaderPreferencesRepository
import com.example.data.translation.MlKitTranslationManager
import com.example.model.LanguagePackItem
import com.example.model.SupportedLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LanguageManagerUiState(
    val packs: List<LanguagePackItem> = emptyList(),
    val requireWifi: Boolean = true,
    val totalDownloadedMb: Double = 0.0,
    val downloadedCount: Int = 0,
    val feedbackMessage: String? = null
)

class LanguageManagerViewModel(
    private val translationManager: MlKitTranslationManager,
    private val preferencesRepository: ReaderPreferencesRepository
) : ViewModel() {

    private val feedbackMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LanguageManagerUiState> = combine(
        translationManager.languagePacks,
        preferencesRepository.preferencesFlow,
        feedbackMessage
    ) { packs, prefs, feedback ->
        val downloaded = packs.filter { it.isDownloaded }
        LanguageManagerUiState(
            packs = packs,
            requireWifi = prefs.requireWifiForDownloads,
            totalDownloadedMb = downloaded.sumOf { it.displaySizeMb },
            downloadedCount = downloaded.size,
            feedbackMessage = feedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LanguageManagerUiState()
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            translationManager.refreshDownloadedModels()
        }
    }

    fun setRequireWifi(requireWifi: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setRequireWifiForDownloads(requireWifi)
        }
    }

    fun downloadLanguage(language: SupportedLanguage) {
        viewModelScope.launch {
            val requireWifi = uiState.value.requireWifi
            val result = translationManager.downloadLanguage(language, requireWifi)
            feedbackMessage.value = if (result.isSuccess) {
                "Pacote ${language.displayNamePt} pronto para uso 100% offline."
            } else {
                "Não foi possível baixar ${language.displayNamePt}. Verifique a rede."
            }
        }
    }

    fun deleteLanguage(language: SupportedLanguage) {
        viewModelScope.launch {
            val result = translationManager.deleteLanguage(language)
            if (result.isSuccess) {
                feedbackMessage.value =
                    "Pacote ${language.displayNamePt} removido (~${"%.1f".format(language.estimatedSizeMb)} MB liberados)."
            }
        }
    }

    fun clearFeedback() {
        feedbackMessage.value = null
    }

    companion object {
        fun factory(
            translationManager: MlKitTranslationManager,
            preferencesRepository: ReaderPreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LanguageManagerViewModel(translationManager, preferencesRepository) as T
            }
        }
    }
}
