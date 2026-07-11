package com.jing.sakura.compose.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideoCard(
    modifier: Modifier = Modifier,
    imageUrl: String,
    title: String,
    subTitle: String = "",
    sourceName: String = "",
    focusScale: Float = AulamaFocusScale,
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    var focused by remember {
        mutableStateOf(false)
    }
    val posterRequest = rememberPosterImageRequest(imageUrl = imageUrl)
    Surface(
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused || it.hasFocus
            }
            .customClick(onClick = onClick, onLongClick = onLongClick, onKeyEvent = onKeyEvent),
        onClick = {},
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusScale),
        shape = ClickableSurfaceDefaults.shape(shape = AulamaCardShape),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                BorderStroke(1.dp, AulamaTvColors.Outline)
            ),
            focusedBorder = Border(
                BorderStroke(2.dp, AulamaTvColors.FocusBorder)
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(AulamaCardShape)
        ) {
            AsyncImage(
                model = posterRequest,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0x18000000),
                                Color(0x66000000),
                                Color(0xF2070A0F)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        border = BorderStroke(
                            if (focused) 2.dp else 1.dp,
                            if (focused) AulamaTvColors.FocusBorder else AulamaTvColors.Outline
                        ),
                        shape = AulamaCardShape
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                if (sourceName.isNotEmpty()) {
                    Text(
                        text = sourceName.toDisplayLineName(),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        color = AulamaTvColors.Cyan
                    )
                }
                Text(
                    text = title,
                    maxLines = 1,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = AulamaTvColors.TextPrimary
                )
                if (subTitle.isNotEmpty()) {
                    Text(
                        text = subTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        color = AulamaTvColors.TextSecondary
                    )
                }
            }
        }
    }
}
