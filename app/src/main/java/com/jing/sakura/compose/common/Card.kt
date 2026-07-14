package com.jing.sakura.compose.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

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
    onFocused: (() -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    val displayTitle = localizedText(title)
    val displaySubtitle = localizedText(subTitle)
    val displaySourceName = localizedText(sourceName.toDisplayLineName())
    var focused by remember {
        mutableStateOf(false)
    }
    var focusSettled by remember { mutableStateOf(false) }
    val posterRequest = rememberPosterImageRequest(imageUrl = imageUrl)
    val artworkAccent = rememberArtworkAccent(imageUrl, enabled = focusSettled)
    val cardScale = if (focused) focusScale else 1f
    LaunchedEffect(focused) {
        focusSettled = false
        if (focused) {
            onFocused?.invoke()
            delay(220)
            focusSettled = true
        }
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
                shadowElevation = if (focused && focusSettled) 8.dp.toPx() else 0f
                shape = AulamaCardShape
                clip = false
                ambientShadowColor = artworkAccent.copy(alpha = 0.54f)
                spotShadowColor = artworkAccent.copy(alpha = 0.82f)
            }
            .onFocusChanged {
                focused = it.isFocused || it.hasFocus
            }
            .focusable()
            .customClick(onClick = onClick, onLongClick = onLongClick, onKeyEvent = onKeyEvent)
            .border(
                border = BorderStroke(
                    width = if (focused) 2.5.dp else 1.dp,
                    color = if (focused) artworkAccent else AulamaTvColors.Outline.copy(alpha = 0.72f)
                ),
                shape = AulamaCardShape
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(AulamaCardShape)
        ) {
            AsyncImage(
                model = posterRequest,
                contentDescription = displayTitle,
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
                                Color(0xF2050810)
                            )
                        )
                    )
            )
            if (displaySubtitle.isNotEmpty()) {
                Text(
                    text = displaySubtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(9.dp)
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xCC080B12))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 11.dp)
            ) {
                if (displaySourceName.isNotEmpty()) {
                    Text(
                        text = displaySourceName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = artworkAccent
                    )
                }
                Text(
                    text = displayTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = AulamaTvColors.TextPrimary
                )
            }
        }
    }
}
