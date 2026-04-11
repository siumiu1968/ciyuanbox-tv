package com.jing.sakura.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SakuraTheme(content: @Composable () -> Unit) {
    val tvDarkColors = darkColorScheme(
        primary = Color(0xFFF4F6FF),
        onPrimary = Color(0xFF0A1020),
        primaryContainer = Color(0xFF7F5CFF),
        onPrimaryContainer = Color(0xFFFFFFFF),
        secondary = Color(0xFF8DE7FF),
        onSecondary = Color(0xFF08131F),
        secondaryContainer = Color(0xFF12354F),
        onSecondaryContainer = Color(0xFFE1F8FF),
        tertiary = Color(0xFFFF82C9),
        onTertiary = Color(0xFF230916),
        tertiaryContainer = Color(0xFF4B2145),
        onTertiaryContainer = Color(0xFFFFE3F3),
        background = Color(0xFF070C18),
        onBackground = Color(0xFFF2F5FF),
        surface = Color(0xFF101828),
        onSurface = Color(0xFFF5F7FF),
        surfaceVariant = Color(0xFF18233A),
        onSurfaceVariant = Color(0xFFC7D2F2),
        surfaceTint = Color(0xFF8F7CFF),
        inverseSurface = Color(0xFFF3F6FF),
        inverseOnSurface = Color(0xFF0D1527),
        error = Color(0xFFFF7B8D),
        onError = Color(0xFF320A14),
        errorContainer = Color(0xFF4D1622),
        onErrorContainer = Color(0xFFFFDCE3),
        border = Color(0xFF8F7CFF),
        borderVariant = Color(0xFF293450),
        scrim = Color(0xCC03060D)
    )

    val material3ThemeColors = androidx.compose.material3.darkColorScheme(
        primary = tvDarkColors.primary,
        onPrimary = tvDarkColors.onPrimary,
        primaryContainer = tvDarkColors.primaryContainer,
        onPrimaryContainer = tvDarkColors.onPrimaryContainer,
        inversePrimary = tvDarkColors.inversePrimary,
        secondary = tvDarkColors.secondary,
        onSecondary = tvDarkColors.onSecondary,
        secondaryContainer = tvDarkColors.secondaryContainer,
        onSecondaryContainer = tvDarkColors.onSecondaryContainer,
        tertiary = tvDarkColors.tertiary,
        onTertiary = tvDarkColors.onTertiary,
        tertiaryContainer = tvDarkColors.tertiaryContainer,
        onTertiaryContainer = tvDarkColors.onTertiaryContainer,
        background = tvDarkColors.background,
        onBackground = tvDarkColors.onBackground,
        surface = tvDarkColors.surface,
        onSurface = tvDarkColors.onSurface,
        surfaceVariant = tvDarkColors.surfaceVariant,
        onSurfaceVariant = tvDarkColors.onSurfaceVariant,
        surfaceTint = tvDarkColors.surfaceTint,
        inverseSurface = tvDarkColors.inverseSurface,
        inverseOnSurface = tvDarkColors.inverseOnSurface,
        error = tvDarkColors.error,
        onError = tvDarkColors.onError,
        errorContainer = tvDarkColors.errorContainer,
        onErrorContainer = tvDarkColors.onErrorContainer,
        outline = tvDarkColors.border,
        outlineVariant = tvDarkColors.borderVariant,
        scrim = tvDarkColors.scrim
    )
    MaterialTheme(colorScheme = tvDarkColors) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = material3ThemeColors,
            content = content
        )
    }
}
