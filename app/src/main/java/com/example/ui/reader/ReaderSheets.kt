package com.example.ui.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ReaderFontOption
import com.example.model.ReaderPreferences
import com.example.model.ReadingTheme
import com.example.model.SupportedLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderAppearanceSheet(
    preferences: ReaderPreferences,
    onSelectTheme: (ReadingTheme) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onSelectFont: (ReaderFontOption) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Aparência & Conforto Ocular",
                style = MaterialTheme.typography.titleLarge
            )

            // 4 temas de conforto ocular
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ReadingTheme.entries.forEach { theme ->
                    val palette = theme.palette()
                    val selected = preferences.readingTheme == theme
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.background,
                        border = BorderStroke(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.secondary else palette.divider
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(66.dp)
                            .testTag("reader_theme_${theme.name.lowercase()}")
                            .clickable { onSelectTheme(theme) }
                    ) {
                        Column(
                            modifier = Modifier.padding(6.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Aa",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.primaryText
                            )
                            Text(
                                text = theme.labelPt,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.secondaryText
                            )
                        }
                    }
                }
            }

            // Fonte
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ReaderFontOption.entries.forEach { option ->
                    FilterChip(
                        selected = preferences.fontOption == option,
                        onClick = { onSelectFont(option) },
                        label = { Text(option.labelPt) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reader_font_${option.name.lowercase()}")
                    )
                }
            }

            // Tamanho da fonte
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tamanho da fonte",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onFontSizeChange((preferences.fontSizeSp - 1f).coerceAtLeast(14f)) },
                            modifier = Modifier.testTag("font_size_decrease")
                        ) {
                            Text("A-")
                        }
                        Text(
                            text = "${preferences.fontSizeSp.toInt()} sp",
                            style = MaterialTheme.typography.labelMedium
                        )
                        OutlinedButton(
                            onClick = { onFontSizeChange((preferences.fontSizeSp + 1f).coerceAtMost(28f)) },
                            modifier = Modifier.testTag("font_size_increase")
                        ) {
                            Text("A+")
                        }
                    }
                }
                Slider(
                    value = preferences.fontSizeSp,
                    onValueChange = onFontSizeChange,
                    valueRange = 14f..28f,
                    steps = 13,
                    modifier = Modifier.testTag("font_size_slider")
                )
            }

            // Espaçamento entre linhas
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Espaçamento entre linhas",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "${"%.2f".format(preferences.lineSpacingMultiplier)}x",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Slider(
                    value = preferences.lineSpacingMultiplier,
                    onValueChange = onLineSpacingChange,
                    valueRange = 1.2f..2.0f,
                    steps = 7,
                    modifier = Modifier.testTag("line_spacing_slider")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguagePairPickerDialog(
    initialSource: SupportedLanguage,
    initialTarget: SupportedLanguage,
    onConfirm: (SupportedLanguage, SupportedLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSource by remember { mutableStateOf(initialSource) }
    var selectedTarget by remember { mutableStateOf(initialTarget) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Idiomas deste livro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Idioma original do livro:",
                    style = MaterialTheme.typography.titleSmall
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SupportedLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = selectedSource == lang,
                            onClick = { selectedSource = lang },
                            label = { Text(lang.displayNamePt) },
                            modifier = Modifier.testTag("pick_source_${lang.mlKitCode}")
                        )
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Traduzir offline para:",
                    style = MaterialTheme.typography.titleSmall
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SupportedLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = selectedTarget == lang,
                            onClick = { selectedTarget = lang },
                            label = { Text(lang.displayNamePt) },
                            modifier = Modifier.testTag("pick_target_${lang.mlKitCode}")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedSource, selectedTarget) },
                modifier = Modifier.testTag("confirm_language_pair_button")
            ) {
                Text("Aplicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
