package com.jing.sakura.compose.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val posterAccentCache = linkedMapOf<String, Color>()

@Composable
fun rememberPosterImageRequest(
    imageUrl: String,
    widthPx: Int = 420,
    heightPx: Int = 600
): ImageRequest {
    val context = LocalContext.current
    return remember(imageUrl, widthPx, heightPx) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .memoryCacheKey(imageUrl)
            .diskCacheKey(imageUrl)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .size(widthPx, heightPx)
            .build()
    }
}

@Composable
fun rememberPosterAccentColor(
    imageUrl: String,
    enabled: Boolean,
    fallback: Color = Color(0xFF8FE7FF)
): Color {
    val context = LocalContext.current
    var accentColor by remember(imageUrl) {
        mutableStateOf(posterAccentCache[imageUrl] ?: fallback)
    }

    LaunchedEffect(imageUrl, enabled) {
        if (!enabled || posterAccentCache.containsKey(imageUrl)) {
            return@LaunchedEffect
        }
        val resolvedColor = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .allowHardware(false)
                    .memoryCacheKey("palette:$imageUrl")
                    .diskCacheKey(imageUrl)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .size(180, 260)
                    .build()
                val drawable = context.imageLoader.execute(request).drawable ?: return@runCatching fallback
                val bitmap = drawable.toBitmap(width = 180, height = 260)
                val palette = Palette.from(bitmap).clearFilters().generate()
                val swatch = palette.vibrantSwatch
                    ?: palette.lightVibrantSwatch
                    ?: palette.dominantSwatch
                    ?: palette.mutedSwatch
                    ?: palette.lightMutedSwatch
                swatch?.rgb?.let(::Color) ?: fallback
            }.getOrDefault(fallback)
        }
        posterAccentCache[imageUrl] = resolvedColor
        if (posterAccentCache.size > 300) {
            val firstKey = posterAccentCache.keys.firstOrNull()
            if (firstKey != null) {
                posterAccentCache.remove(firstKey)
            }
        }
        accentColor = resolvedColor
    }

    return accentColor
}
