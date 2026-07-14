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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.jing.sakura.R
import com.jing.sakura.auth.AulamaAccount

object AulamaTvColors {
    val Background = Color(0xFF05070C)
    val BackgroundTeal = Color(0xFF07151A)
    val BackgroundPlum = Color(0xFF160B17)
    val Surface = Color(0xE60A0E16)
    val SurfaceRaised = Color(0xFF141A24)
    val Outline = Color(0xFF303947)
    val FocusBorder = Color(0xFF66E5EE)
    val Cyan = Color(0xFF52DCE7)
    val Pink = Color(0xFFFF4F91)
    val Blue = Color(0xFF7597FF)
    val Amber = Color(0xFFFFC76A)
    val Green = Color(0xFF67D69B)
    val TextPrimary = Color(0xFFF7F8FB)
    val TextSecondary = Color(0xFFADB7C5)
}

val AulamaCardShape = RoundedCornerShape(8.dp)
const val AulamaFocusScale = 1.06f

fun Modifier.aulamaTvBackground(): Modifier = background(
    Brush.linearGradient(
        colorStops = arrayOf(
            0f to AulamaTvColors.BackgroundTeal,
            0.52f to AulamaTvColors.Background,
            1f to AulamaTvColors.BackgroundPlum
        )
    )
)

@Composable
fun AulamaAnimeBrandMark(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 50.dp
) {
    Image(
        painter = painterResource(R.drawable.aulama_anime_wordmark),
        contentDescription = "Aulama Anime",
        modifier = modifier
            .heightIn(min = height, max = height)
            .width(height * AULAMA_WORDMARK_RATIO)
    )
}

private const val AULAMA_WORDMARK_RATIO = 2.4308f

@Composable
fun AulamaPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val displayTitle = localizedText(title)
    val displaySubtitle = localizedText(subtitle)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .padding(horizontal = 36.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.aulama_anime_icon),
            contentDescription = null,
            modifier = Modifier.size(50.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 26.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = AulamaTvColors.TextPrimary,
                maxLines = 1
            )
            if (displaySubtitle.isNotBlank()) {
                Text(
                    text = displaySubtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
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
    val displayTitle = localizedText(title)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(
            modifier = Modifier
                .size(width = 4.dp, height = 26.dp)
                .background(accent, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = displayTitle,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 22.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = AulamaTvColors.TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        count?.let {
            Text(
                text = "$it 套",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
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
    val displayLabel = localizedText(label)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
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
        Row(
            modifier = Modifier.height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = displayLabel,
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

@Composable
fun AulamaAccountAvatar(
    account: AulamaAccount,
    modifier: Modifier = Modifier
) {
    val photoUrl = remember(account.photoUrl) { normalizeAccountPhotoUrl(account.photoUrl) }
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .clip(CircleShape)
            .background(AulamaTvColors.Cyan.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = account.name.trim().firstOrNull()?.uppercase() ?: "A",
            color = AulamaTvColors.TextPrimary,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        if (photoUrl.isNotBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = account.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
            )
        }
    }
}

private fun normalizeAccountPhotoUrl(value: String): String {
    val normalized = value.trim()
    return when {
        normalized.startsWith("//") -> "https:$normalized"
        normalized.startsWith("/") -> "https://aulama.org$normalized"
        else -> normalized
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
        modifier = modifier.size(52.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(13.dp),
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
            modifier = Modifier.size(24.dp)
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
