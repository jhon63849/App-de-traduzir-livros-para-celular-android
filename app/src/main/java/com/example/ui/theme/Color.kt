package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Editorial "Paper & Ink" Brand Palette
val InkForestPrimary = Color(0xFF1E2D24)
val InkForestOnPrimary = Color(0xFFFBF8F1)
val InkForestContainer = Color(0xFFD6E5DB)
val InkForestOnContainer = Color(0xFF102016)

val BurnishedAmberSecondary = Color(0xFFA66328)
val BurnishedAmberOnSecondary = Color(0xFFFFFBF7)
val BurnishedAmberContainer = Color(0xFFF9E2CB)
val BurnishedAmberOnContainer = Color(0xFF3A1E04)

val SlateTealTertiary = Color(0xFF355C66)
val SlateTealOnTertiary = Color(0xFFFFFFFF)
val SlateTealContainer = Color(0xFFCDE7EE)
val SlateTealOnContainer = Color(0xFF0A2830)

val ArchivalPaperBackground = Color(0xFFF8F4EC)
val ArchivalPaperSurface = Color(0xFFFBF8F1)
val ArchivalPaperSurfaceVariant = Color(0xFFEDE6D8)
val InkCharcoalText = Color(0xFF1E1C19)
val InkMutedText = Color(0xFF5D574E)
val ArchivalOutline = Color(0xFFD5CCB9)

// Dark Mode Editorial Palette
val DarkVelvetBackground = Color(0xFF111412)
val DarkVelvetSurface = Color(0xFF181C19)
val DarkVelvetSurfaceVariant = Color(0xFF232925)
val WarmGoldPrimary = Color(0xFFE3A857)
val WarmGoldOnPrimary = Color(0xFF2D1B03)
val WarmGoldContainer = Color(0xFF473011)
val WarmGoldOnContainer = Color(0xFFFFE1BA)
val SageSecondary = Color(0xFF9EC2AA)
val SageOnSecondary = Color(0xFF0D2618)
val SageContainer = Color(0xFF223C2C)
val SageOnContainer = Color(0xFFD1EAD9)
val DarkTextPrimary = Color(0xFFECE8DF)
val DarkTextMuted = Color(0xFFA7A298)
val DarkOutline = Color(0xFF38403A)

// Book Spine Cover Accent Swatches
val BookCoverSwatches = listOf(
    Color(0xFF213A2C),
    Color(0xFF8C4A27),
    Color(0xFF2B4157),
    Color(0xFF5C3546),
    Color(0xFF4B4F2C),
    Color(0xFF3B3552)
)

// Dedicated Eye-Comfort Reader Themes
data class ReaderThemePalette(
    val background: Color,
    val surfaceChrome: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val divider: Color,
    val isDark: Boolean
)

val PaperReaderPalette = ReaderThemePalette(
    background = Color(0xFFF7F1E3),
    surfaceChrome = Color(0xFFEDE4D0),
    primaryText = Color(0xFF24201A),
    secondaryText = Color(0xFF6B5F50),
    accent = Color(0xFF8C5A2B),
    divider = Color(0xFFDCD0B8),
    isDark = false
)

val SepiaReaderPalette = ReaderThemePalette(
    background = Color(0xFFEFE1C4),
    surfaceChrome = Color(0xFFE3D0AC),
    primaryText = Color(0xFF3B2A1A),
    secondaryText = Color(0xFF6E533B),
    accent = Color(0xFF7D481E),
    divider = Color(0xFFD1BA93),
    isDark = false
)

val LightReaderPalette = ReaderThemePalette(
    background = Color(0xFFFFFFFF),
    surfaceChrome = Color(0xFFF3F4F6),
    primaryText = Color(0xFF161618),
    secondaryText = Color(0xFF5A5E66),
    accent = Color(0xFF1F4E78),
    divider = Color(0xFFE2E4E9),
    isDark = false
)

val AmoledReaderPalette = ReaderThemePalette(
    background = Color(0xFF000000),
    surfaceChrome = Color(0xFF121212),
    primaryText = Color(0xFFD6D6DA),
    secondaryText = Color(0xFF8E8E96),
    accent = Color(0xFFE3A857),
    divider = Color(0xFF242428),
    isDark = true
)
