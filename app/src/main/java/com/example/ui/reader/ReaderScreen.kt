package com.example.ui.reader

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.LoadedPage
import com.example.model.PageTextBlock
import com.example.model.PageTranslationStatus
import com.example.model.ReaderFontOption
import com.example.model.SupportedLanguage
import com.example.model.TranslationDisplayMode
import com.example.reader.PaginationConfig
import com.example.ui.theme.LiterataFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.ReaderThemePalette

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onOpenLanguageManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val palette = prefs.readingTheme.palette()

    var showMenus by remember { mutableStateOf(false) }
    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val view = LocalView.current
    DisposableEffect(palette.isDark) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            val prevLightStatus = controller.isAppearanceLightStatusBars
            val prevLightNav = controller.isAppearanceLightNavigationBars
            controller.isAppearanceLightStatusBars = !palette.isDark
            controller.isAppearanceLightNavigationBars = !palette.isDark
            onDispose {
                controller.isAppearanceLightStatusBars = prevLightStatus
                controller.isAppearanceLightNavigationBars = prevLightNav
            }
        } else {
            onDispose { }
        }
    }

    val fontFamily = when (prefs.fontOption) {
        ReaderFontOption.SERIF_LITERATA -> LiterataFontFamily
        ReaderFontOption.SANS_JAKARTA -> PlusJakartaSansFontFamily
    }

    val readingTextStyle = remember(prefs.fontSizeSp, prefs.lineSpacingMultiplier, fontFamily, palette.primaryText) {
        TextStyle(
            fontFamily = fontFamily,
            fontSize = prefs.fontSizeSp.sp,
            lineHeight = (prefs.fontSizeSp * prefs.lineSpacingMultiplier).sp,
            color = palette.primaryText
        )
    }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        // Área principal de leitura com detecção de toques estilo Kindle (esquerda / centro / direita) e swipe
        var accumulatedDrag by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .pointerInput(uiState.totalPages) {
                    detectTapGestures { offset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val ratio = offset.x / width
                        when {
                            ratio < 0.28f -> {
                                showMenus = false
                                viewModel.previousPage()
                            }

                            ratio > 0.72f -> {
                                showMenus = false
                                viewModel.nextPage()
                            }

                            else -> {
                                showMenus = !showMenus
                            }
                        }
                    }
                }
                .pointerInput(uiState.totalPages) {
                    detectHorizontalDragGestures(
                        onDragStart = { accumulatedDrag = 0f },
                        onHorizontalDrag = { _, dragAmount -> accumulatedDrag += dragAmount },
                        onDragEnd = {
                            if (accumulatedDrag < -85f) {
                                showMenus = false
                                viewModel.nextPage()
                            } else if (accumulatedDrag > 85f) {
                                showMenus = false
                                viewModel.previousPage()
                            }
                            accumulatedDrag = 0f
                        }
                    )
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .padding(horizontal = 22.dp)
            ) {
                // Cabeçalho discreto permanente com o botão rápido de alternância dos 3 modos no topo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = uiState.currentPage?.chapterTitle
                            ?.ifBlank { uiState.book?.title.orEmpty() }
                            .orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.secondaryText.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Botão discreto no topo da tela alternando instantaneamente entre os 3 modos
                    DiscreetModeTogglePill(
                        mode = prefs.displayMode,
                        isTranslating = uiState.currentPage?.translationStatus == PageTranslationStatus.TRANSLATING,
                        palette = palette,
                        onClick = { viewModel.cycleDisplayMode() }
                    )
                }

                // Aviso inline se faltar pacote offline ML Kit
                if (prefs.displayMode != TranslationDisplayMode.ORIGINAL_ONLY && uiState.missingLanguages.isNotEmpty()) {
                    MissingModelsBanner(
                        missingLanguages = uiState.missingLanguages,
                        isDownloading = uiState.isDownloadingModels,
                        palette = palette,
                        onDownloadNow = { viewModel.downloadMissingModelsNow() },
                        onOpenManager = onOpenLanguageManager
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Viewport exato medido via BoxWithConstraints para paginação real sem rolagem vertical
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val contentWidthPx = with(density) { maxWidth.roundToPx() }
                    val contentHeightPx = with(density) { maxHeight.roundToPx() }
                    val paragraphSpacingPx = with(density) { 14.dp.roundToPx() }

                    val bilingualMode = prefs.displayMode == TranslationDisplayMode.BILINGUAL
                    val signature = remember(
                        uiState.book?.id,
                        contentWidthPx,
                        contentHeightPx,
                        prefs.fontSizeSp,
                        prefs.lineSpacingMultiplier,
                        prefs.fontOption,
                        bilingualMode
                    ) {
                        "${uiState.book?.id}|$contentWidthPx|$contentHeightPx|${prefs.fontSizeSp}|${prefs.lineSpacingMultiplier}|${prefs.fontOption}|$bilingualMode"
                    }

                    LaunchedEffect(signature, uiState.book?.id) {
                        if (uiState.book != null && contentWidthPx > 40 && contentHeightPx > 80) {
                            viewModel.requestPagination(
                                textMeasurer = textMeasurer,
                                config = PaginationConfig(
                                    contentWidthPx = contentWidthPx,
                                    contentHeightPx = contentHeightPx,
                                    paragraphSpacingPx = paragraphSpacingPx,
                                    bilingualMode = bilingualMode,
                                    textStyle = readingTextStyle
                                ),
                                signature = signature
                            )
                        }
                    }

                    when {
                        uiState.isPaginating -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = palette.accent,
                                        strokeWidth = 2.5.dp
                                    )
                                    Text(
                                        text = "Paginando para sua tela...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.secondaryText
                                    )
                                }
                            }
                        }

                        uiState.currentPage != null -> {
                            KindlePageCanvas(
                                page = uiState.currentPage!!,
                                displayMode = prefs.displayMode,
                                textStyle = readingTextStyle,
                                palette = palette
                            )
                        }

                        else -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Nenhum conteúdo disponível nesta página.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.secondaryText
                                )
                            }
                        }
                    }
                }

                // Rodapé minimalista estilo Kindle (sempre visível e discreto)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val book = uiState.book
                    val langBadge = if (book != null) {
                        "${SupportedLanguage.fromCode(book.sourceLanguage).codeLabel} → ${SupportedLanguage.fromCode(book.targetLanguage).codeLabel}"
                    } else {
                        ""
                    }
                    Text(
                        text = buildString {
                            append(langBadge)
                            if (uiState.nextPagePreTranslated && prefs.displayMode != TranslationDisplayMode.ORIGINAL_ONLY) {
                                append(" • Próx. pronta")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.secondaryText.copy(alpha = 0.72f)
                    )
                    Text(
                        text = if (uiState.totalPages > 0) {
                            "Pág. ${uiState.currentPageIndex + 1} de ${uiState.totalPages}  •  ${uiState.progressPercent}%"
                        } else {
                            ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.secondaryText.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // Menu Superior (abre/esconde ao tocar no centro da tela)
        AnimatedVisibility(
            visible = showMenus,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderTopOverlayBar(
                title = uiState.book?.title.orEmpty(),
                sourceLang = uiState.book?.let { SupportedLanguage.fromCode(it.sourceLanguage) } ?: SupportedLanguage.ENGLISH,
                targetLang = uiState.book?.let { SupportedLanguage.fromCode(it.targetLanguage) } ?: SupportedLanguage.PORTUGUESE,
                displayMode = prefs.displayMode,
                palette = palette,
                onBack = onBack,
                onSelectMode = { viewModel.setDisplayMode(it) },
                onOpenLanguagePicker = { showLanguageDialog = true },
                onOpenAppearance = { showAppearanceSheet = true }
            )
        }

        // Menu Inferior (abre/esconde ao tocar no centro da tela)
        AnimatedVisibility(
            visible = showMenus,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ReaderBottomOverlayBar(
                currentPageIndex = uiState.currentPageIndex,
                totalPages = uiState.totalPages,
                progressPercent = uiState.progressPercent,
                chapterTitle = uiState.currentPage?.chapterTitle.orEmpty(),
                ramPagesCount = uiState.activeRamPagesCount,
                palette = palette,
                onPrevPage = { viewModel.previousPage() },
                onNextPage = { viewModel.nextPage() },
                onSeekPage = { viewModel.goToPage(it) }
            )
        }
    }

    if (showAppearanceSheet) {
        ReaderAppearanceSheet(
            preferences = prefs,
            onSelectTheme = { viewModel.setReadingTheme(it) },
            onFontSizeChange = { viewModel.setFontSize(it) },
            onLineSpacingChange = { viewModel.setLineSpacing(it) },
            onSelectFont = { viewModel.setFontOption(it) },
            onDismiss = { showAppearanceSheet = false }
        )
    }

    val currentBook = uiState.book
    if (showLanguageDialog && currentBook != null) {
        LanguagePairPickerDialog(
            initialSource = SupportedLanguage.fromCode(currentBook.sourceLanguage),
            initialTarget = SupportedLanguage.fromCode(currentBook.targetLanguage),
            onConfirm = { src, tgt ->
                viewModel.updateLanguagePair(src, tgt)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
}

@Composable
private fun DiscreetModeTogglePill(
    mode: TranslationDisplayMode,
    isTranslating: Boolean,
    palette: ReaderThemePalette,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = palette.surfaceChrome.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, palette.divider),
        modifier = Modifier
            .testTag("reader_discreet_mode_toggle")
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (isTranslating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.8.dp,
                    color = palette.accent
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Translate,
                    contentDescription = "Alternar modo de tradução",
                    tint = palette.accent,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = mode.shortLabelPt,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.primaryText
            )
        }
    }
}

@Composable
private fun KindlePageCanvas(
    page: LoadedPage,
    displayMode: TranslationDisplayMode,
    textStyle: TextStyle,
    palette: ReaderThemePalette
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (page.translationStatus == PageTranslationStatus.TRANSLATING &&
            displayMode != TranslationDisplayMode.ORIGINAL_ONLY
        ) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(CircleShape),
                color = palette.accent,
                trackColor = palette.divider
            )
        }

        page.blocks.forEach { block ->
            PageBlockView(
                block = block,
                displayMode = displayMode,
                textStyle = textStyle,
                palette = palette
            )
        }
    }
}

@Composable
private fun PageBlockView(
    block: PageTextBlock,
    displayMode: TranslationDisplayMode,
    textStyle: TextStyle,
    palette: ReaderThemePalette
) {
    when (displayMode) {
        TranslationDisplayMode.ORIGINAL_ONLY -> {
            Text(
                text = block.originalText,
                style = textStyle,
                color = palette.primaryText
            )
        }

        TranslationDisplayMode.TRANSLATION_ONLY -> {
            val displayText = block.translatedText ?: block.originalText
            val isPending = block.translatedText == null
            Text(
                text = displayText,
                style = textStyle,
                color = if (isPending) palette.secondaryText else palette.primaryText
            )
        }

        TranslationDisplayMode.BILINGUAL -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = block.originalText,
                    style = textStyle.copy(
                        fontSize = (textStyle.fontSize.value * 0.88f).sp,
                        lineHeight = (textStyle.lineHeight.value * 0.88f).sp
                    ),
                    color = palette.secondaryText
                )
                Text(
                    text = block.translatedText ?: "Traduzindo offline...",
                    style = textStyle.copy(
                        fontStyle = if (block.translatedText == null) FontStyle.Italic else FontStyle.Normal
                    ),
                    color = if (block.translatedText == null) palette.accent else palette.primaryText
                )
            }
        }
    }
}

@Composable
private fun MissingModelsBanner(
    missingLanguages: List<SupportedLanguage>,
    isDownloading: Boolean,
    palette: ReaderThemePalette,
    onDownloadNow: () -> Unit,
    onOpenManager: () -> Unit
) {
    val names = missingLanguages.joinToString(" + ") { it.displayNamePt }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = palette.surfaceChrome,
        border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Pacote offline necessário: $names",
                style = MaterialTheme.typography.titleSmall,
                color = palette.primaryText
            )
            Text(
                text = "Baixe uma única vez (~30 MB por idioma) para traduzir 100% offline.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.secondaryText
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onDownloadNow,
                    enabled = !isDownloading,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                    modifier = Modifier.testTag("reader_download_missing_models_button")
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = palette.background
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isDownloading) "Baixando..." else "Baixar agora")
                }
                OutlinedButton(
                    onClick = onOpenManager,
                    modifier = Modifier.testTag("reader_open_lang_manager_button")
                ) {
                    Text("Idiomas", color = palette.primaryText)
                }
            }
        }
    }
}

@Composable
private fun ReaderTopOverlayBar(
    title: String,
    sourceLang: SupportedLanguage,
    targetLang: SupportedLanguage,
    displayMode: TranslationDisplayMode,
    palette: ReaderThemePalette,
    onBack: () -> Unit,
    onSelectMode: (TranslationDisplayMode) -> Unit,
    onOpenLanguagePicker: () -> Unit,
    onOpenAppearance: () -> Unit
) {
    Surface(
        color = palette.surfaceChrome.copy(alpha = 0.97f),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("reader_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar para biblioteca",
                        tint = palette.primaryText
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = palette.background,
                    border = BorderStroke(1.dp, palette.divider),
                    modifier = Modifier
                        .testTag("reader_language_pair_button")
                        .clickable(onClick = onOpenLanguagePicker)
                ) {
                    Text(
                        text = "${sourceLang.codeLabel} → ${targetLang.codeLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.accent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
                IconButton(
                    onClick = onOpenAppearance,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("reader_appearance_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.FormatSize,
                        contentDescription = "Fonte e tema de leitura",
                        tint = palette.primaryText
                    )
                }
            }

            // Seletor explícito dos 3 modos de tradução
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.background)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TranslationDisplayMode.entries.forEach { mode ->
                    val selected = displayMode == mode
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = if (selected) palette.accent else palette.background,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("reader_mode_${mode.name.lowercase()}")
                            .clickable { onSelectMode(mode) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = mode.labelPt,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) palette.background else palette.primaryText,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderBottomOverlayBar(
    currentPageIndex: Int,
    totalPages: Int,
    progressPercent: Int,
    chapterTitle: String,
    ramPagesCount: Int,
    palette: ReaderThemePalette,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onSeekPage: (Int) -> Unit
) {
    Surface(
        color = palette.surfaceChrome.copy(alpha = 0.97f),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chapterTitle.ifBlank { "Leitura em andamento" },
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "RAM: $ramPagesCount págs",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.secondaryText
                )
            }

            if (totalPages > 1) {
                Slider(
                    value = currentPageIndex.toFloat(),
                    onValueChange = { onSeekPage(it.toInt()) },
                    valueRange = 0f..(totalPages - 1).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = palette.accent,
                        activeTrackColor = palette.accent,
                        inactiveTrackColor = palette.divider
                    ),
                    modifier = Modifier.testTag("reader_page_slider")
                )
            }

            HorizontalDivider(color = palette.divider)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrevPage,
                    enabled = currentPageIndex > 0,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("reader_prev_page_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Página anterior",
                        tint = if (currentPageIndex > 0) palette.primaryText else palette.divider
                    )
                }

                Text(
                    text = "Página ${currentPageIndex + 1} de ${totalPages.coerceAtLeast(1)} • $progressPercent%",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.primaryText
                )

                IconButton(
                    onClick = onNextPage,
                    enabled = currentPageIndex < totalPages - 1,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("reader_next_page_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Próxima página",
                        tint = if (currentPageIndex < totalPages - 1) palette.primaryText else palette.divider
                    )
                }
            }
        }
    }
}
