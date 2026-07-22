@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.theme

import androidx.compose.material3.Typography as MaterialTypography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography as TvTypography
import com.jing.sakura.R

private val ClaudeSans = FontFamily(
    Font(
        resId = R.font.anthropic_sans_variable,
        weight = FontWeight.Normal
    )
)

private fun TextStyle.withClaudeStyle(weight: FontWeight): TextStyle = copy(
    fontFamily = ClaudeSans,
    fontWeight = weight,
    letterSpacing = 0.sp
)

private fun TextStyle.withClaudeEditorialStyle(weight: FontWeight): TextStyle = copy(
    fontFamily = FontFamily.Serif,
    fontWeight = weight,
    letterSpacing = 0.sp
)

private val defaultTvTypography = TvTypography()

internal val AulamaTvTypography = defaultTvTypography.copy(
    displayLarge = defaultTvTypography.displayLarge.withClaudeEditorialStyle(FontWeight.ExtraBold),
    displayMedium = defaultTvTypography.displayMedium.withClaudeEditorialStyle(FontWeight.ExtraBold),
    displaySmall = defaultTvTypography.displaySmall.withClaudeEditorialStyle(FontWeight.ExtraBold),
    headlineLarge = defaultTvTypography.headlineLarge.withClaudeEditorialStyle(FontWeight.ExtraBold),
    headlineMedium = defaultTvTypography.headlineMedium.withClaudeEditorialStyle(FontWeight.ExtraBold),
    headlineSmall = defaultTvTypography.headlineSmall.withClaudeEditorialStyle(FontWeight.ExtraBold),
    titleLarge = defaultTvTypography.titleLarge.withClaudeEditorialStyle(FontWeight.Bold),
    titleMedium = defaultTvTypography.titleMedium.withClaudeStyle(FontWeight.SemiBold),
    titleSmall = defaultTvTypography.titleSmall.withClaudeStyle(FontWeight.SemiBold),
    labelLarge = defaultTvTypography.labelLarge.withClaudeStyle(FontWeight.SemiBold),
    labelMedium = defaultTvTypography.labelMedium.withClaudeStyle(FontWeight.SemiBold),
    labelSmall = defaultTvTypography.labelSmall.withClaudeStyle(FontWeight.Medium)
)

private val defaultMaterialTypography = MaterialTypography()

internal val AulamaMaterialTypography = defaultMaterialTypography.copy(
    displayLarge = defaultMaterialTypography.displayLarge.withClaudeEditorialStyle(FontWeight.ExtraBold),
    displayMedium = defaultMaterialTypography.displayMedium.withClaudeEditorialStyle(FontWeight.ExtraBold),
    displaySmall = defaultMaterialTypography.displaySmall.withClaudeEditorialStyle(FontWeight.ExtraBold),
    headlineLarge = defaultMaterialTypography.headlineLarge.withClaudeEditorialStyle(FontWeight.ExtraBold),
    headlineMedium = defaultMaterialTypography.headlineMedium.withClaudeEditorialStyle(FontWeight.ExtraBold),
    headlineSmall = defaultMaterialTypography.headlineSmall.withClaudeEditorialStyle(FontWeight.ExtraBold),
    titleLarge = defaultMaterialTypography.titleLarge.withClaudeEditorialStyle(FontWeight.Bold),
    titleMedium = defaultMaterialTypography.titleMedium.withClaudeStyle(FontWeight.SemiBold),
    titleSmall = defaultMaterialTypography.titleSmall.withClaudeStyle(FontWeight.SemiBold),
    labelLarge = defaultMaterialTypography.labelLarge.withClaudeStyle(FontWeight.SemiBold),
    labelMedium = defaultMaterialTypography.labelMedium.withClaudeStyle(FontWeight.SemiBold),
    labelSmall = defaultMaterialTypography.labelSmall.withClaudeStyle(FontWeight.Medium)
)
