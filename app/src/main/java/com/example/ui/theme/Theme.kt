package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val EditorialLightColorScheme = lightColorScheme(
    primary = InkForestPrimary,
    onPrimary = InkForestOnPrimary,
    primaryContainer = InkForestContainer,
    onPrimaryContainer = InkForestOnContainer,
    secondary = BurnishedAmberSecondary,
    onSecondary = BurnishedAmberOnSecondary,
    secondaryContainer = BurnishedAmberContainer,
    onSecondaryContainer = BurnishedAmberOnContainer,
    tertiary = SlateTealTertiary,
    onTertiary = SlateTealOnTertiary,
    tertiaryContainer = SlateTealContainer,
    onTertiaryContainer = SlateTealOnContainer,
    background = ArchivalPaperBackground,
    onBackground = InkCharcoalText,
    surface = ArchivalPaperSurface,
    onSurface = InkCharcoalText,
    surfaceVariant = ArchivalPaperSurfaceVariant,
    onSurfaceVariant = InkMutedText,
    outline = ArchivalOutline
)

private val EditorialDarkColorScheme = darkColorScheme(
    primary = WarmGoldPrimary,
    onPrimary = WarmGoldOnPrimary,
    primaryContainer = WarmGoldContainer,
    onPrimaryContainer = WarmGoldOnContainer,
    secondary = SageSecondary,
    onSecondary = SageOnSecondary,
    secondaryContainer = SageContainer,
    onSecondaryContainer = SageOnContainer,
    tertiary = SlateTealContainer,
    onTertiary = SlateTealOnContainer,
    background = DarkVelvetBackground,
    onBackground = DarkTextPrimary,
    surface = DarkVelvetSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkVelvetSurfaceVariant,
    onSurfaceVariant = DarkTextMuted,
    outline = DarkOutline
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KindleTranslateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) EditorialDarkColorScheme else EditorialLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
