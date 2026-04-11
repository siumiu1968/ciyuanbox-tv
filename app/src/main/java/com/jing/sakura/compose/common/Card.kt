package com.jing.sakura.compose.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
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
    focusScale: Float = 1.1f,
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    var focused by remember {
        mutableStateOf(false)
    }
    val accentColor = rememberPosterAccentColor(imageUrl = imageUrl, enabled = true)
    val posterRequest = rememberPosterImageRequest(imageUrl = imageUrl)
    val borderColor by animateColorAsState(
        targetValue = if (focused) accentColor.copy(alpha = 0.92f) else Color.Transparent,
        animationSpec = tween(durationMillis = 90),
        label = "cardBorder"
    )
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
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(24.dp)),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                BorderStroke(2.dp, accentColor)
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
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
                                Color(0x180B1120),
                                Color(0x44091426),
                                Color(0xE40A1020)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        border = BorderStroke(3.dp, borderColor),
                        shape = RoundedCornerShape(24.dp)
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
                        text = sourceName,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFC8D8FF)
                    )
                }
                Text(
                    text = title,
                    maxLines = 1,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
                if (subTitle.isNotEmpty()) {
                    Text(
                        text = subTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        color = Color(0xFFD8E0FF),
                        modifier = Modifier.graphicsLayer { alpha = 0.88f }
                    )
                }
            }
        }
    }
}
