package com.example.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.BookRepository
import com.example.data.local.BookEntity
import com.example.data.parser.ImportResult
import com.example.data.prefs.ReaderPreferencesRepository
import com.example.model.SupportedLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val books: List<BookEntity> = emptyList(),
    val isImporting: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val lastImportedBookId: Long? = null
)

class LibraryViewModel(
    private val bookRepository: BookRepository,
    private val preferencesRepository: ReaderPreferencesRepository
) : ViewModel() {

    private val isImporting = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val lastImportedBookId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<LibraryUiState> = combine(
        bookRepository.allBooks,
        isImporting,
        statusMessage,
        errorMessage,
        lastImportedBookId
    ) { books, importing, status, error, importedId ->
        LibraryUiState(
            books = books,
            isImporting = importing,
            statusMessage = status,
            errorMessage = error,
            lastImportedBookId = importedId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    fun importDocument(uri: Uri) {
        if (isImporting.value) return
        viewModelScope.launch {
            isImporting.value = true
            errorMessage.value = null
            statusMessage.value = "Extraindo texto em baixo consumo de RAM..."
            val targetLang = preferencesRepository.preferencesFlow.first().defaultTargetLanguage
            when (val result = bookRepository.importBook(uri, targetLang)) {
                is ImportResult.Success -> {
                    statusMessage.value = "\"${result.title}\" adicionado à biblioteca."
                    lastImportedBookId.value = result.bookId
                }

                is ImportResult.Failure -> {
                    statusMessage.value = null
                    errorMessage.value = result.message
                }
            }
            isImporting.value = false
        }
    }

    fun loadSampleClassic() {
        if (isImporting.value) return
        viewModelScope.launch {
            isImporting.value = true
            errorMessage.value = null
            val targetLang: SupportedLanguage = preferencesRepository.preferencesFlow.first().defaultTargetLanguage
            when (val result = bookRepository.importSampleBook(targetLang)) {
                is ImportResult.Success -> {
                    statusMessage.value = "Clássico \"${result.title}\" pronto para leitura!"
                    lastImportedBookId.value = result.bookId
                }

                is ImportResult.Failure -> {
                    errorMessage.value = result.message
                }
            }
            isImporting.value = false
        }
    }

    fun deleteBook(bookId: Long) {
        viewModelScope.launch {
            bookRepository.deleteBook(bookId)
            statusMessage.value = "Livro e cache local removidos."
        }
    }

    fun clearMessages() {
        statusMessage.value = null
        errorMessage.value = null
    }

    fun consumeImportedBookNavigation() {
        lastImportedBookId.value = null
    }

    companion object {
        fun factory(
            bookRepository: BookRepository,
            preferencesRepository: ReaderPreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LibraryViewModel(bookRepository, preferencesRepository) as T
            }
        }
    }
}
