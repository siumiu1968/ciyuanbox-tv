package com.jing.sakura.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.jing.sakura.compose.common.AulamaTvColors


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SakuraTheme(content: @Composable () -> Unit) {
    val tvDarkColors = darkColorScheme(
        primary = AulamaTvColors.Cyan,
        onPrimary = Color(0xFF041013),
        primaryContainer = Color(0xFF123A40),
        onPrimaryContainer = Color(0xFFD9FBFF),
        secondary = AulamaTvColors.Pink,
        onSecondary = Color(0xFF220510),
        secondaryContainer = Color(0xFF49182E),
        onSecondaryContainer = Color(0xFFFFD9E8),
        tertiary = AulamaTvColors.Amber,
        onTertiary = Color(0xFF201800),
        tertiaryContainer = Color(0xFF433710),
        onTertiaryContainer = Color(0xFFFFEDB6),
        background = AulamaTvColors.Background,
        onBackground = AulamaTvColors.TextPrimary,
        surface = AulamaTvColors.Surface,
        onSurface = AulamaTvColors.TextPrimary,
        surfaceVariant = AulamaTvColors.SurfaceRaised,
        onSurfaceVariant = AulamaTvColors.TextSecondary,
        surfaceTint = AulamaTvColors.Cyan,
        inverseSurface = Color(0xFFE7EEF2),
        inverseOnSurface = Color(0xFF11171D),
        error = Color(0xFFFF7B8D),
        onError = Color(0xFF320A14),
        errorContainer = Color(0xFF4D1622),
        onErrorContainer = Color(0xFFFFDCE3),
        border = AulamaTvColors.FocusBorder,
        borderVariant = AulamaTvColors.Outline,
        scrim = Color(0xD9000306)
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
