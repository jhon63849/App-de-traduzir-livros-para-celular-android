package com.example.data.translation

import android.content.Context
import com.example.data.local.TranslationCacheDao
import com.example.data.local.TranslationCacheEntity
import com.example.model.LanguageModelStatus
import com.example.model.LanguagePackItem
import com.example.model.SupportedLanguage
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

sealed interface TranslationOutcome {
    data class Success(val translatedText: String, val fromCache: Boolean) : TranslationOutcome
    data class ModelMissing(val missingLanguages: List<SupportedLanguage>) : TranslationOutcome
    data class Failure(val reason: String) : TranslationOutcome
}

/**
 * Motor exclusivo Google ML Kit On-Device Translation (~30 MB por idioma).
 * Mantém no máximo 1 instância ativa de Translator na RAM por vez e utiliza cache SQLite (Room).
 */
class MlKitTranslationManager(
    private val context: Context,
    private val cacheDao: TranslationCacheDao
) {
    private val modelManager: RemoteModelManager = RemoteModelManager.getInstance()
    private val translatorMutex = Mutex()

    private var activePair: Pair<String, String>? = null
    private var activeTranslator: Translator? = null

    private val _languagePacks = MutableStateFlow(
        SupportedLanguage.entries.map {
            LanguagePackItem(language = it, status = LanguageModelStatus.NotDownloaded)
        }
    )
    val languagePacks: StateFlow<List<LanguagePackItem>> = _languagePacks.asStateFlow()

    suspend fun refreshDownloadedModels() = withContext(Dispatchers.IO) {
        try {
            val downloaded = modelManager
                .getDownloadedModels(TranslateRemoteModel::class.java)
                .await()
            val downloadedCodes = downloaded.map { it.language }.toSet()
            val diskSizesMb = measureModelSizesOnDisk(downloadedCodes)

            _languagePacks.update { current ->
                current.map { item ->
                    val code = item.language.mlKitCode
                    if (item.status is LanguageModelStatus.Downloading) {
                        item
                    } else if (downloadedCodes.contains(code)) {
                        val measured = diskSizesMb[code]
                        val sizeMb = measured ?: item.language.estimatedSizeMb
                        item.copy(
                            status = LanguageModelStatus.Downloaded(
                                sizeMb = sizeMb,
                                measuredOnDisk = measured != null
                            )
                        )
                    } else {
                        item.copy(status = LanguageModelStatus.NotDownloaded)
                    }
                }
            }
        } catch (_: Exception) {
            // Mantém o estado anterior caso o Play Services / ML Kit ainda esteja inicializando.
        }
    }

    suspend fun downloadLanguage(
        language: SupportedLanguage,
        requireWifi: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        setLanguageStatus(language, LanguageModelStatus.Downloading)
        try {
            val model = TranslateRemoteModel.Builder(language.mlKitCode).build()
            val conditionsBuilder = DownloadConditions.Builder()
            if (requireWifi) {
                conditionsBuilder.requireWifi()
            }
            modelManager.download(model, conditionsBuilder.build()).await()
            refreshDownloadedModels()
            Result.success(Unit)
        } catch (e: Exception) {
            val msg = if (requireWifi) {
                "Falha no download (verifique o Wi-Fi ou desative 'Exigir Wi-Fi')."
            } else {
                "Falha no download: ${e.localizedMessage ?: "verifique sua conexão"}"
            }
            setLanguageStatus(language, LanguageModelStatus.Error(msg))
            Result.failure(e)
        }
    }

    suspend fun deleteLanguage(language: SupportedLanguage): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Fecha o translator ativo caso utilize este idioma para liberar ponteiros nativos.
            translatorMutex.withLock {
                val pair = activePair
                if (pair != null && (pair.first == language.mlKitCode || pair.second == language.mlKitCode)) {
                    activeTranslator?.close()
                    activeTranslator = null
                    activePair = null
                }
            }
            val model = TranslateRemoteModel.Builder(language.mlKitCode).build()
            modelManager.deleteDownloadedModel(model).await()
            setLanguageStatus(language, LanguageModelStatus.NotDownloaded)
            refreshDownloadedModels()
            Result.success(Unit)
        } catch (e: Exception) {
            setLanguageStatus(
                language,
                LanguageModelStatus.Error("Não foi possível remover: ${e.localizedMessage ?: "erro"}")
            )
            Result.failure(e)
        }
    }

    suspend fun getMissingLanguagesForPair(
        source: SupportedLanguage,
        target: SupportedLanguage
    ): List<SupportedLanguage> = withContext(Dispatchers.IO) {
        if (source == target) return@withContext emptyList()
        val downloaded = try {
            modelManager.getDownloadedModels(TranslateRemoteModel::class.java).await()
                .map { it.language }
                .toSet()
        } catch (_: Exception) {
            _languagePacks.value.filter { it.isDownloaded }.map { it.language.mlKitCode }.toSet()
        }

        val needed = LinkedHashSet<SupportedLanguage>()
        needed.add(source)
        needed.add(target)
        // O ML Kit utiliza Inglês como ponte quando nenhum dos dois idiomas é Inglês.
        if (source != SupportedLanguage.ENGLISH && target != SupportedLanguage.ENGLISH) {
            needed.add(SupportedLanguage.ENGLISH)
        }
        needed.filter { it.mlKitCode !in downloaded }
    }

    suspend fun translateBlock(
        bookId: Long,
        text: String,
        source: SupportedLanguage,
        target: SupportedLanguage
    ): TranslationOutcome = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isEmpty() || source == target) {
            return@withContext TranslationOutcome.Success(text, fromCache = true)
        }

        val cacheKey = buildCacheKey(source.mlKitCode, target.mlKitCode, cleanText)
        val cached = cacheDao.getByKey(cacheKey)
        if (cached != null) {
            return@withContext TranslationOutcome.Success(cached.translatedText, fromCache = true)
        }

        val missing = getMissingLanguagesForPair(source, target)
        if (missing.isNotEmpty()) {
            return@withContext TranslationOutcome.ModelMissing(missing)
        }

        try {
            val translator = getOrCreateTranslator(source.mlKitCode, target.mlKitCode)
            val translated = translator.translate(cleanText).await()
            cacheDao.insert(
                TranslationCacheEntity(
                    cacheKey = cacheKey,
                    bookId = bookId,
                    sourceLanguage = source.mlKitCode,
                    targetLanguage = target.mlKitCode,
                    translatedText = translated,
                    byteSize = translated.toByteArray(Charsets.UTF_8).size.toLong() +
                        cacheKey.length.toLong()
                )
            )
            TranslationOutcome.Success(translated, fromCache = false)
        } catch (e: Exception) {
            TranslationOutcome.Failure(e.localizedMessage ?: "Erro na tradução offline.")
        }
    }

    suspend fun releaseTranslatorMemory() {
        translatorMutex.withLock {
            activeTranslator?.close()
            activeTranslator = null
            activePair = null
        }
    }

    private suspend fun getOrCreateTranslator(sourceCode: String, targetCode: String): Translator {
        return translatorMutex.withLock {
            val desired = sourceCode to targetCode
            val existing = activeTranslator
            if (existing != null && activePair == desired) {
                return@withLock existing
            }
            existing?.close()
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceCode)
                .setTargetLanguage(targetCode)
                .build()
            val created = Translation.getClient(options)
            activePair = desired
            activeTranslator = created
            created
        }
    }

    private fun setLanguageStatus(language: SupportedLanguage, status: LanguageModelStatus) {
        _languagePacks.update { list ->
            list.map { item ->
                if (item.language == language) item.copy(status = status) else item
            }
        }
    }

    /**
     * Inspeciona os diretórios internos do ML Kit para medir o espaço real ocupado (em MB)
     * por cada modelo baixado no dispositivo.
     */
    private fun measureModelSizesOnDisk(downloadedCodes: Set<String>): Map<String, Double> {
        if (downloadedCodes.isEmpty()) return emptyMap()
        val roots = listOfNotNull(context.noBackupFilesDir, context.filesDir)
        val totalsByCode = HashMap<String, Long>()

        for (root in roots) {
            val mlkitDirs = root.listFiles()?.filter { file ->
                file.isDirectory && (
                    file.name.contains("mlkit", ignoreCase = true) ||
                        file.name.contains("translate", ignoreCase = true)
                    )
            }.orEmpty()

            for (dir in mlkitDirs) {
                dir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val pathLower = file.absolutePath.lowercase()
                        for (code in downloadedCodes) {
                            if (pathLower.contains("_${code}_") ||
                                pathLower.contains("en_$code") ||
                                pathLower.contains("${code}_en") ||
                                pathLower.contains("/$code/") ||
                                pathLower.contains("-$code-")
                            ) {
                                totalsByCode[code] = (totalsByCode[code] ?: 0L) + file.length()
                            }
                        }
                    }
                }
            }
        }

        val result = HashMap<String, Double>()
        for ((code, bytes) in totalsByCode) {
            if (bytes > 512 * 1024L) { // Apenas usa medida real se os arquivos do modelo forem visíveis (>0.5 MB)
                result[code] = bytes.toDouble() / (1024.0 * 1024.0)
            }
        }
        return result
    }

    private fun buildCacheKey(source: String, target: String, text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$source|$target|$text".toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "$source:$target:$hex"
    }
}
