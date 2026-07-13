@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jing.sakura.R

object AulamaTvColors {
    val Background = Color(0xFF050810)
    val BackgroundTeal = Color(0xFF071922)
    val BackgroundPlum = Color(0xFF171024)
    val Surface = Color(0xFF0A111C)
    val SurfaceRaised = Color(0xFF111C2A)
    val Outline = Color(0xFF2A3A4E)
    val FocusBorder = Color(0xFF72EAF2)
    val Cyan = Color(0xFF32D5E4)
    val Pink = Color(0xFFFF4E9A)
    val Blue = Color(0xFF7787FF)
    val Amber = Color(0xFFF4C95D)
    val Green = Color(0xFF58D68D)
    val TextPrimary = Color(0xFFF3F7FA)
    val TextSecondary = Color(0xFFAAB8C7)
}

val AulamaCardShape = RoundedCornerShape(12.dp)
const val AulamaFocusScale = 1.035f

fun Modifier.aulamaTvBackground(): Modifier = background(
    Brush.linearGradient(
        colorStops = arrayOf(
            0f to AulamaTvColors.BackgroundTeal,
            0.42f to AulamaTvColors.Background,
            1f to AulamaTvColors.BackgroundPlum
        )
    )
)

@Composable
fun AulamaPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.aulama_anime_icon),
            contentDescription = null,
            modifier = Modifier.size(46.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = AulamaTvColors.TextPrimary,
                maxLines = 1
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AulamaTvColors.TextSecondary,
                    maxLines = 1
                )
            }
        }
        trailing()
    }
}

@Composable
fun AulamaSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    accent: Color = AulamaTvColors.Cyan
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(
            modifier = Modifier
                .size(width = 4.dp, height = 24.dp)
                .background(accent, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            color = AulamaTvColors.TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        count?.let {
            Text(
                text = "$it 套",
                style = MaterialTheme.typography.labelLarge,
                color = AulamaTvColors.TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AulamaActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    accent: Color = AulamaTvColors.Cyan
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = ButtonDefaults.shape(shape = AulamaCardShape),
        scale = ButtonDefaults.scale(focusedScale = AulamaFocusScale),
        border = ButtonDefaults.border(
            border = Border(
                BorderStroke(1.dp, AulamaTvColors.Outline),
                shape = AulamaCardShape
            ),
            focusedBorder = Border(
                BorderStroke(2.dp, AulamaTvColors.FocusBorder),
                shape = AulamaCardShape
            )
        ),
        colors = ButtonDefaults.colors(
            containerColor = AulamaTvColors.SurfaceRaised,
            contentColor = AulamaTvColors.TextPrimary,
            focusedContainerColor = accent,
            focusedContentColor = Color(0xFF061014)
        )
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text = label, maxLines = 1)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AulamaIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = AulamaTvColors.Cyan
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        shape = ButtonDefaults.shape(shape = AulamaCardShape),
        scale = ButtonDefaults.scale(focusedScale = AulamaFocusScale),
        border = ButtonDefaults.border(
            border = Border(
                BorderStroke(1.dp, AulamaTvColors.Outline),
                shape = AulamaCardShape
            ),
            focusedBorder = Border(
                BorderStroke(2.dp, AulamaTvColors.FocusBorder),
                shape = AulamaCardShape
            )
        ),
        colors = ButtonDefaults.colors(
            containerColor = AulamaTvColors.SurfaceRaised,
            contentColor = AulamaTvColors.TextPrimary,
            focusedContainerColor = accent,
            focusedContentColor = Color(0xFF061014)
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp)
        )
    }
}

fun String.toDisplayLineName(): String {
    val value = trim()
    return if (
        value.equals("CYC", ignoreCase = true) ||
        value.startsWith("CYC_", ignoreCase = true) ||
        value.startsWith("CYC-", ignoreCase = true)
    ) {
        "主線路"
    } else {
        value
    }
}
